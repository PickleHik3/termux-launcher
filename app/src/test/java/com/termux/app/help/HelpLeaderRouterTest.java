package com.termux.app.help;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static com.termux.app.help.HelpLeaderRouter.*;

public class HelpLeaderRouterTest {
    private Target t(String id, float l, float top, float r, float bottom, Side side) {
        return new Target(id, new Box(l, top, r, bottom), side, 150, 64);
    }
    private Result route(List<Target> targets) {
        return HelpLeaderRouter.route(400, 850, new Box(0, 100, 400, 590), 12, 12, targets);
    }
    private void assertSafe(Result result, int expected) {
        assertTrue(result.unplaced.isEmpty());
        assertEquals(expected, result.placements.size());
        for (Placement p : result.placements) {
            assertTrue(p.lines.size() <= 3);
            assertFalse(p.card.overlaps(p.target.box));
            for (Segment line : p.lines) {
                assertTrue(line.x1 == line.x2 || line.y1 == line.y2);
                assertFalse(line.enters(p.card));
                assertFalse(line.enters(p.target.box));
            }
            for (Placement q : result.placements) {
                if (p == q || p.page != q.page) continue;
                assertFalse(p.card.overlaps(q.card));
                assertFalse(p.card.overlaps(q.target.box));
                for (Segment line : p.lines) {
                    assertFalse(line.enters(q.card));
                    assertFalse(line.enters(q.target.box));
                    for (Segment other : q.lines) assertFalse(line.intersects(other));
                }
                if (p.lane >= 0) assertNotEquals(p.lane, q.lane);
            }
        }
    }
    @Test public void defaultTerminalPortrait() {
        assertSafe(route(Arrays.asList(
            t("sessions", 12, 20, 52, 44, Side.ABOVE),
            t("windows", 60, 20, 165, 44, Side.ABOVE),
            t("plus", 168, 20, 192, 44, Side.ABOVE),
            t("stats", 240, 20, 355, 44, Side.ABOVE),
            t("status", 365, 20, 389, 44, Side.ABOVE),
            t("corner", 0, 100, 32, 132, Side.INSIDE),
            t("dock", 16, 610, 384, 650, Side.BELOW),
            t("az", 16, 656, 384, 680, Side.BELOW),
            t("chords", 0, 800, 120, 840, Side.BELOW),
            t("space", 122, 800, 280, 840, Side.BELOW))), 10);
    }
    @Test public void defaultDisplayPortrait() {
        assertSafe(route(Arrays.asList(
            t("apps", 20, 20, 180, 44, Side.ABOVE),
            t("stats", 230, 20, 350, 44, Side.ABOVE),
            t("status", 360, 20, 390, 44, Side.ABOVE),
            t("corner", 0, 100, 32, 132, Side.INSIDE),
            t("start", 140, 330, 260, 378, Side.INSIDE),
            t("touchpad", 20, 620, 380, 830, Side.BELOW))), 6);
    }
    @Test public void defaultWidgetsPortrait() {
        assertSafe(route(Arrays.asList(
            t("status", 350, 20, 390, 44, Side.ABOVE),
            t("corner", 0, 100, 32, 132, Side.INSIDE),
            t("widget", 15, 230, 185, 345, Side.INSIDE),
            t("empty", 210, 350, 390, 570, Side.INSIDE))), 4);
    }
    @Test public void omitMissingTargetAndItsCard() {
        assertSafe(route(Arrays.asList(null, new Target("gone", null, Side.ABOVE, 150, 64),
            t("shown", 20, 20, 60, 40, Side.ABOVE))), 1);
    }
    @Test public void overflowPaginatesAndEachEdgeLaneCarriesOneLine() {
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < 5; i++) targets.add(t("row"+i, 20, 620+i*30, 380, 640+i*30, Side.BELOW));
        Result result = route(targets);
        assertSafe(result, 5);
        assertTrue(result.pages >= 2);
    }
    @Test public void tryOtherColumnBeforeAnotherPage() {
        Result result = HelpLeaderRouter.route(400, 850, new Box(0,100,400,180), 12,12,
            Arrays.asList(t("one",20,620,380,650,Side.BELOW),
                t("two",20,680,380,710,Side.BELOW)));
        assertSafe(result,2);
        assertEquals(1,result.pages);
        assertNotEquals(result.placements.get(0).column,result.placements.get(1).column);
    }
    @Test public void deterministicAndImpossibleGeometryTerminates() {
        List<Target> input = Arrays.asList(t("one",20,20,60,40,Side.ABOVE));
        Result a=route(input), b=route(input);
        assertEquals(a.placements.get(0).card.top,b.placements.get(0).card.top,0);
        Result impossible=route(Arrays.asList(t("full",0,0,400,850,Side.INSIDE)));
        assertEquals(1,impossible.unplaced.size());
        assertTrue(impossible.placements.isEmpty());
    }
}
