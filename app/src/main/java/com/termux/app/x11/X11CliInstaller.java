package com.termux.app.x11;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
 * a package manager.
 */
public final class X11CliInstaller {

    private static final String LOG_TAG = "X11CliInstaller";

    /** Bumped whenever the written files change, so an upgrade rewrites them once. */
    private static final int VERSION = 1;

    private static final String PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
    private static final String BIN_DIR = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    private static final String LIBEXEC_DIR = PREFIX + "/libexec/termux-launcher/x11";

    static final String SERVER_SCRIPT_PATH = BIN_DIR + "/termux-x11";
    static final String PREFERENCE_SCRIPT_PATH = BIN_DIR + "/termux-x11-preference";
    static final String LOADER_PATH = LIBEXEC_DIR + "/loader.apk";
    private static final String MARKER_PATH = LIBEXEC_DIR + "/.installed";

    /** Every marker this launcher has ever written, so "is this ours?" survives an upgrade. */
    private static final String MARKER_PREAMBLE = "# written by termux-launcher";

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

    private X11CliInstaller() {}

    /**
     * Write the scripts and the loader if they are missing or out of date.
     *
     * <p>Safe to call on every app start: it stats a marker file and returns.
     */
    @NonNull
    public static Result install(@NonNull Context context) {
        if (!new File(BIN_DIR).isDirectory()) return Result.NO_PREFIX;
        String applicationId = context.getPackageName();
        String marker = MARKER_PREAMBLE + " v" + VERSION + " " + applicationId + "\n";
        if (marker.equals(read(MARKER_PATH))) return Result.UP_TO_DATE;
        if (isForeignCommand()) {
            Logger.logInfo(LOG_TAG, "Leaving a termux-x11 we did not write alone");
            return Result.FOREIGN_COMMAND;
        }
        try {
            File libexec = new File(LIBEXEC_DIR);
            if (!libexec.isDirectory() && !libexec.mkdirs()) {
                throw new IOException("Failed to create " + LIBEXEC_DIR);
            }
            copyAsset(context, "x11/loader.apk", LOADER_PATH);
            writeExecutable(SERVER_SCRIPT_PATH, serverScript(applicationId));
            writeExecutable(PREFERENCE_SCRIPT_PATH, preferenceScript(applicationId));
            write(MARKER_PATH, marker);
            return Result.INSTALLED;
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install the X11 commands: " + e.getMessage());
            return Result.FAILED;
        }
    }

    /** Take the commands back out, for a user who switches the display off. */
    public static void uninstall() {
        if (isForeignCommand()) return;
        for (String path : new String[]{SERVER_SCRIPT_PATH, PREFERENCE_SCRIPT_PATH, LOADER_PATH,
                MARKER_PATH}) {
            File file = new File(path);
            if (file.exists() && !file.delete()) {
                Logger.logWarn(LOG_TAG, "Failed to remove " + path);
            }
        }
    }

    /**
     * True while a {@code termux-x11} that is not ours sits in the prefix. Ours always carries
     * the marker comment in its first lines; the apt package's does not.
     */
    public static boolean isForeignCommand() {
        File script = new File(SERVER_SCRIPT_PATH);
        if (!script.exists()) return false;
        String content = read(SERVER_SCRIPT_PATH);
        return content == null || !content.contains(MARKER_PREAMBLE);
    }

    /** True once this launcher's own commands are in place. */
    public static boolean isInstalled() {
        return new File(SERVER_SCRIPT_PATH).exists() && !isForeignCommand();
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

    private static void copyAsset(@NonNull Context context, @NonNull String asset,
                                  @NonNull String path) throws IOException {
        File file = new File(path);
        try (InputStream in = context.getAssets().open(asset);
             OutputStream out = new FileOutputStream(file, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
        file.setReadable(true, false);
    }

    private static void write(@NonNull String path, @NonNull String content) throws IOException {
        File file = new File(path);
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    private static void writeExecutable(@NonNull String path, @NonNull String content)
            throws IOException {
        write(path, content);
        File file = new File(path);
        file.setExecutable(true, false);
        file.setReadable(true, false);
    }

    @Nullable
    private static String read(@NonNull String path) {
        File file = new File(path);
        if (!file.isFile() || file.length() > 64 * 1024) return null;
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
