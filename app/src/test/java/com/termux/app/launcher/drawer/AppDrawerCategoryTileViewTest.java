package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerCategoryTileViewTest {

    @Test public void exactSquareHeadingAndSevenIconRectanglesFollowMetrics() {
        AppDrawerCategoryGridMetrics m = metrics();
        AppDrawerCategoryTileView tile = tile(null, bucket(7), m, new int[1]);
        assertEquals(m.tileSidePx, tile.tileSide(), 1f);
        // Folder-card layout: label band inside the tile top, icon square below it, tile taller
        // than wide by exactly the band.
        assertEquals(m.tileSidePx + m.headingGapPx + m.headingHeightPx, tile.tileHeight(), 1f);
        assertTrue(tile.heading.getTop() < m.innerPaddingPx + 1f);
        assertTrue(tile.heading.getBottom() <= tile.tileHeight());
        assertTrue(tile.icons[0].getTop() >= m.headingGapPx + m.headingHeightPx);
        assertEquals(m.largeIconPx, tile.icons[0].getWidth());
        assertEquals(m.largeIconPx, tile.icons[2].getHeight());
        assertEquals(m.smallIconPx, tile.icons[3].getWidth());
        assertEquals(m.smallIconPx, tile.icons[6].getHeight());
        // Redesign: the open target is the whole drawn card.
        assertEquals(Math.round(m.tileSidePx), tile.expandTarget.getWidth());
        assertEquals(Math.round(tile.tileHeight()), tile.expandTarget.getHeight());
        assertEquals(Math.round(tile.tileLeft()), tile.expandTarget.getLeft());
        assertEquals(0, tile.expandTarget.getTop());
    }

    @Test public void smallSubCellsAreCenteredEvenlyGuttedAndContainedByParentSlot() {
        RectF parent = new RectF(37.5f, 91.25f, 142.5f, 196.25f);
        float gap = 9f;
        RectF[] cells = AppDrawerCategoryTileView.smallCellRects(parent, gap);

        assertEquals(4, cells.length);
        for (RectF cell : cells) {
            assertTrue(parent.contains(cell));
            assertEquals((parent.width() - gap) / 2f, cell.width(), 0.001f);
            assertEquals((parent.height() - gap) / 2f, cell.height(), 0.001f);
        }
        assertEquals(gap, cells[1].left - cells[0].right, 0.001f);
        assertEquals(gap, cells[2].top - cells[0].bottom, 0.001f);
        assertEquals(parent.centerX(), (cells[0].left + cells[1].right) / 2f, 0.001f);
        assertEquals(parent.centerY(), (cells[0].top + cells[2].bottom) / 2f, 0.001f);
        assertEquals(parent.right, cells[3].right, 0f);
        assertEquals(parent.bottom, cells[3].bottom, 0f);
    }

    @Test public void smallPreviewsUseTheirOwnPixelSizedCacheEntriesWithoutViewScaling() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SuggestionBarView dock = new SuggestionBarView(activity, null);
        AppDrawerCategoryGridMetrics metrics = metrics();
        AppDrawerCategoryTileView tile = tile(dock, bucket(7), metrics, new int[1]);

        assertEquals(android.widget.ImageView.ScaleType.CENTER, tile.icons[3].getScaleType());
        assertTrue(tile.icons[0].getDrawable() instanceof BitmapDrawable);
        assertTrue(tile.icons[3].getDrawable() instanceof BitmapDrawable);
        BitmapDrawable large = (BitmapDrawable) tile.icons[0].getDrawable();
        BitmapDrawable small = (BitmapDrawable) tile.icons[3].getDrawable();
        assertEquals(metrics.largeIconPx, large.getBitmap().getWidth());
        assertEquals(metrics.smallIconPx, small.getBitmap().getWidth());
        assertEquals(metrics.smallIconPx, tile.icons[3].getWidth());
    }

    @Test public void largeIconsLaunchWhileTheClusterCardAndHeadingExpand() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SuggestionBarView dock = new SuggestionBarView(activity, null);
        int[] expansions = {0};
        AppDrawerCategoryBucket bucket = bucket(7);
        AppDrawerCategoryTileView tile = tile(dock, bucket, metrics(), expansions);
        for (int i = 0; i < AppDrawerCategoryTileView.LAUNCH_ICON_COUNT; i++) {
            assertTrue(tile.icons[i].isClickable());
            // A long press on a launch icon reuses the dock's Material app-context popup, with a
            // Category row swapped in for Pin — the same reassignment entry point the expanded
            // category grid offers.
            assertTrue(tile.icons[i].isLongClickable());
            assertEquals(bucket.entries().get(i).label, tile.icons[i].getContentDescription());
        }
        for (int i = AppDrawerCategoryTileView.LAUNCH_ICON_COUNT; i < 7; i++) {
            assertFalse(tile.icons[i].isClickable());
            assertFalse(tile.icons[i].isLongClickable());
            assertNull(tile.icons[i].getContentDescription());
        }
        // A launch tap never opens the category, and the display-only slots never take the tap.
        assertTrue(tile.icons[0].performClick());
        assertFalse(tile.icons[6].performClick());
        assertEquals(0, expansions[0]);
        assertTrue(tile.expandTarget.performClick());
        assertTrue(tile.heading.performClick());
        assertEquals(2, expansions[0]);
    }

    @Test public void launchIconsSitAboveTheOpenTargetAndDipOnTheirOwn() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SuggestionBarView dock = new SuggestionBarView(activity, null);
        AppDrawerCategoryTileView tile = tile(dock, bucket(7), metrics(), new int[1]);
        assertTrue(tile.indexOfChild(tile.icons[0]) > tile.indexOfChild(tile.expandTarget));
        tile.icons[0].setPressed(true);
        assertTrue(tile.icons[0].getScaleX() < 1f);
        assertEquals(1f, tile.getScaleX(), 0.001f);
        tile.icons[0].setPressed(false);
        assertEquals(1f, tile.icons[0].getScaleX(), 0.001f);
        // Display-only slots never dip: they are not the thing being pressed.
        tile.icons[6].setPressed(true);
        assertEquals(1f, tile.icons[6].getScaleX(), 0.001f);
    }

    @Test public void pressingTheCardDipsItAndReleaseRestoresIt() {
        AppDrawerCategoryTileView tile = tile(null, bucket(7), metrics(), new int[1]);
        tile.expandTarget.setPressed(true);
        assertEquals(0.98f, tile.getScaleX(), 0.001f);
        assertEquals(0.98f, tile.getScaleY(), 0.001f);
        tile.expandTarget.setPressed(false);
        assertEquals(1f, tile.getScaleX(), 0.001f);
        tile.unbind();
        assertEquals(1f, tile.getScaleY(), 0.001f);
    }

    @Test public void missingPreviewsAreInvisibleNonClickableAndCannotAnswer() {
        int[] expansions = {0};
        AppDrawerCategoryTileView tile = tile(null, bucket(2), metrics(), expansions);
        for (int i = 2; i < 7; i++) {
            assertEquals(View.INVISIBLE, tile.icons[i].getVisibility());
            assertFalse(tile.icons[i].isClickable());
            assertFalse(tile.icons[i].performClick());
        }
        assertTrue(tile.expandTarget.performClick());
        assertEquals(1, expansions[0]);
    }

    @Test public void unbindClearsEveryDrawableListenerAndDescription() {
        AppDrawerCategoryBucket bucket = bucket(7);
        AppDrawerCategoryTileView tile = tile(null, bucket, metrics(), new int[1]);
        for (int i = 0; i < 7; i++) assertSame(bucket.entries().get(i).icon,
            tile.icons[i].getDrawable());
        tile.unbind();
        for (int i = 0; i < 7; i++) {
            assertNull(tile.icons[i].getDrawable());
            assertFalse(tile.icons[i].isClickable());
            assertNull(tile.icons[i].getContentDescription());
        }
        assertFalse(tile.heading.isClickable());
        assertFalse(tile.expandTarget.isClickable());
        assertNull(tile.heading.getContentDescription());
        assertNull(tile.expandTarget.getContentDescription());
    }

    private static AppDrawerCategoryTileView tile(SuggestionBarView dock,
        AppDrawerCategoryBucket bucket, AppDrawerCategoryGridMetrics metrics, int[] expansions) {
        AppDrawerCategoryTileView tile = new AppDrawerCategoryTileView(
            RuntimeEnvironment.getApplication());
        tile.setLayoutParams(new ViewGroup.LayoutParams(Math.round(metrics.spanWidthPx),
            ViewGroup.LayoutParams.WRAP_CONTENT));
        tile.bind(dock, bucket, metrics, (value, source) -> expansions[0]++,
            AppDrawerAppCellView.ALLOW_CLICKS, null);
        int width = Math.round(metrics.spanWidthPx);
        tile.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        tile.layout(0, 0, width, tile.getMeasuredHeight());
        return tile;
    }

    private static AppDrawerCategoryGridMetrics metrics() {
        return AppDrawerCategoryGridMetrics.resolve(360, 640, 1, 16, 16, 24,
            8 * 1024 * 1024);
    }

    private static AppDrawerCategoryBucket bucket(int count) {
        List<LauncherAppEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) entries.add(new LauncherAppEntry(
            new AppRef("com.example.p" + i, "Main"), "App " + i,
            new ColorDrawable(Color.RED)));
        return new AppDrawerCategoryBucket(AppDrawerCategory.SOCIAL, entries);
    }
}
