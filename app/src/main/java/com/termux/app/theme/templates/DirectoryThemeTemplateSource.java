package com.termux.app.theme.templates;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Template directories read straight off the filesystem: the user's own, and every test fixture. */
public final class DirectoryThemeTemplateSource implements ThemeTemplateSource {

    private final File mRoot;

    private final boolean mBuiltIn;

    public DirectoryThemeTemplateSource(File root) {
        this(root, false);
    }

    public DirectoryThemeTemplateSource(File root, boolean builtIn) {
        mRoot = root;
        mBuiltIn = builtIn;
    }

    @Override
    public boolean isBuiltIn() {
        return mBuiltIn;
    }

    @Override
    public List<String> ids() {
        File[] children = mRoot == null ? null : mRoot.listFiles();
        if (children == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory() && new File(child, ThemeTemplate.MANIFEST_NAME).isFile())
                ids.add(child.getName());
        }
        Collections.sort(ids);
        return ids;
    }

    @Override
    public boolean has(String id) {
        return id != null && !id.isEmpty()
            && new File(new File(mRoot, id), ThemeTemplate.MANIFEST_NAME).isFile();
    }

    @Override
    public Properties manifest(String id) throws IOException {
        Properties manifest = new Properties();
        try (InputStream in = new FileInputStream(new File(new File(mRoot, id), ThemeTemplate.MANIFEST_NAME))) {
            manifest.load(in);
        }
        return manifest;
    }

    @Override
    public String readText(String id, String relativePath) throws IOException {
        File file = new File(new File(mRoot, id), relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Override
    public File directory(String id) {
        return new File(mRoot, id);
    }

    @Override
    public File plannedDirectory(String id) {
        return new File(mRoot, id);
    }
}
