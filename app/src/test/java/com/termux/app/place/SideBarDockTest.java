package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.Surface;

import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PageRef;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

/** Home's dock as a side bar on a phone in landscape (ADR 0020 §12). */
public class SideBarDockTest {
    private static final float DENSITY = 2.625f;
    private static final int PHONE_SW = 411, TABLET_SW = 800;

    private static ResolvedArrangement home(RowPlacement apps) {
        PlaceLayout layout = new PlaceLayout(Edge.TOP, apps, true, Edge.BOTTOM,
            RowPlacement.HIDDEN, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 6);
        return ResolvedArrangement.of(layout, LayoutStyle.DOCKED, DockStyle.GLASS_TRAY, false,
            false, PlaceOrientation.LANDSCAPE, LayoutVariant.NORMAL, PageRef.HOME);
    }

    @Test
    public void theBarStandsOnTheLongEdgeTheRotationNames() {
        assertEquals(Edge.RIGHT, SideBarDock.sideFor(true, true, PHONE_SW, Surface.ROTATION_90));
        assertEquals(Edge.LEFT, SideBarDock.sideFor(true, true, PHONE_SW, Surface.ROTATION_270));
    }

    @Test
    public void portraitTabletsAndTheTerminalKeepTheStoredLayout() {
        assertNull(SideBarDock.sideFor(true, false, PHONE_SW, Surface.ROTATION_0));
        assertNull(SideBarDock.sideFor(true, true, TABLET_SW, Surface.ROTATION_90));
        assertNull(SideBarDock.sideFor(false, true, PHONE_SW, Surface.ROTATION_90));
    }

    @Test
    public void theWorkspaceIsPaddedOnTheBarsSide() {
        for (int rotation : new int[] {Surface.ROTATION_90, Surface.ROTATION_270}) {
            Edge side = SideBarDock.sideFor(true, true, PHONE_SW, rotation);
            ResolvedArrangement bar = home(RowPlacement.BOTTOM).asSideBarDock(side);
            assertTrue(bar.sideBarDock());
            assertTrue(PlaceChromePolicy.appsRailShown(bar.layout()));
            assertEquals(rotation == Surface.ROTATION_90,
                PlaceChromePolicy.appsRailOnRight(bar.layout()));
            DockLayout dock = rail(PlaceChromePolicy.appsRailOnRight(bar.layout()), 48f);
            assertTrue(dock.railActive);
            assertEquals(rotation == Surface.ROTATION_90, dock.railOnRight);
            assertTrue(dock.railBandPx > 0);
        }
    }

    @Test
    public void theBarsIconIsTheStep() {
        for (int stepDp : new int[] {40, 48, 54, 60}) {
            DockLayout dock = rail(true, stepDp);
            assertEquals(Math.round(stepDp * DENSITY), dock.railIconSizePx);
            assertEquals(dock.railIconSizePx + 2 * dock.railIconSpacingPx,
                dock.railSlotLengthPx);
        }
        // A rail with no step keeps the rail's own icon.
        assertEquals(DockLayoutPolicy.railIconSizePx(DENSITY), rail(true, 0f).railIconSizePx);
    }

    @Test
    public void putAwayAppsStayPutAwayAndPortraitIsUnchanged() {
        ResolvedArrangement hidden = home(RowPlacement.HIDDEN);
        ResolvedArrangement bar = hidden.asSideBarDock(Edge.RIGHT);
        assertFalse(bar.sideBarDock());
        assertFalse(PlaceChromePolicy.appsShown(bar.layout()));
        ResolvedArrangement upright = home(RowPlacement.BOTTOM);
        assertFalse(upright.sideBarDock());
        assertSame(upright.layout(), upright.standing(upright.layout()).layout());
        assertTrue(PlaceChromePolicy.appsRowShown(upright.layout()));
    }

    @Test
    public void aRemadeArrangementKeepsTheBar() {
        ResolvedArrangement bar = home(RowPlacement.BOTTOM).asSideBarDock(Edge.LEFT);
        ResolvedArrangement remade = bar.keepingSideBar(ResolvedArrangement.of(bar.layout(),
            LayoutStyle.FLOATING, DockStyle.GLASS_TRAY, false, false,
            PlaceOrientation.LANDSCAPE, LayoutVariant.NORMAL, PageRef.HOME));
        assertTrue(remade.sideBarDock());
        assertTrue(bar.standing(home(RowPlacement.BOTTOM).layout()).sideBarDock());
    }

    private static DockLayout rail(boolean onRight, float iconDp) {
        return DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .appsRowOnEdge(true)
            .appsOnRail(true)
            .railIconDp(iconDp)
            .density(DENSITY)
            .barHeightScale(DockLayoutPolicy.sizePreset(2))
            .dockHorizontalInsetDp(8)
            .configuredCornerRadiusDp(16)
            .appsRowEnabledPref(true)
            .baseToolbarHeightPx(Math.round(37.5f * DENSITY))
            .railOnRight(onRight)
            .build());
    }
}
