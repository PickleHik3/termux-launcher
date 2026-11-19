package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * #283: the status bar's ink for the page a crossing arrives on, probed over that page's ground
 * from the page it leaves, is the ink the landing measures there, and the probe records nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ChromeInkProbeTest {

    /** The Terminal's dim, 70%. */
    private static final int TERMINAL_DIM = 0xB3000000;
    private static final int BRIGHT = 0xFFD8E070;
    private static final Rect BAR = new Rect(0, 0, 1080, 136);
    private static final int DARK = 0xFF1A1C1E;
    private static final int PALE = 0xFFE2E2E6;

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.dim = Color.TRANSPARENT;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect rect) {
                return BRIGHT;
            }
        });
        // The bar's frost is up: with the gate on its glass still shows the page's ground.
        ink.noteBandFrame(GlassBackdropCache.Band.STATUS_BAR, true);
    }

    private OnGlass.Resolution onGlass() {
        return ink.onGlass(GlassBackdropCache.Band.STATUS_BAR, new Rect(BAR), DARK, PALE,
            OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void theProbeFromHomeIsWhatTheTerminalMeasures() {
        if (!GlassGround.ON) return;
        OnGlass.Resolution home = onGlass();
        OnGlass.Resolution probed = ink.probeOnGlass(GlassBackdropCache.Band.STATUS_BAR,
            new Rect(BAR), DARK, PALE, OnGlass.TARGET_BODY_TEXT, TERMINAL_DIM);
        assertNotEquals(home.surface, probed.surface);

        surfaces.dim = TERMINAL_DIM;
        ink.invalidate();
        OnGlass.Resolution landed = onGlass();
        assertEquals(landed.surface, probed.surface);
        assertEquals(landed.ink, probed.ink);
        assertEquals(landed.veil, probed.veil);
    }

    @Test
    public void theProbeRecordsNothing() {
        OnGlass.Resolution before = onGlass();
        ChromeInk.Polarity polarity = ink.polarity();
        ink.probeOnGlass(GlassBackdropCache.Band.STATUS_BAR, new Rect(BAR), DARK, PALE,
            OnGlass.TARGET_BODY_TEXT, TERMINAL_DIM);
        assertEquals(polarity, ink.polarity());
        OnGlass.Resolution after = onGlass();
        assertEquals(before.surface, after.surface);
        assertEquals(before.ink, after.ink);
        assertEquals(before.veil, after.veil);
    }
}
