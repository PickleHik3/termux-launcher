package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.WindowChips.Kind;
import com.termux.app.chrome.WindowChips.Meaning;
import com.termux.app.chrome.WindowChips.Surface;
import com.termux.app.page.PageKind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The window chips: the Terminal's terminal row in row order, then the Home floats (ADR 0008;
 * since ADR 0015 A3b the display stands on Display pages of its own and is never a chip); what
 * each surface shows of them, which one is selected, and what a tap or a close on a chip means,
 * from every surface's own index.
 */
public class WindowChipsTest {

    private static final List<String> AB = Arrays.asList("a", "b");

    /** The row a, b with b in front, seen from the Terminal, and two floats f (minimised), g. */
    private static WindowChips<String> terminal() {
        return new WindowChips<>(PageKind.TERMINAL, true, AB, 1)
            .addFloat("f", true, false)
            .addFloat("g", false, false);
    }

    /** The same seen from Home, g focused. */
    private static WindowChips<String> home() {
        return new WindowChips<>(PageKind.HOME, true, AB, 1)
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
            new WindowChips<String>(PageKind.TERMINAL, true, Collections.<String>emptyList(), 0);
        assertTrue(alone.chips().isEmpty());
        assertEquals(0, alone.rowCount());
    }

    @Test
    public void noRow_leavesTheFloatsAlone_onHome() {
        WindowChips<String> chips = new WindowChips<>(PageKind.HOME, false, AB, 0)
            .addFloat("f", false, false);
        assertEquals(0, chips.rowCount());
        List<WindowChips.Chip<String>> strip = chips.chips();
        assertEquals(1, strip.size());
        assertEquals("f", strip.get(0).window);
    }

    // ---------------------------------------------------------------- what each surface shows

    @Test
    public void theBar_showsEveryChip_onTheTerminalAndOnHome() {
        assertEquals(4, terminal().chips().size());
        assertEquals(4, home().chips().size());
    }

    /** The bar shows the Terminal's own windows only: floats are Home's chips (#295). */
    @Test
    public void theBarShowsOnlyTheRowsWindows_floatsAreHomes() {
        for (WindowChips<String> chips : Arrays.asList(terminal(), home())) {
            assertEquals(2, chips.count(Surface.BAR));
            assertEquals("the Wall strip still shows every chip", 4,
                chips.count(Surface.WALL_STRIP));
            assertEquals(Meaning.Action.NONE, chips.tap(Surface.BAR, 2).action);
            assertEquals(Meaning.Action.NONE, chips.tap(Surface.BAR, 3).action);
            assertEquals(Meaning.Action.NONE, chips.close(Surface.BAR, 2).action);
        }
        assertEquals("without a row the bar has no chip", 0,
            new WindowChips<String>(PageKind.HOME, false, AB, 0).addFloat("h", true, false)
                .count(Surface.BAR));
    }

    /** One bar across the wall (#90): the same chips in the same order on both halves. */
    @Test
    public void sameBarChipsOnHomeAndTheTerminal() {
        List<WindowChips.Chip<String>> onHome = home().chips();
        List<WindowChips.Chip<String>> onTerminal = terminal().chips();
        assertEquals(onTerminal.size(), onHome.size());
        for (int i = 0; i < onHome.size(); i++) {
            assertEquals("chip " + i, onTerminal.get(i).kind, onHome.get(i).kind);
            assertEquals("chip " + i, onTerminal.get(i).window, onHome.get(i).window);
            assertEquals("chip " + i, onTerminal.get(i).row, onHome.get(i).row);
            assertEquals("chip " + i, onTerminal.get(i).index, onHome.get(i).index);
            assertEquals("chip " + i, onTerminal.get(i).minimised, onHome.get(i).minimised);
        }
        assertEquals("the same chip is selected, so the bar stands still on the swipe",
            terminal().selected(Surface.BAR), home().selected(Surface.BAR));
    }

