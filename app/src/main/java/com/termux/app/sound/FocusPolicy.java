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
 *   <li>Focus comes in two steps. Something starting from nothing first asks for a transient,
 *       duckable focus, so another app's media is lowered, not paused: a terminal tool's alert is
 *       over before it matters. If anything still plays when {@link #GRACE_MS} has passed
 *       ({@link #onGraceElapsed()}, fired by the caller), the full focus is asked for and other
 *       apps pause as below. If everything stops within the grace, the transient request is
 *       abandoned and the other app's media comes back by itself. A player reporting Playing
 *       counts as activity like a stream, so music reaches the full request.</li>
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

    /** How long something may play on the transient focus before the full one is asked for. */
    static final long GRACE_MS = 3000;

    /** What Android said about focus. */
    enum Change { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

    enum State {
        /** Nothing plays, or focus was asked for and refused. */
        IDLE,
        /** The transient, duckable focus is held and the grace has not run out. */
        PROBATION,
        /** The full focus is held. */
        HELD,
        DUCKED,
        /** A short loss: what this paused is waiting for focus to come back. */
        PAUSED_SHORT,
        /** A lasting loss: focus is gone until something new plays. */
        LOST
    }

    /** Which focus a request is for. */
    enum Kind { TRANSIENT_DUCK, FULL }

    /** What the caller must do, in this order: abandon, request, unduck, duck, unmute, mute, pause, play. */
    static final class Actions {
        boolean request;
        /** Which focus to ask for; meaningful when {@link #request} is set. */
        @NonNull Kind kind = Kind.FULL;
        /** Post {@link #onGraceElapsed()} after {@link #GRACE_MS}, replacing any earlier post. */
        boolean startGrace;
        /** Drop the pending {@link #onGraceElapsed()}. */
        boolean cancelGrace;
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
            return !request && !startGrace && !cancelGrace && !abandon && !duck && !unduck && !mute && !unmute
                && pause.isEmpty() && play.isEmpty();
        }
    }

    @NonNull private State state = State.IDLE;
    private int streams;
    @NonNull private Set<String> playing = Collections.emptySet();
    private final Set<String> pausedByUs = new LinkedHashSet<>();
    private boolean ducked;
    private boolean muted;
    /** The full focus is held (or being asked for), not just the transient one. */
    private boolean full;
    /** What the request in flight is for. */
    @NonNull private Kind pending = Kind.TRANSIENT_DUCK;

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
                if (active()) {
                    ask(actions, Kind.TRANSIENT_DUCK);
                    actions.startGrace = true;
                }
                break;
            case PROBATION:
            case HELD:
            case DUCKED:
                if (!active()) {
                    actions.abandon = true;
                    actions.cancelGrace = true;
                    restore(actions);
                    full = false;
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
                    // The user choosing this sound: the full focus, as before.
                    ask(actions, Kind.FULL);
                }
                break;
        }
        return actions;
    }

    /**
     * The grace ran out. Still playing on the transient focus: ask for the full one. If a short
     * loss is under way nothing is asked; {@link Change#GAIN} starts the grace again. A refused
     * full request stays on the transient focus and tries once more after another grace.
     */
    @NonNull
    Actions onGraceElapsed() {
        Actions actions = new Actions();
        if ((state == State.PROBATION || state == State.DUCKED) && !full && active()) {
            ask(actions, Kind.FULL);
        }
        return actions;
    }

    private void ask(@NonNull Actions actions, @NonNull Kind kind) {
        actions.request = true;
        actions.kind = kind;
        pending = kind;
        if (kind == Kind.FULL) full = true;
    }

    /** What {@link Actions#request} got back. A refusal changes nothing: the next event asks again. */
    @NonNull
    Actions onRequestResult(boolean granted) {
        Actions actions = new Actions();
        if (pending == Kind.FULL) {
            if (!granted) {
                full = false;
                if (state == State.PROBATION || state == State.DUCKED) actions.startGrace = true;
                return actions;
            }
            restore(actions);
            pausedByUs.clear();
            state = State.HELD;
            return actions;
        }
        if (!granted) return actions;
        restore(actions);
        pausedByUs.clear();
        state = State.PROBATION;
        return actions;
    }

    @NonNull
    Actions onFocusChange(@NonNull Change change) {
        Actions actions = new Actions();
        switch (change) {
            case GAIN:
                if (state == State.IDLE) break;
                boolean resumed = state == State.PAUSED_SHORT;
                restore(actions);
                actions.play.addAll(pausedByUs);
                pausedByUs.clear();
                if (full || !active()) {
                    state = State.HELD;
                } else {
                    state = State.PROBATION;
                    if (resumed) actions.startGrace = true;
                }
                break;
            case LOSS_TRANSIENT_CAN_DUCK:
                if (state != State.HELD && state != State.PROBATION) break;
                actions.duck = true;
                ducked = true;
                state = State.DUCKED;
                break;
            case LOSS_TRANSIENT:
                if (state != State.HELD && state != State.DUCKED && state != State.PROBATION) break;
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
                actions.cancelGrace = true;
                full = false;
                state = State.LOST;
                break;
        }
        return actions;
    }

    /** Media controls went off, or the server went away: undo everything and let focus go. */
    @NonNull
    Actions release() {
        Actions actions = new Actions();
        if (state != State.IDLE) {
            actions.abandon = true;
            actions.cancelGrace = true;
        }
        full = false;
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
