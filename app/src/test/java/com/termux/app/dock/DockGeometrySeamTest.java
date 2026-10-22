package com.termux.app.dock;

import com.termux.app.dock.DockGeometry.Half;
import com.termux.app.dock.DockGeometry.Side;
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

    private static DockLayout rest(Snapshot.Builder device, Side side) {
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
                            DockLayout window = rest(device, Side.WORKSPACE);
                            DockLayout onHome = rest(device, Side.HOME);
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
                            DockLayout homeRest = rest(device, Side.HOME);
                            DockLayout workspaceRest = rest(device, Side.WORKSPACE);

                            // From a window toward Home: Home's rows, the field easing in.
                            PlaceLayout towardHome = SeamTravel.withDockOf(WINDOW_APPS, HOME);
                            for (float p : new float[] {0.25f, 0.5f, 0.75f, 1f}) {
                                DockLayout frame = DockGeometry.compute(device.rows(Side.HOME)
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
                                    .rows(Side.WORKSPACE).arrangement(towardWindow)
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
                            DockLayout homeRest = rest(device, Side.HOME);
                            DockLayout window = rest(device, Side.WORKSPACE);
                            assertSameRow(at + " in the window", homeRest, window);
                            // Its band stays the window's: none.
                            assertEquals(at, 0, window.appsRowBandPx);
                            assertEquals(at, 0, window.appsBarHeightPx);
                            // And the slide that brings it in hands it the same.
                            DockLayout sliding = DockGeometry.compute(device.rows(Side.HOME)
                                .arrangement(SeamTravel.withDockOf(WINDOW_KEYS, HOME)).build());
                            assertSameRow(at + " sliding in", homeRest, sliding);
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
                        for (Side side : Side.values()) {
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
                    for (int slots : SLOTS) {
                        String at = at(density, capsule, scale, slots);
                        // Halves that differ: a window on Beads, Home on Tray with a field.
                        Half workspace = half(WINDOW_APPS, capsule, scale, DockStyle.LENS_BEADS,
                            false);
                        Half home = half(HOME, capsule, scale, DockStyle.GLASS_TRAY, true);
                        assertEachFrameHoldsItsHalf(at, device(home, workspace, density, slots),
                            slots, false);
                        // One layout: one width and one pitch from the first frame to the last.
                        Half shared = half(WINDOW_APPS, capsule, scale, DockStyle.LENS_BEADS,
                            false);
                        Half sharedHome = Half.builder(HOME_ON_ONE_LAYOUT).floating(capsule)
                            .dockHeightScale(scale).design(DockStyle.LENS_BEADS).build();
                        assertEachFrameHoldsItsHalf(at + " one layout",
                            device(sharedHome, shared, density, slots), slots, true);
                    }
    }

    /**
     * A slide from a window to Home, frame by frame: the rows switch halves halfway, the Search
     * field eases with the page. Every frame's row is as wide, and its slots as far apart, as the
     * row of the half it shows at rest; with {@code constant}, as each other's too.
     */
    private static void assertEachFrameHoldsItsHalf(String at, Snapshot.Builder device, int slots,
                                                    boolean constant) {
        Snapshot base = device.build();
        DockLayout workspaceRest = DockGeometry.compute(base.restOf(Side.WORKSPACE));
        DockLayout homeRest = DockGeometry.compute(base.restOf(Side.HOME));
        float fieldFrom = base.workspace.searchField ? 1f : 0f;
        float fieldTo = base.home.searchField ? 1f : 0f;
        PlaceLayout towardHome = SeamTravel.withDockOf(base.workspace.layout, base.home.layout);
        int firstWidth = -1;
        for (int step = 0; step <= 10; step++) {
            float p = step / 10f;
            boolean homeRows = p >= 0.5f;
            DockLayout frame = DockGeometry.compute(base.toBuilder()
                .rows(homeRows ? Side.HOME : Side.WORKSPACE)
                .arrangement(homeRows ? towardHome : base.workspace.layout)
                .fieldReveal(fieldFrom + (fieldTo - fieldFrom) * p)
                .build());
            DockLayout rest = homeRows ? homeRest : workspaceRest;
            String what = at + " at " + p;
            assertTrue(what, frame.appsRowUsableWidthPx > 0);
            assertEquals(what + ": the row's width", rest.appsRowUsableWidthPx,
                frame.appsRowUsableWidthPx);
            assertEquals(what + ": the pitch", rest.appsRowUsableWidthPx / slots,
                frame.appsRowUsableWidthPx / slots);
            assertEquals(what + ": the slot's icon", rest.slotIconPx, frame.slotIconPx);
            if (constant) {
                if (firstWidth < 0) firstWidth = frame.appsRowUsableWidthPx;
                assertEquals(what + ": one width across the slide", firstWidth,
                    frame.appsRowUsableWidthPx);
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
