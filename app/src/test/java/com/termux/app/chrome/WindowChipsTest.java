package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.WindowChips.Kind;
import com.termux.app.chrome.WindowChips.Meaning;
import com.termux.app.chrome.WindowChips.Surface;
import com.termux.app.wall.DisplaySlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The window chips: the Workspace's row in row order, the display window's chip at its place
 * among them like any window's (ADR 0008, amended 2026-10-19), then the Home floats; what each
 * surface shows of them, which one is selected, and what a tap or a close on a chip means, from
 * every surface's own index.
 */
public class WindowChipsTest {

    private static final List<String> AB = Arrays.asList("a", "b");

    /** The row a, D, b with b in front, seen from the Workspace, and two floats f (minimised), g. */
    private static WindowChips<String> workspace() {
        return new WindowChips<>(false, true, AB, 1, 2)
            .addFloat("f", true, true, false)
            .addFloat("g", false, true, false);
    }

    /** The same seen from Home, g focused. */
    private static WindowChips<String> home() {
        return new WindowChips<>(true, true, AB, 1, 2)
            .addFloat("f", true, true, false)
            .addFloat("g", false, true, true);
    }

    // ---------------------------------------------------------------- order

    @Test
    public void theRowsWindowsComeInRowOrder_theDisplayAtItsPlace_thenTheFloats() {
        List<WindowChips.Chip<String>> chips = home().chips(Surface.WALL_STRIP);
        assertEquals(5, chips.size());
        assertEquals(Kind.WINDOW, chips.get(0).kind);
        assertEquals("a", chips.get(0).window);
        assertEquals(0, chips.get(0).row);
        assertEquals(0, chips.get(0).index);
        assertEquals(Kind.DISPLAY, chips.get(1).kind);
        assertNull(chips.get(1).window);
        assertEquals(1, chips.get(1).row);
        assertEquals(Kind.WINDOW, chips.get(2).kind);
        assertEquals("b", chips.get(2).window);
        assertEquals(2, chips.get(2).row);
        assertEquals("the terminal window's own index", 1, chips.get(2).index);
        assertEquals(Kind.HOME_FLOAT, chips.get(3).kind);
        assertEquals("f", chips.get(3).window);
        assertEquals(-1, chips.get(3).row);
        assertEquals(0, chips.get(3).index);
        assertTrue(chips.get(3).minimised);
        assertEquals("g", chips.get(4).window);
        assertEquals(1, chips.get(4).index);
        assertFalse(chips.get(4).minimised);
        assertEquals(3, home().rowCount());
        assertEquals(1, home().displayChip());
    }

    @Test
    public void theDisplayAtTheRowsEnd_orFirst_orAlone() {
        List<WindowChips.Chip<String>> end =
            new WindowChips<String>(false, true, AB, 2, 0).chips(Surface.BAR);
        assertEquals(Kind.DISPLAY, end.get(2).kind);
        assertEquals(2, end.get(2).row);
        List<WindowChips.Chip<String>> first =
            new WindowChips<String>(false, true, AB, 0, 0).chips(Surface.BAR);
        assertEquals(Kind.DISPLAY, first.get(0).kind);
        assertEquals("a", first.get(1).window);
        assertEquals(1, first.get(1).row);
        WindowChips<String> alone =
            new WindowChips<String>(false, true, Collections.<String>emptyList(), 0, 0);
        assertEquals(1, alone.chips(Surface.BAR).size());
        assertEquals(Kind.DISPLAY, alone.chips(Surface.BAR).get(0).kind);
        assertEquals(1, alone.rowCount());
    }

    @Test
    public void noDisplay_isNoDisplayChip() {
        WindowChips<String> chips = new WindowChips<>(false, true, AB, DisplaySlot.NONE, 0);
        assertEquals(2, chips.chips(Surface.BAR).size());
        assertEquals(2, chips.rowCount());
        assertEquals(-1, chips.displayChip());
    }

    @Test
    public void noRow_leavesTheFloatsAlone_onHome() {
        WindowChips<String> chips = new WindowChips<>(true, false, AB, 1, 0)
            .addFloat("f", false, true, false);
        assertEquals(0, chips.rowCount());
        assertEquals(-1, chips.displayChip());
        List<WindowChips.Chip<String>> strip = chips.chips(Surface.WALL_STRIP);
        assertEquals(1, strip.size());
        assertEquals("f", strip.get(0).window);
    }

    // ---------------------------------------------------------------- what each surface shows

    @Test
    public void theBar_showsEveryChip_onTheWorkspaceAndOnHome() {
        assertEquals(5, workspace().chips(Surface.BAR).size());
        assertEquals(5, home().chips(Surface.BAR).size());
    }

