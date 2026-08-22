package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
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
 * The third touch category, and the two write paths that keep a scrub from leaving dim cells behind.
 *
 * <p>The headline case is the one the whole region split exists for: a DOWN on the A-Z column must
 * produce <b>no</b> close report for the entire stream. A scrub is a sustained downward drag in the
 * same place at the same speed as a close, so nothing downstream of the down point can tell them
 * apart — which is why the decision is made from geometry once and never revisited, and why a column
 * stream also leaves the arming policy disarmed and the gesture inactive (the recycler never sees the
 * stream, so no {@code onStopNestedScroll} ever arrives to settle it).
 *
 * <p>The rest pin the failure modes that are silent until a close and reopen: a cell bound by the
 * auto-scroll mid-scrub that arrives at full opacity and flashes for a frame, and a cell left at 0.28
 * alpha after the release.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerContentColumnTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;
    private static final float LABEL_HEIGHT_PX = 30f;
    /** Six apps per letter over the whole alphabet: every letter is a real, contiguous run. */
    private static final int PER_LETTER = 6;
    private static final float FRAME = 1f / 60f;

    private Context context;
    private AppDrawerContentView content;
    private AppDrawerSearchController search;
    private AppDrawerRopeColumnView column;
    private RecyclerView grid;
    private RecordingCallbacks callbacks;
    private float density;
    private float pull;
    private int frameRequests;

    @Before
    public void setUp() {
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
        content.setFrameRequestListener(() -> frameRequests++);
        content.setInteractive(true);
        content.setMetrics(AppDrawerGridMetrics.resolve(WIDTH - content.getColumnWidthPx(), density,
            LABEL_HEIGHT_PX));
        content.bind(null, search);
        search.setCatalogue(alphabet());
        grid = content.getGrid();
        column = content.getRopeColumn();
        layout();
    }

    // ------------------------------------------------------------------ the headline

    @Test
    public void aDownOnTheColumnProducesNoCloseReportForTheWholeStream() {
        pressOnColumn(0);
        // A scrub is a long downward drag, so every delta of it is offered to the close channel the
        // grid would have used — the whole way to the bottom of the alphabet. The overpull channel is
        // not offered anything because nothing offers it anything: it is fed by deltas a
        // RecyclerView could not use, and the recycler never receives this stream at all.
        for (int i = 1; i < 26; i++) {
            moveOnColumn(i);
            assertEquals(0, preScroll(-40));
            assertEquals(0f, content.getOverpullTranslationPx(), 0.001f);
        }
        releaseColumn(25);

        assertEquals(0, callbacks.begins);
        assertEquals(0, callbacks.updates);
        assertEquals(0, callbacks.ends);
        assertEquals(0, callbacks.cancels);
        assertEquals(0f, content.getOverpullTranslationPx(), 0.001f);
        assertFalse(content.isScrubbing());
    }

    @Test
    public void aGridPullAtTheTopAfterAColumnStreamStillCloses() {
        pressOnColumn(0);
        moveOnColumn(4);
        releaseColumn(4);

        // The column stream is its own gesture; a fresh downward pull on a grid sitting at its top
        // closes in that one gesture, exactly as it would with no column stream before it.
        pressOnGrid();
        assertEquals(-40, preScroll(-40));
        assertEquals(1, callbacks.begins);
    }

    // ------------------------------------------------------------------ the grid side

    @Test
    public void aLetterJumpsTheGridToTheStartOfThatLettersRun() {
        pressOnColumn(letterIndex('M'));
        layout();

        GridLayoutManager manager = (GridLayoutManager) grid.getLayoutManager();
        assertTrue(manager != null);
        int expected = firstPositionFor('M');
        assertTrue(expected > 0);
        assertEquals(expected, manager.findFirstVisibleItemPosition());
        assertEquals('M', column.activeLetter());

        // A tap is a scrub that lasted 80ms, and the scroll position it put the grid at is kept.
        releaseColumn(letterIndex('M'));
        driveFx();
        assertEquals(expected, manager.findFirstVisibleItemPosition());
    }

    @Test
    public void aCellBoundDuringAScrubArrivesAlreadyDimmed() {
        pressOnColumn(letterIndex('A'));

        // The auto-scroll binds fresh cells continuously while the finger runs down the alphabet.
        // Without the bind-time rule every one of them shows at full opacity for a frame.
        AppDrawerAppsAdapter adapter = (AppDrawerAppsAdapter) grid.getAdapter();
        assertTrue(adapter != null);
        AppDrawerAppsAdapter.Cell fresh = adapter.onCreateViewHolder(grid, 0);
        adapter.bindViewHolder(fresh, firstPositionFor('Z'));
        assertEquals(AppDrawerScrubHighlight.DIM_ALPHA, fresh.itemView.getAlpha(), 0.001f);

        AppDrawerAppsAdapter.Cell matching = adapter.onCreateViewHolder(grid, 0);
        adapter.bindViewHolder(matching, firstPositionFor('A'));
        assertEquals(1f, matching.itemView.getAlpha(), 0.001f);
        assertEquals(AppDrawerScrubHighlight.MATCH_SCALE, matching.itemView.getScaleX(), 0.001f);

        // And a holder that leaves the screen mid-scrub does not take 0.28 alpha into the pool.
        adapter.onViewRecycled(fresh);
        assertEquals(1f, fresh.itemView.getAlpha(), 0f);
        assertEquals(1f, fresh.itemView.getScaleX(), 0f);
    }

    @Test
    public void aScrubDimsTheAttachedCellsThatDoNotMatchAndLiftsTheOnesThatDo() {
        pressOnColumn(letterIndex('A'));
        layout();

        boolean sawMatch = false;
        boolean sawDim = false;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            char letter = letterOfChild(child);
            if (letter == 'A') {
                sawMatch = true;
                assertEquals(1f, child.getAlpha(), 0.001f);
                assertEquals(AppDrawerScrubHighlight.MATCH_SCALE, child.getScaleX(), 0.001f);
            } else if (letter != '\0') {
                sawDim = true;
                assertEquals(AppDrawerScrubHighlight.DIM_ALPHA, child.getAlpha(), 0.001f);
                assertEquals(1f, child.getScaleX(), 0.001f);
            }
        }
        assertTrue("no matching cell was attached", sawMatch);
        assertTrue("no non-matching cell was attached", sawDim);
    }

    @Test
    public void releaseRestoresEveryAttachedChildToExactlyOneAndOne() {
        pressOnColumn(letterIndex('A'));
        layout();
        assertNotEquals(1f, dimmestChildAlpha(), 0.001f);

        releaseColumn(letterIndex('A'));
        driveFx();

        // Exactly 1, not 0.999: with no scrub in progress every cell has to be byte-identical to
        // B-2, and a holder returned to the pool at anything else is a permanently dim cell.
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            assertEquals(1f, child.getAlpha(), 0f);
            assertEquals(1f, child.getScaleX(), 0f);
            assertEquals(1f, child.getScaleY(), 0f);
        }
        assertFalse(content.isScrubbing());
    }

    @Test
    public void aScrubAsksTheHostForFramesBecauseTheLoopIsNotItsOwn() {
        frameRequests = 0;
        pressOnColumn(0);
        assertTrue("a letter change must restart the controller's loop", frameRequests > 0);

        int afterLetter = frameRequests;
        releaseColumn(0);
        assertTrue("the release fade needs frames too", frameRequests > afterLetter);
    }

    // ------------------------------------------------------------------ deactivation

    @Test
    public void aNonEmptyQueryMakesTheColumnStripChromeAgain() {
        assertTrue(content.isColumnActive());
        assertTrue(content.ownsPoint(columnX(), columnY(3)));

        type("app");
        layout();

        // A ranked list is ordered by match quality, so its letters are not contiguous and an index
        // over it would scroll to the wrong place. The strip goes back to being B-1's close drag.
        assertFalse(content.isColumnActive());
        assertFalse(content.ownsPoint(columnX(), columnY(3)));
        assertFalse(down(columnX(), columnY(3)));
        assertFalse(content.isScrubbing());

        // Cleared, and it is a scrubber again.
        assertTrue(content.clearQueryIfPresent());
        layout();
        assertTrue(content.isColumnActive());
        assertTrue(content.ownsPoint(columnX(), columnY(3)));
    }

    @Test
    public void aQueryChangeAndAClosedDrawerBothClearAScrubInFlight() {
        pressOnColumn(letterIndex('A'));
        assertTrue(content.isScrubbing());
        type("app");
        assertFalse(content.isScrubbing());
        assertEquals(1f, dimmestChildAlpha(), 0f);

        assertTrue(content.clearQueryIfPresent());
        layout();
        pressOnColumn(letterIndex('A'));
        assertTrue(content.isScrubbing());
        content.setInteractive(false);
        assertFalse(content.isScrubbing());
        assertEquals(1f, dimmestChildAlpha(), 0f);
    }

    @Test
    public void aHorizontalRoundTripRestoresScrubCellsAndVerticalColumnGeometry() {
        pressOnColumn(letterIndex('A'));
        layout();
        assertNotEquals(1f, dimmestChildAlpha(), 0.001f);

        content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(WIDTH,
            content.horizontalPagerUsableHeight(HEIGHT), density, LABEL_HEIGHT_PX, 4, 2));
        content.setViewType(AppDrawerViewType.HORIZONTAL);
        assertEquals(View.GONE, column.getVisibility());
        assertFalse(content.isColumnActive());
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            assertEquals(1f, child.getAlpha(), 0f);
            assertEquals(1f, child.getScaleX(), 0f);
            assertEquals(1f, child.getScaleY(), 0f);
        }

        content.setViewType(AppDrawerViewType.VERTICAL);
        content.setVerticalMetrics(AppDrawerGridMetrics.resolve(
            WIDTH - content.getColumnWidthPx(), density, LABEL_HEIGHT_PX));
        layout();
        assertEquals(View.VISIBLE, column.getVisibility());
        assertTrue(content.isColumnActive());
        assertEquals(Math.round(content.getColumnWidthPx()),
            ((android.widget.FrameLayout.LayoutParams) grid.getLayoutParams()).rightMargin);
    }

    @Test
    public void theLettersAreTheVisibleSetAndTwoAreNeededForAColumnAtAll() {
        assertEquals(26, column.letterCount());

        search.setCatalogue(oneLetterCatalogue());
        layout();
        // One letter is a decoration that would eat a close drag.
        assertEquals(1, column.letterCount());
        assertFalse(content.isColumnActive());
        assertFalse(content.ownsPoint(columnX(), HEIGHT * 0.5f));
    }

    // ------------------------------------------------------------------ geometry

    @Test
    public void theGridGivesUpTheStripsWidthSoNoCellSitsUnderALetter() {
        assertTrue(content.getColumnWidthPx() > 0f);
        assertEquals(WIDTH, column.getRight());
        assertEquals(Math.round(content.getColumnWidthPx()), column.getWidth());
        assertTrue("cells must not run under the letters", grid.getRight() <= column.getLeft());
        assertEquals(WIDTH - Math.round(content.getColumnWidthPx()), grid.getRight());
        // The track spans the grid's own vertical extent, so a letter lines up with the run it jumps
        // to rather than with the pill above it.
        assertEquals(grid.getTop(), column.getTop());
        assertEquals(grid.getBottom(), column.getBottom());
    }

    // ------------------------------------------------------------------ plumbing

    private void driveFx() {
        for (int i = 0; i < 240 && content.advanceDrawerFx(1f, FRAME, false); i++) {
            // The controller's loop, without a controller.
        }
        content.advanceDrawerFx(1f, FRAME, false);
    }

    private float dimmestChildAlpha() {
        float min = 1f;
        for (int i = 0; i < grid.getChildCount(); i++) {
            min = Math.min(min, grid.getChildAt(i).getAlpha());
        }
        return min;
    }

    private char letterOfChild(View child) {
        AppDrawerAppsAdapter adapter = (AppDrawerAppsAdapter) grid.getAdapter();
        if (adapter == null) return '\0';
        return adapter.letterForPosition(grid.getChildAdapterPosition(child));
    }

    private static int letterIndex(char letter) {
        return letter == '#' ? 26 : letter - 'A';
    }

    private int firstPositionFor(char letter) {
        return AppDrawerSectionIndex.build(search.results()).firstPositionForLetter(letter);
    }

    private float columnX() {
        return column.getLeft() + (column.getWidth() * 0.5f);
    }

    /** The centre of a letter's slot, in the content's coordinates. */
    private float columnY(int index) {
        AppDrawerRopeMetrics metrics = column.metrics();
        return column.getTop() + (metrics == null ? 0f : metrics.centerYForIndex(index));
    }


    private int preScroll(int dy) {
        int[] consumed = new int[2];
        content.onNestedPreScroll(grid, 0, dy, consumed, ViewCompat.TYPE_TOUCH);
        return consumed[1];
    }

    private int unconsumed(int dy) {
        int[] consumed = new int[2];
        content.onNestedScroll(grid, 0, 0, 0, dy, ViewCompat.TYPE_TOUCH, consumed);
        return consumed[1];
    }

    private void type(String text) {
        for (int i = 0; i < text.length(); i++) {
            assertTrue(search.handleCodePoint(text.charAt(i), false));
        }
    }

    private void pressOnGrid() {
        dispatch(MotionEvent.ACTION_DOWN, WIDTH * 0.4f, grid.getTop() + (grid.getHeight() * 0.25f));
    }

    private void pressOnColumn(int index) {
        assertTrue(down(columnX(), columnY(index)));
    }

    private void moveOnColumn(int index) {
        dispatch(MotionEvent.ACTION_MOVE, columnX(), columnY(index));
    }

    private void releaseColumn(int index) {
        dispatch(MotionEvent.ACTION_UP, columnX(), columnY(index));
        content.onStopNestedScroll(grid, ViewCompat.TYPE_TOUCH);
    }

    private boolean down(float x, float y) {
        return dispatch(MotionEvent.ACTION_DOWN, x, y);
    }

    private boolean dispatch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        try {
            content.dispatchTouchEvent(event);
            return content.isScrubbing() || column.isScrubbing();
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

    /** {@link #PER_LETTER} apps under every letter of the alphabet, deliberately out of order. */
    private static List<LauncherAppEntry> alphabet() {
        List<LauncherAppEntry> entries = new ArrayList<>();
        for (int i = 0; i < PER_LETTER; i++) {
            for (char letter = 'Z'; letter >= 'A'; letter--) {
                entries.add(entry(letter + "pp " + i));
            }
        }
        return AppDrawerSectionIndex.sortByLabel(entries);
    }

    private static List<LauncherAppEntry> oneLetterCatalogue() {
        List<LauncherAppEntry> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            entries.add(entry("Apple " + i));
        }
        return entries;
    }

    private static LauncherAppEntry entry(String label) {
        return new LauncherAppEntry(new AppRef("com.example." + label.replace(' ', '.'), ".Main"),
            label, null);
    }

    private static final class RecordingCallbacks implements AppDrawerContentView.Callbacks {

        int begins;
        int updates;
        int ends;
        int cancels;

        void reset() {
            begins = 0;
            updates = 0;
            ends = 0;
            cancels = 0;
        }

        @Override
        public void onContentCloseDragBegin(float downRawY) {
            begins++;
        }

        @Override
        public void onContentCloseDragUpdate(float rawY) {
            updates++;
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
