package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import com.termux.app.place.PlaceLookRefresh.Part;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

/** Which parts of the chrome a move between two places' looks has to repaint. */
public class PlaceLookRefreshTest {

    @Test
    public void aCanvasOpacityRepaintsTheTerminalAndTheStatusSurfaceItTints() {
        assertEquals(EnumSet.of(Part.TERMINAL, Part.STATUS), PlaceLookRefresh.partsFor(
            Collections.singleton(TERMUX_APP.KEY_TERMINAL_BACKGROUND_OPACITY)));
    }

    @Test
    public void eachSurfaceSlotRepaintsItsOwnPart() {
        assertEquals(EnumSet.of(Part.DOCK), partsForCell(SurfaceSlot.DOCK, SurfaceProperty.BLUR));
        assertEquals(EnumSet.of(Part.KEYBOARD),
            partsForCell(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY));
        assertEquals(EnumSet.of(Part.STATUS), partsForCell(SurfaceSlot.STATUS, SurfaceProperty.GRAIN));
        assertEquals(EnumSet.of(Part.TERMINAL, Part.STATUS),
            partsForCell(SurfaceSlot.CANVAS, SurfaceProperty.BLUR));
    }

    @Test
    public void aLinkToBaseBelongsToTheSurfaceItLinks() {
        assertEquals(EnumSet.of(Part.KEYBOARD), PlaceLookRefresh.partsFor(Collections.singleton(
            TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX + SurfaceSlot.KEYBOARD.key + "_"
                + SurfaceProperty.OPACITY.key)));
    }

    @Test
    public void theTerminalsFrameAndTheKeyMetricsStayWithTheirParts() {
        assertEquals(EnumSet.of(Part.TERMINAL), PlaceLookRefresh.partsFor(Arrays.asList(
            TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, TERMUX_APP.KEY_TERMINAL_PANE_GAP)));
        assertEquals(EnumSet.of(Part.KEYBOARD), PlaceLookRefresh.partsFor(Arrays.asList(
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY,
            TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE)));
        assertEquals(EnumSet.of(Part.DOCK), PlaceLookRefresh.partsFor(
            Collections.singleton(TERMUX_APP.KEY_APP_LAUNCHER_BUTTON_COUNT)));
    }

    @Test
    public void whatShapesEverySurfaceRepaintsEverything() {
        assertEquals(EnumSet.allOf(Part.class), PlaceLookRefresh.partsFor(
            Collections.singleton(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE)));
        assertEquals(EnumSet.allOf(Part.class), PlaceLookRefresh.partsFor(
            Collections.singleton(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM)));
        // A key this table has never heard of is not a reason to leave a surface stale.
        assertEquals(EnumSet.allOf(Part.class), PlaceLookRefresh.partsFor(
            Collections.singleton("some_future_look_key")));
    }

    @Test
    public void nothingDifferentRepaintsNothing() {
        assertEquals(EnumSet.noneOf(Part.class),
            PlaceLookRefresh.partsFor(Collections.<String>emptySet()));
    }

    private static EnumSet<Part> partsForCell(SurfaceSlot slot, SurfaceProperty property) {
        return PlaceLookRefresh.partsFor(Collections.singleton(
            TermuxAppSharedPreferences.surfaceOverrideKey(slot, property)));
    }
}
