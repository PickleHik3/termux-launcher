package com.termux.app.terminal;

import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.launcherctl.LauncherToolRegistry;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One ranked list for a palette search: actions, apps and contacts on one score scale, usage
 * breaking ties within a level, query memory ahead of everything, and {@code >} for actions only.
 */
public class CommandPaletteMergedRankingTest {

    private static final Map<String, Double> NO_USAGE = Collections.emptyMap();
    private static final Map<String, CommandPaletteQueryMemory.Remembered> NO_MEMORY =
        Collections.emptyMap();
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    /** A fixed "now" for query memory, so decay is the same on every run. */
    private static final long NOW = 400L * DAY_MS;

    // ------------------------------------------------------------------ fixtures

    private static CommandPaletteFilter.Entry action(String tool, String title) {
        return new CommandPaletteFilter.Entry(tool, title, "", "pane",
            Collections.<String>emptyList(), true, null, false, LauncherToolRegistry.ToolRisk.LOW);
    }

    private static CommandPaletteFilter.Entry appRow(String stableId, String title) {
        JSONObject arguments = new JSONObject();
        try {
            arguments.put("query", stableId);
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
        return new CommandPaletteFilter.Entry(LauncherToolRegistry.TOOL_APP_LAUNCH, title, "",
            LauncherToolRegistry.CATEGORY_APPS, Collections.<String>emptyList(), true, null, false,
            LauncherToolRegistry.ToolRisk.MEDIUM, arguments, null, null, stableId);
    }

    private static CommandPaletteFilter.Entry contactRow(String lookupKey, String name) {
        JSONObject arguments = new JSONObject();
        try {
            arguments.put("lookup_key", lookupKey);
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
        return new CommandPaletteFilter.Entry(LauncherToolRegistry.TOOL_CONTACT_OPEN, name, "",
            TerminalCommandPalette.CATEGORY_CONTACTS, Collections.<String>emptyList(), true, null,
            false, LauncherToolRegistry.ToolRisk.LOW, arguments);
    }

    private static CommandPaletteRanking.Candidate candidate(CommandPaletteFilter.Entry entry,
                                                             CommandPaletteRanking.Source source,
                                                             int score) {
        return new CommandPaletteRanking.Candidate(entry, source, score);
    }

    private static LauncherAppEntry app(String packageName, String label) {
        return new LauncherAppEntry(new AppRef(packageName, packageName + ".MainActivity"),
            label, null);
    }

    private static List<String> titles(List<CommandPaletteFilter.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (CommandPaletteFilter.Entry entry : entries) out.add(entry.title);
        return out;
    }

    private static Map<String, Double> usage(Object... keyThenScore) {
        Map<String, Double> out = new HashMap<>();
        for (int i = 0; i < keyThenScore.length; i += 2)
            out.put((String) keyThenScore[i], ((Number) keyThenScore[i + 1]).doubleValue());
        return out;
    }

    // ------------------------------------------------------------------ match levels

    /**
     * The bug this replaces: apps were appended after every action match, so typing an app's exact
     * name still listed a loose subsequence hit on an action above it.
     */
    @Test
    public void exactAppMatch_beatsFuzzyActionMatch() {
        LauncherAppEntry termux = app("com.termux", "Termux");
        CommandPaletteFilter.Entry fuzzyAction = action("pane.multiplex", "Terminal multiplexer");
        assertEquals("the action only matches as a subsequence",
            CommandPaletteFilter.SCORE_TITLE_SUBSEQUENCE,
            CommandPaletteFilter.score(fuzzyAction, "termux"));

        List<CommandPaletteRanking.Candidate> candidates =
            new ArrayList<>(CommandPaletteRanking.actionCandidates(
                Collections.singletonList(fuzzyAction), "termux"));
        // Actions are gathered first, the way the controller gathers them.
        candidates.add(candidate(appRow(termux.appRef.stableId(), termux.label),
            CommandPaletteRanking.Source.APP, CommandPaletteAppMatch.score(termux, "termux")));

        assertEquals(Arrays.asList("Termux", "Terminal multiplexer"), titles(
            CommandPaletteRanking.rank(candidates, NO_USAGE, NO_USAGE, NO_MEMORY)));
    }

    @Test
    public void appTiers_landOnThePaletteScale() {
        LauncherAppEntry maps = app("com.example.maps", "Google Maps");
        assertEquals(100, CommandPaletteAppMatch.score(maps, "com.example.maps"));
        assertEquals(100, CommandPaletteAppMatch.score(maps, "google maps"));
        assertEquals(100, CommandPaletteAppMatch.score(maps, "google-maps"));
        assertEquals(90, CommandPaletteAppMatch.score(maps, "goo"));
        assertEquals(80, CommandPaletteAppMatch.score(maps, "map"));
        assertEquals(70, CommandPaletteAppMatch.score(maps, "ogle"));
        assertEquals(60, CommandPaletteAppMatch.score(maps, "example"));
    }

    /** The typo pass: a subsequence of the label is as good as an action's; anything looser is not. */
    @Test
    public void appTypoMatches_rankWithSubsequencesOrJustBelowIdMatches() {
        LauncherAppEntry whatsapp = app("com.whatsapp", "WhatsApp");
        List<LauncherAppEntry> all = Collections.singletonList(whatsapp);
        // Both are admitted by the launcher's own typo-tolerant search.
        assertEquals(1, LauncherRankingEngine.filterAndRank(all, "watsapp", 70).size());
        assertEquals(1, LauncherRankingEngine.filterAndRank(all, "whatsepp", 70).size());

        assertEquals(CommandPaletteFilter.SCORE_TITLE_SUBSEQUENCE,
            CommandPaletteAppMatch.score(whatsapp, "watsapp"));
        int typo = CommandPaletteAppMatch.score(whatsapp, "whatsepp");
        assertTrue(typo < CommandPaletteFilter.SCORE_ID_SUBSTRING);
        assertTrue(typo > CommandPaletteFilter.SCORE_CATEGORY_MATCH);
    }

    // ------------------------------------------------------------------ usage

    @Test
    public void usage_reordersWithinALevelButNeverAcrossLevels() {
        CommandPaletteFilter.Entry exact = action("pane.split", "Split");
        CommandPaletteFilter.Entry rarely = action("pane.split_vertical", "Split vertically");
        CommandPaletteFilter.Entry often = action("pane.split_horizontal", "Split horizontally");
        List<CommandPaletteRanking.Candidate> candidates =
            CommandPaletteRanking.actionCandidates(Arrays.asList(exact, rarely, often), "split");

        Map<String, Double> rowUsage = usage(
            "pane.split_vertical", 1.0,
            "pane.split_horizontal", 9.0);
        assertEquals(Arrays.asList("Split", "Split horizontally", "Split vertically"),
            titles(CommandPaletteRanking.rank(candidates, NO_USAGE, rowUsage, NO_MEMORY)));

        // Without usage the shorter title leads among equals.
        assertEquals(Arrays.asList("Split", "Split vertically", "Split horizontally"),
            titles(CommandPaletteRanking.rank(candidates, NO_USAGE, NO_USAGE, NO_MEMORY)));
    }

    /** A palette launch lands in both stores, so an app's usage is the larger one, never the sum. */
    @Test
    public void appUsage_takesTheLargerOfLaunchesAndPaletteRuns() {
        CommandPaletteFilter.Entry app = appRow("com.example/.Main", "Notes");
        CommandPaletteFilter.Entry act = action("note.new", "Notes");
        List<CommandPaletteRanking.Candidate> candidates = Arrays.asList(
            candidate(app, CommandPaletteRanking.Source.APP, 100),
            candidate(act, CommandPaletteRanking.Source.ACTION, 100));
        Map<String, Double> appUsage = usage("com.example/.Main", 3.0);
        Map<String, Double> rowUsage = usage(
            CommandPaletteActionStats.keyFor(app), 2.0,
            "note.new", 4.0);

        assertEquals(3.0, CommandPaletteRanking.usage(candidates.get(0), appUsage, rowUsage), 0.0);
        assertEquals(Arrays.asList("note.new", LauncherToolRegistry.TOOL_APP_LAUNCH),
            tools(CommandPaletteRanking.rank(candidates, appUsage, rowUsage, NO_MEMORY)));
    }

    // ------------------------------------------------------------------ no history

    @Test
    public void noHistory_appThenActionThenContact() {
        List<CommandPaletteRanking.Candidate> candidates = Arrays.asList(
            candidate(contactRow("k1", "Sam"), CommandPaletteRanking.Source.CONTACT, 90),
            candidate(action("session.new", "Sam"), CommandPaletteRanking.Source.ACTION, 90),
            candidate(appRow("com.sam/.Main", "Sam"), CommandPaletteRanking.Source.APP, 90));

        assertEquals(Arrays.asList(LauncherToolRegistry.TOOL_APP_LAUNCH, "session.new",
                LauncherToolRegistry.TOOL_CONTACT_OPEN),
            tools(CommandPaletteRanking.rank(candidates, NO_USAGE, NO_USAGE, NO_MEMORY)));
    }

    @Test
    public void contactMatches_carryTheirLevel() {
        CommandPaletteContacts.Contact sam = new CommandPaletteContacts.Contact("Sam Rivera", 1, "k1");
        sam.addNumber("+1 555 010 9999");
        List<CommandPaletteContacts.Contact> contacts = Collections.singletonList(sam);
        assertEquals(CommandPaletteFilter.SCORE_TITLE_PREFIX,
            CommandPaletteContacts.filter(contacts, "sam").get(0).score);
        assertEquals(CommandPaletteFilter.SCORE_ID_SUBSTRING,
            CommandPaletteContacts.filter(contacts, "0109999").get(0).score);
    }

    // ------------------------------------------------------------------ query memory

    @Test
    public void queryMemory_putsThePickFirstAboveEveryLevel() {
        CommandPaletteFilter.Entry exact = action("terminal.reset", "Term");
        CommandPaletteFilter.Entry app = appRow("com.termux/.Main", "Termux");
        List<CommandPaletteRanking.Candidate> candidates = Arrays.asList(
            candidate(exact, CommandPaletteRanking.Source.ACTION, 100),
            candidate(app, CommandPaletteRanking.Source.APP, 90));

        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        memory.record("Term ", CommandPaletteActionStats.keyFor(app), 1_000L);

        assertEquals(Arrays.asList("Termux", "Term"), titles(CommandPaletteRanking.rank(
            candidates, NO_USAGE, NO_USAGE, memory.picksFor("term", NOW))));
        // Normalized: case, spaces and the actions-only marker do not split the memory.
        assertEquals(memory.picksFor("term", NOW), memory.picksFor("  >TERM ", NOW));
        // Another query has nothing to say.
        assertTrue(memory.picksFor("ter", NOW).isEmpty());
    }

    /**
     * The phone's own book for "s": Shizuku picked six times, Solid Explorer four, Steam three and
     * most recently. The habit leads, not the last pick.
     */
    @Test
    public void queryMemory_pinsTheMostPickedRowNotTheLatest() {
        CommandPaletteFilter.Entry shizuku = appRow("moe.shizuku.privileged.api/.Main", "Shizuku");
        CommandPaletteFilter.Entry solid = appRow("pl.solidexplorer2/.Main", "Solid Explorer");
        CommandPaletteFilter.Entry steam = appRow("com.valvesoftware.android.steam/.Main", "Steam");
        List<CommandPaletteRanking.Candidate> candidates = Arrays.asList(
            candidate(solid, CommandPaletteRanking.Source.APP, 90),
            candidate(steam, CommandPaletteRanking.Source.APP, 90),
            candidate(shizuku, CommandPaletteRanking.Source.APP, 90));

        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        for (int i = 0; i < 6; i++)
            memory.record("s", CommandPaletteActionStats.keyFor(shizuku), NOW - 6 * DAY_MS + i);
        for (int i = 0; i < 4; i++)
            memory.record("s", CommandPaletteActionStats.keyFor(solid), NOW - 4 * DAY_MS + i);
        for (int i = 0; i < 3; i++)
            memory.record("S", CommandPaletteActionStats.keyFor(steam), NOW - DAY_MS + i);

        List<CommandPaletteFilter.Entry> ranked = CommandPaletteRanking.rank(
            candidates, NO_USAGE, NO_USAGE, memory.picksFor("s", NOW));
        assertEquals("Shizuku", ranked.get(0).title);
        // Only one row is pinned; the others keep the order they would have had anyway.
        assertEquals(Arrays.asList("Shizuku", "Steam", "Solid Explorer"), titles(ranked));
    }

    /** Picks decay like usage: a habit left for months yields to what is picked now. */
    @Test
    public void queryMemory_oldPicksDecay() {
        CommandPaletteFilter.Entry vertical = action("pane.split_vertical", "Split vertically");
        CommandPaletteFilter.Entry horizontal = action("pane.split_horizontal", "Split horizontally");
        List<CommandPaletteRanking.Candidate> candidates =
            CommandPaletteRanking.actionCandidates(Arrays.asList(vertical, horizontal), "split");

        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        for (int i = 0; i < 5; i++)
            memory.record("split", "pane.split_horizontal", NOW - 90 * DAY_MS + i);
        memory.record("split", "pane.split_vertical", NOW - DAY_MS);
        assertEquals("Split vertically", CommandPaletteRanking.rank(
            candidates, NO_USAGE, NO_USAGE, memory.picksFor("split", NOW)).get(0).title);
    }

    @Test
    public void queryMemory_recencyBreaksATie() {
        CommandPaletteFilter.Entry vertical = action("pane.split_vertical", "Split vertically");
        CommandPaletteFilter.Entry horizontal = action("pane.split_horizontal", "Split horizontally");
        List<CommandPaletteRanking.Candidate> candidates =
            CommandPaletteRanking.actionCandidates(Arrays.asList(vertical, horizontal), "split");

        // Picked once each, both in the future of NOW, so neither has decayed at all.
        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        memory.record("split", "pane.split_horizontal", NOW + 1_000L);
        memory.record("split", "pane.split_vertical", NOW + 2_000L);
        assertEquals("Split vertically", CommandPaletteRanking.rank(
            candidates, NO_USAGE, NO_USAGE, memory.picksFor("split", NOW)).get(0).title);

        // More picks outweigh any recency: three against two.
        memory.record("split", "pane.split_horizontal", NOW + 3_000L);
        memory.record("split", "pane.split_vertical", NOW + 4_000L);
        memory.record("split", "pane.split_horizontal", NOW + 5_000L);
        assertEquals("Split horizontally", CommandPaletteRanking.rank(
            candidates, NO_USAGE, NO_USAGE, memory.picksFor("split", NOW)).get(0).title);
    }

    /** A disabled row is never the one pinned, however often it was picked. */
    @Test
    public void queryMemory_neverPinsADisabledRow() {
        CommandPaletteFilter.Entry enabled = action("pane.split_vertical", "Split vertically");
        CommandPaletteFilter.Entry disabled = new CommandPaletteFilter.Entry(
            "pane.split_horizontal", "Split horizontally", "", "pane",
            Collections.<String>emptyList(), false, "No pane", false,
            LauncherToolRegistry.ToolRisk.LOW);
        List<CommandPaletteRanking.Candidate> candidates =
            CommandPaletteRanking.actionCandidates(Arrays.asList(disabled, enabled), "split");

        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        for (int i = 0; i < 5; i++) memory.record("split", "pane.split_horizontal", NOW + i);
        memory.record("split", "pane.split_vertical", NOW);
        assertEquals("Split vertically", CommandPaletteRanking.rank(
            candidates, NO_USAGE, NO_USAGE, memory.picksFor("split", NOW)).get(0).title);
    }

    @Test
    public void queryMemory_ignoresAPickThatIsNotInTheResults() {
        CommandPaletteFilter.Entry split = action("pane.split", "Split");
        List<CommandPaletteRanking.Candidate> candidates = Collections.singletonList(
            candidate(split, CommandPaletteRanking.Source.ACTION, 100));
        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        memory.record("split", "app.launch {\"query\":\"gone\"}", 1_000L);
        assertEquals(Collections.singletonList("Split"), titles(CommandPaletteRanking.rank(
            candidates, NO_USAGE, NO_USAGE, memory.picksFor("split", NOW))));
    }

    @Test
    public void queryMemory_skipsEmptyQueries() {
        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        assertFalse(memory.record("   ", "pane.split", 1L));
        assertFalse(memory.record(">", "pane.split", 1L));
        assertEquals(0, memory.size());
    }

    @Test
    public void queryMemory_forgetsTheLeastRecentlyPickedQuery() {
        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        for (int i = 0; i < CommandPaletteQueryMemory.MAX_QUERIES; i++)
            memory.record("q" + i, "row", i);
        // Picking q0 again makes q1 the least recent.
        memory.record("q0", "row", 10_000L);
        memory.record("fresh", "row", 10_001L);

        assertEquals(CommandPaletteQueryMemory.MAX_QUERIES, memory.size());
        assertFalse(memory.picksFor("q0", NOW).isEmpty());
        assertTrue(memory.picksFor("q1", NOW).isEmpty());
        assertFalse(memory.picksFor("fresh", NOW).isEmpty());
    }

    @Test
    public void queryMemory_survivesJsonWithItsRecency() {
        CommandPaletteQueryMemory memory = new CommandPaletteQueryMemory();
        for (int i = 0; i < CommandPaletteQueryMemory.MAX_QUERIES; i++)
            memory.record("q" + i, "row", 100L + i);
        memory.record("q0", "row", 10_000L);

        CommandPaletteQueryMemory read = CommandPaletteQueryMemory.fromJson(memory.toJson());
        assertEquals(memory.picksFor("q0", NOW), read.picksFor("q0", NOW));
        // Recency rebuilt from the stored times: q1 is still the first to go.
        read.record("fresh", "row", 10_001L);
        assertTrue(read.picksFor("q1", NOW).isEmpty());
        assertFalse(read.picksFor("q0", NOW).isEmpty());

        assertEquals(0, CommandPaletteQueryMemory.fromJson("not json").size());
        assertEquals(0, CommandPaletteQueryMemory.fromJson(null).size());
    }

    // ------------------------------------------------------------------ the > prefix

    @Test
    public void actionsOnlyPrefix_isReadFromTheFirstNonSpaceCharacter() {
        CommandPaletteRanking.Query query = CommandPaletteRanking.Query.parse("  >  split ");
        assertTrue(query.actionsOnly);
        assertEquals("split", query.text);

        CommandPaletteRanking.Query plain = CommandPaletteRanking.Query.parse("a>b");
        assertFalse(plain.actionsOnly);
        assertEquals("a>b", plain.text);
        assertFalse(CommandPaletteRanking.Query.parse("split").actionsOnly);
    }

    /** ">" alone is a browse, not a search: no text to match, every action listed. */
    @Test
    public void bareActionsOnlyPrefix_browsesEveryAction() {
        CommandPaletteRanking.Query query = CommandPaletteRanking.Query.parse(">");
        assertTrue(query.actionsOnly);
        assertEquals("", query.text);
        assertTrue(CommandPaletteRanking.actionCandidates(
            Collections.singletonList(action("pane.split", "Split")), query.text).isEmpty());
    }

    @Test
    public void actionsOnlyPrefix_matchesTheTextAfterIt() {
        CommandPaletteRanking.Query query = CommandPaletteRanking.Query.parse(">split");
        List<CommandPaletteRanking.Candidate> candidates = CommandPaletteRanking.actionCandidates(
            Arrays.asList(action("pane.split", "Split"), action("session.new", "New session")),
            query.text);
        assertEquals(1, candidates.size());
        assertEquals(CommandPaletteFilter.SCORE_EXACT_TITLE, candidates.get(0).score);
    }

    private static List<String> tools(List<CommandPaletteFilter.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (CommandPaletteFilter.Entry entry : entries) out.add(entry.toolName);
        return out;
    }
}
