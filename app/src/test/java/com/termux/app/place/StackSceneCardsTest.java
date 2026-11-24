package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.Side;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.wall.PageRef;

import org.junit.Test;

/**
 * The scene's cards (ADR 0032 section 5): the two rests' cards paired by id. A card both ends draw
 * alike keeps its rest; a side's card slides with its side, rides its side's layer and stands its
 * rest's own offset; the frame at p = 0 and p = 1 is each end's rest, so a landing writes nothing.
 */
public class StackSceneCardsTest {

    private static final int WIDTH = 1080;
    private static final float DROP = 16f;
    private static final int LIFT = 21;
    private static final Box HOME_CARD = new Box(0f, 2000f, 1080f, 2412f);
    private static final Box TERMINAL_CARD = new Box(21f, 2200f, 1059f, 2298f);
    private static final int HOST = StackView.HOST.ordinal();
    private static final int T_HOST = StackView.TERMINAL_HOST.ordinal();
    private static final int KEYS = StackView.KEYS_CARD.ordinal();
    private static final int HOME = Side.HOME.ordinal();
    private static final int TERMINAL = Side.TERMINAL.ordinal();

    private final StackScene.Motion mMotion = new StackScene.Motion();
    private final StackScene.Frame mFrame = new StackScene.Frame();

    /** Home's card on the glass host, the Terminal's on {@code terminalView}, apart. */
    private static StackEnd apartEnd(PageRef page, StackView terminalView) {
        StackEnd end = new StackEnd(page);
        end.rest.containerWidthPx = WIDTH;
        end.homeDropPx = DROP;
        end.terminalLiftPx = LIFT;
        boolean home = page.isHome();
        end.setCard(StackView.HOST, CardId.DOCK, HOME, home, HOME_CARD);
        end.setCard(terminalView, CardId.DOCK, TERMINAL, !home, TERMINAL_CARD);
        return end;
    }

    private static StackEnd sharedPeekEnd(PageRef page, Box box) {
        StackEnd end = new StackEnd(page);
        end.rest.containerWidthPx = WIDTH;
        end.setCard(StackView.HOST, CardId.PEEK, StackEnd.STACK_SIDE, true, box);
        return end;
    }

    private void at(StackEnd from, StackEnd to, float p) {
        StackScene.at(from, to, p, mMotion, from.rest, mFrame);
    }

