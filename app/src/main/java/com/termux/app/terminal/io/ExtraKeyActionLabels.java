package com.termux.app.terminal.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;

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

    /** True for a name the row treats as a key rather than as text to type. */
    public static boolean isNamedKey(@NonNull String spec) {
        return ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.containsKey(spec)
            || ExtraKeysPresets.isModifier(spec)
            || ExtraKeysPresets.isRowKey(spec)
            || "DRAWER".equals(spec);
    }
}
