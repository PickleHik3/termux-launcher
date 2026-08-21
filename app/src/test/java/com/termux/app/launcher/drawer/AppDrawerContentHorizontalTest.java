package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.ViewCompat;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppDrawerContentHorizontalTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;
    private AppDrawerContentView content;
    private AppDrawerHorizontalPagerView pager;
    private AppDrawerSearchController search;
    private RecordingCallbacks callbacks;
    private long time;
    private long downTime;

    @Before public void setUp() {
        Robolectric.getForegroundThreadScheduler().pause();
        search = new AppDrawerSearchController();
        search.setHost(new AppDrawerSearchController.Host() {
            @Override public boolean isSearchActive() { return true; }
            @Override public void onSearchCommitRequested() {}
            @Override public void onSearchDismissRequested() {}
        });
        content = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        callbacks = new RecordingCallbacks();
        content.setCallbacks(callbacks);
        content.setViewType(AppDrawerViewType.HORIZONTAL);
        content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(WIDTH,
            content.horizontalPagerUsableHeight(HEIGHT), 1f, 11f, 4, 2));
        content.setInteractive(true);
        content.bind(null, search);
        search.setCatalogue(apps(20));
        content.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
        pager = content.getHorizontalPager();
    }

    @Test public void horizontalIsFullWidthAndRemovesTheRopeAndFormerStrip() {
        assertEquals(View.GONE, content.getRopeColumn().getVisibility());
        assertFalse(content.isColumnActive());
        assertEquals(View.VISIBLE, pager.getVisibility());
        assertEquals(WIDTH, pager.getRight() - pager.getLeft());
        float y = pager.getTop() + 20f;
        assertTrue(content.ownsPoint(WIDTH - 1f, y));
        assertTrue(content.ownsPoint(1f, y));
    }

    @Test public void firstDeliberateDownSwipeClosesWithoutArmingOrOverpull() {
        down(120f, pagerY());
        move(120f, pagerY() + 100f);
        assertEquals(1, callbacks.begins);
        assertEquals(1, callbacks.updates);
        assertTrue(pager.isHorizontalScrollLocked());
        assertEquals(0f, content.getOverpullTranslationPx(), 0f);
        up(120f, pagerY() + 100f);
        assertEquals(1, callbacks.ends);
        assertEquals(0, callbacks.cancels);
    }

    @Test public void horizontalDiagonalUpAndTapNeverClose() {
        down(120f, pagerY()); move(200f, pagerY() + 80f); up(200f, pagerY() + 80f);
        down(120f, pagerY()); move(120f, pagerY() - 100f); up(120f, pagerY() - 100f);
        down(120f, pagerY()); up(120f, pagerY());
        assertEquals(0, callbacks.begins);
        assertEquals(0, callbacks.ends);
    }

    @Test public void pageLatchSurvivesLaterVerticalDrift() {
        down(120f, pagerY());
        move(240f, pagerY());
        move(240f, pagerY() + 300f);
        assertEquals(0, callbacks.begins);
        assertFalse(pager.isHorizontalScrollLocked());
        up(240f, pagerY() + 300f);
    }

    @Test public void pendingDiagonalCannotPageBeforeLaterVerticalCloseClaim() {
        int firstPageLeft = pager.getChildAt(0).getLeft();
        float y = pagerY();
        down(300f, y);
        assertTrue(pager.isHorizontalScrollLocked());

        move(280f, y + 19f);
        move(270f, y + 28f);
        assertTrue(pager.isHorizontalScrollLocked());
        assertEquals(firstPageLeft, pager.getChildAt(0).getLeft());

        move(270f, y + 60f);
        assertEquals(1, callbacks.begins);
        assertTrue(pager.isHorizontalScrollLocked());
        assertEquals(firstPageLeft, pager.getChildAt(0).getLeft());
        up(270f, y + 60f);
    }

    @Test public void cancelStopsAClaimedCloseExactlyOnce() {
        down(120f, pagerY());
        move(120f, pagerY() + 100f);
        cancel(120f, pagerY() + 100f);
        assertEquals(1, callbacks.begins);
        assertEquals(0, callbacks.ends);
        assertEquals(1, callbacks.cancels);
    }

    @Test public void switchingModesCancelsTheNestedCloseAndUnlocksPaging() {
        down(120f, pagerY());
        move(120f, pagerY() + 100f);
        assertEquals(1, callbacks.begins);
        assertTrue(pager.isHorizontalScrollLocked());

        content.setViewType(AppDrawerViewType.VERTICAL);

        assertEquals(1, callbacks.cancels);
        assertFalse(pager.isHorizontalScrollLocked());
    }

    @Test public void claimedCloseRetainsTheCellTargetWithoutASyntheticCancel() {
        AppDrawerHorizontalPageAdapter.PageHolder page =
            (AppDrawerHorizontalPageAdapter.PageHolder)
                pager.findViewHolderForAdapterPosition(0);
        List<Integer> actions = new ArrayList<>();
        page.cells.get(0).setOnTouchListener((view, event) -> {
            actions.add(event.getActionMasked());
            return false;
        });
        float y = pager.getTop() + 25f;
        down(25f, y);
        move(25f, y + 100f);
        up(25f, y + 100f);
        assertTrue(actions.contains(MotionEvent.ACTION_DOWN));
        assertTrue(actions.contains(MotionEvent.ACTION_UP));
        assertFalse(actions.contains(MotionEvent.ACTION_CANCEL));
    }

    @Test public void fastClosingUpKeepsClickSuppressedThroughReentrantInteractivityReset() {
        AppDrawerHorizontalPageAdapter.PageHolder page =
            (AppDrawerHorizontalPageAdapter.PageHolder)
                pager.findViewHolderForAdapterPosition(0);
        AppDrawerAppCellView cell = page.cells.get(0);
        int[] clickCallbacks = {0};
        int[] launches = {0};
        cell.setOnClickListener(view -> {
            clickCallbacks[0]++;
            if (!pager.suppressCellClick()) launches[0]++;
        });
        cell.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
            return false;
        });
        callbacks.endAction = () -> content.setInteractive(false);
        float x = cell.getLeft() + Math.min(25f, cell.getWidth() * 0.5f);
        float y = pager.getTop() + cell.getTop() + Math.min(25f, cell.getHeight() * 0.5f);

        down(x, y);
        move(x, y + 40f);
        up(x, y + 40f);

        assertEquals(1, callbacks.ends);
        assertEquals(1, clickCallbacks[0]);
        assertEquals(0, launches[0]);
    }

    @Test public void slowReleaseStopsWithZeroAndDownwardFlingKeepsPositiveSign() {
        down(120f, pagerY());
        move(120f, pagerY() + 100f);
        time += 1000L;
        move(120f, pagerY() + 100f);
        time += 100L;
        up(120f, pagerY() + 100f);
        assertEquals(0f, callbacks.endVelocity, 0f);

        callbacks = new RecordingCallbacks();
        content.setCallbacks(callbacks);
        down(120f, pagerY());
        move(120f, pagerY() + 180f);
        up(120f, pagerY() + 260f);
        assertTrue(callbacks.endVelocity > 0f);
    }

    @Test public void queryRepartitionsResetsPageAndDots() {
        assertEquals(3, content.getPageIndicator().getPageCount());
        pager.setSelectedPage(2, false);
        search.handleCodePoint('1', false);
        assertEquals(0, pager.getSelectedPage());
        assertEquals(content.getHorizontalAdapter().getItemCount(),
            content.getPageIndicator().getPageCount());
    }

    @Test public void packageRefreshPreservesAndThenClampsTheCurrentPage() {
        pager.setSelectedPage(1, false);
        search.setCatalogue(apps(18));
        assertEquals(1, pager.getSelectedPage());
        search.setCatalogue(apps(3));
        assertEquals(0, pager.getSelectedPage());
        assertEquals(View.GONE, content.getPageIndicator().getVisibility());
    }

    @Test public void switchingBackRestoresVerticalGeometryAndDisarmedFirstPull() {
        content.setViewType(AppDrawerViewType.VERTICAL);
        content.setVerticalMetrics(AppDrawerGridMetrics.resolve(
            WIDTH - content.getColumnWidthPx(), 1f, 11f));
        content.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
        assertEquals(View.VISIBLE, content.getGrid().getVisibility());
        assertEquals(View.VISIBLE, content.getRopeColumn().getVisibility());
        assertEquals(Math.round(content.getColumnWidthPx()),
            ((android.widget.FrameLayout.LayoutParams) content.getGrid().getLayoutParams()).rightMargin);
        search.setCatalogue(apps(120));
        content.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
        MotionEvent down = event(MotionEvent.ACTION_DOWN, 100f, content.getGrid().getTop() + 20f);
        content.dispatchTouchEvent(down);
        down.recycle();
        int[] consumed = {0, 0};
        content.onNestedPreScroll(content.getGrid(), 0, -40, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(0, consumed[1]);
        assertEquals(0, callbacks.begins);
    }

    @Test public void switchingToVerticalUnbindsEveryAttachedHorizontalPageBeforeHiding() {
        List<AppDrawerHorizontalPageAdapter.PageHolder> attached = new ArrayList<>();
        for (int i = 0; i < pager.getChildCount(); i++) {
            attached.add((AppDrawerHorizontalPageAdapter.PageHolder)
                pager.getChildViewHolder(pager.getChildAt(i)));
        }
        assertFalse(attached.isEmpty());
        assertTrue(attached.get(0).cells.get(0).hasOnClickListeners());

        content.setViewType(AppDrawerViewType.VERTICAL);

        assertEquals(View.GONE, pager.getVisibility());
        for (AppDrawerHorizontalPageAdapter.PageHolder holder : attached) {
            for (AppDrawerAppCellView cell : holder.cells) {
                assertNull(cell.icon.getDrawable());
                assertEquals("", cell.label.getText().toString());
                assertNull(cell.getContentDescription());
                assertFalse(cell.hasOnClickListeners());
                assertFalse(cell.isLongClickable());
            }
        }
    }

    private float pagerY() { return pager.getTop() + 80f; }
    private void down(float x, float y) { dispatch(MotionEvent.ACTION_DOWN, x, y); }
    private void move(float x, float y) { dispatch(MotionEvent.ACTION_MOVE, x, y); }
    private void up(float x, float y) { dispatch(MotionEvent.ACTION_UP, x, y); }
    private void cancel(float x, float y) { dispatch(MotionEvent.ACTION_CANCEL, x, y); }

    private void dispatch(int action, float x, float y) {
        MotionEvent event = event(action, x, y);
        content.dispatchTouchEvent(event);
        event.recycle();
    }

    private MotionEvent event(int action, float x, float y) {
        time += 16L;
        if (action == MotionEvent.ACTION_DOWN) downTime = time;
        return MotionEvent.obtain(downTime, time, action, x, y, 0);
    }

    private static List<LauncherAppEntry> apps(int count) {
        List<LauncherAppEntry> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new LauncherAppEntry(
            new AppRef(String.format("pkg.%03d", i), "Main"), String.format("App %03d", i), null));
        return result;
    }

    private static final class RecordingCallbacks implements AppDrawerContentView.Callbacks {
        int begins;
        int updates;
        int ends;
        int cancels;
        float endVelocity;
        Runnable endAction;
        @Override public void onContentCloseDragBegin(float downRawY) { begins++; }
        @Override public void onContentCloseDragUpdate(float rawY) { updates++; }
        @Override public void onContentCloseDragEnd(float velocityPxPerSec) {
            ends++;
            endVelocity = velocityPxPerSec;
            if (endAction != null) endAction.run();
        }
        @Override public void onContentCloseDragCancel() { cancels++; }
    }
}
