package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.chrome.StackMaterial;
import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.wall.PageRef;

import org.junit.Test;

/**
 * The stack's material across a crossing (design 5.1): a region one end has wears that end's look
 * at every p, a region both have blends continuously by {@code w(p)}, p=0 and p=1 are the two
 * ends' rests, and a drag that reverses reads the same frame back.
 */
public class StackSceneMaterialTest {

    private static final int STEPS = 128;

    /** pong's looks: Home's keyboard 60 / 5, the Workspace's 10 / 2. */
    private static StackEnd end(@NonNull PageRef page, boolean keyboardUp) {
        StackEnd end = new StackEnd(page);
        boolean home = page.isHome();
        end.rest.set(geometry());
        end.rest.keyboardPresent = keyboardUp;
        end.looks[StackEnd.ROWS] = glass(home ? 5f : 2f, home ? 0.6f : 0.1f);
        if (keyboardUp) end.looks[StackEnd.KEYBOARD] = glass(home ? 5f : 2f, home ? 0.6f : 0.1f);
        end.strip = keyboardUp ? StackEnd.StripRule.KEYBOARD_FOOT : StackEnd.StripRule.DOCK_FOOT;
        return end;
    }

    private static RegionLook glass(float blur, float opacity) {
        RegionLook look = new RegionLook();
        StackMaterial.glass(blur, opacity, 0xFF203040, 0.05f, 0xFF222222, look);
        return look;
    }

    private static StackGeometry geometry() {
        StackGeometry g = new StackGeometry();
        g.displayWidthPx = 1080;
        g.displayHeightPx = 2400;
        g.containerWidthPx = 1080;
        g.containerHeightPx = 900;
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            g.layerPresent[i] = true;
            g.layerWidthPx[i] = 1080;
            g.layerHeightPx[i] = 300;
        }
        g.keyboardPresent = true;
        g.keyboardInColumn = true;
        g.keyboardTopPx = 300;
        g.keyboardWidthPx = 1080;
        g.keyboardHeightPx = 600;
        return g;
    }

    /** Mid-crossing the keyboard is laid out (pre-rolled) whichever end has it. */
    private static StackScene.Frame at(StackEnd from, StackEnd to, float p) {
        return at(from, to, p, geometry());
    }

    private static StackScene.Frame at(StackEnd from, StackEnd to, float p, StackGeometry live) {
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.at(from, to, p, motion, live, frame);
        return frame;
    }

    @Test public void aKeyboardOnlyOneEndHasWearsThatEndsLookAtEveryP() {
        // Home -> Workspace with the keyboard down at Home: the rising keyboard is the
        // Workspace's from the first frame, blur included; and the reverse keeps it all the way.
        StackEnd home = end(PageRef.HOME, false);
        StackEnd workspace = end(PageRef.TERMINAL, true);
        for (int i = 0; i <= STEPS; i++) {
            float p = i / (float) STEPS;
            StackScene.Frame rising = at(home, workspace, p);
            assertTrue("p=" + p, rising.look[StackEnd.KEYBOARD].sameAs(workspace.looks[StackEnd.KEYBOARD]));
            assertEquals(2f, rising.look[StackEnd.KEYBOARD].blurDp, 0f);
            assertEquals(0f, rising.look[StackEnd.KEYBOARD].blurMix, 0f);
            assertEquals(1f, rising.share[StackEnd.KEYBOARD], 0f);
            StackScene.Frame leaving = at(workspace, home, p);
            assertTrue("p=" + p, leaving.look[StackEnd.KEYBOARD].sameAs(workspace.looks[StackEnd.KEYBOARD]));
            assertEquals(0f, leaving.share[StackEnd.KEYBOARD], 0f);
        }
    }

    @Test public void aRegionBothEndsHaveBlendsContinuously() {
        StackEnd home = end(PageRef.HOME, true);
        StackEnd workspace = end(PageRef.TERMINAL, true);
        StackScene.Frame previous = at(home, workspace, 0f);
        float maxStep = 0f;
        for (int i = 1; i <= STEPS; i++) {
            float p = i / (float) STEPS;
            StackScene.Frame frame = at(home, workspace, p);
            for (int region : new int[] {StackEnd.ROWS, StackEnd.KEYBOARD}) {
                float step = Math.abs(frame.share[region] - previous.share[region]);
                maxStep = Math.max(maxStep, step);
                assertTrue("share never goes back on a forward drag, p=" + p,
                    frame.share[region] >= previous.share[region]);
                assertEquals(frame.look[region].tintAlpha,
                    0.6f + (0.1f - 0.6f) * frame.share[region], 1e-4f);
            }
            previous = frame;
        }
        // The smoothstep over [0.2, 0.8] moves at most 1.5 * (1/0.6) per unit of p.
        assertTrue("largest step " + maxStep, maxStep <= 2.5f / STEPS + 1e-4f);
    }

    @Test public void zeroAndOneAreTheEndsRests() {
        StackScene.Motion scratch = new StackScene.Motion();
        for (boolean fromUp : new boolean[] {false, true}) for (boolean toUp : new boolean[] {false, true}) {
            StackEnd from = end(PageRef.HOME, fromUp);
            StackEnd to = end(PageRef.TERMINAL, toUp);
            StackScene.Frame rest = new StackScene.Frame();
            StackScene.rest(from, 0f, scratch, rest);
            assertTrue(at(from, to, 0f, from.rest).sameAs(rest));
            StackScene.rest(to, 0f, scratch, rest);
            assertTrue(at(from, to, 1f, to.rest).sameAs(rest));
        }
    }

    @Test public void aReversalReturnsToTheSameFrame() {
        StackEnd home = end(PageRef.HOME, true);
        StackEnd workspace = end(PageRef.TERMINAL, false);
        float[] there = {0.1f, 0.3f, 0.45f, 0.62f, 0.75f};
        StackScene.Frame[] forward = new StackScene.Frame[there.length];
        for (int i = 0; i < there.length; i++) forward[i] = at(home, workspace, there[i]);
        // Up to 0.9 and back down: every frame on the way down is the one on the way up.
        at(home, workspace, 0.9f);
        for (int i = there.length - 1; i >= 0; i--)
            assertTrue("p=" + there[i], at(home, workspace, there[i]).sameAs(forward[i]));
    }
}