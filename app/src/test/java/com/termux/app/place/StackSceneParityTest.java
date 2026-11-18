package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.chrome.StackMaterial;
import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockMorph;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * {@link StackScene} is today's stack, moved: on every layout fixture, Docked and Floating, the
 * keyboard up and down at each end, the travel equals {@link Crossing#stack}'s and every
 * transform equals what {@code TermuxActivity#applyAccessoryStackTranslation},
 * {@code #applyUnderKeyboardTravel} and {@code CrossingViews#applyLayerSink} wrote (transcribed
 * below as the oracle, since they write views). And the scene keeps the model's rules: p=0 and
 * p=1 are the two rests, frames are continuous, the rows region is the rows' cut, a region one
 * end has keeps that end's look, and an end paired with itself gives one constant frame.
 */
@RunWith(Parameterized.class)
public class StackSceneParityTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private static final int STEPS = 64;
    private static final boolean[] BOOLS = {false, true};

    private final LayoutFixtures.Fixture f;

    public StackSceneParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    // ---- Fixtures --------------------------------------------------------------------------

    private static final class Laid {
        final StackGeometry g = new StackGeometry();
        int keyboardPx, dockPx, rowsPx;
    }

    /** {@code page}'s stack as the settle lays it out, in the container's pixels. */
    private Laid laid(@NonNull PageRef page, boolean keyboardUp, boolean floating) {
        StackLayout.Result r = f.settle(page, keyboardUp, floating);
        Laid laid = new Laid();
        StackGeometry g = laid.g;
        int kb = keyboardUp ? f.keyboardLaidOutPx() : 0;
        int rows = PlaceChromeTravel.dockRowsLaidOutPx(r.stackHeightPx, kb, r.statusBandPx);
        laid.keyboardPx = kb;
        laid.rowsPx = rows;
        laid.dockPx = PlaceChromeTravel.dockLaidOutPx(rows, r.bottomMarginPx);
        g.displayWidthPx = f.widthPx;
        g.displayHeightPx = f.heightPx;
        g.containerWidthPx = f.widthPx;
        g.containerHeightPx = r.stackHeightPx;
        g.stackPaddingBottomPx = r.flushPaddingPx;
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            g.layerPresent[i] = true;
            g.layerWidthPx[i] = f.widthPx;
            g.layerTopPx[i] = 0;
            g.layerHeightPx[i] = rows;
        }
        // Docked with the keyboard up, the glass host spans the whole stack (unified).
        g.layerHeightPx[StackGeometry.HOST] = !floating && keyboardUp ? r.stackHeightPx : rows;
        g.layerTopPx[StackGeometry.AZ_UNDERLAY] = rows / 2;
        g.layerHeightPx[StackGeometry.AZ_UNDERLAY] = rows - rows / 2;
        g.keyboardPresent = kb > 0;
        g.keyboardInColumn = kb > 0;
        g.keyboardTopPx = rows;
        g.keyboardWidthPx = f.widthPx;
        g.keyboardHeightPx = kb;
        g.underShown = kb > 0 && r.underKeyboardAirPx > 0;
        g.belowKeyboardInColumnPx = g.underShown ? r.underKeyboardAirPx : 0;
        g.underTopPx = rows + kb;
        g.underBottomPx = rows + kb + g.belowKeyboardInColumnPx;
        g.footTopPx = Math.max(0, r.stackHeightPx - r.flushPaddingPx);
        g.footBottomPx = r.stackHeightPx;
        return laid;
    }

    private static RegionLook glass(float blur, float opacity, int tint) {
        RegionLook look = new RegionLook();
        StackMaterial.glass(blur, opacity, tint, 0.1f, 0xFF222222, look);
        return look;
    }

    /** An end on {@code page}: Home's look or the Terminal's, its keyboard up or down. */
    private StackEnd end(@NonNull PageRef page, boolean keyboardUp, boolean floating) {
        StackEnd end = new StackEnd(page);
        end.rest.set(laid(page, keyboardUp, floating).g);
        end.floating = floating;
        boolean home = page.isHome();
        end.looks[StackEnd.ROWS] = glass(home ? 25f : 40f, home ? 0.6f : 0.35f,
            home ? 0xFF102030 : 0xFF405060);
        if (keyboardUp) end.looks[StackEnd.KEYBOARD] = glass(40f, 0.35f, 0xFF405060);
        if (end.rest.underShown) end.looks[StackEnd.UNDER] = glass(40f, 0.5f, 0xFF405060);
        end.looks[StackEnd.FOOT] = glass(home ? 25f : 40f, 0.5f, 0xFF102030);
        end.strip = floating ? StackEnd.StripRule.BARE : keyboardUp
            ? StackEnd.StripRule.KEYBOARD_FOOT : StackEnd.StripRule.DOCK_FOOT;
        end.inkArgb = home ? 0xFFFFFFFF : 0xFF000000;
        if (home) {
            float[] m = new float[20];
            m[0] = m[6] = m[12] = 0.9f;
            m[18] = 1f;
            end.evenOut[StackEnd.ROWS] = m;
        }
        return end;
    }

    // ---- The oracle: today's writes, transcribed --------------------------------------------

    /** TermuxActivity#applyAccessoryStackTranslation (TA:10361) into {@code out}. */
    private static void legacyContainer(float travelPx, float imeLiftPx, StackGeometry g,
                                        StackScene.Frame out) {
        int v = StackScene.V_CONTAINER;
        out.ty[v] = travelPx - imeLiftPx;
        int travel = Math.round(Math.max(0f, travelPx));
        if (travel <= 0) {
            out.clipped[v] = false;
            return;
        }
        int width = Math.max(1, g.containerWidthPx);
        int height = g.containerHeightPx;
        set(out, v, -width, -Math.max(height, g.displayHeightPx), 2 * width,
            Math.max(0, height - travel));
    }

    /** TermuxActivity#applyUnderKeyboardTravel (TA:10435) into {@code out}. */
    private static void legacyUnder(float travelPx, float keyboardSharePx, StackGeometry g,
                                    StackScene.Frame out) {
        boolean inColumn = g.keyboardPresent && g.keyboardInColumn;
        boolean holds = g.underShown && inColumn;
        float share = holds ? Math.max(0f, keyboardSharePx) : 0f;
        out.ty[StackScene.V_UNDER] = -share;
        int v = StackScene.V_KEYBOARD;
        int hidden = inColumn ? BottomInsets.keyboardTravelHiddenPx(travelPx, share,
            g.belowKeyboardInColumnPx, g.stackPaddingBottomPx) : Math.round(share);
        if (hidden <= 0) {
            out.clipped[v] = false;
            return;
        }
        int width = Math.max(g.keyboardWidthPx, g.displayWidthPx);
        int height = g.keyboardHeightPx;
        set(out, v, -width, -Math.max(height, g.displayHeightPx), 2 * width,
            Math.max(0, height - hidden));
    }

    /** CrossingViews#applyLayerSink (CrossingViews:265) into {@code out}. */
    private static void legacyLayers(float rowsPx, float rowsCutPx, float sheetPx,
                                     float sheetCutPx, StackGeometry g, StackScene.Frame out) {
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            int v = StackScene.V_LAYER0 + i;
            boolean sheet = i == StackGeometry.HOST;
            out.ty[v] = sheet ? sheetPx : rowsPx;
            int hidden = Math.round(sheet ? sheetCutPx : rowsCutPx);
            if (hidden <= 0) {
                out.clipped[v] = false;
                continue;
            }
            int width = Math.max(1, g.layerWidthPx[i]);
            set(out, v, -width, -Math.max(g.layerHeightPx[i], g.displayHeightPx), 2 * width,
                Math.max(0, g.layerHeightPx[i] - hidden));
        }
    }

    private static void set(StackScene.Frame out, int v, int l, int t, int r, int b) {
        out.clipped[v] = true;
        out.clipLeft[v] = l;
        out.clipTop[v] = t;
        out.clipRight[v] = r;
        out.clipBottom[v] = b;
    }

    /** Crossing#stack's stack, recorded. */
    private static final class Moves implements Crossing.Stack, Crossing.Bar, Crossing.Cover,
            Crossing.Keyboard, Crossing.Dock {
        float dockPx, keyboardPx, sinkPx;
        @Override public void translate(float dock, float keyboard) {
            dockPx = dock;
            keyboardPx = keyboard;
        }
        @Override public void sink(float px) { sinkPx = px; }
        @Override public void alpha(float status, float side) {}
        @Override public void rideX(float px) {}
        @Override public void cover(int px) {}
        @Override public void preRoll(@NonNull PaneWallPage target) {}
        @Override public void beginMorph(@NonNull DockLayout leaving, @NonNull DockLayout arriving) {}
        @Override public void dress(@NonNull DockMorph morph) {}
        @Override public void endMorph(@NonNull DockLayout landed) {}
    }

    // ---- Parity with today -----------------------------------------------------------------

    @Test public void theTravelIsCrossingStacks() {
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves, moves);
        SeamCrossing.Answers seam = new SeamCrossing.Answers();
        StackScene.Motion motion = new StackScene.Motion();
        for (boolean floating : BOOLS) for (boolean kbUp : BOOLS) for (boolean sink : BOOLS) {
            Laid laid = laid(PageRef.TERMINAL, kbUp, floating);
            for (int i = 0; i <= STEPS; i++) {
                float p = i / (float) STEPS;
                seam.keyboardReveal = kbUp ? 1f - p : p;
                seam.rowsSink = sink;
                float dockReveal = Math.abs(1f - 2f * p);
                float translation = crossing.stack(seam, dockReveal, laid.keyboardPx, laid.dockPx,
                    laid.rowsPx);
                StackScene.travel(seam.rowsSink(laid.keyboardPx), seam.keyboardReveal,
                    dockReveal, laid.keyboardPx, laid.dockPx, laid.rowsPx, motion);
                String what = what(floating, kbUp) + " sink=" + sink + " p=" + p;
                assertEquals(what, translation, motion.dockTravelPx, 0f);
                assertEquals(what, moves.dockPx, motion.dockTravelPx, 0f);
                assertEquals(what, moves.keyboardPx, motion.keyboardSharePx, 0f);
                assertEquals(what, moves.sinkPx, motion.rowsSinkPx, 0f);
                assertEquals(what, moves.sinkPx, motion.sheetCutPx, 0f);
            }
        }
    }

    @Test public void everyTransformIsTodaysWrite() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.Frame legacy = new StackScene.Frame();
        for (boolean floating : BOOLS) for (boolean kbUp : BOOLS) for (int kind = 0; kind < 3; kind++) {
            StackGeometry g = laid(PageRef.TERMINAL, kbUp, floating).g;
            for (int i = 0; i <= STEPS; i++) {
                float p = i / (float) STEPS;
                motion.rest();
                motion.dockTravelPx = (g.containerHeightPx + 40) * p;
                motion.keyboardSharePx = g.keyboardHeightPx * p;
                motion.imeLiftPx = kind == 2 ? 120f : 0f;
                if (kind == 1) motion.sink(g.layerHeightPx[StackGeometry.ROWS] * p);
                StackScene.at(null, null, p, motion, g, frame);
                legacyContainer(motion.dockTravelPx, motion.imeLiftPx, g, legacy);
                legacyUnder(motion.dockTravelPx, motion.keyboardSharePx, g, legacy);
                legacyLayers(motion.rowsSinkPx, motion.rowsCutPx, motion.sheetSinkPx,
                    motion.sheetCutPx, g, legacy);
                assertTrue(what(floating, kbUp) + " kind=" + kind + " p=" + p,
                    frame.sameTransforms(legacy));
            }
        }
    }

    // ---- The model's rules -----------------------------------------------------------------

    @Test public void zeroAndOneAreTheTwoRests() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame at = new StackScene.Frame();
        StackScene.Frame rest = new StackScene.Frame();
        for (boolean floating : BOOLS) for (boolean fromUp : BOOLS) for (boolean toUp : BOOLS) {
            for (float lift : new float[] {0f, 90f}) {
                StackEnd from = end(PageRef.HOME, fromUp, floating);
                StackEnd to = end(PageRef.TERMINAL, toUp, floating);
                String what = what(floating, fromUp) + " to up=" + toUp + " lift=" + lift;
                motion.rest();
                motion.imeLiftPx = lift;
                StackScene.at(from, to, 0f, motion, from.rest, at);
                StackScene.rest(from, lift, new StackScene.Motion(), rest);
                assertTrue(what + " p=0", at.sameAs(rest));
                motion.rest();
                motion.imeLiftPx = lift;
                StackScene.at(from, to, 1f, motion, to.rest, at);
                StackScene.rest(to, lift, new StackScene.Motion(), rest);
                assertTrue(what + " p=1", at.sameAs(rest));
            }
        }
    }

    @Test public void framesAreContinuousAndTheRowsRegionIsTheRowsCut() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.Frame previous = new StackScene.Frame();
        for (boolean floating : BOOLS) for (boolean fromUp : BOOLS) for (boolean toUp : BOOLS) {
            for (boolean sink : BOOLS) {
                StackEnd from = end(PageRef.HOME, fromUp, floating);
                StackEnd to = end(PageRef.TERMINAL, toUp, floating);
                // The keyboard pre-rolled: the stack laid out with it for the whole crossing.
                Laid laid = laid(PageRef.TERMINAL, fromUp || toUp, floating);
                StackGeometry g = laid.g;
                float step = (laid.keyboardPx + laid.dockPx) / (float) STEPS + 2f;
                for (int i = 0; i <= STEPS; i++) {
                    float p = i / (float) STEPS;
                    float kbReveal = (fromUp ? 1f - p : 0f) + (toUp ? p : 0f);
                    StackScene.travel(sink && laid.keyboardPx > 0, kbReveal, 1f - p * 0.5f,
                        laid.keyboardPx, laid.dockPx, laid.rowsPx, motion);
                    StackScene.at(from, to, p, motion, g, frame);
                    String what = what(floating, fromUp) + " to up=" + toUp + " sink=" + sink
                        + " p=" + p;
                    assertRowsRegionIsTheRowsCut(what, g, frame);
                    if (i > 0) assertContinuous(what, step, previous, frame);
                    copy(frame, previous);
                }
            }
        }
    }

    @Test public void aRegionOneEndHasWearsThatEndsLookAllTheWay() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        for (boolean floating : BOOLS) {
            for (boolean toUp : BOOLS) {
                // The keyboard is one end's only: down at Home, up on the Terminal, or the reverse.
                StackEnd from = end(PageRef.HOME, !toUp, floating);
                StackEnd to = end(PageRef.TERMINAL, toUp, floating);
                RegionLook only = toUp ? to.looks[StackEnd.KEYBOARD] : from.looks[StackEnd.KEYBOARD];
                Laid laid = laid(PageRef.TERMINAL, true, floating);
                for (int i = 0; i <= STEPS; i++) {
                    float p = i / (float) STEPS;
                    motion.rest();
                    StackScene.at(from, to, p, motion, laid.g, frame);
                    String what = what(floating, toUp) + " p=" + p;
                    assertTrue(what, frame.present[StackEnd.KEYBOARD]);
                    assertTrue(what, frame.look[StackEnd.KEYBOARD].sameAs(only));
                    // The rows both ends have blend.
                    RegionLook blend = new RegionLook();
                    StackMaterial.lerp(from.looks[StackEnd.ROWS], to.looks[StackEnd.ROWS],
                        StackScene.materialShare(p), blend);
                    assertTrue(what, frame.look[StackEnd.ROWS].sameAs(blend));
                    assertTrue(what, frame.present[StackEnd.FOOT]);
                }
            }
        }
    }

    @Test public void anEndPairedWithItselfGivesOneConstantFrame() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.Frame rest = new StackScene.Frame();
        for (boolean floating : BOOLS) for (boolean up : BOOLS) {
            StackEnd end = end(PageRef.HOME, up, floating);
            StackScene.rest(end, 0f, new StackScene.Motion(), rest);
            for (int i = 0; i <= STEPS; i++) {
                motion.rest();
                StackScene.at(end, end, i / (float) STEPS, motion, end.rest, frame);
                assertTrue(what(floating, up) + " p=" + i, frame.sameAs(rest));
            }
        }
    }

    @Test public void theTopologyChangesOnlyWhereNothingShowsOrAtLanding() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        StackEnd docked = end(PageRef.HOME, false, false);
        StackEnd floating = end(PageRef.TERMINAL, false, true);
        Laid laid = laid(PageRef.HOME, false, false);
        motion.rest();
        StackScene.at(docked, floating, 0.5f, motion, laid.g, frame);
        assertEquals(StackEnd.TOPOLOGY_DOCKED, frame.topology);
        // The whole stack slid out of its cut: nothing shows, the topology may change.
        motion.dockTravelPx = laid.g.containerHeightPx + 10;
        StackScene.at(docked, floating, 0.5f, motion, laid.g, frame);
        assertEquals(StackEnd.TOPOLOGY_FLOATING, frame.topology);
        motion.rest();
        StackScene.at(docked, floating, 1f, motion, floating.rest, frame);
        assertEquals(StackEnd.TOPOLOGY_FLOATING, frame.topology);
    }

    // ---- Rows travel with their side (ADR 0030 section 4) -----------------------------------

    private static int side(@NonNull PageRef page) {
        return com.termux.app.page.Side.of(page.isHome()).ordinal();
    }

    @Test public void rowsSlideWithTheirSideAndRestAtEachEnd() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.Frame previous = new StackScene.Frame();
        StackScene.Frame rest = new StackScene.Frame();
        int home = com.termux.app.page.Side.HOME.ordinal();
        int work = com.termux.app.page.Side.WORKSPACE.ordinal();
        for (boolean floating : BOOLS) for (boolean fromUp : BOOLS) for (boolean toUp : BOOLS) {
            for (boolean fromHome : BOOLS) {
                PageRef a = fromHome ? PageRef.HOME : PageRef.TERMINAL;
                PageRef b = fromHome ? PageRef.TERMINAL : PageRef.HOME;
                StackEnd from = end(a, fromUp, floating);
                StackEnd to = end(b, toUp, floating);
                Laid laid = laid(PageRef.TERMINAL, fromUp || toUp, floating);
                float w = laid.g.containerWidthPx;
                String what = what(floating, fromUp) + " to up=" + toUp + " from home=" + fromHome;
                // Each rest: its own side's rows on screen, the other side's a wall away, Home's
                // to the left of the seam and the Workspace's to its right.
                StackScene.rest(from, 0f, new StackScene.Motion(), rest);
                assertEquals(what + " rest from", 0f, rest.sideTx[side(a)], 0f);
                assertEquals(what + " rest from, other side", fromHome ? w : -w,
                    rest.sideTx[side(b)], 0f);
                for (int i = 0; i <= STEPS; i++) {
                    float p = i / (float) STEPS;
                    motion.rest();
                    StackScene.at(from, to, p, motion, i == 0 ? from.rest : i == STEPS ? to.rest
                        : laid.g, frame);
                    // The two sides stand one wall apart and move together.
                    assertEquals(what + " p=" + p, w, frame.sideTx[work] - frame.sideTx[home],
                        0.01f);
                    float leaving = fromHome ? -p * w : p * w;
                    assertEquals(what + " p=" + p, leaving, frame.sideTx[side(a)], 0.01f);
                    if (i > 0) {
                        assertTrue(what + " continuous p=" + p,
                            Math.abs(frame.sideTx[home] - previous.sideTx[home]) <= w / STEPS + 1f);
                    }
                    System.arraycopy(frame.sideTx, 0, previous.sideTx, 0, frame.sideTx.length);
                }
                StackScene.rest(to, 0f, new StackScene.Motion(), rest);
                assertEquals(what + " p=1", rest.sideTx[home], frame.sideTx[home], 0f);
                assertEquals(what + " p=1", rest.sideTx[work], frame.sideTx[work], 0f);
            }
        }
    }

    @Test public void withinASideNoRowMoves() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.Frame rest = new StackScene.Frame();
        PageRef home2 = PageRef.of(com.termux.app.wall.PaneWallPage.WIDGETS, 2);
        PageRef display = PageRef.of(com.termux.app.wall.PaneWallPage.DISPLAY, 7);
        PageRef[][] pairs = {{PageRef.HOME, home2}, {PageRef.TERMINAL, display}};
        for (PageRef[] pair : pairs) for (boolean floating : BOOLS) for (boolean up : BOOLS) {
            StackEnd from = end(pair[0], up, floating);
            StackEnd to = end(pair[1], up, floating);
            StackScene.rest(from, 0f, new StackScene.Motion(), rest);
            for (int i = 0; i <= STEPS; i++) {
                float p = i / (float) STEPS;
                motion.rest();
                StackScene.at(from, to, p, motion, from.rest, frame);
                String what = pair[0] + " to " + pair[1] + " " + what(floating, up) + " p=" + p;
                for (int s = 0; s < com.termux.app.page.Side.COUNT; s++) {
                    assertEquals(what + " side " + s, rest.sideTx[s], frame.sideTx[s], 0f);
                    assertEquals(what + " side " + s, rest.sideTy[s], frame.sideTy[s], 0f);
                }
            }
        }
    }

    @Test public void rowsRideTheKeyboardsTop() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        for (boolean floating : BOOLS) for (boolean fromUp : BOOLS) for (boolean toUp : BOOLS) {
            StackEnd from = end(PageRef.HOME, fromUp, floating);
            StackEnd to = end(PageRef.TERMINAL, toUp, floating);
            // The keyboard pre-rolled: the stack laid out with it for the whole crossing.
            Laid laid = laid(PageRef.TERMINAL, fromUp || toUp, floating);
            StackGeometry g = laid.g;
            int rowsBottom = g.layerTopPx[StackGeometry.ROWS] + g.layerHeightPx[StackGeometry.ROWS];
            for (int i = 0; i <= STEPS; i++) {
                float p = i / (float) STEPS;
                float kbReveal = (fromUp ? 1f - p : 0f) + (toUp ? p : 0f);
                StackScene.travel(false, kbReveal, 1f, laid.keyboardPx, laid.dockPx,
                    laid.rowsPx, motion);
                StackScene.at(from, to, p, motion, g, frame);
                String what = what(floating, fromUp) + " to up=" + toUp + " p=" + p;
                for (int s = 0; s < com.termux.app.page.Side.COUNT; s++) {
                    float bottom = rowsBottom + frame.sideTy[s];
                    if (g.keyboardPresent && g.keyboardInColumn) {
                        // The rows stand on the keyboard's top as it is drawn: never over air.
                        assertEquals(what + " side " + s, frame.regionTop[StackEnd.KEYBOARD],
                            bottom, 0f);
                    } else {
                        assertEquals(what + " side " + s, rowsBottom, bottom, 0f);
                    }
                }
            }
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private static void assertRowsRegionIsTheRowsCut(String what, StackGeometry g,
                                                     StackScene.Frame frame) {
        int v = StackScene.V_ROWS;
        int top = g.layerTopPx[StackGeometry.ROWS] + Math.round(frame.ty[v]);
        int h = g.layerHeightPx[StackGeometry.ROWS];
        int bottom = top + (frame.clipped[v] ? Math.min(h, frame.clipBottom[v]) : h);
        if (frame.clipped[StackScene.V_CONTAINER])
            bottom = Math.min(bottom, frame.clipBottom[StackScene.V_CONTAINER]);
        assertEquals(what + ": rows region top", top, frame.regionTop[StackEnd.ROWS]);
        assertEquals(what + ": rows region bottom", Math.max(top, bottom),
            frame.regionBottom[StackEnd.ROWS]);
    }

    private static void assertContinuous(String what, float stepPx, StackScene.Frame a,
                                         StackScene.Frame b) {
        for (int v = 0; v < StackScene.V_COUNT; v++) {
            assertTrue(what + " ty v=" + v, Math.abs(a.ty[v] - b.ty[v]) <= stepPx);
            if (a.clipped[v] && b.clipped[v]) {
                assertTrue(what + " clip v=" + v,
                    Math.abs(a.clipBottom[v] - b.clipBottom[v]) <= stepPx);
            }
        }
        // The material follows w(p) (SeamFade.share), a smoothstep over [0.2, 0.8] whose steepest
        // slope is 1.5 / 0.6 = 2.5 per unit of p.
        float lookStep = 2.5f / STEPS + 1f / 255f;
        for (int r = 0; r < StackEnd.REGION_COUNT; r++) {
            assertTrue(what + " tint r=" + r,
                Math.abs(a.look[r].tintAlpha - b.look[r].tintAlpha) <= lookStep);
            assertTrue(what + " solid r=" + r,
                Math.abs(a.look[r].solidness() - b.look[r].solidness()) <= lookStep);
        }
        assertTrue(what + " strip", Math.abs(a.stripAlpha - b.stripAlpha) <= lookStep);
    }

    private static void copy(StackScene.Frame from, StackScene.Frame to) {
        for (int v = 0; v < StackScene.V_COUNT; v++) {
            to.ty[v] = from.ty[v];
            to.clipped[v] = from.clipped[v];
            to.clipBottom[v] = from.clipBottom[v];
        }
        for (int r = 0; r < StackEnd.REGION_COUNT; r++) to.look[r].set(from.look[r]);
        to.stripAlpha = from.stripAlpha;
    }

    private String what(boolean floating, boolean keyboardUp) {
        return f + (floating ? ", Floating" : ", Docked")
            + (keyboardUp ? ", keyboard up" : ", keyboard down");
    }
}