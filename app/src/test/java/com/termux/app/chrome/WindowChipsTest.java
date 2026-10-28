package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.WindowChips.Kind;
import com.termux.app.chrome.WindowChips.Meaning;
import com.termux.app.chrome.WindowChips.Surface;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The window chips: the Workspace's terminal row in row order, then the Home floats (ADR 0008;
 * since ADR 0015 A3b the display stands on Display pages of its own and is never a chip); what
 * each surface shows of them, which one is selected, and what a tap or a close on a chip means,
 * from every surface's own index.
 */
public class WindowChipsTest {

    private static final List<String> AB = Arrays.asList("a", "b");

    /** The row a, b with b in front, seen from the Workspace, and two floats f (minimised), g. */
    private static WindowChips<String> workspace() {
        return new WindowChips<>(false, true, AB, 1)
            .addFloat("f", true, false)
            .addFloat("g", false, false);
    }

    /** The same seen from Home, g focused. */
    private static WindowChips<String> home() {
        return new WindowChips<>(true, true, AB, 1)
            .addFloat("f", true, false)
            .addFloat("g", false, true);
    }

    // ---------------------------------------------------------------- order

    @Test
    public void theRowsWindowsComeInRowOrder_thenTheFloats() {
        List<WindowChips.Chip<String>> chips = home().chips();
        assertEquals(4, chips.size());
        assertEquals(Kind.WINDOW, chips.get(0).kind);
        assertEquals("a", chips.get(0).window);
        assertEquals(0, chips.get(0).row);
        assertEquals(0, chips.get(0).index);
        assertEquals(Kind.WINDOW, chips.get(1).kind);
        assertEquals("b", chips.get(1).window);
        assertEquals(1, chips.get(1).row);
        assertEquals(1, chips.get(1).index);
        assertEquals(Kind.HOME_FLOAT, chips.get(2).kind);
        assertEquals("f", chips.get(2).window);
        assertEquals(-1, chips.get(2).row);
        assertEquals(0, chips.get(2).index);
        assertTrue(chips.get(2).minimised);
        assertEquals("g", chips.get(3).window);
        assertEquals(1, chips.get(3).index);
        assertFalse(chips.get(3).minimised);
        assertEquals(2, home().rowCount());
    }

    @Test
    public void noWindows_isNoRowChip() {
        WindowChips<String> alone =
            new WindowChips<String>(false, true, Collections.<String>emptyList(), 0);
        assertTrue(alone.chips().isEmpty());
        assertEquals(0, alone.rowCount());
    }

    @Test
    public void noRow_leavesTheFloatsAlone_onHome() {
        WindowChips<String> chips = new WindowChips<>(true, false, AB, 0)
            .addFloat("f", false, false);
        assertEquals(0, chips.rowCount());
        List<WindowChips.Chip<String>> strip = chips.chips();
        assertEquals(1, strip.size());
        assertEquals("f", strip.get(0).window);
    }

    // ---------------------------------------------------------------- what each surface shows

    @Test
    public void theBar_showsEveryChip_onTheWorkspaceAndOnHome() {
        assertEquals(4, workspace().chips().size());
        assertEquals(4, home().chips().size());
    }

    /**
     * A minimised Home float is a window chip in the bar on both halves (#91), marked minimised,
     * where minimise's flight lands: no row of its own above the dock.
     */
    @Test
    public void aMinimisedFloat_isABarChipOnBothHalves() {
        for (WindowChips<String> chips : Arrays.asList(workspace(), home())) {
            assertEquals(2, chips.barChipOf("f"));
            WindowChips.Chip<String> f = chips.chips().get(chips.barChipOf("f"));
            assertEquals(Kind.HOME_FLOAT, f.kind);
            assertTrue(f.minimised);
            assertEquals(3, chips.barChipOf("g"));
            assertFalse(chips.chips().get(3).minimised);
        }
        assertEquals("a float on no page is still a chip", 0,
            new WindowChips<String>(true, false, AB, 0).addFloat("h", true, false)
                .barChipOf("h"));
        assertEquals("a row window is not a float's chip", -1, workspace().barChipOf("a"));
        assertEquals(-1, workspace().barChipOf("nobody"));
    }

