package com.termux.app.dock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The dock's size as the icon it draws (issue #46). The stored dock height scale moves the icon
 * only inside a stretch per Style; the size controls stay inside it, so every step of them moves
 * the icon and the band formed around it, and the icon is read back out as the scale went in.
 */
public class DockIconSizeScaleTest {

    private static final float[] DENSITIES = {1f, 2f, 2.625f, 2.75f, 3f, 3.5f};
    private static final boolean[] STYLES = {false, true};

    private static int icon(boolean capsule, float scale, float density) {
        return DockLayoutPolicy.iconPxForScale(capsule, scale, density);
    }

    @Test
    public void theUsefulStretchIsWhereTheCurveIsNotPinned() {
        assertEquals(1.45f, DockLayoutPolicy.minUsefulScale(), 0f);
        // Floating's curve runs to the top of its window; Docked's is a notch ahead and caps
        // first, at 1.45 + (1.18 - 0.27).
        assertEquals(2.45f, DockLayoutPolicy.maxUsefulScale(true), 1e-6f);
        assertEquals(2.36f, DockLayoutPolicy.maxUsefulScale(false), 1e-5f);
        for (boolean capsule : STYLES) {
            float min = DockLayoutPolicy.minUsefulScale();
            float max = DockLayoutPolicy.maxUsefulScale(capsule);
            // Past either end the icon stands still: that is the dead band the controls avoid.
            assertEquals(icon(capsule, min, 2.75f), icon(capsule, 0.4f, 2.75f));
            assertEquals(icon(capsule, max, 2.75f), icon(capsule, 3.0f, 2.75f));
            // Inside, both ends still move it.
            assertTrue(icon(capsule, min + 0.05f, 2.75f) > icon(capsule, min, 2.75f));
            assertTrue(icon(capsule, max - 0.05f, 2.75f) < icon(capsule, max, 2.75f));
            assertEquals(min, DockLayoutPolicy.clampToUsefulScale(capsule, 0.4f), 0f);
            assertEquals(max, DockLayoutPolicy.clampToUsefulScale(capsule, 3.0f), 0f);
        }
    }

    /** It only ever wobbles by the pixel two rounded figures can disagree by, never more. */
    @Test
    public void theIconGrowsWithTheScaleAcrossTheStretch() {
        for (float density : DENSITIES) {
            for (boolean capsule : STYLES) {
                float min = DockLayoutPolicy.minUsefulScale();
                float max = DockLayoutPolicy.maxUsefulScale(capsule);
                int highest = 0;
                for (int i = 0; i <= 200; i++) {
                    int px = icon(capsule, min + (max - min) * i / 200f, density);
                    assertTrue("density " + density + " capsule " + capsule + " step " + i,
                        px >= highest - 1);
                    highest = Math.max(highest, px);
                }
                assertTrue(icon(capsule, max, density) > icon(capsule, min, density));
            }
        }
    }

    /** The size control and the dock can never disagree: one helper answers both. */
    @Test
    public void theDocksIconIsTheControlsIconAtEveryScale() {
        for (boolean capsule : STYLES) {
            for (int i = 0; i <= 26; i++) {
                float scale = 0.4f + i * 0.1f;
                DockLayout layout = DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
                    .preferencesAvailable(true)
                    .capsule(capsule)
                    .appsRowEnabledPref(true)
                    .azRowEnabledPref(true)
                    .density(2.75f)
                    .barHeightScale(scale)
                    .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(2.75f))
                    .build());
                assertEquals("scale " + scale, layout.appsRowIconPx, icon(capsule, scale, 2.75f));
                // The band is the icon and its air, so it moves with the icon and only with it.
                assertEquals(layout.appsRowIconPx + layout.appsTopPaddingPx
                    + layout.appsRowTickSideAirPx, layout.appsRowBandPx);
            }
        }
    }

    /** Every icon the stretch reaches comes back from its own scale; the rest go to the nearest. */
    @Test
    public void theInverseRoundTripsEveryIconTheStretchReaches() {
        for (float density : DENSITIES) {
            for (boolean capsule : STYLES) {
                float min = DockLayoutPolicy.minUsefulScale();
                float max = DockLayoutPolicy.maxUsefulScale(capsule);
                int lo = icon(capsule, min, density);
                int hi = icon(capsule, max, density);
                for (int px = lo - 3; px <= hi + 3; px++) {
                    float scale = DockLayoutPolicy.scaleForIconPx(capsule, px, density);
                    assertTrue("inside the stretch", scale >= min && scale <= max);
                    int back = icon(capsule, scale, density);
                    int target = Math.max(lo, Math.min(hi, px));
                    assertTrue("density " + density + " capsule " + capsule + " px " + px
                        + " came back as " + back, Math.abs(back - target) <= 1);
                }
                // The ends map to the ends.
                assertEquals(lo, icon(capsule, DockLayoutPolicy.scaleForIconPx(capsule, lo,
                    density), density));
                assertEquals(hi, icon(capsule, DockLayoutPolicy.scaleForIconPx(capsule, hi,
                    density), density));
            }
        }
    }

    /** Back on a shipped preset's icon, the preset itself is what is stored. */
    @Test
    public void aPresetsIconWritesThePreset() {
        for (boolean capsule : STYLES) {
            for (int i = 0; i < DockLayoutPolicy.sizePresetCount(); i++) {
                float preset = DockLayoutPolicy.sizePreset(i);
                if (preset > DockLayoutPolicy.maxUsefulScale(capsule)) continue;
                assertEquals(preset, DockLayoutPolicy.scaleForIconPx(capsule,
                    icon(capsule, preset, 2.75f), 2.75f), 0f);
            }
        }
    }
}
