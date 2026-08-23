package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pins the per-surface bookkeeping the seven copy-pasted flag triples used to carry. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceDirtyLedgerTest {

    private SurfaceDirtyLedger ledger;

    @Before
    public void setUp() {
        ledger = new SurfaceDirtyLedger();
    }

    @Test
    public void everySurfaceStartsDirtyWithNothingToMatch() {
        for (SurfaceDirtyLedger.Backdrop backdrop : SurfaceDirtyLedger.Backdrop.values()) {
            assertTrue(backdrop.name(), ledger.isDirty(backdrop));
            assertEquals(backdrop.name(), -1, ledger.lastRadiusDp(backdrop));
            assertFalse(backdrop.name(), ledger.lastManagedSource(backdrop));
            assertTrue(backdrop.name(), ledger.matchesLastRect(backdrop, new Rect()));
        }
        assertTrue(ledger.isFrostDirty());
    }

    @Test
    public void recordingAnAppliedCropClearsOnlyThatSurface() {
        Rect rect = new Rect(0, 100, 200, 300);
        ledger.recordApplied(SurfaceDirtyLedger.Backdrop.ACCESSORY, 12, true, rect);

        assertFalse(ledger.isDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY));
        assertEquals(12, ledger.lastRadiusDp(SurfaceDirtyLedger.Backdrop.ACCESSORY));
        assertTrue(ledger.lastManagedSource(SurfaceDirtyLedger.Backdrop.ACCESSORY));
        assertTrue(ledger.matchesLastRect(SurfaceDirtyLedger.Backdrop.ACCESSORY, rect));
        // The other two are independent surfaces with their own crops.
        assertTrue(ledger.isDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR));
        assertTrue(ledger.isDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD));
    }

    @Test
    public void aRecordedRectIsCopiedNotAliased() {
        Rect rect = new Rect(0, 0, 10, 10);
        ledger.recordApplied(SurfaceDirtyLedger.Backdrop.ACCESSORY, 4, false, rect);
        rect.offset(0, 40);

        assertFalse("the ledger must not follow the caller's rect",
            ledger.matchesLastRect(SurfaceDirtyLedger.Backdrop.ACCESSORY, rect));
    }

    @Test
    public void resettingForgetsTheRadiusAndSourceTooNotJustTheGeometry() {
        ledger.recordApplied(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR, 20, true,
            new Rect(0, 0, 5, 5));

        ledger.reset(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR);

        assertTrue(ledger.isDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR));
        assertEquals(-1, ledger.lastRadiusDp(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR));
        assertFalse(ledger.lastManagedSource(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR));
        assertTrue(ledger.matchesLastRect(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR, new Rect()));
    }

    @Test
    public void invalidatingGeometryKeepsTheRadiusMemo() {
        ledger.recordApplied(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, 16, true,
            new Rect(0, 0, 5, 5));

        ledger.invalidateRect(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD);

        assertEquals(16, ledger.lastRadiusDp(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD));
        assertTrue(ledger.lastManagedSource(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD));
        assertTrue(ledger.matchesLastRect(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, new Rect()));
    }

    @Test
    public void markingAllBackdropsDirtyLeavesTheirRadiusMemosAlone() {
        ledger.recordApplied(SurfaceDirtyLedger.Backdrop.ACCESSORY, 12, false, new Rect(0, 0, 1, 1));

        ledger.markAllBackdropsDirty();

        assertTrue(ledger.isDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY));
        assertEquals("a dirty flag alone must not throw away the radius the crop was cut at",
            12, ledger.lastRadiusDp(SurfaceDirtyLedger.Backdrop.ACCESSORY));
    }

    @Test
    public void allFrostCropsShareOneDirtyFlagBecauseTheyShareOneBlurredFrame() {
        Rect palette = new Rect(0, 0, 100, 100);
        ledger.recordFrostRect(SurfaceDirtyLedger.FrostRect.COMMAND_PALETTE, palette);
        ledger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE, 12);
        ledger.clearFrostDirty();
        assertFalse(ledger.isFrostDirty());

        ledger.markFrostDirty();

        assertTrue(ledger.isFrostDirty());
        // The geometry and radius memos survive; only the "is the frame still good" answer changed.
        assertTrue(ledger.matchesFrostRect(SurfaceDirtyLedger.FrostRect.COMMAND_PALETTE, palette));
        assertEquals(12, ledger.frostRadiusDp(SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE));
    }

    @Test
    public void frostRadiiAreGroupedByTheSliderThatTunesThem() {
        ledger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE, 8);
        ledger.setFrostRadiusDp(SurfaceDirtyLedger.FrostRadius.APP_DRAWER, 20);

        assertEquals(8, ledger.frostRadiusDp(SurfaceDirtyLedger.FrostRadius.TOP_PANE));
        assertEquals(20, ledger.frostRadiusDp(SurfaceDirtyLedger.FrostRadius.APP_DRAWER));
        assertEquals(-1, ledger.frostRadiusDp(SurfaceDirtyLedger.FrostRadius.COMMAND_PALETTE));
    }

    @Test
    public void frostRectsAreTrackedPerSurface() {
        Rect status = new Rect(0, 0, 100, 40);
        ledger.recordFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS, status);

        assertTrue(ledger.matchesFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS, status));
        assertFalse(ledger.matchesFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_WINDOW_BAR, status));

        ledger.clearFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS);
        assertFalse(ledger.matchesFrostRect(SurfaceDirtyLedger.FrostRect.TOP_PANE_STATUS, status));
    }
}
