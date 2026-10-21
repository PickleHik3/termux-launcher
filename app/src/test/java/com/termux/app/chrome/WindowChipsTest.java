package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
        List<WindowChips.Chip<String>> chips = home().chips(Surface.RUN_STRIP);
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
        List<WindowChips.Chip<String>> strip = chips.chips(Surface.RUN_STRIP);
        assertEquals(1, strip.size());
        assertEquals("f", strip.get(0).window);
    }

    // ---------------------------------------------------------------- what each surface shows

    @Test
    public void theBar_showsEveryChipOnTheWorkspace_andNothingOnHome() {
        assertEquals(5, workspace().chips(Surface.BAR).size());
        assertTrue(home().chips(Surface.BAR).isEmpty());
        assertTrue("no row, no bar",
            new WindowChips<String>(false, false, AB, -1, 0).addFloat("f", false, true, false)
                .chips(Surface.BAR).isEmpty());
    }

    @Test
    public void theStripsShowEveryChip_wallStripTheSameAsTheRunStrip() {
        WindowChips<String> chips = home();
        assertEquals(chips.chips(Surface.RUN_STRIP), chips.chips(Surface.WALL_STRIP));
        assertEquals(5, chips.chips(Surface.WALL_STRIP).size());
    }

    @Test
    public void theFloatChips_areTheMinimisedFloatsOnAPage_inOrder() {
        WindowChips<String> chips = new WindowChips<>(true, true, AB, -1, 0)
            .addFloat("f", true, true, false)
            .addFloat("g", false, true, false)
            .addFloat("h", true, false, false)
            .addFloat("k", true, true, false);
        List<WindowChips.Chip<String>> floats = chips.chips(Surface.FLOAT_CHIPS);
        assertEquals(2, floats.size());
        assertEquals("f", floats.get(0).window);
        assertEquals("k", floats.get(1).window);
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
    public void theBarSelectsNothing_onHome_orWithoutARow() {
        assertEquals(-1, home().selected(Surface.BAR));
        assertEquals(-1, new WindowChips<String>(false, false, AB, -1, 0).selected(Surface.BAR));
    }

    @Test
    public void theBar_neverSelectsAFloat_evenAFocusedOne() {
        WindowChips<String> chips = new WindowChips<>(false, true, AB, -1, 0)
            .addFloat("g", false, true, true);
        assertEquals(0, chips.selected(Surface.BAR));
    }

    @Test
    public void theStripsSelect_theFocusedFloat_elseTheRowsWindowInFront() {
        assertEquals("g", 4, home().selected(Surface.RUN_STRIP));
        assertEquals(4, home().selected(Surface.WALL_STRIP));
        WindowChips<String> noFocus = new WindowChips<>(true, true, AB, 1, 2)
            .addFloat("f", true, true, false);
        assertEquals(2, noFocus.selected(Surface.RUN_STRIP));
        WindowChips<String> focusedButMinimised = new WindowChips<>(true, true, AB, 1, 0)
            .addFloat("f", true, true, true);
        assertEquals(0, focusedButMinimised.selected(Surface.RUN_STRIP));
        assertEquals("no row, no float in focus", -1,
            new WindowChips<String>(true, false, AB, -1, 0).addFloat("f", false, true, false)
                .selected(Surface.RUN_STRIP));
    }

    @Test
    public void theFloatChipsSelectNothing() {
        assertEquals(-1, home().selected(Surface.FLOAT_CHIPS));
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
        Meaning<String> strip = home().tap(Surface.RUN_STRIP, 3);
        assertEquals(Meaning.Action.RESTORE_FLOAT, strip.action);
        assertEquals("f", strip.window);
        assertEquals("g", home().tap(Surface.WALL_STRIP, 4).window);
        Meaning<String> chip = home().tap(Surface.FLOAT_CHIPS, 0);
        assertEquals(Meaning.Action.RESTORE_FLOAT, chip.action);
        assertEquals("the minimised one", "f", chip.window);
    }

    @Test
    public void aTapOnAStripsWindow_slidesTheWorkspaceInOnIt() {
        Meaning<String> display = home().tap(Surface.RUN_STRIP, 1);
        assertEquals(Meaning.Action.SHOW_WINDOW, display.action);
        assertEquals(1, display.row);
        assertTrue(display.travel);
        Meaning<String> tile = home().tap(Surface.WALL_STRIP, 2);
        assertEquals(Meaning.Action.SHOW_WINDOW, tile.action);
        assertEquals(2, tile.row);
        assertTrue(tile.travel);
    }

    @Test
    public void theFloatChipsIndex_countsOnlyTheMinimisedFloatsOnAPage() {
        WindowChips<String> chips = new WindowChips<>(true, true, AB, -1, 0)
            .addFloat("f", false, true, false)
            .addFloat("g", true, false, false)
            .addFloat("h", true, true, false);
        assertEquals("h", chips.tap(Surface.FLOAT_CHIPS, 0).window);
        assertEquals(Meaning.Action.NONE, chips.tap(Surface.FLOAT_CHIPS, 1).action);
    }

    @Test
    public void aTapOnTheBarOnHome_meansNothing() {
        assertEquals(Meaning.Action.NONE, home().tap(Surface.BAR, 0).action);
        assertEquals(Meaning.Action.NONE, home().tap(Surface.BAR, 3).action);
    }

    @Test
    public void chipsPastTheEndAndNegative_meanNothing() {
        for (Surface surface : Surface.values()) {
            assertEquals(Meaning.Action.NONE, workspace().tap(surface, -1).action);
            assertEquals(Meaning.Action.NONE, workspace().tap(surface, 5).action);
            assertEquals(Meaning.Action.NONE, workspace().close(surface, -1).action);
            assertEquals(Meaning.Action.NONE, workspace().close(surface, 5).action);
        }
        assertSame(Meaning.Action.NONE, home().tap(Surface.FLOAT_CHIPS, 1).action);
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

    @Test
    public void aCloseOnTheBarOnHome_meansNothing() {
        assertEquals(Meaning.Action.NONE, home().close(Surface.BAR, 0).action);
        assertEquals(Meaning.Action.NONE, home().close(Surface.BAR, 4).action);
    }
}
