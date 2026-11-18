package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.Side;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.SeamFade;
import com.termux.app.place.StackScene;

import org.junit.Test;

import java.util.Arrays;

/** {@link TopStackScene}: one shared slab, chips sliding across it, parity at both rests. */
public class TopStackSceneTest {

    private static final int W = 1000;
    private static final float[] PS = {0f, 0.1f, 0.5f, 0.9f, 1f};
    private static final Side[] SIDES = {Side.HOME, Side.WORKSPACE};
    private static final PlaceLayout.Edge[] EDGES = {PlaceLayout.Edge.TOP, PlaceLayout.Edge.BOTTOM};
    private static final boolean[] BOOLS = {false, true};
    private static final int PALE = 0xFFF0F0F0;
    private static final int DARK = 0xFF101010;

    private static TopStackScene.End end(Side side, PlaceLayout.Edge edge, int outline,
                                         boolean chips, int ink, int look) {
        TopStackScene.End e = new TopStackScene.End();
        e.barShown = true;
        e.edge = edge;
        e.side = side;
        e.lookId = look;
        e.outlineKey = outline;
        e.chipsShown = chips;
        e.stripShown = true;
        e.stripDressedForBar = true;
        Arrays.fill(e.inks, ink);
        return e;
    }

    private static void assertSame(TopStackScene.Frame a, TopStackScene.Frame b) {
        assertTrue(a.sameAs(b));
        assertEquals(a.wornLookId(), b.wornLookId());
    }

    @Test
    public void restPutsChipsOneWallOffOnHome() {
        TopStackScene.Frame f = new TopStackScene.Frame();
        for (PlaceLayout.Edge edge : EDGES) {
            TopStackScene.rest(end(Side.HOME, edge, 1, true, PALE, 1), W, f);
            assertEquals(W, f.chipsTx, 0f);
            assertEquals(0f, f.slabTx, 0f);
            TopStackScene.rest(end(Side.WORKSPACE, edge, 1, true, PALE, 1), W, f);
            assertEquals(0f, f.chipsTx, 0f);
            assertTrue(f.chipsShown);
        }
    }

    @Test
    public void sharedMatrixKeepsSlabAndParity() {
        TopStackScene.Frame f = new TopStackScene.Frame();
        TopStackScene.Frame g = new TopStackScene.Frame();
        TopStackScene.Frame r = new TopStackScene.Frame();
        for (PlaceLayout.Edge edge : EDGES) {
            for (Side a : SIDES) for (Side b : SIDES)
                for (boolean ca : BOOLS) for (boolean cb : BOOLS) {
                    TopStackScene.End from = end(a, edge, 7, ca, PALE, 1);
                    TopStackScene.End to = end(b, edge, 7, cb, DARK, 2);
                    assertTrue(TopStackScene.shared(from, to));
                    float prevShare = -1f;
                    for (float p : PS) {
                        TopStackScene.at(from, to, p, W, Float.NaN, false, f);
                        assertTrue(f.shared);
                        assertEquals(0f, f.slabTx, 0f);
                        assertEquals(0f, f.stripTx, 0f);
                        assertEquals(StackScene.sideSlide(Side.WORKSPACE, a, b, p, W), f.chipsTx, 0f);
                        assertTrue(f.lookShare >= prevShare);
                        prevShare = f.lookShare;
                        if (p > 0f && p < 1f) {
                            assertEquals(ca || cb, f.chipsShown);
                            if (f.lookShare > 0f && f.lookShare < 1f) {
                                for (int i = 0; i < TopStackScene.INK_COUNT; i++) {
                                    int mid = f.inks[i] & 0xFF;
                                    assertTrue(mid > 0x10 && mid < 0xF0);
                                }
                            }
                        }
                        // a ride is ignored for a shared slab
                        TopStackScene.at(from, to, p, W, 123f, true, g);
                        assertSame(f, g);
                    }
                    TopStackScene.at(from, to, 0f, W, Float.NaN, false, f);
                    TopStackScene.rest(from, W, r);
                    assertSame(r, f);
                    TopStackScene.at(from, to, 1f, W, Float.NaN, false, f);
                    TopStackScene.rest(to, W, r);
                    assertSame(r, f);
                }
        }
    }

    @Test
    public void withinASideChipsDoNotMove() {
        TopStackScene.Frame f = new TopStackScene.Frame();
        for (Side s : SIDES) {
            TopStackScene.End a = end(s, PlaceLayout.Edge.TOP, 1, true, PALE, 1);
            TopStackScene.End b = end(s, PlaceLayout.Edge.TOP, 1, true, DARK, 2);
            float rest = s == Side.HOME ? W : 0f;
            for (float p : PS) {
                TopStackScene.at(a, b, p, W, Float.NaN, false, f);
                assertEquals(rest, f.chipsTx, 0f);
            }
        }
    }

