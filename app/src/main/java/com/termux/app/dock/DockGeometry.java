package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.ResolvedArrangement;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.app.wall.PageRef;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Dock geometry: the one owner of the dock's measurements (ADR 0011). One {@link Snapshot} of both
 * halves of the wall goes in, one {@link DockLayout} comes out, with no reference into the
 * activity. The snapshot carries raw facts — each half's resolved arrangement and sizes, which
 * half's rows the frame shows, the device and the live eases — and everything else is derived here:
 * whether the row stands alone, its page strip and air, the put-away row's icon, the rail, the
 * row's usable width from its design's side inset, and the icon one slot draws with the slot fit
 * and the bead fit applied.
 *
 * <p>A slide across the seam is an input, never a path of its own: a frame that shows the other
 * half's rows names that half in {@link Snapshot#rows}, and its rows are measured in that half's
 * Style, size and design from the first frame. {@link DockLayoutPolicy} is the arithmetic behind
 * it, unchanged.
 *
 * <p>{@link #layout} is memoised on snapshot equality, so a pass that asks again with the same
 * facts — or a travel frame that moved nothing the dock reads — gets the same answer back.
 */
public final class DockGeometry {

    /** The pages asked, in order, for the pinned-apps row a layout puts away: Home first. */
    private static final PageRef[] PUT_AWAY_ROW_OWNERS = {PageRef.HOME, PageRef.TERMINAL};

    /**
     * One half's raw facts: its resolved arrangement (#141), which says its layout, Style, Dock
     * design and Search field, and the sizes its dress holds.
     */
    public static final class Half {
        /** The half's resolved arrangement, in the orientation on screen. */
        @NonNull public final ResolvedArrangement arrangement;
        /** The half's stored layout ({@link ResolvedArrangement#layout}). */
        @NonNull public final PlaceLayout layout;
        /** Its Style: Floating (the capsule) or Docked ({@link ResolvedArrangement#floating}). */
        public final boolean floating;
        /** Its stack dress's dock size, as stored (a raw scale). */
        public final float dockHeightScale;
        /** Its Margin and Corners in dp, as stored (-1 Corners is the auto token). */
        public final int marginDp;
        public final int cornersDp;
        /**
         * Its Dock design, and the Search field its layout turns on. The Drawer tile is not a fact
         * of the dock's measurements: it takes one of the row's slots, which share the row's width
         * as they would without it, so the rows draw it ({@code SuggestionBarView#setDrawerTile}).
         */
        @NonNull public final DockStyle design;
        public final boolean searchField;
        /** The Wall strip's row height its layout keeps, dp; zero or less for the shipped one. */
        public final int wallStripHeightDp;

        private Half(@NonNull Builder b) {
            arrangement = b.arrangement();
            layout = arrangement.layout();
            floating = arrangement.floating();
            dockHeightScale = b.dockHeightScale;
            marginDp = b.marginDp;
            cornersDp = b.cornersDp;
            design = arrangement.design();
            searchField = arrangement.searchField();
            wallStripHeightDp = b.wallStripHeightDp;
        }

        /** A half of {@code arrangement}: its layout, Style, design and Search field as resolved. */
        @NonNull
        public static Builder builder(@NonNull ResolvedArrangement arrangement) {
            return new Builder(arrangement);
        }

        /**
         * A half standing {@code layout}, Docked in the Tray with no Search field until the
         * builder says otherwise: for a reader that has a layout and no arrangement (tests).
         */
        @NonNull
        public static Builder builder(@NonNull PlaceLayout layout) {
            return new Builder(ResolvedArrangement.of(layout, LayoutStyle.DOCKED,
                DockStyle.GLASS_TRAY, false, false, PlaceOrientation.PORTRAIT,
                LayoutVariant.NORMAL, PageRef.TERMINAL));
        }

        /** This half with its facts as they are, to change one or two. */
        @NonNull
        public Builder toBuilder() {
            return new Builder(arrangement).dockHeightScale(dockHeightScale)
                .marginDp(marginDp).cornersDp(cornersDp).wallStripHeightDp(wallStripHeightDp);
        }

        /** Equal on what the dock measures; the Drawer tile and where it was resolved are not. */
        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Half)) return false;
            Half that = (Half) other;
            return floating == that.floating
                && Float.compare(dockHeightScale, that.dockHeightScale) == 0
                && marginDp == that.marginDp
                && cornersDp == that.cornersDp
                && design == that.design
                && searchField == that.searchField
                && wallStripHeightDp == that.wallStripHeightDp
                && layout.equals(that.layout);
        }

        @Override
        public int hashCode() {
            return Objects.hash(layout, floating, dockHeightScale, marginDp, cornersDp, design,
                searchField, wallStripHeightDp);
        }

        public static final class Builder {
            @NonNull private final ResolvedArrangement base;
            private boolean floating;
            @NonNull private DockStyle design;
            private boolean searchField;
            private float dockHeightScale =
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT;
            private int marginDp =
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET;
            private int cornersDp = -1;
            private int wallStripHeightDp;

            private Builder(@NonNull ResolvedArrangement arrangement) {
                base = arrangement;
                floating = arrangement.floating();
                design = arrangement.design();
                searchField = arrangement.searchField();
            }

            public Builder floating(boolean v) { floating = v; return this; }
            public Builder dockHeightScale(float v) { dockHeightScale = v; return this; }
            public Builder marginDp(int v) { marginDp = v; return this; }
            public Builder cornersDp(int v) { cornersDp = v; return this; }
            public Builder design(@NonNull DockStyle v) { design = v; return this; }
            public Builder searchField(boolean v) { searchField = v; return this; }
            public Builder wallStripHeightDp(int v) { wallStripHeightDp = v; return this; }

            /** The arrangement as handed in, or remade where the builder changed its dress. */
            @NonNull
            private ResolvedArrangement arrangement() {
                if (floating == base.floating() && design == base.design()
                    && searchField == base.searchField()) return base;
                return ResolvedArrangement.of(base.layout(),
                    floating ? LayoutStyle.FLOATING : LayoutStyle.DOCKED, design, searchField,
                    base.drawerTile(), base.orientation(), base.variant(), base.page());
            }

            @NonNull
            public Half build() {
                return new Half(this);
            }
        }
    }

    /**
     * Everything the dock's measurements are a function of, for one frame: both halves, which
     * half's rows it shows and the arrangement it stands them in, the device, the pinned slot
     * count and the live eases, and how much the stack must shed to fit. The keyboard is not one:
     * whether it is up changes the stack (Stack layout), never the dock's rows.
     */
    public static final class Snapshot {
        /** False before the preference store is attached; the rows collapse to zero then. */
        public final boolean preferencesAvailable;
        @NonNull public final Half home;
        @NonNull public final Half workspace;
        /**
         * A second Home page's half, for a crossing between two Home pages (#173 slice E): it
         * answers for the page its arrangement is resolved for ({@link #half}), {@link #home} for
         * every other Home page. Null while the snapshot holds one Home half.
         */
        @Nullable public final Half otherHome;
        /**
         * The page whose rows the frame shows, and whose dress they wear: Home's half answers for
         * a Home page, the Workspace's for every window ({@link #half}).
         */
        @NonNull public final PageRef rows;
        /**
         * The arrangement the frame stands its chrome in: the committed half's layout, or mid-slide
         * the layout the seam stands with the other half's rows ({@code SeamCrossing#layout}).
         */
        @NonNull public final PlaceLayout arrangement;
        public final float density;
        /** The width the dock's rows span: the content's, between the display cutouts. */
        public final int widthPx;
        public final int cutoutLeftPx;
        public final int cutoutRightPx;
        /** The extra-keys toolbar's single-row height, the dock size curve's unit. */
        public final int baseToolbarHeightPx;
        public final boolean landscape;
        /** The pinned slots a page of the row shares its width between. */
        public final int pinnedSlotCount;
        /** How much of the Search field and of the Wall strip's row the slab carries, 0..1. */
        public final float fieldReveal;
        public final float stripReveal;
        /** How much the bottom stack must shed to fit under its ceiling, px; 0 when it fits. */
        public final int squeezePx;

        private Snapshot(@NonNull Builder b) {
            preferencesAvailable = b.preferencesAvailable;
            home = b.home;
            workspace = b.workspace;
            otherHome = b.otherHome;
            rows = b.rows;
            arrangement = b.arrangement != null ? b.arrangement
                : half(b.rows, b.home, b.otherHome, b.workspace).layout;
            density = b.density;
            widthPx = b.widthPx;
            cutoutLeftPx = b.cutoutLeftPx;
            cutoutRightPx = b.cutoutRightPx;
            baseToolbarHeightPx = b.baseToolbarHeightPx;
            landscape = b.landscape;
            pinnedSlotCount = b.pinnedSlotCount;
            fieldReveal = b.fieldReveal;
            stripReveal = b.stripReveal;
            squeezePx = b.squeezePx;
        }

        @NonNull
        public static Builder builder(@NonNull Half home, @NonNull Half workspace) {
            return new Builder(home, workspace);
        }

        /**
         * The half {@code page} stands in: Home's for a Home page, the Workspace's for a window;
         * the second Home half ({@link #otherHome}) for the Home page it is resolved for, while a
         * crossing between two Home pages holds one.
         */
        @NonNull
        public Half half(@NonNull PageRef page) {
            return half(page, home, otherHome, workspace);
        }

        @NonNull
        private static Half half(@NonNull PageRef page, @NonNull Half home,
                                 @Nullable Half otherHome, @NonNull Half workspace) {
            if (!page.isHome()) return workspace;
            if (otherHome != null && page.id >= 0
                && otherHome.arrangement.page().equals(page)
                && !home.arrangement.page().equals(page)) return otherHome;
            return home;
        }

        /** The half whose rows the frame shows. */
        @NonNull
        public Half rowsHalf() {
            return half(rows);
        }

        /**
         * {@code page} at rest, on this device: its own rows in its own layout, its Search field
         * and Wall strip wholly in or out as its dress has them, nothing squeezed.
         */
        @NonNull
        public Snapshot restOf(@NonNull PageRef page) {
            Half half = half(page);
            return toBuilder().rows(page).arrangement(half.layout)
                .fieldReveal(half.searchField ? 1f : 0f)
                .stripReveal(half.design == DockStyle.WALL_STRIP ? 1f : 0f)
                .squeezePx(0)
                .build();
        }

        @NonNull
        public Builder toBuilder() {
            return new Builder(home, workspace).otherHome(otherHome)
                .preferencesAvailable(preferencesAvailable)
                .rows(rows).arrangement(arrangement)
                .density(density).widthPx(widthPx).cutouts(cutoutLeftPx, cutoutRightPx)
                .baseToolbarHeightPx(baseToolbarHeightPx).landscape(landscape)
                .pinnedSlotCount(pinnedSlotCount).fieldReveal(fieldReveal)
                .stripReveal(stripReveal).squeezePx(squeezePx);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot)) return false;
            Snapshot that = (Snapshot) other;
            return preferencesAvailable == that.preferencesAvailable
                && rows == that.rows
                && Float.compare(density, that.density) == 0
                && widthPx == that.widthPx
                && cutoutLeftPx == that.cutoutLeftPx
                && cutoutRightPx == that.cutoutRightPx
                && baseToolbarHeightPx == that.baseToolbarHeightPx
                && landscape == that.landscape
                && pinnedSlotCount == that.pinnedSlotCount
                && Float.compare(fieldReveal, that.fieldReveal) == 0
                && Float.compare(stripReveal, that.stripReveal) == 0
                && squeezePx == that.squeezePx
                && arrangement.equals(that.arrangement)
                && home.equals(that.home)
                && Objects.equals(otherHome, that.otherHome)
                && workspace.equals(that.workspace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(preferencesAvailable, home, otherHome, workspace, rows, arrangement, density, widthPx, cutoutLeftPx, cutoutRightPx, baseToolbarHeightPx,
                landscape, pinnedSlotCount, fieldReveal, stripReveal, squeezePx);
        }

        public static final class Builder {
            @NonNull private final Half home;
            @NonNull private final Half workspace;
            @Nullable private Half otherHome;
            private boolean preferencesAvailable = true;
            @NonNull private PageRef rows = PageRef.TERMINAL;
            @Nullable private PlaceLayout arrangement;
            private float density = 1f;
            private int widthPx;
            private int cutoutLeftPx;
            private int cutoutRightPx;
            private int baseToolbarHeightPx;
            private boolean landscape;
            private int pinnedSlotCount = 1;
            private float fieldReveal;
            private float stripReveal;
            private int squeezePx;

            private Builder(@NonNull Half home, @NonNull Half workspace) {
                this.home = home;
                this.workspace = workspace;
            }

            /** A second Home page's half ({@link Snapshot#otherHome}); null for none. */
            public Builder otherHome(@Nullable Half v) { otherHome = v; return this; }
            public Builder preferencesAvailable(boolean v) { preferencesAvailable = v; return this; }
            public Builder rows(@NonNull PageRef v) { rows = v; return this; }
            public Builder arrangement(@Nullable PlaceLayout v) { arrangement = v; return this; }
            public Builder density(float v) { density = v; return this; }
            public Builder widthPx(int v) { widthPx = v; return this; }
            public Builder cutouts(int left, int right) {
                cutoutLeftPx = left;
                cutoutRightPx = right;
                return this;
            }
            public Builder baseToolbarHeightPx(int v) { baseToolbarHeightPx = v; return this; }
            public Builder landscape(boolean v) { landscape = v; return this; }
            public Builder pinnedSlotCount(int v) { pinnedSlotCount = v; return this; }
            public Builder fieldReveal(float v) { fieldReveal = v; return this; }
            public Builder stripReveal(float v) { stripReveal = v; return this; }
            public Builder squeezePx(int v) { squeezePx = Math.max(0, v); return this; }

            @NonNull
            public Snapshot build() {
                return new Snapshot(this);
            }
        }
    }

    // ---- The memo: the last two answers, so a pass that asks for its squeezed and unsqueezed
    // stack in turn does not throw either away. ----

    @Nullable private Snapshot mLastSnapshot;
    @Nullable private DockLayout mLastLayout;
    @Nullable private Snapshot mPreviousSnapshot;
    @Nullable private DockLayout mPreviousLayout;

    /** The dock for {@code snapshot}: the same instance again while the snapshot is equal. */
    @NonNull
    public DockLayout layout(@NonNull Snapshot snapshot) {
        if (mLastLayout != null && snapshot.equals(mLastSnapshot)) return mLastLayout;
        if (mPreviousLayout != null && snapshot.equals(mPreviousSnapshot)) {
            Snapshot s = mPreviousSnapshot;
            DockLayout l = mPreviousLayout;
            mPreviousSnapshot = mLastSnapshot;
            mPreviousLayout = mLastLayout;
            mLastSnapshot = s;
            mLastLayout = l;
            return l;
        }
        DockLayout layout = compute(snapshot);
        mPreviousSnapshot = mLastSnapshot;
        mPreviousLayout = mLastLayout;
        mLastSnapshot = snapshot;
        mLastLayout = layout;
        return layout;
    }

    /** The dock for {@code snapshot}, worked out afresh. */
    @NonNull
    public static DockLayout compute(@NonNull Snapshot s) {
        Half rows = s.rowsHalf();
        PlaceLayout layout = s.arrangement;
        boolean prefs = s.preferencesAvailable;
        boolean appsShown = PlaceChromePolicy.appsShown(layout);
        boolean rail = PlaceChromePolicy.appsRailShown(layout);
        List<Element> bottom = EdgeStackPolicy.stack(layout, PlaceLayout.Edge.BOTTOM);
        DockLayoutPolicy.DockInputs.Builder in = DockLayoutPolicy.DockInputs.builder();
        // A row this layout puts away is measured where it shows, so it comes back at its size.
        if (prefs && !appsShown) measurePutAwayRow(s, in);
        return DockLayoutPolicy.compute(in
            .preferencesAvailable(prefs)
            .capsule(prefs && rows.floating)
            .appsRowOnEdge(appsShown && PlaceChromePolicy.appsEdge(layout) != PlaceLayout.Edge.BOTTOM)
            .appsOnRail(rail)
            .appsRowAlone(appsRowAlone(layout))
            .appsRowNextToCanvas(appsRowNextToCanvas(layout))
            // A row that lies down carries the ticks in its own air; a rail's stand in a column
            // beside it, so its cross-axis keeps the plain air of a lone row.
            .appsRowPageStripShown(!rail)
            // The design of the half whose rows these are (#93), from the frame they show.
            .surface(DockSurfaceSpec.forStyle(rows.design))
            .promptFieldReveal(s.fieldReveal)
            .wallStripReveal(s.stripReveal)
            .wallStripHeightDp(prefs ? rows.wallStripHeightDp : 0)
            .density(s.density)
            .barHeightScale(prefs ? rows.dockHeightScale : DockLayoutPolicy.sizePreset(2))
            .dockHorizontalInsetDp(prefs
                ? TermuxAppSharedPreferences.clampSurfaceHorizontalInset(rows.marginDp)
                : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET)
            .configuredCornerRadiusDp(prefs
                ? cornerRadiusDp(rows.cornersDp)
                : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS)
            .appsRowEnabledPref(appsShown)
            .azRowEnabledPref(PlaceChromePolicy.azRowOnDock(layout))
            .rowOverAz(AccessoryStackLayoutPolicy.rowOverAz(bottom))
            .rowUnderAz(AccessoryStackLayoutPolicy.rowUnderAz(bottom))
            .baseToolbarHeightPx(s.baseToolbarHeightPx)
            // The squeeze is the stack's to shed; the policy never takes it out of the icon.
            .additionalAppsBarHeightPx(-s.squeezePx)
            .railOnRight(PlaceChromePolicy.appsRailOnRight(layout))
            .displayCutoutInsetLeftPx(s.cutoutLeftPx)
            .displayCutoutInsetRightPx(s.cutoutRightPx)
            .rowWidthPx(s.widthPx)
            .slotCount(s.pinnedSlotCount)
            .build());
    }

    /**
     * The pinned-apps row the frame's arrangement puts away, measured as the half that shows it
     * lays it out at rest: that half's Style, dock size and whether the row stands alone there for
     * its icon, and its design and width for its slot. The row stays in the dock's view while it
     * is away, so a slide brings it in at the size it settles at. No half shows it: no icon.
     */
    private static void measurePutAwayRow(@NonNull Snapshot s,
                                          @NonNull DockLayoutPolicy.DockInputs.Builder in) {
        for (PageRef page : PUT_AWAY_ROW_OWNERS) {
            Half owner = s.half(page);
            if (!PlaceChromePolicy.appsShown(owner.layout)) continue;
            in.hiddenRowAsShown(owner.floating, owner.dockHeightScale,
                appsRowAlone(owner.layout));
            DockLayout shown = compute(s.restOf(page));
            if (!shown.railActive)
                in.hiddenRowSlot(shown.slotIconPx, shown.appsRowUsableWidthPx);
            return;
        }
    }

    /**
     * The dock's configured corner radius for a Corners value: the follow-the-Style sentinel for
     * the auto token, else capped.
     */
    public static int cornerRadiusDp(int cornersDp) {
        if (cornersDp < 0)
            return TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS;
        return Math.min(TermuxPreferenceConstants.TERMUX_APP.MAX_APP_LAUNCHER_DOCK_CORNER_RADIUS,
            cornersDp);
    }

    /**
     * Whether the pinned-apps row is the band next to the canvas: the last band of its edge's
     * stack, which is the innermost one. It decides which side of the row its ticks stand on.
     */
    public static boolean appsRowNextToCanvas(@NonNull PlaceLayout layout) {
        List<Element> stack =
            EdgeStackPolicy.stack(layout, PlaceChromePolicy.appsEdge(layout));
        return !stack.isEmpty() && stack.get(stack.size() - 1) == Element.APPS;
    }

    /**
     * Whether the pinned-apps row shares its container with nothing: on the dock no band beside it
     * but the extra keys ({@link DockLayoutPolicy#appsRowAloneOnDock}); off the dock, no index
     * riding its plank; a rail always. It decides the air around the row's icons and its ticks.
     */
    public static boolean appsRowAlone(@NonNull PlaceLayout layout) {
        if (!PlaceChromePolicy.appsShown(layout)) return false;
        if (PlaceChromePolicy.appsEdge(layout) == PlaceLayout.Edge.BOTTOM)
            return DockLayoutPolicy.appsRowAloneOnDock(
                EdgeStackPolicy.stack(layout, PlaceLayout.Edge.BOTTOM));
        return offDockPlankBars(layout).size() <= 1;
    }

    /**
     * The bars sharing the pinned-apps row's plank off the dock, outermost first: the row, and the
     * alphabets index when it rides that row from the band right beside it. Empty while the row
     * stands on the dock, on a side, or nowhere.
     */
    @NonNull
    public static List<Element> offDockPlankBars(@NonNull PlaceLayout layout) {
        if (!PlaceChromePolicy.appsShown(layout)) return Collections.emptyList();
        PlaceLayout.Edge edge = PlaceChromePolicy.appsEdge(layout);
        if (edge.isOnSide() || edge == PlaceLayout.Edge.BOTTOM) return Collections.emptyList();
        List<Element> stack = EdgeStackPolicy.stack(layout, edge);
        int apps = stack.indexOf(Element.APPS);
        if (apps < 0) return Collections.emptyList();
        int az = PlaceChromePolicy.azRidesAppsRow(layout) ? stack.indexOf(Element.AZ) : -1;
        if (az < 0 || Math.abs(az - apps) != 1) return Collections.singletonList(Element.APPS);
        return az < apps
            ? Arrays.asList(Element.AZ, Element.APPS)
            : Arrays.asList(Element.APPS, Element.AZ);
    }
}
