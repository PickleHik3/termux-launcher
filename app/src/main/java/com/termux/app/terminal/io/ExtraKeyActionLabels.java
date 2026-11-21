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
     * entry. The Roundline set, except the page switcher's placeholder.
     */
    static final int[][] ICON_DRAWABLES = {
        {ExtraKeyIcons.ASSISTANT, R.drawable.ic_rl_assistant},
        {ExtraKeyIcons.PAGE_SWITCHER, R.drawable.ic_page_terminal},
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
    };

    /** Registers the code points that draw as drawables. Called once from the Application. */
    public static void registerIcons() {
        for (int[] row : ICON_DRAWABLES) NerdFontSpans.registerDrawableSymbol(row[0], row[1]);
    }

    /**
     * The glyph a tool's key cap draws when the tool has one worth a cap: the wall's places, the
     * mouse, keyboard, drawer, dictate, split, window, session, paste and palette keys wear a
     * Roundline icon. Null for everything else, where the title seeds the cap.
     */
    @Nullable
    public static String defaultCapGlyph(@NonNull String toolName) {
        switch (toolName) {
            case LauncherToolRegistry.TOOL_WALL_WIDGETS: return icon(ExtraKeyIcons.HOME);
            case LauncherToolRegistry.TOOL_WALL_TERMINAL: return icon(ExtraKeyIcons.TERMINAL);
            case LauncherToolRegistry.TOOL_WALL_DISPLAY: return icon(ExtraKeyIcons.DISPLAY);
            case LauncherToolRegistry.TOOL_WALL_ASSISTANT: return ASSISTANT_ICON;
            case LauncherToolRegistry.TOOL_APP_PAGE_SWITCHER: return PAGE_SWITCHER_ICON;
            case LauncherToolRegistry.TOOL_MOUSE_TOGGLE: return icon(ExtraKeyIcons.MOUSE);
            case LauncherToolRegistry.TOOL_KEYBOARD_CYCLE_FORM: return icon(ExtraKeyIcons.KEYBOARD);
            case LauncherToolRegistry.TOOL_APP_OPEN_APP_DRAWER: return icon(ExtraKeyIcons.APPS);
            case LauncherToolRegistry.TOOL_VOICE_DICTATE: return icon(ExtraKeyIcons.MIC);
            case LauncherToolRegistry.TOOL_PANE_SPLIT: return icon(ExtraKeyIcons.SPLIT);
            case LauncherToolRegistry.TOOL_SESSION_BROWSER: return icon(ExtraKeyIcons.SESSIONS);
            case LauncherToolRegistry.TOOL_CLIPBOARD_PASTE: return icon(ExtraKeyIcons.PASTE);
            case LauncherToolRegistry.TOOL_WINDOW_NEW: return icon(ExtraKeyIcons.WINDOW_ADD);
            case LauncherToolRegistry.TOOL_WINDOW_CLOSE: return icon(ExtraKeyIcons.WINDOW_CLOSE);
            case LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE: return icon(ExtraKeyIcons.COMMAND);
            default: return null;
        }
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
