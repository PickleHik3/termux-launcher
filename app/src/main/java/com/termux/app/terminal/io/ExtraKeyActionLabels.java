package com.termux.app.terminal.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeyIcons;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.font.NerdFontSpans;

/**
 * Human wording for what a key does, shared by the editor page, the key sheet and the picker so
 * the same action never reads two ways: a terminal key is its name, a launcher action is its
 * palette title, a macro lists its keys, anything else is text the key types.
 */
public final class ExtraKeyActionLabels {

    private static final String TOOL_PREFIX = "tool:";

    private ExtraKeyActionLabels() {}

    /** The action's title as a sentence fragment: "CTRL", "Search scrollback", "Types \"ls\"". */
    @NonNull
    public static String title(@NonNull Context context, @NonNull ExtraKeysLayoutModel.Key key) {
        if (key.macro) {
            return context.getString(R.string.settings_extra_keys_action_macro_title, key.key);
        }
        String spec = key.key;
        if (spec.startsWith(TOOL_PREFIX)) {
            String rest = spec.substring(TOOL_PREFIX.length());
            int argumentStart = rest.indexOf(':');
            String name = argumentStart < 0 ? rest : rest.substring(0, argumentStart);
            String argument = argumentStart < 0 ? null : rest.substring(argumentStart + 1);
            LauncherToolRegistry.ToolMetadata tool = LauncherToolRegistry.getInstance().getTool(name);
            String label = tool != null && tool.titleRes != 0 ? context.getString(tool.titleRes) : name;
            return argument == null || argument.isEmpty() ? label : label + " · " + argument;
        }
        if (isNamedKey(spec)) return spec;
        return context.getString(R.string.settings_extra_keys_action_types_title, spec);
    }

    /** The identifier under the title, or null when the title already is the identifier. */
    @Nullable
    public static String detail(@NonNull ExtraKeysLayoutModel.Key key) {
        if (key.macro) return null;
        if (key.key.startsWith(TOOL_PREFIX)) return key.key;
        return null;
    }

    /** What the cap draws: the same resolution the live row performs. */
    @NonNull
    public static String capText(@NonNull ExtraKeysLayoutModel.Key key,
                                 @NonNull ExtraKeysConstants.ExtraKeyDisplayMap displayMap) {
        return ExtraKeyButton.resolveDisplay(key.key, key.display, displayMap);
    }

    /** The Assistant key's cap; see {@link ExtraKeyIcons}. */
    public static final String ASSISTANT_ICON = ExtraKeyIcons.string(ExtraKeyIcons.ASSISTANT);

    /**
     * The page switcher key's placeholder, for the editor and the key sheet: the Terminal page's
     * glyph. On the bar the cap draws the current page's glyph and number itself.
     */
    public static final String PAGE_SWITCHER_ICON = ExtraKeyIcons.string(ExtraKeyIcons.PAGE_SWITCHER);

