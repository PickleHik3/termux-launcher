package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Robolectric only for {@code android.graphics.Color}, which {@code ColorUtils} blends with. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DeckLookTest {

    private static final int PANEL = 0xCC202428;
    private static final int PRIMARY = 0xFF6750A4;

    @Test public void noneHasNoDeck() {
        assertNull(DeckLook.of(PaneRetroStyle.NONE, PANEL, PRIMARY));
    }

    @Test public void everyEffectHasADeck() {
        for (PaneRetroStyle style : PaneRetroStyle.values())
            if (style != PaneRetroStyle.NONE)
                assertNotNull(style.id(), DeckLook.of(style, PANEL, PRIMARY));
    }

    @Test public void palettePerStyle() {
        assertLights(PaneRetroStyle.CRT, 0xFF7CF5D8, 0xFFFFB020, 0xFF1A1200);
        assertLights(PaneRetroStyle.TFT, 0xFF7CF5D8, 0xFFFFB020, 0xFF1A1200);
        assertLights(PaneRetroStyle.CRT_GREEN, 0xFF8CF0A4, 0xFF6EF08E, 0xFF04200C);
        assertLights(PaneRetroStyle.CRT_AMBER, 0xFFFFBE5C, 0xFFFFB020, 0xFF1A1200);
    }

    @Test public void materialLightsInTheOpaquePrimary() {
        DeckLook deck = DeckLook.of(PaneRetroStyle.CRT_MATERIAL, PANEL, 0x806750A4);
        assertEquals(0xFF6750A4, deck.display);
        assertEquals(0xFF6750A4, deck.lit);
    }

    @Test public void materialInkFlipsWithThePrimary() {
        assertEquals(0xFFFFFFFF, DeckLook.of(PaneRetroStyle.CRT_MATERIAL, PANEL, 0xFF3A2C80).litInk);
        assertEquals(0xFF000000, DeckLook.of(PaneRetroStyle.CRT_MATERIAL, PANEL, 0xFFD0BCFF).litInk);
    }

    @Test public void bedIsOpaqueAndKeysStepUp() {
        DeckLook deck = DeckLook.of(PaneRetroStyle.CRT, PANEL, PRIMARY);
        assertEquals(0xFF202428, deck.bed);
        assertEquals(0xFF, deck.key >>> 24);
        assertTrue(luma(deck.bedTop) > luma(deck.bed));
        assertTrue(luma(deck.key) > luma(deck.bed));
        assertTrue(luma(deck.keyTop) > luma(deck.key));
        assertTrue(luma(deck.keyFoot) < luma(deck.key));
        assertTrue(luma(deck.litFoot) < luma(deck.lit));
    }

    private static void assertLights(PaneRetroStyle style, int display, int lit, int ink) {
        DeckLook deck = DeckLook.of(style, PANEL, PRIMARY);
        assertEquals(style.id(), display, deck.display);
        assertEquals(style.id(), lit, deck.lit);
        assertEquals(style.id(), ink, deck.litInk);
    }

    private static double luma(int c) {
        return 0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF);
    }
}
