package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TerminalPaneController;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FullStatusBarTerminalResizeCountingTest {
    @Test public void realPanesDeliverOnlyOneTerminalAndPtyResizeAtEachSettle() {
        Fixture fixture = new Fixture();
        fixture.layout();
        fixture.idle();
        fixture.viewUpdates.set(0);
        fixture.firstSessionUpdates.set(0);
        long accessoryBaseline = ReflectionHelpers.getField(fixture.activity,
            "mLastAccessoryGeometryApplyUptimeMs");

        assertTrue(fixture.full.open(TopStatusBarState.EXPANDED));
        int openingFrames = 0;
        while (fixture.full.motion() == FullStatusBarController.Motion.OPENING) {
            fixture.frames.runOne();
            fixture.layout();
            openingFrames++;
            if (openingFrames == 3) {
                fixture.panes.showWindow(fixture.secondWindow);
                fixture.layout();
            }
            if (openingFrames == 5) {
                View accessory = fixture.activity.findViewById(R.id.accessory_stack_container);
                accessory.getLayoutParams().height = 0; // keyboard closes mid-FULL
                accessory.setVisibility(View.GONE);
                fixture.layout();
                fixture.full.onParentLayoutChanged();
            }
            if (fixture.full.motion() == FullStatusBarController.Motion.OPENING) {
                fixture.idle();
                assertEquals("TerminalView.updateSize must be zero on spring frames",
                    0, fixture.viewUpdates.get());
                assertEquals("TerminalSession.updateSize must be zero on spring frames",
                    0, fixture.firstSessionUpdates.get() + fixture.secondSessionUpdates.get());
            }
        }
        fixture.idle();
        assertTrue(openingFrames > 1);
        assertEquals(1, fixture.viewUpdates.get());
        assertEquals(0, fixture.firstSessionUpdates.get());
        assertEquals(1, fixture.secondSessionUpdates.get());
        assertEquals("FULL terminal layout frames must not re-enter accessory geometry",
            accessoryBaseline, ReflectionHelpers.<Long>getField(fixture.activity,
                "mLastAccessoryGeometryApplyUptimeMs").longValue());

        fixture.viewUpdates.set(0);
        fixture.firstSessionUpdates.set(0);
        fixture.secondSessionUpdates.set(0);
        assertTrue(fixture.full.onBackPressed());
        int closingFrames = 0;
        while (fixture.full.motion() == FullStatusBarController.Motion.CLOSING) {
            fixture.frames.runOne();
            fixture.layout();
            closingFrames++;
            if (fixture.full.motion() == FullStatusBarController.Motion.CLOSING) {
                fixture.idle();
                assertEquals(0, fixture.viewUpdates.get());
                assertEquals(0, fixture.secondSessionUpdates.get());
            }
        }
        fixture.idle();
        assertTrue(closingFrames > 1);
        assertEquals(1, fixture.viewUpdates.get());
        assertEquals(1, fixture.secondSessionUpdates.get());
    }

    private static final class Fixture {
        final TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        final AtomicInteger viewUpdates = new AtomicInteger();
        final TerminalSession first = session();
        final TerminalSession second = session();
        final AtomicInteger firstSessionUpdates = new AtomicInteger();
        final AtomicInteger secondSessionUpdates = new AtomicInteger();
        final TerminalPaneController panes;
        final TerminalPaneController.Window secondWindow;
        final FakeFrames frames = new FakeFrames();
        final FullStatusBarController full;
        final ViewGroup root;
        final View statusHost;
        final ViewGroup contentColumn;

        Fixture() {
            first.setSizeUpdateObserverForTests((columns, rows, cellWidth, cellHeight, keepBottom) -> {
                firstSessionUpdates.incrementAndGet();
                return true;
            });
            second.setSizeUpdateObserverForTests((columns, rows, cellWidth, cellHeight, keepBottom) -> {
                secondSessionUpdates.incrementAndGet();
                return true;
            });
            activity.setContentView(R.layout.activity_termux);
            root = activity.findViewById(R.id.activity_termux_root_view);
            statusHost = activity.findViewById(R.id.terminal_window_bar_host);
            statusHost.setVisibility(View.VISIBLE);
            contentColumn = activity.findViewById(R.id.terminal_content_column);
            FrameLayout paneHost = activity.findViewById(R.id.terminal_pane_host);
            panes = new TerminalPaneController(new TerminalPaneController.Host() {
                @Override public TerminalSession createShell(String cwd) { return null; }
                @Override public void configurePaneView(TerminalView view) {
                    view.setTerminalViewClient(new NoOpTerminalViewClient());
                    view.setTextSize(24);
                    view.setSizeUpdateObserverForTests(ignored -> viewUpdates.incrementAndGet());
                }
                @Override public void removeShell(TerminalSession session) { }
                @Override public void onActivePaneChanged() { }
                @Override public void onTreesChanged() { }
                @Override public String defaultCwd() { return "/"; }
            }, paneHost, LayoutInflater.from(activity));
            TerminalPaneController.Window firstWindow = panes.newWindow(first);
            secondWindow = panes.newWindow(second);
            panes.showWindow(firstWindow);
            ReflectionHelpers.setField(activity, "mPaneController", panes);

            View accessory = activity.findViewById(R.id.accessory_stack_container);
            accessory.getLayoutParams().height = 180;
            accessory.setVisibility(View.VISIBLE);
            full = new FullStatusBarController(new FullStatusBarController.Host() {
                private int height() {
                    return statusHost.getLayoutParams().height > 0
                        ? statusHost.getLayoutParams().height : statusHost.getHeight();
                }
                @Override public int currentHeight() { return height(); }
                @Override public int normalHeight(@NonNull TopStatusBarState state) { return 96; }
                @Override public int parentMeasuredHeight() { return contentColumn.getMeasuredHeight(); }
                @Override public int parentPaddingTop() { return contentColumn.getPaddingTop(); }
                @Override public int parentPaddingBottom() { return contentColumn.getPaddingBottom(); }
                @Override public int hostTopMargin() { return 0; }
                @Override public boolean reducedMotion() { return false; }
                @Override public void cancelNormalAnimatorKeepingCurrent() { }
                @Override public void beginTerminalResize() { panes.beginHostSurfaceResize(); }
                @Override public void applyFrame(int height, float progress) {
                    statusHost.getLayoutParams().height = height;
                    statusHost.requestLayout();
                }
                @Override public void finishTerminalResizeAfterLayout() {
                    new Handler(Looper.getMainLooper()).post(
                        panes::finishHostSurfaceResizeKeepingBottom);
                }
                @Override public void applyNormalState(@NonNull TopStatusBarState state) { }
                @Override public void onEngagementChanged(boolean engaged,
                    @NonNull TopStatusBarState target) { }
            }, frames);
            ReflectionHelpers.setField(activity, "mFullStatusBarController", full);
            ReflectionHelpers.callInstanceMethod(activity, "addAccessoryLayoutChangeListeners");
        }

        void layout() {
            int width = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
            int height = View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY);
            root.measure(width, height);
            root.layout(0, 0, 1080, 1200);
        }

        void idle() { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); }
    }

    private static TerminalSession session() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }

    private static final class FakeFrames implements FullStatusBarController.FrameScheduler {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        long nanos;
        @Override public void post(@NonNull Runnable frame) { queue.add(frame); }
        @Override public void remove(@NonNull Runnable frame) { queue.remove(frame); }
        @Override public long nowNanos() { return nanos; }
        void runOne() {
            Runnable runnable = queue.poll();
            if (runnable == null) throw new AssertionError("missing spring frame");
            nanos += 16_666_667L;
            runnable.run();
        }
    }

    private static final class NoOpTerminalViewClient implements TerminalViewClient {
        @Override public float onScale(float scale) { return scale; }
        @Override public void onSingleTapUp(MotionEvent event) { }
        @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
        @Override public boolean shouldEnforceCharBasedInput() { return false; }
        @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
        @Override public boolean isTerminalViewSelected() { return true; }
        @Override public void copyModeChanged(boolean copyMode) { }
        @Override public boolean onKeyDown(int keyCode, KeyEvent event, TerminalSession session) { return false; }
        @Override public boolean onKeyUp(int keyCode, KeyEvent event) { return false; }
        @Override public boolean onLongPress(MotionEvent event) { return false; }
        @Override public boolean readControlKey() { return false; }
        @Override public boolean readAltKey() { return false; }
        @Override public boolean readShiftKey() { return false; }
        @Override public boolean readFnKey() { return false; }
        @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
        @Override public void onEmulatorSet() { }
        @Override public void logError(String tag, String message) { }
        @Override public void logWarn(String tag, String message) { }
        @Override public void logInfo(String tag, String message) { }
        @Override public void logDebug(String tag, String message) { }
        @Override public void logVerbose(String tag, String message) { }
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { }
        @Override public void logStackTrace(String tag, Exception e) { }
    }
}
