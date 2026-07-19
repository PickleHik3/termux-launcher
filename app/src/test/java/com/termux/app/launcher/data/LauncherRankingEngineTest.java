package com.termux.app.launcher.data;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LauncherRankingEngineTest {

    @Test
    public void filterAndRank_prefersExactPackageQueries() {
        List<LauncherAppEntry> entries = Arrays.asList(
            entry("com.termux", "com.termux.app.TermuxActivity", "Termux"),
            entry("com.termux.api", "com.termux.api.MainActivity", "Termux API")
        );

        List<LauncherAppEntry> ranked = LauncherRankingEngine.filterAndRank(entries, "com.termux.api", 70);

        assertEquals(2, ranked.size());
        assertEquals("com.termux.api", ranked.get(0).appRef.packageName);
    }

    @Test
    public void filterAndRank_matchesNormalizedPunctuationInLabels() {
        List<LauncherAppEntry> entries = Arrays.asList(
            entry("com.termux.api", "com.termux.api.MainActivity", "Termux:API"),
            entry("com.example.notes", "com.example.notes.MainActivity", "Notes")
        );

        List<LauncherAppEntry> ranked = LauncherRankingEngine.filterAndRank(entries, "termux api", 70);

        assertEquals(1, ranked.size());
        assertEquals("com.termux.api", ranked.get(0).appRef.packageName);
    }

    @Test
    public void filterAndRank_matchesPackageFragments() {
        List<LauncherAppEntry> entries = Arrays.asList(
            entry("com.example.devtools", "com.example.devtools.MainActivity", "Tools"),
            entry("com.example.music", "com.example.music.MainActivity", "Music")
        );

        List<LauncherAppEntry> ranked = LauncherRankingEngine.filterAndRank(entries, "devtools", 70);

        assertEquals(1, ranked.size());
        assertEquals("com.example.devtools", ranked.get(0).appRef.packageName);
    }

    @Test
    public void filterAndRank_matchesTyposAfterExactMatches() {
        List<LauncherAppEntry> entries = Arrays.asList(
            entry("com.example.whatsapp", "com.example.whatsapp.MainActivity", "WhatsApp"),
            entry("com.example.what", "com.example.what.MainActivity", "Whtsapp Notes")
        );

        List<LauncherAppEntry> ranked = LauncherRankingEngine.filterAndRank(entries, "whtsapp", 70);

        assertEquals(2, ranked.size());
        assertEquals("com.example.what", ranked.get(0).appRef.packageName);
        assertEquals("com.example.whatsapp", ranked.get(1).appRef.packageName);
    }

    @Test
    public void filterAndRank_rejectsWeakFuzzyMatches() {
        List<LauncherAppEntry> entries = Arrays.asList(
            entry("com.example.calendar", "com.example.calendar.MainActivity", "Calendar"),
            entry("com.example.camera", "com.example.camera.MainActivity", "Camera")
        );

        List<LauncherAppEntry> ranked = LauncherRankingEngine.filterAndRank(entries, "music", 70);

        assertTrue(ranked.isEmpty());
    }

    @Test
    public void filterAndRank_ordersEveryMatchTierBeforeFuzzyResults() {
        List<LauncherAppEntry> entries = Arrays.asList(
            entry("plain.seven", "plain.seven.Main", "Tarm"),
            entry("plain.six", "plain.six.Main", "Midterm"),
            entry("prefix.three", "prefix.three.Main", "Term Notes"),
            entry("term", "exact.package.Main", "Package Exact"),
            entry("plain.one", "plain.one.Main", "Term"),
            entry("term.prefix", "term.prefix.Main", "Package Prefix"),
            entry("plain.four", "plain.four.Main", "My Termometer"),
            entry("xtermx", "xtermx.Main", "Package Contains")
        );

        List<LauncherAppEntry> ranked = LauncherRankingEngine.filterAndRank(entries, "term", 70);

        assertEquals(8, ranked.size());
        assertEquals("term", ranked.get(0).appRef.packageName);
        assertEquals("plain.one", ranked.get(1).appRef.packageName);
        assertEquals("term.prefix", ranked.get(2).appRef.packageName);
        assertEquals("prefix.three", ranked.get(3).appRef.packageName);
        assertEquals("plain.four", ranked.get(4).appRef.packageName);
        assertEquals("xtermx", ranked.get(5).appRef.packageName);
        assertEquals("plain.six", ranked.get(6).appRef.packageName);
        assertEquals("plain.seven", ranked.get(7).appRef.packageName);
    }

    @Test
    public void similarity_scoresExactSubstringsAndSingleEdits() {
        assertEquals(100, LauncherRankingEngine.similarity("termux", "my termux app"));
        assertEquals(88, LauncherRankingEngine.similarity("whtsapp", "whatsapp"));
        assertEquals(0, LauncherRankingEngine.similarity("abc", "xyz"));
    }

    @Test
    public void normalizeLookupValue_collapsesPunctuationToSpaces() {
        assertEquals("termux api", LauncherRankingEngine.normalizeLookupValue("Termux:API"));
        assertEquals("foo bar baz", LauncherRankingEngine.normalizeLookupValue(" Foo__Bar/Baz "));
        assertTrue(LauncherRankingEngine.normalizeLookupValue(null).isEmpty());
    }

    private static LauncherAppEntry entry(String packageName, String activityName, String label) {
        return new LauncherAppEntry(new AppRef(packageName, activityName), label, null);
    }
}
