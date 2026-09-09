package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * When the display's surface may be on screen. The wall parks the place it is not showing by
 * making that page {@code INVISIBLE}, and a {@code SurfaceView}'s surface does not follow an
 * ancestor's visibility — so the page has to hand its own down, or the X screen shows through
 * whatever place the wall is actually resting on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11PaneFrameSurfaceVisibilityTest {

    /**
     * The frame inside a wall stand-in, as the pane wall holds it. The display view its layout
     * carries binds the native server on construction, so it cannot exist on the JVM; the
     * visibility the page would hand it is read from the page instead.
     */
    private static X11PaneFrame pageOnWall(Activity activity, ViewGroup wall) {
        X11PaneFrame page = new X11PaneFrame(activity);
        wall.addView(page, new FrameLayout.LayoutParams(600, 800));
        return page;
    }

    private static FrameLayout wall(Activity activity) {
        FrameLayout wall = new FrameLayout(activity);
        activity.setContentView(wall);
        return wall;
    }

    @Test
    public void aRunningDisplayOnTheShowingPlaceIsOnScreen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = pageOnWall(activity, wall(activity));
        page.applyRunning(true);
        assertEquals(View.VISIBLE, page.displaySurfaceVisibility());
    }

    @Test
    public void theWallParkingThePlaceTakesTheSurfaceOffScreen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout wall = wall(activity);
        X11PaneFrame page = pageOnWall(activity, wall);
        page.applyRunning(true);
        // What PaneWallLayout does to the place it has left: the page itself, not the display.
        page.setVisibility(View.INVISIBLE);
        assertEquals(View.INVISIBLE, page.displaySurfaceVisibility());
    }

    @Test
    public void comingBackToThePlacePutsTheSurfaceOnScreenAgain() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = pageOnWall(activity, wall(activity));
        page.applyRunning(true);
        page.setVisibility(View.INVISIBLE);
        page.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, page.displaySurfaceVisibility());
    }

    @Test
    public void aParkedPlaceWhoseServerComesUpKeepsTheSurfaceOffScreen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = pageOnWall(activity, wall(activity));
        page.setVisibility(View.INVISIBLE);
        // The display starts while the wall rests on another place.
        page.applyRunning(true);
        assertEquals(View.INVISIBLE, page.displaySurfaceVisibility());
    }

    @Test
    public void aPlaceAddedToAWallRestingElsewhereStartsOffScreen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout wall = wall(activity);
        wall.setVisibility(View.INVISIBLE);
        X11PaneFrame page = pageOnWall(activity, wall);
        page.applyRunning(true);
        assertEquals(View.INVISIBLE, page.displaySurfaceVisibility());
    }

    @Test
    public void noDisplayRunningIsNeverOnScreen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = pageOnWall(activity, wall(activity));
        page.applyRunning(false);
        assertEquals(View.INVISIBLE, page.displaySurfaceVisibility());
    }
}
