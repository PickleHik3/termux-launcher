package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;

import com.termux.app.statusbar.TopStatusBarState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPanePagingTest {
    @Test public void menuAddPageAppendsSwitchesAndRemoveReturns() {
        Fixture fixture = new Fixture();
        assertEquals(0, fixture.controller.currentPage());
        fixture.controller.menuAddPage();
        assertEquals(2, fixture.repository.pageCount());
        assertEquals(1, fixture.controller.currentPage());
        assertEquals(1, fixture.pane.currentPage());
        fixture.controller.menuRemovePage();
        assertEquals(1, fixture.repository.pageCount());
        assertEquals(0, fixture.controller.currentPage());
    }

    @Test public void renderShowsOnlyTheCurrentPagesCellsAndFullCloseResetsToPageZero() {
        Fixture fixture = new Fixture();
        fixture.controller.menuAddPage();
        assertTrue(fixture.repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertTrue(fixture.repository.putRecord(record(2, new WidgetCellRect(0, 0, 1, 1), 1)));
        fixture.controller.setCurrentPage(0);
        assertNotNull(fixture.pane.grid().cellForId(1));
        assertNull("page-1 widget must not render on page 0",
            fixture.pane.grid().cellForId(2));
        fixture.controller.setCurrentPage(1);
        assertNotNull(fixture.pane.grid().cellForId(2));
        assertNull(fixture.pane.grid().cellForId(1));

        fixture.controller.onFullFrame(0f);
        assertEquals("the pane always reopens on page 0", 0, fixture.controller.currentPage());
    }

    @Test public void menuAddWidgetOpensPickerAndNewWidgetsLandOnTheVisiblePage() {
        Fixture fixture = new Fixture();
        fixture.controller.onMenuItemSelected(WidgetPaneMenuPolicy.Item.ADD_WIDGET);
        assertTrue(fixture.pane.picker().isOpen());
        fixture.pane.picker().closeImmediate();

        fixture.controller.menuAddPage();
        fixture.platform.directBind = true;
        LauncherWidgetHostController.AddResult result = fixture.widgets.beginAdd(
            WidgetTestFixtures.info(false), new WidgetCellRect(0, 0, 1, 1), 1,
            fixture.repository.revision(), new Bundle(), null);
        assertEquals(LauncherWidgetHostController.AddResult.READY, result);
        assertEquals(1, fixture.repository.records().size());
        assertEquals("the reservation's page is durable end-to-end",
            1, fixture.repository.records().get(0).page);
    }

    @Test public void cellRelaysProviderEditorFocusToTheHost() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
        WidgetCellView cell = new WidgetCellView(activity);
        root.addView(cell, new android.widget.LinearLayout.LayoutParams(200, 200));
        EditText outside = new EditText(activity);
        root.addView(outside, new android.widget.LinearLayout.LayoutParams(200, 200));
        activity.setContentView(root);
        assertEquals(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS,
            cell.getDescendantFocusability());
        AtomicReference<android.view.View> focused = new AtomicReference<>();
        cell.setEditorFocusListener(focused::set);
        EditText editor = new EditText(activity);
        cell.setContent(editor);
        assertTrue(editor.requestFocus());
        assertSame(editor, focused.get());
        // Focus moving to another subtree must end the relay even without an explicit clear.
        assertTrue(outside.requestFocus());
        assertNull(focused.get());
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING, cell, page, new Bundle(), null);
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final LauncherWidgetRepository repository;
        final WidgetTestFixtures.Platform platform;
        final LauncherWidgetHostController widgets;
        final WidgetPaneView pane;
        final WidgetPaneController controller;

        Fixture() {
            activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            repository = WidgetTestFixtures.repository();
            platform = new WidgetTestFixtures.Platform(activity);
            widgets = new LauncherWidgetHostController(activity, repository, platform);
            pane = new WidgetPaneView(activity);
            activity.setContentView(pane);
            controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
            });
        }
    }
}
