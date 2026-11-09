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
 * in one frame cannot lose one another's change (writes are also serialised), and tells its listeners once: the one redress
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

    private static final java.util.WeakHashMap<SharedPreferences, LayoutStore> SHARED =
        new java.util.WeakHashMap<>();

    /**
     * The app's one store over the {@code layouts} file, shared by every reader in the process so
     * no two hold different copies.
     */
    @NonNull
    public static LayoutStore shared(@NonNull Context context) {
        SharedPreferences file = prefs(context);
        synchronized (SHARED) {
            LayoutStore store = SHARED.get(file);
            if (store == null) {
                store = new LayoutStore(new PrefsStorage(file), true);
                SHARED.put(file, store);
            }
            return store;
        }
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
    public synchronized void update(@NonNull LayoutKind asked, @NonNull UnaryOperator<KindLayout> change) {
        assertMainThread();
        // A retired kind's document is the Workspace's (ADR 0025).
        LayoutKind kind = asked.live();
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
    public synchronized void updateSizes(@NonNull UnaryOperator<Sizes> change) {
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
    public synchronized void replaceAll(@NonNull Layouts layouts) {
        mStorage.write(documents(layouts));
        mCurrent = layouts;
        notifyChanged();
    }

    /**
     * The documents as the file holds them. A retired kind's (ADR 0025) is left out: its key in
     * the file is left as it was, for a downgrade.
     */
    @NonNull
    public static Map<String, String> documents(@NonNull Layouts layouts) {
        Map<String, String> out = new LinkedHashMap<>();
        for (LayoutKind kind : LayoutKind.values()) {
            if (kind.retired()) continue;
            out.put(kind.key, LayoutsJson.write(layouts.kind(kind)));
        }
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
        // Writes are serialised by the lock; one off the main thread is logged, not thrown, so
        // a background caller cannot crash the launcher.
        if (main != null && Looper.myLooper() != main)
            com.termux.shared.logger.Logger.logWarn("LayoutStore",
                "a layout was written off the main thread");
    }
}
