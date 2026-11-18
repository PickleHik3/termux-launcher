package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The wall's pose is state the controller writes and tells its readers, one bit per part. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallPoseTest {

    private static final class Reader implements WallPose.Reader {
        int calls;
        int lastChanged;
        int seen;
        @Override public void onWallPose(@NonNull WallPose pose, int changed) {
            calls++;
            lastChanged = changed;
            seen |= changed;
        }
        void clear() { calls = 0; lastChanged = 0; seen = 0; }
    }

    private PaneWallController controller;
    private Reader reader;

    @Before public void build() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PaneWallLayout layout = new PaneWallLayout(activity);
        FrameLayout terminal = new FrameLayout(activity);
        layout.addView(terminal);
        controller = new PaneWallController(layout, new PaneWallController.Host() { },
            new WallFacts() {
                @Override public boolean reducedMotion() { return true; }
            });
        controller.attachTerminalPage(terminal);
        FrameLayout widgets = new FrameLayout(activity);
        layout.addView(widgets);
        layout.setPageView(PaneWallPage.WIDGETS, widgets);
        activity.setContentView(layout);
        layout.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1080, 1500);
        controller.refreshPages();
        reader = new Reader();
        controller.addPoseReader(reader);
    }

    @Test public void thePoseStartsAtRest() {
        WallPose pose = controller.pose();
        assertEquals(0f, pose.offsetPx(), 0f);
        assertEquals(1f, pose.terminalRimAlpha(), 0f);
        assertEquals(1f, pose.terminalSinkScale(), 0f);
        assertFalse(pose.terminalOffScreen());
    }

    @Test public void anOffsetChangeReachesTheReaderOnceWithItsBit() {
        controller.onWallOffsetChanged(0f);
        reader.clear();
        controller.onWallOffsetChanged(12f);
        assertEquals(12f, controller.pose().offsetPx(), 0f);
        assertEquals(1, Integer.bitCount(reader.seen & WallPose.OFFSET));
        assertTrue((reader.lastChanged & WallPose.OFFSET) != 0);
    }

    @Test public void aRimChangeReachesTheReaderWithItsBit() {
        controller.onWallOffsetChanged(controller.wall().getWidth() / 2f);
        assertTrue((reader.seen & WallPose.RIMS) != 0);
        assertTrue((reader.seen & WallPose.OFFSET) != 0);
        assertTrue(controller.pose().terminalRimAlpha() < 1f);
        reader.clear();
        controller.onWallOffsetChanged(0f);
        assertTrue((reader.seen & WallPose.RIMS) != 0);
        assertEquals(1f, controller.pose().terminalRimAlpha(), 0f);
    }

    @Test public void anOffScreenFlipReachesTheReaderOnceWithItsBit() {
        controller.onTerminalOffScreenChanged(true);
        assertEquals(1, reader.calls);
        assertEquals(WallPose.OFF_SCREEN, reader.lastChanged);
        assertTrue(controller.pose().terminalOffScreen());
        controller.onTerminalOffScreenChanged(false);
        assertEquals(2, reader.calls);
        assertFalse(controller.pose().terminalOffScreen());
    }

    @Test public void aTerminalSinkReachesTheReaderAndAWidgetsSinkDoesNot() {
        controller.onPageSinkChanged(PaneWallPage.TERMINAL, 0.9f);
        assertEquals(1, reader.calls);
        assertEquals(WallPose.SINK, reader.lastChanged);
        assertEquals(0.9f, controller.pose().terminalSinkScale(), 0f);

        reader.clear();
        controller.onPageSinkChanged(PaneWallPage.WIDGETS, 0.8f);
        assertEquals("only the Terminal page's sink is a pose", 0, reader.calls);
        assertEquals(0.9f, controller.pose().terminalSinkScale(), 0f);
    }
}