    @Test
    public void inksEndsExactAndLookShareFollowsSeamFade() {
        TopStackScene.End a = end(Side.HOME, PlaceLayout.Edge.TOP, 1, true, PALE, 1);
        TopStackScene.End b = end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 1, true, DARK, 2);
        TopStackScene.Frame f = new TopStackScene.Frame();
        TopStackScene.at(a, b, 0f, W, Float.NaN, false, f);
        assertEquals(PALE, f.inks[0]);
        assertEquals(1, f.wornLookId());
        TopStackScene.at(a, b, 1f, W, Float.NaN, false, f);
        assertEquals(DARK, f.inks[TopStackScene.INK_COUNT - 1]);
        assertEquals(2, f.wornLookId());
        TopStackScene.at(a, b, 0.5f, W, Float.NaN, false, f);
        assertEquals(SeamFade.share(0.5f), f.lookShare, 0f);
        assertEquals(TopStackScene.MIXED, f.wornLookId());
        assertTrue((f.inks[0] & 0xFF) > 0x10 && (f.inks[0] & 0xFF) < 0xF0);
        // early in the crossing the look has not started
        TopStackScene.at(a, b, 0.1f, W, Float.NaN, false, f);
        assertEquals(PALE, f.inks[0]);
    }

    @Test
    public void differentOutlinesRideWithTheWearer() {
        TopStackScene.Frame f = new TopStackScene.Frame();
        for (PlaceLayout.Edge edge : EDGES) {
            TopStackScene.End docked = end(Side.HOME, edge, 1, true, PALE, 1);
            TopStackScene.End floating = end(Side.WORKSPACE, edge, 2, false, DARK, 2);
            assertFalse(TopStackScene.shared(docked, floating));
            for (float p : new float[]{0.1f, 0.5f, 0.9f}) {
                TopStackScene.at(docked, floating, p, W, 300f, true, f);
                assertFalse(f.shared);
                assertEquals(300f, f.slabTx, 0f);
                assertEquals(300f, f.stripTx, 0f);
                assertEquals(StackScene.sideSlide(Side.WORKSPACE, Side.HOME, Side.WORKSPACE, p, W) - 300f,
                    f.chipsTx, 0f);
                assertEquals(2, f.wornLookId());
                assertEquals(0f, f.lookShare, 0f);
                assertEquals(DARK, f.inks[0]);
                assertFalse(f.chipsShown);
                TopStackScene.at(docked, floating, p, W, 300f, false, f);
                assertEquals(1, f.wornLookId());
                assertEquals(PALE, f.inks[0]);
                assertTrue(f.chipsShown);
            }
            floating.stripDressedForBar = false;
            TopStackScene.at(docked, floating, 0.5f, W, 300f, true, f);
            assertEquals(0f, f.stripTx, 0f);
        }
    }

    @Test
    public void barOnlyOneEndShowsRides() {
        TopStackScene.End a = end(Side.HOME, PlaceLayout.Edge.TOP, 1, true, PALE, 1);
        TopStackScene.End b = end(Side.WORKSPACE, PlaceLayout.Edge.TOP, 1, true, DARK, 2);
        b.barShown = false;
        assertFalse(TopStackScene.shared(a, b));
        TopStackScene.Frame f = new TopStackScene.Frame();
        TopStackScene.at(a, b, 0.5f, W, -200f, false, f);
        assertEquals(-200f, f.slabTx, 0f);
    }

    @Test
    public void unsharedWithoutRideFadesInPlaceWithParity() {
        TopStackScene.Frame f = new TopStackScene.Frame();
        TopStackScene.Frame r = new TopStackScene.Frame();
        for (PlaceLayout.Edge edge : EDGES) {
            TopStackScene.End a = end(Side.HOME, edge, 1, true, PALE, 1);
            TopStackScene.End b = end(Side.WORKSPACE, PlaceLayout.Edge.LEFT, 2, true, DARK, 2);
            assertFalse(TopStackScene.shared(a, b));
            for (float p : PS) {
                TopStackScene.at(a, b, p, W, Float.NaN, false, f);
                assertFalse(f.shared);
                assertEquals(0f, f.slabTx, 0f);
                assertEquals(0f, f.stripTx, 0f);
            }
            TopStackScene.at(a, b, 0f, W, Float.NaN, false, f);
            TopStackScene.rest(a, W, r);
            assertSame(r, f);
            TopStackScene.at(a, b, 1f, W, Float.NaN, false, f);
            TopStackScene.rest(b, W, r);
            assertSame(r, f);
        }
    }

    @Test
    public void sameAsAndSetCopy() {
        TopStackScene.End a = end(Side.HOME, PlaceLayout.Edge.TOP, 1, true, PALE, 1);
        TopStackScene.End c = new TopStackScene.End();
        c.set(a);
        assertEquals(PALE, c.inks[3]);
        assertEquals(PlaceLayout.Edge.TOP, c.edge);
        TopStackScene.Frame f = new TopStackScene.Frame();
        TopStackScene.Frame g = new TopStackScene.Frame();
        TopStackScene.rest(a, W, f);
        g.set(f);
        assertTrue(f.sameAs(g));
        g.chipsTx += 1f;
        assertFalse(f.sameAs(g));
    }
}
