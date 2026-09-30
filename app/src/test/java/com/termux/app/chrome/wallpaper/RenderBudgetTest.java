package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RenderBudgetTest {

    private static boolean fill(RenderBudget budget, int count, float millis) {
        boolean ok = true;
        for (int i = 0; i < count; i++) ok = budget.add(millis);
        return ok;
    }

    @Test
    public void staysHealthyUntilTheWindowCompletes() {
        RenderBudget budget = new RenderBudget();
        assertTrue(fill(budget, RenderBudget.WINDOW - 1, 50f));
        assertEquals(0f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void aFastWindowPasses() {
        RenderBudget budget = new RenderBudget();
        assertTrue(fill(budget, RenderBudget.WINDOW, 2f));
        assertEquals(2f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void aSlowWindowFailsOnItsLastSample() {
        RenderBudget budget = new RenderBudget();
        assertFalse(fill(budget, RenderBudget.WINDOW, 6f));
        assertEquals(6f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void tenPercentSlowOutliersDoNotTripTheLimit() {
        RenderBudget budget = new RenderBudget();
        boolean ok = true;
        for (int i = 0; i < RenderBudget.WINDOW; i++) ok = budget.add(i < 12 ? 20f : 1f);
        assertTrue(ok);
        assertEquals(1f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void moreThanTenPercentSlowTripsIt() {
        RenderBudget budget = new RenderBudget();
        boolean ok = true;
        for (int i = 0; i < RenderBudget.WINDOW; i++) ok = budget.add(i < 13 ? 20f : 1f);
        assertFalse(ok);
        assertEquals(20f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void theWindowStartsOverAfterItCompletes() {
        RenderBudget budget = new RenderBudget();
        assertFalse(fill(budget, RenderBudget.WINDOW, 6f));
        assertTrue(fill(budget, RenderBudget.WINDOW, 1f));
        assertEquals(1f, budget.lastP90Ms(), 0f);
    }
}
