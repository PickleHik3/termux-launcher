package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockGeometry;
import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * D1-A step 1: the in-app keyboard stays laid out while it is down, and "down" is one number the
 * rest and the crossing both read ({@link StackScene#keyboardTranslationPx}). On every layout
 * fixture, Docked and Floating, the keyboard up and down, one pane and many: the crossing's value
 * at progress 0 and 1 is each end's rest value, and the terminal's room with the keyboard laid out
 * is today's room, keyboard down and up.
 */
@RunWith(Parameterized.class)
public class KeyboardLaidOutParityTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private static final int STEPS = 32;
    private static final boolean[] BOOLS = {false, true};
    private static final PageRef[] PAGES = {PageRef.TERMINAL, PageRef.HOME};

    private final LayoutFixtures.Fixture f;

    public KeyboardLaidOutParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    /** The stack laid out once at the union of both rests: the keyboard-up geometry. */
    private StackGeometry union(@NonNull PageRef page, boolean floating) {
        StackLayout.Result r = f.settle(page, true, floating);
        StackGeometry g = new StackGeometry();
        int kb = f.keyboardLaidOutPx();
        int rows = PlaceChromeTravel.dockRowsLaidOutPx(r.stackHeightPx, kb, r.statusBandPx);
        g.displayWidthPx = f.widthPx;
        g.displayHeightPx = f.heightPx;
        g.containerWidthPx = f.widthPx;
        g.containerHeightPx = r.stackHeightPx;
        g.stackPaddingBottomPx = r.flushPaddingPx;
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            g.layerPresent[i] = true;
            g.layerWidthPx[i] = f.widthPx;
            g.layerHeightPx[i] = rows;
        }
        g.keyboardPresent = kb > 0;
        g.keyboardInColumn = kb > 0;
        g.keyboardTopPx = rows;
        g.keyboardWidthPx = f.widthPx;
        g.keyboardHeightPx = kb;
        g.underShown = kb > 0 && r.underKeyboardAirPx > 0;
        g.belowKeyboardInColumnPx = g.underShown ? r.underKeyboardAirPx : 0;
        g.underTopPx = rows + kb;
        g.underBottomPx = rows + kb + g.belowKeyboardInColumnPx;
        return g;
    }

    /** The scene at rest with the keyboard up or down: the crossing's motion plus the rest share. */
    private static void rest(boolean keyboardUp, int laidOutPx, StackGeometry g,
                             StackScene.Frame out) {
        StackScene.Motion none = new StackScene.Motion();
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.withKeyboardRest(none,
            StackScene.keyboardTranslationPx(keyboardUp ? 1f : 0f, laidOutPx), motion);
        StackScene.at(null, null, 0f, motion, g, out);
    }

    @Test public void theCrossingsEndsAreEachEndsRest() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame at = new StackScene.Frame();
        StackScene.Frame rest = new StackScene.Frame();
        for (boolean floating : BOOLS) for (boolean fromUp : BOOLS) for (boolean toUp : BOOLS) {
            for (PageRef page : PAGES) {
                StackGeometry g = union(page, floating);
                int kb = g.keyboardHeightPx;
                String what = f + (floating ? ", Floating" : ", Docked") + ", " + page
                    + ", up " + fromUp + " to " + toUp;
                // p = 0: the seam's keyboard share at the near end's reveal; p = 1: the far end's.
                for (int end = 0; end <= 1; end++) {
                    boolean up = end == 0 ? fromUp : toUp;
                    float reveal = up ? 1f : 0f;
                    StackScene.travel(false, false, reveal, 1f, kb, 200, 120, motion);
                    // The crossing's share is the number the rest reads.
                    assertEquals(what + " end " + end, StackScene.keyboardTranslationPx(reveal, kb),
                        motion.keyboardSharePx, 0f);
                    StackScene.at(null, null, end, motion, g, at);
                    rest(up, kb, g, rest);
                    assertTrue(what + " end " + end, at.sameTransforms(rest));
                }
            }
        }
    }

    @Test public void aKeyboardGoingDownOnTheSeamsCurveRunsFromUpToItsRest() {
        StackScene.Motion motion = new StackScene.Motion();
        for (PageRef page : PAGES) {
            int kb = union(page, false).keyboardHeightPx;
            float last = 0f;
            for (int i = 0; i <= STEPS; i++) {
                float p = StackScene.materialShare(i / (float) STEPS);
                StackScene.travel(false, false, 1f - p, 1f, kb, 200, 120, motion);
                assertTrue(motion.keyboardSharePx >= last);
                assertTrue(motion.keyboardSharePx <= kb);
                last = motion.keyboardSharePx;
            }
            assertEquals(StackScene.keyboardTranslationPx(0f, kb), last, 0f);
        }
    }

    /**
     * The container's height at a crossing's last frame is its height at the landing: the flush
     * padding a pass stands the stack with is the arriving place's rest value while a crossing is
     * live, never one read off the terminal's live (pre-sized, displaced) geometry, which differed
     * from the rest's by a few px on the emulator (Home 2 to Terminal, keyboard down).
     */
    @Test public void theFlushPaddingAtACrossingsLastFrameIsTheRestsAtTheLanding() {
        final int restFlush = 0;
        final int liveMidCrossing = 5;
        StackLayout.FlushRule live = (content, margin) -> liveMidCrossing;
        for (boolean floating : BOOLS) {
            StackLayout.Input crossing = f.pass(PageRef.TERMINAL, false, floating);
            crossing.flush = (content, margin) ->
                StackLayout.flushPx(true, restFlush, live, content, margin);
            StackLayout.Input landing = f.pass(PageRef.TERMINAL, false, floating);
            landing.flush = (content, margin) -> restFlush;
            StackLayout.Result a = StackLayout.layout(crossing, new DockGeometry());
            StackLayout.Result b = StackLayout.layout(landing, new DockGeometry());
            String what = f + (floating ? ", Floating" : ", Docked");
            assertEquals(what, b.flushPaddingPx, a.flushPaddingPx);
            assertEquals(what, b.stackHeightPx, a.stackHeightPx);
            assertEquals(what, b.flushBottomInsetPx, a.flushBottomInsetPx);
            // At rest the live reading is the one used.
            assertEquals(what, live.paddingPx(100, 10),
                StackLayout.flushPx(false, restFlush, live, 100, 10));
        }
    }
}
