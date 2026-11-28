package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowKind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** What the control bar's two rows hold for the place in front (#424). */
public class ControlBarRowsTest {

    @Test
    public void homeShowsADotPerHomePageWithTheOneInFrontLit() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.home(3, 1));
        assertTrue(rows.home);
        assertEquals(3, rows.marks.size());
        for (ControlBarRows.Mark mark : rows.marks) assertNull(mark.kind);
        assertFalse(rows.marks.get(0).lit);
        assertTrue(rows.marks.get(1).lit);
        assertFalse(rows.marks.get(2).lit);
        assertEquals(1, rows.marks.get(1).target);
        assertEquals(1, rows.litIndex());
    }

    @Test
    public void homeAlwaysHasAtLeastOneDotAndClampsThePageInFront() {
        ControlBarRows none = ControlBarRows.of(ControlBarFront.home(0, 0));
        assertEquals(1, none.marks.size());
        assertTrue(none.marks.get(0).lit);
        ControlBarRows past = ControlBarRows.of(ControlBarFront.home(2, 5));
        assertEquals(1, past.litIndex());
    }

    @Test
    public void homesToolsAreThePinnedAppsThenNavigationAndSearch() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.home(1, 0));
        assertEquals(Collections.singletonList(ControlBarTool.PINNED_APPS), rows.tools);
        assertEquals(Arrays.asList(ControlBarTool.NAVIGATION, ControlBarTool.SEARCH), rows.trailing);
    }

    @Test
    public void aWorkspaceShowsItsNumberItsMainWindowsIconAndAMarkPerWindow() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.workspace(2, WindowKind.SHELL,
            windows(win(7, WindowKind.SHELL), win(9, WindowKind.LINUX_APP), win(11, WindowKind.SHELL)), 9));
        assertFalse(rows.home);
        assertEquals(2, rows.workspaceNumber);
        assertEquals(WindowKind.SHELL, rows.badgeKind);
        assertEquals(3, rows.marks.size());
        assertEquals(7, rows.marks.get(0).target);
        assertEquals(WindowKind.LINUX_APP, rows.marks.get(1).kind);
        assertTrue(rows.marks.get(1).lit);
        assertFalse(rows.marks.get(0).lit);
        assertFalse(rows.marks.get(2).lit);
        assertEquals(1, rows.litIndex());
    }

    @Test
    public void aTerminalInFrontHasKeyboardSplitNewTerminalAndTheWindowMenu() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.workspace(1, WindowKind.SHELL,
            windows(win(1, WindowKind.SHELL)), 1));
        assertEquals(Arrays.asList(ControlBarTool.KEYBOARD, ControlBarTool.SPLIT,
            ControlBarTool.NEW_TERMINAL, ControlBarTool.WINDOW_MENU), rows.tools);
        assertEquals(Arrays.asList(ControlBarTool.NAVIGATION, ControlBarTool.SEARCH), rows.trailing);
    }

    @Test
    public void aLinuxAppOrTheDisplayInFrontHasMouseModeKeyboardAndTheWindowMenu() {
        for (WindowKind kind : new WindowKind[] {WindowKind.LINUX_APP, WindowKind.DISPLAY}) {
            ControlBarRows rows = ControlBarRows.of(ControlBarFront.workspace(1, kind,
                windows(win(4, kind)), 4));
            assertEquals(Arrays.asList(ControlBarTool.MOUSE_MODE, ControlBarTool.KEYBOARD,
                ControlBarTool.WINDOW_MENU), rows.tools);
        }
    }

    @Test
    public void theAssistantInFrontHasKeyboardAndTheWindowMenu() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.workspace(1, WindowKind.ASSISTANT,
            windows(win(4, WindowKind.ASSISTANT)), 4));
        assertEquals(Arrays.asList(ControlBarTool.KEYBOARD, ControlBarTool.WINDOW_MENU), rows.tools);
    }

    @Test
    public void anEmptyWorkspaceOffersANewTerminalAndNoMarks() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.workspace(3, null,
            Collections.emptyList(), TilingStack.NO_WINDOW));
        assertEquals(3, rows.workspaceNumber);
        assertNull(rows.badgeKind);
        assertTrue(rows.marks.isEmpty());
        assertEquals(-1, rows.litIndex());
        assertEquals(Collections.singletonList(ControlBarTool.NEW_TERMINAL), rows.tools);
    }

    @Test
    public void aFocusNoWindowHoldsLightsNothingAndTakesTheFirstWindowsTools() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.workspace(1, WindowKind.LINUX_APP,
            windows(win(1, WindowKind.LINUX_APP), win(2, WindowKind.SHELL)), 99));
        assertEquals(-1, rows.litIndex());
        assertEquals(ControlBarTool.MOUSE_MODE, rows.tools.get(0));
    }

    @Test
    public void rowsCompareByWhatTheyHold() {
        ControlBarFront a = ControlBarFront.workspace(1, WindowKind.SHELL, windows(win(1, WindowKind.SHELL)), 1);
        ControlBarFront b = ControlBarFront.workspace(1, WindowKind.SHELL, windows(win(1, WindowKind.SHELL)), 1);
        assertEquals(ControlBarRows.of(a), ControlBarRows.of(b));
        assertEquals(ControlBarRows.of(a).hashCode(), ControlBarRows.of(b).hashCode());
        assertFalse(ControlBarRows.of(a).equals(ControlBarRows.of(ControlBarFront.home(1, 0))));
    }

    private static ControlBarFront.Window win(long id, WindowKind kind) {
        return new ControlBarFront.Window(id, kind);
    }

    private static List<ControlBarFront.Window> windows(ControlBarFront.Window... windows) {
        return Arrays.asList(windows);
    }

    @Test
    public void homeHoldsTheDrawerItemFirstWhenItIsOn() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.home(1, 0), true);
        assertEquals(java.util.Arrays.asList(ControlBarTool.DRAWER, ControlBarTool.PINNED_APPS), rows.tools);
        assertEquals(java.util.Collections.singletonList(ControlBarTool.PINNED_APPS),
            ControlBarRows.of(ControlBarFront.home(1, 0), false).tools);
        assertEquals(ControlBarRows.of(ControlBarFront.home(1, 0)),
            ControlBarRows.of(ControlBarFront.home(1, 0), false));
    }

    /** Round 4: in full screen the window's menu stays first, so one row keeps it. */
    @Test
    public void aFullScreenWindowsMenuStandsFirstAmongTheTools() {
        ControlBarRows rows = ControlBarRows.of(ControlBarFront.workspace(1, WindowKind.SHELL,
            windows(win(7, WindowKind.SHELL)), 7));
        ControlBarRows first = rows.withToolFirst(ControlBarTool.WINDOW_MENU);
        assertEquals(ControlBarTool.WINDOW_MENU, first.tools.get(0));
        assertEquals("no tool twice", rows.tools.size() + (rows.tools.contains(ControlBarTool.WINDOW_MENU) ? 0 : 1),
            first.tools.size());
        assertTrue(first.withToolFirst(ControlBarTool.WINDOW_MENU) == first);
    }
}
