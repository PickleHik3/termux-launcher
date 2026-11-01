package com.termux.app.place.layout;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * The {@code layouts} file (ADR 0020 §5): five JSON documents read once into an immutable
 * {@link Layouts}, each written whole. {@link #update} runs on the main thread only, so two writers
 * in one frame cannot lose one another's change, and tells its listeners once: the one redress
 * request of ADR 0018 §2.
 */
public final class LayoutStore {

    /** The SharedPreferences file the five documents live in. */
    public static final String PREFS = "layouts";

    /** Where the documents are kept; the app's file, or a map in a test. */
    public interface Storage {
        @Nullable String read(@NonNull String key);
        /** Writes every entry in one go. */
        void write(@NonNull Map<String, String> documents);
    }

    /** Told after a document changed: redress once, reading {@link #current()}. */
    public interface Listener {
        void onLayoutsChanged(@NonNull Layouts layouts);
    }

    /** {@link SharedPreferences} as a {@link Storage}. */
    public static final class PrefsStorage implements Storage {
        @NonNull private final SharedPreferences mPrefs;

        public PrefsStorage(@NonNull SharedPreferences prefs) {
            mPrefs = prefs;
        }

        @Nullable @Override public String read(@NonNull String key) {
            return mPrefs.getString(key, null);
        }

        @Override public void write(@NonNull Map<String, String> documents) {
            SharedPreferences.Editor editor = mPrefs.edit();
            for (Map.Entry<String, String> e : documents.entrySet())
                editor.putString(e.getKey(), e.getValue());
            editor.apply();
        }
    }

    @NonNull
    public static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull private final Storage mStorage;
    private final boolean mMainThreadOnly;
    @NonNull private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();
    @NonNull private volatile Layouts mCurrent;

    public LayoutStore(@NonNull Storage storage, boolean mainThreadOnly) {
        mStorage = storage;
        mMainThreadOnly = mainThreadOnly;
        mCurrent = load();
    }

    /** The app's store, over the {@code layouts} file. */
    @NonNull
    public static LayoutStore open(@NonNull Context context) {
        return new LayoutStore(new PrefsStorage(prefs(context)), true);
    }

    /** Reads the five documents afresh. */
    @NonNull
    public Layouts load() {
        Layouts loaded = LayoutsJson.read(new LayoutsJson.Source() {
            @Nullable @Override public String read(@NonNull String key) {
                return mStorage.read(key);
            }

            @Override public void warn(@NonNull String message) {
                com.termux.shared.logger.Logger.logWarn("LayoutStore", message);
            }
        });
        mCurrent = loaded;
        return loaded;
    }

    /** Every layout as it stands; take it per pass, never across a redress. */
    @NonNull
    public Layouts current() {
        return mCurrent;
    }

    /** Changes one kind's document, writes it whole, and asks for one redress. */
    public void update(@NonNull LayoutKind kind, @NonNull UnaryOperator<KindLayout> change) {
        assertMainThread();
        KindLayout before = mCurrent.kind(kind);
        KindLayout after = change.apply(before);
        if (after.equals(before)) return;
        mCurrent = mCurrent.withKind(kind, after);
        Map<String, String> write = new LinkedHashMap<>();
        write.put(kind.key, LayoutsJson.write(after));
        mStorage.write(write);
        notifyChanged();
    }

    /** Changes the shared sizes, writes them whole, and asks for one redress. */
    public void updateSizes(@NonNull UnaryOperator<Sizes> change) {
        assertMainThread();
        Sizes before = mCurrent.sizes();
        Sizes after = change.apply(before);
        if (after.equals(before)) return;
        mCurrent = mCurrent.withSizes(after);
        Map<String, String> write = new LinkedHashMap<>();
        write.put(LayoutsJson.KEY_SIZES, LayoutsJson.write(after));
        mStorage.write(write);
        notifyChanged();
    }

    /** Writes every document at once: the migration's and a reset's one write. */
    public void replaceAll(@NonNull Layouts layouts) {
        mStorage.write(documents(layouts));
        mCurrent = layouts;
        notifyChanged();
    }

    /** The five documents as the file holds them. */
    @NonNull
    public static Map<String, String> documents(@NonNull Layouts layouts) {
        Map<String, String> out = new LinkedHashMap<>();
        for (LayoutKind kind : LayoutKind.values())
            out.put(kind.key, LayoutsJson.write(layouts.kind(kind)));
        out.put(LayoutsJson.KEY_SIZES, LayoutsJson.write(layouts.sizes()));
        return out;
    }

    public void addListener(@NonNull Listener listener) {
        mListeners.addIfAbsent(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    private void notifyChanged() {
        Layouts now = mCurrent;
        for (Listener listener : mListeners) listener.onLayoutsChanged(now);
    }

    private void assertMainThread() {
        if (!mMainThreadOnly) return;
        Looper main = Looper.getMainLooper();
        if (main != null && Looper.myLooper() != main)
            throw new IllegalStateException("layouts are written on the main thread only");
    }
}
