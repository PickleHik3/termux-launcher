package com.termux.app.dock;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The dock's sizing table: every size preset, both styles, both orientations, with and without a
 * display cutout on the rail's edge. The expected numbers were taken from the pre-extraction
 * {@code TermuxActivity} resolvers, so a drift in any of them is a visible dock regression.
 *
 * <p>Fixed for the whole table: density 2.75 (a 440dpi phone), the 37.5dp extra-keys row as the
 * apps-row baseline unit, the shipped 10dp horizontal inset and the follow-the-style corner radius.
 * The non-parameterised cases below cover the axes that do not vary per row: the icon curves, the
 * preset rounding, the radius/inset rules and the no-preferences collapse.
 */
@RunWith(Parameterized.class)
public class DockLayoutPolicyTest {

    private static final float DENSITY = 2.75f;
    private static final int BASE_TOOLBAR_PX = 103; // round(37.5dp * 2.75)
    private static final int INSET_DP = 10;         // the shipped default
    private static final int CORNER_DP = -1;        // follow-the-style radius
    private static final int TOOLBAR_PX = 103;      // one extra-keys row, for combinedHeight

    /** {preset, capsule, landscape, cutoutPx} × expected numbers. */
    @Parameterized.Parameters(name = "{0} preset={1} capsule={2} landscape={3} cutout={4}")
    public static List<Object[]> cases() {
        // preset, capsule, landscape, appsBar, hint, azRow, band, inset, capsuleContentInset,
        // appsTop, appsBottom, combined, compactStatusBar, iconScale
        Object[][] rows = {
            {1.72f, false, false, 132, 107, 52, 8, 0, 67, 17, 8, 295, 88, 1.3068f},
            {1.72f, false, true, 0, 0, 0, 0, 0, 67, 17, 8, 103, 88, 1.3068f},
            {1.72f, true, false, 132, 107, 52, 8, 28, 67, 17, 8, 295, 83, 1.7252f},
            {1.72f, true, true, 0, 0, 0, 0, 28, 67, 17, 8, 103, 83, 1.7252f},
            {1.95f, false, false, 148, 123, 52, 8, 0, 67, 17, 8, 311, 88, 1.487604f},
            {1.95f, false, true, 0, 0, 0, 0, 0, 67, 17, 8, 103, 88, 1.487604f},
            {1.95f, true, false, 149, 120, 52, 8, 28, 67, 19, 10, 312, 83, 1.9633334f},
            {1.95f, true, true, 0, 0, 0, 0, 28, 67, 19, 10, 103, 83, 1.9633334f},
            {2.18f, false, false, 166, 141, 52, 8, 0, 67, 17, 8, 329, 88, 1.68f},
            {2.18f, false, true, 0, 0, 0, 0, 0, 67, 17, 8, 103, 88, 1.68f},
            {2.18f, true, false, 169, 135, 52, 8, 28, 67, 21, 13, 332, 83, 2.21312f},
            {2.18f, true, true, 0, 0, 0, 0, 28, 67, 21, 13, 103, 83, 2.21312f},
            {2.45f, false, false, 183, 158, 52, 8, 0, 67, 17, 8, 346, 88, 1.89072f},
            {2.45f, false, true, 0, 0, 0, 0, 0, 67, 17, 8, 103, 88, 1.89072f},
            {2.45f, true, false, 190, 151, 52, 8, 28, 67, 24, 15, 353, 83, 2.508f},
            {2.45f, true, true, 0, 0, 0, 0, 28, 67, 24, 15, 103, 83, 2.508f},
        };
        List<Object[]> cases = new ArrayList<>();
        for (int cutoutPx : new int[]{0, 44}) {
            for (Object[] row : rows) {
                Object[] c = Arrays.copyOf(row, row.length + 1);
                c[row.length] = cutoutPx;
                cases.add(c);
            }
        }
        return cases;
    }

    @Parameterized.Parameter(0) public float preset;
    @Parameterized.Parameter(1) public boolean capsule;
    @Parameterized.Parameter(2) public boolean landscape;
    @Parameterized.Parameter(3) public int expectedAppsBarPx;
    @Parameterized.Parameter(4) public int expectedHintPx;
    @Parameterized.Parameter(5) public int expectedAzRowPx;
    @Parameterized.Parameter(6) public int expectedBandPx;
    @Parameterized.Parameter(7) public int expectedInsetPx;
    @Parameterized.Parameter(8) public int expectedCapsuleContentInsetPx;
    @Parameterized.Parameter(9) public int expectedAppsTopPx;
    @Parameterized.Parameter(10) public int expectedAppsBottomPx;
    @Parameterized.Parameter(11) public int expectedCombinedPx;
    @Parameterized.Parameter(12) public int expectedCompactStatusPx;
    @Parameterized.Parameter(13) public float expectedIconScale;
    @Parameterized.Parameter(14) public int cutoutPx;

