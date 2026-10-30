package com.termux.shared.termux.settings.preferences;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every look value in one grid (ADR 0017): two axes, four cells.
 *
 * <pre>
 *                 all glass                  this element
 *   everywhere    look.&lt;p&gt;  (Base)          look.&lt;p&gt;.&lt;element&gt;
 *   this page     look.&lt;p&gt;@&lt;page&gt;          look.&lt;p&gt;.&lt;element&gt;@&lt;page&gt;
 * </pre>
 *
 * <p>A value is read from the first cell that holds one: the element on this page, all glass on
 * this page, the element everywhere, Base, then the shipped value. A key present at a cell is
 * that cell's value; an absent key follows; Reset deletes the key. Vibrancy and Margin have only
 * the all-glass column. Clamps live here, once.</p>
 *
 * <p>Pure over {@link Store}: {@link MemoryStore} for tests, {@link PrefsStore} on a device.</p>
 */
public final class LookGrid {

    /** No page named: the everywhere row. */
    public static final int NO_PAGE = -1;

    public static final String PREFIX = "look.";
    public static final String PAGE_SEPARATOR = "@";

    /** Corners' "follow the Style" value: the Style formula decides (resolveAutoCornerRadiusDp). */
    public static final int CORNERS_AUTO = -1;

    /** Tint colour, as stored. */
    public static final int TINT_HUE_PRIMARY = 0;
    public static final int TINT_HUE_SECONDARY = 1;
    public static final int TINT_HUE_TERTIARY = 2;

    /** Rim, as stored. */
    public static final int RIM_OFF = 0;
    public static final int RIM_HAIRLINE = 1;
    public static final int RIM_GRADIENT = 2;

    /** The look properties: range, shipped value and whether an element may hold its own. */
    public enum Property {
        /** The Frost stop, 0 to {@link FrostStops#LAST}; Clear's stop ships. */
        FROST("frost", 0, FrostStops.LAST, FrostStops.CLEAR, true),
        /** Grain in percent; absent everywhere, it follows the Frost stop ({@link #resolve}). */
        GRAIN("grain", 0, 100, FrostStops.grainPercentFor(FrostStops.CLEAR), true),
        OPACITY("opacity", 0, 100, TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY, true),
        TINT("tint", 0, 100, TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_TINT, true),
        TINT_HUE("tint_hue", TINT_HUE_PRIMARY, TINT_HUE_TERTIARY, TINT_HUE_PRIMARY, true),
        /** The refraction bend in dp, today's stored unit (see the W1 report: not the slider's). */
        DEPTH("depth", TermuxPreferenceConstants.TERMUX_APP.MIN_FANCIER_GLASS_BEND,
            TermuxPreferenceConstants.TERMUX_APP.MAX_FANCIER_GLASS_BEND,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_BEND, true),
        /** The dispersion, 0 to 100: the Edge slider's own unit. */
        EDGE("edge", TermuxPreferenceConstants.TERMUX_APP.MIN_FANCIER_GLASS_DISPERSION,
            TermuxPreferenceConstants.TERMUX_APP.MAX_FANCIER_GLASS_DISPERSION,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_DISPERSION, true),
        /** Corner radius in dp, or {@link #CORNERS_AUTO}. */
        CORNERS("corners", CORNERS_AUTO, 40,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS, true),
        RIM("rim", RIM_OFF, RIM_GRADIENT, RIM_HAIRLINE, true),
        VIBRANCY("vibrancy", TermuxPreferenceConstants.TERMUX_APP.MIN_SURFACE_GLASS_VIBRANCY,
            TermuxPreferenceConstants.TERMUX_APP.MAX_SURFACE_GLASS_VIBRANCY,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_GLASS_VIBRANCY, false),
        MARGIN("margin", 0, 40, TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP,
            false);

        public final String key;
        public final int min;
        public final int max;
        public final int shipped;
        /** False for the Where-level properties, which have no element column. */
        public final boolean perElement;

        Property(String key, int min, int max, int shipped, boolean perElement) {
            this.key = key;
            this.min = min;
            this.max = max;
            this.shipped = shipped;
            this.perElement = perElement;
        }

        public int clamp(int value) {
            return Math.max(min, Math.min(max, value));
        }

        @Nullable
        public static Property forKey(@Nullable String key) {
            for (Property property : values()) {
                if (property.key.equals(key)) return property;
            }
            return null;
        }
    }

    /** The glass elements. */
    public enum Element {
        STATUS("status"), DOCK("dock"), KEYBOARD("keyboard"), TERMINAL("terminal"),
        WIDGETS("widgets"), SHEETS("sheets");

        public final String key;

        Element(String key) { this.key = key; }

        @Nullable
        public static Element forKey(@Nullable String key) {
            for (Element element : values()) {
                if (element.key.equals(key)) return element;
            }
            return null;
        }
    }

