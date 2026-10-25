package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The display window owns everything inside its edges: no border, pill box or seam band of the
 * wall's reaches its picture, with the keyboard up or down, the grabber pills shown or not, the
 * window alone in the row or among others, in either orientation, at any Margin. A terminal
 * window keeps the reach it has always had.
 */
public class BorderReachTest {

    private static final float DENSITY = 3f;
    private static final float SLOP = 24f;
    private static final float NO_LIMIT = Float.NEGATIVE_INFINITY;
    private static final float CORNER = 40f * DENSITY;
    private static final float STATUS = 32f * DENSITY;      // the status bar above the wall
    private static final float DOCK = 72f * DENSITY;        // the dock's height
    /** The built-in keyboard's share of the screen's height. */
    private static final float KEYBOARD_SHARE = 0.35f;

    /** Portrait and landscape screens, in px. */
    private static final float[][] SCREENS = {{1080f, 2400f}, {2400f, 1080f}};
    /** Margins: the default, a thin one, and none. */
    private static final float[] MARGINS_DP = {12f, 2f, 0f};

    /** How the keyboard and dock stand below the page. */
    private enum Foot {
        /** Keyboard down, the dock below the page's air. */
        KEYBOARD_DOWN,
        /** Keyboard up in Resize: the page ends above the dock, which rides on the keyboard. */
        KEYBOARD_UP_RESIZE,
        /** Keyboard up in Overlay: the page runs on under the dock and keyboard. */
        KEYBOARD_UP_OVERLAY,
        /** No dock on screen (hidden): the air runs to the wall's edge. */
        NO_DOCK
    }

    /**
     * The zone the wall builds for its page in front, as {@code PaneWallLayout.loadBorderZone}
     * does: the page set in by the Margin below the status bar, the air to the wall's edges, the
     * pills, the seam band where the dock is below, and {@link BorderReach#apply}.
     */
    private static BorderDrag.Zone zone(float[] screen, float marginDp, Foot foot,
                                        boolean pills, boolean display) {
        float margin = marginDp * DENSITY;
        float width = screen[0];
        float keyboard = screen[1] * KEYBOARD_SHARE;
        float wallBottom;
        float dockTop;
        switch (foot) {
            case KEYBOARD_UP_RESIZE:
                dockTop = screen[1] - keyboard - DOCK;
                wallBottom = dockTop;
                break;
            case KEYBOARD_UP_OVERLAY:
                dockTop = screen[1] - keyboard - DOCK;
                wallBottom = screen[1] - DOCK;
                break;
            case NO_DOCK:
                dockTop = Float.NaN;
                wallBottom = screen[1];
                break;
            default:
                dockTop = screen[1] - DOCK;
                wallBottom = dockTop;
                break;
        }
        float left = margin;
        float top = STATUS + margin;
        float right = width - margin;
        float bottom = wallBottom - margin;
        BorderDrag.Zone zone = new BorderDrag.Zone().page(left, top, right, bottom)
            .gaps(margin, margin, margin, margin)
            .minBand(BorderDrag.MIN_BAND_DP * DENSITY)
            .sideReach(BorderDrag.SIDE_REACH_DP * DENSITY)
            .corner(CORNER)
            .pills((left + right) / 2f, pills ? BorderDrag.PILL_SWIPE_WIDTH_DP * DENSITY : 0f,
                pills ? BorderDrag.PILL_SWIPE_REACH_DP * DENSITY : 0f);
        boolean dockBelow = !Float.isNaN(dockTop) && dockTop >= bottom;
        float airBottom = dockBelow ? Math.min(wallBottom, dockTop) : wallBottom;
        zone.seam(dockBelow ? dockTop : Float.NaN,
            SeamSwipe.paneReachPx(airBottom - bottom, DENSITY), SeamSwipe.dockReachPx(DENSITY));
        return BorderReach.apply(zone, display, DENSITY);
    }

    private static BorderDrag.Zone displayAtRest() {
        return zone(SCREENS[0], 12f, Foot.KEYBOARD_DOWN, true, true);
    }

    // ---- Whose page --------------------------------------------------------------------------

    @Test
    public void onlyTheDisplayWindowInFrontOwnsItsContent() {
        assertTrue(BorderReach.ownsContent(PaneWallPage.TERMINAL, true));
        assertFalse("a terminal window", BorderReach.ownsContent(PaneWallPage.TERMINAL, false));
        assertFalse("a Home page", BorderReach.ownsContent(PaneWallPage.WIDGETS, false));
        assertFalse(BorderReach.ownsContent(PaneWallPage.WIDGETS, true));
    }

    // ---- The display window ------------------------------------------------------------------