    private DockLayout compute() {
        return DockLayoutPolicy.compute(inputs(preset, capsule, landscape)
            .displayCutoutInsetLeftPx(cutoutPx)
            .build());
    }

    private static DockLayoutPolicy.DockInputs.Builder inputs(float preset, boolean capsule,
                                                              boolean landscape) {
        return DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(capsule)
            .landscape(landscape)
            .density(DENSITY)
            .barHeightScale(preset)
            .dockHorizontalInsetDp(INSET_DP)
            .configuredCornerRadiusDp(CORNER_DP)
            .appsRowEnabledPref(true)
            .azRowEnabledPref(true)
            .baseToolbarHeightPx(BASE_TOOLBAR_PX)
            .additionalAppsBarHeightPx(0)
            .railOnRight(false);
    }

    @Test
    public void rowMetrics_matchThePreExtractionResolvers() {
        DockLayout l = compute();
        assertEquals("appsBarHeightPx", expectedAppsBarPx, l.appsBarHeightPx);
        assertEquals("appsBarHeightHintPx", expectedHintPx, l.appsBarHeightHintPx);
        assertEquals("azRowHeightPx", expectedAzRowPx, l.azRowHeightPx);
        assertEquals("indicatorBandHeightPx", expectedBandPx, l.indicatorBandHeightPx);
        // The inter-row gap has always been the indicator band itself.
        assertEquals("interRowGapPx", expectedBandPx, l.interRowGapPx);
        assertEquals("combinedHeight", expectedCombinedPx, l.combinedHeight(TOOLBAR_PX, true));
        // Landscape collapses the horizontal rows: the rail is the launcher surface there.
        assertEquals(!landscape, l.appsRowEnabled);
        assertEquals(!landscape, l.azRowEnabled);
    }

    @Test
    public void paddingsAndInsets_matchThePreExtractionResolvers() {
        DockLayout l = compute();
        assertEquals("horizontalInsetPx", expectedInsetPx, l.horizontalInsetPx);
        assertEquals("capsuleContentInsetPx", expectedCapsuleContentInsetPx, l.capsuleContentInsetPx);
        assertEquals("capsuleExtraKeysInsetPx", expectedCapsuleContentInsetPx + 6,
            l.capsuleExtraKeysInsetPx);
        assertEquals("appsTopPaddingPx", expectedAppsTopPx, l.appsTopPaddingPx);
        assertEquals("appsBottomPaddingPx", expectedAppsBottomPx, l.appsBottomPaddingPx);
        assertEquals("capsuleBottomGapPx", 17, l.capsuleBottomGapPx);
        assertEquals("compactStatusBarHeightPx", expectedCompactStatusPx, l.compactStatusBarHeightPx);
        // The capsule margin is the inset as if the capsule style were selected, either way.
        assertEquals("capsuleHorizontalMarginPx", 28, l.capsuleHorizontalMarginPx);
    }

    @Test
    public void iconScaleAndProgress_followTheStyleCurve() {
        DockLayout l = compute();
        assertEquals("iconScale", expectedIconScale, l.iconScale, 0.000001f);
        assertEquals("sizeProgress", DockLayoutPolicy.sizeProgress(preset), l.sizeProgress, 0f);
        assertEquals("defaultDockSizeProgress", DockLayoutPolicy.defaultDockSizeProgress(preset),
            l.defaultDockSizeProgress, 0f);
    }

    @Test
    public void rail_ownsOneEdgeInLandscapeOnly() {
        DockLayout l = compute();
        assertEquals(landscape, l.railActive);
        // 38dp icon + 2 × 10dp margin beats the 52dp floor, so the column is 58dp plus the cutout.
        assertEquals("railWidthPx", cutoutPx + 160, l.railWidthPx);
        assertEquals("railEdgeInsetPx", cutoutPx, l.railEdgeInsetPx);
        assertEquals(landscape ? AppDrawerGestureArbiter.Pull.RIGHT
            : AppDrawerGestureArbiter.Pull.NONE, l.railPull);
    }

    @Test
    public void capsuleRadius_isTheStyleRadiusCappedAtAHalfCapsule() {
        DockLayout l = compute();
        // 20dp follow-the-style radius at density 2.75.
        assertEquals(55f, l.capsuleCornerRadiusPx(Integer.MAX_VALUE), 0f);
        assertEquals(55f, l.capsuleCornerRadiusPx(200), 0f);
        // Shorter than twice the radius: a true half-capsule instead.
        assertEquals(40f, l.capsuleCornerRadiusPx(80), 0f);
        assertEquals(0f, l.capsuleCornerRadiusPx(0), 0f);
    }

    // --- Axes that do not vary per row ---

