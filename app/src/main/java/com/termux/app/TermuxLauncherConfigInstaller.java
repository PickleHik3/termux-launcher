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

/**
 * Ships the launcher's user-editable configuration files into {@code ~/.termux}.
 *
 * <p>Two things happen, and they are deliberately different. Reference copies of every
 * example land in {@code ~/.termux/launcher/examples} and are refreshed whenever their
 * content drifts from the assets, so the documentation a user reads always matches the
 * app they are running. The live {@code .conf} files are only ever <em>seeded</em>: they
 * are written when absent and never touched again, so an app update cannot overwrite
 * hand-edited bindings or font settings.
 *
 * <p>The seeded files ship almost entirely commented out, so seeding one changes close to
 * nothing: an all-comment file parses to zero mappings, zero faces, and zero properties,
 * which is what an absent file already meant. The one live line —
 * {@code terminal-onclick-url-open = true} in the properties file — is this fork's default
 * for fresh installs only, since a present file is never touched again.
 * {@code keyboard/layout.xml} is not seeded at all, because a present layout file replaces
 * the bundled keyboard layout outright.
 */
final class TermuxLauncherConfigInstaller {

    private static final String LOG_TAG = "TermuxLauncherConfig";
    private static final String ASSET_DIRECTORY = "launcher-examples";

    /** Relative to {@code ~/.termux}. */
    static final String EXAMPLES_RELATIVE_PATH = "launcher/examples";

    private static final String PROPERTIES_FILE_NAME = "termux.properties";

    /**
     * The other path Termux reads properties from, relative to {@code ~}. See
     * {@link TermuxConstants#TERMUX_PROPERTIES_SECONDARY_FILE_PATH}.
     */
    private static final String SECONDARY_PROPERTIES_RELATIVE_PATH =
        ".config/termux/" + PROPERTIES_FILE_NAME;

    private static final String[] EXAMPLE_NAMES = {
        "README.md",
        "termux-launcher-bindings.conf",
        "fonts.conf",
        "keyboard-layout.xml",
        PROPERTIES_FILE_NAME
    };

    /** Asset name to live file name, relative to {@code ~/.termux}. */
    private static final String[][] SEEDED_FILES = {
        {"termux-launcher-bindings.conf", "termux-launcher-bindings.conf"},
        {"fonts.conf", "fonts.conf"},
        {PROPERTIES_FILE_NAME, PROPERTIES_FILE_NAME}
    };

    private TermuxLauncherConfigInstaller() {}

    static void ensureInstalled(Context context) {
        try {
            install(context, new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH));
        } catch (IOException e) {
            Logger.logErrorExtended(LOG_TAG,
                "Failed to install launcher configuration files: " + e.getMessage());
        }
    }

    /**
     * Refreshes the examples and seeds any missing live file.
     *
     * <p>Visible to tests so installation can be exercised outside the fixed Termux home
     * path. Returns the number of files written.
     */
    static int install(Context context, File termuxDataHome) throws IOException {
        int written = 0;
        File examples = new File(termuxDataHome, EXAMPLES_RELATIVE_PATH);
        ensureDirectory(examples);
        for (String name : EXAMPLE_NAMES) {
            byte[] content = readAsset(context, name);
            File target = new File(examples, name);
            if (!hasContent(target, content)) {
                writeFile(target, content);
                written++;
            }
            restrictFileToOwner(target);
        }

        // Only create ~/.termux when it is missing; its existing permissions are the
        // user's business, not ours.
        if (!termuxDataHome.isDirectory() && !termuxDataHome.mkdirs()) {
            throw new IOException("Failed to create directory: " + termuxDataHome);
        }
        for (String[] seed : SEEDED_FILES) {
            File target = new File(termuxDataHome, seed[1]);
            if (target.exists() || shadowsExistingProperties(termuxDataHome, seed[1])) continue;
            writeFile(target, readAsset(context, seed[0]));
            restrictFileToOwner(target);
            written++;
        }
        return written;
    }

    /**
     * Whether seeding {@code name} would hide a file the user already relies on.
     *
     * <p>Termux reads the first readable file in
     * {@link TermuxConstants#TERMUX_PROPERTIES_FILE_PATHS_LIST} and ignores the rest, so
     * writing an all-comment {@code ~/.termux/termux.properties} would silently disable a
     * {@code ~/.config/termux/termux.properties} the user keeps there. Nothing else this
     * class seeds has a second location.
     */
    private static boolean shadowsExistingProperties(File termuxDataHome, String name) {
        if (!PROPERTIES_FILE_NAME.equals(name)) return false;
        File home = termuxDataHome.getParentFile();
        return home != null && new File(home, SECONDARY_PROPERTIES_RELATIVE_PATH).isFile();
    }

    private static byte[] readAsset(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(ASSET_DIRECTORY + "/" + name)) {
            return readAllBytes(input);
        }
    }

    private static void writeFile(File file, byte[] content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(content);
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory);
        }
        if (!directory.isDirectory()) {
            throw new IOException("Path is not a directory: " + directory);
        }
        restrictDirectoryToOwner(directory);
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