    /**
     * A minimised Home float is a window chip in the bar on both halves (#91), marked minimised,
     * where minimise's flight lands: no row of its own above the dock.
     */
    @Test
    public void aMinimisedFloat_isABarChipOnBothHalves() {
        for (WindowChips<String> chips : Arrays.asList(workspace(), home())) {
            assertEquals(3, chips.barChipOf("f"));
            WindowChips.Chip<String> f = chips.chips(Surface.BAR).get(chips.barChipOf("f"));
            assertEquals(Kind.HOME_FLOAT, f.kind);
            assertTrue(f.minimised);
            assertEquals(4, chips.barChipOf("g"));
            assertFalse(chips.chips(Surface.BAR).get(4).minimised);
        }
        assertEquals("a float on no page is still a chip", 0,
            new WindowChips<String>(true, false, AB, -1, 0).addFloat("h", true, false, false)
                .barChipOf("h"));
        assertEquals("a row window is not a float's chip", -1, workspace().barChipOf("a"));
        assertEquals(-1, workspace().barChipOf("nobody"));
    }

    /** One bar across the wall (#90): the same chips in the same order on both halves. */
    @Test
    public void sameBarChipsOnHomeAndTheWorkspace() {
        List<WindowChips.Chip<String>> onHome = home().chips(Surface.BAR);
        List<WindowChips.Chip<String>> onWorkspace = workspace().chips(Surface.BAR);
        assertEquals(onWorkspace.size(), onHome.size());
        for (int i = 0; i < onHome.size(); i++) {
            assertEquals("chip " + i, onWorkspace.get(i).kind, onHome.get(i).kind);
            assertEquals("chip " + i, onWorkspace.get(i).window, onHome.get(i).window);
            assertEquals("chip " + i, onWorkspace.get(i).row, onHome.get(i).row);
            assertEquals("chip " + i, onWorkspace.get(i).index, onHome.get(i).index);
            assertEquals("chip " + i, onWorkspace.get(i).minimised, onHome.get(i).minimised);
        }
        assertEquals("the same chip is selected, so the bar stands still on the swipe",
            workspace().selected(Surface.BAR), home().selected(Surface.BAR));
    }

    @Test
    public void noRow_theBarShowsTheFloatsAlone() {
        List<WindowChips.Chip<String>> bar = new WindowChips<String>(true, false, AB, -1, 0)
            .addFloat("f", false, true, false).chips(Surface.BAR);
        assertEquals(1, bar.size());
        assertEquals("f", bar.get(0).window);
    }

    // ---------------------------------------------------------------- selection

    @Test
    public void theBarSelects_theRowsWindowInFront_heldToTheRow() {
        assertEquals("b's place", 2, workspace().selected(Surface.BAR));
        assertEquals("the display's place", 1,
            new WindowChips<String>(false, true, AB, 1, 1).selected(Surface.BAR));
        assertEquals(2, new WindowChips<String>(false, true, AB, 1, 7).selected(Surface.BAR));
        assertEquals(0, new WindowChips<String>(false, true, AB, 1, -2).selected(Surface.BAR));
    }

    @Test
    public void theBarSelects_theRowsWindowInFront_onHomeToo_andNothingWithoutARow() {
        assertEquals("b's place", 2, home().selected(Surface.BAR));
        assertEquals(-1, new WindowChips<String>(false, false, AB, -1, 0).selected(Surface.BAR));
        assertEquals(-1, new WindowChips<String>(true, false, AB, -1, 0)
            .addFloat("f", false, true, true).selected(Surface.BAR));
    }

    @Test
    public void theBar_neverSelectsAFloat_evenAFocusedOne() {
        WindowChips<String> chips = new WindowChips<>(false, true, AB, -1, 0)
            .addFloat("g", false, true, true);
        assertEquals(0, chips.selected(Surface.BAR));
    }

    @Test
    public void theStripsSelect_theFocusedFloat_elseTheRowsWindowInFront() {
        assertEquals("g", 4, home().selected(Surface.WALL_STRIP));
        assertEquals(4, home().selected(Surface.WALL_STRIP));
        WindowChips<String> noFocus = new WindowChips<>(true, true, AB, 1, 2)
            .addFloat("f", true, true, false);
        assertEquals(2, noFocus.selected(Surface.WALL_STRIP));
        WindowChips<String> focusedButMinimised = new WindowChips<>(true, true, AB, 1, 0)
            .addFloat("f", true, true, true);
        assertEquals(0, focusedButMinimised.selected(Surface.WALL_STRIP));
        assertEquals("no row, no float in focus", -1,
            new WindowChips<String>(true, false, AB, -1, 0).addFloat("f", false, true, false)
                .selected(Surface.WALL_STRIP));
    }

    // ---------------------------------------------------------------- what a tap means

    @Test
    public void aTapOnTheBar_bringsTheRowsWindowInFront_inPlace_theDisplaysIncluded() {
        for (int chip = 0; chip < 3; chip++) {
            Meaning<String> meaning = workspace().tap(Surface.BAR, chip);
            assertEquals(Meaning.Action.SHOW_WINDOW, meaning.action);
            assertEquals(chip, meaning.row);
            assertFalse("the Workspace is where the bar is", meaning.travel);
        }
    }

