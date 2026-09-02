package com.termux.app;

import android.os.Build;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertEquals;
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
     * the row, so it takes touches like the rest of it — and the letters do not move with it.
     */
    @Test
    public void chinPadding_addsTouchableSpaceUnderTheLettersWithoutMovingThem() {
        AzScrubRowView bare = layoutRow(48, 0);
        AzScrubRowView chinned = layoutRow(58, 10);

        assertEquals(48, bare.letterBandHeightPx());
        assertEquals(48, chinned.letterBandHeightPx());

        // The letters sit at the same place in the band, so the 10px lands below them.
        AzScrubRowView.LetterVisualMetrics bareMetrics = new AzScrubRowView.LetterVisualMetrics();
        AzScrubRowView.LetterVisualMetrics chinnedMetrics = new AzScrubRowView.LetterVisualMetrics();
        assertTrue(bare.getLetterVisualMetricsOnScreen('M', bareMetrics));
        assertTrue(chinned.getLetterVisualMetricsOnScreen('M', chinnedMetrics));
        assertEquals(bareMetrics.baselineRawY, chinnedMetrics.baselineRawY, 0.01f);

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
}
