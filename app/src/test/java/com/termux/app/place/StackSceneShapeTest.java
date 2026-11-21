package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.chrome.StackMaterial;
import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * A crossing between ends whose stack structure differs (the slab's Corners and Margin, the
 * dock's and the keyboard's size) shows the slab's height, corner radius and inset and the
 * keyboard's height as a pure function of progress: exactly the leaving rest's at 0 and the
 * arriving rest's at 1, no step anywhere between (the biggest per-step move is the curve's
 * steepest slope times the step), so the landing's re-dress finds the numbers already drawn.
 * Docked and Floating apart, the keyboard up on both ends, up to down and down to up, on every
 * layout fixture (pong's among them).
 */
@RunWith(Parameterized.class)
public class StackSceneShapeTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private static final float STEP = 0.05f;
    /** SeamFade's smoothstep over [0.2, 0.8] peaks at 2.5 per unit; a little over, plus 1 px. */
    private static final float SLOPE = 2.6f;

    private final LayoutFixtures.Fixture f;

    public StackSceneShapeTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    private static RegionLook look() {
        RegionLook look = new RegionLook();
        StackMaterial.glass(30f, 0.5f, 0xFF203040, 0.1f, 0xFF222222, look);
        return look;
    }

    /** What differs between the two ends beyond the page: scales of the sizes, and the outline. */
    private static final class Apart {
        final float dockScale, keyboardScale;
        final int cornerPx, insetPx;

        Apart(float dockScale, float keyboardScale, int cornerPx, int insetPx) {
            this.dockScale = dockScale;
            this.keyboardScale = keyboardScale;
            this.cornerPx = cornerPx;
            this.insetPx = insetPx;
        }
    }

    private static final Apart ALIKE = new Apart(1f, 1f, 0, 0);
    private static final Apart ALL = new Apart(1.4f, 1.25f, 40, 24);
    private static final Apart CORNERS = new Apart(1f, 1f, 40, 0);
    private static final Apart MARGIN = new Apart(1f, 1f, 0, 24);
    private static final Apart KEYBOARD = new Apart(1f, 1.25f, 0, 0);
    private static final Apart DOCK = new Apart(1.4f, 1f, 0, 0);

    /** {@code page}'s end, its numbers read from the settle as the rest reads them. */
    private StackEnd end(@NonNull PageRef page, boolean keyboardUp, boolean floating,
                         @NonNull Apart apart, boolean worn) {
        StackLayout.Result r = f.settle(page, keyboardUp, floating);
        int kb = keyboardUp ? f.keyboardLaidOutPx() : 0;
        int rows = PlaceChromeTravel.dockRowsLaidOutPx(r.stackHeightPx, kb, r.statusBandPx);
        float dockScale = worn ? apart.dockScale : 1f;
        float keyboardScale = worn ? apart.keyboardScale : 1f;
        StackEnd end = new StackEnd(page);
        StackGeometry g = end.rest;
        g.layerPresent[StackGeometry.HOST] = true;
        // Docked with the keyboard up, the glass host spans the whole stack (unified).
        boolean spans = !floating && keyboardUp;
        g.layerHeightPx[StackGeometry.HOST] = Math.round(rows * dockScale)
            + (spans ? Math.round(kb * keyboardScale) : 0);
        g.keyboardPresent = kb > 0;
        g.keyboardHeightPx = Math.round(kb * keyboardScale);
        g.keyboardInColumn = kb > 0;
        end.floating = floating;
        // Floating is already a card at rest and Docked is square at rest: each its own number.
        end.cornerPx = (floating ? Math.round(26f * f.density) : 0) + (worn ? apart.cornerPx : 0);
        end.insetPx = (floating ? Math.round(8f * f.density) : 0) + (worn ? apart.insetPx : 0);
        end.looks[StackEnd.ROWS] = look();
        if (keyboardUp && kb > 0) end.looks[StackEnd.KEYBOARD] = look();
        end.looks[StackEnd.FOOT] = look();
        return end;
    }

    private interface Pick {
        float of(StackScene.Frame frame);
    }

    private void cross(StackEnd from, StackEnd to) {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.Frame rest = new StackScene.Frame();
        StackScene.Frame restTo = new StackScene.Frame();
        StackScene.rest(from, 0f, motion, rest);
        StackScene.rest(to, 0f, motion, restTo);
        Pick[] picks = {fr -> fr.slabHeightPx, fr -> fr.slabCornerPx, fr -> fr.slabInsetPx,
            fr -> fr.keyboardShownPx};
        String[] names = {"slab height", "corner radius", "inset", "keyboard height"};
        for (int k = 0; k < picks.length; k++) {
            float a = picks[k].of(rest);
            float b = picks[k].of(restTo);
            float bound = Math.abs(b - a) * STEP * SLOPE + 1f;
            float last = a;
            for (int i = 0; i <= 20; i++) {
                float p = i * STEP;
                motion.rest();
                StackScene.at(from, to, p, motion, from.rest, frame);
                float now = picks[k].of(frame);
                assertTrue(names[k] + " jumped " + Math.abs(now - last) + " (> " + bound
                    + ") at p=" + p + " on " + f.name, Math.abs(now - last) <= bound);
                last = now;
                if (i == 0) assertEquals(names[k] + " at p=0 on " + f.name, a, now, 0f);
                if (i == 20) assertEquals(names[k] + " at p=1 on " + f.name, b, now, 0f);
            }
        }
    }

    private void crossAll(Apart apart) {
        for (boolean fromUp : new boolean[]{false, true}) {
            for (boolean toUp : new boolean[]{false, true}) {
                for (boolean fromFloating : new boolean[]{false, true}) {
                    for (boolean toFloating : new boolean[]{false, true}) {
                        cross(end(PageRef.HOME, fromUp, fromFloating, apart, false),
                            end(PageRef.TERMINAL, toUp, toFloating, apart, true));
                    }
                }
            }
        }
    }

    @Test public void everythingApartBlendsWithoutAStep() { crossAll(ALL); }

    @Test public void onlyCornersApartBlendsWithoutAStep() { crossAll(CORNERS); }

    @Test public void onlyMarginApartBlendsWithoutAStep() { crossAll(MARGIN); }

    @Test public void onlyTheKeyboardsSizeApartBlendsWithoutAStep() { crossAll(KEYBOARD); }

    @Test public void onlyTheDocksSizeApartBlendsWithoutAStep() { crossAll(DOCK); }

    @Test public void endsAlikeInStructureHoldEveryNumberStill() {
        StackEnd from = end(PageRef.HOME, true, false, ALIKE, false);
        StackEnd to = end(PageRef.HOME, true, false, ALIKE, false);
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        for (int i = 0; i <= 20; i++) {
            motion.rest();
            StackScene.at(from, to, i * STEP, motion, from.rest, frame);
            assertEquals(from.slabHeightPx(), frame.slabHeightPx, 0f);
            assertEquals(from.cornerPx, frame.slabCornerPx, 0f);
            assertEquals(from.keyboardShownPx(), frame.keyboardShownPx, 0f);
        }
    }

    /** The landing's re-dress finds the stack already in the arriving dress: same numbers. */
    @Test public void theLandingFrameIsTheArrivingRestExactly() {
        StackEnd from = end(PageRef.HOME, true, true, ALL, false);
        StackEnd to = end(PageRef.TERMINAL, false, false, ALL, true);
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame landed = new StackScene.Frame();
        StackScene.Frame rest = new StackScene.Frame();
        StackScene.at(from, to, 1f, motion, from.rest, landed);
        StackScene.rest(to, 0f, motion, rest);
        assertEquals(rest.slabHeightPx, landed.slabHeightPx, 0f);
        assertEquals(rest.slabCornerPx, landed.slabCornerPx, 0f);
        assertEquals(rest.slabInsetPx, landed.slabInsetPx, 0f);
        assertEquals(rest.keyboardShownPx, landed.keyboardShownPx, 0f);
    }
}
