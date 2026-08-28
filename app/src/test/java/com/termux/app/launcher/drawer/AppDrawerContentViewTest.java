package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid's half of the drawer gesture: who gets a delta, and what the leftovers do.
 *
 * <p>Every case drives {@link AppDrawerContentView}'s nested-scroll entry points directly rather
 * than synthesising a scroll inside {@code RecyclerView}. That is deliberate: what is under test is
 * the arbitration this class performs on the deltas it is handed, and a test that produced them by
 * flinging a {@code RecyclerView} under Robolectric would be testing the recycler's touch pipeline
 * and reporting the result as a drawer bug.
 *
 * <p>The two failure modes being pinned are the ones the design exists to prevent: a close drag
 * reported more than once for one gesture (the plane and the grid both claiming), and a downward
 * pull at the top of a scrollable list closing the drawer on its first attempt (the flick that
 * scrolls to the top also putting the drawer away).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerContentViewTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;
    private static final int APP_COUNT = 120;
    private static final float LABEL_HEIGHT_PX = 30f;

    private Context context;
    private AppDrawerContentView content;
    private AppDrawerSearchController search;
    private RecyclerView grid;
    private RecordingCallbacks callbacks;
    private float density;
    /** A raw pull long enough to pay for an arming at any density. */
    private float pull;

    @Before
    public void setUp() {
        // The overpull spring posts frame callbacks, and an unpaused legacy scheduler runs each one
        // the moment it is posted — advancing the virtual clock by a frame as it goes, and settling
        // the whole spring inside the call that started it. Left running, a release would burn past
        // the policy's 1200ms arming window in virtual time and the arming would expire before the
        // next line of the test. Paused, frames only happen when driveFrames() says so.
        Robolectric.getForegroundThreadScheduler().pause();
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        density = context.getResources().getDisplayMetrics().density;
        pull = 100f * density;

        search = new AppDrawerSearchController();
        search.setHost(new AppDrawerSearchController.Host() {
            @Override
            public boolean isSearchActive() {
                return true;
            }

            @Override
            public void onSearchCommitRequested() {}

            @Override
            public void onSearchDismissRequested() {}
        });

        content = new AppDrawerContentView(context);
        callbacks = new RecordingCallbacks();
        content.setCallbacks(callbacks);
        content.setInteractive(true);
        content.setMetrics(AppDrawerGridMetrics.resolve(WIDTH, density, LABEL_HEIGHT_PX));
        content.bind(null, search);
        search.setCatalogue(apps(APP_COUNT));
        grid = content.getGrid();
        layout();
    }

    // ------------------------------------------------------------------ the claim

    @Test
    public void aTopPullConsumesTheDeltaAndReportsTheCloseExactlyOnce() {
        pressOnGrid();
        assertEquals(-40, preScroll(-40));
        assertEquals(1, callbacks.begins);
        assertEquals(1, callbacks.updates);
        // The drag is anchored where the finger went down, not where the claim landed.
        assertEquals(gridY(), callbacks.downRawY, 0.01f);

        assertEquals(-40, preScroll(-40));
        assertEquals(1, callbacks.begins);
        assertEquals(2, callbacks.updates);

        content.onStopNestedScroll(grid, ViewCompat.TYPE_TOUCH);
        assertEquals(1, callbacks.ends);
        assertEquals(0, callbacks.cancels);
        // The next gesture at the top is simply another close.
        pressOnGrid();
        assertEquals(-40, preScroll(-40));
        assertEquals(2, callbacks.begins);
    }

    @Test
    public void aStreamTakenAwayMidCloseCancelsRatherThanCommits() {
        pressOnGrid();
        preScroll(-40);
        assertEquals(1, callbacks.begins);

        cancelStream();
        assertEquals(1, callbacks.cancels);
        assertEquals(0, callbacks.ends);
    }

    @Test
    public void theFirstPullAtTheTopClosesInOneGesture() {
        pressOnGrid();

        assertEquals(-60, preScroll(-60));
        assertEquals(1, callbacks.begins);
        // A close is a tracked drag, never a rubber-band.
        assertTrue(content.getOverpullTranslationPx() == 0f);
    }

    // ------------------------------------------------------------------ overpull

    @Test
    public void unconsumedDeltaBecomesDampedOverpullAndTranslatesTheGrid() {
        // Overpull belongs to the stream that scrolled: begin mid-list, so the pull past the top
        // stays a scroll whose leftovers rubber-band instead of becoming a close.
        grid.scrollBy(0, 400);
        pressOnGrid();
        preScroll(-Math.round(pull));

        int taken = unconsumed(-Math.round(pull));
        float first = content.getOverpullTranslationPx();
        assertEquals(-Math.round(pull), taken);
        assertTrue(first > 0f);
        // Damped, not tracked: the grid always travels less than the finger.
        assertTrue(first < pull);
        assertEquals(first, grid.getTranslationY(), 0.01f);

        unconsumed(-Math.round(pull));
        float second = content.getOverpullTranslationPx();
        assertTrue(second > first);
        // The same raw pull buys less the second time, and never more than the ceiling.
        assertTrue(second - first < first);
        assertTrue(second < AppDrawerContentView.OVERPULL_MAX_DP * density);

        // An upward delta is a scroll the child owns; nothing here may take it.
        assertEquals(0, unconsumed(40));
    }

    @Test
    public void aFlingContinuationCanNeitherCloseNorOverpull() {
        pressOnGrid();

        assertEquals(0, preScrollOfType(-40, ViewCompat.TYPE_NON_TOUCH));
        assertEquals(0, callbacks.begins);
        assertEquals(0, unconsumedOfType(-Math.round(pull), ViewCompat.TYPE_NON_TOUCH));
        assertEquals(0f, content.getOverpullTranslationPx(), 0.001f);
    }

    // ------------------------------------------------------------------ release

    @Test
    public void aStopSpringsTheOverpullBack() {
        grid.scrollBy(0, 400);
        pressOnGrid();
        preScroll(-Math.round(pull));
        unconsumed(-Math.round(pull));
        assertTrue(content.getOverpullTranslationPx() > 0f);

        content.onStopNestedScroll(grid, ViewCompat.TYPE_TOUCH);
        driveFrames();
        assertEquals(0f, content.getOverpullTranslationPx(), 0.5f);
        assertEquals(0f, grid.getTranslationY(), 0.5f);
    }

    @Test
    public void aShortPullAtTheTopStillBeginsATrackedClose() {
        // Whether it commits is the controller's release policy; the content's job is only to
        // start tracking. A two-pixel twitch begins and ends the same drag a full pull does.
        pressOnGrid();
        assertEquals(-2, preScroll(-2));
        assertEquals(1, callbacks.begins);
        content.onStopNestedScroll(grid, ViewCompat.TYPE_TOUCH);
        assertEquals(1, callbacks.ends);
    }

    @Test
    public void stopOverpullSpringDropsTheTravelWhereverItWas() {
        grid.scrollBy(0, 400);
        pressOnGrid();
        preScroll(-Math.round(pull));
        unconsumed(-Math.round(pull));

        content.stopOverpullSpring();
        assertEquals(0f, content.getOverpullTranslationPx(), 0.001f);
        assertEquals(0f, grid.getTranslationY(), 0.001f);
    }

    // ------------------------------------------------------------------ ownership

    @Test
    public void searchPillDrawsAfterTheGridSoScrolledIconsCannotCoverIt() {
        AppDrawerSearchPillView pill = null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child instanceof AppDrawerSearchPillView) {
                pill = (AppDrawerSearchPillView) child;
                break;
            }
        }

        assertNotNull(pill);
        assertTrue("the search chrome must composite above the scrolling grid",
            content.indexOfChild(pill) > content.indexOfChild(grid));
    }

    @Test
    public void midScrollTopmostVisibleGridItemDoesNotIntersectSearchPill() {
        grid.scrollBy(0, 400);
        layout();
        assertTrue("fixture must exercise a real mid-list production layout",
            grid.computeVerticalScrollOffset() > 0);

        View topmost = null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (topmost == null || child.getTop() < topmost.getTop()) topmost = child;
        }
        assertNotNull(topmost);
        assertTrue("the mid-scroll holder must straddle the production surface's leading edge",
            topmost.getTop() < 0);
        assertTrue("the production grid must install a hard bounds clip",
            grid.getClipToOutline());
        Rect visibleItem = new Rect(0, 0, topmost.getWidth(), topmost.getHeight());
        grid.offsetDescendantRectToMyCoords(topmost, visibleItem);
        assertTrue(visibleItem.intersect(0, 0, grid.getWidth(), grid.getHeight()));
        content.offsetDescendantRectToMyCoords(grid, visibleItem);
        Rect pill = new Rect(content.getSearchPill().getLeft(),
            content.getSearchPill().getTop(), content.getSearchPill().getRight(),
            content.getSearchPill().getBottom());
        assertFalse("the topmost visible production holder must be clipped below the pill",
            Rect.intersects(visibleItem, pill));
    }

    @Test
    public void everyPillViewTypeClipsItsProductionContentSurface() {
        assertTrue(grid.getClipToPadding());
        assertTrue(grid.getClipToOutline());
        assertTrue(content.getHorizontalPager().getClipToPadding());
        assertTrue(content.getHorizontalPager().getClipToOutline());
        assertTrue(content.getCategoryView().getClipChildren());
        assertTrue(content.getCategoryView().getClipToOutline());
        assertTrue(content.getCategoryView().getOverview().getClipToPadding());
        assertTrue(content.getCategoryView().getDetailList().getClipToPadding());
    }

    @Test
    public void gridOwnsItsPointsIncludingTheFormerCogBand() {
        // The name reads backwards on purpose: true means the *content* owns the point and the
        // plane must defer. Every piece of chrome answers false so the plane's close drag runs.
        assertTrue(content.ownsPoint(WIDTH * 0.5f, gridY()));
        assertFalse("the pill must not swallow a close drag",
            content.ownsPoint(WIDTH * 0.5f, 8f * density));
        assertTrue("vertical content must reclaim the removed cog's bottom band",
            content.ownsPoint(WIDTH * 0.5f, HEIGHT - (8f * density)));
    }

    @Test
    public void b2ArbitrationIsByteIdenticalWithTheColumnPresent() {
        // The whole B-2 sequence, with the A-Z column laid out beside the grid and a catalogue that
        // gives it real letters. Every number here is the number the B-2 cases above assert: the
        // third touch category may not cost the grid a single delta.
        search.setCatalogue(lettered(APP_COUNT));
        layout();
        assertTrue(content.isColumnActive());

        pressOnGrid();
        assertEquals(-40, preScroll(-40));
        assertEquals(1, callbacks.begins);
        assertEquals(gridY(), callbacks.downRawY, 0.01f);
        content.onStopNestedScroll(grid, ViewCompat.TYPE_TOUCH);
        assertEquals(1, callbacks.ends);

        // A stream that begins mid-list still scrolls rather than closing...
        grid.scrollBy(0, 400);
        pressOnGrid();
        assertEquals(0, preScroll(-60));
        assertEquals(1, callbacks.begins);
        // ...and the leftovers still become damped travel on the grid alone.
        int taken = unconsumed(-Math.round(pull));
        assertEquals(-Math.round(pull), taken);
        assertTrue(content.getOverpullTranslationPx() > 0f);
        assertTrue(content.getOverpullTranslationPx() < pull);
        assertEquals(content.getOverpullTranslationPx(), grid.getTranslationY(), 0.01f);
        assertEquals(0f, content.getRopeColumn().getTranslationY(), 0f);
    }

    @Test
    public void theGridsColumnCountIsComputedFromTheWidthMinusTheStrip() {
        float columnWidthPx = content.getColumnWidthPx();
        assertTrue(columnWidthPx > 0f);
        // What the controller passes: the plane's width less the strip the letters own. Sizing the
        // grid for the whole plane would push the last cell of every row under the letters.
        AppDrawerGridMetrics metrics =
            AppDrawerGridMetrics.resolve(WIDTH - columnWidthPx, density, LABEL_HEIGHT_PX);
        content.setMetrics(metrics);
        layout();

        assertEquals(AppDrawerGridMetrics.resolveColumns((WIDTH - columnWidthPx) / density),
            metrics.columns);
        assertEquals(metrics.columns,
            ((androidx.recyclerview.widget.GridLayoutManager) grid.getLayoutManager())
                .getSpanCount());
        // And the cell width the icons are sized from is the reduced width divided by that count.
        assertEquals((WIDTH - columnWidthPx) / metrics.columns, metrics.cellWidthPx, 0.01f);
        assertEquals(WIDTH - Math.round(columnWidthPx), grid.getWidth());
    }

    @Test
    public void aClosedDrawerIsInert() {
        content.setInteractive(false);

        MotionEvent event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN,
            WIDTH * 0.5f, gridY(), 0);
        try {
            assertTrue(content.onInterceptTouchEvent(event));
        } finally {
            event.recycle();
        }
        assertFalse(content.ownsPoint(WIDTH * 0.5f, gridY()));
        assertFalse(content.onStartNestedScroll(grid, grid, ViewCompat.SCROLL_AXIS_VERTICAL,
            ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void returningFromHorizontalRestoresVerticalGeometryAndTopPullClose() {
        content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(WIDTH,
            content.horizontalPagerUsableHeight(HEIGHT), density, LABEL_HEIGHT_PX, 4, 2));
        content.setViewType(AppDrawerViewType.HORIZONTAL);
        content.setViewType(AppDrawerViewType.VERTICAL);
        content.setVerticalMetrics(AppDrawerGridMetrics.resolve(
            WIDTH - content.getColumnWidthPx(), density, LABEL_HEIGHT_PX));
        layout();

        assertEquals(View.VISIBLE, grid.getVisibility());
        assertEquals(View.VISIBLE, content.getRopeColumn().getVisibility());
        assertEquals(Math.round(content.getColumnWidthPx()),
            ((android.widget.FrameLayout.LayoutParams) grid.getLayoutParams()).rightMargin);

        pressOnGrid();
        assertEquals(-Math.round(pull), preScroll(-Math.round(pull)));
        assertEquals(1, callbacks.begins);
        content.onStopNestedScroll(grid, ViewCompat.TYPE_TOUCH);
        assertEquals(1, callbacks.ends);
    }

    // ------------------------------------------------------------------ search

    @Test
    public void aRebindKeepsTheQueryAndPutsTheGridBackToItsTop() {
        type("app");
        layout();
        assertTrue(content.hasQuery());
        assertEquals(APP_COUNT, search.results().size());

        grid.scrollBy(0, 400);
        assertTrue(grid.computeVerticalScrollOffset() > 0);

        content.bind(null, search);
        layout();

        assertEquals("app", search.query());
        assertTrue(content.hasQuery());
        assertEquals(0, grid.computeVerticalScrollOffset());
    }

    @Test
    public void aQueryChangeResetsTheScroll() {
        grid.scrollBy(0, 400);
        assertTrue(grid.computeVerticalScrollOffset() > 0);

        type("app");
        layout();
        assertEquals(0, grid.computeVerticalScrollOffset());

        // The filtered list starts at its top, so a pull on it closes like any other top pull.
        pressOnGrid();
        assertEquals(-40, preScroll(-40));
        assertEquals(1, callbacks.begins);
    }

    @Test
    public void clearingTheQueryIsWhatBackSpends() {
        assertFalse(content.clearQueryIfPresent());
        type("app");
        assertEquals(1f, content.getRevealFraction(), 0.001f);

        assertTrue(content.clearQueryIfPresent());
        assertFalse(content.hasQuery());
        assertEquals(APP_COUNT, search.results().size());
        assertEquals(0f, content.getRevealFraction(), 0.001f);
        assertFalse(content.clearQueryIfPresent());
    }

    // ------------------------------------------------------------------ plumbing


    /** @return the delta the parent consumed */
    private int preScroll(int dy) {
        return preScrollOfType(dy, ViewCompat.TYPE_TOUCH);
    }

    private int preScrollOfType(int dy, int type) {
        int[] consumed = new int[2];
        content.onNestedPreScroll(grid, 0, dy, consumed, type);
        return consumed[1];
    }

    /** @return the leftover delta the parent took as overpull */
    private int unconsumed(int dy) {
        return unconsumedOfType(dy, ViewCompat.TYPE_TOUCH);
    }

    private int unconsumedOfType(int dy, int type) {
        int[] consumed = new int[2];
        content.onNestedScroll(grid, 0, 0, 0, dy, type, consumed);
        return consumed[1];
    }

    private void type(@androidx.annotation.NonNull String text) {
        for (int i = 0; i < text.length(); i++) {
            assertTrue(search.handleCodePoint(text.charAt(i), false));
        }
    }

    /** Runs the overpull spring to rest without waiting on a real frame clock. */
    private void driveFrames() {
        long nanos = 0L;
        for (int i = 0; i < 240 && content.getOverpullTranslationPx() != 0f; i++) {
            nanos += 16_666_667L;
            content.doFrame(nanos);
        }
    }

    private float gridY() {
        return grid.getTop() + (grid.getHeight() * 0.25f);
    }

    /** An ACTION_DOWN on the grid: what samples the snapshot the whole arbitration runs off. */
    private void pressOnGrid() {
        dispatch(MotionEvent.ACTION_DOWN, gridY());
    }

    /** The window losing the stream: the child answers it by stopping its nested scroll. */
    private void cancelStream() {
        dispatch(MotionEvent.ACTION_CANCEL, gridY());
        content.onStopNestedScroll(grid, ViewCompat.TYPE_TOUCH);
    }

    private void dispatch(int action, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, WIDTH * 0.5f, y, 0);
        try {
            content.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private void layout() {
        content.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
    }

    /** The same count, spread over the alphabet, for the cases that need a live A-Z column. */
    private static List<LauncherAppEntry> lettered(int count) {
        List<LauncherAppEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            char letter = (char) ('A' + (i % 26));
            String label = letter + "pp " + i;
            entries.add(new LauncherAppEntry(
                new AppRef("com.example.app" + i, ".Main"), label, null));
        }
        return AppDrawerSectionIndex.sortByLabel(entries);
    }

    private static List<LauncherAppEntry> apps(int count) {
        List<LauncherAppEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new LauncherAppEntry(
                new AppRef("com.example.app" + i, ".Main"), "App " + i, null));
        }
        return entries;
    }

    private static final class RecordingCallbacks implements AppDrawerContentView.Callbacks {

        int begins;
        int updates;
        int ends;
        int cancels;
        float downRawY;
        float lastRawY;

        void reset() {
            begins = 0;
            updates = 0;
            ends = 0;
            cancels = 0;
        }

        @Override
        public void onContentCloseDragBegin(float downRawY) {
            begins++;
            this.downRawY = downRawY;
        }

        @Override
        public void onContentCloseDragUpdate(float rawY) {
            updates++;
            lastRawY = rawY;
        }

        @Override
        public void onContentCloseDragEnd(float velocityPxPerSec) {
            ends++;
        }

        @Override
        public void onContentCloseDragCancel() {
            cancels++;
        }
    }
}
