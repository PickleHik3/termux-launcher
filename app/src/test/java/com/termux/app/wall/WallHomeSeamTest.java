package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

/** The wall turns Home pages through its WallHome; its Host implements no Home method. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallHomeSeamTest {

    private static final class FakeHome implements WallHome {
        int pages = 2;
        int page;
        int press = 1;
        int shown = -1;
        int runFirst = -9, runLast = -9;

        @Override public int count() { return pages; }
        @Override public int index() { return page; }
        @Override public void show(int index) { shown = index; page = index; }
        @Override public void bindRun(int first, int last) { runFirst = first; runLast = last; }
        @Override public int pressPage() { return press; }
        @Override public float shiftFraction() { return 0f; }
    }

    /** Home, the Terminal, then a second Home page: two runs, so the second is not the default. */
    private final WallPages pages = WallPagesMigration.seed("home", 0, "home", 1)
        .add(WallPages.PageKind.HOME, WallPages.END, null).pages;
    private FakeHome home;
    private PaneWallController controller;

    @Before public void build() {
        FreshVelocityTrackers.drainPool();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        home = new FakeHome();
        PaneWallLayout layout = new PaneWallLayout(activity);
        FrameLayout terminal = new FrameLayout(activity);
        layout.addView(terminal);
        controller = new PaneWallController(layout, new PaneWallController.Host() {
            @Override public boolean reducedMotion() { return true; }
            @Override public boolean hasWindow() { return true; }
            @Override public WallPages wallPages() { return pages; }
        });
        controller.setHome(home);
        controller.attachTerminalPage(terminal);
        FrameLayout widgets = new FrameLayout(activity);
        layout.addView(widgets);
        layout.setPageView(PaneWallPage.WIDGETS, widgets);
        activity.setContentView(layout);
        layout.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1080, 1500);
        controller.refreshPages();
    }

    @Test public void theHomePressLandsOnTheSeamsPressPage() {
        home.shown = -1;
        controller.goHome(false);
        assertEquals(1, home.shown);
    }

    @Test public void theWallBindsItsRunThroughTheSeam() {
        controller.goHome(false);
        assertTrue("the run reached the seam", home.runFirst != -9);
        assertEquals(1, home.runFirst);
        assertEquals(1, home.runLast);
    }
}