    /** The cell a value came from, in read order. */
    public enum Cell { ELEMENT_ON_PAGE, PAGE, ELEMENT, BASE, SHIPPED }

    /** A resolved value and the cell it was read from. */
    public static final class Resolved {
        public final int value;
        @NonNull public final Cell cell;

        public Resolved(int value, @NonNull Cell cell) {
            this.value = value;
            this.cell = cell;
        }

        @NonNull
        @Override
        public String toString() {
            return value + "@" + cell;
        }
    }

    /** A parsed grid key. {@code element} is null for all glass, {@code page} NO_PAGE for everywhere. */
    public static final class Key {
        @NonNull public final Property property;
        @Nullable public final Element element;
        public final int page;

        public Key(@NonNull Property property, @Nullable Element element, int page) {
            this.property = property;
            this.element = element;
            this.page = page;
        }
    }

    // ------------------------------------------------------------------------------- the store

    /** The few operations the grid and its migration need of a preference store. */
    public interface Store {
        boolean contains(@NonNull String key);
        int getInt(@NonNull String key, int defaultValue);
        @Nullable String getString(@NonNull String key, @Nullable String defaultValue);
        boolean getBoolean(@NonNull String key, boolean defaultValue);
        void putInt(@NonNull String key, int value);
        void putString(@NonNull String key, @NonNull String value);
        void putBoolean(@NonNull String key, boolean value);
        void remove(@NonNull String key);
        @NonNull Set<String> keys();
    }

    /** An in-memory store, for tests. */
    public static final class MemoryStore implements Store {
        private final Map<String, Object> mValues = new HashMap<>();

        @Override public boolean contains(@NonNull String key) { return mValues.containsKey(key); }

        @Override public int getInt(@NonNull String key, int defaultValue) {
            Object value = mValues.get(key);
            return value instanceof Integer ? (Integer) value : defaultValue;
        }

