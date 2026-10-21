package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The seam band between a window and the dock, a fingertip tall: the border's air, the window's
 * last row above it and the dock's top below it. A finger there decides at the touch slop: from
 * the air anything flatter than vertical pages, from the last row and the dock's top only a
 * clearly sideways start, and a tap or a still finger is never the band's off the air. A swipe
 * that starts clearly inside the terminal or the dock's apps row is the content's.
 */
public class SeamSwipeTest {

    private static final float DENSITY = 3f;
    // The window as the wall lays it out, at 3x: Margin 12 dp of air down to the dock's top.
    private static final float LEFT = 100f;
    private static final float TOP = 200f;
    private static final float RIGHT = 1000f;
    private static final float BOTTOM = 1800f;
    private static final float AIR = 36f;              // 12 dp, the default Margin
    private static final float DOCK_TOP = BOTTOM + AIR;
    private static final float CORNER = 120f;          // 40 dp
    private static final float SLOP = 24f;             // 8 dp
    private static final float MID_X = 550f;
    private static final float PANE_REACH = 72f;       // PANE_REACH_DP at 3x
    private static final float DOCK_REACH = 24f;       // DOCK_REACH_DP at 3x
    private static final float ROW = 48f;              // a terminal row, 16 dp
    private static final float NO_LIMIT = Float.NEGATIVE_INFINITY;

    /** A swipe of {@code length} px set off {@code degrees} off the horizontal, to the right. */
    private static float[] swipe(float length, double degrees) {
        double rad = Math.toRadians(degrees);
        return new float[] {(float) (length * Math.cos(rad)), (float) (length * Math.sin(rad))};
    }

    private static SeamSwipe.Start at(float x, float y) {
        return SeamSwipe.startAt(x, y, LEFT, RIGHT, CORNER, BOTTOM, DOCK_TOP, PANE_REACH,
            DOCK_REACH, true);
    }

    private static BorderDrag.Zone zone() {
        return new BorderDrag.Zone().page(LEFT, TOP, RIGHT, BOTTOM).gaps(AIR, AIR, AIR, AIR)
            .minBand(18f).sideReach(6f).corner(CORNER)
            .pills(MID_X, 360f, 48f)
            .seam(DOCK_TOP, SeamSwipe.paneReachPx(AIR, DENSITY), SeamSwipe.dockReachPx(DENSITY));
    }

    /** The wall's own press, with every swipe on, the terminal there giving sideways away. */
    private static BorderDrag pressed(float x, float y, boolean seamYields) {
        BorderDrag drag = new BorderDrag();
        drag.down(x, y, zone(), SLOP, true, true, true, NO_LIMIT, false, seamYields);
        return drag;
    }

    private static BorderDrag onDock(float x, float y) {
        BorderDrag drag = new BorderDrag();
        drag.downOnDock(x, y, zone(), SLOP, true);
        return drag;
    }

    // ---- The band ---------------------------------------------------------------------------

    @Test
    public void theBandIsAFingertipTallAtEveryMargin() {
        assertEquals(PANE_REACH, SeamSwipe.paneReachPx(AIR, DENSITY), 0.001f);
        assertEquals(DOCK_REACH, SeamSwipe.dockReachPx(DENSITY), 0.001f);
        float band = SeamSwipe.BAND_DP * DENSITY;
        assertEquals("at the default Margin", band,
            SeamSwipe.paneReachPx(AIR, DENSITY) + AIR + SeamSwipe.dockReachPx(DENSITY), 0.001f);
        float thinAir = 18f; // Margin 6 dp
        assertEquals("a thin Margin grows the last row's reach, never the dock's", band,
            SeamSwipe.paneReachPx(thinAir, DENSITY) + thinAir
                + SeamSwipe.dockReachPx(DENSITY), 0.001f);
        float wideAir = 90f; // Margin 30 dp
        assertEquals("a wide Margin keeps the least reach", PANE_REACH,
            SeamSwipe.paneReachPx(wideAir, DENSITY), 0.001f);
    }

