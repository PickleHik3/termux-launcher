package com.termux.app.place.layout;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceOrientation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * The {@code layouts} file (ADR 0020 §5): five JSON documents read once into an immutable
 * {@link Layouts}, each written whole. {@link #update} runs on the main thread only, so two writers
 * in one frame cannot lose one another's change (writes are also serialised), and tells its listeners once: the one redress
 * request of ADR 0018 §2.
 *
 * <p>While the Appearance editor is open its edits go into a draft over the file
 * ({@link #beginDraft}, {@link #updateDraft}): {@link #current()} shows the file with the draft on
 * top, Done writes the draft once ({@link #commitDraft}) and Discard drops it
 * ({@link #discardDraft}). A write through {@link #update} meanwhile (launcherctl, Settings, what a
 * place remembers) reaches the file at once and survives both.</p>
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
    /** What readers see: the file, with the editor's draft on top while one is open. */
    @NonNull private volatile Layouts mCurrent;
    /** What the file holds. */
    @NonNull private Layouts mStored;
    /**
     * The editor's edits by cell (a null value removes the field), or null while no draft is
     * open. A cell set back to the file's value is dropped, so a write from outside reaches it.
     */
    @Nullable private LinkedHashMap<Cell, Object> mDraft;
    /**
     * Above zero while {@link #writePastDraft} runs: draft writes go to the file, reads see the
     * file alone, and readers are told once it ends.
     */
    private volatile int mPastDraft;

    public LayoutStore(@NonNull Storage storage, boolean mainThreadOnly) {
        mStorage = storage;
        mMainThreadOnly = mainThreadOnly;
        mStored = Layouts.shipped();
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

    /** Reads the five documents afresh; an open draft stays on top. */
    @NonNull
    public synchronized Layouts load() {
        Layouts loaded = LayoutsJson.read(new LayoutsJson.Source() {
            @Nullable @Override public String read(@NonNull String key) {
                return mStorage.read(key);
            }

            @Override public void warn(@NonNull String message) {
                com.termux.shared.logger.Logger.logWarn("LayoutStore", message);
            }
        });
        mStored = loaded;
        mCurrent = withDraft(loaded);
        return mCurrent;
    }

    /** Every layout as it stands; take it per pass, never across a redress. */
    @NonNull
    public Layouts current() {
        return mPastDraft > 0 ? mStored : mCurrent;
    }

    /** Changes one kind's document, writes it whole, and asks for one redress. */
    public synchronized void update(@NonNull LayoutKind asked, @NonNull UnaryOperator<KindLayout> change) {
        assertMainThread();
        // A retired kind's document is the Workspace's (ADR 0025).
        LayoutKind kind = asked.live();
        KindLayout before = mStored.kind(kind);
        KindLayout after = change.apply(before);
        if (after.equals(before)) return;
        mStored = mStored.withKind(kind, after);
        Map<String, String> write = new LinkedHashMap<>();
        write.put(kind.key, LayoutsJson.write(after));
        mStorage.write(write);
        if (mPastDraft == 0) settle();
    }

    /** Changes the shared sizes, writes them whole, and asks for one redress. */
    public synchronized void updateSizes(@NonNull UnaryOperator<Sizes> change) {
        assertMainThread();
        Sizes before = mStored.sizes();
        Sizes after = change.apply(before);
        if (after.equals(before)) return;
        mStored = mStored.withSizes(after);
        Map<String, String> write = new LinkedHashMap<>();
        write.put(LayoutsJson.KEY_SIZES, LayoutsJson.write(after));
        mStorage.write(write);
        if (mPastDraft == 0) settle();
    }

    /** Writes every document at once: the migration's and a reset's one write. */
    public synchronized void replaceAll(@NonNull Layouts layouts) {
        mStorage.write(documents(layouts));
        mStored = layouts;
        mCurrent = withDraft(layouts);
        notifyChanged();
    }

    // ------------------------------------------------------------------ the editor's draft

    /** One value of the documents: a kind's field (the sizes' for a null kind), wide or per orientation. */
    private static final class Cell {
        @Nullable final LayoutKind kind;
        @Nullable final PlaceOrientation orientation;
        @NonNull final String field;

        Cell(@Nullable LayoutKind kind, @Nullable PlaceOrientation orientation,
             @NonNull String field) {
            this.kind = kind;
            this.orientation = orientation;
            this.field = field;
        }

        @NonNull KindLayout document(@NonNull Layouts layouts) {
            return kind == null ? layouts.sizes().document() : layouts.kind(kind);
        }

        @Nullable Object read(@NonNull Layouts layouts) {
            KindLayout document = document(layouts);
            return (orientation == null ? document.wide() : document.orientation(orientation))
                .get(field);
        }

        @NonNull Layouts write(@NonNull Layouts layouts, @Nullable Object value) {
            KindLayout document = document(layouts);
            document = orientation == null ? document.withWide(field, value)
                : document.with(orientation, field, value);
            return kind == null ? layouts.withSizes(new Sizes(document))
                : layouts.withKind(kind, document);
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Cell)) return false;
            Cell c = (Cell) o;
            return kind == c.kind && orientation == c.orientation && field.equals(c.field);
        }

        @Override public int hashCode() {
            return Objects.hash(kind, orientation, field);
        }
    }

    /**
     * The Appearance editor opened: its edits go into a draft from here on. A draft left open
     * (a session that never ended) is dropped first.
     */
    public synchronized void beginDraft() {
        discardDraft();
        mDraft = new LinkedHashMap<>();
    }

    public synchronized boolean isDraftOpen() {
        return mDraft != null;
    }

    /**
     * The editor's {@link #update}: into the draft while one is open, shown at once, written only
     * by {@link #commitDraft}. With no draft open it is {@link #update}.
     */
    public synchronized void updateDraft(@NonNull LayoutKind asked,
                                         @NonNull UnaryOperator<KindLayout> change) {
        if (mDraft == null || mPastDraft > 0) {
            update(asked, change);
            return;
        }
        assertMainThread();
        LayoutKind kind = asked.live();
        KindLayout before = mCurrent.kind(kind);
        KindLayout after = change.apply(before);
        if (after.equals(before)) return;
        holdChanges(kind, before, after);
    }

    /** The editor's {@link #updateSizes}, as {@link #updateDraft}. */
    public synchronized void updateSizesDraft(@NonNull UnaryOperator<Sizes> change) {
        if (mDraft == null || mPastDraft > 0) {
            updateSizes(change);
            return;
        }
        assertMainThread();
        Sizes before = mCurrent.sizes();
        Sizes after = change.apply(before);
        if (after.equals(before)) return;
        holdChanges(null, before.document(), after.document());
    }

    /**
     * Runs {@code writes} against the file alone, as if no draft were open: they read the file
     * and write it. A layout's first seeding, which a read can start while the editor is open and
     * which is the store's, not the editor's: it must neither copy the editor's edits nor wait for
     * Done. Readers are told once, at the end.
     */
    public synchronized void writePastDraft(@NonNull Runnable writes) {
        mPastDraft++;
        try {
            writes.run();
        } finally {
            mPastDraft--;
            if (mPastDraft == 0) settle();
        }
    }

    /** Whether the draft holds a value the file does not. */
    public synchronized boolean isDraftDirty() {
        return isDraftDirtyOutside(java.util.Collections.emptySet());
    }

    /**
     * Whether the draft holds a value the file does not in a field other than {@code fields}: the
     * fields a caller compares as resolved instead, where a value written out at what an absent
     * field already reads is no change.
     */
    public synchronized boolean isDraftDirtyOutside(@NonNull java.util.Set<String> fields) {
        if (mDraft == null) return false;
        for (Map.Entry<Cell, Object> e : mDraft.entrySet()) {
            if (!fields.contains(e.getKey().field) && !same(e.getKey().read(mStored), e.getValue()))
                return true;
        }
        return false;
    }

    /** What the file holds, without the draft: for a reading of the store alone. */
    @NonNull
    public synchronized Layouts stored() {
        return mStored;
    }

    /** Undo: the draft's edits go, the draft stays open. */
    public synchronized void resetDraft() {
        if (mDraft == null || mDraft.isEmpty()) return;
        mDraft.clear();
        settle();
    }

    /** Done: the draft is written into the file, once, and closes. */
    public synchronized void commitDraft() {
        LinkedHashMap<Cell, Object> draft = mDraft;
        mDraft = null;
        if (draft == null || draft.isEmpty()) return;
        Layouts before = mStored;
        Layouts after = before;
        for (Map.Entry<Cell, Object> e : draft.entrySet())
            after = e.getKey().write(after, e.getValue());
        if (after.equals(before)) return;
        Map<String, String> write = new LinkedHashMap<>();
        for (LayoutKind kind : LayoutKind.values()) {
            if (kind.retired() || after.kind(kind).equals(before.kind(kind))) continue;
            write.put(kind.key, LayoutsJson.write(after.kind(kind)));
        }
        if (!after.sizes().equals(before.sizes()))
            write.put(LayoutsJson.KEY_SIZES, LayoutsJson.write(after.sizes()));
        mStorage.write(write);
        mStored = after;
        settle();
    }

    /** Discard, or a session gone: the draft is dropped and the readers are back on the file. */
    public synchronized void discardDraft() {
        if (mDraft == null) return;
        mDraft = null;
        settle();
    }

    /**
     * At start: drops a draft an editor session left open in the app's shared store, if that
     * store exists yet.
     */
    public static void discardSharedDraft(@NonNull Context context) {
        LayoutStore store;
        synchronized (SHARED) {
            store = SHARED.get(prefs(context));
        }
        if (store != null) store.discardDraft();
    }

    /** Takes every field {@code after} changed from {@code before} into the draft. */
    private void holdChanges(@Nullable LayoutKind kind, @NonNull KindLayout before,
                             @NonNull KindLayout after) {
        hold(kind, null, before.wide(), after.wide());
        for (PlaceOrientation o : PlaceOrientation.values())
            hold(kind, o, before.orientation(o), after.orientation(o));
        settle();
    }

    private void hold(@Nullable LayoutKind kind, @Nullable PlaceOrientation orientation,
                      @NonNull LayoutFields before, @NonNull LayoutFields after) {
        if (before.equals(after)) return;
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>(before.keys());
        fields.addAll(after.keys());
        for (String field : fields) {
            Object value = after.get(field);
            if (same(before.get(field), value)) continue;
            Cell cell = new Cell(kind, orientation, field);
            if (same(cell.read(mStored), value)) mDraft.remove(cell);
            else mDraft.put(cell, value);
        }
    }

    private static boolean same(@Nullable Object a, @Nullable Object b) {
        return a == null ? b == null : b != null && LayoutFields.sameValue(a, b);
    }

    /** {@code stored} with the draft on top. */
    @NonNull
    private Layouts withDraft(@NonNull Layouts stored) {
        LinkedHashMap<Cell, Object> draft = mDraft;
        if (draft == null) return stored;
        Layouts out = stored;
        for (Map.Entry<Cell, Object> e : draft.entrySet())
            out = e.getKey().write(out, e.getValue());
        return out;
    }

    /** The readers' view follows the file and the draft; they are told when it moved. */
    private void settle() {
        Layouts before = mCurrent;
        mCurrent = withDraft(mStored);
        if (!mCurrent.equals(before)) notifyChanged();
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
