package com.termux.app.dock;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * Pure dock sizing. Everything the dock's geometry depends on arrives as a plain value in
 * {@link DockInputs} — no views, no {@code Context}, no preference reads — and one
 * {@link DockLayout} value comes back out.
 *
 * <p>The activity snapshots the inputs at the points it used to call its ~20 {@code resolveDock*}
 * helpers and then reads fields off the layout, so the numbers below are the single source of truth
 * for row heights, paddings, insets, the landscape rail column, the capsule radius and the icon
 * scale curves.
 */
public final class DockLayoutPolicy {

    private DockLayoutPolicy() {}

    /** Where the dock size sliders land; index 2 is the shipped default. */
    private static final float[] SIZE_PRESETS = {1.72f, 1.95f, 2.18f, 2.45f};

    /** The size-progress window the presets are normalized into. */
    private static final float SIZE_PROGRESS_MIN_SCALE = 1.45f;
    private static final float SIZE_PROGRESS_MAX_SCALE = 2.45f;

    /**
     * The default (square) dock runs the same slider one notch up the icon curve than the capsule,
     * because its rows carry less padding for the same visual weight.
     */
    private static final float DEFAULT_DOCK_SIZE_PRESET_SHIFT = 0.27f;
    private static final float DEFAULT_DOCK_SIZE_MAX_PROGRESS = 1.18f;
    private static final float[] DEFAULT_DOCK_ICON_PROGRESS_POINTS = {0.54f, 0.77f, 1.00f, 1.18f};
    private static final float[] DEFAULT_DOCK_ICON_SCALE_POINTS = {
        1.3068f, 1.487604f, 1.68f, 1.89072f
    };
    private static final float[] CAPSULE_DOCK_ICON_PROGRESS_POINTS = {0.27f, 0.50f, 0.73f, 1.00f};
    private static final float[] CAPSULE_DOCK_ICON_SCALE_POINTS = {
        1.7252f, 1.9633334f, 2.21312f, 2.508f
    };

    /** The icon scale used before any preference store exists to read a size preset from. */
    public static final float FALLBACK_ICON_SCALE = 1.36f;

    private static final float DOCK_RAIL_MIN_WIDTH_DP = 52f;
    /**
     * Breathing room between a rail icon and the display edge it is docked to, on top of whatever
     * cutout inset that edge already carries.
     */
    public static final float DOCK_RAIL_EDGE_MARGIN_DP = 10f;
    public static final float DOCK_RAIL_ICON_SIZE_DP = 38f;
    public static final float DOCK_RAIL_ICON_SPACING_DP = 10f;

    /** Everything the dock's numbers are a function of, snapshotted by the caller. */
    public static final class DockInputs {
        /** False before the preference store is attached; the rows collapse to zero then. */
        public final boolean preferencesAvailable;
        /** Rounded (capsule) dock style selected. */
        public final boolean capsule;
        public final boolean landscape;
        public final float density;
        /** The dock size preset, as stored (a raw scale, not a progress). */
        public final float barHeightScale;
        /** Configured dock horizontal inset in dp, before clamping. */
        public final int dockHorizontalInsetDp;
        /** Configured dock corner radius in dp, negative for the follow-the-style radius. */
        public final int configuredCornerRadiusDp;
        /** Row switches as stored, before the landscape gate. */
        public final boolean appsRowEnabledPref;
        public final boolean azRowEnabledPref;
        /**
         * Whether the extra-keys row is showing. Not a row this policy sizes — the toolbar owns its
         * own height — but it decides which row is on the dock's bottom rim, and so whether the A-Z
         * row carries a chin under its letters.
         */
        public final boolean extraKeysRowShown;
        /** The extra-keys toolbar's single-row height, the apps row's baseline unit. */
        public final int baseToolbarHeightPx;
        /** Extra apps-row height requested by the tuning drag; negative values are ignored. */
        public final int additionalAppsBarHeightPx;
        public final boolean railOnRight;
        public final int displayCutoutInsetLeftPx;
        public final int displayCutoutInsetRightPx;