    @Test
    public void aTapOnAFloatsChip_restoresThatFloat_fromEverySurface() {
        Meaning<String> bar = workspace().tap(Surface.BAR, 4);
        assertEquals(Meaning.Action.RESTORE_FLOAT, bar.action);
        assertEquals("g", bar.window);
        Meaning<String> strip = home().tap(Surface.WALL_STRIP, 3);
        assertEquals(Meaning.Action.RESTORE_FLOAT, strip.action);
        assertEquals("f", strip.window);
        assertEquals("g", home().tap(Surface.WALL_STRIP, 4).window);
    }

    /** A float's chip tapped in the Workspace (#91): the wall travels Home and the float comes back. */
    @Test
    public void aFloatsChipTappedInTheWorkspace_meansTravelHomeAndRestore() {
        for (int chip = 3; chip < 5; chip++) {
            Meaning<String> meaning = workspace().tap(Surface.BAR, chip);
            assertEquals(Meaning.Action.RESTORE_FLOAT, meaning.action);
            assertEquals(chip == 3 ? "f" : "g", meaning.window);
            assertTrue("Home is not where the bar is", meaning.travel);
        }
    }

    /** The strips stand on Home: a float's tile restores it where it is. */
    @Test
    public void aFloatsTileOnAStrip_restoresInPlace() {
        assertFalse(home().tap(Surface.WALL_STRIP, 3).travel);
        assertFalse(home().tap(Surface.WALL_STRIP, 4).travel);
    }

    @Test
    public void aTapOnAStripsWindow_slidesTheWorkspaceInOnIt() {
        Meaning<String> display = home().tap(Surface.WALL_STRIP, 1);
        assertEquals(Meaning.Action.SHOW_WINDOW, display.action);
        assertEquals(1, display.row);
        assertTrue(display.travel);
        Meaning<String> tile = home().tap(Surface.WALL_STRIP, 2);
        assertEquals(Meaning.Action.SHOW_WINDOW, tile.action);
        assertEquals(2, tile.row);
        assertTrue(tile.travel);
    }

    /** A window chip tapped on Home means slide to it: the Workspace travels in on it. */
    @Test
    public void aWindowChipTappedOnHome_meansSlideToItWithTravel() {
        for (int chip = 0; chip < 3; chip++) {
            Meaning<String> meaning = home().tap(Surface.BAR, chip);
            assertEquals(Meaning.Action.SHOW_WINDOW, meaning.action);
            assertEquals("the display's chip included", chip, meaning.row);
            assertTrue(meaning.travel);
        }
    }

    @Test
    public void aFloatsChipTappedOnHome_restoresThatFloat() {
        Meaning<String> meaning = home().tap(Surface.BAR, 3);
        assertEquals(Meaning.Action.RESTORE_FLOAT, meaning.action);
        assertEquals("f", meaning.window);
        assertFalse(meaning.travel);
    }

    @Test
    public void chipsPastTheEndAndNegative_meanNothing() {
        for (Surface surface : Surface.values()) {
            assertEquals(Meaning.Action.NONE, workspace().tap(surface, -1).action);
            assertEquals(Meaning.Action.NONE, workspace().tap(surface, 5).action);
            assertEquals(Meaning.Action.NONE, workspace().close(surface, -1).action);
            assertEquals(Meaning.Action.NONE, workspace().close(surface, 5).action);
        }
    }

    // ---------------------------------------------------------------- what a close means

    @Test
    public void aCloseOnARowsChip_closesThatWindow_theDisplaysIncluded() {
        for (int chip = 0; chip < 3; chip++) {
            Meaning<String> meaning = workspace().close(Surface.BAR, chip);
            assertEquals(Meaning.Action.CLOSE_WINDOW, meaning.action);
            assertEquals(chip, meaning.row);
        }
        Meaning<String> tile = home().close(Surface.WALL_STRIP, 2);
        assertEquals(Meaning.Action.CLOSE_WINDOW, tile.action);
        assertEquals(2, tile.row);
    }

    @Test
    public void aCloseOnAFloatsChip_closesThatFloat() {
        Meaning<String> meaning = workspace().close(Surface.BAR, 3);
        assertEquals(Meaning.Action.CLOSE_FLOAT, meaning.action);
        assertEquals("f", meaning.window);
    }

    /** The chip's close means on Home what it means in the Workspace. */
    @Test
    public void aCloseOnTheBarOnHome_meansWhatItMeansInTheWorkspace() {
        for (int chip = 0; chip < 5; chip++) {
            Meaning<String> onHome = home().close(Surface.BAR, chip);
            Meaning<String> onWorkspace = workspace().close(Surface.BAR, chip);
            assertEquals("chip " + chip, onWorkspace.action, onHome.action);
            assertEquals("chip " + chip, onWorkspace.row, onHome.row);
            assertEquals("chip " + chip, onWorkspace.window, onHome.window);
            assertFalse(onHome.travel);
        }
        assertEquals(Meaning.Action.CLOSE_WINDOW, home().close(Surface.BAR, 0).action);
        assertEquals(Meaning.Action.CLOSE_FLOAT, home().close(Surface.BAR, 4).action);
    }
}
