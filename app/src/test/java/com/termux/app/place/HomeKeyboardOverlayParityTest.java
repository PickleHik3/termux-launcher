package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.dock.DockGeometry;
import com.termux.app.place.RestCards.Card;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.wall.PageRef;

import org.junit.Test;

/**
 * On Home the keyboard is an overlay owned by its page (the crossing rule's Q1, 2026-11-25): it
 * covers the dock and what stands under it, and no other element's rest changes for it. The A–Z
 * bar on a side edge keeps its length under it in both owners of that number: the geometry pass's
 * side reach ({@link StackLayout.Result#sideStackOverlapPx}, the rail's padding) and the rest's
 * A–Z card ({@link RestCards}). One policy, {@link HomeKeyboardCover#sideRailKeyboardPx}.
 */
public class HomeKeyboardOverlayParityTest {

    private static final boolean[] BOOLS = {false, true};

    @Test public void theRailEndsAboveAKeyboardThatLiftsAndUnderOneThatCovers() {
        assertEquals(700, HomeKeyboardCover.sideRailKeyboardPx(true, 700));
        assertEquals(0, HomeKeyboardCover.sideRailKeyboardPx(false, 700));
        assertEquals(0, HomeKeyboardCover.sideRailKeyboardPx(true, -3));
    }

    @Test public void aSideAzBarOnHomeKeepsItsLengthUnderTheKeyboard() {
        int checked = 0;
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : BOOLS) {
            Card down = RestCards.of(f.cardsRest(PageRef.HOME, false, floating)).card(CardId.AZ);
            Card up = RestCards.of(f.cardsRest(PageRef.HOME, true, floating)).card(CardId.AZ);
            if (down == null || up == null || down.box.width() >= down.box.height()) continue;
            String at = f.name + " " + f.homeDesign + (floating ? " FLOATING" : " DOCKED");
            assertEquals(at + " top", down.box.top, up.box.top, 0.5f);
            assertEquals(at + " bottom", down.box.bottom, up.box.bottom, 0.5f);
            checked++;
        }
        assertTrue("a side A–Z bar on Home was checked", checked > 0);
    }

    @Test public void theSideReachOnHomeIsTheKeyboardDownRestsUnderACoveringKeyboard() {
        int checked = 0;
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : BOOLS) {
            StackLayout.Input upIn = f.pass(PageRef.HOME, true, floating);
            if (upIn.keyboardHeightPx <= 0
                || HomeKeyboardCover.liftsRows(upIn.dock.rows.kind, upIn.homeKeyboardAlwaysOn))
                continue;
            StackLayout.Result up = StackLayout.layout(upIn, new DockGeometry());
            StackLayout.Result down = StackLayout.layout(f.pass(PageRef.HOME, false, floating),
                new DockGeometry());
            assertNotNull(up);
            assertEquals(f.name + " " + f.homeDesign + (floating ? " FLOATING" : " DOCKED"),
                down.sideStackOverlapPx, up.sideStackOverlapPx);
            checked++;
        }
        assertTrue("a covering keyboard on Home was checked", checked > 0);
    }
}
