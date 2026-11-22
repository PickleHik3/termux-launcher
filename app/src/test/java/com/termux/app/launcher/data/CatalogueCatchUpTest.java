package com.termux.app.launcher.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CatalogueCatchUpTest {

    @Test
    public void firstReadOverLoadedSnapshotRefreshes() {
        assertTrue(CatalogueCatchUp.shouldRefresh(CatalogueCatchUp.UNREAD, 7, true));
    }

    @Test
    public void firstReadWithNothingLoadedOnlyRecords() {
        assertFalse(CatalogueCatchUp.shouldRefresh(CatalogueCatchUp.UNREAD, 7, false));
    }

    @Test
    public void differingSignatureRefreshes() {
        assertTrue(CatalogueCatchUp.shouldRefresh(3, 7, false));
        assertTrue(CatalogueCatchUp.shouldRefresh(3, 7, true));
    }

    @Test
    public void equalSignatureDoesNothing() {
        assertFalse(CatalogueCatchUp.shouldRefresh(7, 7, false));
        assertFalse(CatalogueCatchUp.shouldRefresh(7, 7, true));
    }
}
