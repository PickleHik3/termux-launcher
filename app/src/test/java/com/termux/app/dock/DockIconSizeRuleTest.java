package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.layout.IconSize;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The one icon size on the dock (ADR 0020 §3, spec §8a): every design × style × step with 4–8
 * pinned icons at 360 and 412 dp draws the step's icon exactly, the same on every design, with
 * the band grown around it; the side inset is never under the least; and a row is either laid
 * out with at least the least gap between icons (so nothing is clipped) or flagged as not
 * fitting, never overflowed silently.
 */
public class DockIconSizeRuleTest {

    private static final float DENSITY = 2.75f;
    /** The Home grid's side margin these rows follow, dp. */
    private static final float GRID_EDGE_DP = 16f;
    private static final int GRID_COLUMNS = 4;

    @Test
    public void everyDesignStyleAndStepDrawsTheStepWithinTheInsets() {
        List<String> overflow = new ArrayList<>();
        for (IconSize step : IconSize.values()) {
            int iconPx = Math.round(step.dp * DENSITY);
            for (int widthDp : new int[] {360, 412}) {
                for (int count = 4; count <= 8; count++) {
                    for (boolean capsule : new boolean[] {false, true}) {
                        for (DockStyle design : DockStyle.CHOICES) {
                            DockLayout l = layout(design, capsule, step, widthDp, count);
                            String at = step + " " + widthDp + "dp " + count + " icons "
                                + (capsule ? "floating " : "docked ") + design;
                            assertEquals(at + ": the step's icon", iconPx, l.slotIconPx);
                            assertEquals(at, iconPx, l.appsRowIconPx);
                            assertTrue(at + ": the band holds the icon (" + l.appsRowBandHintPx
                                + ")", l.appsRowIconPx <= l.appsRowBandHintPx);
                            float leastDp = capsule ? GRID_EDGE_DP
                                : Math.max(GRID_EDGE_DP, DockLayoutPolicy.DOCKED_MIN_SIDE_INSET_DP);
                            int fromEdge = l.appsRowEdgeInsetPx + (capsule ? l.horizontalInsetPx : 0);
                            assertTrue(at + ": inset " + fromEdge + "px",
                                fromEdge >= Math.round(leastDp * DENSITY) - 1);
                            boolean tight = DockLayoutPolicy.overflows(iconPx,
                                l.appsRowUsableWidthPx, count, DENSITY);
                            assertEquals(at + ": overflow is reported", tight, l.iconsOverflow);
                            if (tight) {
                                overflow.add(at);
                            } else {
                                int slotPx = l.appsRowUsableWidthPx / count;
                                assertTrue(at + ": slot " + slotPx + " holds the icon and the gap",
                                    slotPx - iconPx >= Math.round(
                                        DockLayoutPolicy.MIN_ICON_GAP_DP * DENSITY) - 1);
                            }
                        }
                    }
                }
            }
        }
        // The rows that do not fit at their step, for the report (spec §8a: report, never overflow).
        System.out.println("DOCK-OVERFLOW " + overflow.size() + " " + overflow);
    }

    @Test
    public void aRowOfAsManyIconsAsTheGridHasColumnsStandsOnTheGridsMargin() {
        for (DockStyle design : DockStyle.CHOICES) {
            DockLayout l = layout(design, false, IconSize.M, 412, GRID_COLUMNS);
            assertEquals(design + ": the grid's margin", Math.round(GRID_EDGE_DP * DENSITY),
                l.appsRowEdgeInsetPx);
        }
    }

    private static DockLayout layout(DockStyle design, boolean capsule, IconSize step,
                                     int widthDp, int count) {
        float least = capsule ? GRID_EDGE_DP
            : Math.max(GRID_EDGE_DP, DockLayoutPolicy.DOCKED_MIN_SIDE_INSET_DP);
        return DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(capsule)
            .surface(DockSurfaceSpec.forStyle(design))
            .appsRowEnabledPref(true)
            .azRowEnabledPref(true)
            .appsRowAlone(false)
            .appsRowPageStripShown(true)
            .density(DENSITY)
            .barHeightScale(DockLayoutPolicy.sizePreset(2))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
            .rowWidthPx(Math.round(widthDp * DENSITY))
            .slotCount(count)
            .iconDp(step.dp)
            .minSideInsetDp(least)
            .alignToGridDp(count == GRID_COLUMNS ? least : 0f)
            .build());
    }
}
