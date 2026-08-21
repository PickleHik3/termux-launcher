package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetProviderMissingCellTest {
    @Test public void uninstallKeepsClippedPlaceholderCellOccupiedForPlacement() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        WidgetCellRect cell = new WidgetCellRect(0, 0, 2, 2);
        repository.putRecord(new LauncherWidgetRecord(5, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, cell, new Bundle(), null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(5, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity, repository, platform);
        platform.info.remove(5); controller.onProvidersChanged();
        assertEquals(LauncherWidgetRecord.State.PROVIDER_MISSING, repository.get(5).state);
        assertEquals(cell, repository.get(5).cell); assertEquals(java.util.Collections.singletonList(5), platform.deleted);
        assertEquals(new WidgetCellRect(2, 0, 4, 2),
            WidgetGridPlacementPolicy.findPlacement(repository.gridDefinition(), repository.records(), 2, 2).rect);
        WidgetGridView grid = new WidgetGridView(activity); grid.bind(controller);
        grid.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 400, 600); assertNotNull(grid.cellForId(5));
    }
}
