package com.termux.app.leader;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where a hardware key goes while Super is about, under Terl (ADR 0042; review of terl-hints-nav).
 * Super is not a leader: a quick tap opens the bar's search, a hold shows the hint card, and only
 * while the card is up do the keys pressed under Super fire its chords. A quick Super+key does
 * nothing of Terl's. Over a Linux window or the Display Super is the guest's: its press and its
 * release go on to it.
 *
 * <p>Feed every key event, in order. The card is told Super is let go on every Super release,
 * whatever is in front, and a key whose own meta state holds no Super ends any hold whose release
 * was lost, so Super can never stay latched (a chord that moved the focus to a guest once left it
 * held, and later plain keys fired desktop actions). Pure: key codes and meta bits only.</p>
 */
public final class SuperKeys {

    /** The hint card as the router sees it. */
    public interface Card {
        /** Super went down with Terl's surface in front: the card shows after its hold. */
        void superDown();

        /** Super came up (or its release was lost): whether the card showed while it was held. */
        boolean superUp();

        boolean isSuperHeld();

        /** Whether the card is on screen now. */
        boolean isVisible();
    }

    /** What to do with one key event. */
    public static final class Step {
        /** The event is Terl's: nothing in front sees it. */
        public final boolean take;
        /** A quick Super tap ended: the bar's search opens (or closes). */
        public final boolean openSearch;
        /** Fire this key as a leader chord (Super's own bit dropped), as the card shows it. */
        public final boolean fire;

        Step(boolean take, boolean openSearch, boolean fire) {
            this.take = take;
            this.openSearch = openSearch;
            this.fire = fire;
        }
    }

    private static final Step PASS = new Step(false, false, false);

    @NonNull private final SuperKeyTap mTap = new SuperKeyTap();

    /**
     * @param frontIsGuest a Linux window or the Display is in front: Super is the guest's
     * @param card the hint card; null without one (Super still taps)
     */
    @NonNull
    public Step onKey(int keyCode, int action, int repeatCount, int metaState, boolean frontIsGuest,
                      @Nullable Card card) {
        boolean tap = mTap.onKey(keyCode, action, repeatCount);
        if (isSuper(keyCode)) {
            boolean showed = false;
            if (card != null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (repeatCount == 0 && !frontIsGuest) card.superDown();
                } else if (action == KeyEvent.ACTION_UP && card.isSuperHeld()) {
                    // Whatever is in front now: a chord may have moved the focus to a guest.
                    showed = card.superUp();
                }
            }
            return new Step(!frontIsGuest, tap && !showed, false);
        }
        if (card == null) return PASS;
        boolean superOn = (metaState & KeyEvent.META_META_MASK) != 0;
        // Super's release went elsewhere (another window had the keys): this key says it is up.
        if (!superOn && card.isSuperHeld()) card.superUp();
        boolean fire = superOn && action == KeyEvent.ACTION_DOWN && repeatCount == 0
            && !frontIsGuest && !isModifier(keyCode) && card.isVisible();
        return fire ? new Step(false, false, true) : PASS;
    }

    /** The Super (Meta) keys. */
    public static boolean isSuper(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT;
    }

    /**
     * A modifier: pressing it under Super (Shift, to read and fire a ⇧ row) is part of the chord,
     * never a chord of its own, so it neither fires nor spends the card.
     */
    public static boolean isModifier(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
            case KeyEvent.KEYCODE_CAPS_LOCK:
            case KeyEvent.KEYCODE_FUNCTION:
            case KeyEvent.KEYCODE_SYM:
            case KeyEvent.KEYCODE_NUM_LOCK:
            case KeyEvent.KEYCODE_SCROLL_LOCK:
                return true;
            default:
                return false;
        }
    }
}
