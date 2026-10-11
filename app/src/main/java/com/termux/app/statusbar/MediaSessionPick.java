package com.termux.app.statusbar;

import android.media.session.PlaybackState;

import androidx.annotation.NonNull;

/**
 * Which of the active media sessions the status bar's media row follows. Pure: it reads the
 * sessions' playback states (in the system's priority order) and the one followed now, so the
 * notification listener can ask again on every state change of any session without touching
 * the sessions themselves.
 *
 * <ul>
 *   <li>A playing session wins. The one already followed keeps the row while it plays, so two
 *       players do not trade it back and forth; otherwise the first playing one in priority
 *       order takes it.</li>
 *   <li>The followed session keeps the row through a transient state (buffering, connecting,
 *       skipping, seeking), so a track change does not hand the row to a paused player for a
 *       moment.</li>
 *   <li>With nothing playing, a paused session is followed, the current one first.</li>
 *   <li>A session with no state, stopped, or in error never claims the row, however high it
 *       ranks: an app that keeps a session open without playing (a reader's read-aloud) must not
 *       hide the music behind it.</li>
 * </ul>
 */
public final class MediaSessionPick {

    /** The answer when no session should be followed. */
    public static final int NONE = -1;

    private MediaSessionPick() {
    }

    /**
     * The index of the session to follow, or {@link #NONE}.
     *
     * @param states  each active session's {@link PlaybackState} state, in priority order; a
     *                session that reports no state is {@link PlaybackState#STATE_NONE}
     * @param current the index of the session followed now, or {@link #NONE}
     */
    public static int choose(@NonNull int[] states, int current) {
        boolean hasCurrent = current >= 0 && current < states.length;
        if (hasCurrent && states[current] == PlaybackState.STATE_PLAYING) return current;
        for (int i = 0; i < states.length; i++) {
            if (states[i] == PlaybackState.STATE_PLAYING) return i;
        }
        if (hasCurrent && isTransient(states[current])) return current;
        if (hasCurrent && states[current] == PlaybackState.STATE_PAUSED) return current;
        for (int i = 0; i < states.length; i++) {
            if (states[i] == PlaybackState.STATE_PAUSED) return i;
        }
        return NONE;
    }

    /** Whether a session in {@code state} shows on the row: only playing and paused do. */
    public static boolean showsRow(int state) {
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED;
    }

    /** A state a player passes through on its way back to playing. */
    static boolean isTransient(int state) {
        switch (state) {
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
    }
}
