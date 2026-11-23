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
 * Every look value in one grid (ADR 0017, cut to two places by ADR 0031): Global, and one
 * element's own.
 *
 * <pre>
 *                 all glass                  this element
 *   everywhere    look.&lt;p&gt;  (Global)        look.&lt;p&gt;.&lt;element&gt;
 * </pre>
 *
 * <p>A value is read from the element's own cell if it holds one, else Global, else the shipped
 * value. The chrome wears one look on every page (ADR 0031 §1): nothing is read or written for a
 * page or a kind of page. Keys an older build wrote for one ({@code look.<p>@<page>},
 * {@code look.<p>@kind:<kind>}) still parse, so the reset that deletes them can find them, and are
 * never read. A key present at a cell is that cell's value; an absent key follows; Reset deletes
 * the key. Vibrancy and Margin have only the all-glass column. Clamps live here, once.</p>
 *
 * <p>Grain follows the Frost stop where nothing holds a grain: an element holding its own Frost
 * stop and no grain wears its stop's grain (2026-11-19), so a surface's own Blur brings its grain
 * with it.</p>
 *
 * <p>Pure over {@link Store}: {@link MemoryStore} for tests, {@link PrefsStore} on a device.</p>
 */
public final class LookGrid {

    /** No page named: the everywhere row. */
    public static final int NO_PAGE = -1;

    public static final String PREFIX = "look.";
    public static final String PAGE_SEPARATOR = "@";
    /** What follows {@link #PAGE_SEPARATOR} in a kind's key: {@code look.opacity@kind:home}. */
    public static final String KIND_PREFIX = "kind:";

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
        OPACITY("opacity", 0, 100, TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_GLOBAL_OPACITY, true),
        TINT("tint", 0, 100, TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_GLOBAL_TINT, true),
        TINT_HUE("tint_hue", TINT_HUE_PRIMARY, TINT_HUE_TERTIARY, TINT_HUE_PRIMARY, true),
        /** The refraction bend in dp, today's stored unit (see the W1 report: not the slider's). */
        DEPTH("depth", TermuxPreferenceConstants.TERMUX_APP.MIN_FANCIER_GLASS_BEND,
            TermuxPreferenceConstants.TERMUX_APP.MAX_FANCIER_GLASS_BEND,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_BEND, true),
        /** Retired by Shine: only the upgrade ({@code GlassControlsMigration}) reads and deletes it. */
        EDGE("edge", TermuxPreferenceConstants.TERMUX_APP.MIN_FANCIER_GLASS_DISPERSION,
            TermuxPreferenceConstants.TERMUX_APP.MAX_FANCIER_GLASS_DISPERSION,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_DISPERSION, true),
        /** Corner radius in dp, or {@link #CORNERS_AUTO}. */
        CORNERS("corners", CORNERS_AUTO, 40,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_GLOBAL_CORNER_RADIUS, true),
        /** Retired by Shine: only the upgrade reads and deletes it. */
        RIM("rim", RIM_OFF, RIM_GRADIENT, RIM_HAIRLINE, true),
        /** Shine, 0 to 100, app-wide: the light on the rim and lens ({@link GlassMaterial}). */
        SHINE("shine", GlassMaterial.MIN, GlassMaterial.MAX, GlassMaterial.SHINE_DEFAULT, false),
        VIBRANCY("vibrancy", TermuxPreferenceConstants.TERMUX_APP.MIN_SURFACE_GLASS_VIBRANCY,
            TermuxPreferenceConstants.TERMUX_APP.MAX_SURFACE_GLASS_VIBRANCY,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_GLASS_VIBRANCY, false),
        /** All glass only. */
        MARGIN("margin", 0, TermuxPreferenceConstants.TERMUX_APP.MAX_SURFACE_HORIZONTAL_INSET,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_GLOBAL_SIDE_GAP, false);

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

    /**
     * The kinds of page an older build kept a look for (ADR 0018 §3, retired by ADR 0031): only
     * so their keys parse and can be deleted.
     */
    public enum Kind {
        HOME("home"), TERMINAL("terminal"), DISPLAY("display"), ASSISTANT("assistant");

        public final String key;

        Kind(String key) { this.key = key; }

        @Nullable
        public static Kind forKey(@Nullable String key) {
            for (Kind kind : values()) {
                if (kind.key.equals(key)) return kind;
            }
            return null;
        }
    }

    /** The cell a value came from, in read order. */
    public enum Cell { ELEMENT, GLOBAL, SHIPPED }

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

    /**
     * A parsed grid key. {@code element} is null for all glass; {@code page} is NO_PAGE and
     * {@code kind} null for everywhere; a key names a page or a kind, never both.
     */
    public static final class Key {
        @NonNull public final Property property;
        @Nullable public final Element element;
        public final int page;
        @Nullable public final Kind kind;

        public Key(@NonNull Property property, @Nullable Element element, int page) {
            this.property = property;
            this.element = element;
            this.page = page;
            this.kind = null;
        }

        public Key(@NonNull Property property, @Nullable Element element, @Nullable Kind kind) {
            this.property = property;
            this.element = element;
            this.page = NO_PAGE;
            this.kind = kind;
        }

