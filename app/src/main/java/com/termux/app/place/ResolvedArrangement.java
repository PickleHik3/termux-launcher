package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import java.util.Objects;

/**
 * A half's resolved arrangement (#141): where every Element stands on one half of the wall, in one
 * orientation, and its shape. Resolved once per (half, orientation) from the stored layout, the
 * Style and the Dock design ({@link #resolve}), then only read: every chrome predicate is answered
 * here, from the same value, so no caller can see two arrangements within one crossing.
 *
 * <p>{@link PlaceChromePolicy} and {@link EdgeStackPolicy} stay the arithmetic; this value asks
 * them once, at construction, and holds the answers. {@link ChromeShapeModel} stays the shape
 * answerer (ADR 0007) and is handed this value's {@link #layout} and {@link #layoutStyle}.
 */
public final class ResolvedArrangement {

    @NonNull private final PlaceLayout mLayout;
    @NonNull private final LayoutStyle mStyle;
    @NonNull private final DockStyle mDesign;
    private final boolean mSearchField;
    private final boolean mDrawerTile;
    @NonNull private final PlaceOrientation mOrientation;
    @NonNull private final LayoutVariant mVariant;
    private final boolean mHome;

    private final boolean mAppsShown;
    @NonNull private final Edge mAppsEdge;
    private final boolean mAppsRowShown;
    private final boolean mAppsRailShown;
    private final boolean mAzRowShown;
    private final boolean mAzIndexShown;
    private final boolean mAzTabShown;
    @NonNull private final Edge mAzBarEdge;
    private final boolean mAzRowOnDock;
    private final boolean mExtraKeysShown;
    @NonNull private final Edge mExtraKeysEdge;
    private final boolean mExtraKeysRowShown;
    private final boolean mExtraKeysColumnShown;
    private final boolean mDockShown;

    private ResolvedArrangement(@NonNull PlaceLayout layout, @NonNull LayoutStyle style,
                                @NonNull DockStyle design, boolean searchField,
                                boolean drawerTile, @NonNull PlaceOrientation orientation,
                                @NonNull LayoutVariant variant, boolean home) {
        mLayout = layout;
        mStyle = style;
        mDesign = design;
        mSearchField = searchField;
        mDrawerTile = drawerTile;
        mOrientation = orientation;
        mVariant = variant;
        mHome = home;
        mAppsShown = PlaceChromePolicy.appsShown(layout);
        mAppsEdge = PlaceChromePolicy.appsEdge(layout);
        mAppsRowShown = PlaceChromePolicy.appsRowShown(layout);
        mAppsRailShown = PlaceChromePolicy.appsRailShown(layout);
        mAzRowShown = PlaceChromePolicy.azRowShown(layout);
        mAzIndexShown = PlaceChromePolicy.azIndexShown(layout);
        mAzTabShown = PlaceChromePolicy.azTabShown(layout);
        mAzBarEdge = PlaceChromePolicy.azBarEdge(layout);
        mAzRowOnDock = PlaceChromePolicy.azRowOnDock(layout);
        mExtraKeysShown = PlaceChromePolicy.extraKeysShown(layout);
        mExtraKeysEdge = PlaceChromePolicy.extraKeysEdge(layout);
        mExtraKeysRowShown = PlaceChromePolicy.extraKeysRowShown(layout);
        mExtraKeysColumnShown = PlaceChromePolicy.extraKeysColumnShown(layout);
        mDockShown = PlaceChromePolicy.dockShown(layout);
    }

    // ---- Resolving ----

    /**
     * {@code home}'s half as the store holds it now: the layout its dress wears ({@link
     * PlaceLayoutStore#variantFor}) in its Style ({@link PlaceLayoutStore#halfShape}, linked
     * across the wall, #84). The one place the activity's arrangement reads the store.
     */
    @NonNull
    public static ResolvedArrangement resolve(@NonNull PlaceLayoutStore store,
                                              @NonNull PlaceOrientation orientation,
                                              boolean home) {
        LayoutVariant variant = store.variantFor(pageOf(home));
        LayoutStyle style = store.shape().style;
        return resolve(store, orientation, variant, home, style);
    }

    /**
     * {@code home}'s half on {@code variant} in {@code style}: the layout the half wears ({@link
     * PlaceLayoutStore#resolve(PlaceOrientation, LayoutVariant, PaneWallPage)}, so a Home page
     * leaves out what Home never wears), and the Dock design, Search field and Drawer tile that
     * layout holds.
     */
    @NonNull
    public static ResolvedArrangement resolve(@NonNull PlaceLayoutStore store,
                                              @NonNull PlaceOrientation orientation,
                                              @NonNull LayoutVariant variant, boolean home,
                                              @NonNull LayoutStyle style) {
        PlaceLayoutStore view = store.forVariant(variant);
        PlaceLayout layout = store.resolve(orientation, variant, pageOf(home));
        return new ResolvedArrangement(layout, style, DockStyle.effectiveStyle(view.dockStyle()),
            view.searchField(), view.drawerTile(), orientation, variant, home);
    }