    @Test
    public void theBandRunsFromTheLastRowThroughTheAirIntoTheDocksTop() {
        assertEquals("on the seam's line", SeamSwipe.Start.AIR, at(MID_X, BOTTOM + AIR / 2f));
        assertEquals(SeamSwipe.Start.AIR, at(MID_X, BOTTOM));
        assertEquals("one row into the window", SeamSwipe.Start.PANE, at(MID_X, BOTTOM - ROW));
        assertEquals(SeamSwipe.Start.PANE, at(MID_X, BOTTOM - PANE_REACH));
        assertEquals("clearly inside the terminal", SeamSwipe.Start.NONE,
            at(MID_X, BOTTOM - PANE_REACH - 1f));
        assertEquals(SeamSwipe.Start.NONE, at(MID_X, BOTTOM - 3f * ROW));
        assertEquals("the dock's top air", SeamSwipe.Start.DOCK, at(MID_X, DOCK_TOP + 12f));
        assertEquals(SeamSwipe.Start.DOCK, at(MID_X, DOCK_TOP));
        assertEquals("clearly inside the apps row", SeamSwipe.Start.NONE,
            at(MID_X, DOCK_TOP + DOCK_REACH));
        assertEquals(SeamSwipe.Start.NONE, at(MID_X, DOCK_TOP + 84f));
    }

    @Test
    public void theCornerSquaresAndAMissingDockAreNotTheBand() {
        assertEquals(SeamSwipe.Start.NONE, at(LEFT + CORNER - 1f, BOTTOM + 10f));
        assertEquals(SeamSwipe.Start.NONE, at(RIGHT - CORNER, BOTTOM - ROW));
        assertEquals(SeamSwipe.Start.AIR, at(LEFT + CORNER, BOTTOM + 10f));
        assertEquals("no dock below: no dock's part", SeamSwipe.Start.NONE,
            SeamSwipe.startAt(MID_X, DOCK_TOP + 6f, LEFT, RIGHT, CORNER, BOTTOM, DOCK_TOP,
                PANE_REACH, DOCK_REACH, false));
        assertEquals("a dock over the window: no band", SeamSwipe.Start.NONE,
            SeamSwipe.startAt(MID_X, BOTTOM - 6f, LEFT, RIGHT, CORNER, BOTTOM, BOTTOM - 30f,
                PANE_REACH, DOCK_REACH, true));
    }

    // ---- The decision -----------------------------------------------------------------------

    @Test
    public void aTapIsUndecidedEverywhere() {
        for (SeamSwipe.Start start : SeamSwipe.Start.values()) {
            assertEquals(start.name(), SeamSwipe.Decision.UNDECIDED,
                SeamSwipe.decide(start, 10f, 8f, SLOP, true, true));
        }
    }

    @Test
    public void aSidewaysSwipeOnTheSeamPagesFromAllThreeParts() {
        float[] flat = swipe(SLOP + 6f, 0);
        for (SeamSwipe.Start start : new SeamSwipe.Start[] {SeamSwipe.Start.AIR,
            SeamSwipe.Start.PANE, SeamSwipe.Start.DOCK}) {
            assertEquals(start.name(), SeamSwipe.Decision.BORDER_DRAG,
                SeamSwipe.decide(start, flat[0], flat[1], SLOP, true, false));
            assertEquals(start.name() + " to the left", SeamSwipe.Decision.BORDER_DRAG,
                SeamSwipe.decide(start, -flat[0], flat[1], SLOP, true, false));
        }
    }

    @Test
    public void aDiagonalOf25DegreesStillPages() {
        float[] diagonal = swipe(SLOP + 6f, 25);
        for (SeamSwipe.Start start : new SeamSwipe.Start[] {SeamSwipe.Start.AIR,
            SeamSwipe.Start.PANE, SeamSwipe.Start.DOCK}) {
            assertEquals(start.name(), SeamSwipe.Decision.BORDER_DRAG,
                SeamSwipe.decide(start, diagonal[0], -diagonal[1], SLOP, true, false));
        }
    }

    @Test
    public void aSteeperStartIsTheContentsOffTheAirAndTheKeyboardsInIt() {
        float[] steep = swipe(SLOP + 6f, 40);
        assertEquals("the air keeps its 45 degree split", SeamSwipe.Decision.BORDER_DRAG,
            SeamSwipe.decide(SeamSwipe.Start.AIR, steep[0], steep[1], SLOP, true, true));
        assertEquals("the terminal's scroll", SeamSwipe.Decision.CONTENT,
            SeamSwipe.decide(SeamSwipe.Start.PANE, steep[0], steep[1], SLOP, true, false));
        assertEquals("the dock's own", SeamSwipe.Decision.CONTENT,
            SeamSwipe.decide(SeamSwipe.Start.DOCK, steep[0], steep[1], SLOP, true, false));
        assertEquals(SeamSwipe.Decision.KEYBOARD,
            SeamSwipe.decide(SeamSwipe.Start.AIR, 0f, -SLOP - 6f, SLOP, true, true));
        assertEquals("by the pill", SeamSwipe.Decision.KEYBOARD,
            SeamSwipe.decide(SeamSwipe.Start.PANE, 0f, -SLOP - 6f, SLOP, true, true));
        assertEquals(SeamSwipe.Decision.CONTENT,
            SeamSwipe.decide(SeamSwipe.Start.DOCK, 0f, -SLOP - 6f, SLOP, true, false));
    }

