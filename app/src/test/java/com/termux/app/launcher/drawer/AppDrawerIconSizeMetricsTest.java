package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The drawer's icon size, column and row settings in the pure metrics. Automatic must resolve to
 * the numbers the drawer always had (the literals below are the pre-setting outputs); a chosen size
 * is honoured, falls back on columns, and is capped to the cell or row that has to hold it.
 */
public class AppDrawerIconSizeMetricsTest {

    private static final float EPS = 1e-3f;
    private static final float LABEL = 33f;

    /** The cell's own air in px: above the icon, between icon and label, under the label. */
    private static float rowAir(float density) {
        return (AppDrawerGridMetrics.ROW_TOP_DP + AppDrawerGridMetrics.LABEL_GAP_DP
            + AppDrawerGridMetrics.ROW_BOTTOM_DP) * density;
    }

    @Test public void automaticGridIsExactlyWhatItAlwaysWas() {
        // 360dp at 3x: four columns, the 48dp ceiling binds.
        AppDrawerGridMetrics a = AppDrawerGridMetrics.resolve(1080f, 3f, LABEL, 0, 0);
        assertEquals(4, a.columns);
        assertEquals(270f, a.cellWidthPx, EPS);
        assertEquals(144f, a.iconPx, EPS);
        assertEquals(144f + rowAir(3f) + LABEL, a.rowHeightPx, EPS);
        // 360dp at 4x: 192px, which is also LauncherIconStore's retained size.
        AppDrawerGridMetrics b = AppDrawerGridMetrics.resolve(1440f, 4f, LABEL, 0, 0);
        assertEquals(4, b.columns);
        assertEquals(192f, b.iconPx, EPS);
        assertEquals(192f + rowAir(4f) + LABEL, b.rowHeightPx, EPS);
        // A tablet-wide plane: six columns, still 48dp.
        AppDrawerGridMetrics c = AppDrawerGridMetrics.resolve(2400f, 3f, LABEL, 0, 0);
        assertEquals(6, c.columns);
        assertEquals(144f, c.iconPx, EPS);
        // A narrow plane: the cell binds, not the ceiling.
        AppDrawerGridMetrics d = AppDrawerGridMetrics.resolve(900f, 3f, LABEL, 0, 0);
        assertEquals(4, d.columns);
        assertEquals(130.5f, d.iconPx, EPS);
        // The three- and four-argument entry points are Automatic.
        assertEquals(a.iconPx, AppDrawerGridMetrics.resolve(1080f, 3f, LABEL).iconPx, 0f);
        assertEquals(a.columns, AppDrawerGridMetrics.resolve(1080f, 3f, LABEL, 0).columns);
    }

    @Test public void aChosenSizeBeyondTheAutomaticCeilingIsHonoured() {
        // 800dp at 3x: six columns of 133dp hold 60dp (needs 103dp); the 48dp ceiling is gone.
        AppDrawerGridMetrics m = AppDrawerGridMetrics.resolve(2400f, 3f, LABEL, 0, 60);
        assertEquals(6, m.columns);
        assertEquals(180f, m.iconPx, EPS);
        assertEquals(180f + rowAir(3f) + LABEL, m.rowHeightPx, EPS);
    }

    @Test public void automaticColumnsDropUntilTheCellHoldsTheIcon() {
        // 500dp: Automatic is six columns; 54dp needs a 93.1dp cell, which five of them give.
        AppDrawerGridMetrics m = AppDrawerGridMetrics.resolve(1500f, 3f, LABEL, 0, 54);
        assertEquals(5, m.columns);
        assertEquals(162f, m.iconPx, EPS);
        // 48dp still fits six there, so nothing drops.
        AppDrawerGridMetrics s = AppDrawerGridMetrics.resolve(1500f, 3f, LABEL, 0, 48);
        assertEquals(6, s.columns);
        assertEquals(144f, s.iconPx, EPS);
    }

    @Test public void fourColumnsAreTheFloorAndTheIconIsCappedToTheCell() {
        // 360dp: even four columns (90dp) hold only 52.2dp of a 60dp icon.
        AppDrawerGridMetrics m = AppDrawerGridMetrics.resolve(1080f, 3f, LABEL, 0, 60);
        assertEquals(4, m.columns);
        assertEquals(270f * AppDrawerGridMetrics.ICON_CELL_FRACTION, m.iconPx, EPS);
    }

    @Test public void chosenColumnsAndSizeCapTheIconToTheCell() {
        AppDrawerGridMetrics m = AppDrawerGridMetrics.resolve(1080f, 3f, LABEL, 6, 60);
        assertEquals(6, m.columns);
        assertEquals(180f * AppDrawerGridMetrics.ICON_CELL_FRACTION, m.iconPx, EPS);
        // Chosen columns with Automatic size: today's clamp.
        assertEquals(5, AppDrawerGridMetrics.resolve(1080f, 3f, LABEL, 5, 0).columns);
        assertEquals(6, AppDrawerGridMetrics.resolve(1080f, 3f, LABEL, 9, 0).columns);
    }

