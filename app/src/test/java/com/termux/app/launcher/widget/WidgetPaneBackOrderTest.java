package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import com.termux.app.TermuxActivity;

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

    /** The widget grid wired into the activity with its picker up. */
    private static final class Fixture {
        TermuxActivity activity;
        WidgetPaneView pane;
    }

    private static Fixture openThePicker() {
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
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
            });
        ReflectionHelpers.setField(fixture.activity, "mWidgetPaneController", paneController);
        fixture.pane.picker().open();
        assertTrue(fixture.pane.picker().isOpen());
        return fixture;
    }

    @Test public void activityBackClosesThePicker() {
        Fixture fixture = openThePicker();
        fixture.activity.onBackPressed();
        assertFalse(fixture.pane.picker().isOpen());
    }

    /**
     * The route that actually runs on a device.
     *
     * <p>On hardware the back key is consumed in the key channel and {@code onBackPressed()} never
     * runs, so the test above once passed while a real press left the picker open. The drawer has
     * had a claim in this channel all along; the widget grid had none.
     */
    @Test public void backThroughTheKeyChannelClosesThePicker() {
        Fixture fixture = openThePicker();
        assertTrue(fixture.activity.consumeOverlayKeyDown(KeyEvent.KEYCODE_BACK, backDown()));
        assertFalse(fixture.pane.picker().isOpen());
    }

    /** With nothing open the claim declines, or Back could never reach the drawer or the shell. */
    @Test public void theClaimDeclinesWhenNoPaneIsUp() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        assertFalse(activity.consumeOverlayKeyDown(KeyEvent.KEYCODE_BACK, backDown()));
        assertFalse(activity.consumeOverlayKeyUp(KeyEvent.KEYCODE_BACK));
    }

    /** A release let through on its own would reach the shell behind the pane that just closed. */
    @Test public void theReleaseOfAClaimedPressIsSwallowedOnce() {
        Fixture fixture = openThePicker();
        assertTrue(fixture.activity.consumeOverlayKeyDown(KeyEvent.KEYCODE_BACK, backDown()));

        assertTrue(fixture.activity.consumeOverlayKeyUp(KeyEvent.KEYCODE_BACK));
        assertFalse("the flag is one-shot",
            fixture.activity.consumeOverlayKeyUp(KeyEvent.KEYCODE_BACK));
    }

    /** Escape belongs to the palette; this claim must never see it. */
    @Test public void onlyTheBackKeyIsClaimed() {
        Fixture fixture = openThePicker();
        assertFalse(fixture.activity.consumeOverlayKeyDown(KeyEvent.KEYCODE_ESCAPE,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)));
        assertTrue("escape must leave the picker alone", fixture.pane.picker().isOpen());
    }

    private static KeyEvent backDown() {
        return new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);
    }
}
