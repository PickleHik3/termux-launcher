package com.termux.app.sound;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.x11.ProotDistro;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The launcher's sound: one PulseAudio server for every terminal session, Linux app and proot
 * distro, a session bus beside it, and the bridge that mirrors Linux media players into Android
 * (spec: {@code project-docs/active/launcher-sound-2026-10-25.md}).
 *
 * <p>Nothing runs until something may want sound — the first terminal session or the first Linux
 * app ({@link #onNeed}) — and nothing runs at all while the Sound switch is off, while
 * {@code pulseaudio} is not installed, or while the user has a setup of their own (decision 7).
 * The server, the bus and the helper are the launcher's own children, each watched by a thread
 * that blocks on its exit ({@link SupervisedProcess}); no timer and no poll keeps any of it alive.
 *
 * <p>The callers reach it through four doors: the environment every new shell is given
 * ({@link #environmentForNewShell}), the need ({@link #onNeed}), a distro about to be entered
 * ({@link #prepareContainer}) and the settings ({@link #onSettingsChanged}, {@link #status}).
 * The helper's reports come in through launcherctl ({@link SoundRoutes}).
 */
public final class LauncherSound {

    /**
     * The variable that carries the bus's address, as a distro sees it, to the line that logs into
     * one: {@link ProotDistro#loginCommand} forwards it only when it is set.
     */
    public static final String DISTRO_BUS_VARIABLE = "TERMUX_LAUNCHER_DISTRO_BUS";

    /** What the Linux apps page says about sound. */
    public enum Status {
        /** The Sound switch is off. */
        OFF,
        /** {@code pulseaudio} is not installed: nothing to run. */
        NOT_INSTALLED,
        /** Nothing has needed sound yet. */
        IDLE,
        RUNNING,
        /** The user's own setup is in place, so the launcher runs and writes nothing (decision 7). */
        USER_SETUP,
        /** The server kept stopping and was left down until the next need (decision 3). */
        GAVE_UP
    }

    private static final String LOG_TAG = "LauncherSound";
    /** PulseAudio's cookie length. */
    private static final int COOKIE_BYTES = 256;
    /** How long a command the media bridge runs (pactl, dbus-send) may take. */
    private static final long COMMAND_TIMEOUT_MS = 5_000L;
    /** The most of a command's output that is kept. */
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final int SIGTERM = 15;
    private static final int SIGKILL = 9;

    @Nullable private static LauncherSound sShared;

    /** The process's one instance. Any thread. */
    @NonNull
    public static synchronized LauncherSound get(@NonNull Context context) {
        if (sShared == null) sShared = new LauncherSound(context);
        return sShared;
    }

    @NonNull private final Context context;
    /** Every start, stop and file write happens here, one at a time. */
    @NonNull private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "launcher-sound");
        thread.setDaemon(true);
        return thread;
    });
    /** Held around every file the launcher writes or removes for sound. */
    @NonNull private final Object files = new Object();
    @NonNull private final SupervisedProcess server;
    @NonNull private final SupervisedProcess bus;
    @NonNull private final SupervisedProcess helper;
    @NonNull private final MediaBridge media;

    @NonNull private volatile Status status = Status.IDLE;
    /** Whether the user has a setup of their own; null until it has been looked at. */
    @Nullable private volatile Boolean userSetup;
    /** Which sink the next server start loads (decision 2). */
    @NonNull private volatile SoundFiles.Sink sink = SoundFiles.Sink.AAUDIO;
    /** Whether the media bridge is running; worker only. */
    private boolean mediaRunning;

    private LauncherSound(@NonNull Context context) {
        this.context = context.getApplicationContext();
        Supervision owner = new Supervision();
        server = new SupervisedProcess("pulseaudio", owner);
        bus = new SupervisedProcess("dbus-daemon", owner);
        helper = new SupervisedProcess("media helper", owner);
        media = new MediaBridge(this.context, new MediaBridge.Shell() {
            @Override public void execute(@NonNull Runnable task) {
                worker.execute(task);
            }

            @Nullable @Override public String run(@NonNull List<String> argv) {
                return runCommand(argv);
            }
        });
    }

    // ---- The doors ------------------------------------------------------------------------------

    /**
     * What a new terminal session, background task or Linux app is given: {@code PULSE_SERVER},
     * and the bus when {@code dbus-daemon} is installed. Null when the launcher's server is not
     * the one to use — Sound off, {@code pulseaudio} missing, or the user's own setup. Cheap
     * enough for any thread: a few file checks, and only the first time a directory listing.
     */
    @Nullable
    public HashMap<String, String> environmentForNewShell() {
        try {
            if (!soundEnabled() || !canExecute(SoundPaths.PULSEAUDIO)) return null;
            Boolean user = userSetup;
            if (user == null) {
                user = !StepAside.userFiles(new File(SoundPaths.USER_CONFIG_DIR).list()).isEmpty();
            }
            if (user) return null;
            return new HashMap<>(SoundFiles.shellEnvironment(canExecute(SoundPaths.DBUS_DAEMON)));
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "No sound environment for a new shell: " + e.getMessage());
            return null;
        }
    }

    /**
     * Something may want sound now: a terminal session was created, or a Linux app is being
     * opened. Starts what is not running yet; a no-op when everything is. Any thread.
     */
    public void onNeed() {
        worker.execute(this::ensureRunning);
    }

    /**
     * {@code container} is about to be entered by the launcher: write or refresh its client
     * drop-in and the cookie's copy (decision 4). Blocking file work; call it off the main thread.
     */
    public void prepareContainer(@NonNull ProotDistro.Container container) {
        if (container.kind != ProotDistro.Container.Kind.DISTRO) return;
        try {
            if (!soundEnabled() || !canExecute(SoundPaths.PULSEAUDIO)) return;
            Boolean user = userSetup;
            if (Boolean.TRUE.equals(user) || status == Status.GAVE_UP) return;
            if (user == null
                    && !StepAside.userFiles(new File(SoundPaths.USER_CONFIG_DIR).list()).isEmpty()) {
                return;
            }
            synchronized (files) {
                ensureCookie();
                writeDistroDropIn(container.root);
            }
        } catch (IOException | RuntimeException e) {
            Logger.logWarn(LOG_TAG, "No sound drop-in for " + container.name + ": " + e.getMessage());
        }
    }

    /** One of the two switches changed. Any thread. */
    public void onSettingsChanged() {
        worker.execute(this::ensureRunning);
    }

    /** What the Linux apps page says. */
    @NonNull
    public Status status() {
        return soundEnabled() ? status : Status.OFF;
    }

    /** A button on the media notification. */
    void onMediaControl(@NonNull String action) {
        media.onControl(action);
    }

    // ---- The helper's reports (SoundRoutes) -------------------------------------------------------

    void onPlayerReport(@NonNull String name, int pid, @Nullable String reply) {
        PlayerState state = MprisReply.parse(name, pid, reply);
        if (state != null) media.onPlayer(state);
    }

    void onPlayerGone(@NonNull String name) {
        media.onGone(name);
    }

    void onPlayerSeeked(@NonNull String name, long positionMicros) {
        media.onSeeked(name, positionMicros / 1000L);
    }

    void onStreams(int playing) {
        media.onStreams(playing);
    }

    // ---- The worker -----------------------------------------------------------------------------

    private void ensureRunning() {
        try {
            if (!soundEnabled()) {
                shutDown();
                userSetup = null;
                status = Status.OFF;
                return;
            }
            if (!canExecute(SoundPaths.PULSEAUDIO)) {
                shutDown();
                status = Status.NOT_INSTALLED;
                return;
            }
            if (!server.isWanted()) {
                stopPieces();
                if (!clearTheWay()) return;
                synchronized (files) {
                    writeServerFiles();
                }
                server.start();
                if (!server.isWanted()) return;
                status = Status.RUNNING;
                refreshDistros();
            }
            ensureBus();
            ensureMedia();
        } catch (IOException | RuntimeException e) {
            Logger.logErrorExtended(LOG_TAG, "Sound did not start: " + e.getMessage());
        }
    }

    /**
     * Decisions 7 and 8. With a setup of the user's own — their files in {@code ~/.config/pulse},
     * or a server started with options of their choosing — the launcher takes its own drop-ins
     * back out and stops there. Otherwise a stock server is stopped, and anything left of the
     * launcher's own from an earlier life of this process is killed, since nothing waits on it.
     *
     * @return whether the launcher's server may start
     */
    private boolean clearTheWay() throws IOException {
        List<String> userFiles = StepAside.userFiles(new File(SoundPaths.USER_CONFIG_DIR).list());
        List<Integer> stock = new ArrayList<>();
        List<Integer> orphans = new ArrayList<>();
        boolean userServer = false;
        for (Map.Entry<Integer, List<String>> process : ownProcesses().entrySet()) {
            List<String> argv = process.getValue();
            if (argv.contains(SoundPaths.HELPER)
                    || argv.contains("--config-file=" + SoundPaths.BUS_CONFIG)) {
                orphans.add(process.getKey());
                continue;
            }
            StepAside.Server kind = StepAside.classify(argv, SoundPaths.STARTUP_FILE);
            if (kind == StepAside.Server.OURS) orphans.add(process.getKey());
            else if (kind == StepAside.Server.STOCK) stock.add(process.getKey());
            else if (kind == StepAside.Server.USER) userServer = true;
        }
        if (!userFiles.isEmpty() || userServer) {
            Logger.logInfo(LOG_TAG, "Using the user's own sound setup"
                + (userFiles.isEmpty() ? " (a server they started)" : " (" + userFiles + ")"));
            userSetup = true;
            synchronized (files) {
                removeDropIns();
            }
            status = Status.USER_SETUP;
            return false;
        }
        userSetup = false;
        for (int pid : stock) {
            Logger.logInfo(LOG_TAG, "Stopping a stock PulseAudio server, " + pid);
            android.os.Process.sendSignal(pid, SIGTERM);
        }
        for (int pid : orphans) {
            android.os.Process.sendSignal(pid, SIGKILL);
            awaitGone(pid);
        }
        return true;
    }

    /**
     * A killed process takes a moment to free its socket, port and pid file. Bounded, and only
     * ever reached for a process of the launcher's own that outlived the process that started it.
     */
    private static void awaitGone(int pid) {
        File proc = new File("/proc/" + pid);
        for (int i = 0; i < 40 && proc.exists(); i++) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** The command lines of this uid's processes, by pid; another uid's cannot be read. */
    @NonNull
    private static Map<Integer, List<String>> ownProcesses() {
        Map<Integer, List<String>> processes = new HashMap<>();
        String[] entries = new File("/proc").list();
        if (entries == null) return processes;
        int self = android.os.Process.myPid();
        for (String entry : entries) {
            if (entry.isEmpty() || !Character.isDigit(entry.charAt(0))) continue;
            int pid;
            try {
                pid = Integer.parseInt(entry);
            } catch (NumberFormatException e) {
                continue;
            }
            if (pid == self) continue;
            byte[] raw = readBytes(new File("/proc/" + entry + "/cmdline"), 8192);
            if (raw == null || raw.length == 0) continue;
            processes.put(pid, StepAside.parseCmdline(raw, raw.length));
        }
        return processes;
    }

    private void ensureBus() throws IOException {
        if (!server.isWanted() || !canExecute(SoundPaths.DBUS_DAEMON)) {
            bus.stop();
            return;
        }
        if (bus.isWanted()) return;
        synchronized (files) {
            writeText(new File(SoundPaths.BUS_CONFIG), SoundFiles.busConfig(SoundPaths.BUS_SOCKET),
                false, false);
            File sockets = new File(SoundPaths.X11_SOCKETS);
            if (!sockets.isDirectory()) {
                if (!sockets.mkdirs()) throw new IOException("Failed to create " + sockets);
                // What an X server makes it, so a display started later finds it as it expects.
                try {
                    android.system.Os.chmod(sockets.getPath(), 01777);
                } catch (Exception ignored) {
                }
            }
            File socket = new File(SoundPaths.BUS_SOCKET);
            if (socket.exists() && !socket.delete()) {
                Logger.logWarn(LOG_TAG, "A stale bus socket could not be removed");
            }
        }
        bus.start();
    }

    private void ensureMedia() throws IOException {
        boolean pactl = canExecute(SoundPaths.PACTL);
        boolean players = bus.isWanted() && canExecute(SoundPaths.DBUS_MONITOR)
            && canExecute(SoundPaths.DBUS_SEND);
        boolean want = mediaEnabled() && server.isWanted() && canExecute(SoundPaths.BASH)
            && canExecute(SoundPaths.LAUNCHERCTL) && (pactl || players);
        if (!want) {
            helper.stop();
            if (mediaRunning) {
                mediaRunning = false;
                media.stop();
            }
            return;
        }
        if (helper.isWanted()) return;
        synchronized (files) {
            installHelper();
        }
        mediaRunning = true;
        helper.start();
    }

    /** Everything down, the launcher's drop-ins taken back out (Sound off, decision 13). */
    private void shutDown() {
        stopPieces();
        server.stop();
        synchronized (files) {
            removeDropIns();
        }
    }

    /** The bus, the helper and the media bridge down; the server is left as it is. */
    private void stopPieces() {
        helper.stop();
        if (mediaRunning) {
            mediaRunning = false;
            if (server.isWanted()) media.restoreServerNow();
            media.stop();
        }
        bus.stop();
    }

    /** Decides for the three processes; runs on their waiter threads. */
    private final class Supervision implements SupervisedProcess.Owner {

        @Nullable
        @Override
        public ProcessBuilder build(@NonNull SupervisedProcess process) {
            Map<String, String> termux;
            try {
                termux = new TermuxShellEnvironment().getEnvironment(context, false);
            } catch (RuntimeException e) {
                Logger.logWarn(LOG_TAG, "No Termux environment for " + process.name);
                return null;
            }
            String preload = preload();
            List<String> argv;
            Map<String, String> env;
            if (process == server) {
                if (!canExecute(SoundPaths.PULSEAUDIO)) return null;
                new File(SoundPaths.VOLUMES_DIR).mkdirs();
                File runtime = new File(SoundPaths.RUNTIME_DIR);
                runtime.mkdirs();
                try {
                    android.system.Os.chmod(runtime.getPath(), 0700);
                } catch (Exception ignored) {
                }
                argv = SoundFiles.serverArgv(SoundPaths.PULSEAUDIO, SoundPaths.STARTUP_FILE);
                env = SoundFiles.processEnvironment(termux, preload, true);
            } else if (process == bus) {
                if (!canExecute(SoundPaths.DBUS_DAEMON)) return null;
                argv = SoundFiles.busArgv(SoundPaths.DBUS_DAEMON, SoundPaths.BUS_CONFIG);
                env = SoundFiles.processEnvironment(termux, preload, false);
            } else {
                if (!canExecute(SoundPaths.BASH)) return null;
                argv = Arrays.asList(SoundPaths.BASH, SoundPaths.HELPER);
                env = SoundFiles.processEnvironment(termux, preload, false);
                // A helper that starts again reports every player afresh.
                media.start();
            }
            ProcessBuilder builder = new ProcessBuilder(argv);
            builder.environment().clear();
            builder.environment().putAll(env);
            builder.directory(new File(SoundPaths.HOME));
            return builder;
        }

        @NonNull
        @Override
        public SupervisedProcess.AfterExit afterExit(@NonNull SupervisedProcess process,
                                                     int exitCode, @NonNull List<String> output) {
            if (process == server && sink == SoundFiles.Sink.AAUDIO) {
                for (String line : output) {
                    if (!SoundFiles.reportsSinkFailure(line, SoundFiles.Sink.AAUDIO)) continue;
                    Logger.logInfo(LOG_TAG, "AAudio would not open; using OpenSL ES");
                    sink = SoundFiles.Sink.SLES;
                    try {
                        synchronized (files) {
                            writeStartupFile();
                        }
                    } catch (IOException e) {
                        return SupervisedProcess.AfterExit.RESTART;
                    }
                    return SupervisedProcess.AfterExit.RESTART_UNCOUNTED;
                }
            }
            if (process == helper && !mediaEnabled()) return SupervisedProcess.AfterExit.STOP;
            return SupervisedProcess.AfterExit.RESTART;
        }

        @Override
        public void onGaveUp(@NonNull SupervisedProcess process) {
            if (process != server) return;
            worker.execute(() -> {
                stopPieces();
                // Clients pointed at a server that is not there would stay silent; without the
                // drop-ins they fall back to their own until the next need starts this again.
                synchronized (files) {
                    removeDropIns();
                }
                status = Status.GAVE_UP;
            });
        }
    }

    // ---- Files --------------------------------------------------------------------------------------

    /** Must hold {@link #files}. */
    private void writeServerFiles() throws IOException {
        ensureCookie();
        writeStartupFile();
        File dropIn = new File(SoundPaths.TERMUX_DROP_IN);
        String wanted = SoundFiles.termuxDropIn(SoundPaths.PULSE_SERVER);
        switch (SoundFiles.decide(dropIn.exists(), readText(dropIn), wanted)) {
            case WRITE:
                writeText(dropIn, wanted, false, false);
                break;
            case FOREIGN:
                Logger.logInfo(LOG_TAG, "Leaving a client drop-in that is not the launcher's");
                break;
            default:
                break;
        }
    }

    /** Must hold {@link #files}. */
    private void writeStartupFile() throws IOException {
        writeText(new File(SoundPaths.STARTUP_FILE), SoundFiles.startupFile(sink, SoundPaths.SOCKET,
            SoundPaths.COOKIE, SoundPaths.TCP_PORT), false, false);
    }

    /** The cookie, made once and kept. Must hold {@link #files}. */
    private void ensureCookie() throws IOException {
        File cookie = new File(SoundPaths.COOKIE);
        if (cookie.isFile() && cookie.length() == COOKIE_BYTES) return;
        byte[] bytes = new byte[COOKIE_BYTES];
        new SecureRandom().nextBytes(bytes);
        writeBytes(cookie, bytes, true, false);
    }

    /** Every installed distro's drop-in, refreshed. Worker. */
    private void refreshDistros() {
        for (ProotDistro.Container container : ProotDistro.installed()) {
            try {
                synchronized (files) {
                    writeDistroDropIn(container.root);
                }
            } catch (IOException | RuntimeException e) {
                Logger.logWarn(LOG_TAG, "No sound drop-in for " + container.name + ": "
                    + e.getMessage());
            }
        }
    }

    /**
     * A distro's drop-in and the cookie's copy, when they are missing or stale; a drop-in that
     * is not the launcher's is never touched, and then no cookie is copied either. Must hold
     * {@link #files}.
     */
    private void writeDistroDropIn(@NonNull File rootfs) throws IOException {
        File dropIn = new File(rootfs, SoundPaths.DISTRO_DROP_IN);
        String wanted = SoundFiles.distroDropIn(SoundPaths.PULSE_SERVER_TCP, SoundPaths.DISTRO_COOKIE);
        SoundFiles.Write action = SoundFiles.decide(dropIn.exists(), readText(dropIn), wanted);
        if (action == SoundFiles.Write.FOREIGN) return;
        if (action == SoundFiles.Write.WRITE) writeText(dropIn, wanted, false, false);
        File copy = new File(rootfs, SoundPaths.DISTRO_COOKIE.substring(1));
        byte[] cookie = readBytes(new File(SoundPaths.COOKIE), COOKIE_BYTES + 1);
        if (cookie == null || cookie.length != COOKIE_BYTES) return;
        byte[] current = copy.isFile() ? readBytes(copy, COOKIE_BYTES + 1) : null;
        if (current == null || !Arrays.equals(current, cookie)) writeBytes(copy, cookie, false, false);
    }

    /** The launcher's drop-ins, in the prefix and in every distro. Must hold {@link #files}. */
    private void removeDropIns() {
        removeIfOurs(new File(SoundPaths.TERMUX_DROP_IN), null);
        for (ProotDistro.Container container : ProotDistro.installed()) {
            removeIfOurs(new File(container.root, SoundPaths.DISTRO_DROP_IN),
                new File(container.root, SoundPaths.DISTRO_COOKIE.substring(1)));
        }
    }

    private static void removeIfOurs(@NonNull File dropIn, @Nullable File cookie) {
        if (!dropIn.exists() || !SoundFiles.isOurs(readText(dropIn))) return;
        if (!dropIn.delete()) Logger.logWarn(LOG_TAG, "Failed to remove " + dropIn);
        if (cookie != null && cookie.exists() && !cookie.delete()) {
            Logger.logWarn(LOG_TAG, "Failed to remove " + cookie);
        }
    }

    /** The helper script, rewritten only when the shipped text differs. Must hold {@link #files}. */
    private void installHelper() throws IOException {
        String text;
        try (InputStream in = context.getAssets().open(SoundPaths.HELPER_ASSET)) {
            text = new String(readAll(in, MAX_OUTPUT_BYTES), StandardCharsets.UTF_8);
        }
        if (text.startsWith("#!")) {
            int newline = text.indexOf('\n');
            if (newline > 0) text = "#!" + SoundPaths.BASH + text.substring(newline);
        }
        File script = new File(SoundPaths.HELPER);
        if (text.equals(readText(script))) return;
        writeText(script, text, false, true);
    }

    @Nullable
    private static String readText(@NonNull File file) {
        byte[] bytes = readBytes(file, MAX_OUTPUT_BYTES);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    @Nullable
    private static byte[] readBytes(@NonNull File file, int limit) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            return readAll(in, limit);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @NonNull
    private static byte[] readAll(@NonNull InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while (out.size() < limit && (read = in.read(buffer)) > 0) {
            out.write(buffer, 0, Math.min(read, limit - out.size()));
        }
        return out.toByteArray();
    }

    private static void writeText(@NonNull File destination, @NonNull String text,
                                  boolean privateFile, boolean executable) throws IOException {
        writeBytes(destination, text.getBytes(StandardCharsets.UTF_8), privateFile, executable);
    }

    /**
     * Written beside the destination and moved into place, so no reader sees half a file; a
     * destination that is a symlink is refused rather than followed.
     */
    private static void writeBytes(@NonNull File destination, @NonNull byte[] bytes,
                                   boolean privateFile, boolean executable) throws IOException {
        if (Files.isSymbolicLink(destination.toPath())) {
            throw new IOException(destination + " is a symlink; refusing to write through it");
        }
        File dir = destination.getParentFile();
        if (dir == null) throw new IOException(destination + " has no directory");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Failed to create " + dir);
        File temp = File.createTempFile(".termux-launcher-", ".tmp", dir);
        try {
            try (OutputStream out = new FileOutputStream(temp, false)) {
                out.write(bytes);
            }
            temp.setReadable(false, false);
            temp.setWritable(false, false);
            temp.setExecutable(false, false);
            temp.setReadable(true, privateFile);
            temp.setWritable(true, true);
            if (executable) temp.setExecutable(true, false);
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    // ---- Commands and settings --------------------------------------------------------------------

    /** Runs {@code argv} with the sound environment and waits for it, bounded. Worker only. */
    @Nullable
    private String runCommand(@NonNull List<String> argv) {
        if (!canExecute(argv.get(0))) return null;
        try {
            Map<String, String> env = SoundFiles.processEnvironment(
                new TermuxShellEnvironment().getEnvironment(context, false), preload(), false);
            env.put("LC_ALL", "C");
            ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
            builder.environment().clear();
            builder.environment().putAll(env);
            Process process = builder.start();
            process.getOutputStream().close();
            // These commands print a line or two, far below what a pipe holds, so waiting before
            // reading cannot block on a full pipe; it bounds a command that never answers.
            if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroy();
                return null;
            }
            return new String(readAll(process.getInputStream(), MAX_OUTPUT_BYTES),
                StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Could not run " + argv.get(0) + ": " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Nullable
    private static String preload() {
        for (String path : SoundPaths.EXEC_PRELOADS) {
            if (new File(path).isFile()) return path;
        }
        return null;
    }

    private static boolean canExecute(@NonNull String path) {
        return new File(path).canExecute();
    }

    private boolean soundEnabled() {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        return preferences != null && preferences.isLauncherSoundEnabled();
    }

    private boolean mediaEnabled() {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        return preferences != null && preferences.isLauncherSoundEnabled()
            && preferences.isLauncherMediaControlsEnabled();
    }

    @VisibleForTesting
    static synchronized void resetForTest() {
        sShared = null;
    }
}
