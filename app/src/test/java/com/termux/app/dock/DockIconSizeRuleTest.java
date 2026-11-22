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
                            // What the view stands the row in by, from the screen edge.
                            int fromEdge = l.appsRowSideInsetPx();
                            assertEquals(at + ": the view's inset is the policy's",
                                l.appsRowEdgeInsetPx, fromEdge);
                            // The first icon's edge, centred in its slot: never inside the least
                            // inset (a row on the grid's columns starts its slots half a gap out).
                            int slot = count > 0 ? l.appsRowUsableWidthPx / count : 0;
                            int iconEdge = fromEdge + Math.max(0, (slot - iconPx) / 2);
                            assertTrue(at + ": inset " + fromEdge + "px",
                                (count == GRID_COLUMNS ? iconEdge : fromEdge)
                                    >= Math.round(leastDp * DENSITY) - 1);
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
            // Home's columns as WidgetGridMetrics cuts them: the width inside the margins less
            // the gaps, shared out, a gap between neighbours.
            int width = Math.round(412 * DENSITY);
            int margin = Math.round(GRID_EDGE_DP * DENSITY);
            int gap = Math.round(com.termux.app.launcher.widget.WidgetGridMetrics.GAP_DP * DENSITY);
            int usable = width - 2 * margin - gap * (GRID_COLUMNS - 1);
            int slot = l.appsRowUsableWidthPx / GRID_COLUMNS;
            for (int c = 0; c < GRID_COLUMNS; c++) {
                float left = margin + edge(usable, GRID_COLUMNS, c) + gap * c;
                float right = margin + edge(usable, GRID_COLUMNS, c + 1) + gap * c;
                float gridCentre = (left + right) / 2f;
                float dockCentre = l.appsRowEdgeInsetPx + slot * c + slot / 2f;
                assertEquals(design + " column " + c + ": the icon on the column's centre",
                    // Within a px per column: the grid's columns keep their fraction of a px
                    // (its remainder goes to the first columns), the dock's slots are whole px
                    // of the shared width, so they drift by under a px per slot.
                    gridCentre, dockCentre, c + 1.5f);
            }
        }
    }

    @Test
    public void theCapsulesPaddingFollowsTheIconStepNotTheDockHeight() {
        for (DockStyle design : DockStyle.CHOICES) {
            int last = -1;
            for (IconSize step : IconSize.values()) {
                int padding = layout(design, true, step, 412, 5).capsuleAppsTotalPaddingPx;
                assertTrue(design + " " + step + ": " + padding + " after " + last,
                    padding >= last);
                last = padding;
            }
            assertTrue(design + ": XL's capsule stands taller than S's",
                layout(design, true, IconSize.XL, 412, 5).capsuleAppsTotalPaddingPx
                    > layout(design, true, IconSize.S, 412, 5).capsuleAppsTotalPaddingPx);
        }
    }

    /** WidgetGridMetrics' share: {@code e} columns' worth of {@code p} split {@code n} ways. */
    private static int edge(int p, int n, int e) {
        return e * (p / n) + Math.min(e, p % n);
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
