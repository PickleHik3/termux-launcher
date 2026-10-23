package com.termux.app.place;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A crossing (ADR 0012) driven the way the activity drives it: begun on the first frame that
 * reaches the other half, one frame per wall frame, retargeted mid-slide, landed and ended at the
 * settle. What is asserted is what the user sees as numbers (the dock's dress, the keyboard's
 * reveal, the cover) and, at the landing, that nothing of the crossing is left behind.
 */
public class CrossingTest {

    private static final int WIDTH = 1000;
    private static final float STEP = 0.02f;
    private static final float DENSITY = 2.625f;
    private static final List<PaneWallPage> ROW =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);
    private static final WallHalfShape SHAPE = WallHalfShape.of(LayoutStyle.DOCKED, 12, 8);

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
        LayoutVariant.HOME, true);
    private static final ResolvedArrangement WORKSPACE_ARRANGED = ResolvedArrangement.of(WINDOW,
        LayoutStyle.DOCKED, DockStyle.GLASS_TRAY, false, false, PlaceOrientation.PORTRAIT,
        LayoutVariant.NORMAL, false);

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

    private static final DockLayout HOME_REST =
        DockGeometry.compute(snapshot().restOf(DockGeometry.Side.HOME));
    private static final DockLayout WORKSPACE_REST =
        DockGeometry.compute(snapshot().restOf(DockGeometry.Side.WORKSPACE));

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
        @NonNull @Override public WallHalfShape shape(boolean home) { return SHAPE; }
    }

    private static SeamCrossing.Half half(@NonNull PaneWallPage page, boolean keyboardUp) {
        Dress dress = new DressResolver(new Stored()).dressOf(page);
        PlaceLayout layout = page == PaneWallPage.WIDGETS ? HOME : WINDOW;
        return SeamCrossing.Half.of(page, dress, layout,
            SeamDress.StackDress.of(dress.shape(), 1f, 1f, 0), keyboardUp);
    }

    // ---- The adapter, recorded ----

    private static final class Moves implements Crossing.Stack, Crossing.Bar, Crossing.Cover,
            Crossing.Keyboard {
        float dockPx = Float.NaN, keyboardPx = Float.NaN, sinkPx = Float.NaN;
        float statusAlpha = Float.NaN, sideAlpha = Float.NaN, barX = Float.NaN;
        int coverPx = -1;
        final List<PaneWallPage> preRolls = new ArrayList<>();

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
        @Override public void cover(int px) { coverPx = px; }
        @Override public void preRoll(@NonNull PaneWallPage target) { preRolls.add(target); }
    }

    // ---- The drive ----

    private static final class Drive {
        final Moves moves = new Moves();
        final Crossing crossing = new Crossing(moves, moves, moves, moves);
        final PaneWallPage committed;
        final SeamCrossing.Half here, there;
        final PlaceChromeTravel.States states;

        Drive(@NonNull PaneWallPage from, boolean fromKeyboard, boolean toKeyboard) {
            committed = from;
            PaneWallPage to = from == PaneWallPage.WIDGETS ? PaneWallPage.TERMINAL
                : PaneWallPage.WIDGETS;
            here = half(from, fromKeyboard);
            there = half(to, toKeyboard);
            states = place -> new Rest(place == committed ? fromKeyboard : toKeyboard, false, true);
        }

        Frame frameAt(float fraction) {
            float offset = committed == PaneWallPage.WIDGETS ? -fraction * WIDTH : fraction * WIDTH;
            return PlaceChromeTravel.at(ROW, committed, offset, WIDTH, states, committed);
        }

        SeamCrossing.Answers step(float fraction) {
            Frame frame = frameAt(fraction);
            PaneWallPage other = SeamTravel.otherHalfPage(frame, committed);
            if (other != null && other != crossing.seam().other()) {
                crossing.begin(here, there, SeamCrossing.Stage.of(false, () -> false, true),
                    HOME_ARRANGED, WORKSPACE_ARRANGED, HOME_REST, true, WORKSPACE_REST, false);
            }
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            if (answers.relayout()) crossing.seam().standLayout();
            return answers;
        }

        int[] dress() {
            DockMorph m = crossing.morph();
            return new int[] {m.slotIconPx(), m.appsRowUsableWidthPx(), m.appsBarHeightPx(),
                m.promptFieldBandPx(), m.rowsPx()};
        }
    }

    private static int[] dressOf(DockLayout l) {
        return new int[] {l.slotIconPx, l.appsRowUsableWidthPx, l.appsBarHeightPx,
            l.promptFieldBandPx, DockMorph.rowsPx(l)};
    }

    private static final PaneWallPage[] BOTH = {PaneWallPage.TERMINAL, PaneWallPage.WIDGETS};

    private static DockLayout restOf(PaneWallPage page) {
        return page == PaneWallPage.WIDGETS ? HOME_REST : WORKSPACE_REST;
    }

    private static PaneWallPage across(PaneWallPage page) {
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
            drive.step(1f - PlaceChromeTravel.LANDING);
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
                    / (1f - 2f * PlaceChromeTravel.LANDING)) + 1;
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
                if (n == turn) drive.crossing.retarget(from);
                if (n == again) drive.crossing.retarget(across(from));
                drive.step(way.get(n));
                int[] now = drive.dress();
                for (int i = 0; i < now.length; i++)
                    assertTrue(from + " measurement " + i + " frame " + n,
                        Math.abs(now[i] - last[i]) <= bound[i]);
                assertTrue("a retarget keeps the crossing", drive.crossing.crossing());
                last = now;
            }
            assertEquals(across(from), drive.crossing.target());
            assertArrayEquals(from + ": it lands on the arriving rest", end, drive.dress());
        }
    }

    @Test
    public void theKeyboardTravelsWithTheHalfThatHasItOpen() {
        // The window has it open and Home does not: it goes with the window, over the first
        // half of the way out, and comes back with the window on the way in.
        Drive out = new Drive(PaneWallPage.TERMINAL, true, false);
        for (float f = STEP; f <= 1f + 1e-6f; f += STEP) {
            float fraction = Math.min(1f, f);
            SeamCrossing.Answers answers = out.step(fraction);
            assertEquals("out at " + fraction,
                1f - PlaceChromeTravel.keyboardHideFraction(fraction),
                answers.keyboardReveal(), 1e-4f);
        }
        Drive in = new Drive(PaneWallPage.WIDGETS, false, true);
        for (float f = STEP; f <= 1f + 1e-6f; f += STEP) {
            float fraction = Math.min(1f, f);
            SeamCrossing.Answers answers = in.step(fraction);
            assertEquals("in at " + fraction, PlaceChromeTravel.chromeFraction(fraction),
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
            false);
        ResolvedArrangement standing = drive.crossing.arrangement(changed);
        assertEquals(LayoutStyle.DOCKED, standing.layoutStyle());
        assertEquals(DockStyle.GLASS_TRAY, standing.design());
        assertSame("one value for every reader of a frame", standing,
            drive.crossing.arrangement(changed));
        assertSame(HOME_ARRANGED, drive.crossing.heldArrangement(true));
        // At rest the committed arrangement is the answer.
        drive.crossing.land(PaneWallPage.WIDGETS);
        drive.crossing.end();
        assertSame(changed, drive.crossing.arrangement(changed));
    }

    @Test
    public void landLeavesNoCrossingState() {
        for (PaneWallPage from : BOTH) {
            Drive drive = new Drive(from, from == PaneWallPage.TERMINAL, false);
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
            crossing.foldShare(0.4f);
            crossing.beadShare(0.6f);
            crossing.roomChange(across(from), 120);
            assertEquals(Arrays.asList(across(from)), moves.preRolls);
            assertEquals(260, moves.coverPx);
            assertTrue(crossing.holdsContent());
            assertEquals(520, crossing.heldReservationPx());
            assertTrue(crossing.morph().active());
            assertFalse(crossing.idle());

            Crossing.Landing landing = crossing.land(across(from));
            assertTrue(landing.held());
            assertTrue(landing.keyboardPreRolled());
            assertTrue(landing.contentPreRolled());
            assertTrue(landing.beadsMelted());
            assertSame(from == PaneWallPage.WIDGETS ? WORKSPACE_ARRANGED : HOME_ARRANGED,
                landing.arriving());
            // In the one step: the cover off the window in front, the stack, its rows and the bar
            // back at rest, and no dress of the leaving half left to draw for a frame.
            assertEquals(0, moves.coverPx);
            assertEquals(0f, moves.dockPx, 0f);
            assertEquals(0f, moves.keyboardPx, 0f);
            assertEquals(0f, moves.sinkPx, 0f);
            assertEquals(1f, moves.statusAlpha, 0f);
            assertEquals(1f, moves.sideAlpha, 0f);
            assertEquals(0f, moves.barX, 0f);
            assertFalse(crossing.morph().active());
            assertTrue(Float.isNaN(crossing.beadShare()));
            assertTrue(Float.isNaN(crossing.foldShare()));
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
        crossing.preRollKeyboard(PaneWallPage.TERMINAL, 500);
        crossing.heldOverlap(200);
        crossing.coverAt(50f);
        assertEquals(150, moves.coverPx);
        Crossing.Landing landing = crossing.releaseHold();
        assertTrue(landing.held());
        assertTrue(landing.keyboardPreRolled());
        assertEquals(0, moves.coverPx);
        assertEquals(0f, moves.dockPx, 0f);
        assertTrue(crossing.idle());
    }

    @Test
    public void theCoverIsOnlyEverAppliedUnderAHold() {
        Moves moves = new Moves();
        Crossing crossing = new Crossing(moves, moves, moves, moves);
        crossing.heldOverlap(200);
        crossing.coverAt(0f);
        assertEquals(-1, moves.coverPx);
        assertEquals(0, crossing.heldOverlapPx());
    }
}
