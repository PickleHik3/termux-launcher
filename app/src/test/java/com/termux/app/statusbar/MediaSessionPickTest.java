package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import org.junit.Test;

public class MediaSessionPickTest {

    private static final int NONE = PlaybackState.STATE_NONE;
    private static final int PLAYING = PlaybackState.STATE_PLAYING;
    private static final int PAUSED = PlaybackState.STATE_PAUSED;
    private static final int STOPPED = PlaybackState.STATE_STOPPED;
    private static final int ERROR = PlaybackState.STATE_ERROR;
    private static final int BUFFERING = PlaybackState.STATE_BUFFERING;
    private static final int SKIPPING = PlaybackState.STATE_SKIPPING_TO_NEXT;

    @Test
    public void nothingActiveFollowsNothing() {
        assertEquals(MediaSessionPick.NONE, MediaSessionPick.choose(new int[0], MediaSessionPick.NONE));
    }

    @Test
    public void aStatelessSessionRankedFirstDoesNotHideThePlayingOne() {
        // The device report: a reader's read-aloud session, active with no state, ahead of music.
        assertEquals(1, MediaSessionPick.choose(new int[] {NONE, PLAYING}, MediaSessionPick.NONE));
        assertEquals(1, MediaSessionPick.choose(new int[] {NONE, PLAYING}, 0));
    }

    @Test
    public void sessionsThatAreNotPlayingOrPausedNeverClaimTheRow() {
        assertEquals(MediaSessionPick.NONE,
            MediaSessionPick.choose(new int[] {NONE, STOPPED, ERROR}, MediaSessionPick.NONE));
    }

    @Test
    public void aPlayingSessionTakesTheRowFromAPausedOne() {
        assertEquals(1, MediaSessionPick.choose(new int[] {PAUSED, PLAYING}, 0));
    }

    @Test
    public void theFollowedPlayerKeepsTheRowWhileAnotherAlsoPlays() {
        assertEquals(1, MediaSessionPick.choose(new int[] {PLAYING, PLAYING}, 1));
        assertEquals(0, MediaSessionPick.choose(new int[] {PLAYING, PLAYING}, MediaSessionPick.NONE));
    }

    @Test
    public void theFollowedPlayerKeepsTheRowThroughATrackChange() {
        assertEquals(1, MediaSessionPick.choose(new int[] {PAUSED, SKIPPING}, 1));
        assertEquals(1, MediaSessionPick.choose(new int[] {PAUSED, BUFFERING}, 1));
    }

    @Test
    public void aTransientSessionNotYetFollowedWaitsUntilItPlays() {
        assertEquals(MediaSessionPick.NONE,
            MediaSessionPick.choose(new int[] {BUFFERING}, MediaSessionPick.NONE));
    }

    @Test
    public void withNothingPlayingTheFollowedPausedSessionStays() {
        assertEquals(1, MediaSessionPick.choose(new int[] {PAUSED, PAUSED}, 1));
        assertEquals(0, MediaSessionPick.choose(new int[] {PAUSED, PAUSED}, MediaSessionPick.NONE));
    }

    @Test
    public void aStaleCurrentIndexIsIgnored() {
        assertEquals(0, MediaSessionPick.choose(new int[] {PAUSED}, 3));
    }

    @Test
    public void onlyPlayingAndPausedShowTheRow() {
        assertTrue(MediaSessionPick.showsRow(PLAYING));
        assertTrue(MediaSessionPick.showsRow(PAUSED));
        assertFalse(MediaSessionPick.showsRow(NONE));
        assertFalse(MediaSessionPick.showsRow(STOPPED));
        assertFalse(MediaSessionPick.showsRow(BUFFERING));
    }
}
