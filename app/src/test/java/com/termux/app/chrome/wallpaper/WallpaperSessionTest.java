package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperSessionTest {

    @Test
    public void aKillIsRetriedOnTheNextVisibleSession() {
        WallpaperSession s = new WallpaperSession();
        assertFalse(s.retryOnVisibleSession());
        s.onKill();
        assertTrue(s.killed());
        assertTrue(s.retryOnVisibleSession());
        assertFalse(s.killed());
        // Nothing killed any more: a second start has nothing to lift.
        assertFalse(s.retryOnVisibleSession());
        assertEquals(1, s.kills());
    }

    @Test
    public void theThirdKillStaysOnTheStill() {
        WallpaperSession s = new WallpaperSession();
        for (int i = 1; i < WallpaperSession.MAX_KILLS; i++) {
            s.onKill();
            assertTrue(s.retryOnVisibleSession());
        }
        s.onKill();
        assertEquals(WallpaperSession.MAX_KILLS, s.kills());
        assertFalse(s.retryOnVisibleSession());
        assertTrue(s.killed());
        // Still no retry on later sessions.
        assertFalse(s.retryOnVisibleSession());
    }

    @Test
    public void aDifferentBackgroundResetsTheKills() {
        WallpaperSession s = new WallpaperSession();
        for (int i = 0; i < WallpaperSession.MAX_KILLS; i++) s.onKill();
        s.reset();
        assertEquals(0, s.kills());
        assertFalse(s.killed());
        s.onKill();
        assertTrue(s.retryOnVisibleSession());
    }

    @Test
    public void startAfterScreenOffUnlocksOnceEvenWithoutUserPresent() {
        WallpaperSession s = new WallpaperSession();
        assertFalse(s.unlockOnStart(true));
        s.onScreenOff();
        assertFalse(s.unlockOnStart(false));
        assertTrue(s.unlockOnStart(true));
        assertFalse(s.unlockOnStart(true));
        assertFalse(s.unlockOnUserPresent());
    }

    @Test
    public void userPresentFirstMeansStartDoesNotUnlockAgain() {
        WallpaperSession s = new WallpaperSession();
        s.onScreenOff();
        assertTrue(s.unlockOnUserPresent());
        assertFalse(s.unlockOnStart(true));
        assertFalse(s.unlockOnUserPresent());
    }
}
