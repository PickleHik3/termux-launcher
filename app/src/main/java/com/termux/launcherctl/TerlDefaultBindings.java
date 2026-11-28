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
 * The default bindings under the Terl desktop (ADR 0042, #439): leader+Enter a terminal, leader+Q
 * close, leader+arrows focus, leader+Shift+arrows move the window, leader+1…9 go to a Workspace,
 * leader+Shift+1…9 send the window there, leader+V float, leader+F full screen, leader+Space
 * search, leader+Tab Overview, leader+W tab group, leader+comma edit mode.
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
        Map<String, List<String>> table = new LinkedHashMap<>();
        put(table, LauncherToolRegistry.TOOL_WINDOW_NEW, "enter");
        put(table, LauncherToolRegistry.TOOL_WINDOW_CLOSE, "q");
        put(table, LauncherToolRegistry.TOOL_WINDOW_PREVIOUS, "left");
        put(table, LauncherToolRegistry.TOOL_WINDOW_NEXT, "right");
        put(table, LauncherToolRegistry.TOOL_SESSION_PREVIOUS, "up");
        put(table, LauncherToolRegistry.TOOL_SESSION_NEXT, "down");
        put(table, LauncherToolRegistry.TOOL_WINDOW_MOVE_LEFT, "shift+left");
        put(table, LauncherToolRegistry.TOOL_WINDOW_MOVE_RIGHT, "shift+right");
        put(table, LauncherToolRegistry.TOOL_WINDOW_MOVE_UP, "shift+up");
        put(table, LauncherToolRegistry.TOOL_WINDOW_MOVE_DOWN, "shift+down");
        for (int n = 1; n <= 9; n++) {
            put(table, LauncherToolRegistry.TOOL_WORKSPACE_GO_PREFIX + n, String.valueOf(n));
            put(table, LauncherToolRegistry.TOOL_WINDOW_SEND_PREFIX + n, "shift+" + n);
        }
        put(table, LauncherToolRegistry.TOOL_WINDOW_FLOAT, "v");
        put(table, LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN, "f");
        put(table, LauncherToolRegistry.TOOL_BAR_SEARCH, "space");
        put(table, LauncherToolRegistry.TOOL_SESSION_BROWSER, "tab");
        put(table, LauncherToolRegistry.TOOL_WINDOW_TAB, "w");
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
        boolean ctrl = false, alt = false, shift = false;
        String key = typed;
        while (true) {
            if (key.startsWith("ctrl+")) { ctrl = true; key = key.substring(5); }
            else if (key.startsWith("alt+")) { alt = true; key = key.substring(4); }
            else if (key.startsWith("shift+")) { shift = true; key = key.substring(6); }
            else break;
        }
        return LeaderChord.reservation(ctrl, alt, shift, key);
    }

    /** The tool names the table binds, for tests and the resolver. */
    @NonNull
    public static List<String> tools() {
        return new ArrayList<>(TABLE.keySet());
    }
}
