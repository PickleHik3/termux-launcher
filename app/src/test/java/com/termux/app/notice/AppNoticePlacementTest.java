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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    /** The band's edges are the terminal area's, expressed in the anchor it is laid out in. */
    @Test
    public void theBandTakesTheTerminalAreasOwnEdges() {
        assertEquals(0, AppNoticePlacement.bandEdge(0, 0));
        assertEquals(156, AppNoticePlacement.bandEdge(240, 84));
        // A window whose content already starts where the area does owes no offset, ever negative.
        assertEquals(0, AppNoticePlacement.bandEdge(84, 240));
    }

    /**
     * The entrance has to carry the pill entirely back through the rim, or a sliver of it shows
     * above the terminal for the length of the animation. Over-travelling is invisible behind the
     * clip, so an unmeasured pill is guessed generously rather than tightly.
     */
    @Test
    public void theEntranceCoversTheWholePillPlusTheGapItRestsBelow() {
        assertEquals(16 + 120, AppNoticePlacement.entranceRisePx(16, 120, 2f), .001f);
        // Never measured: the pill's own minimum, so the first notice of a session is hidden too.
        assertTrue(AppNoticePlacement.entranceRisePx(16, 0, 2f) > 16);
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
        FrameLayout frame = AppNoticeHostView.buildFrame(activity);
        AppNoticeHostView host = new AppNoticeHostView(activity);
        frame.addView(host, AppNoticeHostView.buildHostLayoutParams(activity));
        anchor.addView(frame, AppNoticeHostView.buildFrameLayoutParams());

        AppNoticePlacement.attach(anchor, frame, host, new int[] {OTHER_CHROME_ID, CHROME_ID});
        anchor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        anchor.layout(0, 0, 400, 800);
        Robolectric.flushForegroundThreadScheduler();

        float density = activity.getResources().getDisplayMetrics().density;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) frame.getLayoutParams();
        assertEquals(30 + 120 + Math.round(8f * density), params.topMargin);
        // Off the terminal there is no rim to come out of, so the band clips nothing.
        assertFalse(frame.getClipChildren());
    }

    /**
     * With a terminal on screen the pill belongs to it: the band takes the terminal area's own left
     * edge and width, so the pill centres over the whole of it however many panes it holds, and it
     * clips at the terminal's top edge so the pill arrives out of the rim rather than over the
     * window bar above it.
     */
    @Test
    public void theBandSitsInsideTheTerminalAreaAndClipsAtItsRim() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout anchor = new FrameLayout(activity);
        activity.setContentView(anchor);
        View terminal = new View(activity);
        terminal.setId(AppNoticePlacement.TERMINAL_AREA_ID);
        FrameLayout.LayoutParams terminalParams = new FrameLayout.LayoutParams(300, 500);
        terminalParams.leftMargin = 50;
        terminalParams.topMargin = 140;
        anchor.addView(terminal, terminalParams);
        FrameLayout frame = AppNoticeHostView.buildFrame(activity);
        AppNoticeHostView host = new AppNoticeHostView(activity);
        frame.addView(host, AppNoticeHostView.buildHostLayoutParams(activity));
        anchor.addView(frame, AppNoticeHostView.buildFrameLayoutParams());

        AppNoticePlacement.attach(anchor, frame, host);
        anchor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        anchor.layout(0, 0, 400, 800);
        Robolectric.flushForegroundThreadScheduler();

        FrameLayout.LayoutParams band = (FrameLayout.LayoutParams) frame.getLayoutParams();
        assertEquals(50, band.leftMargin);
        assertEquals(140, band.topMargin);
        assertEquals(300, band.width);
        assertTrue(frame.getClipChildren());
        // The pill rests a hair below the rim rather than sitting on it.
        int gap = Math.round(8f * activity.getResources().getDisplayMetrics().density);
        assertEquals(gap,
            ((ViewGroup.MarginLayoutParams) host.getLayoutParams()).topMargin);
    }

    /**
     * The keyboard, a split and a window bar all resize the terminal without the content root
     * moving, and the band has to follow every one of them.
     */
    @Test
    public void theBandFollowsTheTerminalWhenTheKeyboardMovesIt() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout anchor = new FrameLayout(activity);
        activity.setContentView(anchor);
        View terminal = new View(activity);
        terminal.setId(AppNoticePlacement.TERMINAL_AREA_ID);
        FrameLayout.LayoutParams terminalParams = new FrameLayout.LayoutParams(400, 500);
        terminalParams.topMargin = 140;
        anchor.addView(terminal, terminalParams);
        FrameLayout frame = AppNoticeHostView.buildFrame(activity);
        AppNoticeHostView host = new AppNoticeHostView(activity);
        frame.addView(host, AppNoticeHostView.buildHostLayoutParams(activity));
        anchor.addView(frame, AppNoticeHostView.buildFrameLayoutParams());

        AppNoticePlacement.attach(anchor, frame, host);
        layout(anchor);
        assertEquals(140, ((FrameLayout.LayoutParams) frame.getLayoutParams()).topMargin);

        // The in-app keyboard comes up: the terminal area starts lower and is shorter.
        terminalParams.topMargin = 60;
        terminalParams.height = 300;
        terminal.setLayoutParams(terminalParams);
        layout(anchor);
        assertEquals(60, ((FrameLayout.LayoutParams) frame.getLayoutParams()).topMargin);
    }

    private static void layout(FrameLayout anchor) {
        anchor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        anchor.layout(0, 0, 400, 800);
        Robolectric.flushForegroundThreadScheduler();
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
