package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.chrome.WallpaperBackdropPolicy;
import com.termux.app.chrome.WallpaperPictureReader;
import com.termux.app.notice.AppNotice;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme;
import com.termux.launcherctl.LauncherCtlNotificationStore;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.theme.LauncherSchemeTheme;
import com.termux.app.theme.templates.ThemeTemplate;
import com.termux.app.theme.templates.ThemeTemplates;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Keep
public class TermuxStylePreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_FONT = "in_app_keyboard_font";
    private static final String FONT_DIR_NAME = "inapp-keyboard";
    private static final String FONT_FILE_NAME = "label-font.ttf";

    private ActivityResultLauncher<String[]> mFontPickerLauncher;

    /** The shipped templates by id, for the summary line and the setup a tool still needs. */
    private final Map<String, ThemeTemplate> mThemeTemplates = new LinkedHashMap<>();

    /** The setup dialog on screen, so the view going away takes it along instead of leaking it. */
    @Nullable
    private AlertDialog mThemeTemplateSetupDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFontPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFontPicked);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_style_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        LauncherIconPackPreferenceController.configure(this, context);
        Preference surfaceEditor = findPreference("live_surface_editor");
        if (surfaceEditor != null) {
            surfaceEditor.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(context, TermuxActivity.class);
                intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return true;
            });
        }
        Preference customizeKeyboardSurface = findPreference("customize_keyboard_surface");
        if (customizeKeyboardSurface != null) customizeKeyboardSurface.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, TermuxActivity.class);
            intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR, true);
            intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR_SECTION, "keyboard");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        });
        Preference fontPreference = findPreference(KEY_FONT);
        if (fontPreference != null) {
            updateFontPreferenceSummary(fontPreference);
            fontPreference.setOnPreferenceClickListener(preference -> {
                onFontPreferenceClicked();
                return true;
            });
        }
        configureTerminalContrastPreference();
        configureThemeTemplatesPreference();
        configureDynamicColorsHint();
        refreshThemeEntries();
        updateKeyboardLookEnabled(context);
        configureWallpaperAlignment(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_appearance);
        }
        Context context = getContext();
        if (context != null) {
            LauncherIconPackPreferenceController.configure(this, context);
        }
        configureTerminalContrastPreference();
        configureThemeTemplatesPreference();
        configureDynamicColorsHint();
        refreshThemeEntries();
        if (context != null) updateKeyboardLookEnabled(context);
        if (context != null) updateWallpaperAlignmentVisibility(context);
    }

    /**
     * The keyboard-look rows only matter for the built-in keyboard, so they grey out together
     * when the Keyboard page's input-method choice is something else. That choice lives on a
     * different page, so this is re-read on every resume rather than pushed as a live event.
     */
    private void updateKeyboardLookEnabled(@NonNull Context context) {
        Preference category = findPreference("keyboard_appearance");
        if (category == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        category.setEnabled(preferences != null && preferences.isInAppKeyboardEnabled());
    }

    /**
     * Wallpaper alignment only has something to correct while the system is the one drawing the
     * wallpaper. With a still wallpaper the launcher draws it itself from the frame its own
     * surfaces are cut from, so the row is put away rather than left as a knob that does nothing;
     * a picture the launcher cannot be sure of (or the switch being off) brings it back. The switch
     * above it is on the same page, so it re-checks on the spot as well as on every resume.
     */
    private void configureWallpaperAlignment(@NonNull Context context) {
        updateWallpaperAlignmentVisibility(context);
        SwitchPreferenceCompat wallpaperSwitch = findPreference("use_system_wallpaper");
        if (wallpaperSwitch == null) return;
        wallpaperSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            Preference alignment = findPreference("wallpaper_render_zoom");
            if (alignment != null) {
                alignment.setVisible(WallpaperBackdropPolicy.alignmentSliderApplies(
                    WallpaperBackdropPolicy.mode(Boolean.TRUE.equals(newValue),
                        WallpaperPictureReader.read(context,
                            TermuxAppSharedPreferences.build(context, true)), true)));
            }
            return true;
        });
    }

    private void updateWallpaperAlignmentVisibility(@NonNull Context context) {
        Preference alignment = findPreference("wallpaper_render_zoom");
        if (alignment == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        boolean wallpaperMode = preferences != null && preferences.isUseSystemWallpaperEnabled();
        alignment.setVisible(WallpaperBackdropPolicy.alignmentSliderApplies(
            WallpaperBackdropPolicy.mode(wallpaperMode,
                WallpaperPictureReader.read(context, preferences), true)));
    }

    private void refreshThemeEntries() {
        Context context = getContext();
        ListPreference preference = findPreference("in_app_keyboard_theme");
        if (context == null || preference == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences == null) return;
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            preferences.getInAppKeyboardColorScheme());
        String importedId = scheme.getImportedThemeId();
        if (importedId.isEmpty()) {
            preference.setEntries(R.array.termux_in_app_keyboard_theme_entries);
            preference.setEntryValues(R.array.termux_in_app_keyboard_theme_values);
            if ("custom".equals(preferences.getInAppKeyboardTheme()))
                preferences.setInAppKeyboardTheme("system");
        } else {
            preference.setEntries(new CharSequence[] {
                getString(R.string.termux_in_app_keyboard_theme_system),
                getString(R.string.termux_in_app_keyboard_theme_light),
                getString(R.string.termux_in_app_keyboard_theme_dark),
                getString(R.string.termux_in_app_keyboard_theme_imported, importedId)
            });
            preference.setEntryValues(new CharSequence[] {"system", "light", "dark", "custom"});
        }
        preference.setValue(preferences.getInAppKeyboardTheme());
    }

    private void onFontPreferenceClicked() {
        Context context = getContext();
        if (context == null)
            return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences == null)
            return;
        if (preferences.getInAppKeyboardFontPath().isEmpty()) {
            launchFontPicker();
            return;
        }
        new MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.termux_in_app_keyboard_font_title)
            .setItems(new CharSequence[]{
                getString(R.string.termux_in_app_keyboard_font_pick),
                getString(R.string.termux_in_app_keyboard_font_reset)
            }, (dialog, which) -> {
                if (which == 0) {
                    launchFontPicker();
                } else {
                    clearCustomFont();
                }
            })
            .show();
    }

    private void launchFontPicker() {
        // SAF mime coverage for ttf/otf across providers; octet-stream catches
        // file managers that don't map font extensions.
        mFontPickerLauncher.launch(new String[]{
            "font/ttf", "font/otf", "font/*",
            "application/x-font-ttf", "application/x-font-otf",
            "application/octet-stream"
        });
    }

    private void onFontPicked(@Nullable Uri uri) {
        Context context = getContext();
        if (uri == null || context == null)
            return;
        File fontDir = new File(context.getFilesDir(), FONT_DIR_NAME);
        File fontFile = new File(fontDir, FONT_FILE_NAME);
        File stagedFile = new File(fontDir, FONT_FILE_NAME + ".tmp");
        try {
            if (!fontDir.isDirectory() && !fontDir.mkdirs())
                throw new java.io.IOException("Cannot create " + fontDir);
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(stagedFile)) {
                if (in == null)
                    throw new java.io.IOException("Cannot open " + uri);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1)
                    out.write(buffer, 0, read);
            }
            // createFromFile returns DEFAULT (or throws) when the bytes are not a usable font.
            Typeface typeface = Typeface.createFromFile(stagedFile);
            if (typeface == null || Typeface.DEFAULT.equals(typeface))
                throw new java.io.IOException("Unreadable font " + uri);
            if (!stagedFile.renameTo(fontFile))
                throw new java.io.IOException("Cannot replace " + fontFile);
            TermuxAppSharedPreferences preferences =
                TermuxAppSharedPreferences.build(context, true);
            if (preferences != null)
                preferences.setInAppKeyboardFontPath(fontFile.getAbsolutePath());
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            stagedFile.delete();
            AppNotice.show(context, R.string.termux_in_app_keyboard_font_error, false);
        }
        Preference fontPreference = findPreference(KEY_FONT);
        if (fontPreference != null)
            updateFontPreferenceSummary(fontPreference);
    }

    private void clearCustomFont() {
        Context context = getContext();
        if (context == null)
            return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences != null) {
            String path = preferences.getInAppKeyboardFontPath();
            preferences.setInAppKeyboardFontPath("");
            if (!path.isEmpty()) {
                //noinspection ResultOfMethodCallIgnored
                new File(path).delete();
            }
        }
        Preference fontPreference = findPreference(KEY_FONT);
        if (fontPreference != null)
            updateFontPreferenceSummary(fontPreference);
    }

    private void updateFontPreferenceSummary(@NonNull Preference fontPreference) {
        Context context = getContext();
        if (context == null)
            return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        String path = preferences == null ? "" : preferences.getInAppKeyboardFontPath();
        if (path.isEmpty() || !new File(path).isFile()) {
            fontPreference.setSummary(R.string.termux_in_app_keyboard_font_summary_default);
        } else {
            fontPreference.setSummary(getString(
                R.string.termux_in_app_keyboard_font_summary_custom, new File(path).getName()));
        }
    }

    /**
     * What the wallpaper-colours switch is really choosing, said under it.
     *
     * <p>It picks the palette for the terminal and for the launcher chrome together, so the note
     * names both — and the one case where the chrome cannot follow, the palette being loaded into
     * the activity's resources on API 30 and below, is stated rather than hidden.
     */
    private void configureDynamicColorsHint() {
        Preference hint = findPreference("terminal_dynamic_colors_hint");
        if (hint == null) return;
        hint.setSummary(LauncherSchemeTheme.isSupported()
            ? R.string.settings_wallpaper_colors_hint
            : R.string.settings_wallpaper_colors_hint_chrome_unsupported);
    }

    private void configureTerminalContrastPreference() {
        androidx.preference.ListPreference contrast = findPreference("terminal_contrast_level");
        androidx.preference.SwitchPreferenceCompat dynamic =
            findPreference("terminal_dynamic_colors_enabled");
        if (contrast == null || dynamic == null) return;
        boolean enabled = dynamic.isChecked();
        contrast.setEnabled(enabled);
        updateTerminalContrastSummary(contrast, enabled);
        contrast.setOnPreferenceChangeListener((preference, value) -> {
            contrast.setValue(String.valueOf(value));
            updateTerminalContrastSummary(contrast, true);
            return true;
        });
        dynamic.setOnPreferenceChangeListener((preference, value) -> {
            boolean on = Boolean.TRUE.equals(value);
            contrast.setEnabled(on);
            updateTerminalContrastSummary(contrast, on);
            return true;
        });
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

    private void updateTerminalContrastSummary(@NonNull androidx.preference.ListPreference contrast,
                                               boolean enabled) {
        if (!enabled) {
            contrast.setSummary(R.string.settings_terminal_contrast_disabled);
            return;
        }
        String label = contrast.getEntry() == null ? "Default" : contrast.getEntry().toString();
        contrast.setSummary(getString(R.string.settings_terminal_contrast_summary, label));
    }

}

