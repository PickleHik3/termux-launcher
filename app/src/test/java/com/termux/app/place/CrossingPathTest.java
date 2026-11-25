package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.CrossingPath.Rows;
import com.termux.app.place.CrossingPath.Verb;

import org.junit.Test;

/**
 * The crossing rule's classifier over the classes table of the Stay, Glide or Ride proposal
 * (section 2, C1–C11), each case checked both ways: {@code verb(a, b) == verb(b, a)}.
 */
public class CrossingPathTest {

    private static final int A = CrossingPath.APPS;
    private static final int Z = CrossingPath.AZ;
    private static final int K = CrossingPath.EXTRA_KEYS;
    private static final int OVER = CrossingPath.OVER;
    private static final int UNDER = CrossingPath.UNDER;

    private static Rows rows() {
        return new Rows();
    }

    private static Verb both(Rows a, Rows b, int i) {
        Verb ab = CrossingPath.verb(a, b, i);
        assertEquals("symmetric", ab, CrossingPath.verb(b, a, i));
        assertEquals("crossfade symmetric", CrossingPath.crossfades(a, b, i),
            CrossingPath.crossfades(b, a, i));
        return ab;
    }

    @Test public void c1SameRectStays() {
        Rows a = rows();
        Rows b = rows();
        a.set(A, OVER, -98f, 98f);
        b.set(A, OVER, -98f, 98f);
        assertEquals(Verb.STAY, both(a, b, A));
        assertEquals(0f, CrossingPath.glideTyPx(a, b, A, 0.5f), 0f);
    }

    @Test public void c2SameSizeOtherPlaceOnOneEdgeGlides() {
        // The bands-under apps row: at the bottom on Home, above the Terminal's extra keys.
        Rows home = rows();
        Rows terminal = rows();
        home.set(A, UNDER, -120f, 120f);
        terminal.set(K, UNDER, -98f, 98f);
        terminal.set(A, UNDER, -218f, 120f);
        assertEquals(Verb.GLIDE, both(home, terminal, A));
        assertEquals(0f, CrossingPath.glideTyPx(home, terminal, A, 0f), 0f);
        assertEquals(-98f, CrossingPath.glideTyPx(home, terminal, A, 1f), 0f);
        assertEquals(98f, CrossingPath.glideTyPx(terminal, home, A, 1f), 0f);
    }

    @Test public void c3DifferentSizeRides() {
        Rows a = rows();
        Rows b = rows();
        a.set(A, OVER, -120f, 120f);
        b.set(A, OVER, -98f, 98f);
        assertEquals(Verb.RIDE, both(a, b, A));
        assertEquals(0f, CrossingPath.glideTyPx(a, b, A, 0.5f), 0f);
    }

    @Test public void c4DifferentBandRides() {
        // Over the keyboard at one end, under it at the other: the keyboard lies between.
        Rows a = rows();
        Rows b = rows();
        a.set(A, OVER, -120f, 120f);
        b.set(A, UNDER, -120f, 120f);
        assertEquals(Verb.RIDE, both(a, b, A));
    }

    @Test public void c5OneSideOnlyRidesAndNeitherIsNone() {
        Rows home = rows();
        Rows terminal = rows();
        terminal.set(K, OVER, -98f, 98f);
        assertEquals(Verb.RIDE, both(home, terminal, K));
        assertEquals(Verb.NONE, both(home, terminal, Z));
    }

    @Test public void c6DifferentContentCrossfadesOnlyWhereTheRowStaysOrGlides() {
        Rows a = rows();
        Rows b = rows();
        a.set(A, OVER, -98f, 98f);
        b.set(A, OVER, -98f, 98f);
        assertFalse("equal content: no fade", CrossingPath.crossfades(a, b, A));
        b.contentKey[A] = 7;
        assertTrue(CrossingPath.crossfades(a, b, A));
        b.set(A, OVER, -98f, 120f);
        assertEquals(Verb.RIDE, both(a, b, A));
        assertFalse("a riding row brings its own content", CrossingPath.crossfades(a, b, A));
    }

    @Test public void c7AnOrderSwapRidesBothRows() {
        Rows a = rows();
        Rows b = rows();
        a.set(A, OVER, -98f, 98f);
        a.set(Z, OVER, -196f, 98f);
        b.set(Z, OVER, -98f, 98f);
        b.set(A, OVER, -196f, 98f);
        assertEquals(Verb.RIDE, both(a, b, A));
        assertEquals(Verb.RIDE, both(a, b, Z));
    }

