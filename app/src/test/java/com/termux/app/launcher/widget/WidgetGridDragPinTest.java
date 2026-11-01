package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * A widget carried across pages keeps its cell through the page turn's render: the gesture is
 * delivered through that view, and removing it cancelled the drag mid-air and crashed the flip
 * (2026-10-21). The launcher's own widgets have negative ids, the first of them -1.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetGridDragPinTest {

    @Test public void aCarriedBuiltinKeepsItsCellOnAPageItIsNotOn() {
        for (int id : new int[]{-1, -2}) {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            LauncherWidgetRepository repository = WidgetTestFixtures.repository();
            WidgetGridView grid = new WidgetGridView(activity);
            LauncherWidgetRecord carried = LauncherWidgetRecord.builtin(id, "battery",
                new WidgetCellRect(0, 0, 2, 1), 0, null);
            grid.refresh(repository.gridDefinition(), Collections.singletonList(carried));
            WidgetCellView cell = grid.cellForId(id);
            assertNotNull(cell);

            grid.setDragPinned(id);
            grid.refresh(repository.gridDefinition(), Collections.emptyList());

            assertSame("widget " + id, cell, grid.cellForId(id));
            assertSame("widget " + id, grid, cell.getParent());
        }
    }

    @Test public void releasingTheCarriedBuiltinLetsItsCellGo() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        WidgetGridView grid = new WidgetGridView(activity);
        LauncherWidgetRecord carried = LauncherWidgetRecord.builtin(-1, "battery",
            new WidgetCellRect(0, 0, 2, 1), 0, null);
        grid.refresh(repository.gridDefinition(), Collections.singletonList(carried));
        grid.setDragPinned(-1);
        grid.refresh(repository.gridDefinition(), Collections.emptyList());

        grid.setDragPinned(WidgetGridView.NO_DRAG_PIN);

        assertNull(grid.cellForId(-1));
    }
}
