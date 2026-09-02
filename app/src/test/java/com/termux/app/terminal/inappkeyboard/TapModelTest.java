package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import juloo.keyboard2.TapGeometry;

import org.junit.Test;

public class TapModelTest {

    /**
     * A 3x2 grid of unit keys. Index = row * 3 + column. Row 0 is all characters; in row 1 the
     * middle key (index 4) is a non-character key, standing in for Enter or Ctrl.
     */
    private static TapGeometry grid() {
        float[] left = {0, 1, 2, 0, 1, 2};
        float[] right = {1, 2, 3, 1, 2, 3};
        float[] top = {0, 0, 0, 1, 1, 1};
        float[] bottom = {1, 1, 1, 2, 2, 2};
        int[] row = {0, 0, 0, 1, 1, 1};
        boolean[] isChar = {true, true, true, true, false, true};
        return new TapGeometry(left, top, right, bottom, row, isChar, "test");
    }

    private static void teach(TapModel model, TapGeometry g, int key, float dx, float dy,
                              int taps) {
        for (int i = 0; i < taps; i++)
            model.observe(g, key, g.centerX(key) + dx, g.centerY(key) + dy, false);
    }

    @Test
    public void emptyModelLeavesEveryPressAlone() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        for (int k = 0; k < g.keyCount; k++)
            assertEquals(k, model.resolve(g, k, g.right[k] - 0.01f, g.centerY(k)));
    }

    @Test
    public void learnsARightwardBiasAndMovesTheBoundaryRight() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        // Every tap on keys 0 and 1 lands a fifth of a key to the right of centre.
        teach(model, g, 0, 0.2f, 0f, 200);
        teach(model, g, 1, 0.2f, 0f, 200);
        assertTrue(model.biasX(0) > 0.15f);
        // A press just inside key 1's left edge was meant for key 0.
        assertEquals(0, model.resolve(g, 1, 1.05f, 0.5f));
        // A press well inside key 1 stays.
        assertEquals(1, model.resolve(g, 1, 1.5f, 0.5f));
        // The boundary moved right, so key 0's right edge still belongs to key 0.
        assertEquals(0, model.resolve(g, 0, 0.95f, 0.5f));
    }

    @Test
    public void boundaryIsTheSameLineSeenFromEitherSide() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 0, 0.2f, 0f, 200);
        teach(model, g, 1, -0.05f, 0f, 200);
        // Scan across the shared edge: the resolved key must switch exactly once, from 0 to 1.
        int previous = 0;
        int switches = 0;
        for (float x = 0.5f; x < 1.5f; x += 0.01f) {
            int raw = x < 1f ? 0 : 1;
            int resolved = model.resolve(g, raw, x, 0.5f);
            if (resolved != previous) {
                switches++;
                previous = resolved;
            }
        }
        assertEquals(1, switches);
        assertEquals(1, previous);
    }

    @Test
    public void shiftIsCappedToAFractionOfTheNarrowerKey() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        // An absurd bias: taps land a whole key away.
        teach(model, g, 0, 1f, 0f, 1000);
        teach(model, g, 1, 1f, 0f, 1000);
        // With the cap at 0.3 the boundary sits at 1.3; a press at 1.35 stays on key 1.
        assertEquals(1, model.resolve(g, 1, 1.35f, 0.5f));
        assertEquals(0, model.resolve(g, 1, 1.25f, 0.5f));
    }

    @Test
    public void neverCrossesIntoOrOutOfANonCharacterKey() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        // Strong rightward bias on the whole bottom row, so key 3 → key 4 would be tempting.
        teach(model, g, 3, 0.25f, 0f, 500);
        teach(model, g, 5, 0.25f, 0f, 500);
        assertEquals(3, model.resolve(g, 3, 0.99f, 1.5f));
        // Pressing the non-character key itself is never moved either.
        assertEquals(4, model.resolve(g, 4, 1.01f, 1.5f));
        // And observing it teaches nothing.
        model.observe(g, 4, 1.9f, 1.5f, false);
        assertEquals(0f, model.counts()[4], 0f);
    }

    @Test
    public void swipesTeachNothing() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        for (int i = 0; i < 100; i++)
            model.observe(g, 0, 0.9f, 0.5f, true);
        assertTrue(model.isEmpty());
    }

    @Test
    public void fewTapsBarelyMoveTheBoundary() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 0, 0.3f, 0f, 3);
        // Three taps are shrunk to a fraction of their mean.
        assertTrue(model.biasX(0) < 0.05f);
        assertEquals(1, model.resolve(g, 1, 1.05f, 0.5f));
    }

    @Test
    public void verticalBiasMovesTheRowBoundary() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        // Taps on the left column land low.
        teach(model, g, 0, 0f, 0.2f, 200);
        teach(model, g, 3, 0f, 0.2f, 200);
        assertEquals(0, model.resolve(g, 3, 0.5f, 1.05f));
        assertEquals(3, model.resolve(g, 3, 0.5f, 1.5f));
    }

    @Test
    public void forgetsGraduallySoTheEstimateCanAdapt() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 0, 0.2f, 0f, TapModel.FORGET_AT);
        assertEquals(TapModel.FORGET_AT, model.counts()[0], 0f);
        teach(model, g, 0, 0.2f, 0f, 1);
        assertEquals(TapModel.FORGET_AT / 2f + 1f, model.counts()[0], 0f);
        assertEquals(0.2f, model.sumX()[0] / model.counts()[0], 1e-4f);
    }

    @Test
    public void ignoresAGeometryWithADifferentKeyCount() {
        TapGeometry g = grid();
        TapModel model = new TapModel(2);
        model.observe(g, 0, 0.9f, 0.5f, false);
        assertTrue(model.isEmpty());
        assertEquals(1, model.resolve(g, 1, 1.05f, 0.5f));
    }

    @Test
    public void roundTripsThroughItsArrays() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 2, -0.1f, 0.05f, 30);
        TapModel copy = new TapModel(model.counts(), model.sumX(), model.sumY());
        assertEquals(model.biasX(2), copy.biasX(2), 0f);
        assertEquals(model.biasY(2), copy.biasY(2), 0f);
        assertEquals(model.totalTaps(), copy.totalTaps(), 0f);
    }
}
