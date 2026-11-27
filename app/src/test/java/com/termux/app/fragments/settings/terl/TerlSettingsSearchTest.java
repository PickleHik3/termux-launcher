package com.termux.app.fragments.settings.terl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Terl's search ranks as v1's does, and Terl's own rows come before rows of v1's pages. */
public class TerlSettingsSearchTest {

    private static TerlSettingsSearch.Entry setting(String title, String words, String id) {
        return new TerlSettingsSearch.Entry(TerlSettingsSearch.Kind.SETTING, title, words,
            "Workspaces & windows", TerlSettingsTree.Group.WORKSPACES, id, null, null);
    }

    private static TerlSettingsSearch.Entry v1Row(String title, String key) {
        return new TerlSettingsSearch.Entry(TerlSettingsSearch.Kind.PAGE_ROW, title, null,
            "Workspaces & windows › Clipboard", TerlSettingsTree.Group.WORKSPACES, null,
            "com.termux.app.fragments.settings.termux.TerminalClipboardPreferencesFragment", key);
    }

    private final TerlSettingsSearch search = new TerlSettingsSearch(Arrays.asList(
        setting("Scrollback", "How many lines a terminal keeps for scrolling back. I can't scroll back far enough",
            "terminal.scrollback"),
        setting("Bell", "What a terminal does when a program rings its bell. stop the terminal beeping",
            "terminal.bell"),
        v1Row("Terminal engine", "terminal_engine"),
        setting("Terminal core", "Which engine new terminals run on.", "terminal.core"),
        new TerlSettingsSearch.Entry(TerlSettingsSearch.Kind.GROUP, "Workspaces & windows",
            "Scrollback, bell, clipboard and terminal core", "Settings",
            TerlSettingsTree.Group.WORKSPACES, null, null, null)));

    private List<String> titles(String query) {
        List<String> titles = new ArrayList<>();
        for (TerlSettingsSearch.Entry entry : search.search(query, 10)) titles.add(entry.title);
        return titles;
    }

    @Test
    public void aTitleThatIsTheQueryComesFirst() {
        assertEquals("Bell", titles("bell").get(0));
    }

    @Test
    public void theWordsPeopleUseFindASetting() {
        assertEquals(Arrays.asList("Bell"), titles("beeping"));
        assertEquals(Arrays.asList("Scrollback"), titles("scroll far"));
    }

    @Test
    public void terlsOwnRowsComeBeforeV1PageRowsOfTheSameRank() {
        assertEquals(Arrays.asList("Terminal core", "Terminal engine"), titles("terminal").subList(0, 2));
    }

    @Test
    public void anEmptyOrUnmatchedQueryFindsNothing() {
        assertTrue(titles("  ").isEmpty());
        assertTrue(titles("page transition").isEmpty());
    }

    @Test
    public void theLimitHolds() {
        assertEquals(1, search.search("e", 1).size());
    }
}
