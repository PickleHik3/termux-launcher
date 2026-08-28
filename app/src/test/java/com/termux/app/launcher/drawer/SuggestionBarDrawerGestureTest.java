package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.SuggestionBarView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * The dock row's half of the drawer gesture: who ends up owning a touch stream, and what the row
 * does to its children on the way out of one.
 *
 * <p>Two of these are the slice's stated failure modes rather than features. The synthetic
 * {@code ACTION_CANCEL} is the first: once the drawer claims, every later event is consumed by the
 * row, so a pressed icon that never receives one stays pressed forever and leaks the pickup state
 * behind it — and dispatching two would run the child's release path twice. The second is the page
 * swipe: every eligibility veto has to shut the drawer out <em>without</em> taking paging down with
 * it, because that is the behaviour the dock had before the drawer existed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class SuggestionBarDrawerGestureTest {

    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 160;
    /** Well past any plausible touch slop, in both axes. */
    private static final float TRAVEL = 300f;

    private static final int CLAIM_PENDING = 0;
    private static final int CLAIM_PAGE_SWIPE = 1;
    private static final int CLAIM_DRAWER_DRAG = 2;

    private Context context;
    private SuggestionBarView row;
    private RecordingChild child;
    private RecordingListener listener;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        // The veto sweep re-runs this between cases and one of the vetoes is the orientation, which
        // lives on a Configuration shared for the whole test.
        context.getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
        row = new SuggestionBarView(context, null);
        child = new RecordingChild(context);
        row.addView(child, new ViewGroup.LayoutParams(ROW_WIDTH / 2, ROW_HEIGHT));
        row.measure(
            View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW_HEIGHT, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, ROW_WIDTH, ROW_HEIGHT);
        listener = new RecordingListener();
        row.setAppDrawerGestureListener(listener);
    }

    // ------------------------------------------------------------------ the claim

    @Test
    public void aDownwardPull_runsTheDrawerOnceAndCancelsTheChildOnce() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + TRAVEL);
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + (TRAVEL * 1.5f));
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + (TRAVEL * 2f));
        dispatch(MotionEvent.ACTION_UP, 100f, 20f + (TRAVEL * 2f));

        assertEquals(1, listener.begins);
        assertEquals(1, listener.ends);
        assertEquals(0, listener.cancels);
        // The claiming move begins the drag from the down point and contributes its own travel;
        // the two after it continue driving it.
        assertEquals(3, listener.drags);
        assertEquals(1, child.countOf(MotionEvent.ACTION_CANCEL));
        // The drag is anchored to where the finger went down, not to where the claim landed.
        assertEquals(20f, listener.downRawY, 0.01f);
    }

    @Test
    public void aClaimedDrag_leavesNoLongPressSuppressionBehind() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + TRAVEL);
        dispatch(MotionEvent.ACTION_UP, 100f, 20f + TRAVEL);

        // Cleared on the drawer's own path: the two lines that used to clear it sit further down
        // the UP branch, which a claimed drag returns before ever reaching. Left up, every later
        // long-press on the dock is silently swallowed.
        boolean suppressed = ReflectionHelpers.getField(row, "suppressContextLongPressForSwipe");
        assertFalse(suppressed);
        // ...and the claim is released, so the next stream starts from scratch.
        assertEquals(CLAIM_PENDING, claim());
    }

    @Test
    public void aCancelledDrag_isForwardedToTheController() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + TRAVEL);
        dispatch(MotionEvent.ACTION_CANCEL, 100f, 20f + TRAVEL);

        assertEquals(1, listener.begins);
        assertEquals(1, listener.cancels);
        assertEquals(0, listener.ends);
        assertEquals(CLAIM_PENDING, claim());
    }

    @Test
    public void aHorizontalSwipe_stillPagesAndNeverTouchesTheDrawer() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 80f);
        dispatch(MotionEvent.ACTION_MOVE, 100f + TRAVEL, 80f);
        assertEquals(CLAIM_PAGE_SWIPE, claim());
        dispatch(MotionEvent.ACTION_MOVE, 100f + (TRAVEL * 1.5f), 80f);
        dispatch(MotionEvent.ACTION_UP, 100f + (TRAVEL * 1.5f), 80f);

        assertEquals(0, listener.begins);
        assertEquals(0, listener.drags);
        assertEquals(0, listener.ends);
        // No synthetic cancel on the paging path — the child sees its own UP and bounces back.
        assertEquals(0, child.countOf(MotionEvent.ACTION_CANCEL));
        assertEquals(1, child.countOf(MotionEvent.ACTION_UP));
    }

    @Test
    public void theLatchIsOneWay_soASidewaysDriftCannotStealAClaimedDrag() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + TRAVEL);
        assertEquals(CLAIM_DRAWER_DRAG, claim());
        dispatch(MotionEvent.ACTION_MOVE, 100f + (TRAVEL * 3f), 20f + TRAVEL);

        assertEquals(CLAIM_DRAWER_DRAG, claim());
        assertEquals(1, listener.begins);
        assertEquals(1, child.countOf(MotionEvent.ACTION_CANCEL));
    }

    // ------------------------------------------------------------------ eligibility

    @Test
    public void everyVetoBlocksTheDrawerAndLeavesThePageSwipeIntact() {
        List<String> failures = new ArrayList<>();
        // "searchText" is deliberately absent: a filtered apps row no longer vetoes the pull-down
        // (the vertical pull is unambiguous even over filtered results).
        for (String veto : new String[] {"pref", "surfaceEditor", "palette", "engaged", "full",
            "azLetter", "landscape", "activePickup", "noListener"}) {
            setUp();
            applyVeto(veto);

            dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
            dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + TRAVEL);
            dispatch(MotionEvent.ACTION_UP, 100f, 20f + TRAVEL);
            if (listener.begins != 0 || claim() == CLAIM_DRAWER_DRAG)
                failures.add(veto + ": drawer claimed anyway");
            if (child.countOf(MotionEvent.ACTION_CANCEL) != 0)
                failures.add(veto + ": child was cancelled anyway");

            dispatch(MotionEvent.ACTION_DOWN, 100f, 80f);
            dispatch(MotionEvent.ACTION_MOVE, 100f + TRAVEL, 80f);
            if (claim() != CLAIM_PAGE_SWIPE) failures.add(veto + ": page swipe was lost");
            dispatch(MotionEvent.ACTION_UP, 100f + TRAVEL, 80f);
        }
        assertEquals("[]", failures.toString());
    }

    @Test
    public void aFilteredAppsRowStillOpensTheDrawer() {
        ReflectionHelpers.setField(row, "lastInput", "ls ");

        dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + TRAVEL);

        assertEquals(CLAIM_DRAWER_DRAG, claim());
        assertEquals(1, listener.begins);
    }

    private void applyVeto(String veto) {
        switch (veto) {
            case "pref": listener.drawerEnabled = false; break;
            case "surfaceEditor": listener.surfaceEditor = true; break;
            case "palette": listener.paletteOpen = true; break;
            case "engaged": listener.drawerEngaged = true; break;
            case "full": listener.fullStatusPaneClosed = false; break;
            case "searchText": ReflectionHelpers.setField(row, "lastInput", "ls "); break;
            case "azLetter":
                ReflectionHelpers.setField(row, "activeAzLetter", Character.valueOf('A'));
                break;
            case "landscape":
                context.getResources().getConfiguration().orientation =
                    Configuration.ORIENTATION_LANDSCAPE;
                break;
            case "activePickup": ReflectionHelpers.setField(row, "folderDragHoverIndex", 2); break;
            case "noListener": row.setAppDrawerGestureListener(null); break;
            default: throw new IllegalArgumentException(veto);
        }
    }

    // ------------------------------------------------------------------ choreography

    @Test
    public void pinnedIconsFadeOutStaggeredAndComeBackExactly() {
        for (int i = 0; i < 3; i++) {
            row.addView(new RecordingChild(context), new ViewGroup.LayoutParams(60, ROW_HEIGHT));
        }

        row.setDrawerTransitionProgress(0.20f);
        View first = row.getChildAt(0);
        View last = row.getChildAt(row.getChildCount() - 1);
        assertTrue(first.getAlpha() < 1f);
        // Later slots are still further from gone: that is the whole point of the stagger.
        assertTrue(last.getAlpha() > first.getAlpha());
        assertTrue(first.getScaleX() < 1f);
        assertTrue(first.getTranslationY() > 0f);

        row.setDrawerTransitionProgress(1f);
        assertEquals(0f, last.getAlpha(), 0.001f);
        assertEquals(0.92f, last.getScaleX(), 0.001f);

        row.setDrawerTransitionProgress(0f);
        for (int i = 0; i < row.getChildCount(); i++) {
            View view = row.getChildAt(i);
            assertEquals(1f, view.getAlpha(), 0.001f);
            assertEquals(1f, view.getScaleX(), 0.001f);
            assertEquals(1f, view.getScaleY(), 0.001f);
            assertEquals(0f, view.getTranslationY(), 0.001f);
        }
    }

    @Test
    public void resetTransientVisualState_doesNotStompATransitionInFlight() {
        row.setDrawerTransitionProgress(0.6f);
        float faded = row.getChildAt(0).getAlpha();
        assertNotEquals(1f, faded, 0.001f);

        // The HOME → relaunch path: without the guard this restores every child to alpha 1 in the
        // middle of a fade the controller is still driving, and the drawer's next frame is the only
        // thing that would put them back.
        row.resetTransientVisualState();
        assertEquals(faded, row.getChildAt(0).getAlpha(), 0.001f);

        row.setDrawerTransitionProgress(0f);
        row.resetTransientVisualState();
        assertEquals(1f, row.getChildAt(0).getAlpha(), 0.001f);
    }

    @Test
    public void appsRowScreenRect_isTheRowsOwnBounds() {
        Rect rect = new Rect();
        row.getAppsRowScreenRect(rect);
        assertEquals(ROW_WIDTH, rect.width());
        assertEquals(ROW_HEIGHT, rect.height());
    }

    // ------------------------------------------------------------------ plumbing

    private int claim() {
        return ReflectionHelpers.getField(row, "gestureClaim");
    }

    private void dispatch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        try {
            row.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** A pinned-icon stand-in that consumes its stream, so the row has a real touch target. */
    private static final class RecordingChild extends View {

        private final List<Integer> actions = new ArrayList<>();

        RecordingChild(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            actions.add(event.getActionMasked());
            return true;
        }

        int countOf(int action) {
            int count = 0;
            for (Integer seen : actions) {
                if (seen == action) count++;
            }
            return count;
        }
    }

    private static final class RecordingListener
        implements SuggestionBarView.AppDrawerGestureListener {

        boolean drawerEnabled = true;
        boolean surfaceEditor = false;
        boolean paletteOpen = false;
        boolean drawerEngaged = false;
        boolean fullStatusPaneClosed = true;

        int begins;
        int drags;
        int ends;
        int cancels;
        float downRawY;

        @Override
        public boolean isAppDrawerEnabled() {
            return drawerEnabled;
        }

        @Override
        public boolean isSurfaceEditorActive() {
            return surfaceEditor;
        }

        @Override
        public boolean isCommandPaletteOpen() {
            return paletteOpen;
        }

        @Override
        public boolean isAppDrawerEngaged() {
            return drawerEngaged;
        }

        @Override
        public boolean isFullStatusPaneClosed() {
            return fullStatusPaneClosed;
        }

        @Override
        public void onDrawerDragBegin(float downRawY) {
            begins++;
            this.downRawY = downRawY;
        }

        @Override
        public void onDrawerDrag(float rawY) {
            drags++;
        }

        @Override
        public void onDrawerDragEnd(float velocityPxPerSec) {
            ends++;
        }

        @Override
        public void onDrawerDragCancel() {
            cancels++;
        }
    }
}
