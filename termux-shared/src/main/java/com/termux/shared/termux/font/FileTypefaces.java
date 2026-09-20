package com.termux.shared.termux.font;

import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-lifetime cache of typefaces loaded from files.
 *
 * <p>{@link Typeface#createFromFile} maps the file into a Java buffer and hands the native font a
 * global reference to it. That reference is released on whichever thread drops the last native
 * owner of the face, and once text has been drawn with it the last owner is often the
 * RenderThread's glyph strike cache: a purge there destroys the face and re-enters the runtime
 * through JNI while still holding the strike-cache lock. Seen on 2026-09-07 as an ANR: that JNI
 * call waited for a GC pause, the GC pause waited for the main thread, and the main thread waited
 * for the strike-cache lock inside {@code Paint.descent()} while drawing a TextView.
 *
 * <p>Keeping every file-loaded face alive for the life of the process removes that leg: the strike
 * cache may drop its strikes, but it is never the last owner of a face. Fonts are few and a mapped
 * font costs no Java heap, so the cache never evicts. A file that changes on disk gets a new entry
 * keyed by its size and modification time, and the old face stays alive beside it.
 *
 * <p>Semantics otherwise match {@code createFromFile}: whatever it returns, including
 * {@link Typeface#DEFAULT} for bytes Android cannot parse, is returned and cached; whatever it
 * throws propagates. Callers keep their own checks.
 */
public final class FileTypefaces {

    private static final Map<String, Typeface> CACHE = new ConcurrentHashMap<>();

    private FileTypefaces() {
    }

    /** The face for {@code file}, loading it on first sight and for every change of the file. */
    @Nullable
    public static Typeface load(@NonNull File file) {
        String key = key(file);
        Typeface cached = CACHE.get(key);
        if (cached != null) return cached;
        Typeface loaded = Typeface.createFromFile(file);
        if (loaded == null) return null;
        // Two threads loading the same file at once both parse it; the loser's face is never drawn
        // with, so it dies through the finalizer and never reaches the strike cache.
        Typeface previous = CACHE.putIfAbsent(key, loaded);
        return previous != null ? previous : loaded;
    }

    @NonNull
    private static String key(@NonNull File file) {
        return file.getAbsolutePath() + '\0' + file.length() + '\0' + file.lastModified();
    }

    @VisibleForTesting
    static int size() {
        return CACHE.size();
    }

    @VisibleForTesting
    static void clear() {
        CACHE.clear();
    }
}
