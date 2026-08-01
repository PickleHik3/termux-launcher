package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TopPaneModelsTest {

    private static PinnedNotification pin(String key, String sender, String app, String body) {
        return new PinnedNotification(key, "com.whatsapp", sender, app, body, "rule", false, 1L);
    }

    @Test
    public void pinnedTitleCollapsesMissingHalves() {
        assertEquals("Amma · WhatsApp", pin("k", "Amma", "WhatsApp", "hi").title());
        assertEquals("WhatsApp", pin("k", null, "WhatsApp", "hi").title());
        assertEquals("Amma", pin("k", "Amma", null, "hi").title());
        assertEquals("WhatsApp", pin("k", "WhatsApp", "WhatsApp", "hi").title());
        assertEquals("Amma", pin("k", "Amma", "WhatsApp", "hi").senderOrApp());
        assertEquals("WhatsApp", pin("k", "", "WhatsApp", "hi").senderOrApp());
    }

    @Test
    public void pinnedContentComparisonDrivesRedraws() {
        assertTrue(pin("k", "Amma", "WhatsApp", "hi").sameContentAs(pin("k", "Amma", "WhatsApp", "hi")));
        assertFalse(pin("k", "Amma", "WhatsApp", "hi").sameContentAs(pin("k", "Amma", "WhatsApp", "bye")));
        assertFalse(pin("k", "Amma", "WhatsApp", "hi").sameContentAs(pin("j", "Amma", "WhatsApp", "hi")));
        assertFalse(pin("k", "Amma", "WhatsApp", "hi").sameContentAs(null));
    }

    @Test
    public void mediaLabelsCollapseMissingHalves() {
        TopPaneMediaState full = new TopPaneMediaState("com.music", "Weightless",
            "Marconi Union", "YouTube Music", null, 30_000L, 100_000L, true);
        assertEquals("Marconi Union · YouTube Music", full.subtitle());
        assertEquals("Weightless — Marconi Union", full.stripLabel());

        TopPaneMediaState noArtist = new TopPaneMediaState("com.music", "Weightless", null,
            "YouTube Music", null, 0L, 0L, false);
        assertEquals("YouTube Music", noArtist.subtitle());
        assertEquals("Weightless", noArtist.stripLabel());
    }

    @Test
    public void mediaProgressStaysInRangeWithoutADuration() {
        assertEquals(0f, new TopPaneMediaState("p", "t", "a", "l", null, 5L, 0L, true).progress(), 0f);
        assertEquals(.3f, new TopPaneMediaState("p", "t", "a", "l", null, 30L, 100L, true).progress(), .001f);
        assertEquals(1f, new TopPaneMediaState("p", "t", "a", "l", null, 500L, 100L, true).progress(), 0f);
        assertEquals(0f, new TopPaneMediaState("p", "t", "a", "l", null, -5L, 100L, true).progress(), 0f);
    }

    @Test
    public void optimisticPlayFlipKeepsEverythingElse() {
        TopPaneMediaState playing = new TopPaneMediaState("com.music", "Weightless",
            "Marconi Union", "YouTube Music", null, 30L, 100L, true);
        TopPaneMediaState paused = playing.withPlaying(false);
        assertFalse(paused.playing);
        assertEquals(playing.title, paused.title);
        assertEquals(playing.positionMs, paused.positionMs);
    }
}
