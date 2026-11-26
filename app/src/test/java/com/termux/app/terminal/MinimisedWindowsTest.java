package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.overview.OverviewModel;
import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinimisedWindowsTest {

    @Test public void aMinimisedWindowComesBackAtItsOldPlace() {
        MinimisedWindows<String> min = new MinimisedWindows<>();
        min.add("vim", 2);
        min.add("htop", 0);
        assertEquals(2, min.size());
        MinimisedWindows.Item<String> item = min.remove(0);
        assertSame("vim", item.window);
        assertEquals(2, MinimisedWindows.restoreIndex(item.index, 3));
        assertEquals("held to the row's end when it shrank", 1,
            MinimisedWindows.restoreIndex(item.index, 1));
        assertEquals(0, MinimisedWindows.restoreIndex(-4, 3));
        assertNull(min.remove(5));
        assertTrue(min.forget("htop"));
        assertFalse(min.contains("htop"));
    }

    private static PageBarModel wallWithMinimised() {
        PageBarModel.Input in = new PageBarModel.Input();
        in.pages = Arrays.asList(new PageBarModel.Page(1, PageKind.HOME, null),
            new PageBarModel.Page(2, PageKind.TERMINAL, null));
        in.windowNames = Arrays.asList(null, null);
        in.currentPageId = 1;
        in.minimisedNames = Arrays.asList("build", null);
        in.minimisedLinux = new boolean[] {false, true};
        in.minimisedAppIds = Arrays.asList(null, "org.gimp");
        return PageBarModel.build(in);
    }

    @Test public void minimisedWindowsAreNotInTheRowButWaitInOverview() {
        PageBarModel wall = wallWithMinimised();
        assertEquals("Home and the two row windows only", 3, wall.entries().size());
        assertEquals(2, wall.minimised().size());
        PageBarModel.Entry terminal = wall.minimised().get(0);
        assertEquals(PageKind.TERMINAL, terminal.kind);
        assertEquals("build", terminal.label);
        assertEquals(PageBarModel.Action.RESTORE, terminal.target.action);
        assertEquals(0, terminal.target.index);
        PageBarModel.Entry linux = wall.minimised().get(1);
        assertEquals(PageKind.LINUX, linux.kind);
        assertEquals("org.gimp", linux.iconKey);
        assertEquals(1, linux.target.index);

        OverviewModel overview = OverviewModel.of(wall);
        assertEquals("a card per stop, then a card per minimised window", 5,
            overview.cards().size());
        OverviewModel.Card card = overview.cards().get(3);
        assertEquals("build", card.label);
        assertFalse(card.origin);
    }

    @Test public void aTapOnAMinimisedCardBringsItBack() {
        List<String> calls = new ArrayList<>();
        PageSwitchTargets.Navigator nav = new PageSwitchTargets.Navigator() {
            @Override public void goToPage(int pageId) { calls.add("page " + pageId); }
            @Override public void showTerminalWindow(int index) { calls.add("window " + index); }
            @Override public void goToTerminal() { calls.add("terminal"); }
            @Override public void openOverview() { calls.add("overview"); }
            @Override public void restoreMinimised(int index) { calls.add("restore " + index); }
        };
        PageSwitchTargets.carryOut(wallWithMinimised().minimised().get(1).target, nav);
        assertEquals(Arrays.asList("restore 1"), calls);
    }
}
