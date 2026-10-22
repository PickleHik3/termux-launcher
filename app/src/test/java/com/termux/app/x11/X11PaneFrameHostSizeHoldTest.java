package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;

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
    public void theSwitchsHoldLetsGoAFrameAfterTheNewHeight() {
        page.holdHostSizeOverNextLayout(600, 800);
        assertTrue(page.isHostSizeHeld());

        page.layout(0, 0, 600, 560);
        assertTrue("held through the pass that moved it", page.isHostSizeHeld());
        idle(0);
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
}
