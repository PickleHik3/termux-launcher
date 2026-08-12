package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageButton;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPaneAccessibilityTest {
    @Test public void launcherControlsAreNamedFortyEightDpAndCogReportsReadOnlyGrid() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneView pane = new WidgetPaneView(activity); activity.setContentView(pane);
        LauncherWidgetHostController widgets = new LauncherWidgetHostController(activity,
            WidgetTestFixtures.repository(), new WidgetTestFixtures.Platform(activity));
        new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
            @Override public boolean reducedMotion() { return true; }
            @Override public boolean isFullEngaged() { return true; }
            @Override public com.termux.app.statusbar.TopStatusBarState fullPriorState() {
                return com.termux.app.statusbar.TopStatusBarState.EXPANDED;
            }
            @Override public void restoreFull(com.termux.app.statusbar.TopStatusBarState prior) { }
        });
        pane.setFullProgress(1); layout(pane);
        View add = pane.findViewById(R.id.widget_add_large);
        assertEquals("Add widget", add.getContentDescription());
        assertTrue(add.getHeight() >= dp(activity, 48));
        pane.picker().open(); assertNotNull(findDescription(pane.picker(), "Close widget picker"));
    }

    @Test public void populatedCompactAddUsesDedicatedVisibleIcon() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneView pane = new WidgetPaneView(activity); activity.setContentView(pane);
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(7, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING, null, null));
        pane.render(repository, LauncherWidgetHostController.Capability.AVAILABLE);
        layout(pane);
        ImageButton compact = pane.findViewById(R.id.widget_add_compact);
        assertEquals(View.VISIBLE, compact.getVisibility());
        assertNotNull(compact.getDrawable());
        assertEquals("Add widget", compact.getContentDescription());
        assertEquals(dp(activity, 48), compact.getWidth());
        assertEquals(dp(activity, 48), compact.getHeight());
        assertEquals(0, compact.getLeft());
        assertEquals(0, compact.getTop());
        assertEquals(android.widget.ImageView.ScaleType.CENTER, compact.getScaleType());
        assertTrue(compact.getBackground() instanceof GradientDrawable);
        GradientDrawable background = (GradientDrawable) compact.getBackground();
        assertEquals(GradientDrawable.OVAL, background.getShape());
        float[] hsv = new float[3];
        Color.colorToHSV(background.getColor().getDefaultColor(), hsv);
        assertTrue("compact add must remain visibly muted", hsv[1] < 0.20f);
    }
    private static View findDescription(View view, String description) {
        if (view.getContentDescription() != null
            && description.contentEquals(view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++) {
            View found = findDescription(((android.view.ViewGroup) view).getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }
    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)); view.layout(0, 0, 800, 900);
    }
    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
