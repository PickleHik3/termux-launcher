package com.termux.app.notice;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@LooperMode(LooperMode.Mode.LEGACY)
public class AppNoticePlacementTest {

    private static final int CHROME_ID = View.generateViewId();
    private static final int OTHER_CHROME_ID = View.generateViewId();

    @Test
    public void insetFloorIsTheBarsReachIntoTheAnchor() {
        // Edge to edge: the anchor starts at the window's top, so the whole bar is in the way.
        assertEquals(84, AppNoticePlacement.insetFloor(84, 0));
        // Content already below the status bar needs nothing for it, and never a negative offset.
        assertEquals(0, AppNoticePlacement.insetFloor(84, 84));
        assertEquals(0, AppNoticePlacement.insetFloor(84, 200));
        assertEquals(20, AppNoticePlacement.insetFloor(84, 64));
    }

    @Test
    public void chromeBottomIsInTheAnchorsCoordinates() {
        assertEquals(240, AppNoticePlacement.chromeBottom(84, 156, 0, 84, 0));
        assertEquals(156, AppNoticePlacement.chromeBottom(84, 156, 0, 0, 84));
        // A chrome above the anchor cannot push the chip up past the anchor's own top edge.
        assertEquals(0, AppNoticePlacement.chromeBottom(0, 40, 0, 0, 100));
    }

    @Test
    public void unlaidChromeIsGuessedFromItsMinimumHeightBelowTheFloor() {
        assertEquals(84 + 112, AppNoticePlacement.chromeBottom(0, 0, 112, 84, 0));
        assertEquals(112, AppNoticePlacement.chromeBottom(0, -1, 112, 0, 0));
    }

    @Test
    public void placementTopClearsWhicheverIsLowerByTheGap() {
        assertEquals(240 + 16, AppNoticePlacement.placementTop(84, 240, 2f));
        // No chrome at all: the pill still hangs below the status bar.
        assertEquals(84 + 16, AppNoticePlacement.placementTop(84, 0, 2f));
        assertEquals(0 + 24, AppNoticePlacement.placementTop(0, 0, 3f));
    }

    @Test
    public void hostHangsUnderTheChromeTheCallerNamed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout anchor = new FrameLayout(activity);
        activity.setContentView(anchor);
        View chrome = new View(activity);
        chrome.setId(CHROME_ID);
        FrameLayout.LayoutParams chromeParams = new FrameLayout.LayoutParams(400, 120);
        chromeParams.topMargin = 30;
        anchor.addView(chrome, chromeParams);
        AppNoticeHostView host = new AppNoticeHostView(activity);
        anchor.addView(host, AppNoticeHostView.buildHostLayoutParams(activity));

        AppNoticePlacement.attach(anchor, host, new int[] {OTHER_CHROME_ID, CHROME_ID});
        anchor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        anchor.layout(0, 0, 400, 800);
        Robolectric.flushForegroundThreadScheduler();

        float density = activity.getResources().getDisplayMetrics().density;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) host.getLayoutParams();
        assertEquals(30 + 120 + Math.round(8f * density), params.topMargin);
    }

    @Test
    public void defaultChromeIdsAreTheToolbarsAndTheWindowBarHost() {
        int[] ids = AppNoticePlacement.DEFAULT_CHROME_IDS;
        assertEquals(3, ids.length);
        assertEquals(com.termux.shared.R.id.toolbar_container, ids[0]);
        assertEquals(com.termux.shared.R.id.toolbar, ids[1]);
        assertEquals(com.termux.R.id.terminal_window_bar_host, ids[2]);
    }
}
