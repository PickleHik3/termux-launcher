package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.GlassMaterial;

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

    public static final String PREFIX = "terl.look.";
    static final String KEY_LOOK = PREFIX + "id";
    static final String KEY_CORNERS = PREFIX + "corners";
    static final String KEY_TINT = PREFIX + "tint";
    /** The Depth position last set, kept while it still writes the stored thickness. */
    static final String KEY_DEPTH_SET = PREFIX + "depth_set";
    static final String KEY_BEND = PREFIX + "bend";
    static final String KEY_SHINE = PREFIX + "shine";

    public static final int DEFAULT_CORNER_DP = 26;
    public static final int MAX_CORNER_DP = 48;

    private final LookStore store;
    private final List<TerlLookSource> sources;
    @NonNull private final Physical physical;
    /** Whether {@link #physical} keeps its numbers outside this store's keys: its writes are told here. */
    private final boolean physicalOutside;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final LookStore.Listener storeListener = key -> {
        if (key == null || key.startsWith(PREFIX)) notifyChanged();
    };

    public StoredTerlLookStore(@NonNull LookStore store) {
        this(store, Collections.singletonList(TerlLooks.shipped()));
    }

    /** {@code sources} in filmstrip order, the shipped Looks first; Custom comes last. */
    public StoredTerlLookStore(@NonNull LookStore store, @NonNull List<TerlLookSource> sources) {
        this(store, sources, null);
    }

    /**
     * {@code physical} is where Depth and Shine live (the renderer's own keys, in the app); null
     * keeps them under this store's keys.
     */
    public StoredTerlLookStore(@NonNull LookStore store, @NonNull List<TerlLookSource> sources,
                               @Nullable Physical physical) {
        this.store = store;
        this.sources = new ArrayList<>(sources);
        this.physicalOutside = physical != null;
        this.physical = physical != null ? physical : new Physical() {
            @Override public int bendDp() {
                return store.getInt(KEY_BEND, GlassMaterial.REALISTIC_BEND_DP);
            }
            @Override public void setBendDp(int dp) { store.putInt(KEY_BEND, dp); }
            @Override public int shine() { return store.getInt(KEY_SHINE, GlassMaterial.SHINE_DEFAULT); }
            @Override public void setShine(int shine) { store.putInt(KEY_SHINE, shine); }
        };
    }

    /**
     * Depth and Shine where the renderer reads them: v1's app-wide glass values in the look grid
     * ({@code GlassRefraction.Look.of}), which the Appearance editor writes too.
     */
    @NonNull
    public static Physical physicalOver(
            @NonNull com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences preferences) {
        return new Physical() {
            @Override public int bendDp() { return preferences.getFancierGlassBendDp(); }
            @Override public void setBendDp(int dp) { preferences.setFancierGlassBendDp(dp); }
            @Override public int shine() { return preferences.getGlassShine(); }
            @Override public void setShine(int shine) { preferences.setGlassShine(shine); }
        };
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

    @Override
    public int lookValue(@NonNull String lookId, @NonNull TerlProperty property) {
        TerlLookSource.Look look = CUSTOM.equals(lookId) ? null : find(lookId);
        if (look == null) return customValue(property);
        return property == TerlProperty.OPACITY ? look.opacity : look.blur;
    }

    @Nullable
    @Override
    public TerlLookSource.Look preset(@NonNull String lookId) {
        return CUSTOM.equals(lookId) ? null : find(lookId);
    }

    @Override
    public int tintAmountPercent() {
        String tint = tint();
        if (!TINT_AUTO.equals(tint)) return TerlLookStore.tintAmountPercent(tint);
        TerlLookSource.Look worn = preset(look());
        return worn == null ? 0 : worn.tintPercent;
    }

    @Override
    public int depth() {
        int bend = physical.bendDp();
        int set = store.getInt(KEY_DEPTH_SET, -1);
        return set >= GlassMaterial.MIN && set <= GlassMaterial.MAX && GlassMaterial.bendForDepth(set) == bend
            ? set : GlassMaterial.depthForBend(bend);
    }

    @Override
    public void setDepth(int depth) {
        int position = Math.max(GlassMaterial.MIN, Math.min(GlassMaterial.MAX, depth));
        physical.setBendDp(GlassMaterial.bendForDepth(position));
        // This store's own key, written last: its listeners hear the change once, thickness in place.
        store.putInt(KEY_DEPTH_SET, position);
    }

    @Override
    public int shine() {
        return Math.max(GlassMaterial.MIN, Math.min(GlassMaterial.MAX, physical.shine()));
    }

    @Override
    public void setShine(int shine) {
        physical.setShine(Math.max(GlassMaterial.MIN, Math.min(GlassMaterial.MAX, shine)));
        if (physicalOutside) notifyChanged();
    }

    @NonNull
    @Override
    public String look() {
        String id = store.getString(KEY_LOOK, "");
        if (CUSTOM.equals(id) || find(id) != null) return id;
        // A Look that is gone (one of the retired wallpaper Looks) or never stored: the first one.
        List<String> ids = lookIds();
        return ids.get(0);
    }

    @NonNull
    @Override
    public Pick pick(@NonNull String lookId, boolean confirmed) {
        if (!lookIds().contains(lookId)) return new Pick(false, Collections.emptyList());
        List<OwnValue> own = ownValues();
        if (!own.isEmpty() && !confirmed) return new Pick(false, own);
        // A shipped Look is its whole preset (ADR 0033): its slab and its light, app-wide, set
        // before the Look is written, so whoever the write tells sees them already.
        TerlLookSource.Look preset = preset(lookId);
        if (preset != null) {
            physical.setBendDp(preset.bendDp);
            physical.setShine(preset.shine);
            store.remove(KEY_DEPTH_SET);
        }
        for (OwnValue value : own) store.remove(ownKey(value.surface, value.property));
        store.putString(KEY_LOOK, lookId);
        return new Pick(true, own);
    }

    @Override
    public void wear(@NonNull String lookId) {
        if (lookIds().contains(lookId)) store.putString(KEY_LOOK, lookId);
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
        return lookValue(id, property);
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

    @NonNull
    @Override
    public String tint() {
        String tint = store.getString(KEY_TINT, TINT_AUTO);
        return TINTS.contains(tint) ? tint : TINT_AUTO;
    }

    @Override
    public void setTint(@NonNull String tint) {
        store.putString(KEY_TINT, TINTS.contains(tint) ? tint : TINT_AUTO);
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
