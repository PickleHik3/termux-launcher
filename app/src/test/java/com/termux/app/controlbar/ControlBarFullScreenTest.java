package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.WorkspaceLayout;
import com.termux.app.tiling.desktop.TilingDesktop;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/** Full screen on the control bar (#426): the handle, the raise, the way back, #452's bring-up. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ControlBarFullScreenTest {

    private Activity activity;
    private boolean fullScreen;
    private int leaveCalls;
    private boolean surface;
    private final java.util.List<Boolean> changes = new java.util.ArrayList<>();
    private ControlBar bar;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        bar = new ControlBar(activity, new FakeHost(), new FakeDesktop());
        bar.setFullScreenHost(new ControlBar.FullScreenHost() {
            @Override public void onFullScreenChanged(boolean full) { changes.add(full); }
            @Override public boolean reducedMotion() { return true; }
            @Override public boolean surfaceOpen() { return surface; }
        });
    }

    private void enterFullScreen() {
        fullScreen = true;
        bar.onFrontChanged();
    }

    private void raiseByHandle() {
        bar.handleView().performClick();
    }

    private boolean barShown() { return bar.view().getVisibility() == View.VISIBLE; }

    private boolean handleShown() { return bar.handleView().getVisibility() == View.VISIBLE; }

    /** Round 4: the page's keyboard swipe took the handle's slide; a stream on the handle is the handle's. */
    @Test public void theHandleClaimsAStreamThatStartsOnItOnlyWhileItShows() {
        android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
        activity.setContentView(root);
        root.addView(bar.handleView(), new android.widget.FrameLayout.LayoutParams(400, 100));
        root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 800, 800);
        int[] at = new int[2];
        bar.handleView().getLocationOnScreen(at);
        assertFalse("no handle out of full screen", bar.handleClaims(at[0] + 200f, at[1] + 50f));
        enterFullScreen();
        root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 800, 800);
        bar.handleView().getLocationOnScreen(at);
        assertTrue(bar.handleClaims(at[0] + 200f, at[1] + 50f));
        assertFalse("beside the band", bar.handleClaims(at[0] + 600f, at[1] + 50f));
    }

    @Test public void fullScreenHidesTheBarDownToTheHandle() {
        assertTrue(barShown());
        assertFalse(handleShown());
        enterFullScreen();
        assertFalse(barShown());
        assertTrue(handleShown());
        assertEquals(Collections.singletonList(true), changes);
        assertEquals(FullScreenBar.Mode.HIDDEN_HANDLE, bar.fullScreenMode());
    }

    @Test public void aSlideUpOnTheHandleRaisesTheBarOverTheWindow() {
        enterFullScreen();
        View handle = bar.handleView();
        handle.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        handle.layout(0, 0, 400, 100);
        long now = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 200f, 80f, 0));
        assertFalse(barShown());
        handle.dispatchTouchEvent(MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, 200f, 0f, 0));
        assertTrue(barShown());
        assertFalse(handleShown());
        assertEquals(FullScreenBar.Mode.RAISED, bar.fullScreenMode());
        // The window is still full screen under it.
        assertTrue(bar.isFullScreen());
    }

    /** Round 3: only Back brought the bar back; a tap on the handle raises it as a slide does. */
    @Test public void aTapOnTheHandleRaisesTheBar() {
        enterFullScreen();
        View handle = bar.handleView();
        handle.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        handle.layout(0, 0, 400, 100);
        long now = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 200f, 80f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, 200f, 78f, 0));
        assertTrue(barShown());
        assertFalse(handleShown());
        assertEquals(FullScreenBar.Mode.RAISED, bar.fullScreenMode());
    }

    @Test public void aSidewaysDragOnTheHandleIsNoTap() {
        enterFullScreen();
        View handle = bar.handleView();
        handle.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        handle.layout(0, 0, 400, 100);
        long now = SystemClock.uptimeMillis();
        handle.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 80f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, 300f, 80f, 0));
        handle.dispatchTouchEvent(MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, 300f, 80f, 0));
        assertFalse(barShown());
        assertTrue(handleShown());
    }

    @Test public void theRaisedBarDropsBackAfterAToolIsUsed() {
        enterFullScreen();
        raiseByHandle();
        bar.onTool(ControlBarTool.SEARCH);
        assertFalse(barShown());
        assertTrue(handleShown());
    }

    @Test public void theRaisedBarDropsBackOnATapOutsideNotOnOne() {
        enterFullScreen();
        raiseByHandle();
        // The bar was never laid out here: its bounds are empty, so any point is outside.
        bar.onWindowTouchDown(5f, 5f);
        assertFalse(barShown());
        assertTrue(handleShown());
    }

    @Test public void aTapOutsideDoesNothingWhileTheBarIsNotRaised() {
        enterFullScreen();
        bar.onWindowTouchDown(5f, 5f);
        assertTrue(handleShown());
        assertFalse(barShown());
    }

    @Test public void theRaisedBarDropsBackAfterTheIdleTime() {
        enterFullScreen();
        raiseByHandle();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FullScreenBar.IDLE_MS - 100));
        assertTrue(barShown());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        assertFalse(barShown());
        assertTrue(handleShown());
    }

    @Test public void aTouchOnTheRaisedBarKeepsItUpAnotherIdleTime() {
        enterFullScreen();
        raiseByHandle();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FullScreenBar.IDLE_MS - 500));
        long now = SystemClock.uptimeMillis();
        bar.view().dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1f, 1f, 0));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FullScreenBar.IDLE_MS - 500));
        assertTrue(barShown());
    }

    @Test public void backLeavesFullScreenAndRestoresTheBar() {
        enterFullScreen();
        assertTrue(bar.leaveFullScreen());
        assertEquals(1, leaveCalls);
        // The desktop answered with the new front: the bar is in its place again.
        bar.onFrontChanged();
        assertTrue(barShown());
        assertFalse(handleShown());
        assertEquals(java.util.Arrays.asList(true, false), changes);
    }

    @Test public void backWithNoFullScreenIsNotSpent() {
        assertFalse(bar.leaveFullScreen());
        assertEquals(0, leaveCalls);
    }

    @Test public void theControlBarCardBringsAHiddenBarUpAndPutsItBack() {
        enterFullScreen();
        assertTrue(bar.raise());
        assertTrue(barShown());
        assertFalse(handleShown());
        bar.lower();
        assertFalse(barShown());
        assertTrue(handleShown());
    }

    @Test public void theIdleDropWaitsWhileAPinnedAppMenuIsUpAndResumesAfter() {
        enterFullScreen();
        raiseByHandle();
        surface = true;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FullScreenBar.IDLE_MS * 3));
        assertTrue(barShown());
        bar.onWindowTouchDown(5f, 5f);
        assertTrue("a tap outside does not close what the bar opened", barShown());
        surface = false;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FullScreenBar.IDLE_MS * 2));
        assertFalse(barShown());
        assertTrue(handleShown());
    }

    @Test public void theIdleDropWaitsWhileTheWorkspaceMenuIsUp() {
        enterFullScreen();
        raiseByHandle();
        bar.onPlaceHeld();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FullScreenBar.IDLE_MS * 3));
        assertTrue(barShown());
    }

    @Test public void theCardBringUpWhileRaisedKeepsTheBarUpUntilItCloses() {
        enterFullScreen();
        raiseByHandle();
        assertTrue(bar.raise());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FullScreenBar.IDLE_MS * 3));
        bar.onWindowTouchDown(5f, 5f);
        assertTrue(barShown());
        bar.lower();
        assertTrue("still raised, with its idle time again", barShown());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FullScreenBar.IDLE_MS + 100));
        assertFalse(barShown());
        assertTrue(handleShown());
    }

    @Test public void theHandleIsDressedWithTheSchemesInkTheFirstTimeItShows() {
        enterFullScreen();
        android.graphics.drawable.Drawable pill = bar.handleView() instanceof android.view.ViewGroup
            ? ((android.view.ViewGroup) bar.handleView()).getChildAt(0).getBackground() : null;
        assertTrue(pill instanceof android.graphics.drawable.GradientDrawable);
        assertEquals(0xFFFFFFFF & 0x00FFFFFF,
            ((android.graphics.drawable.GradientDrawable) pill).getColor().getDefaultColor() & 0x00FFFFFF);
    }

    @Test public void theBringUpHookDoesNothingForABarAlreadyShowing() {
        assertFalse(bar.raise());
        enterFullScreen();
        raiseByHandle();
        assertTrue("a slide-raised bar is brought up by the card", bar.raise());
        assertFalse(bar.raise());
    }

    @Test public void theWorkspacesReachOverTheBarsRoomInFullScreenAndBackAgain() {
        RelativeLayout host = new RelativeLayout(activity);
        View stack = new View(activity);
        View workspaces = new View(activity);
        host.addView(workspaces, new RelativeLayout.LayoutParams(-1, -1));
        host.addView(stack, new RelativeLayout.LayoutParams(-1, 100));
        bar.view().setId(View.generateViewId());
        ControlBarLayout layout = new ControlBarLayout(bar, host, stack, workspaces, new LayoutHost());
        host.addView(bar.view(), layout.firstParams());
        host.addView(bar.handleView(), layout.handleParams());
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) workspaces.getLayoutParams();
        assertEquals(bar.view().getId(), params.getRules()[RelativeLayout.ABOVE]);

        layout.setFullScreen(true);
        params = (RelativeLayout.LayoutParams) workspaces.getLayoutParams();
        assertEquals(0, params.getRules()[RelativeLayout.ABOVE]);
        assertTrue(params.getRules()[RelativeLayout.ALIGN_PARENT_BOTTOM] != 0);

        layout.setFullScreen(false);
        params = (RelativeLayout.LayoutParams) workspaces.getLayoutParams();
        assertEquals(bar.view().getId(), params.getRules()[RelativeLayout.ABOVE]);
        assertEquals(0, params.getRules()[RelativeLayout.ALIGN_PARENT_BOTTOM]);
    }

    @Test public void theHandleStandsAtTheBarsBottomMargin() {
        RelativeLayout host = new RelativeLayout(activity);
        View stack = new View(activity);
        ControlBarLayout layout = new ControlBarLayout(bar, host, stack, null, new LayoutHost());
        RelativeLayout.LayoutParams handle = layout.handleParams();
        RelativeLayout.LayoutParams barParams = layout.firstParams();
        assertEquals(barParams.bottomMargin, handle.bottomMargin);
        float density = activity.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(FullScreenHandle.BAND_HEIGHT_DP * density), handle.height);
    }

    private final class LayoutHost implements ControlBarLayout.Host {
        @Override public int keptBottomPx() { return 48; }
        @Override public int keyboardDownLaidOutPx() { return 0; }
        @Override public boolean launcherKeyboardShown() { return false; }
        @Override public boolean phoneKeyboardShown() { return false; }
        @Override public boolean terminalMode() { return false; }
        @NonNull @Override public ControlBarPlacement.RailSide railSide() { return ControlBarPlacement.RailSide.RIGHT; }
        @Override public int cutoutClearPx(int cutoutPx) { return 0; }
        @Override public int marginDp() { return 6; }
        @Override public void pagesBesideBar(int leftPx, int rightPx) { }
        @Override public void post(@NonNull Runnable work) { }
    }

    private final class FakeDesktop implements ControlBar.Desktop {
        @Override public void setFrontListener(@Nullable TilingDesktop.FrontListener listener) { }
        @NonNull @Override public Place front() { return Place.workspace(1); }
        @NonNull @Override public List<TilingWindow> frontWindows() { return Collections.emptyList(); }
        @Nullable @Override public TilingWindow frontMainWindow() { return null; }
        @Override public long frontFocusedWindowId() { return -1; }
        @Override public boolean focusWindow(long windowId) { return true; }
        @Override public void split() { }
        @Override public void newTerminal() { }
        @Override public boolean toggleFrontKeyboard() { return false; }
        @Override public boolean toggleFrontMouseMode() { return false; }
        @Nullable @Override public WorkspaceLayout frontLayout() { return WorkspaceLayout.SCROLLING; }
        @Override public boolean setFrontLayout(@NonNull WorkspaceLayout layout) { return true; }
        @Override public boolean frontFullScreen() { return fullScreen; }
        @Override public boolean setFrontFullScreen(boolean value) {
            if (value) return false;
            leaveCalls++;
            fullScreen = false;
            return true;
        }
    }

    private static final class FakeHost implements ControlBar.Host {
        @Override public int homePageCount() { return 1; }
        @Override public int homePageInFront() { return 0; }
        @Override public void showHomePage(int page) { }
        @NonNull @Override public List<ControlBarView.PinnedApp> pinnedApps() { return Collections.emptyList(); }
        @Override public void openPinnedApp(int index) { }
        @Override public void holdPinnedApp(int index, @NonNull View anchor) { }
        @Override public void toggleKeyboard() { }
        @Override public void openSearch() { }
        @Override public void openDrawer() { }
        @Override public void openOverview() { }
        @Override public boolean drawerToolShown() { return false; }
        @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
        @Override public float cornerRadiusPx() { return 0f; }
        @Override public int ink() { return 0xFFFFFFFF; }
        @Override public int accent() { return 0xFF00FF00; }
    }
}
