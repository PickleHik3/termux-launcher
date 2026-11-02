package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.layout.IconSize;

import org.junit.Test;

import java.util.Collection;
import java.util.EnumSet;

/**
 * The pinned-app count caps the one icon size (ADR 0020 §3, developer 2026-11-01): a step is
 * offered only where the pinned apps fit at it on the designs shown, both styles; the drawn step
 * is the largest no bigger than the choice that fits, and the choice comes back on unpinning.
 */
public class IconSizeCapTest {

    private static final float GRID_EDGE_DP = 16f;
    private static final int GRID_COLUMNS = 4;
    private static final Collection<DockStyle> TRAY = EnumSet.of(DockStyle.GLASS_TRAY);

    private static IconSize cap(Collection<DockStyle> designs, int count, int widthDp) {
        return cap(designs, count, widthDp, false);
    }

    private static IconSize cap(Collection<DockStyle> designs, int count, int widthDp,
                                boolean floating) {
        return IconSizeCap.largestFitting(designs, count, widthDp, GRID_EDGE_DP, GRID_COLUMNS,
            floating);
    }

    @Test
    public void theCapPerStepWidthAndCountOnTheGlassTrayFloating() {
        // 412 dp, the developer's phone, Floating: XL fits 5, L fits 6, M fits 7, 8 only at S.
        assertEquals(IconSize.XL, cap(TRAY, 4, 412, true));
        assertEquals(IconSize.XL, cap(TRAY, 5, 412, true));
        assertEquals(IconSize.L, cap(TRAY, 6, 412, true));
        assertEquals(IconSize.M, cap(TRAY, 7, 412, true));
        assertEquals(IconSize.S, cap(TRAY, 8, 412, true));
        // 360 dp: L fits 5, 6 only at M, 7 only at S.
        assertEquals(IconSize.L, cap(TRAY, 5, 360, true));
        assertEquals(IconSize.M, cap(TRAY, 6, 360, true));
        assertEquals(IconSize.S, cap(TRAY, 7, 360, true));
    }

    @Test
    public void onlyTheStyleTheDockWearsCapsTheStep() {
        // Lens beads fits 7 at M docked but only at S floating (412 dp): a Terminal on beads
        // caps Home at S only while the dock is Floating (developer, 2026-11-02).
        java.util.Set<DockStyle> pong = EnumSet.of(DockStyle.WALL_STRIP, DockStyle.LENS_BEADS);
        assertEquals(IconSize.M, cap(pong, 7, 412, false));
        assertEquals(IconSize.S, cap(pong, 7, 412, true));
        assertEquals(IconSize.M,
            cap(EnumSet.of(DockStyle.GLASS_TRAY, DockStyle.PEEK_GLASS), 7, 412, true));
    }

    @Test
    public void aStepFitsOnlyWhereEveryShownDesignFitsItInTheStyleWorn() {
        java.util.Set<DockStyle> both = EnumSet.of(DockStyle.GLASS_TRAY, DockStyle.LENS_BEADS);
        for (boolean floating : new boolean[] {false, true}) {
            for (IconSize step : IconSize.values()) {
                for (int count = 4; count <= 8; count++) {
                    boolean all = true;
                    for (DockStyle design : both) {
                        all &= IconSizeCap.fits(design, floating, step, count, 412,
                            GRID_EDGE_DP, GRID_COLUMNS);
                    }
                    assertEquals(step + " " + count + " floating=" + floating, all,
                        IconSizeCap.fits(both, step, count, 412, GRID_EDGE_DP, GRID_COLUMNS,
                            floating));
                }
            }
        }
        assertTrue(IconSizeCap.fits(TRAY, IconSize.XL, 0, 412, GRID_EDGE_DP, GRID_COLUMNS, false));
        assertFalse(IconSizeCap.fits(TRAY, IconSize.XL, 7, 412, GRID_EDGE_DP, GRID_COLUMNS, false));
    }

    @Test
    public void anEighthPinDropsTheDrawnStepAndUnpinningBringsTheChoiceBack() {
        IconSize chosen = IconSize.M;
        assertEquals(IconSize.M, IconSizeCap.effective(chosen, cap(TRAY, 7, 412)));
        assertEquals(IconSize.S, IconSizeCap.effective(chosen, cap(TRAY, 8, 412)));
        assertEquals(IconSize.M, IconSizeCap.effective(chosen, cap(TRAY, 7, 412)));
        // A smaller choice is never raised by a roomier cap.
        assertEquals(IconSize.S, IconSizeCap.effective(IconSize.S, cap(TRAY, 4, 412)));
    }

    @Test
    public void theMostThatFitShrinksAsTheIconsGrowAndAgreesWithTheCap() {
        int small = IconSizeCap.mostFitting(TRAY, com.termux.app.place.layout.IconSize.S, 10, 412,
            GRID_EDGE_DP, GRID_COLUMNS, false);
        int large = IconSizeCap.mostFitting(TRAY, com.termux.app.place.layout.IconSize.XL, 10,
            412, GRID_EDGE_DP, GRID_COLUMNS, false);
        org.junit.Assert.assertTrue(small + " at S, " + large + " at XL", small >= large);
        org.junit.Assert.assertTrue(large > 0);
        // The most that fit at a step fits it; one more is the cap's step down.
        org.junit.Assert.assertTrue(IconSizeCap.fits(TRAY, com.termux.app.place.layout.IconSize.XL,
            large, 412, GRID_EDGE_DP, GRID_COLUMNS, false));
        if (large < 10)
            org.junit.Assert.assertFalse(IconSizeCap.fits(TRAY,
                com.termux.app.place.layout.IconSize.XL, large + 1, 412, GRID_EDGE_DP,
                GRID_COLUMNS, false));
    }
}
