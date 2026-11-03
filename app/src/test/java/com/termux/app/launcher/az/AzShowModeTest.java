package com.termux.app.launcher.az;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.cards.DockCard;

import org.junit.Test;

/** The A–Z strip's Show modes and glass switch (editor-redesign §4). */
public class AzShowModeTest {

    @Test
    public void readsTheDockCardsKeysAndValues() {
        assertEquals(DockCard.KEY_AZ_SHOW, AzShowMode.KEY);
        assertEquals(DockCard.KEY_AZ_GLASS_BACKGROUND, AzShowMode.KEY_GLASS);
        assertEquals(DockCard.DEFAULT_AZ_GLASS_BACKGROUND, AzShowMode.DEFAULT_GLASS);
        String[] values = new String[AzShowMode.values().length];
        for (int i = 0; i < values.length; i++) values[i] = AzShowMode.values()[i].storageValue;
        assertArrayEquals("the Show row's index is the mode's ordinal",
            DockCard.AZ_SHOW_VALUES, values);
    }

    @Test
    public void anUnknownOrMissingValueIsShown() {
        assertEquals(AzShowMode.SHOWN, AzShowMode.parse(null));
        assertEquals(AzShowMode.SHOWN, AzShowMode.parse("minimised"));
        assertEquals(AzShowMode.FAINT, AzShowMode.parse("faint"));
        assertEquals(AzShowMode.PULL_TAB, AzShowMode.parse("pull_tab"));
        assertEquals(AzShowMode.SHOWN, AzShowMode.of(null));
        assertFalse(AzShowMode.glassOf(null));
    }

    @Test
    public void onlyThePullTabFreesTheBand() {
        assertTrue(AzShowMode.SHOWN.claimsBand());
        assertTrue(AzShowMode.FAINT.claimsBand());
        assertFalse(AzShowMode.PULL_TAB.claimsBand());
    }

    @Test
    public void faintRestsAtAboutAThirdAndComesUpForTheScrub() {
        assertEquals(0.3f, AzShowMode.FAINT.restingAlpha(), 0f);
        assertEquals(1f, AzShowMode.FAINT.lettersAlpha(true), 0f);
        assertEquals(0.3f, AzShowMode.FAINT.lettersAlpha(false), 0f);
        assertEquals(1f, AzShowMode.SHOWN.lettersAlpha(false), 0f);
        assertEquals("the tab's letters are only out while held", 1f,
            AzShowMode.PULL_TAB.restingAlpha(), 0f);
    }

    @Test
    public void glassOffTakesOnlyTheStripsOwnCapsule() {
        assertTrue(AzShowMode.ownGlassShown(true, false));
        assertFalse(AzShowMode.ownGlassShown(false, false));
        assertFalse("a shared sheet is not the strip's to draw",
            AzShowMode.ownGlassShown(true, true));
    }
}
