package com.termux.app.place;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.chrome.Dress;
import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PageRef;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A crossing (ADR 0012) driven the way the activity drives it: begun on the first frame that
 * reaches the other half, one frame per wall frame, turned mid-slide, landed and ended at the
 * settle. What is asserted is what the user sees as numbers (the dock's dress, the keyboard's
 * reveal, the cover) and, at the landing, that nothing of the crossing is left behind.
 */
public class CrossingTest {

    static final int WIDTH = 1000;
    static final float STEP = 0.02f;
    /** The steepest the look's curve climbs per unit of the wall's way (a smoothstep over SeamFade's stretch). */

    private static final float DENSITY = 2.625f;
    static final List<PageKind> ROW =
        Arrays.asList(PageKind.HOME, PageKind.TERMINAL);
    private static final WallShape SHAPE = WallShape.of(LayoutStyle.DOCKED, 12, 8);

    /** A window with the pinned apps and the extra keys along the bottom. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** Home: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    private static final ResolvedArrangement HOME_ARRANGED = ResolvedArrangement.of(HOME,
        LayoutStyle.DOCKED, DockStyle.LENS_BEADS, true, true, PlaceOrientation.PORTRAIT,
        LayoutVariant.HOME, PageRef.HOME);
    private static final ResolvedArrangement WORKSPACE_ARRANGED = ResolvedArrangement.of(WINDOW,
        LayoutStyle.DOCKED, DockStyle.GLASS_TRAY, false, false, PlaceOrientation.PORTRAIT,
        LayoutVariant.NORMAL, PageRef.TERMINAL);

    // ---- The rests, from Dock geometry ----

    private static DockGeometry.Snapshot snapshot() {
        DockGeometry.Half home = DockGeometry.Half.builder(HOME_ARRANGED).build();
        DockGeometry.Half workspace = DockGeometry.Half.builder(WORKSPACE_ARRANGED).build();
        return DockGeometry.Snapshot.builder(home, workspace)
            .density(DENSITY)
            .widthPx(Math.round(411 * DENSITY))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
            .pinnedSlotCount(7)
            .build();
    }

    static final DockLayout HOME_REST =
        DockGeometry.compute(snapshot().restOf(PageRef.HOME));
    static final DockLayout WORKSPACE_REST =
        DockGeometry.compute(snapshot().restOf(PageRef.TERMINAL));

    // ---- Dresses ----

    private static final class Stored implements DressResolver.Source {
        @Override public boolean minimal() { return false; }
        @Override public boolean homeLayout() { return true; }
        @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY;
        }
        @Override public boolean searchField(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME;
        }
        @Override public boolean drawerTile(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME;
        }
        @Override public boolean shows(@NonNull com.termux.app.page.PageKind kind,
                                       @NonNull Element element) {
            return kind != com.termux.app.page.PageKind.HOME
                || element != Element.EXTRA_KEYS;
        }
        @NonNull @Override public WallShape shape() { return SHAPE; }
    }

    static SeamCrossing.Endpoint half(@NonNull PageKind page, boolean keyboardUp) {
        return half(page, keyboardUp, false);
    }

    /** With {@code apart}, the window's stack is sized apart from Home's: a stack dressed apart. */
    private static SeamCrossing.Endpoint half(@NonNull PageKind page, boolean keyboardUp,
                                          boolean apart) {
        Dress dress = new DressResolver(new Stored()).dressOf(page);
        PlaceLayout layout = page == PageKind.HOME ? HOME : WINDOW;
        float dock = apart && page != PageKind.HOME ? 0.8f : 1f;
        ResolvedArrangement arranged = page == PageKind.HOME ? HOME_ARRANGED
            : WORKSPACE_ARRANGED;
        return SeamCrossing.Endpoint.of(PageRef.of(page), dress, null, arranged, layout,
            SeamDress.StackDress.of(dress.shape(), dock, 1f, 0), keyboardUp);
    }

    // ---- The adapter, recorded ----

