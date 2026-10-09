package com.termux.app.launcher.widget.builtin;

import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * A text file in the user's home, kept current for the widgets that show it (Tasks, Scratchpad).
 *
 * <p>One source per file, shared by every widget on it: the first subscriber reads the file on
 * the widgets' background thread and starts watching its directory, later ones get the last read
 * at once, and the last one to leave stops the watch. A change on disk — an editor saving, a
 * script appending, the file deleted — is debounced and read again. Directory watches are shared
 * too (two files in {@code ~/notes} use one observer), because before Android 10 two observers on
 * one directory silently cancel each other. When the directory does not exist yet the nearest
 * existing parent is watched instead, so creating {@code ~/notes} from a terminal is noticed.</p>
 *
 * <p>Edits run on the background thread against a fresh read of the file and replace it
 * atomically (write {@code <file>.tmp}, rename over it), creating the folder and the file when
 * missing. Subscribers see the edit at once; reads that land while edits are still in flight are
 * dropped, so a slow write never flickers a checkbox back.</p>
 *
 * <p>Every method is called on the main thread.</p>
 */
final class MarkdownFileSource {
    /** Hears every new read of the file while subscribed. */
    interface Listener { void onMarkdownChanged(@NonNull Snapshot snapshot); }

    /** A change to the file's text; null leaves the file alone. Runs on the background thread. */
    interface Edit { @Nullable String apply(@NonNull String current); }

    /** What a read of the file found. */
    static final class Snapshot {
        @NonNull final String path;
        final boolean exists;
        @NonNull final String text;
        final long lastModified;
        /** The file was larger than a widget reads; edits still see the whole file. */
        final boolean truncated;

        Snapshot(@NonNull String path, boolean exists, @NonNull String text, long lastModified,
                 boolean truncated) {
            this.path = path; this.exists = exists; this.text = text;
            this.lastModified = lastModified; this.truncated = truncated;
        }

        boolean sameAs(@Nullable Snapshot other) {
            return other != null && path.equals(other.path) && exists == other.exists
                && lastModified == other.lastModified && truncated == other.truncated
                && text.equals(other.text);
        }
    }

    static final long DEBOUNCE_MS = 300L;
    /** What a widget reads for display: far more than a card can show. */
    private static final int READ_LIMIT = 256 * 1024;
    /** What an edit is willing to rewrite; a larger file is left alone. */
    private static final int EDIT_LIMIT = 4 * 1024 * 1024;
    private static final int WATCH_MASK = FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO
        | FileObserver.MOVED_FROM | FileObserver.CREATE | FileObserver.DELETE
        | FileObserver.DELETE_SELF | FileObserver.MOVE_SELF;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, MarkdownFileSource> SOURCES = new HashMap<>();
    private static final Map<String, DirectoryWatch> WATCHES = new HashMap<>();

    @NonNull private final BuiltinWidgetServices services;
    @NonNull private final String key;
    @NonNull final String path;
    @NonNull private final String fileName;
    private final List<Listener> listeners = new ArrayList<>();
    @Nullable private Snapshot latest;
    private boolean active;
    private int editsInFlight;
    @Nullable private DirectoryWatch watch;
    /** True when the watch is on the file's own directory, false when on a parent of it. */
    private boolean watchingOwnDirectory;
    private final Runnable debouncedReload = this::onDebouncedChange;

    private MarkdownFileSource(@NonNull BuiltinWidgetServices services, @NonNull String key,
                               @NonNull String path) {
        this.services = services;
        this.key = key;
        this.path = path;
        this.fileName = new File(path).getName();
    }

    // ----- subscription ---------------------------------------------------------------------

    /** The source for {@code path}, with {@code listener} subscribed. Pair with {@link #release}. */
    @MainThread @NonNull
    static MarkdownFileSource acquire(@NonNull BuiltinWidgetServices services, @NonNull String path,
                                      @NonNull Listener listener) {
        String key = System.identityHashCode(services) + ":" + path;
        MarkdownFileSource source = SOURCES.get(key);
        if (source == null) {
            source = new MarkdownFileSource(services, key, path);
            SOURCES.put(key, source);
        }
        if (!source.listeners.contains(listener)) source.listeners.add(listener);
        if (!source.active) {
            source.start();
        } else if (source.latest != null) {
            listener.onMarkdownChanged(source.latest);
        }
        return source;
    }

