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
                assertFalse(enters(line,p.card));
                assertFalse(enters(line,p.target.box));
            }
            for (Placement q : result.placements) {
                if (p == q || p.page != q.page) continue;
                assertFalse(p.card.overlaps(q.card));
                assertFalse(p.card.overlaps(q.target.box));
                for (Segment line : p.lines) {
                    assertFalse(enters(line,q.card));
                    assertFalse(enters(line,q.target.box));
                    for (Segment other : q.lines) assertFalse(crosses(line,other));
                }
                if (p.lane >= 0) assertNotEquals(p.lane, q.lane);
            }
        }
    }
    // Independent geometry oracle: tests do not reuse the router's collision helpers.
    private boolean enters(Segment s, Box b) {
        float l=Math.min(s.x1,s.x2), r=Math.max(s.x1,s.x2);
        float t=Math.min(s.y1,s.y2), bottom=Math.max(s.y1,s.y2);
        return s.x1==s.x2 ? l>b.left && l<b.right && bottom>b.top && t<b.bottom
            : t>b.top && t<b.bottom && r>b.left && l<b.right;
    }
    private boolean crosses(Segment a,Segment b) {
        if (a.x1==a.x2 && b.y1==b.y2)
            return between(a.x1,b.x1,b.x2) && between(b.y1,a.y1,a.y2);
        if (a.y1==a.y2 && b.x1==b.x2) return crosses(b,a);
        if (a.x1==a.x2) return a.x1==b.x1 && overlap(a.y1,a.y2,b.y1,b.y2);
        return a.y1==b.y1 && overlap(a.x1,a.x2,b.x1,b.x2);
    }
    private boolean between(float x,float a,float b) { return x>=Math.min(a,b) && x<=Math.max(a,b); }
    private boolean overlap(float a,float b,float c,float d) {
        return between(a,c,d)||between(b,c,d)||between(c,a,b)||between(d,a,b);
    }
    @Test public void flushRowsLeaveFromTheUpperEndCap() {
        assertSafe(route(Arrays.asList(t("dock",0,620,400,660,Side.BELOW),
            t("az",0,665,400,690,Side.BELOW))),2);
    }
    @Test public void landscapeSideControlsStayOrthogonal() {
        Result result=HelpLeaderRouter.route(850,400,new Box(90,30,790,350),12,12,
            Arrays.asList(t("rail",0,50,60,350,Side.LEFT),
                t("column",800,80,850,340,Side.RIGHT),
                t("status",300,0,480,24,Side.ABOVE)));
        assertSafe(result,3);
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