    @Test
    public void railOnRight_pullsTheOtherWayAndTakesTheRightCutout() {
        DockLayout l = DockLayoutPolicy.compute(inputs(2.18f, true, true)
            .railOnRight(true)
            .displayCutoutInsetLeftPx(44)
            .displayCutoutInsetRightPx(61)
            .build());
        assertTrue(l.railActive);
        assertEquals(AppDrawerGestureArbiter.Pull.LEFT, l.railPull);
        assertEquals(61, l.railEdgeInsetPx);
        assertEquals(61 + 160, l.railWidthPx);
    }

    @Test
    public void rail_isInactiveWhenTheAppsRowIsOff() {
        DockLayout l = DockLayoutPolicy.compute(inputs(2.18f, true, true)
            .appsRowEnabledPref(false)
            .build());
        assertFalse(l.railActive);
        assertEquals(AppDrawerGestureArbiter.Pull.NONE, l.railPull);
        // The column itself is still measurable; only the pull and the rail's activity gate on it.
        assertEquals(160, l.railWidthPx);
    }

    @Test
    public void rowSwitches_collapseTheirOwnRowsAndTheBandBetweenThem() {
        DockLayout noAz = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .azRowEnabledPref(false).build());
        assertEquals(0, noAz.azRowHeightPx);
        assertEquals(0, noAz.indicatorBandHeightPx);
        assertEquals(169, noAz.appsBarHeightPx);

