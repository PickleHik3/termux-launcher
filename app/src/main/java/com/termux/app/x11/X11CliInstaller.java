package com.termux.app.x11;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Puts {@code termux-x11} and {@code termux-x11-preference} in the prefix, the way the launcher
 * already puts {@code launcherctl} there.
 *
 * <p>The apt package cannot do this job for us: {@code termux-x11-nightly} bakes
 * {@code com.termux.x11} into both the script and the loader's signature check, so it can only
 * ever talk to the separate Termux:X11 app. These are the same scripts with this edition's ids.
 *
 * <p>It refuses to overwrite a {@code termux-x11} it did not write. Someone who has installed the
 * apt package has made a choice, and a home screen must not quietly take a command out from under
 * a package manager. Every file is written beside its destination and moved into place, so a
 * reader never sees a half-written command, and a destination that is a symlink is never followed.
 *
 * <p>All of it is disk I/O; {@link #installAsync} keeps it off the main thread.
 */
public final class X11CliInstaller {

    private static final String LOG_TAG = "X11CliInstaller";

    /** Bumped whenever the written files change, so an upgrade rewrites them once. */
    @VisibleForTesting static final int VERSION = 6;

    private static final String PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
    private static final String BIN_DIR = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    private static final String LIBEXEC_DIR = PREFIX + "/libexec/termux-launcher/x11";
    private static final String LOADER_ASSET = "x11/loader.apk";
    private static final String GPU_SETUP_ASSET = "x11/x11-gpu-setup.sh";

    static final String SERVER_SCRIPT_PATH = BIN_DIR + "/termux-x11";
    static final String PREFERENCE_SCRIPT_PATH = BIN_DIR + "/termux-x11-preference";
    /** The script that tries every GPU profile on this phone and keeps the best. */
    public static final String GPU_SETUP_SCRIPT_PATH = BIN_DIR + "/termux-x11-gpu-setup";
    static final String LOADER_PATH = LIBEXEC_DIR + "/loader.apk";
    /** openbox's configuration for the display: every window maximised, none decorated. */
    public static final String OPENBOX_RC_PATH = LIBEXEC_DIR + "/openbox-rc.xml";

    /** Every marker this launcher has ever written, so "is this ours?" survives an upgrade. */
    @VisibleForTesting static final String MARKER_PREAMBLE = "# written by termux-launcher";

    /** One thread for the prefix writes: they are rare and must never overlap. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "x11-cli-installer");
        thread.setDaemon(true);
        return thread;
    });

    /** What the install attempt found. */
    public enum Result {
        /** The scripts are in place and ours. */
        INSTALLED,
        /** Nothing to do: the same version is already written. */
        UP_TO_DATE,
        /** Another {@code termux-x11} is installed; left alone. */
        FOREIGN_COMMAND,
        /** The prefix is not there yet — the bootstrap has not run. */
        NO_PREFIX,
        FAILED
    }

    /** Where the shipped files' bytes come from: the app's assets, or a fixture. */
    interface AssetSource {
        @NonNull InputStream open(@NonNull String name) throws IOException;
    }

    @NonNull private final File binDir;
    @NonNull private final File libexecDir;
    @NonNull private final String applicationId;
    @NonNull private final AssetSource assets;

    @VisibleForTesting
    X11CliInstaller(@NonNull File binDir, @NonNull File libexecDir, @NonNull String applicationId,
                    @NonNull AssetSource assets) {
        this.binDir = binDir;
        this.libexecDir = libexecDir;
        this.applicationId = applicationId;
        this.assets = assets;
    }

    /** The installer for this launcher's own prefix. */
    @NonNull
    static X11CliInstaller forPrefix(@NonNull Context context) {
        Context app = context.getApplicationContext();
        return new X11CliInstaller(new File(BIN_DIR), new File(LIBEXEC_DIR), app.getPackageName(),
            name -> app.getAssets().open(name));
    }

    // ---- The static face the launcher uses -------------------------------------------------

    /**
     * Write the scripts and the loader if they are missing or out of date, off the main thread,
     * and report on it. Safe to call on every app start: an up-to-date install is one file read.
     */
    public static void installAsync(@NonNull Context context, @NonNull Consumer<Result> onResult) {
        X11CliInstaller installer = forPrefix(context);
        Handler main = new Handler(Looper.getMainLooper());
        EXECUTOR.execute(() -> {
            Result result = installer.install();
            main.post(() -> onResult.accept(result));
        });
    }

    /** Take the commands back out, for a user who switches the display off. */
    public static void uninstallAsync(@NonNull Context context) {
        X11CliInstaller installer = forPrefix(context);
        EXECUTOR.execute(installer::uninstall);
    }

    /**
     * Whether the XKB keyboard data the server needs is in the prefix. It comes from the
     * {@code xkeyboard-config} package; without it the server exits before it opens a port.
     */
    public static boolean hasKeyboardData() {
        return new File(PREFIX + "/share/X11/xkb").isDirectory()
            || new File(PREFIX + "/share/xkeyboard-config-2").isDirectory();
    }

    /** True once this launcher's own commands are in place. */
    public static boolean isInstalled(@NonNull Context context) {
        X11CliInstaller installer = forPrefix(context);
        return installer.serverScript().exists() && !installer.isForeignCommand();
    }

    // ---- The work ---------------------------------------------------------------------------

    @NonNull File serverScript() { return new File(binDir, "termux-x11"); }
    @NonNull File preferenceScript() { return new File(binDir, "termux-x11-preference"); }
    @NonNull File gpuSetupScript() { return new File(binDir, "termux-x11-gpu-setup"); }
    @NonNull File loaderFile() { return new File(libexecDir, "loader.apk"); }
    @NonNull File markerFile() { return new File(libexecDir, ".installed"); }
    @NonNull File openboxRc() { return new File(libexecDir, "openbox-rc.xml"); }

    @NonNull
    Result install() {
        if (!binDir.isDirectory()) return Result.NO_PREFIX;
        String marker = MARKER_PREAMBLE + " v" + VERSION + " " + applicationId + "\n";
        if (marker.equals(read(markerFile()))) return Result.UP_TO_DATE;
        if (isForeignCommand()) {
            Logger.logInfo(LOG_TAG, "Leaving a termux-x11 we did not write alone");
            return Result.FOREIGN_COMMAND;
        }
        try {
            if (!libexecDir.isDirectory() && !libexecDir.mkdirs()) {
                throw new IOException("Failed to create " + libexecDir);
            }
            // The loader must end up read-only: ART refuses a writable dex on CLASSPATH
            // ("Writable dex file ... is not allowed") and aborts app_process before main.
            try (InputStream in = assets.open(LOADER_ASSET)) {
                writeAtomically(loaderFile(), in, false, false);
            }
            writeAtomically(serverScript(), bytes(serverScript(applicationId)), true, true);
            writeAtomically(preferenceScript(), bytes(preferenceScript(applicationId)), true, true);
            writeAtomically(gpuSetupScript(), bytes(withPrefixShebang(readText(GPU_SETUP_ASSET))), true, true);
            writeAtomically(openboxRc(), bytes(openboxRcContent()), true, false);
            writeAtomically(markerFile(), bytes(marker), true, false);
            return Result.INSTALLED;
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install the X11 commands: " + e.getMessage());
            return Result.FAILED;
        }
    }

    void uninstall() {
        if (isForeignCommand()) return;
        for (File file : new File[]{serverScript(), preferenceScript(), gpuSetupScript(),
                loaderFile(), openboxRc(), markerFile()}) {
            if (!file.exists() && !Files.isSymbolicLink(file.toPath())) continue;
            file.setWritable(true, true);
            if (!file.delete()) {
                Logger.logWarn(LOG_TAG, "Failed to remove " + file);
            }
        }
    }

    /**
     * True while a {@code termux-x11} that is not ours sits in the prefix. Ours is a plain file
     * carrying the marker comment in its first lines; the apt package's does not, and a symlink
     * is somebody's arrangement whatever it points at.
     */
    boolean isForeignCommand() {
        File script = serverScript();
        if (Files.isSymbolicLink(script.toPath())) return true;
        if (!script.exists()) return false;
        String content = read(script);
        return content == null || !content.contains(MARKER_PREAMBLE);
    }

    /**
     * Upstream's {@code termux-x11.in} with our ids. The environment dance is upstream's and
     * matters: the X server must not inherit the shell's linker environment, and an xstartup
     * command has to be able to get it back.
     */
    @NonNull
    static String serverScript(@NonNull String applicationId) {
        return "#!" + BIN_DIR + "/bash\n"
            + MARKER_PREAMBLE + " — do not edit; the launcher rewrites it\n"
            + "if [ ! -e /system/bin/getprop ] || [ ! -e /system/bin/app_process ]; then\n"
            + "  echo \"This needs a standard Android system: the display server runs as an app process.\"\n"
            + "  exit 1\n"
            + "fi\n"
            // The server refuses to start without the XKB data and only knows how to find it in
            // com.termux's own prefix, so point it at this edition's. It is the xkeyboard-config
            // package's; the Display page says so when it is missing.
            + "if [ -z \"${XKB_CONFIG_ROOT+x}\" ]; then\n"
            + "  for dir in " + PREFIX + "/share/X11/xkb "
            + PREFIX + "/share/xkeyboard-config-2; do\n"
            + "    if [ -d \"$dir\" ]; then export XKB_CONFIG_ROOT=\"$dir\"; break; fi\n"
            + "  done\n"
            + "fi\n"
            + "[ -z \"${LD_LIBRARY_PATH+x}\" ] || export XSTARTUP_LD_LIBRARY_PATH=\"$LD_LIBRARY_PATH\"\n"
            + "[ -z \"${LD_PRELOAD+x}\" ] || export XSTARTUP_LD_PRELOAD=\"$LD_PRELOAD\"\n"
            + "[ -z \"${CLASSPATH+x}\" ] || export XSTARTUP_CLASSPATH=\"$CLASSPATH\"\n"
            + "export CLASSPATH=" + LOADER_PATH + "\n"
            + "unset LD_LIBRARY_PATH LD_PRELOAD\n"
            + "[ -n \"$(trap -p USR1)\" ] && export TERMUX_X11_NOTIFY_PARENT=1\n"
            + "exec /system/bin/app_process -Xnoimage-dex2oat / "
            + "--nice-name=\"termux-x11 " + applicationId + " $*\" com.termux.x11.Loader \"$@\"\n";
    }

    /**
     * Upstream's {@code termux-x11-preference.in}, kept to its {@code app_process} path only. The
     * {@code am broadcast} fallback it uses below sdk 34 cannot reach a receiver that is not
     * exported, and the launcher's is not.
     */
    @NonNull
    static String preferenceScript(@NonNull String applicationId) {
        return "#!" + BIN_DIR + "/bash\n"
            + MARKER_PREAMBLE + " — do not edit; the launcher rewrites it\n"
            + "if [ ! -e /system/bin/app_process ]; then\n"
            + "  echo \"This needs a standard Android system: the display server runs as an app process.\"\n"
            + "  exit 1\n"
            + "fi\n"
            + "if [ $# -eq 0 ]; then\n"
            + "  echo \"$0 [list] {key:value} [{key2:value2}]...\"\n"
            + "  exit 1\n"
            + "fi\n"
            + "unset LD_LIBRARY_PATH LD_PRELOAD\n"
            + "export CLASSPATH=" + LOADER_PATH + "\n"
            + "export TERMUX_X11_LOADER_OVERRIDE_CMDENTRYPOINT_CLASS="
            + "com.termux.x11.LoriePreferences\\$Receiver\n"
            + "exec /system/bin/app_process -Xnoimage-dex2oat / com.termux.x11.Loader \"$@\"\n";
    }

    /**
     * The window-manager rule behind "one app at a time, full size": every window maximised and
     * undecorated, focus following the newest. openbox fills every other setting from its own
     * defaults, so this is the whole file.
     */
    @NonNull
    static String openboxRcContent() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!-- " + MARKER_PREAMBLE + " for the Linux display; do not edit -->\n"
            + "<openbox_config xmlns=\"http://openbox.org/3.4/rc\">\n"
            + "  <focus><focusNew>yes</focusNew></focus>\n"
            // The window-switching chords the touchpad's three-finger swipe sends, and a hardware
            // keyboard's Alt+Tab; a rc.xml without a keyboard section has no bindings at all.
            + "  <keyboard>\n"
            + "    <keybind key=\"A-Tab\"><action name=\"NextWindow\">"
            + "<finalactions><action name=\"Focus\"/><action name=\"Raise\"/>"
            + "<action name=\"Unshade\"/></finalactions></action></keybind>\n"
            + "    <keybind key=\"A-S-Tab\"><action name=\"PreviousWindow\">"
            + "<finalactions><action name=\"Focus\"/><action name=\"Raise\"/>"
            + "<action name=\"Unshade\"/></finalactions></action></keybind>\n"
            + "  </keyboard>\n"
            + "  <applications>\n"
            + "    <application class=\"*\">\n"
            + "      <decor>no</decor>\n"
            + "      <maximized>yes</maximized>\n"
            + "      <focus>yes</focus>\n"
            + "    </application>\n"
            + "  </applications>\n"
            + "</openbox_config>\n";
    }

    // ---- Files ------------------------------------------------------------------------------

    /**
     * Write {@code content} to a temporary file beside {@code destination}, give it its final
     * mode, and move it into place in one step. A destination that is a symlink is refused rather
     * than followed: the launcher writes its own files, never through someone else's link.
     *
     * @param ownerWritable whether the owner keeps write access (the loader must not: ART
     *                      refuses a writable dex on {@code CLASSPATH})
     * @param executable    whether everyone may execute it (the two commands)
     */
    private static void writeAtomically(@NonNull File destination, @NonNull InputStream content,
                                        boolean ownerWritable, boolean executable)
            throws IOException {
        if (Files.isSymbolicLink(destination.toPath())) {
            throw new IOException(destination + " is a symlink; refusing to write through it");
        }
        File dir = destination.getParentFile();
        if (dir == null) throw new IOException(destination + " has no directory");
        File temp = File.createTempFile(".termux-x11-", ".tmp", dir);
        try {
            try (OutputStream out = new FileOutputStream(temp, false)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) > 0) out.write(buffer, 0, read);
            }
            // Mode first, then move, so the file is never observable half-permissioned.
            temp.setReadable(false, false);
            temp.setWritable(false, false);
            temp.setExecutable(false, false);
            temp.setReadable(true, false);
            if (ownerWritable) temp.setWritable(true, true);
            if (executable) temp.setExecutable(true, false);
            // A read-only destination cannot be truncated, but the directory entry can be
            // replaced: rename is what makes the upgrade of the read-only loader work at all.
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /** The shipped script's text, as written in the tree. */
    @NonNull
    private String readText(@NonNull String asset) throws IOException {
        try (InputStream in = assets.open(asset)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Point a script's {@code #!} line at this prefix's bash. The tree keeps
     * {@code #!/usr/bin/env bash} so the file runs on a PC too; on the phone the kernel needs
     * the real path, and it differs per edition.
     */
    @NonNull
    String withPrefixShebang(@NonNull String script) {
        if (!script.startsWith("#!")) return script;
        int newline = script.indexOf('\n');
        if (newline < 0) return script;
        return "#!" + new File(binDir, "bash").getPath() + script.substring(newline);
    }

    @NonNull
    private static InputStream bytes(@NonNull String content) {
        return new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private static String read(@NonNull File file) {
        if (!file.isFile() || file.length() > 64 * 1024) return null;
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
