package com.termux.app.launcher.popup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Which row of an anchored menu is armed.
 *
 * <p>An unselected row draws nothing — it is the bare panel — so the only thing saying where the
 * finger is, is how far the armed row leans off that panel. The lean was always toward white,
 * which over a light panel walks it into the panel instead of off it; and the panel's opacity is a
 * user preference, so there may be very little panel to lean off.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class MenuRowShadeTest {

    /** {@code termux_surface_panel_high}, light and night: what a menu panel is tinted from. */
    private static final int LIGHT_GLASS = 0xFFE1E7F2;
    private static final int NIGHT_GLASS = 0xFF202837;
    /** The tint base a launcher menu passes in: the panel's own surface colour. */
    private static final int LIGHT_TINT_BASE = 0xFFE1E7F2;
    private static final int NIGHT_TINT_BASE = 0xFF202837;

    @After
    public void clearSnapshot() {
        ChromeShade.clear();
    }

    /** Over the light panel the armed row has to be findable against its unselected neighbour. */
    @Test
    public void theArmedRowLeansOffTheLightPanel() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int armed = MenuRowFactory.armedFill(LIGHT_TINT_BASE);
        int stroke = MenuRowFactory.armedStroke(LIGHT_TINT_BASE);
        assertTrue("armed row separates from the bare panel by "
                + ChromeShade.separation(armed, LIGHT_GLASS),
            ChromeShade.separation(armed, LIGHT_GLASS) >= ChromeShade.TARGET_STATE);
        assertTrue("armed row's edge separates by " + ChromeShade.separation(stroke, LIGHT_GLASS),
            ChromeShade.separation(stroke, LIGHT_GLASS) >= ChromeShade.TARGET_RIM);
    }

    /**
     * The bug as it shipped: leaning toward white over a light panel is leaning toward the panel.
     * The restated row is further off the panel than the one that produced the report.
     */
    @Test
    public void leaningTowardWhiteWasLeaningIntoTheLightPanel() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int restated = MenuRowFactory.armedFill(LIGHT_TINT_BASE);
        int asShipped = MenuRowFactory.blendColors(
            (0x8C << 24) | (LIGHT_TINT_BASE & 0x00FFFFFF), 0x66FFFFFF, 0.35f);
        assertTrue("as shipped the armed row separated by "
                + ChromeShade.separation(asShipped, LIGHT_GLASS),
            ChromeShade.separation(asShipped, LIGHT_GLASS) < 1.1d);
        assertTrue(ChromeShade.separation(restated, LIGHT_GLASS)
            > ChromeShade.separation(asShipped, LIGHT_GLASS));
    }

    /** Over the dark panel nothing moves: the armed row is exactly the blend that shipped. */
    @Test
    public void theDarkPanelKeepsTheRowItAlwaysHad() {
        ChromeShade.note(ChromeInk.Polarity.PALE_INK, NIGHT_GLASS);
        assertEquals(MenuRowFactory.blendColors(
                (0x8C << 24) | (NIGHT_TINT_BASE & 0x00FFFFFF), 0x66FFFFFF, 0.35f),
            MenuRowFactory.armedFill(NIGHT_TINT_BASE));
        assertEquals(MenuRowFactory.blendColors(
                0x99FFFFFF, (0xFF << 24) | (NIGHT_TINT_BASE & 0x00FFFFFF), 0.5f),
            MenuRowFactory.armedStroke(NIGHT_TINT_BASE));
    }
}