    private static final class Moves implements Crossing.Stack, Crossing.Bar, Crossing.Cover,
            Crossing.Keyboard {
        float dockPx = Float.NaN, keyboardPx = Float.NaN, sinkPx = Float.NaN;
        float statusAlpha = Float.NaN, sideAlpha = Float.NaN, barX = Float.NaN;
        int coverPx = -1;
        int covers;
        final List<PageKind> preRolls = new ArrayList<>();

        @Override public void translate(float dock, float keyboard) {
            dockPx = dock;
            keyboardPx = keyboard;
        }
        @Override public void sink(float px) { sinkPx = px; }
        @Override public void alpha(float status, float side) {
            statusAlpha = status;
            sideAlpha = side;
        }
        @Override public void rideX(float px) { barX = px; }
        @Override public void cover(int px) {
            coverPx = px;
            covers++;
        }
        @Override public void preRoll(@NonNull PageKind target) { preRolls.add(target); }
    }

    static final int REVISION = 7;

    // ---- The drive ----

    private static final class Drive {
        final Moves moves = new Moves();
        final Crossing crossing = new Crossing(moves, moves, moves, moves);
        final PageKind committed;
        final SeamCrossing.Endpoint here, there;
        final PlaceChromeTravel.States states;

        Drive(@NonNull PageKind from, boolean fromKeyboard, boolean toKeyboard) {
            this(from, fromKeyboard, toKeyboard, false);
        }

        Drive(@NonNull PageKind from, boolean fromKeyboard, boolean toKeyboard,
              boolean apart) {
            committed = from;
            PageKind to = from == PageKind.HOME ? PageKind.TERMINAL
                : PageKind.HOME;
            here = half(from, fromKeyboard, apart);
            there = half(to, toKeyboard, apart);
            states = place -> new Rest(place == committed ? fromKeyboard : toKeyboard, false, true);
        }

        Frame frameAt(float fraction) {
            float offset = committed == PageKind.HOME ? -fraction * WIDTH : fraction * WIDTH;
            return PlaceChromeTravel.at(ROW, committed, offset, WIDTH, states);
        }

        SeamCrossing.Answers step(float fraction) {
            Frame frame = frameAt(fraction);
            PageKind other = SeamTravel.otherHalfPage(frame, committed);
            if (other != null && other != crossing.seam().other()) {
                boolean fromHome = committed == PageKind.HOME;
                crossing.begin(here, there, SeamCrossing.Stage.of(false, () -> false, true),
                    fromHome ? HOME_REST : WORKSPACE_REST, fromHome ? WORKSPACE_REST : HOME_REST,
                    REVISION);
            }
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            if (answers.relayout()) crossing.seam().standLayout();
            // The stack, once the frame's passes have run, as the activity applies it.
            crossing.stack(answers, answers.dockReveal(), 0, 200, 150);
            return answers;
        }

    }

    /** The dock rows' height a rest stands, less the extra keys row. */
    private static int rowsPx(DockLayout l) {
        return l.combinedHeight(0, false);
    }

    static final PageKind[] BOTH = {PageKind.TERMINAL, PageKind.HOME};

    static DockLayout restOf(PageKind page) {
        return page == PageKind.HOME ? HOME_REST : WORKSPACE_REST;
    }

    static PageKind across(PageKind page) {
        return page == PageKind.HOME ? PageKind.TERMINAL : PageKind.HOME;
    }

    @Test
    public void theTwoHalvesWearDifferentDresses() {
        assertNotEquals(rowsPx(HOME_REST), rowsPx(WORKSPACE_REST));
        assertTrue(HOME_REST.promptFieldBandPx > 0);
        assertEquals(0, WORKSPACE_REST.promptFieldBandPx);
    }

