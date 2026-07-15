package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.termux.R;
import com.termux.app.fragments.settings.PillPreference;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxGUIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    public static final String EXTRA_INITIAL_FRAGMENT = "settings_initial_fragment";
    public static final String EXTRA_INITIAL_TITLE_RES = "settings_initial_title_res";
    public static final String EXTRA_OPEN_TAI_SETTINGS = "open_tai_settings";

    public static Intent createFragmentIntent(@NonNull Context context, @NonNull Class<? extends Fragment> fragmentClass, int titleResId) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(EXTRA_INITIAL_FRAGMENT, fragmentClass.getName());
        if (titleResId != 0) {
            intent.putExtra(EXTRA_INITIAL_TITLE_RES, titleResId);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TermuxThemeUtils.setAppNightMode(this);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setTheme(R.style.Theme_TermuxApp_DayNight_NoActionBar);
        TermuxThemeManager.applyThemeOverlays(this);
        super.onCreate(savedInstanceState);
        registerSettingsStyleCallbacks();
        setContentView(R.layout.activity_settings);
        applySettingsSystemBars();
        if (savedInstanceState == null) {
            // QA deep-link entry path:
            // adb shell am start -n com.termux/.app.activities.SettingsActivity --ez open_tai_settings true
            Intent intent = getIntent();
            if (intent.getBooleanExtra(EXTRA_OPEN_TAI_SETTINGS, false)) {
                intent.putExtra(EXTRA_INITIAL_FRAGMENT,
                    "com.termux.app.fragments.settings.termux.TaiPreferencesFragment");
                intent.putExtra(EXTRA_INITIAL_TITLE_RES, R.string.termux_ai_preferences_title);
            }
            Fragment initialFragment = buildInitialFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.settings, initialFragment).commit();
        }
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        int titleResId = getIntent().getIntExtra(EXTRA_INITIAL_TITLE_RES, R.string.title_activity_termux_settings);
        setTitle(titleResId);
    }

    /**
     * Applies the TL handoff styling to every settings page so individual fragments do not
     * each need to opt in:
     * <ul>
     *   <li>Row/category/card layouts (applied in onFragmentCreated, before the list adapter
     *       is built, so older sub-screens such as Debugging / Terminal IO / Terminal view
     *       pick up the redesigned rows too).</li>
     *   <li>Dividers (applied in onFragmentViewCreated, once the list exists): inset,
     *       icon-aligned dividers between root rows, and none on sub-screens where sections
     *       are separated by the category hairline instead.</li>
     * </ul>
     */
    private void registerSettingsStyleCallbacks() {
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
            new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentCreated(@NonNull androidx.fragment.app.FragmentManager fm,
                                              @NonNull Fragment fragment, Bundle savedInstanceState) {
                    if (!(fragment instanceof PreferenceFragmentCompat)) return;
                    PreferenceFragmentCompat preferenceFragment = (PreferenceFragmentCompat) fragment;
                    if (preferenceFragment.getPreferenceScreen() == null) return;
                    if (fragment instanceof RootPreferencesFragment) {
                        SettingsLayoutUtils.applyRootLayout(preferenceFragment);
                    } else {
                        SettingsLayoutUtils.applyScreenLayout(preferenceFragment);
                    }
                }

                @Override
                public void onFragmentViewCreated(@NonNull androidx.fragment.app.FragmentManager fm,
                                                  @NonNull Fragment fragment, @NonNull View view,
                                                  Bundle savedInstanceState) {
                    if (!(fragment instanceof PreferenceFragmentCompat)) return;
                    PreferenceFragmentCompat preferenceFragment = (PreferenceFragmentCompat) fragment;
                    if (fragment instanceof RootPreferencesFragment) {
                        // Root rows rely on the category header hairline; a list divider here
                        // creates the unwanted double-line seen between sections.
                        preferenceFragment.setDivider(null);
                        preferenceFragment.setDividerHeight(0);
                    } else {
                        preferenceFragment.setDivider(null);
                        preferenceFragment.setDividerHeight(0);
                    }
                }
            }, true);
    }

    private void applySettingsSystemBars() {
        Window window = getWindow();
        int surface = ThemeUtils.getSystemAttrColor(this, com.termux.shared.R.attr.termuxColorSurfaceBase, android.graphics.Color.BLACK);
        window.setStatusBarColor(surface);
        window.setNavigationBarColor(surface);
    }

    @NonNull
    private Fragment buildInitialFragment() {
        String fragmentClassName = getIntent().getStringExtra(EXTRA_INITIAL_FRAGMENT);
        if (fragmentClassName == null || fragmentClassName.isEmpty()) {
            return new RootPreferencesFragment();
        }
        return getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), fragmentClassName);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller,
                                             @NonNull Preference preference) {
        String fragmentClassName = preference.getFragment();
        if (fragmentClassName == null || fragmentClassName.isEmpty())
            return false;
        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
            .instantiate(getClassLoader(), fragmentClassName);
        fragment.setArguments(preference.getExtras());
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit();
        return true;
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null)
                return;
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            SettingsLayoutUtils.applyRootLayout(this);
            configureTermuxAPIPreference(context);
            configureTermuxGUIPreference(context);
            configureTermuxFloatPreference(context);
            configureTermuxTaskerPreference(context);
            configureTermuxWidgetPreference(context);
            configureAboutPreference(context);
            configureOpenSourceLicensesPreference(context);
            configureDonatePreference(context);
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getActivity() != null) {
                getActivity().setTitle(R.string.application_name);
            }
            updateShizukuPill();
        }

        private void updateShizukuPill() {
            Preference preference = findPreference("shizuku");
            if (!(preference instanceof PillPreference))
                return;
            PillPreference pillPreference = (PillPreference) preference;
            PrivilegedBackendManager.BackendState state = PrivilegedBackendManager.getInstance().getBackendState();
            switch (state) {
                case READY:
                    pillPreference.setPill("READY", PillPreference.Tone.POSITIVE);
                    break;
                case FALLBACK_SHELL:
                    pillPreference.setPill("SHELL", PillPreference.Tone.POSITIVE);
                    break;
                case PERMISSION_DENIED:
                    pillPreference.setPill("DENIED", PillPreference.Tone.NEGATIVE);
                    break;
                default:
                    pillPreference.setPill("OFF", PillPreference.Tone.NEUTRAL);
                    break;
            }
        }

        private void configureTermuxAPIPreference(@NonNull Context context) {
            Preference termuxAPIPreference = findPreference("termux_api");
            if (termuxAPIPreference != null) {
                TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxAPIPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxFloatPreference(@NonNull Context context) {
            Preference termuxFloatPreference = findPreference("termux_float");
            if (termuxFloatPreference != null) {
                TermuxFloatAppSharedPreferences preferences = TermuxFloatAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxFloatPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxTaskerPreference(@NonNull Context context) {
            Preference termuxTaskerPreference = findPreference("termux_tasker");
            if (termuxTaskerPreference != null) {
                TermuxTaskerAppSharedPreferences preferences = TermuxTaskerAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxTaskerPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxGUIPreference(@NonNull Context context) {
            Preference termuxGUIPreference = findPreference("termux_gui");
            if (termuxGUIPreference != null) {
                TermuxGUIAppSharedPreferences preferences = TermuxGUIAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxGUIPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxWidgetPreference(@NonNull Context context) {
            Preference termuxWidgetPreference = findPreference("termux_widget");
            if (termuxWidgetPreference != null) {
                TermuxWidgetAppSharedPreferences preferences = TermuxWidgetAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxWidgetPreference.setVisible(preferences != null);
            }
        }

        private void configureAboutPreference(@NonNull Context context) {
            Preference aboutPreference = findPreference("about");
            if (aboutPreference != null) {
                aboutPreference.setOnPreferenceClickListener(preference -> {
                    new Thread() {

                        @Override
                        public void run() {
                            String title = "About";
                            StringBuilder aboutString = new StringBuilder();
                            aboutString.append("## Termux Launcher\n\n");
                            aboutString.append("Modified Termux/Termux:Monet distribution, licensed GPLv3-only.  \n");
                            aboutString.append("[Project source](https://github.com/PickleHik3/termux-launcher) · ");
                            aboutString.append("[Releases](https://github.com/PickleHik3/termux-launcher/releases)  \n\n");
                            aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                            aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true));
                            aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context));
                            String userActionName = UserAction.ABOUT.getName();
                            ReportInfo reportInfo = new ReportInfo(userActionName, TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                            reportInfo.setReportString(aboutString.toString());
                            reportInfo.setReportSaveFileLabelAndPath(userActionName, Environment.getExternalStorageDirectory() + "/" + FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));
                            if (isAdded() && getActivity() != null) {
                                getActivity().runOnUiThread(() -> ReportActivity.startReportActivity(context, reportInfo));
                            }
                        }
                    }.start();
                    return true;
                });
            }
        }

        private void configureOpenSourceLicensesPreference(@NonNull Context context) {
            Preference licensesPreference = findPreference("open_source_licenses");
            if (licensesPreference == null) return;

            licensesPreference.setOnPreferenceClickListener(preference -> {
                new Thread(() -> {
                    String title = context.getString(R.string.open_source_licenses_preference_title);
                    String licenses = buildOpenSourceLicensesMarkdown(context);
                    ReportInfo reportInfo = new ReportInfo("OpenSourceLicenses",
                        TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                    reportInfo.setReportString(licenses);
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> ReportActivity.startReportActivity(context, reportInfo));
                    }
                }).start();
                return true;
            });
        }

        private static String buildOpenSourceLicensesMarkdown(@NonNull Context context) {
            StringBuilder licenses = new StringBuilder(readRawText(context, R.raw.third_party_notices));
            appendLicense(licenses, "GNU General Public License v3", readRawText(context, R.raw.license_gpl_3));
            appendLicense(licenses, "Apache License 2.0", readRawText(context, R.raw.license_apache_2));
            appendLicense(licenses, "MIT License", readRawText(context, R.raw.license_mit));
            appendLicense(licenses, "GPLv2 with Classpath exception", readRawText(context, R.raw.license_gpl_2_classpath));
            appendLicense(licenses, "BSD 2-Clause License", readRawText(context, R.raw.license_bsd_2_clause));
            return licenses.toString();
        }

        private static void appendLicense(@NonNull StringBuilder output, @NonNull String title,
                                          @NonNull String body) {
            output.append("\n\n## ").append(title).append("\n\n```text\n")
                .append(body).append("\n```\n");
        }

        @NonNull
        private static String readRawText(@NonNull Context context, int resourceId) {
            try (InputStream input = context.getResources().openRawResource(resourceId);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "Unable to read bundled license text: " + e.getMessage();
            }
        }

        private void configureDonatePreference(@NonNull Context context) {
            Preference donatePreference = findPreference("donate");
            if (donatePreference != null) {
                String signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context);
                if (signingCertificateSHA256Digest != null) {
                    // If APK is a Google Playstore release, then do not show the donation link
                    // since Termux isn't exempted from the playstore policy donation links restriction
                    // Check Fund solicitations: https://pay.google.com/intl/en_in/about/policy/
                    String apkRelease = TermuxUtils.getAPKRelease(signingCertificateSHA256Digest);
                    if (apkRelease == null || apkRelease.equals(TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)) {
                        donatePreference.setVisible(false);
                        return;
                    } else {
                        donatePreference.setVisible(true);
                    }
                }
                donatePreference.setOnPreferenceClickListener(preference -> {
                    ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL);
                    return true;
                });
            }
        }
    }
}
