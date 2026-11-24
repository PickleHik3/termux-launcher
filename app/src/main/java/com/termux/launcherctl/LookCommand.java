package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * {@code launcherctl look get|set|reset}: the look grid (ADR 0017) over the API. Pure over a
 * {@link LookGrid}, so the grammar is tested without a device. A write goes through the grid's
 * store, which is a plain preference write and redresses nothing by itself: a {@code set}, or a
 * {@code reset} that cleared something, runs the caller's {@code onChanged} once, and the route
 * re-dresses the live chrome from there.
 *
 * <p>It sets what the Appearance cards can (ADR 0031): Global's values; the own Opacity and Blur
 * of the status bar, the dock and the keyboard; and the Terminal's Background (opacity, blur and
 * wallpaper dim, kept in their own preferences, never following Global).</p>
 *
 * <p>Request: {@code {"op":"get|set|reset","property":"opacity","value":"60","element":"dock"}};
 * property and element are optional where the CLI makes them so. The chrome wears one look on
 * every page: a {@code page} or {@code kind} is refused.</p>
 */
final class LookCommand {

    /** What a request naming a page or a kind of page is told. */
    static final String NO_PAGE_LOOK =
        "the look is the same on every page: leave out --page and --kind";
    static final String NO_TERMINAL_ELEMENT =
        "the Terminal's look is its Background: use --element background";
    static final String NO_OWN_LOOK =
        "widgets and menus wear Global's look and hold none of their own: leave out --element";
    static final String DIM_IS_TERMINALS =
        "dim belongs to the Terminal's Background: use --element background";
    static final String ELEMENTS = "'element' must be one of status, dock, keyboard, background";
    private static final String TERMINAL = "Terminal";

    private LookCommand() {
    }

    /** The look grid's key for a property word: {@code colour} is tint_hue, {@code blur} is frost. */
    @NonNull
    static String gridKey(@NonNull String word) {
        return word.equals("colour") ? "tint_hue" : word.equals("blur") ? "frost" : word;
    }

    /** The Background setting's key for a property word: {@code frost} is {@code blur}. */
    @NonNull
    static String backgroundKey(@NonNull String word) {
        return word.equals("frost") ? "blur" : word;
    }

    /** The cell named by an element, or all glass, in the CLI's words. */
    @NonNull
    static String describeCell(@Nullable LookGrid.Element element) {
        return element == null ? "Global" : element.key;
    }

    /** Where a resolved value came from, for the element the read asked about. */
    @NonNull
    static String describe(@NonNull LookGrid.Cell cell, @Nullable LookGrid.Element element) {
        switch (cell) {
            case ELEMENT: return describeCell(element);
            case GLOBAL: return describeCell(null);
            default: return "shipped";
        }
    }

    /** The Terminal's Background: its own three numbers, never Global's. */
    enum Setting {
        OPACITY("opacity", 0, 100, TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_BG_OPACITY),
        /** In dp, what a pane can render. */
        BLUR("blur", 0, TermuxPreferenceConstants.TERMUX_APP.MAX_TERMINAL_BG_BLUR,
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_BG_BLUR),
        DIM("dim", 0, 100, TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_WALLPAPER_DIM);

        final String key;
        final int min;
        final int max;
        final int shipped;

        Setting(String key, int min, int max, int shipped) {
            this.key = key;
            this.min = min;
            this.max = max;
            this.shipped = shipped;
        }

        @Nullable
        static Setting forKey(@NonNull String key) {
            for (Setting each : values()) {
                if (each.key.equals(key)) return each;
            }
            return null;
        }
    }

    /** Where the Terminal's Background lives: the preferences on a device, a map in tests. */
    interface TerminalBackground {
        int get(@NonNull Setting setting);

        void set(@NonNull Setting setting, int value);
    }

    @NonNull
    static JSONObject handle(@NonNull LookGrid grid, @NonNull TerminalBackground background,
                             @NonNull JSONObject request) throws JSONException {
        return handle(grid, background, request, () -> { });
    }