    /** One bar across the wall (#90): the same chips in the same order on both halves. */
    @Test
    public void sameBarChipsOnHomeAndTheWorkspace() {
        List<WindowChips.Chip<String>> onHome = home().chips();
        List<WindowChips.Chip<String>> onWorkspace = workspace().chips();
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
        List<WindowChips.Chip<String>> bar = new WindowChips<String>(true, false, AB, 0)
            .addFloat("f", false, false).chips();
        assertEquals(1, bar.size());
        assertEquals("f", bar.get(0).window);
    }

    // ---------------------------------------------------------------- selection

    @Test
    public void theBarSelects_theRowsWindowInFront_heldToTheRow() {
        assertEquals("b's place", 1, workspace().selected(Surface.BAR));
        assertEquals("a's place", 0,
            new WindowChips<String>(false, true, AB, 0).selected(Surface.BAR));
        assertEquals(1, new WindowChips<String>(false, true, AB, 7).selected(Surface.BAR));
        assertEquals(0, new WindowChips<String>(false, true, AB, -2).selected(Surface.BAR));
    }

    @Test
    public void theBarSelects_theRowsWindowInFront_onHomeToo_andNothingWithoutARow() {
        assertEquals("b's place", 1, home().selected(Surface.BAR));
        assertEquals(-1, new WindowChips<String>(false, false, AB, 0).selected(Surface.BAR));
        assertEquals(-1, new WindowChips<String>(true, false, AB, 0)
            .addFloat("f", false, true).selected(Surface.BAR));
    }

    @Test
    public void theBar_neverSelectsAFloat_evenAFocusedOne() {
        WindowChips<String> chips = new WindowChips<>(false, true, AB, 0)
            .addFloat("g", false, true);
        assertEquals(0, chips.selected(Surface.BAR));
    }

    @Test
    public void theStripsSelect_theFocusedFloat_elseTheRowsWindowInFront() {
        assertEquals("g", 3, home().selected(Surface.WALL_STRIP));
        WindowChips<String> noFocus = new WindowChips<>(true, true, AB, 1)
            .addFloat("f", true, false);
        assertEquals(1, noFocus.selected(Surface.WALL_STRIP));
        WindowChips<String> focusedButMinimised = new WindowChips<>(true, true, AB, 0)
            .addFloat("f", true, true);
        assertEquals(0, focusedButMinimised.selected(Surface.WALL_STRIP));
        assertEquals("no row, no float in focus", -1,
            new WindowChips<String>(true, false, AB, 0).addFloat("f", false, false)
                .selected(Surface.WALL_STRIP));
    }

    // ---------------------------------------------------------------- what a tap means

    @Test
    public void aTapOnTheBar_bringsTheRowsWindowInFront_inPlace() {
        for (int chip = 0; chip < 2; chip++) {
            Meaning<String> meaning = workspace().tap(Surface.BAR, chip);
            assertEquals(Meaning.Action.SHOW_WINDOW, meaning.action);
            assertEquals(chip, meaning.row);
            assertFalse("the Workspace is where the bar is", meaning.travel);
        }
    }

    @Test
    public void aTapOnAFloatsChip_restoresThatFloat_fromEverySurface() {
        Meaning<String> bar = workspace().tap(Surface.BAR, 3);
        assertEquals(Meaning.Action.RESTORE_FLOAT, bar.action);
        assertEquals("g", bar.window);
        Meaning<String> strip = home().tap(Surface.WALL_STRIP, 2);
        assertEquals(Meaning.Action.RESTORE_FLOAT, strip.action);
        assertEquals("f", strip.window);
        assertEquals("g", home().tap(Surface.WALL_STRIP, 3).window);
    }

    /** A float's chip tapped in the Workspace (#91): the wall travels Home and the float comes back. */
    @Test
    public void aFloatsChipTappedInTheWorkspace_meansTravelHomeAndRestore() {
        for (int chip = 2; chip < 4; chip++) {
            Meaning<String> meaning = workspace().tap(Surface.BAR, chip);
            assertEquals(Meaning.Action.RESTORE_FLOAT, meaning.action);
            assertEquals(chip == 2 ? "f" : "g", meaning.window);
            assertTrue("Home is not where the bar is", meaning.travel);
        }
    }

