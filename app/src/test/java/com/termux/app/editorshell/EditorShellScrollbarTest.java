package com.termux.app.editorshell;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.widget.ScrollView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * A view built with the one-argument constructor never reads the scrollbar attributes off its
 * theme — only the inflating constructors do. Turning the scrollbar on without handing over the
 * drawables therefore leaves the platform with nothing to draw, and it throws inside
 * {@code View.onDrawScrollBars} the moment a body is long enough to show one: the Layout editor
 * took the whole app down that way. These pin that the scrollbar is only ever switched on
 * together with a thumb to draw it with.
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
            assertFalse("it says how much is below, so it never fades out",
                scroller.isScrollbarFadingEnabled());
        } else {
            // Nothing can be handed over here, so the view keeps whatever its own style set up —
            // which is a scrollbar the platform already has a drawable for, or none at all. What
            // must not happen is this code putting it into the persistent mode it cannot dress.
            assertTrue("an undressed bar is left exactly as the platform made it",
                scroller.isScrollbarFadingEnabled());
        }
    }
}
