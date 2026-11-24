package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.app.AppEdition;

import org.junit.Test;

public class GraphicsAddonRowTest {

    private static GraphicsAddonRow.Row row(MesaWaylandOverlay.State s, boolean listed) {
        return GraphicsAddonRow.of(s, listed);
    }

    @Test public void missingOffersTheInstall() {
        GraphicsAddonRow.Row r = row(MesaWaylandOverlay.State.MISSING, true);
        assertEquals(R.string.settings_gui_apps_addon_install, r.summary);
        assertEquals(GraphicsAddonRow.Action.COPY_INSTALL, r.action);
    }

    @Test public void mismatchOffersTheUpdate() {
        GraphicsAddonRow.Row r = row(MesaWaylandOverlay.State.MISMATCH, true);
        assertEquals(R.string.settings_gui_apps_addon_update, r.summary);
        assertEquals(GraphicsAddonRow.Action.COPY_INSTALL, r.action);
    }

    @Test public void installedAndNoMesaOfferNothing() {
        assertEquals(R.string.settings_gui_apps_addon_installed,
            row(MesaWaylandOverlay.State.USE, true).summary);
        assertEquals(GraphicsAddonRow.Action.NONE, row(MesaWaylandOverlay.State.USE, false).action);
        assertEquals(R.string.settings_gui_apps_addon_no_mesa,
            row(MesaWaylandOverlay.State.NO_MESA, true).summary);
        assertEquals(GraphicsAddonRow.Action.NONE, row(MesaWaylandOverlay.State.NO_MESA, true).action);
    }

    @Test public void anUnlistedAddonSaysSoInsteadOfOffering() {
        for (MesaWaylandOverlay.State s : new MesaWaylandOverlay.State[] {
            MesaWaylandOverlay.State.MISSING, MesaWaylandOverlay.State.MISMATCH}) {
            GraphicsAddonRow.Row r = row(s, false);
            assertEquals(R.string.settings_gui_apps_addon_unavailable, r.summary);
            assertEquals(GraphicsAddonRow.Action.NONE, r.action);
        }
    }

    @Test public void showsOnlyForWaylandChosenOnTheStockEdition() {
        assertTrue(GraphicsAddonRow.shows(true, LinuxDisplayBackend.WAYLAND, AppEdition.TERMUX));
        assertFalse(GraphicsAddonRow.shows(false, LinuxDisplayBackend.WAYLAND, AppEdition.TERMUX));
        assertFalse(GraphicsAddonRow.shows(true, LinuxDisplayBackend.X11, AppEdition.TERMUX));
        assertFalse(GraphicsAddonRow.shows(true, LinuxDisplayBackend.WAYLAND, AppEdition.VAJ));
        assertFalse(GraphicsAddonRow.shows(true, LinuxDisplayBackend.WAYLAND, AppEdition.NIX));
    }

    @Test public void listedFindsTheItemInAnyCatalogue() {
        assertTrue(GraphicsAddonRow.listed(null, "a\tmesa-wayland-egl\tb\n"));
        assertFalse(GraphicsAddonRow.listed(null, "a\tfastfetch\n"));
        assertEquals("tlstore install mesa-wayland-egl", GraphicsAddonRow.INSTALL_COMMAND);
    }
}
