package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Home in landscape the Launcher3 way (ADR 0020 §12, spec §8b), on the developer's phone:
 * 1080 × 2412 px at 420 dpi (density 2.625), turned. One grid for both orientations: the caps
 * come from the screen in its portrait form, so the grid keeps its columns and rows and every
 * item its cell; the cells get shorter, the icons keep the step, the labels hide by Launcher3's
 * rule.
 */
public class HomeLandscapeGridTest {

    private static final float DENSITY = 2.625f;
    private static final float WIDTH_DP = 1080 / DENSITY;
    private static final float HEIGHT_DP = 2412 / DENSITY;
    private static final float MARGIN_DP = 16f;
    /** Grids around the developer's: 4 × 6 and the larger ones a user may pick. */
    private static final int[][] GRIDS = {{4, 6}, {5, 6}, {4, 7}, {5, 8}};

    @Test
    public void aTurnKeepsTheCapsSoNoGridShrinksAndNoItemMoves() {
        WidgetGridCaps portrait = WidgetGridCaps.forPortraitScreen(WIDTH_DP, HEIGHT_DP, MARGIN_DP);
        WidgetGridCaps landscape = WidgetGridCaps.forPortraitScreen(HEIGHT_DP, WIDTH_DP, MARGIN_DP);
        assertEquals(portrait, landscape);
        for (int[] grid : GRIDS) {
            assertEquals(grid[0] + "x" + grid[1] + ": the columns stand",
                grid[0], landscape.clampColumns(grid[0]));
            assertEquals(grid[0] + "x" + grid[1] + ": the rows stand",
                grid[1], landscape.clampRows(grid[1]));
            // Every cell of the portrait grid is a cell of the landscape one: the same grid.
            for (int column = 0; column < grid[0]; column++) {
                for (int row = 0; row < grid[1]; row++) {
                    assertTrue(column < landscape.clampColumns(grid[0])
                        && row < landscape.clampRows(grid[1]));
                }
            }
        }
    }

    @Test
    public void inLandscapeTheIconKeepsTheStepAndTheLabelsHide() {
        int labelPx = Math.round(16 * DENSITY);
        float mScale = 48f / HomeAppIconMetrics.ICON_DP;
        int stepPx = Math.round(48 * DENSITY);
        // Landscape: about 411 dp tall less the status bar (24 dp), 6 rows; 919 dp wide less a
        // side dock bar (about 80 dp) and the margins, 4 columns.
        int cellH = Math.round((WIDTH_DP - 24 - 2 * MARGIN_DP) / 6f * DENSITY);
        int cellW = Math.round((HEIGHT_DP - 80 - 2 * MARGIN_DP) / 4f * DENSITY);
        HomeAppIconMetrics turned = HomeAppIconMetrics.of(cellW, cellH, labelPx, DENSITY, mScale);
        assertFalse("labels hide in a " + cellH + "px cell", turned.showLabel);
        assertEquals("the icon keeps the step", stepPx, turned.iconPx);
        // Portrait: the same grid's cells are tall enough for the label beside the step.
        int portraitH = Math.round((HEIGHT_DP - 24 - 120 - 2 * MARGIN_DP) / 6f * DENSITY);
        int portraitW = Math.round((WIDTH_DP - 2 * MARGIN_DP) / 4f * DENSITY);
        HomeAppIconMetrics upright = HomeAppIconMetrics.of(portraitW, portraitH, labelPx, DENSITY,
            mScale);
        assertTrue("labels show upright", upright.showLabel);
        assertEquals(stepPx, upright.iconPx);
    }

    @Test
    public void launcher3sLabelRule() {
        // cellHeight - icon - padding - text < text hides.
        assertTrue(HomeAppIconMetrics.labelsCrowded(150, 126, 10, 42));
        assertFalse(HomeAppIconMetrics.labelsCrowded(230, 126, 10, 42));
        assertFalse(HomeAppIconMetrics.labelsCrowded(220, 126, 10, 42));
        assertTrue(HomeAppIconMetrics.labelsCrowded(219, 126, 10, 42));
    }
}
