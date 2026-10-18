package com.termux.app.terminal.inappkeyboard.touch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static juloo.keyboard2.TapGeometry.ROLE_BACKSPACE;
import static juloo.keyboard2.TapGeometry.ROLE_CHAR;
import static juloo.keyboard2.TapGeometry.ROLE_ESC;
import static juloo.keyboard2.TapGeometry.ROLE_OTHER;
import static juloo.keyboard2.TapGeometry.ROLE_SPACE;
import static juloo.keyboard2.TapGeometry.ROLE_TAB;

import juloo.keyboard2.TapGeometry;

import org.junit.Test;

public class TapModelTest {

    /**
     * A 3x2 grid of unit keys. Index = row * 3 + column. Row 0 is all characters; in row 1 the
     * middle key (index 4) is never corrected, standing in for Enter or Ctrl. Every key sits in
     * its own third of the keyboard, so every group has one member.
     */
    private static TapGeometry grid() {
        float[] left = {0, 1, 2, 0, 1, 2};
        float[] right = {1, 2, 3, 1, 2, 3};
        float[] top = {0, 0, 0, 1, 1, 1};
        float[] bottom = {1, 1, 1, 2, 2, 2};
        int[] row = {0, 0, 0, 1, 1, 1};
        int[] role = {ROLE_CHAR, ROLE_CHAR, ROLE_CHAR, ROLE_CHAR, ROLE_OTHER, ROLE_CHAR};
        return new TapGeometry(left, top, right, bottom, row, role, "test");
    }

    /**
     * Two rows on a nine-key-wide keyboard: row 0 holds nine unit characters (three per third),
     * row 1 a backspace at 0..1, a space bar at 1..8 and an Esc at 8..9. Rows are 1.2 keys tall.
     */
    static TapGeometry spaceGrid() {
        int n = 12;
        float[] left = new float[n], right = new float[n], top = new float[n],
            bottom = new float[n];
        int[] row = new int[n], role = new int[n];
        for (int i = 0; i < 9; i++) {
            left[i] = i;
            right[i] = i + 1;
            top[i] = 0f;
            bottom[i] = 1.2f;
            role[i] = ROLE_CHAR;
        }
        float[][] bottomRow = {{0, 1}, {1, 8}, {8, 9}};
        int[] bottomRoles = {ROLE_BACKSPACE, ROLE_SPACE, ROLE_ESC};
        for (int j = 0; j < 3; j++) {
            int i = 9 + j;
            left[i] = bottomRow[j][0];
            right[i] = bottomRow[j][1];
            top[i] = 1.2f;
            bottom[i] = 2.4f;
            row[i] = 1;
            role[i] = bottomRoles[j];
        }
        return new TapGeometry(left, top, right, bottom, row, role, "space");
    }

    static final int SPACE = 10;
    static final int BACKSPACE = 9;
    static final int ESC = 11;

    private static void teach(TapModel model, TapGeometry g, int key, float dx, float dy,
                              int taps) {
        for (int i = 0; i < taps; i++)
            model.observe(g, key, g.centerX(key) + dx, g.centerY(key) + dy, 1f);
    }

