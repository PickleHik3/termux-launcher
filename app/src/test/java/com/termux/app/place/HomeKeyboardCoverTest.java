package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.dock.DockStyle;
import com.termux.app.page.PageKind;
import com.termux.app.wall.PageRef;

import org.junit.Test;

public class HomeKeyboardCoverTest {

    private static final int K = 600;

    @Test
    public void onlyHomeStandsStill() {
        assertFalse(HomeKeyboardCover.liftsRows(PageKind.HOME));
        for (PageKind page : PageKind.values()) {
            if (page != PageKind.HOME) assertTrue(page.name(), HomeKeyboardCover.liftsRows(page));
        }
    }

    @Test
    public void anAlwaysOnHomeKeepsRidingTheKeyboard() {
        assertTrue(HomeKeyboardCover.liftsRows(PageKind.HOME, true));
        assertFalse(HomeKeyboardCover.liftsRows(PageKind.HOME, false));
        for (PageKind page : PageKind.values()) {
            if (page != PageKind.HOME) {
                assertTrue(page.name(), HomeKeyboardCover.liftsRows(page, true));
                assertTrue(page.name(), HomeKeyboardCover.liftsRows(page, false));
            }
        }
    }

    @Test
    public void aLiftingSideIsNeverDropped() {
        assertEquals(0f, HomeKeyboardCover.rowsDropPx(true, K, 0f), 0f);
        assertEquals(0f, HomeKeyboardCover.imeStandPx(true, 300, 40, 10f), 0f);
    }

    @Test
    public void dropIsWhatTheShareDoesNotAlreadyCarry() {
        assertEquals(K, HomeKeyboardCover.rowsDropPx(false, K, 0f), 0f);
        assertEquals(K, HomeKeyboardCover.rowsDropPx(false, K, -5f), 0f);
        assertEquals(0f, HomeKeyboardCover.rowsDropPx(false, K, K), 0f);
        assertEquals(0f, HomeKeyboardCover.rowsDropPx(false, K, K + 10f), 0f);
    }

    @Test
    public void homeRowsStandAtTheirKeyboardDownRectAtEveryReveal() {
        for (float r : new float[] {0f, .3f, 1f}) {
            float share = StackScene.keyboardTranslationPx(r, K);
            float home = share + HomeKeyboardCover.rowsDropPx(false, K, share);
            float terminal = share + HomeKeyboardCover.rowsDropPx(true, K, share);
            assertEquals("home at " + r, K, home, .001f);
            assertEquals("terminal at " + r, share, terminal, .001f);
        }
    }

    @Test
    public void sharedRowsBlendByHomesShare() {
        assertEquals(0f, HomeKeyboardCover.sharedDropPx(0f, 400f), 0f);
        assertEquals(200f, HomeKeyboardCover.sharedDropPx(.5f, 400f), 0f);
        assertEquals(400f, HomeKeyboardCover.sharedDropPx(1f, 400f), 0f);
        assertEquals(400f, HomeKeyboardCover.sharedDropPx(2f, 400f), 0f);
        assertEquals(0f, HomeKeyboardCover.sharedDropPx(.5f, -10f), 0f);
    }

    @Test
    public void imeStandIsEverythingThatRaisesTheContainer() {
        assertEquals(350f, HomeKeyboardCover.imeStandPx(false, 300, 40, 10f), 0f);
        assertEquals(0f, HomeKeyboardCover.imeStandPx(false, 0, 0, 0f), 0f);
        assertEquals(0f, HomeKeyboardCover.imeStandPx(false, 0, 0, -20f), 0f);
    }

    @Test
    public void coverCutIsThePartUnderTheKeyboardsTop() {
        assertEquals(30, HomeKeyboardCover.coverCutPx(1000f, 970f));
        assertEquals(0, HomeKeyboardCover.coverCutPx(900f, 970f));
        assertEquals(30, HomeKeyboardCover.coverCutPx(1000.4f, 970f));
    }
    /**
     * Docked, Home's rest with the keyboard down and the cards apart: a keyboard that wears the
     * foot under the gesture pill is laid out keys and foot, and sinks the container by its keys
     * alone, so Home's cover drops its rows by nothing. The pins stand on the keyboard column's
     * top as the rest's answer draws it, inside the dock's card (Tray) or over the Peek card,
     * and nothing of them is cut at the keyboard's top, on pong's height and the emulator's.
     */
    @Test
    public void dockedHomesPinsStandWholeOnTheirCardWithTheKeyboardDown() {
        for (LayoutFixtures.Fixture base : new LayoutFixtures.Fixture[] {LayoutFixtures.PONG,
                LayoutFixtures.TL16}) {
            for (DockStyle design : new DockStyle[] {DockStyle.PEEK_GLASS, DockStyle.GLASS_TRAY}) {
                LayoutFixtures.Fixture f = base.withFoot(LayoutFixtures.KEPT_BAND_PX,
                    LayoutFixtures.FLUSH_PX).withHomeDesign(design);
                String at = f.name + " " + design + " h=" + f.heightPx;
                RestCards.Rest rest = f.cardsRest(PageRef.HOME, false, false, false);
                RestCards cards = RestCards.of(rest);
                assertTrue(at + " apart", cards.apart());
                int laidOut = rest.measuredKeyboardPx;
                int keys = f.keyboardLaidOutPx();
                int foot = laidOut - keys;
                assertTrue(at + " the keyboard wears the foot", foot > 0);
                // The renderer's drop, from what it reads: the laid-out keyboard, its foot, and
                // the container's share with the keyboard down.
                float share = StackScene.keyboardTranslationPx(0f, keys);
                float drop = HomeKeyboardCover.rowsDropPx(false, laidOut, foot, share);
                assertEquals(at + " drop", cards.rowsDropPx(), drop, 0f);
                assertEquals(at + " drop", 0f, drop, 0f);
                ChromeShape.Box column = cards.laidOut(RestCards.StackView.KEYBOARD_COLUMN);
                assertNotNull(at, column);
                float keyboardTop = column.top + share;
                float pinsBottom = column.top + share + drop;
                assertEquals(at + " cut", 0, HomeKeyboardCover.coverCutPx(pinsBottom, keyboardTop));
                RestCards.Card card = cards.card(design == DockStyle.PEEK_GLASS
                    ? RestCards.CardId.PEEK : RestCards.CardId.DOCK);
                assertNotNull(at + " card", card);
                float visibleBottom = Math.min(card.box.bottom, f.heightPx);
                assertTrue(at + " pins " + pinsBottom + " inside " + card.box,
                    pinsBottom > card.box.top && pinsBottom <= visibleBottom);
            }
        }
    }
}
