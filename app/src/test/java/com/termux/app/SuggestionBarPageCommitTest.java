package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import android.content.res.Configuration;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.launcher.paging.PageTickStripView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.Slot;
import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A qualified page swipe is a decision, and the slide is only how it is shown. Interrupting the
 * slide — a new touch on the row, a reset from the host — must therefore land on the page the
 * swipe asked for, not back on the page it came from. Interrupting without idling first is what
 * makes these deterministic: the settle is still on its first frame, exactly where a second
 * finger or a host reset lands in practice.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class SuggestionBarPageCommitTest {

    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 160;
    private static final float TRAVEL = 300f;
    /** The letter Robolectric's own label for every test activity files under. */
    private static final char SCRUB_LETTER = 'T';

    private Context context;
    private SuggestionBarView row;
    private ActivityController<Activity> activity;

    @Before
    public void setUp() {
        // Robolectric's default scale of zero would finish every settle inside the frame that
        // starts it, and an animation that cannot be interrupted cannot show this defect.
        setDurationScale(1f);
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        context.getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
        row = new SuggestionBarView(context, null);
        row.addView(new View(context), new ViewGroup.LayoutParams(ROW_WIDTH / 2, ROW_HEIGHT));
        // In a window, not free-floating: a detached view queues everything it posts until it is
        // attached, so every deferred render and every timeout the row relies on would sit unrun,
        // and the gates under test would look stuck for a reason the phone never has.
        activity = Robolectric.buildActivity(Activity.class).setup();
        FrameLayout host = new FrameLayout(activity.get());
        activity.get().setContentView(host);
        host.addView(row, new ViewGroup.LayoutParams(ROW_WIDTH, ROW_HEIGHT));
        row.measure(
            View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW_HEIGHT, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, ROW_WIDTH, ROW_HEIGHT);
        assertTrue("the row must be in a window", row.isAttachedToWindow());
        assertEquals(ROW_HEIGHT, row.getHeight());

        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        }
        ReflectionHelpers.setField(row, "pinnedItems", new ArrayList<>(items));
        ReflectionHelpers.setField(row, "maxButtonCount", 3);
        ReflectionHelpers.setField(row, "pinnedItemsPerPage", 3);
    }

    @After
    public void tearDown() {
        if (activity != null) activity.pause().stop().destroy();
        setDurationScale(0f);
    }

    @Test
    public void anUninterruptedSwipeLandsOnTheNextPage() {
        swipeLeft();
        idle();

        assertEquals(1, page());
    }

    @Test
    public void aTouchThatInterruptsTheSettleStillLandsOnTheSwipedPage() {
        swipeLeft();
        // Mid-settle, without idling: the slide is running and the page is not committed yet.
        // The settle has its own field, apart from the drag-back rebound that commits nothing.
        assertNotNull(ReflectionHelpers.getField(row, "swipePreviewSettleAnimator"));
        assertNull(ReflectionHelpers.getField(row, "swipePreviewReboundAnimator"));
        assertEquals(0, page());

        dispatch(MotionEvent.ACTION_DOWN, 100f, 80f);

        assertEquals(1, page());
    }

    @Test
    public void aHostResetThatInterruptsTheSettleStillLandsOnTheSwipedPage() {
        swipeLeft();
        assertEquals(0, page());

        row.resetTransientVisualState();

        assertEquals(1, page());
    }

    /**
     * The dock hands the row the band it was given, and that band carries the page ticks' own
     * strip as well as the icons — so the hint can ask for a height this row is never laid out at.
     * The gate that waits for it is anti-flicker for the first frame, never a mute switch: a page
     * swipe under an impossible hint must still land, render and draw.
     */
    @Test
    public void aSwipeUnderAnImpossibleHeightHintStillRendersAndDraws() {
        row.setDockRowHeightHintPx(ROW_HEIGHT + 40);
        row.reload();
        List<View> beforeSwipe = children();

        swipeLeft();
        idle();

        assertEquals(1, page());
        assertNotEquals("the committed page must actually be rendered", beforeSwipe, children());
        assertTrue("the row must hold something", row.getChildCount() > 0);
        assertTrue("the hint must stop deciding once it has had its say",
            ReflectionHelpers.callInstanceMethod(row, "hasStableRenderBounds"));
        assertFalse("draw suppression must not outlive the swipe", suppressed());
        assertFalse("the drag is over", dragging());
        assertEquals(0f, row.getTranslationX(), 0.01f);
        assertEquals(0f, offset(), 0.01f);
    }

    /**
     * The A-Z scrub remembers the page it put up so a held letter does not re-render every frame.
     * A page it was turned away from is not a page it put up — remembering one silenced every
     * later scrub of the same letter, because the row "already had" matches it had never drawn.
     */
    @Test
    public void aScrubThatCouldNotRenderDoesNotSilenceTheNextOne() throws Exception {
        row.setAppDataProvider(loadedCatalogue());
        row.setDockRowHeightHintPx(ROW_HEIGHT + 40);

        row.previewAzLetter(SCRUB_LETTER, 0, false);
        List<View> turnedAway = children();

        idle();
        row.previewAzLetter(SCRUB_LETTER, 0, false);

        assertNotEquals("the repeat scrub must render", turnedAway, children());
    }

    /**
     * A settle cut short is still a settle finished: the row back on a whole page, untranslated,
     * with no neighbouring page left staged behind it and the ticks told where it landed. Cancelled
     * straight on the animator, which is what a host that tears the slide down does — it used to
     * commit the page and walk away from everything the last frame would have tidied, and get away
     * with it only because the commit's own re-render tidied up after it. Under a height hint the
     * row can never meet there is no re-render to lean on.
     */
    @Test
    public void aSettleCutShortLeavesNoHalfSlideBehind() {
        PageTickStripView ticks = new PageTickStripView(context);
        row.setPageIndicator(ticks);
        row.setDockRowHeightHintPx(ROW_HEIGHT + 40);
        row.reload();

        swipeLeft();
        ValueAnimator settle = ReflectionHelpers.getField(row, "swipePreviewSettleAnimator");
        assertNotNull(settle);
        assertNotEquals("the slide is mid-flight", 0f, offset(), 0.01f);

        settle.cancel();

        assertEquals(1, page());
        assertFalse("the drag is over", dragging());
        assertEquals(0f, offset(), 0.01f);
        assertEquals(0f, row.getTranslationX(), 0.01f);
        assertEquals(1f, ticks.getPagePosition(), 0.01f);
        assertTrue("the row has pages to count", ticks.getPageCount() > 1);
    }

    // ----------------------------------------- the row as one band of the developer's dock stack

    /** The phone in the report: 1080x2412, portrait, everything on the bottom edge. */
    private static final int PHONE_WIDTH = 1080;
    private static final int PHONE_HEIGHT = 2412;

    /**
     * The arrangement the ghost swipe was reported on: extra keys, apps row and letters all on the
     * bottom edge in that order reading down, with the status bar innermost on its own sheet above
     * them. Every band is the one the real {@code activity_termux.xml} and {@code DockLayoutPolicy}
     * give it, so the row is handed the height and the hint the arrangement actually produces.
     */
    private SuggestionBarView rowInTheDockStack() {
        TermuxActivity screen = Robolectric.buildActivity(TermuxActivity.class).get();
        screen.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences =
            TermuxAppSharedPreferences.build(screen, false);
        assertNotNull(preferences);
        ReflectionHelpers.setField(screen, "mPreferences", preferences);

        SuggestionBarView bar = new SuggestionBarView(screen, null);
        ViewGroup plank = screen.findViewById(R.id.apps_bar_plank_layer);
        plank.addView(bar, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ReflectionHelpers.setField(screen, "mSuggestionBarView", bar);
        // Pinned to what the catalogue actually holds, so every slot resolves to a real icon: a
        // row of unresolvable refs falls back to blank fillers, which have no press target to lift.
        bar.setAppDataProvider(loadedCatalogue(9));
        bar.reloadAllApps();
        List<LauncherAppEntry> catalogue = ReflectionHelpers.getField(bar, "allApps");
        assertTrue("the catalogue reached the row", catalogue != null && catalogue.size() >= 9);
        List<PinnedItem> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) items.add(new PinnedAppItem(catalogue.get(i).appRef));
        ReflectionHelpers.setField(bar, "pinnedItems", items);
        bar.setMaxButtonCount(3);

        PlaceLayout layout = bottomStack(
            Element.STATUS, Element.EXTRA_KEYS, Element.APPS, Element.AZ);
        screen.applyEdgeStacks(layout);
        screen.syncPinnedAppsHost(layout);
        screen.applyDockLayout(screen.dockLayoutFor(layout));
        showBand(screen, R.id.apps_bar_az_row);
        showBand(screen, R.id.terminal_toolbar_view_pager);
        showBand(screen, R.id.terminal_window_bar_host);
        showBand(screen, R.id.apps_bar_viewpager);
        showBand(screen, R.id.apps_bar_indicator_band);
        screen.findViewById(R.id.accessory_stack_container).setVisibility(View.VISIBLE);

        // In a window: a detached view queues everything it posts, so every deferred render and
        // every gate timeout the row leans on would sit unrun. The screen is built rather than
        // resumed — TermuxActivity's own onCreate wants a service — so its decor is stood inside
        // an activity that is, which attaches the whole tree without moving a single band.
        activity = Robolectric.buildActivity(Activity.class).setup();
        FrameLayout window = new FrameLayout(activity.get());
        activity.get().setContentView(window);
        window.addView(screen.getWindow().getDecorView(),
            new FrameLayout.LayoutParams(PHONE_WIDTH, PHONE_HEIGHT));
        window.measure(
            View.MeasureSpec.makeMeasureSpec(PHONE_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(PHONE_HEIGHT, View.MeasureSpec.EXACTLY));
        window.layout(0, 0, PHONE_WIDTH, PHONE_HEIGHT);
        assertTrue("the row must be in a window", bar.isAttachedToWindow());
        assertTrue("the row must have a band to draw in: " + bar.getHeight(),
            bar.getWidth() > 0 && bar.getHeight() > 0);
        bar.reload();
        idle();
        return bar;
    }

    /** Everything on the bottom edge in the order given, innermost (nearest the canvas) first. */
    private static PlaceLayout bottomStack(Element... innermostFirst) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (int i = 0; i < innermostFirst.length; i++)
            slots.put(innermostFirst[i],
                new Slot(false, PlaceLayout.Edge.BOTTOM, innermostFirst.length - i));
        for (Element element : Element.values())
            if (!slots.containsKey(element))
                slots.put(element, Slot.on(PlaceLayout.Edge.BOTTOM, element));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /** A band the arrangement stands, which the render passes this test does not run would show. */
    private static void showBand(TermuxActivity screen, int viewId) {
        View band = screen.findViewById(viewId);
        assertNotNull(band);
        band.setVisibility(View.VISIBLE);
    }

    /**
     * The ghost swipe from the report: with the row one band of a three-row dock under a status
     * bar, a page swipe played its whole slide and landed back on page 1.
     */
    @Test
    public void aSwipeOnTheRowInADockStackLandsOnTheNextPage() {
        SuggestionBarView bar = rowInTheDockStack();
        assertTrue("the row has pages to swipe between: " + pagesOf(bar), pagesOf(bar) > 1);

        swipeLeftOn(bar);
        idle();

        assertEquals(1, pageOf(bar));
        assertFalse("draw suppression must not outlive the swipe",
            (Boolean) ReflectionHelpers.getField(bar, "suppressDrawUntilStableLayout"));
    }

    /**
     * The "ghost touch" icon: a finger starts a page swipe <em>on</em> an icon, so that icon has
     * already played its press-down lift. The row then claims the stream and consumes the release
     * itself, so the icon never sees an UP and never bounces back — it is left standing 4dp above
     * the row it belongs to and scaled up, looking permanently pressed. The re-render the commit
     * happens to run is the only thing that has ever hidden it, and the row's own render gates mean
     * a commit cannot promise one.
     */
    @Test
    public void aPageSwipeTakesBackTheLiftOfTheIconItStartedOn() {
        SuggestionBarView bar = rowInTheDockStack();
        View pressed = pressTargetUnder(bar, downXOn(bar));
        assertNotNull("the swipe has to start on an icon to show this", pressed);

        swipeLeftOn(bar);
        idle();

        assertEquals(1, pageOf(bar));
        assertEquals("the icon the swipe started on is left lifted",
            0f, pressed.getTranslationY(), 0.01f);
        assertEquals("and left scaled up", 1f, pressed.getScaleY(), 0.01f);
        for (int i = 0; i < bar.getChildCount(); i++) {
            assertEquals("slot " + i + " is drawn off its own row",
                0f, bar.getChildAt(i).getTranslationY(), 0.01f);
        }
    }

    /**
     * The same defect at the moment it happens. A claim is where the row takes the stream away from
     * its children, so a claim is where they have to stand down — the drawer's drag has always sent
     * one synthetic cancel at exactly that point for exactly this reason. A page swipe sent none,
     * so the icon under the finger kept the lift it took on the way down and only ever lost it to
     * whatever re-render happened to follow; with the render deferred — which is the state the dock
     * spends its first frames in — nothing ever took it back.
     */
    @Test
    public void theIconUnderTheFingerStandsDownTheMomentTheRowClaimsTheSwipe() {
        SuggestionBarView bar = rowInTheDockStack();
        View pressed = pressTargetUnder(bar, downXOn(bar));
        assertNotNull("the swipe has to start on an icon to show this", pressed);

        float y = bar.getHeight() * 0.5f;
        float from = downXOn(bar);
        dispatchOn(bar, MotionEvent.ACTION_DOWN, from, y);
        dispatchOn(bar, MotionEvent.ACTION_MOVE, from - (bar.getWidth() * 0.3f), y);
        idle();

        assertEquals("the row owns the swipe, so the icon it started on is not left pressed",
            0f, pressed.getTranslationY(), 0.01f);
        assertEquals("nor left scaled up", 1f, pressed.getScaleY(), 0.01f);
    }

    /** The icon's own press target at that point along the row, which is what carries the lift. */
    private static View pressTargetUnder(SuggestionBarView bar, float x) {
        for (int i = 0; i < bar.getChildCount(); i++) {
            View slot = bar.getChildAt(i);
            if (x < slot.getLeft() || x >= slot.getRight()) continue;
            if (!(slot instanceof ViewGroup)) return slot;
            View press = ((ViewGroup) slot).getChildAt(0);
            float inSlot = x - slot.getLeft();
            return press != null && inSlot >= press.getLeft() && inSlot < press.getRight()
                ? press : null;
        }
        return null;
    }

    private static int pageOf(SuggestionBarView bar) {
        return ReflectionHelpers.getField(bar, "pinnedPageIndex");
    }

    private static int pagesOf(SuggestionBarView bar) {
        return bar.getPinnedVisiblePageCount();
    }

    /** The report's gesture: a swipe along the row, well past the commit distance it asks for. */
    private void swipeLeftOn(SuggestionBarView bar) {
        float y = bar.getHeight() * 0.5f;
        float travel = bar.getWidth() * 0.6f;
        float from = downXOn(bar);
        dispatchOn(bar, MotionEvent.ACTION_DOWN, from, y);
        dispatchOn(bar, MotionEvent.ACTION_MOVE, from - (travel * 0.5f), y);
        dispatchOn(bar, MotionEvent.ACTION_MOVE, from - travel, y);
        dispatchOn(bar, MotionEvent.ACTION_UP, from - travel, y);
    }

    /**
     * Where the swipe puts its finger down: the middle of a slot, where a thumb starts one, so the
     * icon standing there takes the press.
     */
    private static float downXOn(SuggestionBarView bar) {
        return bar.getWidth() * 5f / 6f;
    }

    private void dispatchOn(SuggestionBarView bar, int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        bar.dispatchTouchEvent(event);
        event.recycle();
    }

    /**
     * A catalogue with something under {@link #SCRUB_LETTER}. Robolectric labels every resolved
     * activity from the test application's own info, so the letter is the one that label starts
     * with rather than one this can choose.
     */
    private LauncherAppDataProvider loadedCatalogue() {
        return loadedCatalogue(4);
    }

    private LauncherAppDataProvider loadedCatalogue(int apps) {
        ShadowPackageManager packageManager = Shadows.shadowOf(context.getPackageManager());
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (int i = 0; i < apps; i++) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = new ActivityInfo();
            resolveInfo.activityInfo.packageName = "com.example.app" + i;
            resolveInfo.activityInfo.name = "Main";
            resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
            packageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
        }
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        provider.invalidate();
        provider.getAllAppsBlocking();
        assertTrue("the catalogue must be loaded", provider.hasLoadedApps());
        assertFalse("the scrub letter must have matches",
            provider.getAppsForLetter(SCRUB_LETTER).isEmpty());
        return provider;
    }

    private List<View> children() {
        List<View> views = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) views.add(row.getChildAt(i));
        return views;
    }

    private boolean suppressed() {
        return ReflectionHelpers.getField(row, "suppressDrawUntilStableLayout");
    }

    private boolean dragging() {
        return ReflectionHelpers.getField(row, "swipePageDragging");
    }

    private float offset() {
        return ReflectionHelpers.getField(row, "swipeVisualOffsetPx");
    }

    private void swipeLeft() {
        dispatch(MotionEvent.ACTION_DOWN, 400f, 80f);
        dispatch(MotionEvent.ACTION_MOVE, 400f - TRAVEL, 80f);
        dispatch(MotionEvent.ACTION_UP, 400f - TRAVEL, 80f);
    }

    private int page() {
        return ReflectionHelpers.getField(row, "pinnedPageIndex");
    }

    private void dispatch(int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        row.dispatchTouchEvent(event);
        event.recycle();
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
    }

    private static void setDurationScale(float scale) {
        ReflectionHelpers.setStaticField(ValueAnimator.class, "sDurationScale", scale);
    }
}