    @Test public void c7ARowAddedBelowKeepsTheOthersOrder() {
        Rows a = rows();
        Rows b = rows();
        a.set(A, OVER, -98f, 98f);
        a.set(Z, OVER, -196f, 98f);
        b.set(K, OVER, -98f, 98f);
        b.set(A, OVER, -196f, 98f);
        b.set(Z, OVER, -294f, 98f);
        assertEquals(Verb.GLIDE, both(a, b, A));
        assertEquals(Verb.GLIDE, both(a, b, Z));
        assertEquals(Verb.RIDE, both(a, b, K));
    }

    @Test public void c8TheKeyboardRidesWhereOneEndHasItUpAndStaysWhereBothDo() {
        assertEquals(Verb.RIDE, CrossingPath.keyboard(true, false));
        assertEquals(Verb.RIDE, CrossingPath.keyboard(false, true));
        assertEquals(Verb.STAY, CrossingPath.keyboard(true, true));
        assertEquals(Verb.NONE, CrossingPath.keyboard(false, false));
        // Up at both: the keys do not move.
        assertEquals(0f, CrossingPath.keyboardTxPx(true, true, KeyboardColumn.LEAVES_LEFT, 0.5f,
            1080), 0f);
    }

    @Test public void c8TheKeyboardSlidesWithThePageThatHasItUp() {
        int w = 1080;
        // Leaving up: out with the leaving page, a whole wall at the landing.
        assertEquals(0f, CrossingPath.keyboardTxPx(true, false, KeyboardColumn.LEAVES_LEFT, 0f, w), 0f);
        assertEquals(-540f, CrossingPath.keyboardTxPx(true, false, KeyboardColumn.LEAVES_LEFT, 0.5f, w), 0f);
        assertEquals(-1080f, CrossingPath.keyboardTxPx(true, false, KeyboardColumn.LEAVES_LEFT, 1f, w), 0f);
        // Arriving up: in with the arriving page from the far side.
        assertEquals(1080f, CrossingPath.keyboardTxPx(false, true, KeyboardColumn.LEAVES_LEFT, 0f, w), 0f);
        assertEquals(0f, CrossingPath.keyboardTxPx(false, true, KeyboardColumn.LEAVES_LEFT, 1f, w), 0f);
        assertEquals(-1080f, CrossingPath.keyboardTxPx(false, true, KeyboardColumn.LEAVES_RIGHT, 0f, w), 0f);
    }

    @Test public void c9TheVerbsReadNoStyle() {
        // Docked and Floating differ only in the per-rest answer: the same rows, the same verb.
        Rows docked = rows();
        Rows floating = rows();
        docked.set(A, UNDER, -120f, 120f);
        floating.set(A, UNDER, -120f, 120f);
        assertEquals(Verb.STAY, both(docked, floating, A));
    }

    @Test public void c10TheDirectionIsTheWallsElseBySide() {
        assertEquals(KeyboardColumn.LEAVES_RIGHT, CrossingPath.leavesDirection(
            KeyboardColumn.LEAVES_RIGHT, true, true));
        assertEquals(KeyboardColumn.LEAVES_LEFT, CrossingPath.leavesDirection(
            KeyboardColumn.LEAVES_UNKNOWN, true, false));
        assertEquals(KeyboardColumn.LEAVES_RIGHT, CrossingPath.leavesDirection(
            KeyboardColumn.LEAVES_UNKNOWN, false, true));
        assertEquals(KeyboardColumn.LEAVES_UNKNOWN, CrossingPath.leavesDirection(
            KeyboardColumn.LEAVES_UNKNOWN, true, true));
        // Home to Home with the wall's direction: the keyboard rides with its page.
        assertEquals(-1080f, CrossingPath.keyboardTxPx(true, false, CrossingPath.leavesDirection(
            KeyboardColumn.LEAVES_LEFT, true, true), 1f, 1080), 0f);
    }

    @Test public void c11ATurnedBackSwipeRunsTheSameFrames() {
        Rows a = rows();
        Rows b = rows();
        a.set(A, UNDER, -120f, 120f);
        b.set(A, UNDER, -218f, 120f);
        float[] forward = new float[9];
        for (int k = 0; k <= 8; k++) forward[k] = CrossingPath.glideTyPx(a, b, A, k / 8f);
        // The drag turned back: the same ends, p read downward, the same frames at the same p.
        for (int k = 8; k >= 0; k--)
            assertEquals("p=" + k / 8f, forward[k], CrossingPath.glideTyPx(a, b, A, k / 8f), 0f);
        for (int k = 0; k <= 8; k++) {
            float p = k / 8f;
            // The keyboard's slide is the wall's, linear in p: out one way is in the other.
            assertEquals(CrossingPath.keyboardTxPx(true, false, KeyboardColumn.LEAVES_LEFT, p, 1080),
                CrossingPath.keyboardTxPx(false, true, KeyboardColumn.LEAVES_RIGHT, 1f - p, 1080),
                1e-3f);
        }
    }

