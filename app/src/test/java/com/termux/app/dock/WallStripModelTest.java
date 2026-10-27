package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.WindowChips;
import com.termux.app.terminal.AgentStatus;
import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Wall strip's entries from the window bar's chips: one numbered entry per chip in the bar's order, the
 * one in front lit, the display window and the Home floats told apart, a minimised float marked,
 * attention as the chip column reads it, {@code +} last, and nothing at all when nothing runs.
 */
public class WallStripModelTest {

    private static WindowItem chip(String label) {
        return new WindowItem(label, label);
    }

    private static List<WallStripModel.Entry> build(List<WindowItem> chips, int selected, int row,
                                                   int display, boolean[] minimised) {
        return WallStripModel.build(chips, selected, row, display, minimised, "New window");
    }

    @Test
    public void nothingRunning_isNoStrip_notALonePlus() {
        assertTrue(build(Collections.emptyList(), -1, 0, -1, null).isEmpty());
        assertTrue(build(null, -1, 0, -1, null).isEmpty());
    }

    @Test
    public void oneEntryPerChip_numberedInOrder_plusLast() {
        List<WallStripModel.Entry> entries = build(
            Arrays.asList(chip("❯ nvim"), chip("❯ htop")), 0, 2, -1, null);
        assertEquals(3, entries.size());
        assertEquals("1", entries.get(0).number);
        assertEquals("❯ nvim", entries.get(0).label);
        assertEquals("2", entries.get(1).number);
        assertEquals(1, entries.get(1).chipIndex);
        WallStripModel.Entry plus = entries.get(2);
        assertEquals(WallStripModel.Kind.NEW_WINDOW, plus.kind);
        assertEquals(-1, plus.chipIndex);
        assertEquals("+", plus.number);
        assertEquals("New window", plus.spokenLabel);
    }

    @Test
    public void theWindowInFront_isLit_andOnlyIt() {
        List<WallStripModel.Entry> entries = build(
            Arrays.asList(chip("a"), chip("b"), chip("c")), 1, 3, -1, null);
        assertFalse(entries.get(0).current);
        assertTrue(entries.get(1).current);
        assertFalse(entries.get(2).current);
        assertFalse(entries.get(3).current);
    }

    @Test
    public void noneInFront_litsNothing() {
        for (WallStripModel.Entry entry : build(Arrays.asList(chip("a")), -1, 0, -1, null))
            assertFalse(entry.current);
    }

    @Test
    public void displayWindow_andHomeFloats_areToldApart() {
        List<WallStripModel.Entry> entries = build(
            Arrays.asList(chip("nvim"), chip("GIMP"), chip("btop"), chip("float")), 0, 3, 1,
            new boolean[]{true});
        assertEquals(WallStripModel.Kind.WINDOW, entries.get(0).kind);
        assertEquals(WallStripModel.Kind.DISPLAY, entries.get(1).kind);
        assertEquals(WallStripModel.Kind.WINDOW, entries.get(2).kind);
        assertEquals(WallStripModel.Kind.HOME_FLOAT, entries.get(3).kind);
        assertTrue(entries.get(3).minimised);
        assertFalse(entries.get(0).minimised);
    }

    @Test
    public void onHome_everyChipIsAFloat_andMissingFlagsReadAsShown() {
        List<WallStripModel.Entry> entries = build(
            Arrays.asList(chip("one"), chip("two")), -1, 0, -1, new boolean[]{false});
        assertEquals(WallStripModel.Kind.HOME_FLOAT, entries.get(0).kind);
        assertEquals(WallStripModel.Kind.HOME_FLOAT, entries.get(1).kind);
        assertFalse(entries.get(0).minimised);
        assertFalse(entries.get(1).minimised);
    }