    @Test
    public void theKeyboardTravelsWithTheHalfThatHasItOpen() {
        // The window has it open and Home does not: it goes with the window, on the look's curve both
        // ways out and in.
        Drive out = new Drive(PageKind.TERMINAL, true, false);
        for (float f = STEP; f <= 1f + 1e-6f; f += STEP) {
            float fraction = Math.min(1f, f);
            SeamCrossing.Answers answers = out.step(fraction);
            assertEquals("out at " + fraction,
                1f - StackScene.materialShare(fraction),
                answers.keyboardReveal(), 1e-4f);
        }
        Drive in = new Drive(PageKind.HOME, false, true);
        for (float f = STEP; f <= 1f + 1e-6f; f += STEP) {
            float fraction = Math.min(1f, f);
            SeamCrossing.Answers answers = in.step(fraction);
            assertEquals("in at " + fraction, StackScene.materialShare(fraction),
                answers.keyboardReveal(), 1e-4f);
        }
    }

    @Test
    public void midSlideEveryReaderStandsInTheHeldArrangement() {
        Drive drive = new Drive(PageKind.TERMINAL, false, false);
        drive.step(0.3f);
        // A layout that changed under the slide is not what the chrome stands in.
        ResolvedArrangement changed = ResolvedArrangement.of(HOME, LayoutStyle.FLOATING,
            DockStyle.WALL_STRIP, false, false, PlaceOrientation.PORTRAIT, LayoutVariant.NORMAL,
            PageRef.TERMINAL);
        ResolvedArrangement standing = drive.crossing.arrangement(changed);
        assertEquals(LayoutStyle.DOCKED, standing.layoutStyle());
        assertEquals(DockStyle.GLASS_TRAY, standing.design());
        assertSame("one value for every reader of a frame", standing,
            drive.crossing.arrangement(changed));
        assertSame(HOME_ARRANGED, drive.crossing.heldArrangement(PageRef.HOME));
        assertSame(WORKSPACE_ARRANGED, drive.crossing.heldArrangement(PageRef.TERMINAL));
        // At rest the committed arrangement is the answer, and nothing is held for any reader.
        drive.crossing.land(PageRef.of(PageKind.HOME), REVISION);
        drive.crossing.end();
        assertSame(changed, drive.crossing.arrangement(changed));
        assertNull(drive.crossing.heldArrangement(PageRef.HOME));
        assertNull(drive.crossing.heldArrangement(PageRef.TERMINAL));
    }

    @Test
    public void aStoreThatMovesMidSlideIsAppliedAtTheLandingAndNotBefore() {
        for (PageKind from : BOTH) {
            Drive drive = new Drive(from, false, false);
            for (float f = STEP; f <= 0.5f; f += STEP) drive.step(f);
            // The store moves under the slide (a layout seeded, a setting written): the readers
            // keep reading what the crossing took as it began, frame after frame.
            for (float f = 0.5f; f <= 1f + 1e-6f; f += STEP) {
                drive.step(Math.min(1f, f));
                assertSame(HOME_ARRANGED, drive.crossing.heldArrangement(PageRef.HOME));
                assertSame(WORKSPACE_ARRANGED, drive.crossing.heldArrangement(PageRef.TERMINAL));
            }
            Crossing.Landing moved = drive.crossing.land(PageRef.of(across(from)), REVISION + 1);
            assertTrue(from + ": the landing applies it", moved.storeMoved());
            drive.crossing.end();
            assertNull("and the store answers from there", drive.crossing.heldArrangement(PageRef.HOME));
            // A store that stood still has nothing for the landing to apply.
            Drive still = new Drive(from, false, false);
            for (float f = STEP; f <= 1f + 1e-6f; f += STEP) still.step(Math.min(1f, f));
            assertFalse(still.crossing.land(PageRef.of(across(from)), REVISION).storeMoved());
        }
    }