    @Test
    public void noRow_theBarShowsTheFloatsAlone() {
        List<WindowChips.Chip<String>> bar = new WindowChips<String>(PageKind.HOME, false, AB, 0)
            .addFloat("f", false, false).chips();
        assertEquals(1, bar.size());
        assertEquals("f", bar.get(0).window);
    }

    // ---------------------------------------------------------------- selection

    @Test
    public void theBarSelects_theRowsWindowInFront_heldToTheRow() {
        assertEquals("b's place", 1, terminal().selected(Surface.BAR));
        assertEquals("a's place", 0,
            new WindowChips<String>(PageKind.TERMINAL, true, AB, 0).selected(Surface.BAR));
        assertEquals(1, new WindowChips<String>(PageKind.TERMINAL, true, AB, 7).selected(Surface.BAR));
        assertEquals(0, new WindowChips<String>(PageKind.TERMINAL, true, AB, -2).selected(Surface.BAR));
    }

    @Test
    public void theBarSelects_theRowsWindowInFront_onHomeToo_andNothingWithoutARow() {
        assertEquals("b's place", 1, home().selected(Surface.BAR));
        assertEquals(-1, new WindowChips<String>(PageKind.TERMINAL, false, AB, 0).selected(Surface.BAR));
        assertEquals(-1, new WindowChips<String>(PageKind.HOME, false, AB, 0)
            .addFloat("f", false, true).selected(Surface.BAR));
    }

    @Test
    public void theBar_neverSelectsAFloat_evenAFocusedOne() {
        WindowChips<String> chips = new WindowChips<>(PageKind.TERMINAL, true, AB, 0)
            .addFloat("g", false, true);
        assertEquals(0, chips.selected(Surface.BAR));
    }

    @Test
    public void theStripsSelect_theFocusedFloat_elseTheRowsWindowInFront() {
        assertEquals("g", 3, home().selected(Surface.WALL_STRIP));
        WindowChips<String> noFocus = new WindowChips<>(PageKind.HOME, true, AB, 1)
            .addFloat("f", true, false);
        assertEquals(1, noFocus.selected(Surface.WALL_STRIP));
        WindowChips<String> focusedButMinimised = new WindowChips<>(PageKind.HOME, true, AB, 0)
            .addFloat("f", true, true);
        assertEquals(0, focusedButMinimised.selected(Surface.WALL_STRIP));
        assertEquals("no row, no float in focus", -1,
            new WindowChips<String>(PageKind.HOME, false, AB, 0).addFloat("f", false, false)
                .selected(Surface.WALL_STRIP));
    }

    // ---------------------------------------------------------------- what a tap means

    @Test
    public void aTapOnTheBar_bringsTheRowsWindowInFront_inPlace() {
        for (int chip = 0; chip < 2; chip++) {
            Meaning<String> meaning = terminal().tap(Surface.BAR, chip);
            assertEquals(Meaning.Action.SHOW_WINDOW, meaning.action);
            assertEquals(chip, meaning.row);
            assertFalse("the Terminal is where the bar is", meaning.travel);
        }
    }

    @Test
    public void aTapOnAFloatsTile_restoresThatFloat() {
        Meaning<String> strip = home().tap(Surface.WALL_STRIP, 2);
        assertEquals(Meaning.Action.RESTORE_FLOAT, strip.action);
        assertEquals("f", strip.window);
        assertEquals("g", home().tap(Surface.WALL_STRIP, 3).window);
    }

    /** The strips stand on Home: a float's tile restores it where it is. */
    @Test
    public void aFloatsTileOnAStrip_restoresInPlace() {
        assertFalse(home().tap(Surface.WALL_STRIP, 2).travel);
        assertFalse(home().tap(Surface.WALL_STRIP, 3).travel);
    }

    @Test
    public void aTapOnAStripsWindow_slidesTheTerminalInOnIt() {
        for (int chip = 0; chip < 2; chip++) {
            Meaning<String> tile = home().tap(Surface.WALL_STRIP, chip);
            assertEquals(Meaning.Action.SHOW_WINDOW, tile.action);
            assertEquals(chip, tile.row);
            assertTrue(tile.travel);
        }
    }