        private DockInputs(Builder b) {
            this.preferencesAvailable = b.preferencesAvailable;
            this.capsule = b.capsule;
            this.landscape = b.landscape;
            this.density = b.density;
            this.barHeightScale = b.barHeightScale;
            this.dockHorizontalInsetDp = b.dockHorizontalInsetDp;
            this.configuredCornerRadiusDp = b.configuredCornerRadiusDp;
            this.appsRowEnabledPref = b.appsRowEnabledPref;
            this.azRowEnabledPref = b.azRowEnabledPref;
            this.extraKeysRowShown = b.extraKeysRowShown;
            this.baseToolbarHeightPx = b.baseToolbarHeightPx;
            this.additionalAppsBarHeightPx = b.additionalAppsBarHeightPx;
            this.railOnRight = b.railOnRight;
            this.displayCutoutInsetLeftPx = b.displayCutoutInsetLeftPx;
            this.displayCutoutInsetRightPx = b.displayCutoutInsetRightPx;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private boolean preferencesAvailable = true;
            private boolean capsule;
            private boolean landscape;
            private float density = 1f;
            private float barHeightScale = SIZE_PRESETS[2];
            private int dockHorizontalInsetDp =
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET;
            private int configuredCornerRadiusDp =
                TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS;
            private boolean appsRowEnabledPref;
            private boolean azRowEnabledPref;
            // Defaults to shown, which is the shape every caller sized against before the A-Z
            // row's chin existed: no chin unless a caller says the row is the bottom one.
            private boolean extraKeysRowShown = true;
            private int baseToolbarHeightPx;
            private int additionalAppsBarHeightPx;
            private boolean railOnRight;
            private int displayCutoutInsetLeftPx;
            private int displayCutoutInsetRightPx;

            public Builder preferencesAvailable(boolean v) { this.preferencesAvailable = v; return this; }
            public Builder capsule(boolean v) { this.capsule = v; return this; }
            public Builder landscape(boolean v) { this.landscape = v; return this; }
            public Builder density(float v) { this.density = v; return this; }
            public Builder barHeightScale(float v) { this.barHeightScale = v; return this; }
            public Builder dockHorizontalInsetDp(int v) { this.dockHorizontalInsetDp = v; return this; }
            public Builder configuredCornerRadiusDp(int v) { this.configuredCornerRadiusDp = v; return this; }
            public Builder appsRowEnabledPref(boolean v) { this.appsRowEnabledPref = v; return this; }
            public Builder azRowEnabledPref(boolean v) { this.azRowEnabledPref = v; return this; }
            public Builder extraKeysRowShown(boolean v) { this.extraKeysRowShown = v; return this; }
            public Builder baseToolbarHeightPx(int v) { this.baseToolbarHeightPx = v; return this; }
            public Builder additionalAppsBarHeightPx(int v) { this.additionalAppsBarHeightPx = v; return this; }
            public Builder railOnRight(boolean v) { this.railOnRight = v; return this; }
            public Builder displayCutoutInsetLeftPx(int v) { this.displayCutoutInsetLeftPx = v; return this; }
            public Builder displayCutoutInsetRightPx(int v) { this.displayCutoutInsetRightPx = v; return this; }

            public DockInputs build() {
                return new DockInputs(this);
            }
        }
    }

