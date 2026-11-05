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
 * "page":2}}, or {@code "kind":"home|terminal|display"} in place of the page (ADR 0018 §3);
 * property, element and page or kind are optional where the CLI makes them so.</p>
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

    /** A kind's cell, in the CLI's words: "keyboard on Home pages". */
    @NonNull
    static String describeCell(@Nullable LookGrid.Element element, @Nullable LookGrid.Kind kind) {
        if (kind == null) return describeCell(element, LookGrid.NO_PAGE);
        return (element == null ? "all glass" : element.key) + " on " + kindName(kind);
    }

    @NonNull
    static String kindName(@NonNull LookGrid.Kind kind) {
        switch (kind) {
            case HOME: return "Home pages";
            case DISPLAY: return "Display pages";
            case ASSISTANT: return "the Assistant page";
            case TERMINAL:
            default:
                return "the Terminal page";
        }
    }

    /** Where a resolved value came from, for the element and page the read asked about. */
    @NonNull
    static String describe(@NonNull LookGrid.Cell cell, @Nullable LookGrid.Element element, int page) {
        return describe(cell, element, page, null);
    }

    /**
     * Where a resolved value came from, for the element and the page or kind the read asked
     * about; {@code kind} is the kind read at, or the page's.
     */
    @NonNull
    static String describe(@NonNull LookGrid.Cell cell, @Nullable LookGrid.Element element, int page,
                           @Nullable LookGrid.Kind kind) {
        switch (cell) {
            case ELEMENT_ON_PAGE: return describeCell(element, page);
            case PAGE: return describeCell(null, page);
            case ELEMENT_ON_KIND: return describeCell(element, kind);
            case KIND: return describeCell(null, kind);
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
        LookGrid.Kind kind = null;
        String kindName = request.optString("kind", "").trim().toLowerCase(Locale.ROOT);
        if (!kindName.isEmpty()) {
            kind = LookGrid.Kind.forKey(kindName);
            if (kind == null) return error("'kind' must be one of home, terminal, display");
            if (page >= 0) return error("give 'page' or 'kind', not both");
        }
        LookGrid.Property property = null;
        if (!propertyName.isEmpty()) {
            property = LookGrid.Property.forKey(propertyName.equals("colour") ? "tint_hue" : propertyName);
            if (property == null)
                return error("'property' must be one of frost, opacity, tint, colour, corners, depth, edge,"
                    + " grain, rim, vibrancy, margin");
            if (element != null && !property.perElement)
                return error(propertyName + " is set for all glass, not for one element");
            if (kind != null && !property.perKind)
                return error(propertyName + " is set everywhere, not for one kind of page");
            if (page >= 0 && !property.perKind)
                return error(propertyName + " is set everywhere, not for one page");
        }
        // A page names its kind's cell (ADR 0020 §2): pages have no cells of their own.
        if (kind == null && page >= 0) {
            kind = grid.kindOf(page);
            if (kind == null) return error("no page " + page + " on the wall");
            page = LookGrid.NO_PAGE;
        }
        switch (op) {
            case "get":
                if (property == null) return error("'property' is required");
                return get(grid, cell(property, element, page, kind));
            case "set":
                if (property == null) return error("'property' is required");
                return set(grid, cell(property, element, page, kind), request.optString("value", ""),
                    onChanged);
            case "reset":
                return reset(grid, property, element, page, kind, onChanged);
            default:
                return error("'op' must be get, set or reset");
        }
    }

    /** The cell a request names: a kind's, or a page's (everywhere for none). */
    @NonNull
    private static LookGrid.Key cell(@NonNull LookGrid.Property property,
                                     @Nullable LookGrid.Element element, int page,
                                     @Nullable LookGrid.Kind kind) {
        return kind != null ? new LookGrid.Key(property, element, kind)
            : new LookGrid.Key(property, element, page);
    }

    @NonNull
    private static String describeCell(@NonNull LookGrid.Key cell) {
        return cell.kind != null ? describeCell(cell.element, cell.kind)
            : describeCell(cell.element, cell.page);
    }

    @NonNull
    private static JSONObject get(LookGrid grid, LookGrid.Key cell) throws JSONException {
        LookGrid.Resolved resolved = grid.resolve(cell);
        LookGrid.Kind kind = cell.kind != null ? cell.kind : grid.kindOf(cell.page);
        return new JSONObject().put("ok", true).put("property", name(cell.property))
            .put("value", render(cell.property, resolved.value))
            .put("from", describe(resolved.cell, cell.element, cell.page, kind));
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
            .put("at", describeCell(cell));
    }

    /** Resets one cell of one property, or of every property the cell holds. */
    @NonNull
    private static JSONObject reset(LookGrid grid, @Nullable LookGrid.Property property,
                                    @Nullable LookGrid.Element element, int page,
                                    @Nullable LookGrid.Kind kind,
                                    @NonNull Runnable onChanged) throws JSONException {
        int cleared = 0;
        for (LookGrid.Property each : LookGrid.Property.values()) {
            if (property != null && each != property) continue;
            if ((element != null && !each.perElement) || (kind != null && !each.perKind))
                continue;
            LookGrid.Key cell = cell(each, element, page, kind);
            if (grid.has(cell)) {
                grid.clear(cell);
                cleared++;
            }
        }
        if (cleared > 0) onChanged.run();
        return new JSONObject().put("ok", true).put("reset", cleared)
            .put("at", kind != null ? describeCell(element, kind) : describeCell(element, page));
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
