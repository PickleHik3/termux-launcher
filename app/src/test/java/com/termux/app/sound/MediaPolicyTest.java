package com.termux.app.sound;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Which player the media session shows (decision 11) and what audio focus does (decision 12). */
public class MediaPolicyTest {

    private static PlayerState player(String name, PlayerState.Status status) {
        return new PlayerState("org.mpris.MediaPlayer2." + name, 0, status, name, null, null, null,
            null, -1, -1, 1f, true, true, true, true, false);
    }

    private static final List<String> NONE = Collections.emptyList();

    // ---- Decision 11: the pick ----------------------------------------------------------------------

    @Test public void thePlayerThatMostRecentlyStartedPlayingIsShown() {
        PlayerPick pick = new PlayerPick();
        pick.update(player("kew", PlayerState.Status.PLAYING));
        pick.update(player("mpv", PlayerState.Status.PLAYING));

        assertEquals("org.mpris.MediaPlayer2.mpv", pick.pick().name);

        pick.update(player("kew", PlayerState.Status.PAUSED));
        pick.update(player("kew", PlayerState.Status.PLAYING));
        assertEquals("org.mpris.MediaPlayer2.kew", pick.pick().name);
    }

    @Test public void aReportThatChangesNothingDoesNotStealTheSession() {
        PlayerPick pick = new PlayerPick();
        pick.update(player("kew", PlayerState.Status.PLAYING));
        pick.update(player("mpv", PlayerState.Status.PLAYING));
        // A new title from kew while it keeps playing is not "changed to playing".
        pick.update(player("kew", PlayerState.Status.PLAYING));

        assertEquals("org.mpris.MediaPlayer2.mpv", pick.pick().name);
    }

    @Test public void whenTheShownOneStopsAnotherThatIsPlayingOrPausedTakesOver() {
        PlayerPick pick = new PlayerPick();
        pick.update(player("kew", PlayerState.Status.PLAYING));
        pick.update(player("mpv", PlayerState.Status.PLAYING));
        assertEquals("org.mpris.MediaPlayer2.mpv", pick.pick().name);

        pick.update(player("mpv", PlayerState.Status.STOPPED));
        assertEquals("org.mpris.MediaPlayer2.kew", pick.pick().name);

        pick.update(player("kew", PlayerState.Status.PAUSED));
        assertEquals("org.mpris.MediaPlayer2.kew", pick.pick().name);

        pick.remove("org.mpris.MediaPlayer2.kew");
        assertNull(pick.pick());
    }

    @Test public void aPausedShownPlayerStaysShownUntilAnotherPlays() {
        PlayerPick pick = new PlayerPick();
        pick.update(player("kew", PlayerState.Status.PLAYING));
        pick.update(player("mpv", PlayerState.Status.PAUSED));
        assertEquals("org.mpris.MediaPlayer2.kew", pick.pick().name);

        pick.update(player("kew", PlayerState.Status.PAUSED));
        assertEquals("org.mpris.MediaPlayer2.kew", pick.pick().name);

        pick.update(player("mpv", PlayerState.Status.PLAYING));
        assertEquals("org.mpris.MediaPlayer2.mpv", pick.pick().name);
    }

    @Test public void aSeekMovesThePositionOfThatPlayerOnly() {
        PlayerPick pick = new PlayerPick();
        pick.update(player("kew", PlayerState.Status.PLAYING));
        pick.seeked("org.mpris.MediaPlayer2.kew", 42_000);
        pick.seeked("org.mpris.MediaPlayer2.nobody", 1);

        assertEquals(42_000, pick.get("org.mpris.MediaPlayer2.kew").positionMs);
        assertNull(pick.get("org.mpris.MediaPlayer2.nobody"));
        assertEquals(Collections.singletonList("org.mpris.MediaPlayer2.kew"), pick.playing());
    }

    // ---- Decision 12: focus -------------------------------------------------------------------------

    private static final List<String> KEW = Collections.singletonList("kew");

    private static FocusPolicy held(int streams, List<String> playing) {
        FocusPolicy focus = new FocusPolicy();
        FocusPolicy.Actions first = focus.onActivity(streams, playing);
        assertTrue(first.request);
        focus.onRequestResult(true);
        // Still playing when the grace runs out: the full focus.
        FocusPolicy.Actions full = focus.onGraceElapsed();
        assertTrue(full.request);
        assertSame(FocusPolicy.Kind.FULL, full.kind);
        focus.onRequestResult(true);
        assertSame(FocusPolicy.State.HELD, focus.state());
        return focus;
    }

    private static FocusPolicy probation(int streams, List<String> playing) {
        FocusPolicy focus = new FocusPolicy();
        FocusPolicy.Actions first = focus.onActivity(streams, playing);
        assertTrue(first.request);
        assertSame(FocusPolicy.Kind.TRANSIENT_DUCK, first.kind);
        assertTrue(first.startGrace);
        focus.onRequestResult(true);
        assertSame(FocusPolicy.State.PROBATION, focus.state());
        return focus;
    }

