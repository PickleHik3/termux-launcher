package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The last {@link #MAX} photos applied as a wallpaper, kept as copies in one private directory
 * (lock-live-wallpaper.md, "Photos"): {@code files/wallpaper/recent/<timestamp>.png}, the cropped
 * wide picture. Newest first, deduplicated by content (SHA-256), trimmed to {@link #MAX}.
 *
 * <p>Plain Java over {@link File}, no Android, so it is a unit test. The order lives in a small
 * index file ({@code index}, one {@code <file name> <hash>} line per entry, newest first); an entry
 * whose file has gone is skipped when listing and dropped on the next write. Blocking file work:
 * call it off the main thread, except {@link #list}, which reads only the index.</p>
 */
public final class RecentWallpapers {

    /** How many photos are kept. */
    public static final int MAX = 3;

    static final String INDEX = "index";

    @NonNull private final File mDir;

    public RecentWallpapers(@NonNull File dir) {
        mDir = dir;
    }

    @NonNull
    public File directory() {
        return mDir;
    }

    /** The kept photos, newest first; entries whose file is missing or empty are skipped. */
    @NonNull
    public synchronized List<File> list() {
        List<File> out = new ArrayList<>();
        for (Entry e : readIndex()) {
            File f = new File(mDir, e.name);
            if (f.isFile() && f.length() > 0) out.add(f);
        }
        return out;
    }

    /**
     * Keeps a copy of {@code source} as the newest photo. A photo already kept (the same bytes)
     * moves to the front instead of being copied again. Older photos past {@link #MAX} are
     * deleted.
     *
     * @return the kept copy
     */
    @NonNull
    public synchronized File add(@NonNull File source, long nowMs) throws IOException {
        if (!source.isFile()) throw new IOException("No such file: " + source);
        String hash = sha256(source);
        List<Entry> entries = live(readIndex());
        Entry keep = null;
        for (Entry e : entries) {
            if (e.hash.equals(hash)) {
                keep = e;
                break;
            }
        }
        if (keep != null) {
            entries.remove(keep);
        } else {
            if (!mDir.isDirectory() && !mDir.mkdirs()) throw new IOException("Cannot create " + mDir);
            long stamp = nowMs;
            File target = new File(mDir, stamp + ".png");
            while (target.exists()) target = new File(mDir, (++stamp) + ".png");
            copy(source, target);
            keep = new Entry(target.getName(), hash);
        }
        entries.add(0, keep);
        while (entries.size() > MAX) {
            Entry gone = entries.remove(entries.size() - 1);
            //noinspection ResultOfMethodCallIgnored
            new File(mDir, gone.name).delete();
        }
        writeIndex(entries);
        sweep(entries);
        return new File(mDir, keep.name);
    }

    // --- internals ---

    private static final class Entry {
        @NonNull final String name;
        @NonNull final String hash;

        Entry(@NonNull String name, @NonNull String hash) {
            this.name = name;
            this.hash = hash;
        }
    }

    @NonNull
    private List<Entry> live(@NonNull List<Entry> entries) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            File f = new File(mDir, e.name);
            if (f.isFile() && f.length() > 0) out.add(e);
        }
        return out;
    }

    @NonNull
    private List<Entry> readIndex() {
        List<Entry> out = new ArrayList<>();
        File index = new File(mDir, INDEX);
        if (!index.isFile()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(index),
            StandardCharsets.UTF_8))) {
            String line;
            Set<String> seen = new HashSet<>();
            while ((line = r.readLine()) != null) {
                Entry e = parse(line);
                if (e != null && seen.add(e.name)) out.add(e);
            }
        } catch (IOException ignored) {
            // An unreadable index lists nothing; the next add rewrites it.
        }
        return out;
    }

    @Nullable
    private static Entry parse(@NonNull String line) {
        String t = line.trim();
        int space = t.indexOf(' ');
        if (space <= 0 || space == t.length() - 1) return null;
        String name = t.substring(0, space);
        // Names are ours (<digits>.png): never a path.
        if (name.contains("/") || name.contains("\\") || name.startsWith(".") || name.equals(INDEX)) return null;
        return new Entry(name, t.substring(space + 1).trim());
    }

    private void writeIndex(@NonNull List<Entry> entries) throws IOException {
        File index = new File(mDir, INDEX);
        File tmp = new File(mDir, INDEX + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp, false), StandardCharsets.UTF_8)) {
            for (Entry e : entries) w.write(e.name + " " + e.hash + "\n");
        }
        if (!tmp.renameTo(index)) {
            //noinspection ResultOfMethodCallIgnored
            index.delete();
            if (!tmp.renameTo(index)) throw new IOException("Cannot write " + index);
        }
    }

    /** Deletes files in the directory that no entry names (a crash between copy and index). */
    private void sweep(@NonNull List<Entry> entries) {
        File[] files = mDir.listFiles();
        if (files == null) return;
        Set<String> names = new HashSet<>();
        for (Entry e : entries) names.add(e.name);
        for (File f : files) {
            String n = f.getName();
            if (n.equals(INDEX) || names.contains(n)) continue;
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** Copies {@code from} to {@code to}, replacing it. */
    static void copy(@NonNull File from, @NonNull File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }
    }

    @NonNull
    static String sha256(@NonNull File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}
