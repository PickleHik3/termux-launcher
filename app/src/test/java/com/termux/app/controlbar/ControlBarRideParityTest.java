package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.StackGeometry;
import com.termux.app.place.StackScene;

import org.junit.Test;

/**
 * The bar riding on the keyboard is a two-phase number (#427; motion invariants, "one owner per
 * two-phase number"): the bar is laid out at its rest's answer ({@link
 * ControlBarPlacement#settledBottomPx}) and moved frame by frame from the stack's own translation
 * ({@link StackScene}, the container's {@code ty}). Both read the stack's one number for a down
 * keyboard ({@link StackScene#keyboardTranslationPx}); at each rest the ride is zero, on every frame
 * between the bar stands its gap above the keyboard's drawn top, and the frame the new rest is laid
 * out draws the bar where the last frame of the reveal did.
 */
public class ControlBarRideParityTest {

    private static final int STEPS = 32;
    private static final int GAP = 18;
    /** Keys, gesture navigation, three-button navigation; a keyboard shorter than the navigation. */
    private static final int[][] CASES = {{720, 48}, {720, 126}, {900, 0}, {40, 126}};

    private static StackGeometry terlStack(int keyboardPx) {
        // Under the bar v1's rows are hidden: the stack is the keyboard's column.
        StackGeometry g = new StackGeometry();
        g.displayWidthPx = 1080;
        g.displayHeightPx = 2400;
        g.containerWidthPx = 1080;
        g.containerHeightPx = keyboardPx;
        g.keyboardPresent = true;
        g.keyboardInColumn = true;
        g.keyboardTopPx = 0;
        g.keyboardWidthPx = 1080;
        g.keyboardHeightPx = keyboardPx;
        return g;
    }

    /** The container's translation the stack's writer draws at keyboard reveal {@code r} at rest. */
    private static float restTy(int keyboardPx, boolean up, StackGeometry g) {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.withKeyboardRest(new StackScene.Motion(),
            StackScene.keyboardTranslationPx(up ? 1f : 0f, keyboardPx), motion);
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.at(null, null, 0f, motion, g, frame);
        return frame.ty[StackScene.V_CONTAINER];
    }

    /** The container's translation on a frame of the keyboard's reveal at {@code reveal}. */
    private static float revealTy(int keyboardPx, float reveal, StackGeometry g) {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.travel(false, reveal, 1f, keyboardPx, 0, 0, motion);
        StackScene.Frame frame = new StackScene.Frame();
        StackScene.at(null, null, 0f, motion, g, frame);
        return frame.ty[StackScene.V_CONTAINER];
    }

    @Test
    public void atEachRestTheBarIsWhereItIsLaidOut() {
        for (int[] c : CASES) {
            int kb = c[0];
            int nav = c[1];
            StackGeometry g = terlStack(kb);
            for (boolean up : new boolean[] {false, true}) {
                float rest = StackScene.keyboardTranslationPx(up ? 1f : 0f, kb);
                // The layout's rest share is the number the writer draws at that rest.
                assertEquals(rest, restTy(kb, up, g), 0f);
                int settled = ControlBarPlacement.settledBottomPx(nav, kb, rest, GAP);
                assertEquals(0f, ControlBarPlacement.rideTyPx(settled, nav, kb, restTy(kb, up, g),
                    GAP), 0f);
            }
        }
    }

    @Test
    public void onEveryFrameOfTheRevealTheBarStandsOnTheKeyboard() {
        for (int[] c : CASES) {
            int kb = c[0];
            int nav = c[1];
            StackGeometry g = terlStack(kb);
            int laidOut = ControlBarPlacement.settledBottomPx(nav, kb,
                StackScene.keyboardTranslationPx(0f, kb), GAP);
            float last = -1f;
            for (int i = 0; i <= STEPS; i++) {
                float reveal = StackScene.materialShare(i / (float) STEPS);
                float ty = revealTy(kb, reveal, g);
                float barBottom = laidOut - ControlBarPlacement.rideTyPx(laidOut, nav, kb, ty, GAP);
                float keyboardTop = kb - ty;
                // Its gap above the keys, and never into Android's navigation.
                assertEquals("kb " + kb + " nav " + nav + " reveal " + reveal,
                    Math.max(keyboardTop, nav) + GAP, barBottom, 0.01f);
                assertTrue(barBottom >= last);
                last = barBottom;
            }
        }
    }

    @Test
    public void theNewRestsLayoutDrawsTheBarWhereTheRevealsLastFrameDid() {
        for (int[] c : CASES) {
            int kb = c[0];
            int nav = c[1];
            StackGeometry g = terlStack(kb);
            for (boolean up : new boolean[] {false, true}) {
                int from = ControlBarPlacement.settledBottomPx(nav, kb,
                    StackScene.keyboardTranslationPx(up ? 0f : 1f, kb), GAP);
                int to = ControlBarPlacement.settledBottomPx(nav, kb,
                    StackScene.keyboardTranslationPx(up ? 1f : 0f, kb), GAP);
                float lastTy = revealTy(kb, up ? 1f : 0f, g);
                float lastFrame = from - ControlBarPlacement.rideTyPx(from, nav, kb, lastTy, GAP);
                float landed = to - ControlBarPlacement.rideTyPx(to, nav, kb, restTy(kb, up, g), GAP);
                assertEquals(lastFrame, landed, 0.5f);
            }
        }
    }
}
