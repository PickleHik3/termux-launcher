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

    /**
     * Home's Margin is the grid's rim, and a new rim resizes every cell without the grid's size
     * changing. Each size the cells settle at reaches the provider, a Margin put back by Undo
     * included, so a widget is never left drawing for the size it had before (2026-10-18 check:
     * a Clock widget cut off after a Margin change and Undo).
     */
    @Test public void aRimChangeAndItsUndoEachReachTheProvider() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        WidgetGridView grid = boundGrid(activity, platform);
        grid.setEdgePadding(8);
        layout(grid, 800, 600); settle();
        assertEquals(1, platform.optionUpdates);
        int before = platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);

        grid.setEdgePadding(48);            // the Margin slider let go at 48
        layout(grid, 800, 600); settle();
        assertEquals("the narrower cells reach the provider", 2, platform.optionUpdates);
        assertTrue(platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            < before);

        grid.setEdgePadding(8);             // Undo
        layout(grid, 800, 600); settle();
        assertEquals("the size put back reaches it too", 3, platform.optionUpdates);
        assertEquals(before,
            platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
    }

    /**
     * The emulator repro of 2026-10-18 in small: Margin dragged from 8 to 45 (each tick moves the
     * rim, and the narrower cells reach the provider), then Undo puts 8 back while the wall is
     * not at rest. When it rests again the provider's last word is 8's size.
     */
    @Test public void aMarginDragThenUndoOffRestEndsOnTheRestoredSize() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        WidgetGridView grid = boundGrid(activity, platform);
        grid.setEdgePadding(8);
        layout(grid, 800, 600); settle();
        int atEight = platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        for (int rim = 9; rim <= 45; rim += 9) {   // the slider's ticks
            grid.setEdgePadding(rim);
            layout(grid, 800, 600);
        }
        settle();
        assertTrue("the drag's size reached the provider",
            platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) < atEight);
        int updates = platform.optionUpdates;

        grid.setPageAtRest(false);
        grid.setEdgePadding(8);             // Undo
        layout(grid, 800, 600); settle();
        assertEquals("held while the wall is not at rest", updates, platform.optionUpdates);

        grid.setPageAtRest(true); settle();
        assertEquals(updates + 1, platform.optionUpdates);
        assertEquals("the provider's last size is 8's", atEight,
            platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
    }

    /**
     * The end of an editor session tells every widget whose cell moved in it its size once more,
     * even where the ledger and the provider's options already agree: the provider's renders for
     * the sizes passed through may have landed after the one for the size it ends at. A widget
     * that did not move is not woken.
     */
    @Test public void aSessionEndRetellsOnlyTheWidgetsWhoseCellsMoved() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        WidgetGridView grid = boundGrid(activity, platform);
        grid.setEdgePadding(8);
        layout(grid, 800, 600); settle();
        int atEight = platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        // The window's own first layout and the one above count as a move: an earlier session.
        grid.resyncSizes(); settle();
        int base = platform.optionUpdates;

        grid.resyncSizes(); settle();
        assertEquals("nothing moved, nothing is woken", base, platform.optionUpdates);

        grid.setEdgePadding(45); layout(grid, 800, 600); settle();
        grid.setEdgePadding(8); layout(grid, 800, 600); settle();
        assertEquals(base + 2, platform.optionUpdates);

        grid.resyncSizes(); settle();
        assertEquals("told again although its options already match", base + 3,
            platform.optionUpdates);
        assertEquals(atEight,
            platform.lastOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));

        grid.resyncSizes(); settle();
        assertEquals("once per session that moved it", base + 3, platform.optionUpdates);
    }

    /** A session that ends while the wall is elsewhere tells the provider when the wall rests. */
    @Test public void aSessionEndOffRestWaitsForTheRest() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        WidgetGridView grid = boundGrid(activity, platform);
        grid.setEdgePadding(8);
        layout(grid, 800, 600); settle();
        grid.resyncSizes(); settle();       // an earlier session's end
        grid.setEdgePadding(45); layout(grid, 800, 600); settle();
        grid.setEdgePadding(8); layout(grid, 800, 600); settle();
        int updates = platform.optionUpdates;

        grid.setPageAtRest(false);
        grid.resyncSizes(); settle();
        assertEquals(updates, platform.optionUpdates);
        grid.setPageAtRest(true); settle();
        assertEquals(updates + 1, platform.optionUpdates);
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