    @Test public void aSizeThatIsNotAStepIsAutomatic() {
        for (int stray : new int[] {-1, 36, 40, 44, 50, 72}) {
            AppDrawerGridMetrics m = AppDrawerGridMetrics.resolve(1080f, 3f, LABEL, 0, stray);
            assertEquals(stray + "", 144f, m.iconPx, EPS);
            assertEquals(stray + "", 4, m.columns);
        }
        assertTrue(AppDrawerGridMetrics.isIconStep(48));
        assertTrue(AppDrawerGridMetrics.isIconStep(54));
        assertTrue(AppDrawerGridMetrics.isIconStep(60));
    }

    @Test public void horizontalAutomaticIsUnchangedAndRowsCapTheChosenIcon() {
        AppDrawerHorizontalGridMetrics a = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 1440f, 3f, LABEL, 0, 0, 0);
        assertEquals(4, a.columns);
        assertEquals(6, a.rows);
        assertEquals(144f, a.iconPx, EPS);
        assertEquals(1440f / 6f, a.rowHeightPx, EPS);
        // Chosen rows, Automatic size.
        AppDrawerHorizontalGridMetrics three = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 1440f, 3f, LABEL, 0, 3, 0);
        assertEquals(3, three.rows);
        assertEquals(144f, three.iconPx, EPS);

        // 60dp on Automatic rows: the larger cell holds fewer rows.
        AppDrawerHorizontalGridMetrics big = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 1440f, 3f, LABEL, 0, 0, 60);
        assertEquals(5, big.rows);
        // 60dp with six rows asked for: six rows win and the icon gives way to the row.
        AppDrawerHorizontalGridMetrics six = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 1440f, 3f, LABEL, 0, 6, 60);
        assertEquals(6, six.rows);
        float rowHeight = 1440f / 6f;
        assertEquals(rowHeight - LABEL - rowAir(3f), six.iconPx, EPS);
        assertTrue(six.iconPx < 60f * 3f);
    }

    @Test public void categoriesAutomaticSharesOneIconSizeAndAChosenSizeSizesTheDetailGrid() {
        for (float width : new float[] {1080f, 1440f, 2400f}) {
            AppDrawerCategoryGridMetrics auto = AppDrawerCategoryGridMetrics.resolve(
                width, 1600f, 3f, 40f, LABEL, 60f, 8 * 1024 * 1024, 0, 0);
            assertEquals(auto.largeIconPx, auto.detailIconPx);
        }
        AppDrawerCategoryGridMetrics chosen = AppDrawerCategoryGridMetrics.resolve(
            1080f, 1600f, 3f, 40f, LABEL, 60f, 8 * 1024 * 1024, 0, 54);
        // The expanded grid is a third of 360dp wide, 209px of icon per cell: 54dp = 162px fits.
        assertEquals(162, chosen.detailIconPx);
        // Previews take the size or what the card holds, whichever is less.
        assertTrue(chosen.largeIconPx <= 162);
        assertTrue(chosen.largeIconPx > 0);
        assertEquals(chosen.detailIconPx + rowAir(3f) + LABEL, chosen.expandedRowHeightPx, EPS);
        // A cell that holds less than the setting wins over it.
        AppDrawerCategoryGridMetrics narrow = AppDrawerCategoryGridMetrics.resolve(
            780f, 1600f, 3f, 40f, LABEL, 60f, 8 * 1024 * 1024, 0, 60);
        assertEquals(Math.round(780f / 3f * 0.58f), narrow.detailIconPx);
    }

    @Test public void storedStepsParseAndGarbageIsAutomatic() {
        assertEquals(0, AppDrawerLayoutConfig.parseStep("auto"));
        assertEquals(0, AppDrawerLayoutConfig.parseStep(null));
        assertEquals(0, AppDrawerLayoutConfig.parseStep("garbage"));
        assertEquals(54, AppDrawerLayoutConfig.parseStep("54"));
        // A number the sets do not hold reaches the constructor and is dropped there.
        AppDrawerLayoutConfig config = new AppDrawerLayoutConfig(AppDrawerViewType.HORIZONTAL,
            AppDrawerLayoutConfig.parseStep("55"), AppDrawerLayoutConfig.parseStep("9"), 4,
            AppDrawerLayoutConfig.parseStep("2"), 0);
        assertEquals(0, config.iconSizeDp);
        assertEquals(0, config.verticalColumns);
        assertEquals(0, config.horizontalRows);
        assertEquals(4, config.horizontalColumns);
    }
}
