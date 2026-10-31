package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.FrostStops;

import org.junit.Test;

/**
 * Pins the dock's one surface (ADR 0018 §1, D3, D8, D11) on pong's setup: 1080 x 2412 at 420 dpi,
 * a 63 px navigation inset (2412 - 2349), the row's content ending on the inset at 2349, five
 * 48 dp icons whose centres stand on row 2139.
 */
public class DockSpecTest {

    private static final int W = 1080;
    private static final int H = 2412;
    private static final float DENSITY = 420f / 160f;
    private static final int NAV = 63;
    private static final int ROW_BOTTOM = H - NAV;     // 2349
    private static final int ROW_TOP = 2034;
    private static final int ICON = 126;               // 48 dp
    private static final int ICON_TOP = 2139 - ICON / 2;

    private static DockSpec.Input pong(DockStyle design, boolean floating, boolean keyboard) {
        DockSpec.Input in = new DockSpec.Input();
        in.kind = DockSpec.PageKind.HOME;
        in.design = design;
        in.floatingStyle = floating;
        in.keyboardShown = keyboard;
        in.look = new DockSpec.Look(FrostStops.blurDpFor(2), 34, 0, 0, false, 26f, null, null);
        in.screenWidthPx = W;
        in.screenHeightPx = H;
        in.density = DENSITY;
        in.navInsetPx = NAV;
        in.leftPx = 0;
        in.rightPx = W;
        in.rowTopPx = ROW_TOP;
        in.rowBottomPx = ROW_BOTTOM;
        float[] boxes = new float[5 * 4];
        float slot = W / 5f;
        for (int i = 0; i < 5; i++) {
            float cx = slot * i + slot / 2f;
            boxes[i * 4] = cx - ICON / 2f;
            boxes[i * 4 + 1] = ICON_TOP;
            boxes[i * 4 + 2] = cx + ICON / 2f;
            boxes[i * 4 + 3] = ICON_TOP + ICON;
        }
        in.iconBoxes = boxes;
        in.iconCount = 5;
        return in;
    }

    /** Floating + Peek, opacity 34, frost stop 2: the setup the seam at row 2349 was read on. */
    @Test
    public void pongFloatingPeekIsOneSurfaceToTheScreenEdge() {
        DockSpec spec = DockSpec.of(pong(DockStyle.PEEK_GLASS, true, false));
        assertEquals(DockStyle.PEEK_GLASS, spec.design);
        assertFalse("the Peek card rises from the edge, it does not float", spec.floats);
        assertTrue(spec.foot);
        assertEquals(NAV, spec.footHeightPx);
        assertEquals(DockSpec.Base.SCREEN_EDGE, spec.base);
        assertNotNull(spec.card);
        assertEquals(32, spec.left);
        assertEquals(1048, spec.right);
        assertEquals(2139, spec.top);
        assertEquals(H, spec.bottom);
        assertTrue(spec.seamsBottom());
        assertEquals(0f, spec.bottomCornerPx, 0f);
        assertEquals(28f * DENSITY, spec.topCornerPx, 0.01f);
        assertEquals(0.34f, spec.tintAlpha, 1e-6f);
        assertEquals(FrostStops.blurDpFor(2), spec.blurDp);
        assertTrue(spec.rim);
        assertEquals(PeekGlassGeometry.reflectionAlpha(PeekGlassGeometry.REFLECTION_DEFAULT),
            spec.reflectionAlpha, 1e-6f);
    }

    @Test
    public void dockedTrayKeepsItsFootWithTheKeyboardDown() {
        DockSpec spec = DockSpec.of(pong(DockStyle.GLASS_TRAY, false, false));
        assertFalse(spec.floats);
        assertTrue(spec.foot);
        assertEquals(ROW_TOP, spec.top);
        assertEquals(0, spec.left);
        assertEquals(W, spec.right);
        assertEquals(H, spec.bottom);
        assertTrue(spec.seamsBottom());
        assertNull(spec.card);
        assertEquals(0f, spec.reflectionAlpha, 0f);
    }

    @Test
    public void floatingTrayIsACardWithAirUnderItAndNoFoot() {
        DockSpec.Input in = pong(DockStyle.GLASS_TRAY, true, false);
        in.capsuleGapPx = 21;
        in.leftPx = 32;
        in.rightPx = 1048;
        in.rowBottomPx = ROW_BOTTOM - 21;
        DockSpec spec = DockSpec.of(in);
        assertTrue(spec.floats);
        assertFalse(spec.foot);
        assertEquals(DockSpec.Base.AIR, spec.base);
        assertEquals(ROW_BOTTOM - 21, spec.bottom);
        assertFalse(spec.seamsBottom());
        assertEquals(spec.topCornerPx, spec.bottomCornerPx, 0f);
        assertEquals(26f * DENSITY, spec.topCornerPx, 0.01f);
    }

