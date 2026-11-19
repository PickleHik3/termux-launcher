package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

/**
 * Last change wins for the Global and Workspace cards' sliders (developer, 2026-11-04), the pure part
 * ({@link SurfacePresets#clearBeneathSlider}): a Global move resets the values beneath it for its
 * one property; a page card's (Home, Workspace) resets those on its kinds only; an element
 * card's resets nothing.
 * The editor's side (Discard, the cards' own writes) is in {@link PresetApplyTest}.
 */
public class SliderBeneathResetTest {

    private static final LookGrid.Property OPACITY = LookGrid.Property.OPACITY;
    private static final LookGrid.Property FROST = LookGrid.Property.FROST;
    private static final LookGrid.Element DOCK = LookGrid.Element.DOCK;
    private static final LookGrid.Element STATUS = LookGrid.Element.STATUS;

    private static final EditorChips GLOBAL = new EditorChips(null);

    private static LookGrid grid() {
        return new LookGrid(new LookGrid.MemoryStore());
    }

    @Test
    public void aGlobalMoveResetsEveryElementsOwnValueForThatPropertyOnly() {
        LookGrid grid = grid();
        grid.write(OPACITY, DOCK, 23);
        grid.write(OPACITY, STATUS, 31);
        grid.write(FROST, DOCK, 2);

        SurfacePresets.clearBeneathSlider(grid, GLOBAL, OPACITY);

        assertFalse(grid.has(OPACITY, DOCK));
        assertFalse(grid.has(OPACITY, STATUS));
        assertTrue("another property stays the dock's own", grid.has(FROST, DOCK));
        assertEquals(2, grid.value(FROST, DOCK));
    }

    @Test
    public void anElementCardMoveResetsNothing() {
        LookGrid grid = grid();
        grid.write(OPACITY, STATUS, 31);

        SurfacePresets.clearBeneathSlider(grid, new EditorChips(DOCK), OPACITY);

        assertEquals(31, grid.value(OPACITY, STATUS));
    }

    @Test
    public void aGlobalOnlyPropertyHasNothingBeneath() {
        LookGrid grid = grid();
        grid.write(LookGrid.Property.MARGIN, null, 14);
        SurfacePresets.clearBeneathSlider(grid, GLOBAL, LookGrid.Property.MARGIN);
        assertEquals(14, grid.value(LookGrid.Property.MARGIN, null));
    }
}
