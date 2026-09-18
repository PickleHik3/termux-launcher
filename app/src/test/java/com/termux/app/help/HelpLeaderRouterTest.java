package com.termux.app.help;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    @Test public void fixedLabelsAndFooterAreObstaclesOnEveryPage() {
        Box label = new Box(0,700,60,730);
        Result result=HelpLeaderRouter.route(400,850,new Box(0,100,400,590),12,12,
            Arrays.asList(t("chords",0,800,120,840,Side.BELOW)),Arrays.asList(label));
        assertSafe(result,1);
        for (Placement p : result.placements) {
            assertFalse(p.card.overlaps(label));
            for (Segment segment : p.lines) assertFalse(enters(segment,label));
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

    // ---- The colour-paired layout: cards where the eye looks for them, leaders in the pair colour. ----

    private static Box B(float l, float t, float r, float b) { return new Box(l, t, r, b); }

    private List<Target> terminalTargets() {
        return Arrays.asList(
            t("sessions", 20, 20, 60, 44, Side.ABOVE), t("windows", 62, 20, 200, 44, Side.ABOVE),
            t("stats", 300, 20, 460, 44, Side.ABOVE), t("status", 0, 0, 480, 48, Side.ABOVE),
            t("divider", 238, 60, 242, 700, Side.INSIDE), t("dock", 0, 720, 480, 780, Side.BELOW),
            t("az", 0, 782, 480, 800, Side.BELOW), t("prefix", 0, 900, 120, 960, Side.BELOW),
            t("space", 130, 900, 330, 960, Side.BELOW));
    }

    @Test public void arrangePutsTheWholeTerminalOnOnePage() {
        Result r = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 8, terminalTargets(),
            Collections.singletonList(B(12, 660, 468, 700)), Collections.singletonList(B(238, 60, 242, 700)));
        assertEquals(1, r.pages);
        assertTrue(r.unplaced.isEmpty());
        assertEquals(9, r.placements.size());
        assertNoOverlap(r);
        for (Placement p : r.placements) {
            assertFalse("no leader for " + p.target.id, p.lines.isEmpty());
            // Every card leans on its own control, so none climbs above the topmost one.
            assertTrue(p.target.id + " at " + p.card.top, p.card.top >= 52);
            assertFalse("card over the footer", p.card.overlaps(B(12, 660, 468, 700)));
        }
    }

    @Test public void cardsSitWhereTheEyeLooksForThem() {
        Box band = B(0, 60, 480, 700);
        Result r = HelpLeaderRouter.arrange(band, 12, 12,
            Arrays.asList(t("a", 0, 0, 100, 40, Side.ABOVE), t("b", 300, 0, 400, 40, Side.ABOVE),
                t("c", 40, 800, 140, 840, Side.BELOW), t("d", 238, 200, 242, 500, Side.INSIDE)),
            Collections.emptyList(), Collections.emptyList());
        Placement a = placement(r, "a"), b = placement(r, "b"), c = placement(r, "c"), d = placement(r, "d");
        // On the shelf leaning on its control, across from it; the next one along shares the shelf.
        assertEquals(52f, a.card.top, 0.01f);
        assertEquals(12f, a.card.left, 0.01f);
        assertEquals(a.card.top, b.card.top, 0.01f);
        assertTrue(b.card.left <= 350 && b.card.right >= 350);
        // Over its control, leaning on it, in the slot nearest it.
        assertEquals(788f, c.card.bottom, 0.01f);
        assertEquals(12f, c.card.left, 0.01f);
        // Beside the divider, level with its middle.
        assertEquals(254f, d.card.left, 0.01f);
        assertEquals(350f, d.card.cy(), 0.01f);
        assertNoOverlap(r);
    }

    @Test public void aTakenSpotSlidesTheCardAwayFromItsControl() {
        Result r = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 12,
            Arrays.asList(t("a", 0, 0, 100, 40, Side.ABOVE), t("b", 100, 0, 200, 40, Side.ABOVE)),
            Collections.emptyList(), Collections.emptyList());
        Placement a = placement(r, "a"), b = placement(r, "b");
        // The second card shares the shelf, in the next slot along, rather than stepping in.
        assertEquals(a.card.top, b.card.top, 0.01f);
        assertEquals(a.card.right + 12, b.card.left, 0.01f);
        // Its leader takes a lane of its own rather than a line through the first card.
        for (Segment line : b.lines) assertFalse(enters(line, a.card));
        assertLeadersClear(r, 12);
    }

    /** A control the cards have boxed in keeps its card in place and gives up its line. */
    @Test public void aBoxedInControlKeepsItsCardAndLosesItsLine() {
        List<Target> crowd = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++)
            crowd.add(new Target("t" + i, B(i * 20, 0, i * 20 + 10, 40), Side.ABOVE, 200, 100));
        Result r = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 8, crowd,
            Collections.emptyList(), Collections.emptyList());
        assertTrue(r.unplaced.isEmpty());
        assertEquals(1, r.pages);
        assertNoOverlap(r);
        assertLeadersClear(r, 8);
        int bare = 0;
        for (Placement p : r.placements) if (p.lines.isEmpty()) bare++;
        assertTrue("every crowded card still drew a line", bare > 0);
    }

    @Test public void leadersAreStraightWhenFacingAndElbowedWhenBeside() {
        Target above = t("above", 100, 0, 200, 40, Side.ABOVE);
        assertEquals(1, HelpLeaderRouter.leader(above, B(100, 100, 200, 160)).size());
        assertEquals(3, HelpLeaderRouter.leader(above, B(300, 100, 400, 160)).size());
        Target inside = t("inside", 238, 60, 242, 700, Side.INSIDE);
        List<Segment> beside = HelpLeaderRouter.leader(inside, B(20, 300, 200, 360));
        assertEquals(1, beside.size());
        // From the card's edge to the box's.
        assertEquals(200f, beside.get(0).x1, 0.01f);
        assertEquals(238f, beside.get(0).x2, 0.01f);
        assertTrue(HelpLeaderRouter.leader(inside, B(200, 300, 300, 360)).isEmpty());
    }

    @Test public void arrangeKeepsACardOnItsControlsSide() {
        Result r = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 8,
            Arrays.asList(t("left", 0, 0, 100, 40, Side.ABOVE), t("right", 380, 0, 480, 40, Side.ABOVE)),
            Collections.emptyList(), Collections.emptyList());
        assertEquals(0, placement(r, "left").column);
        assertEquals(1, placement(r, "right").column);
    }

    @Test public void arrangeYieldsToInsideBoxesUnlessNothingFitsOtherwise() {
        // A widget row one shelf tall under the status bar: the status card steps one shelf in.
        Box widget = B(0, 60, 480, 120);
        Result r = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 8,
            Arrays.asList(t("widget", 0, 60, 480, 120, Side.INSIDE), t("status", 0, 0, 480, 48, Side.ABOVE)),
            Collections.emptyList(), Collections.singletonList(widget));
        assertEquals(1, r.pages);
        for (Placement p : r.placements) assertFalse(p.target.id, p.card.overlaps(widget));
        // A wall-sized soft box leaves no room; the card sits over the dimmed control instead of on a second page.
        Result full = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 8,
            Arrays.asList(t("empty", 0, 60, 480, 700, Side.INSIDE)),
            Collections.emptyList(), Collections.singletonList(B(0, 60, 480, 700)));
        assertEquals(1, full.pages);
        assertTrue(full.unplaced.isEmpty());
    }

    @Test public void arrangeStartsASecondPageOnlyWhenNothingFits() {
        List<Target> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) many.add(new Target("t" + i, B(i * 20, 0, i * 20 + 10, 40), Side.ABOVE, 200, 100));
        Result r = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 8, many, Collections.emptyList(), Collections.emptyList());
        assertTrue(r.pages >= 2);
        assertTrue(r.unplaced.isEmpty());
        assertNoOverlap(r);
        // Two cards of 200 share each shelf of a 480-wide band and a card walks four shelves at
        // most: page one holds eight.
        int onFirst = 0;
        for (Placement p : r.placements) if (p.page == 0) onFirst++;
        assertEquals(8, onFirst);
    }

    // ---- No two leaders may overlap or touch; parallel runs keep the gap. ----

    @Test public void coincidentLeadersTakeSeparateLanes() {
        // Both boxes are centred on x = 100: the straight leaders would be the same line.
        List<Target> targets = Arrays.asList(
            t("wide", 0, 0, 200, 40, Side.ABOVE), t("narrow", 80, 0, 120, 40, Side.ABOVE));
        Result r = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 12, targets,
            Collections.emptyList(), Collections.emptyList());
        assertTrue(r.unplaced.isEmpty());
        assertEquals(2, r.placements.size());
        assertNoOverlap(r);
        assertLeadersClear(r, 12);
        for (Placement p : r.placements) assertFalse(p.lines.isEmpty());
    }

    /** The Terminal as it opens on the phone: everything fits on one page. */
    @Test public void defaultTerminalArrangesOnOnePage() {
        List<Target> targets = new java.util.ArrayList<>(terminalTargets());
        targets.add(t("plus", 202, 20, 226, 44, Side.ABOVE));
        List<Box> keys = Arrays.asList(B(0, 900, 120, 960), B(130, 900, 330, 960));
        Result r = HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 12, targets, keys,
            Collections.singletonList(B(238, 60, 242, 700)));
        assertTrue("unplaced " + ids(r.unplaced), r.unplaced.isEmpty());
        assertEquals(1, r.pages);
        assertEquals(10, r.placements.size());
        assertNoOverlap(r);
        assertLeadersClear(r, 12);
        for (Placement p : r.placements) assertFalse("no leader for " + p.target.id, p.lines.isEmpty());
    }

    /**
     * The phone this was measured on, portrait with the keyboard up: a 1080x2412 screen whose pane
     * wall stops at 1428 and whose dock (1428-1581), A-Z row (1581-1631), extra keys (1631-1729)
     * and keyboard (1729-2349) fill everything under it, with the two prefix keys, the space bar
     * and the settings cog in the keyboard's bottom row. Every one of those controls has to come
     * away with a card of its own, above its own box, on one page, off every control and off the
     * seven key cards the overlay has already fixed under the row of keys.
     */
    @Test public void everyControlUnderTheWallIsExplainedAboveItself() {
        Box pane = B(0, 210, 1080, 1428);
        Box dock = B(0, 1428, 1080, 1581), az = B(0, 1581, 1080, 1631);
        Box keysRow = B(0, 1631, 1080, 1729);
        Box prefix = B(0, 2225, 250, 2349), space = B(300, 2225, 780, 2349);
        Box cog = B(196, 2236, 254, 2294);
        List<Target> input = Arrays.asList(
            new Target("windows", B(24, 134, 420, 202), Side.ABOVE, 490, 120),
            new Target("sessions", B(430, 140, 500, 196), Side.ABOVE, 490, 120),
            new Target("stats", B(700, 140, 960, 196), Side.ABOVE, 490, 120),
            new Target("status", B(0, 126, 1080, 210), Side.ABOVE, 490, 120),
            new Target("terminal", pane, Side.INSIDE, 490, 120),
            new Target("dock", dock, Side.UNDER, 316, 130),
            new Target("az", az, Side.UNDER, 316, 130),
            new Target("prefix", prefix, Side.UNDER, 316, 160),
            new Target("space", space, Side.UNDER, 316, 130),
            new Target("settings", cog, Side.UNDER, 316, 150));
        // The overlay lays the key cards out itself, in two rows under the keys; to the router
        // they are simply in the way.
        int[] centres = {77, 231, 385, 539, 693, 847, 1001};
        List<Box> keyCards = new java.util.ArrayList<>();
        for (int i = 0; i < centres.length; i++) {
            float l = i == 0 ? 33 : centres[i - 1] + 27;
            float r = i == centres.length - 1 ? 1047 : centres[i + 1] - 27;
            float top = i % 2 == 0 ? 1767 : 1904;
            keyCards.add(B(l, top, r, top + 110));
        }
        List<Box> controls = Arrays.asList(dock, az, keysRow, prefix, space, cog);
        List<Box> hard = new java.util.ArrayList<>(keyCards);
        hard.addAll(controls);
        Result r = HelpLeaderRouter.arrange(B(0, 232, 1080, 1406), 33, 33, input, hard,
            Arrays.asList(pane));
        assertTrue("unplaced " + ids(r.unplaced), r.unplaced.isEmpty());
        assertEquals(1, r.pages);
        assertNoOverlap(r);
        assertLeadersClear(r, 33);
        for (Placement p : r.placements) {
            if (p.target.side != Side.UNDER) continue;
            assertTrue(p.target.id + " is not above its control", p.card.bottom <= p.target.box.top);
            assertFalse("no leader for " + p.target.id, p.lines.isEmpty());
            for (Box control : controls)
                assertFalse(p.target.id + " sits on a control", p.card.overlaps(control));
            for (Box key : keyCards)
                assertFalse(p.target.id + " sits on a key card", p.card.overlaps(key));
        }
        // The dock and the A-Z row are explained from the wall's foot, not from down in the
        // keyboard; the keyboard's own three from the shelf over its bottom row.
        assertTrue(placement(r, "dock").card.top > 1000);
        assertTrue(placement(r, "az").card.bottom <= dock.top);
        for (String id : new String[] {"prefix", "space", "settings"})
            assertTrue(id + " left the keyboard", placement(r, id).card.top > keysRow.bottom);
    }

    @Test public void everyDefaultLayoutKeepsItsLeadersApart() {
        assertLeadersClear(HelpLeaderRouter.arrange(B(0, 60, 480, 700), 12, 12, terminalTargets(),
            Collections.emptyList(), inside(terminalTargets())), 12);
        assertLeadersClear(HelpLeaderRouter.arrange(B(0, 100, 400, 590), 12, 12, displayTargets(),
            Collections.emptyList(), inside(displayTargets())), 12);
        assertLeadersClear(HelpLeaderRouter.arrange(B(0, 100, 400, 590), 12, 12, widgetsTargets(),
            Collections.emptyList(), inside(widgetsTargets())), 12);
    }

    private List<Target> displayTargets() {
        return Arrays.asList(t("apps", 20, 20, 180, 44, Side.ABOVE),
            t("stats", 230, 20, 350, 44, Side.ABOVE), t("status", 360, 20, 390, 44, Side.ABOVE),
            t("corner", 0, 100, 32, 132, Side.INSIDE), t("start", 140, 330, 260, 378, Side.INSIDE),
            t("touchpad", 20, 620, 380, 830, Side.BELOW));
    }

    private List<Target> widgetsTargets() {
        return Arrays.asList(t("status", 350, 20, 390, 44, Side.ABOVE),
            t("corner", 0, 100, 32, 132, Side.INSIDE), t("widget", 15, 230, 185, 345, Side.INSIDE),
            t("empty", 210, 350, 390, 570, Side.INSIDE));
    }

    private static List<Box> inside(List<Target> targets) {
        List<Box> boxes = new java.util.ArrayList<>();
        for (Target t : targets) if (t.side == Side.INSIDE) boxes.add(t.box);
        return boxes;
    }

    private static String ids(List<Target> targets) {
        StringBuilder s = new StringBuilder();
        for (Target t : targets) s.append(t.id).append(' ');
        return s.toString();
    }

    /** No leader may cross, touch or run within the gap of another card's leader. */
    private void assertLeadersClear(Result r, float gap) {
        for (Placement p : r.placements) for (Placement q : r.placements) {
            if (p == q || p.page != q.page) continue;
            for (Segment a : p.lines) for (Segment b : q.lines) {
                assertFalse(p.target.id + " crosses " + q.target.id, crosses(a, b));
                assertTrue(p.target.id + " runs into " + q.target.id + " (" + clearance(a, b) + ")",
                    clearance(a, b) >= gap - 0.001f);
            }
        }
    }

    // Endpoint projection, not the router's bounding-box maths.
    private float clearance(Segment a, Segment b) {
        if (crosses(a, b)) return 0;
        return Math.min(Math.min(pointToSegment(a.x1, a.y1, b), pointToSegment(a.x2, a.y2, b)),
            Math.min(pointToSegment(b.x1, b.y1, a), pointToSegment(b.x2, b.y2, a)));
    }

    private float pointToSegment(float px, float py, Segment s) {
        float dx = s.x2 - s.x1, dy = s.y2 - s.y1, len = dx * dx + dy * dy;
        float u = len == 0 ? 0 : ((px - s.x1) * dx + (py - s.y1) * dy) / len;
        u = Math.max(0, Math.min(1, u));
        return (float) Math.hypot(px - (s.x1 + u * dx), py - (s.y1 + u * dy));
    }

    private static Placement placement(Result r, String id) {
        for (Placement p : r.placements) if (p.target.id.equals(id)) return p;
        throw new AssertionError(id);
    }

    private static void assertNoOverlap(Result r) {
        for (Placement a : r.placements) for (Placement b : r.placements)
            if (a != b && a.page == b.page) assertFalse(a.target.id + " over " + b.target.id, a.card.overlaps(b.card));
    }

    // ---- One rule for every edge. ----
    /** The same rule seats a control on any edge: its card leans on it from the band's side. */
    @Test public void aControlOnAnyEdgeGetsItsCardOnTheShelfFacingIt() {
        Box band = B(100, 100, 900, 900);
        Result r = HelpLeaderRouter.arrange(band, 12, 12, Arrays.asList(
            t("top", 400, 20, 600, 80, Side.ABOVE), t("bottom", 400, 920, 600, 980, Side.UNDER),
            t("left", 20, 400, 80, 600, Side.LEFT), t("right", 920, 400, 980, 600, Side.RIGHT)),
            Collections.emptyList(), Collections.emptyList());
        assertTrue("unplaced " + ids(r.unplaced), r.unplaced.isEmpty());
        Placement top = placement(r, "top"), bottom = placement(r, "bottom");
        Placement left = placement(r, "left"), right = placement(r, "right");
        assertEquals(92f, top.card.top, 0.01f);
        assertEquals(908f, bottom.card.bottom, 0.01f);
        assertEquals(92f, left.card.left, 0.01f);
        assertEquals(908f, right.card.right, 0.01f);
        // Across from its control: the card spans the control's centre line.
        assertTrue(top.card.left <= 500 && top.card.right >= 500);
        assertTrue(bottom.card.left <= 500 && bottom.card.right >= 500);
        assertTrue(left.card.top <= 500 && left.card.bottom >= 500);
        assertTrue(right.card.top <= 500 && right.card.bottom >= 500);
        for (Placement p : r.placements) assertFalse("no leader for " + p.target.id, p.lines.isEmpty());
        assertNoOverlap(r);
        assertLeadersClear(r, 12);
    }
    /** A rail of controls down one side shares one column of cards, as a row shares a shelf. */
    @Test public void controlsDownOneSideShareAColumnOfCards() {
        Result r = HelpLeaderRouter.arrange(B(100, 0, 900, 1000), 12, 12, Arrays.asList(
            t("a", 20, 100, 80, 160, Side.LEFT), t("b", 20, 180, 80, 240, Side.LEFT),
            t("c", 20, 260, 80, 320, Side.LEFT)),
            Collections.emptyList(), Collections.emptyList());
        assertTrue("unplaced " + ids(r.unplaced), r.unplaced.isEmpty());
        Placement a = placement(r, "a"), b = placement(r, "b"), c = placement(r, "c");
        assertEquals(92f, a.card.left, 0.01f);
        assertEquals(a.card.left, b.card.left, 0.01f);
        assertEquals(a.card.left, c.card.left, 0.01f);
        assertTrue(a.card.bottom <= b.card.top && b.card.bottom <= c.card.top);
        assertNoOverlap(r);
        assertLeadersClear(r, 12);
    }
}
