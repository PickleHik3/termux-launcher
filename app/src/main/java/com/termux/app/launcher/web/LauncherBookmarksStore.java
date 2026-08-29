package com.termux.app.launcher.web;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The user's bookmarks, read from {@code ~/.termux/bookmarks.txt}.
 *
 * <p>A file rather than a settings screen, because it is the one place a Termux user can edit
 * their bookmarks the way they edit everything else: with an editor, from a shell, under version
 * control. The format is one bookmark per line, a name and a URL separated by a tab or by two or
 * more spaces, {@code #} starting a comment. A line that is only a URL is a bookmark named after
 * its host.
 *
 * <p>Reading is off the main thread and cached against the file's size and modification time, so
 * an unchanged file costs a stat. Callers ask for {@link #snapshot()} — which is whatever was
 * last read, possibly empty — and call {@link #refreshAsync} to have it caught up.
 */
public final class LauncherBookmarksStore {

    private static final String LOG_TAG = "LauncherBookmarks";
    private static final String RELATIVE_PATH = "bookmarks.txt";

    /** Caps mirroring the bindings config: enough for any hand-written file, bounded for us. */
    static final long MAX_BYTES = 128L * 1024L;
    static final int MAX_LINES = 2048;
    static final int MAX_BOOKMARKS = 512;

    @Nullable
    private static volatile LauncherBookmarksStore sInstance;

    /** One bookmark. {@code url} is always an absolute http(s) URL. */
    public static final class Bookmark {
        public final String name;
        public final String url;

        Bookmark(@NonNull String name, @NonNull String url) {
            this.name = name;
            this.url = url;
        }
    }

    private final File file;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @NonNull
    private volatile List<Bookmark> bookmarks = Collections.emptyList();
    private long loadedSize = -1L;
    private long loadedModified = -1L;
    private boolean loading;

    private LauncherBookmarksStore(@NonNull File file) {
        this.file = file;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "termux-bookmarks");
            thread.setDaemon(true);
            return thread;
        });
    }

    @NonNull
    public static LauncherBookmarksStore getInstance(@NonNull Context context) {
        LauncherBookmarksStore instance = sInstance;
        if (instance != null) return instance;
        synchronized (LauncherBookmarksStore.class) {
            if (sInstance == null) {
                sInstance = new LauncherBookmarksStore(new File(
                    TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/" + RELATIVE_PATH));
            }
            return sInstance;
        }
    }

    /** Visible for tests, which need a store over a temporary file. */
    @NonNull
    static LauncherBookmarksStore forFile(@NonNull File file) {
        return new LauncherBookmarksStore(file);
    }

    /** What was last read. Never null, empty until the first read finishes. */
    @NonNull
    public List<Bookmark> snapshot() {
        return bookmarks;
    }

    /**
     * Re-reads the file when it has moved, calling {@code onChanged} on the main thread only if
     * the bookmarks actually differ — so a palette that is already open redraws once, not on
     * every keystroke that triggers a refresh.
     */
    public void refreshAsync(@Nullable Runnable onChanged) {
        synchronized (this) {
            if (loading) return;
            loading = true;
        }
        executor.execute(() -> {
            List<Bookmark> parsed = null;
            try {
                parsed = readIfChanged();
            } catch (IOException e) {
                Logger.logWarn(LOG_TAG, "Could not read " + file.getName() + ": " + e.getMessage());
            } finally {
                synchronized (this) {
                    loading = false;
                }
            }
            if (parsed == null) return;
            bookmarks = parsed;
            if (onChanged != null) mainHandler.post(onChanged);
        });
    }

    /** Null when nothing changed, so callers can skip the redraw. */
    @Nullable
    private List<Bookmark> readIfChanged() throws IOException {
        boolean exists = file.isFile() && file.canRead();
        long size = exists ? file.length() : -1L;
        long modified = exists ? file.lastModified() : -1L;
        synchronized (this) {
            if (size == loadedSize && modified == loadedModified) return null;
            loadedSize = size;
            loadedModified = modified;
        }
        if (!exists) return Collections.emptyList();
        if (size > MAX_BYTES) {
            Logger.logWarn(LOG_TAG, file.getName() + " exceeds " + MAX_BYTES + " bytes; ignored");
            return Collections.emptyList();
        }
        return parse(readBounded(file));
    }

    @NonNull
    private static String readBounded(@NonNull File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                 (int) Math.min(Math.max(file.length(), 1L), 32L * 1024L))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES)
                    throw new IOException("bookmarks file grew past " + MAX_BYTES + " bytes");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Parses the file's text. Bad lines are skipped rather than failing the file: a typo in one
     * bookmark must not cost the user the other forty.
     */
    @NonNull
    static List<Bookmark> parse(@NonNull String text) {
        List<Bookmark> parsed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String[] lines = text.split("\n", MAX_LINES + 1);
        int count = Math.min(lines.length, MAX_LINES);
        for (int i = 0; i < count; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String name;
            String target;
            int split = splitIndex(line);
            if (split < 0) {
                // No separator run: a single space still reads as "name url" to anyone typing
                // it, so accept that shape when what follows the last space is an address.
                int lastSpace = line.lastIndexOf(' ');
                String tail = lastSpace < 0 ? "" : line.substring(lastSpace + 1);
                if (LauncherWebLinks.looksLikeUrl(tail)) {
                    name = line.substring(0, lastSpace).trim();
                    target = tail;
                } else {
                    name = "";
                    target = line;
                }
            } else {
                name = line.substring(0, split).trim();
                target = line.substring(split).trim();
            }
            String url = LauncherWebLinks.normalizeUrl(target);
            if (url == null) continue;
            if (!seen.add(url.toLowerCase(Locale.US))) continue;
            parsed.add(new Bookmark(name.isEmpty() ? LauncherWebLinks.labelFor(url) : name, url));
            if (parsed.size() == MAX_BOOKMARKS) break;
        }
        return Collections.unmodifiableList(parsed);
    }

    /**
     * Where the name ends and the URL begins: a tab, or a run of two or more spaces. One space
     * is not a separator, so a bookmark can be named {@code Nix packages} without quoting.
     */
    private static int splitIndex(@NonNull String line) {
        int tab = line.indexOf('\t');
        if (tab >= 0) return tab;
        int spaces = line.indexOf("  ");
        return spaces >= 0 ? spaces : -1;
    }
}