    @Test public void theSharedRowsEaseIsTheGlideWithItsExactNumbers() {
        float drop = 16.4f;
        int lift = 30;
        for (float s = -0.25f; s <= 1.25f; s += 0.0625f) {
            float clamped = s < 0f ? 0f : s > 1f ? 1f : s;
            float before = drop * (1f - clamped) - Math.max(0, lift) * clamped;
            assertEquals("s=" + s, before, SideCards.laneTyPx(StackLanes.STACK, true, drop, lift, s),
                0f);
        }
    }

    // ---- Cards (step 4) and the status bar (step 5) -----------------------------------------

    private static final int DOCK = RestCards.CardId.DOCK.ordinal();
    private static final int PEEK = RestCards.CardId.PEEK.ordinal();
    private static final int NO = StackEnd.NO_CARD;
    private static final int HOME = com.termux.app.page.Side.HOME.ordinal();
    private static final int TERM = com.termux.app.page.Side.TERMINAL.ordinal();
    private static final int STACK = StackEnd.STACK_SIDE;
    private static final int NO_SIDE = StackEnd.NO_SIDE;

    private static Verb card(int fromCard, int toCard, int fromSide, int toSide, boolean same) {
        Verb ab = CrossingPath.card(fromCard, toCard, fromSide, toSide, same);
        assertEquals("card symmetric", ab, CrossingPath.card(toCard, fromCard, toSide, fromSide,
            same));
        return ab;
    }

    @Test public void aCardIsPairedByElementOneElementAtOneRectStays() {
        assertEquals(Verb.STAY, card(PEEK, PEEK, STACK, STACK, true));
        assertEquals(Verb.STAY, card(DOCK, DOCK, HOME, HOME, true));
    }

    @Test public void aSidesCardRidesWithItsPage() {
        assertEquals(Verb.RIDE, card(DOCK, NO, HOME, HOME, false));
        assertEquals(Verb.RIDE, card(NO, DOCK, TERM, TERM, false));
    }

    @Test public void theStacksOwnCardStaysAndNoCardIsNone() {
        assertEquals(Verb.STAY, card(DOCK, NO, STACK, STACK, false));
        assertEquals(Verb.NONE, card(NO, NO, NO_SIDE, NO_SIDE, false));
    }

    @Test public void aViewHandedTwoCardsKeepsItsPoseAndIsNamed() {
        assertEquals(Verb.STAY, card(DOCK, PEEK, STACK, STACK, false));
        assertTrue(CrossingPath.morphs(DOCK, PEEK, STACK, STACK, false));
        assertTrue(CrossingPath.morphs(DOCK, DOCK, HOME, HOME, false));
        assertTrue(CrossingPath.morphs(NO, NO, HOME, TERM, false));
        assertFalse(CrossingPath.morphs(DOCK, DOCK, HOME, HOME, true));
    }

    private static Verb status(boolean a, boolean b, boolean place, boolean outline) {
        Verb ab = CrossingPath.status(a, b, place, outline);
        assertEquals("status symmetric", ab, CrossingPath.status(b, a, place, outline));
        return ab;
    }

    @Test public void theStatusBarStaysOnOneEdgeInOneOutlineElseRides() {
        assertEquals(Verb.STAY, status(true, true, true, true));
        // C4: an edge change rides (Q3).
        assertEquals(Verb.RIDE, status(true, true, false, true));
        assertEquals(Verb.RIDE, status(true, true, true, false));
        // C5: one end only rides with the page that wears it.
        assertEquals(Verb.RIDE, status(true, false, false, true));
        assertEquals(Verb.NONE, status(false, false, true, true));
    }

    @Test public void aStayingBarCrossfadesOnlyWhatDiffers() {
        assertTrue(CrossingPath.statusCrossfades(Verb.STAY, 1, 2, true, true));
        assertTrue(CrossingPath.statusCrossfades(Verb.STAY, 1, 1, true, false));
        assertFalse(CrossingPath.statusCrossfades(Verb.STAY, 1, 1, true, true));
        assertFalse(CrossingPath.statusCrossfades(Verb.RIDE, 1, 2, true, false));
    }
}
