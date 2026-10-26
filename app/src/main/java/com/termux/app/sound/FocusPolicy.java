package com.termux.app.sound;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decision 12: Android's audio focus for everything that plays through the launcher's server.
 * <ul>
 *   <li>Focus is asked for while anything plays — a stream through the server, MPRIS or not, or
 *       a player that says it is playing — and given back when nothing does.</li>
 *   <li>A lasting loss pauses the MPRIS players; nothing is resumed afterwards.</li>
 *   <li>A short loss pauses them too, and when focus comes back resumes exactly the ones this
 *       paused — never one the user paused themselves.</li>
 *   <li>A loss that allows ducking lowers the server's volume, and restores it after.</li>
 *   <li>A stream with no MPRIS player cannot be paused, so on a loss the server is muted
 *       instead, until focus comes back or nothing plays any more.</li>
 *   <li>After a lasting loss, something new starting to play — a stream more, or a player that
 *       was not playing before — asks for focus again: that is the user choosing this sound.</li>
 * </ul>
 * Pure and single-threaded: the caller feeds it events and carries out the {@link Actions}.
 */
final class FocusPolicy {

    /** What Android said about focus. */
    enum Change { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

    enum State {
        /** Nothing plays, or focus was asked for and refused. */
        IDLE,
        HELD,
        DUCKED,
        /** A short loss: what this paused is waiting for focus to come back. */
        PAUSED_SHORT,
        /** A lasting loss: focus is gone until something new plays. */
        LOST
    }

    /** What the caller must do, in this order: abandon, request, unduck, duck, unmute, mute, pause, play. */
    static final class Actions {
        boolean request;
        boolean abandon;
        boolean duck;
        boolean unduck;
        boolean mute;
        boolean unmute;
        /** MPRIS players to pause. */
        final List<String> pause = new ArrayList<>();
        /** MPRIS players to resume. */
        final List<String> play = new ArrayList<>();

        boolean isEmpty() {
            return !request && !abandon && !duck && !unduck && !mute && !unmute
                && pause.isEmpty() && play.isEmpty();
        }
    }

    @NonNull private State state = State.IDLE;
    private int streams;
    @NonNull private Set<String> playing = Collections.emptySet();
    private final Set<String> pausedByUs = new LinkedHashSet<>();
    private boolean ducked;
    private boolean muted;

    @NonNull
    State state() {
        return state;
    }

    private boolean active() {
        return streams > 0 || !playing.isEmpty();
    }

    /** The playing streams or the playing MPRIS players changed. */
    @NonNull
    Actions onActivity(int playingStreams, @NonNull Collection<String> playingPlayers) {
        int previousStreams = streams;
        Set<String> previousPlaying = playing;
        streams = Math.max(0, playingStreams);
        playing = new LinkedHashSet<>(playingPlayers);
        Actions actions = new Actions();
        switch (state) {
            case IDLE:
                if (active()) actions.request = true;
                break;
            case HELD:
            case DUCKED:
                if (!active()) {
                    actions.abandon = true;
                    restore(actions);
                    state = State.IDLE;
                }
                break;
            case PAUSED_SHORT:
                // Quiet is what this asked for: wait for focus to come back.
                break;
            case LOST:
                if (!active()) {
                    restore(actions);
                    state = State.IDLE;
                } else if (streams > previousStreams || !previousPlaying.containsAll(playing)) {
                    actions.request = true;
                }
                break;
        }
        return actions;
    }

    /** What {@link Actions#request} got back. A refusal changes nothing: the next event asks again. */
    @NonNull
    Actions onRequestResult(boolean granted) {
        Actions actions = new Actions();
        if (!granted) return actions;
        restore(actions);
        pausedByUs.clear();
        state = State.HELD;
        return actions;
    }

    @NonNull
    Actions onFocusChange(@NonNull Change change) {
        Actions actions = new Actions();
        switch (change) {
            case GAIN:
                if (state == State.IDLE) break;
                restore(actions);
                actions.play.addAll(pausedByUs);
                pausedByUs.clear();
                state = State.HELD;
                break;
            case LOSS_TRANSIENT_CAN_DUCK:
                if (state != State.HELD) break;
                actions.duck = true;
                ducked = true;
                state = State.DUCKED;
                break;
            case LOSS_TRANSIENT:
                if (state != State.HELD && state != State.DUCKED) break;
                unduck(actions);
                actions.pause.addAll(playing);
                pausedByUs.addAll(playing);
                muteIfStreams(actions);
                state = State.PAUSED_SHORT;
                break;
            case LOSS:
                if (state == State.IDLE || state == State.LOST) break;
                unduck(actions);
                actions.pause.addAll(playing);
                // A lasting loss resumes nothing, the short loss's leftovers included.
                pausedByUs.clear();
                muteIfStreams(actions);
                actions.abandon = true;
                state = State.LOST;
                break;
        }
        return actions;
    }

    /** Media controls went off, or the server went away: undo everything and let focus go. */
    @NonNull
    Actions release() {
        Actions actions = new Actions();
        if (state != State.IDLE) actions.abandon = true;
        restore(actions);
        pausedByUs.clear();
        streams = 0;
        playing = Collections.emptySet();
        state = State.IDLE;
        return actions;
    }

    private void muteIfStreams(@NonNull Actions actions) {
        if (streams > 0 && !muted) {
            actions.mute = true;
            muted = true;
        }
    }

    private void unduck(@NonNull Actions actions) {
        if (!ducked) return;
        actions.unduck = true;
        ducked = false;
    }

    private void restore(@NonNull Actions actions) {
        unduck(actions);
        if (muted) {
            actions.unmute = true;
            muted = false;
        }
    }
}
