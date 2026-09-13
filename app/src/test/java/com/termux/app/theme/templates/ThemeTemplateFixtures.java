package com.termux.app.theme.templates;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/** Builds template directories on disk, the way a user or the APK extraction would leave them. */
final class ThemeTemplateFixtures {

    private ThemeTemplateFixtures() {
    }

    static Properties palette() {
        Properties palette = new Properties();
        palette.setProperty("primary", "#4080C0");
        palette.setProperty("surface", "#101418");
        palette.setProperty("mode", "dark");
        return palette;
    }

    /** A template whose input renders to the primary colour, with both hooks declared. */
    static File template(File root, String id, String outputPath) throws IOException {
        return template(root, id, outputPath, "primary = \"{{ colors.primary.dark.hex }}\"\n");
    }

    static File template(File root, String id, String outputPath, String input) throws IOException {
        File directory = new File(root, id);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("mkdirs " + directory);
        write(new File(directory, "input.txt"), input);
        write(new File(directory, "apply.sh"), "#!/bin/bash\n");
        write(new File(directory, "undo.sh"), "#!/bin/bash\n");
        write(new File(directory, ThemeTemplate.MANIFEST_NAME),
            "name=" + id.substring(0, 1).toUpperCase() + id.substring(1) + "\n"
                + "summary=Colours for " + id + "\n"
                + "input=input.txt\n"
                + "output=" + outputPath + "\n"
                + "post_hook=apply.sh\n"
                + "undo_hook=undo.sh\n");
        return directory;
    }

    static void write(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("mkdirs " + parent);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
