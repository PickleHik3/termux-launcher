package com.termux.app.launcher.paging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class AzWarmPlanTest {

    private static List<Integer> range(int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    @Test
    public void firstPageIsTheFirstSlotsMatches() {
        assertEquals(Arrays.asList(0, 1, 2, 3), AzWarmPlan.firstPage(range(20), 4));
    }

    @Test
    public void fewerMatchesThanSlotsWarmsAllOfThem() {
        assertEquals(Arrays.asList(0, 1), AzWarmPlan.firstPage(range(2), 7));
    }

    @Test
    public void noMatchesWarmsNothing() {
        assertTrue(AzWarmPlan.firstPage(range(0), 7).isEmpty());
    }

    @Test
    public void nonPositiveSlotsStillWarmsOneEntry() {
        assertEquals(Arrays.asList(0), AzWarmPlan.firstPage(range(5), 0));
    }

    @Test
    public void sameLetterIsNotWarmedTwice() {
        assertFalse(AzWarmPlan.shouldWarm('B', 'B'));
        assertTrue(AzWarmPlan.shouldWarm('C', 'B'));
    }
}
