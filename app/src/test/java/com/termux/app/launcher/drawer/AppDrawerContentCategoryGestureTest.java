package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerContentCategoryGestureTest {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 300;
    private AppDrawerContentView content;
    private AppDrawerCategoryView categories;
    private RecordingCallbacks callbacks;

    @Before public void setUp() {
        AppDrawerSearchController search = new AppDrawerSearchController();
        search.setHost(new AppDrawerSearchController.Host() {
            @Override public boolean isSearchActive() { return true; }
            @Override public void onSearchCommitRequested() {}
            @Override public void onSearchDismissRequested() {}
        });
        content = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        callbacks = new RecordingCallbacks();
        content.setCallbacks(callbacks);
        content.setViewType(AppDrawerViewType.CATEGORIES);
        content.setCategoryMetrics(AppDrawerCategoryGridMetrics.resolve(WIDTH,
            content.horizontalPagerUsableHeight(HEIGHT), 1, 16, 16, 24, 8 * 1024 * 1024));
        content.setInteractive(true);
        content.bind(null, search);
        search.setCatalogue(manyBuckets());
        layout();
        categories = content.getCategoryView();
    }

    @Test public void overviewPullAtTheTopClosesInOneGesture() {
        RecyclerView overview = categories.getOverview();
        assertTrue(overview.canScrollVertically(1));
        downOnOverview();
        int[] consumed = {0, 0};
        content.onNestedPreScroll(overview, 0, -60, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(-60, consumed[1]);
        assertEquals(1, callbacks.begins);
        content.onStopNestedScroll(overview, ViewCompat.TYPE_TOUCH);
        assertEquals(1, callbacks.ends);
    }

    @Test public void midListSnapshotNeverChangesToCloseAfterReachingTop() {
        RecyclerView overview = categories.getOverview();
        overview.scrollBy(0, 200);
        assertTrue(overview.canScrollVertically(-1));
        downOnOverview();
        overview.scrollToPosition(0);
        layout();
        int[] consumed = {0, 0};
        content.onNestedPreScroll(overview, 0, -300, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(0, consumed[1]);
        assertEquals(0, callbacks.begins);
        content.onStopNestedScroll(overview, ViewCompat.TYPE_TOUCH);
    }

    @Test public void expandedTopPullCollapsesOnlyAndUpwardLatchesScroll() {
        expandFirst();
        RecyclerView detail = categories.getDetailList();
        float x = categories.getLeft() + detail.getLeft() + detail.getWidth() / 2f;
        float y = categories.getTop() + detail.getTop() + 10f;
        dispatch(MotionEvent.ACTION_DOWN, x, y);
        dispatch(MotionEvent.ACTION_MOVE, x, y + categories.getHeight() * 0.7f);
        int[] consumed = {0, 0};
        content.onNestedPreScroll(detail, 0, -100, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(-100, consumed[1]);
        assertEquals(0, callbacks.begins);
        content.onStopNestedScroll(detail, ViewCompat.TYPE_TOUCH);
        content.advanceDrawerFx(1, 1f / 60f, true);
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW,
            categories.expansionState());

        expandFirst();
        detail = categories.getDetailList();
        x = categories.getLeft() + detail.getLeft() + detail.getWidth() / 2f;
        y = categories.getTop() + detail.getTop() + 10f;
        dispatch(MotionEvent.ACTION_DOWN, x, y);
        consumed[1] = 0;
        content.onNestedPreScroll(detail, 0, 40, consumed, ViewCompat.TYPE_TOUCH);
        content.onNestedPreScroll(detail, 0, -100, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(0, consumed[1]);
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED,
            categories.expansionState());
    }

    @Test public void detailEmptyTopIsPlaneChromeAndContentNeverIntercepts() {
        expandFirst();
        float x = categories.getLeft() + categories.getWidth() / 2f;
        float y = categories.getTop() + Math.max(1, categories.getDetailHeader().getTop() / 2f);
        assertFalse(content.ownsPoint(x, y));
        MotionEvent down = event(MotionEvent.ACTION_DOWN, x, y);
        try { assertFalse(content.onInterceptTouchEvent(down)); }
        finally { down.recycle(); }
    }

    @Test public void nestedFlingSignIsConvertedExactlyOnceAndDuplicateStopFinalizesOnce() {
        RecyclerView overview = categories.getOverview();
        downOnOverview();
        int[] consumed = {0, 0};
        content.onNestedPreScroll(overview, 0, -20, consumed, ViewCompat.TYPE_TOUCH);
        assertTrue(content.onNestedPreFling(overview, 0, -1234));
        assertEquals(1234f, callbacks.lastVelocity, 0f);
        content.onStopNestedScroll(overview, ViewCompat.TYPE_TOUCH);
        content.onStopNestedScroll(overview, ViewCompat.TYPE_TOUCH);
        assertEquals(1, callbacks.ends);
    }

    private void expandFirst() {
        AppDrawerCategoryBucket bucket = categories.getTileAdapter().bucketAt(0);
        AppDrawerCategoryTileView tile = (AppDrawerCategoryTileView)
            categories.getOverview().getChildAt(0);
        categories.onExpandRequested(bucket, tile);
        layout();
        content.advanceDrawerFx(1, 1f / 60f, true);
        layout();
    }

    private void downOnOverview() {
        View tile = categories.getOverview().getChildAt(0);
        float x = categories.getLeft() + tile.getLeft() + 4f;
        float y = categories.getTop() + categories.getOverview().getTop()
            + tile.getTop() + 4f;
        dispatch(MotionEvent.ACTION_DOWN, x, y);
    }

    private void dispatch(int action, float x, float y) {
        MotionEvent event = event(action, x, y);
        try { content.dispatchTouchEvent(event); }
        finally { event.recycle(); }
    }

    private MotionEvent event(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action, x, y, 0);
    }

    private void layout() {
        content.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
    }

    private static List<com.termux.app.launcher.model.LauncherAppEntry> manyBuckets() {
        List<com.termux.app.launcher.model.LauncherAppEntry> apps = new ArrayList<>(
            AppDrawerContentCategoriesTest.apps());
        apps.add(AppDrawerContentCategoriesTest.app("com.example.bank", "Bank",
            ApplicationInfo.CATEGORY_PRODUCTIVITY));
        apps.add(AppDrawerContentCategoriesTest.app("com.example.reader", "Reader",
            ApplicationInfo.CATEGORY_NEWS));
        return apps;
    }

    private static final class RecordingCallbacks implements AppDrawerContentView.Callbacks {
        int begins;
        int ends;
        int cancels;
        float lastVelocity;
        @Override public void onContentCloseDragBegin(float downRawY) { begins++; }
        @Override public void onContentCloseDragUpdate(float rawY) {}
        @Override public void onContentCloseDragEnd(float velocityPxPerSec) {
            ends++;
            lastVelocity = velocityPxPerSec;
        }
        @Override public void onContentCloseDragCancel() { cancels++; }
    }
}
