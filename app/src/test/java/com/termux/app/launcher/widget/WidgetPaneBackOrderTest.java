package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;
import android.view.View;

import com.termux.R;
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
    @Test public void activityBackClosesPickerThenFull() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        WidgetPaneView pane = new WidgetPaneView(activity); activity.setContentView(pane);
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        LauncherWidgetHostController widgets = new LauncherWidgetHostController(activity,
            WidgetTestFixtures.repository(), platform);
        WidgetPaneController paneController = new WidgetPaneController(pane, widgets,
            new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isFullEngaged() { return true; }
                @Override public TopStatusBarState fullPriorState() { return TopStatusBarState.EXPANDED; }
                @Override public void restoreFull(TopStatusBarState prior) { }
            });
        final int[] height = {96};
        FullStatusBarController full = new FullStatusBarController(new FullStatusBarController.Host() {
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
        full.open(TopStatusBarState.EXPANDED);
        ReflectionHelpers.setField(activity, "mWidgetPaneController", paneController);
        ReflectionHelpers.setField(activity, "mFullStatusBarController", full);
        pane.picker().open(); assertTrue(full.isEngaged());
        activity.onBackPressed(); assertFalse(pane.picker().isOpen()); assertTrue(full.isEngaged());
        activity.onBackPressed(); assertFalse(full.isEngaged());
    }
}
