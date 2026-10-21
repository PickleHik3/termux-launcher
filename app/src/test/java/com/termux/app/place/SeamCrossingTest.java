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

    private static final WallHalfShape WORKSPACE_SHAPE = WallHalfShape.of(LayoutStyle.DOCKED, 12, 8);
    private static final WallHalfShape HOME_SHAPE = WallHalfShape.of(LayoutStyle.FLOATING, 24, 16);

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
            return home ? HOME_SHAPE : WORKSPACE_SHAPE;
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
        float fraction, progress, dockReveal, keyboardReveal, extraKeysShare, beadShare;
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
                boolean first = crossing.begin(here, there, stage);
                if (begins == 1) barFirst = first;
            }
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            if (answers.relayout()) crossing.standLayout();
            boolean rides = crossing.rideBar(frame, true);
            Step step = new Step();
            step.fraction = fraction;
            step.active = crossing.other() != null;
            step.progress = answers.progress();
            step.dockReveal = answers.dockReveal();
            step.keyboardReveal = answers.keyboardReveal();
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
            assertFalse(step.bandsMoved);
            assertTrue(Float.isNaN(step.beadShare));
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

    // ---- (b) Home layout on, a design and a shape of its own ----

    private static Stored homeLayoutOn() {
        Stored stored = new Stored();
        stored.homeLayout = true;
        stored.designs.put(LayoutVariant.HOME, DockStyle.WALL_STRIP);
        return stored;
    }

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
    public void homeLayoutOnSwapsTheDressOnlyWhileHiddenFromTheWindow() {
        Drive drive = drive(homeLayoutOn(), PaneWallPage.TERMINAL, window(), false,
            PaneWallPage.WIDGETS, home(), false, false).slow(0f, 1f);
        assertDressSwapsOnlyWhileHidden(drive);
        assertEquals(1, drive.count(s -> s.restyle));
        for (Step step : drive.steps) {
            if (step.restyle) {
                assertTrue(step.restyleShape);
                assertTrue(step.progress >= SeamTravel.DIP_SWAP);
            }
            // The whole stack dips: down to the swap, up after it.
            assertEquals(SeamTravel.dockReveal(SeamTravel.DockPlan.DIP, step.progress),
                step.dockReveal, EPSILON);
            assertFalse(step.folds);
            assertTrue(Float.isNaN(step.extraKeysShare));
            assertFalse("a whole-stack dip moves by the translation", step.rowsSink);
        }
        assertSame(PaneWallPage.WIDGETS, drive.last().stackDressPage);
        assertEquals(SeamTravel.withDockOf(window(), home()), drive.last().layout);
        // The Workspace's bar rides with its page, dressed for the window, from the first frame.
        assertTrue(drive.steps.get(1).rides);
        assertEquals(1, drive.count(s -> s.barRedressed));
        assertFalse(drive.barFirst);
    }

    @Test
    public void homeLayoutOnEasesTheStripBandWithThePage() {
        Drive drive = drive(homeLayoutOn(), PaneWallPage.TERMINAL, window(), false,
            PaneWallPage.WIDGETS, home(), false, false).slow(0f, 1f);
        float previous = 0f;
        for (Step step : drive.steps) {
            // Home wears the Wall strip and the window does not: the row follows Home's share.
            assertEquals(Math.max(0f, Math.min(1f, step.progress)), step.stripShare, EPSILON);
            assertTrue(step.stripShare >= previous - EPSILON);
            assertEquals("no Search field on either half", 0f, step.fieldShare, EPSILON);
            previous = step.stripShare;
        }
        assertTrue(drive.count(s -> s.bandsMoved) > 10);
        assertEquals(1f, drive.last().stripShare, EPSILON);
        SeamCrossing.Landing landing = drive.crossing.land();
        assertTrue(landing.bandsMoved());
        // Landed, the band is the committed design's again until the commit dresses the page.
        assertEquals(0f, drive.crossing.designShare(DockStyle.WALL_STRIP, 0f), EPSILON);
        assertNull(drive.crossing.committedDesign());
    }

    @Test
    public void homeLayoutOnReversedMidWaySwapsBackWhileHidden() {
        Drive drive = drive(homeLayoutOn(), PaneWallPage.TERMINAL, window(), false,
            PaneWallPage.WIDGETS, home(), false, false).slow(0f, 0.7f).slow(0.7f, 0f);
        assertDressSwapsOnlyWhileHidden(drive);
        assertEquals(2, drive.count(s -> s.restyle));
        assertNull(drive.last().stackDressPage);
        assertNull(drive.last().layout);
        assertEquals(0f, drive.last().stripShare, EPSILON);
        SeamCrossing.Landing landing = drive.crossing.land();
        drive.crossing.end();
        assertFalse("came back through the swap: nothing to dress back",
            landing.redressesBack(PaneWallPage.TERMINAL));
    }

    @Test
    public void homeLayoutOnFastFlingThatSkipsTheWayBackDressesBackAtSettle() {
        // The drawer or reduced motion took the frames back past the swap: settle dresses back.
        Drive drive = drive(homeLayoutOn(), PaneWallPage.TERMINAL, window(), false,
            PaneWallPage.WIDGETS, home(), false, false).run(0f, 0.3f, 0.9f);
        assertEquals(1, drive.count(s -> s.restyle));
        SeamCrossing.Landing landing = drive.crossing.land();
        drive.crossing.end();
        assertTrue(landing.redressesBack(PaneWallPage.TERMINAL));
        assertTrue(landing.shapeDiffered());
        assertFalse(landing.redressesBack(PaneWallPage.WIDGETS));
    }

    @Test
    public void homeLayoutOnFromHomeTakesTheWorkspaceBarDressFirst() {
        Drive drive = drive(homeLayoutOn(), PaneWallPage.WIDGETS, homeWithoutBar(), false,
            PaneWallPage.TERMINAL, window(), false, false).slow(0f, 1f);
        assertTrue("the bar takes off Home's dress while it is still hidden", drive.barFirst);
        assertDressSwapsOnlyWhileHidden(drive);
        // The Workspace's bar is stood over Home from the first frame into the window's half.
        Step first = null;
        for (Step step : drive.steps) if (step.relayout && first == null) first = step;
        assertNotNull(first);
        assertTrue(first.progress > 0f);
        assertEquals(SeamTravel.withStatusOf(homeWithoutBar(), window()), first.layout);
        assertEquals(SeamTravel.withStatusOf(SeamTravel.withDockOf(homeWithoutBar(), window()),
            window()), drive.last().layout);
        // The bar rides with the window's page while any of Home shows, and stands at its rest
        // once the frame is the window's alone.
        assertTrue(drive.steps.get(drive.steps.size() / 2).rides);
        assertFalse(drive.last().rides);
    }

    @Test
    public void homeLayoutOnMeltsTheBeadsAsHomeLeaves() {
        Stored stored = homeLayoutOn();
        stored.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
        Drive drive = drive(stored, PaneWallPage.WIDGETS, home(), false, PaneWallPage.TERMINAL,
            window(), false, true).slow(SLOW, 0.5f).slow(0.5f, 0f);
        float previous = 1f;
        boolean turned = false;
        for (Step step : drive.steps) {
            assertFalse("a melt while one half draws beads at " + step.fraction,
                Float.isNaN(step.beadShare));
            // Home's rows leave: the beads melt over the wall's first stretch, while the chrome
            // still rests, and come back out on the way back.
            if (step.fraction >= PlaceChromeTravel.LANDING) assertEquals(0f, step.beadShare, EPSILON);
            if (!turned && step.beadShare > previous + EPSILON) turned = true;
            if (!turned) assertTrue(step.beadShare <= previous + EPSILON);
            previous = step.beadShare;
        }
        assertEquals(1f, drive.last().beadShare, EPSILON);
    }

    @Test
    public void theSearchFieldKeepsHomesBeadsSoTheyMeltInAsHomeArrives() {
        // #84: Lens beads with the Search field keep their beads (the field in a capsule of its
        // own under them), so a slide in from the window's slab melts them in as Home comes.
        Stored stored = homeLayoutOn();
        stored.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
        stored.searchFields.put(LayoutVariant.HOME, true);
        // Home's row alone on its dock (the index put away), where it wears beads.
        PlaceLayout beadedHome = home().withSlot(Element.AZ,
            Slot.on(Edge.BOTTOM, Element.AZ).withHidden(true));
        Drive drive = drive(stored, PaneWallPage.TERMINAL, window(), false, PaneWallPage.WIDGETS,
            beadedHome, false, false).slow(0f, 1f);
        assertTrue("the beads melt in with Home's half",
            drive.count(s -> !Float.isNaN(s.beadShare)) > 0);
        float last = Float.NaN;
        for (Step step : drive.steps) if (!Float.isNaN(step.beadShare)) last = step.beadShare;
        assertEquals("whole beads once Home has come", 1f, last, 0.05f);
    }

    // ---- (c) Minimal mode on in a window ----

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
}
