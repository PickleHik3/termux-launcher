package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A live neighbour parked beside the Workspace page ({@code GONE}) is laid out at the page's size
 * at rest, so a keyboard or chrome change resizes its shells then and a crossing lays it out at
 * the size it already has.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class ParkedNeighbourRoomTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1800;

    private PaneWallLayout wall;
    private View terminal;
    private FrameLayout previous;
    private FrameLayout next;

    @Before
    public void setUp() {
        FreshVelocityTrackers.drainPool();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        wall = new PaneWallLayout(activity);
        terminal = new FrameLayout(activity);
        wall.addView(terminal);
        wall.setReducedMotion(true);
        wall.setPages(PaneWallPolicy.availablePages(true, false, false));
        wall.setPageView(PaneWallPage.TERMINAL, terminal);
        previous = new FrameLayout(activity);
        previous.addView(new View(activity));
        next = new FrameLayout(activity);
        next.addView(new View(activity));
        wall.setInnerNeighbourViews(previous, next, null);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean hasInnerPage(PaneWallPage page, int s) {
                return page == PaneWallPage.TERMINAL;
            }
        });
        layOut();
    }

    private void layOut() {
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
    }

    @Test
    public void aParkedNeighbourHasTheHostsSizeAtRest() {
        assertEquals(View.GONE, previous.getVisibility());
        assertEquals(View.GONE, next.getVisibility());
        assertNotEquals(0, terminal.getHeight());
        assertEquals(terminal.getWidth(), previous.getWidth());
        assertEquals(terminal.getHeight(), previous.getHeight());
        assertEquals(terminal.getWidth(), next.getWidth());
        assertEquals(terminal.getHeight(), next.getHeight());
    }

    @Test
    public void aChromeChangeAtRestResizesTheParkedNeighbours() {
        wall.setPageBottomInsetPx(PaneWallPage.TERMINAL, 300);
        layOut();
        assertEquals(View.GONE, previous.getVisibility());
        assertEquals(View.GONE, next.getVisibility());
        assertEquals(HEIGHT - 300, terminal.getHeight());
        assertEquals(terminal.getHeight(), previous.getHeight());
        assertEquals(terminal.getHeight(), next.getHeight());
    }

    @Test
    public void aCrossingLaysTheNeighboursOutAtTheSizeTheyAlreadyHave() {
        wall.setPageBottomInsetPx(PaneWallPage.TERMINAL, 300);
        layOut();
        int previousHeight = previous.getHeight();
        int nextHeight = next.getHeight();
        assertNotEquals(0, nextHeight);
        wall.beginDrag();
        wall.dragTo(-600f);
        layOut();
        assertEquals(previousHeight, previous.getHeight());
        assertEquals(nextHeight, next.getHeight());
    }
}