    @MainThread void release(@NonNull Listener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty() && active) {
            stop();
            SOURCES.remove(key);
        }
    }

    /** The last read, or null before the first one lands. */
    @Nullable Snapshot latest() { return latest; }

    private void start() {
        active = true;
        arm();
        reload();
    }

    private void stop() {
        active = false;
        MAIN.removeCallbacks(debouncedReload);
        disarm();
    }

    // ----- reading --------------------------------------------------------------------------

    /** Reads the file again; the result reaches the subscribers if it differs from the last. */
    void reload() {
        if (!active) return;
        submit(services, () -> {
            Snapshot read = read(path);
            MAIN.post(() -> deliver(read));
        });
    }

    private void deliver(@NonNull Snapshot read) {
        if (!active || editsInFlight > 0) return;
        if (read.sameAs(latest)) return;
        latest = read;
        dispatch(read);
    }

    private void dispatch(@NonNull Snapshot snapshot) {
        for (Listener listener : new ArrayList<>(listeners)) listener.onMarkdownChanged(snapshot);
    }

    @WorkerThread @NonNull
    private static Snapshot read(@NonNull String path) {
        File file = new File(path);
        if (!file.isFile()) return new Snapshot(path, false, "", 0L, false);
        long modified = file.lastModified();
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            boolean truncated = readUpTo(in, bytes, READ_LIMIT);
            return new Snapshot(path, true, new String(bytes.toByteArray(), StandardCharsets.UTF_8),
                modified, truncated);
        } catch (IOException | RuntimeException e) {
            return new Snapshot(path, false, "", 0L, false);
        }
    }

    /** Copies at most {@code limit} bytes; true when the stream had more. */
    @WorkerThread
    private static boolean readUpTo(@NonNull InputStream in, @NonNull ByteArrayOutputStream out,
                                    int limit) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        while (total < limit) {
            int n = in.read(buffer, 0, Math.min(buffer.length, limit - total));
            if (n < 0) return false;
            out.write(buffer, 0, n);
            total += n;
        }
        return in.read() >= 0;
    }

    // ----- editing --------------------------------------------------------------------------

    /**
     * Applies {@code edit} to the file. Subscribers see the result at once when the last read
     * holds the whole file; the file itself is rewritten on the background thread from a fresh
     * read, and read back once every pending edit has landed. {@code onFailure} runs on the main
     * thread when the file could not be written.
     */
    void edit(@NonNull Edit edit, @Nullable Runnable onFailure) {
        Snapshot base = latest;
        if (base != null && !base.truncated) {
            String optimistic = edit.apply(base.exists ? base.text : "");
            if (optimistic != null && !optimistic.equals(base.text)) {
                latest = new Snapshot(path, true, optimistic, base.lastModified, false);
                dispatch(latest);
            }
        }
        editsInFlight++;
        boolean queued = submit(services, () -> {
            boolean written = write(path, edit);
            MAIN.post(() -> {
                editsInFlight = Math.max(0, editsInFlight - 1);
                if (!written && onFailure != null) onFailure.run();
                if (editsInFlight == 0) reload();
            });
        });
        if (!queued) {
            editsInFlight = Math.max(0, editsInFlight - 1);
            if (onFailure != null) onFailure.run();
        }
    }

    /** {@link #edit} for a file nobody is watching at the moment. */
    static void editDetached(@NonNull BuiltinWidgetServices services, @NonNull String path,
                             @NonNull Edit edit, @Nullable Runnable onFailure) {
        boolean queued = submit(services, () -> {
            boolean written = write(path, edit);
            if (!written && onFailure != null) MAIN.post(onFailure);
        });
        if (!queued && onFailure != null) onFailure.run();
    }

    /** Rewrites {@code path} through {@code edit}; true unless the write failed. */
    @WorkerThread
    private static boolean write(@NonNull String path, @NonNull Edit edit) {
        File file = new File(path);
        File tmp = null;
        try {
            // Write through a symlink rather than replacing it with a plain file.
            if (file.exists()) file = file.getCanonicalFile();
            String current = "";
            if (file.isFile()) {
                if (file.length() > EDIT_LIMIT) return false;
                try (InputStream in = new FileInputStream(file)) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    if (readUpTo(in, bytes, EDIT_LIMIT)) return false;
                    current = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            String next = edit.apply(current);
            if (next == null || next.equals(current)) return true;
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return false;
            tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(next.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            tmp = null;
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (tmp != null) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private static boolean submit(@NonNull BuiltinWidgetServices services, @NonNull Runnable task) {
        try {
            services.io().execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            // The page is being torn down; nothing is left to show the result.
            return false;
        }
    }

    // ----- watching -------------------------------------------------------------------------

    private void arm() {
        File dir = new File(path).getParentFile();
        File target = dir;
        while (target != null && !target.isDirectory()) target = target.getParentFile();
        if (target == null) return;
        watchingOwnDirectory = target.equals(dir);
        String watched = target.getPath();
        DirectoryWatch next = WATCHES.get(watched);
        if (next == null) {
            next = new DirectoryWatch(watched);
            WATCHES.put(watched, next);
        }
        next.sources.add(this);
        watch = next;
    }

    private void disarm() {
        DirectoryWatch current = watch;
        watch = null;
        if (current == null) return;
        current.sources.remove(this);
        if (current.sources.isEmpty()) {
            current.close();
            WATCHES.remove(current.dir);
        }
    }

    /** Something changed in the watched directory; {@code name} is the entry, when known. */
    private void onDirectoryEvent(@Nullable String name) {
        if (!active) return;
        if (watchingOwnDirectory && name != null && !name.equals(fileName)) return;
        MAIN.removeCallbacks(debouncedReload);
        MAIN.postDelayed(debouncedReload, DEBOUNCE_MS);
    }

    private void onDebouncedChange() {
        if (!active) return;
        File dir = new File(path).getParentFile();
        boolean ownExists = dir != null && dir.isDirectory();
        // The folder appeared (watch it now) or went away (fall back to a parent).
        if (ownExists != watchingOwnDirectory) {
            disarm();
            arm();
        }
        reload();
    }

    /** One inotify watch on a directory, shared by every source whose file lives under it. */
    private static final class DirectoryWatch {
        @NonNull final String dir;
        final List<MarkdownFileSource> sources = new ArrayList<>();
        @NonNull private final FileObserver observer;

        @SuppressWarnings("deprecation")
        DirectoryWatch(@NonNull String dir) {
            this.dir = dir;
            observer = new FileObserver(dir, WATCH_MASK) {
                @Override public void onEvent(int event, @Nullable String name) {
                    if ((event & WATCH_MASK) == 0) return;
                    MAIN.post(() -> {
                        for (MarkdownFileSource source : new ArrayList<>(sources)) {
                            source.onDirectoryEvent(name);
                        }
                    });
                }
            };
            observer.startWatching();
        }

        void close() { observer.stopWatching(); }
    }
}
