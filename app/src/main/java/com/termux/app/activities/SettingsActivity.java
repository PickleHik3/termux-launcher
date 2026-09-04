package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.SettingsSearchPreference;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String LOG_TAG = "SettingsActivity";
    private static final String SETTINGS_FRAGMENT_PACKAGE_PREFIX = "com.termux.app.fragments.settings.";

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
        setTitleFromIntent(getIntent());
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
        setTitleFromIntent(intent);
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

    /**
     * Fragment class names carried by an Intent are attacker-supplied: this Activity is exported,
     * so any installed app can name a class here. Only settings screens shipped by this app may be
     * instantiated -- everything else (arbitrary library fragments, anything with a side effect in
     * its constructor or {@code onCreate}) falls back to the root screen.
     */
    static boolean isAllowedInitialFragment(@NonNull Class<?> candidate) {
        // Any fragment from the settings package, not only a preference screen: the keyboard's
        // colour editor is a plain Fragment, and requiring PreferenceFragmentCompat here quietly
        // bounced every deep link to it back to the root page. The package prefix is the guard
        // that matters — this activity is exported, so the class name in the Intent is
        // attacker-controlled, and nothing outside the settings screens may be instantiated.
        if (!Fragment.class.isAssignableFrom(candidate)) return false;
        String name = candidate.getName();
        return name.startsWith(SETTINGS_FRAGMENT_PACKAGE_PREFIX)
            || name.startsWith(SettingsActivity.class.getName() + "$");
    }

    @NonNull
    private Fragment buildInitialFragment() {
        String fragmentClassName = getIntent().getStringExtra(EXTRA_INITIAL_FRAGMENT);
        if (fragmentClassName == null || fragmentClassName.isEmpty()) {
            return new RootPreferencesFragment();
        }
        try {
            Class<?> fragmentClass = getClassLoader().loadClass(fragmentClassName);
            if (!isAllowedInitialFragment(fragmentClass)) {
                Logger.logWarn(LOG_TAG, "Refusing to open non-settings fragment: " + fragmentClassName);
                return new RootPreferencesFragment();
            }
            return getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), fragmentClassName);
        } catch (ClassNotFoundException e) {
            // A Settings task, shortcut, or rebroadcast Intent may outlive an in-place APK upgrade.
            // Fragment class names carried by that old Intent are not guaranteed to exist in the
            // newly installed build, so return to the stable root screen instead of crashing.
            return new RootPreferencesFragment();
        } catch (Fragment.InstantiationException e) {
            // A Settings task, shortcut, or rebroadcast Intent may outlive an in-place APK upgrade.
            // Fragment class names carried by that old Intent are not guaranteed to exist in the
            // newly installed build, so return to the stable root screen instead of crashing.
            if (e.getCause() instanceof ClassNotFoundException)
                return new RootPreferencesFragment();
            throw e;
        }
    }

    private void setTitleFromIntent(@NonNull Intent intent) {
        int titleResId = intent.getIntExtra(EXTRA_INITIAL_TITLE_RES,
            R.string.title_activity_termux_settings);
        try {
            setTitle(titleResId != 0 ? titleResId : R.string.title_activity_termux_settings);
        } catch (android.content.res.Resources.NotFoundException e) {
            // Resource IDs are build-local integers. A Settings task, shortcut, or rebroadcast
            // Intent retained across an in-place APK upgrade can therefore carry a dangling ID.
            setTitle(R.string.title_activity_termux_settings);
        }
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
                R.xml.launcher_preferences, R.xml.x11_display_preferences});
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

    }
}
