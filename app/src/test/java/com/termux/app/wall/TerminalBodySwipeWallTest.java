package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * A sideways swipe on a terminal window's text pages the wall's row exactly as the border slide
 * does — to the next window, and from the first window back to Home's last page — while a
 * vertical one, and any swipe the terminal keeps, reaches the terminal untouched.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, qualifiers = "xxhdpi")
public class TerminalBodySwipeWallTest {

    /** A row of three Home pages and some windows, whose terminal gives the swipe away or not. */
    private static final class Host implements PaneWallController.Host {
        boolean yields = true;
        int homePage;
        int windows = 3;
        int current;
        int stepped;

        @Override public boolean reducedMotion() { return false; }
        @Override public boolean isPlankTiltEnabled() { return true; }
        @Override public int innerCount(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? 3 : page == PaneWallPage.TERMINAL ? windows : 1;
        }
        @Override public int innerIndex(PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? homePage
                : page == PaneWallPage.TERMINAL ? current : 0;
        }
        @Override public void showInnerPage(PaneWallPage page, int index) {
            if (page == PaneWallPage.WIDGETS) homePage = index;
            if (page == PaneWallPage.TERMINAL) current = index;
        }
        @Override public void onInnerStep(PaneWallPage page, int steps, float fromPx) {
            stepped = steps;
            current += steps;
        }
        @Override public boolean bodySwipePagesWall(float rawX, float rawY) { return yields; }
    }

    /** Stands in for the terminal: takes every touch on the page and remembers what it got. */
    private static final class Terminal extends View {
        final List<Integer> actions = new ArrayList<>();

        Terminal(Context context) {
            super(context);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            actions.add(event.getActionMasked());
            return true;
        }

        int count(int action) {
            int n = 0;
            for (int a : actions) if (a == action) n++;
            return n;
        }
    }

    private Activity activity;
    private Host host;
    private PaneWallController row;
    private PaneWallLayout wall;
    private Terminal terminal;

    @Before public void build() {
        FreshVelocityTrackers.drainPool();
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        host = new Host();
        wall = new PaneWallLayout(activity);
        FrameLayout page = new FrameLayout(activity);
        terminal = new Terminal(activity);
        page.addView(terminal, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        wall.addView(page);
        row = new PaneWallController(wall, host);
        row.attachTerminalPage(page);
        row.attachWidgetsPage(LayoutInflater.from(activity));
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) page.getLayoutParams();
        margins.setMargins(22, 20, 22, 300);
        page.setLayoutParams(margins);
        activity.setContentView(wall);
        wall.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, 1080, 1500);
        row.refreshPages();
        idle();
        enterTheFirstWindow();
        terminal.actions.clear();
    }

    /** Home's last page, then its swipe on through the seam to the first window. */
    private void enterTheFirstWindow() {
        row.goHome(false);
        host.homePage = 2;
        row.homeSeam().begin();
        row.homeSeam().drag(-800f);
        row.homeSeam().end(0f);
        idle();
        assertEquals(PaneWallPage.TERMINAL, row.currentPage());
        assertEquals(0, host.current);
        host.homePage = 0;
    }

    /** A drag from the middle of the window's text, {@code dx} and {@code dy} per step. */
    private void dragOnTheText(float dx, float dy) {
        float x = 540f;
        float y = 600f;
        wall.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, x, y, 0L));
        for (int i = 1; i <= 20; i++) {
            wall.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE, x + i * dx, y + i * dy,
                i * 16L));
        }
    }

    private void lift(float dx, float dy) {
        wall.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 540f + 20 * dx, 600f + 20 * dy,
            336L));
        idle();
    }

    @Test public void aSidewaysSwipeOnTheTextStepsToTheNextWindow() {
        dragOnTheText(-45f, 2f);
        assertTrue("the wall follows the finger", wall.isDragging());
        assertEquals("the terminal had the press", MotionEvent.ACTION_DOWN,
            (int) terminal.actions.get(0));
        assertEquals("and was sent the cancel at the claim", 1,
            terminal.count(MotionEvent.ACTION_CANCEL));
        int seen = terminal.actions.size();
        lift(-45f, 2f);
        assertEquals("nothing reaches the terminal after the claim", seen, terminal.actions.size());
        assertEquals(PaneWallPage.TERMINAL, row.currentPage());
        assertEquals("one window on", 1, host.stepped);
        assertEquals(1, host.current);
    }

    @Test public void aSidewaysSwipeOnTheFirstWindowsTextReachesTheLastHomePage() {
        dragOnTheText(45f, 0f);
        assertTrue(wall.isDragging());
        lift(45f, 0f);
        assertEquals(PaneWallPage.WIDGETS, row.currentPage());
        assertEquals("no step inside the Workspace", 0, host.stepped);
        assertEquals("Home arrives on its last page, the one beside the seam", 2, host.homePage);
    }

    @Test public void aVerticalSwipeOnTheTextScrollsTheTerminal() {
        dragOnTheText(3f, 30f);
        assertFalse("the wall stays put", wall.isDragging());
        lift(3f, 30f);
        assertEquals(0, terminal.count(MotionEvent.ACTION_CANCEL));
        assertEquals("every move reached the terminal", 20, terminal.count(MotionEvent.ACTION_MOVE));
        assertEquals(1, terminal.count(MotionEvent.ACTION_UP));
        assertEquals(PaneWallPage.TERMINAL, row.currentPage());
        assertEquals(0, host.current);
    }

    @Test public void aDiagonalSwipeOnTheTextStaysTheTerminals() {
        dragOnTheText(25f, 20f);
        assertFalse(wall.isDragging());
        lift(25f, 20f);
        assertEquals(0, terminal.count(MotionEvent.ACTION_CANCEL));
        assertEquals(PaneWallPage.TERMINAL, row.currentPage());
    }

    @Test public void aTerminalThatKeepsTheSwipeGetsIt() {
        host.yields = false;
        dragOnTheText(-45f, 0f);
        assertFalse("a selection, the mouse or touch reports keep the drag", wall.isDragging());
        lift(-45f, 0f);
        assertEquals(0, terminal.count(MotionEvent.ACTION_CANCEL));
        assertEquals(20, terminal.count(MotionEvent.ACTION_MOVE));
        assertEquals(0, host.stepped);
    }

    private static MotionEvent touch(int action, float x, float y, long timeMs) {
        return MotionEvent.obtain(0L, timeMs, action, x, y, 0);
    }

    private static void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(3000L));
    }
}
