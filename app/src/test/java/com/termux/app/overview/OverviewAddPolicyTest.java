package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.overview.OverviewAddPolicy.Row;

import org.junit.Test;

import java.util.Arrays;

public class OverviewAddPolicyTest {

    @Test public void allThreeRowsInOrder() {
        assertEquals(Arrays.asList(Row.HOME_PAGE, Row.WORKSPACE, Row.ASSISTANT),
            OverviewAddPolicy.rows(true, false));
    }

    @Test public void assistantGoesWhileTheWallHasOne() {
        assertEquals(Arrays.asList(Row.HOME_PAGE, Row.WORKSPACE),
            OverviewAddPolicy.rows(true, true));
    }

    @Test public void homePageGoesUnderUseAsTerminalOnly() {
        assertEquals(Arrays.asList(Row.WORKSPACE, Row.ASSISTANT),
            OverviewAddPolicy.rows(false, false));
        assertEquals(Arrays.asList(Row.WORKSPACE), OverviewAddPolicy.rows(false, true));
    }

    @Test public void onlyAHomePageNeedsAHold() {
        assertTrue(OverviewAddPolicy.requiresHold(Row.HOME_PAGE));
        assertFalse(OverviewAddPolicy.requiresHold(Row.WORKSPACE));
        assertFalse(OverviewAddPolicy.requiresHold(Row.ASSISTANT));
    }

    @Test public void landsAfterTheCardInFocusOrAtTheEnd() {
        assertEquals(7, OverviewAddPolicy.afterPageId(7));
        assertEquals(0, OverviewAddPolicy.afterPageId(0));
        assertEquals(OverviewAddPolicy.END, OverviewAddPolicy.afterPageId(-1));
    }

    @Test public void findsTheNewPageByIdentity() {
        long[] before = {OverviewAddPolicy.key(1, -1), OverviewAddPolicy.key(2, -1)};
        long[] after = {OverviewAddPolicy.key(1, -1), OverviewAddPolicy.key(9, -1),
            OverviewAddPolicy.key(2, -1)};
        assertEquals(1, OverviewAddPolicy.addedIndex(before, after));
    }

    @Test public void findsTheNewWindowOfAWorkspace() {
        long[] before = {OverviewAddPolicy.key(3, 0), OverviewAddPolicy.key(3, 1)};
        long[] after = {OverviewAddPolicy.key(3, 0), OverviewAddPolicy.key(3, 1),
            OverviewAddPolicy.key(3, 2)};
        assertEquals(2, OverviewAddPolicy.addedIndex(before, after));
    }

    @Test public void nothingNewIsMinusOne() {
        long[] same = {OverviewAddPolicy.key(1, 0)};
        assertEquals(-1, OverviewAddPolicy.addedIndex(same, same));
    }

    @Test public void keysKeepNegativeIndexesApart() {
        assertFalse(OverviewAddPolicy.key(1, -1) == OverviewAddPolicy.key(1, 0));
        assertFalse(OverviewAddPolicy.key(1, -1) == OverviewAddPolicy.key(2, -1));
    }
}
