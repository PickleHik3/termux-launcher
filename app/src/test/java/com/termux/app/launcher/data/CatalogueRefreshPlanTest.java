package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.data.CatalogueRefreshPlan.Route;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CatalogueRefreshPlanTest {

    @Test
    public void aPageThatListsAppsRefreshesThroughTheDock() {
        assertEquals(Route.DOCK, CatalogueRefreshPlan.route(true, true, true));
        assertEquals(Route.DOCK, CatalogueRefreshPlan.route(true, false, false));
    }

    /** The install heard on a page with no apps row and no index: the dock loaded elsewhere. */
    @Test
    public void aPageWithoutTheRowStillRefreshesADockThatHoldsTheCatalogue() {
        assertEquals(Route.DOCK, CatalogueRefreshPlan.route(false, true, true));
    }

    /** No dock ever loaded, but the drawer did: its snapshot must not stay on the old list. */
    @Test
    public void aSnapshotHeldForOtherReadersIsRefreshedDirectly() {
        assertEquals(Route.SNAPSHOT, CatalogueRefreshPlan.route(false, false, true));
    }

    @Test
    public void nothingLoadedScansNothing() {
        assertEquals(Route.NONE, CatalogueRefreshPlan.route(false, false, false));
    }

    @Test
    public void aChangeToAPackInUseTouchesTheIconPack() {
        Set<String> changed = new HashSet<>(Arrays.asList("com.example.app", "com.example.pack"));
        assertTrue(CatalogueRefreshPlan.touchesIconPack(changed, "com.example.pack", null));
        assertTrue(CatalogueRefreshPlan.touchesIconPack(changed, null, "com.example.pack"));
    }

    @Test
    public void anyOtherChangeLeavesTheIconPackAlone() {
        Set<String> changed = Collections.singleton("com.example.app");
        assertFalse(CatalogueRefreshPlan.touchesIconPack(changed, "com.example.pack", null));
        assertFalse(CatalogueRefreshPlan.touchesIconPack(changed, null, null));
        assertFalse(CatalogueRefreshPlan.touchesIconPack(null, "com.example.pack", null));
        assertFalse(CatalogueRefreshPlan.touchesIconPack(
            Collections.emptySet(), "com.example.pack", null));
    }
}