    /** A window chip tapped on Home means slide to it: the Terminal travels in on it. */
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
    public void chipsPastTheEndAndNegative_meanNothing() {
        for (Surface surface : Surface.values()) {
            assertEquals(Meaning.Action.NONE, terminal().tap(surface, -1).action);
            assertEquals(Meaning.Action.NONE,
                terminal().tap(surface, terminal().count(surface)).action);
            assertEquals(Meaning.Action.NONE, terminal().close(surface, -1).action);
            assertEquals(Meaning.Action.NONE,
                terminal().close(surface, terminal().count(surface)).action);
        }
    }

    // ---------------------------------------------------------------- what a close means

    @Test
    public void aCloseOnARowsChip_closesThatWindow() {
        for (int chip = 0; chip < 2; chip++) {
            Meaning<String> meaning = terminal().close(Surface.BAR, chip);
            assertEquals(Meaning.Action.CLOSE_WINDOW, meaning.action);
            assertEquals(chip, meaning.row);
        }
        Meaning<String> tile = home().close(Surface.WALL_STRIP, 1);
        assertEquals(Meaning.Action.CLOSE_WINDOW, tile.action);
        assertEquals(1, tile.row);
    }

    @Test
    public void aCloseOnAFloatsChip_closesThatFloat() {
        Meaning<String> meaning = home().close(Surface.WALL_STRIP, 2);
        assertEquals(Meaning.Action.CLOSE_FLOAT, meaning.action);
        assertEquals("f", meaning.window);
    }

    /** The chip's close means on Home what it means in the Terminal. */
    @Test
    public void aCloseOnTheBarOnHome_meansWhatItMeansInTheTerminal() {
        for (int chip = 0; chip < 2; chip++) {
            Meaning<String> onHome = home().close(Surface.BAR, chip);
            Meaning<String> onTerminal = terminal().close(Surface.BAR, chip);
            assertEquals("chip " + chip, onTerminal.action, onHome.action);
            assertEquals("chip " + chip, onTerminal.row, onHome.row);
            assertEquals("chip " + chip, onTerminal.window, onHome.window);
            assertFalse(onHome.travel);
        }
        assertEquals(Meaning.Action.CLOSE_WINDOW, home().close(Surface.BAR, 0).action);
        assertEquals(Meaning.Action.NONE, home().close(Surface.BAR, 3).action);
    }

    @Test
    public void aBarWindowTapTravelsFromEveryPlaceButTheWindowRow() {
        for (PageKind place : PageKind.values()) {
            WindowChips<String> chips = new WindowChips<>(place, true, AB, 1);
            boolean travels = chips.tap(Surface.BAR, 0).travel;
            assertEquals(place.name(), place != PageKind.TERMINAL, travels);
            assertEquals(place.name(), travels, WindowChips.barWindowTravels(place));
        }
        assertTrue(new WindowChips<>(PageKind.ASSISTANT, true, AB, 1)
            .tap(Surface.BAR, 0).travel);
        assertTrue(new WindowChips<>(PageKind.HOME, true, AB, 1)
            .tap(Surface.BAR, 0).travel);
        assertFalse(new WindowChips<>(PageKind.TERMINAL, true, AB, 1)
            .tap(Surface.BAR, 0).travel);
    }

    @Test
    public void aFloatTapTravelsFromEveryPlaceButHome_fromTheSameInput() {
        for (PageKind place : PageKind.values()) {
            WindowChips<String> chips = new WindowChips<>(place, true, AB, 1)
                .addFloat("f", false, false);
            assertEquals(place.name(), place != PageKind.HOME,
                chips.tap(Surface.WALL_STRIP, 2).travel);
        }
    }

    @Test
    public void theWallStripAlwaysTravelsWhicheverPlace() {
        for (PageKind place : PageKind.values())
            assertTrue(new WindowChips<>(place, true, AB, 1).tap(Surface.WALL_STRIP, 0).travel);
    }
}