    /** Resolves one complete dock geometry from its inputs. */
    public static DockLayout compute(DockInputs in) {
        float density = Math.max(0f, in.density);
        boolean capsule = in.capsule;
        DockLayout.Builder out = new DockLayout.Builder();
        out.capsule = capsule;
        out.landscape = in.landscape;
        out.density = density;
        out.configuredCornerRadiusDp = in.configuredCornerRadiusDp;

        float sizeProgress = sizeProgress(in.barHeightScale);
        float defaultDockProgress = defaultDockSizeProgress(in.barHeightScale);
        out.sizeProgress = sizeProgress;
        out.defaultDockSizeProgress = defaultDockProgress;

        // Horizontal insets. The capsule margin is the inset as if the capsule style were selected,
        // because the capsule's inner content insets are measured from it either way.
        out.capsuleHorizontalMarginPx =
            surfaceHorizontalInsetPx(in.dockHorizontalInsetDp, true, density);
        out.horizontalInsetPx =
            surfaceHorizontalInsetPx(in.dockHorizontalInsetDp, capsule, density);
        // Inner padding between the capsule border and the row content. Trimmed slightly from the
        // 16dp redline so the rows (and the 2-row extra keys) sit a touch closer to the edges.
        out.capsuleContentInsetPx = out.capsuleHorizontalMarginPx + Math.round(density * 14f);
        out.capsuleExtraKeysInsetPx = out.capsuleContentInsetPx + Math.round(density * 2f);

        // Vertical paddings. Exactly preserves the previous top (6dp + 7dp*progress) plus a 1dp
        // bottom budget; without a preference store the padding sits at the full-progress value.
        float paddingProgress = in.preferencesAvailable ? sizeProgress : 1f;
        int capsuleTotalPadding = Math.round((6f + paddingProgress * 7f) * density)
            + Math.round(density);
        out.capsuleAppsTotalPaddingPx = capsuleTotalPadding;
        // Top space equals bottom padding plus the 3dp icon/A-Z indicator band. Together with the
        // paired bottom formula this preserves the old total inset while centering the icon row.
        int indicatorBand = Math.round(density * 3f);
        out.capsuleAppsTopPaddingPx = Math.min(capsuleTotalPadding,
            Math.max(0, (capsuleTotalPadding + indicatorBand + 1) / 2));
        out.capsuleAppsBottomPaddingPx =
            Math.max(0, capsuleTotalPadding - out.capsuleAppsTopPaddingPx);
        // 6dp above equals 3dp below plus the fixed 3dp icon/A-Z band.
        out.defaultAppsTopPaddingPx = Math.round(density * 6f);
        out.defaultAppsBottomPaddingPx = Math.round(density * 3f);
        out.appsTopPaddingPx = capsule ? out.capsuleAppsTopPaddingPx : out.defaultAppsTopPaddingPx;
        out.appsBottomPaddingPx =
            capsule ? out.capsuleAppsBottomPaddingPx : out.defaultAppsBottomPaddingPx;
        out.capsuleBottomGapPx = Math.round(density * 6f);

        // Row metrics. The horizontal rows collapse in landscape, where the rail is the launcher
        // surface instead, and collapse outright before a preference store exists.
        boolean appsRowEnabled =
            in.preferencesAvailable && in.appsRowEnabledPref && !in.landscape;
        boolean azRowEnabled = in.preferencesAvailable && in.azRowEnabledPref && !in.landscape;
        out.appsRowEnabled = appsRowEnabled;
        out.azRowEnabled = azRowEnabled;
        if (in.preferencesAvailable) {
            out.appsBarHeightPx = appsRowEnabled
                ? appsBarHeightPx(capsule, sizeProgress, defaultDockProgress,
                    in.baseToolbarHeightPx, out.appsTopPaddingPx, out.appsBottomPaddingPx,
                    density, Math.max(0, in.additionalAppsBarHeightPx))
                : 0;
            out.azRowHeightPx = AccessoryStackLayoutPolicy.computeAzRowHeightPx(
                azRowEnabled, in.extraKeysRowShown, density);
            out.azRowChinPaddingPx = AccessoryStackLayoutPolicy.computeAzRowChinPaddingPx(
                azRowEnabled, in.extraKeysRowShown, density);
            out.indicatorBandHeightPx = AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(
                appsRowEnabled && azRowEnabled, density);
            out.interRowGapPx = out.indicatorBandHeightPx;
        }
        out.appsBarHeightHintPx =
            Math.max(0, out.appsBarHeightPx - out.appsTopPaddingPx - out.appsBottomPaddingPx);

        out.iconScale = in.preferencesAvailable
            ? iconScaleFor(capsule, capsule ? sizeProgress : defaultDockProgress)
            : FALLBACK_ICON_SCALE;

        // Landscape rail.
        boolean railActive = in.landscape && in.preferencesAvailable && in.appsRowEnabledPref;
        out.railActive = railActive;
        out.railOnRight = in.railOnRight;
        out.railPull = railActive
            ? (in.railOnRight ? AppDrawerGestureArbiter.Pull.LEFT
                              : AppDrawerGestureArbiter.Pull.RIGHT)
            : AppDrawerGestureArbiter.Pull.NONE;
        out.railEdgeInsetPx =
            in.railOnRight ? in.displayCutoutInsetRightPx : in.displayCutoutInsetLeftPx;
        // The docked edge's cutout inset PLUS a column wide enough for an icon and its two margins
        // (not the larger of the two, which left the icons hard against the display edge).
        out.railWidthPx = out.railEdgeInsetPx
            + Math.max(Math.round(density * DOCK_RAIL_MIN_WIDTH_DP),
                Math.round(density * (DOCK_RAIL_ICON_SIZE_DP + 2 * DOCK_RAIL_EDGE_MARGIN_DP)));

        out.compactStatusBarHeightPx = Math.round(density * (capsule ? 30f : 32f));

        return out.build();
    }

