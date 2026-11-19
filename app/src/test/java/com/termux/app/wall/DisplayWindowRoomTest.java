package com.termux.app.wall;

import static org.junit.Assert.assertEquals;

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
import com.termux.app.page.PageKind;

/**
 * The display window stands in the Workspace's room: it ends where the Terminal's rest puts the
 * Workspace page's bottom, so a Resize display keyboard (a rest with the keyboard taking room)
 * shrinks it, and an Overlay one (the keyboard's height handed back) leaves it to the rows.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class DisplayWindowRoomTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1800;

    private PaneWallLayout wall;
    private View display;

    @Before
    public void setUp() {
        FreshVelocityTrackers.drainPool();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        wall = new PaneWallLayout(activity);
        View terminal = new FrameLayout(activity);
        display = new FrameLayout(activity);
        wall.addView(terminal);
        wall.addView(display);
        wall.setReducedMotion(true);
        wall.setPages(PaneWallPolicy.availablePages(true, false, true));
        wall.setPageView(PageKind.TERMINAL, terminal);
        wall.setPageView(PageKind.DISPLAY, display);
        layOut();
    }

    private void layOut() {
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
    }

    @Test
    public void theDisplayEndsWhereTheWorkspacesRoomPutsIt() {
        wall.setPageBottomInsetPx(PageKind.TERMINAL, 900);
        wall.goTo(PageKind.DISPLAY, false);
        layOut();
        assertEquals(900, wall.pageBottomInsetPx(PageKind.DISPLAY));
        assertEquals(HEIGHT - 900, display.getHeight());
    }

    @Test
    public void aRoomWrittenForTheDisplayIsTheWorkspaces() {
        wall.setPageBottomInsetPx(PageKind.DISPLAY, 400);
        assertEquals(400, wall.pageBottomInsetPx(PageKind.TERMINAL));
    }
}
