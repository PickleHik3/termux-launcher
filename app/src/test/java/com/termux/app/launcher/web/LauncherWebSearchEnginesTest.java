package com.termux.app.launcher.web;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Query to URL, and what a half-written custom template resolves to. */
public class LauncherWebSearchEnginesTest {

    @Test
    public void encodesTheQueryIntoTheTemplate() {
        LauncherWebSearchEngines.Engine engine =
            LauncherWebSearchEngines.resolve("duckduckgo", null);
        assertEquals("DuckDuckGo", engine.label);
        assertEquals("https://duckduckgo.com/?q=nixos+generations",
            LauncherWebSearchEngines.searchUrl(engine, "nixos generations"));
        assertEquals("https://duckduckgo.com/?q=c%2B%2B+lambda",
            LauncherWebSearchEngines.searchUrl(engine, "c++ lambda"));
    }

    @Test
    public void anUnknownIdFallsBackToTheDefault() {
        assertEquals("DuckDuckGo",
            LauncherWebSearchEngines.resolve("altavista", null).label);
        assertEquals("DuckDuckGo", LauncherWebSearchEngines.resolve(null, null).label);
    }

    @Test
    public void aCustomTemplateSearchesAndNamesItselfAfterItsHost() {
        LauncherWebSearchEngines.Engine engine = LauncherWebSearchEngines.resolve(
            LauncherWebSearchEngines.CUSTOM, "https://searx.example/search?q=%s");
        assertEquals("searx.example", engine.label);
        assertEquals("https://searx.example/search?q=jq",
            LauncherWebSearchEngines.searchUrl(engine, "jq"));
    }

    /** A template with no placeholder would search for nothing, which is worse than falling back. */
    @Test
    public void aTemplateWithoutAPlaceholderIsNotUsed() {
        assertEquals("DuckDuckGo", LauncherWebSearchEngines.resolve(
            LauncherWebSearchEngines.CUSTOM, "https://searx.example/").label);
        assertEquals("DuckDuckGo",
            LauncherWebSearchEngines.resolve(LauncherWebSearchEngines.CUSTOM, "").label);
    }

    @Test
    public void anEmptyQuerySearchesNothing() {
        assertNull(LauncherWebSearchEngines.searchUrl(
            LauncherWebSearchEngines.resolve("google", null), "   "));
    }
}
