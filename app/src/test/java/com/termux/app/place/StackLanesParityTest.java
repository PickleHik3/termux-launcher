package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.page.Side;
import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * The bottom stack laid out once at the union of both sides' rests (D1-A step 2), on every layout
 * fixture, Docked and Floating apart, the keyboard up and down: the band is the taller side's;
 * each lane's slide at rest is the crossing's end exactly, so a landing writes nothing; and the
 * room is today's, the container's extra height handed back as the content's reach past its top.
 */
@RunWith(Parameterized.class)
public class StackLanesParityTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private static final boolean[] BOOLS = {false, true};
    private static final PageRef[] PAGES = {PageRef.HOME, PageRef.TERMINAL};
    private static final int WINDOW_PX = 2400;

    private final LayoutFixtures.Fixture f;

    public StackLanesParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    private int rowsPx(@NonNull PageRef front, @NonNull PageRef page, boolean keyboardUp,
                       boolean floating) {
        return StackLayout.restRowsPx(f.rest(front, page, keyboardUp, floating));
    }

    @Test public void theBandIsTheTallerSidesRestAndTheFrontStandsTodays() {
        for (boolean floating : BOOLS) for (boolean up : BOOLS) for (PageRef front : PAGES) {
            PageRef other = front.isHome() ? PageRef.TERMINAL : PageRef.HOME;
            int mine = rowsPx(front, front, up, floating);
            int theirs = rowsPx(front, other, up, floating);
            int slack = StackLanes.slackPx(mine, theirs);
            String where = f.name + " front=" + front + " floating=" + floating + " up=" + up;
            assertEquals(where + ": union", Math.max(mine, theirs), mine + slack);
            // The same union from either front.
            assertEquals(where + ": both fronts agree on the band",
                Math.max(mine, theirs), Math.max(
                    rowsPx(other, other, up, floating), rowsPx(other, front, up, floating)));
            assertTrue(where, slack >= 0);
            // What the front stands at rest is its own, the other side's rows never counted in it.
            assertEquals(where + ": the front rest is the page's own",
                mine, rowsPx(other, front, up, floating));
        }
    }

    @Test public void theExtraKeysAreTheWorkspacesAndStandTheBandTallerOnHome() {
        for (boolean floating : BOOLS) for (boolean up : BOOLS) {
            int home = rowsPx(PageRef.HOME, PageRef.HOME, up, floating);
            int window = rowsPx(PageRef.HOME, PageRef.TERMINAL, up, floating);
            assertTrue(f.name + ": the Terminal's rows (apps and extra keys) reach over Home's",
                window > home);
            assertEquals(f.name + ": from Home the slab starts below the extra keys' room",
                window - home, StackLanes.slackPx(home, window));
            assertEquals(f.name + ": from the Terminal the band is its own rest", 0,
                StackLanes.slackPx(window, home));
        }
    }

    @Test public void theRoomIsTodaysWithTheBandAtTheUnion() {
        for (boolean floating : BOOLS) for (boolean up : BOOLS) for (PageRef front : PAGES) {
            PageRef other = front.isHome() ? PageRef.TERMINAL : PageRef.HOME;
            StackLayout.Result r = f.settle(front, up, floating);
            int kbDown = up ? 0 : f.keyboardLaidOutPx();
            int slack = StackLanes.slackPx(rowsPx(front, front, up, floating),
                rowsPx(front, other, up, floating));
            // Today: the window less the stack and its margin, the content reaching past by the overlap.
            int today = WINDOW_PX - StackScene.containerHeightPx(r.stackHeightPx, kbDown)
                - r.bottomMarginPx + StackScene.contentOverlapPx(r.contentOverlapPx, kbDown);
            int union = WINDOW_PX
                - StackScene.containerHeightPx(r.stackHeightPx, kbDown, slack)
                - r.bottomMarginPx
                + StackScene.contentOverlapPx(r.contentOverlapPx, kbDown, slack);
            assertEquals(f.name + " front=" + front + " floating=" + floating + " up=" + up,
                today, union);
            // And the room before the keyboard stood laid out while down: the stack alone.
            assertEquals(WINDOW_PX - r.stackHeightPx - r.bottomMarginPx + r.contentOverlapPx,
                union);
        }
    }

    private StackEnd end(@NonNull PageRef page) {
        StackEnd end = new StackEnd(page);
        end.rest.containerWidthPx = f.widthPx;
        end.rest.displayWidthPx = f.widthPx;
        end.rest.displayHeightPx = f.heightPx;
        return end;
    }

    private static final int HOME_SIDE = Side.HOME.ordinal();
    private static final int WORKSPACE_SIDE = Side.WORKSPACE.ordinal();

    @Test public void atRestTheFrontLaneStandsAtZeroAndTheOtherOffItsPagesSide() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame frame = new StackScene.Frame();
        StackEnd home = end(PageRef.HOME);
        StackEnd window = end(PageRef.TERMINAL);
        StackScene.rest(home, 0f, motion, frame);
        assertEquals(0f, frame.sideTx[HOME_SIDE], 0f);
        assertEquals(f.widthPx, frame.sideTx[WORKSPACE_SIDE], 0f);
        StackScene.rest(window, 0f, motion, frame);
        assertEquals(-f.widthPx, frame.sideTx[HOME_SIDE], 0f);
        assertEquals(0f, frame.sideTx[WORKSPACE_SIDE], 0f);
    }

    @Test public void aCrossingsFirstAndLastFramesAreEachEndsRestExactly() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame at = new StackScene.Frame();
        StackScene.Frame rest = new StackScene.Frame();
        StackEnd[] ends = {end(PageRef.HOME), end(PageRef.TERMINAL)};
        for (StackEnd from : ends) for (StackEnd to : ends) {
            StackGeometry g = from.rest;
            StackScene.at(from, to, 0f, motion, g, at);
            StackScene.rest(from, 0f, motion, rest);
            assertSides(f.name + " p=0", rest, at);
            StackScene.at(from, to, 1f, motion, g, at);
            StackScene.rest(to, 0f, motion, rest);
            assertSides(f.name + " p=1", rest, at);
        }
    }

    @Test public void inACrossingEachLaneSlidesWithItsPageAndTheOtherStaysApartFromIt() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame at = new StackScene.Frame();
        StackEnd home = end(PageRef.HOME);
        StackEnd window = end(PageRef.TERMINAL);
        float lastHome = 0f;
        float lastWorkspace = f.widthPx;
        for (int i = 1; i <= 16; i++) {
            float p = i / 16f;
            StackScene.at(home, window, p, motion, home.rest, at);
            assertEquals(-p * f.widthPx, at.sideTx[HOME_SIDE], 0.01f);
            assertEquals((1f - p) * f.widthPx, at.sideTx[WORKSPACE_SIDE], 0.01f);
            assertTrue("Home's lane only moves toward its own side", at.sideTx[HOME_SIDE] <= lastHome);
            assertTrue("the Workspace's only moves toward the front",
                at.sideTx[WORKSPACE_SIDE] <= lastWorkspace);
            assertEquals("a lane is a wall's width from the other's",
                f.widthPx, at.sideTx[WORKSPACE_SIDE] - at.sideTx[HOME_SIDE], 0.01f);
            lastHome = at.sideTx[HOME_SIDE];
            lastWorkspace = at.sideTx[WORKSPACE_SIDE];
        }
    }

    @Test public void betweenTwoPagesOfOneSideNoLaneMoves() {
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame at = new StackScene.Frame();
        StackEnd home = end(PageRef.HOME);
        for (float p : new float[] {0f, 0.3f, 0.7f, 1f}) {
            StackScene.at(home, home, p, motion, home.rest, at);
            assertEquals(0f, at.sideTx[HOME_SIDE], 0f);
            assertEquals(f.widthPx, at.sideTx[WORKSPACE_SIDE], 0f);
        }
    }

    private void assertSides(String what, StackScene.Frame expected, StackScene.Frame actual) {
        for (Side side : Side.values()) {
            assertEquals(what + " " + side, expected.sideTx[side.ordinal()],
                actual.sideTx[side.ordinal()], 0f);
        }
        assertTrue(what + " transforms", expected.sameAs(actual));
    }
}
