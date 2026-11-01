package com.termux.app.dock;

import androidx.annotation.NonNull;

import com.termux.app.place.layout.IconSize;

import java.util.Collection;

/**
 * The cap the pinned-app count puts on the one icon size (ADR 0020 §3, developer 2026-11-01).
 * Icons are one size everywhere, so a step is offered only where the dock's pinned apps fit at
 * it, with the least gap between them ({@link DockLayoutPolicy#MIN_ICON_GAP_DP}), on the dock
 * designs the user sees now (Home's and the Terminal's) in both styles: no dock has to shrink or
 * clip an icon, the step drawn is the same on every page, and turning the dock floating does not
 * change it. (Every design would be stabler but caps 7 pins at S on a 412 dp phone, since Lens
 * beads floating fits 7 only at S; the developer's 7 pins land on M.)
 *
 * <p>The stored choice is kept as the user made it; what draws is {@link #effective}: the largest
 * step no bigger than the choice that fits. Unpinning apps brings the choice back.</p>
 *
 * <p>The fit is measured at the screen's smallest width (a phone's portrait width), so it is the
 * same in both orientations, and at a reference density: the row's arithmetic is in dp, so the
 * density moves the result by rounding only.</p>
 */
public final class IconSizeCap {

    /** The density the fit is measured at. */
    static final float REFERENCE_DENSITY = 2.75f;

    private IconSizeCap() {}

    /**
     * Whether {@code count} pinned icons of {@code step} stand in a row {@code widthDp} wide with
     * the least gap, on each of {@code designs} in both styles (Docked and Floating, so turning
     * the dock floating never changes the step). Zero or fewer pins always fit.
     *
     * @param gridEdgeDp the Home grid's side margin, dp (the dock's least side inset)
     * @param gridColumns the Home grid's columns (a row of that many stands on the grid's margin)
     */
    public static boolean fits(@NonNull Collection<DockStyle> designs, @NonNull IconSize step,
                               int count, int widthDp, float gridEdgeDp, int gridColumns) {
        if (count <= 0 || widthDp <= 0) return true;
        for (DockStyle design : designs) {
            for (boolean capsule : new boolean[] {false, true}) {
                if (!fits(design, capsule, step, count, widthDp, gridEdgeDp, gridColumns)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether the row fits at {@code step} on one design and style. */
    static boolean fits(@NonNull DockStyle design, boolean capsule, @NonNull IconSize step,
                        int count, int widthDp, float gridEdgeDp, int gridColumns) {
        float least = capsule ? gridEdgeDp
            : Math.max(gridEdgeDp, DockLayoutPolicy.DOCKED_MIN_SIDE_INSET_DP);
        DockLayout l = DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(capsule)
            .surface(DockSurfaceSpec.forStyle(design))
            .appsRowEnabledPref(true)
            .azRowEnabledPref(true)
            .appsRowAlone(false)
            .appsRowPageStripShown(true)
            .density(REFERENCE_DENSITY)
            .barHeightScale(DockLayoutPolicy.sizePreset(2))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(REFERENCE_DENSITY))
            .rowWidthPx(Math.round(widthDp * REFERENCE_DENSITY))
            .slotCount(count)
            .iconDp(step.dp)
            .minSideInsetDp(least)
            .alignToGridDp(gridColumns > 0 && count == gridColumns ? least : 0f)
            .build());
        return !l.iconsOverflow;
    }

    /** The largest step at which {@code count} pins fit; {@link IconSize#S} when none does. */
    @NonNull
    public static IconSize largestFitting(@NonNull Collection<DockStyle> designs, int count,
                                          int widthDp, float gridEdgeDp, int gridColumns) {
        IconSize[] steps = IconSize.values();
        for (int i = steps.length - 1; i > 0; i--) {
            if (fits(designs, steps[i], count, widthDp, gridEdgeDp, gridColumns)) return steps[i];
        }
        return steps[0];
    }

    /** The step that draws: the largest no bigger than {@code chosen} that fits. */
    @NonNull
    public static IconSize effective(@NonNull IconSize chosen, @NonNull IconSize cap) {
        return chosen.dp <= cap.dp ? chosen : cap;
    }

    /** The cap the launcher last measured, and the pinned count it measured it for. */
    private static volatile IconSize sCap = IconSize.XL;
    private static volatile int sCount;

    /** The launcher publishes the cap it measured, for the editor's and Settings' rows. */
    public static void publish(@NonNull IconSize cap, int count) {
        sCap = cap;
        sCount = count;
    }

    /** The cap last published; {@link IconSize#XL} (no cap) before the launcher measured one. */
    @NonNull
    public static IconSize published() {
        return sCap;
    }

    /** The pinned count the published cap was measured for. */
    public static int publishedCount() {
        return sCount;
    }
}
