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
        layout(grid); Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(WidgetGridView.SIZE_DELIVERY_SETTLE_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(0, platform.allocations); assertTrue(platform.optionUpdates >= 1);
        assertNotNull(grid.cellForId(20));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1, java.util.concurrent.TimeUnit.SECONDS);
        int stableWidth = grid.getWidth(), stableHeight = grid.getHeight();
        platform.optionUpdates = 0;
        grid.layout(0, 0, stableWidth, stableHeight);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(WidgetGridView.SIZE_DELIVERY_SETTLE_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
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
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(WidgetGridView.SIZE_DELIVERY_SETTLE_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        WidgetCellView cell = grid.cellForId(20);
        assertNotNull(cell);
        // The framework pads every host view on its own; the provider lays out inside that
        // padding, so the size it is told must be inside it too.
        View host = cell.getChildAt(0);
        assertTrue(host instanceof android.appwidget.AppWidgetHostView);
        assertTrue("the framework host view pads its content",
            host.getPaddingLeft() + host.getPaddingRight() > 0);
        int expectedWidthDp = Math.round((cell.getWidth() - cell.getPaddingLeft()
            - cell.getPaddingRight() - host.getPaddingLeft() - host.getPaddingRight())
            / activity.getResources().getDisplayMetrics().density);
        int expectedHeightDp = Math.round((cell.getHeight() - cell.getPaddingTop()
            - cell.getPaddingBottom() - host.getPaddingTop() - host.getPaddingBottom())
            / activity.getResources().getDisplayMetrics().density);
        assertEquals(expectedWidthDp,
            platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        assertEquals(expectedHeightDp,
            platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
    }
    /**
     * The terminal's keyboard shrinks the whole wall, the Widgets page included, while the wall is
     * leaving it or away from it. A size the user never sees at rest must not reach the provider:
     * telling it made every widget re-render small, then large again on the way back, which showed
     * as each widget squeezed and snapping to size after the page landed (Pong, 2026-09-23).
     */
    @Test public void aSizeThePageOnlyPassesThroughOffRestIsNeverDelivered() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        WidgetGridView grid = boundGrid(activity, platform);
        // Attached to the activity's window, the grid rests at whatever size that window lays it
        // out at; that is the size at rest.
        layout(grid, 800, 600); settle();
        int width = grid.getWidth(), height = grid.getHeight();
        assertEquals(1, platform.optionUpdates);

        grid.setPageAtRest(false);          // the wall leaves: the keyboard opens mid-slide
        layout(grid, width, height * 2 / 3);
        settle();
        assertEquals("a size seen only in passing is not delivered", 1, platform.optionUpdates);

        layout(grid, width, height);        // back: the keyboard closes as the wall arrives
        grid.setPageAtRest(true); settle();
        assertEquals("the size at rest is what the provider already has", 1,
            platform.optionUpdates);
    }

    @Test public void aRealChangeWhileAwayIsDeliveredOnceTheWallRests() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        WidgetGridView grid = boundGrid(activity, platform);
        layout(grid, 800, 600); settle();
        int width = grid.getWidth(), height = grid.getHeight();
        grid.setPageAtRest(false);
        layout(grid, width, height * 2 / 3);  // e.g. the home place's own dock grew meanwhile
        settle();
        assertEquals(1, platform.optionUpdates);

        grid.setPageAtRest(true);
        settle();
        assertEquals(2, platform.optionUpdates);
        settle();
        assertEquals("once, not again", 2, platform.optionUpdates);
    }

    private static WidgetGridView boundGrid(Activity activity,
                                            WidgetTestFixtures.Platform platform) {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(20, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 2, 2),
            new Bundle(), null));
        platform.info.put(20, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        WidgetGridView grid = new WidgetGridView(activity); activity.setContentView(grid);
        grid.bind(controller);
        return grid;
    }

    private static void settle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            WidgetGridView.SIZE_DELIVERY_SETTLE_MS * 2, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)); view.layout(0, 0, 800, 600);
    }
}
