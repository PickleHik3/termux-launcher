package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The focus ring's feather, which is the one cue saying which icon a tap will launch.
 *
 * <p>The crisp stroke carries the ring itself at the full accent and reads in either mode; the
 * quarter-strength feather around it is what made the ring findable at a glance, and a quarter of
 * a dark accent over a light band is a much fainter mark than a quarter of a pale one over a dark
 * band.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FocusOutlineShadeTest {

    /** {@code termux_primary}, light and night: the accent the ring is tinted to. */
    private static final int LIGHT_ACCENT = 0xFF345CA8;
    private static final int NIGHT_ACCENT = 0xFFB8C7FF;
    private static final int LIGHT_GLASS = 0xFFE1E7F2;
    private static final int NIGHT_GLASS = 0xFF202837;

    @After
    public void clearSnapshot() {
        ChromeShade.clear();
    }

    /** Over the dark glass the feather is exactly the quarter it always was. */
    @Test
    public void theFeatherIsUnchangedOverDarkGlass() {
        ChromeShade.note(ChromeInk.Polarity.PALE_INK, NIGHT_GLASS);
        assertEquals(0.25f, FocusOutlineRenderer.haloAlpha(NIGHT_ACCENT), 0.005f);
    }

    /** Over the light band it is raised until the feather can actually be found on the surface. */
    @Test
    public void theFeatherIsFoundOnTheLightBand() {
        ChromeShade.note(ChromeInk.Polarity.DARK_INK, LIGHT_GLASS);
        float resolved = FocusOutlineRenderer.haloAlpha(LIGHT_ACCENT);
        assertTrue("the authored quarter already read: "
                + ChromeShade.separation(withAlpha(LIGHT_ACCENT, 64), LIGHT_GLASS),
            ChromeShade.separation(withAlpha(LIGHT_ACCENT, 64), LIGHT_GLASS)
                < ChromeShade.TARGET_RIM);
        assertTrue("feather resolved to " + resolved, resolved > 0.25f);
        int feather = withAlpha(LIGHT_ACCENT, Math.round(resolved * 255f));
        assertTrue("feather separates by " + ChromeShade.separation(feather, LIGHT_GLASS),
            ChromeShade.separation(feather, LIGHT_GLASS) >= ChromeShade.TARGET_RIM);
    }

    /** Nothing has measured the chrome: the ring draws what it always drew. */
    @Test
    public void anUnmeasuredChromeKeepsTheAuthoredFeather() {
        ChromeShade.clear();
        assertEquals(0.25f, FocusOutlineRenderer.haloAlpha(LIGHT_ACCENT), 0.005f);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
