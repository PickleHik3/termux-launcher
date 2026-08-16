package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetGridHostViewIntegrationTest {
    @Test public void recreationUsesPersistedIdAndCommitsExactSizeOnlyOnce() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        WidgetCellRect cell = new WidgetCellRect(0, 0, 2, 2);
        LauncherWidgetRecord record = new LauncherWidgetRecord(20, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, cell, new Bundle(), null);
        assertTrue(repository.putRecord(record)); platform.info.put(20, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity, repository, platform);
        WidgetGridView grid = new WidgetGridView(activity); activity.setContentView(grid); grid.bind(controller);
        layout(grid); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(0, platform.allocations); assertTrue(platform.optionUpdates >= 1);
        assertNotNull(grid.cellForId(20));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1, java.util.concurrent.TimeUnit.SECONDS);
        int stableWidth = grid.getWidth(), stableHeight = grid.getHeight();
        platform.optionUpdates = 0;
        grid.layout(0, 0, stableWidth, stableHeight);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(0, platform.optionUpdates);
    }

    @Test public void committedOptionsUseInsetHostContentSizeNotWrapperCellSize() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(20, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 2, 2),
            new Bundle(), null));
        platform.info.put(20, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        WidgetGridView grid = new WidgetGridView(activity); activity.setContentView(grid);
        grid.bind(controller); layout(grid);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        WidgetCellView cell = grid.cellForId(20);
        assertNotNull(cell);
        int expectedWidthDp = Math.round((cell.getWidth() - cell.getPaddingLeft()
            - cell.getPaddingRight()) / activity.getResources().getDisplayMetrics().density);
        int expectedHeightDp = Math.round((cell.getHeight() - cell.getPaddingTop()
            - cell.getPaddingBottom()) / activity.getResources().getDisplayMetrics().density);
        assertEquals(expectedWidthDp,
            platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        assertEquals(expectedHeightDp,
            platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
    }
    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)); view.layout(0, 0, 800, 600);
    }
}