    /**
     * Keeps the old row/icon result as each preset's baseline, then allocates enough extra row
     * height for the new icon curve. This makes the requested icon-size bump real in pixels while
     * preserving the smallest preset and the fixed A-Z/extra-keys heights.
     */
    private static int appsBarHeightPx(boolean capsule, float sizeProgress,
                                       float defaultDockProgress, int baseToolbarHeightPx,
                                       int appsTopPaddingPx, int appsBottomPaddingPx,
                                       float density, int additionalAppsBarHeightPx) {
        float baselineHeightFactor = capsule
            ? (1.12f + (sizeProgress * 0.60f))
            : (1.00f + (defaultDockProgress * 0.52f));
        int baselineRowHeightPx = Math.round(baseToolbarHeightPx * baselineHeightFactor);
        int verticalPaddingPx = appsTopPaddingPx + appsBottomPaddingPx;
        int twoDpPx = Math.round(density * 2f);
        int minUsablePx = Math.round(density * 24f);
        int baselineHintPx = Math.max(0, baselineRowHeightPx - verticalPaddingPx);
        int baselineUsablePx = Math.max(minUsablePx, baselineHintPx - twoDpPx);

        float baselineIconScale = capsule
            ? (1.52f + (sizeProgress * 0.76f))
            : (1.08f + (defaultDockProgress * 0.42f));
        float targetIconScale = iconScaleFor(capsule, capsule ? sizeProgress : defaultDockProgress);
        float requestedIconGrowth = targetIconScale / Math.max(0.0001f, baselineIconScale);
        if (Math.abs(requestedIconGrowth - 1f) < 0.0001f) {
            return Math.max(0, baselineRowHeightPx + additionalAppsBarHeightPx);
        }

        int baselineIconPx = Math.round(baselineUsablePx
            * AccessoryStackLayoutPolicy.computeDockIconFillRatio(baselineIconScale));
        int targetIconPx = Math.max(1, Math.round(baselineIconPx * requestedIconGrowth));
        float targetFill = AccessoryStackLayoutPolicy.computeDockIconFillRatio(targetIconScale);
        int targetUsablePx = Math.max(minUsablePx, Math.round(targetIconPx / targetFill));
        return Math.max(0, targetUsablePx + twoDpPx + verticalPaddingPx + additionalAppsBarHeightPx);
    }

    /**
     * A floating capsule keeps its configured inset as-is; the flush styles spend the shipped
     * default first, so their shape stays flush until the user pushes past that baseline.
     */
    public static int surfaceHorizontalInsetPx(int configuredDp, boolean capsule, float density) {
        // Docked surfaces are flush with the screen edges by definition - that is what separates
        // them from Floating - so the side gap simply does not apply there. It used to spend the
        // 10dp shipped default first and then start moving, which made a control that is supposed
        // to be inert in this style quietly do something past a threshold.
        if (!capsule)
            return 0;
        int insetDp = TermuxAppSharedPreferences.clampSurfaceHorizontalInset(configuredDp);
        return Math.round(Math.max(0f, density) * insetDp);
    }

    /**
     * The capsule radius: the configured dp when set, otherwise the follow-the-style radius shared
     * with the status surface and the terminal border — capped at a true half-capsule either way.
     */
    public static float capsuleCornerRadiusPx(int configuredCornerRadiusDp, int surfaceHeightPx,
                                              float density) {
        float safeDensity = Math.max(0f, density);
        if (configuredCornerRadiusDp >= 0) {
            return Math.min(safeDensity * configuredCornerRadiusDp, surfaceHeightPx / 2f);
        }
        return Math.min(safeDensity * TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(
            TermuxAppSharedPreferences.SurfaceSlot.DOCK, true),
            surfaceHeightPx / 2f);
    }

