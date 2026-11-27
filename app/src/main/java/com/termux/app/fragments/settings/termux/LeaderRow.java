package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.settings.LauncherSettings;
import com.termux.app.settings.LauncherSettingsAdapters;
import com.termux.app.leader.LeaderChordSettingAdapter;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.app.terminal.TerminalKeyBindingResolver;
import com.termux.app.terminal.inappkeyboard.TerlKeyboardLayout;
import com.termux.app.leader.Leader;
import com.termux.app.leader.LeaderChord;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

/**
 * The Leader row on the Keyboard page (ADR 0042): the Terl key, Ctrl+Alt, or Custom keys typed in
 * a small dialog that refuses Android's hardware chords with the reason and warns on Ctrl+Shift.
 * It saves through the Settings registry, so it is refused exactly as terl.conf and launcherctl
 * are, and the launcher's preference listener re-types the bindings.
 */
final class LeaderRow {

    static final String KEY = "keys_leader_row";

    private LeaderRow() {}

    /**
     * Wires {@code row}, a null-tolerant no-op when the page has no such row. The row shows only
     * with the Terl desktop switch on: the leader is Terl's, and until the switch-over today's
     * Keyboard page stays as it was (the setting itself is still there for terl.conf and
     * launcherctl, and its default keeps Ctrl+Alt as it always was).
     */
    static void bind(@NonNull Context context, Preference row) {
        if (row == null) return;
        boolean terl = TerlKeyboardLayout.isOn(context);
        row.setVisible(terl);
        if (!terl) return;
        refresh(context, row);
        row.setOnPreferenceClickListener(preference -> {
            choose(preference.getContext(), preference);
            return true;
        });
    }

    @NonNull
    private static SettingsRegistry registry(@NonNull Context context) {
        return LauncherSettings.registry(context, c -> LauncherSettingsAdapters.forDevice(c,
            () -> TerminalActionDispatcher.getInstance().redressChrome()));
    }

    @NonNull
    private static Leader current(@NonNull SettingsRegistry registry) {
        return Leader.of(registry.get(SettingDeclarations.KEYS_LEADER.id),
            registry.get(LeaderChordSettingAdapter.ID));
    }

    private static void refresh(@NonNull Context context, @NonNull Preference row) {
        Leader leader = current(registry(context));
        switch (leader.kind) {
            case CTRL_ALT:
                row.setSummary(R.string.settings_keys_leader_summary_ctrl_alt);
                break;
            case CUSTOM:
                row.setSummary(context.getString(R.string.settings_keys_leader_summary_custom,
                    TerminalKeyBindingResolver.displaySequence(leader.chord.spec())));
                break;
            default:
                row.setSummary(R.string.settings_keys_leader_summary_terl);
        }
    }

    private static void choose(@NonNull Context context, @NonNull Preference row) {
        SettingsRegistry registry = registry(context);
        Leader leader = current(registry);
        CharSequence[] choices = {
            context.getString(R.string.settings_keys_leader_choice_terl),
            context.getString(R.string.settings_keys_leader_choice_ctrl_alt),
            context.getString(R.string.settings_keys_leader_choice_custom)};
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_keys_leader_title)
            .setSingleChoiceItems(choices, leader.kind.ordinal(), (dialog, which) -> {
                dialog.dismiss();
                if (which == 2) {
                    askCustom(context, row, leader.chord == null ? "" : leader.chord.spec());
                    return;
                }
                registry.set(SettingDeclarations.KEYS_LEADER.id,
                    which == 0 ? Leader.TERL_KEY_NAME : Leader.CTRL_ALT_NAME);
                refresh(context, row);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static void askCustom(@NonNull Context context, @NonNull Preference row,
                                  @NonNull String chord) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(R.string.settings_keys_leader_custom_hint);
        input.setText(chord);
        int pad = Math.round(20 * context.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
        frame.setPadding(pad, 0, pad, 0);
        frame.addView(input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_keys_leader_custom_title)
            .setMessage(R.string.settings_keys_leader_custom_message)
            .setView(frame)
            .setPositiveButton(R.string.settings_keys_leader_custom_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(button -> {
                LeaderChord.Check check = LeaderChord.parse(input.getText().toString());
                if (!check.ok()) {
                    // Refused: say why and keep the dialog, so the keys can be fixed in place.
                    input.setError(check.refusal);
                    return;
                }
                SettingsRegistry registry = registry(context);
                registry.set(LeaderChordSettingAdapter.ID, check.chord.spec());
                registry.set(SettingDeclarations.KEYS_LEADER.id, Leader.CUSTOM_NAME);
                if (check.warning != null)
                    Toast.makeText(context, check.warning, Toast.LENGTH_LONG).show();
                refresh(context, row);
                dialog.dismiss();
            }));
        dialog.show();
    }
}