    @Test
    public void attention_isTheBell_orABlockedAgent() {
        WindowItem bell = chip("a").withAttention(true);
        WindowItem blocked = chip("b").withAgentState(AgentStatus.State.BLOCKED);
        WindowItem working = chip("c").withAgentState(AgentStatus.State.WORKING);
        List<WallStripModel.Entry> entries = build(Arrays.asList(bell, blocked, working,
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
        List<WallStripModel.Entry> entries = build(Arrays.asList(chip("a")), 0, 5, -1, null);
        assertEquals(WallStripModel.Kind.WINDOW, entries.get(0).kind);
        assertEquals(2, entries.size());
    }

    @Test
    public void rowEntries_leaveOutFloatsAndPlus() {
        List<WallStripModel.Entry> entries = build(
            Arrays.asList(chip("nvim"), chip("GIMP"), chip("float")), 0, 2, 1, null);
        List<WallStripModel.Entry> row = WallStripModel.rowEntries(entries);
        assertEquals(2, row.size());
        assertEquals(WallStripModel.Kind.WINDOW, row.get(0).kind);
        assertEquals(WallStripModel.Kind.DISPLAY, row.get(1).kind);
    }

    @Test
    public void sameChips_buildEqualEntries() {
        List<WindowItem> chips = Arrays.asList(chip("a"), chip("b"));
        assertEquals(build(chips, 0, 2, -1, null), build(chips, 0, 2, -1, null));
    }

    // ---------------------------------------------------------------- the process glyph

    /** The terminal glyph the chip factories put in front of an idle shell's label. */
    private static final String TERMINAL = "";
    /** An astral Material Design glyph (U+F01DA), two chars long. */
    private static final String ASTRAL = new String(Character.toChars(0xF01DA));

    @Test
    public void theProcessGlyph_isKeptApart_andTheLabelIsPlainText() {
        List<WallStripModel.Entry> entries = build(Arrays.asList(chip(TERMINAL + " home"),
            chip(ASTRAL + " notes.md"), chip("plain")), 0, 3, -1, null);
        assertEquals(TERMINAL, entries.get(0).glyph);
        assertEquals("home", entries.get(0).label);
        assertEquals(ASTRAL, entries.get(1).glyph);
        assertEquals("notes.md", entries.get(1).label);
        assertEquals("", entries.get(2).glyph);
        assertEquals("plain", entries.get(2).label);
        for (WallStripModel.Entry entry : entries) assertNoNerdSymbol(entry.label + entry.shortLabel);
    }

    @Test
    public void aGlyphInsideANamedLabel_isTakenOut() {
        List<WallStripModel.Entry> entries = build(
            Arrays.asList(chip(TERMINAL + " my " + ASTRAL + " box")), 0, 1, -1, null);
        assertEquals(TERMINAL, entries.get(0).glyph);
        assertEquals("my box", entries.get(0).label);
    }

    @Test
    public void aGlyphAlone_isAllGlyph_andAGlyphRunIntoAWordIsNotTheProcessGlyph() {
        assertEquals(TERMINAL, WallStripModel.leadingGlyph(TERMINAL));
        assertEquals("", WallStripModel.leadingGlyph(TERMINAL + "x"));
        assertEquals("", WallStripModel.leadingGlyph("~ home"));
    }

    private static void assertNoNerdSymbol(String text) {
        text.codePoints().forEach(cp -> assertFalse(Integer.toHexString(cp),
            WallStripModel.isNerdSymbol(cp)));
    }

    // ---------------------------------------------------------------- what tells entries apart

    @Test
    public void programs_readAsThemselves() {
        List<WallStripModel.Entry> entries = build(Arrays.asList(chip(TERMINAL + " nvim"),
            chip(TERMINAL + " htop"), chip(TERMINAL + " home"), chip(TERMINAL + " git")), 0, 4,
            -1, null);
        assertEquals("nvim", entries.get(0).shortLabel);
        assertEquals("htop", entries.get(1).shortLabel);
        assertEquals("home", entries.get(2).shortLabel);
        assertEquals("git", entries.get(3).shortLabel);
    }

    @Test
    public void aUserAtHostLead_isNeverWhatIsShown() {
        List<WallStripModel.Entry> entries = build(Arrays.asList(chip("u0_a123@localhost: ~/proj"),
            chip("u0_a123@localhost:~/src"), chip("[me@box htop]"), chip("me@box")), 0, 4, -1,
            null);
        assertEquals("~/proj", entries.get(0).shortLabel);
        assertEquals("~/src", entries.get(1).shortLabel);
        assertEquals("htop]", entries.get(2).shortLabel);
        // Nothing after the lead: the lead is all there is to show.
        assertEquals("me@box", entries.get(3).shortLabel);
    }

    @Test
    public void aLeadEveryEntryShares_isLeftOff_atAWordBoundary() {
        List<WallStripModel.Entry> entries = build(Arrays.asList(chip("ssh prod-1"),
            chip("ssh prod-2"), chip("ssh staging")), 0, 3, -1, null);
        assertEquals("prod-1", entries.get(0).shortLabel);
        assertEquals("prod-2", entries.get(1).shortLabel);
        assertEquals("staging", entries.get(2).shortLabel);
        // A shared lead inside a word is not cut: "project" and "projector" share "project".
        List<WallStripModel.Entry> words = build(Arrays.asList(chip("project"),
            chip("projector")), 0, 2, -1, null);
        assertEquals("project", words.get(0).shortLabel);
        assertEquals("projector", words.get(1).shortLabel);
    }

    @Test
    public void sameLabels_keepTheirWholeText() {
        List<WallStripModel.Entry> entries = build(Arrays.asList(chip(TERMINAL + " home"),
            chip(TERMINAL + " home"), chip(TERMINAL + " home")), 0, 3, -1, null);
        for (int i = 0; i < 3; i++) assertEquals("home", entries.get(i).shortLabel);
    }

    @Test
    public void aFloatsPageMark_isLeftOffItsShortLabel_butNotARowWindows() {
        List<WallStripModel.Entry> entries = build(Arrays.asList(chip(TERMINAL + " nvim"),
            chip(TERMINAL + " v · 2"), chip(TERMINAL + " home · 2")), 0, 2, -1, null);
        assertEquals("v · 2", entries.get(1).shortLabel);
        assertEquals("home", entries.get(2).shortLabel);
        assertEquals("home · 2", entries.get(2).label);
        assertTrue(entries.get(2).isFloat());
    }

    @Test
    public void plus_hasNoLabelOrGlyph() {
        WallStripModel.Entry plus = build(Arrays.asList(chip(TERMINAL + " a")), 0, 1, -1, null)
            .get(1);
        assertEquals("", plus.label);
        assertEquals("", plus.shortLabel);
        assertEquals("", plus.glyph);
    }

    // ---------------------------------------------------------------- the Wall strip's terminals

    @Test
    public void wallEntries_keepTheRowAndTheFloats_inChipOrder_plusLeftOut() {
        List<WallStripModel.Entry> entries = build(
            Arrays.asList(chip("nvim"), chip("GIMP"), chip("float")), 0, 2, 1,
            new boolean[]{true});
        List<WallStripModel.Entry> wall = WallStripModel.wallEntries(entries);
        assertEquals(3, wall.size());
        assertEquals(WallStripModel.Kind.WINDOW, wall.get(0).kind);
        assertEquals(WallStripModel.Kind.DISPLAY, wall.get(1).kind);
        assertEquals(WallStripModel.Kind.HOME_FLOAT, wall.get(2).kind);
        assertTrue(wall.get(2).isFloat());
        assertFalse(wall.get(0).isFloat());
        assertEquals(2, wall.get(2).chipIndex);
    }

    @Test
    public void fromTheWindowChips_kindsSelectionAndMinimisedFloatsFollowTheChips() {
        // a D b, then floats f (minimised) and g (in focus), as Home's dock sees them.
        WindowChips<String> chips = new WindowChips<>(true, true, Arrays.asList("a", "b"), 1, 0)
            .addFloat("f", true, false)
            .addFloat("g", false, true);
        List<WindowItem> items = Arrays.asList(chip("a"), chip("Display"), chip("b"),
            chip("f"), chip("g"));
        List<WallStripModel.Entry> entries =
            WallStripModel.of(chips, WindowChips.Surface.WALL_STRIP, items, "New window");
        assertEquals(6, entries.size());
        assertEquals(WallStripModel.Kind.WINDOW, entries.get(0).kind);
        assertEquals(WallStripModel.Kind.DISPLAY, entries.get(1).kind);
        assertEquals(WallStripModel.Kind.WINDOW, entries.get(2).kind);
        assertEquals(WallStripModel.Kind.HOME_FLOAT, entries.get(3).kind);
        assertTrue(entries.get(3).minimised);
        assertFalse(entries.get(4).minimised);
        assertTrue("the float in focus is lit", entries.get(4).current);
        assertFalse(entries.get(0).current);
        assertEquals(WallStripModel.Kind.NEW_WINDOW, entries.get(5).kind);
        assertEquals(entries,
            WallStripModel.of(chips, WindowChips.Surface.WALL_STRIP, items, "New window"));
    }
}