    /**
     * Answers one request. {@code onChanged} runs once after a {@code set}, or after a
     * {@code reset} that changed at least one value; never for a {@code get} or an error.
     */
    @NonNull
    static JSONObject handle(@NonNull LookGrid grid, @NonNull TerminalBackground background,
                             @NonNull JSONObject request, @NonNull Runnable onChanged)
            throws JSONException {
        String op = request.optString("op", "");
        String propertyName = request.optString("property", "").trim().toLowerCase(Locale.ROOT);
        String elementName = request.optString("element", "").trim().toLowerCase(Locale.ROOT);
        if ((request.has("page") && !request.isNull("page"))
                || !request.optString("kind", "").trim().isEmpty())
            return error(NO_PAGE_LOOK);
        boolean terminal = false;
        LookGrid.Element element = null;
        switch (elementName) {
            case "":
                break;
            case "background":
                terminal = true;
                break;
            case "terminal":
                return error(NO_TERMINAL_ELEMENT);
            case "widgets":
            case "sheets":
                return error(NO_OWN_LOOK);
            default:
                element = LookGrid.Element.forKey(elementName);
                if (element == null) return error(ELEMENTS);
        }
        if (terminal) return background(background, op, propertyName, request, onChanged);
        LookGrid.Property property = null;
        if (!propertyName.isEmpty()) {
            if (propertyName.equals("dim")) return error(DIM_IS_TERMINALS);
            property = LookGrid.Property.forKey(gridKey(propertyName));
            if (property == null)
                return error("'property' must be one of blur, opacity, tint, colour, corners, depth,"
                    + " edge, grain, rim, vibrancy, margin");
            if (element != null && !ownable(property))
                return error(propertyName + " is Global's only: a surface owns just opacity and blur,"
                    + " so leave out --element");
        }
        switch (op) {
            case "get":
                if (property == null) return error("'property' is required");
                return get(grid, cell(property, element));
            case "set":
                if (property == null) return error("'property' is required");
                return set(grid, cell(property, element), request.optString("value", ""),
                    onChanged);
            case "reset":
                return reset(grid, property, element, onChanged);
            default:
                return error("'op' must be get, set or reset");
        }
    }

    /** What the status bar, the dock and the keyboard may own. */
    static boolean ownable(@NonNull LookGrid.Property property) {
        return property == LookGrid.Property.OPACITY || property == LookGrid.Property.FROST;
    }

    /** {@code --element background}: the Terminal's own numbers; they never follow Global. */
    @NonNull
    private static JSONObject background(@NonNull TerminalBackground background, @NonNull String op,
                                         @NonNull String propertyName, @NonNull JSONObject request,
                                         @NonNull Runnable onChanged) throws JSONException {
        Setting setting = null;
        if (!propertyName.isEmpty()) {
            setting = Setting.forKey(backgroundKey(propertyName));
            if (setting == null) return error("the Terminal's Background takes opacity, blur and dim");
        }
        switch (op) {
            case "get":
                if (setting == null) return error("'property' is required");
                return new JSONObject().put("ok", true).put("property", setting.key)
                    .put("value", render(setting, background.get(setting))).put("from", TERMINAL);
            case "set": {
                if (setting == null) return error("'property' is required");
                Integer value = parse(setting,
                    request.optString("value", "").trim().toLowerCase(Locale.ROOT));
                if (value == null) return error(setting.key + " takes a whole number");
                background.set(setting, value);
                onChanged.run();
                return new JSONObject().put("ok", true).put("property", setting.key)
                    .put("value", render(setting, background.get(setting))).put("at", TERMINAL);
            }
            case "reset": {
                int changed = 0;
                for (Setting each : Setting.values()) {
                    if (setting != null && each != setting) continue;
                    if (background.get(each) == each.shipped) continue;
                    background.set(each, each.shipped);
                    changed++;
                }
                if (changed > 0) onChanged.run();
                return new JSONObject().put("ok", true).put("reset", changed).put("at", TERMINAL);
            }
            default:
                return error("'op' must be get, set or reset");
        }
    }

    /** A Background number in the words the CLI takes it in. */
    @NonNull
    static String render(@NonNull Setting setting, int value) {
        return setting == Setting.BLUR ? value + "dp" : String.valueOf(value);
    }

    @Nullable
    static Integer parse(@NonNull Setting setting, @NonNull String text) {
        String digits = setting == Setting.BLUR && text.endsWith("dp")
            ? text.substring(0, text.length() - 2) : text;
        Integer number = whole(digits);
        return number == null ? null : Math.max(setting.min, Math.min(setting.max, number));
    }

    /** The cell a request names. */
    @NonNull
    private static LookGrid.Key cell(@NonNull LookGrid.Property property,
                                     @Nullable LookGrid.Element element) {
        return new LookGrid.Key(property, element, LookGrid.NO_PAGE);
    }

