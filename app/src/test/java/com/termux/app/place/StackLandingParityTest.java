package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

/**
 * The landing frame writes what the crossing's last frame wrote (D1-A): the container's height and
 * its bottom margin are one function of the kept band, not of who owns the dock's foot, which flips
 * at the landing. So the stack's layout and the scene's container clip at the last crossing frame
 * (the foot still on the side left) equal the landing's (the foot on the side arrived at), for
 * both directions, the keyboard up and down at each end, Docked and Floating.
 */
@RunWith(Parameterized.class)
public class StackLandingParityTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private static final boolean[] BOOLS = {false, true};
    private static final List<PageRef> PAGES = Arrays.asList(PageRef.HOME, PageRef.TERMINAL);
    private static final int[] KEPT = {0, 24, 63};

    private final LayoutFixtures.Fixture f;

    public StackLandingParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    /** The container's laid-out height as the pass writes it, keyboard down laid out. */
    private static int containerHeight(StackLayout.Result r, BottomInsets insets, int kbDownPx) {
        return StackScene.containerHeightPx(r.stackHeightPx + insets.stackExtraHeightPx, kbDownPx,
            0);
    }

    /** The scene's container frame for a wall travel, the container as laid out. */
    private StackScene.Frame frame(int containerHeightPx, float travel) {
        StackGeometry g = new StackGeometry();
        g.displayWidthPx = f.widthPx;
        g.displayHeightPx = f.heightPx;
        g.containerWidthPx = f.widthPx;
        g.containerHeightPx = containerHeightPx;
        StackScene.Motion m = new StackScene.Motion();
        m.dockTravelPx = travel;
        StackScene.Frame out = new StackScene.Frame();
        StackScene.at(null, null, 1f, m, g, out);
        return out;
    }

    @Test public void theFootFlippingAtTheLandingChangesNoLayoutNumber() {
        for (int kept : KEPT) for (boolean floating : BOOLS) for (boolean up : BOOLS)
            for (PageRef page : PAGES) {
            StackLayout.Result r = f.settle(page, up, floating);
            BottomInsets last = BottomInsets.forKept(0, kept, false, r.bottomMarginPx,
                r.flushBottomInsetPx);
            BottomInsets land = BottomInsets.forKept(0, kept, true, r.bottomMarginPx,
                r.flushBottomInsetPx);
            String at = f.name + " " + page + " floating=" + floating + " up=" + up
                + " kept=" + kept;
            assertEquals(at + " extra", last.stackExtraHeightPx, land.stackExtraHeightPx);
            assertEquals(at + " margin", last.stackMarginPx, land.stackMarginPx);
            assertEquals(at + " rows", last.rowsBottomFromScreenBottomPx(),
                land.rowsBottomFromScreenBottomPx());
            int kbDown = up ? 0 : f.keyboardLaidOutPx();
            assertEquals(at + " height", containerHeight(r, last, kbDown),
                containerHeight(r, land, kbDown));
        }
    }

    @Test public void theLandingsSceneClipsAreTheLastCrossingFramesInBothDirections() {
        for (int kept : KEPT) for (boolean floating : BOOLS) for (boolean upFrom : BOOLS)
            for (boolean upTo : BOOLS) for (PageRef from : PAGES) for (PageRef to : PAGES) {
            if (from.equals(to)) continue;
            // Last crossing frame: the foot still the side left's (it follows the keyboard of
            // the side whose rows stand); the landing: the arriving page's. The same pixels.
            StackLayout.Result r = f.settle(to, upTo, floating);
            BottomInsets last = BottomInsets.forKept(0, kept, !upFrom, r.bottomMarginPx,
                r.flushBottomInsetPx);
            BottomInsets land = BottomInsets.forKept(0, kept, !upTo, r.bottomMarginPx,
                r.flushBottomInsetPx);
            int kbDown = upTo ? 0 : f.keyboardLaidOutPx();
            int hLast = containerHeight(r, last, kbDown);
            int hLand = containerHeight(r, land, kbDown);
            String at = f.name + " " + from + "->" + to + " floating=" + floating
                + " upFrom=" + upFrom + " upTo=" + upTo + " kept=" + kept;
            assertEquals(at + " height", hLast, hLand);
            for (float travel : new float[] {0f, kbDown, kbDown + kept}) {
                StackScene.Frame a = frame(hLast, travel);
                StackScene.Frame b = frame(hLand, travel);
                assertTrue(at + " clip travel=" + travel, a.sameTransforms(b));
            }
        }
    }

    /**
     * With no pre-roll the keyboard is laid out the whole crossing, shown or down (D1-A): the
     * crossing's last frame stands the keyboard {@code (1 - reveal) * laidOutPx} down, which is
     * what the landing's rest stands for the same keyboard, so the landing writes the same
     * container translation and clips, down to up and up to down, the keyboard counted once.
     */
    @Test public void aKeyboardLaidOutTheWholeCrossingLandsOnTheTransformsItsLastFrameStood() {
        for (int kept : KEPT) for (boolean floating : BOOLS) for (boolean upTo : BOOLS)
            for (PageRef to : PAGES) {
            StackLayout.Result r = f.settle(to, upTo, floating);
            BottomInsets land = BottomInsets.forKept(0, kept, !upTo, r.bottomMarginPx,
                r.flushBottomInsetPx);
            int kb = f.keyboardLaidOutPx();
            int height = containerHeight(r, land, upTo ? 0 : kb);
            // The crossing's last frame: the keyboard laid out (up or down), reveal of the end.
            StackScene.Motion last = new StackScene.Motion();
            StackScene.travel(false, upTo ? 1f : 0f, 1f, kb, 0, 0, last);
            // The landing's rest: nothing of the crossing, plus the down keyboard's own share.
            StackScene.Motion rest = new StackScene.Motion();
            StackScene.withKeyboardRest(new StackScene.Motion(),
                StackScene.keyboardTranslationPx(0f, upTo ? 0 : kb), rest);
            String at = f.name + " to=" + to + " floating=" + floating + " upTo=" + upTo
                + " kept=" + kept;
            assertEquals(at + " travel", last.dockTravelPx, rest.dockTravelPx, 0f);
            assertTrue(at + " clips", frame(height, last.dockTravelPx)
                .sameTransforms(frame(height, rest.dockTravelPx)));
        }
    }

    /** The Terminal's flush padding (px) a pass stands while the keyboard is down. */
    private static final int FLUSH_PX = 19;

    /**
     * The Terminal's pass as the activity fills it: flush absorption stood down while Home is on
     * screen (the crossing toward the Terminal), on while the Terminal is.
     */
    private StackLayout.Result terminalPass(boolean up, boolean floating, boolean flushAllowed) {
        StackLayout.Input in = f.pass(PageRef.TERMINAL, up, floating);
        in.flush = (content, margin) -> FLUSH_PX;
        in.flushAllowed = flushAllowed && in.flushAllowed;
        return StackLayout.layout(in, new com.termux.app.dock.DockGeometry());
    }

    /**
     * The Terminal's flush padding is part of its rest, not of the pass: the container is laid
     * out as tall for a pass that stands it as for one that does not (Home on screen, a crossing
     * toward the Terminal), so the landing, where the gate flips, writes the clip the crossing's
     * last frame stood, Docked and Floating, the keyboard up or down at each end.
     */
    @Test public void theTerminalsFlushPaddingMovesNoContainerHeightAcrossALanding() {
        for (int kept : KEPT) for (boolean floating : BOOLS) {
            int restFlush = terminalPass(false, floating, true).flushPaddingPx;
            for (boolean upFrom : BOOLS) for (boolean upTo : BOOLS)
                for (boolean flushFrom : BOOLS) for (boolean flushTo : BOOLS) {
                StackLayout.Result a = terminalPass(upFrom, floating, flushFrom);
                StackLayout.Result b = terminalPass(upTo, floating, flushTo);
                String at = f.name + " floating=" + floating + " kept=" + kept + " up="
                    + upFrom + "/" + upTo + " flush=" + flushFrom + "/" + flushTo;
                int hA = containerHeightWithFlush(a, kept, upFrom, restFlush);
                int hB = containerHeightWithFlush(b, kept, upTo, restFlush);
                assertEquals(at + " height", hA, hB);
                int kbA = upFrom ? 0 : f.keyboardLaidOutPx();
                for (float travel : new float[] {0f, kbA, kbA + kept}) {
                    assertTrue(at + " clip travel=" + travel,
                        frame(hA, travel).sameTransforms(frame(hB, travel)));
                }
            }
        }
    }

    /** The container's height as the pass writes it, the flush slack included. */
    private int containerHeightWithFlush(StackLayout.Result r, int kept, boolean up,
                                         int restFlushPx) {
        BottomInsets insets = BottomInsets.forKept(0, kept, !up, r.bottomMarginPx,
            r.flushBottomInsetPx);
        return StackScene.containerHeightPx(r.stackHeightPx + insets.stackExtraHeightPx,
            up ? 0 : f.keyboardLaidOutPx(), StackLanes.flushSlackPx(restFlushPx, r.flushPaddingPx));
    }
}
