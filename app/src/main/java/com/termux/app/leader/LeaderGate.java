package com.termux.app.leader;

import java.util.HashSet;
import java.util.Set;

/**
 * The one rule every screen follows for a leader chord (ADR 0042, Decision 3): Terl takes a
 * leader chord only when it is bound; anything else goes to whatever is in front, and the Terl
 * key itself never reaches an app. The activity asks it once, early in its key dispatch, and the
 * in-app keyboard asks it for the Terl key, so a terminal, Home, a Linux window, the X11 Display,
 * the Assistant, search and the drawer all answer alike.
 *
 * <p>With the Terl desktop switch off nothing here is Terl's: the verdict is always
 * {@link Verdict#PASS} and each screen keeps v1's own handling.
 */
public final class LeaderGate {

    /** What the screen does with the stroke. */
    public enum Verdict {
        /** A binding took it: nothing else sees the key. */
        RUN,
        /** Not Terl's: it reaches the app or field in front unchanged. */
        PASS,
        /** An unbound Terl key chord: the key goes on, without the Terl key. */
        DROP_TERL_MODIFIER
    }

    private final Set<Integer> mRunKeys = new HashSet<>();

    /**
     * @param terlDesktop whether the Terl desktop switch is on
     * @param fromTerlKey whether the stroke is typed under the keyboard's Terl key
     * @param bound whether the binding table holds the stroke (a binding, a prefix or the next
     *     key of a pending sequence)
     */
    public static Verdict decide(boolean terlDesktop, boolean fromTerlKey, boolean bound) {
        if (fromTerlKey) return bound ? Verdict.RUN : Verdict.DROP_TERL_MODIFIER;
        if (!terlDesktop) return Verdict.PASS;
        return bound ? Verdict.RUN : Verdict.PASS;
    }

    /** A press of {@code keyCode} ran a binding: its repeats and its release are the launcher's too. */
    public void ran(int keyCode) {
        mRunKeys.add(keyCode);
    }

    /**
     * Whether this event is the tail of a press that ran: a repeat or the release, which must not
     * reach what is in front on their own. A release ends it.
     */
    public boolean swallows(boolean down, int keyCode, int repeatCount) {
        if (!mRunKeys.contains(keyCode)) return false;
        if (down) return repeatCount > 0;
        mRunKeys.remove(keyCode);
        return true;
    }
}
