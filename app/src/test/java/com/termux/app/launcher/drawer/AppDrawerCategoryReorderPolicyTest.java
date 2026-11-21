package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AppDrawerCategoryReorderPolicyTest {

    private static final int END = AppDrawerCategoryReorderPolicy.END;

    /** Two columns of 100 x 50 tiles, 10 apart, from (0, 0): tile i at col i%2, row i/2. */
    private static AppDrawerCategoryReorderPolicy grid(int count, int source) {
        AppDrawerCategoryReorderPolicy p = new AppDrawerCategoryReorderPolicy();
        p.begin(count, 2, 0f, 0f, 100f, 50f, 10f, 10f, source);
        return p;
    }

    private static float cx(int col, float ratio) {
        return col * 110f + ratio * 100f;
    }

    private static float cy(int row) {
        return row * 60f + 25f;
    }

    @Test public void atRestNothingMoves() {
        AppDrawerCategoryReorderPolicy p = grid(6, 2);
        assertTrue(p.isNoOp());
        assertEquals(3, p.before());
    }

    @Test public void overTheLeftHalfOfATileInsertsBeforeIt() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        assertTrue(p.hover(cx(0, 0.2f), cy(1)));
        assertEquals(2, p.before());
        assertFalse(p.isNoOp());
    }

    @Test public void overTheRightHalfInsertsAfterIt() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        p.hover(cx(1, 0.8f), cy(0));
        assertEquals(2, p.before());
    }

    @Test public void pastTheLastTileIsTheEnd() {
        AppDrawerCategoryReorderPolicy p = grid(6, 0);
        p.hover(cx(1, 0.9f), cy(2));
        assertEquals(END, p.before());
        assertFalse(p.isNoOp());
    }

    @Test public void belowEveryRowClampsToTheLastRow() {
        AppDrawerCategoryReorderPolicy p = grid(6, 0);
        p.hover(cx(1, 0.9f), 900f);
        assertEquals(END, p.before());
    }

    @Test public void aPartialLastRowEndsAtTheLastTile() {
        AppDrawerCategoryReorderPolicy p = grid(5, 0);
        p.hover(cx(1, 0.5f), cy(2));
        assertEquals(END, p.before());
    }

    @Test public void aListIsOneColumn() {
        AppDrawerCategoryReorderPolicy p = new AppDrawerCategoryReorderPolicy();
        p.begin(4, 1, 0f, 0f, 100f, 50f, 0f, 10f, 3);
        p.hover(20f, 60f + 5f);
        assertEquals(1, p.before());
    }

    @Test public void hoverOverTheSourceAndItsNextSlotIsTheSameOrder() {
        AppDrawerCategoryReorderPolicy p = grid(6, 2);
        p.hover(cx(0, 0.1f), cy(1));
        assertEquals(3, p.before());
        assertTrue(p.isNoOp());
        p.hover(cx(1, 0.1f), cy(1));
        assertEquals(3, p.before());
        assertTrue(p.isNoOp());
    }

    @Test public void theLastTileInFrontOfNothingIsTheEnd() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        assertEquals(END, p.before());
        assertTrue(p.isNoOp());
    }

    @Test public void theTargetDoesNotFlickerAtABoundary() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        p.hover(cx(0, 0.1f), cy(0));
        assertEquals(0, p.before());
        // Just across the midpoint: held.
        assertFalse(p.hover(cx(0, 0.55f), cy(0)));
        assertEquals(0, p.before());
        // Back and forth around the boundary never changes it.
        for (int i = 0; i < 6; i++) {
            assertFalse(p.hover(cx(0, i % 2 == 0 ? 0.48f : 0.58f), cy(0)));
        }
        assertEquals(0, p.before());
    }

    @Test public void theTargetChangesOnceClearlyPastTheBoundary() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        p.hover(cx(0, 0.1f), cy(0));
        assertTrue(p.hover(cx(0, 0.9f), cy(0)));
        assertEquals(1, p.before());
    }

    @Test public void hysteresisHoldsBothWays() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        p.hover(cx(1, 0.1f), cy(0));
        assertEquals(1, p.before());
        // Back across the midpoint by less than the margin: held.
        assertFalse(p.hover(cx(0, 0.9f), cy(0)));
        assertEquals(1, p.before());
        assertTrue(p.hover(cx(0, 0.3f), cy(0)));
        assertEquals(0, p.before());
    }

    @Test public void aCentreInTheGapBelongsToTheNeighbourBoundary() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        p.hover(105f, cy(0));
        assertEquals(1, p.before());
    }

    @Test public void aCentreLeftOfTheGridIsTheStartOfItsRow() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        p.hover(-40f, cy(1));
        assertEquals(2, p.before());
    }

    @Test public void aNaNCentreChangesNothing() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        assertFalse(p.hover(Float.NaN, 10f));
        assertEquals(END, p.before());
    }

    @Test public void anEmptyGridHasNothingToAnswer() {
        AppDrawerCategoryReorderPolicy p = grid(0, -1);
        assertFalse(p.hover(10f, 10f));
        assertEquals(END, p.before());
    }

    @Test public void beginStartsAgain() {
        AppDrawerCategoryReorderPolicy p = grid(6, 5);
        p.hover(cx(0, 0.1f), cy(0));
        p.begin(6, 2, 0f, 0f, 100f, 50f, 10f, 10f, 1);
        assertEquals(2, p.before());
        assertTrue(p.isNoOp());
    }

    @Test public void theAnswerFeedsTheOrderStore() {
        AppDrawerCategoryOrder order = new AppDrawerCategoryOrder(null);
        List<AppDrawerCategory> all = order.apply(Arrays.asList(AppDrawerCategory.values()));
        int source = 4;
        AppDrawerCategoryReorderPolicy p = grid(all.size(), source);
        p.hover(cx(0, 0.1f), cy(0));
        int before = p.before();
        assertEquals(0, before);
        List<String> next = order.moved(all.get(source), all.get(before));
        assertEquals(all.get(source).slug, next.get(0));
        assertEquals(all.size(), next.size());

        p.hover(cx(1, 0.9f), cy(10));
        assertEquals(END, p.before());
        next = order.moved(all.get(source), null);
        assertEquals(all.get(source).slug, next.get(next.size() - 1));
    }
}
