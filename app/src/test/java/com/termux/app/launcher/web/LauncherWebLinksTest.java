package com.termux.app.launcher.web;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Telling an address from a phrase, which is the whole of the palette's web mode: get it wrong
 * one way and a search opens a browser at nothing, wrong the other way and a typed URL is
 * searched for instead of opened.
 */
public class LauncherWebLinksTest {

    @Test
    public void keepsAbsoluteHttpUrls() {
        assertEquals("https://example.com/a?b=c#d",
            LauncherWebLinks.normalizeUrl("https://example.com/a?b=c#d"));
        assertEquals("http://example.com", LauncherWebLinks.normalizeUrl("http://example.com"));
    }

    @Test
    public void promotesBareDomainsToHttps() {
        assertEquals("https://example.com", LauncherWebLinks.normalizeUrl("example.com"));
        assertEquals("https://news.ycombinator.com/newest",
            LauncherWebLinks.normalizeUrl("news.ycombinator.com/newest"));
        assertEquals("https://example.co.uk:8443/x",
            LauncherWebLinks.normalizeUrl("example.co.uk:8443/x"));
    }

    @Test
    public void developmentHostsStayPlainHttp() {
        assertEquals("http://localhost:8080", LauncherWebLinks.normalizeUrl("localhost:8080"));
        assertEquals("http://127.0.0.1:3000/app",
            LauncherWebLinks.normalizeUrl("127.0.0.1:3000/app"));
    }

    @Test
    public void phrasesAreNotAddresses() {
        assertNull(LauncherWebLinks.normalizeUrl("nixos generations"));
        assertNull(LauncherWebLinks.normalizeUrl("what is a monad"));
        assertNull(LauncherWebLinks.normalizeUrl("example"));
        assertNull(LauncherWebLinks.normalizeUrl(""));
        assertNull(LauncherWebLinks.normalizeUrl("   "));
        assertNull(LauncherWebLinks.normalizeUrl(null));
    }

    /** The allow-list is the point: ACTION_VIEW on anything else is not a web page. */
    @Test
    public void refusesEverySchemeButHttp() {
        assertNull(LauncherWebLinks.normalizeUrl("intent://scan/#Intent;scheme=zxing;end"));
        assertNull(LauncherWebLinks.normalizeUrl("content://com.example/secret"));
        assertNull(LauncherWebLinks.normalizeUrl("file:///data/data/com.termux/files"));
        assertNull(LauncherWebLinks.normalizeUrl("javascript:alert(1)"));
        assertNull(LauncherWebLinks.normalizeUrl("https://"));
    }

    @Test
    public void hostIsTheLabel() {
        assertEquals("example.com", LauncherWebLinks.hostOf("https://www.example.com/a/b"));
        assertEquals("search.nixos.org",
            LauncherWebLinks.hostOf("https://search.nixos.org/packages?query=jq"));
        assertEquals("localhost", LauncherWebLinks.hostOf("http://localhost:8080/x"));
        assertNull(LauncherWebLinks.hostOf("not a url"));
        assertEquals("example.com", LauncherWebLinks.labelFor("https://example.com"));
    }

    @Test
    public void looksLikeUrlAgreesWithNormalize() {
        assertTrue(LauncherWebLinks.looksLikeUrl("github.com"));
        assertFalse(LauncherWebLinks.looksLikeUrl("github com"));
    }
}
