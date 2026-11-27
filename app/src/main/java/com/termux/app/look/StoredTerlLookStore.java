package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link TerlLookStore} over a {@link LookStore} key-value adapter: {@link PrefsLookStore} in the
 * app, {@link MemoryLookStore} in tests. Every key starts with {@code terl.look.}, which no v1 look
 * key does, so the two models share a preferences file without touching each other.
 */
public final class StoredTerlLookStore implements TerlLookStore {

    static final String PREFIX = "terl.look.";
    static final String KEY_LOOK = PREFIX + "id";
    static final String KEY_CORNERS = PREFIX + "corners";

    public static final int DEFAULT_CORNER_DP = 26;
    private static final int MAX_CORNER_DP = 48;

    private final LookStore store;
    private final List<TerlLookSource> sources;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final LookStore.Listener storeListener = key -> {
        if (key == null || key.startsWith(PREFIX)) notifyChanged();
    };

    public StoredTerlLookStore(@NonNull LookStore store) {
        this(store, Collections.singletonList(TerlLooks.shipped()));
    }

    /** {@code sources} in filmstrip order; the shipped Looks first, wallpaper Looks after. */
    public StoredTerlLookStore(@NonNull LookStore store, @NonNull List<TerlLookSource> sources) {
        this.store = store;
        this.sources = new ArrayList<>(sources);
    }

    @NonNull
    private static String ownKey(TerlSurface surface, TerlProperty property) {
        return PREFIX + "own." + surface.slug + "." + property.slug;
    }

    @NonNull
    private static String customKey(TerlProperty property) {
        return PREFIX + "custom." + property.slug;
    }

    @Nullable
    private TerlLookSource.Look find(@NonNull String id) {
        for (TerlLookSource source : sources) {
            TerlLookSource.Look look = source.find(id);
            if (look != null) return look;
        }
        return null;
    }

    @NonNull
    @Override
    public List<String> lookIds() {
        List<String> ids = new ArrayList<>();
        for (TerlLookSource source : sources) {
            for (TerlLookSource.Look look : source.looks()) ids.add(look.id);
        }
        ids.add(CUSTOM);
        return ids;
    }

    @NonNull
    @Override
    public String look() {
        String id = store.getString(KEY_LOOK, "");
        if (CUSTOM.equals(id) || find(id) != null) return id;
        // A Look that is gone (a wallpaper Look not made yet) or never stored: the first one.
        List<String> ids = lookIds();
        return ids.get(0);
    }

    @NonNull
    @Override
    public Pick pick(@NonNull String lookId, boolean confirmed) {
        if (!lookIds().contains(lookId)) return new Pick(false, Collections.emptyList());
        List<OwnValue> own = ownValues();
        if (!own.isEmpty() && !confirmed) return new Pick(false, own);
        for (OwnValue value : own) store.remove(ownKey(value.surface, value.property));
        store.putString(KEY_LOOK, lookId);
        return new Pick(true, own);
    }

    @NonNull
    @Override
    public List<OwnValue> ownValues() {
        List<OwnValue> own = new ArrayList<>();
        for (TerlSurface surface : TerlSurface.values()) {
            if (!surface.acceptsOwn) continue;
            for (TerlProperty property : TerlProperty.values()) {
                if (holdsOwn(surface, property)) {
                    own.add(new OwnValue(surface, property, value(surface, property)));
                }
            }
        }
        return own;
    }

    @Override
    public int value(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        if (holdsOwn(surface, property)) {
            return property.clamp(store.getInt(ownKey(surface, property), property.min));
        }
        return global(property);
    }

    @Override
    public int global(@NonNull TerlProperty property) {
        String id = look();
        if (CUSTOM.equals(id)) return customValue(property);
        TerlLookSource.Look look = find(id);
        if (look == null) return customValue(property);
        return property == TerlProperty.OPACITY ? look.opacity : look.blur;
    }

    @Override
    public boolean holdsOwn(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        return surface.acceptsOwn && store.has(ownKey(surface, property));
    }

    @Override
    public boolean setOwn(@NonNull TerlSurface surface, @NonNull TerlProperty property, int value) {
        if (!surface.acceptsOwn) return false;
        store.putInt(ownKey(surface, property), property.clamp(value));
        return true;
    }

    @Override
    public void followGlobal(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        store.remove(ownKey(surface, property));
    }

    @Override
    public int customValue(@NonNull TerlProperty property) {
        // A fresh Custom starts as the first Look, until the user sets its numbers.
        TerlLookSource.Look first = find(lookIds().get(0));
        int fallback = first == null ? property.min
            : property == TerlProperty.OPACITY ? first.opacity : first.blur;
        return property.clamp(store.getInt(customKey(property), fallback));
    }

    @Override
    public void setCustomValue(@NonNull TerlProperty property, int value) {
        store.putInt(customKey(property), property.clamp(value));
    }

    @Override
    public int cornerRadiusDp() {
        return Math.max(0, Math.min(MAX_CORNER_DP, store.getInt(KEY_CORNERS, DEFAULT_CORNER_DP)));
    }

    @Override
    public void setCornerRadiusDp(int dp) {
        store.putInt(KEY_CORNERS, Math.max(0, Math.min(MAX_CORNER_DP, dp)));
    }

    @Override
    public void addListener(@NonNull Listener listener) {
        if (listeners.isEmpty()) store.addListener(storeListener);
        listeners.add(listener);
    }

    @Override
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) store.removeListener(storeListener);
    }

    private void notifyChanged() {
        for (Listener listener : listeners) listener.onLookChanged();
    }
}