    @Test
    public void noZoneOfTheDisplayReachesItsPictureInAnySetUp() {
        for (float[] screen : SCREENS) {
            for (float margin : MARGINS_DP) {
                for (Foot foot : Foot.values()) {
                    for (boolean pills : new boolean[] {true, false}) {
                        BorderDrag.Zone zone = zone(screen, margin, foot, pills, true);
                        assertFalse(describe(screen, margin, foot, pills),
                            BorderReach.reachesContent(zone));
                    }
                }
            }
        }
    }

    @Test
    public void noPressOnTheDisplaysPictureIsTheWallsWhateverTheListenerSays() {
        float[] content = new float[4];
        for (float[] screen : SCREENS) {
            for (float margin : MARGINS_DP) {
                for (Foot foot : Foot.values()) {
                    for (boolean pills : new boolean[] {true, false}) {
                        BorderDrag.Zone zone = zone(screen, margin, foot, pills, true);
                        assertTrue(BorderReach.contentBounds(zone, content));
                        for (boolean alone : new boolean[] {true, false}) {
                            assertNoPressArms(zone, content, !alone,
                                describe(screen, margin, foot, pills) + " alone=" + alone);
                        }
                    }
                }
            }
        }
    }

    /**
     * Every point along the picture's edges, a fingertip in from them, and across its middle:
     * none arms the border drag, even with every swipe on and the body and seam swipes asked
     * for, and none arms the dock's seam press either.
     */
    private static void assertNoPressArms(BorderDrag.Zone zone, float[] c, boolean canPage,
                                          String what) {
        float in = 48f * DENSITY;
        float[] ys = {c[1], c[1] + 1f, c[1] + in / 2f, c[1] + in, (c[1] + c[3]) / 2f,
            c[3] - in, c[3] - in / 2f, c[3] - 1f, Math.nextDown(c[3])};
        float[] xs = {c[0], c[0] + 1f, c[0] + in / 2f, c[0] + in, (c[0] + c[2]) / 2f,
            c[2] - in, c[2] - in / 2f, c[2] - 1f, Math.nextDown(c[2])};
        BorderDrag drag = new BorderDrag();
        for (float y : ys) {
            for (float x : xs) {
                assertFalse(what + " at " + x + "," + y,
                    drag.down(x, y, zone, SLOP, canPage, true, true, NO_LIMIT, true, true));
                assertFalse(what + " dock at " + x + "," + y,
                    drag.downOnDock(x, y, zone, SLOP, canPage));
                drag.reset();
            }
        }
    }

    @Test
    public void atTheDefaultMarginEveryBorderStopsAtTheDisplaysEdge() {
        BorderDrag.Zone zone = displayAtRest();
        float[] box = new float[4];
        assertTrue(zone.bounds(BorderDrag.Border.TOP, box));
        assertEquals("the top border ends at the page's top", zone.mTop, box[3], 0.001f);
        assertTrue(zone.bounds(BorderDrag.Border.BOTTOM, box));
        assertEquals("the bottom border starts at the page's bottom", zone.mBottom, box[1],
            0.001f);
        assertTrue(zone.bounds(BorderDrag.Border.LEFT, box));
        assertEquals("no side reach over the left edge", zone.mLeft, box[2], 0.001f);
        assertTrue(zone.bounds(BorderDrag.Border.RIGHT, box));
        assertEquals("no side reach over the right edge", zone.mRight, box[0], 0.001f);
        assertFalse("no box by the keyboard's pill", zone.pillBounds(BorderDrag.Border.BOTTOM, box));
        assertFalse("no box by the status pill", zone.pillBounds(BorderDrag.Border.TOP, box));
        assertFalse("the seam band has no last row", zone.seamPaneBounds(box));
        assertEquals(SeamSwipe.Start.NONE,
            zone.seamAt((zone.mLeft + zone.mRight) / 2f, zone.mBottom - 1f));
    }

    @Test
    public void whereTheAirIsThinTheDisplaysBorderReachesInByTheFrameLineAlone() {
        float strip = BorderReach.frameStripPx(DENSITY);
        for (float margin : new float[] {2f, 0f}) {
            BorderDrag.Zone zone = zone(SCREENS[0], margin, Foot.KEYBOARD_DOWN, true, true);
            for (BorderDrag.Border side : new BorderDrag.Border[] {BorderDrag.Border.TOP,
                    BorderDrag.Border.BOTTOM, BorderDrag.Border.LEFT, BorderDrag.Border.RIGHT}) {
                assertEquals(side + " at Margin " + margin, strip, zone.inside(side), 0.001f);
            }
        }
    }

