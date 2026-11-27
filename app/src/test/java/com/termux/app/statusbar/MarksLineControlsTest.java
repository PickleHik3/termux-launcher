package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.MarkControls;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Holding a window's mark opens its controls on the row; a lift on one acts, anywhere else nothing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MarksLineControlsTest {

    private final List<String> calls = new ArrayList<>();
    private MarksLineView view;

    private final PageSwitchTargets.Navigator nav = new PageSwitchTargets.Navigator() {
        @Override public void goToPage(int pageId) { calls.add("page " + pageId); }
        @Override public void showTerminalWindow(int index) { calls.add("window " + index); }
        @Override public void goToTerminal() { calls.add("terminal"); }
        @Override public void openOverview() { calls.add("overview"); }
    };

    @Before
    public void setUp() {
        view = new MarksLineView(RuntimeEnvironment.getApplication(), null);
        view.setNavigator(nav);
        view.setWindowControls((entry, control, x, y, id) ->
            calls.add(control + " " + entry.kind + " " + entry.target.index));
        // Home 1 (current), then the Terminal page's two windows: Overview, Home, T1, T2.
        PageBarModel.Input in = new PageBarModel.Input();
        in.pages = Arrays.asList(new PageBarModel.Page(1, PageKind.HOME, null),
            new PageBarModel.Page(2, PageKind.TERMINAL, null));
        in.windowNames = Arrays.asList(null, null);
        in.currentPageId = 1;
        in.currentWindow = 0;
        view.setPages(PageBarModel.build(in), null);
        view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 600, 40);
    }

    private void send(int action, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, action, x, y, 0);
        view.dispatchTouchEvent(e);
        e.recycle();
    }

    @Test public void aStillHoldOnAWindowMarkOpensItsControlsAndALiftOnCloseClosesIt() {
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(2), 20);
        view.fireHoldForTest();
        MarkControls.Layout controls = view.controls();
        assertNotNull("a Terminal window's mark opens controls", controls);
        assertEquals("Minimise, Maximise, Close", 3, controls.count());
        float close = controls.centreAt(controls.count() - 1);
        send(MotionEvent.ACTION_MOVE, close, 20);
        send(MotionEvent.ACTION_UP, close, 20);
        assertEquals(Arrays.asList("CLOSE TERMINAL 0"), calls);
        assertNull("the touch is over", view.controls());
    }

    @Test public void aLiftOffTheControlsDoesNothing() {
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(3), 20);
        view.fireHoldForTest();
        MarkControls.Layout controls = view.controls();
        assertNotNull(controls);
        float away = controls.atStart() ? controls.startPx() + controls.lengthPx() + 80f
            : view.rowLayout().centreAt(1);
        send(MotionEvent.ACTION_MOVE, away, 20);
        send(MotionEvent.ACTION_UP, away, 20);
        assertTrue("no control, no page: " + calls, calls.isEmpty());
    }

    @Test public void pageMarksNeverOpenControls() {
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(1), 20);
        view.fireHoldForTest();
        assertNull("Home is a page", view.controls());
        send(MotionEvent.ACTION_CANCEL, 0, 20);
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(0), 20);
        view.fireHoldForTest();
        assertNull("Overview is a page", view.controls());
    }

    @Test public void aMoveBeforeTheHoldIsTheSlideUnchanged() {
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(2), 20);
        send(MotionEvent.ACTION_MOVE, view.rowLayout().centreAt(2) + 60f, 20);
        view.fireHoldForTest();
        assertNull("the slide started: the hold is off", view.controls());
    }

    @Test public void aQuickTapStillGoesToTheWindow() {
        float x = view.rowLayout().centreAt(3);
        send(MotionEvent.ACTION_DOWN, x, 20);
        send(MotionEvent.ACTION_UP, x, 20);
        assertEquals(Arrays.asList("window 1"), calls);
    }

    /** Overview, Home, a terminal window (place 0) and a Linux window (place 1). */
    private void withALinuxWindow(boolean scales) {
        view.setWindowControls(new MarksLineView.WindowControls() {
            @Override public void onWindowControl(PageBarModel.Entry entry,
                                                  MarkControls.Control control, float x, float y,
                                                  int id) {
                calls.add(control + " " + entry.kind + " " + entry.target.index);
            }
            @Override public boolean scales(PageBarModel.Entry entry) { return scales; }
        });
        PageBarModel.Input in = new PageBarModel.Input();
        in.pages = Arrays.asList(new PageBarModel.Page(1, PageKind.HOME, null),
            new PageBarModel.Page(2, PageKind.TERMINAL, null));
        in.windowNames = Arrays.asList(null, "gimp");
        in.linuxWindows = new boolean[] {false, true};
        in.currentPageId = 1;
        in.currentWindow = 0;
        view.setPages(PageBarModel.build(in), null);
        view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 600, 40);
    }

    @Test public void aHeldLinuxWindowMarkShowsFiveControlsWithScaleBeforeClose() {
        withALinuxWindow(true);
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(3), 20);
        view.fireHoldForTest();
        MarkControls.Layout controls = view.controls();
        assertNotNull(controls);
        assertEquals("Minimise, Maximise, Move, Scale, Close", 5, controls.count());
        float scale = controls.centreAt(3);
        send(MotionEvent.ACTION_MOVE, scale, 20);
        send(MotionEvent.ACTION_UP, scale, 20);
        assertEquals(Arrays.asList("SCALE LINUX 1"), calls);
    }

    @Test public void aLinuxWindowThatTakesNoScaleHasFourControls() {
        withALinuxWindow(false);
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(3), 20);
        view.fireHoldForTest();
        MarkControls.Layout controls = view.controls();
        assertNotNull(controls);
        assertEquals("Minimise, Maximise, Move, Close", 4, controls.count());
    }

    @Test public void theWindowsAppAskingForItsMenuOpensTheControlsWithNoFingerDown() {
        withALinuxWindow(true);
        assertTrue(view.openControlsFor(PageKind.LINUX, 1));
        MarkControls.Layout controls = view.controls();
        assertNotNull(controls);
        assertEquals(5, controls.count());
        // A press on Scale and a lift on it acts.
        float close = controls.centreAt(4);
        send(MotionEvent.ACTION_DOWN, close, 20);
        send(MotionEvent.ACTION_UP, close, 20);
        assertEquals(Arrays.asList("CLOSE LINUX 1"), calls);
        assertNull(view.controls());
    }

    @Test public void aPressElsewhereWhileTheControlsWaitPutsThemAway() {
        withALinuxWindow(true);
        assertTrue(view.openControlsFor(PageKind.LINUX, 1));
        MarkControls.Layout controls = view.controls();
        assertNotNull(controls);
        // On the Home mark: not a control. The controls go and the press is an ordinary one.
        send(MotionEvent.ACTION_DOWN, view.rowLayout().centreAt(1), 20);
        assertNull(view.controls());
        send(MotionEvent.ACTION_UP, view.rowLayout().centreAt(1), 20);
        assertTrue("no control acted: " + calls, calls.stream().noneMatch(c -> c.contains("LINUX")));
    }

    @Test public void noSuchWindowOpensNothing() {
        withALinuxWindow(true);
        assertTrue(!view.openControlsFor(PageKind.LINUX, 5));
        assertNull(view.controls());
    }
}
