package com.termux.app.launcher.popup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;
import com.termux.app.material.M3;

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
    /** {@code colorOnSurface} over the light and the night panel. */
    private static final int LIGHT_ON_SURFACE = 0xFF1C1B1F;
    private static final int NIGHT_ON_SURFACE = 0xFFE6E1E5;

    @After
    public void clearSnapshot() {
        ChromeShade.clear();
    }

    /** Over the light panel the armed row has to be findable against its unselected neighbour. */
    @Test
    public void theArmedRowLeansOffTheLightPanel() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        int armed = MenuRowFactory.armedFill(LIGHT_ON_SURFACE);
        assertTrue("armed row separates from the bare panel by "
                + ChromeShade.separation(armed, LIGHT_GLASS),
            ChromeShade.separation(armed, LIGHT_GLASS) >= ChromeShade.TARGET_STATE);
    }

    /** Over the dark panel the armed row is the M3 dragged state layer of onSurface, 16%. */
    @Test
    public void theArmedRowIsTheDraggedStateLayer() {
        ChromeShade.note(ChromeInk.Polarity.PALE_INK, NIGHT_GLASS);
        int armed = MenuRowFactory.armedFill(NIGHT_ON_SURFACE);
        assertEquals(Math.round(255f * M3.STATE_DRAGGED), (armed >>> 24));
        assertEquals(NIGHT_ON_SURFACE & 0x00FFFFFF, armed & 0x00FFFFFF);
    }
}