    @Test
    public void aSwipeClearlyInsideTheTerminalOrTheAppsRowIsTheContents() {
        assertEquals(SeamSwipe.Decision.CONTENT,
            SeamSwipe.decide(at(MID_X, BOTTOM - 3f * ROW), SLOP + 6f, 0f, SLOP, true, true));
        assertEquals(SeamSwipe.Decision.CONTENT,
            SeamSwipe.decide(at(MID_X, DOCK_TOP + 84f), SLOP + 6f, 0f, SLOP, true, true));
    }

    @Test
    public void aTerminalThatKeepsTheDragKeepsItOnTheLastRow() {
        assertEquals("selecting, or a program tracking the mouse", SeamSwipe.Decision.CONTENT,
            SeamSwipe.decide(SeamSwipe.Start.PANE, SLOP + 6f, 0f, SLOP, false, false));
    }

    @Test
    public void aLongPressPagesOnlyFromTheAir() {
        assertEquals(SeamSwipe.Decision.BORDER_DRAG, SeamSwipe.hold(SeamSwipe.Start.AIR, true));
        assertEquals("the terminal's long press", SeamSwipe.Decision.CONTENT,
            SeamSwipe.hold(SeamSwipe.Start.PANE, true));
        assertEquals("an app's menu", SeamSwipe.Decision.CONTENT,
            SeamSwipe.hold(SeamSwipe.Start.DOCK, true));
        assertEquals(SeamSwipe.Decision.CONTENT, SeamSwipe.hold(SeamSwipe.Start.AIR, false));
    }

    // ---- The border drag on the band -----------------------------------------------------------

    @Test
    public void aDiagonalFromTheLastRowPagesWhereTheTerminalGivesItAway() {
        float y = BOTTOM - ROW;
        BorderDrag drag = pressed(MID_X - 200f, y, true);
        assertTrue("armed on the last row", drag.isArmed());
        assertEquals(SeamSwipe.Start.PANE, drag.seamStart());
        float[] diagonal = swipe(SLOP + 6f, 25);
        assertEquals(BorderDrag.Claim.PAGING,
            drag.move(MID_X - 200f + diagonal[0], y - diagonal[1]));
        assertEquals("the travel the claim left out", diagonal[0], drag.claimLead(), 0.001f);

        BorderDrag kept = pressed(MID_X - 200f, y, false);
        assertFalse("a terminal keeping the drag leaves the row alone", kept.isArmed());
    }

    @Test
    public void aStillFingerOrAScrollOnTheLastRowIsTheTerminals() {
        float x = MID_X - 200f; // outside the pill's box
        BorderDrag held = pressed(x, BOTTOM - ROW, true);
        assertFalse("the hold never pages from the text", held.holdElapsed());
        assertEquals(BorderDrag.Claim.ABANDONED, held.claim());

        BorderDrag scroll = pressed(x, BOTTOM - ROW, true);
        assertEquals(BorderDrag.Claim.ABANDONED, scroll.move(x + 5f, BOTTOM - ROW - SLOP - 6f));

        BorderDrag tap = pressed(x, BOTTOM - ROW, true);
        assertEquals("a tap moves nothing", BorderDrag.Claim.PENDING, tap.move(x + 4f, BOTTOM - ROW));
        tap.reset();
        assertEquals(BorderDrag.Claim.NONE, tap.claim());
    }

    @Test
    public void theKeyboardSwipeByThePillStillStartsOnTheLastRow() {
        BorderDrag drag = pressed(MID_X, BOTTOM - 20f, true);
        assertEquals(BorderDrag.Claim.KEYBOARD, drag.move(MID_X, BOTTOM - 20f - SLOP - 6f));
    }

