package com.termux.app;

import android.content.Context;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Installs the app-managed OSC 133 integration scripts without changing user shell rc files. */
final class TermuxShellIntegrationInstaller {

    private static final String LOG_TAG = "TermuxShellIntegration";
    private static final String ASSET_DIRECTORY = "shell-integration";
    private static final String[] SCRIPT_NAMES = {
        "termux-launcher.bash",
        "termux-launcher.zsh"
    };

    /** Set once an install has succeeded in this process; later calls have nothing to do. */
    private static final AtomicBoolean sInstalled = new AtomicBoolean();

    private TermuxShellIntegrationInstaller() {}

    /**
     * Installs the scripts once per process, on the installer thread. The application and the
     * activity's bootstrap callback both ask; whichever runs second finds the work done. Nothing on
     * the main thread reads these files: shells source them, and every write is a rename, so a
     * shell starting mid-install reads either the old script or the new one, never half of one.
     */
    static void ensureInstalled(Context context) {
        if (sInstalled.get()) return;
        Context appContext = context.getApplicationContext();
        TermuxApplication.installerExecutor().execute(() -> {
            if (sInstalled.get()) return;
            File destination = new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH, "shell-integration");
            try {
                install(appContext, destination);
                sInstalled.set(true);
            } catch (IOException e) {
                Logger.logErrorExtended(LOG_TAG, "Failed to install shell integration scripts: " + e.getMessage());
            }
        });
    }

    /** Visible to tests so installation can be exercised outside the fixed Termux home path. */
    static int install(Context context, File destination) throws IOException {
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Failed to create shell integration directory: " + destination);
        }
        if (!destination.isDirectory()) {
            throw new IOException("Shell integration path is not a directory: " + destination);
        }

        restrictDirectoryToOwner(destination);
        int updated = 0;
        for (String scriptName : SCRIPT_NAMES) {
            byte[] content;
            try (InputStream input = context.getAssets().open(ASSET_DIRECTORY + "/" + scriptName)) {
                content = readAllBytes(input);
            }

            File script = new File(destination, scriptName);
            if (!hasContent(script, content)) {
                writeAtomically(script, content);
                updated++;
            }
            restrictFileToOwner(script);
        }
        return updated;
    }

    /** Writes beside the target and renames over it, so a reader never sees a partial script. */
    private static void writeAtomically(File file, byte[] content) throws IOException {
        File temporary = new File(file.getParentFile(), "." + file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(content);
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IOException("Failed to replace shell integration script: " + file);
        }
    }

    private static boolean hasContent(File file, byte[] expected) throws IOException {
        if (!file.isFile() || file.length() != expected.length) {
            return false;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            return Arrays.equals(readAllBytes(input), expected);
        }
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void restrictDirectoryToOwner(File directory) throws IOException {
        directory.setReadable(false, false);
        directory.setWritable(false, false);
        directory.setExecutable(false, false);
        if (!directory.setReadable(true, true) || !directory.setWritable(true, true)
            || !directory.setExecutable(true, true)) {
            throw new IOException("Failed to set permissions on directory: " + directory);
        }
    }

    private static void restrictFileToOwner(File file) throws IOException {
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        if (!file.setReadable(true, true) || !file.setWritable(true, true)) {
            throw new IOException("Failed to set permissions on file: " + file);
        }
    }
}
