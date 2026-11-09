package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.dock.DockSurfaceSpec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The strip under the gesture pill as the Peek glass card's foot: its sides are the card's sides
 * run on past the seam, so a pixel at a given distance in from the side samples the picture at the
 * same depth on both sides of the seam.
 *
 * <p>The lighter patch on pong (the card's bottom-left, under the first icon, on pages whose
 * wallpaper is bright beside the card) was the two programs bending that side differently: the
 * card wore the dock design's 24 dp soft rim, the strip the look's narrow ring. Near the side the
 * frost holds the bright wallpaper the blur bleeds in from beside the card; the card's soft band
 * reads past it, the strip's narrow ring shows it, starting at the seam.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PeekCardFootGlassTest {

    private static final float DENSITY = 2.625f; // pong, 420 dpi
    /** Half the card's glass rect's short side on pong: wide and tall, well past every band. */
    private static final float CARD_HALF_SHORT_PX = 190f;
    /** The strip under the pill on pong: 63 px under 137 px of upward overscan. */
    private static final float STRIP_HEIGHT_PX = 63f + 137f;

    private static float rimW() {
        return GlassRefraction.Look.rimWidthPx(DENSITY);
    }

    /** How deep the card's side samples, the soft band its design wears. */
    private static float cardDepth(GlassRefraction.Look look, float insidePx) {
        float softPx = DockSurfaceSpec.PEEK_GLASS.softRimDp * DENSITY;
        return insidePx + GlassRefraction.softPullPx(insidePx, look.bandPx(DENSITY), softPx,
            look.strengthPx(DENSITY), CARD_HALF_SHORT_PX, rimW(), 0f);
    }

    /** How deep the strip's side samples with {@code softRimDp} on its program (0: the narrow ring). */
    private static float stripDepth(GlassRefraction.Look look, float insidePx, float softRimDp) {
        float softPx = softRimDp * DENSITY;
        float reach = GlassRefraction.seamReachPx(look, DENSITY, 0f, softPx);
        float halfShort = Math.min(508f, (STRIP_HEIGHT_PX + 2f * reach) / 2f);
        if (softPx <= 0f) {
            return insidePx + GlassRefraction.pullPx(insidePx, look.bandPx(DENSITY),
                look.strengthPx(DENSITY), halfShort, rimW());
        }
        return insidePx + GlassRefraction.softPullPx(insidePx, look.bandPx(DENSITY), softPx,
            look.strengthPx(DENSITY), halfShort, rimW(), 0f);
    }

    @Test
    public void theNarrowRingUnderTheCardReadsTheFrostTheCardReadsPast() {
        // The defect: 12 px in from the side, the card samples ~34 px in, past the bright frost by
        // the side; the strip right under it, on the narrow ring, samples ~20 px in.
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        float worst = 0f;
        for (float in = 0f; in <= 70f; in += 0.5f) {
            worst = Math.max(worst, Math.abs(cardDepth(look, in) - stripDepth(look, in, 0f)));
        }
        assertTrue("the two sides part by " + worst + " px", worst > 10f);
    }

    @Test
    public void theCardsFootWearsTheCardsSoftRim() {
        assertEquals(DockSurfaceSpec.PEEK_GLASS.softRimDp,
            DockSurfaceSpec.PEEK_GLASS.underPillSoftRimDp(true), 0f);
        assertTrue(DockSurfaceSpec.PEEK_GLASS.underPillSoftRimDp(true) > 0f);
        // No card standing (Floating, the keyboard up, the Search field): the strip as it was.
        assertEquals(0f, DockSurfaceSpec.PEEK_GLASS.underPillSoftRimDp(false), 0f);
        // Every other design's strip spans the screen; its sides are seams.
        for (DockSurfaceSpec spec : new DockSurfaceSpec[] {DockSurfaceSpec.PLAIN,
            DockSurfaceSpec.GLASS_TRAY, DockSurfaceSpec.LENS_BEADS, DockSurfaceSpec.WALL_STRIP}) {
            assertEquals(spec.name, 0f, spec.underPillSoftRimDp(true), 0f);
        }
    }

    @Test
    public void wearingItTheSidesSampleAlikeAcrossTheSeam() {
        for (GlassRefraction.Look look : new GlassRefraction.Look[] {GlassRefraction.Look.DEFAULT,
            new GlassRefraction.Look(27, 8, 100), new GlassRefraction.Look(4, 40, 100)}) {
            float soft = DockSurfaceSpec.PEEK_GLASS.underPillSoftRimDp(true);
            for (float in = 0f; in <= 140f; in += 0.5f) {
                assertEquals(look + " at " + in, cardDepth(look, in), stripDepth(look, in, soft),
                    1e-3f);
            }
        }
    }

    @Test
    public void theStripsSeamsClearTheSoftBand() {
        // Its top runs on into the card and its bottom off the screen: neither may bend. Pushed
        // by the narrow ring's reach alone, a look with a narrow Edge width leaves the 24 dp band
        // reaching the strip's bottom edge.
        float softPx = DockSurfaceSpec.PEEK_GLASS.softRimDp * DENSITY;
        GlassRefraction.Look narrow = new GlassRefraction.Look(9, 8, 100);
        float oldReach = GlassRefraction.seamReachPx(narrow, DENSITY, 0f);
        assertTrue(GlassRefraction.softPullPx(oldReach, narrow.bandPx(DENSITY), softPx,
            narrow.strengthPx(DENSITY), 1000f, rimW(), 0f) > 0f);
        for (GlassRefraction.Look look : new GlassRefraction.Look[] {GlassRefraction.Look.DEFAULT,
            narrow, new GlassRefraction.Look(0, 1, 100), new GlassRefraction.Look(60, 40, 100)}) {
            float reach = GlassRefraction.seamReachPx(look, DENSITY, 0f, softPx);
            assertTrue(look.toString(), reach >= softPx);
            assertEquals(look.toString(), 0f, GlassRefraction.softPullPx(reach, look.bandPx(DENSITY),
                softPx, look.strengthPx(DENSITY), 1000f, rimW(), 0f), 0f);
            assertTrue(look.toString(), reach >= GlassRefraction.Look.rimReachPx(DENSITY));
        }
    }

    @Test
    public void withoutASoftBandTheSeamReachIsUnchanged() {
        for (GlassRefraction.Look look : new GlassRefraction.Look[] {GlassRefraction.Look.DEFAULT,
            new GlassRefraction.Look(9, 8, 100), new GlassRefraction.Look(0, 1, 100)}) {
            for (float radius : new float[] {0f, 40f, 73.5f}) {
                assertEquals(GlassRefraction.seamReachPx(look, DENSITY, radius),
                    GlassRefraction.seamReachPx(look, DENSITY, radius, 0f), 0f);
            }
        }
    }
}