    @Test
    public void landLeavesNoCrossingState() {
        for (PageKind from : BOTH) {
            Drive drive = new Drive(from, from == PageKind.TERMINAL, false);
            Crossing crossing = drive.crossing;
            Moves moves = drive.moves;
            assertTrue("idle before", crossing.idle());
            // Everything a slide takes: the hold, a keyboard and the content pre-rolled, the band
            // the chrome's edge rises through and its cover, a fold and a melt, the seam's stand.
            crossing.preRollKeyboard(across(from), 640);
            assertTrue(crossing.giveRoomBack(120, 0));
            crossing.heldOverlap(300);
            for (float f = STEP; f <= 0.6f; f += STEP) drive.step(f);
            crossing.coverAt(40f);
            crossing.beadShare(0.6f);
            crossing.roomChange(across(from), 120);
            assertEquals(Arrays.asList(across(from)), moves.preRolls);
            assertEquals(260, moves.coverPx);
            assertTrue(crossing.holdsContent());
            assertEquals(520, crossing.heldReservationPx());
            assertNotNull(crossing.heldRest(PageRef.HOME));
            assertFalse(crossing.idle());

            Crossing.Landing landing = crossing.land(PageRef.of(across(from)), REVISION);
            assertTrue(landing.held());
            assertTrue(landing.keyboardPreRolled());
            assertTrue(landing.contentPreRolled());
            assertTrue(landing.beadsMelted());
            assertFalse(landing.storeMoved());
            // In the one step: the cover off the window in front, the stack, its rows and the bar
            // back at rest.
            assertEquals(0, moves.coverPx);
            assertEquals(0f, moves.dockPx, 0f);
            assertEquals(0f, moves.keyboardPx, 0f);
            assertEquals(0f, moves.sinkPx, 0f);
            assertEquals(1f, moves.statusAlpha, 0f);
            assertEquals(1f, moves.sideAlpha, 0f);
            assertEquals(0f, moves.barX, 0f);
            assertNull(crossing.heldRest(PageRef.HOME));
            assertNull(crossing.heldRest(PageRef.TERMINAL));
            assertNull(crossing.heldArrangement(PageRef.HOME));
            assertTrue(Float.isNaN(crossing.beadShare()));
            assertFalse(crossing.holdsContent());
            assertFalse(crossing.keyboardPreRolled());
            assertNull(crossing.keyboardPlace());
            assertFalse(crossing.contentPreRolled());
            assertFalse(crossing.crossing());
            assertNull(crossing.seam().layout());
            // The page's commit reads the stack's dress, which goes with the crossing's end.
            crossing.end();
            assertTrue(from + ": nothing left", crossing.idle());
        }
    }

