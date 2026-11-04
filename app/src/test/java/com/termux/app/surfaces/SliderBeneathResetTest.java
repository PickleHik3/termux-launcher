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

    private static final EditorChips GLOBAL = new EditorChips(EditorChips.AppliesTo.GLOBAL, null);
    private static final EditorChips WORKSPACE =
        new EditorChips(EditorChips.AppliesTo.WORKSPACE, null);

    private static LookGrid grid() {
        return new LookGrid(new LookGrid.MemoryStore());
    }

    @Test
    public void aGlobalMoveResetsEveryValueBeneathItForThatPropertyOnly() {
        LookGrid grid = grid();
        grid.write(OPACITY, DOCK, (LookGrid.Kind) null, 23);
        grid.write(OPACITY, STATUS, LookGrid.Kind.TERMINAL, 31);
        grid.write(OPACITY, null, LookGrid.Kind.HOME, 47);
        grid.write(FROST, DOCK, (LookGrid.Kind) null, 2);

        SurfacePresets.clearBeneathSlider(grid, GLOBAL, OPACITY);

        assertFalse(grid.has(OPACITY, DOCK, (LookGrid.Kind) null));
        assertFalse(grid.has(OPACITY, STATUS, LookGrid.Kind.TERMINAL));
        assertFalse(grid.has(OPACITY, null, LookGrid.Kind.HOME));
        assertTrue("another property stays detached", grid.has(FROST, DOCK, (LookGrid.Kind) null));
        assertEquals(2, grid.value(FROST, DOCK, (LookGrid.Kind) null));
    }

    @Test
    public void aWorkspaceMoveResetsTheWorkspaceKindsAndLeavesHomeAsItLooked() {
        LookGrid grid = grid();
        grid.write(OPACITY, DOCK, (LookGrid.Kind) null, 23);
        grid.write(OPACITY, STATUS, LookGrid.Kind.DISPLAY, 31);
        grid.write(OPACITY, STATUS, LookGrid.Kind.HOME, 12);

        SurfacePresets.clearBeneathSlider(grid, WORKSPACE, OPACITY);

        assertFalse(grid.has(OPACITY, STATUS, LookGrid.Kind.DISPLAY));
        assertFalse(grid.has(OPACITY, DOCK, LookGrid.Kind.TERMINAL));
        assertFalse(grid.has(OPACITY, DOCK, LookGrid.Kind.DISPLAY));
        assertFalse(grid.has(OPACITY, DOCK, (LookGrid.Kind) null));
        assertEquals("the dock's Global value still reaches Home", 23,
            grid.value(OPACITY, DOCK, LookGrid.Kind.HOME));
        assertEquals("a Home value is not the Workspace's", 12,
            grid.value(OPACITY, STATUS, LookGrid.Kind.HOME));
    }

    @Test
    public void anElementCardMoveResetsNothing() {
        LookGrid grid = grid();
        grid.write(OPACITY, STATUS, (LookGrid.Kind) null, 31);
        grid.write(OPACITY, DOCK, LookGrid.Kind.HOME, 23);

        SurfacePresets.clearBeneathSlider(grid,
            new EditorChips(EditorChips.AppliesTo.GLOBAL, DOCK), OPACITY);
        SurfacePresets.clearBeneathSlider(grid,
            new EditorChips(EditorChips.AppliesTo.WORKSPACE, DOCK), OPACITY);

        assertEquals(31, grid.value(OPACITY, STATUS, (LookGrid.Kind) null));
        assertEquals(23, grid.value(OPACITY, DOCK, LookGrid.Kind.HOME));
    }

    @Test
    public void aHomeCardMoveResetsHomeAndLeavesTheWorkspaceAsItLooked() {
        LookGrid grid = grid();
        grid.write(OPACITY, DOCK, (LookGrid.Kind) null, 23);
        grid.write(OPACITY, STATUS, LookGrid.Kind.HOME, 12);
        grid.write(OPACITY, STATUS, LookGrid.Kind.TERMINAL, 31);
        SurfacePresets.clearBeneathSlider(grid,
            new EditorChips(EditorChips.AppliesTo.HOME, null), OPACITY);
        assertFalse(grid.has(OPACITY, STATUS, LookGrid.Kind.HOME));
        assertFalse(grid.has(OPACITY, DOCK, LookGrid.Kind.HOME));
        assertEquals("the dock's Global value still reaches the Workspace", 23,
            grid.value(OPACITY, DOCK, LookGrid.Kind.TERMINAL));
        assertEquals(23, grid.value(OPACITY, DOCK, LookGrid.Kind.DISPLAY));
        assertEquals(31, grid.value(OPACITY, STATUS, LookGrid.Kind.TERMINAL));
    }

    @Test
    public void aBaseOnlyPropertyHasNothingBeneath() {
        LookGrid grid = grid();
        grid.write(LookGrid.Property.MARGIN, null, (LookGrid.Kind) null, 14);
        SurfacePresets.clearBeneathSlider(grid, GLOBAL, LookGrid.Property.MARGIN);
        assertEquals(14, grid.value(LookGrid.Property.MARGIN, null, (LookGrid.Kind) null));
    }
}