    /**
     * Each icon code point with the drawable that draws it, one row per {@link ExtraKeyIcons#ALL}
     * entry, all Roundline.
     */
    public static final int[][] ICON_DRAWABLES = {
        {ExtraKeyIcons.ASSISTANT, R.drawable.ic_rl_assistant},
        {ExtraKeyIcons.PAGE_SWITCHER, R.drawable.ic_rl_pg_terminal},
        {ExtraKeyIcons.HOME, R.drawable.ic_rl_home},
        {ExtraKeyIcons.TERMINAL, R.drawable.ic_rl_terminal},
        {ExtraKeyIcons.DISPLAY, R.drawable.ic_rl_display},
        {ExtraKeyIcons.MOUSE, R.drawable.ic_rl_mouse},
        {ExtraKeyIcons.KEYBOARD, R.drawable.ic_rl_keyboard},
        {ExtraKeyIcons.APPS, R.drawable.ic_rl_apps},
        {ExtraKeyIcons.MIC, R.drawable.ic_rl_mic},
        {ExtraKeyIcons.SPLIT, R.drawable.ic_rl_split},
        {ExtraKeyIcons.SESSIONS, R.drawable.ic_rl_sessions},
        {ExtraKeyIcons.PASTE, R.drawable.ic_rl_paste},
        {ExtraKeyIcons.WINDOW_ADD, R.drawable.ic_rl_window_add},
        {ExtraKeyIcons.WINDOW_CLOSE, R.drawable.ic_rl_window_close},
        {ExtraKeyIcons.COMMAND, R.drawable.ic_rl_command},
        {ExtraKeyIcons.ARROW_LEFT, R.drawable.ic_rl_arrow_left},
        {ExtraKeyIcons.ARROW_RIGHT, R.drawable.ic_rl_arrow_right},
        {ExtraKeyIcons.ARROW_UP, R.drawable.ic_rl_arrow_up},
        {ExtraKeyIcons.ARROW_DOWN, R.drawable.ic_rl_arrow_down},
        {ExtraKeyIcons.ENTER, R.drawable.ic_rl_enter},
        {ExtraKeyIcons.TAB, R.drawable.ic_rl_tab},
        {ExtraKeyIcons.BACKSPACE, R.drawable.ic_rl_backspace},
        {ExtraKeyIcons.DELETE_FORWARD, R.drawable.ic_rl_delete_forward},
        {ExtraKeyIcons.MENU, R.drawable.ic_rl_menu},
        {ExtraKeyIcons.SCROLL, R.drawable.ic_rl_scroll},
        {ExtraKeyIcons.KEY_HOME, R.drawable.ic_rl_key_home},
        {ExtraKeyIcons.KEY_END, R.drawable.ic_rl_key_end},
        {ExtraKeyIcons.PAGE_UP, R.drawable.ic_rl_page_up},
        {ExtraKeyIcons.PAGE_DOWN, R.drawable.ic_rl_page_down},
        {ExtraKeyIcons.DOCK, R.drawable.ic_rl_dock},
        {ExtraKeyIcons.ZOOM_IN, R.drawable.ic_rl_zoom_in},
        {ExtraKeyIcons.ZOOM_OUT, R.drawable.ic_rl_zoom_out},
        {ExtraKeyIcons.LINK, R.drawable.ic_rl_link},
        {ExtraKeyIcons.SELECT, R.drawable.ic_rl_select},
        {ExtraKeyIcons.SELECT_ALL, R.drawable.ic_rl_select_all},
        {ExtraKeyIcons.QUICK_SELECT, R.drawable.ic_rl_quick_select},
        {ExtraKeyIcons.FIND, R.drawable.ic_rl_find},
        {ExtraKeyIcons.SHARE, R.drawable.ic_rl_share},
        {ExtraKeyIcons.RESET, R.drawable.ic_rl_reset},
        {ExtraKeyIcons.EDIT, R.drawable.ic_rl_edit},
        {ExtraKeyIcons.FLOAT_WINDOW, R.drawable.ic_rl_float_window},
        {ExtraKeyIcons.ADD, R.drawable.ic_rl_add},
        {ExtraKeyIcons.CLONE, R.drawable.ic_rl_clone},
        {ExtraKeyIcons.NEXT, R.drawable.ic_rl_next},
        {ExtraKeyIcons.PREVIOUS, R.drawable.ic_rl_previous},
        {ExtraKeyIcons.CLOSE, R.drawable.ic_rl_close},
        {ExtraKeyIcons.SWITCH_INDEX, R.drawable.ic_rl_switch_index},
        {ExtraKeyIcons.LOAD, R.drawable.ic_rl_load},
        {ExtraKeyIcons.SAVE, R.drawable.ic_rl_save},
        {ExtraKeyIcons.LAYOUT, R.drawable.ic_rl_layout},
        {ExtraKeyIcons.ROTATE, R.drawable.ic_rl_rotate},
        {ExtraKeyIcons.EQUALIZE, R.drawable.ic_rl_equalize},
        {ExtraKeyIcons.CONSUME, R.drawable.ic_rl_consume},
        {ExtraKeyIcons.EXPEL, R.drawable.ic_rl_expel},
        {ExtraKeyIcons.MOVE_WINDOW, R.drawable.ic_rl_move_window},
        {ExtraKeyIcons.CLIPBOARD, R.drawable.ic_rl_clipboard},
        {ExtraKeyIcons.COPY, R.drawable.ic_rl_copy},
        {ExtraKeyIcons.WALLPAPER, R.drawable.ic_rl_wallpaper},
        {ExtraKeyIcons.CURSOR_TRAIL, R.drawable.ic_rl_cursor_trail},
        {ExtraKeyIcons.APPEARANCE, R.drawable.ic_rl_appearance},
        {ExtraKeyIcons.FONT, R.drawable.ic_rl_font},
        {ExtraKeyIcons.SETTINGS, R.drawable.ic_rl_settings},
        {ExtraKeyIcons.HELP, R.drawable.ic_rl_help},
        {ExtraKeyIcons.MINIMAL, R.drawable.ic_rl_minimal},
        {ExtraKeyIcons.KEY_INSPECTOR, R.drawable.ic_rl_key_inspector},
        {ExtraKeyIcons.GO_PAGE, R.drawable.ic_rl_go_page},
        {ExtraKeyIcons.PROMPT_PREVIOUS, R.drawable.ic_rl_prompt_previous},
        {ExtraKeyIcons.PROMPT_NEXT, R.drawable.ic_rl_prompt_next},
        {ExtraKeyIcons.PANE_FOCUS, R.drawable.ic_rl_pane_focus},
        {ExtraKeyIcons.PANE_FOCUS_LEFT, R.drawable.ic_rl_pane_focus_left},
        {ExtraKeyIcons.PANE_FOCUS_RIGHT, R.drawable.ic_rl_pane_focus_right},
        {ExtraKeyIcons.PANE_FOCUS_UP, R.drawable.ic_rl_pane_focus_up},
        {ExtraKeyIcons.PANE_FOCUS_DOWN, R.drawable.ic_rl_pane_focus_down},
        {ExtraKeyIcons.PANE_RESIZE, R.drawable.ic_rl_pane_resize},
        {ExtraKeyIcons.PANE_MOVE_EDGE, R.drawable.ic_rl_pane_move_edge},
        {ExtraKeyIcons.PANE_RESIZE_LEFT, R.drawable.ic_rl_pane_resize_left},
        {ExtraKeyIcons.PANE_RESIZE_RIGHT, R.drawable.ic_rl_pane_resize_right},
        {ExtraKeyIcons.PANE_RESIZE_UP, R.drawable.ic_rl_pane_resize_up},
        {ExtraKeyIcons.PANE_RESIZE_DOWN, R.drawable.ic_rl_pane_resize_down},
        {ExtraKeyIcons.PANE_MOVE_EDGE_LEFT, R.drawable.ic_rl_pane_move_edge_left},
        {ExtraKeyIcons.PANE_MOVE_EDGE_RIGHT, R.drawable.ic_rl_pane_move_edge_right},
        {ExtraKeyIcons.PANE_MOVE_EDGE_UP, R.drawable.ic_rl_pane_move_edge_up},
        {ExtraKeyIcons.PANE_MOVE_EDGE_DOWN, R.drawable.ic_rl_pane_move_edge_down},
    };

