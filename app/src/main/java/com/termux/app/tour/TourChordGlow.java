package com.termux.app.tour;

import androidx.annotation.Nullable;

/**
 * Which key of a chord card to glow, given what the keyboard has latched.
 *
 * <p>The in-app keyboard latches a modifier on a plain tap and holds it until the key it modifies
 * is pressed, so "Ctrl, Alt, then Enter" is three taps the user makes one at a time — and a card
 * that glowed all three at once would be asking for a grip nobody has on a phone. The glow walks
 * instead: it rests on the first key of the chord the keyboard has not latched yet, and lands on
 * the key itself once every modifier is down.
 *
 * <p>Pure, and driven by the card's own targets rather than by a second list: a chord card names
 * its modifier keys in the order it wants them pressed, so the targets are the chord.
 */
public final class TourChordGlow {

    /**
     * The target slot to glow.
     *
     * @param step the card, or null
     * @return 0 for any card that is not a chord, so the caller never has to ask twice
     */
    public static int indexFor(@Nullable TourStep step, boolean ctrl, boolean alt, boolean shift) {
        if (step == null || !step.chordGlow) return 0;
        int count = step.targetCount();
        for (int i = 0; i < count; i++) {
            Boolean latched = latchOf(step.targetIdAt(i), ctrl, alt, shift);
            // The first key that is not a latched modifier is the one being asked for: either a
            // modifier the user has not pressed yet, or the key the chord ends on.
            if (latched == null || !latched) return i;
        }
        return Math.max(0, count - 1);
    }

    /** Whether a target is a modifier key and whether it is latched; null when it is neither. */
    @Nullable
    private static Boolean latchOf(String targetId, boolean ctrl, boolean alt, boolean shift) {
        if (TourTargets.CTRL_KEY.equals(targetId)) return ctrl;
        if (TourTargets.ALT_KEY.equals(targetId)) return alt;
        if (TourTargets.SHIFT_KEY.equals(targetId)) return shift;
        return null;
    }

    private TourChordGlow() {}
}
