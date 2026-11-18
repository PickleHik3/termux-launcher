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
import com.termux.app.dock.WornDockDesign;
import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceChromeTravel.Rest;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PageRef;
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
    /** The steepest the look's curve climbs per unit of the wall's way (a smoothstep over SeamFade's stretch). */
    private static final float MAX_CURVE_SLOPE = 1.5f / (SeamFade.END - SeamFade.START);


    /** Home and the Workspace's window beside it: the two halves of the wall. */
    private static final List<PageKind> ROW =
        Arrays.asList(PageKind.HOME, PageKind.TERMINAL);

    /** The one Style, Corners and Margin both halves wear, a Home layout of its own too (#84). */
    private static final WallShape SHAPE = WallShape.of(LayoutStyle.DOCKED, 12, 8);

    /** Both directions across the seam: from the window, and from Home. */
    private static final PageKind[] BOTH = {PageKind.TERMINAL, PageKind.HOME};

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

    /** The Home page in front's own layout: what the old Home layout switch stood for. */
    private static final LayoutVariant HOME_PAGE =
        LayoutVariant.HOME;

    private static final class Stored implements DressResolver.Source {
        boolean minimal;
        boolean homeLayout;
        final java.util.Map<LayoutVariant, DockStyle> designs = new java.util.HashMap<>();
        final java.util.Map<LayoutVariant, Boolean> searchFields = new java.util.HashMap<>();

        Stored() {
            for (LayoutVariant variant : LayoutVariant.values()) {
                designs.put(variant, DockStyle.GLASS_TRAY);
                searchFields.put(variant, false);
            }
        }

        @Override public boolean minimal() { return minimal; }
        // homeLayout: the Home page in front (id 2) holds a page layout of its own.
        @Override public int pageIdOf(@NonNull PageKind page) {
            return page == PageKind.HOME ? 2 : 1;
        }
        @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant variant) {
            return designs.getOrDefault(variant, DockStyle.GLASS_TRAY);
        }
        @Override public boolean searchField(@NonNull LayoutVariant variant) {
            return searchFields.getOrDefault(variant, false);
        }
        @Override public boolean drawerTile(@NonNull LayoutVariant variant) { return false; }
        @Override public boolean shows(@NonNull com.termux.app.page.PageKind kind,
                                       @NonNull Element element) {
            return kind != com.termux.app.page.PageKind.HOME
                || element != Element.EXTRA_KEYS;
        }
        @NonNull @Override public WallShape shape() {
            // Linked: the preferences answer the Workspace's on either half.
            return SHAPE;
        }
        /** Home's own Margin, held per kind; null: the Workspace's. */
        @androidx.annotation.Nullable WallShape homeShape;
        @NonNull @Override public WallShape shape(
                @NonNull com.termux.app.page.PageKind kind) {
            return kind == com.termux.app.page.PageKind.HOME && homeShape != null
                ? homeShape : SHAPE;
        }
    }

    /** Each layout's sizes as a test sets them: the minimal layout's dock is shorter. */
    private static SeamDress.StackDress stackOf(@NonNull Dress dress) {
        float dock = dress.layout() == LayoutVariant.MINIMAL ? 0.8f : 1f;
        return SeamDress.StackDress.of(dress.shape(), dock, 1f, 0);
    }

    private static SeamCrossing.Endpoint half(@NonNull DressResolver dresses, @NonNull PageKind page,
                                          @NonNull PlaceLayout layout, boolean keyboardUp) {
        Dress dress = dresses.dressOf(page);
        return SeamCrossing.Endpoint.of(PageRef.of(page), dress, layout, stackOf(dress), keyboardUp);
    }

    // ---- The drive: the activity's frame protocol ----

    /** What one frame answered, copied out of the crossing's reused answers. */
    private static final class Step {
        float fraction, progress, dockReveal, keyboardReveal, frameKeyboardReveal;
        float beadShare;
        float stripShare, fieldShare;
        float barMarginShare, barMarginDp, lookShare;
        DockStyle stackDesign;
        boolean active, rowsSink, relayout, restyle, restyleShape, bandsMoved, rides;
        boolean barRedressed;
        PageKind stackDressPage;
        PlaceLayout layout;
    }

    private static final class Drive {
        final SeamCrossing crossing = new SeamCrossing();
        final PageKind committed;
        final SeamCrossing.Endpoint here, there;
        final SeamCrossing.Stage stage;
        final PlaceChromeTravel.States states;
        final List<Step> steps = new ArrayList<>();
        int begins;
        boolean barFirst;

        Drive(@NonNull SeamCrossing.Endpoint here, @NonNull SeamCrossing.Endpoint there,
              @NonNull SeamCrossing.Stage stage) {
            this.here = here;
            this.there = there;
            this.stage = stage;
            this.committed = here.page.kind;
            boolean hereKeyboard = here.keyboardUp, thereKeyboard = there.keyboardUp;
            this.states = place -> new Rest(place == committed ? hereKeyboard : thereKeyboard,
                false, true);
        }

        /** The frame {@code fraction} of the way from the committed page to the other half. */
        Frame frameAt(float fraction) {
            float offset = committed == PageKind.HOME ? -fraction * WIDTH : fraction * WIDTH;
            return PlaceChromeTravel.at(ROW, committed, offset, WIDTH, states);
        }

        Step step(float fraction) {
            Frame frame = frameAt(fraction);
            PageKind other = SeamTravel.otherHalfPage(frame, committed);
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
            step.beadShare = answers.beadShare();
            step.barMarginShare = answers.barMarginShare();
            step.barMarginDp = crossing.barMarginDp(answers.barMarginShare());
            step.lookShare = answers.lookShare();
            step.stackDesign = crossing.stackDesign();
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
                worn(here) == DockStyle.WALL_STRIP ? 1f : 0f);
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

        /** The Workspace's end of a crossing that started on Home or not. */
        SeamCrossing.Endpoint window(boolean fromHome) {
            return fromHome ? there : here;
        }

        /** Home's end of it. */
        SeamCrossing.Endpoint home(boolean fromHome) {
            return fromHome ? here : there;
        }
    }

    private static Drive drive(@NonNull Stored stored, @NonNull PageKind from,
                               @NonNull PlaceLayout fromLayout, boolean fromKeyboard,
                               @NonNull PageKind to, @NonNull PlaceLayout toLayout,
                               boolean toKeyboard, boolean beadsDrawn) {
        DressResolver dresses = new DressResolver(stored);
        return new Drive(half(dresses, from, fromLayout, fromKeyboard),
            half(dresses, to, toLayout, toKeyboard),
            SeamCrossing.Stage.of(beadsDrawn, () -> false, true));
    }

    // ---- (a) Equal dresses: Home on the one layout ----

    private static Drive oneLayout(@NonNull PageKind from) {
        Stored stored = new Stored();
        return from == PageKind.HOME
            ? drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL, window(),
                false, false)
            : drive(stored, PageKind.TERMINAL, window(), false, PageKind.HOME, home(),
                false, false);
    }

    /**
     * Halves whose rows differ and stand them both keep the stack as it stands to the landing:
     * nothing is laid out, dressed or sunk on the way, the rows stay whole and the keyboard
     * follows the frame's own reveal.
     */
    private static void assertNothingStandsMidWay(@NonNull Drive drive) {
        for (Step step : drive.steps) {
            assertFalse("nothing to lay out at " + step.fraction, step.relayout);
            assertFalse("nothing to dress at " + step.fraction, step.restyle);
            assertFalse("nothing sinks at " + step.fraction, step.rowsSink);
            assertNull(step.layout);
            assertNull(step.stackDressPage);
            assertEquals("the rows stay whole at " + step.fraction, 1f, step.dockReveal, EPSILON);
            assertEquals("the keyboard keeps its own travel at " + step.fraction,
                step.frameKeyboardReveal, step.keyboardReveal, EPSILON);
        }
        assertFalse(drive.barFirst);
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

    // ---- (b) Home's own layout on: the sizes and the shape linked to the shared layout (#84) ----
    //
    // A Home layout of its own differs from the shared one only in which rows it stands, their
    // order and edge, its Dock design and its toggles: the stack's dress (Style, Corners, Margin,
    // the dock's and the keyboard's sizes) is one on both halves. So the rows cross by how they
    // differ, a swap while they are hidden, a fold or nothing, and the stack is never dressed
    // again mid-slide; a Dock design crosses by its bands and beads, which follow the page.



    /** Home's own layout with the index put away: only the window stands the A-Z row. */
    private static Stored homeLayoutOn() {
        Stored stored = new Stored();
        stored.homeLayout = true;
        return stored;
    }

    /** Home's own layout with the pinned apps put away: the index alone on its dock. */
    private static PlaceLayout homeWithoutApps() {
        return home().withSlot(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS).withHidden(true));
    }

    private static PlaceLayout homeWithoutAz() {
        return home().withSlot(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ).withHidden(true));
    }

    /** A crossing between the window and Home's own {@code homeLayout}, from {@code from}. */
    private static Drive homeOwn(@NonNull Stored stored, @NonNull PageKind from,
                                 @NonNull PlaceLayout homeLayout, boolean windowKeyboard) {
        return from == PageKind.HOME
            ? drive(stored, PageKind.HOME, homeLayout, false, PageKind.TERMINAL,
                window(), windowKeyboard, false)
            : drive(stored, PageKind.TERMINAL, window(), windowKeyboard, PageKind.HOME,
                homeLayout, false, false);
    }


    /** The design {@code half}'s slab wears: none where its layout shows no apps row. */
    private static DockStyle worn(@NonNull SeamCrossing.Endpoint half) {
        return WornDockDesign.of(half.dress.dockDesign(), half.layout);
    }

    /** The share of a band only {@code design} carries that {@code half}'s dock rests with. */
    private static float restShare(@NonNull SeamCrossing.Endpoint half, @NonNull DockStyle design) {
        return worn(half) == design ? 1f : 0f;
    }


    /** The bands and the Search field the other half rests with, drawn by the last frame. */
    private static void assertBandsLandOnTheOtherHalfsRest(@NonNull Drive drive) {
        Step last = drive.last();
        assertEquals(1f, last.progress, EPSILON);
        assertEquals("all of the rows showing", 1f, last.dockReveal, EPSILON);
        assertEquals("the Wall strip's row as the other half rests",
            restShare(drive.there, DockStyle.WALL_STRIP), last.stripShare, EPSILON);
        assertEquals("the Search field as the other half rests",
            drive.there.dress.searchField() ? 1f : 0f, last.fieldShare, EPSILON);
        assertEquals("the stack is dressed alike on both halves", drive.here.stack,
            drive.there.stack);
    }

    @Test
    public void homesOwnWithoutTheAppsRowKeepsTheStackToTheLandingBothWays() {
        // The rows differ and both halves stand them: they swap at the landing, not on the way.
        for (PageKind from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutApps(), false).slow(0f, 1f);
            assertNothingStandsMidWay(drive);
            assertBandsLandOnTheOtherHalfsRest(drive);
            SeamCrossing.Landing landing = drive.crossing.land();
            assertFalse("the rows are laid out by the landing's own pass", landing.laidOut());
            drive.crossing.end();
            assertFalse(landing.redressesBack(PageKind.TERMINAL));
            assertFalse(landing.redressesBack(PageKind.HOME));
        }
    }

    @Test
    public void homesOwnWithoutTheAppsRowAndTheWindowsKeyboardSinkNothing() {
        for (PageKind from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutApps(), true).slow(0f, 1f);
            assertNothingStandsMidWay(drive);
            assertBandsLandOnTheOtherHalfsRest(drive);
        }
    }

    @Test
    public void homesOwnWithoutTheAppsRowReversedMidWayComesBackAsItLeft() {
        for (PageKind from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutApps(), false)
                .slow(0f, 0.7f).slow(0.7f, 0f);
            assertNothingStandsMidWay(drive);
            assertNull(drive.last().layout);
            assertNull(drive.last().stackDressPage);
            assertEquals(1f, drive.last().dockReveal, EPSILON);
            SeamCrossing.Landing landing = drive.crossing.land();
            assertFalse(landing.laidOut());
            drive.crossing.end();
            assertFalse(landing.redressesBack(from));
        }
    }

    @Test
    public void homesOwnFlingThatSkipsTheWayBackLaysNothingOutAtSettle() {
        // The drawer or reduced motion took the frames back: the settle has nothing to put back,
        // for the crossing laid nothing out and dressed nothing.
        for (PageKind from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutApps(), false)
                .run(0f, 0.3f, 0.9f);
            assertEquals(0, drive.count(s -> s.restyle));
            SeamCrossing.Landing landing = drive.crossing.land();
            assertFalse(landing.laidOut());
            drive.crossing.end();
            assertFalse(landing.redressesBack(from));
            assertNull(drive.crossing.stackDressPage());
        }
    }

    @Test
    public void homesOwnDesignEasesTheStripWithThePageAndStandsNothing() {
        // Another Dock design on Home, the same rows less the extra keys: the Wall strip's row
        // follows Home's share, and nothing is laid out or dressed on the way.
        for (PageKind from : BOTH) {
            Stored stored = homeLayoutOn();
            stored.designs.put(HOME_PAGE, DockStyle.WALL_STRIP);
            Drive drive = homeOwn(stored, from, home(), false).slow(0f, 1f);
            assertNothingStandsMidWay(drive);
            float previous = Float.NaN;
            for (Step step : drive.steps) {
                float homeShare = from == PageKind.HOME
                    ? 1f - clamp01(step.progress) : clamp01(step.progress);
                assertEquals("the strip follows Home at " + step.fraction, homeShare,
                    step.stripShare, EPSILON);
                if (!Float.isNaN(previous)) {
                    assertTrue("one way at " + step.fraction, from == PageKind.HOME
                        ? step.stripShare <= previous + EPSILON
                        : step.stripShare >= previous - EPSILON);
                }
                previous = step.stripShare;
                assertEquals("no Search field on either half", 0f, step.fieldShare, EPSILON);
            }
            assertTrue(drive.count(s -> s.bandsMoved) > 10);
            assertBandsLandOnTheOtherHalfsRest(drive);
            SeamCrossing.Landing landing = drive.crossing.land();
            assertTrue(landing.bandsMoved());
            // Landed, the band is the committed design's again until the commit dresses the page.
            assertEquals(0f, drive.crossing.designShare(DockStyle.WALL_STRIP, 0f), EPSILON);
            assertNull(drive.crossing.committedDesign());
            drive.crossing.end();
        }
    }

    @Test
    public void homesOwnDesignWithoutTheAppsRowEasesTheStripAndKeepsTheRowsToTheLanding() {
        for (PageKind from : BOTH) {
            Stored stored = homeLayoutOn();
            stored.designs.put(HOME_PAGE, DockStyle.WALL_STRIP);
            stored.searchFields.put(HOME_PAGE, true);
            Drive drive = homeOwn(stored, from, homeWithoutApps(), false).slow(0f, 1f);
            assertNothingStandsMidWay(drive);
            assertTrue(drive.count(s -> s.bandsMoved) > 10);
            assertBandsLandOnTheOtherHalfsRest(drive);
        }
    }

    @Test
    public void homesOwnLensBeadsMeltIntoTheSlabAsHomeLeavesAndBackOut() {
        Stored stored = homeLayoutOn();
        stored.designs.put(HOME_PAGE, DockStyle.LENS_BEADS);
        Drive drive = drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL,
            window(), false, true).slow(0f, 1f);
        assertNothingStandsMidWay(drive);
        float previous = 1f;
        for (Step step : drive.steps) {
            if (!step.active) continue;
            // The rows stay up: the beads melt over the way, as Home leaves.
            assertEquals("Home's share at " + step.fraction, 1f - clamp01(step.progress),
                step.beadShare, EPSILON);
            assertTrue(step.beadShare <= previous + EPSILON);
            previous = step.beadShare;
        }
        assertEquals("the window's slab at the end", 0f, drive.last().beadShare, EPSILON);
        assertBandsLandOnTheOtherHalfsRest(drive);
        Drive back = drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL,
            window(), false, true).slow(0f, 0.6f).slow(0.6f, 0f);
        assertEquals("whole beads back on Home", 1f, back.last().beadShare, EPSILON);
    }

    @Test
    public void theSearchFieldKeepsHomesBeadsSoTheyMeltInAsHomeArrives() {
        // #84: Lens beads with the Search field keep their beads (the field in a capsule of its
        // own under them), so a slide in from the window's slab melts them in as Home comes.
        Stored stored = homeLayoutOn();
        stored.designs.put(HOME_PAGE, DockStyle.LENS_BEADS);
        stored.searchFields.put(HOME_PAGE, true);
        // Home's row alone on its dock (the index put away), where it wears beads.
        Drive drive = drive(stored, PageKind.TERMINAL, window(), false, PageKind.HOME,
            homeWithoutAz(), false, false).slow(0f, 1f);
        assertTrue("the beads melt in with Home's half",
            drive.count(s -> !Float.isNaN(s.beadShare)) > 0);
        float last = Float.NaN;
        for (Step step : drive.steps) if (!Float.isNaN(step.beadShare)) last = step.beadShare;
        assertEquals("whole beads once Home has come", 1f, last, 0.05f);
        assertBandsLandOnTheOtherHalfsRest(drive);
    }

    @Test
    public void homesOwnWithoutTheAzRowKeepsTheStackToTheLandingBothWays() {
        for (PageKind from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, homeWithoutAz(), false).slow(0f, 1f);
            assertNothingStandsMidWay(drive);
            assertBandsLandOnTheOtherHalfsRest(drive);
            Drive back = homeOwn(homeLayoutOn(), from, homeWithoutAz(), false)
                .slow(0f, 0.7f).slow(0.7f, 0f);
            assertNothingStandsMidWay(back);
            assertNull(back.last().layout);
        }
    }

    @Test
    public void homesOwnWithOnlyTheExtraKeysApartStandsStillAsTheSharedLayoutDoes() {
        for (PageKind from : BOTH) {
            Drive drive = homeOwn(homeLayoutOn(), from, home(), false).slow(0f, 1f);
            assertNothingStandsMidWay(drive);
            assertBandsLandOnTheOtherHalfsRest(drive);
            Drive fling = homeOwn(homeLayoutOn(), from, home(), false).run(0f, 0.3f, 0.9f, 1f);
            assertNothingStandsMidWay(fling);
            assertBandsLandOnTheOtherHalfsRest(fling);
            Drive back = homeOwn(homeLayoutOn(), from, home(), false)
                .slow(0f, 0.6f).slow(0.6f, 0f);
            assertNothingStandsMidWay(back);
        }
    }

    @Test
    public void homesOwnWithoutTheBarLaysTheWorkspacesBarOverHomeFromTheFirstFrame() {
        Drive drive = drive(homeLayoutOn(), PageKind.HOME, homeWithoutBar(), false,
            PageKind.TERMINAL, window(), false, false).slow(0f, 1f);
        // One shape across the wall: the bar has no dress of Home's to take off first.
        assertFalse(drive.barFirst);
        for (Step step : drive.steps) {
            assertFalse("nothing to dress again at " + step.fraction, step.restyle);
            assertEquals("the rows stay whole at " + step.fraction, 1f, step.dockReveal, EPSILON);
        }
        // The window's bar is stood over Home from the first frame into the window's half, once.
        assertEquals(1, drive.count(s -> s.relayout));
        Step first = null;
        for (Step step : drive.steps) if (step.relayout && first == null) first = step;
        assertNotNull(first);
        assertTrue(first.progress > 0f);
        PlaceLayout stood = SeamTravel.withStatusOf(homeWithoutBar(), window());
        assertEquals(stood, first.layout);
        assertEquals(stood, drive.last().layout);
        // The bar rides with the window's page while any of Home shows, and stands at its rest
        // once the frame is the window's alone.
        assertTrue(drive.steps.get(drive.steps.size() / 2).rides);
        assertFalse(drive.last().rides);
        assertBandsLandOnTheOtherHalfsRest(drive);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }

    // ---- (c) Minimal mode on in a window ----

    @Test
    public void minimalWindowReversedMidWayComesBackDressedAsItLeft() {
        Stored stored = new Stored();
        stored.minimal = true;
        // The window's minimal layout dresses the stack apart from Home's (its sizes): the
        // stack is neither laid out nor dressed on the way, out or back.
        Drive drive = drive(stored, PageKind.TERMINAL, minimalWindow(), true,
            PageKind.HOME, home(), false, false).slow(0f, 0.8f).slow(0.8f, 0f);
        assertNothingStandsMidWay(drive);
        assertEquals(1f, drive.last().keyboardReveal, EPSILON);
        assertEquals(1f, drive.last().dockReveal, EPSILON);
        assertNull(drive.last().stackDressPage);
        assertFalse(drive.crossing.land().redressesBack(PageKind.HOME));
    }

    /**
     * Home and the Workspace hold different Margins (Margin is per kind) and stand the bar alike:
     * the bar stays on screen through the crossing, either way, rather than riding off with the
     * Workspace's page and leaving Home without one until the landing. The landing's half re-dress
     * insets it for the half it lands on.
     */
    @Test
    public void aBarBothHalvesStandAlikeStaysOnScreenWhenOnlyTheirMarginsDiffer() {
        for (PageKind from : BOTH) {
            Stored stored = new Stored();
            stored.homeShape = WallShape.of(SHAPE.style, SHAPE.cornersDp, SHAPE.marginDp + 8);
            Drive drive = from == PageKind.HOME
                ? drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL,
                    window(), false, false)
                : drive(stored, PageKind.TERMINAL, window(), false, PageKind.HOME,
                    home(), false, false);
            drive.slow(0f, 1f);
            assertFalse("from " + from + ": no dress of Home's to take off a bar on screen",
                drive.barFirst);
            for (Step step : drive.steps) {
                assertFalse("from " + from + ": the bar stands at " + step.fraction, step.rides);
                if (step.layout != null) {
                    assertFalse(step.layout.slot(Element.STATUS).hidden);
                }
            }
            // The stack is not dressed again on the way: the bar eases, the landing re-dresses.
            assertEquals(0, drive.count(s -> s.restyle));
        }
    }

    /**
     * A still bar between two Margins eases from the committed page's to the arriving page's with
     * the wall (amendment 2026-11-04): the frame the wall lands on draws it at the arriving
     * Margin, so the landing's re-dress has nothing to show.
     */
    @Test
    public void aStillBarEasesItsMarginWithTheWall() {
        for (PageKind from : BOTH) {
            Stored stored = new Stored();
            stored.homeShape = WallShape.of(SHAPE.style, SHAPE.cornersDp, SHAPE.marginDp + 8);
            Drive drive = from == PageKind.HOME
                ? drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL,
                    window(), false, false)
                : drive(stored, PageKind.TERMINAL, window(), false, PageKind.HOME,
                    home(), false, false);
            drive.slow(0f, 1f);
            int here = from == PageKind.HOME ? SHAPE.marginDp + 8 : SHAPE.marginDp;
            int there = from == PageKind.HOME ? SHAPE.marginDp : SHAPE.marginDp + 8;
            float last = -1f;
            for (Step step : drive.steps) {
                if (!step.active) continue;
                assertFalse("from " + from + ": eases at " + step.fraction,
                    Float.isNaN(step.barMarginShare));
                assertTrue("from " + from + ": never goes back", step.barMarginShare >= last);
                assertEquals(here + (there - here) * step.barMarginShare, step.barMarginDp,
                    EPSILON);
                last = step.barMarginShare;
            }
            assertEquals("from " + from + ": lands at the arriving Margin", 1f,
                drive.last().barMarginShare, EPSILON);
            assertEquals(there, drive.last().barMarginDp, EPSILON);
            // And back the other way, the committed page's Margin again.
            drive.slow(1f, 0f);
            assertEquals(here, drive.last().barMarginDp, EPSILON);
        }
    }

    /**
     * The landing changes nothing the last moving frame drew (amendment 2026-11-04): driven to
     * that frame between halves with Margins apart, the bar was drawn at the landed page's Margin,
     * which the landing's re-dress lays it out at; the bar never rode, so nothing puts it back;
     * and the same holds for a slide that came back, at the committed page's Margin.
     */
    @Test
    public void theLandingChangesNothingTheLastFrameDrew() {
        for (PageKind from : BOTH) {
            for (boolean comesBack : new boolean[] {false, true}) {
                Stored stored = new Stored();
                stored.homeShape = WallShape.of(SHAPE.style, SHAPE.cornersDp, SHAPE.marginDp + 8);
                boolean fromHome = from == PageKind.HOME;
                Drive drive = fromHome
                    ? drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL,
                        window(), false, false)
                    : drive(stored, PageKind.TERMINAL, window(), false, PageKind.HOME,
                        home(), false, false);
                drive.slow(0f, 1f);
                if (comesBack) drive.slow(1f, 0f);
                Step last = drive.last();
                PageKind landed = comesBack ? from : drive.there.page.kind;
                int landedMargin = (landed == drive.here.page.kind ? drive.here : drive.there)
                    .stack.shape.marginDp;
                String why = "from " + from + (comesBack ? ", back" : "");
                assertFalse(why + ": the re-dress moves nothing",
                    SeamCrossing.landingMovesBar(last.barMarginDp, landedMargin));
                SeamCrossing.Landing landing = drive.crossing.land();
                assertFalse(why + ": the bar never rode", landing.barTravelled());
                assertFalse(why + ": nothing to dress back", landing.redressesBack(landed));
            }
        }
    }

    /** One end of a crossing whose stack wears the dock at {@code dockOpacity}, nothing else apart. */
    private static SeamCrossing.Endpoint lookEnd(@NonNull DressResolver dresses,
                                                 @NonNull PageKind page, int dockOpacity) {
        Dress dress = dresses.dressOf(page);
        PlaceLayout layout = page == PageKind.HOME ? home() : window();
        return SeamCrossing.Endpoint.of(PageRef.of(page), dress, layout,
            SeamDress.StackDress.of(dress.shape(), 1f, 1f, 0, HalfLook.ofDockOpacity(dockOpacity)),
            false);
    }

    /**
     * Halves apart only in look (amendment 2026-11-04): the stack moves exactly as between halves
     * dressed alike (no dip, no re-dress mid-slide), its look blends with the wall in steps to the
     * landed end, and the landing's re-dress is one the landed frame has nothing to show from, so
     * it follows that frame instead of holding it (58-88 ms on pong). A slide that came back has
     * blended back and commits nothing.
     */
    @Test
    public void aLookApartAloneBlendsAndItsLandingFollowsTheFrame() {
        for (PageKind from : BOTH) {
            for (boolean comesBack : new boolean[] {false, true}) {
                PageKind to = from == PageKind.HOME ? PageKind.TERMINAL
                    : PageKind.HOME;
                DressResolver dresses = new DressResolver(new Stored());
                Drive drive = new Drive(lookEnd(dresses, from, 20), lookEnd(dresses, to, 90),
                    SeamCrossing.Stage.of(false, () -> false, true));
                Drive alike = oneLayout(from);
                drive.slow(0f, 1f);
                alike.slow(0f, 1f);
                if (comesBack) {
                    drive.slow(1f, 0f);
                    alike.slow(1f, 0f);
                }
                String why = "from " + from + (comesBack ? ", back" : "");
                float last = Float.NaN;
                for (int i = 0; i < drive.steps.size(); i++) {
                    Step step = drive.steps.get(i), plain = alike.steps.get(i);
                    assertEquals(why + ": no dip", plain.dockReveal, step.dockReveal, EPSILON);
                    assertEquals(why, plain.keyboardReveal, step.keyboardReveal, EPSILON);
                    assertFalse(why + ": nothing dressed again mid-slide", step.restyle);
                    if (!step.active) continue;
                    assertFalse(why + ": blends", Float.isNaN(step.lookShare));
                    assertEquals(why + ": in steps", 0f,
                        step.lookShare * SeamCrossing.LOOK_STEPS
                            - Math.round(step.lookShare * SeamCrossing.LOOK_STEPS), EPSILON);
                    last = step.lookShare;
                }
                assertEquals(why + ": the last frame shows the landed look", comesBack ? 0f : 1f,
                    last, EPSILON);
                // The glass the last moving frame drew is the landed end's, which the landing's
                // re-dress builds: the dock's opacity (20 here, 90 there) and, read the same
                // way, its tint strength (the sheet's crossfade stands wholly on that end).
                assertEquals(why + ": opacity", comesBack ? 20 : 90,
                    SeamCrossing.lookBlendValue(20, 90, last));
                assertEquals(why + ": tint", comesBack ? 35 : 70,
                    SeamCrossing.lookBlendValue(35, 70, last));
                PageKind landed = comesBack ? from : to;
                SeamCrossing.Landing landing = drive.crossing.land();
                assertFalse(why, landing.stackDressedFor(landed));
                assertFalse(why, landing.redressesBack(landed));
                assertEquals(why + ": the re-dress follows the landed frame", !comesBack,
                    landing.lookBlendedOnto(landed));
            }
        }
    }

    /** One Margin on both ends: nothing eases. */
    @Test
    public void oneMarginEasesNothing() {
        Drive drive = oneLayout(PageKind.HOME).slow(0f, 1f);
        for (Step step : drive.steps) assertTrue(Float.isNaN(step.barMarginShare));
    }

    /**
     * Under a page transition that turns the pages, the Workspace's bar of another Style fades in
     * place by the way toward the Workspace's page instead of riding a turned page (amendment
     * 2026-11-04).
     */
    @Test
    public void aBarApartFadesInPlaceWhileThePagesTurn() {
        for (PageKind from : BOTH) {
            Stored stored = new Stored();
            stored.homeShape = WallShape.of(LayoutStyle.FLOATING, SHAPE.cornersDp, SHAPE.marginDp);
            DressResolver dresses = new DressResolver(stored);
            boolean fromHome = from == PageKind.HOME;
            Drive drive = new Drive(
                half(dresses, from, fromHome ? home() : window(), false),
                half(dresses, fromHome ? PageKind.TERMINAL : PageKind.HOME,
                    fromHome ? window() : home(), false),
                SeamCrossing.Stage.of(false, () -> false, true, false));
            drive.slow(0f, 1f);
            SeamCrossing.Answers answers = drive.crossing.frame(drive.frameAt(1f), true, false,
                false);
            assertEquals("from " + from + ": the Workspace's bar shows there", fromHome ? 1f : 0f,
                answers.statusFade(), EPSILON);
            assertEquals("from " + from + ": never rides", 0, drive.count(st -> st.rides));
        }
    }

    /** A Style apart still reshapes the bar: it rides with the Workspace's page. */
    @Test
    public void aBarOfAnotherStyleStillRidesWithTheWorkspace() {
        Stored stored = new Stored();
        stored.homeShape = WallShape.of(LayoutStyle.FLOATING, SHAPE.cornersDp, SHAPE.marginDp);
        Drive drive = drive(stored, PageKind.TERMINAL, window(), false, PageKind.HOME,
            home(), false, false).slow(0f, 0.6f);
        assertTrue(drive.count(s -> s.rides) > 0);
    }

    // ---- Rest ----

    @Test
    public void aFrameThatNeverReachesTheOtherHalfAnswersTheTravelsOwnReveals() {
        SeamCrossing crossing = new SeamCrossing();
        Frame frame = PlaceChromeTravel.rest(PageKind.TERMINAL, new Rest(true, false));
        SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
        assertEquals(frame.dockReveal, answers.dockReveal(), EPSILON);
        assertEquals(frame.keyboardReveal, answers.keyboardReveal(), EPSILON);
        assertFalse(answers.relayout());
        assertFalse(answers.moved());
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
    public void theRowsAHalfStandsKeepTheCommittedHalfsDesignToTheLanding() {
        // The developer's phone: Home's own layout with the pinned apps and the index, the
        // Workspace with the extra keys alone. Since round 9 a half with no apps row wears no
        // design (WornDockDesign), so the halves differ by Home's Lens beads against the
        // Workspace's tray. The seam stands no other rows on the way (the rows swap at the
        // landing), so the rows keep laying out in the committed half's design.
        for (PageKind from : BOTH) {
            Stored stored = homeLayoutOn();
            stored.designs.put(HOME_PAGE, DockStyle.LENS_BEADS);
            stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
            boolean fromHome = from == PageKind.HOME;
            Drive drive = fromHome
                ? drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL,
                    keysOnlyWindow(), true, false)
                : drive(stored, PageKind.TERMINAL, keysOnlyWindow(), true,
                    PageKind.HOME, home(), false, false);
            DockStyle committed = fromHome ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY;
            assertNull("no crossing, no design", drive.crossing.rowsDesign());
            int n = Math.round(1f / SLOW);
            for (int i = 0; i <= n; i++) {
                Step step = drive.step(i / (float) n);
                if (!step.active) continue;
                String at = from + " at " + step.fraction;
                assertNull(at, step.layout);
                assertEquals(at, committed, drive.crossing.rowsDesign());
                assertEquals(at, committed, drive.crossing.committedDesign());
            }
            drive.crossing.land();
            assertNull(drive.crossing.rowsDesign());
            drive.crossing.end();
        }
    }

    /**
     * Pong, round 9: the Workspace stores Lens beads but shows the extra keys alone, Home wears
     * Peek glass. The crossing's designs are the resolver's for each page at rest, so the
     * Workspace's is the plain tray in both directions, and what the crossing carries to the
     * landed page is the design the settle resolves there.
     */
    @Test
    public void aDockOffWorkspaceCrossesWithTheDesignItsSlabWears() {
        for (PageKind from : BOTH) {
            Stored stored = homeLayoutOn();
            stored.designs.put(HOME_PAGE, DockStyle.PEEK_GLASS);
            stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
            boolean fromHome = from == PageKind.HOME;
            Drive drive = fromHome
                ? drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL,
                    keysOnlyWindow(), true, false)
                : drive(stored, PageKind.TERMINAL, keysOnlyWindow(), true, PageKind.HOME,
                    home(), false, false);
            assertSame(DockStyle.LENS_BEADS, drive.window(fromHome).dress.dockDesign());
            drive.slow(0f, 1f);
            // At rest on either page the resolver answers; the crossing holds the same answers.
            assertSame(from + ": the committed page", worn(drive.here),
                drive.crossing.committedDesign());
            assertSame(DockStyle.GLASS_TRAY, worn(drive.window(fromHome)));
            assertSame(DockStyle.PEEK_GLASS, worn(drive.home(fromHome)));
            // The rows stand as the committed page's to the landing, in its design.
            assertSame(from + ": the rows on the way", worn(drive.here),
                drive.crossing.rowsDesign());
            assertFalse("never dressed apart", drive.count(s -> s.restyle) > 0);
            drive.crossing.land();
            assertNull(drive.crossing.rowsDesign());
            drive.crossing.end();
        }
    }

    // ---- The dock's foot under the gesture pill ----


    /** The foot's keyboard over a drive's frames, the live keyboard up throughout. */
    private static List<Boolean> footKeyboards(@NonNull Drive drive, float... fractions) {
        List<Boolean> out = new ArrayList<>();
        Boolean previous = null;
        for (float fraction : fractions) {
            Step step = drive.step(fraction);
            boolean keyboard = drive.crossing.footKeyboardUp(true);
            // The foot is laid out by the geometry pass, which runs on the seam's relayout
            // frames: it changes only there, never on a frame of its own.
            if (previous != null && previous != keyboard)
                assertTrue("the foot changes on a relayout frame, " + fraction, step.relayout);
            if (previous != null && previous != keyboard)
                assertTrue("out of sight, " + fraction, step.dockReveal
                    <= 2f * SLOW * MAX_CURVE_SLOPE + EPSILON);
            previous = keyboard;
            out.add(keyboard);
        }
        return out;
    }

    private static float[] slowWay(float from, float to) {
        int n = Math.round(Math.abs(to - from) / SLOW);
        float[] out = new float[n + 1];
        for (int i = 0; i <= n; i++) out[i] = from + (to - from) * i / n;
        return out;
    }

    /**
     * The developer's crossing: the Workspace with its keyboard up and the extra keys alone, Home
     * with the pinned apps and the index, keyboard down. Both stand rows, so no other rows are
     * stood on the way and the foot answers the live keyboard throughout.
     */
    private static Drive footDrive(@NonNull PageKind from) {
        Stored stored = homeLayoutOn();
        stored.designs.put(HOME_PAGE, DockStyle.PEEK_GLASS);
        return from == PageKind.HOME
            ? drive(stored, PageKind.HOME, home(), false, PageKind.TERMINAL,
                keysOnlyWindow(), true, false)
            : drive(stored, PageKind.TERMINAL, keysOnlyWindow(), true, PageKind.HOME,
                home(), false, false);
    }

    /** A Workspace with no dock rows at all, the bar alone: Home's rows slide in over it. */
    private static PlaceLayout rowlessWindow() {
        return window()
            .withSlot(Element.APPS, Slot.on(Edge.BOTTOM, Element.APPS).withHidden(true))
            .withSlot(Element.AZ, Slot.on(Edge.BOTTOM, Element.AZ).withHidden(true))
            .withSlot(Element.EXTRA_KEYS,
                Slot.on(Edge.BOTTOM, Element.EXTRA_KEYS).withHidden(true));
    }

    /** The slide in: Home's rows rise over a window that stands none, keyboard down on Home. */
    private static Drive slideInDrive() {
        return drive(new Stored(), PageKind.TERMINAL, rowlessWindow(), false,
            PageKind.HOME, home(), false, false);
    }

    @Test
    public void theFootAnswersTheLiveKeyboardThroughoutWhenNoRowsAreStood() {
        for (PageKind from : BOTH) {
            Drive drive = footDrive(from);
            List<Boolean> keyboards = footKeyboards(drive, slowWay(0f, 1f));
            assertSame(SeamTravel.DockPlan.NONE,
                SeamTravel.dockPlan(drive.here.layout, drive.there.layout));
            for (int i = 0; i < keyboards.size(); i++) assertTrue("at " + i, keyboards.get(i));
            // The settle reads the live keyboard, as it is then, and finds nothing held.
            drive.crossing.land(across(from));
            assertFalse(drive.crossing.footKeyboardUp(false));
            assertTrue(drive.crossing.footKeyboardUp(true));
            drive.crossing.end();
        }
    }

    private static PageKind across(PageKind page) {
        return page == PageKind.HOME ? PageKind.TERMINAL : PageKind.HOME;
    }

    @Test
    public void aSlideInStandsTheFootOfTheArrivingHalfFromItsFirstFrame() {
        Drive drive = slideInDrive();
        List<Boolean> keyboards = footKeyboards(drive, slowWay(0f, 1f));
        assertSame(SeamTravel.DockPlan.SLIDE_IN,
            SeamTravel.dockPlan(drive.here.layout, drive.there.layout));
        assertTrue("short of the other half the live keyboard answers", keyboards.get(0));
        assertFalse("Home's rows own the foot by the end", keyboards.get(keyboards.size() - 1));
        // Parity: the settle on Home reads the live keyboard, down there, and finds the same.
        boolean travelled = keyboards.get(keyboards.size() - 1);
        drive.crossing.land();
        assertEquals(travelled, drive.crossing.footKeyboardUp(drive.there.keyboardUp));
        drive.crossing.end();
    }

    @Test
    public void aSlideInThatFallsBackPutsTheFootBackWithTheCommittedRows() {
        Drive drive = slideInDrive();
        float[] out = slowWay(0f, 0.7f);
        float[] back = slowWay(0.7f, 0f);
        float[] way = new float[out.length + back.length + 1];
        System.arraycopy(out, 0, way, 0, out.length);
        System.arraycopy(back, 0, way, out.length, back.length);
        way[way.length - 1] = 0f;
        List<Boolean> keyboards = footKeyboards(drive, way);
        assertTrue("Home's rows had it on the way", keyboards.contains(false));
        assertTrue("the live keyboard again", keyboards.get(keyboards.size() - 1));
        drive.crossing.land();
        assertTrue(drive.crossing.footKeyboardUp(true));
        drive.crossing.end();
    }

    /**
     * Pong round 12: the crossing landed on Home and ended, and the settle's first geometry pass
     * still read the Workspace's keyboard up, so the foot dropped for a pass before the keyboard's
     * state caught up. The travelled answer holds through the landing until the live keyboard
     * agrees, for a few passes at most.
     */
    @Test
    public void theFootHoldsThroughTheLandingUntilTheKeyboardStateLands() {
        Drive drive = slideInDrive();
        List<Boolean> keyboards = footKeyboards(drive, slowWay(0f, 1f));
        boolean travelled = keyboards.get(keyboards.size() - 1);
        assertFalse(travelled);
        drive.crossing.land(PageKind.HOME);
        // Landed, then ended: the page's commit has run, the keyboard has not yet gone.
        assertEquals("through the commit", travelled, drive.crossing.footKeyboardUp(true));
        drive.crossing.end();
        assertEquals("crossing just ended, keyboard state not yet committed", travelled,
            drive.crossing.footKeyboardUp(true));
        // The keyboard's state lands: the live answer, the same, and the hold is gone.
        assertEquals(travelled, drive.crossing.footKeyboardUp(false));
        assertTrue("nothing held after", drive.crossing.footKeyboardUp(true));
    }

    @Test
    public void aKeyboardThatNeverLandsGivesTheFootBackAfterAFewPasses() {
        Drive drive = slideInDrive();
        footKeyboards(drive, slowWay(0f, 1f));
        drive.crossing.land(PageKind.HOME);
        drive.crossing.end();
        for (int i = 0; i < SeamCrossing.LANDED_FOOT_READS; i++)
            assertFalse("held, pass " + i, drive.crossing.footKeyboardUp(true));
        assertTrue("the fail-safe lets go", drive.crossing.footKeyboardUp(true));
    }

    @Test
    public void aSlideThatFallsBackHoldsNothingAtItsLanding() {
        Drive drive = slideInDrive();
        footKeyboards(drive, slowWay(0f, 0.7f));
        footKeyboards(drive, slowWay(0.7f, 0.6f));
        // The wall went back to the Workspace without a frame under the swap to undo it.
        drive.crossing.land(PageKind.TERMINAL);
        drive.crossing.end();
        assertTrue(drive.crossing.footKeyboardUp(true));
    }

    @Test
    public void atRestTheLiveKeyboardAnswers() {
        SeamCrossing crossing = new SeamCrossing();
        assertTrue(crossing.footKeyboardUp(true));
        assertFalse(crossing.footKeyboardUp(false));
    }

    @Test
    public void oneLayoutsRowsKeepTheOneDesign() {
        for (PageKind from : BOTH) {
            Drive drive = oneLayout(from);
            int n = Math.round(1f / SLOW);
            for (int i = 0; i <= n; i++) {
                Step step = drive.step(i / (float) n);
                if (step.active)
                    assertEquals(DockStyle.GLASS_TRAY, drive.crossing.rowsDesign());
            }
        }
    }

    // ---- The status bar's look half through the settle ----

    /** The half whose look the status bar's glass, and the strip behind the system bar, read. */
    private static PageKind statusHalf(@NonNull Drive drive, @NonNull PageKind committed) {
        PageKind stackPage = drive.crossing.stackDressPage();
        return SeamDress.lookPage(SeamDress.Surface.STATUS, committed,
            stackPage != null ? stackPage : committed, drive.crossing.sliding());
    }

    /**
     * Workspace to Home: the page's commit runs between land and end, and it is where the status
     * bar's glass is built for the landed half. It used to read "a crossing names another page" as
     * still sliding, so on Home it built the pane in the Workspace's look while the strip behind
     * the system bar, glazed again at the next insets pass after end, took Home's: the two parted.
     * The commit now reads the same half every later pass does.
     */
    @Test
    public void statusBarWearsTheLandedHalfLookThroughThePageCommit() {
        Drive drive = oneLayout(PageKind.TERMINAL).slow(0f, 1f);
        assertTrue(drive.crossing.sliding());
        assertEquals(PageKind.TERMINAL, statusHalf(drive, PageKind.TERMINAL));
        drive.crossing.land();
        // The stack still reads the crossing through the commit...
        assertNotNull(drive.crossing.other());
        // ...but the wall is at rest: the commit, now on Home, dresses the bar in Home's look.
        assertFalse(drive.crossing.sliding());
        PageKind atCommit = statusHalf(drive, PageKind.HOME);
        assertEquals(PageKind.HOME, atCommit);
        // The reading the commit used to take, which answered the Workspace on Home.
        assertEquals(PageKind.TERMINAL, SeamDress.lookPage(SeamDress.Surface.STATUS,
            PageKind.HOME, PageKind.HOME, drive.crossing.other() != null));
        drive.crossing.end();
        assertEquals("every pass after the settle reads what the commit built", atCommit,
            statusHalf(drive, PageKind.HOME));
    }

    /** Home to the Workspace and back: each landing's commit reads its own half's look. */
    @Test
    public void statusBarLookHalfFollowsARoundTripAcrossTheSeam() {
        Drive out = oneLayout(PageKind.HOME).slow(0f, 1f);
        assertEquals(PageKind.TERMINAL, statusHalf(out, PageKind.HOME));
        out.crossing.land();
        assertEquals(PageKind.TERMINAL, statusHalf(out, PageKind.TERMINAL));
        out.crossing.end();
        Drive back = oneLayout(PageKind.TERMINAL).slow(0f, 1f);
        back.crossing.land();
        assertEquals(PageKind.HOME, statusHalf(back, PageKind.HOME));
        back.crossing.end();
        assertEquals(PageKind.HOME, statusHalf(back, PageKind.HOME));
    }

    /**
     * A slide from Home that springs back commits nothing, so the settle itself must see that
     * the bar's half changed from the Workspace's it slid in to Home's at rest.
     */
    @Test
    public void statusBarLookHalfReturnsToHomeWhenASlideSpringsBack() {
        Drive drive = oneLayout(PageKind.HOME).slow(0f, 0.5f).slow(0.5f, 0f);
        assertNotNull(drive.crossing.other());
        PageKind travelled = statusHalf(drive, PageKind.HOME);
        assertEquals(PageKind.TERMINAL, travelled);
        drive.crossing.land();
        drive.crossing.end();
        PageKind atRest = statusHalf(drive, PageKind.HOME);
        assertEquals(PageKind.HOME, atRest);
        assertTrue(travelled != atRest);
    }

    // ---- Between two Home pages (#173 slice E) ----

    private static final PageRef HOME_2 = PageRef.of(PageKind.HOME, 2);
    private static final PageRef HOME_3 = PageRef.of(PageKind.HOME, 3);

    /** A Home page's end, on the one dress both pages wear. */
    private static SeamCrossing.Endpoint homePage(@NonNull Stored stored, @NonNull PageRef page,
                                                  @NonNull PlaceLayout layout) {
        Dress dress = new DressResolver(stored).dressOf(PageKind.HOME);
        return SeamCrossing.Endpoint.of(page, dress, layout, stackOf(dress), false);
    }

    /** Home's pager {@code fraction} of the way from page {@code from} to page {@code toward}. */
    private static Frame pagerAt(int from, int toward, float fraction) {
        return PlaceChromeTravel.homePages(new Rest(false, false, true), from, toward,
            -fraction * WIDTH, WIDTH);
    }

    /** One Home layout (ADR 0020 §1): two Home pages are dressed alike, so a swipe is a plain flip. */
    @Test
    public void twoHomePagesWearOneLayoutSoTheyNeverMorph() {
        for (PlaceLayout layout : new PlaceLayout[] {home(), homeWithoutBar()}) {
            Stored stored = new Stored();
            assertFalse("dressed alike: a plain flip", SeamCrossing.crosses(
                homePage(stored, HOME_2, layout), homePage(stored, HOME_3, layout)));
            Stored beads = new Stored();
            beads.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
            assertFalse("one design on both pages", SeamCrossing.crosses(
                homePage(beads, HOME_2, layout), homePage(beads, HOME_3, layout)));
        }
        Stored stored = new Stored();
        // Home and the Workspace still cross.
        assertTrue(SeamCrossing.crosses(homePage(stored, HOME_2, homeWithoutBar()),
            half(new DressResolver(stored), PageKind.TERMINAL, window(), false)));
    }

    /**
     * From page 2, which puts the bar away, to page 3, which wears it along the top: the bar is
     * laid out over page 2 from the first frame and rides in with page 3, dressed for Home
     * throughout; nothing of the dock moves and the stack keeps its dress.
     */
    @Test
    public void towardAHomePageThatWearsTheBarItRidesInWithThatPage() {
        Stored stored = new Stored();
        SeamCrossing crossing = new SeamCrossing();
        crossing.begin(homePage(stored, HOME_2, homeWithoutBar()),
            homePage(stored, HOME_3, home()), SeamCrossing.Stage.of(false, () -> false, true));
        assertTrue(crossing.oneKind());
        assertEquals(HOME_3, crossing.otherEnd());
        assertFalse(crossing.barTakesWorkspaceDressFirst());
        for (float fraction = SLOW; fraction < 1f; fraction += SLOW) {
            Frame frame = pagerAt(2, 3, fraction);
            SeamCrossing.Answers answers = crossing.frame(frame, true, false, false);
            if (answers.relayout()) crossing.standLayout();
            assertTrue(crossing.rideBar(frame, true));
            assertFalse(answers.barRedressed());
            assertEquals((1f - fraction) * WIDTH, crossing.barScreenXPx(frame, WIDTH), 0.01f);
            assertTrue(Float.isNaN(answers.statusFade()));
            assertEquals(1f, answers.dockReveal(), EPSILON);
            assertNull(crossing.stackDressPage());
            assertFalse("the bar wears Home's dress", crossing.barDressedAcross());
            if (SeamTravel.otherEndProgress(frame, HOME_2) > 0f) {
                assertNotNull(crossing.layout());
                assertFalse(crossing.layout().slot(Element.STATUS).hidden);
            }
        }
        SeamCrossing.Landing landing = crossing.land();
        assertTrue(landing.laidOut());
        assertFalse(landing.barTravelled());
        crossing.end();
        assertNull(crossing.otherEnd());
    }

    /** Under a page transition that turns the pages the bar fades instead, either way. */
    @Test
    public void betweenTurningHomePagesTheBarFadesInPlace() {
        Stored stored = new Stored();
        SeamCrossing.Stage turning = SeamCrossing.Stage.of(false, () -> false, true, false);
        SeamCrossing toward = new SeamCrossing();
        toward.begin(homePage(stored, HOME_2, homeWithoutBar()),
            homePage(stored, HOME_3, home()), turning);
        SeamCrossing away = new SeamCrossing();
        away.begin(homePage(stored, HOME_3, home()),
            homePage(stored, HOME_2, homeWithoutBar()), turning);
        for (float fraction = SLOW; fraction < 1f; fraction += SLOW) {
            Frame in = pagerAt(2, 3, fraction);
            float progress = SeamTravel.otherEndProgress(in, HOME_2);
            SeamCrossing.Answers answers = toward.frame(in, true, false, false);
            if (answers.relayout()) toward.standLayout();
            assertFalse(toward.rideBar(in, true));
            assertEquals(progress, answers.statusFade(), EPSILON);
            if (progress > 0f) assertFalse(toward.layout().slot(Element.STATUS).hidden);

            Frame out = pagerAt(3, 2, fraction);
            SeamCrossing.Answers leaving = away.frame(out, true, false, false);
            if (leaving.relayout()) away.standLayout();
            assertFalse(away.rideBar(out, true));
            assertEquals(1f - SeamTravel.otherEndProgress(out, HOME_3), leaving.statusFade(),
                EPSILON);
            assertNull("the leaving page's bar stays laid out", away.layout());
        }
    }

    /** A bar both ends stand alike stays put and keeps the committed page's look through the slide. */
    @Test
    public void aStillBarIsNotDressedAcrossWhileTheWallSlides() {
        for (PageKind from : new PageKind[] {PageKind.HOME, PageKind.TERMINAL}) {
            Drive drive = oneLayout(from).slow(0f, 0.6f);
            assertTrue(drive.crossing.sliding());
            assertTrue(drive.crossing.barStill());
            assertFalse(drive.crossing.barDressedAcross());
            assertFalse(drive.crossing.oneKind());
        }
    }

    /** A bar only the Workspace shows rides with its page, dressed for the Workspace's look. */
    @Test
    public void aRidingBarIsDressedAcrossWhileTheWallSlides() {
        Drive drive = drive(new Stored(), PageKind.HOME, homeWithoutBar(), false,
            PageKind.TERMINAL, window(), false, false).slow(0f, 0.6f);
        assertTrue(drive.crossing.sliding());
        assertFalse(drive.crossing.barStill());
        assertTrue(drive.crossing.barDressedAcross());
        assertFalse(drive.crossing.oneKind());
    }

    /** The riding bar wears the Workspace's look: the other end from Home, the committed one from it. */
    @Test
    public void theRidingBarWearsTheOtherEndOnlyWhenThatIsTheWorkspace() {
        Drive fromHome = drive(new Stored(), PageKind.HOME, homeWithoutBar(), false,
            PageKind.TERMINAL, window(), false, false).slow(0f, 0.6f);
        assertTrue(fromHome.crossing.barRideWearsOther());
        Drive toHome = drive(new Stored(), PageKind.TERMINAL, window(), false,
            PageKind.HOME, homeWithoutBar(), false, false).slow(0f, 0.6f);
        assertFalse(toHome.crossing.barStill());
        assertFalse(toHome.crossing.barRideWearsOther());
    }

    /** Between two Home pages the page that wears the bar is the one it wears. */
    @Test
    public void betweenHomePagesTheRidingBarWearsThePageThatWearsIt() {
        Stored stored = new Stored();
        SeamCrossing toward = new SeamCrossing();
        toward.begin(homePage(stored, HOME_2, homeWithoutBar()), homePage(stored, HOME_3, home()),
            SeamCrossing.Stage.of(false, () -> false, true));
        assertTrue(toward.barRideWearsOther());
        SeamCrossing away = new SeamCrossing();
        away.begin(homePage(stored, HOME_3, home()), homePage(stored, HOME_2, homeWithoutBar()),
            SeamCrossing.Stage.of(false, () -> false, true));
        assertFalse(away.barRideWearsOther());
        assertFalse(new SeamCrossing().barRideWearsOther());
    }
}
