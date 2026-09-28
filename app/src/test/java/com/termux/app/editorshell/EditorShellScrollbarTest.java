package com.termux.app.editorshell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.widget.ScrollView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * The body scroller's job: say there is more past the edge without becoming furniture.
 *
 * <p>It has a fault behind it. A view built with the one-argument constructor never reads the
 * scrollbar attributes off its theme — only the inflating constructors do — so turning the
 * scrollbar on without handing over a thumb leaves the platform with nothing to draw, and it throws
 * inside {@code View.onDrawScrollBars} the moment a body is long enough to show one: the Layout
 * editor took the whole app down that way.
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
}
