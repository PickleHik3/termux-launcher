package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.tiling.WindowKind;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The bar's view reports a tap on a mark, a slide along the marks and a tool's tap (#424). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
public class ControlBarViewTest {

    private final List<String> heard = new ArrayList<>();
    private ControlBarView view;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        view = new ControlBarView(activity, new ControlBarView.Listener() {
            @Override public void onMark(int index) { heard.add("mark " + index); }
            @Override public void onTool(@NonNull ControlBarTool tool) { heard.add("tool " + tool); }
            @Override public void onPinnedApp(int index) { heard.add("app " + index); }
        });
        view.setRows(ControlBarRows.of(ControlBarFront.workspace(1, WindowKind.SHELL, Arrays.asList(
            new ControlBarFront.Window(1, WindowKind.SHELL), new ControlBarFront.Window(2, WindowKind.SHELL),
            new ControlBarFront.Window(3, WindowKind.SHELL)), 1)));
        view.measure(View.MeasureSpec.makeMeasureSpec(388, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 388, 96);
    }

    /** Mark {@code index}'s centre at mdpi: marks are 32dp cells 2dp apart after the badge. */
    private float markX(int index) {
        return firstMarkLeft() + index * 34f + 16f;
    }

    /** The badge (side 6, then 8 + number + 6 + icon 17 + 10) and 6dp of air. */
    private float firstMarkLeft() {
        android.graphics.Paint text = new android.graphics.Paint();
        text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD));
        text.setTextSize(15f);
        return 6f + 8f + 17f + 10f + 6f + text.measureText("1") + 6f;
    }

    @Test public void aTapOnAMarkGoesToIt() {
        tap(markX(2), 26f);
        assertEquals(Arrays.asList("mark 2"), heard);
    }

    @Test public void aSlideAlongTheMarksGoesToEachOne() {
        long t = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, markX(0), 26f, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, markX(1), 26f, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, markX(2), 26f, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, markX(2), 26f, 0));
        assertEquals(Arrays.asList("mark 1", "mark 2"), heard);
    }

    @Test public void theBottomRowsToolsAreTheTerminalsThenNavigationAndSearch() {
        // Tools are 40dp cells from 6dp, 6dp apart; search is the last cell at the right.
        tap(6f + 20f, 70f);
        tap(6f + 46f + 20f, 70f);
        tap(388f - 6f - 20f, 70f);
        tap(388f - 6f - 46f - 20f, 70f);
        assertEquals(Arrays.asList("tool KEYBOARD", "tool SPLIT", "tool SEARCH", "tool NAVIGATION"), heard);
    }

    @Test public void theBarKeepsEveryTouchThatLandsOnIt() {
        long t = SystemClock.uptimeMillis();
        assertTrue(view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 380f, 5f, 0)));
    }

    @Test public void theMarksToolsAndPinnedAppsAreNodesTalkBackCanTap() {
        assertEquals("Terminal, window 2 of 3", view.labelOf(1).toString());
        assertEquals(1, view.virtualViewAt(markX(1), 26f));
        assertEquals("Keyboard", view.labelOf(ControlBarView.TOOL_BASE).toString());
        assertEquals("Search", view.labelOf(ControlBarView.TOOL_BASE + 5).toString());
        assertEquals(ControlBarView.TOOL_BASE + 5, view.virtualViewAt(388f - 6f - 20f, 70f));
        assertTrue(view.clickVirtualView(2));
        assertTrue(view.clickVirtualView(ControlBarView.TOOL_BASE + 4));
        assertEquals(Arrays.asList("mark 2", "tool NAVIGATION"), heard);
        assertEquals(null, view.labelOf(ControlBarView.TOOL_BASE + 6));
    }

    @Test public void homesDotsAndPinnedAppsAreSpokenByName() {
        view.setPinnedApps(Arrays.asList(new ControlBarView.PinnedApp(null, "Camera"),
            new ControlBarView.PinnedApp(null, "Phone")));
        view.setRows(ControlBarRows.of(ControlBarFront.home(3, 0)));
        assertEquals("Home page 2 of 3", view.labelOf(1).toString());
        assertEquals("Phone", view.labelOf(ControlBarView.APP_BASE + 1).toString());
        assertEquals(ControlBarView.APP_BASE, view.virtualViewAt(6f + 20f, 70f));
        assertTrue(view.clickVirtualView(ControlBarView.APP_BASE + 1));
        assertEquals(Arrays.asList("app 1"), heard);
    }

    private void tap(float x, float y) {
        long t = SystemClock.uptimeMillis();
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0));
    }
}