    @Test
    public void theAirKeepsItsRuleAndCountsTheClaimsLead() {
        float y = BOTTOM + AIR / 2f;
        BorderDrag drag = pressed(MID_X, y, false);
        float[] steep = swipe(SLOP + 6f, 40);
        assertEquals(BorderDrag.Claim.PAGING, drag.move(MID_X - steep[0], y - steep[1]));
        assertEquals(-steep[0], drag.claimLead(), 0.001f);

        BorderDrag held = pressed(MID_X, y, false);
        assertTrue("a hold on the air still pages", held.holdElapsed());
        assertEquals("a hold leaves nothing out", 0f, held.claimLead(), 0f);
    }

    @Test
    public void theWallsOwnPressNeverTakesTheDocksTop() {
        BorderDrag drag = pressed(MID_X, DOCK_TOP + 12f, true);
        assertFalse(drag.isArmed());
    }

    @Test
    public void aSidewaysSwipeFromTheDocksTopPages() {
        BorderDrag drag = onDock(MID_X, DOCK_TOP + 12f);
        assertTrue(drag.isArmed());
        assertEquals(BorderDrag.Border.BOTTOM, drag.border());
        float[] diagonal = swipe(SLOP + 6f, 25);
        assertEquals(BorderDrag.Claim.PAGING,
            drag.move(MID_X + diagonal[0], DOCK_TOP + 12f - diagonal[1]));
        assertEquals("dragged from the claim, so the wall does not jump by the slop",
            -diagonal[0], drag.travel(MID_X), 0.001f);
        assertEquals(diagonal[0], drag.claimLead(), 0.001f);
    }

    @Test
    public void theDocksTapLongPressAndOwnSwipesStayTheDocks() {
        float y = DOCK_TOP + 12f;
        BorderDrag tap = onDock(MID_X, y);
        assertEquals(BorderDrag.Claim.PENDING, tap.move(MID_X + 3f, y + 2f));

        BorderDrag held = onDock(MID_X, y);
        assertFalse("the hold never pages from the dock", held.holdElapsed());
        assertEquals(BorderDrag.Claim.ABANDONED, held.claim());

        BorderDrag up = onDock(MID_X, y);
        assertEquals("the dock's swipe up", BorderDrag.Claim.ABANDONED,
            up.move(MID_X, y - SLOP - 6f));

        BorderDrag steep = onDock(MID_X, y);
        float[] s = swipe(SLOP + 6f, 40);
        assertEquals(BorderDrag.Claim.ABANDONED, steep.move(MID_X + s[0], y + s[1]));

        BorderDrag pinch = onDock(MID_X, y);
        assertEquals(BorderDrag.Claim.ABANDONED, pinch.secondPointer());
    }

    @Test
    public void aSwipeClearlyInsideTheAppsRowIsNeverArmed() {
        assertFalse(new BorderDrag().downOnDock(MID_X, DOCK_TOP + DOCK_REACH, zone(), SLOP, true));
        assertFalse(new BorderDrag().downOnDock(MID_X, DOCK_TOP + 84f, zone(), SLOP, true));
        assertFalse("nowhere to page to",
            new BorderDrag().downOnDock(MID_X, DOCK_TOP + 6f, zone(), SLOP, false));
        assertFalse("the corner tabs'",
            new BorderDrag().downOnDock(LEFT + 10f, DOCK_TOP + 6f, zone(), SLOP, true));
        BorderDrag.Zone noSeam = zone().clearSeam();
        assertFalse("no band set",
            new BorderDrag().downOnDock(MID_X, DOCK_TOP + 6f, noSeam, SLOP, true));
    }

    // ---- The flick ------------------------------------------------------------------------------

    @Test
    public void aShortFastFlickCountsTheTravelTheClaimLeftOut() {
        int width = 1080;
        float fast = -(PaneWallPolicy.DRAG_COMMIT_VELOCITY_DP_PER_SEC * DENSITY + 1f);
        // 20 dp of wall travel after an 8 dp claim: a 28 dp flick of the finger.
        float offset = -60f;
        assertEquals("without the lead it springs back", 0,
            PaneWallPolicy.settle(offset, fast, width, DENSITY, true, true));
        assertEquals(1, PaneWallPolicy.settle(offset, fast, width, DENSITY, true, true, -SLOP));
        assertEquals("a lead the other way is no flick", 0,
            PaneWallPolicy.settle(offset, fast, width, DENSITY, true, true, SLOP));
    }
}
