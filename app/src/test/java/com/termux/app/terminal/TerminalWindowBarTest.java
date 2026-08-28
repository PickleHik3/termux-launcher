package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWindowBarTest {

    @Test
    public void setWindows_marksSelectionAndDispatchesClicks() {
        TerminalWindowBar bar = new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
        assertTrue(bar.getClipToPadding());
        AtomicInteger selected = new AtomicInteger(-1);
        AtomicInteger created = new AtomicInteger();
        bar.setOnWindowSelectedListener(selected::set);
        bar.setOnCreateWindowListener(created::incrementAndGet);
        bar.setWindows(Arrays.asList(
            new TerminalWindowBar.WindowItem("fish-icon home", "fish in home"),
            new TerminalWindowBar.WindowItem("ssh-icon zbook", "ssh in zbook")), 1);

        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        assertEquals(3, tabs.getChildCount());
        assertFalse(tabs.getChildAt(0).isSelected());
        assertTrue(tabs.getChildAt(1).isSelected());
        assertEquals("ssh-icon zbook", ((TextView) tabs.getChildAt(1)).getText().toString());
        assertEquals(Math.round(3.5f * bar.getResources().getDisplayMetrics().density),
            tabs.getChildAt(1).getPaddingLeft());
        assertEquals(tabs.getChildAt(1).getPaddingLeft(), tabs.getChildAt(1).getPaddingRight());
        assertFalse(((TextView) tabs.getChildAt(1)).getIncludeFontPadding());

        tabs.getChildAt(0).performClick();
        assertEquals(0, selected.get());
        tabs.getChildAt(2).performClick();
        assertEquals(1, created.get());
        assertEquals(null, tabs.getChildAt(2).getBackground());
    }

    @Test
    public void addWindowIcon_isGeometricallyCenteredWithoutTextPadding() {
        TerminalWindowBar bar = new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
        bar.setWindows(Arrays.asList(new TerminalWindowBar.WindowItem("home", "home")), 0);
        int rowHeight = Math.round(24f * bar.getResources().getDisplayMetrics().density);
        bar.measure(exact(240), exact(rowHeight));
        bar.layout(0, 0, 240, rowHeight);

        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        ImageView add = (ImageView) tabs.getChildAt(1);
        assertEquals(ImageView.ScaleType.CENTER, add.getScaleType());
        assertEquals(add.getPaddingTop(), add.getPaddingBottom());
        assertEquals(add.getHeight() / 2f, (add.getTop() + add.getBottom()) / 2f, .01f);
        assertEquals(add.getDrawable().getIntrinsicWidth(), add.getDrawable().getIntrinsicHeight());
        assertEquals(Math.round(10f * bar.getResources().getDisplayMetrics().density),
            add.getDrawable().getIntrinsicWidth());
        assertEquals(0f, add.getTranslationY(), .01f);
    }

    @Test
    public void nullSession_usesStableWindowNumber() {
        TerminalWindowBar.WindowItem item = TerminalWindowBar.itemFor(null, 2);
        assertEquals("window 3", item.spokenLabel);
        assertTrue(item.label.endsWith(" 3"));
    }

    @Test
    public void middleEllipsize_preservesMeaningfulEnds() {
        assertEquals("verylong…name", TerminalWindowBar.middleEllipsize("verylongfoldername", 13));
    }

    /**
     * The pills draw the radius they are handed, in either style. Who decides that number moved to
     * the caller when the status row grew its own chip-radius knob: the bar used to throw the
     * radius away whenever the surface was Docked, which is exactly what the knob has to be able to
     * override.
     */
    @Test
    public void surfaceStyle_updatesTabsWithTheRadiusItIsGiven() {
        TerminalWindowBar bar = new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
        bar.setWindows(Arrays.asList(new TerminalWindowBar.WindowItem("home", "home")), 0);
        bar.setSurfaceStyle(false, 0f);
        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        assertEquals(0f,
            ((GradientDrawable) tabs.getChildAt(0).getBackground()).getCornerRadius(), .01f);

        // Docked, with the chip knob dialled in: the pills round.
        bar.setSurfaceStyle(false, 10f);
        tabs = (LinearLayout) bar.getChildAt(0);
        assertEquals(10f,
            ((GradientDrawable) tabs.getChildAt(0).getBackground()).getCornerRadius(), .01f);

        bar.setSurfaceStyle(true, 40f);
        tabs = (LinearLayout) bar.getChildAt(0);
        assertEquals(40f,
            ((GradientDrawable) tabs.getChildAt(0).getBackground()).getCornerRadius(), .01f);
    }

    @Test
    public void selectionChange_reusesStationaryTabsInsteadOfWigglingSelectedLabel() {
        TerminalWindowBar bar = new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
        java.util.List<TerminalWindowBar.WindowItem> items = Arrays.asList(
            new TerminalWindowBar.WindowItem("home", "home"),
            new TerminalWindowBar.WindowItem("work", "work"),
            new TerminalWindowBar.WindowItem("ssh", "ssh"));
        bar.setWindows(items, 0);
        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        android.view.View second = tabs.getChildAt(1);

        bar.setWindows(items, 1);

        assertSame(second, tabs.getChildAt(1));
        assertEquals(0f, tabs.getChildAt(1).getTranslationX(), .01f);
        assertEquals(1f, tabs.getChildAt(1).getAlpha(), .01f);
        assertTrue(tabs.getChildAt(1).isSelected());
        assertFalse(tabs.getChildAt(0).isSelected());
        assertEquals(560L, TerminalWindowBar.WINDOW_SWITCH_ANIMATION_DURATION_MS);
    }

    /**
     * Attention is a separate state from busy and outranks it: a window waiting on the user is not
     * working. Both drive the same breath, so either one alone has to start it.
     */
    @Test
    public void attentionOnlyChange_reachesTheStripAndStartsTheBreath() {
        TerminalWindowBar bar = attachedBar();
        java.util.List<TerminalWindowBar.WindowItem> idle = Arrays.asList(
            new TerminalWindowBar.WindowItem("home", "home"),
            new TerminalWindowBar.WindowItem("work", "work"));
        bar.setWindows(idle, 0);
        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        android.view.View second = tabs.getChildAt(1);
        assertFalse(bar.isBusyAnimationRunning());

        bar.setWindows(Arrays.asList(idle.get(0), idle.get(1).withAttention(true)), 0);

        assertSame(second, tabs.getChildAt(1));
        assertTrue(bar.isBusyAnimationRunning());
    }

    @Test
    public void attentionOutranksBusyOnTheSameWindow() {
        TerminalWindowBar.WindowItem both = new TerminalWindowBar.WindowItem("home", "home")
            .withBusy(true).withAttention(true);
        assertTrue(both.busy);
        assertTrue(both.attention);
        // withBusy/withAttention compose rather than overwrite, so the strip can decide which rim
        // wins; the pill must not end up with the working state silently erased.
        assertSame(both, both.withAttention(true));
    }

    @Test
    public void busyOnlyChange_reachesTheStripWithoutReinflatingThePills() {
        // The early return bails when the labels and the selection are unchanged, and a busy-only
        // flip changes neither — so without sameActivity in the guard this state would be dropped. The
        // pill views still have to be reused, or starting a command would rebuild the row.
        TerminalWindowBar bar = attachedBar();
        java.util.List<TerminalWindowBar.WindowItem> idle = Arrays.asList(
            new TerminalWindowBar.WindowItem("home", "home"),
            new TerminalWindowBar.WindowItem("work", "work"));
        bar.setWindows(idle, 0);
        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        android.view.View first = tabs.getChildAt(0);
        assertFalse(bar.isBusyAnimationRunning());

        bar.setWindows(Arrays.asList(idle.get(0).withBusy(true), idle.get(1)), 0);

        assertSame(first, tabs.getChildAt(0));
        assertTrue(bar.isBusyAnimationRunning());
        assertTrue(tabs.getChildAt(0).getContentDescription().toString().contains("working"));
        // The reuse path has to refresh descriptions too, or the second pill keeps a stale one.
        assertFalse(tabs.getChildAt(1).getContentDescription().toString().contains("working"));
    }

    @Test
    public void busyGoingIdleStopsTheAnimation() {
        TerminalWindowBar bar = attachedBar();
        TerminalWindowBar.WindowItem item = new TerminalWindowBar.WindowItem("home", "home");
        bar.setWindows(Arrays.asList(item.withBusy(true)), 0);
        assertTrue(bar.isBusyAnimationRunning());

        bar.setWindows(Arrays.asList(item), 0);

        assertFalse(bar.isBusyAnimationRunning());
    }

    @Test
    public void detachStopsTheAnimation() {
        // Otherwise a backgrounded activity keeps waking the Choreographer for an invisible sweep.
        android.widget.FrameLayout host = attachedHost();
        TerminalWindowBar bar = (TerminalWindowBar) host.getChildAt(0);
        bar.setWindows(Arrays.asList(
            new TerminalWindowBar.WindowItem("home", "home", true)), 0);
        assertTrue(bar.isBusyAnimationRunning());

        host.removeView(bar);

        assertFalse(bar.isBusyAnimationRunning());
    }

    @Test
    public void withBusy_leavesTheLabelsAlone() {
        TerminalWindowBar.WindowItem item = new TerminalWindowBar.WindowItem("home", "in home");

        TerminalWindowBar.WindowItem busy = item.withBusy(true);

        assertEquals(item.label, busy.label);
        assertEquals(item.spokenLabel, busy.spokenLabel);
        assertTrue(busy.busy);
        assertFalse(item.busy);
        // Same flag, same instance: nothing to copy.
        assertSame(busy, busy.withBusy(true));
    }

    @Test
    public void edgeOverswipeRequiresExtraDistanceAndReversalCancelsIt() {
        TerminalWindowBar bar = new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
        List<Boolean> requests = new ArrayList<>();
        bar.setOnEdgeOverswipeListener(requests::add);
        bar.setStatusBarCollapsed(true);
        bar.measure(exact(220), exact(30));
        bar.layout(0, 0, 220, 30);

        touch(bar, android.view.MotionEvent.ACTION_DOWN, 20, 15);
        touch(bar, android.view.MotionEvent.ACTION_MOVE, 80, 15);
        touch(bar, android.view.MotionEvent.ACTION_MOVE, 40, 15); // reverse clears accumulated 60dp
        touch(bar, android.view.MotionEvent.ACTION_UP, 40, 15);
        assertEquals(0, requests.size());

        touch(bar, android.view.MotionEvent.ACTION_DOWN, 20, 15);
        touch(bar, android.view.MotionEvent.ACTION_MOVE, 90, 15);
        touch(bar, android.view.MotionEvent.ACTION_UP, 90, 15);
        assertEquals(Collections.singletonList(Boolean.FALSE), requests);
    }

    /**
     * A bar inside a real attached window, because the busy animator refuses to run while detached
     * or in an invisible window — the whole point of those guards.
     */
    private static TerminalWindowBar attachedBar() {
        return (TerminalWindowBar) attachedHost().getChildAt(0);
    }

    private static android.widget.FrameLayout attachedHost() {
        android.app.Activity activity = org.robolectric.Robolectric
            .buildActivity(android.app.Activity.class).setup().get();
        android.widget.FrameLayout host = new android.widget.FrameLayout(activity);
        host.addView(new TerminalWindowBar(activity, null));
        activity.setContentView(host);
        return host;
    }

    private static int exact(int size) {
        return android.view.View.MeasureSpec.makeMeasureSpec(
            size, android.view.View.MeasureSpec.EXACTLY);
    }

    private static void touch(TerminalWindowBar bar, int action, float x, float y) {
        android.view.MotionEvent event = android.view.MotionEvent.obtain(0, 1, action, x, y, 0);
        bar.onTouchEvent(event);
        event.recycle();
    }
}
