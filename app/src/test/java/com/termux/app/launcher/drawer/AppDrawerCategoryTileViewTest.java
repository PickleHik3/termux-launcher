package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
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
        assertTrue(tile.heading.getTop() >= Math.round(tile.tileSide() + m.headingGapPx) - 1);
        assertTrue(tile.heading.getTop() >= tile.tileTop() + tile.tileSide());
        assertEquals(m.largeIconPx, tile.icons[0].getWidth());
        assertEquals(m.largeIconPx, tile.icons[2].getHeight());
        assertEquals(m.smallIconPx, tile.icons[3].getWidth());
        assertEquals(m.smallIconPx, tile.icons[6].getHeight());
        assertEquals(Math.round(m.largeSlotPx), tile.expandTarget.getWidth());
        assertEquals(Math.round(m.largeSlotPx), tile.expandTarget.getHeight());
    }

    @Test public void firstThreeAreDirectActionsAndSmallBlockAndHeadingOnlyExpand() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SuggestionBarView dock = new SuggestionBarView(activity, null);
        int[] expansions = {0};
        AppDrawerCategoryTileView tile = tile(dock, bucket(7), metrics(), expansions);
        for (int i = 0; i < 3; i++) {
            assertTrue(tile.icons[i].isClickable());
            assertTrue(tile.icons[i].isLongClickable());
        }
        for (int i = 3; i < 7; i++) {
            assertFalse(tile.icons[i].isClickable());
            assertFalse(tile.icons[i].isLongClickable());
            assertNull(tile.icons[i].getContentDescription());
        }
        assertTrue(tile.expandTarget.performClick());
        assertTrue(tile.heading.performClick());
        assertEquals(2, expansions[0]);
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
            AppDrawerAppCellView.ALLOW_CLICKS);
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
