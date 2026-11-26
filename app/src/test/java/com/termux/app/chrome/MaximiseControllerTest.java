package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarModel;

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

    private final MaximiseController controller = new MaximiseController(new MaximiseController.Host() {
        @Override public void applyMaximise() { calls.add("apply " + (isMax() ? "max" : "rest")); }
        @Override public void navigateTo(@NonNull PageBarModel.Target t) {
            calls.add("go " + MaximiseController.keyOf(t));
            front = MaximiseController.keyOf(t);
        }
        @NonNull @Override public String frontKey() { return front; }
        @Override public boolean keyboardShown() { return false; }
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
}
