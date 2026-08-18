package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import com.termux.app.TermuxActivity;
import com.termux.app.statusbar.FullStatusBarController;
import com.termux.app.statusbar.TopStatusBarState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetPaneBackOrderTest {

    /** Both panes, wired into the activity, with FULL engaged and the picker up. */
    private static final class Fixture {
        TermuxActivity activity;
        WidgetPaneView pane;
        FullStatusBarController full;
    }

    private static Fixture openBothPanes() {
        Fixture fixture = new Fixture();
        fixture.activity = Robolectric.buildActivity(TermuxActivity.class).get();
        fixture.pane = new WidgetPaneView(fixture.activity);
        fixture.activity.setContentView(fixture.pane);
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(fixture.activity);
        LauncherWidgetHostController widgets = new LauncherWidgetHostController(fixture.activity,
            WidgetTestFixtures.repository(), platform);
        WidgetPaneController paneController = new WidgetPaneController(fixture.pane, widgets,
            new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isFullEngaged() { return true; }
                @Override public TopStatusBarState fullPriorState() { return TopStatusBarState.EXPANDED; }
                @Override public void restoreFull(TopStatusBarState prior) { }
            });
        final int[] height = {96};
        fixture.full = new FullStatusBarController(new FullStatusBarController.Host() {
            @Override public int currentHeight() { return height[0]; }
            @Override public int normalHeight(TopStatusBarState state) { return 96; }
            @Override public int parentMeasuredHeight() { return 900; }
            @Override public int parentPaddingTop() { return 0; }
            @Override public int parentPaddingBottom() { return 0; }
            @Override public int hostTopMargin() { return 0; }
            @Override public boolean reducedMotion() { return true; }
            @Override public void cancelNormalAnimatorKeepingCurrent() { }
            @Override public void beginTerminalResize() { }
            @Override public void applyFrame(int value, float progress) { height[0] = value; }
            @Override public void finishTerminalResizeAfterLayout() { }
            @Override public void applyNormalState(TopStatusBarState state) { }
            @Override public void onEngagementChanged(boolean engaged, TopStatusBarState target) { }
        });
        fixture.full.open(TopStatusBarState.EXPANDED);
        ReflectionHelpers.setField(fixture.activity, "mWidgetPaneController", paneController);
        ReflectionHelpers.setField(fixture.activity, "mFullStatusBarController", fixture.full);
        fixture.pane.picker().open();
        assertTrue(fixture.full.isEngaged());
        return fixture;
    }

    @Test public void activityBackClosesPickerThenFull() {
        Fixture fixture = openBothPanes();
        fixture.activity.onBackPressed();
        assertFalse(fixture.pane.picker().isOpen());
        assertTrue(fixture.full.isEngaged());
        fixture.activity.onBackPressed();
        assertFalse(fixture.full.isEngaged());
    }

    /**
     * The route that actually runs on a device.
     *
     * <p>On hardware the back key is consumed in the key channel and {@code onBackPressed()} never
     * runs, so the test above passed while two real presses left the pane open and only the pull-up
     * gesture closed it. The drawer has had a claim in this channel all along; these two had none.
     */
    @Test public void backThroughTheKeyChannelClosesPickerThenFull() {
        Fixture fixture = openBothPanes();

        assertTrue(fixture.activity.handleOverlayPaneKey(KeyEvent.KEYCODE_BACK, backDown()));
        assertFalse(fixture.pane.picker().isOpen());
        assertTrue(fixture.full.isEngaged());

        assertTrue(fixture.activity.handleOverlayPaneKey(KeyEvent.KEYCODE_BACK, backDown()));
        assertFalse(fixture.full.isEngaged());
    }

    /** With nothing open the claim declines, or Back could never reach the drawer or the shell. */
    @Test public void theClaimDeclinesWhenNoPaneIsUp() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        assertFalse(activity.handleOverlayPaneKey(KeyEvent.KEYCODE_BACK, backDown()));
        assertFalse(activity.consumeOverlayPaneKeyUp(KeyEvent.KEYCODE_BACK));
    }

    /** A release let through on its own would reach the shell behind the pane that just closed. */
    @Test public void theReleaseOfAClaimedPressIsSwallowedOnce() {
        Fixture fixture = openBothPanes();
        assertTrue(fixture.activity.handleOverlayPaneKey(KeyEvent.KEYCODE_BACK, backDown()));

        assertTrue(fixture.activity.consumeOverlayPaneKeyUp(KeyEvent.KEYCODE_BACK));
        assertFalse("the flag is one-shot",
            fixture.activity.consumeOverlayPaneKeyUp(KeyEvent.KEYCODE_BACK));
    }

    /** Escape belongs to the palette; this claim must never see it. */
    @Test public void onlyTheBackKeyIsClaimed() {
        Fixture fixture = openBothPanes();
        assertFalse(fixture.activity.handleOverlayPaneKey(KeyEvent.KEYCODE_ESCAPE,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)));
        assertTrue("escape must leave the picker alone", fixture.pane.picker().isOpen());
    }

    private static KeyEvent backDown() {
        return new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);
    }
}
