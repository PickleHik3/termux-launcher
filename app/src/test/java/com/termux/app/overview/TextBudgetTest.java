package com.termux.app.overview;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TextBudgetTest {

    @Test public void givesWhatItHasThenNothing() {
        TextBudget budget = new TextBudget(10);
        assertEquals(6, budget.take(6));
        assertEquals(4, budget.take(6));
        assertEquals(0, budget.take(1));
        assertEquals(0, budget.left());
    }

    @Test public void chargeCutsTextToTheRoomLeft() {
        TextBudget budget = new TextBudget(5);
        assertEquals("abc", budget.charge("abc"));
        assertEquals("de", budget.charge("def"));
        assertEquals("", budget.charge("xyz"));
    }

    @Test public void aNegativeBudgetIsEmpty() {
        assertEquals(0, new TextBudget(-3).take(2));
    }
}
