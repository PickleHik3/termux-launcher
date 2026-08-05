package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import com.termux.R;
import com.termux.app.fragments.settings.PillPreference;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.SettingsSearchPreference;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_TAI_SETTINGS, false)) {
            intent.putExtra(EXTRA_INITIAL_FRAGMENT,
                "com.termux.app.fragments.settings.termux.TaiPreferencesFragment");
            intent.putExtra(EXTRA_INITIAL_TITLE_RES, R.string.termux_ai_preferences_title);
        }
        getSupportFragmentManager().popBackStackImmediate(null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settings, buildInitialFragment())
            .commit();
        setTitle(intent.getIntExtra(EXTRA_INITIAL_TITLE_RES,
            R.string.title_activity_termux_settings));
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

        /**
         * Maps each root destination row key to the XML preference resources that are reachable
         * underneath it (including nested sub-screens), used to build the lazily-computed child
         * search index below.
         */
        private static final Map<String, int[]> CHILD_XML_RESOURCES = new HashMap<>();
        static {
            CHILD_XML_RESOURCES.put("appearance", new int[]{
                R.xml.termux_style_preferences, R.xml.termux_fonts_preferences});
            CHILD_XML_RESOURCES.put("terminal_status", new int[]{
                R.xml.terminal_status_preferences});
            CHILD_XML_RESOURCES.put("keyboard_input", new int[]{
                R.xml.termux_keyboard_preferences});
            CHILD_XML_RESOURCES.put("launcher_apps", new int[]{
                R.xml.launcher_preferences});
            CHILD_XML_RESOURCES.put("services_permissions", new int[]{
                R.xml.services_permissions_preferences, R.xml.termux_ai_preferences,
                R.xml.termux_privileged_access_preferences, R.xml.termux_api_preferences});
            CHILD_XML_RESOURCES.put("advanced_diagnostics", new int[]{
                R.xml.advanced_diagnostics_preferences, R.xml.termux_terminal_io_preferences,
                R.xml.termux_terminal_view_preferences});
            CHILD_XML_RESOURCES.put("about_support", new int[]{
                R.xml.about_support_preferences});
        }

        /** One indexed child preference: its display title and lowercase searchable text. */
        private static final class ChildSearchEntry {
            final String title;
            final String searchable;
            ChildSearchEntry(String title, String searchable) {
                this.title = title;
                this.searchable = searchable;
            }
        }

        // Lazily built on first non-empty query; key -> indexed child preferences under it.
        private final Map<String, List<ChildSearchEntry>> mChildSearchIndex = new HashMap<>();
        private boolean mChildSearchIndexBuilt = false;

        // Stashed original summaries for destination rows, keyed by preference key, so a
        // "Contains: X, Y, Z" summary swapped in during a search can be restored afterwards.
        private final Map<String, CharSequence> mOriginalSummaries = new HashMap<>();

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null)
                return;
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            SettingsLayoutUtils.applyRootLayout(this);
            configureSearch();
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getActivity() != null) {
                getActivity().setTitle(R.string.title_activity_termux_settings);
            }
        }

        private void configureSearch() {
            SettingsSearchPreference search = findPreference("settings_search");
            if (search == null) return;
            stashOriginalSummaries();
            search.setOnQueryChangedListener(query -> {
                String needle = query.trim().toLowerCase(Locale.ROOT);
                PreferenceScreen screen = getPreferenceScreen();
                if (screen == null) return;
                if (!needle.isEmpty()) {
                    ensureChildSearchIndexBuilt();
                }
                for (int i = 0; i < screen.getPreferenceCount(); i++) {
                    Preference top = screen.getPreference(i);
                    if (top == search) continue;
                    if (top instanceof PreferenceCategory) {
                        PreferenceGroup category = (PreferenceGroup) top;
                        boolean anyChildVisible = false;
                        for (int j = 0; j < category.getPreferenceCount(); j++) {
                            if (filterDestinationRow(category.getPreference(j), needle)) {
                                anyChildVisible = true;
                            }
                        }
                        top.setVisible(needle.isEmpty() || anyChildVisible);
                    } else {
                        filterDestinationRow(top, needle);
                    }
                }
            });
        }

        /** Captures each destination row's original summary before the search box mutates it. */
        private void stashOriginalSummaries() {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) return;
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference top = screen.getPreference(i);
                if (top instanceof PreferenceCategory) {
                    PreferenceGroup category = (PreferenceGroup) top;
                    for (int j = 0; j < category.getPreferenceCount(); j++) {
                        Preference row = category.getPreference(j);
                        if (row.getKey() != null) {
                            mOriginalSummaries.put(row.getKey(), row.getSummary());
                        }
                    }
                }
            }
        }

        /**
         * Shows/hides a single destination row for the given lowercase query and returns whether
         * it should be visible. Matches on the row's own title/summary first; if that fails, falls
         * back to the indexed child preferences reachable under it (see {@link #CHILD_XML_RESOURCES}),
         * swapping in a "Contains: ..." summary so the match reason is visible.
         */
        private boolean filterDestinationRow(@NonNull Preference row, @NonNull String needle) {
            String key = row.getKey();
            CharSequence originalSummary = key == null ? row.getSummary() : mOriginalSummaries.get(key);

            if (needle.isEmpty()) {
                row.setSummary(originalSummary);
                row.setVisible(true);
                return true;
            }

            CharSequence title = row.getTitle();
            String ownSearchable = ((title == null ? "" : title.toString()) + " "
                + (originalSummary == null ? "" : originalSummary.toString()))
                .toLowerCase(Locale.ROOT);
            if (ownSearchable.contains(needle)) {
                row.setSummary(originalSummary);
                row.setVisible(true);
                return true;
            }

            List<ChildSearchEntry> childEntries = key == null ? null : mChildSearchIndex.get(key);
            if (childEntries != null) {
                List<String> matchedTitles = new ArrayList<>();
                boolean anyChildMatch = false;
                for (ChildSearchEntry entry : childEntries) {
                    if (entry.searchable.contains(needle)) {
                        anyChildMatch = true;
                        if (!entry.title.isEmpty() && matchedTitles.size() < 3) {
                            matchedTitles.add(entry.title);
                        }
                    }
                }
                if (anyChildMatch) {
                    row.setSummary(row.getContext().getString(R.string.settings_search_contains,
                        TextUtils.join(", ", matchedTitles)));
                    row.setVisible(true);
                    return true;
                }
            }

            row.setSummary(originalSummary);
            row.setVisible(false);
            return false;
        }

        /**
         * Inflates the XML resources reachable under each destination row into a scratch
         * {@link PreferenceScreen} and walks them to build a searchable index of child titles and
         * summaries. Runs once, lazily, on the first non-empty search query. A fresh
         * {@link PreferenceManager} is used per inflate (rather than this fragment's own manager)
         * so keys in these sub-screen XMLs cannot collide with the live root screen's preferences.
         * Each XML is inflated in its own try/catch so a single misbehaving custom Preference
         * constructor cannot break search for the rest.
         */
        private void ensureChildSearchIndexBuilt() {
            if (mChildSearchIndexBuilt) return;
            mChildSearchIndexBuilt = true;
            Context context = getContext();
            if (context == null) return;
            for (Map.Entry<String, int[]> destination : CHILD_XML_RESOURCES.entrySet()) {
                List<ChildSearchEntry> entries = new ArrayList<>();
                for (int xmlRes : destination.getValue()) {
                    try {
                        PreferenceManager scratchManager = new PreferenceManager(context);
                        PreferenceScreen inflated = scratchManager.inflateFromResource(context, xmlRes, null);
                        if (inflated != null) {
                            collectChildSearchEntries(inflated, entries);
                        }
                    } catch (Exception e) {
                        // Skip this XML; search degrades gracefully instead of crashing the screen.
                    }
                }
                mChildSearchIndex.put(destination.getKey(), entries);
            }
        }

        private static void collectChildSearchEntries(@NonNull PreferenceGroup group,
                                                       @NonNull List<ChildSearchEntry> out) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference child = group.getPreference(i);
                CharSequence title = child.getTitle();
                CharSequence summary = child.getSummary();
                String titleText = title == null ? "" : title.toString();
                String searchable = (titleText + " " + (summary == null ? "" : summary.toString()))
                    .trim().toLowerCase(Locale.ROOT);
                if (!searchable.isEmpty()) {
                    out.add(new ChildSearchEntry(titleText, searchable));
                }
                if (child instanceof PreferenceGroup) {
                    collectChildSearchEntries((PreferenceGroup) child, out);
                }
            }
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

        private void configureReportIssuePreference(@NonNull Context context) {
            Preference reportIssuePreference = findPreference("report_issue");
            if (reportIssuePreference != null) {
                reportIssuePreference.setOnPreferenceClickListener(preference -> {
                    ShareUtils.openUrl(context, "https://github.com/PickleHik3/termux-launcher/issues");
                    return true;
                });
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