    /** Registers the code points that draw as drawables. Called once from the Application. */
    public static void registerIcons() {
        for (int[] row : ICON_DRAWABLES) NerdFontSpans.registerDrawableSymbol(row[0], row[1]);
    }

    /**
     * The glyph a tool's key cap draws when the tool has one worth a cap: most launcher actions
     * wear a Roundline icon, several actions sharing one (every keyboard action, the renames).
     * Null for everything else, where the title seeds the cap.
     */
    @Nullable
    public static String defaultCapGlyph(@NonNull String toolName) {
        switch (toolName) {
            case LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_TOOLBAR: return icon(ExtraKeyIcons.DOCK);
            case LauncherToolRegistry.TOOL_TERMINAL_FONT_SIZE_INCREASE: return icon(ExtraKeyIcons.ZOOM_IN);
            case LauncherToolRegistry.TOOL_TERMINAL_FONT_SIZE_DECREASE: return icon(ExtraKeyIcons.ZOOM_OUT);
            case LauncherToolRegistry.TOOL_TERMINAL_FONT_SIZE_TOGGLE_COMPACT: return icon(ExtraKeyIcons.FONT);
            case LauncherToolRegistry.TOOL_TERMINAL_SELECT_URL: return icon(ExtraKeyIcons.LINK);
            case LauncherToolRegistry.TOOL_TERMINAL_SELECT_AT_CURSOR: return icon(ExtraKeyIcons.SELECT);
            case LauncherToolRegistry.TOOL_TERMINAL_SELECT_ALL: return icon(ExtraKeyIcons.SELECT_ALL);
            case LauncherToolRegistry.TOOL_TERMINAL_HINTS: return icon(ExtraKeyIcons.QUICK_SELECT);
            case LauncherToolRegistry.TOOL_TERMINAL_SEARCH_SCROLLBACK: return icon(ExtraKeyIcons.FIND);
            case LauncherToolRegistry.TOOL_TERMINAL_SHARE_TRANSCRIPT: return icon(ExtraKeyIcons.SHARE);
            case LauncherToolRegistry.TOOL_TERMINAL_SHARE_SELECTED: return icon(ExtraKeyIcons.SHARE);
            case LauncherToolRegistry.TOOL_TERMINAL_RESET: return icon(ExtraKeyIcons.RESET);
            case LauncherToolRegistry.TOOL_EXTRA_KEYS_EDIT: return icon(ExtraKeyIcons.EDIT);
            case LauncherToolRegistry.TOOL_WINDOW_RENAME: return icon(ExtraKeyIcons.EDIT);
            case LauncherToolRegistry.TOOL_WINDOW_RENAME_PROMPT: return icon(ExtraKeyIcons.EDIT);
            case LauncherToolRegistry.TOOL_SESSION_RENAME: return icon(ExtraKeyIcons.EDIT);
            case LauncherToolRegistry.TOOL_SESSION_RENAME_AT_INDEX: return icon(ExtraKeyIcons.EDIT);
            case LauncherToolRegistry.TOOL_SESSION_RENAME_PROMPT: return icon(ExtraKeyIcons.EDIT);
            case LauncherToolRegistry.TOOL_PANE_RENAME: return icon(ExtraKeyIcons.EDIT);
            case LauncherToolRegistry.TOOL_PANE_RENAME_PROMPT: return icon(ExtraKeyIcons.EDIT);
            case LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_SCRATCHPAD: return icon(ExtraKeyIcons.FLOAT_WINDOW);
            case LauncherToolRegistry.TOOL_PANE_TOGGLE_FLOAT: return icon(ExtraKeyIcons.FLOAT_WINDOW);
            case LauncherToolRegistry.TOOL_TERMINAL_ACTION_SHEET: return icon(ExtraKeyIcons.MENU);
            case LauncherToolRegistry.TOOL_SESSION_NEW: return icon(ExtraKeyIcons.ADD);
            case LauncherToolRegistry.TOOL_SESSION_CLONE_CURRENT: return icon(ExtraKeyIcons.CLONE);
            case LauncherToolRegistry.TOOL_SESSION_NEXT: return icon(ExtraKeyIcons.NEXT);
            case LauncherToolRegistry.TOOL_WINDOW_NEXT: return icon(ExtraKeyIcons.NEXT);
            case LauncherToolRegistry.TOOL_SESSION_PREVIOUS: return icon(ExtraKeyIcons.PREVIOUS);
            case LauncherToolRegistry.TOOL_WINDOW_PREVIOUS: return icon(ExtraKeyIcons.PREVIOUS);
            case LauncherToolRegistry.TOOL_SESSION_CLOSE_CURRENT: return icon(ExtraKeyIcons.CLOSE);
            case LauncherToolRegistry.TOOL_PANE_KILL_FOCUSED: return icon(ExtraKeyIcons.CLOSE);
            case LauncherToolRegistry.TOOL_WINDOW_SELECT: return icon(ExtraKeyIcons.SWITCH_INDEX);
            case LauncherToolRegistry.TOOL_SESSION_ACTIVATE_BY_INDEX: return icon(ExtraKeyIcons.SWITCH_INDEX);
            case LauncherToolRegistry.TOOL_WORKSPACE_PICKER: return icon(ExtraKeyIcons.LOAD);
            case LauncherToolRegistry.TOOL_WORKSPACE_LOAD: return icon(ExtraKeyIcons.LOAD);
            case LauncherToolRegistry.TOOL_WORKSPACE_SAVE_PROMPT: return icon(ExtraKeyIcons.SAVE);
            case LauncherToolRegistry.TOOL_WORKSPACE_SAVE: return icon(ExtraKeyIcons.SAVE);
            case LauncherToolRegistry.TOOL_PANE_LAYOUT: return icon(ExtraKeyIcons.LAYOUT);
            case LauncherToolRegistry.TOOL_PANE_NEXT_LAYOUT: return icon(ExtraKeyIcons.LAYOUT);
            case LauncherToolRegistry.TOOL_PANE_ROTATE: return icon(ExtraKeyIcons.ROTATE);
            case LauncherToolRegistry.TOOL_PANE_EQUALIZE: return icon(ExtraKeyIcons.EQUALIZE);
            case LauncherToolRegistry.TOOL_PANE_CONSUME: return icon(ExtraKeyIcons.CONSUME);
            case LauncherToolRegistry.TOOL_PANE_EXPEL: return icon(ExtraKeyIcons.EXPEL);
            case LauncherToolRegistry.TOOL_PANE_SPLIT: return icon(ExtraKeyIcons.SPLIT);
            case LauncherToolRegistry.TOOL_PANE_SPLIT_VERTICAL: return icon(ExtraKeyIcons.SPLIT);
            case LauncherToolRegistry.TOOL_PANE_SPLIT_HORIZONTAL: return icon(ExtraKeyIcons.SPLIT);
            case LauncherToolRegistry.TOOL_WINDOW_MOVE: return icon(ExtraKeyIcons.MOVE_WINDOW);
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_LEFT: return icon(ExtraKeyIcons.MOVE_WINDOW);
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_RIGHT: return icon(ExtraKeyIcons.MOVE_WINDOW);
            case LauncherToolRegistry.TOOL_KEYBOARD_CLIPBOARD: return icon(ExtraKeyIcons.CLIPBOARD);
            case LauncherToolRegistry.TOOL_CLIPBOARD_COPY_SELECTED: return icon(ExtraKeyIcons.COPY);
            case LauncherToolRegistry.TOOL_APPEARANCE_SET_WALLPAPER: return icon(ExtraKeyIcons.WALLPAPER);
            case LauncherToolRegistry.TOOL_APPEARANCE_TOGGLE_WALLPAPER: return icon(ExtraKeyIcons.WALLPAPER);
            case LauncherToolRegistry.TOOL_APPEARANCE_TOGGLE_CURSOR_TRAIL: return icon(ExtraKeyIcons.CURSOR_TRAIL);
            case LauncherToolRegistry.TOOL_APPEARANCE_SURFACE_EDITOR: return icon(ExtraKeyIcons.APPEARANCE);
            case LauncherToolRegistry.TOOL_FONTS_PICK: return icon(ExtraKeyIcons.FONT);
            case LauncherToolRegistry.TOOL_FONTS_INSTALL: return icon(ExtraKeyIcons.FONT);
            case LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS: return icon(ExtraKeyIcons.SETTINGS);
            case LauncherToolRegistry.TOOL_APP_OPEN_LOOK_AND_FEEL: return icon(ExtraKeyIcons.SETTINGS);
            case LauncherToolRegistry.TOOL_APP_OPEN_APPS_BAR: return icon(ExtraKeyIcons.SETTINGS);
            case LauncherToolRegistry.TOOL_APP_OPEN_HELP: return icon(ExtraKeyIcons.HELP);
            case LauncherToolRegistry.TOOL_MINIMAL_TOGGLE: return icon(ExtraKeyIcons.MINIMAL);
            case LauncherToolRegistry.TOOL_APP_KEY_INSPECTOR: return icon(ExtraKeyIcons.KEY_INSPECTOR);
            case LauncherToolRegistry.TOOL_WALL_GO: return icon(ExtraKeyIcons.GO_PAGE);
            case LauncherToolRegistry.TOOL_TERMINAL_JUMP_PREVIOUS_PROMPT: return icon(ExtraKeyIcons.PROMPT_PREVIOUS);
            case LauncherToolRegistry.TOOL_TERMINAL_JUMP_NEXT_PROMPT: return icon(ExtraKeyIcons.PROMPT_NEXT);
            case LauncherToolRegistry.TOOL_PANE_FOCUS_DIRECTION: return icon(ExtraKeyIcons.PANE_FOCUS);
            case LauncherToolRegistry.TOOL_PANE_RESIZE: return icon(ExtraKeyIcons.PANE_RESIZE);
            case LauncherToolRegistry.TOOL_PANE_MOVE_TO_EDGE: return icon(ExtraKeyIcons.PANE_MOVE_EDGE);
            case LauncherToolRegistry.TOOL_APP_LAUNCH: return icon(ExtraKeyIcons.APPS);
            case LauncherToolRegistry.TOOL_APP_OPEN_APP_DRAWER: return icon(ExtraKeyIcons.APPS);
            case LauncherToolRegistry.TOOL_APP_OPEN_DRAWER: return icon(ExtraKeyIcons.SESSIONS);
            case LauncherToolRegistry.TOOL_APP_CLOSE_DRAWER: return icon(ExtraKeyIcons.SESSIONS);
            case LauncherToolRegistry.TOOL_SESSION_PANEL: return icon(ExtraKeyIcons.SESSIONS);
            case LauncherToolRegistry.TOOL_SESSION_BROWSER: return icon(ExtraKeyIcons.SESSIONS);
            case LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_SOFT_KEYBOARD: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_KEYBOARD_SHOW: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_KEYBOARD_HIDE: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_KEYBOARD_TOGGLE_ENABLED: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_KEYBOARD_CYCLE_LAYOUT: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_KEYBOARD_SELECT_LAYOUT: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_KEYBOARD_SET_FORM: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_KEYBOARD_CYCLE_FORM: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_WALL_WIDGETS: return icon(ExtraKeyIcons.HOME);
            case LauncherToolRegistry.TOOL_WALL_TERMINAL: return icon(ExtraKeyIcons.TERMINAL);
            case LauncherToolRegistry.TOOL_WALL_DISPLAY: return icon(ExtraKeyIcons.DISPLAY);
            case LauncherToolRegistry.TOOL_MOUSE_TOGGLE: return icon(ExtraKeyIcons.MOUSE);
            case LauncherToolRegistry.TOOL_VOICE_DICTATE: return icon(ExtraKeyIcons.MIC);
            case LauncherToolRegistry.TOOL_CLIPBOARD_PASTE: return icon(ExtraKeyIcons.PASTE);
            case LauncherToolRegistry.TOOL_WINDOW_NEW: return icon(ExtraKeyIcons.WINDOW_ADD);
            case LauncherToolRegistry.TOOL_WINDOW_CLOSE: return icon(ExtraKeyIcons.WINDOW_CLOSE);
            case LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE: return icon(ExtraKeyIcons.COMMAND);
            case LauncherToolRegistry.TOOL_WALL_ASSISTANT: return ASSISTANT_ICON;
            case LauncherToolRegistry.TOOL_APP_PAGE_SWITCHER: return PAGE_SWITCHER_ICON;
            default: return null;
        }
    }

