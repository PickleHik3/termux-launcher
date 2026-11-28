package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.leader.Leader;
import com.termux.app.leader.LeaderChord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The default bindings under the Terl desktop (ADR 0042, #439; round 3's table K1-K10 in
 * terl-decisions-2026-11-28.md): leader+Enter a new window right of the focused one (Shift: left),
 * leader+W close, leader+O pop out, leader+arrows focus (up and down through the column, then the
 * places), leader+Home/End the first/last column, leader+C centre it, leader+Shift+arrows move the
 * window, leader+[ and ] into or out of a column, leader+J split (Shift: the other way), leader+R
 * the next width (Shift: back), leader+minus/equals narrower/wider (Shift: height),
 * leader+1…9 and 0 go to a Workspace, leader+Shift+1…9 send the window there, PageUp/PageDown the
 * Workspace above/below, leader+L the layout, leader+V float, leader+F full screen (Shift: a full-width column), leader+G tab
 * group, leader+Space search, leader+Tab Overview, leader+comma edit mode.
 *
 * <p>Strokes are stored the way the resolver stores leader-relative bindings, under
 * {@link Leader#CANONICAL_PREFIX}, so each is typed as whichever leader is chosen. With the switch
 * off v1's own table (the registry's defaults) stays; this one replaces it with the switch on.
 * Pure: no Android.
 */
public final class TerlDefaultBindings {

    private TerlDefaultBindings() {}

    private static final Map<String, List<String>> TABLE = build();

    private static Map<String, List<String>> build() {
        // Round 3's table (K1-K10, terl-decisions-2026-11-28.md), after Omarchy and niri.
        Map<String, List<String>> table = new LinkedHashMap<>();
        // Open and close.
        put(table, LauncherToolRegistry.TOOL_WINDOW_NEW, "enter");
        put(table, LauncherToolRegistry.TOOL_WINDOW_NEW_LEFT, "shift+enter");
        put(table, LauncherToolRegistry.TOOL_WINDOW_CLOSE, "w");
        put(table, LauncherToolRegistry.TOOL_WINDOW_POP_OUT, "o");
        // Focus: sideways through the row, up and down through the column, then the places.
        put(table, LauncherToolRegistry.TOOL_WINDOW_PREVIOUS, "left");
        put(table, LauncherToolRegistry.TOOL_WINDOW_NEXT, "right");
        put(table, LauncherToolRegistry.TOOL_WINDOW_FOCUS_UP, "up");
        put(table, LauncherToolRegistry.TOOL_WINDOW_FOCUS_DOWN, "down");
        put(table, LauncherToolRegistry.TOOL_WINDOW_FOCUS_FIRST, "home");
        put(table, LauncherToolRegistry.TOOL_WINDOW_FOCUS_LAST, "end");
        put(table, LauncherToolRegistry.TOOL_WINDOW_CENTRE, "c");
        // Move.
        put(table, LauncherToolRegistry.TOOL_WINDOW_MOVE_LEFT, "shift+left");
        put(table, LauncherToolRegistry.TOOL_WINDOW_MOVE_RIGHT, "shift+right");
        put(table, LauncherToolRegistry.TOOL_WINDOW_MOVE_UP, "shift+up");
        put(table, LauncherToolRegistry.TOOL_WINDOW_MOVE_DOWN, "shift+down");
        put(table, LauncherToolRegistry.TOOL_WINDOW_CONSUME_LEFT, "[");
        put(table, LauncherToolRegistry.TOOL_WINDOW_CONSUME_RIGHT, "]");
        // Resize and split. K8's full-width column is leader+Shift+F (leader+Alt+F would be stored
        // as Ctrl+Alt+F, which is leader+F, full screen).
        put(table, LauncherToolRegistry.TOOL_WINDOW_FULL_WIDTH, "shift+f");
        put(table, LauncherToolRegistry.TOOL_WINDOW_SPLIT, "j");
        put(table, LauncherToolRegistry.TOOL_WINDOW_SPLIT_OTHER, "shift+j");
        put(table, LauncherToolRegistry.TOOL_WINDOW_WIDTH_NEXT, "r");
        put(table, LauncherToolRegistry.TOOL_WINDOW_WIDTH_PREVIOUS, "shift+r");
        put(table, LauncherToolRegistry.TOOL_WINDOW_NARROWER, "minus");
        put(table, LauncherToolRegistry.TOOL_WINDOW_WIDER, "equals");
        put(table, LauncherToolRegistry.TOOL_WINDOW_SHORTER, "shift+minus");
        put(table, LauncherToolRegistry.TOOL_WINDOW_TALLER, "shift+equals");
        // Workspaces.
        for (int n = 1; n <= 9; n++) {
            put(table, LauncherToolRegistry.TOOL_WORKSPACE_GO_PREFIX + n, String.valueOf(n));
            put(table, LauncherToolRegistry.TOOL_WINDOW_SEND_PREFIX + n, "shift+" + n);
        }
        put(table, LauncherToolRegistry.TOOL_WORKSPACE_GO_PREFIX + 10, "0");
        put(table, LauncherToolRegistry.TOOL_WORKSPACE_PREVIOUS, "pageup");
        put(table, LauncherToolRegistry.TOOL_WORKSPACE_NEXT, "pagedown");
        // Layout.
        put(table, LauncherToolRegistry.TOOL_WORKSPACE_LAYOUT_TOGGLE, "l");
        put(table, LauncherToolRegistry.TOOL_WINDOW_FLOAT, "v");
        put(table, LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN, "f");
        put(table, LauncherToolRegistry.TOOL_WINDOW_TAB, "g");
        // Tools.
        put(table, LauncherToolRegistry.TOOL_BAR_SEARCH, "space");
        put(table, LauncherToolRegistry.TOOL_SESSION_BROWSER, "tab");
        put(table, LauncherToolRegistry.TOOL_EDIT_MODE_OPEN, ",");
        return Collections.unmodifiableMap(table);
    }

    private static void put(Map<String, List<String>> table, String tool, String key) {
        table.put(tool, Collections.singletonList(Leader.CANONICAL_PREFIX + key));
    }

    /** Each tool with the stored strokes it is bound to by default, in the order Settings lists them. */
    @NonNull
    public static Map<String, List<String>> table() {
        return TABLE;
    }

    /**
     * Why Android takes {@code stored} from a hardware keyboard when typed under {@code leader}
     * (leader+Tab under a Custom leader holding Alt is Alt+Tab, leader+Space under one holding
     * Ctrl is Ctrl+Space), or null when it reaches Terl. Only a Custom leader can be taken so:
     * the Terl key never reaches Android, and Ctrl+Alt+Tab is fine.
     */
    @Nullable
    public static String reservation(@NonNull Leader leader, @NonNull String stored) {
        if (leader.kind != Leader.Kind.CUSTOM) return null;
        String typed = leader.physicalSequence(stored);
        if (typed.indexOf('>') >= 0) return null;
        return LeaderChord.reservationOfTyped(typed);
    }

    /** The tool names the table binds, for tests and the resolver. */
    @NonNull
    public static List<String> tools() {
        return new ArrayList<>(TABLE.keySet());
    }
}
