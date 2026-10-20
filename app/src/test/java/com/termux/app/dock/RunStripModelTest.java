package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.AgentStatus;
import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The run strip from the window bar's chips: one numbered entry per chip in the bar's order, the
 * one in front lit, the display window and the Home floats told apart, a minimised float marked,
 * attention as the chip column reads it, {@code +} last, and nothing at all when nothing runs.
 */
public class RunStripModelTest {

    private static WindowItem chip(String label) {
        return new WindowItem(label, label);
    }

    private static List<RunStripModel.Entry> build(List<WindowItem> chips, int selected, int row,
                                                   int display, boolean[] minimised) {
        return RunStripModel.build(chips, selected, row, display, minimised, "New window");
    }

    @Test
    public void nothingRunning_isNoStrip_notALonePlus() {
        assertTrue(build(Collections.emptyList(), -1, 0, -1, null).isEmpty());
        assertTrue(build(null, -1, 0, -1, null).isEmpty());
        assertFalse(RunStripModel.isShown(build(null, -1, 0, -1, null)));
    }

    @Test
    public void oneEntryPerChip_numberedInOrder_plusLast() {
        List<RunStripModel.Entry> entries = build(
            Arrays.asList(chip("❯ nvim"), chip("❯ htop")), 0, 2, -1, null);
        assertEquals(3, entries.size());
        assertEquals("1", entries.get(0).number);
        assertEquals("❯ nvim", entries.get(0).label);
        assertEquals("2", entries.get(1).number);
        assertEquals(1, entries.get(1).chipIndex);
        RunStripModel.Entry plus = entries.get(2);
        assertEquals(RunStripModel.Kind.NEW_WINDOW, plus.kind);
        assertEquals(-1, plus.chipIndex);
        assertEquals("+", plus.number);
        assertEquals("New window", plus.spokenLabel);
    }

    @Test
    public void theWindowInFront_isLit_andOnlyIt() {
        List<RunStripModel.Entry> entries = build(
            Arrays.asList(chip("a"), chip("b"), chip("c")), 1, 3, -1, null);
        assertFalse(entries.get(0).current);
        assertTrue(entries.get(1).current);
        assertFalse(entries.get(2).current);
        assertFalse(entries.get(3).current);
    }

    @Test
    public void noneInFront_litsNothing() {
        for (RunStripModel.Entry entry : build(Arrays.asList(chip("a")), -1, 0, -1, null))
            assertFalse(entry.current);
    }

    @Test
    public void displayWindow_andHomeFloats_areToldApart() {
        List<RunStripModel.Entry> entries = build(
            Arrays.asList(chip("nvim"), chip("GIMP"), chip("btop"), chip("float")), 0, 3, 1,
            new boolean[]{true});
        assertEquals(RunStripModel.Kind.WINDOW, entries.get(0).kind);
        assertEquals(RunStripModel.Kind.DISPLAY, entries.get(1).kind);
        assertEquals(RunStripModel.Kind.WINDOW, entries.get(2).kind);
        assertEquals(RunStripModel.Kind.HOME_FLOAT, entries.get(3).kind);
        assertTrue(entries.get(3).minimised);
        assertFalse(entries.get(0).minimised);
    }

    @Test
    public void onHome_everyChipIsAFloat_andMissingFlagsReadAsShown() {
        List<RunStripModel.Entry> entries = build(
            Arrays.asList(chip("one"), chip("two")), -1, 0, -1, new boolean[]{false});
        assertEquals(RunStripModel.Kind.HOME_FLOAT, entries.get(0).kind);
        assertEquals(RunStripModel.Kind.HOME_FLOAT, entries.get(1).kind);
        assertFalse(entries.get(0).minimised);
        assertFalse(entries.get(1).minimised);
    }

    @Test
    public void attention_isTheBell_orABlockedAgent() {
        WindowItem bell = chip("a").withAttention(true);
        WindowItem blocked = chip("b").withAgentState(AgentStatus.State.BLOCKED);
        WindowItem working = chip("c").withAgentState(AgentStatus.State.WORKING);
        List<RunStripModel.Entry> entries = build(Arrays.asList(bell, blocked, working,
            chip("d").withBusy(true)), 0, 4, -1, null);
        assertTrue(entries.get(0).attention);
        assertTrue(entries.get(1).attention);
        assertFalse(entries.get(2).attention);
        assertTrue(entries.get(2).busy);
        assertTrue(entries.get(3).busy);
        assertFalse(entries.get(3).attention);
    }

    @Test
    public void rowCountPastTheChips_isHeldToThem() {
        List<RunStripModel.Entry> entries = build(Arrays.asList(chip("a")), 0, 5, -1, null);
        assertEquals(RunStripModel.Kind.WINDOW, entries.get(0).kind);
        assertEquals(2, entries.size());
    }

    @Test
    public void rowEntries_leaveOutFloatsAndPlus() {
        List<RunStripModel.Entry> entries = build(
            Arrays.asList(chip("nvim"), chip("GIMP"), chip("float")), 0, 2, 1, null);
        List<RunStripModel.Entry> row = RunStripModel.rowEntries(entries);
        assertEquals(2, row.size());
        assertEquals(RunStripModel.Kind.WINDOW, row.get(0).kind);
        assertEquals(RunStripModel.Kind.DISPLAY, row.get(1).kind);
    }

    @Test
    public void sameChips_buildEqualEntries() {
        List<WindowItem> chips = Arrays.asList(chip("a"), chip("b"));
        assertEquals(build(chips, 0, 2, -1, null), build(chips, 0, 2, -1, null));
    }
}
