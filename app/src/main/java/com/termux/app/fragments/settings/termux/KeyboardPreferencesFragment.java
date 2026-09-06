package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;

import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.settings.TermuxPropertiesFile;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardExtraKeys;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme;
import com.termux.app.terminal.inappkeyboard.TapCorrectionController;
import com.termux.app.terminal.inappkeyboard.TapModelStore;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboardLayoutLoader;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import juloo.keyboard2.KeyboardData;

/** Standalone settings page for the built-in terminal keyboard. */
@Keep
public class KeyboardPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_FONT = "in_app_keyboard_font";
    private static final String KEY_EXTRA_KEYS = "in_app_keyboard_extra_keys";
    private static final String KEY_CREDITS_GITHUB = "keyboard_credits_github";
    private static final String KEY_CREDITS_PLAY = "keyboard_credits_play";
    private static final String KEY_DOCS_LAYOUTS = "keyboard_docs_layouts";
    private static final String KEY_DOCS_KEYS = "keyboard_docs_keys";
    private static final String KEY_TAP_CORRECTION_RESET = "in_app_keyboard_tap_correction_reset";

    private static final String UPSTREAM_GITHUB_URL =
        "https://github.com/Julow/Unexpected-Keyboard";
    private static final String UPSTREAM_PLAY_URL =
        "https://play.google.com/store/apps/details?id=juloo.keyboard2";
    private static final String DOCS_LAYOUTS_URL =
        "https://github.com/Julow/Unexpected-Keyboard/blob/master/doc/Custom-layouts.md";
    private static final String DOCS_KEYS_URL =
        "https://github.com/Julow/Unexpected-Keyboard/blob/master/doc/Possible-key-values.md";

    private static final String FONT_DIR_NAME = "inapp-keyboard";
    private static final String FONT_FILE_NAME = "label-font.ttf";

    private ActivityResultLauncher<String[]> mFontPickerLauncher;

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
        KeyboardPreferencesDataStore store = KeyboardPreferencesDataStore.getInstance(context);
        store.forgetTermuxProperties();
        preferenceManager.setPreferenceDataStore(store);
        setPreferencesFromResource(R.xml.termux_keyboard_preferences, rootKey);
        refreshThemeEntries();

        Preference customizeSurface = findPreference("customize_keyboard_surface");
        if (customizeSurface != null) customizeSurface.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, TermuxActivity.class);
            intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR, true);
            intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR_SECTION, "keyboard");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        });

        ListPreference inputMethodPreference = findPreference("keyboard_input_method");
        if (inputMethodPreference != null) {
            updateBuiltInKeyboardRows("built_in".equals(inputMethodPreference.getValue()));
            inputMethodPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                updateBuiltInKeyboardRows("built_in".equals(newValue));
                return true;
            });
        }

        Preference fontPreference = findPreference(KEY_FONT);
        if (fontPreference != null) {
            updateFontPreferenceSummary(fontPreference);
            fontPreference.setOnPreferenceClickListener(preference -> {
                onFontPreferenceClicked();
                return true;
            });
        }

        MultiSelectListPreference extraKeysPreference = findPreference(KEY_EXTRA_KEYS);
        if (extraKeysPreference != null)
            populateExtraKeysEntries(extraKeysPreference);

        bindLinkPreference(KEY_DOCS_LAYOUTS, DOCS_LAYOUTS_URL);
        bindLinkPreference(KEY_DOCS_KEYS, DOCS_KEYS_URL);
        bindLinkPreference(KEY_CREDITS_GITHUB, UPSTREAM_GITHUB_URL);
        bindLinkPreference(KEY_CREDITS_PLAY, UPSTREAM_PLAY_URL);
        Preference tapReset = findPreference(KEY_TAP_CORRECTION_RESET);
        if (tapReset != null) {
            refreshTapCorrectionSummary(tapReset);
            tapReset.setOnPreferenceClickListener(preference -> {
                TapModelStore.delete(TapCorrectionController.modelFile(context));
                refreshTapCorrectionSummary(preference);
                // The running keyboard holds its own copy; it re-reads the file on the way back.
                TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
                return true;
            });
        }

        Preference customLayout = findPreference("in_app_keyboard_custom_layout");
        if (customLayout != null) customLayout.setOnPreferenceClickListener(preference -> {
            validateCustomLayout();
            return true;
        });

        SettingsLayoutUtils.applyScreenLayout(this);
    }

    private void refreshTapCorrectionSummary(Preference preference) {
        Context context = getContext();
        if (context == null) return;
        int taps = Math.round(
            TapModelStore.load(TapCorrectionController.modelFile(context)).totalTaps());
        if (taps <= 0)
            preference.setSummary(R.string.settings_keyboard_tap_correction_reset_summary_none);
        else
            preference.setSummary(getResources().getQuantityString(
                R.plurals.settings_keyboard_tap_correction_reset_summary, taps, taps));
    }

    private void validateCustomLayout() {
        Context context = getContext();
        if (context == null) return;
        File file = new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH,
            "keyboard/layout.xml");
        if (!file.isFile()) {
            AppNotice.show(context, R.string.settings_custom_layout_missing, true);
            return;
        }
        new Thread(() -> {
            boolean valid = false;
            try (FileInputStream input = new FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0, read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES)
                        throw new IOException("layout too large");
                    output.write(buffer, 0, read);
                }
                KeyboardData.load_string_exn(
                    new String(output.toByteArray(), StandardCharsets.UTF_8));
                valid = true;
            } catch (Exception ignored) {}
            final int message = valid ? R.string.settings_custom_layout_valid
                : R.string.settings_custom_layout_invalid;
            if (isAdded() && getActivity() != null) getActivity().runOnUiThread(() ->
                AppNotice.show(context, message, true));
        }, "keyboard-layout-validation").start();
    }

    private void updateBuiltInKeyboardRows(boolean enabled) {
        Preference appearance = findPreference("keyboard_appearance");
        Preference layout = findPreference("keyboard_layout");
        Preference feedback = findPreference("keyboard_feedback");
        if (appearance != null) appearance.setEnabled(enabled);
        if (layout != null) layout.setEnabled(enabled);
        if (feedback != null) feedback.setEnabled(enabled);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.settings_destination_keyboard_input);
        }
        // The file may have been hand-edited while the screen was away.
        if (getContext() != null)
            KeyboardPreferencesDataStore.getInstance(getContext()).forgetTermuxProperties();
        refreshThemeEntries();
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

    private void bindLinkPreference(@NonNull String key, @NonNull String url) {
        Preference preference = findPreference(key);
        if (preference == null)
            return;
        preference.setOnPreferenceClickListener(unused -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                // No browser available; nothing sensible to do.
            }
            return true;
        });
    }

    private void populateExtraKeysEntries(@NonNull MultiSelectListPreference preference) {
        String[] names = InAppKeyboardExtraKeys.catalog();
        CharSequence[] entries = new CharSequence[names.length];
        for (int i = 0; i < names.length; i++)
            entries[i] = extraKeyLabel(names[i]);
        preference.setEntries(entries);
        preference.setEntryValues(names);
    }

    /** Human-readable labels for the extra-keys picker; falls back to the key's own glyph. */
    private static String extraKeyLabel(@NonNull String name) {
        switch (name) {
            case "tab": return "Tab";
            case "esc": return "Esc";
            case "capslock": return "Caps Lock";
            case "compose": return "Compose";
            case "home": return "Home";
            case "end": return "End";
            case "page_up": return "Page Up";
            case "page_down": return "Page Down";
            case "copy": return "Copy";
            case "paste": return "Paste";
            case "cut": return "Cut";
            case "selectAll": return "Select all terminal text";
            case "undo": return "Undo (Ctrl+_)";
            case "redo": return "Redo";
            case "delete_word": return "Delete word";
            case "forward_delete_word": return "Delete word forward";
            case "shareText": return "Share text";
            case "pasteAsPlainText": return "Paste as plain text";
            case "switch_greekmath": return "Greek & math symbols";
            case "meta": return "Meta";
            case "alt": return "Alt";
            case "superscript": return "Superscript";
            case "subscript": return "Subscript";
            case "f11_placeholder": return "F11";
            case "f12_placeholder": return "F12";
            case "menu": return "Menu";
            case "scroll_lock": return "Scroll Lock";
            default:
                if (name.startsWith("accent_"))
                    return "Dead key: " + name.substring("accent_".length()).replace('_', ' ');
                return InAppKeyboardExtraKeys.displayName(name);
        }
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
}

