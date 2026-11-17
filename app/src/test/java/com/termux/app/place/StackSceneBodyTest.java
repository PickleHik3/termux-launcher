package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * The flat body surface drawn on every page (D1-A step 3): laid out once, at the smallest of the
 * pages' rooms, and cut down to the front page's room by the scene, so a landing lays nothing out.
 * At rest its bottom is exactly where the front page's room put its layout before; a crossing
 * blends the two ends' rooms, and its last frame is the arriving rest.
 */
@RunWith(Parameterized.class)
public class StackSceneBodyTest {

    private static final int WINDOW_HEIGHT = 2400;
    private static final int WINDOW_WIDTH = 1080;
    private static final boolean[] BOOLS = {false, true};

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private final LayoutFixtures.Fixture f;

    public StackSceneBodyTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    @Test public void theCutIsTheRoomsOverTheLayoutsAtBothEnds() {
        assertEquals(60, StackScene.bodyCutPx(100, 40, 0f, 40));
        assertEquals(0, StackScene.bodyCutPx(100, 40, 1f, 40));
        assertEquals("the rooms blended, 70, over the layout's 40", 30,
            StackScene.bodyCutPx(100, 40, 0.5f, 40));
        assertEquals("never below the layout", 0, StackScene.bodyCutPx(30, 20, 0.5f, 40));
        assertEquals("p is clamped", 0, StackScene.bodyCutPx(100, 40, 2f, 40));
    }

    @Test public void atRestTheFrontPagesBottomIsExactlyItsRoom() {
        for (boolean floating : BOOLS) for (boolean up : BOOLS) for (PageRef front : pages()) {
            int homeRoom = room(PageRef.HOME, up, floating);
            int wsRoom = room(PageRef.TERMINAL, up, floating);
            int laidOut = Math.min(homeRoom, wsRoom);
            StackEnd frontEnd = front.isHome() ? end(PageRef.HOME, homeRoom)
                : end(PageRef.TERMINAL, wsRoom);
            StackScene.Frame frame = frame(frontEnd, frontEnd, 0f, laidOut);
            int frontRoom = front.isHome() ? homeRoom : wsRoom;
            assertEquals(f.name + " front=" + front + " floating=" + floating + " up=" + up,
                WINDOW_HEIGHT - frontRoom, bottomShown(frame, laidOut));
        }
    }

    @Test public void aCrossingsLastFrameIsTheArrivingRestAndItsFirstTheLeftOne() {
        for (boolean floating : BOOLS) for (boolean upHome : BOOLS) for (boolean upWs : BOOLS) {
            int homeRoom = room(PageRef.HOME, upHome, floating);
            int wsRoom = room(PageRef.TERMINAL, upWs, floating);
            int laidOut = Math.min(homeRoom, wsRoom);
            StackEnd home = end(PageRef.HOME, homeRoom);
            StackEnd ws = end(PageRef.TERMINAL, wsRoom);
            for (boolean fromHome : BOOLS) {
                StackEnd from = fromHome ? home : ws;
                StackEnd to = fromHome ? ws : home;
                assertTrue(f.name, frame(from, to, 0f, laidOut)
                    .sameTransforms(frame(from, from, 0f, laidOut)));
                assertTrue(f.name + " floating=" + floating + " " + upHome + "/" + upWs,
                    frame(from, to, 1f, laidOut)
                        .sameTransforms(frame(to, to, 0f, laidOut)));
                int last = Integer.MAX_VALUE;
                for (int i = 0; i <= 8; i++) {
                    float p = i / 8f;
                    int shown = bottomShown(frame(from, to, p, laidOut), laidOut);
                    int lo = WINDOW_HEIGHT - Math.max(homeRoom, wsRoom);
                    int hi = WINDOW_HEIGHT - Math.min(homeRoom, wsRoom);
                    assertTrue(f.name + " frame " + i + " stays between the rests",
                        shown >= lo && shown <= hi);
                }
            }
        }
    }

    @Test public void theSmallestRoomIsLaidOutWithNoCut() {
        for (boolean floating : BOOLS) for (boolean up : BOOLS) {
            int homeRoom = room(PageRef.HOME, up, floating);
            int wsRoom = room(PageRef.TERMINAL, up, floating);
            int laidOut = Math.min(homeRoom, wsRoom);
            PageRef small = homeRoom <= wsRoom ? PageRef.HOME : PageRef.TERMINAL;
            StackEnd end = end(small, laidOut);
            assertFalse(f.name, frame(end, end, 0f, laidOut).clipped[StackScene.V_BODY]);
        }
    }

    private static PageRef[] pages() {
        return new PageRef[] {PageRef.HOME, PageRef.TERMINAL};
    }

    private int room(PageRef page, boolean up, boolean floating) {
        return PageRoom.bottomInsetPx(f.rest(page, page, up, floating));
    }

    private static StackEnd end(PageRef page, int room) {
        StackEnd end = new StackEnd(page);
        end.roomPx = room;
        return end;
    }

    private static StackScene.Frame frame(StackEnd from, StackEnd to, float p, int laidOutRoom) {
        StackGeometry g = new StackGeometry();
        g.displayWidthPx = WINDOW_WIDTH;
        g.displayHeightPx = WINDOW_HEIGHT;
        g.bodyWidthPx = WINDOW_WIDTH;
        g.bodyHeightPx = WINDOW_HEIGHT - laidOutRoom;
        g.bodyLaidOutRoomPx = laidOutRoom;
        StackScene.Motion motion = new StackScene.Motion();
        StackScene.Frame out = new StackScene.Frame();
        StackScene.at(from, to, p, motion, g, out);
        return out;
    }

    /** Where the body surface's bottom stands in the window: its layout's, less its cut. */
    private static int bottomShown(StackScene.Frame frame, int laidOutRoom) {
        return frame.clipped[StackScene.V_BODY]
            ? frame.clipBottom[StackScene.V_BODY] : WINDOW_HEIGHT - laidOutRoom;
    }
}