    @Test
    public void emptyModelLeavesEveryPressAlone() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        for (int k = 0; k < g.keyCount; k++)
            assertEquals(k, model.resolve(g, k, g.right[k] - 0.01f, g.centerY(k)));
    }

    @Test
    public void learnsARightwardBiasAndMovesTheBorderRight() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 0, 0.2f, 0f, 200);
        teach(model, g, 1, 0.2f, 0f, 200);
        assertTrue(model.biasX(g, 0) > 0.15f);
        assertEquals(0, model.resolve(g, 1, 1.05f, 0.5f));
        assertEquals(1, model.resolve(g, 1, 1.5f, 0.5f));
        assertEquals(0, model.resolve(g, 0, 0.95f, 0.5f));
    }

    @Test
    public void borderIsTheSameLineSeenFromEitherSide() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 0, 0.2f, 0f, 200);
        teach(model, g, 1, -0.05f, 0f, 200);
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
        teach(model, g, 0, 1f, 0f, 1000);
        teach(model, g, 1, 1f, 0f, 1000);
        assertEquals(1, model.resolve(g, 1, 1.35f, 0.5f));
        assertEquals(0, model.resolve(g, 1, 1.25f, 0.5f));
    }

    @Test
    public void theCentreOfEveryKeyStaysThatKeyWhateverWasLearned() {
        TapGeometry g = spaceGrid();
        TapModel model = new TapModel(g.keyCount);
        // Absurd offsets in every direction on every key.
        for (int k = 0; k < g.keyCount; k++) {
            float sign = k % 2 == 0 ? 1f : -1f;
            teach(model, g, k, sign * 3f, -sign * 3f, 500);
        }
        for (int k = 0; k < g.keyCount; k++)
            assertEquals(k, model.resolve(g, k, g.centerX(k), g.centerY(k)));
    }

    @Test
    public void neverCorrectsIntoOrOutOfAKeyThatIsNotCorrectable() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 3, 0.25f, 0f, 500);
        teach(model, g, 5, 0.25f, 0f, 500);
        assertEquals(3, model.resolve(g, 3, 0.99f, 1.5f));
        assertEquals(4, model.resolve(g, 4, 1.01f, 1.5f));
        assertFalse(model.observe(g, 4, 1.9f, 1.5f, 1f));
        assertEquals(0f, model.counts()[4], 0f);
    }

    @Test
    public void spaceEscAndTabAreCorrectableEnterAndBackspaceAreNot() {
        assertTrue(TapGeometry.isCorrectableRole(ROLE_CHAR));
        assertTrue(TapGeometry.isCorrectableRole(ROLE_SPACE));
        assertTrue(TapGeometry.isCorrectableRole(ROLE_ESC));
        assertTrue(TapGeometry.isCorrectableRole(ROLE_TAB));
        assertFalse(TapGeometry.isCorrectableRole(ROLE_BACKSPACE));
        assertFalse(TapGeometry.isCorrectableRole(ROLE_OTHER));
    }

    @Test
    public void fewTapsBarelyMoveTheBorder() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 0, 0.3f, 0f, 3);
        assertTrue(model.biasX(g, 0) < 0.05f);
        assertEquals(1, model.resolve(g, 1, 1.05f, 0.5f));
    }

    @Test
    public void aKeyWithNoTapsBorrowsItsGroupsOffset() {
        TapGeometry g = spaceGrid();
        TapModel model = new TapModel(g.keyCount);
        // Keys 0 and 1 share the left third with key 2; only they are taught.
        teach(model, g, 0, 0.2f, 0.1f, 200);
        teach(model, g, 1, 0.2f, 0.1f, 200);
        float borrowed = model.biasX(g, 2);
        assertTrue("borrowed " + borrowed, borrowed > 0.15f);
        assertTrue(model.biasY(g, 2) > 0.05f);
        // A key in another third borrows nothing.
        assertEquals(0f, model.biasX(g, 5), 0f);
    }

    @Test
    public void ownTapsOutweighTheGroupAsTheyAccumulate() {
        TapGeometry g = spaceGrid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 0, 0.2f, 0f, 300);
        teach(model, g, 1, 0.2f, 0f, 300);
        teach(model, g, 2, -0.2f, 0f, 300);
        // Pooled prior is about +0.067; key 2's own -0.2 dominates at 300 taps.
        assertTrue(model.biasX(g, 2) < -0.15f);
    }

    @Test
    public void wideKeysAreTheirOwnGroupAndLearnVerticalOnly() {
        TapGeometry g = spaceGrid();
        KeyGroups groups = KeyGroups.of(g);
        assertTrue(groups.wide[SPACE]);
        assertEquals(1, groups.members[groups.group[SPACE]]);
        assertEquals(-1, groups.group[BACKSPACE]);

        TapModel model = new TapModel(g.keyCount);
        teach(model, g, SPACE, 2f, -0.3f, 200);
        assertEquals(0f, model.biasX(g, SPACE), 0f);
        assertEquals(0f, model.sumX()[SPACE], 0f);
        assertTrue(model.biasY(g, SPACE) < -0.25f);
    }

    @Test
    public void rowsSplitIntoThirdsOfTheKeyboardWidth() {
        TapGeometry g = spaceGrid();
        KeyGroups groups = KeyGroups.of(g);
        assertEquals(groups.group[0], groups.group[2]);
        assertEquals(groups.group[3], groups.group[5]);
        assertEquals(groups.group[6], groups.group[8]);
        assertTrue(groups.group[2] != groups.group[3]);
        assertTrue(groups.group[5] != groups.group[6]);
        // Esc is narrow, alone in the bottom row's right third.
        assertEquals(1, groups.members[groups.group[ESC]]);
    }

    @Test
    public void verticalBiasMovesTheRowBorder() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 0, 0f, 0.2f, 200);
        teach(model, g, 3, 0f, 0.2f, 200);
        assertEquals(0, model.resolve(g, 3, 0.5f, 1.05f));
        assertEquals(3, model.resolve(g, 3, 0.5f, 1.5f));
    }

    @Test
    public void weightCountsAsThatManyTaps() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        model.observe(g, 0, 0.9f, 0.5f, 3f);
        assertEquals(3f, model.counts()[0], 0f);
        assertEquals(1.2f, model.sumX()[0], 1e-5f);
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
        assertFalse(model.observe(g, 0, 0.9f, 0.5f, 1f));
        assertTrue(model.isEmpty());
        assertEquals(1, model.resolve(g, 1, 1.05f, 0.5f));
    }

    @Test
    public void roundTripsThroughItsArrays() {
        TapGeometry g = grid();
        TapModel model = new TapModel(g.keyCount);
        teach(model, g, 2, -0.1f, 0.05f, 30);
        TapModel copy = new TapModel(model.counts(), model.sumX(), model.sumY());
        assertEquals(model.biasX(g, 2), copy.biasX(g, 2), 0f);
        assertEquals(model.biasY(g, 2), copy.biasY(g, 2), 0f);
        assertEquals(model.totalTaps(), copy.totalTaps(), 0f);
    }

    /** {@link #spaceGrid()} with every row {@code factor} times as tall: the same key structure. */
    static TapGeometry spaceGridRowsScaled(float factor) {
        TapGeometry s = spaceGrid();
        float[] top = s.top.clone();
        float[] bottom = s.bottom.clone();
        for (int i = 0; i < s.keyCount; i++) {
            top[i] *= factor;
            bottom[i] *= factor;
        }
        return new TapGeometry(s.left.clone(), top, s.right.clone(), bottom, s.row.clone(),
            s.role.clone(), s.signature);
    }

    @Test
    public void verticalOffsetsAreLearnedInKeyHeightsAndSurviveAResize() {
        TapGeometry g = spaceGrid();
        TapModel model = new TapModel(g.keyCount);
        // Taps on space and on b both land a fifth of a key height high.
        float spaceHigh = -0.2f * g.height(SPACE);
        teach(model, g, SPACE, 0f, spaceHigh, 200);
        teach(model, g, 4, 0f, -0.2f * g.height(4), 200);
        assertEquals(spaceHigh, model.biasY(g, SPACE), 0.03f);

        // The key width shrinks, so in key widths every row is a quarter taller.
        TapGeometry taller = spaceGridRowsScaled(1.25f);
        assertEquals(1.25f * model.biasY(g, SPACE), model.biasY(taller, SPACE), 1e-5f);
        // The border between b and space sits at the same fraction of the keys on both grids.
        for (float fraction : new float[]{0.05f, 0.15f, 0.3f, 0.5f}) {
            float y = g.bottom[4] - fraction * g.height(4);
            float yTaller = taller.bottom[4] - fraction * taller.height(4);
            assertEquals(model.resolve(g, 4, 4.5f, y), model.resolve(taller, 4, 4.5f, yTaller));
        }
        assertEquals(SPACE, model.resolve(taller, 4, 4.5f, taller.bottom[4] - 0.05f));
        assertEquals(SPACE,
            model.resolve(taller, 4, 4.5f, taller.bottom[4] - 0.15f * taller.height(4)));
        assertEquals(4, model.resolve(taller, 4, 4.5f, taller.bottom[4] - 0.3f * taller.height(4)));
    }
}
