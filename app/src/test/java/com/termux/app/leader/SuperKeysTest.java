package com.termux.app.leader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * Super under Terl (ADR 0042, review of terl-hints-nav): a tap opens the search, a hold shows the
 * hint card, its keys fire chords only while the card shows, and Super never stays latched.
 */
public class SuperKeysTest {

    private static final int DOWN = KeyEvent.ACTION_DOWN;
    private static final int UP = KeyEvent.ACTION_UP;
    private static final int META = KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
    private static final int SUPER = KeyEvent.KEYCODE_META_LEFT;

    /** The card as TerlHintCardPresenter answers it, with the hold's delay played by hand. */
    private static final class FakeCard implements SuperKeys.Card {
        boolean held;
        boolean visible;
        boolean showedThisHold;

        @Override public void superDown() {
            held = true;
            showedThisHold = false;
        }

        @Override public boolean superUp() {
            held = false;
            visible = false;
            return showedThisHold;
        }

        @Override public boolean isSuperHeld() { return held; }

        @Override public boolean isVisible() { return visible; }

        void holdElapsed() {
            if (!held) return;
            visible = true;
            showedThisHold = true;
        }
    }

    @Test
    public void aQuickTapOpensTheSearchAndShowsNothing() {
        SuperKeys keys = new SuperKeys();
        FakeCard card = new FakeCard();
        assertTrue(keys.onKey(SUPER, DOWN, 0, META, false, card).take);
        SuperKeys.Step up = keys.onKey(SUPER, UP, 0, 0, false, card);
        assertTrue(up.take);
        assertTrue(up.openSearch);
        assertFalse(card.held);
    }

    @Test
    public void aHoldThatShowedTheCardIsNoTap() {
        SuperKeys keys = new SuperKeys();
        FakeCard card = new FakeCard();
        keys.onKey(SUPER, DOWN, 0, META, false, card);
        card.holdElapsed();
        assertFalse(keys.onKey(SUPER, UP, 0, 0, false, card).openSearch);
        assertFalse(card.visible);
    }

    @Test
    public void keysFireOnlyWhileTheCardShows() {
        SuperKeys keys = new SuperKeys();
        FakeCard card = new FakeCard();
        keys.onKey(SUPER, DOWN, 0, META, false, card);
        // A quick Super+Q, before the card: nothing of Terl's; Q goes on.
        SuperKeys.Step quick = keys.onKey(KeyEvent.KEYCODE_Q, DOWN, 0, META, false, card);
        assertFalse(quick.fire);
        assertFalse(quick.take);
        card.holdElapsed();
        assertTrue(keys.onKey(KeyEvent.KEYCODE_Q, DOWN, 0, META, false, card).fire);
        assertFalse("a repeat is the press's", keys.onKey(KeyEvent.KEYCODE_Q, DOWN, 1, META, false, card).fire);
    }

    @Test
    public void shiftUnderSuperKeepsTheCardAndTheShiftedRowFires() {
        SuperKeys keys = new SuperKeys();
        FakeCard card = new FakeCard();
        keys.onKey(SUPER, DOWN, 0, META, false, card);
        card.holdElapsed();
        SuperKeys.Step shift = keys.onKey(KeyEvent.KEYCODE_SHIFT_LEFT, DOWN, 0,
            META | KeyEvent.META_SHIFT_ON, false, card);
        assertFalse("a modifier is part of the chord", shift.fire);
        assertTrue("the card stays", card.visible && card.held);
        assertTrue(keys.onKey(KeyEvent.KEYCODE_DPAD_LEFT, DOWN, 0, META | KeyEvent.META_SHIFT_ON,
            false, card).fire);
    }

    /** Review P1-1: a chord moves the focus to a Linux window; Super's release must still end the hold. */
    @Test
    public void superLetGoOverAGuestEndsTheHoldAndLaterKeysAreTyping() {
        SuperKeys keys = new SuperKeys();
        FakeCard card = new FakeCard();
        keys.onKey(SUPER, DOWN, 0, META, false, card);
        card.holdElapsed();
        assertTrue(keys.onKey(KeyEvent.KEYCODE_2, DOWN, 0, META, false, card).fire);
        // Workspace 2's Linux app is in front now; Super comes up there.
        keys.onKey(SUPER, UP, 0, 0, true, card);
        assertFalse(card.held);
        SuperKeys.Step typed = keys.onKey(KeyEvent.KEYCODE_Q, DOWN, 0, 0, true, card);
        assertFalse(typed.fire);
        assertFalse(typed.take);
        assertFalse(keys.onKey(KeyEvent.KEYCODE_Q, DOWN, 0, 0, false, card).fire);
    }

    @Test
    public void aLostReleaseNeverLatches() {
        SuperKeys keys = new SuperKeys();
        FakeCard card = new FakeCard();
        keys.onKey(SUPER, DOWN, 0, META, false, card);
        card.holdElapsed();
        // Super's release went to another window: the next key carries no Super.
        SuperKeys.Step typed = keys.onKey(KeyEvent.KEYCODE_Q, DOWN, 0, 0, false, card);
        assertFalse(typed.fire);
        assertFalse("the hold ended", card.held);
    }

    @Test
    public void overAGuestSuperIsTheGuestsAndShowsNoCard() {
        SuperKeys keys = new SuperKeys();
        FakeCard card = new FakeCard();
        assertFalse(keys.onKey(SUPER, DOWN, 0, META, true, card).take);
        assertFalse(card.held);
        assertFalse(keys.onKey(SUPER, UP, 0, 0, true, card).take);
    }
}
