package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.launcher.az.AzFloatingStripPolicy;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Item 02 of {@code project-docs/landscape-round/SPEC.md}: the reported profile is a landscape
 * place with the pinned apps on a <b>left rail</b>, the alphabets bar on the <b>bottom</b>, and
 * <b>nothing pinned</b> — and on it, a finger on B or C was reported to produce no visible app
 * choices at all.
 *
 * <p>The rail is on a side edge, so {@code PlaceChromePolicy.azRidesAppsRow} is false and
 * {@code AzPreviewTargetPolicy.resolve} sends the matches to the {@code FLOATING_STRIP}
 * ({@code TermuxActivity.syncAzStandaloneStrip}, :7535) rather than into the apps row. Nothing
 * along that path had a test: no case anywhere in {@code app/src/test} drives
 * {@code handleAzGestureScrub} on a standalone index, and every case that drives the scrub at all
 * ({@code SuggestionBarPageCommitTest}) seeds pinned items first and puts the row on the bottom.
 *
 * <p>So this is the whole route, in one arrangement, at the reported size: the catalogue is
 * loaded, the letters are wired, a real {@code ACTION_DOWN} goes through the real window onto a
 * real letter, and what is asserted is the three links that make matches visible — the candidate
 * list, the band's geometry, and the layer that draws it being on screen.
 *
 * <p><b>Verdict: the reported failure did not reproduce here.</b> With the rail empty, both
 * reviewed letters produce candidates, a band and a visible layer, and so does the same
 * arrangement with the apps put away entirely. Every case below therefore passes; they are the
 * ground covered, not a red reproduction. What they did pin down is two facts the report is
 * consistent with and neither of which is a broken results path: the standalone strip is
 * dismissed the moment the finger lifts, unlike the apps row's matches, which outlive the lift
 * until a timeout; and with nothing pinned the rail is shown to the policy and absent from the
 * screen, taking the app drawer's only pull with it.
 *
 * <p><b>Reproduction phase only (decision D5).</b> Nothing here is a fix.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class,
    qualifiers = "w1300dp-h600dp-land-mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class AzStandaloneStripEmptyPinnedTest {

    /** Waydroid in the review: 1300x600 at mdpi, so a dp is a pixel and the numbers read plainly. */
    private static final int SCREEN_WIDTH = 1300;
    private static final int SCREEN_HEIGHT = 600;

    /** The two letters the reviewer touched. Both have apps in the catalogue below. */
    private static final char FIRST_LETTER = 'B';
    private static final char SECOND_LETTER = 'C';
    private static final int APPS_PER_LETTER = 3;

    private Context context;
    private TermuxActivity screen;
    private FrameLayout window;
    private ActivityController<Activity> activity;

    @Before
    public void setUp() {
        setDurationScale(1f);
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        assertEquals("the fixture must be the reviewed screen width",
            SCREEN_WIDTH, context.getResources().getDisplayMetrics().widthPixels);
        assertEquals("the fixture must be the reviewed screen height",
            SCREEN_HEIGHT, context.getResources().getDisplayMetrics().heightPixels);
    }

    @After
    public void tearDown() {
        if (activity != null) activity.pause().stop().destroy();
        setDurationScale(0f);
    }

    // ------------------------------------------------------------------ the reported arrangement

    /**
     * The miniature in {@code 27-home-layout.png}: status bar on the top edge, the pinned apps as
     * a rail down the left, the alphabets bar along the bottom. The extra keys are put away, which
     * is what the reviewed Home place shows.
     */
    private static PlaceLayout railLeftLettersBottom() {
        return lettersBottomWithApps(new Slot(false, PlaceLayout.Edge.LEFT, 0));
    }

    /** The other way the index stands alone: the pinned apps put away altogether. */
    private static PlaceLayout appsHiddenLettersBottom() {
        return lettersBottomWithApps(Slot.hiddenFrom(PlaceLayout.Edge.LEFT, 0));
    }

    private static PlaceLayout lettersBottomWithApps(Slot apps) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(PlaceLayout.Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, apps);
        slots.put(Element.AZ, new Slot(false, PlaceLayout.Edge.BOTTOM, 0));
        slots.put(Element.EXTRA_KEYS, Slot.hiddenFrom(PlaceLayout.Edge.BOTTOM, 1));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    // ------------------------------------------------------------------------------- the reports

    /**
     * The reported configuration, exactly: the rail empty. A finger down on B must produce
     * matches, a band to put them in, and a layer on screen drawing it.
     */
    @Test
    public void aFingerOnALetterWithAnEmptyRailShowsTheMatches() {
        SuggestionBarView bar = standaloneIndex(0);
        AzScrubRowView letters = wireTheScrub();
        assertTrue("the arrangement must take the standalone strip path",
            (Boolean) ReflectionHelpers.callInstanceMethod(screen, "isAzIndexStandalone"));

        touchLetter(letters, FIRST_LETTER);

        assertMatchesAreOnScreen(bar, FIRST_LETTER);
    }

    /** The same on the second letter the reviewer touched. */
    @Test
    public void aFingerOnTheSecondLetterWithAnEmptyRailShowsTheMatches() {
        SuggestionBarView bar = standaloneIndex(0);
        AzScrubRowView letters = wireTheScrub();

        touchLetter(letters, SECOND_LETTER);

        assertMatchesAreOnScreen(bar, SECOND_LETTER);
    }

    /**
     * The control: the identical arrangement with the rail carrying icons — the state the A–Z
     * standalone work was verified in. Whatever the empty case does, this one is the baseline it
     * has to be compared against, so a failure above cannot be blamed on the fixture.
     */
    @Test
    public void aFingerOnALetterWithAStockedRailShowsTheMatches() {
        SuggestionBarView bar = standaloneIndex(6);
        AzScrubRowView letters = wireTheScrub();
        assertTrue("the rail must be carrying icons for this to be the control",
            bar.hasPinnedItems());

        touchLetter(letters, FIRST_LETTER);

        assertMatchesAreOnScreen(bar, FIRST_LETTER);
    }

    /**
     * The other arrangement that stands the index alone, in case the reviewed profile's stored
     * slots are not what its miniature drew: the pinned apps put away rather than railed.
     */
    @Test
    public void aFingerOnALetterWithTheAppsPutAwayShowsTheMatches() {
        SuggestionBarView bar = standaloneIndex(0, appsHiddenLettersBottom());
        AzScrubRowView letters = wireTheScrub();

        touchLetter(letters, FIRST_LETTER);

        assertMatchesAreOnScreen(bar, FIRST_LETTER);
    }

    /**
     * What a screenshot taken after the gesture sees, which is the difference between the two
     * paths rather than a defect in either.
     *
     * <p>The floating strip lives only while the finger is down: the release runs
     * {@code TermuxActivity.resetAzGestureState} (:8033), which calls {@code clearAzStandaloneStrip}
     * (:8039) unconditionally and {@code clearDrag()} on every FX layer. The apps-row path does the
     * opposite — the matches stay in the row after the lift until the row's own preview timeout
     * puts them away, which is what {@code SuggestionBarPageCommitTest}'s eight-second idle in
     * {@code aSecondScrubOfTheSameLetterFiltersTheRowAgain} is waiting for.
     *
     * <p>So on the reviewed arrangement a tap leaves nothing behind by design, while on a bottom
     * apps row the same tap leaves matches standing for seconds. An {@code adb}-driven review that
     * taps and then screenshots sees "no visible app choices" on one and choices on the other,
     * with no code path having failed. Recorded here, not fixed (decision D5).
     */
    @Test
    public void theStripIsGoneTheMomentTheFingerLifts() {
        SuggestionBarView bar = standaloneIndex(0);
        AzScrubRowView letters = wireTheScrub();
        touchLetter(letters, FIRST_LETTER);
        assertMatchesAreOnScreen(bar, FIRST_LETTER);

        liftFinger(letters, FIRST_LETTER);

        assertNull("the band outlived the finger",
            ReflectionHelpers.getField(screen, "mAzStrip"));
        assertEquals("the layer that draws the strip is still on screen after the lift",
            View.GONE, screen.findViewById(R.id.apps_bar_az_label_overlay).getVisibility());
    }

    /**
     * The one thing in item 02 that is confirmed rather than reported: "is the apps row shown?"
     * has two answers on this arrangement, and they disagree.
     *
     * <p>{@code PlaceChromePolicy.appsShown} is {@code EdgeStackPolicy.isShown} and nothing else,
     * so it is true whenever the slot is not hidden — that is what the Layout miniature draws, and
     * what {@code AzPreviewTargetPolicy} reads. {@code TermuxActivity.isDockRailShown} (:9376–9380)
     * also demands {@code hasPinnedItems()}, and {@code syncPinnedAppsHost} (:9452–9469) keeps the
     * rail's host away on the same condition. With nothing pinned the miniature therefore draws a
     * rail the place does not stand — which is exactly the mismatch the review reported — and the
     * app drawer's only pull on this arrangement ({@code mDockRailDrawerPullListener}, :9387) rides
     * a host that is not there. Recorded, not fixed (decision D5).
     */
    @Test
    public void anEmptyRailIsShownToThePolicyAndNotToTheScreen() {
        standaloneIndex(0);

        assertTrue("the miniature's answer: the rail is configured",
            PlaceChromePolicy.appsShown(railLeftLettersBottom()));
        assertTrue("and it is a rail, not a row",
            PlaceChromePolicy.appsRailShown(railLeftLettersBottom()));
        assertFalse("the screen's answer: nothing stands there",
            (Boolean) ReflectionHelpers.callInstanceMethod(screen, "isDockRailShown"));
        assertEquals("so the rail's host — and the drawer pull on it — is away",
            View.GONE, screen.findViewById(R.id.place_apps_bar_host).getVisibility());
    }

    /** The three links between a held letter and matches a thumb can see. */
    private void assertMatchesAreOnScreen(SuggestionBarView bar, char letter) {
        List<LauncherAppEntry> matches = bar.azStripVisibleEntries();
        assertFalse("the letter '" + letter + "' produced no candidates at all", matches.isEmpty());
        for (LauncherAppEntry entry : matches)
            assertTrue("a candidate that is not the letter's: " + entry.label,
                Character.toUpperCase(entry.label.charAt(0)) == letter);

        AzFloatingStripPolicy.Strip strip = ReflectionHelpers.getField(screen, "mAzStrip");
        assertNotNull("the matches were never laid out into a band", strip);
        assertFalse("the band has no slots", strip.isEmpty());
        assertTrue("the band is off screen: " + strip.top + ".." + strip.bottom,
            strip.bottom > 0f && strip.top < SCREEN_HEIGHT);

        View fx = screen.findViewById(R.id.apps_bar_az_label_overlay);
        assertNotNull(fx);
        assertEquals("the layer that draws the strip is not on screen",
            View.VISIBLE, fx.getVisibility());
    }

    // ---------------------------------------------------------------------------- the fixture

    /**
     * The screen with the reported arrangement applied and {@code pinned} apps on the rail. Built
     * the way {@code SuggestionBarPageCommitTest} builds its dock: the real
     * {@code activity_termux.xml}, the real store, the real edge stacks, stood inside a window so
     * everything the views post actually runs.
     */
    private SuggestionBarView standaloneIndex(int pinned) {
        return standaloneIndex(pinned, railLeftLettersBottom());
    }

    private SuggestionBarView standaloneIndex(int pinned, PlaceLayout arrangement) {
        screen = Robolectric.buildActivity(TermuxActivity.class).get();
        screen.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(screen, false);
        assertNotNull(preferences);
        ReflectionHelpers.setField(screen, "mPreferences", preferences);

        SuggestionBarView bar = new SuggestionBarView(screen, null);
        ViewGroup plank = screen.findViewById(R.id.apps_bar_plank_layer);
        plank.addView(bar, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ReflectionHelpers.setField(screen, "mSuggestionBarView", bar);

        bar.setAppDataProvider(loadedCatalogue());
        bar.reloadAllApps();
        List<LauncherAppEntry> catalogue = ReflectionHelpers.getField(bar, "allApps");
        assertNotNull(catalogue);
        assertTrue("the catalogue reached the row", catalogue.size() >= 2 * APPS_PER_LETTER);

        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < pinned && i < catalogue.size(); i++)
            items.add(new PinnedAppItem(catalogue.get(i).appRef));
        LauncherConfigRepository.getInstance(screen).savePinnedItems(items);
        ReflectionHelpers.setField(bar, "pinnedItems", items);
        bar.setMaxButtonCount(3);
        assertEquals("the rail must hold exactly what the case asked for",
            pinned > 0, bar.hasPinnedItems());

        PlaceLayout layout = arrangement;
        PlaceLayoutStore store = ReflectionHelpers.callInstanceMethod(screen, "placeLayoutStore");
        assertNotNull(store);
        for (PaneWallPage place : PaneWallPage.values())
            for (PlaceOrientation orientation : PlaceOrientation.values())
                for (Element element : Element.values())
                    store.setSlot(place, orientation, element, layout.slot(element));
        assertFalse("the arrangement must not let the index ride the row",
            PlaceChromePolicy.azRidesAppsRow(layout));
        screen.applyEdgeStacks(layout);
        screen.syncPinnedAppsHost(layout);
        screen.applyDockLayout(screen.dockLayoutFor(layout));
        showBand(R.id.apps_bar_az_row);
        showBand(R.id.apps_bar_viewpager);
        showBand(R.id.apps_bar_indicator_band);
        screen.findViewById(R.id.accessory_stack_container).setVisibility(View.VISIBLE);

        activity = Robolectric.buildActivity(Activity.class).setup();
        window = new FrameLayout(activity.get());
        activity.get().setContentView(window);
        window.addView(screen.getWindow().getDecorView(),
            new FrameLayout.LayoutParams(SCREEN_WIDTH, SCREEN_HEIGHT));
        layoutWindow();
        bar.reload();
        idle();
        return bar;
    }

    /** The letters view, wired to the screen's own scrub callback the way onCreate wires it. */
    private AzScrubRowView wireTheScrub() {
        ReflectionHelpers.callInstanceMethod(screen, "setSuggestionBarView");
        ReflectionHelpers.callInstanceMethod(screen, "syncAzBarHosts");
        ReflectionHelpers.callInstanceMethod(screen, "syncAzScrubLettersAndTint");
        screen.mSuggestionBarView.setMaxButtonCount(3);
        screen.mSuggestionBarView.reload();
        layoutWindow();
        idle();
        AzScrubRowView letters = ReflectionHelpers.getField(screen, "mAzScrubRowView");
        assertNotNull("the scrub must be wired to a letters view", letters);
        assertTrue("the letters must be laid out to be scrubbed: "
                + letters.getWidth() + "x" + letters.getHeight(),
            letters.getWidth() > 0 && letters.getHeight() > 0);
        List<Character> visible = visibleLetters(letters);
        assertTrue("both reviewed letters must be on the bar: " + visible,
            visible.contains(FIRST_LETTER) && visible.contains(SECOND_LETTER));
        return letters;
    }

    /** The letters the bar is actually showing, which is the catalogue's own set. */
    private static List<Character> visibleLetters(AzScrubRowView letters) {
        char[] shown = ReflectionHelpers.getField(letters, "visibleLetters");
        List<Character> visible = new ArrayList<>(shown.length);
        for (char c : shown) visible.add(c);
        return visible;
    }

    /**
     * A finger down on one letter, through the window the way a thumb arrives. Down only: the
     * standalone strip lives while the finger is down and is dismissed on release, so the state
     * under test is the one a held thumb sees.
     */
    private void touchLetter(AzScrubRowView letters, char letter) {
        int index = onLetter(letters, letter, MotionEvent.ACTION_DOWN);
        assertEquals("the finger must be on the letter the case is about",
            index, (int) (Integer) ReflectionHelpers.getField(letters, "activeLetterIndex"));
    }

    /** The finger off the same letter, which is where the standalone strip is dismissed. */
    private void liftFinger(AzScrubRowView letters, char letter) {
        onLetter(letters, letter, MotionEvent.ACTION_UP);
    }

    private int onLetter(AzScrubRowView letters, char letter, int action) {
        List<Character> visible = visibleLetters(letters);
        int index = visible.indexOf(letter);
        assertTrue("the letter must be on the bar", index >= 0);
        float along = (index + 0.5f) / visible.size();
        int[] at = new int[2];
        letters.getLocationOnScreen(at);
        dispatchOn(screen.getWindow().getDecorView(), action,
            at[0] + (letters.getWidth() * along), at[1] + (letters.getHeight() * 0.5f));
        idle();
        return index;
    }

    private void layoutWindow() {
        window.measure(
            View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT, View.MeasureSpec.EXACTLY));
        window.layout(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    }

    private void showBand(int viewId) {
        View band = screen.findViewById(viewId);
        assertNotNull(band);
        band.setVisibility(View.VISIBLE);
    }

    private void dispatchOn(View view, int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    /** A catalogue with apps under both reviewed letters and nothing else. */
    private LauncherAppDataProvider loadedCatalogue() {
        ShadowPackageManager packageManager = Shadows.shadowOf(context.getPackageManager());
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (char letter : new char[] {FIRST_LETTER, SECOND_LETTER}) {
            for (int i = 0; i < APPS_PER_LETTER; i++) {
                ResolveInfo resolveInfo = new ResolveInfo();
                resolveInfo.activityInfo = new ActivityInfo();
                resolveInfo.activityInfo.packageName = "com.example." + letter + i;
                resolveInfo.activityInfo.name = "Main" + letter + i;
                resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
                resolveInfo.activityInfo.nonLocalizedLabel = letter + "ravo " + i;
                packageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
            }
        }
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        provider.invalidate();
        provider.getAllAppsBlocking();
        assertTrue("the catalogue must be loaded", provider.hasLoadedApps());
        assertFalse("the first letter must have matches",
            provider.getAppsForLetter(FIRST_LETTER).isEmpty());
        assertFalse("the second letter must have matches",
            provider.getAppsForLetter(SECOND_LETTER).isEmpty());
        return provider;
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
    }

    private static void setDurationScale(float scale) {
        ReflectionHelpers.setStaticField(ValueAnimator.class, "sDurationScale", scale);
    }
}
