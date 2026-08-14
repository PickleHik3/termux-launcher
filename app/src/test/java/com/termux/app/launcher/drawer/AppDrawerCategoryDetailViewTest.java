package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerCategoryDetailViewTest {

    @Test public void oneTwoAndManyRowsStayBottomAlignedWithSpaceAbove() {
        for (int count : new int[] {1, 2, 8}) {
            AppDrawerCategoryView view = expanded(count, 640);
            assertEquals(640, view.getDetailList().getBottom());
            assertEquals(view.getDetailList().getTop()
                - Math.round(AppDrawerCategoryTileAdapterTest.metrics().headerListGapPx),
                view.getDetailHeader().getBottom(), 1);
            assertTrue(view.getDetailHeader().getTop() >= 0);
            assertTrue(view.getDetailHeader().getBottom() <= view.getDetailList().getTop());
        }
    }

    @Test public void overflowStartsAtFirstEntryAndScrollsInOrdinaryRowMajorOrder() {
        AppDrawerCategoryView view = expanded(100, 320);
        assertEquals(100, view.getDetailAdapter().getItemCount());
        assertEquals("App 0", view.getDetailAdapter().entries().get(0).label);
        assertEquals("App 99", view.getDetailAdapter().entries().get(99).label);
        assertTrue(view.getDetailList().canScrollVertically(1));
        assertFalse(view.getDetailList().canScrollVertically(-1));
        assertEquals(320, view.getDetailList().getBottom());
    }

    @Test public void largeHeaderCannotOverlapBottomListInShortFontScaledGeometry() {
        AppDrawerCategoryGridMetrics metrics = AppDrawerCategoryGridMetrics.resolve(
            360, 160, 1, 72, 72, 24, 8 * 1024 * 1024);
        AppDrawerCategoryView view = new AppDrawerCategoryView(
            org.robolectric.RuntimeEnvironment.getApplication(), null);
        view.setMetrics(metrics);
        AppDrawerCategoryBucket bucket = AppDrawerCategoryTileAdapterTest.bucket(
            AppDrawerCategory.SOCIAL, 20);
        view.submitBuckets(java.util.Collections.singletonList(bucket));
        AppDrawerCategoryTileAdapterTest.layout(view, 360, 160);
        view.onExpandRequested(bucket, (AppDrawerCategoryTileView) view.getOverview().getChildAt(0));
        AppDrawerCategoryTileAdapterTest.layout(view, 360, 160);
        view.advance(1f / 60f, true);
        AppDrawerCategoryTileAdapterTest.layout(view, 360, 160);
        assertTrue(view.getDetailHeader().getBottom() <= view.getDetailList().getTop());
        assertEquals(160, view.getDetailList().getBottom());
    }

    @Test public void headerShowsChevronTitleAndAppCountAndChevronCollapses() {
        AppDrawerCategoryView view = expanded(8, 640);
        assertEquals(View.VISIBLE, view.getCollapseChevron().getVisibility());
        assertEquals(View.VISIBLE, view.getDetailCount().getVisibility());
        assertEquals("8 APPS", view.getDetailCount().getText().toString());
        assertTrue(view.getDetailCount().getRight() <= view.getWidth());
        assertTrue(view.getCollapseChevron().getLeft() >= 0);
        assertTrue(view.getCollapseChevron().getRight() <= view.getDetailHeader().getLeft());

        assertTrue(view.getCollapseChevron().performClick());
        assertEquals(AppDrawerCategoryExpansionModel.State.COLLAPSING, view.expansionState());
        view.advance(1f / 60f, true);
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW, view.expansionState());
        assertEquals(View.INVISIBLE, view.getCollapseChevron().getVisibility());
        assertEquals("", view.getDetailCount().getText().toString());
    }

    @Test public void singleAppCategoryCountsWithoutThePlural() {
        AppDrawerCategoryView view = expanded(1, 640);
        assertEquals("1 APP", view.getDetailCount().getText().toString());
    }

    @Test public void scrollingOverviewAndDetailDismissesAnchoredContextPopups() {
        List<AppDrawerCategoryBucket> buckets = new ArrayList<>();
        for (AppDrawerCategory category : AppDrawerCategory.values())
            buckets.add(AppDrawerCategoryTileAdapterTest.bucket(category, 1));
        AppDrawerCategoryView overview = AppDrawerCategoryTileAdapterTest.categoryView(buckets);
        AppDrawerCategoryTileAdapterTest.layout(overview, 360, 160);
        int[] dismissals = {0};
        overview.setPopupDismissCallback(() -> dismissals[0]++);
        overview.getOverview().scrollBy(0, 40);
        assertTrue(dismissals[0] > 0);

        AppDrawerCategoryView detail = expanded(100, 320);
        detail.setPopupDismissCallback(() -> dismissals[0]++);
        int beforeDetailScroll = dismissals[0];
        detail.getDetailList().scrollBy(0, 40);
        assertTrue(dismissals[0] > beforeDetailScroll);
    }

    private static AppDrawerCategoryView expanded(int count, int height) {
        AppDrawerCategoryView view = new AppDrawerCategoryView(
            org.robolectric.RuntimeEnvironment.getApplication(), null);
        AppDrawerCategoryGridMetrics metrics = AppDrawerCategoryGridMetrics.resolve(
            360, height, 1, 16, 16, 24, 8 * 1024 * 1024);
        view.setMetrics(metrics);
        AppDrawerCategoryBucket bucket = AppDrawerCategoryTileAdapterTest.bucket(
            AppDrawerCategory.SOCIAL, count);
        view.submitBuckets(java.util.Collections.singletonList(bucket));
        AppDrawerCategoryTileAdapterTest.layout(view, 360, height);
        view.onExpandRequested(bucket, (AppDrawerCategoryTileView) view.getOverview().getChildAt(0));
        AppDrawerCategoryTileAdapterTest.layout(view, 360, height);
        view.advance(1f / 60f, true);
        AppDrawerCategoryTileAdapterTest.layout(view, 360, height);
        return view;
    }
}
