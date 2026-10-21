package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The Wall strip's clock as the row view lays it out (spec "Wall strip"): it stands in the row with
 * none, one or two terminals, at the policy's share, and has slid out from three on. The 10-21
 * emulator check saw no clock with four windows open (three row windows and a Home float), which
 * is the spec's row for four.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallStripRowViewClockTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 252;
    /** The spec's 14 dp side inset, at 3x. */
    private static final float INSET = 42f;
    private static final float GAP = 24f;

    private static List<WallStripRowView.Tile> tiles(int count) {
        List<WallStripRowView.Tile> tiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tiles.add(new WallStripRowView.Tile(i, String.valueOf(i + 1), "home", "home",
                i == 0, false, new float[]{0f, 0f, 1f, 1f}));
        }
        return tiles;
    }

    private static WallStripRowView laidOut(int terminals) {
        WallStripRowView row = new WallStripRowView(ApplicationProvider.getApplicationContext());
        row.setGeometry(INSET, GAP, 60f, 24f);
        row.setTiles(tiles(terminals));
        row.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, WIDTH, HEIGHT);
        return row;
    }

    @Test
    public void noneOneAndTwoTerminals_showTheClock_atThePolicysShare() {
        for (int terminals = 0; terminals <= 2; terminals++) {
            WallStripRowPolicy.Row row = laidOut(terminals).rowNow();
            assertTrue(terminals + " terminals: the clock stands in the row", row.showsClock());
            WallStripRowPolicy.Row expected = WallStripRowPolicy.layout(WIDTH, INSET, GAP,
                terminals, 60f, 24f);
            assertEquals(terminals + " terminals", expected.clockWidthPx, row.clockWidthPx, 0.01f);
        }
    }

    @Test
    public void threeAndFourTerminals_haveSlidTheClockOut() {
        assertFalse(laidOut(3).rowNow().showsClock());
        assertFalse(laidOut(4).rowNow().showsClock());
    }

    @Test
    public void closingDownToTwo_bringsTheClockBack() {
        WallStripRowView row = laidOut(4);
        row.setTiles(tiles(2));
        // Unattached, the row lands at rest without a slide.
        assertTrue(row.rowNow().showsClock());
    }
}
