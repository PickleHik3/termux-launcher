package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.core.graphics.ColorUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerSectionSwitchGlassTest {

    @Test
    public void glassIsWantedOnlyForDefaultWhileOpen() {
        assertTrue(AppDrawerSectionSwitchGlass.wanted(AppDrawerViewType.DEFAULT, true));
        assertFalse(AppDrawerSectionSwitchGlass.wanted(AppDrawerViewType.DEFAULT, false));
        for (AppDrawerViewType type : AppDrawerViewType.values()) {
            if (type == AppDrawerViewType.DEFAULT) continue;
            assertFalse(type.name(), AppDrawerSectionSwitchGlass.wanted(type, true));
        }
    }

    @Test
    public void radiusIsHalfTheHeight() {
        assertEquals(60f, AppDrawerSectionSwitchGlass.radiusPx(120), 0f);
        assertEquals(0f, AppDrawerSectionSwitchGlass.radiusPx(-4), 0f);
    }

    private static final int VEIL = 0x54000000;

    @Test
    public void tintLiftsTheCardsOwnTint() {
        for (int base : new int[] {0xFF202020, 0xFFF0F0F0}) {
            int plane = ColorUtils.setAlphaComponent(base, Math.round(255 * 0.6f));
            int card = ColorUtils.compositeColors(plane, VEIL);
            int tint = AppDrawerSectionSwitchGlass.tint(VEIL, base, 0.6f);
            assertTrue(ColorUtils.calculateLuminance(tint) > ColorUtils.calculateLuminance(card));
        }
    }

    @Test
    public void darkCardStaysDarkAndLightCardStaysLight() {
        int dark = AppDrawerSectionSwitchGlass.tint(VEIL, 0xFF202020, 0.6f);
        int light = AppDrawerSectionSwitchGlass.tint(VEIL, 0xFFF0F0F0, 0.6f);
        assertTrue(ColorUtils.calculateLuminance(dark) < 0.3);
        assertTrue(ColorUtils.calculateLuminance(light) > 0.5);
    }

    @Test
    public void tintTakesTheOpacityAndIgnoresTheBaseAlpha() {
        assertEquals(AppDrawerSectionSwitchGlass.tint(VEIL, 0xFF202020, 0.6f),
            AppDrawerSectionSwitchGlass.tint(VEIL, 0x80202020, 0.6f));
        assertTrue((AppDrawerSectionSwitchGlass.tint(VEIL, 0xFF202020, 0.9f) >>> 24)
            > (AppDrawerSectionSwitchGlass.tint(VEIL, 0xFF202020, 0.3f) >>> 24));
    }
}