    @Test
    public void theDisplayStillPagesFromItsAirAndTheDocksTop() {
        BorderDrag.Zone zone = displayAtRest();
        float midX = (zone.mLeft + zone.mRight) / 2f;
        float midY = (zone.mTop + zone.mBottom) / 2f;
        BorderDrag drag = new BorderDrag();

        // The bottom border's air: a sideways slide pages at once.
        assertTrue(drag.down(midX, zone.mBottom + 2f, zone, SLOP, true, true, true, NO_LIMIT));
        assertTrue(drag.slidePages());
        assertEquals(BorderDrag.Claim.PAGING, drag.move(midX + 60f, zone.mBottom + 2f));
        drag.reset();

        // The same air: up is the keyboard swipe.
        assertTrue(drag.down(midX, zone.mBottom + 2f, zone, SLOP, true, true, true, NO_LIMIT));
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(midX, zone.mBottom - 60f));
        drag.reset();

        // The top border's air: down is the status bar's swipe.
        assertTrue(drag.down(midX, zone.mTop - 2f, zone, SLOP, true, true, true, NO_LIMIT));
        assertEquals(BorderDrag.Claim.STATUS, drag.move(midX, zone.mTop + 60f));
        drag.reset();

        // The air beside it: a sideways slide pages at once.
        assertTrue(drag.down(zone.mLeft - 2f, midY, zone, SLOP, true, false, false, NO_LIMIT));
        assertEquals(BorderDrag.Claim.PAGING, drag.move(zone.mLeft + 60f, midY));
        drag.reset();

        // The dock's top, below the air: a clearly sideways start pages.
        float dockTop = zone.seamAirBottom();
        assertTrue(drag.downOnDock(midX, dockTop + 3f, zone, SLOP, true));
        assertEquals(BorderDrag.Claim.PAGING, drag.move(midX + 60f, dockTop + 3f));
    }

    @Test
    public void theDisplaysFrameLinePagesWhereItHasNoAir() {
        BorderDrag.Zone zone = zone(SCREENS[0], 0f, Foot.KEYBOARD_DOWN, true, true);
        float midY = (zone.mTop + zone.mBottom) / 2f;
        BorderDrag drag = new BorderDrag();
        assertTrue("the left frame line",
            drag.down(zone.mLeft + 1f, midY, zone, SLOP, true, false, false, NO_LIMIT));
        assertEquals(BorderDrag.Claim.PAGING, drag.move(zone.mLeft + 60f, midY));
        drag.reset();
        float strip = BorderReach.frameStripPx(DENSITY);
        assertFalse("past the frame line is the app's",
            drag.down(zone.mLeft + strip, midY, zone, SLOP, true, false, false, NO_LIMIT));
    }

    // ---- A terminal window, unchanged --------------------------------------------------------

    @Test
    public void aTerminalWindowKeepsItsReach() {
        for (float[] screen : SCREENS) {
            for (Foot foot : new Foot[] {Foot.KEYBOARD_DOWN, Foot.KEYBOARD_UP_RESIZE}) {
                BorderDrag.Zone zone = zone(screen, 12f, foot, true, false);
                assertTrue(BorderReach.reachesContent(zone));
                assertEquals(BorderDrag.SIDE_REACH_DP * DENSITY,
                    zone.inside(BorderDrag.Border.LEFT), 0.001f);
                float[] box = new float[4];
                assertTrue(zone.pillBounds(BorderDrag.Border.BOTTOM, box));
                assertEquals(BorderDrag.PILL_SWIPE_REACH_DP * DENSITY, box[3] - box[1], 0.001f);
                float midX = (zone.mLeft + zone.mRight) / 2f;
                assertEquals(SeamSwipe.Start.PANE, zone.seamAt(midX, zone.mBottom - 10f));
                // The last row's clearly sideways start pages where the terminal yields it.
                BorderDrag drag = new BorderDrag();
                assertTrue(drag.down(midX, zone.mBottom - 10f, zone, SLOP, true, true, true,
                    NO_LIMIT, false, true));
                assertTrue(drag.seamSlides());
            }
        }
    }

    @Test
    public void aTerminalWindowsBodySwipeStillPages() {
        BorderDrag.Zone zone = zone(SCREENS[0], 12f, Foot.KEYBOARD_DOWN, true, false);
        float midX = (zone.mLeft + zone.mRight) / 2f;
        float midY = (zone.mTop + zone.mBottom) / 2f;
        BorderDrag drag = new BorderDrag();
        assertTrue(drag.down(midX, midY, zone, SLOP, true, true, true, NO_LIMIT, true, false));
        assertTrue(drag.bodySlides());
    }

    private static String describe(float[] screen, float margin, Foot foot, boolean pills) {
        return (screen[0] < screen[1] ? "portrait" : "landscape") + " Margin " + margin + "dp "
            + foot + (pills ? " pills" : " no pills");
    }
}
