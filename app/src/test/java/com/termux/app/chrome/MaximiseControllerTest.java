package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockStyle;
import com.termux.app.page.PageKind;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.ResolvedArrangement;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.pageswitch.PageBarModel;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class MaximiseControllerTest {

    private final List<String> calls = new ArrayList<>();
    private final List<Runnable> posted = new ArrayList<>();
    private Runnable delayed;
    private long delayedMs;
    private String front = "";
    private boolean keyboard;

    private final MaximiseController controller = new MaximiseController(new MaximiseController.Host() {
        @Override public void applyMaximise() { calls.add("apply " + (isMax() ? "max" : "rest")); }
        @Override public void navigateTo(@NonNull PageBarModel.Target t) {
            calls.add("go " + MaximiseController.keyOf(t));
            front = MaximiseController.keyOf(t);
        }
        @NonNull @Override public String frontKey() { return front; }
        @Override public boolean keyboardShown() { return keyboard; }
        @Override public void post(@NonNull Runnable r) { posted.add(r); }
        @Override public void postDelayed(@NonNull Runnable r, long ms) { delayed = r; delayedMs = ms; }
        @Override public void removeCallbacks(@NonNull Runnable r) { if (delayed == r) delayed = null; }
    });

    private boolean isMax() {
        return controller.isMaximised();
    }

    /** The Terminal page (2) with two windows; Home 1 in front. */
    private static PageBarModel.Target window(int w) {
        PageBarModel.Input in = new PageBarModel.Input();
        in.pages = Arrays.asList(new PageBarModel.Page(1, PageKind.HOME, null),
            new PageBarModel.Page(2, PageKind.TERMINAL, null));
        in.windowNames = Arrays.asList(null, null);
        in.currentPageId = 1;
        return PageBarModel.build(in).rowWindow(w).target;
    }

    private void runPosted() {
        List<Runnable> now = new ArrayList<>(posted);
        posted.clear();
        for (Runnable r : now) r.run();
    }

    @Test public void maximiseGoesToTheWindowAndAgainRestores() {
        controller.toggle(window(1));
        assertTrue(controller.isMaximised());
        assertEquals(Arrays.asList("go 2:1", "apply max"), calls);
        assertTrue("the setting off: Maximise hides the bars", controller.hidesSystemBars(false));
        controller.toggle(window(1));
        assertFalse(controller.isMaximised());
        assertEquals("apply rest", calls.get(calls.size() - 1));
        assertFalse(controller.hidesSystemBars(false));
        assertTrue("the setting owns them", controller.hidesSystemBars(true));
    }

    @Test public void aPageChangeAwayEndsIt() {
        controller.toggle(window(0));
        front = "2:0";
        controller.checkLater();
        runPosted();
        assertTrue("still the maximised window", controller.isMaximised());
        front = "1:-1";
        controller.checkLater();
        runPosted();
        assertFalse("Home is in front", controller.isMaximised());
        controller.checkLater();
        assertTrue("nothing to check at rest", posted.isEmpty());
    }

    @Test public void aClosingWindowEndsIt() {
        controller.toggle(window(0));
        controller.windowClosed();
        assertFalse(controller.isMaximised());
    }

    @Test public void theBarsShownBringTheRowBackForRevealMs() {
        controller.noteBars(true);
        controller.toggle(window(0));
        controller.noteBars(false);
        assertFalse(controller.revealed());
        controller.noteBars(true);
        assertTrue("the swipe from the top", controller.revealed());
        assertEquals(MaximisePolicy.REVEAL_MS, delayedMs);
        runPosted();
        assertEquals("apply max", calls.get(calls.size() - 1));
        delayed.run();
        assertFalse("the row goes again", controller.revealed());
        assertTrue(controller.isMaximised());
    }

    @Test public void theBarsGoingEndTheRevealAtOnce() {
        controller.toggle(window(0));
        controller.noteBars(false);
        controller.noteBars(true);
        controller.noteBars(false);
        runPosted();
        assertFalse(controller.revealed());
    }

    @Test public void barsAtRestRevealNothing() {
        controller.noteBars(false);
        controller.noteBars(true);
        assertFalse(controller.revealed());
    }

    // ---- The keyboard: one arrangement for what shows and for the room ----

    private static ResolvedArrangement terminal() {
        PlaceLayout layout = new PlaceLayout(PlaceLayout.Edge.TOP, PlaceLayout.RowPlacement.BOTTOM,
            false, PlaceLayout.Edge.BOTTOM, PlaceLayout.RowPlacement.BOTTOM,
            PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);
        return ResolvedArrangement.of(layout, LayoutStyle.DOCKED, DockStyle.GLASS_TRAY, false,
            false, PlaceOrientation.PORTRAIT, LayoutVariant.NORMAL, PageRef.TERMINAL);
    }

    @Test public void theKeyboardGoingUpStandsTheExtraKeysAndAppliesOnce() {
        ResolvedArrangement a = terminal();
        controller.toggle(window(0));
        ResolvedArrangement down = controller.arrangement(a);
        assertTrue(down.layout().slot(Element.EXTRA_KEYS).hidden);
        assertTrue(down.layout().slot(Element.APPS).hidden);
        assertSame("read again, built once", down, controller.arrangement(a));
        assertTrue(posted.isEmpty());
        keyboard = true;
        ResolvedArrangement up = controller.arrangement(a);
        assertFalse("the extra keys stand over the keyboard", up.layout().slot(Element.EXTRA_KEYS).hidden);
        assertTrue(up.layout().slot(Element.APPS).hidden);
        assertEquals("the layout is applied again for it", 1, posted.size());
        runPosted();
        assertEquals("apply max", calls.get(calls.size() - 1));
        assertSame(up, controller.arrangement(a));
        assertTrue("one apply per change", posted.isEmpty());
    }

    @Test public void eitherKeyboardsRestIsReadFromOneAnswerWhicheverWayItIsNow() {
        ResolvedArrangement a = terminal();
        assertSame("not maximised: the stored layout", a.layout(), controller.layoutAt(a, true));
        controller.toggle(window(0));
        PlaceLayout up = controller.layoutAt(a, true);
        PlaceLayout down = controller.layoutAt(a, false);
        assertFalse(up.slot(Element.EXTRA_KEYS).hidden);
        assertTrue(down.slot(Element.EXTRA_KEYS).hidden);
        assertSame("kept", up, controller.layoutAt(a, true));
        assertSame("what shows with the keyboard down", down, controller.arrangement(a).layout());
        keyboard = true;
        assertSame("what shows with the keyboard up", up, controller.arrangement(a).layout());
        controller.toggle(window(0));
        assertSame("restored", a, controller.arrangement(a));
    }

    // ---- The screen edges while maximised ----

    private boolean touch(int action, float y) {
        return controller.onWindowTouch(action, y, 2412);
    }

    @Test public void aFingerOnTheTopEdgeBringsTheRowBackAndReachesNoView() {
        controller.noteGestureBands(0, 0, 63);
        assertFalse("not maximised: the views'", touch(MotionEvent.ACTION_DOWN, 10f));
        controller.toggle(window(0));
        assertTrue(touch(MotionEvent.ACTION_DOWN, 10f));
        assertTrue(controller.revealed());
        assertEquals(MaximisePolicy.REVEAL_MS, delayedMs);
        assertTrue("the stream is the edge's", touch(MotionEvent.ACTION_MOVE, 300f));
        assertTrue(touch(MotionEvent.ACTION_UP, 400f));
        assertFalse("a stream in the middle is the views'", touch(MotionEvent.ACTION_DOWN, 900f));
        assertFalse(touch(MotionEvent.ACTION_UP, 900f));
        assertFalse("the row is back: the top edge is the row's", touch(MotionEvent.ACTION_DOWN, 10f));
        runPosted();
        assertEquals("apply max", calls.get(calls.size() - 1));
        delayed.run();
        assertFalse(controller.revealed());
    }

    @Test public void theHomeGestureFromTheBottomEdgeReachesNoView() {
        controller.noteGestureBands(126, 48, 63);
        controller.toggle(window(0));
        assertFalse("above the band", touch(MotionEvent.ACTION_DOWN, 2412 - 60));
        assertTrue(touch(MotionEvent.ACTION_DOWN, 2412 - 40));
        assertFalse("not the top: the row stays away", controller.revealed());
        assertTrue(touch(MotionEvent.ACTION_MOVE, 2000f));
        assertTrue(touch(MotionEvent.ACTION_CANCEL, 2000f));
        keyboard = true;
        assertFalse("the keyboard's own bottom row", touch(MotionEvent.ACTION_DOWN, 2412 - 40));
        assertTrue("the top band from the insets", touch(MotionEvent.ACTION_DOWN, 100f));
    }

    @Test public void barsStillHiddenOnTheNextInsetsKeepTheReveal() {
        controller.toggle(window(0));
        controller.noteBars(false);
        controller.noteGestureBands(0, 0, 63);
        touch(MotionEvent.ACTION_DOWN, 5f);
        assertTrue(controller.revealed());
        // Android's transient bars move no insets: every later pass still says hidden.
        controller.noteBars(false);
        runPosted();
        assertTrue("held for REVEAL_MS", controller.revealed());
    }
}
