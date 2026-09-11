package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.EditText;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The picker must never reach for the keyboard itself. Its search field takes focus only when it
 * is tapped, and the activity is told through the same seam a widget's own text input uses — the
 * one that puts the in-app keyboard down first — then told again when the picker closes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerSearchFocusSeamTest {
    @Test public void tappingSearchRoutesFocusThroughTheEditorSeamAndClosingReleasesIt() {
        Fixture fixture = new Fixture();
        fixture.controller.openPicker();
        EditText search = fixture.pane.picker().searchField();
        assertTrue(fixture.events.isEmpty()); // opening the picker takes nothing

        assertTrue(search.performClick());
        assertEquals(1, fixture.events.size());
        assertSame(search, fixture.events.get(0));
        assertTrue(search.hasFocus());

        fixture.pane.picker().close();
        assertEquals(2, fixture.events.size());
        assertNull(fixture.events.get(1)); // the terminal's keyboard arrangement comes back
        assertFalse(search.hasFocus());
        assertFalse(search.isFocusable());
        assertEquals("", search.getText().toString());
    }

    @Test public void losingFocusWithoutClosingAlsoReleasesTheKeyboard() {
        Fixture fixture = new Fixture();
        fixture.controller.openPicker();
        EditText search = fixture.pane.picker().searchField();
        assertTrue(search.performClick());
        search.clearFocus();
        assertEquals(2, fixture.events.size());
        assertNull(fixture.events.get(1));
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final ArrayList<View> events = new ArrayList<>();
        final WidgetPaneView pane;
        final WidgetPaneController controller;

        Fixture() {
            activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            pane = new WidgetPaneView(activity);
            activity.setContentView(pane);
            LauncherWidgetHostController widgets = new LauncherWidgetHostController(activity,
                WidgetTestFixtures.repository(), new WidgetTestFixtures.Platform(activity));
            controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
                @Override public void onWidgetEditorFocused(View editor) { events.add(editor); }
                @Override public void onWidgetEditorClosed() { events.add(null); }
            });
            pane.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
            pane.layout(0, 0, 800, 900);
        }
    }
}
