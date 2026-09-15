package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Which extra keys do anything on the place the wall is standing on.
 *
 * <p>The key row and the key column follow the wall from place to place, but a key's meaning does
 * not: there is no shell in front of the widget grid to take an Esc, and splitting a pane means
 * nothing while the X11 display is up. A key that cannot act is drawn dead rather than removed, so
 * the row keeps its shape and the user's own arrangement stays where they put it.
 *
 * <p>Pure and tested: the view layer asks this one question per key and applies the answer.
 *
 * <h3>The table</h3>
 *
 * Every key value falls in exactly one band, and each band says where it works:
 *
 * <table>
 *   <tr><th>Band</th><th>Widgets</th><th>Terminal</th><th>Display</th></tr>
 *   <tr><td>{@link Band#TERMINAL_INPUT} — every name in
 *       {@link ExtraKeysConstants#PRIMARY_KEY_CODES_FOR_STRINGS} (SPACE ESC TAB HOME END PGUP PGDN
 *       INS DEL BKSP UP LEFT RIGHT DOWN ENTER F1–F12), the modifiers CTRL ALT SHIFT FN, and any
 *       literal text a key sends</td><td>no</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>{@link Band#TERMINAL_TOOL} — the row's own PASTE and SCROLL, and every tool that acts
 *       on what is in the terminal or on the screen in front of it</td>
 *       <td>no</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>{@link Band#MULTIPLEX} — panes, windows, sessions and workspaces: the terminal
 *       multiplexing tools</td><td>no</td><td>yes</td><td>no</td></tr>
 *   <tr><td>{@link Band#SESSION_OVERLAY} — the sessions browser and panel, and the DRAWER key that
 *       opens them; launcher overlays that happen to list sessions</td>
 *       <td>yes</td><td>yes</td><td>no</td></tr>
 *   <tr><td>{@link Band#LAUNCHER} — the place switches, the KEYBOARD key, and everything that
 *       belongs to the launcher rather than to a shell: settings, help, the palette, the editors,
 *       the wallpaper, app launching, fonts, the dock</td><td>yes</td><td>yes</td><td>yes</td></tr>
 * </table>
 *
 * <p>An unrecognised {@code tool:} value is usable everywhere. Greying a key the user deliberately
 * added, on a guess, is worse than leaving it live — and {@code ExtraKeyEligibilityTest} holds every
 * declared {@code TOOL_*} to an explicit band so the guess is never reached for a tool we ship.
 */
public final class ExtraKeyEligibility {

    private ExtraKeyEligibility() {}

    /** Where a key acts. One band per key value; the bands are the whole table above. */
    public enum Band {
        /** Keystrokes and modifiers: wherever typing goes. */
        TERMINAL_INPUT(false, true, true),
        /** Acts on the terminal's contents, or on what is drawn in front of it. */
        TERMINAL_TOOL(false, true, true),
        /** Panes, windows, sessions, workspaces — terminal multiplexing. */
        MULTIPLEX(false, true, false),
        /** The sessions browser and panel: a launcher overlay over the terminal's sessions. */
        SESSION_OVERLAY(true, true, false),
        /** The launcher's own: places, keyboard, settings, appearance, apps. */
        LAUNCHER(true, true, true);

        private final boolean widgets;
        private final boolean terminal;
        private final boolean display;

        Band(boolean widgets, boolean terminal, boolean display) {
            this.widgets = widgets;
            this.terminal = terminal;
            this.display = display;
        }

        public boolean worksOn(@NonNull PaneWallPage place) {
            switch (place) {
                case WIDGETS: return widgets;
                case DISPLAY: return display;
                default: return terminal;
            }
        }
    }

    /** The row keys the toolbar handles itself rather than sending on. */
    public static final String KEY_KEYBOARD = "KEYBOARD";
    public static final String KEY_DRAWER = "DRAWER";
    public static final String KEY_PASTE = "PASTE";
    public static final String KEY_SCROLL = "SCROLL";

    /** The modifiers, which live outside {@code PRIMARY_KEY_CODES_FOR_STRINGS}. */
    private static final String[] MODIFIERS = {"CTRL", "ALT", "SHIFT", "FN"};

    private static final Map<String, Band> ROW_KEYS;
    private static final Map<String, Band> TOOLS;

    static {
        Map<String, Band> rowKeys = new HashMap<>();
        rowKeys.put(KEY_KEYBOARD, Band.LAUNCHER);
        rowKeys.put(KEY_DRAWER, Band.SESSION_OVERLAY);
        rowKeys.put(KEY_PASTE, Band.TERMINAL_TOOL);
        rowKeys.put(KEY_SCROLL, Band.TERMINAL_TOOL);
        ROW_KEYS = Collections.unmodifiableMap(rowKeys);

        Map<String, Band> tools = new HashMap<>();

        // ------------------------------------------------------------------ the launcher's own
        tools.put(LauncherToolRegistry.TOOL_WALL_GO, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_WALL_WIDGETS, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_WALL_TERMINAL, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_WALL_DISPLAY, Band.LAUNCHER);
        // The keyboard is the launcher's, not the terminal's: a widget's own text field needs one
        // as much as a shell does.
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_KEYBOARD_SHOW, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_KEYBOARD_HIDE, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_KEYBOARD_CYCLE_LAYOUT, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_KEYBOARD_SELECT_LAYOUT, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_KEYBOARD_CYCLE_FORM, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_KEYBOARD_SET_FORM, Band.LAUNCHER);
        // The dock and the key row are chrome every place wears.
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_TOOLBAR, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_EXTRA_KEYS_EDIT, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APP_OPEN_HELP, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APP_OPEN_LOOK_AND_FEEL, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APP_OPEN_APPS_BAR, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APP_LAUNCH, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APP_KEY_INSPECTOR, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APPEARANCE_SET_WALLPAPER, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APPEARANCE_TOGGLE_WALLPAPER, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_APPEARANCE_SURFACE_EDITOR, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_FONTS_PICK, Band.LAUNCHER);
        tools.put(LauncherToolRegistry.TOOL_FONTS_INSTALL, Band.LAUNCHER);

        // --------------------------------------------------------------- the sessions overlays
        tools.put(LauncherToolRegistry.TOOL_SESSION_BROWSER, Band.SESSION_OVERLAY);
        tools.put(LauncherToolRegistry.TOOL_SESSION_PANEL, Band.SESSION_OVERLAY);
        tools.put(LauncherToolRegistry.TOOL_APP_OPEN_DRAWER, Band.SESSION_OVERLAY);
        tools.put(LauncherToolRegistry.TOOL_APP_CLOSE_DRAWER, Band.SESSION_OVERLAY);

        // ------------------------------------------------------------------------ multiplexing
        tools.put(LauncherToolRegistry.TOOL_PANE_SPLIT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_SPLIT_VERTICAL, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_SPLIT_HORIZONTAL, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_FOCUS_DIRECTION, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_RESIZE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_KILL_FOCUSED, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_LAYOUT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_EQUALIZE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_ROTATE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_MOVE_TO_EDGE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_NEXT_LAYOUT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_TOGGLE_FLOAT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_OPEN, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_LIST, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_FOCUS, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_CLOSE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_WRITE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_READ, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_RENAME, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_PANE_RENAME_PROMPT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WINDOW_NEW, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WINDOW_CLOSE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WINDOW_SELECT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WINDOW_RENAME, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WINDOW_RENAME_PROMPT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_NEW, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_CLONE_CURRENT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_NEXT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_PREVIOUS, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_CLOSE_CURRENT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_RENAME, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_RENAME_AT_INDEX, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_RENAME_PROMPT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_SESSION_ACTIVATE_BY_INDEX, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WORKSPACE_SAVE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WORKSPACE_LOAD, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WORKSPACE_LIST, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WORKSPACE_DELETE, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WORKSPACE_PICKER, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_WORKSPACE_SAVE_PROMPT, Band.MULTIPLEX);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_SCRATCHPAD, Band.MULTIPLEX);

        // ---------------------------------------------------------------- the terminal's own
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_STATE, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_RESET, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_FONT_SIZE_INCREASE, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_FONT_SIZE_DECREASE, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_SELECT_URL, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_SELECT_AT_CURSOR, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_SELECT_ALL, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_HINTS, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_SEARCH_SCROLLBACK, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_SHARE_TRANSCRIPT, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_SHARE_SELECTED, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_ACTION_SHEET, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_TERMINAL_JUMP_NEXT_PROMPT, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_CLIPBOARD_PASTE, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_CLIPBOARD_COPY_SELECTED, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL, Band.TERMINAL_TOOL);
        // Mouse mode is the terminal's pointer and the Display's touchpad — it means something on
        // both, and nothing on a page of widgets.
        tools.put(LauncherToolRegistry.TOOL_MOUSE_TOGGLE, Band.TERMINAL_TOOL);
        // These two step through windows in the terminal and through apps on the Display, which is
        // why they are not filed with the rest of the window tools.
        tools.put(LauncherToolRegistry.TOOL_WINDOW_NEXT, Band.TERMINAL_TOOL);
        tools.put(LauncherToolRegistry.TOOL_WINDOW_PREVIOUS, Band.TERMINAL_TOOL);

        TOOLS = Collections.unmodifiableMap(tools);
    }

    /** Whether the key sending {@code keyValue} can act on {@code place}. */
    public static boolean isUsable(@Nullable String keyValue, @NonNull PaneWallPage place) {
        if (keyValue == null || keyValue.isEmpty())
            return true;
        // A macro is only as usable as its least usable step: half a sequence is not a shortcut.
        if (keyValue.indexOf(' ') >= 0) {
            for (String step : keyValue.split(" ")) {
                if (!step.isEmpty() && !bandOf(step).worksOn(place))
                    return false;
            }
            return true;
        }
        return bandOf(keyValue).worksOn(place);
    }

    /** The band one key value falls in. Never null: anything unrecognised types into the shell. */
    @NonNull
    public static Band bandOf(@NonNull String keyValue) {
        if (keyValue.startsWith(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX))
            return bandOfTool(toolNameOf(keyValue));
        Band rowKey = ROW_KEYS.get(keyValue);
        if (rowKey != null)
            return rowKey;
        for (String modifier : MODIFIERS) {
            if (modifier.equals(keyValue))
                return Band.TERMINAL_INPUT;
        }
        return Band.TERMINAL_INPUT;
    }

    /**
     * The band a registry tool falls in. An unknown name is {@link Band#LAUNCHER} — usable
     * everywhere — because a key we cannot place is better left live than greyed on a guess.
     */
    @NonNull
    public static Band bandOfTool(@NonNull String toolName) {
        Band band = TOOLS.get(toolName);
        return band == null ? Band.LAUNCHER : band;
    }

    /** Whether the classification has an explicit answer for a tool, for the coverage test. */
    public static boolean classifies(@NonNull String toolName) {
        return TOOLS.containsKey(toolName);
    }

    /**
     * The tool a {@code tool:} key names, without its prefix and without its arguments —
     * {@code tool:pane.move_to_edge:edge=left} is {@code pane.move_to_edge}.
     */
    @NonNull
    public static String toolNameOf(@NonNull String keyValue) {
        String spec = keyValue.startsWith(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX)
            ? keyValue.substring(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX.length())
            : keyValue;
        int colon = spec.indexOf(':');
        return colon > 0 ? spec.substring(0, colon) : spec;
    }
}
