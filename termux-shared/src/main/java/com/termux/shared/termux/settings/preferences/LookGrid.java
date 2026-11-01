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
 * Every look value in one grid (ADR 0017, the kind row ADR 0018 §3): two axes, six cells.
 *
 * <pre>
 *                 all glass                  this element
 *   everywhere    look.&lt;p&gt;  (Base)          look.&lt;p&gt;.&lt;element&gt;
 *   this kind     look.&lt;p&gt;@kind:&lt;kind&gt;     look.&lt;p&gt;.&lt;element&gt;@kind:&lt;kind&gt;
 *   this page     look.&lt;p&gt;@&lt;page&gt;          look.&lt;p&gt;.&lt;element&gt;@&lt;page&gt;
 * </pre>
 *
 * <p>A value is read from the first cell that holds one: the element on this page, all glass on
 * this page, the element on this page's kind, all glass on that kind, the element everywhere,
 * Base, then the value shipped for that kind. A key present at a cell is that cell's value; an
 * absent key follows; Reset deletes the key. Vibrancy and Margin have only the all-glass column
 * and no kind row. A key names a page or a kind, never both. Clamps live here, once.</p>
 *
 * <p>The kind of a page comes from {@link PageKinds} and a kind's shipped value from
 * {@link ShippedLooks}, both supplied at construction; {@link #install} sets the ones every grid
 * built with {@link #LookGrid(Store)} gets, so this module needs no app class.</p>
 *
 * <p>Who writes which cell: a setter named for an element (the dock's opacity, the status bar's
 * blur) writes that element's everywhere-cell when it holds one and Base when it follows; the
 * unnamed Base setters write Base, and only the editor's chips name a page or another cell.</p>
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
        MARGIN("margin", 0, TermuxPreferenceConstants.TERMUX_APP.MAX_SURFACE_HORIZONTAL_INSET,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP, false);

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

    /** The kinds of page (ADR 0018 §3): a kind's cells hold for every page of it. */
    public enum Kind {
        HOME("home"), TERMINAL("terminal"), DISPLAY("display");

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

    /** The kind of a page, from the wall. */
    public interface PageKinds {
        /** The kind of page {@code pageId}; null for the wall itself or a page it does not know. */
        @Nullable Kind kindOf(int pageId);
    }

    /** The value a kind ships with where it differs from the property's own shipped value. */
    public interface ShippedLooks {
        /**
         * The shipped value of {@code property} for {@code element} (null: all glass) on
         * {@code kind} (null: no kind), or null for the property's own {@link Property#shipped}.
         */
        @Nullable Integer shipped(@NonNull Property property, @Nullable Element element,
                                  @Nullable Kind kind);
    }

    /** The cell a value came from, in read order. */
    public enum Cell { ELEMENT_ON_PAGE, PAGE, ELEMENT_ON_KIND, KIND, ELEMENT, BASE, SHIPPED }

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

    @Nullable private static volatile PageKinds sPageKinds;
    @Nullable private static volatile ShippedLooks sShippedLooks;

    /**
     * The page kinds and kind-shipped values every grid built with {@link #LookGrid(Store)} gets:
     * the app's wall and shipped Looks, set once at start-up. Until then a page has no kind and
     * every kind ships the property's own value.
     */
    public static void install(@Nullable PageKinds kinds, @Nullable ShippedLooks shipped) {
        sPageKinds = kinds;
        sShippedLooks = shipped;
    }

    @NonNull private final Store mStore;
    @Nullable private final PageKinds mKinds;
    @Nullable private final ShippedLooks mShipped;

    public LookGrid(@NonNull Store store) {
        this(store, sPageKinds, sShippedLooks);
    }

    public LookGrid(@NonNull Store store, @Nullable PageKinds kinds, @Nullable ShippedLooks shipped) {
        mStore = store;
        mKinds = kinds;
        mShipped = shipped;
    }

    /** The kind of page {@code page}; null for everywhere, the wall or a page the wall lacks. */
    @Nullable
    public Kind kindOf(int page) {
        PageKinds kinds = mKinds;
        return page >= 0 && kinds != null ? kinds.kindOf(page) : null;
    }

    /**
     * Whether {@code element} holds a value of its own for {@code property} on {@code page}: in
     * the element's cell on that page, on the page's kind, or everywhere. False means the element
     * follows all glass there (the "linked" state the keyboard's stack reads).
     */
    public boolean holdsOwn(@NonNull Property property, @Nullable Element element, int page) {
        if (element == null || !property.perElement) return false;
        Kind kind = kindOf(page);
        if (kind != null && mStore.contains(key(property, element, kind))) return true;
        return mStore.contains(key(property, element, NO_PAGE));
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

    /**
     * The key of one kind's cell; a null kind is everywhere.
     *
     * @throws IllegalArgumentException for an element on, or a kind of, Vibrancy or Margin
     */
    @NonNull
    public static String key(@NonNull Property property, @Nullable Element element,
                             @Nullable Kind kind) {
        if (kind != null && !property.perElement)
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
        if (kind != null) return property.perElement ? new Key(property, element, kind) : null;
        return new Key(property, element, page);
    }

    /**
     * The value at (property, element, page) and the cell it came from: the element on this page,
     * all glass on this page, the element on the page's kind, all glass on that kind, the element
     * everywhere, Base, then the value shipped for that kind. Grain absent from every cell (and
     * not shipped apart for the kind) follows the Frost stop the same place resolves to.
     */
    @NonNull
    public Resolved resolve(@NonNull Property property, @Nullable Element element, int page) {
        return resolveAt(property, element, page, kindOf(page));
    }

    /** The value at (property, element) for every page of {@code kind}: the kind's row down. */
    @NonNull
    public Resolved resolve(@NonNull Property property, @Nullable Element element,
                            @Nullable Kind kind) {
        return resolveAt(property, element, NO_PAGE, kind);
    }

    /** The value a cell shows: from its page, its kind, or everywhere down. */
    @NonNull
    public Resolved resolve(@NonNull Key cell) {
        return cell.kind != null ? resolve(cell.property, cell.element, cell.kind)
            : resolve(cell.property, cell.element, cell.page);
    }

    @NonNull
    private Resolved resolveAt(@NonNull Property property, @Nullable Element element, int page,
                               @Nullable Kind kind) {
        if (!property.perElement) {
            element = null;
            kind = null;
        }
        // The page tier is retired (ADR 0020 §2): a page reads its kind's cells, where its
        // values are written and where step 17 folded the Start-in page's.
        if (element != null && kind != null) {
            Resolved found = read(key(property, element, kind), property, Cell.ELEMENT_ON_KIND);
            if (found != null) return found;
        }
        if (kind != null) {
            Resolved found = read(key(property, null, kind), property, Cell.KIND);
            if (found != null) return found;
        }
        if (element != null) {
            Resolved found = read(key(property, element, NO_PAGE), property, Cell.ELEMENT);
            if (found != null) return found;
        }
        Resolved found = read(key(property, null, NO_PAGE), property, Cell.BASE);
        if (found != null) return found;
        ShippedLooks looks = mShipped;
        Integer shipped = looks == null ? null : looks.shipped(property, element, kind);
        if (shipped != null) return new Resolved(property.clamp(shipped), Cell.SHIPPED);
        if (property == Property.GRAIN)
            return new Resolved(FrostStops.grainPercentFor(
                resolveAt(Property.FROST, element, page, kind).value), Cell.SHIPPED);
        return new Resolved(property.shipped, Cell.SHIPPED);
    }

    /** {@link #resolve}'s value alone. */
    public int value(@NonNull Property property, @Nullable Element element, int page) {
        return resolve(property, element, page).value;
    }

    /** {@link #resolve}'s value alone, for a kind. */
    public int value(@NonNull Property property, @Nullable Element element, @Nullable Kind kind) {
        return resolve(property, element, kind).value;
    }

    /** {@link #resolve}'s value alone, for a cell. */
    public int value(@NonNull Key cell) {
        return resolve(cell).value;
    }

    @Nullable
    private Resolved read(@NonNull String key, @NonNull Property property, @NonNull Cell cell) {
        if (!mStore.contains(key)) return null;
        return new Resolved(property.clamp(mStore.getInt(key, property.shipped)), cell);
    }

    /**
     * The cell a page's value lives in (ADR 0020 §2: no look is kept per page): the page's kind's
     * cell; Base for a property with no kind row (Vibrancy, Margin) or a page of no known kind.
     */
    @NonNull
    public String cellKey(@NonNull Property property, @Nullable Element element, int page) {
        if (page < 0) return key(property, element, NO_PAGE);
        Kind kind = property.perElement ? kindOf(page) : null;
        return kind != null ? key(property, element, kind) : key(property, element, NO_PAGE);
    }

    /** Whether the cell holds a value of its own. */
    public boolean has(@NonNull Property property, @Nullable Element element, int page) {
        return mStore.contains(cellKey(property, element, page));
    }

    /** Sets one cell, clamped: a page's value goes to its kind's cell. */
    public void write(@NonNull Property property, @Nullable Element element, int page, int value) {
        mStore.putInt(cellKey(property, element, page), property.clamp(value));
    }

    /** Resets one cell: it follows again. */
    public void clear(@NonNull Property property, @Nullable Element element, int page) {
        mStore.remove(cellKey(property, element, page));
    }

    /** Whether a kind's cell holds a value of its own. */
    public boolean has(@NonNull Property property, @Nullable Element element, @Nullable Kind kind) {
        return mStore.contains(key(property, element, kind));
    }

    /** Sets one kind's cell, clamped. */
    public void write(@NonNull Property property, @Nullable Element element, @Nullable Kind kind,
                      int value) {
        mStore.putInt(key(property, element, kind), property.clamp(value));
    }

    /** Resets one kind's cell: it follows again. */
    public void clear(@NonNull Property property, @Nullable Element element, @Nullable Kind kind) {
        mStore.remove(key(property, element, kind));
    }

    /** Whether {@code cell} holds a value of its own. */
    public boolean has(@NonNull Key cell) {
        return mStore.contains(cell.text());
    }

    /** Sets {@code cell}, clamped. */
    public void write(@NonNull Key cell, int value) {
        mStore.putInt(cell.text(), cell.property.clamp(value));
    }

    /** Resets {@code cell}: it follows again. */
    public void clear(@NonNull Key cell) {
        mStore.remove(cell.text());
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
