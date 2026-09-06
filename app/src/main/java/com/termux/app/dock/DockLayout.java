package com.termux.app.dock;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;

/**
 * One immutable snapshot of every dock number a layout pass needs: row heights, paddings, insets,
 * the rail column, the icon scale and the capsule radius rule.
 *
 * <p>Produced by {@link DockLayoutPolicy#compute(DockLayoutPolicy.DockInputs)}. Nothing here reads
 * preferences, resources or views — the values are fixed at construction, so the whole dock geometry
 * is testable as plain arithmetic.
 */
public final class DockLayout {

    /** True when the rounded (capsule) dock style is selected. */
    public final boolean capsule;
    /** True when the pinned apps stand on a screen edge, where the horizontal rows collapse. */
    public final boolean appsRowOnEdge;
    public final float density;

    // --- Row metrics (the old DockLayoutMetrics) ---
    public final int appsBarHeightPx;
    public final int indicatorBandHeightPx;
    public final int azRowHeightPx;
    /** Dead space under the A-Z row's letters, drawn as its bottom padding; 0 unless it is last. */
    public final int azRowChinPaddingPx;
    public final int interRowGapPx;
    /** The apps row's usable (icon) height: the row minus its own vertical padding. */
    public final int appsBarHeightHintPx;
    /** Row switches after the edge gate, mirroring the render state's collapse. */
    public final boolean appsRowEnabled;
    public final boolean azRowEnabled;

    // --- Size curve ---
    public final float sizeProgress;
    public final float defaultDockSizeProgress;
    /** The dock icon scale for the active style and size preset. */
    public final float iconScale;

    // --- Horizontal insets ---
    /** The dock's outer screen margin in the active style. */
    public final int horizontalInsetPx;
    /** The dock's outer screen margin as if the capsule style were selected. */
    public final int capsuleHorizontalMarginPx;
    public final int capsuleContentInsetPx;
    public final int capsuleExtraKeysInsetPx;

    // --- Vertical paddings ---
    public final int capsuleAppsTotalPaddingPx;
    public final int capsuleAppsTopPaddingPx;
    public final int capsuleAppsBottomPaddingPx;
    public final int defaultAppsTopPaddingPx;
    public final int defaultAppsBottomPaddingPx;
    /** The apps row's top padding in the active style. */
    public final int appsTopPaddingPx;
    /** The apps row's bottom padding in the active style. */
    public final int appsBottomPaddingPx;
    /** The gap a floating capsule keeps below itself. */
    public final int capsuleBottomGapPx;

    // --- Apps rail ---
    public final boolean railActive;
    public final boolean railOnRight;
    public final AppDrawerGestureArbiter.Pull railPull;
    public final int railEdgeInsetPx;
    public final int railWidthPx;

    /** The top pane's compact height in the active style, read by the drawer's top-band clip. */
    public final int compactStatusBarHeightPx;

    /** Configured dock corner radius in dp, or a negative value for the follow-the-style radius. */
    private final int mConfiguredCornerRadiusDp;

    DockLayout(Builder b) {
        this.capsule = b.capsule;
        this.appsRowOnEdge = b.appsRowOnEdge;
        this.density = b.density;
        this.appsBarHeightPx = Math.max(0, b.appsBarHeightPx);
        this.indicatorBandHeightPx = Math.max(0, b.indicatorBandHeightPx);
        this.azRowHeightPx = Math.max(0, b.azRowHeightPx);
        this.azRowChinPaddingPx = Math.max(0, b.azRowChinPaddingPx);
        this.interRowGapPx = Math.max(0, b.interRowGapPx);
        this.appsBarHeightHintPx = Math.max(0, b.appsBarHeightHintPx);
        this.appsRowEnabled = b.appsRowEnabled;
        this.azRowEnabled = b.azRowEnabled;
        this.sizeProgress = b.sizeProgress;
        this.defaultDockSizeProgress = b.defaultDockSizeProgress;
        this.iconScale = b.iconScale;
        this.horizontalInsetPx = b.horizontalInsetPx;
        this.capsuleHorizontalMarginPx = b.capsuleHorizontalMarginPx;
        this.capsuleContentInsetPx = b.capsuleContentInsetPx;
        this.capsuleExtraKeysInsetPx = b.capsuleExtraKeysInsetPx;
        this.capsuleAppsTotalPaddingPx = b.capsuleAppsTotalPaddingPx;
        this.capsuleAppsTopPaddingPx = b.capsuleAppsTopPaddingPx;
        this.capsuleAppsBottomPaddingPx = b.capsuleAppsBottomPaddingPx;
        this.defaultAppsTopPaddingPx = b.defaultAppsTopPaddingPx;
        this.defaultAppsBottomPaddingPx = b.defaultAppsBottomPaddingPx;
        this.appsTopPaddingPx = b.appsTopPaddingPx;
        this.appsBottomPaddingPx = b.appsBottomPaddingPx;
        this.capsuleBottomGapPx = b.capsuleBottomGapPx;
        this.railActive = b.railActive;
        this.railOnRight = b.railOnRight;
        this.railPull = b.railPull;
        this.railEdgeInsetPx = b.railEdgeInsetPx;
        this.railWidthPx = b.railWidthPx;
        this.compactStatusBarHeightPx = b.compactStatusBarHeightPx;
        this.mConfiguredCornerRadiusDp = b.configuredCornerRadiusDp;
    }

    /**
     * The capsule radius for a surface of the given height, never more than a true half-capsule.
     * Also the command palette's open-state radius, so the two glass surfaces read as one kit.
     */
    public float capsuleCornerRadiusPx(int surfaceHeightPx) {
        return DockLayoutPolicy.capsuleCornerRadiusPx(mConfiguredCornerRadiusDp, surfaceHeightPx,
            density);
    }

    /** The dock's stacked height for the given extra-keys toolbar height. */
    public int combinedHeight(int toolbarHeightPx, boolean extraKeysRowEnabled) {
        return AccessoryStackLayoutPolicy.computeCombinedHeight(
            appsBarHeightPx > 0,
            azRowHeightPx > 0,
            extraKeysRowEnabled,
            appsBarHeightPx,
            azRowHeightPx,
            toolbarHeightPx,
            indicatorBandHeightPx);
    }

    static final class Builder {
        boolean capsule;
        boolean appsRowOnEdge;
        float density;
        int appsBarHeightPx;
        int indicatorBandHeightPx;
        int azRowHeightPx;
        int azRowChinPaddingPx;
        int interRowGapPx;
        int appsBarHeightHintPx;
        boolean appsRowEnabled;
        boolean azRowEnabled;
        float sizeProgress;
        float defaultDockSizeProgress;
        float iconScale;
        int horizontalInsetPx;
        int capsuleHorizontalMarginPx;
        int capsuleContentInsetPx;
        int capsuleExtraKeysInsetPx;
        int capsuleAppsTotalPaddingPx;
        int capsuleAppsTopPaddingPx;
        int capsuleAppsBottomPaddingPx;
        int defaultAppsTopPaddingPx;
        int defaultAppsBottomPaddingPx;
        int appsTopPaddingPx;
        int appsBottomPaddingPx;
        int capsuleBottomGapPx;
        boolean railActive;
        boolean railOnRight;
        AppDrawerGestureArbiter.Pull railPull = AppDrawerGestureArbiter.Pull.NONE;
        int railEdgeInsetPx;
        int railWidthPx;
        int compactStatusBarHeightPx;
        int configuredCornerRadiusDp;

        DockLayout build() {
            return new DockLayout(this);
        }
    }
}
