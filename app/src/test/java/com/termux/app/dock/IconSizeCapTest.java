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
        return IconSizeCap.largestFitting(designs, count, widthDp, GRID_EDGE_DP, GRID_COLUMNS);
    }

    @Test
    public void theCapPerStepWidthAndCountOnTheGlassTray() {
        // 412 dp, the developer's phone: XL fits 5, L fits 6, M fits 7, 8 only at S.
        assertEquals(IconSize.XL, cap(TRAY, 4, 412));
        assertEquals(IconSize.XL, cap(TRAY, 5, 412));
        assertEquals(IconSize.L, cap(TRAY, 6, 412));
        assertEquals(IconSize.M, cap(TRAY, 7, 412));
        assertEquals(IconSize.S, cap(TRAY, 8, 412));
        // 360 dp: L fits 5, 6 only at M, 7 only at S.
        assertEquals(IconSize.L, cap(TRAY, 5, 360));
        assertEquals(IconSize.M, cap(TRAY, 6, 360));
        assertEquals(IconSize.S, cap(TRAY, 7, 360));
    }

    @Test
    public void everyDesignShownCapsTheStepItsTightestStyleFits() {
        // Lens beads floating fits 7 only at S at 412 dp, so a Terminal on beads caps Home too.
        assertEquals(IconSize.S,
            cap(EnumSet.of(DockStyle.GLASS_TRAY, DockStyle.LENS_BEADS), 7, 412));
        assertEquals(IconSize.M,
            cap(EnumSet.of(DockStyle.GLASS_TRAY, DockStyle.PEEK_GLASS), 7, 412));
    }

    @Test
    public void aStepFitsOnlyWhereEveryShownDesignAndStyleFitsIt() {
        for (IconSize step : IconSize.values()) {
            for (int count = 4; count <= 8; count++) {
                boolean all = true;
                for (boolean capsule : new boolean[] {false, true}) {
                    all &= IconSizeCap.fits(DockStyle.GLASS_TRAY, capsule, step, count, 412,
                        GRID_EDGE_DP, GRID_COLUMNS);
                }
                assertEquals(step + " " + count, all,
                    IconSizeCap.fits(TRAY, step, count, 412, GRID_EDGE_DP, GRID_COLUMNS));
            }
        }
        assertTrue(IconSizeCap.fits(TRAY, IconSize.XL, 0, 412, GRID_EDGE_DP, GRID_COLUMNS));
        assertFalse(IconSizeCap.fits(TRAY, IconSize.XL, 7, 412, GRID_EDGE_DP, GRID_COLUMNS));
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
}
