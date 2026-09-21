package com.termux.app.x11;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

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
    @VisibleForTesting static final int VERSION = 12;

    private static final String PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
    private static final String BIN_DIR = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    private static final String LIBEXEC_DIR = PREFIX + "/libexec/termux-launcher/x11";
    private static final String LOADER_ASSET = "x11/loader.apk";
    private static final String GPU_SETUP_ASSET = "x11/x11-gpu-setup.sh";

    static final String SERVER_SCRIPT_PATH = BIN_DIR + "/termux-x11";
    static final String PREFERENCE_SCRIPT_PATH = BIN_DIR + "/termux-x11-preference";
    /** The script that tries every GPU profile on this phone and keeps the best. */
    public static final String GPU_SETUP_SCRIPT_PATH = BIN_DIR + "/termux-x11-gpu-setup";
    /**
     * The nix edition's window-manager wrapper: the whole {@code login} line baked into a file so
     * the server is handed one short path. Written only on nix — see {@link X11WindowManager}.
     */
    public static final String WM_SCRIPT_PATH = BIN_DIR + "/termux-x11-wm";
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
    /** The window manager the user has configured, as the wrapper has to run it; may be empty. */
    @NonNull private final String windowManager;

    @VisibleForTesting
    X11CliInstaller(@NonNull File binDir, @NonNull File libexecDir, @NonNull String applicationId,
                    @NonNull AssetSource assets) {
        this(binDir, libexecDir, applicationId, assets, "");
    }

    @VisibleForTesting
    X11CliInstaller(@NonNull File binDir, @NonNull File libexecDir, @NonNull String applicationId,
                    @NonNull AssetSource assets, @NonNull String windowManager) {
        this.binDir = binDir;
        this.libexecDir = libexecDir;
        this.applicationId = applicationId;
        this.assets = assets;
        this.windowManager = windowManager;
    }

    /** The installer for this launcher's own prefix. */
    @NonNull
    static X11CliInstaller forPrefix(@NonNull Context context) {
        Context app = context.getApplicationContext();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app);
        return new X11CliInstaller(new File(BIN_DIR), new File(LIBEXEC_DIR), app.getPackageName(),
            name -> app.getAssets().open(name),
            preferences == null ? "" : preferences.getX11WindowManager());
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
     *
     * <p>A filesystem probe: cheap, but never call it from a hot path — the Display page re-runs
     * it only on arrival and on a running-state change, never on every frame.
     */
    public static boolean hasKeyboardData() {
        return hasKeyboardData(new File(PREFIX));
    }

    /**
     * The same check against an injected prefix root, so a test can fake "installed" or "missing"
     * without the real prefix. {@link #hasKeyboardData()} is the only caller that reaches for the
     * static {@link #PREFIX} directly.
     */
    @VisibleForTesting
    static boolean hasKeyboardData(@NonNull File prefixDir) {
        return new File(prefixDir, "share/X11/xkb").isDirectory()
            || new File(prefixDir, "share/xkeyboard-config-2").isDirectory();
    }

    /** The link {@link #linkKeyboardData} keeps, and the server script's first search path. */
    @NonNull
    private static File keyboardDataLink(@NonNull File prefixDir) {
        return new File(prefixDir, "share/X11/xkb");
    }

    /**
     * Whether what sits at {@link #keyboardDataLink} is the link this class made: a symlink whose
     * own target — as written, not as resolved, since resolving a link somebody else made is
     * exactly what must not decide this — is inside the nix store. A real directory there, and a
     * symlink pointing anywhere else, are both somebody's own arrangement.
     */
    private static boolean isOurKeyboardDataLink(@NonNull File prefixDir) {
        File link = keyboardDataLink(prefixDir);
        if (!Files.isSymbolicLink(link.toPath())) return false;
        try {
            String target = Files.readSymbolicLink(link.toPath()).toString();
            String store = new File(prefixDir, "nix/store").getPath();
            return target.startsWith(store + "/");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * On nix, point {@code $PREFIX/share/X11/xkb} at whichever store path holds this phone's
     * {@code xkeyboard-config} right now, so the probe above and the server script's own search
     * both find it where they already look.
     *
     * <p>Run on every install pass rather than once behind the version marker: a
     * {@code nix-on-droid switch} rebuilds the package into a store path of a different name, and
     * a link left pointing at the old one is a display that stops starting for no visible reason.
     * A {@code share/X11/xkb} that is not a link of ours is somebody's own arrangement and is
     * left exactly as it is.
     *
     * @return whether the link now points at the data
     */
    @VisibleForTesting
    boolean linkKeyboardData() {
        File prefixDir = binDir.getParentFile();
        if (prefixDir == null || !NixProfile.isNix(prefixDir)) return false;
        File data = NixProfile.keyboardData(prefixDir);
        if (data == null) return false;
        File link = keyboardDataLink(prefixDir);
        try {
            boolean isLink = Files.isSymbolicLink(link.toPath());
            if (isLink && data.toPath().equals(Files.readSymbolicLink(link.toPath()))) return true;
            // Anything there that is not this class's own link — a real directory, or a link
            // somebody pointed somewhere of their own — stays exactly as they left it; only a
            // link of ours is re-pointed, which is what a switch needs.
            if (isLink && !isOurKeyboardDataLink(prefixDir)) return false;
            if (!isLink && link.exists()) return link.isDirectory();
            File parent = link.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Failed to create " + parent);
            }
            if (isLink) Files.delete(link.toPath());
            Files.createSymbolicLink(link.toPath(), data.toPath());
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to link the keyboard data: " + e.getMessage());
            return false;
        }
    }

    /**
     * On nix, write {@code $PREFIX/bin/termux-x11-wm}: the whole {@code login} line for the
     * configured window manager, baked into a file the server can be handed by its short path.
     *
     * <p>Two facts make the wrapper the only way. The server execs {@code -xstartup} from
     * Android's side, where a nix binary and the profile's {@code PATH} do not exist, so it has to
     * go through {@code login}; and the server refuses any single argument past about 128
     * characters, which that login line — with a configuration file path in it — is well beyond.
     *
     * <p>Rewritten on every pass rather than once behind the version marker, because the window
     * manager is a setting: the file has to say what the user last chose. When nothing is
     * configured, or its binary is not installed, any wrapper left from before is taken out, so
     * {@link X11WindowManager#xstartup} finds nothing and the display starts without one.
     *
     * @return whether a wrapper is now in place
     */
    @VisibleForTesting
    boolean writeWindowManagerScript() {
        File prefixDir = binDir.getParentFile();
        if (prefixDir == null || !NixProfile.isNix(prefixDir)) return false;
        String command = X11WindowManager.command(windowManager, prefixDir);
        File script = wmScript();
        if (command == null) {
            if (script.exists()) {
                script.setWritable(true, true);
                //noinspection ResultOfMethodCallIgnored
                script.delete();
            }
            return false;
        }
        try {
            writeAtomically(script, bytes(windowManagerScript(prefixDir, command)), true, true);
            return true;
        } catch (IOException e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to write the window-manager wrapper: "
                + e.getMessage());
            return false;
        }
    }

    /** The wrapper's text: a login into the nix environment, and the window manager inside it. */
    @NonNull
    static String windowManagerScript(@NonNull File prefixDir, @NonNull String command) {
        return "#!" + NixProfile.hostShell(prefixDir).getPath() + "\n"
            + MARKER_PREAMBLE + " — do not edit; the launcher rewrites it\n"
            + "exec " + new File(prefixDir, "bin/login").getPath() + " " + command + "\n";
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
    @NonNull File wmScript() { return new File(binDir, "termux-x11-wm"); }
    @NonNull File loaderFile() { return new File(libexecDir, "loader.apk"); }
    @NonNull File markerFile() { return new File(libexecDir, ".installed"); }
    @NonNull File openboxRc() { return new File(libexecDir, "openbox-rc.xml"); }

    @NonNull
    Result install() {
        if (!binDir.isDirectory()) return Result.NO_PREFIX;
        linkKeyboardData();
        writeWindowManagerScript();
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
            // The server is exec'd by the launcher, from Android's side; the other two are run
            // by the user from inside the environment. On nix those are two different shells.
            File prefixDir = binDir.getParentFile();
            boolean nix = prefixDir != null && NixProfile.isNix(prefixDir);
            writeAtomically(serverScript(),
                bytes(serverScript(applicationId, hostShellPath(), nix)), true, true);
            writeAtomically(preferenceScript(),
                bytes(preferenceScript(applicationId, prefixShellPath())), true, true);
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
        File prefixDir = binDir.getParentFile();
        if (prefixDir != null && isOurKeyboardDataLink(prefixDir)) {
            File link = keyboardDataLink(prefixDir);
            try {
                Files.delete(link.toPath());
            } catch (IOException e) {
                Logger.logWarn(LOG_TAG, "Failed to remove " + link);
            }
        }
        for (File file : new File[]{serverScript(), preferenceScript(), gpuSetupScript(),
                wmScript(), loaderFile(), openboxRc(), markerFile()}) {
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
        return serverScript(applicationId, BIN_DIR + "/bash");
    }

    /**
     * {@link #serverScript(String)} with the shell spelled out. The nix edition's prefix has no
     * {@code bash} — it has the bootstrap's {@code sh} and nothing else — and a script whose
     * shebang names a file that is not there does not run at all, which is a display that never
     * starts. See {@link NixProfile#hostShell}.
     */
    @NonNull
    static String serverScript(@NonNull String applicationId, @NonNull String shell) {
        return serverScript(applicationId, shell, false);
    }

    /**
     * Where a font package puts its directories inside its store entry. Current nixpkgs
     * ({@code font-misc-misc}) uses {@code share/fonts/X11}; the older {@code xorg.*} packages
     * used {@code lib/X11/fonts}, and a store can hold both at once.
     */
    private static final String[] NIX_FONT_ROOTS = {"share/fonts/X11", "lib/X11/fonts"};

    /**
     * The font directories a nix store can hold, under each of {@link #NIX_FONT_ROOTS}. The X
     * server ships only its own built-in {@code fixed} and {@code cursor}, and an old core-font
     * client — xterm above all — asks for a real one by name and quits when the server has no
     * font path to look it up in. Termux's prefix keeps its fonts where the server already looks;
     * nixpkgs puts each font package in its own store entry, so the path has to be gathered when
     * the server starts, after whichever switch put them there.
     */
    private static final String[] NIX_FONT_DIRS =
        {"misc", "75dpi", "100dpi", "TTF", "Type1", "cyrillic"};

    /**
     * {@link #serverScript(String)} with the shell spelled out, and — for nix — a font path
     * gathered out of the store. See {@link NixProfile#hostShell} for the shell: the nix
     * edition's prefix has no {@code bash}, and its own {@code sh} is a store link an
     * {@code execve} from Android cannot follow, so a script whose shebang names either does not
     * run at all, which is a display that never starts.
     */
    @NonNull
    static String serverScript(@NonNull String applicationId, @NonNull String shell,
                               boolean nixFontPath) {
        return "#!" + shell + "\n"
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
            // Android's mksh has no `trap -p` and says so on stderr; the answer is the same
            // either way (no handler, no notification), so only the complaint is silenced.
            + "[ -n \"$(trap -p USR1 2>/dev/null)\" ] && export TERMUX_X11_NOTIFY_PARENT=1\n"
            + (nixFontPath ? nixFontPathBlock(applicationId) : "")
            + "exec /system/bin/app_process -Xnoimage-dex2oat / "
            + "--nice-name=\"" + (nixFontPath ? "$TERMUX_X11_NICE_NAME"
                : "termux-x11 " + applicationId + " $*")
            + "\" com.termux.x11.Loader \"$@\"\n";
    }

    /**
     * The lines that build {@code -fp} out of whatever font packages this phone's nix store has,
     * at the moment the server starts — a switch adds and removes them, and each lands in a store
     * entry of its own name, so there is nothing to write down once.
     *
     * <p>An unmatched glob stays literal in a POSIX shell, so a store with no font package at all
     * simply tests a handful of paths that hold no {@code fonts.dir} and ends with an empty list,
     * and the server is started exactly as it was before. A directory is only worth naming when
     * it has that index in it: the server reads the index, not the files. The name the process
     * shows is taken before the flag is prepended, so it stays the arguments the user asked for.
     */
    @NonNull
    private static String nixFontPathBlock(@NonNull String applicationId) {
        StringBuilder globs = new StringBuilder();
        for (String root : NIX_FONT_ROOTS) {
            for (String dir : NIX_FONT_DIRS) {
                globs.append(' ').append(PREFIX).append("/nix/store/*/").append(root).append('/')
                    .append(dir);
            }
        }
        return "TERMUX_X11_FONT_PATH=\"\"\n"
            + "for dir in" + globs + "; do\n"
            + "  case \"$dir\" in *.drv/*) continue ;; esac\n"
            + "  [ -f \"$dir/fonts.dir\" ] || continue\n"
            + "  TERMUX_X11_FONT_PATH=\"${TERMUX_X11_FONT_PATH:+$TERMUX_X11_FONT_PATH,}$dir\"\n"
            + "done\n"
            + "TERMUX_X11_NICE_NAME=\"termux-x11 " + applicationId + " $*\"\n"
            + "[ -z \"$TERMUX_X11_FONT_PATH\" ] || set -- -fp \"$TERMUX_X11_FONT_PATH\" \"$@\"\n";
    }

    /**
     * Upstream's {@code termux-x11-preference.in}, kept to its {@code app_process} path only. The
     * {@code am broadcast} fallback it uses below sdk 34 cannot reach a receiver that is not
     * exported, and the launcher's is not.
     */
    @NonNull
    static String preferenceScript(@NonNull String applicationId) {
        return preferenceScript(applicationId, BIN_DIR + "/bash");
    }

    /** {@link #preferenceScript(String)} with the shell spelled out; see the server script's. */
    @NonNull
    static String preferenceScript(@NonNull String applicationId, @NonNull String shell) {
        return "#!" + shell + "\n"
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
        return "#!" + prefixShellPath() + script.substring(newline);
    }

    /** What the launcher execs itself: {@code bash} everywhere but nix, Android's shell there. */
    @NonNull
    private String hostShellPath() {
        File prefixDir = binDir.getParentFile();
        return prefixDir == null ? new File(binDir, "bash").getPath()
            : NixProfile.hostShell(prefixDir).getPath();
    }

    /** What the user runs from inside: {@code bash} everywhere but nix, the prefix's sh there. */
    @NonNull
    private String prefixShellPath() {
        File prefixDir = binDir.getParentFile();
        return prefixDir == null ? new File(binDir, "bash").getPath()
            : NixProfile.prefixShell(prefixDir).getPath();
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
