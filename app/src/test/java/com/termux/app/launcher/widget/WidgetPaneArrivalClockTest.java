package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;

import com.termux.app.launcher.home.HomePageTransition;
import com.termux.app.wall.FirstDrawGate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/**
 * A Home page that slides in holds its spring's clock until the page has drawn (#173), and what
 * waits for the settle - the chrome's re-arrangement - runs once it lands.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPaneArrivalClockTest {
    private final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();

    private WidgetPaneView pane() {
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneView pane = new WidgetPaneView(activity);
        activity.setContentView(pane);
        pane.setPageMotion(HomePageTransition.SLIDE, 1f);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(pane.getWidth() > 0);
        return pane;
    }

    @Test public void theArrivingPageStandsAtItsStartUntilTheFrameAfterItsDraw() {
        WidgetPaneView pane = pane();
        int[] landed = {0};
        pane.slideInFrom(1);
        assertTrue(pane.isContentSettling());
        assertEquals("posed one width to the side", 1f, pane.contentShiftFraction(), 1e-4f);
        pane.runWhenContentSettles(() -> landed[0]++);
        assertEquals(0, landed[0]);
        // Whether the draw comes or the gate's timeout does, the spring then runs to rest.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FirstDrawGate.TIMEOUT_MS + 3000));
        assertFalse(pane.isContentSettling());
        assertEquals(0f, pane.contentShiftFraction(), 1e-4f);
        assertEquals("what waited runs once, on landing", 1, landed[0]);
    }

    @Test public void aSettleCutShortStillLetsWhatWaitedRun() {
        WidgetPaneView pane = pane();
        int[] landed = {0};
        pane.slideInFrom(-1);
        pane.runWhenContentSettles(() -> landed[0]++);
        // The gate has not opened yet: a second arrival cuts the first settle short, and what
        // waited for it runs then, though that settle never started.
        pane.slideInFrom(1);
        assertTrue(pane.isContentSettling());
        assertEquals(1, landed[0]);
        pane.runWhenContentSettles(() -> landed[0]++);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FirstDrawGate.TIMEOUT_MS + 3000));
        assertEquals(2, landed[0]);
        // Nothing under way: at once.
        pane.runWhenContentSettles(() -> landed[0]++);
        assertEquals(3, landed[0]);
    }
}
