package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;

import org.junit.Test;

public class MaximisePolicyTest {

    private static PlaceLayout layout() {
        return new PlaceLayout(PlaceLayout.Edge.TOP, PlaceLayout.RowPlacement.values()[0], true,
            PlaceLayout.Edge.BOTTOM, PlaceLayout.RowPlacement.values()[0],
            PlaceLayout.KeyboardMode.values()[0], PlaceLayout.KeyboardForm.values()[0], 4, 4);
    }

    @Test public void maximisedHidesTheRowTheAppsAndTheAzBar() {
        PlaceLayout out = MaximisePolicy.layoutFor(layout(), false, false);
        assertTrue(out.slot(Element.STATUS).hidden);
        assertTrue(out.slot(Element.APPS).hidden);
        assertTrue(out.slot(Element.AZ).hidden);
        assertTrue("no keyboard, no extra keys", out.slot(Element.EXTRA_KEYS).hidden);
    }

    @Test public void theExtraKeysStayWhileTheKeyboardIsUp() {
        PlaceLayout in = layout();
        PlaceLayout out = MaximisePolicy.layoutFor(in, false, true);
        assertTrue(out.slot(Element.STATUS).hidden);
        assertTrue(out.slot(Element.EXTRA_KEYS).hidden == in.slot(Element.EXTRA_KEYS).hidden);
    }

    @Test public void theRowComesBackWhileTheBarsAreRevealed() {
        PlaceLayout in = layout();
        PlaceLayout out = MaximisePolicy.layoutFor(in, true, false);
        assertTrue(out.slot(Element.STATUS).hidden == in.slot(Element.STATUS).hidden);
        assertTrue(out.slot(Element.APPS).hidden);
    }

    @Test public void anAlreadyHiddenLayoutComesBackAsItIs() {
        PlaceLayout once = MaximisePolicy.layoutFor(layout(), false, false);
        assertSame(once, MaximisePolicy.layoutFor(once, false, false));
    }

    @Test public void theFullScreenSettingOwnsTheSystemBars() {
        assertTrue(MaximisePolicy.hidesSystemBars(true, false));
        assertFalse("the setting already hides them", MaximisePolicy.hidesSystemBars(true, true));
        assertFalse(MaximisePolicy.hidesSystemBars(false, false));
    }

    @Test public void maximisedEndsWhenThePageChangesAwayOrTheWindowGoes() {
        assertFalse(MaximisePolicy.ends("2:1", "2:1", false));
        assertTrue("another window in front", MaximisePolicy.ends("2:1", "2:0", false));
        assertTrue("a Home page in front", MaximisePolicy.ends("2:1", "1:-1", false));
        assertTrue("the window closed", MaximisePolicy.ends("2:1", "2:1", true));
    }
}
