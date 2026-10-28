package com.termux.app.dock;

import com.termux.app.dock.DockGeometry.Half;
import com.termux.app.wall.PageRef;
import com.termux.app.dock.DockGeometry.Snapshot;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.SeamTravel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Dock geometry's seam, crossed the way the activity crosses it: a snapshot of both halves in,
 * one DockLayout out, and what the user sees asserted as numbers — the icon a pinned slot draws,
 * the width the row shares between its slots, the band it takes. One invariant per bug class
 * seen in the week of 2026-10-22, across the shipped densities, both Styles and the size range.
 */
public class DockGeometrySeamTest {

    private static final float[] DENSITIES = {1f, 2f, 2.625f, 2.75f, 3f, 3.5f};
    private static final boolean[] STYLES = {false, true};
    /** A 411 dp phone. */
    private static final int WIDTH_DP = 411;
    /** A short page, the width rule's seven on 411 dp, and a page full enough that slots bind. */
    private static final int[] SLOTS = {5, 7, 10};

    /** A window with the pinned apps and the extra keys along the bottom. */
    private static final PlaceLayout WINDOW_APPS = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A window whose layout puts the pinned apps away: the extra keys alone. */
    private static final PlaceLayout WINDOW_KEYS = new PlaceLayout(Edge.TOP, RowPlacement.HIDDEN,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A Home layout: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A Home layout that puts the pinned apps away: no dock rows at all. */
    private static final PlaceLayout HOME_BARE = new PlaceLayout(Edge.TOP, RowPlacement.HIDDEN,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** The Workspace's layout as Home wears it on One layout: without the extra keys. */
    private static final PlaceLayout HOME_ON_ONE_LAYOUT = WINDOW_APPS.withSlot(Element.EXTRA_KEYS,
        WINDOW_APPS.slot(Element.EXTRA_KEYS).withHidden(true));

    private static float[] sizes(boolean capsule) {
        return new float[] {DockLayoutPolicy.minUsefulScale(), 1.95f, 2.18f,
            DockLayoutPolicy.maxUsefulScale(capsule)};
    }

    private static Half half(PlaceLayout layout, boolean floating, float scale, DockStyle design,
                             boolean searchField) {
        return Half.builder(layout).floating(floating).dockHeightScale(scale).design(design)
            .searchField(searchField).build();
    }

    private static Snapshot.Builder device(Half home, Half workspace, float density, int slots) {
        return Snapshot.builder(home, workspace)
            .density(density)
            .widthPx(Math.round(WIDTH_DP * density))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .pinnedSlotCount(slots);
    }

    private static DockLayout rest(Snapshot.Builder device, PageRef side) {
        return DockGeometry.compute(device.build().restOf(side));
    }

    private static String at(float density, boolean capsule, float scale, int slots) {
        return "density " + density + (capsule ? " floating" : " docked") + " scale " + scale
            + " slots " + slots;
    }

    /** The row's own figures, the ones the user sees: the slot's icon and the row's width. */
    private static void assertSameRow(String at, DockLayout expected, DockLayout actual) {
        assertTrue(at + ": a slot draws an icon", expected.slotIconPx > 0);
        assertEquals(at + ": the slot's icon", expected.slotIconPx, actual.slotIconPx);
        assertEquals(at + ": the row's icon", expected.appsRowIconPx, actual.appsRowIconPx);
        assertEquals(at + ": the row's width", expected.appsRowUsableWidthPx,
            actual.appsRowUsableWidthPx);
    }

    // ---- 1. One layout: the same pinned icon on Home and in a window ----

    @Test
    public void oneLayoutGivesTheSamePinnedIconOnHomeAndInAWindow() {
        for (float density : DENSITIES)
            for (boolean capsule : STYLES)
                for (float scale : sizes(capsule))
                    for (int slots : SLOTS)
                        for (DockStyle design : new DockStyle[] {DockStyle.GLASS_TRAY,
                                DockStyle.LENS_BEADS}) {
                            String at = at(density, capsule, scale, slots) + " " + design;
                            Half workspace = half(WINDOW_APPS, capsule, scale, design, false);
                            // Home wears the Workspace's dress; only what it leaves out differs.
                            Half home = Half.builder(HOME_ON_ONE_LAYOUT).floating(capsule)
                                .dockHeightScale(scale).design(design).build();
                            Snapshot.Builder device = device(home, workspace, density, slots);
                            DockLayout window = rest(device, PageRef.TERMINAL);
                            DockLayout onHome = rest(device, PageRef.HOME);
                            assertTrue(at, window.appsRowUsableWidthPx > 0);
                            assertSameRow(at, window, onHome);
                        }
    }

    // ---- 2. The rows a frame shows are measured in their own half, both ways ----

    @Test
    public void theRowsAFrameShowsAreMeasuredInTheirOwnHalfBothWays() {
        for (float density : DENSITIES)
            for (boolean capsule : STYLES)
                for (float scale : sizes(capsule))
                    for (int slots : SLOTS)
                        for (boolean workspaceBeads : new boolean[] {true, false}) {
                            String at = at(density, capsule, scale, slots)
                                + (workspaceBeads ? " workspace beads" : " home beads");
                            // The Workspace on Beads and Home on Tray with a Search field, and
                            // the other way round; Home a size up, as a Home layout may keep it.
                            Half workspace = half(WINDOW_APPS, capsule, scale,
                                workspaceBeads ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY,
                                false);
                            Half home = half(HOME, capsule,
                                Math.min(DockLayoutPolicy.maxUsefulScale(capsule), scale + 0.23f),
                                workspaceBeads ? DockStyle.GLASS_TRAY : DockStyle.LENS_BEADS,
                                true);
                            Snapshot.Builder device = device(home, workspace, density, slots);
                            DockLayout homeRest = rest(device, PageRef.HOME);
                            DockLayout workspaceRest = rest(device, PageRef.TERMINAL);

                            // From a window toward Home: Home's rows, the field easing in.
                            PlaceLayout towardHome = SeamTravel.withDockOf(WINDOW_APPS, HOME);
                            for (float p : new float[] {0.25f, 0.5f, 0.75f, 1f}) {
                                DockLayout frame = DockGeometry.compute(device.rows(PageRef.HOME)
                                    .arrangement(towardHome).fieldReveal(p).build());
                                assertShowsItsOwnHalf(at + " toward Home at " + p, homeRest,
                                    frame);
                                if (p == 1f) assertSameField(at, homeRest, frame);
                            }
                            // From Home toward a window: the Workspace's rows, the field easing
                            // out.
                            PlaceLayout towardWindow = SeamTravel.withDockOf(HOME, WINDOW_APPS);
                            for (float p : new float[] {0.25f, 0.5f, 0.75f, 1f}) {
                                DockLayout frame = DockGeometry.compute(device
                                    .rows(PageRef.TERMINAL).arrangement(towardWindow)
                                    .fieldReveal(1f - p).build());
                                assertShowsItsOwnHalf(at + " toward a window at " + p,
                                    workspaceRest, frame);
                                if (p == 1f) assertSameField(at, workspaceRest, frame);
                            }
                        }
    }

    private static void assertShowsItsOwnHalf(String at, DockLayout rest, DockLayout frame) {
        assertSameRow(at, rest, frame);
        assertEquals(at + ": the design", rest.surface, frame.surface);
        assertEquals(at + ": the row's side inset", rest.appsRowSideInsetPx(),
            frame.appsRowSideInsetPx());
        assertEquals(at + ": the Style", rest.capsule, frame.capsule);
    }

    private static void assertSameField(String at, DockLayout rest, DockLayout frame) {
        assertEquals(at + ": the field's band", rest.promptFieldBandPx, frame.promptFieldBandPx);
        assertEquals(at + ": the field's side inset", rest.promptFieldSideInsetPx,
            frame.promptFieldSideInsetPx);
    }

    // ---- 3. A put-away row keeps the showing half's icon ----

    @Test
    public void aPutAwayRowKeepsTheShowingHalfsIcon() {
        for (float density : DENSITIES)
            for (boolean capsule : STYLES)
                for (float scale : sizes(capsule))
                    for (int slots : SLOTS)
                        for (boolean workspaceBeads : new boolean[] {true, false}) {
                            String at = at(density, capsule, scale, slots)
                                + (workspaceBeads ? " workspace beads" : " home beads");
                            Half workspace = half(WINDOW_KEYS, capsule,
                                DockLayoutPolicy.minUsefulScale(),
                                workspaceBeads ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY,
                                false);
                            Half home = half(HOME, capsule, scale,
                                workspaceBeads ? DockStyle.GLASS_TRAY : DockStyle.LENS_BEADS,
                                false);
                            Snapshot.Builder device = device(home, workspace, density, slots);
                            DockLayout homeRest = rest(device, PageRef.HOME);
                            DockLayout window = rest(device, PageRef.TERMINAL);
                            assertSameRow(at + " in the window", homeRest, window);
                            // Its band stays the window's: none.
                            assertEquals(at, 0, window.appsRowBandPx);
                            assertEquals(at, 0, window.appsBarHeightPx);
                            // And the slide that brings it in hands it the same.
                            DockLayout sliding = DockGeometry.compute(device.rows(PageRef.HOME)
                                .arrangement(SeamTravel.withDockOf(WINDOW_KEYS, HOME)).build());
                            assertSameRow(at + " sliding in", homeRest, sliding);
                        }
    }

    @Test
    public void aRowHomePutsAwayKeepsTheWorkspacesIcon() {
        // The other way round: the Workspace shows the row, Home's layout puts it away.
        for (float density : DENSITIES)
            for (boolean capsule : STYLES)
                for (float scale : sizes(capsule))
                    for (int slots : SLOTS)
                        for (boolean workspaceBeads : new boolean[] {true, false}) {
                            String at = at(density, capsule, scale, slots)
                                + (workspaceBeads ? " workspace beads" : " home beads");
                            Half workspace = half(WINDOW_APPS, capsule, scale,
                                workspaceBeads ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY,
                                false);
                            Half home = half(HOME_BARE, capsule,
                                DockLayoutPolicy.minUsefulScale(),
                                workspaceBeads ? DockStyle.GLASS_TRAY : DockStyle.LENS_BEADS,
                                true);
                            Snapshot.Builder device = device(home, workspace, density, slots);
                            DockLayout workspaceRest = rest(device, PageRef.TERMINAL);
                            DockLayout onHome = rest(device, PageRef.HOME);
                            assertSameRow(at + " on Home", workspaceRest, onHome);
                            // Its band stays Home's: none.
                            assertEquals(at, 0, onHome.appsRowBandPx);
                            assertEquals(at, 0, onHome.appsBarHeightPx);
                            // And the slide from Home that brings it in hands it the same.
                            DockLayout sliding = DockGeometry.compute(device
                                .rows(PageRef.TERMINAL)
                                .arrangement(SeamTravel.withDockOf(HOME_BARE, WINDOW_APPS))
                                .build());
                            assertSameRow(at + " sliding in", workspaceRest, sliding);
                        }
    }

    // ---- 4. A squeeze changes the band at most, never the icon ----

    @Test
    public void aSqueezeNeverChangesTheIcon() {
        for (float density : DENSITIES)
            for (boolean capsule : STYLES)
                for (float scale : sizes(capsule))
                    for (int slots : SLOTS) {
                        String at = at(density, capsule, scale, slots);
                        Half workspace = half(WINDOW_APPS, capsule, scale, DockStyle.LENS_BEADS,
                            false);
                        Half home = half(HOME, capsule, scale, DockStyle.GLASS_TRAY, true);
                        for (PageRef side : new PageRef[] {PageRef.HOME, PageRef.TERMINAL}) {
                            Snapshot rest = device(home, workspace, density, slots).build()
                                .restOf(side);
                            DockLayout loose = DockGeometry.compute(rest);
                            for (int squeezePx : new int[] {1, 24, 400}) {
                                DockLayout squeezed = DockGeometry.compute(
                                    rest.toBuilder().squeezePx(squeezePx).build());
                                String what = at + " " + side + " squeezed " + squeezePx;
                                assertSameRow(what, loose, squeezed);
                                assertTrue(what + ": the band never grows",
                                    squeezed.appsRowBandPx <= loose.appsRowBandPx);
                                assertTrue(what + ": the row never grows",
                                    squeezed.appsBarHeightPx <= loose.appsBarHeightPx);
                            }
                        }
                    }
    }

    // ---- 5. The row's width and pitch hold across a slide ----

    @Test
    public void theRowsWidthAndPitchHoldAcrossASlide() {
        for (float density : DENSITIES)
            for (boolean capsule : STYLES)
                for (float scale : sizes(capsule))
                    for (int slots : SLOTS)
                        for (DockStyle[] designs : new DockStyle[][] {
                                {DockStyle.LENS_BEADS, DockStyle.GLASS_TRAY},
                                {DockStyle.GLASS_TRAY, DockStyle.LENS_BEADS},
                                {DockStyle.LENS_BEADS, DockStyle.WALL_STRIP},
                                {DockStyle.WALL_STRIP, DockStyle.GLASS_TRAY}}) {
                            String at = at(density, capsule, scale, slots) + " window "
                                + designs[0] + " home " + designs[1];
                            // Halves that differ: their designs, Home a size up with a field.
                            Half workspace = half(WINDOW_APPS, capsule, scale, designs[0], false);
                            Half home = half(HOME, capsule,
                                Math.min(DockLayoutPolicy.maxUsefulScale(capsule), scale + 0.23f),
                                designs[1], true);
                            Snapshot.Builder device = device(home, workspace, density, slots);
                            assertEachFrameHoldsItsHalf(at + " toward Home", device,
                                PageRef.TERMINAL);
                            assertEachFrameHoldsItsHalf(at + " toward a window", device,
                                PageRef.HOME);
                        }
    }

    /**
     * A slide from {@code from}'s half to the other, frame by frame: every ease goes the whole
     * way (the Search field and the Wall strip's row from the departing half's to the arriving
     * half's), and the rows swap halves halfway, the seam then standing the arriving half's rows
     * in the departing arrangement. Every frame, the row the frame shows is as wide, and its slots
     * as far apart, as that half's row at rest, and draws the same icon.
     */
    private static void assertEachFrameHoldsItsHalf(String at, Snapshot.Builder device,
                                                    PageRef from) {
        Snapshot base = device.build();
        PageRef to = from == PageRef.HOME ? PageRef.TERMINAL : PageRef.HOME;
        Half departing = base.half(from);
        Half arriving = base.half(to);
        PlaceLayout seam = SeamTravel.withDockOf(departing.layout, arriving.layout);
        float fieldFrom = departing.searchField ? 1f : 0f;
        float fieldTo = arriving.searchField ? 1f : 0f;
        float stripFrom = departing.design == DockStyle.WALL_STRIP ? 1f : 0f;
        float stripTo = arriving.design == DockStyle.WALL_STRIP ? 1f : 0f;
        for (int step = 0; step <= 10; step++) {
            float p = step / 10f;
            PageRef shown = p >= 0.5f ? to : from;
            Snapshot frameSnapshot = base.toBuilder()
                .rows(shown)
                .arrangement(shown == to ? seam : departing.layout)
                .fieldReveal(fieldFrom + (fieldTo - fieldFrom) * p)
                .stripReveal(stripFrom + (stripTo - stripFrom) * p)
                .build();
            DockLayout frame = DockGeometry.compute(frameSnapshot);
            // The rest is the shown half's own snapshot, worked out apart from the frame.
            Snapshot restSnapshot = base.restOf(shown);
            DockLayout rest = DockGeometry.compute(restSnapshot);
            String what = at + " at " + p + " showing " + shown;
            assertTrue(what, frame.appsRowUsableWidthPx > 0);
            assertEquals(what + ": the row's width", rest.appsRowUsableWidthPx,
                frame.appsRowUsableWidthPx);
            // The pitch the row lays its slots at: its width over the slots a page holds, each
            // read from its own snapshot (the Drawer tile takes one of those slots).
            assertEquals(what + ": the pitch",
                pitchPx(rest, restSnapshot), pitchPx(frame, frameSnapshot), 0f);
            assertEquals(what + ": the slot's icon", rest.slotIconPx, frame.slotIconPx);
        }
    }

    /** The distance between two slots' centres as the row lays them out. */
    private static float pitchPx(DockLayout layout, Snapshot snapshot) {
        return layout.appsRowUsableWidthPx / (float) Math.max(1, snapshot.pinnedSlotCount);
    }

    @Test
    public void onOneLayoutTheRowsWidthNeverStepsAcrossASlide() {
        for (float density : DENSITIES)
            for (boolean capsule : STYLES)
                for (float scale : sizes(capsule))
                    for (int slots : SLOTS) {
                        String at = at(density, capsule, scale, slots) + " one layout";
                        Half workspace = half(WINDOW_APPS, capsule, scale, DockStyle.LENS_BEADS,
                            false);
                        Half home = Half.builder(HOME_ON_ONE_LAYOUT).floating(capsule)
                            .dockHeightScale(scale).design(DockStyle.LENS_BEADS).build();
                        Snapshot base = device(home, workspace, density, slots).build();
                        int width = DockGeometry.compute(base.restOf(PageRef.TERMINAL))
                            .appsRowUsableWidthPx;
                        PlaceLayout seam = SeamTravel.withDockOf(WINDOW_APPS, HOME_ON_ONE_LAYOUT);
                        for (int step = 0; step <= 10; step++) {
                            boolean homeRows = step >= 5;
                            DockLayout frame = DockGeometry.compute(base.toBuilder()
                                .rows(homeRows ? PageRef.HOME : PageRef.TERMINAL)
                                .arrangement(homeRows ? seam : WINDOW_APPS).build());
                            assertEquals(at + " at " + step, width, frame.appsRowUsableWidthPx);
                        }
                    }
    }

    // ---- The memo ----

    @Test
    public void anEqualSnapshotIsAnsweredWithTheSameLayout() {
        Half workspace = half(WINDOW_APPS, false, 2.18f, DockStyle.LENS_BEADS, false);
        Half home = half(HOME, false, 2.18f, DockStyle.GLASS_TRAY, true);
        DockGeometry geometry = new DockGeometry();
        DockLayout first = geometry.layout(device(home, workspace, 2.625f, 7).build());
        DockLayout again = geometry.layout(device(home.toBuilder().build(),
            workspace.toBuilder().build(), 2.625f, 7).build());
        assertSame(first, again);
        DockLayout squeezed = geometry.layout(device(home, workspace, 2.625f, 7).squeezePx(40)
            .build());
        assertNotSame(first, squeezed);
        // The pass asks for its squeezed and unsqueezed stack in turn: both stay remembered.
        assertSame(first, geometry.layout(device(home, workspace, 2.625f, 7).build()));
        assertSame(squeezed, geometry.layout(device(home, workspace, 2.625f, 7).squeezePx(40)
            .build()));
    }
}
