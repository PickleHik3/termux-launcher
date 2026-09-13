package com.termux.app.theme.templates;

import android.content.res.AssetManager;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * The templates shipped inside the APK, under {@code assets/theme-templates/<id>/}.
 *
 * <p>Reading a manifest or an input file goes straight to the asset; a hook cannot, since
 * {@code bash} needs a path. {@link #directory(String)} therefore extracts the whole template
 * directory to {@code $PREFIX/libexec/termux-launcher/theme-templates/<id>/}, writing only the files
 * whose bytes actually differ — the same restraint the exported palette files are written with, so
 * an unchanged app update does not churn files a tool may be watching.
 */
public final class AssetThemeTemplateSource implements ThemeTemplateSource {

    private final AssetManager mAssets;

    private final String mAssetRoot;

    private final File mExtractRoot;

    public AssetThemeTemplateSource(@NonNull AssetManager assets, @NonNull String assetRoot,
                                    @NonNull File extractRoot) {
        mAssets = assets;
        mAssetRoot = assetRoot;
        mExtractRoot = extractRoot;
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public List<String> ids() {
        String[] children;
        try {
            children = mAssets.list(mAssetRoot);
        } catch (IOException e) {
            return Collections.emptyList();
        }
        if (children == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (String child : children) {
            if (listOf(mAssetRoot + "/" + child).contains(ThemeTemplate.MANIFEST_NAME)) ids.add(child);
        }
        Collections.sort(ids);
        return ids;
    }

    @Override
    public boolean has(String id) {
        return id != null && !id.isEmpty()
            && listOf(mAssetRoot + "/" + id).contains(ThemeTemplate.MANIFEST_NAME);
    }

    @Override
    public Properties manifest(String id) throws IOException {
        Properties manifest = new Properties();
        try (InputStream in = mAssets.open(assetPath(id, ThemeTemplate.MANIFEST_NAME))) {
            manifest.load(in);
        }
        return manifest;
    }

    @Override
    public String readText(String id, String relativePath) throws IOException {
        try (InputStream in = mAssets.open(assetPath(id, relativePath))) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    @Override
    public File directory(String id) throws IOException {
        File directory = new File(mExtractRoot, id);
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Cannot create " + directory);
        extractInto(mAssetRoot + "/" + id, directory);
        return directory;
    }

    private void extractInto(String assetDirectory, File target) throws IOException {
        for (String child : listOf(assetDirectory)) {
            String childAsset = assetDirectory + "/" + child;
            List<String> grandChildren = listOf(childAsset);
            File childFile = new File(target, child);
            if (!grandChildren.isEmpty()) {
                if (!childFile.isDirectory() && !childFile.mkdirs())
                    throw new IOException("Cannot create " + childFile);
                extractInto(childAsset, childFile);
                continue;
            }
            byte[] wanted;
            try (InputStream in = mAssets.open(childAsset)) {
                wanted = readAll(in);
            }
            if (sameOnDisk(childFile, wanted)) continue;
            try (FileOutputStream out = new FileOutputStream(childFile)) {
                out.write(wanted);
            }
            // Hooks are run as `bash <hook>`, so the bit is not needed to execute them; it is set
            // anyway so the same directory can be run by hand while writing a template.
            //noinspection ResultOfMethodCallIgnored
            childFile.setExecutable(child.endsWith(".sh"), true);
        }
    }

    private static boolean sameOnDisk(File file, byte[] wanted) {
        if (!file.isFile() || file.length() != wanted.length) return false;
        try {
            return Arrays.equals(Files.readAllBytes(file.toPath()), wanted);
        } catch (IOException e) {
            return false;
        }
    }

    private List<String> listOf(String assetPath) {
        try {
            String[] children = mAssets.list(assetPath);
            return children == null ? Collections.emptyList() : Arrays.asList(children);
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private String assetPath(String id, String relativePath) {
        return mAssetRoot + "/" + id + "/" + relativePath;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }
}
