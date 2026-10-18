package com.termux.app.terminal.inappkeyboard;

import static com.termux.app.terminal.inappkeyboard.PasteOffer.CLIPBOARD;
import static com.termux.app.terminal.inappkeyboard.PasteOffer.CLIPBOARD_PICTURE;
import static com.termux.app.terminal.inappkeyboard.PasteOffer.FRESH_MILLIS;
import static com.termux.app.terminal.inappkeyboard.PasteOffer.NONE_CONSUMED;
import static com.termux.app.terminal.inappkeyboard.PasteOffer.SCREENSHOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasteOfferTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final RecentScreenshot.Shot SHOT =
        new RecentScreenshot.Shot(42, "image/png", NOW - 5_000);

    @Test
    public void aScreenshotTakenAfterTheClipWins() {
        assertEquals(SCREENSHOT, PasteOffer.decide(NOW - 60_000, false, SHOT, NONE_CONSUMED, NOW));
        assertEquals("over a picture on the clipboard too",
            SCREENSHOT, PasteOffer.decide(NOW - 60_000, true, SHOT, NONE_CONSUMED, NOW));
        assertEquals("an empty clipboard is older than any screenshot",
            SCREENSHOT, PasteOffer.decide(0, false, SHOT, NONE_CONSUMED, NOW));
    }

    @Test
    public void aClipSetAfterTheScreenshotWins() {
        assertEquals(CLIPBOARD, PasteOffer.decide(NOW - 1_000, false, SHOT, NONE_CONSUMED, NOW));
        assertEquals(CLIPBOARD_PICTURE, PasteOffer.decide(NOW - 1_000, true, SHOT, NONE_CONSUMED, NOW));
        assertEquals("a tie goes to the clipboard",
            CLIPBOARD, PasteOffer.decide(SHOT.takenMillis, false, SHOT, NONE_CONSUMED, NOW));
    }

    @Test
    public void aScreenshotIsOfferedOnlyWhileFresh() {
        long justFresh = SHOT.takenMillis + FRESH_MILLIS - 1;
        assertEquals(SCREENSHOT, PasteOffer.decide(0, false, SHOT, NONE_CONSUMED, justFresh));
        long stale = SHOT.takenMillis + FRESH_MILLIS;
        assertEquals(CLIPBOARD, PasteOffer.decide(0, false, SHOT, NONE_CONSUMED, stale));
        assertEquals(CLIPBOARD_PICTURE, PasteOffer.decide(0, true, SHOT, NONE_CONSUMED, stale));
    }

    @Test
    public void aConsumedScreenshotIsNotOfferedAgainButANewerOneIs() {
        assertEquals(CLIPBOARD, PasteOffer.decide(0, false, SHOT, SHOT.id, NOW));
        RecentScreenshot.Shot newer = new RecentScreenshot.Shot(43, "image/png", NOW - 1_000);
        assertEquals(SCREENSHOT, PasteOffer.decide(0, false, newer, SHOT.id, NOW));
    }

    @Test
    public void noScreenshotIsTheClipboard() {
        assertEquals(CLIPBOARD, PasteOffer.decide(0, false, null, NONE_CONSUMED, NOW));
        assertEquals(CLIPBOARD_PICTURE, PasteOffer.decide(0, true, null, NONE_CONSUMED, NOW));
    }

    @Test
    public void everyOfferButPlainClipboardShowsAPicture() {
        assertFalse(CLIPBOARD.showsPicture());
        assertTrue(CLIPBOARD_PICTURE.showsPicture());
        assertTrue(SCREENSHOT.showsPicture());
    }

    @Test
    public void theOfferEndsWhenTheScreenshotStopsBeingFresh() {
        assertEquals(FRESH_MILLIS - 5_000, PasteOffer.millisUntilStale(SHOT, NOW));
        long stale = SHOT.takenMillis + FRESH_MILLIS;
        assertEquals(0, PasteOffer.millisUntilStale(SHOT, stale));
        assertEquals(CLIPBOARD, PasteOffer.decide(0, false, SHOT, NONE_CONSUMED, stale));
    }
}
