package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import com.termux.R;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.SettingsMaterialDialogs;
import com.termux.app.fragments.settings.SettingsSearchPreference;
import com.termux.app.launcher.LauncherUseCaseMode;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
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

    /**
     * Which place a per-place settings page should open on, as its tool name ("widgets"), and
     * which of its rows to bring into view. Both are handed to the initial fragment as arguments,
     * so a page that can be deep-linked reads them like any other fragment argument and a page
     * that cannot simply ignores them - no bespoke Intent per caller.
     */
    public static final String EXTRA_INITIAL_PLACE = "settings_initial_place";
    public static final String EXTRA_SCROLL_TO_KEY = "settings_scroll_to_key";

    /** SharedPreferences key for the JSON stack {@link SettingsBackStackState} serializes. */
    static final String PREFS_KEY_BACK_STACK_STATE = "settings_back_stack_state_v1";
    /** {@code Bundle} key the parallel title bookkeeping below is saved under across rotation etc. */
    private static final String STATE_KEY_PUSHED_SCREENS = "settings_pushed_screens_v1";

    /**
     * Screens pushed after the root, in push order, kept in parallel with the FragmentManager's
     * own back stack. The FragmentManager knows how to restore the fragments themselves (from
     * {@code savedInstanceState}, or transaction-by-transaction as this class replays a saved
     * stack), but not which title each one carried or which deep-link arguments it was opened
     * with -- both of which this list keeps so the toolbar title and persisted state stay right.
     */
    private final List<SettingsBackStackState.Entry> mPushedScreens = new ArrayList<>();

    /** Set in {@link #onStop}; read by {@link #onNewIntent} to judge whether the stack is stale. */
    private long mLastStoppedAtEpochMs = 0;

    public static Intent createFragmentIntent(@NonNull Context context, @NonNull Class<? extends Fragment> fragmentClass, int titleResId) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(EXTRA_INITIAL_FRAGMENT, fragmentClass.getName());
        if (titleResId != 0) {
            intent.putExtra(EXTRA_INITIAL_TITLE_RES, titleResId);
        }
        return intent;
    }

    /** The same Intent, opened on one place and scrolled to one of its rows. */
    public static Intent createFragmentIntent(@NonNull Context context,
                                              @NonNull Class<? extends Fragment> fragmentClass,
                                              int titleResId, @Nullable String place,
                                              @Nullable String scrollToKey) {
        Intent intent = createFragmentIntent(context, fragmentClass, titleResId);
        if (place != null) intent.putExtra(EXTRA_INITIAL_PLACE, place);
        if (scrollToKey != null) intent.putExtra(EXTRA_SCROLL_TO_KEY, scrollToKey);
        return intent;
    }

    /**
     * The deep-link arguments an Intent carries, or null when it carries none. Read by the
     * initial fragment; nothing else in the Intent reaches it.
     */
    private static final String PAGES = "com.termux.app.fragments.settings.termux.";

    /**
     * A deep link that names an old page and a row that has since moved to a focused subpage
     * (an external Intent, a saved Back stack, a search hit) is sent to the page that holds the row,
     * with the same scroll key, so the row is still found. Any other class or key is returned as
     * given, so a link without a key keeps opening the overview it always named.
     */
    @NonNull
    static String redirectLegacyPage(@NonNull String className, @Nullable String scrollToKey) {
        if (scrollToKey == null) return className;
        String page = null;
        if ((PAGES + "KeyboardPreferencesFragment").equals(className)) {
            if (scrollToKey.startsWith("keyboard_voice") || scrollToKey.equals("keyboard_docs_voice"))
                page = "KeyboardVoicePreferencesFragment";
            else if (scrollToKey.equals("in_app_keyboard_hide_on_hardware")
                || scrollToKey.equals("pass_ctrl_space_to_android"))
                page = "KeyboardHardwarePreferencesFragment";
            else if (scrollToKey.equals("keyboard_layout") || scrollToKey.equals("in_app_keyboard_extra_keys")
                || scrollToKey.equals("in_app_keyboard_custom_layout") || scrollToKey.equals("in_app_keyboard_layouts")
                || scrollToKey.startsWith("keyboard_docs_") || scrollToKey.startsWith("keyboard_credits"))
                page = "KeyboardLayoutPreferencesFragment";
            else if (scrollToKey.equals("keyboard_shapes") || scrollToKey.startsWith("in_app_keyboard_floating_")
                || scrollToKey.equals("in_app_keyboard_split_gap"))
                page = "KeyboardSizePreferencesFragment";
            else if (scrollToKey.equals("keyboard_typing") || scrollToKey.equals("keyboard_feedback")
                || scrollToKey.startsWith("in_app_keyboard_tap_correction")
                || scrollToKey.equals("in_app_keyboard_key_sound_enabled")
                || scrollToKey.equals("in_app_keyboard_key_popup"))
                page = "KeyboardTypingPreferencesFragment";
            else if (scrollToKey.equals("in_app_keyboard_haptics_enabled"))
                page = "AppBehaviorPreferencesFragment";
        } else if ((PAGES + "LauncherPreferencesFragment").equals(className)) {
            if (scrollToKey.equals("app_launcher_input_char") || scrollToKey.equals("app_launcher_reset_usage_ranking"))
                page = "LauncherSearchPreferencesFragment";
            else if (scrollToKey.equals("app_launcher_az_lock_method")
                || scrollToKey.equals("app_launcher_az_double_tap_lock"))
                page = "LauncherLockPreferencesFragment";
            else if (scrollToKey.equals("app_launcher_most_used_page"))
                page = "LauncherDockPreferencesFragment";
            else if (scrollToKey.equals("app_haptics_enabled") || scrollToKey.equals("app_launcher_row_haptics")
                || scrollToKey.equals("show_in_recents_when_not_default"))
                page = "AppBehaviorPreferencesFragment";
        } else if ((PAGES + "X11DisplayPreferencesFragment").equals(className)) {
            switch (scrollToKey) {
                case "touchMode": case "x11_android_keyboard": case "x11_keyboard_follows_text":
                case "clipboardEnable":
                    page = "X11DisplayInputPreferencesFragment"; break;
                case "displayResolutionMode": case "displayScale": case "displayResolutionExact":
                case "displayResolutionCustom": case "displayFilteringMode": case "x11_display_dpi":
                    page = "X11DisplayResolutionPreferencesFragment"; break;
                case "x11_display_autostart": case "x11_display_command": case "x11_set_display_env":
                    page = "X11DisplayStartupPreferencesFragment"; break;
                case "x11_legacy_drawing": case "x11_force_bgra":
                    page = "X11DisplayTroubleshootingPreferencesFragment"; break;
                case "x11_drawer_apps": case "x11_gui_apps_setup": case "x11_hidden_apps":
                case "x11_window_manager": case "x11_runtime_badge": case "x11_window_manager_hint":
                    page = "X11DisplayLinuxAppsPreferencesFragment"; break;
                default: break;
            }
        }
        return page == null ? className : PAGES + page;
    }

    @Nullable
    static Bundle deepLinkArguments(@NonNull Intent intent) {
        String place = intent.getStringExtra(EXTRA_INITIAL_PLACE);
        String scrollToKey = intent.getStringExtra(EXTRA_SCROLL_TO_KEY);
        if (place == null && scrollToKey == null) return null;
        Bundle arguments = new Bundle();
        if (place != null) arguments.putString(EXTRA_INITIAL_PLACE, place);
        if (scrollToKey != null) arguments.putString(EXTRA_SCROLL_TO_KEY, scrollToKey);
        return arguments;
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
            // A plain re-entry (no deep link) within the retain window means the previous
            // instance of this task was killed (process death, not a deliberate exit) while the
            // user was somewhere other than the root; put them back where they were instead of
            // starting over. restoreSavedStackIfFresh returns false, doing nothing, whenever
            // there is nothing to restore, it has expired, or it fails validation.
            if (!(isPlainEntryIntent(intent) && restoreSavedStackIfFresh())) {
                Fragment initialFragment = buildInitialFragment();
                getSupportFragmentManager().beginTransaction().replace(R.id.settings, initialFragment).commit();
            }
        } else {
            restorePushedScreensBookkeeping(savedInstanceState);
        }
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        // Keeps the toolbar title in step with Back, including a Back that the platform's own
        // OnBackPressedDispatcher integration pops without this class hearing about it directly.
        getSupportFragmentManager().addOnBackStackChangedListener(this::onBackStackChanged);
        if (mPushedScreens.isEmpty()) {
            setTitleFromIntent(getIntent());
        } else {
            updateTitleForCurrentStack();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // The FragmentManager saves the fragments and its own back stack by itself; this saves
        // only the parallel title/argument bookkeeping in mPushedScreens, which it knows nothing
        // about. The timestamp is irrelevant here (this path is a config change or process death
        // with the Activity coming straight back, not a stale re-entry) so it is left at 0.
        outState.putString(STATE_KEY_PUSHED_SCREENS,
            new SettingsBackStackState(new ArrayList<>(mPushedScreens), 0).serialize());
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
        if (isPlainEntryIntent(intent) && isWithinRetainWindow()) {
            // The user tapped Settings again from the launcher shortly after leaving it (this
            // instance was never killed - it just went to the background); keep whatever screen
            // and back stack they had instead of popping to root.
            return;
        }
        mPushedScreens.clear();
        getSupportFragmentManager().popBackStackImmediate(null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settings, buildInitialFragment())
            .commit();
        setTitleFromIntent(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mLastStoppedAtEpochMs = System.currentTimeMillis();
        if (isFinishing() && mPushedScreens.isEmpty()) {
            // A deliberate exit from the root screen (Back/Up with nothing left to pop): the next
            // open should start fresh at root, not resume a stack that no longer exists.
            getSettingsBackStackPreferences().edit().remove(PREFS_KEY_BACK_STACK_STATE).apply();
        } else {
            SettingsBackStackState state =
                new SettingsBackStackState(new ArrayList<>(mPushedScreens), mLastStoppedAtEpochMs);
            getSettingsBackStackPreferences().edit()
                .putString(PREFS_KEY_BACK_STACK_STATE, state.serialize()).apply();
        }
    }

    /**
     * A "plain" open of Settings: no deep-linked fragment, no QA TAI shortcut, no per-place
     * arguments. This is what {@code openSettingsHome} in TermuxActivity sends, and it is the only
     * kind of Intent this class treats as a request to resume wherever the user left off rather
     * than a request to open a specific screen.
     */
    static boolean isPlainEntryIntent(@NonNull Intent intent) {
        return TextUtils.isEmpty(intent.getStringExtra(EXTRA_INITIAL_FRAGMENT))
            && !intent.getBooleanExtra(EXTRA_OPEN_TAI_SETTINGS, false)
            && intent.getStringExtra(EXTRA_INITIAL_PLACE) == null
            && intent.getStringExtra(EXTRA_SCROLL_TO_KEY) == null;
    }

    private boolean isWithinRetainWindow() {
        // 0 means this instance has not been stopped yet (e.g. onNewIntent while still resumed,
        // which the platform allows for a singleTask Activity); treat that as still fresh.
        if (mLastStoppedAtEpochMs == 0) return true;
        long elapsed = System.currentTimeMillis() - mLastStoppedAtEpochMs;
        return elapsed >= 0 && elapsed < SettingsBackStackState.RETAIN_WINDOW_MS;
    }

    private SharedPreferences getSettingsBackStackPreferences() {
        return getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
    }

    /**
     * Rebuilds the stack this Activity had when it was last stopped, provided that was within
     * {@link SettingsBackStackState#RETAIN_WINDOW_MS}. Returns false, having built nothing, when
     * there is no saved stack, it has expired, or its very first entry fails validation -- every
     * one of those cases falls back to the plain root the caller builds instead. An entry after
     * the first that fails validation (for example a fragment class an APK upgrade removed) simply
     * stops the replay there, keeping whatever was already legitimately restored, the same way
     * {@link #buildInitialFragment} falls back rather than crashes on a single bad entry.
     */
    private boolean restoreSavedStackIfFresh() {
        String raw = getSettingsBackStackPreferences().getString(PREFS_KEY_BACK_STACK_STATE, null);
        SettingsBackStackState saved = SettingsBackStackState.parse(raw);
        if (saved == null || !saved.isFresh(System.currentTimeMillis())) return false;

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settings, new RootPreferencesFragment())
            .commit();

        for (SettingsBackStackState.Entry entry : saved.entries) {
            Class<?> fragmentClass;
            try {
                fragmentClass = getClassLoader().loadClass(entry.className);
            } catch (ClassNotFoundException e) {
                break;
            }
            if (!isAllowedInitialFragment(fragmentClass)) break;
            @SuppressWarnings("unchecked")
            Class<? extends Fragment> screenClass = (Class<? extends Fragment>) fragmentClass;
            Bundle args = null;
            if (entry.place != null || entry.scrollToKey != null) {
                args = new Bundle();
                if (entry.place != null) args.putString(EXTRA_INITIAL_PLACE, entry.place);
                if (entry.scrollToKey != null) args.putString(EXTRA_SCROLL_TO_KEY, entry.scrollToKey);
            }
            pushScreen(screenClass, entry.titleResId, entry.titleText, args);
        }
        return true;
    }

    /** Restores only the title/argument bookkeeping; the fragments/back stack restore themselves. */
    private void restorePushedScreensBookkeeping(@NonNull Bundle savedInstanceState) {
        SettingsBackStackState state =
            SettingsBackStackState.parse(savedInstanceState.getString(STATE_KEY_PUSHED_SCREENS));
        mPushedScreens.clear();
        if (state != null) mPushedScreens.addAll(state.entries);
    }

    private void onBackStackChanged() {
        int entryCount = getSupportFragmentManager().getBackStackEntryCount();
        while (mPushedScreens.size() > entryCount) {
            mPushedScreens.remove(mPushedScreens.size() - 1);
        }
        updateTitleForCurrentStack();
    }

    private void updateTitleForCurrentStack() {
        if (mPushedScreens.isEmpty()) {
            setTitle(R.string.title_activity_termux_settings);
            return;
        }
        SettingsBackStackState.Entry top = mPushedScreens.get(mPushedScreens.size() - 1);
        if (top.titleResId != 0) {
            try {
                setTitle(top.titleResId);
                return;
            } catch (android.content.res.Resources.NotFoundException e) {
                // Fall through to the plain-text/default title below; see setTitleFromIntent for
                // why a stale resource id can outlive the build that assigned it.
            }
        }
        if (top.titleText != null) {
            setTitle(top.titleText);
        } else {
            setTitle(R.string.title_activity_termux_settings);
        }
    }

    /**
     * Pushes a settings sub-screen onto the back stack in place, instead of relaunching this
     * singleTask Activity through {@link #onNewIntent} -- which pops back to the root, so Back
     * closed Settings instead of returning to the screen the user came from. Call sites inside
     * Settings that used to do {@code startActivity(createFragmentIntent(...))} call this instead;
     * {@link #createFragmentIntent} is unchanged for launches that arrive from outside Settings, for
     * which going through onCreate/onNewIntent at the deep-linked screen is exactly what is wanted.
     */
    public void openScreen(@NonNull Class<? extends Fragment> fragmentClass, @StringRes int titleResId,
                           @Nullable Bundle args) {
        pushScreen(fragmentClass, titleResId, null, args);
    }

    private void pushScreen(@NonNull Class<? extends Fragment> fragmentClass, int titleResId,
                            @Nullable String titleText, @Nullable Bundle args) {
        String place = args == null ? null : args.getString(EXTRA_INITIAL_PLACE);
        String scrollToKey = args == null ? null : args.getString(EXTRA_SCROLL_TO_KEY);
        // A saved or external entry for an old page, scrolled to a row that moved, opens the
        // subpage that holds the row; the saved entry then records the page actually shown.
        String className = redirectLegacyPage(fragmentClass.getName(), scrollToKey);
        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
            .instantiate(getClassLoader(), className);
        if (args != null) fragment.setArguments(args);
        mPushedScreens.add(new SettingsBackStackState.Entry(
            className, titleResId, titleText, place, scrollToKey));
        // Named by its position so it always pops exactly one entry at a time, staying aligned
        // with mPushedScreens (see onBackStackChanged).
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(String.valueOf(mPushedScreens.size()))
            .commit();
        updateTitleForCurrentStack();
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
        fragmentClassName = redirectLegacyPage(fragmentClassName,
            getIntent().getStringExtra(EXTRA_SCROLL_TO_KEY));
        try {
            Class<?> fragmentClass = getClassLoader().loadClass(fragmentClassName);
            if (!isAllowedInitialFragment(fragmentClass)) {
                Logger.logWarn(LOG_TAG, "Refusing to open non-settings fragment: " + fragmentClassName);
                return new RootPreferencesFragment();
            }
            Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), fragmentClassName);
            Bundle arguments = deepLinkArguments(getIntent());
            if (arguments != null) fragment.setArguments(arguments);
            return fragment;
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

    /**
     * Back with nothing to pop finishes Settings itself. From Android 12 a Back at the root of a
     * task is handed to the system, which finishes only after it calls back; this activity is the
     * root of its own task (singleTask, own affinity), and on some systems that call never comes,
     * so Back and Up did nothing on the first screen. A screen pushed on top, or a fragment's own
     * Back handler (the extra-keys editor's unsaved edits), still goes through the dispatcher.
     */
    @Override
    public void onBackPressed() {
        if (getOnBackPressedDispatcher().hasEnabledCallbacks()) {
            super.onBackPressed();
            return;
        }
        finish();
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
        try {
            Class<?> fragmentClass = getClassLoader().loadClass(fragmentClassName);
            if (!isAllowedInitialFragment(fragmentClass)) {
                Logger.logWarn(LOG_TAG, "Refusing to open non-settings fragment: " + fragmentClassName);
                return false;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Fragment> screenClass = (Class<? extends Fragment>) fragmentClass;
            CharSequence title = preference.getTitle();
            pushScreen(screenClass, 0, title == null ? null : title.toString(), preference.getExtras());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {

        /**
         * Maps each root destination row key to the XML preference resources that are reachable
         * underneath it (including nested sub-screens), used to build the lazily-computed child
         * search index below.
         */
        private static final Map<String, int[]> CHILD_XML_RESOURCES = new HashMap<>();
        static {
            CHILD_XML_RESOURCES.put("wallpaper_style", new int[]{
                R.xml.termux_style_preferences, R.xml.termux_fonts_preferences});
            CHILD_XML_RESOURCES.put("terminal", new int[]{
                R.xml.terminal_preferences});
            CHILD_XML_RESOURCES.put("status_bar", new int[]{
                R.xml.status_bar_preferences});
            CHILD_XML_RESOURCES.put("notifications", new int[]{
                R.xml.notifications_preferences});
            CHILD_XML_RESOURCES.put("keyboard_input", new int[]{
                R.xml.termux_keyboard_preferences, R.xml.termux_keyboard_layout_preferences,
                R.xml.termux_keyboard_size_preferences, R.xml.termux_keyboard_typing_preferences,
                R.xml.termux_keyboard_voice_preferences, R.xml.termux_keyboard_voice_details_preferences,
                R.xml.termux_keyboard_hardware_preferences,
                R.xml.speech_model_preferences});
            CHILD_XML_RESOURCES.put("display", new int[]{
                R.xml.x11_display_preferences, R.xml.x11_display_input_preferences,
                R.xml.x11_display_resolution_preferences, R.xml.x11_display_linux_apps_preferences,
                R.xml.x11_display_startup_preferences, R.xml.x11_display_troubleshooting_preferences});
            CHILD_XML_RESOURCES.put("launcher_apps", new int[]{
                R.xml.launcher_preferences, R.xml.launcher_dock_preferences,
                R.xml.launcher_search_preferences, R.xml.launcher_lock_preferences,
                R.xml.app_drawer_preferences});
            CHILD_XML_RESOURCES.put("app_behavior", new int[]{
                R.xml.app_behavior_preferences});
            CHILD_XML_RESOURCES.put("services_permissions", new int[]{
                R.xml.services_permissions_preferences, R.xml.termux_ai_preferences,
                R.xml.termux_privileged_access_preferences, R.xml.termux_api_preferences});
            CHILD_XML_RESOURCES.put("advanced_diagnostics", new int[]{
                R.xml.advanced_diagnostics_preferences});
            CHILD_XML_RESOURCES.put("about_support", new int[]{
                R.xml.about_support_preferences});
        }

        /**
         * The focused subpage that holds each XML resource's rows, so a search hit can open the
         * page the setting is on rather than only the overview above it. A resource not listed
         * here belongs to the destination row's own page.
         */
        private static final String TERMUX_PAGES = "com.termux.app.fragments.settings.termux.";
        private static final Map<Integer, String> CHILD_XML_PAGES = new HashMap<>();
        static {
            CHILD_XML_PAGES.put(R.xml.termux_keyboard_layout_preferences, TERMUX_PAGES + "KeyboardLayoutPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.termux_keyboard_size_preferences, TERMUX_PAGES + "KeyboardSizePreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.termux_keyboard_typing_preferences, TERMUX_PAGES + "KeyboardTypingPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.termux_keyboard_voice_preferences, TERMUX_PAGES + "KeyboardVoicePreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.termux_keyboard_voice_details_preferences, TERMUX_PAGES + "KeyboardVoiceDetailsPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.termux_keyboard_hardware_preferences, TERMUX_PAGES + "KeyboardHardwarePreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.speech_model_preferences, TERMUX_PAGES + "SpeechModelPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.launcher_dock_preferences, TERMUX_PAGES + "LauncherDockPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.launcher_search_preferences, TERMUX_PAGES + "LauncherSearchPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.launcher_lock_preferences, TERMUX_PAGES + "LauncherLockPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.app_drawer_preferences, TERMUX_PAGES + "AppDrawerPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.x11_display_input_preferences, TERMUX_PAGES + "X11DisplayInputPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.x11_display_resolution_preferences, TERMUX_PAGES + "X11DisplayResolutionPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.x11_display_linux_apps_preferences, TERMUX_PAGES + "X11DisplayLinuxAppsPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.x11_display_startup_preferences, TERMUX_PAGES + "X11DisplayStartupPreferencesFragment");
            CHILD_XML_PAGES.put(R.xml.x11_display_troubleshooting_preferences, TERMUX_PAGES + "X11DisplayTroubleshootingPreferencesFragment");
        }

        /** One indexed child preference: its display title, lowercase searchable text and page. */
        private static final class ChildSearchEntry {
            final String title;
            final String searchable;
            /** The subpage that holds it, or null when it is on the destination row's own page. */
            @Nullable final String fragment;
            ChildSearchEntry(String title, String searchable, @Nullable String fragment) {
                this.title = title;
                this.searchable = searchable;
                this.fragment = fragment;
            }
        }

        // Each destination row's own fragment, captured before a search may point it at a subpage.
        private final Map<String, String> mOriginalFragments = new HashMap<>();

        // Lazily built on first non-empty query; key -> indexed child preferences under it.
        private final Map<String, List<ChildSearchEntry>> mChildSearchIndex = new HashMap<>();
        private boolean mChildSearchIndexBuilt = false;

        // Stashed original summaries for destination rows, keyed by preference key, so a
        // "Contains: X, Y, Z" summary swapped in during a search can be restored afterwards.
        private final Map<String, CharSequence> mOriginalSummaries = new HashMap<>();

        /** The usage mode row, which is the one root row that writes a preference. */
        private static final String KEY_USE_AS = "app_launcher_use_case_mode";

        /** The one door to wallpaper, Look and Layout; the Look page rows are indexed under it. */
        private static final String KEY_WALLPAPER_STYLE = "wallpaper_style";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null)
                return;
            // The mode row reads and writes through the launcher's store, the same path the
            // Appearance and Launcher pages take; every other root row only navigates.
            getPreferenceManager().setPreferenceDataStore(
                com.termux.app.fragments.settings.termux.TermuxStylePreferencesFragment
                    .dataStore(context));
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            // A build made without the X server has no display to set up.
            Preference display = findPreference("display");
            if (display != null && !com.termux.BuildConfig.X11_SERVER) display.setVisible(false);
            configureWallpaperStyleRow(context);
            SettingsLayoutUtils.applyRootLayout(this);
            configureUseAsRow(context);
            configureSearch();
        }

        /**
         * The "Appearance" row opens no page of its own: it brings the launcher forward with
         * the wallpaper picker over it, the same door the corner tab's button is. While a search hit
         * comes from a Look page row it carries that page's fragment instead (see
         * {@link #filterDestinationRow}), and the default handling opens the page.
         */
        private void configureWallpaperStyleRow(@NonNull Context context) {
            Preference row = findPreference(KEY_WALLPAPER_STYLE);
            if (row == null) return;
            row.setOnPreferenceClickListener(preference -> {
                if (preference.getFragment() != null) return false;
                Intent intent = new Intent(context, com.termux.app.TermuxActivity.class);
                intent.putExtra(com.termux.app.TermuxActivity.EXTRA_WALLPAPER_STYLE, true);
                intent.putExtra(com.termux.app.TermuxActivity.EXTRA_APPEARANCE_FROM_SETTINGS, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return true;
            });
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getActivity() != null) {
                getActivity().setTitle(R.string.title_activity_termux_settings);
            }
            // A switch on a page below can have moved the surfaces off the preset.
            refreshUseAsSummary();
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            // The mode picker is the rich radio list the other pages' choices use: a bold name
            // with its one line under it, rather than the platform's plain single-choice list.
            if (getContext() != null && SettingsMaterialDialogs.show(getContext(), preference)) {
                return;
            }
            super.onDisplayPreferenceDialog(preference);
        }

        /**
         * The "Use as" row: a quiet row above the destinations, whose summary names the preset
         * the launcher is in — or "Custom" once a switch below has moved it off one. Its picker
         * lists the modes this build offers, each with its one-line description.
         */
        private void configureUseAsRow(@NonNull Context context) {
            ListPreference row = findPreference(KEY_USE_AS);
            if (row == null) return;
            // A destination row carries an icon tile; this one is a setting, so it takes the
            // plain row every page below uses, with the chevron a chooser promises.
            row.setLayoutResource(R.layout.preference_settings_row);
            row.setWidgetLayoutResource(R.layout.preference_widget_chevron);
            List<String> modes = LauncherUseCaseMode.offeredModes(com.termux.BuildConfig.X11_SERVER);
            CharSequence[] entries = new CharSequence[modes.size()];
            CharSequence[] values = new CharSequence[modes.size()];
            for (int i = 0; i < modes.size(); i++) {
                String mode = modes.get(i);
                values[i] = mode;
                // A choice with nothing to add is the bare title: no empty second line.
                String description = context.getString(LauncherUseCaseMode.descriptionRes(mode));
                entries[i] = description.isEmpty()
                    ? context.getString(LauncherUseCaseMode.titleRes(mode))
                    : context.getString(LauncherUseCaseMode.titleRes(mode)) + "\n" + description;
            }
            row.setEntries(entries);
            row.setEntryValues(values);
            row.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean terminal = LauncherUseCaseMode.MODE_TERMINAL.equals(newValue);
                // The store applies the preset as the value lands; the summary is read back
                // after that write, and the home-screen question is asked once the picker is down.
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    refreshUseAsSummary();
                    if (terminal && isDefaultHomeApp(context)) offerAnotherHomeApp(context);
                });
                return true;
            });
            refreshUseAsSummary();
        }

        private void refreshUseAsSummary() {
            ListPreference row = findPreference(KEY_USE_AS);
            Context context = getContext();
            if (row == null || context == null) return;
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences preferences =
                com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
                    .build(context, true);
            if (preferences == null) return;
            String mode = LauncherUseCaseMode.summaryMode(preferences, com.termux.BuildConfig.X11_SERVER);
            CharSequence summary = context.getString(LauncherUseCaseMode.titleRes(mode));
            row.setSummary(summary);
            // The search box restores this when a query is cleared, so it has to follow the mode.
            mOriginalSummaries.put(KEY_USE_AS, summary);
        }

        /**
         * Terminal mode was picked while this launcher is the phone's home app. Nothing is forced:
         * the terminal keeps answering Home, and the way to another home screen is offered once.
         */
        private void offerAnotherHomeApp(@NonNull Context context) {
            if (!isAdded()) return;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.settings_use_as_still_home_title)
                .setMessage(R.string.settings_use_as_still_home_message)
                .setNegativeButton(R.string.settings_use_as_keep_home, null)
                .setPositiveButton(R.string.settings_use_as_choose_home,
                    (dialog, which) -> com.termux.app.HomeAppChooser.open(context))
                .show();
        }

        /** Whether this package answers the phone's Home intent right now. */
        private static boolean isDefaultHomeApp(@NonNull Context context) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolved = context.getPackageManager()
                .resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null && resolved.activityInfo != null
                && context.getPackageName().equals(resolved.activityInfo.packageName);
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

        /**
         * Captures each row's original summary before the search box mutates it: the destination
         * rows under their headers, and the mode row that stands above them on its own.
         */
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
                            mOriginalFragments.put(row.getKey(), row.getFragment());
                        }
                    }
                } else if (top.getKey() != null && !(top instanceof SettingsSearchPreference)) {
                    mOriginalSummaries.put(top.getKey(), top.getSummary());
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

            if (KEY_WALLPAPER_STYLE.equals(key)) row.setFragment(null);
            else if (key != null && mOriginalFragments.containsKey(key)) {
                row.setFragment(mOriginalFragments.get(key));
            }

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
                // The subpage every hit lives on, when they all live on the same one.
                java.util.Set<String> matchedPages = new java.util.HashSet<>();
                for (ChildSearchEntry entry : childEntries) {
                    if (entry.searchable.contains(needle)) {
                        anyChildMatch = true;
                        matchedPages.add(entry.fragment == null ? "" : entry.fragment);
                        if (!entry.title.isEmpty() && matchedTitles.size() < 3) {
                            matchedTitles.add(entry.title);
                        }
                    }
                }
                if (anyChildMatch) {
                    // A hit on a Look page row opens that page rather than the picker.
                    if (KEY_WALLPAPER_STYLE.equals(key)) {
                        row.setFragment(com.termux.app.fragments.settings.termux
                            .TermuxStylePreferencesFragment.class.getName());
                    }
                    if (matchedPages.size() == 1 && !KEY_WALLPAPER_STYLE.equals(key)) {
                        String page = matchedPages.iterator().next();
                        if (!page.isEmpty()) row.setFragment(page);
                    }
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
                            collectChildSearchEntries(inflated, entries, CHILD_XML_PAGES.get(xmlRes));
                        }
                    } catch (Exception e) {
                        // Skip this XML; search degrades gracefully instead of crashing the screen.
                    }
                }
                mChildSearchIndex.put(destination.getKey(), entries);
            }
        }

        private static void collectChildSearchEntries(@NonNull PreferenceGroup group,
                                                       @NonNull List<ChildSearchEntry> out,
                                                       @Nullable String page) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference child = group.getPreference(i);
                CharSequence title = child.getTitle();
                CharSequence summary = child.getSummary();
                String titleText = title == null ? "" : title.toString();
                String searchable = (titleText + " " + (summary == null ? "" : summary.toString()))
                    .trim().toLowerCase(Locale.ROOT);
                if (!searchable.isEmpty()) {
                    out.add(new ChildSearchEntry(titleText, searchable, page));
                }
                if (child instanceof PreferenceGroup) {
                    collectChildSearchEntries((PreferenceGroup) child, out, page);
                }
            }
        }

    }
}
