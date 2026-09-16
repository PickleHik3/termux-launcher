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
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.launcher.paging.PageTickStripView;

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
import java.util.List;
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

    /**
     * A catalogue with something under {@link #SCRUB_LETTER}. Robolectric labels every resolved
     * activity from the test application's own info, so the letter is the one that label starts
     * with rather than one this can choose.
     */
    private LauncherAppDataProvider loadedCatalogue() {
        ShadowPackageManager packageManager = Shadows.shadowOf(context.getPackageManager());
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (int i = 0; i < 4; i++) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = new ActivityInfo();
            resolveInfo.activityInfo.packageName = "com.example.app" + i;
            resolveInfo.activityInfo.name = "com.example.app" + i + ".MainActivity";
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
