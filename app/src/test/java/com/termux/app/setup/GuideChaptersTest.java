package com.termux.app.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class GuideChaptersTest {

    private static List<String> ids(boolean home, boolean mux, boolean desktop) {
        List<String> out = new ArrayList<>();
        for (GuideChapters.Chapter c : GuideChapters.forFacts(home, mux, desktop)) out.add(c.id);
        return out;
    }

    @Test public void everythingOn_keepsAllNineInOrder() {
        assertEquals(Arrays.asList("pages", "apps", "dock", "keyboard", "status", "palette",
            "help", "panes", "desktop"), ids(true, true, true));
    }

    @Test public void notHome_dropsHomeChapters() {
        assertEquals(Arrays.asList("keyboard", "status", "palette", "help"),
            ids(false, false, false));
    }

    @Test public void multiplexerAndDesktopAreIndependent() {
        assertTrue(ids(false, true, false).contains("panes"));
        assertFalse(ids(false, true, false).contains("desktop"));
        assertTrue(ids(false, false, true).contains("desktop"));
        assertFalse(ids(false, false, true).contains("panes"));
    }

    @Test public void allWatched_needsEveryListedChapter() {
        List<GuideChapters.Chapter> list = GuideChapters.forFacts(false, false, false);
        assertFalse(GuideChapters.allWatched(list, new HashSet<>(Arrays.asList("keyboard"))));
        assertTrue(GuideChapters.allWatched(list,
            new HashSet<>(Arrays.asList("keyboard", "status", "palette", "help", "pages"))));
        assertFalse(GuideChapters.allWatched(new ArrayList<>(), new HashSet<>()));
    }
}
