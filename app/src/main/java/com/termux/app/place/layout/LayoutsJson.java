package com.termux.app.place.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the five layout documents (ADR 0020 §5). Field names, types and units are the
 * flat keys' own (PlaceLayoutStore before step 17), so the fold of step 17 is a copy, not a
 * translation:
 *
 * <pre>
 * Kind documents ("home", "terminal", "terminal_minimal", "display"):
 *   top level (both orientations)
 *     version                 int      document version, {@link KindLayout#VERSION}
 *     dock_design             string   DockStyle storage value
 *     search_field            bool
 *     drawer_tile             bool
 *     drawer_tile_position    string   DrawerTilePolicy persisted value
 *     keyboard_shown          bool     the kind's keyboard rest (ADR 0018 §4; spec §9.2)
 *     keyboard_rows           int      rows shown at rest, 0 = all
 *     keyboard_open           bool     whether the keyboard was open when last left
 *   "portrait" / "landscape"
 *     status_bar, apps_row, extra_keys     string  an edge ("top", "bottom", "left", "right")
 *                                                  or "hidden"
 *     az_bar                  string   an edge
 *     az_row                  bool     whether the A-Z bar shows
 *     &lt;element&gt;_order         int      stack position (status_bar, apps_row, az_bar, extra_keys)
 *     &lt;element&gt;_under_keyboard bool   (apps_row, az_bar, extra_keys)
 *     &lt;element&gt;_restore_edge  string   where a put-away bar comes back (status_bar, apps_row,
 *                                       extra_keys)
 *     keyboard_mode           string   KeyboardMode storage value
 *     keyboard_form           string   KeyboardForm storage value
 *     bar_order               string   the Dock card's bar order
 *     status_compact          bool     the status bar's rest (ADR 0018 §4)
 *     dress.&lt;element&gt;        bool     whether the kind shows the element (was place.dress.*)
 *     widget_columns          int      "home" only
 *     widget_rows             int      "home" only
 *
 * "sizes": "portrait" / "landscape"
 *     dock_height             float    multiple of the unscaled apps row
 *     keyboard_height         float    multiple of the unscaled keyboard
 *     keyboard_chin           int      dp
 *     wall_strip_height       int      dp
 * </pre>
 *
 * An absent field reads its shipped default. Unknown fields are kept on rewrite and ignored on
 * read. A document that does not parse is replaced by its shipped default, the others untouched.
 */
public final class LayoutsJson {

    public static final String KEY_SIZES = "sizes";
    public static final String VERSION = "version";
    public static final String PORTRAIT = "portrait";
    public static final String LANDSCAPE = "landscape";

    // Layout-wide fields.
    public static final String DOCK_DESIGN = "dock_design";
    public static final String SEARCH_FIELD = "search_field";
    public static final String DRAWER_TILE = "drawer_tile";
    public static final String DRAWER_TILE_POSITION = "drawer_tile_position";
    public static final String KEYBOARD_SHOWN = "keyboard_shown";
    public static final String KEYBOARD_ROWS = "keyboard_rows";
    public static final String KEYBOARD_OPEN = "keyboard_open";

    // Per-orientation fields.
    public static final String AZ_ROW = "az_row";
    public static final String KEYBOARD_MODE = "keyboard_mode";
    public static final String KEYBOARD_FORM = "keyboard_form";
    public static final String BAR_ORDER = "bar_order";
    public static final String STATUS_COMPACT = "status_compact";
    /**
     * The edge the status bar stands on, one value for every page (ADR 0023 §1). Held by the Home
     * document whatever kind asks ({@link #SHARED_ORIENTATION_FIELDS}); a kind's own
     * {@code status_bar} keeps only its put-away mark.
     */
    public static final String STATUS_EDGE = "status_edge";
    public static final String WIDGET_COLUMNS = "widget_columns";
    public static final String WIDGET_ROWS = "widget_rows";
    public static final String DRESS_PREFIX = "dress.";
    public static final String ORDER_SUFFIX = "_order";
    public static final String UNDER_KEYBOARD_SUFFIX = "_under_keyboard";
    public static final String RESTORE_EDGE_SUFFIX = "_restore_edge";

    // Sizes.
    public static final String DOCK_HEIGHT = "dock_height";
    public static final String KEYBOARD_HEIGHT = "keyboard_height";
    public static final String KEYBOARD_CHIN = "keyboard_chin";
    public static final String WALL_STRIP_HEIGHT = "wall_strip_height";

    /** The values a kind document holds once for both orientations. */
    public static final List<String> WIDE_FIELDS = Collections.unmodifiableList(java.util.Arrays.asList(
        DOCK_DESIGN, SEARCH_FIELD, DRAWER_TILE, DRAWER_TILE_POSITION,
        KEYBOARD_SHOWN, KEYBOARD_ROWS, KEYBOARD_OPEN));

    /**
     * The values every page reads from the Home document (ADR 0023 §1): the dock's design, search
     * field and drawer tile, and where the tile stands. A kind document holds none of them.
     */
    public static final List<String> SHARED_WIDE_FIELDS = Collections.unmodifiableList(
        java.util.Arrays.asList(DOCK_DESIGN, SEARCH_FIELD, DRAWER_TILE, DRAWER_TILE_POSITION));

    /**
     * The per-orientation values every page reads from the Home document (ADR 0023 §1): the status
     * bar's edge and whether it rests compact.
     */
    public static final List<String> SHARED_ORIENTATION_FIELDS = Collections.unmodifiableList(
        java.util.Arrays.asList(STATUS_EDGE, STATUS_COMPACT));

    /** The four sizes, per orientation, in the {@code sizes} document. */
    public static final List<String> SIZE_FIELDS = Collections.unmodifiableList(java.util.Arrays.asList(
        DOCK_HEIGHT, KEYBOARD_HEIGHT, KEYBOARD_CHIN, WALL_STRIP_HEIGHT));

    /** The widget grid, which only the Home document holds. */
    public static final List<String> GRID_FIELDS = Collections.unmodifiableList(java.util.Arrays.asList(
        WIDGET_COLUMNS, WIDGET_ROWS));

    /** Every per-orientation arrangement field but the grid and the visibility. */
    public static final List<String> ORIENTATION_FIELDS;

    static {
        List<String> fields = new ArrayList<>();
        for (Element element : Element.values()) fields.add(element.storageKey());
        fields.add(AZ_ROW);
        for (Element element : Element.values()) fields.add(element.storageKey() + ORDER_SUFFIX);
        for (Element element : Element.values())
            fields.add(element.storageKey() + UNDER_KEYBOARD_SUFFIX);
        for (Element element : Element.values())
            fields.add(element.storageKey() + RESTORE_EDGE_SUFFIX);
        fields.add(KEYBOARD_MODE);
        fields.add(KEYBOARD_FORM);
        fields.add(BAR_ORDER);
        fields.add(STATUS_COMPACT);
        ORIENTATION_FIELDS = Collections.unmodifiableList(fields);
    }

    /** The field saying whether a kind shows {@code element}. */
    @NonNull
    public static String dressField(@NonNull Element element) {
        return DRESS_PREFIX + element.storageKey();
    }

    private LayoutsJson() {}

    /** Where documents are read from and logged to; the Android store and the tests each give one. */
    public interface Source {
        @Nullable String read(@NonNull String key);
        void warn(@NonNull String message);
    }

    /**
     * The shipped document for {@code kind}: what a fresh install wears. Every field is absent
     * and reads its default, the kind's visibility included
     * ({@link PlaceLayoutStore#kindShowsByDefault}), so a fresh install writes nothing and "holds
     * a value" keeps meaning the user (or a migration) set one.
     */
    @NonNull
    public static KindLayout shippedDefault(@NonNull LayoutKind kind) {
        return KindLayout.EMPTY;
    }

    /** Reads all five documents; a missing one is its shipped default, a bad one too, logged. */
    @NonNull
    public static Layouts read(@NonNull Source source) {
        Map<LayoutKind, KindLayout> kinds = new EnumMap<>(LayoutKind.class);
        for (LayoutKind kind : LayoutKind.values()) {
            // A retired kind's document (ADR 0025) is never read: the Workspace's is worn.
            if (kind.retired()) continue;
            String json = source.read(kind.key);
            if (json == null) continue;
            try {
                kinds.put(kind, parseKind(json));
            } catch (JSONException e) {
                source.warn("The " + kind.key + " layout does not read (" + e.getMessage()
                    + "); the shipped one is worn instead");
            }
        }
        Sizes sizes = Sizes.EMPTY;
        String json = source.read(KEY_SIZES);
        if (json != null) {
            try {
                sizes = parseSizes(json);
            } catch (JSONException e) {
                source.warn("The sizes layout does not read (" + e.getMessage()
                    + "); the shipped sizes are worn instead");
            }
        }
        return new Layouts(kinds, sizes);
    }

    @NonNull
    public static KindLayout parseKind(@NonNull String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (!o.has(VERSION)) throw new JSONException("no version");
        int version = o.getInt(VERSION);
        LayoutFields portrait = section(o.optJSONObject(PORTRAIT), PORTRAIT, o);
        LayoutFields landscape = section(o.optJSONObject(LANDSCAPE), LANDSCAPE, o);
        Map<String, Object> wide = new LinkedHashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (VERSION.equals(key) || PORTRAIT.equals(key) || LANDSCAPE.equals(key)) continue;
            wide.put(key, plain(o.get(key)));
        }
        return new KindLayout(version, LayoutFields.of(wide), portrait, landscape);
    }

    @NonNull
    public static Sizes parseSizes(@NonNull String json) throws JSONException {
        return new Sizes(parseKind(json));
    }

    @NonNull
    private static LayoutFields section(@Nullable JSONObject section, @NonNull String name,
                                        @NonNull JSONObject parent) throws JSONException {
        if (section == null) {
            if (parent.has(name)) throw new JSONException(name + " is not an object");
            return LayoutFields.EMPTY;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        Iterator<String> keys = section.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            values.put(key, plain(section.get(key)));
        }
        return LayoutFields.of(values);
    }

    /** A JSON value as a field holds it: an unknown nested value is kept as its JSON. */
    @Nullable
    private static Object plain(@Nullable Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        return value;
    }

    @NonNull
    public static String write(@NonNull KindLayout layout) {
        try {
            JSONObject o = new JSONObject();
            o.put(VERSION, layout.version);
            for (Map.Entry<String, Object> e : layout.wide().asMap().entrySet())
                o.put(e.getKey(), json(e.getValue()));
            o.put(PORTRAIT, sectionJson(layout.portrait()));
            o.put(LANDSCAPE, sectionJson(layout.landscape()));
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @NonNull
    public static String write(@NonNull Sizes sizes) {
        return write(sizes.document());
    }

    @NonNull
    private static JSONObject sectionJson(@NonNull LayoutFields fields) throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, Object> e : fields.asMap().entrySet())
            o.put(e.getKey(), json(e.getValue()));
        return o;
    }

    @NonNull
    private static Object json(@NonNull Object value) {
        if (value instanceof Float) return ((Float) value).doubleValue();
        if (value instanceof JSONObject || value instanceof JSONArray) return value;
        return value;
    }

    /**
     * Checks a document handed in from outside ({@code launcherctl layout set}): known fields
     * must have their type and, where the set is closed, a value the launcher reads; unknown fields are not checked. Null when it is fine, else the message naming the field.
     */
    @Nullable
    public static String validate(@NonNull LayoutKind kind, @NonNull String json) {
        KindLayout layout;
        try {
            layout = parseKind(json);
        } catch (JSONException e) {
            return "not a layout document: " + e.getMessage();
        }
        for (String key : WIDE_FIELDS) {
            String error = typeError(key, layout.wide().get(key));
            if (error != null) return error;
        }
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            LayoutFields fields = layout.orientation(orientation);
            for (String key : fields.keys()) {
                if (kind != LayoutKind.HOME && GRID_FIELDS.contains(key))
                    return orientation.storageValue() + "." + key + ": only the home layout has a widget grid";
                String error = typeError(key, fields.get(key));
                if (error != null) return orientation.storageValue() + "." + error;
            }
        }
        return null;
    }

    @Nullable
    private static String typeError(@NonNull String key, @Nullable Object value) {
        if (value == null) return null;
        Class<?> wanted;
        if (BAR_ORDER.equals(key)) {
            wanted = String.class;
        } else if (key.startsWith(DRESS_PREFIX) || key.endsWith(UNDER_KEYBOARD_SUFFIX)
            || SEARCH_FIELD.equals(key) || DRAWER_TILE.equals(key) || KEYBOARD_SHOWN.equals(key)
            || KEYBOARD_OPEN.equals(key) || AZ_ROW.equals(key) || STATUS_COMPACT.equals(key)) {
            wanted = Boolean.class;
        } else if (STATUS_EDGE.equals(key)) {
            wanted = String.class;
        } else if (key.endsWith(ORDER_SUFFIX) || GRID_FIELDS.contains(key)
            || KEYBOARD_ROWS.equals(key) || KEYBOARD_CHIN.equals(key)
            || WALL_STRIP_HEIGHT.equals(key) || DOCK_HEIGHT.equals(key)
            || KEYBOARD_HEIGHT.equals(key)) {
            wanted = Number.class;
        } else if (WIDE_FIELDS.contains(key) || ORIENTATION_FIELDS.contains(key)) {
            wanted = String.class;
        } else {
            return null;
        }
        if (!wanted.isInstance(value))
            return key + ": expected " + wanted.getSimpleName().toLowerCase(java.util.Locale.ROOT)
                + ", found " + value;
        return valueError(key, value);
    }

    /**
     * The closed sets and ranges a known field reads (the type is already right). Each set is
     * what the store's own reader accepts: {@code DockStyle.parse} (every id, legacy ones too),
     * {@code DrawerTilePolicy.parsePlace}, {@code Edge.parse} (plus "hidden" for the rows'
     * own key), {@code KeyboardMode.parse}, {@code KeyboardForm.parse}, {@code IconSize.forKey}.
     * A key this build does not know is not checked, so a newer document still loads.
     */
    @Nullable
    private static String valueError(@NonNull String key, @NonNull Object value) {
        if (DOCK_DESIGN.equals(key)) {
            for (com.termux.app.dock.DockStyle style : com.termux.app.dock.DockStyle.values())
                if (style.storageValue().equals(value)) return null;
            return badValue(key, value, dockDesigns());
        }
        if (DRAWER_TILE_POSITION.equals(key)) {
            if (com.termux.app.dock.DrawerTilePolicy.isPlaceValue((String) value)) return null;
            return badValue(key, value, "start, middle, end, or a column number");
        }
        if (IconSize.FIELD.equals(key)) {
            if (value instanceof String && IconSize.forKey((String) value) != null) return null;
            return badValue(key, value, "s, m, l, xl");
        }
        if (KEYBOARD_MODE.equals(key)) {
            for (com.termux.app.place.PlaceLayout.KeyboardMode m
                : com.termux.app.place.PlaceLayout.KeyboardMode.values())
                if (m.storageValue().equals(value)) return null;
            return badValue(key, value, "resize, overlay");
        }
        if (KEYBOARD_FORM.equals(key)) {
            for (com.termux.app.place.PlaceLayout.KeyboardForm f
                : com.termux.app.place.PlaceLayout.KeyboardForm.values())
                if (f.storageValue().equals(value)) return null;
            return badValue(key, value, "docked, floating, split");
        }
        boolean restore = key.endsWith(RESTORE_EDGE_SUFFIX);
        if (restore || isElementKey(key)) {
            if (!restore && "hidden".equals(value)) return null;
            for (com.termux.app.place.PlaceLayout.Edge e
                : com.termux.app.place.PlaceLayout.Edge.values())
                if (e.storageValue().equals(value)) return null;
            return badValue(key, value, restore ? "top, bottom, left, right"
                : "top, bottom, left, right, hidden");
        }
        if (!BAR_ORDER.equals(key) && key.endsWith(ORDER_SUFFIX)) {
            double d = ((Number) value).doubleValue();
            if (d >= 0 && d == Math.rint(d) && !Double.isInfinite(d)) return null;
            return key + ": expected a non-negative integer, found " + value;
        }
        if (DOCK_HEIGHT.equals(key)) return rangeError(key, value,
            TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT, TERMUX_APP.MAX_APP_LAUNCHER_BAR_HEIGHT);
        if (KEYBOARD_HEIGHT.equals(key)) return rangeError(key, value,
            TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE, TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE);
        if (KEYBOARD_CHIN.equals(key)) return rangeError(key, value,
            TERMUX_APP.MIN_IN_APP_KEYBOARD_BOTTOM_PADDING, TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING);
        if (WALL_STRIP_HEIGHT.equals(key)) return rangeError(key, value,
            com.termux.app.dock.WallStripRowPolicy.MIN_HEIGHT_DP,
            com.termux.app.dock.WallStripRowPolicy.MAX_HEIGHT_DP);
        return null;
    }

    private static boolean isElementKey(@NonNull String key) {
        for (Element element : Element.values())
            if (element.storageKey().equals(key)) return true;
        return false;
    }

    @NonNull
    private static String dockDesigns() {
        StringBuilder out = new StringBuilder();
        for (com.termux.app.dock.DockStyle style : com.termux.app.dock.DockStyle.values()) {
            if (out.length() > 0) out.append(", ");
            out.append(style.storageValue());
        }
        return out.toString();
    }

    @NonNull
    private static String badValue(@NonNull String key, @NonNull Object value, @NonNull String allowed) {
        return key + ": unknown value \"" + value + "\"; one of: " + allowed;
    }

    @Nullable
    private static String rangeError(@NonNull String key, @NonNull Object value, double min, double max) {
        double d = ((Number) value).doubleValue();
        if (!Double.isNaN(d) && !Double.isInfinite(d) && d >= min && d <= max) return null;
        return key + ": " + value + " is outside " + min + " to " + max;
    }

    /**
     * Checks a {@code sizes} document: the same type and value rules as a kind document's size
     * fields, and {@code icon_size} one of s, m, l, xl. Null when fine.
     */
    @Nullable
    public static String validateSizes(@NonNull String json) {
        KindLayout layout;
        try {
            layout = parseKind(json);
        } catch (JSONException e) {
            return "not a sizes document: " + e.getMessage();
        }
        Object icon = layout.wide().get(IconSize.FIELD);
        if (icon != null) {
            String error = valueError(IconSize.FIELD, icon);
            if (error != null) return error;
        }
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            LayoutFields fields = layout.orientation(orientation);
            for (String key : SIZE_FIELDS) {
                Object v = fields.get(key);
                if (v == null) continue;
                String error = v instanceof Number ? valueError(key, v)
                    : key + ": expected number, found " + v;
                if (error != null) return orientation.storageValue() + "." + error;
            }
        }
        return null;
    }
}