    @Test public void aShortSoundAsksOnlyForDuckableFocusAndPausesNobody() {
        FocusPolicy focus = probation(1, NONE);

        FocusPolicy.Actions quiet = focus.onActivity(0, NONE);
        assertTrue(quiet.abandon);
        assertTrue(quiet.cancelGrace);
        assertFalse(quiet.request);
        assertTrue(quiet.pause.isEmpty());
        assertTrue(quiet.mute == false && quiet.duck == false);
        assertSame(FocusPolicy.State.IDLE, focus.state());
        // A late grace event does nothing.
        assertTrue(focus.onGraceElapsed().isEmpty());
    }

    @Test public void aSoundThatOutlivesTheGraceAsksForTheFullFocus() {
        FocusPolicy focus = probation(1, NONE);
        assertTrue(focus.onActivity(2, NONE).isEmpty());

        FocusPolicy.Actions grace = focus.onGraceElapsed();
        assertTrue(grace.request);
        assertSame(FocusPolicy.Kind.FULL, grace.kind);
        assertTrue(grace.pause.isEmpty());
        focus.onRequestResult(true);
        assertSame(FocusPolicy.State.HELD, focus.state());
        // Nothing more to ask for a second time.
        assertTrue(focus.onGraceElapsed().isEmpty());
    }

    @Test public void aPlayerStartingDuringProbationReachesTheFullFocusAtTheGrace() {
        FocusPolicy focus = probation(1, NONE);
        assertTrue(focus.onActivity(0, KEW).isEmpty());

        // The stream is over, the player plays on: still active at 3 s.
        assertTrue(focus.onGraceElapsed().request);
    }

    @Test public void silenceDuringProbationGivesTheFocusBackAndStopsTheGrace() {
        FocusPolicy focus = probation(0, KEW);
        FocusPolicy.Actions quiet = focus.onActivity(0, NONE);
        assertTrue(quiet.abandon);
        assertTrue(quiet.cancelGrace);
        assertSame(FocusPolicy.State.IDLE, focus.state());
    }

    @Test public void aLastingLossDuringProbationAbandonsAndWaitsForSomethingNew() {
        FocusPolicy focus = probation(1, KEW);

        FocusPolicy.Actions loss = focus.onFocusChange(FocusPolicy.Change.LOSS);
        assertEquals(KEW, loss.pause);
        assertTrue(loss.mute);
        assertTrue(loss.abandon);
        assertTrue(loss.cancelGrace);
        assertSame(FocusPolicy.State.LOST, focus.state());
        assertTrue(focus.onGraceElapsed().isEmpty());
    }

    @Test public void aShortLossDuringProbationDefersTheFullFocusToTheNextGain() {
        FocusPolicy focus = probation(0, KEW);

        FocusPolicy.Actions loss = focus.onFocusChange(FocusPolicy.Change.LOSS_TRANSIENT);
        assertEquals(KEW, loss.pause);
        assertSame(FocusPolicy.State.PAUSED_SHORT, focus.state());
        assertTrue(focus.onGraceElapsed().isEmpty());

        FocusPolicy.Actions gain = focus.onFocusChange(FocusPolicy.Change.GAIN);
        assertEquals(KEW, gain.play);
        assertTrue(gain.startGrace);
        assertSame(FocusPolicy.State.PROBATION, focus.state());
    }

    @Test public void aDuckedProbationStillReachesTheFullFocus() {
        FocusPolicy focus = probation(1, NONE);
        assertTrue(focus.onFocusChange(FocusPolicy.Change.LOSS_TRANSIENT_CAN_DUCK).duck);
        assertSame(FocusPolicy.State.DUCKED, focus.state());
        assertTrue(focus.onGraceElapsed().request);
    }

    @Test public void aRefusedFullRequestKeepsTheDuckableFocusAndTriesAgainAfterAnotherGrace() {
        FocusPolicy focus = probation(1, NONE);
        assertTrue(focus.onGraceElapsed().request);
        assertTrue(focus.onRequestResult(false).startGrace);
        assertSame(FocusPolicy.State.PROBATION, focus.state());
        assertTrue(focus.onGraceElapsed().request);
    }

    @Test public void focusIsAskedForWhileAnythingPlaysAndGivenBackAfter() {
        FocusPolicy focus = new FocusPolicy();
        assertTrue(focus.onActivity(0, NONE).isEmpty());

        // A stream with no MPRIS player counts, as does a player.
        assertTrue(focus.onActivity(1, NONE).request);
        focus.onRequestResult(true);
        assertTrue(focus.onActivity(1, KEW).isEmpty());

        FocusPolicy.Actions quiet = focus.onActivity(0, NONE);
        assertTrue(quiet.abandon);
        assertSame(FocusPolicy.State.IDLE, focus.state());
    }

    @Test public void aRefusedRequestIsAskedAgainOnTheNextEvent() {
        FocusPolicy focus = new FocusPolicy();
        assertTrue(focus.onActivity(1, NONE).request);
        assertTrue(focus.onRequestResult(false).isEmpty());
        assertSame(FocusPolicy.State.IDLE, focus.state());
        assertTrue(focus.onActivity(1, KEW).request);
    }

