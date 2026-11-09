package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Who lets go of the display's held size. The keyboard mode switch holds it over the next
 * layout and lets go of its own hold only; a status-bar fold that begins meanwhile takes the
 * hold over, and only the fold's own finish ends it. The display view itself cannot exist on the
 * JVM (it binds the native server), so the hold is taken through the size-taking seam.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11PaneFrameHostSizeHoldTest {

    private X11PaneFrame page;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout wall = new FrameLayout(activity);
        activity.setContentView(wall);
        page = new X11PaneFrame(activity);
        wall.addView(page, new FrameLayout.LayoutParams(600, 800));
        idle(0);
        assertEquals(800, page.getHeight());
    }

    private static void idle(long ms) {
        if (ms == 0) shadowOf(Looper.getMainLooper()).idle();
        else shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    @Test
    public void theSwitchsHoldLetsGoOnceTheNewHeightHasSettled() {
        page.holdHostSizeOverNextLayout(600, 800);
        assertTrue(page.isHostSizeHeld());

        page.layout(0, 0, 600, 560);
        assertTrue("held through the pass that moved it", page.isHostSizeHeld());
        idle(0);
        assertTrue("and a frame on, in case another pass follows", page.isHostSizeHeld());
        idle(60);
        assertFalse(page.isHostSizeHeld());
    }

    @Test
    public void aHeightThatSettlesOverSeveralPassesIsOneResize() {
        page.holdHostSizeOverNextLayout(600, 800);

        // The pad host, the accessory stack and the overlay decision land in separate passes.
        int[] heights = {560, 900, 560, 560};
        for (int h : heights) {
            page.layout(0, 0, 600, h);
            idle(20);
            assertTrue("held while the height is still moving (" + h + ")", page.isHostSizeHeld());
        }
        idle(60);
        assertFalse(page.isHostSizeHeld());
    }

    @Test
    public void aHeightThatNeverMovesLetsGoAtTheBackstop() {
        page.holdHostSizeOverNextLayout(600, 800);
        idle(100);
        assertTrue(page.isHostSizeHeld());
        idle(100);
        assertFalse(page.isHostSizeHeld());
    }

    @Test
    public void aFoldThatBeginsUnderTheHoldTakesItOver() {
        page.holdHostSizeOverNextLayout(600, 800);
        // The status bar starts folding before the switch's layout lands.
        page.beginHostResize(600, 800);

        page.layout(0, 0, 600, 560);
        idle(400);
        assertTrue("the switch's release does not end the fold's hold", page.isHostSizeHeld());

        page.finishHostResize();
        assertFalse(page.isHostSizeHeld());
    }

    @Test
    public void aHoldAfterAFinishedFoldIsItsOwnAgain() {
        page.holdHostSizeOverNextLayout(600, 800);
        page.beginHostResize(600, 800);
        page.finishHostResize();

        page.holdHostSizeOverNextLayout(600, 800);
        assertTrue(page.isHostSizeHeld());
        idle(200);
        assertFalse(page.isHostSizeHeld());
    }

    @Test
    public void aHoldAlreadyTakenByAFoldIsLeftToIt() {
        page.beginHostResize(600, 800);
        page.holdHostSizeOverNextLayout(600, 800);
        idle(400);
        assertTrue(page.isHostSizeHeld());
        page.finishHostResize();
        assertFalse(page.isHostSizeHeld());
    }

    /**
     * The display announces every height it is measured at to the X server, so a hold that lets
     * the frame measure it at the frame's new height first, and only then at the held one, sends
     * the new size and the old one back. Under the hold it is only ever measured at the held size.
     */
    @Test
    public void theHeldDisplayIsNeverMeasuredAtTheFramesNewHeight() {
        HeightRecorder display = new HeightRecorder(page.getContext());
        display.setId(R.id.x11_display_view);
        page.addView(display, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        idle(0);
        assertEquals(800, display.getHeight());

        page.holdHostSizeOverNextLayout(600, 800);
        display.heights.clear();
        page.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, 600, 560);
        assertTrue(page.isHostSizeHeld());
        assertFalse("measured at " + display.heights + " under the hold",
            display.heights.contains(560));
        assertEquals(800, display.getHeight());

        idle(200);
        assertFalse(page.isHostSizeHeld());
        page.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, 600, 560);
        assertEquals(560, display.getHeight());
    }

    /** Stands in for the display view, which cannot exist on the JVM: notes each measured height. */
    private static final class HeightRecorder extends View {
        final List<Integer> heights = new ArrayList<>();

        HeightRecorder(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            heights.add(getMeasuredHeight());
        }
    }
}
