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
                                     float sheetCutPx, float heldPx, boolean holdsAz,
                                     StackGeometry g, StackScene.Frame out) {
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            int v = StackScene.V_LAYER0 + i;
            boolean sheet = i == StackGeometry.HOST;
            boolean az = i == StackGeometry.AZ_UNDERLAY || i == StackGeometry.AZ_OVERLAY
                || i == StackGeometry.AZ_LABEL;
            boolean held = holdsAz && az;
            out.ty[v] = held ? heldPx : sheet ? sheetPx : rowsPx;
            int hidden = held ? 0 : Math.round(sheet ? sheetCutPx : rowsCutPx);
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
        int folds;
        @Override public void translate(float dock, float keyboard) {
            dockPx = dock;
            keyboardPx = keyboard;
        }
        @Override public void sink(float px) { sinkPx = px; }
        @Override public void fold() { folds++; }
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
                seam.folds = false;
                float dockReveal = Math.abs(1f - 2f * p);
                float translation = crossing.stack(seam, dockReveal, laid.keyboardPx, laid.dockPx,
                    laid.rowsPx);
                StackScene.travel(false, seam.rowsSink(laid.keyboardPx), seam.keyboardReveal,
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
        for (boolean floating : BOOLS) for (boolean kbUp : BOOLS) for (int kind = 0; kind < 4; kind++) {
            StackGeometry g = laid(PageRef.TERMINAL, kbUp, floating).g;
            for (int i = 0; i <= STEPS; i++) {
                float p = i / (float) STEPS;
                motion.rest();
                motion.dockTravelPx = (g.containerHeightPx + 40) * p - (kind == 3 ? 30f : 0f);
                motion.keyboardSharePx = g.keyboardHeightPx * p;
                motion.imeLiftPx = kind == 2 ? 120f : 0f;
                if (kind == 1) motion.sink(g.layerHeightPx[StackGeometry.ROWS] * p);
                boolean holdsAz = kind == 2;
                if (kind >= 2) {
                    StackScene.foldSink(98, 1f - p, 12, 9, 7, holdsAz, kind == 3, motion);
                }
                StackScene.at(null, null, p, motion, g, frame);
                legacyContainer(motion.dockTravelPx, motion.imeLiftPx, g, legacy);
                legacyUnder(motion.dockTravelPx, motion.keyboardSharePx, g, legacy);
                legacyLayers(motion.rowsSinkPx, motion.rowsCutPx, motion.sheetSinkPx,
                    motion.sheetCutPx, motion.heldSinkPx, motion.holdsAz, g, legacy);
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
                    StackScene.travel(false, sink && laid.keyboardPx > 0, kbReveal, 1f - p * 0.5f,
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