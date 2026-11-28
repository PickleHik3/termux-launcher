package com.termux.app.fragments.settings.terl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.settings.LauncherSettings;
import com.termux.app.settings.LauncherSettingsAdapters;
import com.termux.app.settings.UseAsAdapter;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsFile;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

/**
 * Terl's Settings on the device: whether they are on, the registry and applier every row saves
 * through, what this phone shows, how a value reads on screen, and the two things every setting
 * row offers besides its own control: Reset and Copy as config.
 */
public final class TerlSettings {

    private TerlSettings() {}

    /** Whether Settings opens as Terl's: the Terl desktop switch ({@code terl.desktop}). */
    public static boolean isOn(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        return preferences != null && preferences.isTerlDesktopEnabled();
    }

    /**
     * Terl's Settings is one page at a time on every screen: on a wide screen the root list that
     * would stand beside the page ({@code list}) and the line between them step aside.
     */
    public static void keepOnePane(@NonNull android.view.View list) {
        android.view.ViewParent parent = list.getParent();
        if (!(parent instanceof android.view.ViewGroup)) {
            list.setVisibility(android.view.View.GONE);
            return;
        }
        android.view.ViewGroup row = (android.view.ViewGroup) parent;
        for (int i = 0; i < row.getChildCount(); i++) {
            android.view.View child = row.getChildAt(i);
            if (child.getId() != R.id.settings) child.setVisibility(android.view.View.GONE);
        }
    }

    /** The app's one registry, with the device's adapters. */
    @NonNull
    public static SettingsRegistry registry(@NonNull Context context) {
        return LauncherSettings.registry(context, c -> LauncherSettingsAdapters.forDevice(c,
            () -> TerminalActionDispatcher.getInstance().redressChrome()));
    }

    @NonNull
    static SettingsApplier applier() {
        return LauncherSettings.applier();
    }

    /** Whether this build and phone show what {@code need} asks for. */
    static boolean has(@NonNull Context context, @NonNull TerlSettingsTree.Need need) {
        switch (need) {
            case X11_SERVER: return com.termux.app.x11.X11ServerSupport.isAvailable(context);
            case WAYLAND: return com.termux.BuildConfig.WAYLAND_DISPLAY;
            case TERMINAL_ENGINE: return com.termux.terminal.TerminalEngine.isAvailable();
            default: return true;
        }
    }

    /** Whether a group is offered here: Linux apps only in a build with the X server. */
    static boolean shows(@NonNull Context context, @NonNull TerlSettingsTree.Group group) {
        return has(context, group.need);
    }

    /** Whether the setting row for {@code id} is shown on this phone. */
    static boolean shows(@NonNull Context context, @NonNull String id) {
        TerlSettingsTree.Item item = TerlSettingsTree.setting(id);
        return item != null && shows(context, item.group) && has(context, item.need);
    }

    /** Whether Use Terl as is Terminal, so Home's rows give way to one line. */
    static boolean homeIsOff(@NonNull Context context) {
        return TERMUX_APP.USE_AS_TERMINAL.equals(registry(context).get(UseAsAdapter.ID));
    }

    /** {@code value} of {@code id} as its row shows it: a choice's label, On or Off, a number. */
    @NonNull
    static String label(@NonNull Context context, @NonNull SettingDeclaration declaration,
                        @NonNull String value) {
        switch (declaration.kind) {
            case BOOLEAN:
                return context.getString(Boolean.parseBoolean(value) ? R.string.terl_settings_on
                    : R.string.terl_settings_off);
            case CHOICE: {
                CharSequence[] labels = labels(context, declaration);
                int index = declaration.choices.indexOf(value);
                return index >= 0 && index < labels.length ? labels[index].toString() : value;
            }
            default:
                return value;
        }
    }

    /** A choice's labels in its order: the row's own, else its names with a capital. */
    @NonNull
    static CharSequence[] labels(@NonNull Context context, @NonNull SettingDeclaration declaration) {
        TerlSettingsTree.Item item = TerlSettingsTree.setting(declaration.id);
        if (item != null && item.labelsRes != 0) {
            CharSequence[] labels = context.getResources().getTextArray(item.labelsRes);
            if (labels.length == declaration.choices.size()) return labels;
        }
        CharSequence[] labels = new CharSequence[declaration.choices.size()];
        for (int i = 0; i < labels.length; i++) {
            String name = declaration.choices.get(i).replace('_', ' ');
            labels[i] = name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return labels;
    }

    /** Puts {@code id} back on its default and refreshes the launcher; says so, or why not. */
    static boolean reset(@NonNull Context context, @NonNull String id) {
        SettingsRegistry registry = registry(context);
        SettingsRegistry.Outcome outcome = TerlChanged.reset(registry, id);
        SettingsRegistry.Refusal refusal = outcome.refusal();
        if (refusal != null) {
            Toast.makeText(context, refusal.reason, Toast.LENGTH_SHORT).show();
            return false;
        }
        SettingsRegistry.Change change = outcome.change();
        if (change != null) {
            applier().apply(change);
            Toast.makeText(context, context.getString(R.string.terl_settings_reset_done,
                change.declaration.title, label(context, change.declaration, change.newValue)),
                Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** The row's terl.conf line, or null for a setting the file does not carry. */
    @Nullable
    static String configLine(@NonNull Context context, @NonNull String id) {
        return SettingsFile.line(registry(context), id);
    }

    /** Puts the row's terl.conf line on the clipboard (Settings' rows, and edit mode's cards' rows). */
    public static void copyAsConfig(@NonNull Context context, @NonNull String id) {
        String line = configLine(context, id);
        if (line == null) return;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("terl.conf", line));
        // Android 13 shows its own note of what was copied.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(R.string.terl_settings_copied, line),
                Toast.LENGTH_SHORT).show();
        }
    }
}
