package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.overview.OverviewGoTo.Entry;
import com.termux.app.overview.OverviewGoTo.Match;

import com.termux.app.page.PageKind;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OverviewGoToTest {

    private static final List<Entry> WALL = Arrays.asList(
        new Entry(0, PageKind.HOME, "Home 1", null),
        new Entry(1, PageKind.HOME, "Home 2", null),
        new Entry(2, PageKind.HOME, "Terminal 1", "nvim"),
        new Entry(3, PageKind.HOME, "Build", "gradle"),
        new Entry(4, PageKind.HOME, "Assistant", null));

    private static List<Integer> cards(List<Match> matches) {
        List<Integer> out = new ArrayList<>();
        for (Match m : matches) out.add(m.entry.card);
        return out;
    }

    @Test public void aBlankQueryMatchesNothingSoTheStripStays() {
        assertTrue(OverviewGoTo.match(WALL, "").isEmpty());
        assertTrue(OverviewGoTo.match(WALL, "   ").isEmpty());
        assertTrue(OverviewGoTo.match(WALL, null).isEmpty());
    }

    @Test public void pagesMatchByNameIgnoringCaseAndSpaces() {
        assertEquals(Arrays.asList(0, 1), cards(OverviewGoTo.match(WALL, "  HOME ")));
        assertEquals(Collections.singletonList(1), cards(OverviewGoTo.match(WALL, "home 2")));
    }

    @Test public void aWorkspaceAlsoMatchesByItsCommand() {
        List<Match> m = OverviewGoTo.match(WALL, "nvim");
        assertEquals(Collections.singletonList(2), cards(m));
        assertTrue(m.get(0).viaCommand);
        assertFalse(OverviewGoTo.match(WALL, "home").get(0).viaCommand);
    }

    @Test public void aNameMatchComesBeforeACommandMatch() {
        List<Entry> entries = Arrays.asList(
            new Entry(0, PageKind.HOME, "Notes", "build"),
            new Entry(1, PageKind.HOME, "Build", null));
        assertEquals(Arrays.asList(1, 0), cards(OverviewGoTo.match(entries, "build")));
    }

    @Test public void exactBeatsPrefixBeatsWordStartBeatsAnywhere() {
        List<Entry> entries = Arrays.asList(
            new Entry(0, PageKind.HOME, "My dev box", null),
            new Entry(1, PageKind.HOME, "Redevelop", null),
            new Entry(2, PageKind.HOME, "Dev tools", null),
            new Entry(3, PageKind.HOME, "Dev", null));
        assertEquals(Arrays.asList(3, 2, 0, 1), cards(OverviewGoTo.match(entries, "dev")));
    }

    @Test public void equalMatchesKeepWallOrder() {
        assertEquals(Arrays.asList(0, 1), cards(OverviewGoTo.match(WALL, "ho")));
    }

    @Test public void everyWordOfALongerQueryMustBeFound() {
        assertEquals(Collections.singletonList(3), cards(OverviewGoTo.match(WALL, "gradle build")));
        assertTrue(OverviewGoTo.match(WALL, "gradle nothing").isEmpty());
    }

    @Test public void nothingMatchingIsEmpty() {
        assertTrue(OverviewGoTo.match(WALL, "zzz").isEmpty());
        assertTrue(OverviewGoTo.match(Collections.<Entry>emptyList(), "home").isEmpty());
    }

    @Test public void anEmptyCommandIsNoCommand() {
        assertEquals(null, new Entry(0, PageKind.HOME, "A", "  ").command);
    }

    @Test public void sessionsMatchByNameAndGoToTheirPlace() {
        List<Entry> entries = new ArrayList<>(WALL);
        // A session waiting in the place of the Workspace card 2, second in its row.
        entries.add(new Entry(2, PageKind.TERMINAL, "logs", null, 1));
        List<Match> matches = OverviewGoTo.match(entries, "log");
        assertEquals(1, matches.size());
        assertEquals(2, matches.get(0).entry.card);
        assertEquals(1, matches.get(0).entry.session);
        assertEquals(-1, WALL.get(2).session);
    }
}