    @Test public void eachSidesCardSlidesWithItsSideAndStandsItsRestsOffset() {
        StackEnd home = apartEnd(PageRef.HOME, StackView.TERMINAL_HOST);
        StackEnd terminal = apartEnd(PageRef.TERMINAL, StackView.TERMINAL_HOST);
        for (float p : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}) {
            at(home, terminal, p);
            float hidden = StackScene.homeHidden(Side.HOME, Side.TERMINAL, p);
            assertEquals("p=" + p, -hidden * WIDTH, mFrame.cardTx[HOST], 0.01f);
            assertEquals("p=" + p, (1f - hidden) * WIDTH, mFrame.cardTx[T_HOST], 0.01f);
            assertEquals("p=" + p, DROP, mFrame.cardTy[HOST], 0f);
            assertEquals("p=" + p, -LIFT, mFrame.cardTy[T_HOST], 0f);
            assertEquals(HOME, mFrame.cardSide[HOST]);
            assertEquals(TERMINAL, mFrame.cardSide[T_HOST]);
            assertFalse(mFrame.cardMorphs[HOST]);
            assertFalse(mFrame.cardMorphs[T_HOST]);
        }
    }

    @Test public void theEndsOfACrossingAreEachEndsRestSoTheLandingWritesNothing() {
        for (StackView terminalView : new StackView[] {StackView.TERMINAL_HOST, StackView.KEYS_CARD}) {
            StackEnd home = apartEnd(PageRef.HOME, terminalView);
            StackEnd terminal = apartEnd(PageRef.TERMINAL, terminalView);
            StackScene.Frame rest = new StackScene.Frame();
            for (StackEnd[] pair : new StackEnd[][] {{home, terminal}, {terminal, home}}) {
                StackScene.at(pair[0], pair[0], 0f, mMotion, pair[0].rest, rest);
                at(pair[0], pair[1], 0f);
                assertSamePoses(rest, mFrame);
                StackScene.at(pair[1], pair[1], 0f, mMotion, pair[1].rest, rest);
                at(pair[0], pair[1], 1f);
                assertSamePoses(rest, mFrame);
            }
        }
    }

    private static void assertSamePoses(StackScene.Frame want, StackScene.Frame got) {
        for (int v = 0; v < StackEnd.VIEW_COUNT; v++) {
            assertEquals("tx " + v, want.cardTx[v], got.cardTx[v], 0f);
            assertEquals("ty " + v, want.cardTy[v], got.cardTy[v], 0f);
        }
        assertEquals(want.slabView, got.slabView);
    }

    @Test public void theTerminalsCardRidesTheRowsAndHomesTheHostsSink() {
        StackEnd home = apartEnd(PageRef.HOME, StackView.KEYS_CARD);
        StackEnd terminal = apartEnd(PageRef.TERMINAL, StackView.KEYS_CARD);
        mMotion.layerSink(10f, 10f, 4f, 4f);
        at(home, terminal, 0.5f);
        assertEquals(4f + DROP, mFrame.cardTy[HOST], 0f);
        assertEquals(10f - LIFT, mFrame.cardTy[KEYS], 0f);
    }

    @Test public void homesCardStandsTheKeyboardsCoverTheTerminalsNone() {
        StackEnd home = apartEnd(PageRef.HOME, StackView.TERMINAL_HOST);
        StackEnd terminal = apartEnd(PageRef.TERMINAL, StackView.TERMINAL_HOST);
        mMotion.homeCoverPx = 30f;
        at(home, terminal, 0.5f);
        assertEquals(DROP + 30f, mFrame.cardTy[HOST], 0f);
        assertEquals(30f, mFrame.cardCoverPx[HOST], 0f);
        assertEquals(-LIFT, mFrame.cardTy[T_HOST], 0f);
    }

    @Test public void aCardBothEndsDrawAlikeKeepsItsRest() {
        StackEnd home = sharedPeekEnd(PageRef.HOME, HOME_CARD);
        StackEnd terminal = sharedPeekEnd(PageRef.TERMINAL, HOME_CARD);
        mMotion.homeCoverPx = 30f;
        for (float p : new float[] {0f, 0.3f, 0.7f, 1f}) {
            at(home, terminal, p);
            assertEquals(0f, mFrame.cardTx[HOST], 0f);
            assertEquals(StackEnd.STACK_SIDE, mFrame.cardSide[HOST]);
            assertFalse(mFrame.cardMorphs[HOST]);
            // The stack's own layer carries the cover by Home's share.
            assertEquals(StackScene.homeShare(true, false, p) * 30f, mFrame.cardTy[HOST], 1e-4f);
            assertEquals(HOST, mFrame.slabView);
        }
    }

    @Test public void twoDifferentCardsOnOneViewAreAMorphAndStayPut() {
        StackEnd home = sharedPeekEnd(PageRef.HOME, HOME_CARD);
        StackEnd terminal = sharedPeekEnd(PageRef.TERMINAL, TERMINAL_CARD);
        at(home, terminal, 0.5f);
        assertTrue(mFrame.cardMorphs[HOST]);
        assertEquals(0f, mFrame.cardTx[HOST], 0f);
    }

    @Test public void theSlabIsTheNearerEndsDockCard() {
        StackEnd home = apartEnd(PageRef.HOME, StackView.KEYS_CARD);
        StackEnd terminal = apartEnd(PageRef.TERMINAL, StackView.KEYS_CARD);
        at(home, terminal, 0.25f);
        assertEquals(HOST, mFrame.slabView);
        at(home, terminal, 0.75f);
        assertEquals(KEYS, mFrame.slabView);
        StackEnd bare = new StackEnd(PageRef.TERMINAL);
        at(bare, bare, 0f);
        assertEquals(StackEnd.NO_CARD, mFrame.slabView);
    }

    @Test public void withoutEndsNothingIsPosed() {
        StackScene.at(null, null, 0.5f, mMotion, new StackGeometry(), mFrame);
        for (int v = 0; v < StackEnd.VIEW_COUNT; v++) {
            assertEquals(0f, mFrame.cardTx[v], 0f);
            assertEquals(StackEnd.NO_SIDE, mFrame.cardSide[v]);
        }
    }

    @Test public void readCardsCopiesARestsTableBySide() {
        StackEnd end = new StackEnd(PageRef.HOME);
        end.readCards(null);
        for (int v = 0; v < StackEnd.VIEW_COUNT; v++) {
            assertEquals(StackEnd.NO_CARD, end.cardOn[v]);
            assertEquals(StackEnd.NO_SIDE, end.viewSide[v]);
        }
        assertEquals(HOME, StackEnd.sideOf(StackView.HOST, true));
        assertEquals(TERMINAL, StackEnd.sideOf(StackView.TERMINAL_HOST, true));
        assertEquals(TERMINAL, StackEnd.sideOf(StackView.KEYS_CARD, true));
        assertEquals(StackEnd.STACK_SIDE, StackEnd.sideOf(StackView.HOST, false));
        assertEquals(StackEnd.STACK_SIDE, StackEnd.sideOf(StackView.KEYBOARD_COLUMN, true));
    }
}
