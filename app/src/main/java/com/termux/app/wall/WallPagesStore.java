package com.termux.app.wall;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loads, saves and announces the wall's {@link WallPages}. One versioned JSON under {@code state}
 * in the {@code wall_pages} preferences file (the pattern of the widget repository), read
 * synchronously at start, behind {@link Storage} so tests use {@link MemoryStorage}. No singleton:
 * the activity builds one and hands it to its owners.
 *
 * <p>{@link Listener#onRemoved} fires only for a page whose removal is final: from
 * {@link #commitRemoval} (the caller owns the undo window) or {@link #flushPending} (the app
 * stops). Its owners drop that page's data then.
 */
public final class WallPagesStore {

    static final int VERSION = 1;
    public static final String PREFS = "wall_pages";
    public static final String KEY_STATE = "state";

    /** Where the JSON lives. */
    public interface Storage {
        @Nullable String read();
        void write(@NonNull String json);
        /** Calls {@code changed} when another holder writes; the default never does. */
        default void watch(@NonNull Runnable changed) {}
    }

    /** The {@code wall_pages} preferences file. */
    public static final class PrefsStorage implements Storage {
        private final SharedPreferences prefs;

        public PrefsStorage(@NonNull Context context) {
            this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        }

        public PrefsStorage(@NonNull SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override @Nullable public String read() { return prefs.getString(KEY_STATE, null); }

        @Override public void write(@NonNull String json) {
            prefs.edit().putString(KEY_STATE, json).apply();
        }

        // Held here: the preferences keep their listeners weakly.
        private SharedPreferences.OnSharedPreferenceChangeListener watcher;

        @Override public void watch(@NonNull final Runnable changed) {
            watcher = (p, key) -> { if (KEY_STATE.equals(key)) changed.run(); };
            prefs.registerOnSharedPreferenceChangeListener(watcher);
        }
    }

    /** For tests. */
    public static final class MemoryStorage implements Storage {
        private String json;

        @Override @Nullable public String read() { return json; }

        @Override public void write(@NonNull String value) { json = value; }
    }

    public interface Listener {
        void onChanged(@NonNull WallPages pages);
        void onRemoved(@NonNull Page page);
    }

    /** An edit to run on the current value, for {@link #edit}. */
    public interface Edit {
        @NonNull WallPages.Result apply(@NonNull WallPages pages);
    }

    private final Storage storage;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private WallPages current;
    private final boolean seeded;
    private String lastJson;
    private boolean memoryOnly;

    /** Reads the stored value; an absent or unreadable one leaves {@link WallPages#EMPTY}. */
    public WallPagesStore(@NonNull Storage storage) {
        this.storage = storage;
        WallPages loaded = fromJson(storage.read());
        this.seeded = loaded != null;
        this.current = loaded != null ? loaded : WallPages.EMPTY;
        this.lastJson = loaded != null ? storage.read() : null;
        storage.watch(this::reloadIfChanged);
    }

    /** A store on the {@code wall_pages} preferences; another holder's writes reach this one. */
    @NonNull
    public static WallPagesStore open(@NonNull Context context) {
        return new WallPagesStore(new PrefsStorage(context));
    }

    private void reloadIfChanged() {
        if (memoryOnly) return;
        String json = storage.read();
        if (json == null || json.equals(lastJson)) return;
        WallPages loaded = fromJson(json);
        if (loaded == null || loaded.equals(current)) return;
        lastJson = json;
        current = loaded;
        for (Listener l : listeners) l.onChanged(loaded);
    }

    /**
     * The stored string when one is there but cannot be read (bad JSON, or a version this build
     * does not know, as after a downgrade); null when nothing is stored or it reads. Such a value
     * is not "no pages": the upgrade must not seed over it (W7).
     */
    @Nullable
    public String unreadable() {
        String raw = storage.read();
        return raw != null && !raw.isEmpty() && fromJson(raw) == null ? raw : null;
    }

    /** Whether a value was stored when this store was built (the upgrade seeds when not). */
    public boolean wasSeeded() { return seeded; }

    @NonNull public WallPages current() { return current; }

    public void addListener(@NonNull Listener listener) { listeners.add(listener); }

    public void removeListener(@NonNull Listener listener) { listeners.remove(listener); }

    /** Replaces the value (the upgrade's seed); saves and announces it. */
    public void replace(@NonNull WallPages pages) {
        current = pages;
        lastJson = toJson(pages);
        // Over an unreadable stored value the run's pages live in memory only (W7).
        if (!memoryOnly) storage.write(lastJson);
        for (Listener l : listeners) l.onChanged(pages);
    }

    /**
     * Gives this run {@code pages} without saving them, now or on any later change: the stored
     * value this build cannot read stays as it was for the build that wrote it (W7).
     */
    public void useForThisRun(@NonNull WallPages pages) {
        memoryOnly = true;
        replace(pages);
    }

    /** Whether this run's pages are kept in memory only ({@link #useForThisRun}). */
    public boolean isMemoryOnly() { return memoryOnly; }

    /**
     * Runs {@code edit} on the current value. An accepted change is saved and announced; a
     * committed removal also fires {@code onRemoved} once. A refusal changes nothing.
     */
    @NonNull
    public WallPages.Result edit(@NonNull Edit edit) {
        WallPages.Result result = edit.apply(current);
        if (!result.ok() || result.change == WallPages.ChangeType.NONE) return result;
        replace(result.pages);
        if (result.change == WallPages.ChangeType.REMOVAL_COMMITTED && result.page != null) {
            for (Listener l : listeners) l.onRemoved(result.page);
        }
        return result;
    }

    /** A change to the value that is not one {@link WallPages.Result} describes. */
    public interface Transform {
        @NonNull WallPages apply(@NonNull WallPages pages);
    }

    /**
     * Replaces the value with {@code transform}'s answer. A page that is gone from it (neither on
     * the wall nor detached) was removed for good: {@code onRemoved} fires for it once.
     */
    public void transform(@NonNull Transform transform) {
        WallPages before = current;
        WallPages after = transform.apply(before);
        if (after.equals(before)) return;
        replace(after);
        List<Page> held = new ArrayList<>(after.allPages());
        held.addAll(after.detached());
        List<Page> was = new ArrayList<>(before.allPages());
        was.addAll(before.detached());
        for (Page p : was) {
            boolean still = false;
            for (Page h : held) if (h.id == p.id) still = true;
            if (!still) for (Listener l : listeners) l.onRemoved(p);
        }
    }

    /** Commits one pending removal. */
    @NonNull
    public WallPages.Result commitRemoval(final int id) {
        return edit(new Edit() {
            @NonNull @Override public WallPages.Result apply(@NonNull WallPages pages) {
                return pages.commitRemoval(id);
            }
        });
    }

    /** App stop: every pending removal becomes final, each announced once. */
    public void flushPending() {
        for (Integer id : new ArrayList<>(current.pendingIds())) commitRemoval(id);
    }

    // ---- JSON -----------------------------------------------------------------------------

    @NonNull
    static String toJson(@NonNull WallPages pages) {
        try {
            JSONObject o = new JSONObject();
            o.put("version", VERSION);
            o.put("home", pages.homeId());
            o.put("next", pages.nextId());
            o.put("homeIsDisplayWindow", pages.homeIsDisplayWindow());
            o.put("lastHome", pages.lastHomePageId());
            o.put("homeParked", pages.homeParked());
            o.put("pages", pagesToJson(pages.allPages()));
            o.put("detached", pagesToJson(pages.detached()));
            JSONArray pending = new JSONArray();
            for (Integer id : pages.pendingIds()) pending.put(id.intValue());
            o.put("pending", pending);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONArray pagesToJson(List<Page> pages) throws JSONException {
        JSONArray array = new JSONArray();
        for (Page p : pages) {
            JSONObject o = new JSONObject();
            o.put("id", p.id);
            o.put("kind", p.kind.name());
            if (p.name != null) o.put("name", p.name);
            if (p.binding != null) o.put("binding", p.binding);
            array.put(o);
        }
        return array;
    }

    /** The value in {@code json}; null when absent, unreadable or from a newer version. */
    @Nullable
    static WallPages fromJson(@Nullable String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            if (o.optInt("version", 0) != VERSION) return null;
            List<Page> all = pagesFromJson(o.getJSONArray("pages"));
            List<Page> detached = pagesFromJson(o.optJSONArray("detached"));
            List<Integer> pending = new ArrayList<>();
            JSONArray p = o.optJSONArray("pending");
            for (int i = 0; p != null && i < p.length(); i++) pending.add(p.getInt(i));
            return new WallPages(all, pending, detached, o.getInt("home"), o.getInt("next"),
                o.optBoolean("homeIsDisplayWindow", false), o.optInt("lastHome", 0),
                o.optBoolean("homeParked", false));
        } catch (JSONException | IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Page> pagesFromJson(@Nullable JSONArray array) throws JSONException {
        List<Page> out = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            out.add(new Page(o.getInt("id"), PageKind.valueOf(o.getString("kind")),
                o.has("name") ? o.getString("name") : null,
                o.has("binding") ? o.getString("binding") : null));
        }
        return out;
    }
}
