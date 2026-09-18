package com.termux.app.help;

import android.content.Context;
import com.termux.R;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;

/**
 * What one of the reader's own extra keys is called. Everything else a control is called comes from
 * {@link HelpTopics}; only the keys are named by the reader's own key assignments, and a tool
 * identifier never becomes a label.
 */
public final class HelpCopy {
    private HelpCopy() {}

    /** The one key named for what it types rather than for a terminal key it sends. */
    private static final String PASTE_KEY = "PASTE";
    /** The keyboard key, which shows and hides the launcher's own keyboard. */
    private static final String KEYBOARD_KEY = "KEYBOARD";

    /**
     * Whether this cap is one of the launcher's own keys — a {@code tool:} action, the keyboard
     * key or paste — rather than a key that sends what it says it sends. Only the launcher's keys
     * are labelled: a reader already knows what ESC, TAB and the arrows do, and a label on every
     * cap is a row of labels nobody reads.
     */
    public static boolean isLauncherKey(ExtraKeyButton key) {
        if (key == null) return false;
        String name = key.getKey();
        if (name == null) return false;
        return name.startsWith(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX)
            || KEYBOARD_KEY.equals(name) || PASTE_KEY.equals(name);
    }

    public static String keyLabel(Context context, ExtraKeyButton key) {
        if (key == null) return "";
        String name = key.getKey();
        int label = 0;
        if (name.startsWith(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX)) {
            String toolName = name.substring(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX.length());
            int arguments = toolName.indexOf(':');
            if (arguments > 0) toolName = toolName.substring(0, arguments);
            switch (toolName) {
                case "keyboard.cycle_form": label = R.string.help_key_form; break;
                case "mouse.toggle": label = R.string.help_key_mouse; break;
                case "wall.widgets": label = R.string.help_key_widgets; break;
                case "wall.terminal": label = R.string.help_key_terminal; break;
                case "wall.display": label = R.string.help_key_display; break;
                case "pane.split": label = R.string.help_key_split; break;
                case "window.new": label = R.string.help_key_window; break;
                case "session.browser": label = R.string.help_key_sessions; break;
                case "session.new": label = R.string.help_key_session; break;
                case "keyboard.toggle": label = R.string.help_key_keyboard; break;
            }
            if (label == 0) {
                com.termux.launcherctl.LauncherToolRegistry.ToolMetadata tool =
                    com.termux.launcherctl.LauncherToolRegistry.getInstance().getTool(toolName);
                if (tool != null && tool.titleRes != 0) label = tool.titleRes;
                else if (name.equals(key.getDisplay())) label = R.string.help_key_action;
            }
        } else if (KEYBOARD_KEY.equals(name)) label = R.string.help_key_keyboard;
        else if (PASTE_KEY.equals(name)) label = R.string.help_key_paste;
        return label == 0 ? key.getDisplay() : context.getString(label);
    }
}