        /** This key's text in the store. */
        @NonNull
        public String text() {
            return kind != null ? key(property, element, kind) : key(property, element, page);
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

    /**
     * Whether {@code element} holds a value of its own for {@code property}. False means the
     * element follows Global (the "linked" state the keyboard's stack reads).
     */
    public boolean holdsOwn(@NonNull Property property, @Nullable Element element) {
        return element != null && property.perElement && mStore.contains(key(property, element));
    }

    @NonNull
    public Store store() {
        return mStore;
    }

    /**
     * The key of one cell: Global's for a null element, else the element's own.
     *
     * @throws IllegalArgumentException for an element on Vibrancy or Margin
     */
    @NonNull
    public static String key(@NonNull Property property, @Nullable Element element) {
        return key(property, element, NO_PAGE);
    }

    /**
     * The key of a cell on one page, as an older build wrote it; {@link #NO_PAGE} for the cell
     * every page reads. Only the reset and the migrations name a page.
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

    /**
     * The key of a kind's cell, as an older build wrote it; a null kind is the cell every page
     * reads. Only the reset and the migrations name a kind.
     *
     * @throws IllegalArgumentException for an element on Vibrancy or Margin, or a kind of Vibrancy
     */
    @NonNull
    public static String key(@NonNull Property property, @Nullable Element element,
                             @Nullable Kind kind) {
        if (kind != null && property == Property.VIBRANCY)
            throw new IllegalArgumentException(property.key + " has no kind row");
        String plain = key(property, element, NO_PAGE);
        return kind == null ? plain : plain + PAGE_SEPARATOR + KIND_PREFIX + kind.key;
    }

    /** A grid key read back, or null for anything that is not one. */
    @Nullable
    public static Key parse(@Nullable String key) {
        if (key == null || !key.startsWith(PREFIX)) return null;
        String rest = key.substring(PREFIX.length());
        int page = NO_PAGE;
        Kind kind = null;
        int at = rest.indexOf(PAGE_SEPARATOR);
        if (at >= 0 && rest.startsWith(KIND_PREFIX, at + 1)) {
            kind = Kind.forKey(rest.substring(at + 1 + KIND_PREFIX.length()));
            if (kind == null) return null;
            rest = rest.substring(0, at);
        } else if (at >= 0) {
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
        if (kind != null) return property != Property.VIBRANCY ? new Key(property, element, kind) : null;
        return new Key(property, element, page);
    }

    /**
     * The value of {@code property} for {@code element} (null: all glass) and the cell it came
     * from: the element's own, Global, then the shipped value. Grain no cell holds follows the
     * Frost stop: the element's own stop where it holds one, else the stop Global resolves to.
     */
    @NonNull
    public Resolved resolve(@NonNull Property property, @Nullable Element element) {
        if (!property.perElement)
            element = null;
        if (element != null) {
            Resolved found = read(key(property, element), property, Cell.ELEMENT);
            if (found != null) return found;
            if (property == Property.GRAIN && mStore.contains(key(Property.FROST, element)))
                return new Resolved(FrostStops.grainPercentFor(
                    resolve(Property.FROST, element).value), Cell.ELEMENT);
        }
        Resolved found = read(key(property, null), property, Cell.GLOBAL);
        if (found != null) return found;
        if (property == Property.GRAIN)
            return new Resolved(FrostStops.grainPercentFor(
                resolve(Property.FROST, null).value), Cell.SHIPPED);
        return new Resolved(property.shipped, Cell.SHIPPED);
    }

    /** {@link #resolve}'s value alone. */
    public int value(@NonNull Property property, @Nullable Element element) {
        return resolve(property, element).value;
    }

    @Nullable
    private Resolved read(@NonNull String key, @NonNull Property property, @NonNull Cell cell) {
        if (!mStore.contains(key)) return null;
        return new Resolved(property.clamp(mStore.getInt(key, property.shipped)), cell);
    }

    /** Whether the cell holds a value of its own. */
    public boolean has(@NonNull Property property, @Nullable Element element) {
        return mStore.contains(key(property, element));
    }

    /** Sets one cell, clamped. */
    public void write(@NonNull Property property, @Nullable Element element, int value) {
        mStore.putInt(key(property, element), property.clamp(value));
    }

    /** Resets one cell: it follows again. */
    public void clear(@NonNull Property property, @Nullable Element element) {
        mStore.remove(key(property, element));
    }

    /** {@link #resolve} for a cell; its page or kind, if an older key named one, is not read. */
    @NonNull
    public Resolved resolve(@NonNull Key cell) {
        return resolve(cell.property, cell.element);
    }

    /** {@link #value} for a cell. */
    public int value(@NonNull Key cell) {
        return value(cell.property, cell.element);
    }

    /** {@link #has} for a cell. */
    public boolean has(@NonNull Key cell) {
        return has(cell.property, cell.element);
    }

    /** {@link #write} for a cell. */
    public void write(@NonNull Key cell, int value) {
        write(cell.property, cell.element, value);
    }

    /** {@link #clear} for a cell. */
    public void clear(@NonNull Key cell) {
        clear(cell.property, cell.element);
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
