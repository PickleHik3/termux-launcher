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
        } else if ("KEYBOARD".equals(name)) label = R.string.help_key_keyboard;
        return label == 0 ? key.getDisplay() : context.getString(label);
    }
}
