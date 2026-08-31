package com.termux.app.terminal.keybind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.KeybindGroupPalette;
import com.termux.app.terminal.TerminalKeyBindingResolver.Hint;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hint surfaces' pure core: what a hint table turns into before any of it is drawn. No
 * Robolectric — {@link KeybindHintModel} takes its two host-dependent answers (a tool's display
 * name, a group's colour) through seams, which is the point of the split.
 */
public class KeybindHintModelTest {

    /** Group colours as a stable stand-in for the theme maths, counting the memoisation. */
    private static final class FakeColors implements KeybindHintModel.GroupColors {
        final List<KeybindGroupPalette.Group> asked = new ArrayList<>();

        @Override
        public int colorFor(KeybindGroupPalette.Group group) {
            asked.add(group);
            return 0xFF000000 | (group.ordinal() + 1);
        }
    }

    /** The registry's job: the binding's own --label wins, else the tool id stands in. */
    private static final KeybindHintModel.Labels LABELS =
        (toolName, bindingLabel) -> bindingLabel != null && !bindingLabel.isEmpty()
            ? bindingLabel : "title:" + toolName;

    private static Map<String, Hint> hints(String... tokenToolPairs) {
        Map<String, Hint> hints = new LinkedHashMap<>();
        for (int i = 0; i < tokenToolPairs.length; i += 2)
            hints.put(tokenToolPairs[i], new Hint(tokenToolPairs[i + 1], null));
        return hints;
    }

    private static Map<String, Hint> none() {
        return Collections.emptyMap();
    }

    private static List<KeybindHintModel.Entry> flat(KeybindHintModel.Legend legend) {
        List<KeybindHintModel.Entry> all = new ArrayList<>();
        for (List<KeybindHintModel.Entry> entries : legend.groups.values()) all.addAll(entries);
        return all;
    }

    // ------------------------------------------------------------------ caps

    @Test
    public void capTextSpellsNamedKeysAsGlyphsAndFollowsThePrefixCase() {
        assertEquals("←", KeybindHintModel.capText("left", false));
        assertEquals("↓", KeybindHintModel.capText("down", false));
        assertEquals("↑", KeybindHintModel.capText("up", false));
        assertEquals("→", KeybindHintModel.capText("right", false));
        assertEquals("-", KeybindHintModel.capText("minus", false));
        assertEquals("=", KeybindHintModel.capText("equals", false));
        assertEquals("+", KeybindHintModel.capText("plus", false));
        assertEquals("␣", KeybindHintModel.capText("space", false));
        assertEquals("⇥", KeybindHintModel.capText("tab", false));
        assertEquals("⏎", KeybindHintModel.capText("enter", false));
        assertEquals("⌫", KeybindHintModel.capText("backspace", false));
        assertEquals("⌦", KeybindHintModel.capText("delete", false));
        assertEquals("esc", KeybindHintModel.capText("escape", false));
        assertEquals("⇞", KeybindHintModel.capText("pageup", false));
        assertEquals("⇟", KeybindHintModel.capText("pagedown", false));
        // Letters follow the held prefix: the Shift layer prints the cap the user will press.
        assertEquals("c", KeybindHintModel.capText("C", false));
        assertEquals("C", KeybindHintModel.capText("c", true));
        // A named key keeps its glyph whether or not Shift joined.
        assertEquals("␣", KeybindHintModel.capText("space", true));
    }

    @Test
    public void arrowGlyphOnlyAnswersForArrows() {
        assertEquals("←", KeybindHintModel.arrowGlyph("left"));
        assertNull(KeybindHintModel.arrowGlyph("l"));
        assertNull(KeybindHintModel.arrowGlyph("5"));
    }

    @Test
    public void runTokensAreTheArrowsAndTheSingleDigits() {
        assertTrue(KeybindHintModel.isRunToken("left"));
        assertTrue(KeybindHintModel.isRunToken("right"));
        assertTrue(KeybindHintModel.isRunToken("7"));
        assertTrue(!KeybindHintModel.isRunToken("12"));
        assertTrue(!KeybindHintModel.isRunToken("c"));
        assertTrue(!KeybindHintModel.isRunToken("space"));
    }

    @Test
    public void runCapPrintsArrowsInReadingOrderAndDigitsAsASpan() {
        assertEquals("←↓↑→",
            KeybindHintModel.runCap(Arrays.asList("up", "right", "left", "down")));
        assertEquals("←→", KeybindHintModel.runCap(Arrays.asList("right", "left")));
        // Three or more digits collapse to the range they span, contiguous or not.
        assertEquals("1-9",
            KeybindHintModel.runCap(Arrays.asList("3", "1", "9", "5")));
        assertEquals("2 4", KeybindHintModel.runCap(Arrays.asList("4", "2")));
    }

    // ------------------------------------------------------------------ legend packing

    @Test
    public void entriesLandInPaletteGroupOrderWithOneColourLookupPerGroup() {
        FakeColors colors = new FakeColors();
        KeybindHintModel.Legend legend = KeybindHintModel.entriesFor(
            hints("c", "window.new", "v", "pane.split_vertical", "x", "window.close",
                "h", "pane.split_horizontal"),
            none(), false, LABELS, colors);

        assertEquals(Arrays.asList(KeybindGroupPalette.Group.PANES,
                KeybindGroupPalette.Group.WINDOWS),
            new ArrayList<>(legend.groups.keySet()));
        assertEquals(2, legend.groups.get(KeybindGroupPalette.Group.PANES).size());
        assertEquals(2, legend.groups.get(KeybindGroupPalette.Group.WINDOWS).size());
        assertEquals(4, legend.entryCount());
        // One lookup per group, memoised across both tables.
        assertEquals(Arrays.asList(KeybindGroupPalette.Group.WINDOWS,
                KeybindGroupPalette.Group.PANES),
            colors.asked);
    }

    @Test
    public void litTokensFollowTheHintOrderAndWearTheirGroupColour() {
        FakeColors colors = new FakeColors();
        KeybindHintModel.Legend legend = KeybindHintModel.entriesFor(
            hints("c", "window.new", "v", "pane.split_vertical"), none(), false, LABELS, colors);

        assertEquals(Arrays.asList("c", "v"), new ArrayList<>(legend.litTokens.keySet()));
        assertEquals(legend.groupColors.get(KeybindGroupPalette.Group.WINDOWS),
            legend.litTokens.get("c"));
        assertEquals(legend.groupColors.get(KeybindGroupPalette.Group.PANES),
            legend.litTokens.get("v"));
    }

    @Test
    public void rowsAreCappedAtTwentyFourButEveryBoundCapStillLights() {
        Map<String, Hint> many = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++)
            many.put("k" + i, new Hint("terminal.action" + i, null));

        KeybindHintModel.Legend legend =
            KeybindHintModel.entriesFor(many, none(), false, LABELS, new FakeColors());

        assertEquals(KeybindHintModel.MAX_ENTRIES, legend.entryCount());
        assertEquals(30, legend.litTokens.size());
        // The cap drops rows off the end, so the first 24 are the ones that made it.
        assertEquals("k0", flat(legend).get(0).tokens.get(0));
        assertEquals("k23", flat(legend).get(23).tokens.get(0));
    }

    @Test
    public void theRowCapSpansBothTablesAndTheCtrlTableNeverLights() {
        Map<String, Hint> many = new LinkedHashMap<>();
        for (int i = 0; i < 24; i++)
            many.put("k" + i, new Hint("terminal.action" + i, null));

        KeybindHintModel.Legend legend = KeybindHintModel.entriesFor(many,
            hints("f", "pane.focus_next"), false, LABELS, new FakeColors());

        // The prefixed table already spent the cap, so the Ctrl row is dropped...
        assertEquals(KeybindHintModel.MAX_ENTRIES, legend.entryCount());
        // ...but nothing from a table that is a different chord ever lit a key anyway.
        assertEquals(24, legend.litTokens.size());
        assertTrue(!legend.litTokens.containsKey("f"));
    }

    @Test
    public void ctrlEntriesSpellTheirOwnChordOnTheCap() {
        KeybindHintModel.Legend legend = KeybindHintModel.entriesFor(
            hints("c", "window.new"),
            hints("left", "pane.focus", "right", "pane.focus"),
            false, LABELS, new FakeColors());

        List<KeybindHintModel.Entry> panes = legend.groups.get(KeybindGroupPalette.Group.PANES);
        assertEquals(1, panes.size());
        // A merged run keeps its chord in front of the range it spells.
        assertEquals("Ctrl+←→", panes.get(0).cap);
        assertEquals("Ctrl+", panes.get(0).capPrefix);
        assertEquals("", legend.groups.get(KeybindGroupPalette.Group.WINDOWS).get(0).capPrefix);
    }

    @Test
    public void aRunOfOneToolsKeysCostsOneRow() {
        Map<String, Hint> hints = new LinkedHashMap<>();
        for (String token : new String[] {"left", "down", "up", "right"})
            hints.put(token, new Hint("pane.focus", "Move pane focus"));
        for (int i = 1; i <= 9; i++)
            hints.put(String.valueOf(i), new Hint("session.select", "Switch to session"));

        KeybindHintModel.Legend legend =
            KeybindHintModel.entriesFor(hints, none(), false, LABELS, new FakeColors());

        assertEquals(2, legend.entryCount());
        assertEquals("←↓↑→", legend.groups.get(KeybindGroupPalette.Group.PANES).get(0).cap);
        assertEquals("1-9", legend.groups.get(KeybindGroupPalette.Group.SESSION).get(0).cap);
        // Every key of a merged run still lights, one row or not.
        assertEquals(13, legend.litTokens.size());
    }

    @Test
    public void runsMergeOnThePrintedLabelNotJustTheTool() {
        Map<String, Hint> hints = new LinkedHashMap<>();
        hints.put("1", new Hint("app.launch", "WhatsApp"));
        hints.put("2", new Hint("app.launch", "Firefox"));
        hints.put("3", new Hint("app.launch", "Firefox"));

        KeybindHintModel.Legend legend =
            KeybindHintModel.entriesFor(hints, none(), false, LABELS, new FakeColors());

        List<KeybindHintModel.Entry> app = legend.groups.get(KeybindGroupPalette.Group.APP);
        assertEquals(2, app.size());
        assertEquals("WhatsApp", app.get(0).label);
        assertEquals("1", app.get(0).cap);
        assertEquals("Firefox", app.get(1).label);
        assertEquals("2 3", app.get(1).cap);
    }

    @Test
    public void anUnlabelledBindingIsNamedByTheLabelSeam() {
        KeybindHintModel.Legend legend = KeybindHintModel.entriesFor(
            hints("c", "window.new"), none(), false, LABELS, new FakeColors());

        assertEquals("title:window.new",
            legend.groups.get(KeybindGroupPalette.Group.WINDOWS).get(0).label);
    }

    @Test
    public void unknownNamespacesFallIntoTheViewGroup() {
        KeybindHintModel.Legend legend = KeybindHintModel.entriesFor(
            hints("z", "nonsense"), none(), false, LABELS, new FakeColors());

        assertEquals(Collections.singletonList(KeybindGroupPalette.Group.VIEW),
            new ArrayList<>(legend.groups.keySet()));
    }

    @Test
    public void litTokensAloneNeedsNoLegend() {
        FakeColors colors = new FakeColors();
        Map<String, Integer> lit = KeybindHintModel.litTokens(
            hints("c", "window.new", "v", "pane.split_vertical", "x", "window.close"), colors);

        assertEquals(Arrays.asList("c", "v", "x"), new ArrayList<>(lit.keySet()));
        assertEquals(lit.get("c"), lit.get("x"));
        assertEquals(2, colors.asked.size());
    }

    // ------------------------------------------------------------------ the strip

    @Test
    public void stripKeepsTheCuratedOrderAndDropsWhatIsNotBound() {
        List<KeybindHintModel.StripChip> chips = KeybindHintModel.stripChips(
            hints("v", "pane.split_vertical", "c", "window.new",
                "left", "window.previous", "right", "window.next"),
            false);

        assertEquals(3, chips.size());
        assertEquals("v", chips.get(0).caps);
        assertEquals("split", chips.get(0).label);
        assertEquals("v", chips.get(0).colorToken);
        assertEquals("c", chips.get(1).caps);
        assertEquals("new window", chips.get(1).label);
        // Both arrows bound: one chip, and the colour comes from the first of them.
        assertEquals("← →", chips.get(2).caps);
        assertEquals("left", chips.get(2).colorToken);
    }

    @Test
    public void stripLeadsWithTheNewPaneChipAsAnEnterGlyph() {
        List<KeybindHintModel.StripChip> chips = KeybindHintModel.stripChips(
            hints("enter", "pane.split", "v", "pane.split_vertical"), false);
        assertEquals(2, chips.size());
        assertEquals("⏎", chips.get(0).caps);
        assertEquals("new pane", chips.get(0).label);
        assertEquals("enter", chips.get(0).colorToken);
        assertEquals("split", chips.get(1).label);
    }

    @Test
    public void stripIsEmptyWhenNothingCuratedIsBound() {
        assertTrue(KeybindHintModel.stripChips(hints("q", "terminal.thing"), false).isEmpty());
        assertTrue(KeybindHintModel.stripChips(none(), false).isEmpty());
    }

    @Test
    public void theShiftLayerIsItsOwnCuratedListInUpperCase() {
        List<KeybindHintModel.StripChip> chips = KeybindHintModel.stripChips(
            hints("c", "session.new", "p", "view.palette",
                "left", "pane.resize", "up", "pane.resize"),
            true);

        assertEquals(3, chips.size());
        assertEquals("C", chips.get(0).caps);
        assertEquals("new session", chips.get(0).label);
        // The resize chip lists only the arrows that are actually bound, in curated order.
        assertEquals("← ↑", chips.get(1).caps);
        assertEquals("P", chips.get(2).caps);
        assertEquals("palette", chips.get(2).label);
    }

    /**
     * The strip's own tokens and nothing else — the keyboard's lighting under a held prefix is
     * built from this, so it cannot disagree with what the strip prints.
     */
    @Test
    public void stripLitTokens_coverTheStripsChipsOnly() {
        Map<String, Hint> hints = new LinkedHashMap<>();
        hints.put("v", new Hint("pane.split_vertical", null));
        hints.put("c", new Hint("window.new", null));
        hints.put("left", new Hint("window.previous", null));
        hints.put("u", new Hint("terminal.hints", null));

        Map<String, Integer> lit = KeybindHintModel.stripLitTokens(hints, false, group -> 0xFF00FF00);

        assertTrue(lit.containsKey("v"));
        assertTrue(lit.containsKey("c"));
        assertTrue(lit.containsKey("left"));
        assertFalse("a bind no chip prints is not the strip's to light", lit.containsKey("u"));
    }

    @Test
    public void prefixLabel_spellsTheChordAHumanHolds() {
        assertEquals("Ctrl+Alt", KeybindHintModel.prefixLabel("ctrl+alt+"));
        assertEquals("Ctrl+Alt+Shift", KeybindHintModel.prefixLabel("ctrl+alt+shift+"));
        // A leader keeps the mark that says it is waiting for a second key.
        assertEquals("Ctrl+Space \u25b8", KeybindHintModel.prefixLabel("ctrl+space>"));
    }
}
