package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;

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
 * <p>Request: {@code {"op":"get|set|reset","property":"opacity","value":"60","element":"dock",
 * "page":2}}; property, element and page are optional where the CLI makes them so.</p>
 */
final class LookCommand {

    private LookCommand() {
    }

    /** The cell named by an element (or all glass) and a page (or everywhere), in the CLI's words. */
    @NonNull
    static String describeCell(@Nullable LookGrid.Element element, int page) {
        String name = element == null ? "all glass" : element.key;
        return page >= 0 ? name + " on page " + page : name + ", everywhere";
    }

    /** Where a resolved value came from, for the element and page the read asked about. */
    @NonNull
    static String describe(@NonNull LookGrid.Cell cell, @Nullable LookGrid.Element element, int page) {
        switch (cell) {
            case ELEMENT_ON_PAGE: return describeCell(element, page);
            case PAGE: return describeCell(null, page);
            case ELEMENT: return describeCell(element, LookGrid.NO_PAGE);
            case BASE: return describeCell(null, LookGrid.NO_PAGE);
            default: return "shipped";
        }
    }

    @NonNull
    static JSONObject handle(@NonNull LookGrid grid, @NonNull JSONObject request) throws JSONException {
        return handle(grid, request, () -> { });
    }

    /**
     * Answers one request. {@code onChanged} runs once after a {@code set}, or after a
     * {@code reset} that cleared at least one cell; never for a {@code get} or an error.
     */
    @NonNull
    static JSONObject handle(@NonNull LookGrid grid, @NonNull JSONObject request,
                             @NonNull Runnable onChanged) throws JSONException {
        String op = request.optString("op", "");
        String propertyName = request.optString("property", "").trim().toLowerCase(Locale.ROOT);
        String elementName = request.optString("element", "").trim().toLowerCase(Locale.ROOT);
        LookGrid.Element element = null;
        if (!elementName.isEmpty()) {
            element = LookGrid.Element.forKey(elementName);
            if (element == null)
                return error("'element' must be one of status, dock, keyboard, terminal, widgets, sheets");
        }
        int page = LookGrid.NO_PAGE;
        if (request.has("page") && !request.isNull("page")) {
            Object raw = request.opt("page");
            if (!(raw instanceof Number) || ((Number) raw).intValue() < 0)
                return error("'page' must be a page number");
            page = ((Number) raw).intValue();
        }
        LookGrid.Property property = null;
        if (!propertyName.isEmpty()) {
            property = LookGrid.Property.forKey(propertyName.equals("colour") ? "tint_hue" : propertyName);
            if (property == null)
                return error("'property' must be one of frost, opacity, tint, colour, corners, depth, edge,"
                    + " grain, rim, vibrancy, margin");
            if (element != null && !property.perElement)
                return error(propertyName + " is set for all glass, not for one element");
        }
        switch (op) {
            case "get":
                if (property == null) return error("'property' is required");
                return get(grid, property, element, page);
            case "set":
                if (property == null) return error("'property' is required");
                return set(grid, property, element, page, request.optString("value", ""), onChanged);
            case "reset":
                return reset(grid, property, element, page, onChanged);
            default:
                return error("'op' must be get, set or reset");
        }
    }

    @NonNull
    private static JSONObject get(LookGrid grid, LookGrid.Property property,
                                  @Nullable LookGrid.Element element, int page) throws JSONException {
        LookGrid.Resolved resolved = grid.resolve(property, element, page);
        return new JSONObject().put("ok", true).put("property", name(property))
            .put("value", render(property, resolved.value))
            .put("from", describe(resolved.cell, element, page));
    }

    @NonNull
    private static JSONObject set(LookGrid grid, LookGrid.Property property,
                                  @Nullable LookGrid.Element element, int page, String text,
                                  @NonNull Runnable onChanged) throws JSONException {
        Integer value = parse(property, text.trim().toLowerCase(Locale.ROOT));
        if (value == null) return error(hint(property));
        grid.write(property, element, page, value);
        onChanged.run();
        return new JSONObject().put("ok", true).put("property", name(property))
            .put("value", render(property, grid.value(property, element, page)))
            .put("at", describeCell(element, page));
    }

    /** Resets one cell of one property, or of every property the cell holds. */
    @NonNull
    private static JSONObject reset(LookGrid grid, @Nullable LookGrid.Property property,
                                    @Nullable LookGrid.Element element, int page,
                                    @NonNull Runnable onChanged) throws JSONException {
        int cleared = 0;
        for (LookGrid.Property each : LookGrid.Property.values()) {
            if (property != null && each != property) continue;
            if (element != null && !each.perElement) continue;
            if (grid.has(each, element, page)) {
                grid.clear(each, element, page);
                cleared++;
            }
        }
        if (cleared > 0) onChanged.run();
        return new JSONObject().put("ok", true).put("reset", cleared)
            .put("at", describeCell(element, page));
    }

    @NonNull
    static String name(@NonNull LookGrid.Property property) {
        return property == LookGrid.Property.TINT_HUE ? "colour" : property.key;
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
            case FROST: return "frost takes a stop from 0 to " + FrostStops.LAST + ", or a blur such as 12dp";
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
