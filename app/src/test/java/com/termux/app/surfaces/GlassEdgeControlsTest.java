package com.termux.app.surfaces;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The global set's Depth over the six stored edge keys. */
public class GlassEdgeControlsTest {

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    private static GlassEdgeControls.Edge edgeOf(SurfacePresets.Preset preset) {
        Map<String, Object> v = preset.values;
        return new GlassEdgeControls.Edge((Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_BEND),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
    }

    @Test
    public void everyGlassLookSitsOnTheCurveAndRoundTrips() {
        String[] ids = {"minimal", "frost", "stock"};
        // Clear 27/18, Mist 24/16, Tint 21/14: each the first position of its stretch.
        int[] depth = {90, 80, 70};
        int[][] pair = {{27, 18}, {24, 16}, {21, 14}};
        for (int i = 0; i < ids.length; i++) {
            GlassEdgeControls.Edge edge = edgeOf(preset(ids[i]));
            assertEquals(ids[i], pair[i][0], edge.bend);
            assertEquals(ids[i], pair[i][1], edge.width);
            assertEquals(ids[i], depth[i], edge.depth());
            // Depth written back at its reading reproduces the Look exactly.
            assertEquals(ids[i], edge, edge.withDepth(edge.depth()));
            // The bend is one and a half bands.
            assertEquals(ids[i], 2 * edge.bend, 3 * edge.width);
        }
    }

    @Test
    public void depthWritesTheBendAndAWidthThatFollowsIt() {
        assertEquals(0, GlassEdgeControls.bendForDepth(0));
        assertEquals("never under the stored minimum", 1, GlassEdgeControls.widthForDepth(0));
        assertEquals(30, GlassEdgeControls.bendForDepth(100));
        assertEquals(20, GlassEdgeControls.widthForDepth(100));
        // floor(0.30 t): 89 is still 26, 90 is 27.
        assertEquals(26, GlassEdgeControls.bendForDepth(89));
        assertEquals(27, GlassEdgeControls.bendForDepth(90));
        assertEquals(27, GlassEdgeControls.bendForDepth(93));
        assertEquals(28, GlassEdgeControls.bendForDepth(94));
        // R(2 bend / 3) in integers: 26 is 17.33 and 25 is 16.67.
        assertEquals(17, GlassEdgeControls.widthForDepth(89));
        assertEquals(17, GlassEdgeControls.widthForDepth(84));
        for (int t = 1; t <= 100; t++) {
            assertTrue(GlassEdgeControls.bendForDepth(t) >= GlassEdgeControls.bendForDepth(t - 1));
            assertTrue(GlassEdgeControls.widthForDepth(t)
                >= GlassEdgeControls.widthForDepth(t - 1));
        }
        for (int bend = 0; bend <= 30; bend++) {
            int width = Math.max(1, (int) Math.floor(2.0 * bend / 3.0 + 0.5));
            int t = (10 * bend + 2) / 3; // the first position whose floor(0.3 t) is bend
            assertEquals("bend " + bend, bend, GlassEdgeControls.bendForDepth(t));
            assertEquals("bend " + bend, width, GlassEdgeControls.widthForDepth(t));
        }
    }

    @Test
    public void sliderClampsOutsideItsRange() {
        assertEquals(GlassEdgeControls.bendForDepth(0), GlassEdgeControls.bendForDepth(-20));
        assertEquals(GlassEdgeControls.bendForDepth(100), GlassEdgeControls.bendForDepth(150));
        assertEquals(GlassEdgeControls.widthForDepth(100), GlassEdgeControls.widthForDepth(150));
        // A stored edge past every curve output reads at the end it is nearest: 30/20, first
        // written at 100.
        assertEquals(100, GlassEdgeControls.depthFor(30, 60));
        assertEquals(0, GlassEdgeControls.depthFor(0, 1));
    }

    /** A pair on the curve reads as the smallest position that writes it, and writes it back. */
    @Test
    public void anOnCurvePairReadsAsTheSmallestPositionThatWritesIt() {
        for (int t = 0; t <= 100; t++) {
            int bend = GlassEdgeControls.bendForDepth(t);
            int width = GlassEdgeControls.widthForDepth(t);
            int read = GlassEdgeControls.depthFor(bend, width);
            assertTrue(t + " reads " + read, read <= t);
            assertEquals(bend, GlassEdgeControls.bendForDepth(read));
            assertEquals(width, GlassEdgeControls.widthForDepth(read));
            if (read > 0)
                assertTrue(GlassEdgeControls.bendForDepth(read - 1) != bend
                    || GlassEdgeControls.widthForDepth(read - 1) != width);
        }
    }

    @Test
    public void anOffCurveCustomReadsAtTheNearestPosition() {
        // The generation-4 Looks: their wide bands pull them up the curve, over the keys' ranges,
        // past where the Looks now sit. Clear's 26/26 reads as 28/19 (t 94).
        assertEquals(94, GlassEdgeControls.depthFor(26, 26));
        assertEquals(84, GlassEdgeControls.depthFor(24, 24));
        assertEquals(74, GlassEdgeControls.depthFor(20, 22));
        // A pair just off the curve reads at its own bend: 27/17 at Clear's 90.
        assertEquals(90, GlassEdgeControls.depthFor(27, 17));
    }

    @Test
    public void depthLeavesTheOtherKeysAlone() {
        GlassEdgeControls.Edge custom = new GlassEdgeControls.Edge(7, 40, 60, 13, 22, 55);
        GlassEdgeControls.Edge depth = custom.withDepth(50);
        assertEquals(GlassEdgeControls.bendForDepth(50), depth.bend);
        assertEquals(GlassEdgeControls.widthForDepth(50), depth.width);
        assertEquals(new GlassEdgeControls.Edge(depth.bend, depth.width, 60, 13, 22, 55), depth);
    }

    @Test
    public void aRememberedPositionOnTheSameStretchReadsBackWhereItWasLeft() {
        // 92 writes Clear's 27/18, as 90 (the stretch's first position) does.
        GlassEdgeControls.Edge clear = new GlassEdgeControls.Edge(26, 26, 10, 0, 40, 76)
            .withDepth(92);
        assertEquals(new GlassEdgeControls.Edge(27, 18, 10, 0, 40, 76), clear);
        assertEquals(90, clear.depth());
        assertEquals(92, clear.depth(92));
        assertEquals(90, clear.depth(GlassEdgeControls.NONE));
        // A remembered position that no longer writes the stored edge (a Look, an Undo, another
        // writer) is ignored: the nearest position reads as before.
        assertEquals(90, clear.depth(89));
        assertEquals(90, clear.depth(150));
        assertEquals(GlassEdgeControls.depthFor(24, 30),
            new GlassEdgeControls.Edge(24, 30, 28, 0, 30, 90).depth(80));
    }

    @Test
    public void readingAnOldCustomChangesNothing() {
        GlassEdgeControls.Edge custom = new GlassEdgeControls.Edge(13, 32, 60, 20, 20, 90);
        custom.depth();
        custom.depth(40);
        assertEquals(new GlassEdgeControls.Edge(13, 32, 60, 20, 20, 90), custom);
    }
}
