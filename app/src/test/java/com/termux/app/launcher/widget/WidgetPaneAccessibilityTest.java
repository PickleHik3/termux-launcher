package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

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
    @Test public void emptyPaneShowsLongPressHintAndPickerCloseIsNamed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneView pane = new WidgetPaneView(activity); activity.setContentView(pane);
        LauncherWidgetHostController widgets = new LauncherWidgetHostController(activity,
            WidgetTestFixtures.repository(), new WidgetTestFixtures.Platform(activity));
        WidgetPaneController controller = new WidgetPaneController(pane, widgets,
            new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
            });
        layout(pane);
        TextView hint = pane.findViewById(R.id.widget_empty_message);
        assertEquals(View.VISIBLE, ((View) hint.getParent()).getVisibility());
        assertEquals(activity.getString(R.string.widget_empty_hint), hint.getText().toString());
        // The empty grid stays hittable: its surface owns the long-press menu.
        assertEquals(View.VISIBLE, pane.findViewById(R.id.widget_grid).getVisibility());
        // Single page: the indicator stays out of the way.
        assertEquals(View.GONE, pane.findViewById(R.id.widget_page_dots).getVisibility());
        controller.openPicker();
        assertNotNull(findDescription(pane.picker(), "Close widget picker"));
    }

    @Test public void populatedPageHidesHintAndSecondPageShowsDots() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPaneView pane = new WidgetPaneView(activity); activity.setContentView(pane);
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(7, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING, null, null));
        pane.render(repository, LauncherWidgetHostController.Capability.AVAILABLE, 0);
        layout(pane);
        View emptyState = (View) pane.findViewById(R.id.widget_empty_message).getParent();
        assertEquals(View.GONE, emptyState.getVisibility());
        assertEquals(View.GONE, pane.findViewById(R.id.widget_page_dots).getVisibility());
        assertTrue(repository.addPage() > 0);
        pane.render(repository, LauncherWidgetHostController.Capability.AVAILABLE, 1);
        layout(pane);
        assertEquals(View.VISIBLE, pane.findViewById(R.id.widget_page_dots).getVisibility());
        // The appended page is empty, so the hint returns there.
        assertEquals(View.VISIBLE, emptyState.getVisibility());
        assertEquals(1, pane.currentPage());
    }

    private static View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null
            ? "" : view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void layout(View pane) {
        pane.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        pane.layout(0, 0, 800, 900);
    }
}