    /** The strips stand on Home: a float's tile restores it where it is. */
    @Test
    public void aFloatsTileOnAStrip_restoresInPlace() {
        assertFalse(home().tap(Surface.WALL_STRIP, 2).travel);
        assertFalse(home().tap(Surface.WALL_STRIP, 3).travel);
    }

    @Test
    public void aTapOnAStripsWindow_slidesTheWorkspaceInOnIt() {
        for (int chip = 0; chip < 2; chip++) {
            Meaning<String> tile = home().tap(Surface.WALL_STRIP, chip);
            assertEquals(Meaning.Action.SHOW_WINDOW, tile.action);
            assertEquals(chip, tile.row);
            assertTrue(tile.travel);
        }
    }

    /** A window chip tapped on Home means slide to it: the Workspace travels in on it. */
    @Test
    public void aWindowChipTappedOnHome_meansSlideToItWithTravel() {
        for (int chip = 0; chip < 2; chip++) {
            Meaning<String> meaning = home().tap(Surface.BAR, chip);
            assertEquals(Meaning.Action.SHOW_WINDOW, meaning.action);
            assertEquals(chip, meaning.row);
            assertTrue(meaning.travel);
        }
    }

    @Test
    public void aFloatsChipTappedOnHome_restoresThatFloat() {
        Meaning<String> meaning = home().tap(Surface.BAR, 2);
        assertEquals(Meaning.Action.RESTORE_FLOAT, meaning.action);
        assertEquals("f", meaning.window);
        assertFalse(meaning.travel);
    }

    @Test
    public void chipsPastTheEndAndNegative_meanNothing() {
        for (Surface surface : Surface.values()) {
            assertEquals(Meaning.Action.NONE, workspace().tap(surface, -1).action);
            assertEquals(Meaning.Action.NONE, workspace().tap(surface, 4).action);
            assertEquals(Meaning.Action.NONE, workspace().close(surface, -1).action);
            assertEquals(Meaning.Action.NONE, workspace().close(surface, 4).action);
        }
    }

    // ---------------------------------------------------------------- what a close means

    @Test
    public void aCloseOnARowsChip_closesThatWindow() {
        for (int chip = 0; chip < 2; chip++) {
            Meaning<String> meaning = workspace().close(Surface.BAR, chip);
            assertEquals(Meaning.Action.CLOSE_WINDOW, meaning.action);
            assertEquals(chip, meaning.row);
        }
        Meaning<String> tile = home().close(Surface.WALL_STRIP, 1);
        assertEquals(Meaning.Action.CLOSE_WINDOW, tile.action);
        assertEquals(1, tile.row);
    }

    @Test
    public void aCloseOnAFloatsChip_closesThatFloat() {
        Meaning<String> meaning = workspace().close(Surface.BAR, 2);
        assertEquals(Meaning.Action.CLOSE_FLOAT, meaning.action);
        assertEquals("f", meaning.window);
    }

    /** The chip's close means on Home what it means in the Workspace. */
    @Test
    public void aCloseOnTheBarOnHome_meansWhatItMeansInTheWorkspace() {
        for (int chip = 0; chip < 4; chip++) {
            Meaning<String> onHome = home().close(Surface.BAR, chip);
            Meaning<String> onWorkspace = workspace().close(Surface.BAR, chip);
            assertEquals("chip " + chip, onWorkspace.action, onHome.action);
            assertEquals("chip " + chip, onWorkspace.row, onHome.row);
            assertEquals("chip " + chip, onWorkspace.window, onHome.window);
            assertFalse(onHome.travel);
        }
        assertEquals(Meaning.Action.CLOSE_WINDOW, home().close(Surface.BAR, 0).action);
        assertEquals(Meaning.Action.CLOSE_FLOAT, home().close(Surface.BAR, 3).action);
    }
}
