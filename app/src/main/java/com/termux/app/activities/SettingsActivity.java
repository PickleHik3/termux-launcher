package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewGroupCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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
import com.termux.app.fragments.settings.SettingsMotion;
import com.termux.app.fragments.settings.SettingsMotionPolicy;
import com.termux.app.fragments.settings.SettingsTransitions;
import com.termux.app.fragments.settings.SettingsMaterialDialogs;
import com.termux.app.fragments.settings.SettingsRowRing;
import com.termux.app.fragments.settings.SettingsSearchFragment;
import com.termux.app.fragments.settings.SettingsSearchPreference;
import com.termux.app.fragments.settings.SettingsSearchResults;
import com.termux.app.fragments.settings.SettingsTree;
import com.termux.app.fragments.settings.SettingsUnsavedChanges;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity implements
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, SettingsSearchResults.Navigator {

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

    /** {@code Bundle} key for whether the saved state was laid out with two panes. */
    private static final String STATE_KEY_TWO_PANE = "settings_two_pane_v1";

    /**
     * Two panes (F4, a smallest width of 600 dp: tablets, never a phone in landscape): the root list stands in {@code settings_list} and the
     * open section in {@code settings}, whose back stack {@link #mPushedScreens} mirrors as on a
     * phone. On a phone the root is the base of {@code settings} itself.
     */
    private boolean mTwoPane;

    /**
     * When Settings last closed (elapsedRealtime), set in {@link #onStop}. Static so a reopen after
     * the window finished sees it; read by {@link #onCreate} and {@link #onNewIntent} through
     * {@link SettingsReopenPolicy}. After process death it is empty and Settings opens at the root.
     */
    private static long sLastClosedAtMs = SettingsReopenPolicy.NO_CLOSE;

    @androidx.annotation.VisibleForTesting
    static void setLastClosedAtMs(long closedAtMs) {
        sLastClosedAtMs = closedAtMs;
    }

    /** The sheet over Home the pages stand in; see {@link SettingsSheet}. */
    private SettingsSheet mSheet;

    /** Whether the toolbar shows the close mark (true) or the arrow; null before the first look. */
    @Nullable private Boolean mNavigationClosesShown;

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
     * Where a link that names an old page and a row should land in today's tree: an old page name
     * that folded into another gives that one, and a row that moved to another page sends the link
     * there with the same scroll key, so the row is still found (an external Intent, a saved Back
     * stack, a search hit). See {@link SettingsTree#redirect}.
     */
    @NonNull
    static String redirectLegacyPage(@NonNull String className, @Nullable String scrollToKey) {
        return SettingsTree.redirect(className, scrollToKey);
    }

    /**
     * The deep-link arguments an Intent carries, or null when it carries none. Read by the
     * initial fragment; nothing else in the Intent reaches it.
     */
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
        // The manifest's theme too: the window is see-through, over the live Home.
        setTheme(R.style.Theme_TermuxApp_SettingsSheet);
        TermuxThemeManager.applyThemeOverlays(this);
        super.onCreate(savedInstanceState);
        suppressWindowMotion();
        registerSettingsStyleCallbacks();
        // The pages stand in a sheet over Home: activity_settings, unchanged, is its body.
        setContentView(R.layout.activity_settings_sheet);
        getLayoutInflater().inflate(R.layout.activity_settings,
            (ViewGroup) findViewById(R.id.settings_sheet_body), true);
        applySettingsSystemBars();
        View list = findViewById(R.id.settings_list);
        mTwoPane = list != null && list.getVisibility() == View.VISIBLE;
        mSheet = new SettingsSheet(findViewById(R.id.settings_sheet_root), mTwoPane,
            SettingsMotion.isInstant(this), savedInstanceState, this::finishUnderSheet);
        mSheet.open(savedInstanceState == null);
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
            if (mTwoPane) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_list, new RootPreferencesFragment()).commit();
            }
            boolean plain = isPlainEntryIntent(intent);
            boolean keepPage = plain && sLastClosedAtMs != SettingsReopenPolicy.NO_CLOSE
                && !SettingsReopenPolicy.shouldReset(sLastClosedAtMs, SystemClock.elapsedRealtime(), false);
            if (!(keepPage && restoreSavedStackIfFresh())) {
                showInitialFragment(buildInitialFragment());
            }
        } else {
            restorePushedScreensBookkeeping(savedInstanceState);
            // The smallest width crossed 600 dp (a fold, a window resize): the restored
            // fragments sit in the other shape's containers, so the stack is rebuilt for this one.
            if (savedInstanceState.getBoolean(STATE_KEY_TWO_PANE, false) != mTwoPane) rebuildForPaneChange();
        }
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        mSheet.setHeader(findViewById(com.termux.shared.R.id.toolbar));
        // Keeps the toolbar title in step with Back, including a Back that the platform's own
        // OnBackPressedDispatcher integration pops without this class hearing about it directly.
        getSupportFragmentManager().addOnBackStackChangedListener(this::onBackStackChanged);
        if (mPushedScreens.isEmpty()) {
            setTitleFromIntent(getIntent());
        } else {
            updateTitleForCurrentStack();
        }
        updateNavigationIcon();
    }

    /**
     * Settings over Home moves itself: the sheet rises and falls, so the window neither slides in
     * nor out (the theme's window animations are empty too, for systems that read only those).
     */
    @SuppressWarnings("deprecation")
    private void suppressWindowMotion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    /** The sheet has gone: the window goes with no motion of its own. */
    @SuppressWarnings("deprecation")
    private void finishUnderSheet() {
        finish();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) overridePendingTransition(0, 0);
    }

    @androidx.annotation.VisibleForTesting
    SettingsSheet sheet() {
        return mSheet;
    }

    /** Closes Settings: the sheet slides away first, then the window goes. */
    private void closeSheet() {
        if (mSheet != null) {
            mSheet.close();
        } else {
            finish();
        }
    }

    /**
     * Where Back closes Settings (the root, or with two panes a section's page alone) the toolbar
     * shows a close mark instead of the arrow.
     */
    private void updateNavigationIcon() {
        androidx.appcompat.app.ActionBar bar = getSupportActionBar();
        if (bar == null) return;
        boolean closes = mTwoPane ? isSectionPageAlone()
            : getSupportFragmentManager().getBackStackEntryCount() == 0;
        if (mNavigationClosesShown != null && mNavigationClosesShown == closes) return;
        mNavigationClosesShown = closes;
        bar.setHomeAsUpIndicator(closes ? R.drawable.ic_symbol_close : 0);
        bar.setHomeActionContentDescription(closes ? R.string.settings_sheet_close : 0);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // The sheet is dragged only from its header; where this finger lands decides.
        if (mSheet != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN)
            mSheet.onTouchDown(ev.getX(), ev.getY());
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The launcher's app-only picture can be set, replaced or ended while this page sits
        // behind it (Appearance opened from here), and Android sends no configuration change for
        // a picture it never got: re-theme here when the colours it gave no longer apply.
        TermuxThemeManager.recreateIfWallpaperColorsMoved(this);
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
        outState.putBoolean(STATE_KEY_TWO_PANE, mTwoPane);
        if (mSheet != null) mSheet.saveState(outState);
    }

    /**
     * The first screen, without motion. With two panes the root is the list already, so where a
     * phone would show the root the detail opens on the first section, Home.
     */
    private void showInitialFragment(@NonNull Fragment initial) {
        if (mTwoPane && initial instanceof RootPreferencesFragment) {
            openSectionPage(SettingsTree.Section.HOME, SettingsMotionPolicy.Move.RESTORE, null);
            return;
        }
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settings, initial, screenTag(0)).commit();
    }

    /**
     * The tag of the screen at {@code position} of the detail: 0 for the base (the root on a phone,
     * a deep-linked page), then one per pushed screen. A screen under the top one is found by it,
     * though the FragmentManager no longer lists it as added.
     */
    @NonNull
    static String screenTag(int position) {
        return "settings_screen_" + position;
    }

    /** Pushes a section's own page as the detail's first screen. */
    private void openSectionPage(@NonNull SettingsTree.Section section, @NonNull SettingsMotionPolicy.Move move,
                                 @Nullable Fragment out) {
        SettingsTree.Screen page = SettingsTree.sectionPage(section);
        pushScreen(loadScreenClass(page.className), page.titleRes, null, null, move, null, out);
    }

    /**
     * Moves a restored stack into this shape's containers: the root into or out of the list, the
     * deep-linked base (if any) kept, every pushed screen replayed on top without motion.
     */
    private void rebuildForPaneChange() {
        FragmentManager fm = getSupportFragmentManager();
        List<SettingsBackStackState.Entry> entries = new ArrayList<>(mPushedScreens);
        mPushedScreens.clear();
        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        Fragment base = fm.findFragmentById(R.id.settings);
        Fragment listed = fm.findFragmentById(R.id.settings_list);
        androidx.fragment.app.FragmentTransaction transaction = fm.beginTransaction();
        Fragment out;
        if (mTwoPane) {
            if (listed == null) transaction.replace(R.id.settings_list, new RootPreferencesFragment());
            if (base instanceof RootPreferencesFragment) {
                transaction.remove(base);
                out = null;
            } else {
                out = base;
            }
        } else {
            if (listed != null) transaction.remove(listed);
            if (base == null) {
                base = new RootPreferencesFragment();
                transaction.replace(R.id.settings, base, screenTag(0));
            }
            out = base;
        }
        transaction.commitNow();
        for (SettingsBackStackState.Entry entry : entries) {
            Class<? extends Fragment> screen;
            try {
                screen = loadScreenClass(entry.className);
            } catch (IllegalStateException e) {
                break;
            }
            Bundle args = null;
            if (entry.place != null || entry.scrollToKey != null) {
                args = new Bundle();
                if (entry.place != null) args.putString(EXTRA_INITIAL_PLACE, entry.place);
                if (entry.scrollToKey != null) args.putString(EXTRA_SCROLL_TO_KEY, entry.scrollToKey);
                SettingsRowRing.markRung(args);
            }
            out = pushScreen(screen, entry.titleResId, entry.titleText, args,
                SettingsMotionPolicy.Move.RESTORE, null, out);
        }
        if (mTwoPane && out == null) openSectionPage(SettingsTree.Section.HOME, SettingsMotionPolicy.Move.RESTORE, null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Opened again while the sheet was going away: it comes back up instead.
        if (mSheet != null) mSheet.reopen();
        if (intent.getBooleanExtra(EXTRA_OPEN_TAI_SETTINGS, false)) {
            intent.putExtra(EXTRA_INITIAL_FRAGMENT,
                "com.termux.app.fragments.settings.termux.TaiPreferencesFragment");
            intent.putExtra(EXTRA_INITIAL_TITLE_RES, R.string.termux_ai_preferences_title);
        }
        boolean plainEntry = isPlainEntryIntent(intent);
        if (plainEntry && !SettingsReopenPolicy.shouldReset(
                sLastClosedAtMs, SystemClock.elapsedRealtime(), false)) {
            // Opened again within three minutes of closing (or still open): keep the page and back
            // stack. Later than that a plain open pops to the root below, before the sheet shows.
            return;
        }
        // A deep link into an open Settings crosses the tree: whatever is on show fades through
        // to the page it names, in one batch, so nothing in between is drawn.
        FragmentManager fm = getSupportFragmentManager();
        if (askAboutUnsavedChanges()) return;
        Fragment base = buildInitialFragment();
        if (mTwoPane && base instanceof RootPreferencesFragment) {
            jumpTo(SettingsTree.path(SettingsTree.sectionPage(SettingsTree.Section.HOME).className), null);
            return;
        }
        // A timed reset is not a navigation the user should see: no fade through the old page.
        boolean instant = SettingsMotion.isInstant(this) || plainEntry;
        Fragment visible = fm.findFragmentById(R.id.settings);
        if (visible != null) SettingsTransitions.leaveByJump(this, visible, instant);
        mPushedScreens.clear();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        SettingsTransitions.arriveByJump(this, base, instant);
        fm.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings, base, screenTag(0))
            .commit();
        setTitleFromIntent(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        long stoppedAtEpochMs = System.currentTimeMillis();
        sLastClosedAtMs = SystemClock.elapsedRealtime();
        if (isFinishing() && (mPushedScreens.isEmpty() || (mTwoPane && isSectionPageAlone()))) {
            // A deliberate exit from the root screen (Back/Up with nothing left to pop): the next
            // open should start fresh at root, not resume a stack that no longer exists.
            getSettingsBackStackPreferences().edit().remove(PREFS_KEY_BACK_STACK_STATE).apply();
        } else {
            // The search screen is a moment, not a place: a reopen lands where it was opened from.
            List<SettingsBackStackState.Entry> kept = new ArrayList<>();
            for (SettingsBackStackState.Entry entry : mPushedScreens) {
                if (!SettingsSearchFragment.class.getName().equals(entry.className)) kept.add(entry);
            }
            SettingsBackStackState state = new SettingsBackStackState(kept, stoppedAtEpochMs);
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

        Fragment out = null;
        if (!mTwoPane) {
            out = new RootPreferencesFragment();
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings, out, screenTag(0))
                .commit();
        }

        // A stack saved before the restructure names pages that moved: replay it in today's tree,
        // stopping short of an editor (a reopen lands on the page that opened it).
        for (SettingsBackStackState.Entry entry : saved.entriesToRestore()) {
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
                // It rang when it was first opened.
                SettingsRowRing.markRung(args);
            }
            // The tree's own title where it knows the page: a saved resource id belongs to the
            // build that saved it.
            SettingsTree.Screen known = SettingsTree.find(entry.className);
            out = pushScreen(screenClass, known != null ? known.titleRes : entry.titleResId,
                known != null ? null : entry.titleText, args, SettingsMotionPolicy.Move.RESTORE, null, out);
        }
        // Left at the root: with two panes the root is the list, and the detail opens on Home.
        if (mTwoPane && out == null) openSectionPage(SettingsTree.Section.HOME, SettingsMotionPolicy.Move.RESTORE, null);
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
        updateNavigationIcon();
        invalidateOptionsMenu();
    }

    /** True when the list and the page stand side by side (smallest width 600 dp). */
    public boolean isTwoPane() {
        return mTwoPane;
    }

    // ---- Search from every screen (F1) ----

    private static final int MENU_SEARCH = 1;

    @Override
    public boolean onCreateOptionsMenu(@NonNull android.view.Menu menu) {
        android.view.MenuItem item = menu.add(android.view.Menu.NONE, MENU_SEARCH, android.view.Menu.NONE,
            R.string.settings_search_hint);
        item.setIcon(R.drawable.ic_symbol_search);
        item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull android.view.Menu menu) {
        android.view.MenuItem item = menu.findItem(MENU_SEARCH);
        if (item != null) item.setVisible(showsSearchAction());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == MENU_SEARCH) {
            openSearch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * The toolbar's search action shows on every screen except where a search box already is:
     * the root (its first row), the search screen itself, and every screen of the two-pane layout,
     * whose list keeps the root's box in view.
     */
    boolean showsSearchAction() {
        if (isTwoPane()) return false;
        FragmentManager fm = getSupportFragmentManager();
        Fragment shown = fm.findFragmentById(R.id.settings);
        if (shown == null || shown instanceof RootPreferencesFragment || shown instanceof SettingsSearchFragment)
            return false;
        // An editor is a task of its own; a search from it would jump away from its edits.
        SettingsTree.Screen screen = SettingsTree.find(shown.getClass().getName());
        if (screen != null && screen.editor) return false;
        if (shown instanceof SettingsUnsavedChanges && ((SettingsUnsavedChanges) shown).hasUnsavedChanges())
            return false;
        // With nothing to pop the FragmentManager's own Back handler is off, so an enabled one is
        // the screen's.
        return !(fm.getBackStackEntryCount() == 0 && getOnBackPressedDispatcher().hasEnabledCallbacks());
    }

    /**
     * Before a jump away from the screen on show: when it holds unsaved edits, Back goes to it
     * instead (its own handler asks to discard them) and the jump is dropped. True when it was.
     */
    private boolean askAboutUnsavedChanges() {
        Fragment shown = getSupportFragmentManager().findFragmentById(R.id.settings);
        if (!(shown instanceof SettingsUnsavedChanges) || !((SettingsUnsavedChanges) shown).hasUnsavedChanges())
            return false;
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    /** Opens the search screen, its placeholder naming the section on screen. */
    void openSearch() {
        Fragment shown = getSupportFragmentManager().findFragmentById(R.id.settings);
        SettingsTree.Section scope = shown == null ? null : SettingsTree.sectionOf(shown.getClass().getName());
        pushScreen(SettingsSearchFragment.class, R.string.settings_search_hint, null,
            SettingsSearchFragment.arguments(scope), SettingsMotionPolicy.Move.JUMP, null, shown);
    }

    /**
     * A search result: its page opens in the tree (the path from its section down, so Back walks
     * up), scrolled to the row, which rings once. Whatever was open, the search screen included,
     * gives way to it.
     */
    @Override
    public void openSearchResult(@NonNull String screenClass, @Nullable String key) {
        List<SettingsTree.Screen> path = SettingsTree.path(screenClass);
        if (path.isEmpty()) return;
        hideKeyboard();
        if (path.get(path.size() - 1).door) {
            // A row the Appearance editor holds: the editor opens over the launcher on its card.
            startActivity(com.termux.app.fragments.settings.termux.SettingsEditorDoors.editorIntent(
                this, com.termux.app.fragments.settings.termux.SettingsEditorDoors.cardForSearchKey(key)));
            return;
        }
        jumpTo(path, key);
    }

    /**
     * Replaces whatever is open with {@code path} (a section's page down to one of its screens),
     * the last one scrolled to {@code key}. The screen on show fades through to the last one; the
     * screens between are pushed paired with shared-axis motion, so Back walks up the tree as if
     * the reader had come down it. One batch: nothing in between is drawn.
     */
    private void jumpTo(@NonNull List<SettingsTree.Screen> path, @Nullable String key) {
        if (askAboutUnsavedChanges()) return;
        FragmentManager fm = getSupportFragmentManager();
        boolean instant = SettingsMotion.isInstant(this);
        Fragment visible = fm.findFragmentById(R.id.settings);
        // The base stays under the new stack: paired with its first screen, it slides back in
        // when Back reaches it.
        Fragment out = fm.getBackStackEntryCount() == 0 ? visible : fm.findFragmentByTag(screenTag(0));
        mPushedScreens.clear();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        for (int i = 0; i < path.size(); i++) {
            SettingsTree.Screen screen = path.get(i);
            Bundle args = null;
            if (i == path.size() - 1 && key != null) {
                args = new Bundle();
                args.putString(EXTRA_SCROLL_TO_KEY, key);
            }
            out = pushScreen(loadScreenClass(screen.className), screen.titleRes, null, args,
                SettingsMotionPolicy.Move.PUSH, null, out);
        }
        // After the pairing above, which may have given the base (when it is the visible one)
        // a slide: the screen on show fades through.
        if (visible != null) SettingsTransitions.leaveByJump(this, visible, instant);
        if (out != null) SettingsTransitions.arriveByJump(this, out, instant);
        fm.executePendingTransactions();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private Class<? extends Fragment> loadScreenClass(@NonNull String className) {
        try {
            return (Class<? extends Fragment>) getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            // SettingsTreeTest holds every name in the tree to an existing class.
            throw new IllegalStateException(className, e);
        }
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (ime != null && focus != null) ime.hideSoftInputFromWindow(focus.getWindowToken(), 0);
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
        pushScreen(fragmentClass, titleResId, null, args, SettingsMotionPolicy.Move.PUSH, null,
            getSupportFragmentManager().findFragmentById(R.id.settings));
    }

    /**
     * Pushes one screen. {@code out} is the screen it replaces (the one on show, or the previous
     * one of a batch); {@code fromRow} the row it was opened from, when on screen. The motion is
     * {@link SettingsMotionPolicy}'s choice for {@code move}. Returns the pushed fragment.
     */
    @NonNull
    private Fragment pushScreen(@NonNull Class<? extends Fragment> fragmentClass, int titleResId,
                                @Nullable String titleText, @Nullable Bundle args,
                                @NonNull SettingsMotionPolicy.Move move, @Nullable View fromRow,
                                @Nullable Fragment out) {
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
        SettingsMotionPolicy.Kind kind = SettingsMotionPolicy.choose(move,
            out == null ? null : out.getClass().getName(), className, fromRow != null,
            SettingsMotion.isInstant(this));
        // Reordering lets a batch (a jump, a restored stack) run as one move from the screen on
        // show to the last one, skipping the screens in between.
        androidx.fragment.app.FragmentTransaction transaction =
            getSupportFragmentManager().beginTransaction().setReorderingAllowed(true);
        SettingsTransitions.apply(this, kind, out, fragment, transaction, fromRow);
        // Named by its position so it always pops exactly one entry at a time, staying aligned
        // with mPushedScreens (see onBackStackChanged).
        transaction
            .replace(R.id.settings, fragment, screenTag(mPushedScreens.size()))
            .addToBackStack(String.valueOf(mPushedScreens.size()))
            .commit();
        updateTitleForCurrentStack();
        return fragment;
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
                    // The toolbar's search action depends on which screen is showing.
                    invalidateOptionsMenu();
                    // An editor growing out of its row takes the row's transition name.
                    SettingsTransitions.nameContainer(fragment, view);
                    if (!(fragment instanceof PreferenceFragmentCompat)) return;
                    PreferenceFragmentCompat preferenceFragment = (PreferenceFragmentCompat) fragment;
                    // The page moves as one: the transition targets its root rather than the
                    // rows it finds. An arriving list has no rows yet when the move captures its
                    // views (they come with its first layout), so they escaped the fade-through
                    // and drew at full strength over the leaving page's last rows.
                    if (view instanceof ViewGroup) ViewGroupCompat.setTransitionGroup((ViewGroup) view, true);
                    // Root rows rely on the category header hairline, sub-screens on theirs; a
                    // list divider creates the unwanted double line between sections.
                    preferenceFragment.setDivider(null);
                    preferenceFragment.setDividerHeight(0);
                    // Each section's rows share one rounded tonal card instead.
                    SettingsLayoutUtils.applyGroups(preferenceFragment);
                    // A page opened on a row (a search result, a link with a scroll key) lands on
                    // it and rings it once.
                    SettingsRowRing.ringIfAsked(preferenceFragment);
                }
            }, true);
    }

    /**
     * Edge to edge over Home, the sheet taking the insets ({@link SettingsSheet}). The status bar
     * stands over Home, so it stays clear and its icons keep the theme's look, which is Home's
     * too; the navigation bar stands on the sheet, so its icons follow the sheet's surface.
     */
    private void applySettingsSystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.setNavigationBarContrastEnforced(false);
        int surface = ThemeUtils.getSystemAttrColor(this, com.termux.shared.R.attr.termuxColorSurfaceBase, Color.BLACK);
        boolean lightSurface = ColorUtils.calculateLuminance(surface) > 0.5;
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window, window.getDecorView());
        bars.setAppearanceLightNavigationBars(lightSurface);
        // Home in full screen hides the system bars; Settings over it hides them too, or they
        // come back while it is open and Home lays out again for insets it then loses.
        com.termux.shared.termux.settings.properties.TermuxAppSharedProperties properties =
            com.termux.shared.termux.settings.properties.TermuxAppSharedProperties.getProperties();
        if (properties != null && properties.isUsingFullScreen()) {
            bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
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
     * Back with nothing to pop closes Settings itself: the sheet slides away, then the window
     * finishes ({@link SettingsSheet#close}). From Android 12 a Back at the root of a
     * task is handed to the system, which finishes only after it calls back; this activity is the
     * root of its own task (singleTask, own affinity), and on some systems that call never comes,
     * so Back and Up did nothing on the first screen. A screen pushed on top, or a fragment's own
     * Back handler (the extra-keys editor's unsaved edits), still goes through the dispatcher.
     */
    @Override
    public void onBackPressed() {
        if (SettingsMotion.isInstant(this)) cutBackMotion();
        // With two panes a section's page is the detail's first screen, the list beside it: Back
        // there leaves Settings rather than emptying the detail.
        if (mTwoPane && isSectionPageAlone()) {
            closeSheet();
            return;
        }
        if (getOnBackPressedDispatcher().hasEnabledCallbacks()) {
            super.onBackPressed();
            return;
        }
        closeSheet();
    }

    /** The screen leaving and the one coming back move with no transition. */
    private void cutBackMotion() {
        FragmentManager fm = getSupportFragmentManager();
        int count = fm.getBackStackEntryCount();
        if (count == 0) return;
        Fragment top = fm.findFragmentById(R.id.settings);
        if (top != null) {
            top.setReturnTransition(null);
            top.setSharedElementReturnTransition(null);
        }
        Fragment below = fm.findFragmentByTag(screenTag(count - 1));
        if (below != null) below.setReenterTransition(null);
    }

    private boolean isSectionPageAlone() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() != 1) return false;
        Fragment top = fm.findFragmentById(R.id.settings);
        SettingsTree.Screen screen = top == null ? null : SettingsTree.find(top.getClass().getName());
        return screen != null && screen.isSectionPage();
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
            if (mTwoPane && caller instanceof RootPreferencesFragment) {
                // The list picks the section the detail shows: a jump across the tree, unless
                // that section is already open on its own page.
                Fragment shown = getSupportFragmentManager().findFragmentById(R.id.settings);
                List<SettingsTree.Screen> path = SettingsTree.path(fragmentClassName);
                boolean alreadyOpen = shown != null && isSectionPageAlone()
                    && SettingsTree.canonical(shown.getClass().getName()).equals(SettingsTree.canonical(fragmentClassName));
                if (!path.isEmpty() && !alreadyOpen) jumpTo(path, null);
                return true;
            }
            CharSequence title = preference.getTitle();
            Bundle extras = preference.getExtras();
            pushScreen(screenClass, 0, title == null ? null : title.toString(),
                extras.isEmpty() ? null : new Bundle(extras), SettingsMotionPolicy.Move.PUSH,
                SettingsTransitions.rowView(caller, preference), caller);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {

        @Nullable private SettingsSearchResults mSearch;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null)
                return;
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            SettingsLayoutUtils.applyRootLayout(this);
            // A build made without the X server has no Linux apps to set up.
            Preference linuxApps = findPreference(SettingsTree.Section.LINUX_APPS.rootKey);
            if (linuxApps != null && !com.termux.BuildConfig.X11_SERVER) linuxApps.setVisible(false);
            // Appearance is the editor itself, over the live launcher, not a page of rows.
            Preference appearance = findPreference(SettingsTree.Section.APPEARANCE.rootKey);
            if (appearance != null) appearance.setOnPreferenceClickListener(row -> {
                com.termux.app.fragments.settings.termux.SettingsEditorDoors.openEditor(this, row, null);
                return true;
            });
            SettingsSearchPreference search = findPreference("settings_search");
            // The root searches everything: no section is ranked first.
            if (search != null) mSearch = SettingsSearchResults.attach(this, search, null);
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getActivity() instanceof SettingsActivity && !((SettingsActivity) getActivity()).isTwoPane()) {
                getActivity().setTitle(R.string.title_activity_termux_settings);
            }
        }

        @Override
        public void onDestroyView() {
            // A result opened from here replaced the root: Back returns to the root, not the search.
            if (mSearch != null) mSearch.onViewDestroyed();
            super.onDestroyView();
        }

        @androidx.annotation.VisibleForTesting
        @Nullable
        public SettingsSearchResults searchResults() {
            return mSearch;
        }
    }
}