class KeyboardPreferencesDataStore extends PreferenceDataStore {

    private final TermuxAppSharedPreferences mPreferences;
    private final Context mContext;
    /**
     * termux.properties as last read. The preference framework asks for every bound key on each
     * bind, so this is read once per screen and dropped by {@link #forgetTermuxProperties} when the
     * fragment (re)appears, and after this store's own write.
     */
    @Nullable private Properties mTermuxProperties;

    private static KeyboardPreferencesDataStore mInstance;

    private KeyboardPreferencesDataStore(Context context) {
        mContext = context.getApplicationContext();
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized KeyboardPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new KeyboardPreferencesDataStore(context);
        }
        return mInstance;
    }

    @NonNull
    private Properties termuxProperties() {
        if (mTermuxProperties == null)
            mTermuxProperties = TermuxPropertiesFile.load(mContext);
        return mTermuxProperties;
    }

    void forgetTermuxProperties() {
        mTermuxProperties = null;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null || key == null)
            return;
        switch (key) {
            case "in_app_keyboard_hide_on_hardware":
                mPreferences.setSoftKeyboardEnabledOnlyIfNoHardware(value);
                break;
            case "in_app_keyboard_enabled":
                mPreferences.setInAppKeyboardEnabled(value);
                break;
            case "in_app_keyboard_haptics_enabled":
                mPreferences.setInAppKeyboardHapticsEnabled(value);
                break;
            case "in_app_keyboard_key_sound_enabled":
                mPreferences.setInAppKeyboardKeySoundEnabled(value);
                break;
            case "in_app_keyboard_tap_correction":
                mPreferences.setInAppKeyboardTapCorrectionEnabled(value);
                TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, false);
                break;
            case "extra_keys_text_all_caps":
                // A property, not a preference: the row reads it from termux.properties, so this
                // writes there and the styling reload picks it up like any hand edit would.
                TermuxPropertiesFile.write(TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS,
                    Boolean.toString(value));
                mTermuxProperties = null;
                TermuxActivity.requestTermuxActivityStylingOnNextResume(
                    mContext, false);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null || key == null)
            return defValue;
        switch (key) {
            case "in_app_keyboard_hide_on_hardware":
                return mPreferences.isSoftKeyboardEnabledOnlyIfNoHardware();
            case "in_app_keyboard_enabled":
                return mPreferences.isInAppKeyboardEnabled();
            case "in_app_keyboard_haptics_enabled":
                return mPreferences.isInAppKeyboardHapticsEnabled();
            case "in_app_keyboard_key_sound_enabled":
                return mPreferences.isInAppKeyboardKeySoundEnabled();
            case "in_app_keyboard_tap_correction":
                return mPreferences.isInAppKeyboardTapCorrectionEnabled();
            case "extra_keys_text_all_caps": {
                String stored = termuxProperties()
                    .getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS);
                // Absent means this property's documented default-true behaviour.
                return stored == null
                    || !"false".equals(stored.trim().toLowerCase(Locale.ROOT));
            }
            default:
                return defValue;
        }
    }

    @Override
    public void putInt(String key, int value) {
        if (mPreferences == null || !"in_app_keyboard_bottom_padding".equals(key))
            return;
        mPreferences.setInAppKeyboardBottomPadding(value);
        // The keyboard is laid out by the activity, not by this screen, so the change lands when
        // the user goes back to it — the same route the extra-keys row toggle takes.
        TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, false);
    }

    @Override
    public int getInt(String key, int defValue) {
        if (mPreferences == null || !"in_app_keyboard_bottom_padding".equals(key))
            return defValue;
        return mPreferences.getInAppKeyboardBottomPadding();
    }

    @Override
    public void putString(String key, @Nullable String value) {
        if (mPreferences == null || key == null)
            return;
        switch (key) {
            case "keyboard_input_method":
                String inputMethod = value == null ? "built_in" : value;
                mPreferences.setInAppKeyboardEnabled("built_in".equals(inputMethod));
                mPreferences.setSoftKeyboardEnabled("android".equals(inputMethod));
                break;
            case "in_app_keyboard_theme":
                mPreferences.setInAppKeyboardTheme(value);
                break;
            default:
                break;
        }
    }

    @Override
    @Nullable
    public String getString(String key, @Nullable String defValue) {
        if (mPreferences == null || key == null)
            return defValue;
        switch (key) {
            case "keyboard_input_method":
                if (mPreferences.isInAppKeyboardEnabled()) return "built_in";
                return mPreferences.isSoftKeyboardEnabled() ? "android" : "none";
            case "in_app_keyboard_theme":
                return mPreferences.getInAppKeyboardTheme();
            default:
                return defValue;
        }
    }

    @Override
    public void putStringSet(String key, @Nullable Set<String> values) {
        if (mPreferences == null || !"in_app_keyboard_extra_keys".equals(key))
            return;
        mPreferences.setInAppKeyboardExtraKeys(
            values == null ? "" : String.join(",", values));
    }

    @Override
    @Nullable
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        if (mPreferences == null || !"in_app_keyboard_extra_keys".equals(key))
            return defValues;
        String stored = InAppKeyboardExtraKeys.effectiveStoredValue(
            mPreferences.getInAppKeyboardExtraKeys());
        if (stored.isEmpty())
            return new HashSet<>();
        return new HashSet<>(Arrays.asList(stored.split(",")));
    }
}