    @NonNull
    private static JSONObject get(LookGrid grid, LookGrid.Key cell) throws JSONException {
        LookGrid.Resolved resolved = grid.resolve(cell);
        return new JSONObject().put("ok", true).put("property", name(cell.property))
            .put("value", render(cell.property, resolved.value))
            .put("from", describe(resolved.cell, cell.element));
    }

    @NonNull
    private static JSONObject set(LookGrid grid, LookGrid.Key cell, String text,
                                  @NonNull Runnable onChanged) throws JSONException {
        Integer value = parse(cell.property, text.trim().toLowerCase(Locale.ROOT));
        if (value == null) return error(hint(cell.property));
        grid.write(cell, value);
        onChanged.run();
        return new JSONObject().put("ok", true).put("property", name(cell.property))
            .put("value", render(cell.property, grid.value(cell)))
            .put("at", describeCell(cell.element));
    }

    /** Resets one cell of one property, or of every property the cell holds. */
    @NonNull
    private static JSONObject reset(LookGrid grid, @Nullable LookGrid.Property property,
                                    @Nullable LookGrid.Element element,
                                    @NonNull Runnable onChanged) throws JSONException {
        int cleared = 0;
        for (LookGrid.Property each : LookGrid.Property.values()) {
            if (property != null && each != property) continue;
            if (element != null && !each.perElement)
                continue;
            LookGrid.Key cell = cell(each, element);
            if (grid.has(cell)) {
                grid.clear(cell);
                cleared++;
            }
        }
        if (cleared > 0) onChanged.run();
        return new JSONObject().put("ok", true).put("reset", cleared)
            .put("at", describeCell(element));
    }

    @NonNull
    static String name(@NonNull LookGrid.Property property) {
        return property == LookGrid.Property.TINT_HUE ? "colour"
            : property == LookGrid.Property.FROST ? "blur" : property.key;
    }

    /** A grid number in the words the CLI takes it in. */
    @NonNull
    static String render(@NonNull LookGrid.Property property, int value) {
        switch (property) {
            case FROST:
                return value + " (" + FrostStops.blurDpFor(value) + "dp)";
            case TINT_HUE:
                return value == LookGrid.TINT_HUE_SECONDARY ? "secondary"
                    : value == LookGrid.TINT_HUE_TERTIARY ? "tertiary" : "primary";
            case RIM:
                return value == LookGrid.RIM_OFF ? "off"
                    : value == LookGrid.RIM_HAIRLINE ? "hairline" : "bright";
            case CORNERS:
                return value == LookGrid.CORNERS_AUTO ? "auto" : String.valueOf(value);
            default:
                return String.valueOf(value);
        }
    }

    @Nullable
    static Integer parse(@NonNull LookGrid.Property property, @NonNull String text) {
        switch (property) {
            case TINT_HUE:
                if (text.equals("primary")) return LookGrid.TINT_HUE_PRIMARY;
                if (text.equals("secondary")) return LookGrid.TINT_HUE_SECONDARY;
                if (text.equals("tertiary")) return LookGrid.TINT_HUE_TERTIARY;
                return null;
            case RIM:
                if (text.equals("off")) return LookGrid.RIM_OFF;
                if (text.equals("hairline")) return LookGrid.RIM_HAIRLINE;
                if (text.equals("bright")) return LookGrid.RIM_GRADIENT;
                return null;
            case CORNERS:
                if (text.equals("auto")) return LookGrid.CORNERS_AUTO;
                return whole(text);
            case FROST: {
                if (text.endsWith("dp")) {
                    Integer dp = whole(text.substring(0, text.length() - 2));
                    return dp == null || dp < 0 ? null : FrostStops.nearestStopByBlur(dp);
                }
                Integer stop = whole(text);
                return stop == null || stop < 0 || stop > FrostStops.LAST ? null : stop;
            }
            default:
                Integer number = whole(text);
                return number == null ? null : property.clamp(number);
        }
    }

    @Nullable
    private static Integer whole(String text) {
        if (text.isEmpty() || text.length() > 6) return null;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private static String hint(LookGrid.Property property) {
        switch (property) {
            case FROST: return "blur takes a stop from 0 to " + FrostStops.LAST + ", or a blur such as 12dp";
            case TINT_HUE: return "colour takes primary, secondary or tertiary";
            case RIM: return "rim takes off, hairline or bright";
            case CORNERS: return "corners takes a size in dp, or auto";
            default: return name(property) + " takes a whole number";
        }
    }

    @NonNull
    private static JSONObject error(@NonNull String message) throws JSONException {
        return new JSONObject().put("ok", false).put("error", "bad_request").put("message", message)
            .put("_statusCode", 400);
    }
}
