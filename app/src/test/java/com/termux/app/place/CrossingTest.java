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
import com.termux.app.dock.DockMorph;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallPage;
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
    private static final float MAX_CURVE_SLOPE = 1.5f / (SeamFade.END - SeamFade.START);

    private static final float DENSITY = 2.625f;
    static final List<PaneWallPage> ROW =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);
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
        @Override public boolean shows(@NonNull com.termux.app.wall.WallPages.PageKind kind,
                                       @NonNull Element element) {
            return kind != com.termux.app.wall.WallPages.PageKind.HOME
                || element != Element.EXTRA_KEYS;
        }
        @NonNull @Override public WallShape shape() { return SHAPE; }
    }

    static SeamCrossing.Endpoint half(@NonNull PaneWallPage page, boolean keyboardUp) {
        return half(page, keyboardUp, false);
    }

    /** With {@code apart}, the window's stack is sized apart from Home's: a whole-stack dip. */
    private static SeamCrossing.Endpoint half(@NonNull PaneWallPage page, boolean keyboardUp,
                                          boolean apart) {
        Dress dress = new DressResolver(new Stored()).dressOf(page);
        PlaceLayout layout = page == PaneWallPage.WIDGETS ? HOME : WINDOW;
        float dock = apart && page != PaneWallPage.WIDGETS ? 0.8f : 1f;
        ResolvedArrangement arranged = page == PaneWallPage.WIDGETS ? HOME_ARRANGED
            : WORKSPACE_ARRANGED;
        return SeamCrossing.Endpoint.of(PageRef.of(page), dress, null, arranged, layout,
            SeamDress.StackDress.of(dress.shape(), dock, 1f, 0), keyboardUp, -1);
    }

    // ---- The adapter, recorded ----

    private static final class Moves implements Crossing.Stack, Crossing.Bar, Crossing.Cover,
            Crossing.Keyboard, Crossing.Dock {
        float dockPx = Float.NaN, keyboardPx = Float.NaN, sinkPx = Float.NaN;
        float statusAlpha = Float.NaN, sideAlpha = Float.NaN, barX = Float.NaN;
        int coverPx = -1;
        int folds;
        // The picture of the committed half's stack, and the terminal page's pre-size.
        boolean pictureTaken = true;
        int pictures, pictureDrops;
        float pictureReveal = Float.NaN, pictureSlide = Float.NaN, slide = Float.NaN;
        int covers;
        final List<PaneWallPage> preRolls = new ArrayList<>();
        // The row: told both rests once, dressed every frame, laid out once at the landing.
        int morphBegins, dresses, morphEnds;
        DockLayout leaving, arriving, landed;
        DockMorph dressed;

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
        @Override public void preRoll(@NonNull PaneWallPage target) { preRolls.add(target); }
        @Override public void beginMorph(@NonNull DockLayout from, @NonNull DockLayout to) {
            morphBegins++;
            leaving = from;
            arriving = to;
        }
        @Override public void dress(@NonNull DockMorph morph) {
            dresses++;
            assertTrue("the same morph every frame", dressed == null || dressed == morph);
            dressed = morph;
        }
        @Override public void endMorph(@NonNull DockLayout rest) {
            morphEnds++;
            landed = rest;
        }
    }

    static final int REVISION = 7;

    // ---- The drive ----

    private static final class Drive {
        final Moves moves = new Moves();
        final Crossing crossing = new Crossing(moves, moves, moves, moves, moves);
        final PaneWallPage committed;
        final SeamCrossing.Endpoint here, there;
        final PlaceChromeTravel.States states;

        Drive(@NonNull PaneWallPage from, boolean fromKeyboard, boolean toKeyboard) {
            this(from, fromKeyboard, toKeyboard, false);
        }

        Drive(@NonNull PaneWallPage from, boolean fromKeyboard, boolean toKeyboard,
              boolean apart) {
            committed = from;
            PaneWallPage to = from == PaneWallPage.WIDGETS ? PaneWallPage.TERMINAL
                : PaneWallPage.WIDGETS;
            here = half(from, fromKeyboard, apart);
            there = half(to, toKeyboard, apart);
            states = place -> new Rest(place == committed ? fromKeyboard : toKeyboard, false, true);
        }

        Frame frameAt(float fraction) {
            float offset = committed == PaneWallPage.WIDGETS ? -fraction * WIDTH : fraction * WIDTH;
            return PlaceChromeTravel.at(ROW, committed, offset, WIDTH, states);
        }

        SeamCrossing.Answers step(float fraction) {
            Frame frame = frameAt(fraction);
            PaneWallPage other = SeamTravel.otherHalfPage(frame, committed);
            if (other != null && other != crossing.seam().other()) {
                boolean fromHome = committed == PaneWallPage.WIDGETS;
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

        /** The dress the row was handed for the last frame. */
        int[] dress() {
            DockMorph m = moves.dressed;
            return new int[] {m.slotIconPx(), m.appsRowUsableWidthPx(), m.appsBarHeightPx(),
                m.promptFieldBandPx(), m.rowsPx()};
        }
    }

    private static int[] dressOf(DockLayout l) {
        return new int[] {l.slotIconPx, l.appsRowUsableWidthPx, l.appsBarHeightPx,
            l.promptFieldBandPx, DockMorph.rowsPx(l)};
    }

    static final PaneWallPage[] BOTH = {PaneWallPage.TERMINAL, PaneWallPage.WIDGETS};

    static DockLayout restOf(PaneWallPage page) {
        return page == PaneWallPage.WIDGETS ? HOME_REST : WORKSPACE_REST;
    }

    static PaneWallPage across(PaneWallPage page) {
        return page == PaneWallPage.WIDGETS ? PaneWallPage.TERMINAL : PaneWallPage.WIDGETS;
    }

    @Test
    public void theTwoHalvesWearDifferentDresses() {
        assertNotEquals(DockMorph.rowsPx(HOME_REST), DockMorph.rowsPx(WORKSPACE_REST));
        assertTrue(HOME_REST.promptFieldBandPx > 0);
        assertEquals(0, WORKSPACE_REST.promptFieldBandPx);
    }

    @Test
    public void theDressIsTheLeavingRestAtTheStartAndTheArrivingRestAtTheEnd() {
        for (PaneWallPage from : BOTH) {
            Drive drive = new Drive(from, false, false);
            drive.step(STEP);
            assertTrue(from + ": the crossing began", drive.crossing.crossing());
            assertArrayEquals(from + ": first frame", dressOf(restOf(from)), drive.dress());
            drive.step(SeamFade.END);
            assertArrayEquals(from + ": where the chrome lands", dressOf(restOf(across(from))),
                drive.dress());
            drive.step(1f);
            assertArrayEquals(from + ": the wall's last frame", dressOf(restOf(across(from))),
                drive.dress());
        }
    }

    @Test
    public void theDressIsMonotonicOverASlowDrag() {
        for (PaneWallPage from : BOTH) {
            Drive drive = new Drive(from, false, false);
            int[] start = dressOf(restOf(from));
            int[] end = dressOf(restOf(across(from)));
            drive.step(STEP);
            int[] last = drive.dress();
            for (float f = 2 * STEP; f <= 1f + 1e-6f; f += STEP) {
                drive.step(Math.min(1f, f));
                int[] now = drive.dress();
                for (int i = 0; i < now.length; i++) {
                    int moved = Integer.signum(now[i] - last[i]);
                    assertTrue(from + " measurement " + i + " at " + f,
                        moved == 0 || moved == Integer.signum(end[i] - start[i]));
                }
                last = now;
            }
        }
    }

    @Test
    public void aReversalReversesTheDress() {
        for (PaneWallPage from : BOTH) {
            Drive drive = new Drive(from, false, false);
            int frames = 30;
            int[][] out = new int[frames + 1][];
            for (int i = 1; i <= frames; i++) {
                drive.step(i * STEP);
                out[i] = drive.dress();
            }
            for (int i = frames; i >= 1; i--) {
                drive.step(i * STEP);
                assertArrayEquals(from + " frame " + i + " back", out[i], drive.dress());
            }
        }
    }

    @Test
    public void aRetargetMidSlideNeverMovesAMeasurementByMoreThanAFrame() {
        for (PaneWallPage from : BOTH) {
            Drive drive = new Drive(from, false, false);
            int[] start = dressOf(restOf(from));
            int[] end = dressOf(restOf(across(from)));
            // The most one frame of STEP may move a measurement: its share of the chrome's way.
            int[] bound = new int[start.length];
            for (int i = 0; i < bound.length; i++)
                bound[i] = (int) Math.ceil(Math.abs(end[i] - start[i]) * STEP
                    * MAX_CURVE_SLOPE) + 1;
            List<Float> way = new ArrayList<>();
            for (int i = 1; i <= 25; i++) way.add(i * STEP);
            // A chip tapped mid-slide: the wall turns back toward the page it left...
            int turn = way.size();
            for (int i = 24; i >= 10; i--) way.add(i * STEP);
            // ...and the person changes their mind again, through to the other half.
            int again = way.size();
            for (int i = 11; i <= 50; i++) way.add(i * STEP);
            drive.step(way.get(0));
            int[] last = drive.dress();
            for (int n = 1; n < way.size(); n++) {
                drive.step(way.get(n));
                int[] now = drive.dress();
                for (int i = 0; i < now.length; i++)
                    assertTrue(from + " measurement " + i + " frame " + n
                            + (n >= again ? " after the second turn" : n >= turn ? " after the turn" : ""),
                        Math.abs(now[i] - last[i]) <= bound[i]);
                assertTrue("a turn keeps the crossing", drive.crossing.crossing());
                last = now;
            }
            // The rows were told the rests once; every turn was only the wall's position.
            assertEquals(1, drive.moves.morphBegins);
            assertArrayEquals(from + ": it lands on the arriving rest", end, drive.dress());
        }
    }

    @Test
    public void theKeyboardTravelsWithTheHalfThatHasItOpen() {
        // The window has it open and Home does not: it goes with the window, on the look's curve both
        // ways out and in.
        Drive out = new Drive(PaneWallPage.TERMINAL, true, false);
        for (float f = STEP; f <= 1f + 1e-6f; f += STEP) {
            float fraction = Math.min(1f, f);
            SeamCrossing.Answers answers = out.step(fraction);
            assertEquals("out at " + fraction,
                1f - StackScene.materialShare(fraction),
                answers.keyboardReveal(), 1e-4f);
        }
        Drive in = new Drive(PaneWallPage.WIDGETS, false, true);
        for (float f = STEP; f <= 1f + 1e-6f; f += STEP) {
            float fraction = Math.min(1f, f);
            SeamCrossing.Answers answers = in.step(fraction);
            assertEquals("in at " + fraction, StackScene.materialShare(fraction),
                answers.keyboardReveal(), 1e-4f);
        }
    }

    @Test
    public void midSlideEveryReaderStandsInTheHeldArrangement() {
        Drive drive = new Drive(PaneWallPage.TERMINAL, false, false);
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
        drive.crossing.land(PageRef.of(PaneWallPage.WIDGETS), REVISION);
        drive.crossing.end();
        assertSame(changed, drive.crossing.arrangement(changed));
        assertNull(drive.crossing.heldArrangement(PageRef.HOME));
        assertNull(drive.crossing.heldArrangement(PageRef.TERMINAL));
    }

    @Test
    public void aStoreThatMovesMidSlideIsAppliedAtTheLandingAndNotBefore() {
        for (PaneWallPage from : BOTH) {
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
    public void theRowIsToldBothRestsOnceDressedEveryFrameAndLaidOutOnceAtTheLanding() {
        for (PaneWallPage from : BOTH) {
            Drive drive = new Drive(from, false, false);
            int frames = 0;
            for (float f = STEP; f <= 1f + 1e-6f; f += STEP) {
                drive.step(Math.min(1f, f));
                frames++;
            }
            Moves moves = drive.moves;
            assertEquals(1, moves.morphBegins);
            assertSame(restOf(from), moves.leaving);
            assertSame(restOf(across(from)), moves.arriving);
            assertEquals("one dress a frame", frames, moves.dresses);
            assertEquals("nothing is laid out before the landing", 0, moves.morphEnds);
            drive.crossing.land(PageRef.of(across(from)), REVISION);
            assertEquals(1, moves.morphEnds);
            assertSame("the rest the last frame drew", restOf(across(from)), moves.landed);
            // A slide that came back lands on the rest it left.
            Drive back = new Drive(from, false, false);
            for (int i = 1; i <= 20; i++) back.step(i * STEP);
            for (int i = 20; i >= 0; i--) back.step(i * STEP);
            assertArrayEquals("its last frame drew the rest it left", dressOf(restOf(from)),
                back.dress());
            back.crossing.land(PageRef.of(from), REVISION);
            assertSame(restOf(from), back.moves.landed);
        }
    }

    @Test
    public void landLeavesNoCrossingState() {
    }

    @Test
    public void aKeyboardSwipeLetsTheHoldGoInOneStep() {
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves, moves);
        crossing.preRollKeyboard(PaneWallPage.TERMINAL, 500);
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
        assertEquals("no row was morphed", 0, moves.morphEnds);
        assertTrue(crossing.idle());
    }

    @Test
    public void aHoldLetGoOnItsOwnHandsBackNothingOfAnEarlierSeam() {
        // A crossing that stood the seam's layout lands, and its seam's landing says so...
        Drive drive = new Drive(PaneWallPage.WIDGETS, false, false);
        for (float f = STEP; f <= 1f + 1e-6f; f += STEP) drive.step(Math.min(1f, f));
        Crossing.Landing landed = drive.crossing.land(PageRef.of(PaneWallPage.TERMINAL), REVISION);
        assertTrue(landed.seam().laidOut());
        drive.crossing.end();
        // ...and a keyboard swipe after it lets its hold go with nothing for the seam to do.
        drive.crossing.preRollKeyboard(PaneWallPage.TERMINAL, 500);
        Crossing.Landing released = drive.crossing.releaseHold();
        assertFalse(released.seam().laidOut());
        assertFalse(released.seam().bandsMoved());
        assertFalse(released.seam().barTravelled());
        assertFalse(released.seam().redressesBack(PaneWallPage.WIDGETS));
        assertFalse(released.storeMoved());
    }

    /**
     * Halves dressed apart, both standing a stack, either way: the leaving stack is pictured
     * once, before the row hears of the crossing, and each stack rides with its own page
     * (SeamDress#stacksRide): the picture out with the committed page, whole, the stack dressed
     * for the other half in with the arriving page, at its rest, the row wearing the arriving
     * rest from the first frame. Neither covers the window in front, so the terminal's page,
     * laid out at the Terminal's rest, has its own stack under it all the way. The landing takes
     * the picture away and puts the stack back at its place.
     */
    @Test
    public void aWholeStackDipRidesEachStackWithItsPage() {
    }

    /** Turned back, both stacks ride back with their pages to where they stood. */
    @Test
    public void aRideTurnedBackPutsBothStacksBackWithTheirPages() {
        Drive back = new Drive(PaneWallPage.WIDGETS, false, true, true);
        back.step(0.4f);
        assertEquals(-0.4f, back.moves.pictureSlide, 1e-4f);
        assertEquals(0.6f, back.moves.slide, 1e-4f);
        back.step(0f);
        assertEquals("Home's dock back with its page", 0f, back.moves.pictureSlide, 1e-4f);
        assertEquals("the Terminal's stack off with its page", 1f, back.moves.slide, 1e-4f);
        back.crossing.land(PageRef.of(PaneWallPage.WIDGETS), REVISION);
        assertEquals(0f, back.moves.slide, 0f);
        back.crossing.end();
        assertTrue(back.crossing.idle());
    }


    @Test
    public void halvesDressedAlikeArePicturedNever() {
        Drive drive = new Drive(PaneWallPage.TERMINAL, true, false);
        drive.step(0.3f);
        assertEquals(0, drive.moves.pictures);
    }

    /**
     * A crossing gives nothing back and takes nothing: no page's room is written by it (each
     * is its own rest's, PageRoom), so a landing has no pre-size to put back and the crossing is
     * idle once it ends.
     */
    @Test
    public void aCrossingLeavesNothingOfTheRoomToPutBackAtTheLanding() {
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves, moves);
        assertFalse(crossing.holdsContent());
        crossing.land(PageRef.of(PaneWallPage.TERMINAL), REVISION);
        crossing.end();
        assertTrue(crossing.idle());
    }


    @Test
    public void theCoverIsOnlyEverAppliedUnderAHold() {
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves, moves);
        crossing.heldOverlap(200);
        crossing.coverAt(0f);
        assertEquals(-1, moves.coverPx);
        // The overlap noted before the hold was not kept: under the hold, nothing is covered.
        crossing.hold(0);
        crossing.coverAt(0f);
        assertEquals(0, moves.coverPx);
    }

    // ---- Between two Home pages (#173 slice E) ----

    private static final PageRef HOME_2 = PageRef.of(PaneWallPage.WIDGETS, 2);
    private static final PageRef HOME_3 = PageRef.of(PaneWallPage.WIDGETS, 3);
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
            com.termux.app.wall.WallPages.PageKind.HOME), HOME_3);

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
        assertNotEquals(dressOf(DockGeometry.compute(rests.restOf(HOME_2)))[4],
            dressOf(DockGeometry.compute(rests.restOf(HOME_3)))[4]);
    }

    /**
     * A slide on Home's pager between page 2 and page 3, whose own layout adds the index: the
     * crossing holds both pages' arrangements and rests, the row morphs from page 2's rest to
     * page 3's on the pager's way, a reader asking for Home by kind reads the committed page, and
     * the landing lays out page 3's rest.
     */
    @Test
    public void aSlideBetweenTwoHomePagesMorphsTheRowFromOnePagesRestToTheOthers() {
        DockGeometry.Snapshot rests = twoHomes();
        DockLayout rest2 = DockGeometry.compute(rests.restOf(HOME_2));
        DockLayout rest3 = DockGeometry.compute(rests.restOf(HOME_3));
        Dress dress = new DressResolver(new Stored()).dressOf(PaneWallPage.WIDGETS);
        SeamDress.StackDress stack = SeamDress.StackDress.of(dress.shape(), 1f, 1f, 0);
        SeamCrossing.Endpoint here = SeamCrossing.Endpoint.of(HOME_2, dress, null,
            HOME_2_ARRANGED, HOME, stack, false, -1);
        SeamCrossing.Endpoint there = SeamCrossing.Endpoint.of(HOME_3, dress, null,
            HOME_3_ARRANGED, HOME_WITH_INDEX, stack, false, -1);
        assertTrue(SeamCrossing.crosses(here, there));
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves, moves);
        Rest home = new Rest(false, false, true);
        crossing.begin(here, there, SeamCrossing.Stage.of(false, () -> false, true), rest2,
            rest3, REVISION);
        assertEquals(1, moves.morphBegins);
        assertSame(rest2, moves.leaving);
        assertSame(rest3, moves.arriving);
        for (float fraction = STEP; fraction < 1f; fraction += STEP) {
            Frame frame = PlaceChromeTravel.homePages(home, 2, 3, -fraction * WIDTH, WIDTH);
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            if (answers.relayout()) crossing.seam().standLayout();
            assertEquals("the row on the pager's way at " + fraction,
                SeamTravel.otherEndProgress(frame, HOME_2), moves.dressed.fraction(), 1e-4f);
        }
        assertTrue("the morph reaches the arriving page",
            moves.dressed.fraction() > 0.99f);
        assertSame(HOME_2_ARRANGED, crossing.heldArrangement(HOME_2));
        assertSame(HOME_3_ARRANGED, crossing.heldArrangement(HOME_3));
        assertSame("Home asked by kind alone is the committed page", HOME_2_ARRANGED,
            crossing.heldArrangement(PageRef.HOME));
        assertSame(rest3, crossing.heldRest(HOME_3));
        assertSame(rest2, crossing.heldRest(PageRef.HOME));
        assertNull("a kind neither end has is the store's to answer",
            crossing.heldArrangement(PageRef.TERMINAL));
        assertNull(crossing.heldRest(PageRef.TERMINAL));
        assertNull(crossing.heldArrangement(PageRef.of(PaneWallPage.DISPLAY, 7)));
        assertEquals("a Home reader by kind is the committed page, whatever the pager committed to",
            HOME_2, crossing.heldCommittedPage(PaneWallPage.WIDGETS));
        assertNull("no Workspace end is held", crossing.heldCommittedPage(PaneWallPage.TERMINAL));
        crossing.land(HOME_3, REVISION);
        assertNull("landed, the store answers", crossing.heldCommittedPage(PaneWallPage.WIDGETS));
        assertEquals(1, moves.morphEnds);
        assertSame(rest3, moves.landed);
        crossing.end();
        assertTrue(crossing.idle());
    }

    @Test
    public void twoHomePagesThatDressAlikeAreNoCrossing() {
        Dress dress = new DressResolver(new Stored()).dressOf(PaneWallPage.WIDGETS);
        SeamDress.StackDress stack = SeamDress.StackDress.of(dress.shape(), 1f, 1f, 0);
        SeamCrossing.Endpoint here = SeamCrossing.Endpoint.of(HOME_2, dress, null,
            HOME_2_ARRANGED, HOME, stack, false, -1);
        SeamCrossing.Endpoint alike = SeamCrossing.Endpoint.of(HOME_3, dress, null,
            HOME_2_ARRANGED, HOME, stack, false, -1);
        assertFalse(SeamCrossing.crosses(here, alike));
        assertFalse("one page with itself", SeamCrossing.crosses(here, here));
    }
}
