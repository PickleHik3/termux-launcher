package com.termux.app.terminal.extrakeys;

import static com.termux.app.terminal.extrakeys.ExtraKeyPressPolicy.Action.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.extrakeys.ExtraKeyPressPolicy.Kind;
import com.termux.app.terminal.extrakeys.ExtraKeyPressPolicy.Move;

import org.junit.Test;

public class ExtraKeyPressPolicyTest {

    private static final float SLOP = 8f;
    private static final float FIRE = 48f;

    private static ExtraKeyPressPolicy policy(Kind kind, boolean second) {
        return new ExtraKeyPressPolicy(kind, second, true);
    }

    @Test
    public void repeatKeyTappedFiresOnceOnRelease() {
        ExtraKeyPressPolicy p = policy(Kind.REPEAT, false);
        assertEquals(NONE, p.down());
        assertEquals(FIRE_PRIMARY, p.up());
    }

    @Test
    public void repeatKeyHeldFiresAtHoldAndEveryTickButNotOnRelease() {
        ExtraKeyPressPolicy p = policy(Kind.REPEAT, false);
        p.down();
        assertEquals(REPEAT_FIRE, p.holdElapsed());
        assertTrue(p.isRepeating());
        assertEquals(REPEAT_FIRE, p.repeatTick());
        assertEquals(REPEAT_FIRE, p.repeatTick());
        assertEquals(NONE, p.up());
        assertFalse(p.isRepeating());
        assertEquals(NONE, p.repeatTick());
    }

    @Test
    public void plainKeyHeldThenReleasedStillFires() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, false);
        p.down();
        assertEquals(NONE, p.holdElapsed());
        assertEquals(FIRE_PRIMARY, p.up());
    }

    @Test
    public void modifierTapAndHold() {
        ExtraKeyPressPolicy tap = policy(Kind.MODIFIER, false);
        tap.down();
        assertEquals(MODIFIER_TAP, tap.up());

        ExtraKeyPressPolicy hold = policy(Kind.MODIFIER, false);
        hold.down();
        assertEquals(MODIFIER_HOLD, hold.holdElapsed());
        assertEquals(NONE, hold.up());
    }

    @Test
    public void swipeUpBelowFireDistanceFiresPrimary() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, true);
        p.down();
        assertEquals(Move.SWIPE, p.move(0, -(FIRE - 1), SLOP, FIRE));
        assertTrue(p.isSwiping());
        assertTrue(p.swipeProgress() < 1f);
        assertEquals(FIRE_PRIMARY, p.up());
    }

    @Test
    public void swipeUpAtFireDistanceFiresSecond() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, true);
        p.down();
        p.move(0, -FIRE, SLOP, FIRE);
        assertEquals(1f, p.swipeProgress(), 0f);
        assertEquals(FIRE_SECOND, p.up());
    }

    @Test
    public void swipeBackDownBelowFireDistanceFiresPrimary() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, true);
        p.down();
        p.move(0, -FIRE, SLOP, FIRE);
        p.move(0, -20, SLOP, FIRE);
        assertEquals(FIRE_PRIMARY, p.up());
    }

    @Test
    public void swipeNeedsASecondKey() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, false);
        p.down();
        assertEquals(Move.NONE, p.move(0, -FIRE, SLOP, FIRE));
        assertEquals(FIRE_PRIMARY, p.up());
    }

    @Test
    public void downwardTravelDoesNothing() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, true);
        p.down();
        assertEquals(Move.NONE, p.move(0, FIRE, SLOP, FIRE));
        assertEquals(FIRE_PRIMARY, p.up());
    }

    @Test
    public void sidewaysPastSlopPagesAndReleaseFiresNothing() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, true);
        p.down();
        assertEquals(Move.NONE, p.move(SLOP, 0, SLOP, FIRE));
        assertEquals(Move.PAGE_DRAG, p.move(SLOP + 1, 0, SLOP, FIRE));
        assertTrue(p.isPaging());
        assertEquals(NONE, p.up());
    }

    @Test
    public void pagingStopsRepeating() {
        ExtraKeyPressPolicy p = policy(Kind.REPEAT, false);
        p.down();
        p.holdElapsed();
        p.move(40, 0, SLOP, FIRE);
        assertFalse(p.isRepeating());
        assertEquals(NONE, p.repeatTick());
        assertEquals(NONE, p.up());
    }

    @Test
    public void afterSwipeArmsSidewaysDoesNotPage() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, true);
        p.down();
        p.move(0, -20, SLOP, FIRE);
        assertEquals(Move.SWIPE, p.move(60, -20, SLOP, FIRE));
        assertFalse(p.isPaging());
    }

    @Test
    public void swipeStopsTheHold() {
        ExtraKeyPressPolicy p = policy(Kind.REPEAT, true);
        p.down();
        p.move(0, -20, SLOP, FIRE);
        assertEquals(NONE, p.holdElapsed());
        assertFalse(p.isRepeating());
    }

    @Test
    public void deadKeyFiresNothingButCanPage() {
        ExtraKeyPressPolicy p = policy(Kind.DEAD, false);
        p.down();
        assertEquals(NONE, p.holdElapsed());
        assertEquals(NONE, p.up());

        ExtraKeyPressPolicy q = policy(Kind.DEAD, false);
        q.down();
        assertEquals(Move.PAGE_DRAG, q.move(30, 0, SLOP, FIRE));
        assertEquals(NONE, q.up());
    }

    @Test
    public void holdThatBelongsToTheHostFiresNothingAfter() {
        ExtraKeyPressPolicy p = new ExtraKeyPressPolicy(Kind.REPEAT, true, false);
        p.down();
        assertEquals(HOLD_TO_HOST, p.holdElapsed());
        assertEquals(NONE, p.holdElapsed());
        assertEquals(NONE, p.repeatTick());
        assertEquals(Move.NONE, p.move(40, 0, SLOP, FIRE));
        assertEquals(NONE, p.up());
    }

    @Test
    public void holdAfterPagingIsIgnored() {
        ExtraKeyPressPolicy p = new ExtraKeyPressPolicy(Kind.PLAIN, false, false);
        p.down();
        p.move(40, 0, SLOP, FIRE);
        assertEquals(NONE, p.holdElapsed());
    }

    @Test
    public void cancelFiresNothing() {
        ExtraKeyPressPolicy p = policy(Kind.PLAIN, false);
        p.down();
        assertEquals(NONE, p.cancel());
        assertEquals(NONE, p.up());
    }

    @Test
    public void kindOfKeys() {
        assertEquals(Kind.DEAD, ExtraKeyPressPolicy.kindOf("CTRL", false));
        assertEquals(Kind.MODIFIER, ExtraKeyPressPolicy.kindOf("CTRL", true));
        assertEquals(Kind.REPEAT, ExtraKeyPressPolicy.kindOf("LEFT", true));
        assertEquals(Kind.PLAIN, ExtraKeyPressPolicy.kindOf("TAB", true));
        assertEquals(Kind.PLAIN, ExtraKeyPressPolicy.kindOf(null, true));
    }
}