    /**
     * The cap for a row that picks one value of a fixed set. The value is what tells these rows
     * apart, so they keep their text, except the wall's three places and the directions of focus, resize
     * and move-to-edge, which wear their own icon.
     *
     * @param choice the picked value, or null for a row that fixes no value
     */
    @Nullable
    public static String defaultCapGlyph(@NonNull String toolName, @Nullable String choice) {
        if (choice == null) return defaultCapGlyph(toolName);
        int kind = LauncherToolRegistry.TOOL_PANE_FOCUS_DIRECTION.equals(toolName) ? 0
            : LauncherToolRegistry.TOOL_PANE_RESIZE.equals(toolName) ? 1
            : LauncherToolRegistry.TOOL_PANE_MOVE_TO_EDGE.equals(toolName) ? 2 : -1;
        if (kind >= 0) {
            int[] left = {ExtraKeyIcons.PANE_FOCUS_LEFT, ExtraKeyIcons.PANE_RESIZE_LEFT, ExtraKeyIcons.PANE_MOVE_EDGE_LEFT};
            int[] right = {ExtraKeyIcons.PANE_FOCUS_RIGHT, ExtraKeyIcons.PANE_RESIZE_RIGHT, ExtraKeyIcons.PANE_MOVE_EDGE_RIGHT};
            int[] up = {ExtraKeyIcons.PANE_FOCUS_UP, ExtraKeyIcons.PANE_RESIZE_UP, ExtraKeyIcons.PANE_MOVE_EDGE_UP};
            int[] down = {ExtraKeyIcons.PANE_FOCUS_DOWN, ExtraKeyIcons.PANE_RESIZE_DOWN, ExtraKeyIcons.PANE_MOVE_EDGE_DOWN};
            switch (choice) {
                case "left": return icon(left[kind]);
                case "right": return icon(right[kind]);
                case "up": return icon(up[kind]);
                case "down": return icon(down[kind]);
                default: return null;
            }
        }
        if (!LauncherToolRegistry.TOOL_WALL_GO.equals(toolName)) return null;
        switch (choice) {
            case "widgets": return icon(ExtraKeyIcons.HOME);
            case "terminal": return icon(ExtraKeyIcons.TERMINAL);
            case "display": return icon(ExtraKeyIcons.DISPLAY);
            default: return null;
        }
    }

    /** The drawable of {@code toolName}'s bar icon, or 0 when the tool has none. */
    @androidx.annotation.DrawableRes
    public static int iconDrawable(@NonNull String toolName) {
        String glyph = defaultCapGlyph(toolName);
        if (glyph == null) return 0;
        int codePoint = glyph.codePointAt(0);
        for (int[] row : ICON_DRAWABLES) if (row[0] == codePoint) return row[1];
        return 0;
    }

    private static String icon(int codePoint) {
        return ExtraKeyIcons.string(codePoint);
    }

    /** True for a name the row treats as a key rather than as text to type. */
    public static boolean isNamedKey(@NonNull String spec) {
        return ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(spec)
            || ExtraKeysPresets.isModifier(spec)
            || ExtraKeysPresets.isRowKey(spec)
            || "DRAWER".equals(spec);
    }
}
