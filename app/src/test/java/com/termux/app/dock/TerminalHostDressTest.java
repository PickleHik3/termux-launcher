package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.LayoutFixtures;
import com.termux.app.place.RestCards;
import com.termux.app.wall.PageRef;

import org.junit.Test;

/** The Terminal's own host wears its Style's glass: a capsule under Floating, a slice under Docked. */
public class TerminalHostDressTest {

    private static RestCards.Card terminalCard(boolean floating) {
        RestCards cards = RestCards.of(LayoutFixtures.TL16.cardsRest(PageRef.TERMINAL, false,
            floating));
        assertTrue(cards.apart());
        RestCards.Card card = cards.cardOn(RestCards.StackView.TERMINAL_HOST);
        assertNotNull(card);
        return card;
    }

    /** #305: Floating with rows beside the keys, the glass and rim are rounded with the card. */
    @Test public void floatingTheGlassIsRoundedAtTheCardsRadius() {
        RestCards.Card card = terminalCard(true);
        assertTrue(card.corners.topLeft > 0f);
        assertEquals(card.corners.topLeft, TerminalHostDress.capsuleRadiusPx(card), 0f);
    }

    /** #304: Docked keeps radius 0 for its glass; only the outline rounds a screen corner. */
    @Test public void dockedTheGlassKeepsRadiusZero() {
        assertEquals(0f, TerminalHostDress.capsuleRadiusPx(terminalCard(false)), 0f);
    }
}