    @Test
    public void beadsDockedOwnTheFoot() {
        DockSpec spec = DockSpec.of(pong(DockStyle.LENS_BEADS, false, false));
        assertEquals(DockStyle.LENS_BEADS, spec.design);
        assertTrue(spec.surface.beads);
        assertTrue(spec.foot);
        assertEquals(H, spec.bottom);
    }

    /** D8: with the keyboard up the keyboard stands on the edge and owns the band. */
    @Test
    public void keyboardUpHandsTheFootToTheKeyboard() {
        DockSpec.Input in = pong(DockStyle.GLASS_TRAY, false, true);
        in.rowBottomPx = 1400;
        DockSpec spec = DockSpec.of(in);
        assertFalse(spec.foot);
        assertEquals(0, spec.footHeightPx);
        assertEquals(DockSpec.Base.STACK, spec.base);
        assertEquals(1400, spec.bottom);
        assertTrue("the keyboard continues the dock's bottom edge", spec.seamsBottom());
    }

    /** D11: the dock over the keyboard on Home wears Home's design, Peek included. */
    @Test
    public void homePeekStaysPeekOverTheKeyboard() {
        for (boolean floating : new boolean[] {false, true}) {
            DockSpec.Input in = pong(DockStyle.PEEK_GLASS, floating, true);
            DockSpec spec = DockSpec.of(in);
            assertEquals(DockStyle.PEEK_GLASS, spec.design);
            assertTrue(spec.surface.peek);
            assertNotNull(spec.card);
            assertFalse(spec.foot);
            assertEquals(DockSpec.Base.STACK, spec.base);
            assertEquals(ROW_BOTTOM, spec.bottom);
        }
    }

    @Test
    public void terminalKindWearsItsOwnDesignWhateverTheKeyboard() {
        for (boolean keyboard : new boolean[] {false, true}) {
            DockSpec.Input in = pong(DockStyle.GLASS_TRAY, false, keyboard);
            in.kind = DockSpec.PageKind.TERMINAL;
            assertEquals(DockStyle.GLASS_TRAY, DockSpec.of(in).design);
        }
    }

    @Test
    public void noFootWhileTheSystemKeyboardIsVisible() {
        DockSpec.Input in = pong(DockStyle.GLASS_TRAY, false, false);
        in.imeVisible = true;
        assertFalse(DockSpec.of(in).foot);
    }

    @Test
    public void rimOffCellTurnsTheRimOff() {
        DockSpec.Input in = pong(DockStyle.GLASS_TRAY, false, false);
        in.look = new DockSpec.Look(8, 100, 0, 0, true, 0f, null, null);
        DockSpec spec = DockSpec.of(in);
        assertFalse(spec.rim);
        assertEquals(1f, spec.tintAlpha, 0f);
    }

    /** Parity: whenever the foot exists the surface ends on the screen's edge with a seam. */
    @Test
    public void footImpliesScreenBottomAndSeamEverywhere() {
        for (DockSpec.PageKind kind : DockSpec.PageKind.values())
            for (DockStyle design : DockStyle.CHOICES)
                for (boolean floating : new boolean[] {false, true})
                    for (boolean keyboard : new boolean[] {false, true})
                        for (boolean ime : new boolean[] {false, true})
                            for (int opacity : new int[] {0, 34, 100}) {
                                DockSpec.Input in = pong(design, floating, keyboard);
                                in.kind = kind;
                                in.imeVisible = ime;
                                in.look = new DockSpec.Look(8, opacity, 0, 0, false, 26f, null, null);
                                DockSpec spec = DockSpec.of(in);
                                String at = kind + " " + design + " floating=" + floating
                                    + " keyboard=" + keyboard + " ime=" + ime;
                                assertEquals(at, DockStyle.effectiveStyle(design), spec.design);
                                if (spec.foot) {
                                    assertEquals(at, H, spec.bottom);
                                    assertTrue(at, spec.seamsBottom());
                                    assertEquals(at, 0f, spec.bottomCornerPx, 0f);
                                } else {
                                    assertTrue(at, spec.bottom < H);
                                }
                                if (keyboard || ime) assertFalse(at, spec.foot);
                            }
    }
}
