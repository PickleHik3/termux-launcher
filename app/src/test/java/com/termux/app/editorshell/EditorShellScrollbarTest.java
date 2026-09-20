package com.termux.app.editorshell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * The body scroller's two jobs: say there is more below without becoming furniture, and stop on a
 * whole row.
 *
 * <p>Both have a fault behind them. A view built with the one-argument constructor never reads the
 * scrollbar attributes off its theme — only the inflating constructors do — so turning the
 * scrollbar on without handing over a thumb leaves the platform with nothing to draw, and it throws
 * inside {@code View.onDrawScrollBars} the moment a body is long enough to show one: the Layout
 * editor took the whole app down that way. And the cut used to be worked out from row heights left
 * over from the previous layout, which on the first open did not exist.
 */
@RunWith(RobolectricTestRunner.class)
public class EditorShellScrollbarTest {

    private ScrollView applied() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ScrollView scroller = new ScrollView(EditorShellRows.scrollerContext(activity));
        EditorShellRows.applyBodyScroller(scroller);
        return scroller;
    }

    @Test
    public void theBodyAlwaysFades() {
        ScrollView scroller = applied();
        assertTrue("the fade covers the peek whatever the release",
            scroller.isVerticalFadingEdgeEnabled());
    }

    @Test
    public void theScrollbarIsOnlyOnWhenThereIsAThumbToDrawIt() {
        ScrollView scroller = applied();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTrue("the release can carry a thumb, so the bar is on",
                scroller.isVerticalScrollBarEnabled());
            assertNotNull("and the thumb is the thing that stops it throwing",
                scroller.getVerticalScrollbarThumbDrawable());
        } else {
            // Nothing can be handed over here, so the view keeps whatever its own style set up.
            assertTrue("an undressed bar is left exactly as the platform made it",
                scroller.isScrollbarFadingEnabled());
        }
    }

    @Test
    public void theMarkGoesAwayAfterTheFingerDoes() {
        ScrollView scroller = applied();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return;
        assertTrue("it is a position, not a piece of the card",
            scroller.isScrollbarFadingEnabled());
        assertEquals("about a second after the list stops",
            EditorShellRows.SCROLLBAR_FADE_DELAY_MS, scroller.getScrollBarDefaultDelayBeforeFade());
        assertNull("no track: a rule down the card's edge is not what the mark is for",
            scroller.getVerticalScrollbarTrackDrawable());
    }

    // --------------------------------------------------------------------------------- the cut

    /** A column of rows of the stated measured heights, inside a scroller. */
    private static ScrollView bodyOf(Activity activity, int... heightsPx) {
        ScrollView scroller = new ScrollView(EditorShellRows.scrollerContext(activity));
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        for (int height : heightsPx) {
            View row = new View(activity);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height));
            column.addView(row);
        }
        scroller.addView(column, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scroller.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.AT_MOST));
        return scroller;
    }

    @Test
    public void theCapComesBackToTheLastWholeRow() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        // A heading, a pick row and two number rows — 515px of body in the 481px of room pong's
        // Terminal place leaves under the miniature.
        ScrollView body = bodyOf(activity, 73, 126, 158, 158);
        int peek = EditorShellMetrics.px(EditorShellMetrics.PEEK_DP, 2.625f);
        assertEquals("the heading and two rows, then a peek of the last",
            73 + 126 + 158 + peek, EditorShellRows.wholeRowCapPx(body, 481, 2.625f));
    }

    @Test
    public void aBodyThatFitsKeepsTheWholeRoom() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ScrollView body = bodyOf(activity, 73, 126);
        assertEquals(900, EditorShellRows.wholeRowCapPx(body, 900, 2.625f));
    }

    @Test
    public void anEmptyBodyAsksForNothingBack() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(400, EditorShellRows.wholeRowCapPx(bodyOf(activity), 400, 2.625f));
        assertEquals(400, EditorShellRows.wholeRowCapPx(
            new ScrollView(EditorShellRows.scrollerContext(activity)), 400, 2.625f));
    }
}
