package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.ColorMatrix;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import android.app.Application;

/**
 * A live picture's colours equal the stored frame's for the same wallpaper and the same inputs:
 * the stored frame is the wallpaper blurred, the live recording is the wallpaper under the wall's
 * dim, and the parity takes the dim back out. The glass's colour is the program's, on both (#321).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Application.class)
public class LiveStoredParityTest {

    /** A frame tint in a warm hue at a third of the way, as the Tint amount bakes one. */
    private static final int TINT = GlassFrameTint.of(60, 10, 0xFF8A4B2C, 0xFFE07A3A);
    private static final int[] DIMS = {0, 0x33000000, 0x8C000000, 0x4D101820, 0xE6000000};
    private static final int[] VIBRANCY = {70, 100, 130, 150};
    private static final int[][] WALLPAPER = {
        {200, 220, 240}, {40, 90, 160}, {128, 128, 128}, {250, 180, 30}, {10, 12, 16}};

    /** The stored bake as the cache made it before: setSaturation, then the tint post-concat. */
    private static float[] storedBakeAsBuilt(int vibrancy, int tint) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(GlassVibrancy.saturation(vibrancy));
        if (tint != GlassFrameTint.NONE) matrix.postConcat(new ColorMatrix(GlassFrameTint.matrix(tint)));
        return matrix.getArray();
    }

    /** What the launcher-drawn wallpaper puts in the window: the pixel, the dim source-over it. */
    private static float[] dimmed(int[] rgb, int dim) {
        float a = (dim >>> 24) / 255f;
        return new float[] {
            rgb[0] * (1f - a) + ((dim >> 16) & 0xFF) * a,
            rgb[1] * (1f - a) + ((dim >> 8) & 0xFF) * a,
            rgb[2] * (1f - a) + (dim & 0xFF) * a};
    }

    /** Saturation and the frame tint both keep it; only the dim moves it. */
    private static float luma(float[] rgb) {
        return 0.213f * rgb[0] + 0.715f * rgb[1] + 0.072f * rgb[2];
    }

    @Test
    public void theTintUnderTestIsReal() {
        assertNotEquals(GlassFrameTint.NONE, TINT);
    }

    @Test
    public void theColourIsTheMatrixTheStoredFrameUsedToBeBakedThrough() {
        for (int vibrancy : VIBRANCY) {
            for (int tint : new int[] {GlassFrameTint.NONE, TINT}) {
                assertArrayEquals("vibrancy " + vibrancy + " tint " + Integer.toHexString(tint),
                    storedBakeAsBuilt(vibrancy, tint), GlassVibrancy.bakeMatrix(vibrancy, tint),
                    1e-5f);
            }
        }
    }

    @Test
    public void withoutADimThereIsNoFilter() {
        // The glass's colour is the program's now (#321): the live picture adds none.
        assertNull(LiveStoredParity.matrix(0));
    }

    @Test
    public void theLivePixelIsTheStoredPixelForEveryDim() {
        // Both routes hand the program the raw blur; the program then colours both through one
        // matrix, so the stored and the live glass match for every Vibrancy and tint.
        float[] stored = new float[3];
        float[] live = new float[3];
        for (int dim : DIMS) {
            float[] undim = LiveStoredParity.matrix(dim);
            for (int vibrancy : VIBRANCY) {
                float[] colour = GlassVibrancy.bakeMatrix(vibrancy, TINT);
                for (int[] rgb : WALLPAPER) {
                    float[] seen = dimmed(rgb, dim);
                    float[] got = undim == null ? seen
                        : LiveStoredParity.apply(undim, seen[0], seen[1], seen[2]);
                    String at = "dim " + Integer.toHexString(dim) + " vibrancy " + vibrancy;
                    if ((dim >>> 24) / 255f > LiveStoredParity.MAX_UNDIM_ALPHA) {
                        // Past the cap the picture keeps the rest of the dim: never lighter.
                        assertTrue(at, luma(got) <= luma(new float[] {rgb[0], rgb[1], rgb[2]}) + 0.5f);
                        continue;
                    }
                    GlassRefraction.colourOf(colour, rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f,
                        1f, stored);
                    GlassRefraction.colourOf(colour, got[0] / 255f, got[1] / 255f, got[2] / 255f,
                        1f, live);
                    assertArrayEquals(at, stored, live, 0.05f / 255f * 4f);
                }
            }
        }
    }

    @Test
    public void aPictureTakesOnlyTheDimBackOut() {
        View surface = new View(RuntimeEnvironment.getApplication());
        LivePicture widget = new LivePicture(surface, 20f);
        assertNull("nothing to match yet", widget.parityFilter());
        widget.setRecordedDim(0x66000000);
        assertNotNull("the wall's dim comes back out", widget.parityFilter());
        widget.setRecordedDim(0);
        assertNull(widget.parityFilter());
    }

    @Test
    public void theRadiusIsTheStoredFramesOwn() {
        // The live blur lands on the stored frame's sigma (0.4 r + 0.6 f full-scale) at the same px.
        for (float stored : new float[] {18.375f, 42f, 60f, 94.5f}) {
            float f = Math.max(1f, (float) Math.ceil(stored / LivePicture.STORED_SCRIPT_CAP_PX));
            float live = LivePicture.SKIA_SIGMA_SLOPE * LivePicture.renderEffectRadius(stored)
                + LivePicture.SKIA_SIGMA_BASE * LivePicture.SCALE;
            assertEquals(0.4f * stored + 0.6f * f, live, 1e-3f);
        }
    }

    @Test
    public void theSceneCardWearsTheColourThenTheFrost() {
        // The departing card's ground has no program: over the raw scene it takes the glass's
        // colour, then the frost, as the slab's program and paint do, so the card and the slab
        // agree at the first frame of the slide.
        for (int opacity : new int[] {100, 40, 2}) {
            for (int vibrancy : VIBRANCY) {
                float[] card = GlassVibrancy.groundMatrix(opacity, vibrancy, TINT);
                float[] colour = GlassVibrancy.bakeMatrix(vibrancy, TINT);
                float[] frost = GlassFilters.frostMatrix(opacity).getArray();
                assertArrayEquals(GlassVibrancy.concat(frost, colour), card, 1e-5f);
                for (int[] px : WALLPAPER) {
                    float[] viaCard = LiveStoredParity.apply(card, px[0], px[1], px[2]);
                    float[] mid = LiveStoredParity.apply(colour, px[0], px[1], px[2]);
                    float[] viaSlab = LiveStoredParity.apply(frost, mid[0], mid[1], mid[2]);
                    assertArrayEquals(viaSlab, viaCard, 1e-2f);
                }
            }
        }
    }

    @Test
    public void aGroundWithNothingToColourIsTheFrostAlone() {
        assertEquals(GlassFilters.frost(100),
            GlassVibrancy.groundFilter(100, true, 100, GlassFrameTint.NONE));
        assertEquals("a picture that already carries the colour",
            GlassFilters.frost(100), GlassVibrancy.groundFilter(100, false, 150, TINT));
        assertSame("one filter per colour, not per call",
            GlassVibrancy.groundFilter(100, true, 130, TINT),
            GlassVibrancy.groundFilter(100, true, 130, TINT));
    }
}
