package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.MarkControls;
import com.termux.app.wall.pageswitch.MarkControls.Control;
import com.termux.app.wall.pageswitch.PageBarModel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WindowControlRouterTest {

    private final List<String> calls = new ArrayList<>();
    private boolean linuxScales = true;
    private boolean linuxDesktop;
    private final WindowControlRouter router = new WindowControlRouter(
        new WindowControlRouter.Actions() {
            @Override public void closeRowWindow(int place) { calls.add("close " + place); }
            @Override public void closeDisplay(int pageId) { calls.add("close display " + pageId); }
            @Override public void minimiseRowWindow(int place) { calls.add("minimise " + place); }
            @Override public void toggleMaximise(@NonNull PageBarModel.Target target) {
                calls.add("maximise " + target.pageId + ":" + target.index);
            }
            @Override public void moveLinuxWindow(int place, float x, float y, int id) {
                calls.add("move " + place + " " + (int) x + "," + (int) y + " #" + id);
            }
            @Override public void scaleLinuxWindow(int place) { calls.add("scale " + place); }
            @Override public boolean linuxWindowScales(int place) { return linuxScales; }
            @Override public boolean linuxWindowIsDesktop(int place) { return linuxDesktop; }
            @Override public void fullScreenLinuxWindow(int place) { calls.add("full screen " + place); }
        });

    /** Home 1, the Terminal page (2) with a terminal window and a Linux window, a Display page (3). */
    private static List<PageBarModel.Entry> entries() {
        PageBarModel.Input in = new PageBarModel.Input();
        in.pages = Arrays.asList(new PageBarModel.Page(1, PageKind.HOME, null),
            new PageBarModel.Page(2, PageKind.TERMINAL, null),
            new PageBarModel.Page(3, PageKind.DISPLAY, null));
        in.windowNames = Arrays.asList(null, "gimp");
        in.linuxWindows = new boolean[] {false, true};
        in.displayRunning = true;
        in.currentPageId = 1;
        return PageBarModel.build(in).entries();
    }

    private static PageBarModel.Entry of(PageKind kind) {
        for (PageBarModel.Entry e : entries()) if (e.kind == kind) return e;
        throw new AssertionError(kind);
    }

    @Test public void eachControlReachesItsWindow() {
        router.onWindowControl(of(PageKind.TERMINAL), Control.CLOSE, 0, 0, -1);
        router.onWindowControl(of(PageKind.LINUX), Control.CLOSE, 0, 0, -1);
        router.onWindowControl(of(PageKind.DISPLAY), Control.CLOSE, 0, 0, -1);
        router.onWindowControl(of(PageKind.TERMINAL), Control.MINIMISE, 0, 0, -1);
        router.onWindowControl(of(PageKind.LINUX), Control.MINIMISE, 0, 0, -1);
        router.onWindowControl(of(PageKind.TERMINAL), Control.MAXIMISE, 0, 0, -1);
        router.onWindowControl(of(PageKind.DISPLAY), Control.MAXIMISE, 0, 0, -1);
        router.onWindowControl(of(PageKind.LINUX), Control.MOVE, 10, 20, 3);
        assertEquals(Arrays.asList("close 0", "close 1", "close display 3", "minimise 0",
            "minimise 1", "maximise 2:0", "maximise 3:-1", "move 1 10,20 #3"), calls);
    }

    @Test public void scaleReachesALinuxWindowAndNoOtherKind() {
        router.onWindowControl(of(PageKind.LINUX), Control.SCALE, 0, 0, -1);
        router.onWindowControl(of(PageKind.TERMINAL), Control.SCALE, 0, 0, -1);
        router.onWindowControl(of(PageKind.DISPLAY), Control.SCALE, 0, 0, -1);
        assertEquals(Arrays.asList("scale 1"), calls);
    }

    @Test public void aLinuxWindowThatTakesNoScaleOfItsOwnHidesScale() {
        assertTrue(router.scales(of(PageKind.LINUX)));
        assertEquals(5, MarkControls.controlsFor(PageKind.LINUX,
            router.scales(of(PageKind.LINUX))).length);
        linuxScales = false;
        assertFalse(router.scales(of(PageKind.LINUX)));
        assertTrue("only a Linux window is asked", router.scales(of(PageKind.TERMINAL)));
        assertEquals(4, MarkControls.controlsFor(PageKind.LINUX,
            router.scales(of(PageKind.LINUX))).length);
    }

    @Test public void moveIsForLinuxWindowsAndPagesTakeNoControl() {
        router.onWindowControl(of(PageKind.TERMINAL), Control.MOVE, 0, 0, -1);
        router.onWindowControl(of(PageKind.DISPLAY), Control.MINIMISE, 0, 0, -1);
        router.onWindowControl(of(PageKind.HOME), Control.CLOSE, 0, 0, -1);
        assertTrue(calls.toString(), calls.isEmpty());
    }

    @Test public void closeAsksOnlyWhileAPaneRunsSomething() {
        assertFalse("every pane at its prompt",
            WindowControlRouter.asksBeforeClose(new Boolean[] {true, true}));
        assertFalse("unknown reads as idle",
            WindowControlRouter.asksBeforeClose(new Boolean[] {null, true}));
        assertFalse("no resolver", WindowControlRouter.asksBeforeClose(null));
        assertTrue("one pane busy",
            WindowControlRouter.asksBeforeClose(new Boolean[] {true, false}));
    }

    @Test public void aDesktopsMaximiseTakesTheWholeScreenAndAnAppsMaximisesThePage() {
        router.onWindowControl(of(PageKind.LINUX), Control.MAXIMISE, 0, 0, -1);
        linuxDesktop = true;
        router.onWindowControl(of(PageKind.LINUX), Control.MAXIMISE, 0, 0, -1);
        router.onWindowControl(of(PageKind.TERMINAL), Control.MAXIMISE, 0, 0, -1);
        router.onWindowControl(of(PageKind.DISPLAY), Control.MAXIMISE, 0, 0, -1);
        assertEquals(Arrays.asList("maximise 2:1", "full screen 1", "maximise 2:0",
            "maximise 3:-1"), calls);
    }

    @Test public void aWaylandDesktopsControlsKeepMaximiseAndHaveNoScale() {
        linuxDesktop = true;
        linuxScales = false;
        assertEquals(Arrays.asList(Control.MINIMISE, Control.MAXIMISE, Control.MOVE, Control.CLOSE),
            Arrays.asList(MarkControls.controlsFor(PageKind.LINUX, router.scales(of(PageKind.LINUX)))));
    }
}
