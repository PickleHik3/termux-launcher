package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/**
 * The controller's three jobs: which places exist (and following the preferences when that
 * changes), keeping the page across an activity recreate, and building the side pages only when
 * they are asked for.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class PaneWallControllerTest {

    private static final class Host implements PaneWallController.Host {
        boolean terminalOnly;
        boolean widgets = true;
        boolean display = true;
        int interrupted;

        @Override public boolean reducedMotion() { return true; }
        @Override public boolean isTerminalOnly() { return terminalOnly; }
        @Override public boolean isWidgetsEnabled() { return widgets; }
        @Override public boolean isDisplayEnabled() { return display; }
        @Override public void onWallDragInterrupted() { interrupted++; }
    }

    private Activity activity;
    private PaneWallLayout wall;
    private Host host;
    private PaneWallController controller;

    @Before public void build() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        wall = new PaneWallLayout(activity);
        View terminal = new FrameLayout(activity);
        wall.addView(terminal);
        host = new Host();
        controller = new PaneWallController(wall, host);
        controller.attachTerminalPage(terminal);
        wall.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, 1080, 1800);
    }

    @Test public void thePlacesFollowThePreferences() {
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY),
            controller.pages());

        host.display = false;
        controller.refreshPages();
        assertEquals(Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL), controller.pages());

        host.terminalOnly = true;
        controller.refreshPages();
        assertEquals(Collections.singletonList(PaneWallPage.TERMINAL), controller.pages());
        assertFalse("nowhere to go, so nothing to drag", controller.canDrag());
    }

    @Test public void aPlaceSwitchedOffWhileShowingHandsTheWallBackToTheTerminal() {
        assertTrue(controller.goTo(PaneWallPage.DISPLAY, false));
        assertEquals(PaneWallPage.DISPLAY, controller.currentPage());

        host.display = false;
        controller.refreshPages();

        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        assertTrue(controller.isTerminalShowing());
    }

    @Test public void thePageSurvivesARecreateThroughSavedState() {
        controller.goTo(PaneWallPage.WIDGETS, false);
        Bundle state = new Bundle();
        controller.onSaveInstanceState(state);

        PaneWallLayout again = new PaneWallLayout(activity);
        again.addView(new FrameLayout(activity));
        PaneWallController recreated = new PaneWallController(again, host);
        recreated.attachTerminalPage(again.getChildAt(0));
        recreated.restoreInstanceState(state);

        assertEquals(PaneWallPage.WIDGETS, recreated.currentPage());
    }

    @Test public void restoreToleratesNothingAndNonsense() {
        controller.restoreInstanceState(null);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());

        controller.restoreInstanceState(new Bundle());
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());

        Bundle stale = new Bundle();
        stale.putString(PaneWallController.ARG_PAGE, "GONE_IN_THIS_VERSION");
        controller.restoreInstanceState(stale);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());

        // A page this install no longer has stays where it is: the terminal.
        host.display = false;
        controller.refreshPages();
        Bundle display = new Bundle();
        display.putString(PaneWallController.ARG_PAGE, PaneWallPage.DISPLAY.name());
        controller.restoreInstanceState(display);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void theSidePagesAreBuiltOnlyWhenAskedForAndOnlyOnce() {
        assertFalse(controller.hasWidgetsPage());
        assertNull(controller.displayPage());
        assertNull("registered on the wall only once built", wall.pageView(PaneWallPage.WIDGETS));

        LayoutInflater inflater = LayoutInflater.from(activity);
        controller.attachWidgetsPage(inflater);
        assertTrue(controller.hasWidgetsPage());
        View widgets = wall.pageView(PaneWallPage.WIDGETS);
        assertNotNull(widgets);
        assertEquals("added as the leftmost child", widgets, wall.getChildAt(0));

        controller.attachWidgetsPage(inflater);
        assertSame("a second call does not rebuild the grid", widgets,
            wall.pageView(PaneWallPage.WIDGETS));
        assertEquals(2, wall.getChildCount());
    }

    @Test public void wallGoParsesNamesAndDirections() {
        assertTrue(controller.goTo("display"));
        assertEquals(PaneWallPage.DISPLAY, controller.currentPage());
        assertTrue(controller.goTo("left"));
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
        assertTrue(controller.goTo("left"));
        assertEquals(PaneWallPage.WIDGETS, controller.currentPage());
        // Three places form a ring: left of the leftmost is the rightmost.
        assertTrue(controller.goTo("left"));
        assertEquals(PaneWallPage.DISPLAY, controller.currentPage());
        assertFalse(controller.goTo("nowhere"));
        controller.returnToTerminal(false);
        assertEquals(PaneWallPage.TERMINAL, controller.currentPage());
    }

    @Test public void aNavigationUnderALiveDragReachesTheHost() {
        assertTrue(controller.beginDrag());
        controller.dragTo(-200f);

        controller.goTo(PaneWallPage.DISPLAY, false);

        assertEquals(1, host.interrupted);
    }
}
