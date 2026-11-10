package com.termux.app.launcher.folder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The numbers a folder grows out of its icon by: pinned without Android. */
public class FolderExpandGeometryTest {
    private static final float EPS = 0.0001f;

    @Test public void theMotionTakes200Ms() {
        assertEquals(200L, FolderExpandGeometry.DURATION_MS);
    }

    @Test public void theStartPutsThePanelsCentreOnTheIconsCentreAtTheIconsScale() {
        // Icon 100x100 at (50, 400); panel 400x300 at (300, 100).
        FolderExpandGeometry g = FolderExpandGeometry.of(50, 400, 150, 500, 300, 100, 700, 400);
        FolderExpandGeometry.Frame f = new FolderExpandGeometry.Frame();
        g.frameAt(0f, f);
        // Larger side ratio: max(100/400, 100/300).
        assertEquals(1f / 3f, f.scale, EPS);
        assertEquals(0f, f.alpha, EPS);
        // Panel centre (500, 250) plus the translation is the icon centre (100, 450).
        assertEquals(100f, 500f + f.translationX, EPS);
        assertEquals(450f, 250f + f.translationY, EPS);
    }

    @Test public void theEndIsTheIdentityAndOpaque() {
        FolderExpandGeometry g = FolderExpandGeometry.of(50, 400, 150, 500, 300, 100, 700, 400);
        FolderExpandGeometry.Frame f = new FolderExpandGeometry.Frame();
        g.frameAt(1f, f);
        assertEquals(1f, f.scale, EPS);
        assertEquals(0f, f.translationX, EPS);
        assertEquals(0f, f.translationY, EPS);
        assertEquals(1f, f.alpha, EPS);
    }

    @Test public void anIconLargerThanThePanelStartsAtFullScale() {
        FolderExpandGeometry g = FolderExpandGeometry.of(0, 0, 500, 500, 0, 0, 100, 100);
        assertEquals(1f, g.startScale(), EPS);
    }

    @Test public void aTinyIconStartsAtTheMinimumScale() {
        FolderExpandGeometry g = FolderExpandGeometry.of(0, 0, 2, 2, 0, 0, 1000, 1000);
        assertEquals(FolderExpandGeometry.MIN_SCALE, g.startScale(), EPS);
    }

    @Test public void theAlphaRampsOverTheFirstFractionThenStaysOpaque() {
        FolderExpandGeometry g = FolderExpandGeometry.of(50, 400, 150, 500, 300, 100, 700, 400);
        FolderExpandGeometry.Frame f = new FolderExpandGeometry.Frame();
        g.frameAt(FolderExpandGeometry.FADE_FRACTION / 2f, f);
        assertEquals(0.5f, f.alpha, EPS);
        g.frameAt(FolderExpandGeometry.FADE_FRACTION, f);
        assertEquals(1f, f.alpha, EPS);
        g.frameAt(0.9f, f);
        assertEquals(1f, f.alpha, EPS);
    }

    @Test public void midwayIsBetweenTheStartAndTheEnd() {
        FolderExpandGeometry g = FolderExpandGeometry.of(50, 400, 150, 500, 300, 100, 700, 400);
        FolderExpandGeometry.Frame f = new FolderExpandGeometry.Frame();
        g.frameAt(0.5f, f);
        assertEquals((1f + 1f / 3f) / 2f, f.scale, EPS);
        assertEquals(g.startTranslationX() / 2f, f.translationX, EPS);
        assertEquals(g.startTranslationY() / 2f, f.translationY, EPS);
    }

    @Test public void noIconGivesTodaysSettleWithNoTranslation() {
        FolderExpandGeometry.Frame f = new FolderExpandGeometry.Frame();
        for (FolderExpandGeometry g : new FolderExpandGeometry[] {
            FolderExpandGeometry.of(0, 0, 0, 0, 0, 0, 400, 300),
            FolderExpandGeometry.of(10, 10, 10, 50, 0, 0, 400, 300),
            FolderExpandGeometry.fallback()}) {
            g.frameAt(0f, f);
            assertEquals(0.94f, f.scale, EPS);
            assertEquals(0f, f.translationX, EPS);
            assertEquals(0f, f.translationY, EPS);
            assertEquals(0f, f.alpha, EPS);
        }
    }
}
