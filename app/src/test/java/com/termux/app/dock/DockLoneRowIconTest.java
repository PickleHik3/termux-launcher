package com.termux.app.dock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A pinned-apps row lying down with nothing else in its container. Its bar keeps the height every
 * size has always given it, and its icons fill that bar instead of a fill ratio of it, which left
 * a tall bar around a small icon. A row sharing its container is untouched.
 */
public class DockLoneRowIconTest {

    private static final float[] DENSITIES = {1f, 2f, 2.625f, 2.75f, 3f, 3.5f};
    private static final boolean[] STYLES = {false, true};
    private static final boolean[] BOOLS = {false, true};

    private static DockLayout layout(boolean capsule, boolean alone, boolean ticks, float scale,
                                     float density) {
        return DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(capsule)
            .appsRowEnabledPref(true)
            .azRowEnabledPref(!alone)
            .appsRowAlone(alone)
            .appsRowPageStripShown(ticks)
            .density(density)
            .barHeightScale(scale)
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .build());
    }

    /** min, the shipped default, max: the three sizes the device check walks. */
    private static float[] sizes(boolean capsule) {
        return new float[] {DockLayoutPolicy.minUsefulScale(), 2.18f,
            DockLayoutPolicy.maxUsefulScale(capsule)};
    }

    @Test
    public void aLoneRowsIconIsItsBandLessItsAir() {
        for (float density : DENSITIES) {
            for (boolean capsule : STYLES) {
                for (boolean ticks : BOOLS) {
                    for (float scale : sizes(capsule)) {
                        DockLayout l = layout(capsule, true, ticks, scale, density);
                        String at = (capsule ? "Floating" : "Docked") + " ticks " + ticks
                            + " scale " + scale + " density " + density;
                        assertEquals(at, l.appsRowBandPx - l.appsTopPaddingPx
                            - l.appsRowTickSideAirPx, l.appsRowIconPx);
                        assertEquals(at, l.appsRowBandHintPx, l.appsRowIconPx);
                        assertTrue(at, l.appsRowIconPx
                            > layout(capsule, false, ticks, scale, density).appsRowIconPx);
                    }
                }
            }
        }
    }

    /**
     * The lone band is the preset's baseline plus the lone air (P8's 135px Floating baseline at
     * 440dpi, for one): the ticks' lone band either side while it carries them, the sliver while
     * it does not.
     */
    @Test
    public void theLoneBarIsItsBaselineAndItsAir() {
        int stripPx = DockLayoutPolicy.stripBandPx(true, 2.75f);
        assertEquals("6.5dp at density 2.75", 18, stripPx);
        assertEquals(135 + 2 * stripPx, layout(true, true, true, 2.18f, 2.75f).appsRowBandPx);
        int sliverPx = DockLayoutPolicy.loneRowAirPx(2.75f);
        assertEquals(135 + 2 * sliverPx, layout(true, true, false, 2.18f, 2.75f).appsRowBandPx);
    }

    /**
     * What pong (420dpi, 411dp wide, ticks shown) draws alone at min, the shipped default and max,
     * in px: the icon fills the baseline. Docked was 21, 40 and 55dp, and is 32, 51 and 68dp;
     * Floating was 24, 39 and 55dp, and is 34, 49 and 67dp. The bars are 5dp shorter than they
     * were, the ticks' band a size down on both sides.
     */
    @Test
    public void pongsLoneIconsAtEachSize() {
        float density = 2.625f;
        int[][] docked = {{55, 84, 118}, {105, 134, 168}, {145, 178, 212}};
        int[][] floating = {{64, 88, 122}, {103, 128, 162}, {144, 176, 210}};
        for (boolean capsule : STYLES) {
            float[] scales = sizes(capsule);
            int[][] expected = capsule ? floating : docked;
            for (int i = 0; i < scales.length; i++) {
                String at = (capsule ? "Floating " : "Docked ") + scales[i];
                DockLayout shared = layout(capsule, false, true, scales[i], density);
                DockLayout alone = layout(capsule, true, true, scales[i], density);
                assertEquals(at, expected[i][0], shared.appsRowIconPx);
                assertEquals(at, expected[i][1], alone.appsRowIconPx);
                assertEquals(at, expected[i][2], alone.appsRowBandPx);
            }
        }
    }

    /** The size control reads the icon the lone dock draws, at every scale and both styles. */
    @Test
    public void theControlsIconIsTheLoneDocksIcon() {
        for (boolean capsule : STYLES) {
            for (int i = 0; i <= 26; i++) {
                float scale = 0.4f + i * 0.1f;
                assertEquals("scale " + scale,
                    layout(capsule, true, true, scale, 2.75f).appsRowIconPx,
                    DockLayoutPolicy.iconPxForScale(capsule, true, scale, 2.75f));
            }
        }
    }

    /** Every lone icon the stretch reaches comes back from its own scale; the rest go nearest. */
    @Test
    public void theInverseRoundTripsTheLoneRow() {
        for (float density : DENSITIES) {
            for (boolean capsule : STYLES) {
                float min = DockLayoutPolicy.minUsefulScale();
                float max = DockLayoutPolicy.maxUsefulScale(capsule);
                int lo = DockLayoutPolicy.iconPxForScale(capsule, true, min, density);
                int hi = DockLayoutPolicy.iconPxForScale(capsule, true, max, density);
                assertTrue(hi > lo);
                for (int px = lo - 3; px <= hi + 3; px++) {
                    float scale = DockLayoutPolicy.scaleForIconPx(capsule, true, px, density);
                    assertTrue("inside the stretch", scale >= min && scale <= max);
                    int back = DockLayoutPolicy.iconPxForScale(capsule, true, scale, density);
                    int target = Math.max(lo, Math.min(hi, px));
                    assertTrue("density " + density + " capsule " + capsule + " px " + px
                        + " came back as " + back, Math.abs(back - target) <= 1);
                }
                for (int i = 0; i < DockLayoutPolicy.sizePresetCount(); i++) {
                    float preset = DockLayoutPolicy.sizePreset(i);
                    if (preset > max) continue;
                    assertEquals(preset, DockLayoutPolicy.scaleForIconPx(capsule, true,
                        DockLayoutPolicy.iconPxForScale(capsule, true, preset, density),
                        density), 0f);
                }
            }
        }
    }

    /**
     * Width still wins: ten lone icons at the largest size on a 360dp phone are held to their
     * 36dp slots less the 4dp between them, as before the icon grew.
     */
    @Test
    public void tenLoneIconsOnANarrowPhoneStayInTheirSlots() {
        for (float density : DENSITIES) {
            int rowPx = Math.round(360f * density);
            int slotPx = rowPx / 10;
            int airPx = DockLayoutPolicy.loneRowAirPx(density);
            for (boolean capsule : STYLES) {
                int top = DockLayoutPolicy.iconPxForScale(capsule, true,
                    DockLayoutPolicy.maxUsefulScale(capsule), density);
                int shared = DockLayoutPolicy.iconPxForScale(capsule, false,
                    DockLayoutPolicy.maxUsefulScale(capsule), density);
                assertEquals(slotPx - airPx,
                    DockLayoutPolicy.slotFitIconPx(top, rowPx, 10, density));
                assertEquals(DockLayoutPolicy.slotFitIconPx(shared, rowPx, 10, density),
                    DockLayoutPolicy.slotFitIconPx(top, rowPx, 10, density));
            }
        }
    }
}
