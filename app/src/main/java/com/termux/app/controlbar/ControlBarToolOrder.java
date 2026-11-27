package com.termux.app.controlbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Which tools the control bar's bottom row holds for each place, and in what order (#452). A place
 * keeps what the user stored as tool names in order, separated by commas; with nothing stored the
 * bar's own list stands. Pure: the activity reads the stored text, this decides what it means.
 *
 * <p>On Home the Drawer item is also the {@code terl.drawer_tool} setting: that setting says
 * whether it is shown, and the stored order only says where it stands among Home's tools (the
 * pinned apps are one entry, {@link ControlBarTool#PINNED_APPS}).
 */
public final class ControlBarToolOrder {

    /** The places that keep a list of their own. */
    public enum Place {
        /** Home: the Drawer item and the pinned apps. */
        HOME("home"),
        /** A terminal window in front. */
        TERMINAL("terminal"),
        /** A Linux app (or the Display) in front. */
        LINUX_APP("linux");

        /** The name the place goes by in settings and in edit mode's seam. */
        @NonNull public final String id;

        Place(@NonNull String id) {
            this.id = id;
        }

        @Nullable
        public static Place ofId(@Nullable String id) {
            for (Place place : values()) if (place.id.equals(id)) return place;
            return null;
        }
    }

    /** What stored text means for no tool at all. */
    private static final String NONE = "-";

    private static final List<ControlBarTool> HOME = list(ControlBarTool.DRAWER,
        ControlBarTool.PINNED_APPS);
    private static final List<ControlBarTool> TERMINAL = list(ControlBarTool.KEYBOARD,
        ControlBarTool.SPLIT, ControlBarTool.NEW_TERMINAL, ControlBarTool.WINDOW_MENU);
    private static final List<ControlBarTool> LINUX_APP = list(ControlBarTool.MOUSE_MODE,
        ControlBarTool.KEYBOARD, ControlBarTool.WINDOW_MENU);

    private ControlBarToolOrder() {}

    @NonNull
    private static List<ControlBarTool> list(@NonNull ControlBarTool... tools) {
        return Collections.unmodifiableList(Arrays.asList(tools));
    }

    /** Every tool {@code place} may hold, in the bar's own order. */
    @NonNull
    public static List<ControlBarTool> options(@NonNull Place place) {
        switch (place) {
            case HOME: return HOME;
            case TERMINAL: return TERMINAL;
            default: return LINUX_APP;
        }
    }

    /** The tool's name in stored text. */
    @NonNull
    public static String id(@NonNull ControlBarTool tool) {
        return tool.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The tool a name stands for, or null. */
    @Nullable
    public static ControlBarTool tool(@Nullable String id) {
        if (id == null) return null;
        for (ControlBarTool tool : ControlBarTool.values()) if (id(tool).equals(id.trim())) return tool;
        return null;
    }

    /**
     * The tools {@code place} holds, in order: the stored ones that it may hold (an unknown or
     * repeated name is ignored), or the bar's own list when {@code stored} is null or blank.
     *
     * @param drawerTool whether Home's Drawer item is switched on ({@code terl.drawer_tool})
     */
    @NonNull
    public static List<ControlBarTool> resolve(@NonNull Place place, @Nullable String stored,
                                               boolean drawerTool) {
        List<ControlBarTool> out = parse(place, stored);
        if (out == null) out = new ArrayList<>(options(place));
        if (place == Place.HOME) {
            int at = out.indexOf(ControlBarTool.DRAWER);
            // Switched on but not in the stored order: it leads, as in the bar's own list.
            if (drawerTool && at < 0) out.add(0, ControlBarTool.DRAWER);
            if (!drawerTool && at >= 0) out.remove(at);
        }
        return Collections.unmodifiableList(out);
    }

    /** The stored names as tools {@code place} may hold, in order; null when nothing is stored. */
    @Nullable
    private static List<ControlBarTool> parse(@NonNull Place place, @Nullable String stored) {
        if (stored == null || stored.trim().isEmpty()) return null;
        List<ControlBarTool> out = new ArrayList<>();
        if (NONE.equals(stored.trim())) return out;
        List<ControlBarTool> allowed = options(place);
        for (String part : stored.split(",")) {
            ControlBarTool tool = tool(part);
            if (tool != null && allowed.contains(tool) && !out.contains(tool)) out.add(tool);
        }
        return out;
    }

    /** The text that stores {@code tools}; no tool at all is stored as a dash. */
    @NonNull
    public static String format(@NonNull List<ControlBarTool> tools) {
        if (tools.isEmpty()) return NONE;
        StringBuilder out = new StringBuilder();
        for (ControlBarTool tool : tools) {
            if (out.length() > 0) out.append(',');
            out.append(id(tool));
        }
        return out.toString();
    }

    /**
     * {@code tools} (names, in order) as stored text: names that are not tools of {@code place},
     * and repeats, drop out.
     */
    @NonNull
    public static String normalise(@NonNull Place place, @Nullable String tools) {
        List<ControlBarTool> parsed = parse(place, tools == null || tools.trim().isEmpty() ? NONE : tools);
        return format(parsed == null ? Collections.<ControlBarTool>emptyList() : parsed);
    }

    /** Whether the stored text for Home holds the Drawer item. */
    public static boolean holdsDrawer(@Nullable String stored) {
        List<ControlBarTool> tools = parse(Place.HOME, stored);
        return tools != null && tools.contains(ControlBarTool.DRAWER);
    }
}
