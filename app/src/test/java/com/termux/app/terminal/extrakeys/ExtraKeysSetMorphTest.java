package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExtraKeysSetMorphTest {

    private static KeyId id(String key, String second, int occurrence) {
        return new KeyId(key, second, occurrence);
    }

    @Test
    public void sharedKeysStayAndTheRestEnterOrLeave() {
        KeyId[] old = {id("A", null, 0), id("B", null, 0)};
        KeyId[] now = {id("B", null, 0), id("C", null, 0)};
        assertArrayEquals(new int[] {1, ExtraKeysSetMorph.ENTER}, ExtraKeysSetMorph.match(old, now));
        ExtraKeysSetMorph.Plan plan = ExtraKeysSetMorph.plan(old,
            new float[] {0, 0, 10, 10, 10, 0, 20, 10}, now, new float[] {0, 0, 10, 10, 10, 0, 20, 10});
        assertTrue(plan.leaving[0]);
        assertFalse(plan.leaving[1]);
    }

    @Test
    public void duplicateKeysMatchByOccurrence() {
        KeyId[] old = {id("A", null, 0), id("A", null, 1)};
        KeyId[] now = {id("A", null, 0)};
        assertArrayEquals(new int[] {0}, ExtraKeysSetMorph.match(old, now));
        ExtraKeysSetMorph.Plan plan = ExtraKeysSetMorph.plan(old, new float[8], now, new float[4]);
        assertFalse(plan.leaving[0]);
        assertTrue(plan.leaving[1]);
    }

    @Test
    public void aDifferentSecondKeyIsAnotherKey() {
        KeyId[] old = {id("A", "X", 0)};
        KeyId[] now = {id("A", "Y", 0), id("A", null, 0)};
        assertArrayEquals(new int[] {ExtraKeysSetMorph.ENTER, ExtraKeysSetMorph.ENTER},
            ExtraKeysSetMorph.match(old, now));
    }

    @Test
    public void aStayingKeyStartsOnItsOldRectAndEndsOnItsNew() {
        KeyId[] ids = {id("A", null, 0)};
        // Old 100 wide at x 0..100, new 50 wide at x 200..250, same row.
        ExtraKeysSetMorph.Plan plan = ExtraKeysSetMorph.plan(ids, new float[] {0, 0, 100, 40},
            ids, new float[] {200, 0, 250, 40});
        assertEquals(-175f, plan.fromDx[0], 0.001f);
        assertEquals(0f, plan.fromDy[0], 0.001f);
        assertEquals(2f, plan.fromScaleX[0], 0.001f);
        assertEquals(1f, plan.fromScaleY[0], 0.001f);
        assertEquals(-175f, ExtraKeysSetMorph.stayTranslation(plan.fromDx[0], 0f), 0.001f);
        assertEquals(-87.5f, ExtraKeysSetMorph.stayTranslation(plan.fromDx[0], 0.5f), 0.001f);
        assertEquals(0f, ExtraKeysSetMorph.stayTranslation(plan.fromDx[0], 1f), 0.001f);
        assertEquals(2f, ExtraKeysSetMorph.stayScale(plan.fromScaleX[0], 0f), 0.001f);
        assertEquals(1.5f, ExtraKeysSetMorph.stayScale(plan.fromScaleX[0], 0.5f), 0.001f);
        assertEquals(1f, ExtraKeysSetMorph.stayScale(plan.fromScaleX[0], 1f), 0.001f);
    }

    @Test
    public void enterAndLeaveValues() {
        assertEquals(0.5f, ExtraKeysSetMorph.enterScale(0f), 0.001f);
        assertEquals(0.75f, ExtraKeysSetMorph.enterScale(0.5f), 0.001f);
        assertEquals(1f, ExtraKeysSetMorph.enterScale(1f), 0.001f);
        assertEquals(0f, ExtraKeysSetMorph.enterAlpha(0f), 0.001f);
        assertEquals(0.5f, ExtraKeysSetMorph.enterAlpha(0.5f), 0.001f);
        assertEquals(1f, ExtraKeysSetMorph.enterAlpha(1f), 0.001f);
        assertEquals(1f, ExtraKeysSetMorph.leaveScale(0f), 0.001f);
        assertEquals(0.75f, ExtraKeysSetMorph.leaveScale(0.5f), 0.001f);
        assertEquals(0.5f, ExtraKeysSetMorph.leaveScale(1f), 0.001f);
        assertEquals(1f, ExtraKeysSetMorph.leaveAlpha(0f), 0.001f);
        assertEquals(0.5f, ExtraKeysSetMorph.leaveAlpha(0.5f), 0.001f);
        assertEquals(0f, ExtraKeysSetMorph.leaveAlpha(1f), 0.001f);
    }
}
