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
    /** Air over the A-Z row's letters, drawn as its top padding; 0 unless it is the top row. */
    public final int azRowCrownPaddingPx;
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
    /** The rail's own band: its width without the display cutout the edge stack already carries. */
    public final int railBandPx;
    public final int railIconSizePx;
    public final int railIconSpacingPx;
    /** How much of the rail's axis one icon takes, itself and its air either side. */
    public final int railSlotLengthPx;
    /**
     * One pinned icon in the form that lies down, for the row to draw rather than work out: the
     * size preset scales this and nothing else, and a shared row's band is exactly it plus
     * {@link com.termux.app.dock.DockLayoutPolicy#SHARED_ROW_AIR_DP} on each side.
     */
    public final int appsRowIconPx;
    /**
     * The band a lying-down pinned-apps row claims wherever it lies. Equal to
     * {@link #appsBarHeightPx} while the row is the dock's own; non-zero for a row along the top,
     * where the dock has collapsed and this is the only height there is.
     */
    public final int appsRowBandPx;
    /**
     * That band's usable (icon) height: {@link #appsRowBandPx} minus the row's own vertical
     * padding. It is what the row sizes its icons against wherever it lies down, so a row standing
     * off the dock — where {@link #appsBarHeightHintPx} is zero because the dock's row collapsed —
     * still has a ceiling of its own instead of scaling to whatever host it was lent to.
     */
    public final int appsRowBandHintPx;
    /**
     * The band the row's page ticks stand in, which is part of the row's air on its centre-facing
     * side rather than a band beside it: a host carrying the strip gives it this and takes it off
     * the row's own padding on that side, so the two together are {@link #appsTopPaddingPx}. Zero
     * while the row shows no ticks, and for a rail, whose ticks stand in a column of their own.
     */
    public final int appsRowStripBandPx;

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
        this.azRowCrownPaddingPx = Math.max(0, b.azRowCrownPaddingPx);
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
        this.railBandPx = Math.max(0, b.railBandPx);
        this.railIconSizePx = Math.max(0, b.railIconSizePx);
        this.railIconSpacingPx = Math.max(0, b.railIconSpacingPx);
        this.railSlotLengthPx = Math.max(0, b.railSlotLengthPx);
        this.appsRowBandPx = Math.max(0, b.appsRowBandPx);
        this.appsRowBandHintPx = Math.max(0, b.appsRowBandHintPx);
        this.appsRowIconPx = Math.max(0, b.appsRowIconPx);
        this.appsRowStripBandPx = Math.max(0, b.appsRowStripBandPx);
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

    /**
     * The height of the row's own view inside its host: the band less the band the ticks stand in,
     * which the host gives the strip. A row showing no ticks is the whole band.
     */
    public int appsRowViewBandPx() {
        return Math.max(0, appsRowBandPx - appsRowStripBandPx);
    }

    /**
     * The row view's own padding on the side its ticks stand on: the air there, less the band the
     * strip already fills. Zero wherever the strip's band is the whole of that air, which is what
     * keeps the host from reserving anything extra for it.
     */
    public int appsRowPaddingBesideTicksPx() {
        return Math.max(0, appsTopPaddingPx - appsRowStripBandPx);
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
        int azRowCrownPaddingPx;
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
        int railBandPx;
        int railIconSizePx;
        int railIconSpacingPx;
        int railSlotLengthPx;
        int appsRowBandPx;
        int appsRowBandHintPx;
        int appsRowIconPx;
        int appsRowStripBandPx;
        int compactStatusBarHeightPx;
        int configuredCornerRadiusDp;

        DockLayout build() {
            return new DockLayout(this);
        }
    }
}