    @Test
    public void aKeyboardSwipeLetsTheHoldGoInOneStep() {
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves);
        crossing.preRollKeyboard(PageKind.TERMINAL, 500);
        crossing.heldOverlap(200);
        // The swipe's frame goes through the crossing: the stack, all of it the keyboard's, and
        // the cover under it.
        crossing.keyboardSwipe(50f);
        assertEquals(50f, moves.dockPx, 0f);
        assertEquals(50f, moves.keyboardPx, 0f);
        assertEquals(150, moves.coverPx);
        Crossing.Landing landing = crossing.releaseHold();
        assertTrue(landing.held());
        assertTrue(landing.keyboardPreRolled());
        assertEquals(0, moves.coverPx);
        assertEquals(0f, moves.dockPx, 0f);
        assertTrue(crossing.idle());
    }

    @Test
    public void aHoldLetGoOnItsOwnHandsBackNothingOfAnEarlierSeam() {
        // A crossing lands and ends...
        Drive drive = new Drive(PageKind.HOME, false, false);
        for (float f = STEP; f <= 1f + 1e-6f; f += STEP) drive.step(Math.min(1f, f));
        drive.crossing.land(PageRef.of(PageKind.TERMINAL), REVISION);
        drive.crossing.end();
        // ...and a keyboard swipe after it lets its hold go with nothing for the seam to do.
        drive.crossing.preRollKeyboard(PageKind.TERMINAL, 500);
        Crossing.Landing released = drive.crossing.releaseHold();
        assertFalse(released.seam().laidOut());
        assertFalse(released.seam().bandsMoved());
        assertFalse(released.seam().barTravelled());
        assertFalse(released.seam().redressesBack(PageKind.HOME));
        assertFalse(released.storeMoved());
    }

    /**
     * A crossing gives nothing back and takes nothing: no page's room is written by it (each
     * is its own rest's, PageRoom), so a landing has no pre-size to put back and the crossing is
     * idle once it ends.
     */
    @Test
    public void aCrossingLeavesNothingOfTheRoomToPutBackAtTheLanding() {
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves);
        assertFalse(crossing.holdsContent());
        crossing.land(PageRef.of(PageKind.TERMINAL), REVISION);
        crossing.end();
        assertTrue(crossing.idle());
    }


    @Test
    public void theCoverIsOnlyEverAppliedUnderAHold() {
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves);
        crossing.heldOverlap(200);
        crossing.coverAt(0f);
        assertEquals(-1, moves.coverPx);
        // The overlap noted before the hold was not kept: under the hold, nothing is covered.
        crossing.hold(0);
        crossing.coverAt(0f);
        assertEquals(0, moves.coverPx);
    }

    // ---- Between two Home pages (#173 slice E) ----

    private static final PageRef HOME_2 = PageRef.of(PageKind.HOME, 2);
    private static final PageRef HOME_3 = PageRef.of(PageKind.HOME, 3);
    /** Home page 3's own layout: Home's, with the index shown beside the pinned apps. */
    private static final PlaceLayout HOME_WITH_INDEX = new PlaceLayout(Edge.TOP,
        RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.HIDDEN,
        PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    private static final ResolvedArrangement HOME_2_ARRANGED = ResolvedArrangement.of(HOME,
        LayoutStyle.DOCKED, DockStyle.GLASS_TRAY, false, false, PlaceOrientation.PORTRAIT,
        LayoutVariant.NORMAL, HOME_2);
    private static final ResolvedArrangement HOME_3_ARRANGED = ResolvedArrangement.of(
        HOME_WITH_INDEX, LayoutStyle.DOCKED, DockStyle.GLASS_TRAY, false, false,
        PlaceOrientation.PORTRAIT, LayoutVariant.page(3,
            com.termux.app.page.PageKind.HOME), HOME_3);

    /** One snapshot holding both Home pages' halves, the other in {@code otherHome}. */
    private static DockGeometry.Snapshot twoHomes() {
        return DockGeometry.Snapshot.builder(DockGeometry.Half.builder(HOME_2_ARRANGED).build(),
                DockGeometry.Half.builder(WORKSPACE_ARRANGED).build())
            .otherHome(DockGeometry.Half.builder(HOME_3_ARRANGED).build())
            .density(DENSITY)
            .widthPx(Math.round(411 * DENSITY))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
            .pinnedSlotCount(7)
            .build();
    }

    @Test
    public void aSnapshotAnswersEachOfTwoHomePagesWithItsOwnHalf() {
        DockGeometry.Snapshot rests = twoHomes();
        assertSame(HOME_2_ARRANGED, rests.half(HOME_2).arrangement);
        assertSame(HOME_3_ARRANGED, rests.half(HOME_3).arrangement);
        assertSame("a Home page with no id said reads the one in front", HOME_2_ARRANGED,
            rests.half(PageRef.HOME).arrangement);
        assertSame(WORKSPACE_ARRANGED, rests.half(PageRef.TERMINAL).arrangement);
        // Without a second half, every Home page reads the one Home half, as before.
        assertSame(HOME_ARRANGED, snapshot().half(HOME_3).arrangement);
        assertNotEquals(rowsPx(DockGeometry.compute(rests.restOf(HOME_2))),
            rowsPx(DockGeometry.compute(rests.restOf(HOME_3))));
    }

    /**
     * A slide on Home's pager between page 2 and page 3, whose own layout adds the index: the
     * crossing holds both pages' arrangements and rests, a reader asking for Home by kind reads
     * the committed page, and the landing lets them go.
     */
    @Test
    public void aSlideBetweenTwoHomePagesHoldsBothPagesRests() {
        DockGeometry.Snapshot rests = twoHomes();
        DockLayout rest2 = DockGeometry.compute(rests.restOf(HOME_2));
        DockLayout rest3 = DockGeometry.compute(rests.restOf(HOME_3));
        Dress dress = new DressResolver(new Stored()).dressOf(PageKind.HOME);
        SeamDress.StackDress stack = SeamDress.StackDress.of(dress.shape(), 1f, 1f, 0);
        SeamCrossing.Endpoint here = SeamCrossing.Endpoint.of(HOME_2, dress, null,
            HOME_2_ARRANGED, HOME, stack, false);
        SeamCrossing.Endpoint there = SeamCrossing.Endpoint.of(HOME_3, dress, null,
            HOME_3_ARRANGED, HOME_WITH_INDEX, stack, false);
        // Pages whose bottom rows differ and both stand rows plan no slide (nothing is laid out
        // on the way), yet they still cross: the rows swap at the landing.
        assertNotEquals(EdgeStackPolicy.stack(HOME, Edge.BOTTOM),
            EdgeStackPolicy.stack(HOME_WITH_INDEX, Edge.BOTTOM));
        assertSame(SeamTravel.DockPlan.NONE, SeamTravel.dockPlan(HOME, HOME_WITH_INDEX));
        assertTrue(SeamCrossing.crosses(here, there));
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves);
        Rest home = new Rest(false, false, true);
        crossing.begin(here, there, SeamCrossing.Stage.of(false, () -> false, true), rest2,
            rest3, REVISION);
        for (float fraction = STEP; fraction < 1f; fraction += STEP) {
            Frame frame = PlaceChromeTravel.homePages(home, 2, 3, -fraction * WIDTH, WIDTH);
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            assertFalse("the rows swap at the landing, not at " + fraction, answers.relayout());
            assertFalse(answers.restyle());
            assertEquals("the rows stay whole at " + fraction, 1f, answers.dockReveal(), 1e-4f);
        }
        assertSame(HOME_2_ARRANGED, crossing.heldArrangement(HOME_2));
        assertSame(HOME_3_ARRANGED, crossing.heldArrangement(HOME_3));
        assertSame("Home asked by kind alone is the committed page", HOME_2_ARRANGED,
            crossing.heldArrangement(PageRef.HOME));
        assertSame(rest3, crossing.heldRest(HOME_3));
        assertSame(rest2, crossing.heldRest(PageRef.HOME));
        assertNull("a kind neither end has is the store's to answer",
            crossing.heldArrangement(PageRef.TERMINAL));
        assertNull(crossing.heldRest(PageRef.TERMINAL));
        assertNull(crossing.heldArrangement(PageRef.of(PageKind.DISPLAY, 7)));
        assertEquals("a Home reader by kind is the committed page, whatever the pager committed to",
            HOME_2, crossing.heldCommittedPage(PageKind.HOME));
        assertNull("no Workspace end is held", crossing.heldCommittedPage(PageKind.TERMINAL));
        Crossing.Landing landed = crossing.land(HOME_3, REVISION);
        assertFalse("the arriving page's rows are laid out by the landing's own pass",
            landed.seam().laidOut());
        assertNull("landed, the store answers", crossing.heldCommittedPage(PageKind.HOME));
        crossing.end();
        assertTrue(crossing.idle());
    }

    @Test
    public void twoHomePagesThatDressAlikeAreNoCrossing() {
        Dress dress = new DressResolver(new Stored()).dressOf(PageKind.HOME);
        SeamDress.StackDress stack = SeamDress.StackDress.of(dress.shape(), 1f, 1f, 0);
        SeamCrossing.Endpoint here = SeamCrossing.Endpoint.of(HOME_2, dress, null,
            HOME_2_ARRANGED, HOME, stack, false);
        SeamCrossing.Endpoint alike = SeamCrossing.Endpoint.of(HOME_3, dress, null,
            HOME_2_ARRANGED, HOME, stack, false);
        assertFalse(SeamCrossing.crosses(here, alike));
        assertFalse("one page with itself", SeamCrossing.crosses(here, here));
    }
}
