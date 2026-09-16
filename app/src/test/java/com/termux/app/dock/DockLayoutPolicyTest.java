package com.termux.app.dock;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;

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

    /** {preset, capsule, appsRowOnEdge, cutoutPx} × expected numbers. */
    @Parameterized.Parameters(name = "{0} preset={1} capsule={2} appsRowOnEdge={3} cutout={4}")
    public static List<Object[]> cases() {
        // preset, capsule, appsRowOnEdge, appsBar, hint, azRow, band, inset, capsuleContentInset,
        // appsTop, appsBottom, combined, compactStatusBar, iconScale, icon
        // With the apps row on an edge the letters are the dock's top row and wear a 6dp crown
        // (17px): 52 -> 69, and the stack grows with it.
        // The band under the apps row is the page ticks' own strip (9dp = 25px) rather than the
        // 3dp of air it used to be, because the dock's row carries the same indicator every other
        // edge does instead of the FX layer painting one over the glass.
        // Q7: a shared row's band is the icon and its air on each side, and `hint` — the box the
        // icon is centred in — is the icon itself. The `icon` column is the snapshot of what each
        // preset drew before that rule and must not move: the preset scales the icon, and only the
        // air around it ever changes.
        // Q8: that air is the band the page ticks stand in (9dp = 25px here) wherever the row
        // carries them, because the strip draws inside the air instead of stacking on it. The row
        // view is the band less the strip's share of it, so `appsBar` is `icon + 25` and the host
        // — the pair of them — is `icon + 50`, 9px shorter than the `icon + 34` plus a 25px band
        // it used to be. A rail carries no strip across its row axis, so its columns keep 17.
        Object[][] rows = {
            {1.72f, false, false, 100, 75, 52, 25, 0, 67, 25, 25, 280, 88, 1.3068f, 75},
            {1.72f, false, true, 0, 0, 69, 0, 0, 67, 17, 17, 172, 88, 1.3068f, 75},
            {1.72f, true, false, 108, 83, 52, 25, 28, 67, 25, 25, 288, 83, 1.7252f, 83},
            {1.72f, true, true, 0, 0, 69, 0, 28, 67, 17, 17, 172, 83, 1.7252f, 83},
            {1.95f, false, false, 116, 91, 52, 25, 0, 67, 25, 25, 296, 88, 1.487604f, 91},
            {1.95f, false, true, 0, 0, 69, 0, 0, 67, 17, 17, 172, 88, 1.487604f, 91},
            {1.95f, true, false, 121, 96, 52, 25, 28, 67, 25, 25, 301, 83, 1.9633334f, 96},
            {1.95f, true, true, 0, 0, 69, 0, 28, 67, 17, 17, 172, 83, 1.9633334f, 96},
            {2.18f, false, false, 135, 110, 52, 25, 0, 67, 25, 25, 315, 88, 1.68f, 110},
            {2.18f, false, true, 0, 0, 69, 0, 0, 67, 17, 17, 172, 88, 1.68f, 110},
            {2.18f, true, false, 133, 108, 52, 25, 28, 67, 25, 25, 313, 83, 2.21312f, 108},
            {2.18f, true, true, 0, 0, 69, 0, 28, 67, 17, 17, 172, 83, 2.21312f, 108},
            {2.45f, false, false, 153, 128, 52, 25, 0, 67, 25, 25, 333, 88, 1.89072f, 128},
            {2.45f, false, true, 0, 0, 69, 0, 0, 67, 17, 17, 172, 88, 1.89072f, 128},
            {2.45f, true, false, 147, 122, 52, 25, 28, 67, 25, 25, 327, 83, 2.508f, 122},
            {2.45f, true, true, 0, 0, 69, 0, 28, 67, 17, 17, 172, 83, 2.508f, 122},
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
    @Parameterized.Parameter(2) public boolean appsRowOnEdge;
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
    /** What each preset's icon has always been: the pin that keeps this change to the air alone. */
    @Parameterized.Parameter(14) public int expectedIconPx;
    @Parameterized.Parameter(15) public int cutoutPx;

    private DockLayout compute() {
        return DockLayoutPolicy.compute(inputs(preset, capsule, appsRowOnEdge)
            .displayCutoutInsetLeftPx(cutoutPx)
            .build());
    }

    private static DockLayoutPolicy.DockInputs.Builder inputs(float preset, boolean capsule,
                                                              boolean appsRowOnEdge) {
        return DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(capsule)
            // The parameter is the shipped meaning of "off the dock": a rail down one side. A row
            // standing along the top is off the dock too and is covered by its own test below.
            .appsRowOnEdge(appsRowOnEdge)
            .appsOnRail(appsRowOnEdge)
            // A rail's ticks stand in a column beside it, so they are no part of its row axis.
            // Every row that lies down carries them inside its own air.
            .appsRowPageStripShown(!appsRowOnEdge)
            .density(DENSITY)
            .barHeightScale(preset)
            .dockHorizontalInsetDp(INSET_DP)
            .configuredCornerRadiusDp(CORNER_DP)
            .appsRowEnabledPref(true)
            .azRowEnabledPref(true)
            // The shipped bottom order: the apps row over the letters, the extra keys under them.
            .rowOverAz(!appsRowOnEdge)
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
        // A rail collapses the pinned-apps row: it is the launcher surface instead. The letters
        // keep their slot — they are their own index, and show their matches on a floating strip.
        assertEquals(!appsRowOnEdge, l.appsRowEnabled);
        assertTrue(l.azRowEnabled);
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

    /**
     * The rule the whole table is an instance of: a row sharing its container is one icon and the
     * same air on each side of it, on every preset step and in both styles — the band the page
     * ticks stand in while it carries them, and the letters' own crown while it does not. The icon
     * is pinned to what it drew before the rule, so nothing but the air moved.
     */
    @Test
    public void aSharedRowIsTheIconAndTheSameAirOnEachSide() {
        boolean stripShown = !appsRowOnEdge;
        DockLayout l = compute();
        int airPx = DockLayoutPolicy.rowAirPx(false, stripShown, DENSITY);
        assertEquals(stripShown ? "9dp at density 2.75" : "6dp at density 2.75",
            stripShown ? 25 : 17, airPx);
        assertEquals("the plain air is the letters' crown, one constant for both",
            AccessoryStackLayoutPolicy.AZ_ROW_CROWN_DP, DockLayoutPolicy.SHARED_ROW_AIR_DP, 0f);
        assertEquals("appsRowIconPx", expectedIconPx, l.appsRowIconPx);
        assertEquals(airPx, l.appsTopPaddingPx);
        assertEquals("the air is the same on both sides, so the icon is in the middle of it",
            l.appsTopPaddingPx, l.appsBottomPaddingPx);
        assertEquals("band = icon + 2 x air", expectedIconPx + (2 * airPx), l.appsRowBandPx);
        // The box the icon is centred in is the icon: the air is the padding around it.
        assertEquals(expectedIconPx, l.appsRowBandHintPx);
        // And the ticks stand inside that air rather than beside it: the strip's band and the
        // row's own view are the band between them, and the row keeps nothing on that side.
        assertEquals(stripShown ? 25 : 0, l.appsRowStripBandPx);
        assertEquals(l.appsRowBandPx, l.appsRowViewBandPx() + l.appsRowStripBandPx);
        assertEquals(stripShown ? 0 : airPx, l.appsRowPaddingBesideTicksPx());
    }

    /**
     * The same rule with the ticks switched off, which is the shape the row had before they were
     * its air: 6dp of the letters' crown on each side, and no band to share it with.
     */
    @Test
    public void aRowWithNoTicksKeepsThePlainSixDpOfAir() {
        DockLayout l = DockLayoutPolicy.compute(inputs(preset, capsule, appsRowOnEdge)
            .appsRowPageStripShown(false)
            .displayCutoutInsetLeftPx(cutoutPx)
            .build());
        int airPx = DockLayoutPolicy.sharedRowAirPx(DENSITY);
        assertEquals(17, airPx);
        assertEquals("the icon is the preset's, whatever the air does",
            expectedIconPx, l.appsRowIconPx);
        assertEquals(airPx, l.appsTopPaddingPx);
        assertEquals(airPx, l.appsBottomPaddingPx);
        assertEquals(expectedIconPx + (2 * airPx), l.appsRowBandPx);
        assertEquals(0, l.appsRowStripBandPx);
        assertEquals(0, l.indicatorBandHeightPx);
        assertEquals(l.appsRowBandPx, l.appsRowViewBandPx());
    }

    /**
     * The proof that the icon did not move: the bands this file pinned before the rule changed —
     * the extra-keys row scaled, less the legacy paddings — put through the same fill ratio the
     * row has always sized its icons by. Those are the numbers in the {@code icon} column.
     */
    @Test
    public void theIconIsExactlyWhatTheLegacyBandLeftForIt() {
        int[] legacyDefaultHints = {107, 123, 141, 158};
        int[] legacyCapsuleHints = {107, 120, 135, 151};
        int[] defaultIcons = {75, 91, 110, 128};
        int[] capsuleIcons = {83, 96, 108, 122};
        for (int i = 0; i < DockLayoutPolicy.sizePresetCount(); i++) {
            float scale = DockLayoutPolicy.sizePreset(i);
            assertEquals("default preset " + i, defaultIcons[i],
                DockLayoutPolicy.dockIconSizePx(legacyDefaultHints[i],
                    DockLayoutPolicy.defaultDockIconScaleForProgress(
                        DockLayoutPolicy.defaultDockSizeProgress(scale)), DENSITY));
            assertEquals("capsule preset " + i, capsuleIcons[i],
                DockLayoutPolicy.dockIconSizePx(legacyCapsuleHints[i],
                    DockLayoutPolicy.capsuleDockIconScaleForProgress(
                        DockLayoutPolicy.sizeProgress(scale)), DENSITY));
            assertEquals(defaultIcons[i], DockLayoutPolicy.compute(
                inputs(scale, false, false).build()).appsRowIconPx);
            assertEquals(capsuleIcons[i], DockLayoutPolicy.compute(
                inputs(scale, true, false).build()).appsRowIconPx);
        }
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
    public void rail_ownsOneEdgeOnlyWhenTheAppsRowStandsOnOne() {
        DockLayout l = compute();
        assertEquals(appsRowOnEdge, l.railActive);
        // Updated for P8: the column is the icon and the lone row's own 4dp of air either side,
        // mirrored onto this axis — 46dp, which the 52dp thumb floor wins, plus the cutout.
        assertEquals("railWidthPx", cutoutPx + 143, l.railWidthPx);
        assertEquals("railEdgeInsetPx", cutoutPx, l.railEdgeInsetPx);
        assertEquals(appsRowOnEdge ? AppDrawerGestureArbiter.Pull.RIGHT
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
        assertEquals(61 + 143, l.railWidthPx);
    }

    @Test
    public void appsRowOnTheTopEdge_collapsesTheDockRowButKeepsItsBand() {
        DockLayout top = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .appsRowOnEdge(true)
            .appsOnRail(false)
            .build());
        // Nothing on the dock and no rail either: the row stands along the top instead.
        assertFalse(top.appsRowEnabled);
        assertFalse(top.railActive);
        assertEquals(0, top.appsBarHeightPx);
        // The band it claims up there is the height it had at the bottom, ticks and all.
        assertEquals(158, top.appsRowBandPx);
        assertEquals("and it carries them up there too", 25, top.appsRowStripBandPx);
        // And the row still knows how tall its icons may be. The dock's own hint is zero up here —
        // the dock has no row — and a row with no hint scales its icons to whatever host it was
        // lent to, which off the dock is a plank rather than a row.
        assertEquals(0, top.appsBarHeightHintPx);
        assertEquals(158 - top.appsTopPaddingPx - top.appsBottomPaddingPx, top.appsRowBandHintPx);
        assertEquals(top.appsRowIconPx, top.appsRowBandHintPx);
        assertTrue(top.appsRowBandHintPx > 0);
    }

    @Test
    public void aRowStandingAloneKeepsASliverOfAirAndTheSameIcon() {
        // The complaint: a row on a plank of its own was spaced like a row on a dock with two
        // others under it — the dock's paddings inside the sheet, the plank's margin outside it.
        DockLayout shared = DockLayoutPolicy.compute(inputs(2.18f, true, false).build());
        DockLayout alone = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .appsRowAlone(true).build());
        // P8's sliver, which is what a lone row that carries no ticks still keeps.
        DockLayout aloneNoTicks = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .appsRowAlone(true).appsRowPageStripShown(false).build());
        int sliverPx = DockLayoutPolicy.loneRowAirPx(DENSITY);
        assertEquals("4dp at density 2.75", 11, sliverPx);
        assertEquals(sliverPx, aloneNoTicks.appsTopPaddingPx);
        assertEquals(sliverPx, aloneNoTicks.appsBottomPaddingPx);
        assertEquals(135 + (2 * sliverPx), aloneNoTicks.appsRowBandPx);
        // Updated for Q8 with a reason: a lone row showing its ticks is symmetric about its icons
        // like any other, so the band the ticks stand in is its air on both sides.
        assertEquals(25, alone.appsTopPaddingPx);
        assertEquals(25, alone.appsBottomPaddingPx);
        // The icon is the same size either way: only the air around it changes.
        assertEquals(shared.appsRowIconPx, alone.appsRowIconPx);
        // P8's baseline, untouched: a lone row is still formed around the preset's own baseline
        // rather than around the icon, so the box its icons stand in is the one it drew.
        assertEquals(135, alone.appsRowBandHintPx);
        assertEquals(135 + 50, alone.appsRowBandPx);
        // Updated for Q7 with a reason: the shared row is the tighter of the two now. Its band is
        // the icon and its air, where the lone row's is a baseline that still carries the preset's
        // leftover around the same icon.
        assertTrue("and the shared row is the tighter for it",
            shared.appsRowBandPx < alone.appsRowBandPx);
        assertEquals(25, shared.appsTopPaddingPx);
        assertEquals(25, shared.appsBottomPaddingPx);
        assertEquals(shared.appsRowIconPx + 50, shared.appsRowBandPx);
    }

    @Test
    public void aCollapsedRowStaysCollapsedWhenItStandsAlone() {
        DockLayout l = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .appsRowEnabledPref(false).appsRowAlone(true).build());
        assertEquals(0, l.appsRowBandPx);
        assertEquals(0, l.appsBarHeightPx);
    }

    @Test
    public void onTheDockTheBandHintIsTheRowHint() {
        DockLayout onDock = DockLayoutPolicy.compute(inputs(2.18f, true, false).build());
        assertTrue(onDock.appsRowEnabled);
        assertEquals(onDock.appsBarHeightHintPx, onDock.appsRowBandHintPx);
    }

    @Test
    public void aRowIsNeverDeeperThanTwoRailSlots() {
        // The ceiling a row falls back on before anything has told it its band, so a host that has
        // swallowed a content column cannot make one pinned icon that tall. Every preset's real
        // band stays well inside it.
        DockLayout l = compute();
        assertEquals(2 * l.railSlotLengthPx, DockLayoutPolicy.maxRowBandPx(DENSITY));
        assertTrue(l.appsRowBandPx < DockLayoutPolicy.maxRowBandPx(DENSITY));
    }

    @Test
    public void railAxis_isOneFixedIconAndItsAirPerSlot() {
        DockLayout l = compute();
        // 38dp icon and 10dp of air either side, at density 2.75.
        assertEquals(105, l.railIconSizePx);
        assertEquals(28, l.railIconSpacingPx);
        assertEquals(105 + 2 * 28, l.railSlotLengthPx);
        assertEquals(l.railWidthPx - l.railEdgeInsetPx, l.railBandPx);
        assertEquals(0, DockLayoutPolicy.railSlotOffsetPx(0, DENSITY));
        assertEquals(l.railSlotLengthPx, DockLayoutPolicy.railSlotOffsetPx(1, DENSITY));
        assertEquals(3 * l.railSlotLengthPx, DockLayoutPolicy.railSlotOffsetPx(3, DENSITY));
    }

    @Test
    public void rail_isInactiveWhenTheAppsRowIsOff() {
        DockLayout l = DockLayoutPolicy.compute(inputs(2.18f, true, true)
            .appsRowEnabledPref(false)
            .build());
        assertFalse(l.railActive);
        assertEquals(AppDrawerGestureArbiter.Pull.NONE, l.railPull);
        // The column itself is still measurable; only the pull and the rail's activity gate on it.
        assertEquals(143, l.railWidthPx);
    }

    @Test
    public void rowSwitches_collapseTheirOwnRowsAndTheBandTheTicksStandIn() {
        DockLayout noAz = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .azRowEnabledPref(false).build());
        assertEquals(0, noAz.azRowHeightPx);
        // The band belongs to the apps row, not to the gap between two rows: it is where the page
        // ticks stand, and the row still has pages with the letters switched off.
        assertEquals(25, noAz.indicatorBandHeightPx);
        // The row view is the band less the band the ticks stand in, which is the other 25.
        assertEquals(133, noAz.appsBarHeightPx);
        assertEquals(158, noAz.appsRowBandPx);

        DockLayout noApps = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .appsRowEnabledPref(false).rowOverAz(false).build());
        assertEquals(0, noApps.appsBarHeightPx);
        // No apps row above: the letters wear the 6dp crown the band would otherwise have given.
        assertEquals(17, noApps.azRowCrownPaddingPx);
        assertEquals(52 + 17, noApps.azRowHeightPx);
        assertEquals(0, noApps.indicatorBandHeightPx);
        assertEquals(0, noAz.azRowCrownPaddingPx);
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
    public void additionalAppsBarHeight_growsTheIconAndNegativeValuesAreIgnored() {
        DockLayout base = DockLayoutPolicy.compute(inputs(2.18f, true, false).build());
        DockLayout grown = DockLayoutPolicy.compute(inputs(2.18f, true, false)
            .additionalAppsBarHeightPx(30).build());
        // Updated for Q7 with a reason: the drag used to add its pixels to the band, which grew
        // the icon and the air together. The air is a constant now, so the drag lands where the
        // size preset does — on the icon — and the band follows it.
        assertTrue(grown.appsRowIconPx > base.appsRowIconPx);
        assertEquals(grown.appsRowIconPx + 25, grown.appsBarHeightPx);
        assertEquals(grown.appsRowIconPx + 50, grown.appsRowBandPx);
        assertTrue(grown.appsBarHeightPx > base.appsBarHeightPx);
        assertEquals(base.appsBarHeightPx, DockLayoutPolicy.compute(inputs(2.18f, true, false)
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
