package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.chrome.Dress;
import com.termux.app.chrome.DressResolver;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One crossing of the seam between Home and the Workspace, read as frame sequences through the
 * crossing's own interface, the way the activity drives it: begin on the first frame that reaches
 * the other half, one frame call per wall frame (the arrangement stood and the bar ridden where the
 * answers say), land and end at settle. Each case is what the chrome did before the crossing was
 * one module (#94): slow, fast, and reversed mid-way.
 */
public class SeamCrossingTest {

    private static final float EPSILON = 1e-4f;
    private static final int WIDTH = 1000;
    /** A slow drag's step: one frame per 2% of the way. */
    private static final float SLOW = 0.02f;

    /** Home and the Workspace's window beside it: the two halves of the wall. */
    private static final List<PaneWallPage> ROW =
        Arrays.asList(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);

    /** The one Style, Corners and Margin both halves wear, a Home layout of its own too (#84). */
    private static final WallHalfShape SHAPE = WallHalfShape.of(LayoutStyle.DOCKED, 12, 8);

    /** Both directions across the seam: from the window, and from Home. */
    private static final PaneWallPage[] BOTH = {PaneWallPage.TERMINAL, PaneWallPage.WIDGETS};

    // ---- Layouts ----

    private static PlaceLayout layout(Map<Element, Slot> slots) {
        Map<Element, Slot> all = new EnumMap<>(Element.class);
        for (Element element : Element.values())
            all.put(element, Slot.on(Edge.BOTTOM, element).withHidden(true));
        all.putAll(slots);
        return new PlaceLayout(all, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /** The shipped Workspace: the bar on top, apps, index and extra keys on the bottom. */
    private static PlaceLayout window() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        slots.put(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ));
        slots.put(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS));
        return layout(slots);
    }

    /** Home on the one layout: the window's, less the extra keys, the bar worn too (#90). */
    private static PlaceLayout home() {
        return window().withSlot(Element.EXTRA_KEYS,
            Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS).withHidden(true));
    }

    /** Home with a layout of its own that puts the bar away: the pinned apps and the index. */
    private static PlaceLayout homeWithoutBar() {
        return home().withSlot(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS).withHidden(true));
    }

    /** The minimal layout a window wears with minimal mode on: the bar and the apps alone. */
    private static PlaceLayout minimalWindow() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS));
        return layout(slots);
    }

    // ---- Dresses, as the resolver answers them ----

    private static final class Stored implements DressResolver.Source {
        boolean minimal;
        boolean homeLayout;
        final EnumMap<LayoutVariant, DockStyle> designs = new EnumMap<>(LayoutVariant.class);
        final EnumMap<LayoutVariant, Boolean> searchFields = new EnumMap<>(LayoutVariant.class);

        Stored() {
            for (LayoutVariant variant : LayoutVariant.values()) {
                designs.put(variant, DockStyle.GLASS_TRAY);
                searchFields.put(variant, false);
            }
        }

        @Override public boolean minimal() { return minimal; }
        @Override public boolean homeLayout() { return homeLayout; }
        @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant variant) {
            return designs.get(variant);
        }
        @Override public boolean searchField(@NonNull LayoutVariant variant) {
            return searchFields.get(variant);
        }
        @Override public boolean drawerTile(@NonNull LayoutVariant variant) { return false; }
        @NonNull @Override public WallHalfShape shape(boolean home) {
            // Linked: the preferences answer the Workspace's on either half.
            return SHAPE;
        }
    }

    /** Each layout's sizes as a test sets them: the minimal layout's dock is shorter. */
    private static SeamDress.StackDress stackOf(@NonNull Dress dress) {
        float dock = dress.layout() == LayoutVariant.MINIMAL ? 0.8f : 1f;
        return SeamDress.StackDress.of(dress.shape(), dock, 1f, 0);
    }

    private static SeamCrossing.Half half(@NonNull DressResolver dresses, @NonNull PaneWallPage page,
                                          @NonNull PlaceLayout layout, boolean keyboardUp) {
        Dress dress = dresses.dressOf(page);
        return SeamCrossing.Half.of(page, dress, layout, stackOf(dress), keyboardUp);
    }

    // ---- The drive: the activity's frame protocol ----

    /** What one frame answered, copied out of the crossing's reused answers. */
    private static final class Step {
        float fraction, progress, dockReveal, keyboardReveal, frameKeyboardReveal, extraKeysShare;
        float beadShare;
        float stripShare, fieldShare;
        boolean active, folds, rowsSink, relayout, restyle, restyleShape, bandsMoved, rides;
        boolean barRedressed;
        PaneWallPage stackDressPage;
        PlaceLayout layout;
    }

    private static final class Drive {
        final SeamCrossing crossing = new SeamCrossing();
        final PaneWallPage committed;
        final SeamCrossing.Half here, there;
        final SeamCrossing.Stage stage;
        final PlaceChromeTravel.States states;
        final List<Step> steps = new ArrayList<>();
        int begins;
        boolean barFirst;

        Drive(@NonNull SeamCrossing.Half here, @NonNull SeamCrossing.Half there,
              @NonNull SeamCrossing.Stage stage) {
            this.here = here;
            this.there = there;
            this.stage = stage;
            this.committed = here.page;
            boolean hereKeyboard = here.keyboardUp, thereKeyboard = there.keyboardUp;
            this.states = place -> new Rest(place == committed ? hereKeyboard : thereKeyboard,
                false, true);
        }

        /** The frame {@code fraction} of the way from the committed page to the other half. */
        Frame frameAt(float fraction) {
            float offset = committed == PaneWallPage.WIDGETS ? -fraction * WIDTH : fraction * WIDTH;
            return PlaceChromeTravel.at(ROW, committed, offset, WIDTH, states, committed);
        }

        Step step(float fraction) {
            Frame frame = frameAt(fraction);
            PaneWallPage other = SeamTravel.otherHalfPage(frame, committed);
            if (other != null && other != crossing.other()) {
                begins++;
                crossing.begin(here, there, stage);
                if (begins == 1) barFirst = crossing.barTakesWorkspaceDressFirst();
            }
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            if (answers.relayout()) crossing.standLayout();
            boolean rides = crossing.rideBar(frame, true);
            Step step = new Step();
            step.fraction = fraction;
            step.active = crossing.other() != null;
            step.progress = SeamTravel.otherHalfProgress(frame, committed);
            step.dockReveal = answers.dockReveal();
            step.keyboardReveal = answers.keyboardReveal();
            step.frameKeyboardReveal = frame.keyboardReveal;
            step.extraKeysShare = answers.extraKeysShare();
            step.beadShare = answers.beadShare();
            step.folds = answers.folds();
            step.rowsSink = answers.rowsSink(100);
            step.relayout = answers.relayout();
            step.restyle = answers.restyle();
            step.restyleShape = answers.restyleShape();
            step.bandsMoved = answers.bandsMoved();
            step.rides = rides;
            step.barRedressed = answers.barRedressed();
            step.stackDressPage = crossing.stackDressPage();
            step.layout = crossing.layout();
            step.stripShare = crossing.designShare(DockStyle.WALL_STRIP,
                here.dress.dockDesign() == DockStyle.WALL_STRIP ? 1f : 0f);
            step.fieldShare = crossing.searchFieldShare(here.dress.searchField() ? 1f : 0f);
            steps.add(step);
            return step;
        }

        Drive run(float... fractions) {
            for (float fraction : fractions) step(fraction);
            return this;
        }

        Drive slow(float from, float to) {
            int n = Math.round(Math.abs(to - from) / SLOW);
            for (int i = 0; i <= n; i++) step(from + (to - from) * i / n);
            return this;
        }

        int count(java.util.function.Predicate<Step> test) {
            int n = 0;
            for (Step step : steps) if (test.test(step)) n++;
            return n;
        }

        Step firstActive() {
            for (Step step : steps) if (step.active) return step;
            throw new AssertionError("the drive never reached the other half");
        }

        Step last() {
            return steps.get(steps.size() - 1);
        }
    }

    private static Drive drive(@NonNull Stored stored, @NonNull PaneWallPage from,
                               @NonNull PlaceLayout fromLayout, boolean fromKeyboard,
                               @NonNull PaneWallPage to, @NonNull PlaceLayout toLayout,
                               boolean toKeyboard, boolean beadsDrawn) {
        DressResolver dresses = new DressResolver(stored);
        return new Drive(half(dresses, from, fromLayout, fromKeyboard),
            half(dresses, to, toLayout, toKeyboard),
            SeamCrossing.Stage.of(beadsDrawn, () -> false, true));
    }

    // ---- (a) Equal dresses: Home on the one layout ----

    private static Drive oneLayout(@NonNull PaneWallPage from) {
        Stored stored = new Stored();
        return from == PaneWallPage.WIDGETS
            ? drive(stored, PaneWallPage.WIDGETS, home(), false, PaneWallPage.TERMINAL, window(),
                false, false)
            : drive(stored, PaneWallPage.TERMINAL, window(), false, PaneWallPage.WIDGETS, home(),
                false, false);
    }

    /** The invariants of a crossing between halves dressed alike, frame by frame. */
    private static void assertOnlyTheExtraKeysFold(@NonNull Drive drive) {
        assertOnlyTheExtraKeysFold(drive, true);
    }

    /**
     * The same, where the halves' Dock designs may differ ({@code sameDesign} false): a band only
     * one of them carries eases and beads only one draws melt over the fold, the rows standing.
     */
    private static void assertOnlyTheExtraKeysFold(@NonNull Drive drive, boolean sameDesign) {
        for (Step step : drive.steps) {
            if (!step.active) {
                // Short of the other half the travel's own reveals stand.
                assertFalse(step.folds);
                assertEquals(1f, step.dockReveal, EPSILON);
                continue;
            }
            assertFalse("no restyle at " + step.fraction, step.restyle);
            assertFalse("the bar stands still at " + step.fraction, step.rides);
            assertFalse(step.barRedressed);
            assertEquals("the rows never move at " + step.fraction, 1f, step.dockReveal, EPSILON);
            assertEquals(0f, step.keyboardReveal, EPSILON);
            assertTrue(step.folds);
            assertFalse(step.rowsSink);
            if (sameDesign) {
                assertFalse(step.bandsMoved);
                assertTrue(Float.isNaN(step.beadShare));
            }
            float windowShare = drive.committed == PaneWallPage.WIDGETS
                ? step.progress : 1f - step.progress;
            assertEquals("the extra keys follow the window at " + step.fraction, windowShare,
                step.extraKeysShare, EPSILON);
        }
        assertFalse(drive.barFirst);
    }

    @Test
    public void equalDressesFoldOnlyTheExtraKeysFromTheWindowSlow() {
        Drive drive = oneLayout(PaneWallPage.TERMINAL).slow(0f, 1f);
        assertOnlyTheExtraKeysFold(drive);
        assertEquals(1f, drive.firstActive().extraKeysShare, EPSILON);
        assertEquals(0f, drive.last().extraKeysShare, EPSILON);
        // Out, the window's rows stay laid out to settle: nothing is stood or laid out mid-way.
        assertEquals(0, drive.count(s -> s.relayout));
        assertEquals(0, drive.count(s -> s.layout != null));
        SeamCrossing.Landing landing = drive.crossing.land();
        assertFalse(landing.laidOut());
        assertFalse(landing.barTravelled());
        assertFalse(landing.bandsMoved());
        drive.crossing.end();
        assertFalse(landing.redressesBack(PaneWallPage.WIDGETS));
        assertNull(drive.crossing.other());
    }

    @Test
    public void equalDressesFoldOnlyTheExtraKeysFromHomeSlow() {
        Drive drive = oneLayout(PaneWallPage.WIDGETS).slow(0f, 1f);
        assertOnlyTheExtraKeysFold(drive);
        assertEquals(0f, drive.firstActive().extraKeysShare, EPSILON);
        assertEquals(1f, drive.last().extraKeysShare, EPSILON);
        // In, the window's rows are stood once, on the first frame into the window's half, the
        // extra keys folded shut, and never restyled.
        assertEquals(1, drive.count(s -> s.relayout));
        Step first = null;
        for (Step step : drive.steps) if (step.relayout) first = step;
        assertNotNull(first);
        assertTrue(first.progress > 0f);
        assertEquals(0f, first.extraKeysShare, 0.05f);
        assertEquals(SeamTravel.withDockOf(home(), window()), drive.last().layout);
        SeamCrossing.Landing landing = drive.crossing.land();
        assertTrue(landing.laidOut());
        assertNull(drive.crossing.layout());
        drive.crossing.end();
        assertFalse(landing.redressesBack(PaneWallPage.TERMINAL));
        assertFalse(landing.redressesBack(PaneWallPage.WIDGETS));
    }

    /**
     * The one layout with its bottom rows in another order, outermost first: the window's, and
     * Home's less the extra keys.
     */
    private static PlaceLayout windowStacking(Element... outermostFirst) {
        PlaceLayout layout = window();
        for (int order = 0; order < outermostFirst.length; order++) {
            Element element = outermostFirst[order];
            layout = layout.withSlot(element, Slot.on(Edge.BOTTOM, element).withOrder(order));
        }
        return layout;
    }

    private static PlaceLayout homeOf(@NonNull PlaceLayout window) {
        return window.withSlot(Element.EXTRA_KEYS,
            window.slot(Element.EXTRA_KEYS).withHidden(true));
    }

    // ---- The fold lands on Home's rest: nothing sinks then snaps back at settle ----

    private static final int BOTTOM = 2000;
    private static final int KEYS = 110;
    private static final int KEYBOARD = 700;
    /** The window's rows less the extra keys, and Home's: the A-Z row's 10dp chin taller. */
    private static final int WINDOW_ROWS = 190;
    private static final int HOME_ROWS = 218;

    private static Drive foldDrive(@NonNull PaneWallPage from, boolean windowKeyboard) {
        return foldDrive(new Stored(), from, windowKeyboard);
    }

    private static Drive foldDrive(@NonNull Stored stored, @NonNull PaneWallPage from,
                                   boolean windowKeyboard) {
        DressResolver dresses = new DressResolver(stored);
        Dress windowDress = dresses.dressOf(PaneWallPage.TERMINAL);
        Dress homeDress = dresses.dressOf(PaneWallPage.WIDGETS);
        SeamCrossing.Half window = SeamCrossing.Half.of(PaneWallPage.TERMINAL, windowDress,
            window(), stackOf(windowDress), windowKeyboard, WINDOW_ROWS);
        SeamCrossing.Half home = SeamCrossing.Half.of(PaneWallPage.WIDGETS, homeDress, home(),
            stackOf(homeDress), false, HOME_ROWS);
        SeamCrossing.Stage stage = SeamCrossing.Stage.of(false, () -> false, true);
        return from == PaneWallPage.TERMINAL ? new Drive(window, home, stage)
            : new Drive(home, window, stage);
    }

    /**
     * The dock's top on screen at one step, the window's stack laid out (its flush padding, half
     * under the rows, and a keyboard in the stack travelling down by its own reveal) and sunk by
     * the fold, as the activity applies it.
     */
    private static float windowStackTop(@NonNull Drive drive, @NonNull Step step, int flush,
                                        int keyboard) {
        int pad = flush / 2;
        float keyboardTravel = PlaceChromeTravel.stackTranslationPx(step.keyboardReveal, 1f,
            keyboard, WINDOW_ROWS + KEYS);
        return BOTTOM - keyboard + keyboardTravel - pad - (WINDOW_ROWS + KEYS)
            + SeamTravel.foldRowsSinkPx(KEYS, step.extraKeysShare, pad,
                drive.crossing.foldRestGrowthPx());
    }

    @Test
    public void workspaceToHomeTheLastFrameStandsTheDockWhereHomesRests() {
        for (int flush : new int[] {0, 24}) {
            for (Drive drive : new Drive[] {foldDrive(PaneWallPage.TERMINAL, false).slow(0f, 1f),
                foldDrive(PaneWallPage.TERMINAL, false).run(0f, 0.3f, 0.9f, 1f)}) {
                assertEquals(HOME_ROWS - WINDOW_ROWS, drive.crossing.foldRestGrowthPx());
                assertEquals(0f, drive.last().extraKeysShare, EPSILON);
                assertEquals("flush " + flush, BOTTOM - HOME_ROWS,
                    windowStackTop(drive, drive.last(), flush, 0), 0.5f);
                // And it went there in one direction, never past it.
                float previous = Float.NEGATIVE_INFINITY;
                for (Step step : drive.steps) {
                    if (!step.active) continue;
                    float top = windowStackTop(drive, step, flush, 0);
                    assertTrue("backwards at " + step.fraction, top >= previous - 0.5f);
                    assertTrue("past Home's rest at " + step.fraction,
                        top <= BOTTOM - HOME_ROWS + 0.5f);
                    previous = top;
                }
                drive.crossing.land();
                drive.crossing.end();
                assertEquals("the settle lets the growth go", 0,
                    drive.crossing.foldRestGrowthPx());
            }
        }
    }

    @Test
    public void workspaceToHomeWithTheKeyboardUpLandsOnHomesRestToo() {
        Drive drive = foldDrive(PaneWallPage.TERMINAL, true).slow(0f, 1f);
        assertTrue(drive.last().folds);
        assertEquals(0f, drive.last().keyboardReveal, EPSILON);
        // The keyboard up, the window keeps no flush padding.
        assertEquals(BOTTOM - HOME_ROWS, windowStackTop(drive, drive.last(), 0, KEYBOARD), 0.5f);
    }

    @Test
    public void homeToWorkspaceTheFirstFrameStandsTheDockWhereHomeRests() {
        for (int flush : new int[] {0, 24}) {
            Drive drive = foldDrive(PaneWallPage.WIDGETS, false).slow(0f, 1f);
            assertEquals(HOME_ROWS - WINDOW_ROWS, drive.crossing.foldRestGrowthPx());
            // The first frame that lays the window's rows out stands them at Home's rest, to
            // within the little the fold has opened by then.
            Step first = null;
            for (Step step : drive.steps) {
                if (step.relayout) {
                    first = step;
                    break;
                }
            }
            assertNotNull(first);
            float opened = (KEYS + flush / 2f) * first.extraKeysShare;
            assertEquals("flush " + flush, BOTTOM - HOME_ROWS,
                windowStackTop(drive, first, flush, 0), opened + 0.5f);
            // At the window the fold is open: the window's own rest, which the settle keeps.
            assertEquals(BOTTOM - flush / 2 - WINDOW_ROWS - KEYS,
                windowStackTop(drive, drive.last(), flush, 0), 0.5f);
        }
    }

    @Test
    public void aCrossingThatDoesNotFoldReadsNoGrowth() {
        Drive drive = foldDrive(PaneWallPage.TERMINAL, false);
        drive.run(0f, 0.5f);
        assertTrue(drive.crossing.foldRestGrowthPx() != 0);
        // Halves whose rows are not known fold the extra keys alone.
        assertEquals(0, oneLayout(PaneWallPage.TERMINAL).run(0f, 0.5f).crossing
            .foldRestGrowthPx());
    }

    @Test
    public void extraKeysInTheMiddleFoldWithTheRowsOutsideThemHeld() {
        PlaceLayout window = windowStacking(Element.AZ, Element.EXTRA_KEYS, Element.APPS);
        for (PaneWallPage from : new PaneWallPage[] {PaneWallPage.TERMINAL, PaneWallPage.WIDGETS}) {
            Drive drive = from == PaneWallPage.TERMINAL
                ? drive(new Stored(), PaneWallPage.TERMINAL, window, false, PaneWallPage.WIDGETS,
                    homeOf(window), false, false)
                : drive(new Stored(), PaneWallPage.WIDGETS, homeOf(window), false,
                    PaneWallPage.TERMINAL, window, false, false);
            drive.slow(0f, 0.6f);
            assertOnlyTheExtraKeysFold(drive);
            // The A-Z row stands between the extra keys and the screen's edge: it never moves.
            assertTrue(drive.crossing.foldHolds(Element.AZ));
            assertFalse(drive.crossing.foldHolds(Element.APPS));
            assertFalse(drive.crossing.foldHolds(Element.EXTRA_KEYS));
            drive.crossing.land();
            assertFalse("a landed crossing holds nothing", drive.crossing.foldHolds(Element.AZ));
            drive.crossing.end();
        }
    }

    @Test
    public void extraKeysInnermostFoldWithEveryOtherRowHeld() {
        PlaceLayout window = windowStacking(Element.AZ, Element.APPS, Element.EXTRA_KEYS);
        Drive drive = drive(new Stored(), PaneWallPage.TERMINAL, window, false,
            PaneWallPage.WIDGETS, homeOf(window), false, false).slow(0f, 1f);
        assertOnlyTheExtraKeysFold(drive);
        assertTrue(drive.crossing.foldHolds(Element.AZ));
        assertTrue(drive.crossing.foldHolds(Element.APPS));
    }

    @Test
    public void extraKeysOutermostHoldNoRow() {
        Drive drive = oneLayout(PaneWallPage.TERMINAL).slow(0f, 0.5f);
        assertOnlyTheExtraKeysFold(drive);
        assertFalse(drive.crossing.foldHolds(Element.AZ));
        assertFalse(drive.crossing.foldHolds(Element.APPS));
    }

    @Test
    public void equalDressesFoldAndUnfoldWhenReversedMidWay() {
        for (PaneWallPage from : new PaneWallPage[] {PaneWallPage.TERMINAL, PaneWallPage.WIDGETS}) {
            Drive drive = oneLayout(from).slow(0f, 0.6f).slow(0.6f, 0f);
            assertOnlyTheExtraKeysFold(drive);
            // Back where it started, the extra keys are where they rest on the committed page.
            float rest = from == PaneWallPage.WIDGETS ? 0f : 1f;
            assertEquals(rest, drive.last().extraKeysShare, EPSILON);
            assertEquals(1, drive.begins);
            if (from == PaneWallPage.WIDGETS) {
                // The window's rows stood in, and give way again once the drag is back home.
                assertEquals(2, drive.count(s -> s.relayout));
                assertNull(drive.last().layout);
            } else {
                assertEquals(0, drive.count(s -> s.relayout));
            }
        }
    }

    @Test
    public void equalDressesFoldOnAFastFling() {
        for (PaneWallPage from : new PaneWallPage[] {PaneWallPage.TERMINAL, PaneWallPage.WIDGETS}) {
            Drive drive = oneLayout(from).run(0f, 0.3f, 0.9f, 1f);
            assertOnlyTheExtraKeysFold(drive);
            float landed = from == PaneWallPage.WIDGETS ? 1f : 0f;
            assertEquals(landed, drive.last().extraKeysShare, EPSILON);
        }
    }

    // ---- (b) Home's own layout on: the sizes and the shape linked to the shared layout (#84) ----
    //
    // A Home layout of its own differs from the shared one only in which rows it stands, their
    // order and edge, its Dock design and its toggles: the stack's dress (Style, Corners, Margin,
    // the dock's and the keyboard's sizes) is one on both halves. So the rows cross by how they
    // differ, a swap while they are hidden, a fold or nothing, and the stack is never dressed
    // again mid-slide; a Dock design crosses by its bands and beads, which follow the page.

    private static Stored homeLayoutOn() {
        Stored stored = new Stored();
        stored.homeLayout = true;
        return stored;
    }

    /** Home's own layout with the pinned apps put away: the index alone on its dock. */
    private static PlaceLayout homeWithoutApps() {
        return home().withSlot(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS).withHidden(true));
    }

    /** Home's own layout with the index put away: only the window stands the A-Z row. */
    private static PlaceLayout homeWithoutAz() {
        return home().withSlot(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ).withHidden(true));
    }

    /** A crossing between the window and Home's own {@code homeLayout}, from {@code from}. */
    private static Drive homeOwn(@NonNull Stored stored, @NonNull PaneWallPage from,
                                 @NonNull PlaceLayout homeLayout, boolean windowKeyboard) {
        return from == PaneWallPage.WIDGETS
            ? drive(stored, PaneWallPage.WIDGETS, homeLayout, false, PaneWallPage.TERMINAL,
                window(), windowKeyboard, false)
            : drive(stored, PaneWallPage.TERMINAL, window(), windowKeyboard, PaneWallPage.WIDGETS,
                homeLayout, false, false);
    }

    /**
     * The dock rows the stack shows on a step, outermost first: the seam's arrangement, else the
     * committed one, less the extra keys while a fold has them shut.
     */
    private static List<Element> rowsShown(@NonNull Drive drive, @NonNull Step step) {
        PlaceLayout layout = step.layout != null ? step.layout : drive.here.layout;
        List<Element> rows = new ArrayList<>(EdgeStackPolicy.stack(layout, Edge.BOTTOM));
        if (step.folds && step.extraKeysShare < EPSILON) rows.remove(Element.EXTRA_KEYS);
        return rows;
    }

    /** The share of a band only {@code design} carries that {@code half}'s dock rests with. */
    private static float restShare(@NonNull SeamCrossing.Half half, @NonNull DockStyle design) {
        return half.dress.dockDesign() == design ? 1f : 0f;
    }

    /**
     * The last frame of a crossing that went all the way stands what the other half rests with:
     * its rows, all of them showing, its bands, and the stack dressed as it is dressed there.
     */
    private static void assertLandsOnTheOtherHalfsRest(@NonNull Drive drive) {
        Step last = drive.last();
        assertEquals(1f, last.progress, EPSILON);
        assertEquals("the other half's rows", EdgeStackPolicy.stack(drive.there.layout, Edge.BOTTOM),
            rowsShown(drive, last));
        assertEquals("all of them showing", 1f, last.dockReveal, EPSILON);
        assertEquals("the Wall strip's row as the other half rests",
            restShare(drive.there, DockStyle.WALL_STRIP), last.stripShare, EPSILON);
        assertEquals("the Search field as the other half rests",
            drive.there.dress.searchField() ? 1f : 0f, last.fieldShare, EPSILON);
        assertEquals("the stack is dressed alike on both halves", drive.here.stack,
            drive.there.stack);
    }

    /**
     * Rows that differ between halves dressed alike are swapped once each way, on a frame where
     * none of them shows, and never dressed again: there is nothing to dress.
     */
    private static void assertRowsSwapOnlyWhileHidden(@NonNull Drive drive) {
        for (Step step : drive.steps) {
            assertFalse("nothing to dress again at " + step.fraction, step.restyle);
            if (!step.active) continue;
            assertFalse(step.folds);
            assertEquals("the rows dip at " + step.fraction,
                SeamTravel.dockReveal(SeamTravel.DockPlan.DIP, step.progress), step.dockReveal,
                EPSILON);
            assertEquals("the keyboard keeps its own travel at " + step.fraction,
                step.frameKeyboardReveal, step.keyboardReveal, EPSILON);
            if (step.relayout) {
                assertTrue("the rows are hidden at the swap, " + step.fraction,
                    step.dockReveal <= 2f * SLOW / (1f - 2f * PlaceChromeTravel.LANDING) + EPSILON);
            }
        }
        assertFalse(drive.barFirst);
    }

    @Test
    public void homesOwnWithoutTheAppsRowSwapsTheRowsWhileHiddenBothWays() {
        for (PaneWallPage from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutApps(), false).slow(0f, 1f);
            assertRowsSwapOnlyWhileHidden(drive);
            assertEquals("one swap, at the midpoint", 1, drive.count(s -> s.relayout));
            assertLandsOnTheOtherHalfsRest(drive);
            assertSame(drive.there.page, drive.last().stackDressPage);
            SeamCrossing.Landing landing = drive.crossing.land();
            assertTrue(landing.laidOut());
            drive.crossing.end();
            assertFalse(landing.redressesBack(PaneWallPage.TERMINAL));
            assertFalse(landing.redressesBack(PaneWallPage.WIDGETS));
        }
    }

    @Test
    public void homesOwnWithoutTheAppsRowSinksTheRowsIntoTheWindowsKeyboard() {
        for (PaneWallPage from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutApps(), true).slow(0f, 1f);
            assertRowsSwapOnlyWhileHidden(drive);
            for (Step step : drive.steps) {
                if (step.active) assertTrue("no whole-stack dip at " + step.fraction, step.rowsSink);
            }
            assertLandsOnTheOtherHalfsRest(drive);
        }
    }

    @Test
    public void homesOwnWithoutTheAppsRowReversedMidWayComesBackWithItsOwnRows() {
        for (PaneWallPage from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutApps(), false)
                .slow(0f, 0.7f).slow(0.7f, 0f);
            assertRowsSwapOnlyWhileHidden(drive);
            assertEquals("out through the swap and back", 2, drive.count(s -> s.relayout));
            assertNull(drive.last().layout);
            assertNull(drive.last().stackDressPage);
            assertEquals(EdgeStackPolicy.stack(drive.here.layout, Edge.BOTTOM),
                rowsShown(drive, drive.last()));
            assertEquals(1f, drive.last().dockReveal, EPSILON);
            SeamCrossing.Landing landing = drive.crossing.land();
            assertFalse(landing.laidOut());
            drive.crossing.end();
            assertFalse(landing.redressesBack(from));
        }
    }

    @Test
    public void homesOwnFlingThatSkipsTheWayBackLaysTheCommittedRowsOutAtSettle() {
        // The drawer or reduced motion took the frames back past the swap: the settle lays the
        // committed rows out again, and has no dress to put back, the halves wearing one.
        for (PaneWallPage from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutApps(), false)
                .run(0f, 0.3f, 0.9f);
            assertEquals(0, drive.count(s -> s.restyle));
            SeamCrossing.Landing landing = drive.crossing.land();
            assertTrue(landing.laidOut());
            drive.crossing.end();
            assertFalse(landing.redressesBack(from));
            assertNull(drive.crossing.stackDressPage());
        }
    }

    @Test
    public void homesOwnDesignFoldsTheExtraKeysAndEasesTheStripWithThePage() {
        // Another Dock design on Home, the same rows less the extra keys: the extra keys fold as
        // on the shared layout, the Wall strip's row follows Home's share, and nothing dips.
        for (PaneWallPage from : BOTH) {
            Stored stored = homeLayoutOn();
            stored.designs.put(LayoutVariant.HOME, DockStyle.WALL_STRIP);
            Drive drive = homeOwn(stored, from, home(), false).slow(0f, 1f);
            assertOnlyTheExtraKeysFold(drive, false);
            float previous = Float.NaN;
            for (Step step : drive.steps) {
                float homeShare = from == PaneWallPage.WIDGETS
                    ? 1f - clamp01(step.progress) : clamp01(step.progress);
                assertEquals("the strip follows Home at " + step.fraction, homeShare,
                    step.stripShare, EPSILON);
                if (!Float.isNaN(previous)) {
                    assertTrue("one way at " + step.fraction, from == PaneWallPage.WIDGETS
                        ? step.stripShare <= previous + EPSILON
                        : step.stripShare >= previous - EPSILON);
                }
                previous = step.stripShare;
                assertEquals("no Search field on either half", 0f, step.fieldShare, EPSILON);
            }
            assertTrue(drive.count(s -> s.bandsMoved) > 10);
            assertLandsOnTheOtherHalfsRest(drive);
            SeamCrossing.Landing landing = drive.crossing.land();
            assertTrue(landing.bandsMoved());
            // Landed, the band is the committed design's again until the commit dresses the page.
            assertEquals(0f, drive.crossing.designShare(DockStyle.WALL_STRIP, 0f), EPSILON);
            assertNull(drive.crossing.committedDesign());
            drive.crossing.end();
        }
    }

    @Test
    public void homesOwnDesignWithoutTheAppsRowSwapsTheRowsAndEasesTheStrip() {
        for (PaneWallPage from : BOTH) {
            Stored stored = homeLayoutOn();
            stored.designs.put(LayoutVariant.HOME, DockStyle.WALL_STRIP);
            stored.searchFields.put(LayoutVariant.HOME, true);
            Drive drive = homeOwn(stored, from, homeWithoutApps(), false).slow(0f, 1f);
            assertRowsSwapOnlyWhileHidden(drive);
            assertEquals(1, drive.count(s -> s.relayout));
            assertTrue(drive.count(s -> s.bandsMoved) > 10);
            assertLandsOnTheOtherHalfsRest(drive);
        }
    }

    @Test
    public void homesOwnLensBeadsMeltIntoTheSlabAsHomeLeavesAndBackOut() {
        Stored stored = homeLayoutOn();
        stored.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
        Drive drive = drive(stored, PaneWallPage.WIDGETS, home(), false, PaneWallPage.TERMINAL,
            window(), false, true).slow(0f, 1f);
        assertOnlyTheExtraKeysFold(drive, false);
        float previous = 1f;
        for (Step step : drive.steps) {
            if (!step.active) continue;
            // The rows stay up through the fold: the beads melt over the way, as Home leaves.
            assertEquals("Home's share at " + step.fraction, 1f - clamp01(step.progress),
                step.beadShare, EPSILON);
            assertTrue(step.beadShare <= previous + EPSILON);
            previous = step.beadShare;
        }
        assertEquals("the window's slab at the end", 0f, drive.last().beadShare, EPSILON);
        assertLandsOnTheOtherHalfsRest(drive);
        Drive back = drive(stored, PaneWallPage.WIDGETS, home(), false, PaneWallPage.TERMINAL,
            window(), false, true).slow(0f, 0.6f).slow(0.6f, 0f);
        assertEquals("whole beads back on Home", 1f, back.last().beadShare, EPSILON);
    }

    @Test
    public void theSearchFieldKeepsHomesBeadsSoTheyMeltInAsHomeArrives() {
        // #84: Lens beads with the Search field keep their beads (the field in a capsule of its
        // own under them), so a slide in from the window's slab melts them in as Home comes.
        Stored stored = homeLayoutOn();
        stored.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
        stored.searchFields.put(LayoutVariant.HOME, true);
        // Home's row alone on its dock (the index put away), where it wears beads.
        Drive drive = drive(stored, PaneWallPage.TERMINAL, window(), false, PaneWallPage.WIDGETS,
            homeWithoutAz(), false, false).slow(0f, 1f);
        assertTrue("the beads melt in with Home's half",
            drive.count(s -> !Float.isNaN(s.beadShare)) > 0);
        float last = Float.NaN;
        for (Step step : drive.steps) if (!Float.isNaN(step.beadShare)) last = step.beadShare;
        assertEquals("whole beads once Home has come", 1f, last, 0.05f);
        assertLandsOnTheOtherHalfsRest(drive);
    }

    @Test
    public void homesOwnWithoutTheAzRowSwapsTheRowsWhileHiddenBothWays() {
        for (PaneWallPage from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutAz(), false).slow(0f, 1f);
            assertRowsSwapOnlyWhileHidden(drive);
            assertEquals(1, drive.count(s -> s.relayout));
            assertLandsOnTheOtherHalfsRest(drive);
            Drive back = homeOwn(homeLayoutOn(), from, homeWithoutAz(), false)
                .slow(0f, 0.7f).slow(0.7f, 0f);
            assertRowsSwapOnlyWhileHidden(back);
            assertEquals(EdgeStackPolicy.stack(back.here.layout, Edge.BOTTOM),
                rowsShown(back, back.last()));
        }
    }

    @Test
    public void homesOwnWithOnlyTheExtraKeysApartFoldsAsTheSharedLayoutDoes() {
        for (PaneWallPage from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, home(), false).slow(0f, 1f);
            assertOnlyTheExtraKeysFold(drive);
            assertLandsOnTheOtherHalfsRest(drive);
            Drive fling = homeOwn(homeLayoutOn(), from, home(), false).run(0f, 0.3f, 0.9f, 1f);
            assertOnlyTheExtraKeysFold(fling);
            assertLandsOnTheOtherHalfsRest(fling);
            Drive back = homeOwn(homeLayoutOn(), from, home(), false)
                .slow(0f, 0.6f).slow(0.6f, 0f);
            assertOnlyTheExtraKeysFold(back);
            assertEquals(from == PaneWallPage.WIDGETS ? 0f : 1f, back.last().extraKeysShare,
                EPSILON);
        }
    }

    @Test
    public void homesOwnFoldLandsOnHomesRestAndStartsThere() {
        // The fold's rows travel from one half's rest to the other's (d22084796), Home's own
        // layout on as on the shared one.
        for (int flush : new int[] {0, 24}) {
            Drive out = foldDrive(homeLayoutOn(), PaneWallPage.TERMINAL, false).slow(0f, 1f);
            assertEquals(HOME_ROWS - WINDOW_ROWS, out.crossing.foldRestGrowthPx());
            assertEquals(0f, out.last().extraKeysShare, EPSILON);
            assertEquals("flush " + flush, BOTTOM - HOME_ROWS,
                windowStackTop(out, out.last(), flush, 0), 0.5f);
            Drive in = foldDrive(homeLayoutOn(), PaneWallPage.WIDGETS, false).slow(0f, 1f);
            Step first = null;
            for (Step step : in.steps) {
                if (step.relayout) {
                    first = step;
                    break;
                }
            }
            assertNotNull(first);
            assertFalse(first.restyle);
            float opened = (KEYS + flush / 2f) * first.extraKeysShare;
            assertEquals("flush " + flush, BOTTOM - HOME_ROWS,
                windowStackTop(in, first, flush, 0), opened + 0.5f);
            assertEquals(BOTTOM - flush / 2 - WINDOW_ROWS - KEYS,
                windowStackTop(in, in.last(), flush, 0), 0.5f);
        }
    }

    @Test
    public void homesOwnWithoutTheBarLaysTheWorkspacesBarOverHomeFromTheFirstFrame() {
        Drive drive = drive(homeLayoutOn(), PaneWallPage.WIDGETS, homeWithoutBar(), false,
            PaneWallPage.TERMINAL, window(), false, false).slow(0f, 1f);
        // One shape across the wall: the bar has no dress of Home's to take off first.
        assertFalse(drive.barFirst);
        for (Step step : drive.steps) {
            assertFalse("nothing to dress again at " + step.fraction, step.restyle);
            if (!step.active) continue;
            // The rows differ by the extra keys alone: they fold open with the window.
            assertTrue(step.folds);
            assertEquals(step.progress, step.extraKeysShare, EPSILON);
        }
        // The window's rows, the extra keys folded shut, and its bar are stood over Home from the
        // first frame into the window's half, once.
        assertEquals(1, drive.count(s -> s.relayout));
        Step first = null;
        for (Step step : drive.steps) if (step.relayout && first == null) first = step;
        assertNotNull(first);
        assertTrue(first.progress > 0f);
        PlaceLayout stood = SeamTravel.withStatusOf(
            SeamTravel.withDockOf(homeWithoutBar(), window()), window());
        assertEquals(stood, first.layout);
        assertEquals(stood, drive.last().layout);
        // The bar rides with the window's page while any of Home shows, and stands at its rest
        // once the frame is the window's alone.
        assertTrue(drive.steps.get(drive.steps.size() / 2).rides);
        assertFalse(drive.last().rides);
        assertLandsOnTheOtherHalfsRest(drive);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }

    // ---- (c) Minimal mode on in a window ----

    /** The dress changes only where none of the stack shows, and only with its rows. */
    private static void assertDressSwapsOnlyWhileHidden(@NonNull Drive drive) {
        PaneWallPage previous = null;
        for (Step step : drive.steps) {
            if (step.restyle) {
                assertTrue("a restyle is a layout pass at " + step.fraction, step.relayout);
                assertTrue("the dock is hidden at the swap, " + step.fraction,
                    step.dockReveal <= 2f * SLOW / (1f - 2f * PlaceChromeTravel.LANDING) + EPSILON);
                assertTrue("the keyboard is hidden at the swap, " + step.fraction,
                    step.keyboardReveal <= step.dockReveal + EPSILON);
                assertTrue(step.stackDressPage != previous);
            } else {
                assertSame("the dress holds at " + step.fraction, previous, step.stackDressPage);
            }
            previous = step.stackDressPage;
        }
    }

    @Test
    public void minimalWindowAgainstNormalHomeDipsTheWholeStack() {
        Stored stored = new Stored();
        stored.minimal = true;
        // The window's minimal layout dresses the stack apart from Home's normal one (its sizes),
        // with the window's keyboard up: the keyboard dips with the rows.
        Drive drive = drive(stored, PaneWallPage.TERMINAL, minimalWindow(), true,
            PaneWallPage.WIDGETS, home(), false, false).slow(0f, 1f);
        assertDressSwapsOnlyWhileHidden(drive);
        assertEquals(1, drive.count(s -> s.restyle));
        for (Step step : drive.steps) {
            assertEquals(SeamTravel.dockReveal(SeamTravel.DockPlan.DIP, step.progress),
                step.dockReveal, EPSILON);
            assertEquals(SeamDress.wholeStackKeyboardReveal(step.progress, true, false),
                step.keyboardReveal, EPSILON);
            if (step.restyle) assertFalse("one shape, other sizes", step.restyleShape);
            assertFalse(step.rides);
            assertFalse(step.rowsSink);
        }
        assertSame(PaneWallPage.WIDGETS, drive.last().stackDressPage);
        assertEquals(0f, drive.last().keyboardReveal, EPSILON);
    }

    @Test
    public void minimalWindowReversedMidWayComesBackDressedAsItLeft() {
        Stored stored = new Stored();
        stored.minimal = true;
        Drive drive = drive(stored, PaneWallPage.TERMINAL, minimalWindow(), true,
            PaneWallPage.WIDGETS, home(), false, false).slow(0f, 0.8f).slow(0.8f, 0f);
        assertDressSwapsOnlyWhileHidden(drive);
        assertEquals(2, drive.count(s -> s.restyle));
        assertEquals(1f, drive.last().keyboardReveal, EPSILON);
        assertEquals(1f, drive.last().dockReveal, EPSILON);
        assertNull(drive.last().stackDressPage);
    }

    // ---- Rest ----

    @Test
    public void aFrameThatNeverReachesTheOtherHalfAnswersTheTravelsOwnReveals() {
        SeamCrossing crossing = new SeamCrossing();
        Frame frame = PlaceChromeTravel.rest(PaneWallPage.TERMINAL, new Rest(true, false));
        SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
        assertEquals(frame.dockReveal, answers.dockReveal(), EPSILON);
        assertEquals(frame.keyboardReveal, answers.keyboardReveal(), EPSILON);
        assertFalse(answers.relayout());
        assertFalse(answers.moved());
        assertFalse(answers.folds());
        assertTrue(Float.isNaN(answers.extraKeysShare()));
        assertFalse(crossing.rideBar(frame, true));
        assertFalse(answers.barRedressed());
        assertNull(crossing.layout());
        assertNull(crossing.other());
    }

    // ---- The rows the seam stands wear their own half's design ----

    /** The Workspace with the extra keys alone along the bottom and the bar on top. */
    private static PlaceLayout keysOnlyWindow() {
        return window()
            .withSlot(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS).withHidden(true))
            .withSlot(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ).withHidden(true));
    }

    @Test
    public void theRowsTheSeamStandsAreLaidOutInTheirOwnHalfsDesign() {
        // The developer's phone: Home's own layout on Tray with the pinned apps and the index, the
        // Workspace on Lens beads with the extra keys alone. A row slid in from Home was held to
        // the Workspace's bead fit for the whole slide (118 px) and grew at the settle (143 px).
        for (PaneWallPage from : BOTH) {
            Stored stored = homeLayoutOn();
            stored.designs.put(LayoutVariant.HOME, DockStyle.GLASS_TRAY);
            stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
            boolean fromHome = from == PaneWallPage.WIDGETS;
            Drive drive = fromHome
                ? drive(stored, PaneWallPage.WIDGETS, home(), false, PaneWallPage.TERMINAL,
                    keysOnlyWindow(), true, false)
                : drive(stored, PaneWallPage.TERMINAL, keysOnlyWindow(), true,
                    PaneWallPage.WIDGETS, home(), false, false);
            DockStyle committed = fromHome ? DockStyle.GLASS_TRAY : DockStyle.LENS_BEADS;
            DockStyle other = fromHome ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY;
            assertNull("no crossing, no design", drive.crossing.rowsDesign());
            int othersFrames = 0;
            int n = Math.round(1f / SLOW);
            for (int i = 0; i <= n; i++) {
                Step step = drive.step(i / (float) n);
                if (!step.active) continue;
                String at = from + " at " + step.fraction;
                if (step.layout == null) {
                    assertEquals(at, committed, drive.crossing.rowsDesign());
                } else {
                    // The seam stands the other half's rows: they wear that half's design.
                    assertEquals(at, EdgeStackPolicy.stack(drive.there.layout, Edge.BOTTOM),
                        EdgeStackPolicy.stack(step.layout, Edge.BOTTOM));
                    assertEquals(at, other, drive.crossing.rowsDesign());
                    othersFrames++;
                }
                // The slab and the beads keep the committed half's design and its melt.
                assertEquals(at, committed, drive.crossing.committedDesign());
            }
            assertTrue(from + ": the other half's rows stood before the settle", othersFrames > 5);
            drive.crossing.land();
            assertNull(drive.crossing.rowsDesign());
            drive.crossing.end();
        }
    }

    @Test
    public void oneLayoutsRowsKeepTheOneDesign() {
        for (PaneWallPage from : BOTH) {
            Drive drive = oneLayout(from);
            int n = Math.round(1f / SLOW);
            for (int i = 0; i <= n; i++) {
                Step step = drive.step(i / (float) n);
                if (step.active)
                    assertEquals(DockStyle.GLASS_TRAY, drive.crossing.rowsDesign());
            }
        }
    }
}
