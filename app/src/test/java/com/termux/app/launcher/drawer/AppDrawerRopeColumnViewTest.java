package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.view.NestedScrollingChild;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The A-Z column's touch contract and its place in the drawer's one animation loop.
 *
 * <p>The cases here are the ones the design exists to make true rather than restatements of
 * {@link AppDrawerRopeMetrics}, which is tested on its own. A stream that lands on the strip is a
 * scrub for its whole life and nothing about the motion can change that; only Y is read after the
 * down, so a finger that drifts off the strip keeps scrubbing; the scrub ends exactly once whichever
 * way the stream ends; and the view owns no time source of its own, because the plane's growing
 * rectangle and the letters inside it have to be rendered on the same frame.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerRopeColumnViewTest {

    private static final int HEIGHT = 1200;
    private static final float FRAME = 1f / 60f;

    private Context context;
    private AppDrawerRopeColumnView column;
    private RecordingParent parent;
    private RecordingCallbacks callbacks;
    private int width;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        float density = context.getResources().getDisplayMetrics().density;
        width = Math.round(AppDrawerRopeMetrics.resolveColumnWidthPx(density));

        column = new AppDrawerRopeColumnView(context);
        callbacks = new RecordingCallbacks();
        column.setCallbacks(callbacks);
        column.setLetters(AppDrawerSectionIndex.AZ_ORDER.toCharArray());
        column.setActive(true);

        parent = new RecordingParent(context);
        parent.addView(column, new FrameLayout.LayoutParams(width, HEIGHT));
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, width, HEIGHT);
    }

    // ------------------------------------------------------------------ the claim

    @Test
    public void aDownClaimsTheStreamAndReportsTheLetterUnderItWithNoSlopAndNoTimeout() {
        // A tap and a scrub are the same code path: the scrub is live from ACTION_DOWN, so there is
        // nothing to wait for and nothing to move past.
        assertTrue(down(centerY(0)));

        assertEquals(1, callbacks.letters.size());
        assertEquals(Character.valueOf('A'), callbacks.letters.get(0));
        assertTrue(column.isScrubbing());
        assertEquals('A', column.activeLetter());
        // Insurance against an ancestor added later intercepting halfway down the alphabet.
        assertTrue(parent.disallowIntercept);
        assertEquals(0, callbacks.ends);
    }

    @Test
    public void afterTheDownOnlyYIsReadSoAFingerDriftingOntoTheGridKeepsScrubbing() {
        down(centerY(0));

        // Well off the left of the strip, i.e. over the grid: X was spent at the down deciding the
        // stream belongs here and must never be read again.
        assertTrue(move(-400f, centerY(5)));
        assertEquals(Character.valueOf('F'), last());
        assertTrue(move(-400f, centerY(26)));
        assertEquals(Character.valueOf('#'), last());
        assertTrue(column.isScrubbing());
    }

    @Test
    public void aBoundaryNeedsOvershootingBeforeTheLetterChanges() {
        AppDrawerRopeMetrics metrics = column.metrics();
        assertTrue(metrics != null);
        down(centerY(3));
        int reports = callbacks.letters.size();

        // A finger parked on the boundary must not flicker: every letter change scrolls the grid and
        // ticks the haptic, so a flicker is a buzzing, jumping list.
        float boundary = metrics.trackTopPx + (metrics.slotHeightPx * 4f);
        move(0f, boundary + (metrics.slotHeightPx * 0.1f));
        assertEquals(reports, callbacks.letters.size());

        move(0f, boundary + (metrics.slotHeightPx * 0.5f));
        assertEquals(reports + 1, callbacks.letters.size());
        assertEquals(Character.valueOf('E'), last());
    }

    @Test
    public void theScrubEndsExactlyOnceHoweverTheStreamEnds() {
        down(centerY(2));
        move(0f, centerY(4));
        assertTrue(up(centerY(4)));

        assertEquals(1, callbacks.ends);
        assertFalse(column.isScrubbing());
        assertEquals('\0', column.activeLetter());

        // A second end for one stream would release the highlight twice and, at the content level,
        // spend the arming a real grid pull had earned.
        assertFalse(up(centerY(4)));
        assertEquals(1, callbacks.ends);

        down(centerY(0));
        assertTrue(cancel(centerY(0)));
        assertEquals(2, callbacks.ends);
        assertFalse(up(centerY(0)));
        assertEquals(2, callbacks.ends);
    }

    @Test
    public void anInactiveColumnTakesNoTouchesAndDrawsNothing() {
        column.setActive(false);

        assertFalse(down(centerY(0)));
        assertTrue(callbacks.letters.isEmpty());
        assertFalse(column.isScrubbing());
        assertFalse(column.isActive());
        // Its strip resolves to CHROME at the content level, so B-1's close drag runs there; that
        // only works if nothing here claims the down first.
        column.advance(1f, FRAME, false);
        assertEquals(0f, column.getAlpha(), 0.001f);

        // And with no letters at all it is inactive even when told otherwise.
        column.setLetters(new char[0]);
        column.setActive(true);
        assertFalse(column.isActive());
        assertFalse(down(centerY(0)));
    }

    @Test
    public void aChangedLetterSetDropsTheScrubItWouldOtherwiseMisreport() {
        down(centerY(6));
        assertEquals(Character.valueOf('G'), last());

        // Index 6 means a different letter in a different set, and the finger is holding an index.
        column.setLetters("ABCDEF".toCharArray());
        assertFalse(column.isScrubbing());
        assertEquals(1, callbacks.ends);
        assertEquals(6, column.letterCount());
    }

    // ------------------------------------------------------------------ the loop

    @Test
    public void theRopeRidesTheCallersFramesAndNeverNestedScrolls() {
        // No Choreographer, no ValueAnimator, no nested scrolling: the only motion this view has is
        // whatever advance() is handed, and the only report it makes is a letter.
        assertFalse(NestedScrollingChild.class.isAssignableFrom(AppDrawerRopeColumnView.class));
        assertFalse(column.isNestedScrollingEnabled());

        // The fade is a pure function of progress and is over well before the anchor is home, so the
        // settle happens in full view.
        column.advance(AppDrawerRopeMetrics.COLUMN_IN_START * 0.5f, FRAME, false);
        assertEquals(0f, column.getAlpha(), 0.001f);
        column.advance(AppDrawerRopeMetrics.COLUMN_ALPHA_END, FRAME, false);
        assertEquals(1f, column.getAlpha(), 0.001f);

        // Driven, it asks for more frames; left at rest with the anchor home, it asks for none, or
        // the controller's loop would never stop on an idle open drawer.
        assertTrue(column.advance(0f, FRAME, false));
        int frames = 0;
        while (column.advance(1f, FRAME, false) && frames < 240) frames++;
        assertTrue("never settled: " + frames, frames < 240);
        assertFalse(column.advance(1f, FRAME, false));

        // Reduced motion collapses the chain in one call rather than snapping it to the anchor.
        column.advance(0f, FRAME, false);
        assertFalse(column.advance(0f, FRAME, true));
    }

    // ------------------------------------------------------------------ plumbing

    private float centerY(int index) {
        AppDrawerRopeMetrics metrics = column.metrics();
        return metrics == null ? 0f : metrics.centerYForIndex(index);
    }

    private Character last() {
        return callbacks.letters.isEmpty() ? null
            : callbacks.letters.get(callbacks.letters.size() - 1);
    }

    private boolean down(float y) {
        return dispatch(MotionEvent.ACTION_DOWN, width * 0.5f, y);
    }

    private boolean move(float x, float y) {
        return dispatch(MotionEvent.ACTION_MOVE, x, y);
    }

    private boolean up(float y) {
        return dispatch(MotionEvent.ACTION_UP, width * 0.5f, y);
    }

    private boolean cancel(float y) {
        return dispatch(MotionEvent.ACTION_CANCEL, width * 0.5f, y);
    }

    private boolean dispatch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        try {
            return column.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** Records the one thing the column asks of its ancestors. */
    private static final class RecordingParent extends FrameLayout {

        boolean disallowIntercept;

        RecordingParent(Context context) {
            super(context);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallow) {
            disallowIntercept = disallow;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private static final class RecordingCallbacks implements AppDrawerRopeColumnView.Callbacks {

        final List<Character> letters = new ArrayList<>();
        int ends;

        @Override
        public void onScrubLetterChanged(char letter) {
            letters.add(letter);
        }

        @Override
        public void onScrubEnded() {
            ends++;
        }
    }
}
