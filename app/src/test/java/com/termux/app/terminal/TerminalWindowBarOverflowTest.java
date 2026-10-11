package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The window chips past the end of their room: feathered there, and swiped or scrolled to. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWindowBarOverflowTest {

    @Test public void aRowThatFitsIsNotFeatheredAnywhere() throws Exception {
        TerminalWindowBar bar = bar();
        bar.setWindows(windows(2), 0);
        layOut(bar, 1000);
        assertEquals(0f, strength(bar, "getLeftFadingEdgeStrength"), 0f);
        assertEquals(0f, strength(bar, "getRightFadingEdgeStrength"), 0f);
    }

    @Test public void anOverflowingRowFeathersOnlyTheEndsWithMoreBeyondThem() throws Exception {
        TerminalWindowBar bar = bar();
        bar.setWindows(windows(8), 0);
        layOut(bar, 240);
        assertEquals(0f, strength(bar, "getLeftFadingEdgeStrength"), 0f);
        assertEquals(1f, strength(bar, "getRightFadingEdgeStrength"), 0f);

        bar.scrollTo(Integer.MAX_VALUE, 0);
        assertEquals(1f, strength(bar, "getLeftFadingEdgeStrength"), 0f);
        assertEquals(0f, strength(bar, "getRightFadingEdgeStrength"), 0f);
        assertEquals(Math.round(TerminalWindowBar.OVERFLOW_FADE_DP * density(bar)),
            bar.getHorizontalFadingEdgeLength());
    }

    @Test public void aNewCurrentWindowPastTheEndIsScrolledInClearOfTheFade() {
        TerminalWindowBar bar = bar();
        List<TerminalWindowBar.WindowItem> items = windows(12);
        bar.setWindows(items, 0);
        layOut(bar, 240);
        assertEquals(0, bar.getScrollX());

        bar.setWindows(items, 9);
        layOut(bar, 240);

        View chip = tabs(bar).getChildAt(9);
        assertTrue("the chip started out past the end", bar.getScrollX() > 0);
        int viewport = bar.getWidth() - bar.getPaddingLeft() - bar.getPaddingRight();
        assertEquals("as little travel as it takes",
            chip.getRight() + bar.getHorizontalFadingEdgeLength() - viewport, bar.getScrollX());
    }

    @Test public void aRefreshOfTheSameWindowLeavesTheRowWhereTheFingerPutIt() {
        TerminalWindowBar bar = bar();
        List<TerminalWindowBar.WindowItem> items = windows(8);
        bar.setWindows(items, 7);
        layOut(bar, 240);
        assertTrue("the current window was scrolled to", bar.getScrollX() > 0);

        bar.scrollTo(0, 0);
        List<TerminalWindowBar.WindowItem> busy = new ArrayList<>(items);
        busy.set(7, items.get(7).withBusy(true));
        bar.setWindows(busy, 7);
        layOut(bar, 240);

        assertEquals(0, bar.getScrollX());
    }

    @Test public void aCurrentWindowAlreadyInViewDoesNotMoveTheRow() {
        TerminalWindowBar bar = bar();
        List<TerminalWindowBar.WindowItem> items = windows(8);
        bar.setWindows(items, 0);
        layOut(bar, 240);

        bar.setWindows(items, 1);
        layOut(bar, 240);

        assertEquals(0, bar.getScrollX());
    }

    // ---- helpers ----

    private static TerminalWindowBar bar() {
        return new TerminalWindowBar(ApplicationProvider.getApplicationContext(), null);
    }

    private static List<TerminalWindowBar.WindowItem> windows(int count) {
        List<TerminalWindowBar.WindowItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new TerminalWindowBar.WindowItem("window " + i, "window " + i));
        }
        return items;
    }

    private static LinearLayout tabs(TerminalWindowBar bar) {
        return (LinearLayout) bar.getChildAt(0);
    }

    private static void layOut(TerminalWindowBar bar, int width) {
        int rowHeight = Math.round(24f * density(bar));
        bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(rowHeight, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, width, rowHeight);
    }

    private static float density(View view) {
        return view.getResources().getDisplayMetrics().density;
    }

    private static float strength(TerminalWindowBar bar, String name) throws Exception {
        Method method = HorizontalScrollView.class.getDeclaredMethod(name);
        method.setAccessible(true);
        float value = (Float) method.invoke(bar);
        return Math.max(0f, Math.min(1f, value));
    }
}
