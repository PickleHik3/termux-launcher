package com.termux.app.terminal;

import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Ranking and filtering behavior for the command palette. */
public class CommandPaletteFilterTest {

    private static CommandPaletteFilter.Entry entry(String tool, String title, String category,
                                                    List<String> bindings) {
        return new CommandPaletteFilter.Entry(tool, title, bindings.isEmpty() ? "" : bindings.get(0),
            category, bindings, true, null, false, LauncherToolRegistry.ToolRisk.LOW);
    }

    private static CommandPaletteFilter.Entry disabled(String tool, String title, String category) {
        return new CommandPaletteFilter.Entry(tool, title, "", category,
            Collections.<String>emptyList(), false, "unavailable", false,
            LauncherToolRegistry.ToolRisk.LOW);
    }

    private static List<CommandPaletteFilter.Entry> sample() {
        return Arrays.asList(
            entry("session.new", "New session", "session", Collections.singletonList("ctrl+alt+shift+c")),
            entry("session.next", "Next session", "session", Collections.<String>emptyList()),
            entry("session.close_current", "Close session", "session", Collections.singletonList("ctrl+alt+shift+x")),
            entry("window.new", "New window", "window", Collections.singletonList("ctrl+alt+c")),
            entry("window.close", "Close window", "window", Collections.singletonList("ctrl+alt+x")),
            entry("pane.split_vertical", "Split pane vertically", "pane", Collections.singletonList("ctrl+alt+v")),
            entry("pane.kill_focused", "Kill focused pane", "pane", Collections.<String>emptyList()));
    }

    private static List<String> titles(List<CommandPaletteFilter.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (CommandPaletteFilter.Entry e : entries) out.add(e.title);
        return out;
    }

    @Test
    public void emptyQuery_preservesSuppliedOrder() {
        assertEquals(titles(sample()), titles(CommandPaletteFilter.filterAndRank(sample(), "")));
        assertEquals(titles(sample()), titles(CommandPaletteFilter.filterAndRank(sample(), "   ")));
        assertEquals(titles(sample()), titles(CommandPaletteFilter.filterAndRank(sample(), null)));
    }

    @Test
    public void exactTitleMatch_ranksFirst() {
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "new window");
        assertEquals("New window", result.get(0).title);
    }

    @Test
    public void prefixBeatsSubstring() {
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "close");
        // "Close session" and "Close window" start with the query; nothing else should outrank them.
        assertTrue(result.get(0).title.startsWith("Close"));
        assertTrue(result.get(1).title.startsWith("Close"));
    }

    @Test
    public void wordPrefixMatches() {
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "sess");
        assertFalse(result.isEmpty());
        for (CommandPaletteFilter.Entry e : result) {
            assertTrue(e.title + " should relate to sessions",
                e.title.toLowerCase().contains("session") || e.category.equals("session"));
        }
    }

    @Test
    public void toolIdIsSearchable_withSeparatorsAsSpaces() {
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "split vertical");
        assertEquals("Split pane vertically", result.get(0).title);
    }

    @Test
    public void bindingIsSearchable() {
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "ctrl+alt+v");
        assertEquals("Split pane vertically", result.get(0).title);
    }

    @Test
    public void categoryIsSearchable() {
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "pane");
        assertFalse(result.isEmpty());
        assertTrue(titles(result).contains("Kill focused pane"));
    }

    @Test
    public void fuzzySubsequenceMatches() {
        // "kfp" hits "Kill focused pane" only as a subsequence.
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "kfp");
        assertEquals("Kill focused pane", result.get(0).title);
    }

    @Test
    public void singleCharQuery_doesNotFuzzyMatchEverything() {
        // A one-character query must not fall back to subsequence matching.
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "z");
        assertTrue(result.isEmpty());
    }

    @Test
    public void nonMatchingQuery_returnsNothing() {
        assertTrue(CommandPaletteFilter.filterAndRank(sample(), "wallpaper").isEmpty());
    }

    @Test
    public void queryIsCaseInsensitive() {
        assertEquals(titles(CommandPaletteFilter.filterAndRank(sample(), "NEW WINDOW")),
            titles(CommandPaletteFilter.filterAndRank(sample(), "new window")));
    }

    @Test
    public void enabledEntriesOutrankDisabledAtEqualScore() {
        List<CommandPaletteFilter.Entry> entries = Arrays.asList(
            disabled("window.close", "Close window", "window"),
            entry("session.close_current", "Close window", "session", Collections.<String>emptyList()));
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(entries, "close window");
        assertEquals(2, result.size());
        assertTrue("enabled entry must come first", result.get(0).enabled);
    }

    @Test
    public void shorterTitleWinsAtEqualScore() {
        List<CommandPaletteFilter.Entry> entries = Arrays.asList(
            entry("a.long", "Close window immediately", "window", Collections.<String>emptyList()),
            entry("b.short", "Close window", "window", Collections.<String>emptyList()));
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(entries, "close");
        assertEquals("Close window", result.get(0).title);
    }

    @Test
    public void isSubsequence_behavesAsExpected() {
        assertTrue(CommandPaletteFilter.isSubsequence("kfp", "kill focused pane"));
        assertTrue(CommandPaletteFilter.isSubsequence("nw", "new window"));
        // "wn" matches too: the w of "window" precedes its n.
        assertTrue(CommandPaletteFilter.isSubsequence("wn", "new window"));
        // "dn" cannot: no n follows the d.
        assertFalse(CommandPaletteFilter.isSubsequence("dn", "new window"));
        assertFalse(CommandPaletteFilter.isSubsequence("xyz", "new window"));
    }

    @Test
    public void destructiveClassification_followsRisk() {
        CommandPaletteFilter.Entry high = new CommandPaletteFilter.Entry(
            "window.close", "Close window", "", "window", Collections.<String>emptyList(),
            true, null, true, LauncherToolRegistry.ToolRisk.HIGH);
        CommandPaletteFilter.Entry medium = new CommandPaletteFilter.Entry(
            "window.new", "New window", "", "window", Collections.<String>emptyList(),
            true, null, true, LauncherToolRegistry.ToolRisk.MEDIUM);
        assertTrue(high.isDestructive());
        assertFalse(medium.isDestructive());
    }

    @Test
    public void resultsAreImmutable() {
        List<CommandPaletteFilter.Entry> result = CommandPaletteFilter.filterAndRank(sample(), "close");
        try {
            result.add(sample().get(0));
            org.junit.Assert.fail("result must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
