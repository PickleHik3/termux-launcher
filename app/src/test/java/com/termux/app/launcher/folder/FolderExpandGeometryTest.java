package com.termux.app.launcher.folder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The numbers a folder grows out of its icon by: pinned without Android. */
public class FolderExpandGeometryTest {
    private static final float EPS = 0.0001f;

    @Test public void theMotionTakes200Ms() {
        assertEquals(200L, FolderExpandGeometry.DURATION_MS);
    }

    // Panel 400x300 at (300, 100); the dock case has the icon below it.
    @Test public void anIconBelowThePanelPivotsOnItsBottomEdgeAtTheIconsX() {
        FolderExpandGeometry g = FolderExpandGeometry.of(450, 600, 550, 700, 300, 100, 700, 400);
        assertEquals(200f, g.pivotX(), EPS);
        assertEquals(300f, g.pivotY(), EPS);
        assertEquals(1f / 3f, g.startScale(), EPS);
    }

    @Test public void anIconInsideThePanelsSpanPivotsAtItsOwnCentre() {
        FolderExpandGeometry g = FolderExpandGeometry.of(450, 200, 550, 300, 300, 100, 700, 400);
        assertEquals(200f, g.pivotX(), EPS);
        assertEquals(150f, g.pivotY(), EPS);
    }

    @Test public void anIconToTheSideClampsToThePanelsEdge() {
        FolderExpandGeometry g = FolderExpandGeometry.of(50, 150, 150, 250, 300, 100, 700, 400);
        assertEquals(0f, g.pivotX(), EPS);
        assertEquals(100f, g.pivotY(), EPS);
        g = FolderExpandGeometry.of(800, 150, 900, 250, 300, 100, 700, 400);
        assertEquals(400f, g.pivotX(), EPS);
    }

    @Test public void theScaledPanelAtTheStartStaysWithinThePanelsBounds() {
        int[][] icons = {{450, 600, 550, 700}, {50, 150, 150, 250}, {800, 0, 900, 50},
            {450, 200, 550, 300}};
        FolderExpandGeometry.Frame f = new FolderExpandGeometry.Frame();
        for (int[] i : icons) {
            FolderExpandGeometry g = FolderExpandGeometry.of(i[0], i[1], i[2], i[3],
                300, 100, 700, 400);
            for (float t : new float[] {0f, 0.3f, 1f}) {
                g.frameAt(t, f);
                float left = g.pivotX() - g.pivotX() * f.scale;
                float right = g.pivotX() + (400f - g.pivotX()) * f.scale;
                float top = g.pivotY() - g.pivotY() * f.scale;
                float bottom = g.pivotY() + (300f - g.pivotY()) * f.scale;
                assertTrue(left >= -EPS && right <= 400f + EPS);
                assertTrue(top >= -EPS && bottom <= 300f + EPS);
            }
        }
    }

    @Test public void theEndIsTheIdentityAndOpaque() {
        FolderExpandGeometry g = FolderExpandGeometry.of(50, 400, 150, 500, 300, 100, 700, 400);
        FolderExpandGeometry.Frame f = new FolderExpandGeometry.Frame();
        g.frameAt(1f, f);
        assertEquals(1f, f.scale, EPS);
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
    }

    @Test public void noIconGivesTheSettleAboutThePanelsCentre() {
        FolderExpandGeometry.Frame f = new FolderExpandGeometry.Frame();
        for (FolderExpandGeometry g : new FolderExpandGeometry[] {
            FolderExpandGeometry.of(0, 0, 0, 0, 0, 0, 400, 300),
            FolderExpandGeometry.of(10, 10, 10, 50, 0, 0, 400, 300),
            FolderExpandGeometry.fallback(400, 300)}) {
            g.frameAt(0f, f);
            assertEquals(0.94f, f.scale, EPS);
            assertEquals(200f, g.pivotX(), EPS);
            assertEquals(150f, g.pivotY(), EPS);
            assertEquals(0f, f.alpha, EPS);
        }
    }
}
