package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/**
 * A seam drag moves the dock over new wallpaper every frame. Measured afresh, each move of its
 * even-out matrix booked a whole {@link ChromeRenderer#SCOPE_APPLY_THIS_FRAME} commit in that
 * frame (pong 2026-11-03: 19-28 ms per drag frame). Held for the travel, the band keeps its matrix
 * and books nothing; the settle after the landing measures it once where it rests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutSeamHoldTest {

    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;
    private static final GlassBackdropCache.Band DOCK = GlassBackdropCache.Band.DOCK;
    /** The dock over Home's wallpaper, and the same dock a few frames into the slide. */
    private static final Rect HOME = new Rect(0, 2000, 1080, 2200);
    private static final Rect MID_SLIDE = new Rect(0, 1900, 1080, 2100);

    private FakeChromeSurfaces surfaces;
    private ChromeRenderer chrome;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.dim = Color.TRANSPARENT;
        chrome = new ChromeRenderer(surfaces, null);
        int dark = 0xFF2A3140;
        int light = 0xFFB7C9E9;
        chrome.ink().backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return screenRect.top >= HOME.top ? dark : light;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                return screenRect.top >= HOME.top
                    ? new GlassBackdropCache.Lightness(13.4d, 30.2d, 63.8d, dark)
                    : new GlassBackdropCache.Lightness(57.86d, 80.58d, 94.69d, light);
            }
        });
        EvenOut.Ceiling[] ceilings = MaterialTerminalColorScheme.evenOutCeilings(
            RuntimeEnvironment.getApplication(), LEVEL);
        chrome.ink().setBandEvenOutPolicy(DOCK, new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0],
            ceilings[1], ChromeInk.Polarity.PALE_INK));
    }

    private static void idle() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
    }

    private static boolean same(@Nullable ColorMatrix a, @Nullable ColorMatrix b) {
        return Arrays.equals(a == null ? null : a.getArray(), b == null ? null : b.getArray());
    }

    @Test
    public void aSeamDragFrameOverNewGroundBooksNoCommitAndKeepsItsMatrix() {
        ColorMatrix atRest = chrome.ink().bandEvenOutMatrix(DOCK, new Rect(HOME));
        assertNotNull("the dock is evened out over Home", atRest);
        idle();
        assertFalse(chrome.isCommitPending());

        // Unheld, the same move re-measures and books a whole apply: the cost being removed.
        ColorMatrix unheld = chrome.ink().bandEvenOutMatrix(DOCK, new Rect(MID_SLIDE));
        assertFalse("the two grounds even out differently", same(atRest, unheld));
        assertTrue("a moved matrix books a commit", chrome.isCommitPending());
        idle();
        chrome.ink().bandEvenOutMatrix(DOCK, new Rect(HOME));
        idle();

        chrome.holdEvenOut();
        ColorMatrix held = chrome.ink().bandEvenOutMatrix(DOCK, new Rect(MID_SLIDE));
        assertTrue("held, the band keeps the matrix it stands on", same(atRest, held));
        assertFalse("held, a drag frame books no SCOPE_APPLY_THIS_FRAME commit",
            chrome.isCommitPending());
        assertFalse(chrome.isRenderSyncPending());
    }

    @Test
    public void theSettleAfterTheLandingMeasuresOnceWhereTheBandRests() {
        ColorMatrix atRest = chrome.ink().bandEvenOutMatrix(DOCK, new Rect(HOME));
        idle();
        chrome.holdEvenOut();
        chrome.ink().bandEvenOutMatrix(DOCK, new Rect(MID_SLIDE));
        assertFalse(chrome.isCommitPending());

        chrome.settleEvenOut();
        assertFalse(chrome.isEvenOutHeld());
        assertTrue("one pass is booked to measure the band where it rests",
            chrome.isCommitPending());
        idle();
        ColorMatrix landed = chrome.ink().bandEvenOutMatrix(DOCK, new Rect(MID_SLIDE));
        assertFalse("released, the band takes the ground it rests on", same(atRest, landed));
    }

    @Test
    public void aSettleWithNothingHeldOffItsGroundBooksNothing() {
        ColorMatrix atRest = chrome.ink().bandEvenOutMatrix(DOCK, new Rect(HOME));
        idle();
        chrome.holdEvenOut();
        assertSame(atRest, chrome.ink().bandEvenOutMatrix(DOCK, new Rect(HOME)));
        chrome.settleEvenOut();
        assertFalse(chrome.isCommitPending());
        assertFalse(chrome.isRenderSyncPending());
    }
}