        DockLayout noApps = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .appsRowEnabledPref(false).build());
        assertEquals(0, noApps.appsBarHeightPx);
        assertEquals(52, noApps.azRowHeightPx);
        assertEquals(0, noApps.indicatorBandHeightPx);
    }

    @Test
    public void noPreferences_collapsesTheRowsButStillMeasuresTheShell() {
        DockLayout l = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .preferencesAvailable(false).build());
        assertEquals(0, l.appsBarHeightPx);
        assertEquals(0, l.azRowHeightPx);
        assertEquals(0, l.indicatorBandHeightPx);
        assertEquals(0, l.appsBarHeightHintPx);
        assertEquals(DockLayoutPolicy.FALLBACK_ICON_SCALE, l.iconScale, 0f);
        assertEquals(28, l.horizontalInsetPx);
        assertEquals(17, l.capsuleBottomGapPx);
        // Padding sits at the full-progress value with no preset to read, exactly as before.
        assertEquals(39, l.capsuleAppsTotalPaddingPx);
    }

    @Test
    public void additionalAppsBarHeight_growsTheRowAndNegativeValuesAreIgnored() {
        int base = DockLayoutPolicy.compute(inputs(2.18f, true, false).build()).appsBarHeightPx;
        assertEquals(base + 30, DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .additionalAppsBarHeightPx(30).build()).appsBarHeightPx);
        assertEquals(base, DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .additionalAppsBarHeightPx(-30).build()).appsBarHeightPx);
    }

    @Test
    public void horizontalInset_isInertWhileDockedAndVerbatimWhileFloating() {
        // Docked is flush with the screen edges, so the side gap does nothing at any configured
        // value - it no longer spends the 10dp default and then starts moving past that.
        assertEquals(0, DockLayoutPolicy.surfaceHorizontalInsetPx(10, false, DENSITY));
        assertEquals(0, DockLayoutPolicy.surfaceHorizontalInsetPx(0, false, DENSITY));
        assertEquals(0, DockLayoutPolicy.surfaceHorizontalInsetPx(24, false, DENSITY));
        assertEquals(0, DockLayoutPolicy.surfaceHorizontalInsetPx(96, false, DENSITY));
        // A capsule keeps its configured inset as-is, clamped to the 48dp ceiling.
        assertEquals(28, DockLayoutPolicy.surfaceHorizontalInsetPx(10, true, DENSITY));
        assertEquals(66, DockLayoutPolicy.surfaceHorizontalInsetPx(24, true, DENSITY));
        assertEquals(132, DockLayoutPolicy.surfaceHorizontalInsetPx(96, true, DENSITY));
    }

    @Test
    public void configuredCornerRadius_winsOverTheStyleRadius() {
        DockLayout l = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .configuredCornerRadiusDp(8).build());
        assertEquals(22f, l.capsuleCornerRadiusPx(Integer.MAX_VALUE), 0f);
        assertEquals(20f, l.capsuleCornerRadiusPx(40), 0f);
        DockLayout square = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .configuredCornerRadiusDp(0).build());
        assertEquals(0f, square.capsuleCornerRadiusPx(Integer.MAX_VALUE), 0f);
    }

    @Test
    public void nearestSizePreset_roundsToTheClosestPresetAndTiesTakeTheLower() {
        assertEquals(0, DockLayoutPolicy.nearestSizePresetIndex(1.0f));
        assertEquals(0, DockLayoutPolicy.nearestSizePresetIndex(1.72f));
        assertEquals(0, DockLayoutPolicy.nearestSizePresetIndex(1.835f)); // exact tie 1.72/1.95
        assertEquals(1, DockLayoutPolicy.nearestSizePresetIndex(1.84f));
        assertEquals(1, DockLayoutPolicy.nearestSizePresetIndex(1.95f));
        assertEquals(2, DockLayoutPolicy.nearestSizePresetIndex(2.18f));
        assertEquals(3, DockLayoutPolicy.nearestSizePresetIndex(2.45f));
        assertEquals(3, DockLayoutPolicy.nearestSizePresetIndex(99f));
        assertEquals(4, DockLayoutPolicy.sizePresetCount());
        assertEquals(1.72f, DockLayoutPolicy.minSizePreset(), 0f);
        assertEquals(2.45f, DockLayoutPolicy.maxSizePreset(), 0f);
        // Out-of-range slider indices clamp to the table's ends.
        assertEquals(1.72f, DockLayoutPolicy.sizePreset(-3), 0f);
        assertEquals(2.45f, DockLayoutPolicy.sizePreset(9), 0f);
    }

    @Test
    public void sizeProgress_normalizesThePresetWindowAndShiftsForTheDefaultDock() {
        assertEquals(0f, DockLayoutPolicy.sizeProgress(1.45f), 0f);
        assertEquals(0f, DockLayoutPolicy.sizeProgress(0.5f), 0f);
        assertEquals(0.27f, DockLayoutPolicy.sizeProgress(1.72f), 0.000001f);
        assertEquals(1f, DockLayoutPolicy.sizeProgress(2.45f), 0f);
        assertEquals(1f, DockLayoutPolicy.sizeProgress(9f), 0f);
        assertEquals(0.27f, DockLayoutPolicy.defaultDockSizeProgress(1.45f), 0.000001f);
        assertEquals(0.54f, DockLayoutPolicy.defaultDockSizeProgress(1.72f), 0.000001f);
        // The shifted progress is capped one notch above the capsule's own ceiling.
        assertEquals(1.18f, DockLayoutPolicy.defaultDockSizeProgress(2.45f), 0.000001f);
        assertEquals(1.18f, DockLayoutPolicy.defaultDockSizeProgress(99f), 0.000001f);
    }

    @Test
    public void defaultDockCurve_preservesSmallestAndHitsRequestedPresetGrowth() {
        float[] progress = {0.54f, 0.77f, 1.00f, 1.18f};
        float[] previous = {1.3068f, 1.4034f, 1.50f, 1.5756f};
        float[] growth = {1f, 1.06f, 1.12f, 1.20f};
        for (int i = 0; i < progress.length; i++) {
            assertEquals(previous[i] * growth[i],
                DockLayoutPolicy.defaultDockIconScaleForProgress(progress[i]), 0.0001f);
            assertEquals(DockLayoutPolicy.defaultDockIconScaleForProgress(progress[i]),
                DockLayoutPolicy.iconScaleFor(false, progress[i]), 0f);
        }
    }

    @Test
    public void capsuleDockCurve_preservesSmallestAndSpreadsTenPercentGrowthProportionally() {
        float[] progress = {0.27f, 0.50f, 0.73f, 1.00f};
        float[] previous = {1.7252f, 1.90f, 2.0748f, 2.28f};
        float[] growth = {1f, 1f + (0.10f / 3f), 1f + (0.20f / 3f), 1.10f};
        for (int i = 0; i < progress.length; i++) {
            assertEquals(previous[i] * growth[i],
                DockLayoutPolicy.capsuleDockIconScaleForProgress(progress[i]), 0.0001f);
            assertEquals(DockLayoutPolicy.capsuleDockIconScaleForProgress(progress[i]),
                DockLayoutPolicy.iconScaleFor(true, progress[i]), 0f);
        }
    }

    @Test
    public void iconCurves_interpolateBetweenPresetsAndExtrapolatePastTheEnds() {
        // Midway between the first two capsule presets is midway between their scales.
        assertEquals((1.7252f + 1.9633334f) / 2f,
            DockLayoutPolicy.capsuleDockIconScaleForProgress((0.27f + 0.50f) / 2f), 0.0001f);
        // Below the first point and above the last, the end segments extrapolate.
        assertTrue(DockLayoutPolicy.capsuleDockIconScaleForProgress(0f) < 1.7252f);
        assertTrue(DockLayoutPolicy.capsuleDockIconScaleForProgress(1.5f) > 2.508f);
        assertEquals((1.3068f + 1.487604f) / 2f,
            DockLayoutPolicy.defaultDockIconScaleForProgress((0.54f + 0.77f) / 2f), 0.0001f);
        assertTrue(DockLayoutPolicy.defaultDockIconScaleForProgress(0.2f) < 1.3068f);
    }
}
