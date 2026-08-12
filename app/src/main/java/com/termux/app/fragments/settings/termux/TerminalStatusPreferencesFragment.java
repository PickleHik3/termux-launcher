package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.app.statusbar.EssentialNotificationRule;
import com.termux.app.statusbar.EssentialNotificationRules;
import com.termux.launcherctl.LauncherCtlNotificationListener;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/** User-facing terminal page that combines the old Terminal I/O and Terminal view screens. */
@Keep
public final class TerminalStatusPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(new TerminalStatusDataStore(context));
        setPreferencesFromResource(R.xml.terminal_status_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        SegmentedPillPreference alignment = findPreference("top_pane_clock_alignment");
        if (alignment != null) alignment.setSegments(
            new String[]{
                com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                    .TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT,
                com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                    .TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER,
                com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                    .TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT},
            new int[]{
                R.string.settings_clock_alignment_left,
                R.string.settings_clock_alignment_center,
                R.string.settings_clock_alignment_right});
        Preference customize = findPreference("customize_status_surface");
        if (customize != null) customize.setOnPreferenceClickListener(preference -> {
            openSurfaceEditor(context, "status");
            return true;
        });
        Preference access = findPreference("top_pane_notification_access");
        if (access != null) access.setOnPreferenceClickListener(preference -> {
            openNotificationAccessSettings(context);
            return true;
        });
        Preference essentialRules = findPreference("essential_notification_rules_manage");
        if (essentialRules != null) essentialRules.setOnPreferenceClickListener(preference -> {
            showEssentialRulesDialog(context);
            return true;
        });
    }

    /**
     * Management UI over the {@link EssentialNotificationRules} store the
     * {@code notifications.pin_rule_add}/{@code _remove} registry tools already drive: a list of
     * the stored rules with per-row delete, and package/keywords fields to add one.
     */
    private void showEssentialRulesDialog(Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * density);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, Math.round(8 * density), pad, 0);

        LinearLayout rulesList = new LinearLayout(context);
        rulesList.setOrientation(LinearLayout.VERTICAL);
        content.addView(rulesList);

        EditText packageInput = new EditText(context);
        packageInput.setHint(R.string.essential_rules_package_hint);
        packageInput.setSingleLine(true);
        packageInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        content.addView(packageInput);

        EditText keywordsInput = new EditText(context);
        keywordsInput.setHint(R.string.essential_rules_keywords_hint);
        keywordsInput.setSingleLine(true);
        content.addView(keywordsInput);

        CheckBox clearCheckbox = new CheckBox(context);
        clearCheckbox.setText(R.string.essential_rules_clear_label);
        content.addView(clearCheckbox);

        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.addView(content);

        androidx.appcompat.app.AlertDialog dialog =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.settings_essential_notifications_title)
                .setView(scroller)
                .setPositiveButton(R.string.essential_rules_add, null)
                .setNegativeButton(R.string.essential_rules_done, null)
                .create();
        dialog.setOnShowListener(shown -> {
            rebuildEssentialRulesList(context, preferences, rulesList);
            // Positive button adds without dismissing, so several rules can be entered in one go.
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(clicked -> {
                    String pkg = packageInput.getText().toString().trim();
                    String keywords = keywordsInput.getText().toString().trim();
                    if (pkg.isEmpty() && keywords.isEmpty()) {
                        Toast.makeText(context, R.string.essential_rules_needs_field,
                            Toast.LENGTH_SHORT).show();
                        return;
                    }
                    EssentialNotificationRule rule = new EssentialNotificationRule(
                        EssentialNotificationRules.deriveId(pkg, keywords), pkg, keywords,
                        clearCheckbox.isChecked());
                    if (EssentialNotificationRules.add(preferences, rule) == null) {
                        Toast.makeText(context, R.string.essential_rules_full,
                            Toast.LENGTH_SHORT).show();
                        return;
                    }
                    LauncherCtlNotificationListener.requestPinnedRefresh();
                    packageInput.setText("");
                    keywordsInput.setText("");
                    clearCheckbox.setChecked(false);
                    rebuildEssentialRulesList(context, preferences, rulesList);
                });
        });
        dialog.show();
    }

    private void rebuildEssentialRulesList(Context context,
                                           TermuxAppSharedPreferences preferences,
                                           LinearLayout rulesList) {
        rulesList.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;
        java.util.List<EssentialNotificationRule> rules =
            EssentialNotificationRules.load(preferences);
        if (rules.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.essential_rules_empty);
            empty.setPadding(0, 0, 0, Math.round(12 * density));
            rulesList.addView(empty);
            return;
        }
        for (EssentialNotificationRule rule : rules) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView label = new TextView(context);
            String pkg = rule.packageName.isEmpty()
                ? getString(R.string.essential_rules_any_package) : rule.packageName;
            String match = rule.match.isEmpty()
                ? getString(R.string.essential_rules_any_text) : "“" + rule.match + "”";
            label.setText(pkg + " · " + match);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label, labelParams);

            ImageButton remove = new ImageButton(context);
            remove.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            remove.setBackground(null);
            remove.setContentDescription(getString(R.string.essential_rules_remove_description));
            remove.setOnClickListener(clicked -> {
                if (EssentialNotificationRules.remove(preferences, rule.id) != null) {
                    LauncherCtlNotificationListener.requestPinnedRefresh();
                }
                rebuildEssentialRulesList(context, preferences, rulesList);
            });
            row.addView(remove);
            rulesList.addView(row);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_terminal_status);
        }
        Context context = getContext();
        Preference access = findPreference("top_pane_notification_access");
        if (context != null && access != null) {
            access.setSummary(LauncherNotificationAccess.isEnabled(context)
                ? R.string.termux_app_launcher_access_status_on
                : R.string.termux_top_pane_notification_access_summary);
        }
    }

    /** The media widget and pinned notifications both need listener access to have any data. */
    private void openNotificationAccessSettings(Context context) {
        Intent detail = LauncherNotificationAccess.detailSettingsIntent(context);
        if (detail != null && startSettingsIntent(context, detail)) return;
        startSettingsIntent(context, LauncherNotificationAccess.listSettingsIntent());
    }

    private boolean startSettingsIntent(Context context, @Nullable Intent intent) {
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openSurfaceEditor(Context context, String section) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(TermuxActivity.EXTRA_DOCK_TUNING, true);
        intent.putExtra(TermuxActivity.EXTRA_DOCK_TUNING_SECTION, section);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private static final class TerminalStatusDataStore extends PreferenceDataStore {
        private final TerminalIOPreferencesDataStore io;
        private final TerminalViewPreferencesDataStore view;

        TerminalStatusDataStore(Context context) {
            io = TerminalIOPreferencesDataStore.getInstance(context);
            view = TerminalViewPreferencesDataStore.getInstance(context);
        }

        @Override public void putBoolean(String key, boolean value) {
            if ("split_pane_controls".equals(key)) io.putBoolean("compatibility_mode", !value);
            else if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)) view.putBoolean(key, value);
            else io.putBoolean(key, value);
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            if ("split_pane_controls".equals(key)) return !io.getBoolean("compatibility_mode", !fallback);
            if ("fullscreen".equals(key) || "terminal_margin_adjustment".equals(key)) return view.getBoolean(key, fallback);
            return io.getBoolean(key, fallback);
        }

        @Override public void putString(String key, @Nullable String value) { io.putString(key, value); }
        @Override public String getString(String key, @Nullable String fallback) { return io.getString(key, fallback); }
    }
}