    @Test public void aLastingLossPausesThePlayersMutesTheRestAndResumesNothing() {
        FocusPolicy focus = held(2, KEW);

        FocusPolicy.Actions loss = focus.onFocusChange(FocusPolicy.Change.LOSS);
        assertEquals(KEW, loss.pause);
        assertTrue(loss.mute);
        assertTrue(loss.abandon);
        assertSame(FocusPolicy.State.LOST, focus.state());

        // The pause lands, a stream without MPRIS keeps going: still lost, still muted.
        FocusPolicy.Actions after = focus.onActivity(1, NONE);
        assertTrue(after.isEmpty());

        // It ends: the mute goes, nothing is resumed.
        FocusPolicy.Actions quiet = focus.onActivity(0, NONE);
        assertTrue(quiet.unmute);
        assertTrue(quiet.play.isEmpty());
        assertSame(FocusPolicy.State.IDLE, focus.state());
    }

    @Test public void afterALastingLossSomethingNewPlayingAsksAgain() {
        FocusPolicy focus = held(1, KEW);
        focus.onFocusChange(FocusPolicy.Change.LOSS);
        focus.onActivity(1, NONE);

        // The user presses play in kew.
        assertTrue(focus.onActivity(1, KEW).request);
        FocusPolicy.Actions granted = focus.onRequestResult(true);
        assertTrue(granted.unmute);
        assertTrue(granted.play.isEmpty());
        assertSame(FocusPolicy.State.HELD, focus.state());
    }

    @Test public void aShortLossResumesExactlyWhatTheLauncherPaused() {
        FocusPolicy focus = held(2, Arrays.asList("kew", "mpv"));

        FocusPolicy.Actions loss = focus.onFocusChange(FocusPolicy.Change.LOSS_TRANSIENT);
        assertEquals(Arrays.asList("kew", "mpv"), loss.pause);
        assertTrue(loss.mute);
        assertFalse(loss.abandon);

        // Quiet while waiting is what was asked for: focus is not given back.
        assertTrue(focus.onActivity(0, NONE).isEmpty());

        FocusPolicy.Actions gain = focus.onFocusChange(FocusPolicy.Change.GAIN);
        assertEquals(Arrays.asList("kew", "mpv"), gain.play);
        assertTrue(gain.unmute);
        assertSame(FocusPolicy.State.HELD, focus.state());
    }

    @Test public void aPlayerTheUserHadPausedIsNotResumed() {
        FocusPolicy focus = held(0, KEW);
        FocusPolicy.Actions loss = focus.onFocusChange(FocusPolicy.Change.LOSS_TRANSIENT);
        assertEquals(KEW, loss.pause);
        assertFalse(loss.mute);

        FocusPolicy.Actions gain = focus.onFocusChange(FocusPolicy.Change.GAIN);
        assertEquals(KEW, gain.play);
        assertFalse(gain.unmute);

        // mpv was paused by the user all along; it never appears in a play list.
        assertFalse(gain.play.contains("mpv"));
    }

    @Test public void aDuckLowersTheServerAndAGainRestoresIt() {
        FocusPolicy focus = held(1, NONE);

        FocusPolicy.Actions duck = focus.onFocusChange(FocusPolicy.Change.LOSS_TRANSIENT_CAN_DUCK);
        assertTrue(duck.duck);
        assertTrue(duck.pause.isEmpty());
        assertSame(FocusPolicy.State.DUCKED, focus.state());

        FocusPolicy.Actions gain = focus.onFocusChange(FocusPolicy.Change.GAIN);
        assertTrue(gain.unduck);
        assertSame(FocusPolicy.State.HELD, focus.state());
    }

    @Test public void aDuckedServerThatGoesQuietIsRestoredAndLetsFocusGo() {
        FocusPolicy focus = held(1, NONE);
        focus.onFocusChange(FocusPolicy.Change.LOSS_TRANSIENT_CAN_DUCK);

        FocusPolicy.Actions quiet = focus.onActivity(0, NONE);
        assertTrue(quiet.unduck);
        assertTrue(quiet.abandon);
    }

    @Test public void aLossWhileDuckedRestoresTheVolumeBeforeMuting() {
        FocusPolicy focus = held(1, KEW);
        focus.onFocusChange(FocusPolicy.Change.LOSS_TRANSIENT_CAN_DUCK);

        FocusPolicy.Actions loss = focus.onFocusChange(FocusPolicy.Change.LOSS);
        assertTrue(loss.unduck);
        assertTrue(loss.mute);
        assertEquals(KEW, loss.pause);
    }

    @Test public void releasingUndoesEverything() {
        FocusPolicy focus = held(1, KEW);
        focus.onFocusChange(FocusPolicy.Change.LOSS_TRANSIENT);

        FocusPolicy.Actions release = focus.release();
        assertTrue(release.abandon);
        assertTrue(release.unmute);
        assertTrue(release.play.isEmpty());
        assertSame(FocusPolicy.State.IDLE, focus.state());
        assertTrue(new FocusPolicy().release().isEmpty());
    }
}
