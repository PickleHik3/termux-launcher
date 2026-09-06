package com.termux.app.place;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The look layer of a place, as one seam.
 *
 * <p>Looks are shared: every place wears the same dock, keyboard, status bar and terminal until one
 * of them is given something of its own. This sits between the launcher's preferences and the file
 * they live in and adds that third layer — a read of a look key answers with the place's own value
 * when it has one, and with the shared value otherwise, which itself still resolves through Base.
 * Nothing above it knows a place is involved: {@code getStatusBarBlurRadius()} and every other
 * getter the chrome reads keep their signature and start telling the truth for the place on screen.
 *
 * <p>Keys are the scheme the arrangement store already uses, one level deeper:
 * {@code place.<home|terminal|display>.look.<shared key>}. Only the keys the surface editor owns
 * can be scoped — the cells of the inheritance model and their links to Base, the dock's shape and
 * size, the apps per page, the keyboard's key metrics, the status chips, the terminal's frame and
 * the wallpaper dim. Everything else, Base itself included, is shared by definition and passes
 * straight through.
 *
 * <p>Two places are remembered rather than one. The <em>render place</em> is the place on screen
 * and is what reads resolve through. The <em>edit scope</em> is what the surface editor is holding
 * open, and it is what writes land in: while it is set, a scopable write goes to that place's own
 * key and leaves the shared value alone. Setters still write through their link to Base exactly as
 * before — a scoped write is a scoped detached value, never a silent detach of the shared surface.
 *
 * <p>No Android views here: it is a {@link SharedPreferences} over a {@link SharedPreferences}, and
 * it is tested as one.
 */
public final class PlaceLookPreferences implements SharedPreferences {

    /** Same prefix as the arrangement keys, so one place's storage reads as one group. */
    private static final String PREFIX = "place.";
    private static final String INFIX = ".look.";

    /**
     * The keys a place may take for itself. Everything the surface editor's cells and its other
     * controls write, plus the link flags those cells carry — a place that overrides a cell
     * overrides whether that cell follows Base too, or the shared link would decide whether the
     * scoped number is ever read.
     */
    private static final Set<String> SCOPABLE = buildScopableKeys();

