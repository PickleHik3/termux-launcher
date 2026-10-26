package com.termux.app.dock;

import androidx.annotation.NonNull;

import com.termux.app.chrome.ChromeSpec;
import com.termux.app.dock.DockGeometry.Half;
import com.termux.app.dock.DockGeometry.Side;
import com.termux.app.dock.DockGeometry.Snapshot;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.SeamTravel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Dock rows' seam, crossed through a fake adapter the way the activity crosses it: a chrome spec
 * and the applied DockLayout in, and what the user would see out — which of the dock's views show,
 * how tall its rows are, whether an Element's row is on screen and which design dresses it. The
 * listener is asserted as its subscribers meet it: once per change, before the views move.
 */
public class DockRowsSeamTest {

    private static final float DENSITY = 2.75f;
    private static final int WIDTH_DP = 411;

    /** A window with the pinned apps and the extra keys along the bottom. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A window whose layout puts every dock row away. */
    private static final PlaceLayout WINDOW_BARE = new PlaceLayout(Edge.TOP, RowPlacement.HIDDEN,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A Home layout: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    // ---- the fake adapter ----

    /** The accessory views as a test sees them: their state, and every write in order. */
    private static final class FakeViews implements DockRows.Views {
        boolean stack, surface, dockGlass, azLayers = true, underKeyboard, shelf, statusBar;
        final Map<Element, Boolean> rows = new EnumMap<>(Element.class);
        final Map<Element, Integer> heights = new EnumMap<>(Element.class);
        int pageBandHeight = -1;
        DockRows.PageBand pageBand;
        boolean ticksAreTheDocks = true;
        final List<String> log = new ArrayList<>();

        @Override public void setStackShown(boolean shown) { stack = shown; log.add("stack"); }
        @Override public void setSurfaceShown(boolean shown) { surface = shown; log.add("surface"); }
        @Override public void setDockGlassShown(boolean shown) { dockGlass = shown; log.add("glass"); }
        @Override public void setShelfShown(boolean shown) { shelf = shown; log.add("shelf"); }
        @Override public boolean setStatusBarShown(boolean shown) {
            boolean moved = statusBar != shown;
            statusBar = shown;
            log.add("status");
            return moved;
        }
        @Override public void setRowShown(@NonNull Element row, boolean shown) {
            rows.put(row, shown);
            log.add("row " + row);
        }
        @Override public void setPageBand(@NonNull DockRows.PageBand band) {
            pageBand = band;
            log.add("band");
        }
        @Override public void setAzLayersShown(boolean shown) { azLayers = shown; log.add("layers"); }
        @Override public boolean setUnderKeyboardShown(boolean shown) {
            boolean moved = underKeyboard != shown;
            underKeyboard = shown;
            log.add("under");
            return moved;
        }
        @Override public boolean setRowHeight(@NonNull Element row, int px) {
            Integer before = heights.put(row, px);
            log.add("height " + row);
            return before == null || before != px;
        }
        @Override public boolean setPageBandHeight(int px) {
            boolean moved = pageBandHeight != px;
            pageBandHeight = px;
            return moved;
        }
        @Override public boolean pageTicksAreTheDocks() { return ticksAreTheDocks; }

        boolean row(Element element) {
            Boolean shown = rows.get(element);
            return shown != null && shown;
        }
    }

    /** A subscriber counting announcements and noting what the views showed when each came. */
    private static final class Subscriber implements DockRows.Listener {
        int calls;
        final List<DockRows.Shown> seen = new ArrayList<>();
        final FakeViews views;
        final List<Boolean> appsPagerWhenTold = new ArrayList<>();

        Subscriber(FakeViews views) { this.views = views; }

        @Override public void onRowsChanged(@NonNull DockRows.Shown shown) {
            calls++;
            seen.add(shown);
            appsPagerWhenTold.add(views.row(Element.APPS));
        }
    }

    // ---- the caller's side: a chrome spec and a DockLayout as the activity builds them ----

    /** The chrome spec the activity builds for {@code layout}. */
    private static ChromeSpec chrome(PlaceLayout layout, boolean keyboard) {
        return new ChromeSpec(PlaceChromePolicy.dockShown(layout), keyboard, keyboard ? 700 : 0,
            true, PlaceChromePolicy.appsRowShown(layout), PlaceChromePolicy.azRowOnDock(layout),
            PlaceChromePolicy.extraKeysRowShown(layout), 0.8f, 24);
    }

    private static Half half(PlaceLayout layout, DockStyle design) {
        return Half.builder(layout).floating(true).dockHeightScale(2f).design(design).build();
    }

    private static Snapshot.Builder device(Half home, Half workspace) {
        return Snapshot.builder(home, workspace)
            .density(DENSITY)
            .widthPx(Math.round(WIDTH_DP * DENSITY))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
            .pinnedSlotCount(7);
    }

    private static DockLayout rest(Snapshot.Builder device, Side side) {
        return DockGeometry.compute(device.build().restOf(side));
    }

    // ---- what shows on Home, in a window and mid-crossing ----

    @Test
    public void homeShowsThePinnedAppsAloneAndTheirTicks() {
        Snapshot.Builder device = device(half(HOME, DockStyle.GLASS_TRAY),
            half(WINDOW, DockStyle.GLASS_TRAY));
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        DockLayout dock = rest(device, Side.HOME);
        rows.applyLayout(dock);
        rows.apply(chrome(HOME, false), false, false);

        assertTrue("the stack is up", views.stack);
        assertTrue("the dock's glass host is up", views.surface);
        assertTrue("its sheet is up", views.dockGlass);
        assertTrue("the pinned apps show", views.row(Element.APPS));
        assertFalse("Home leaves the extra keys out", views.row(Element.EXTRA_KEYS));
        assertFalse("the letters are not on the dock", views.row(Element.AZ));
        assertFalse("the letters' effect layers rest hidden", views.azLayers);
        assertEquals("the row's own ticks draw its band", DockRows.PageBand.TICKS, views.pageBand);
        assertTrue(rows.isRowOnScreen(Element.APPS));
        assertFalse(rows.isRowOnScreen(Element.EXTRA_KEYS));
        assertFalse(rows.isRowOnScreen(Element.AZ));
        assertEquals("the row is as tall as the dock says", dock.appsBarHeightPx,
            (int) views.heights.get(Element.APPS));
        assertEquals(dock.indicatorBandHeightPx, views.pageBandHeight);
    }

    @Test
    public void aWindowShowsThePinnedAppsAndTheExtraKeys() {
        Snapshot.Builder device = device(half(HOME, DockStyle.GLASS_TRAY),
            half(WINDOW, DockStyle.GLASS_TRAY));
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        rows.applyLayout(rest(device, Side.WORKSPACE));
        rows.apply(chrome(WINDOW, false), false, false);

        assertTrue(views.stack);
        assertTrue(views.row(Element.APPS));
        assertTrue(views.row(Element.EXTRA_KEYS));
        assertTrue(rows.isRowOnScreen(Element.APPS));
        assertTrue(rows.isRowOnScreen(Element.EXTRA_KEYS));
        assertFalse("no status bar stands on the dock", rows.isRowOnScreen(Element.STATUS));
    }

    @Test
    public void aBandBeforeTheRowBindsItsTicksIsHeldRatherThanGone() {
        FakeViews views = new FakeViews();
        views.ticksAreTheDocks = false;
        DockRows rows = new DockRows(views);
        rows.apply(chrome(WINDOW, false), false, false);
        // The dock's height counts the band, so a gone band would be a gap.
        assertEquals(DockRows.PageBand.HELD, views.pageBand);
    }

    @Test
    public void aWindowWithoutDockRowsHidesTheDockAndKeepsTheStackOnlyForWhatStandsInIt() {
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        rows.apply(chrome(WINDOW_BARE, false), false, false);
        assertFalse("nothing stands in the stack", views.stack);
        assertFalse(views.surface);
        assertFalse(views.dockGlass);
        assertFalse(views.row(Element.APPS));
        assertFalse(views.row(Element.EXTRA_KEYS));
        assertFalse(views.row(Element.AZ));
        assertEquals(DockRows.PageBand.GONE, views.pageBand);
        for (Element element : Element.values())
            assertFalse(element + " has no row on screen", rows.isRowOnScreen(element));

        rows.apply(chrome(WINDOW_BARE, true), false, false);
        assertTrue("the keyboard stands in the stack", views.stack);
        assertFalse("but the dock's rows stay away", views.surface);

        rows.setStatusBarShown(true);
        rows.apply(chrome(WINDOW_BARE, false), true, false);
        assertTrue("a status bar along the bottom keeps the stack up", views.stack);
        assertTrue(rows.isRowOnScreen(Element.STATUS));
        assertFalse(views.surface);
    }

    // ---- the status bar, the shelf and the extra keys' height ----

    @Test
    public void theStatusBarIsOnTheStackWhileItShowsAlongTheBottom() {
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        assertTrue("shown, it is written", rows.setStatusBarShown(true));
        assertTrue(views.statusBar);
        assertTrue(rows.statusBarShown());
        assertFalse("shown again, nothing moves", rows.setStatusBarShown(true));

        rows.apply(chrome(WINDOW_BARE, false), false, false);
        assertFalse("along the top it is no band of the stack",
            rows.isRowOnScreen(Element.STATUS));
        assertFalse(views.stack);

        rows.apply(chrome(WINDOW_BARE, false), true, false);
        assertTrue("along the bottom it is", rows.isRowOnScreen(Element.STATUS));
        assertTrue(views.stack);

        // Put away in the Layout editor: gone, and gone from the stack at the next pass.
        assertTrue(rows.setStatusBarShown(false));
        assertFalse(views.statusBar);
        rows.apply(chrome(WINDOW_BARE, false), true, false);
        assertFalse(rows.isRowOnScreen(Element.STATUS));
        assertFalse("nothing stands in the stack", views.stack);
    }

    @Test
    public void theShelfIsWrittenAndAnsweredHere() {
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        assertFalse(rows.shelfShown());
        rows.setShelfShown(true);
        assertTrue(views.shelf);
        assertTrue(rows.shelfShown());
        rows.setShelfShown(false);
        assertFalse(views.shelf);
        assertFalse(rows.shelfShown());
    }

    @Test
    public void theExtraKeysRowsHeightIsWrittenHereAndReportsWhetherItMoved() {
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        assertTrue(rows.setExtraKeysHeight(150));
        assertEquals(150, (int) views.heights.get(Element.EXTRA_KEYS));
        assertFalse("the same height moves nothing", rows.setExtraKeysHeight(150));
        assertTrue("off, the row is no height", rows.setExtraKeysHeight(-4));
        assertEquals(0, (int) views.heights.get(Element.EXTRA_KEYS));
    }

    @Test
    public void theDockSheetStandsDownWhereTheNavStripCarriesTheGlass() {
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        rows.apply(chrome(WINDOW, false), false, true);
        assertTrue(views.surface);
        assertFalse(views.dockGlass);
        assertTrue("the rows still show", views.row(Element.APPS));
    }

    @Test
    public void midCrossingTheStackShowsTheRowsOfTheHalfItStands() {
        Snapshot.Builder device = device(half(HOME, DockStyle.LENS_BEADS),
            half(WINDOW, DockStyle.GLASS_TRAY));
        // From a window toward Home: the seam stands Home's rows in the window's arrangement.
        PlaceLayout towardHome = SeamTravel.withDockOf(WINDOW, HOME);
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        rows.applyLayout(DockGeometry.compute(device.rows(Side.HOME).arrangement(towardHome)
            .build()));
        rows.apply(chrome(towardHome, false), false, false);
        assertTrue("Home's pinned apps", views.row(Element.APPS));
        assertFalse("without the window's extra keys", views.row(Element.EXTRA_KEYS));
        assertFalse(rows.isRowOnScreen(Element.EXTRA_KEYS));

        // And back, from Home toward a window: the window's rows.
        PlaceLayout towardWindow = SeamTravel.withDockOf(HOME, WINDOW);
        rows.applyLayout(DockGeometry.compute(device.rows(Side.WORKSPACE)
            .arrangement(towardWindow).build()));
        rows.apply(chrome(towardWindow, false), false, false);
        assertTrue(views.row(Element.APPS));
        assertTrue("the window's extra keys come with its rows", views.row(Element.EXTRA_KEYS));
    }

    // ---- the design is the shown half's, never the committed one ----

    @Test
    public void theCommittedDesignIsNeverReadWhileACrossingShowsTheOtherHalfsRows() {
        for (boolean homeBeads : new boolean[] {true, false}) {
            DockStyle homeDesign = homeBeads ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY;
            DockStyle windowDesign = homeBeads ? DockStyle.GLASS_TRAY : DockStyle.LENS_BEADS;
            Snapshot.Builder device = device(half(HOME, homeDesign), half(WINDOW, windowDesign));
            FakeViews views = new FakeViews();
            DockRows rows = new DockRows(views);

            // Resting in a window: the window's design.
            rows.applyLayout(rest(device, Side.WORKSPACE));
            rows.apply(chrome(WINDOW, false), false, false);
            assertSame(DockSurfaceSpec.forStyle(windowDesign), rows.surface());

            // A slide toward Home stands Home's rows while the window is still committed: the
            // dock is dressed in Home's design from that frame, though nothing told the rows
            // which half is committed.
            PlaceLayout towardHome = SeamTravel.withDockOf(WINDOW, HOME);
            rows.applyLayout(DockGeometry.compute(device.rows(Side.HOME)
                .arrangement(towardHome).build()));
            rows.apply(chrome(towardHome, false), false, false);
            assertSame("home beads " + homeBeads, DockSurfaceSpec.forStyle(homeDesign),
                rows.surface());
            assertSame(rows.surface(), rows.shown().surface);
        }
    }

    @Test
    public void whileACrossingStandsTheRowsWearTheirOwnHalfsDesignNeverTheCommittedOne() {
        for (boolean homeBeads : new boolean[] {true, false}) {
            DockStyle homeDesign = homeBeads ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY;
            DockStyle windowDesign = homeBeads ? DockStyle.GLASS_TRAY : DockStyle.LENS_BEADS;
            DockSurfaceSpec home = DockSurfaceSpec.forStyle(homeDesign);
            DockSurfaceSpec window = DockSurfaceSpec.forStyle(windowDesign);
            Snapshot.Builder device = device(half(HOME, homeDesign), half(WINDOW, windowDesign));
            DockRows rows = new DockRows(new FakeViews());

            // At rest in a window the committed dress is worn, so a design just picked in the
            // Layout editor is worn before the pass that lays it out.
            rows.applyLayout(rest(device, Side.WORKSPACE));
            assertSame(window, rows.wornSurface(window, false));
            assertSame("a pick not laid out yet", home, rows.wornSurface(home, false));

            // A slide toward Home stands Home's rows while the window is committed: the
            // window's design, which the activity hands as the committed one, is never worn.
            rows.applyLayout(DockGeometry.compute(device.rows(Side.HOME)
                .arrangement(SeamTravel.withDockOf(WINDOW, HOME)).build()));
            assertSame("home beads " + homeBeads, home, rows.wornSurface(window, true));

            // And back from Home: the window's rows wear the window's design.
            rows.applyLayout(DockGeometry.compute(device.rows(Side.WORKSPACE)
                .arrangement(SeamTravel.withDockOf(HOME, WINDOW)).build()));
            assertSame(window, rows.wornSurface(home, true));
        }
        // Before any layout a crossing has nothing else to wear.
        DockSurfaceSpec committed = DockSurfaceSpec.forStyle(DockStyle.GLASS_TRAY);
        assertSame(committed, new DockRows(new FakeViews()).wornSurface(committed, true));
    }

    // ---- the listener ----

    @Test
    public void theListenerFiresOncePerChange() {
        Snapshot.Builder device = device(half(HOME, DockStyle.GLASS_TRAY),
            half(WINDOW, DockStyle.LENS_BEADS));
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        Subscriber subscriber = new Subscriber(views);
        rows.addListener(subscriber);

        rows.applyLayout(rest(device, Side.WORKSPACE));
        assertEquals("the first design is a change", 1, subscriber.calls);
        rows.apply(chrome(WINDOW, false), false, false);
        assertEquals("the first rows are a change", 2, subscriber.calls);

        // The same answer again, as every chrome pass at rest hands it: no announcement, though
        // the views are written again (other code shares some of them, e.g. the letters' slot).
        int writes = views.log.size();
        rows.applyLayout(rest(device, Side.WORKSPACE));
        rows.apply(chrome(WINDOW, false), false, false);
        rows.apply(chrome(WINDOW, false), false, false);
        assertEquals(2, subscriber.calls);
        assertTrue("the views are written every pass", views.log.size() > writes);

        // The keyboard rising changes nothing a row shows.
        rows.apply(chrome(WINDOW, true), false, false);
        assertEquals(2, subscriber.calls);

        // The pinned apps leave: one announcement.
        rows.apply(chrome(WINDOW_BARE, false), false, false);
        assertEquals(3, subscriber.calls);
        assertFalse(subscriber.seen.get(2).rowOnScreen(Element.APPS));

        // Bands go under the keyboard: one more, and only one, however often it is said.
        rows.setUnderKeyboardBands(true);
        rows.setUnderKeyboardBands(true);
        assertEquals(4, subscriber.calls);
        assertTrue(rows.underKeyboardBandsShown());
        assertTrue(views.underKeyboard);

        // The design the rows wear changes with the half they show: one announcement.
        rows.applyLayout(rest(device, Side.HOME));
        assertEquals(5, subscriber.calls);
        rows.applyLayout(rest(device, Side.HOME));
        assertEquals(5, subscriber.calls);

        rows.removeListener(subscriber);
        rows.apply(chrome(WINDOW, false), false, false);
        assertEquals("a removed subscriber hears nothing", 5, subscriber.calls);
    }

    @Test
    public void subscribersAreToldBeforeTheViewsMove() {
        // A drag on the pinned apps is cancelled while its source is still on screen.
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        rows.apply(chrome(WINDOW, false), false, false);
        Subscriber subscriber = new Subscriber(views);
        rows.addListener(subscriber);
        rows.apply(chrome(WINDOW_BARE, false), false, false);
        assertEquals(1, subscriber.calls);
        assertTrue("the row was still shown when its subscribers heard it leave",
            subscriber.appsPagerWhenTold.get(0));
        assertFalse("and is gone once the pass is done", views.row(Element.APPS));
    }

    @Test
    public void heightsAreWrittenFromTheAppliedLayoutAndReportWhetherTheyMoved() {
        Snapshot.Builder device = device(half(HOME, DockStyle.GLASS_TRAY),
            half(WINDOW, DockStyle.GLASS_TRAY));
        FakeViews views = new FakeViews();
        DockRows rows = new DockRows(views);
        DockLayout window = rest(device, Side.WORKSPACE);
        assertTrue("the first layout moves the rows", rows.applyLayout(window));
        assertEquals(window.appsBarHeightPx, (int) views.heights.get(Element.APPS));
        assertEquals(window.azRowHeightPx, (int) views.heights.get(Element.AZ));
        assertEquals(window.indicatorBandHeightPx, views.pageBandHeight);
        assertFalse("the same layout moves nothing", rows.applyLayout(window));
        assertSame(window, rows.layout());
    }
}