    /** An arrangement made of its parts, for a reader with no store (before one, and tests). */
    @NonNull
    public static ResolvedArrangement of(@NonNull PlaceLayout layout, @NonNull LayoutStyle style,
                                         @NonNull DockStyle design, boolean searchField,
                                         boolean drawerTile,
                                         @NonNull PlaceOrientation orientation,
                                         @NonNull LayoutVariant variant, boolean home) {
        return new ResolvedArrangement(layout, style, design, searchField, drawerTile,
            orientation, variant, home);
    }

    /**
     * This half's dress standing {@code layout}: mid-slide across the seam the chrome stands the
     * layout the crossing composes from both halves' arrangements ({@link SeamCrossing#layout}),
     * in the committed half's Style and design. Reads no store.
     */
    @NonNull
    public ResolvedArrangement standing(@NonNull PlaceLayout layout) {
        if (layout == mLayout) return this;
        return new ResolvedArrangement(layout, mStyle, mDesign, mSearchField, mDrawerTile,
            mOrientation, mVariant, mHome);
    }

    /** The page a half's arrangement is resolved for: Home's own page, or any window. */
    @NonNull
    private static PaneWallPage pageOf(boolean home) {
        return home ? PaneWallPage.WIDGETS : PaneWallPage.TERMINAL;
    }

    // ---- What it was resolved from ----

    @NonNull
    public PlaceLayout layout() {
        return mLayout;
    }

    @NonNull
    public LayoutStyle layoutStyle() {
        return mStyle;
    }

    /** The Floating Style: each piece a rounded card with air around it. */
    public boolean floating() {
        return mStyle == LayoutStyle.FLOATING;
    }

    /** The Dock design the half draws, as built ({@link DockStyle#effectiveStyle}). */
    @NonNull
    public DockStyle design() {
        return mDesign;
    }

    public boolean searchField() {
        return mSearchField;
    }

    public boolean drawerTile() {
        return mDrawerTile;
    }

    @NonNull
    public PlaceOrientation orientation() {
        return mOrientation;
    }

    @NonNull
    public LayoutVariant variant() {
        return mVariant;
    }

    /** Whether this is Home's half; the Workspace's otherwise. */
    public boolean home() {
        return mHome;
    }

    // ---- Where every Element stands (PlaceChromePolicy, asked once) ----

    public boolean appsShown() {
        return mAppsShown;
    }

    @NonNull
    public Edge appsEdge() {
        return mAppsEdge;
    }

    public boolean appsRowShown() {
        return mAppsRowShown;
    }

    public boolean appsRailShown() {
        return mAppsRailShown;
    }

    public boolean azTabShown() {
        return mAzTabShown;
    }

    public boolean azRowShown() {
        return mAzRowShown;
    }

    public boolean azIndexShown() {
        return mAzIndexShown;
    }

    @NonNull
    public Edge azBarEdge() {
        return mAzBarEdge;
    }

    public boolean azRowOnDock() {
        return mAzRowOnDock;
    }

    public boolean extraKeysShown() {
        return mExtraKeysShown;
    }

    @NonNull
    public Edge extraKeysEdge() {
        return mExtraKeysEdge;
    }

    public boolean extraKeysRowShown() {
        return mExtraKeysRowShown;
    }

    public boolean extraKeysColumnShown() {
        return mExtraKeysColumnShown;
    }

    public boolean dockShown() {
        return mDockShown;
    }

    // ---- Its shape ----

    /**
     * Whether the Docked frame's glass runs down to the bottom edge, under the gesture pill. In
     * Docked it does, except under a Beads dock, whose beads float on the background with the
     * frame ending above them (the glossary's Style). Floating has no frame to run anywhere.
     */
    public boolean frameRunsToBottom() {
        return !floating() && mDesign != DockStyle.LENS_BEADS;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof ResolvedArrangement)) return false;
        ResolvedArrangement that = (ResolvedArrangement) other;
        return mStyle == that.mStyle && mDesign == that.mDesign
            && mSearchField == that.mSearchField && mDrawerTile == that.mDrawerTile
            && mOrientation == that.mOrientation && mVariant == that.mVariant
            && mHome == that.mHome && mLayout.equals(that.mLayout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLayout, mStyle, mDesign, mSearchField, mDrawerTile, mOrientation,
            mVariant, mHome);
    }

    @NonNull
    @Override
    public String toString() {
        return (mHome ? "Home" : "Workspace") + " " + mOrientation + " " + mVariant + " "
            + mStyle + " " + mDesign;
    }
}