    private static Set<String> buildScopableKeys() {
        Set<String> keys = new HashSet<>();
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                String key = TermuxAppSharedPreferences.surfaceOverrideKey(slot, property);
                if (key != null) keys.add(key);
            }
        }
        Collections.addAll(keys,
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE,
            TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT,
            TERMUX_APP.KEY_APP_LAUNCHER_BUTTON_COUNT,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            TERMUX_APP.KEY_STATUS_INDICATOR_CORNER_RADIUS,
            TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED,
            TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS,
            TERMUX_APP.KEY_TERMINAL_PANE_GAP,
            TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM);
        return Collections.unmodifiableSet(keys);
    }

    /** Whether a place is allowed an opinion of its own about this key. */
    public static boolean isScopable(@Nullable String key) {
        return key != null
            && (SCOPABLE.contains(key) || key.startsWith(TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX));
    }

    /**
     * The name a place stores under. The widget grid is the home screen everywhere a user can see
     * it; only the enum says WIDGETS. Kept in step with the arrangement store's own mapping.
     */
    @NonNull
    public static String placeKey(@NonNull PaneWallPage place) {
        return place == PaneWallPage.WIDGETS ? "home" : place.name().toLowerCase(Locale.ROOT);
    }

    /** Where one place keeps its own value for a shared look key. */
    @NonNull
    public static String lookKey(@NonNull PaneWallPage place, @NonNull String key) {
        return PREFIX + placeKey(place) + INFIX + key;
    }

    /** The place a stored look key belongs to, or null for any other key. */
    @Nullable
    public static PaneWallPage placeOfLookKey(@NonNull String key) {
        if (!key.startsWith(PREFIX)) return null;
        for (PaneWallPage place : PaneWallPage.values()) {
            if (key.startsWith(PREFIX + placeKey(place) + INFIX)) return place;
        }
        return null;
    }

    @NonNull private final SharedPreferences mStore;

    /** The place on screen; what reads resolve through while the editor is not holding a scope. */
    @Nullable private PaneWallPage mRenderPlace;
    /** The place the editor is open on, or null for the shared layer. Only meaningful editing. */
    @Nullable private PaneWallPage mEditPlace;
    private boolean mEditing;
    /**
     * Depth of {@link #runShared}. The shared layer's own controls — Base, the material, the
     * presets — mean "everywhere" whichever place the editor was opened on, so they read and write
     * with the scope lifted.
     */
    private int mSharedDepth;

    public PlaceLookPreferences(@NonNull SharedPreferences store) {
        mStore = store;
    }

    // ------------------------------------------------------------------------------ the scopes

    /**
     * The place the chrome is being drawn for. Returns whether it moved, so the caller only pays
     * for a re-apply when it did.
     */
    public boolean setRenderPlace(@Nullable PaneWallPage place) {
        if (mRenderPlace == place) return false;
        mRenderPlace = place;
        return true;
    }

    @Nullable
    public PaneWallPage renderPlace() {
        return mRenderPlace;
    }

    /**
     * The editor opened on a place, or on the shared layer for a null place. The place it edits is
     * also the place it renders, so what the sliders move is what the user is looking at.
     */
    public void beginEdit(@Nullable PaneWallPage place) {
        mEditPlace = place;
        mEditing = true;
    }

    /** The editor closed; reads go back to the place on screen. */
    public void endEdit() {
        mEditing = false;
        mEditPlace = null;
    }

    public boolean isEditing() {
        return mEditing;
    }

    /** The place the editor is holding open, or null while it is on the shared layer. */
    @Nullable
    public PaneWallPage editPlace() {
        return mEditing ? mEditPlace : null;
    }

    /** The place reads currently resolve through: the editor's scope while it is open, else the
     *  place on screen. Null is the shared layer. */
    @Nullable
    public PaneWallPage effectivePlace() {
        return mEditing ? mEditPlace : mRenderPlace;
    }

    /** Runs one action against the shared layer, whatever place is open. */
    public void runShared(@NonNull Runnable action) {
        mSharedDepth++;
        try {
            action.run();
        } finally {
            mSharedDepth--;
        }
    }

    /** The place a read or a write resolves through right now, or null for the shared layer. */
    @Nullable
    private PaneWallPage scope() {
        if (mSharedDepth > 0) return null;
        return effectivePlace();
    }

    /** The key a read should answer from: the place's own where it has one, the shared one else. */
    @NonNull
    private String readKey(@NonNull String key) {
        PaneWallPage place = scope();
        if (place == null || !isScopable(key)) return key;
        String scoped = lookKey(place, key);
        return mStore.contains(scoped) ? scoped : key;
    }

    /** The key a write should land on. Writes only scope while the editor holds a place open. */
    @NonNull
    private String writeKey(@NonNull String key) {
        PaneWallPage place = mSharedDepth > 0 || !mEditing ? null : mEditPlace;
        return place == null || !isScopable(key) ? key : lookKey(place, key);
    }

    // -------------------------------------------------------------------- the overrides as data

    /** Whether any place has taken anything of its own. */
    public boolean hasAnyOverrides() {
        for (String key : mStore.getAll().keySet()) {
            if (placeOfLookKey(key) != null) return true;
        }
        return false;
    }

    /** Whether this place has its own value for any of these shared keys. */
    public boolean hasOverride(@NonNull PaneWallPage place, @NonNull Collection<String> keys) {
        for (String key : keys) {
            if (mStore.contains(lookKey(place, key))) return true;
        }
        return false;
    }

    /** The places holding their own value for any of these shared keys, in wall order. */
    @NonNull
    public List<PaneWallPage> placesOverriding(@NonNull Collection<String> keys) {
        List<PaneWallPage> places = new ArrayList<>(3);
        for (PaneWallPage place : PaneWallPage.values()) {
            if (hasOverride(place, keys)) places.add(place);
        }
        return places;
    }

    /** Gives these shared keys back to the shared layer for one place. */
    public void clearOverride(@NonNull PaneWallPage place, @NonNull Collection<String> keys) {
        SharedPreferences.Editor editor = mStore.edit();
        for (String key : keys) editor.remove(lookKey(place, key));
        editor.apply();
    }

    /** Every place back on the shared look. What Reset held and a preset both mean. */
    public void clearAllOverrides() {
        SharedPreferences.Editor editor = mStore.edit();
        for (String key : mStore.getAll().keySet()) {
            if (placeOfLookKey(key) != null) editor.remove(key);
        }
        editor.apply();
    }

    /** Every place's whole look layer, for an exact undo. */
    @NonNull
    public Map<String, Object> capture() {
        Map<String, Object> captured = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : mStore.getAll().entrySet()) {
            if (placeOfLookKey(entry.getKey()) != null)
                captured.put(entry.getKey(), entry.getValue());
        }
        return captured;
    }

    /** Puts the look layer back exactly as {@link #capture()} found it. */
    @SuppressWarnings("unchecked")
    public void restore(@NonNull Map<String, Object> captured) {
        SharedPreferences.Editor editor = mStore.edit();
        for (String key : mStore.getAll().keySet()) {
            if (placeOfLookKey(key) != null) editor.remove(key);
        }
        for (Map.Entry<String, Object> entry : captured.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Set) editor.putStringSet(entry.getKey(), (Set<String>) value);
        }
        editor.apply();
    }

    /** The whole look layer as one string, so the editor's unsaved check can see it move. */
    @NonNull
    public String signature() {
        StringBuilder out = new StringBuilder(64);
        for (Map.Entry<String, Object> entry : new TreeMap<>(capture()).entrySet())
            out.append(entry.getKey()).append('=').append(entry.getValue()).append(';');
        return out.toString();
    }

    // ------------------------------------------------------------------------------- delegation

    @Override
    public Map<String, ?> getAll() {
        return mStore.getAll();
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        return mStore.getString(readKey(key), defValue);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return mStore.getStringSet(readKey(key), defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        return mStore.getInt(readKey(key), defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        return mStore.getLong(readKey(key), defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return mStore.getFloat(readKey(key), defValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return mStore.getBoolean(readKey(key), defValue);
    }

    @Override
    public boolean contains(String key) {
        return mStore.contains(readKey(key));
    }

    @Override
    public Editor edit() {
        return new ScopedEditor(mStore.edit());
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        mStore.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        mStore.unregisterOnSharedPreferenceChangeListener(listener);
    }

    /** Sends every scopable key to the place the editor is holding open, and the rest straight on. */
    private final class ScopedEditor implements Editor {
        @NonNull private final Editor mEditor;

        ScopedEditor(@NonNull Editor editor) {
            mEditor = editor;
        }

        @Override public Editor putString(String key, @Nullable String value) {
            mEditor.putString(writeKey(key), value);
            return this;
        }

        @Override public Editor putStringSet(String key, @Nullable Set<String> values) {
            mEditor.putStringSet(writeKey(key), values);
            return this;
        }

        @Override public Editor putInt(String key, int value) {
            mEditor.putInt(writeKey(key), value);
            return this;
        }

        @Override public Editor putLong(String key, long value) {
            mEditor.putLong(writeKey(key), value);
            return this;
        }

        @Override public Editor putFloat(String key, float value) {
            mEditor.putFloat(writeKey(key), value);
            return this;
        }

        @Override public Editor putBoolean(String key, boolean value) {
            mEditor.putBoolean(writeKey(key), value);
            return this;
        }

        @Override public Editor remove(String key) {
            mEditor.remove(writeKey(key));
            return this;
        }

        @Override public Editor clear() {
            mEditor.clear();
            return this;
        }

        @Override public boolean commit() {
            return mEditor.commit();
        }

        @Override public void apply() {
            mEditor.apply();
        }
    }
}