        @Nullable @Override public String getString(@NonNull String key, @Nullable String defaultValue) {
            Object value = mValues.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @Override public boolean getBoolean(@NonNull String key, boolean defaultValue) {
            Object value = mValues.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        @Override public void putInt(@NonNull String key, int value) { mValues.put(key, value); }
        @Override public void putString(@NonNull String key, @NonNull String value) { mValues.put(key, value); }
        @Override public void putBoolean(@NonNull String key, boolean value) { mValues.put(key, value); }
        @Override public void remove(@NonNull String key) { mValues.remove(key); }
        @NonNull @Override public Set<String> keys() { return new HashSet<>(mValues.keySet()); }
    }

    /**
     * The device's store. Every write applies at once; a read of a key holding another type
     * answers the default rather than throwing (a hand-edited or older value must not crash home).
     */
    public static final class PrefsStore implements Store {
        @Nullable private final SharedPreferences mPrefs;

        public PrefsStore(@Nullable SharedPreferences prefs) { mPrefs = prefs; }

        /** What a read sees: the store, or the Look shown over it while a drag previews one. */
        @Nullable
        private SharedPreferences reads() {
            return com.termux.shared.settings.preferences.SharedPreferencesPreview.readsFor(mPrefs);
        }

        @Override public boolean contains(@NonNull String key) {
            SharedPreferences reads = reads();
            return reads != null && reads.contains(key);
        }

        @Override public int getInt(@NonNull String key, int defaultValue) {
            SharedPreferences reads = reads();
            if (reads == null) return defaultValue;
            try { return reads.getInt(key, defaultValue); } catch (ClassCastException e) { return defaultValue; }
        }

        @Nullable @Override public String getString(@NonNull String key, @Nullable String defaultValue) {
            SharedPreferences reads = reads();
            if (reads == null) return defaultValue;
            try { return reads.getString(key, defaultValue); } catch (ClassCastException e) { return defaultValue; }
        }

        @Override public boolean getBoolean(@NonNull String key, boolean defaultValue) {
            SharedPreferences reads = reads();
            if (reads == null) return defaultValue;
            try { return reads.getBoolean(key, defaultValue); } catch (ClassCastException e) { return defaultValue; }
        }

        @Override public void putInt(@NonNull String key, int value) {
            if (mPrefs != null) mPrefs.edit().putInt(key, value).apply();
        }

        @Override public void putString(@NonNull String key, @NonNull String value) {
            if (mPrefs != null) mPrefs.edit().putString(key, value).apply();
        }

        @Override public void putBoolean(@NonNull String key, boolean value) {
            if (mPrefs != null) mPrefs.edit().putBoolean(key, value).apply();
        }

        @Override public void remove(@NonNull String key) {
            if (mPrefs != null && mPrefs.contains(key)) mPrefs.edit().remove(key).apply();
        }

        @NonNull @Override public Set<String> keys() {
            SharedPreferences reads = reads();
            return reads == null ? new HashSet<>() : new HashSet<>(reads.getAll().keySet());
        }
    }

    // -------------------------------------------------------------------------------- the grid

    @NonNull private final Store mStore;

    public LookGrid(@NonNull Store store) {
        mStore = store;
    }

    @NonNull
    public Store store() {
        return mStore;
    }

    /**
     * The key of one cell. An element on a Where-level property is refused: it has no such cell.
     *
     * @throws IllegalArgumentException for an element on Vibrancy or Margin
     */
    @NonNull
    public static String key(@NonNull Property property, @Nullable Element element, int page) {
        if (element != null && !property.perElement)
            throw new IllegalArgumentException(property.key + " has no element column");
        StringBuilder key = new StringBuilder(PREFIX).append(property.key);
        if (element != null) key.append('.').append(element.key);
        if (page >= 0) key.append(PAGE_SEPARATOR).append(page);
        return key.toString();
    }

    /** A grid key read back, or null for anything that is not one. */
    @Nullable
    public static Key parse(@Nullable String key) {
        if (key == null || !key.startsWith(PREFIX)) return null;
        String rest = key.substring(PREFIX.length());
        int page = NO_PAGE;
        int at = rest.indexOf(PAGE_SEPARATOR);
        if (at >= 0) {
            String id = rest.substring(at + 1);
            if (id.isEmpty()) return null;
            for (int i = 0; i < id.length(); i++) {
                if (!Character.isDigit(id.charAt(i))) return null;
            }
            try {
                page = Integer.parseInt(id);
            } catch (NumberFormatException e) {
                return null;
            }
            rest = rest.substring(0, at);
        }
        Element element = null;
        int dot = rest.indexOf('.');
        if (dot >= 0) {
            element = Element.forKey(rest.substring(dot + 1));
            if (element == null) return null;
            rest = rest.substring(0, dot);
        }
        Property property = Property.forKey(rest);
        if (property == null || (element != null && !property.perElement)) return null;
        return new Key(property, element, page);
    }

    /**
     * The value at (property, element, page) and the cell it came from: the element on this page,
     * all glass on this page, the element everywhere, Base, then the shipped value. Grain absent
     * from every cell follows the Frost stop the same place resolves to.
     */
    @NonNull
    public Resolved resolve(@NonNull Property property, @Nullable Element element, int page) {
        if (!property.perElement) element = null;
        if (element != null && page >= 0) {
            Resolved found = read(property, element, page, Cell.ELEMENT_ON_PAGE);
            if (found != null) return found;
        }
        if (page >= 0) {
            Resolved found = read(property, null, page, Cell.PAGE);
            if (found != null) return found;
        }
        if (element != null) {
            Resolved found = read(property, element, NO_PAGE, Cell.ELEMENT);
            if (found != null) return found;
        }
        Resolved found = read(property, null, NO_PAGE, Cell.BASE);
        if (found != null) return found;
        if (property == Property.GRAIN)
            return new Resolved(FrostStops.grainPercentFor(resolve(Property.FROST, element, page).value),
                Cell.SHIPPED);
        return new Resolved(property.shipped, Cell.SHIPPED);
    }

    /** {@link #resolve}'s value alone. */
    public int value(@NonNull Property property, @Nullable Element element, int page) {
        return resolve(property, element, page).value;
    }

    @Nullable
    private Resolved read(Property property, @Nullable Element element, int page, Cell cell) {
        String key = key(property, element, page);
        if (!mStore.contains(key)) return null;
        return new Resolved(property.clamp(mStore.getInt(key, property.shipped)), cell);
    }

    /** Whether the cell holds a value of its own. */
    public boolean has(@NonNull Property property, @Nullable Element element, int page) {
        return mStore.contains(key(property, element, page));
    }

    /** Sets one cell, clamped. */
    public void write(@NonNull Property property, @Nullable Element element, int page, int value) {
        mStore.putInt(key(property, element, page), property.clamp(value));
    }

    /** Resets one cell: it follows again. */
    public void clear(@NonNull Property property, @Nullable Element element, int page) {
        mStore.remove(key(property, element, page));
    }

    /** Drops every cell a page holds, all glass and element alike. */
    public void clearPage(int page) {
        if (page < 0) return;
        for (String stored : gridKeys()) {
            Key key = parse(stored);
            if (key != null && key.page == page) mStore.remove(stored);
        }
    }

    /** Drops every cell an element holds, everywhere and on every page. */
    public void clearElement(@NonNull Element element) {
        for (String stored : gridKeys()) {
            Key key = parse(stored);
            if (key != null && key.element == element) mStore.remove(stored);
        }
    }

    /** Every grid key the store holds. */
    @NonNull
    public List<String> gridKeys() {
        List<String> keys = new ArrayList<>();
        for (String stored : mStore.keys()) {
            if (parse(stored) != null) keys.add(stored);
        }
        return keys;
    }
}
