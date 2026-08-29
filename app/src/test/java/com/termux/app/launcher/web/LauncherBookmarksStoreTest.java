package com.termux.app.launcher.web;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The bookmarks file is hand-written, so parsing it is forgiving by design: what this pins down
 * is that a bad line costs the user only that line.
 */
public class LauncherBookmarksStoreTest {

    @Test
    public void readsNameAndAddress() {
        List<LauncherBookmarksStore.Bookmark> parsed = LauncherBookmarksStore.parse(
            "github\thttps://github.com\n"
                + "Nix packages    https://search.nixos.org/packages\n"
                + "hn https://news.ycombinator.com\n");
        assertEquals(3, parsed.size());
        assertEquals("github", parsed.get(0).name);
        assertEquals("https://github.com", parsed.get(0).url);
        assertEquals("Nix packages", parsed.get(1).name);
        assertEquals("https://search.nixos.org/packages", parsed.get(1).url);
        assertEquals("hn", parsed.get(2).name);
    }

    @Test
    public void anAddressAloneIsNamedAfterItsHost() {
        List<LauncherBookmarksStore.Bookmark> parsed =
            LauncherBookmarksStore.parse("news.ycombinator.com\n");
        assertEquals(1, parsed.size());
        assertEquals("news.ycombinator.com", parsed.get(0).name);
        assertEquals("https://news.ycombinator.com", parsed.get(0).url);
    }

    @Test
    public void commentsAndBlankLinesAreSkipped() {
        List<LauncherBookmarksStore.Bookmark> parsed = LauncherBookmarksStore.parse(
            "# a comment\n\n   \n# name\thttps://commented.example\nreal\thttps://real.example\n");
        assertEquals(1, parsed.size());
        assertEquals("real", parsed.get(0).name);
    }

    @Test
    public void badLinesCostOnlyThemselves() {
        List<LauncherBookmarksStore.Bookmark> parsed = LauncherBookmarksStore.parse(
            "broken\tnot an address\n"
                + "scheme\tftp://example.com/pub\n"
                + "good\thttps://example.com\n");
        assertEquals(1, parsed.size());
        assertEquals("good", parsed.get(0).name);
    }

    @Test
    public void theSameAddressIsKeptOnce() {
        List<LauncherBookmarksStore.Bookmark> parsed = LauncherBookmarksStore.parse(
            "first\thttps://example.com\nsecond\tHTTPS://example.com\n");
        assertEquals(1, parsed.size());
        assertEquals("first", parsed.get(0).name);
    }

    @Test
    public void theShippedExampleAddsNothing() {
        // Every line of the seeded file is a comment, so seeding it changes nothing for a user
        // who never edits it — the same contract the bindings and fonts examples hold to.
        assertTrue(LauncherBookmarksStore.parse(
            "# ~/.termux/bookmarks.txt\n# name\thttps://example.com\n").isEmpty());
    }

    @Test
    public void tooManyLinesAreBounded() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < LauncherBookmarksStore.MAX_BOOKMARKS + 50; i++) {
            text.append("name").append(i).append("\thttps://example").append(i).append(".com\n");
        }
        assertEquals(LauncherBookmarksStore.MAX_BOOKMARKS,
            LauncherBookmarksStore.parse(text.toString()).size());
    }
}