    /**
     * The inset that keeps the status bar's bottom row clear of its own rounded corners.
     *
     * <p>That row — sessions chip on the left, status widgets on the right — is bottom-gravity and
     * therefore sits in the surface's bottom corners. Whatever radius rounds those corners eats
     * into it, so the inset has to follow the radius rather than be a fixed number: half the radius
     * is the arc's worst-case encroachment over the row's height.</p>
     *
     * <p>The two styles start from different places because their radii do. Docked is square at
     * rest, so its 3dp baseline is the whole story and every bit of radius the user dials in is new
     * encroachment. Floating is a card already rounded at rest — {@code baselineRadiusPx}, the auto
     * radius — and its 8dp baseline was measured against exactly that, so only radius beyond the
     * default is encroachment the baseline does not already answer. A stock Floating surface
     * therefore keeps the inset it has always had, and a raised radius stops clipping the chips.</p>
     *
     * @param radiusPx          the radius actually rounding the row's corners
     * @param baselineRadiusPx  the radius the fixed baseline was measured against; 0 for Docked
     */
    public static int statusBarContentEdgeInsetPx(boolean capsule, float radiusPx,
                                                  float baselineRadiusPx, float density) {
        float safeDensity = density > 0f ? density : 1f;
        float basePx = safeDensity * (capsule ? 8f : 3f);
        float encroachmentPx = Math.max(0f, radiusPx - Math.max(0f, baselineRadiusPx)) * 0.5f;
        return Math.round(basePx + encroachmentPx);
    }

    /** The stored size preset normalized into 0..1. */
    public static float sizeProgress(float barHeightScale) {
        return Math.max(0f, Math.min(1f, (barHeightScale - SIZE_PROGRESS_MIN_SCALE)
            / (SIZE_PROGRESS_MAX_SCALE - SIZE_PROGRESS_MIN_SCALE)));
    }

    /** The default dock's shifted progress, which runs one preset notch above the capsule's. */
    public static float defaultDockSizeProgress(float barHeightScale) {
        float progress = sizeProgress(barHeightScale) + DEFAULT_DOCK_SIZE_PRESET_SHIFT;
        return Math.max(0f, Math.min(DEFAULT_DOCK_SIZE_MAX_PROGRESS, progress));
    }

    /**
     * The icon scale for a style's own progress: {@link #sizeProgress} for the capsule,
     * {@link #defaultDockSizeProgress} for the default dock.
     */
    public static float iconScaleFor(boolean capsule, float progress) {
        return capsule ? capsuleDockIconScaleForProgress(progress)
                       : defaultDockIconScaleForProgress(progress);
    }

    public static float defaultDockIconScaleForProgress(float defaultDockProgress) {
        return AccessoryStackLayoutPolicy.interpolatePresetCurve(defaultDockProgress,
            DEFAULT_DOCK_ICON_PROGRESS_POINTS, DEFAULT_DOCK_ICON_SCALE_POINTS);
    }

    public static float capsuleDockIconScaleForProgress(float normalizedProgress) {
        return AccessoryStackLayoutPolicy.interpolatePresetCurve(normalizedProgress,
            CAPSULE_DOCK_ICON_PROGRESS_POINTS, CAPSULE_DOCK_ICON_SCALE_POINTS);
    }

    public static int sizePresetCount() {
        return SIZE_PRESETS.length;
    }

    /** The stored scale for a preset slider index, clamped to the table. */
    public static float sizePreset(int index) {
        return SIZE_PRESETS[Math.max(0, Math.min(SIZE_PRESETS.length - 1, index))];
    }

    public static float minSizePreset() {
        return SIZE_PRESETS[0];
    }

    public static float maxSizePreset() {
        return SIZE_PRESETS[SIZE_PRESETS.length - 1];
    }

    /** The preset slider index whose scale is closest to the stored one; ties take the lower. */
    public static int nearestSizePresetIndex(float scale) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < SIZE_PRESETS.length; i++) {
            float distance = Math.abs(scale - SIZE_PRESETS[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }
}
