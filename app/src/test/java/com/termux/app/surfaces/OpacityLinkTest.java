package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.surfaces.AppearanceLooks.Control;

import org.junit.Test;

/** The keyboard's opacity link: the arithmetic that moves the partner. */
public class OpacityLinkTest {

    @Test
    public void theTwoOpacitiesArePartnersAndNothingElseIs() {
        assertEquals(Control.KEY_OPACITY, OpacityLink.partner(Control.OPACITY));
        assertEquals(Control.OPACITY, OpacityLink.partner(Control.KEY_OPACITY));
        for (Control control : Control.values()) {
            if (control != Control.OPACITY && control != Control.KEY_OPACITY)
                assertNull(control.name(), OpacityLink.partner(control));
        }
    }

    @Test
    public void thePartnerMovesByTheSameAmountEitherWay() {
        assertEquals(70, OpacityLink.follow(40, 50, 60, Control.KEY_OPACITY));
        assertEquals(50, OpacityLink.follow(40, 30, 60, Control.KEY_OPACITY));
        assertEquals("Key opacity moving BG opacity", 25,
            OpacityLink.follow(80, 65, 40, Control.OPACITY));
        assertEquals("no move, no change", 60, OpacityLink.follow(40, 40, 60, Control.OPACITY));
    }

    @Test
    public void thePartnerStopsAtItsEndsAndComesBackByTheSharedOffset() {
        assertEquals(Control.KEY_OPACITY.max,
            OpacityLink.follow(40, 90, 80, Control.KEY_OPACITY));
        assertEquals(Control.OPACITY.min, OpacityLink.follow(60, 0, 20, Control.OPACITY));
        // Measured from where the move began: past the end and back, the offset holds.
        assertEquals(85, OpacityLink.follow(40, 45, 80, Control.KEY_OPACITY));
    }
}
