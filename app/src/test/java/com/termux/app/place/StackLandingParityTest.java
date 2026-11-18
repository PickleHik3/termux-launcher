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
}
