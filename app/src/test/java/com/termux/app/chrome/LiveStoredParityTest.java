package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.ColorMatrix;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A live picture's colours equal the stored frame's for the same wallpaper and the same inputs:
 * the stored frame is the wallpaper blurred with Vibrancy and the frame tint baked in, the live
 * recording is the wallpaper under the wall's dim, and the parity takes the one to the other.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU)
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
    public void theSharedBakeIsTheMatrixTheStoredFrameWasBakedThrough() {
        for (int vibrancy : VIBRANCY) {
            for (int tint : new int[] {GlassFrameTint.NONE, TINT}) {
                assertArrayEquals("vibrancy " + vibrancy + " tint " + Integer.toHexString(tint),
                    storedBakeAsBuilt(vibrancy, tint), GlassVibrancy.bakeMatrix(vibrancy, tint),
                    1e-5f);
            }
        }
    }

    @Test
    public void nothingToDoIsNoFilter() {
        assertNull(LiveStoredParity.matrix(0, 100, GlassFrameTint.NONE, true));
        assertNull("radius 0 is the wallpaper itself: nothing baked",
            LiveStoredParity.matrix(0, 140, TINT, false));
    }

    @Test
    public void withoutADimTheLivePictureTakesTheStoredBakeExactly() {
        for (int vibrancy : VIBRANCY) {
            float[] live = LiveStoredParity.matrix(0, vibrancy, TINT, true);
            assertNotNull(live);
            assertArrayEquals(GlassVibrancy.bakeMatrix(vibrancy, TINT), live, 1e-5f);
        }
    }

    @Test
    public void theLivePixelEqualsTheStoredPixelForEveryDimVibrancyAndTint() {
        for (int dim : DIMS) {
            for (int vibrancy : VIBRANCY) {
                for (int tint : new int[] {GlassFrameTint.NONE, TINT}) {
                    float[] live = LiveStoredParity.matrix(dim, vibrancy, tint, true);
                    float[] stored = GlassVibrancy.bakeMatrix(vibrancy, tint);
                    for (int[] rgb : WALLPAPER) {
                        float[] want = LiveStoredParity.apply(stored, rgb[0], rgb[1], rgb[2]);
                        float[] seen = dimmed(rgb, dim);
                        float[] got = live == null ? seen
                            : LiveStoredParity.apply(live, seen[0], seen[1], seen[2]);
                        String at = "dim " + Integer.toHexString(dim) + " vibrancy " + vibrancy
                            + " tint " + Integer.toHexString(tint);
                        if ((dim >>> 24) / 255f > LiveStoredParity.MAX_UNDIM_ALPHA) {
                            // Past the cap the picture keeps the rest of the dim: never lighter.
                            assertTrue(at, luma(got) <= luma(want) + 0.5f);
                            continue;
                        }
                        assertArrayEquals(at, want, got, 0.05f);
                    }
                }
            }
        }
    }

    @Test
    public void aPictureTakesTheParityAndAPanelNever() {
        View surface = new View(RuntimeEnvironment.getApplication());
        LivePicture widget = new LivePicture(surface, 20f, false);
        assertNull("nothing to match yet", widget.parityFilter());
        widget.setStoredParity(0x66000000, 100, GlassFrameTint.NONE);
        assertNotNull("the wall's dim comes back out", widget.parityFilter());
        widget.setRecordedDim(0);
        assertNull(widget.parityFilter());
        widget.setStoredParity(0, 130, TINT);
        assertNotNull("Vibrancy and the frame tint go in", widget.parityFilter());

        LivePicture panel = new LivePicture(surface, 20f, true);
        panel.setStoredParity(0x66000000, 130, TINT);
        assertNull("a panel matches its old live blur, which saw the dim", panel.parityFilter());
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
}
