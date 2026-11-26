package com.termux.app.surfaces;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;

import org.junit.Test;

public class EditPagesChipTest {

    @Test
    public void noCardLinksToPagesWhateverTheOverviewState() {
        // Overview and the editor do not link (#354).
        for (boolean overviewUp : new boolean[] {false, true}) {
            for (boolean homeShown : new boolean[] {false, true}) {
                Offers offers = new Offers(true, true, homeShown, overviewUp);
                for (EditorCard card : EditorCard.values()) {
                    for (int tier = 1; tier <= 3; tier++) {
                        assertFalse(card + " tier " + tier,
                            EditorRows.tier(card, offers, tier).contains(Row.LINK_PAGES));
                    }
                }
            }
        }
        assertTrue(EditorRows.tier(EditorCard.HOME, new Offers(true, true, true, false), 3)
            .contains(Row.LINK_GESTURES));
    }
}
