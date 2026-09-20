package com.termux.app;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@LooperMode(LooperMode.Mode.LEGACY)
public class AzScrubRowViewTest {

    @Test
    public void scrubMapping_isDeterministic() {
        AzScrubRowView view = new AzScrubRowView(RuntimeEnvironment.application);
        view.setInteractionMode(AzScrubRowView.InteractionMode.INLINE_EMPHASIS_TRACK);
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(540, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(48, android.view.View.MeasureSpec.EXACTLY)
        );
        view.layout(0, 0, 540, 48);

        final char[] lastLetter = {'?'};
        final int[] lastSelection = {-1};

        view.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, float touchX, float touchY, float rawX, float rawY, long eventTimeMs, AzScrubRowView.GesturePhase phase) {
                lastLetter[0] = letter;
                lastSelection[0] = selectionIndex;
            }

            @Override
            public void onCancel() {}
        });

        view.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 0f, 24f, 0));
        assertEquals(AzScrubRowView.PINNED_APPS_SYMBOL, lastLetter[0]);
        assertEquals(0, lastSelection[0]);

        view.onTouchEvent(MotionEvent.obtain(0, 15, MotionEvent.ACTION_MOVE, 30f, 24f, 0));
        assertEquals('A', lastLetter[0]);

        view.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 539f, 24f, 0));
        assertEquals('#', lastLetter[0]);

        view.onTouchEvent(MotionEvent.obtain(0, 30, MotionEvent.ACTION_MOVE, 200f, -40f, 0));
        assertTrue(lastSelection[0] >= 1);
    }

    @Test
    public void scrubMapping_usesBoundaryHysteresisDuringWaveTrack() {
        AzScrubRowView view = new AzScrubRowView(RuntimeEnvironment.application);
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(540, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(48, android.view.View.MeasureSpec.EXACTLY)
        );
        view.layout(0, 0, 540, 48);

        final char[] lastLetter = {'?'};
        view.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, float touchX, float touchY, float rawX, float rawY, long eventTimeMs, AzScrubRowView.GesturePhase phase) {
                lastLetter[0] = letter;
            }

            @Override
            public void onCancel() {}
        });

        view.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 0f, 24f, 0));
        assertEquals(AzScrubRowView.PINNED_APPS_SYMBOL, lastLetter[0]);

        float slotWidth = 540f / 28f;

        // Cross the raw slot boundary a little, but not far enough to commit the neighboring slot.
        view.onTouchEvent(MotionEvent.obtain(0, 15, MotionEvent.ACTION_MOVE, slotWidth + (slotWidth * 0.10f), 24f, 0));
        assertEquals(AzScrubRowView.PINNED_APPS_SYMBOL, lastLetter[0]);

        // Move deeper into the next slot and confirm the letter now advances.
        view.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, slotWidth + (slotWidth * 0.30f), 24f, 0));
        assertEquals('A', lastLetter[0]);
    }

    /**
     * The chin the dock hands this row when it is the bottom one: space under the letters, inside
     * the row, so it takes touches like the rest of it. The letters ride the row's own centre
     * line, chin included, so a chin carries them down by half of itself.
     */
    @Test
    public void chinPadding_addsTouchableSpaceAndCarriesTheLettersToTheNewCentre() {
        AzScrubRowView bare = layoutRow(48, 0);
        AzScrubRowView chinned = layoutRow(58, 10);

        assertEquals(48, bare.letterBandThicknessPx());
        assertEquals(48, chinned.letterBandThicknessPx());

        AzScrubRowView.LetterVisualMetrics bareMetrics = new AzScrubRowView.LetterVisualMetrics();
        AzScrubRowView.LetterVisualMetrics chinnedMetrics = new AzScrubRowView.LetterVisualMetrics();
        assertTrue(bare.getLetterVisualMetricsOnScreen('M', bareMetrics));
        assertTrue(chinned.getLetterVisualMetricsOnScreen('M', chinnedMetrics));
        assertEquals(bareMetrics.baselineRawY + 5f, chinnedMetrics.baselineRawY, 0.01f);

        // A touch down in the chin, below every glyph, still picks the letter above it.
        final char[] letter = {'?'};
        final int[] selection = {-1};
        chinned.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char l, int selectionIndex, float touchX, float touchY, float rawX,
                                float rawY, long eventTimeMs, AzScrubRowView.GesturePhase phase) {
                letter[0] = l;
                selection[0] = selectionIndex;
            }

            @Override
            public void onCancel() {}
        });
        chinned.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 0f, 54f, 0));
        assertEquals(AzScrubRowView.PINNED_APPS_SYMBOL, letter[0]);
        assertEquals(0, selection[0]);
    }

    /**
     * The drag-up selection step is a fraction of the letter band, not of the row, so hiding the
     * extra-keys row (which is what grows this one) must not retune it.
     */
    @Test
    public void chinPadding_doesNotRetuneTheDragUpSelectionStep() {
        assertEquals(selectionIndexForDragUp(layoutRow(48, 0), -40f),
            selectionIndexForDragUp(layoutRow(58, 10), -40f));
    }

    private static AzScrubRowView layoutRow(int heightPx, int chinPx) {
        AzScrubRowView view = new AzScrubRowView(RuntimeEnvironment.application);
        view.setInteractionMode(AzScrubRowView.InteractionMode.INLINE_EMPHASIS_TRACK);
        view.setChinPaddingPx(chinPx);
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(540, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
        );
        view.layout(0, 0, 540, heightPx);
        return view;
    }

    private static int selectionIndexForDragUp(AzScrubRowView view, float y) {
        final int[] selection = {-1};
        view.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, float touchX, float touchY,
                                float rawX, float rawY, long eventTimeMs,
                                AzScrubRowView.GesturePhase phase) {
                selection[0] = selectionIndex;
            }

            @Override
            public void onCancel() {}
        });
        view.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 200f, 10f, 0));
        view.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 200f, y, 0));
        return selection[0];
    }

    /**
     * The letter tick belongs to whoever the finger is choosing with. A scrub that has climbed onto
     * the apps row goes on passing over letter slots, and ticking those made every part of the
     * gesture feel like a scrub along the letters.
     */
    @Test
    public void letterTicks_stopWhileTheFingerIsChoosingAnIconInstead() {
        assertTrue(crossedALetterBoundaryWithSuspension(false));
        assertFalse(crossedALetterBoundaryWithSuspension(true));
    }

    /**
     * The suspension is read after the callback, so the sample that hands the gesture to the apps
     * row is already silent — the row does not owe it one last letter tick.
     */
    @Test
    public void letterTicks_areDecidedBySampleNotByTheSampleBefore() {
        AzScrubRowView view = layoutRow(48, 0);
        view.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, float touchX, float touchY,
                                float rawX, float rawY, long eventTimeMs,
                                AzScrubRowView.GesturePhase phase) {
                // What the activity does with the decision this very sample produced.
                view.setLetterHapticTicksSuspended(true);
            }

            @Override
            public void onCancel() {}
        });
        view.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 0f, 24f, 0));
        view.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 400f, 24f, 0));
        assertFalse(tickedOn(view));
    }

    private static boolean crossedALetterBoundaryWithSuspension(boolean suspended) {
        AzScrubRowView view = layoutRow(48, 0);
        view.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, float touchX, float touchY,
                                float rawX, float rawY, long eventTimeMs,
                                AzScrubRowView.GesturePhase phase) {}

            @Override
            public void onCancel() {}
        });
        view.setLetterHapticTicksSuspended(suspended);
        view.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 0f, 24f, 0));
        view.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 400f, 24f, 0));
        return tickedOn(view);
    }

    private static boolean tickedOn(AzScrubRowView view) {
        return Shadows.shadowOf(view).lastHapticFeedbackPerformed()
            == HapticFeedbackConstants.CLOCK_TICK;
    }

    /**
     * A column reads the letters down its height, so the same fraction of the bar picks the same
     * letter it would along a row of that length — the axis is all that changes.
     */
    @Test
    public void verticalBar_picksLettersDownItsHeight() {
        AzScrubRowView column = layoutColumn(Edge.LEFT, 58, 540);
        final char[] letter = {'?'};
        column.setScrubCallback(recordLetter(letter));

        column.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 20f, 0f, 0));
        assertEquals(AzScrubRowView.PINNED_APPS_SYMBOL, letter[0]);

        column.onTouchEvent(MotionEvent.obtain(0, 15, MotionEvent.ACTION_MOVE, 20f, 30f, 0));
        assertEquals('A', letter[0]);

        column.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 20f, 539f, 0));
        assertEquals('#', letter[0]);

        // Sliding across the column, not down it, holds the letter it is already on.
        column.onTouchEvent(MotionEvent.obtain(0, 25, MotionEvent.ACTION_MOVE, 200f, 539f, 0));
        assertEquals('#', letter[0]);
    }

    /** The two side edges read the letters the same way down the screen. */
    @Test
    public void verticalBar_readsTheSameWayOnEitherSide() {
        final char[] left = {'?'};
        final char[] right = {'?'};
        AzScrubRowView leftBar = layoutColumn(Edge.LEFT, 58, 540);
        AzScrubRowView rightBar = layoutColumn(Edge.RIGHT, 58, 540);
        leftBar.setScrubCallback(recordLetter(left));
        rightBar.setScrubCallback(recordLetter(right));
        leftBar.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 20f, 300f, 0));
        rightBar.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 38f, 300f, 0));
        assertEquals(left[0], right[0]);
    }

    /**
     * The drag that opens a letter's later selections is a drag away from the bar, whichever way
     * that is: off the top of a bottom bar, and off the inner face of a side one.
     */
    @Test
    public void verticalBar_readsTheDragAwayFromItsOwnEdge() {
        AzScrubRowView leftBar = layoutColumn(Edge.LEFT, 58, 540);
        final int[] selection = {-1};
        leftBar.setScrubCallback(recordSelection(selection));
        leftBar.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 20f, 300f, 0));
        assertEquals(0, selection[0]);
        // Past the column's inner (content-facing) face is the away direction for a left bar.
        leftBar.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 140f, 300f, 0));
        assertTrue(selection[0] >= 1);

        AzScrubRowView rightBar = layoutColumn(Edge.RIGHT, 58, 540);
        final int[] rightSelection = {-1};
        rightBar.setScrubCallback(recordSelection(rightSelection));
        rightBar.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 38f, 300f, 0));
        assertEquals(0, rightSelection[0]);
        // For a right bar the away direction is the other way: left of the column.
        rightBar.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, -80f, 300f, 0));
        assertTrue(rightSelection[0] >= 1);
    }

    /** A top bar is still a row: the letters read across it and the drag goes down instead of up. */
    @Test
    public void topBar_keepsTheRowAndMirrorsTheDrag() {
        AzScrubRowView top = new AzScrubRowView(RuntimeEnvironment.application);
        top.setBarEdge(Edge.TOP);
        top.setInteractionMode(AzScrubRowView.InteractionMode.INLINE_EMPHASIS_TRACK);
        top.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(540, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(48, android.view.View.MeasureSpec.EXACTLY)
        );
        top.layout(0, 0, 540, 48);
        final char[] letter = {'?'};
        final int[] selection = {-1};
        top.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char l, int selectionIndex, float touchX, float touchY, float rawX,
                                float rawY, long eventTimeMs, AzScrubRowView.GesturePhase phase) {
                letter[0] = l;
                selection[0] = selectionIndex;
            }

            @Override
            public void onCancel() {}
        });
        top.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 30f, 24f, 0));
        assertEquals('A', letter[0]);
        assertEquals(0, selection[0]);
        // Below a top bar is away from it, so that is the drag the later selections read.
        top.onTouchEvent(MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 30f, 96f, 0));
        assertTrue(selection[0] >= 1);
    }

    /** The chin lands on the side the bar stands on, and never inside the letters' own band. */
    @Test
    public void chinPadding_landsOnTheEdgeTheBarStandsOn() {
        AzScrubRowView leftBar = layoutColumn(Edge.LEFT, 58, 540);
        leftBar.setChinPaddingPx(10);
        assertEquals(48, leftBar.letterBandThicknessPx());
        assertTrue(leftBar.getPaddingLeft() > leftBar.getPaddingRight());

        AzScrubRowView rightBar = layoutColumn(Edge.RIGHT, 58, 540);
        rightBar.setChinPaddingPx(10);
        assertTrue(rightBar.getPaddingRight() > rightBar.getPaddingLeft());

        AzScrubRowView top = new AzScrubRowView(RuntimeEnvironment.application);
        top.setBarEdge(Edge.TOP);
        top.setChinPaddingPx(10);
        assertTrue(top.getPaddingTop() > top.getPaddingBottom());
    }

    /**
     * A column's letter metrics stack down its height with the glyphs upright, and the wave lifts
     * them sideways — away from the edge — rather than up out of the bar.
     */
    @Test
    public void verticalBar_metricsStackDownTheColumn() {
        AzScrubRowView column = layoutColumn(Edge.LEFT, 58, 540);
        AzScrubRowView.LetterVisualMetrics first = new AzScrubRowView.LetterVisualMetrics();
        AzScrubRowView.LetterVisualMetrics later = new AzScrubRowView.LetterVisualMetrics();
        assertTrue(column.getLetterVisualMetricsOnScreen('A', first));
        assertTrue(column.getLetterVisualMetricsOnScreen('Z', later));
        // Later letters are further down the column, and both sit at the same place across it.
        assertTrue(later.baselineRawY > first.baselineRawY);
        assertEquals(first.centerRawX, later.centerRawX, 0.01f);
        // The glass is inside the column's own width.
        assertTrue(first.glassBoundsRaw.left >= 0f);
        assertTrue(first.glassBoundsRaw.right <= 58f);
    }

    /** A bottom bar is the canonical frame, so it hands the activity its raw touch point as-is. */
    @Test
    public void bottomBar_handsTheTouchPointThroughUnchanged() {
        AzScrubRowView view = layoutRow(48, 0);
        final float[] seen = {Float.NaN, Float.NaN};
        view.setScrubCallback(new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, float touchX, float touchY,
                                float rawX, float rawY, long eventTimeMs,
                                AzScrubRowView.GesturePhase phase) {
                seen[0] = touchX;
                seen[1] = touchY;
            }

            @Override
            public void onCancel() {}
        });
        view.onTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 137f, -22f, 0));
        assertEquals(137f, seen[0], 0.001f);
        assertEquals(-22f, seen[1], 0.001f);
    }

    private static AzScrubRowView layoutColumn(Edge edge, int widthPx, int heightPx) {
        AzScrubRowView view = new AzScrubRowView(RuntimeEnvironment.application);
        view.setBarEdge(edge);
        view.setInteractionMode(AzScrubRowView.InteractionMode.INLINE_EMPHASIS_TRACK);
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
        );
        view.layout(0, 0, widthPx, heightPx);
        return view;
    }

    private static AzScrubRowView.ScrubCallback recordLetter(char[] out) {
        return new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, float touchX, float touchY,
                                float rawX, float rawY, long eventTimeMs,
                                AzScrubRowView.GesturePhase phase) {
                out[0] = letter;
            }

            @Override
            public void onCancel() {}
        };
    }

    // ------------------------------------------------------- the horizontal letter baseline

    @Test
    public void horizontalLettersSitOnTheBarsOwnCentreLine() {
        // A 100px bar and a font 20 up, 6 down: the glyph's own centre lands on 50.
        float centred = AzScrubRowView.horizontalLetterBaselinePx(100f, -20f, 6f, 0f, Edge.BOTTOM);
        assertEquals(57f, centred, 0.001f);
        assertEquals("both bars rest on the same line",
            centred, AzScrubRowView.horizontalLetterBaselinePx(100f, -20f, 6f, 0f, Edge.TOP),
            0.001f);
        // Centred means centred: the glyph box's top gap equals its bottom gap.
        assertEquals((centred - 20f) - 0f, 100f - (centred + 6f), 0.001f);
    }

    @Test
    public void theWaveCarriesEachBarsLettersAwayFromItsEdge() {
        float rest = AzScrubRowView.horizontalLetterBaselinePx(100f, -20f, 6f, 0f, Edge.BOTTOM);
        // A bottom bar lifts its letters up the screen, which is a smaller baseline.
        assertEquals(rest - 15f,
            AzScrubRowView.horizontalLetterBaselinePx(100f, -20f, 6f, 15f, Edge.BOTTOM), 0.001f);
        // A top bar mirrors it and pushes them down.
        assertEquals(rest + 15f,
            AzScrubRowView.horizontalLetterBaselinePx(100f, -20f, 6f, 15f, Edge.TOP), 0.001f);
    }

    @Test
    public void theChinCountsTowardsTheCentreLine() {
        // The chin is part of the view's height, so a taller bar moves the letters down with it.
        float shortBar = AzScrubRowView.horizontalLetterBaselinePx(48f, -18f, 5f, 0f, Edge.BOTTOM);
        float withChin = AzScrubRowView.horizontalLetterBaselinePx(72f, -18f, 5f, 0f, Edge.BOTTOM);
        assertEquals(shortBar + 12f, withChin, 0.001f);
    }

    private static AzScrubRowView.ScrubCallback recordSelection(int[] out) {
        return new AzScrubRowView.ScrubCallback() {
            @Override
            public void onScrub(char letter, int selectionIndex, float touchX, float touchY,
                                float rawX, float rawY, long eventTimeMs,
                                AzScrubRowView.GesturePhase phase) {
                out[0] = selectionIndex;
            }

            @Override
            public void onCancel() {}
        };
    }
}