class TermuxStylePreferencesDataStore extends PreferenceDataStore {

    private static final long STYLE_SYNC_DEBOUNCE_MS = 140L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;
    private boolean mPendingRecreateActivity;
    private final Runnable mStyleSyncRunnable;
    private final Runnable mDrawerSyncRunnable;

    private static TermuxStylePreferencesDataStore mInstance;

    private final KeyboardPreferencesDataStore mKeyboardLook;

    private TermuxStylePreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
        mKeyboardLook = KeyboardPreferencesDataStore.getInstance(context);
        mStyleSyncRunnable = () -> {
            boolean recreateActivity = mPendingRecreateActivity;
            mPendingRecreateActivity = false;
            TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, recreateActivity);
        };
        mDrawerSyncRunnable = () -> TermuxActivity.requestAppDrawerReloadOnNextResume(mContext);
    }

    public static synchronized TermuxStylePreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxStylePreferencesDataStore(context);
        }
        return mInstance;
    }

    private void scheduleTermuxActivityStylingSync(boolean recreateActivity) {
        mPendingRecreateActivity = mPendingRecreateActivity || recreateActivity;
        MAIN_HANDLER.removeCallbacks(mStyleSyncRunnable);
        MAIN_HANDLER.postDelayed(mStyleSyncRunnable, STYLE_SYNC_DEBOUNCE_MS);
    }

    private void scheduleAppDrawerSync() {
        MAIN_HANDLER.removeCallbacks(mDrawerSyncRunnable);
        MAIN_HANDLER.postDelayed(mDrawerSyncRunnable, STYLE_SYNC_DEBOUNCE_MS);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch(key) {
            case "use_system_wallpaper":
                TermuxActivity.setWallpaperModeEnabled(mContext, value);
                break;
            case "terminal_dynamic_colors_enabled":
                // This switch is the whole palette decision: on, the terminal and the chrome both
                // take the wallpaper; off, both take the scheme. Either way the chrome palette
                // changes, and it is loaded into the activity's Resources at theme time, so
                // nothing short of a recreate can swap it.
                mPreferences.setTerminalDynamicColorsEnabled(value);
                LauncherSchemeTheme.invalidate();
                scheduleTermuxActivityStylingSync(true);
                break;
            case "app_launcher_bw_icons":
                mPreferences.setAppLauncherBwIconsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "show_in_recents_when_not_default":
                mPreferences.setShowInRecentsWhenNotDefaultEnabled(value);
                break;
            case "app_launcher_display_app_names":
                mPreferences.setAppLauncherDisplayAppNamesEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_notification_dots":
                mPreferences.setAppLauncherNotificationDotsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_notification_history":
                mPreferences.setAppLauncherNotificationHistoryEnabled(value);
                // Turning it off means the captured message bodies go too, not just future ones.
                if (!value) LauncherCtlNotificationStore.getInstance().clearAll();
                break;
            case "app_launcher_most_used_page":
                mPreferences.setAppLauncherMostUsedPageEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_drawer_enabled":
                mPreferences.setAppLauncherDrawerEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_drawer_search_on_open":
                // Read fresh every time the drawer commits open, so nothing has to be re-styled.
                mPreferences.setAppLauncherDrawerSearchOnOpenEnabled(value);
                break;
            case "app_launcher_drawer_search_android_keyboard":
                // Same: decided per open, never mid-search.
                mPreferences.setAppLauncherDrawerSearchAndroidKeyboardEnabled(value);
                break;
            case "app_launcher_widget_pane_enabled":
                mPreferences.setAppLauncherWidgetPaneEnabled(value);
                // The pane is built once per activity, so it has to come back on a recreate.
                scheduleTermuxActivityStylingSync(true);
                break;
            case "x11_display_enabled":
                mPreferences.setX11DisplayEnabled(value);
                // The commands go into the prefix with the feature and come back out with it;
                // a running server is left alone either way.
                if (!value) com.termux.app.x11.X11CliInstaller.uninstallAsync(mContext);
                if (value) com.termux.app.x11.X11Defaults.applyOnce(mContext);
                // The drawer lists Linux apps only while the display is on.
                com.termux.app.launcher.data.LauncherAppDataProvider.getInstance(mContext).refreshAsync(null, null);
                // The page and the prefix commands are set up once per activity, so turning the
                // display on or off has to come back through a recreate.
                scheduleTermuxActivityStylingSync(true);
                break;
            case "app_launcher_row_haptics":
                mPreferences.setAppLauncherRowHapticsEnabled(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_az_double_tap_lock":
                mPreferences.setAppLauncherAzDoubleTapLockEnabled(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null)
            return defValue;
        switch(key) {
            case "use_system_wallpaper":
                return mPreferences.isUseSystemWallpaperEnabled();
            case "terminal_dynamic_colors_enabled":
                return mPreferences.isTerminalDynamicColorsEnabled();
            case "app_launcher_bw_icons":
                return mPreferences.isAppLauncherBwIconsEnabled();
            case "show_in_recents_when_not_default":
                return mPreferences.isShowInRecentsWhenNotDefaultEnabled();
            case "app_launcher_display_app_names":
                return mPreferences.isAppLauncherDisplayAppNamesEnabled();
            case "app_launcher_notification_dots":
                return mPreferences.isAppLauncherNotificationDotsEnabled();
            case "app_launcher_notification_history":
                return mPreferences.isAppLauncherNotificationHistoryEnabled();
            case "app_launcher_most_used_page":
                return mPreferences.isAppLauncherMostUsedPageEnabled();
            case "app_launcher_drawer_enabled":
                return mPreferences.isAppLauncherDrawerEnabled();
            case "app_launcher_drawer_search_on_open":
                return mPreferences.isAppLauncherDrawerSearchOnOpenEnabled();
            case "app_launcher_drawer_search_android_keyboard":
                return mPreferences.isAppLauncherDrawerSearchAndroidKeyboardEnabled();
            case "app_launcher_widget_pane_enabled":
                return mPreferences.isAppLauncherWidgetPaneEnabled();
            case "x11_display_enabled":
                return mPreferences.isX11DisplayEnabled();
            case "app_launcher_row_haptics":
                return mPreferences.isAppLauncherRowHapticsEnabled();
            case "app_launcher_az_double_tap_lock":
                return mPreferences.isAppLauncherAzDoubleTapLockEnabled();
            default:
                return defValue;
        }
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (mPreferences == null || key == null) return defValues;
        if ("theme_templates_enabled".equals(key)) return mPreferences.getThemeTemplatesEnabled();
        return defValues;
    }

    @Override
    public void putStringSet(String key, Set<String> values) {
        if (mPreferences == null || key == null) return;
        if ("theme_templates_enabled".equals(key)) {
            mPreferences.setThemeTemplatesEnabled(values);
            // A tool the user just switched on should be wearing the colours by the time they leave
            // this screen, so the pass runs now rather than at the next palette refresh.
            ThemeTemplates.applyFromDiskAsync(mContext);
        }
    }

    @Override
    public void putInt(String key, int value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
            case "in_app_keyboard_bottom_padding":
                mKeyboardLook.putInt(key, value);
                break;
            case "wallpaper_render_zoom":
                mPreferences.setWallpaperRenderZoom(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            default:
                break;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch (key) {
            case "in_app_keyboard_bottom_padding":
                return mKeyboardLook.getInt(key, defValue);
            case "wallpaper_render_zoom":
                return mPreferences.getWallpaperRenderZoom();
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, String value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
            case "terminal_contrast_level":
                mPreferences.setTerminalContrastLevel(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "theme_mode":
                writeTermuxPropertyToProperties(TermuxPropertyConstants.KEY_NIGHT_MODE, value);
                TermuxThemeUtils.setAppNightMode(value);
                scheduleTermuxActivityStylingSync(true);
                break;
            case "app_launcher_input_char":
                mPreferences.setAppLauncherInputChar(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_az_lock_method":
                mPreferences.setAppLauncherAzLockMethod(value);
                break;
            case "app_launcher_use_case_mode":
                com.termux.app.launcher.LauncherUseCaseMode.applyMode(mPreferences, value);
                // Flips the drawer, both dock rows and the widget pane at once: recreate so every
                // surface is rebuilt against the new state instead of restyled in place.
                scheduleTermuxActivityStylingSync(true);
                break;
            case "app_launcher_drawer_view_type":
                mPreferences.setAppLauncherDrawerViewType(value);
                scheduleAppDrawerSync();
                break;
            case "app_launcher_default_buttons":
                mPreferences.setAppLauncherDefaultButtons(value);
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_icon_pack_package":
                mPreferences.setAppLauncherIconPackPackage(value);
                // Not invalidate(): that resets catalogue state only, and leaves every layer of
                // held artwork on the previous pack.
                com.termux.app.launcher.data.LauncherAppDataProvider.getInstance(mContext)
                    .invalidateIconArtwork();
                scheduleTermuxActivityStylingSync(false);
                break;
            case "app_launcher_pinned_icon_pack_package":
                mPreferences.setAppLauncherPinnedIconPackPackage(value);
                com.termux.app.launcher.data.LauncherAppDataProvider.getInstance(mContext)
                    .invalidateIconArtwork();
                scheduleTermuxActivityStylingSync(false);
                break;
            case "in_app_keyboard_theme":
                mKeyboardLook.putString(key, value);
                break;
            default:
                break;
        }
    }

    @Override
    public String getString(String key, String defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch (key) {
            case "terminal_contrast_level":
                return mPreferences.getTerminalContrastLevel().value;
            case "theme_mode":
                return TermuxSharedProperties.getNightMode(mContext);
            case "app_launcher_input_char":
                return mPreferences.getAppLauncherInputChar();
            case "app_launcher_az_lock_method":
                return mPreferences.getAppLauncherAzLockMethod();
            case "in_app_keyboard_theme":
                return mKeyboardLook.getString(key, defValue);
            case "app_launcher_use_case_mode":
                return com.termux.app.launcher.LauncherUseCaseMode.currentMode(mPreferences);
            case "app_launcher_drawer_view_type":
                return mPreferences.getAppLauncherDrawerViewType();
            case "app_launcher_default_buttons":
                return mPreferences.getAppLauncherDefaultButtons();
            case "app_launcher_icon_pack_package":
                return mPreferences.getAppLauncherIconPackPackage();
            case "app_launcher_pinned_icon_pack_package":
                return mPreferences.getAppLauncherPinnedIconPackPackage();
            default:
                return defValue;
        }
    }

    private void writeTermuxPropertyToProperties(@NonNull String propertyKey, @NonNull String propertyValue) {
        com.termux.app.settings.TermuxPropertiesFile.write(propertyKey, propertyValue);
    }
}
