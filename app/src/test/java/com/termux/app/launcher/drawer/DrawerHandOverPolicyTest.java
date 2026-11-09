package com.termux.app.launcher.drawer;

import static com.termux.app.launcher.drawer.DrawerHandOverPolicy.Decision.HAND_OVER;
import static com.termux.app.launcher.drawer.DrawerHandOverPolicy.Decision.STAY;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DrawerHandOverPolicyTest {

    private static final float DENSITY = 2.75f;
    private static final float TOP = 400f;
    /** 24dp at this density, the distance past the top that hands over at once. */
    private static final float CROSS_PX = DrawerHandOverPolicy.CROSS_DP * DENSITY;

    // ---- the rule

    /** Inside the drawer, however long: the drag stays the drawer's. */
    @Test
    public void aFingerInsideTheDrawerStays() {
        assertEquals(STAY, DrawerHandOverPolicy.decide(TOP + 1f, TOP, 10_000L, DENSITY));
        assertEquals(STAY, DrawerHandOverPolicy.decide(TOP, TOP, 10_000L, DENSITY));
    }

    /** Just above the top, briefly: not yet. */
    @Test
    public void aBriefPeekAboveTheTopStays() {
        assertEquals(STAY, DrawerHandOverPolicy.decide(TOP - 1f, TOP,
            DrawerHandOverPolicy.HOLD_MS - 1L, DENSITY));
    }

    /** Held above the top for the hold: handed over, however little past it. */
    @Test
    public void heldAboveTheTopForTheHoldHandsOver() {
        assertEquals(HAND_OVER, DrawerHandOverPolicy.decide(TOP - 1f, TOP,
            DrawerHandOverPolicy.HOLD_MS, DENSITY));
    }

    /** More than 24dp past the top: handed over at once. Exactly 24dp is not more. */
    @Test
    public void crossingByMoreThan24dpHandsOverAtOnce() {
        assertEquals(HAND_OVER, DrawerHandOverPolicy.decide(TOP - CROSS_PX - 1f, TOP, 0L,
            DENSITY));
        assertEquals(STAY, DrawerHandOverPolicy.decide(TOP - CROSS_PX, TOP, 0L, DENSITY));
    }

    /** No top to cross (the drawer is not laid out): nothing is ever handed over. */
    @Test
    public void noTopNeverHandsOver() {
        assertEquals(STAY, DrawerHandOverPolicy.decide(-10_000f, Float.NaN, 10_000L, DENSITY));
    }

    // ---- the hold, as a drag's moves feed it

    /** The hold starts when the finger goes above, not when the drag started. */
    @Test
    public void theHoldCountsFromTheCrossing() {
        DrawerHandOverPolicy policy = new DrawerHandOverPolicy();
        assertEquals(STAY, policy.onMove(TOP + 50f, TOP, DENSITY, 0L));
        assertEquals(STAY, policy.onMove(TOP - 5f, TOP, DENSITY, 1_000L));
        assertEquals(STAY, policy.onMove(TOP - 6f, TOP, DENSITY,
            1_000L + DrawerHandOverPolicy.HOLD_MS - 1L));
        assertEquals(HAND_OVER, policy.onMove(TOP - 6f, TOP, DENSITY,
            1_000L + DrawerHandOverPolicy.HOLD_MS));
    }

    /** Coming back into the drawer before the hold runs out starts it over. */
    @Test
    public void comingBackRestartsTheHold() {
        DrawerHandOverPolicy policy = new DrawerHandOverPolicy();
        policy.onMove(TOP - 5f, TOP, DENSITY, 0L);
        assertEquals(STAY, policy.onMove(TOP + 5f, TOP, DENSITY, 200L));
        assertEquals(STAY, policy.onMove(TOP - 5f, TOP, DENSITY, 300L));
        assertEquals(STAY, policy.onMove(TOP - 5f, TOP, DENSITY, 300L
            + DrawerHandOverPolicy.HOLD_MS - 1L));
        assertEquals(HAND_OVER, policy.onMove(TOP - 5f, TOP, DENSITY,
            300L + DrawerHandOverPolicy.HOLD_MS));
    }

    /** A resting finger sends no moves; the caller asks again when the hold would run out. */
    @Test
    public void theWaitTellsTheCallerWhenToAskAgain() {
        DrawerHandOverPolicy policy = new DrawerHandOverPolicy();
        assertEquals(-1L, policy.msUntilHandOver(0L));
        policy.onMove(TOP - 5f, TOP, DENSITY, 100L);
        assertEquals(DrawerHandOverPolicy.HOLD_MS, policy.msUntilHandOver(100L));
        assertEquals(DrawerHandOverPolicy.HOLD_MS - 60L, policy.msUntilHandOver(160L));
        assertEquals(0L, policy.msUntilHandOver(100L + DrawerHandOverPolicy.HOLD_MS + 40L));
        policy.onMove(TOP + 5f, TOP, DENSITY, 200L);
        assertEquals(-1L, policy.msUntilHandOver(200L));
    }

    /** A fresh drag forgets the last one's hold. */
    @Test
    public void resetForgetsTheHold() {
        DrawerHandOverPolicy policy = new DrawerHandOverPolicy();
        policy.onMove(TOP - 5f, TOP, DENSITY, 0L);
        policy.reset();
        assertEquals(-1L, policy.msUntilHandOver(1_000L));
        assertEquals(STAY, policy.onMove(TOP - 5f, TOP, DENSITY, 1_000L));
    }
}
