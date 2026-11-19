package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;
import com.termux.app.theme.templates.ThemeTemplate;
import com.termux.app.theme.templates.ThemeTemplates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Terminal › TUI app themes: which tools wear the terminal's colours. The Appearance editor's
 * Global card links here. The row's key and store are the ones the old Look page used.
 */
@Keep
public class TuiAppThemesPreferencesFragment extends MaterialPreferenceFragment {

    /** The shipped templates by id, for the summary line and the setup a tool still needs. */
    private final Map<String, ThemeTemplate> mThemeTemplates = new LinkedHashMap<>();

    /** The setup dialog on screen, so the view going away takes it along instead of leaking it. */
    @Nullable
    private AlertDialog mThemeTemplateSetupDialog;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.tui_app_themes_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configureThemeTemplatesPreference();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.appearance_row_tool_colours);
        }
        configureThemeTemplatesPreference();
    }

    /**
     * The list of tools that can take the terminal palette, named by the templates that do it.
     *
     * <p>The entries are the shipped templates' own names, read at startup rather than written into
     * the preference XML, so a template added to the app appears here without a second list to keep
     * in step. Templates the user wrote are not listed: those apply because they exist. With nothing
     * shipped to offer, the row is not shown at all.
     */
    private void configureThemeTemplatesPreference() {
        MultiSelectListPreference templates = findPreference("theme_templates_enabled");
        Context context = getContext();
        if (templates == null || context == null) return;
        List<ThemeTemplate> builtIns = ThemeTemplates.builtInTemplates(context);
        mThemeTemplates.clear();
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        for (ThemeTemplate template : builtIns) {
            mThemeTemplates.put(template.id, template);
            entries.add(template.name);
            values.add(template.id);
        }
        templates.setVisible(!entries.isEmpty());
        if (entries.isEmpty()) return;
        templates.setEntries(entries.toArray(new CharSequence[0]));
        templates.setEntryValues(values.toArray(new CharSequence[0]));
        updateThemeTemplatesSummary(templates, templates.getValues());
        templates.setOnPreferenceChangeListener((preference, value) -> {
            Set<String> chosen = new LinkedHashSet<>();
            if (value instanceof Set) {
                for (Object id : (Set<?>) value) chosen.add(String.valueOf(id));
            }
            Set<String> before = templates.getValues();
            updateThemeTemplatesSummary(templates, chosen);
            offerThemeTemplateSetup(newlyEnabledWithSetup(before, chosen), 0);
            return true;
        });
    }

    /** The tools just switched on that still need a line in the user's own shell setup. */
    @NonNull
    private List<ThemeTemplate> newlyEnabledWithSetup(@Nullable Set<String> before,
                                                      @NonNull Set<String> after) {
        List<ThemeTemplate> needing = new ArrayList<>();
        for (Map.Entry<String, ThemeTemplate> entry : mThemeTemplates.entrySet()) {
            if (!after.contains(entry.getKey())) continue;
            if (before != null && before.contains(entry.getKey())) continue;
            if (entry.getValue().hasSetupHook()) needing.add(entry.getValue());
        }
        return needing;
    }

    /**
     * Hand over the one command the app will not run for the user.
     *
     * <p>Tools like these are switched on by a line in a shell startup file, and the app does not
     * edit those behind someone's back. So the command is offered to copy and the user runs it
     * themselves. Several at once are offered one after another rather than all at the same time.
     *
     * <p>The chain stops when the screen goes: the dismiss that runs while the activity is being
     * torn down for a rotation must not open the next dialog against a window that is on its way
     * out. The tools already switched on keep working; the command is also in the docs.
     */
    private void offerThemeTemplateSetup(@NonNull List<ThemeTemplate> pending, int index) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !isAdded()
            || getView() == null || index >= pending.size()) return;
        ThemeTemplate template = pending.get(index);
        mThemeTemplateSetupDialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(template.name)
            .setMessage(R.string.settings_theme_templates_setup_message)
            .setNegativeButton(R.string.settings_theme_templates_setup_dismiss, null)
            .setPositiveButton(R.string.settings_theme_templates_setup_copy,
                (dialog, which) -> copyThemeTemplateSetupCommand(template))
            .setOnDismissListener(dialog -> {
                if (mThemeTemplateSetupDialog != dialog) return;
                mThemeTemplateSetupDialog = null;
                offerThemeTemplateSetup(pending, index + 1);
            })
            .show();
    }

    @Override
    public void onDestroyView() {
        AlertDialog dialog = mThemeTemplateSetupDialog;
        mThemeTemplateSetupDialog = null;
        if (dialog != null) dialog.dismiss();
        super.onDestroyView();
    }

    private void copyThemeTemplateSetupCommand(@NonNull ThemeTemplate template) {
        Context context = getContext();
        if (context == null) return;
        ClipboardManager clipboard =
            (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        // The command names files inside the template's unpacked directory; put them there now,
        // so they exist by the time the user has pasted it.
        ThemeTemplates.unpackAsync(template);
        clipboard.setPrimaryClip(ClipData.newPlainText(template.name, template.setupCommand()));
        // Android 13 and up shows its own confirmation for anything copied; a second one would be
        // the app talking over the system.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            AppNotice.show(context, R.string.settings_theme_templates_setup_copied, false);
    }

    /** What is on, in the templates' own words, or the invitation when nothing is. */
    private void updateThemeTemplatesSummary(@NonNull MultiSelectListPreference templates,
                                             @Nullable Set<String> enabled) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, ThemeTemplate> entry : mThemeTemplates.entrySet()) {
            if (enabled != null && enabled.contains(entry.getKey())) names.add(entry.getValue().name);
        }
        if (names.isEmpty()) {
            templates.setSummary(R.string.settings_theme_templates_summary_none);
            return;
        }
        if (names.size() == 1) {
            templates.setSummary(names.get(0));
            return;
        }
        if (names.size() > 3) {
            String first = getString(R.string.settings_theme_templates_summary_separator,
                names.get(0), names.get(1));
            templates.setSummary(getResources().getQuantityString(
                R.plurals.settings_theme_templates_summary_more, names.size() - 2, first,
                names.size() - 2));
            return;
        }
        String head = names.get(0);
        for (int index = 1; index < names.size() - 1; index++)
            head = getString(R.string.settings_theme_templates_summary_separator, head, names.get(index));
        templates.setSummary(getString(R.string.settings_theme_templates_summary_last, head,
            names.get(names.size() - 1)));
    }
}
