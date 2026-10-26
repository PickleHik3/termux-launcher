package com.termux.app.dock;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.terminal.TerminalClockWidget;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The Wall strip's clock fits its room at every row height and terminal count it stands in: the
 * 2026-10-21 emulator check saw the 128 dp row cut the clock's period to "P" and its seconds to
 * one digit with two terminals open. Drawn for real, every face's time band (digits, period,
 * seconds) ends inside the clock's room, and the face the clock reports painting does too.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallStripRowViewClockFitTest {

    private static final float DENSITY = 3f;
    private static final int WIDTH = Math.round(411 * DENSITY);
    /** Ink a face's glyphs carry past their advance (the LCD's segment edge), tolerated. */
    private static final int INK_SLACK = Math.round(DENSITY);

    private static final String[] FACES = {
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE,
        TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB,
    };

    private static List<WallStripRowView.Tile> tiles(int count) {
        List<WallStripRowView.Tile> tiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tiles.add(new WallStripRowView.Tile(i, String.valueOf(i + 1), "home", "home",
                i == 0, false, new float[]{0f, 0f, 1f, 1f}));
        }
        return tiles;
    }

    private static WallStripRowView laidOut(String face, int heightDp, int terminals) {
        WallStripRowView row = new WallStripRowView(ApplicationProvider.getApplicationContext());
        row.setGeometry(14f * DENSITY, 10f * DENSITY, 28f * DENSITY, 12f * DENSITY);
        row.setClockFace(face, true, true);
        row.setTiles(tiles(terminals));
        int height = Math.round(heightDp * DENSITY);
        row.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, WIDTH, height);
        return row;
    }

    /** The rightmost column the clock inks in its time band, drawn unclipped; -1 for none. */
    private static int bandInkRight(TerminalClockWidget clock, int room) {
        Bitmap bitmap = Bitmap.createBitmap(room + Math.round(120 * DENSITY), clock.getHeight(),
            Bitmap.Config.ARGB_8888);
        clock.draw(new Canvas(bitmap));
        float center = clock.fullBandCenterYPx();
        float band = clock.fullBandHeightPx();
        int top = Math.max(0, Math.round(center - band / 2f));
        int bottom = Math.min(bitmap.getHeight(), Math.round(center + band / 2f));
        for (int x = bitmap.getWidth() - 1; x >= 0; x--) {
            for (int y = top; y < bottom; y++) {
                if ((bitmap.getPixel(x, y) >>> 24) > 40) return x;
            }
        }
        return -1;
    }

    @Test
    public void everyFace_fitsItsRoom_atEveryRowHeight_withNoneOneOrTwoTerminals() {
        for (String face : FACES) {
            for (int heightDp = WallStripRowPolicy.MIN_HEIGHT_DP;
                 heightDp <= WallStripRowPolicy.MAX_HEIGHT_DP; heightDp += 12) {
                for (int terminals = 0; terminals <= 2; terminals++) {
                    WallStripRowView row = laidOut(face, heightDp, terminals);
                    TerminalClockWidget clock = (TerminalClockWidget) row.clockView();
                    int room = Math.round(row.rowNow().clockWidthPx);
                    String at = face + " at " + heightDp + " dp, " + terminals + " terminals";
                    assertTrue(at + ": painted " + clock.paintedRightPx() + " in " + room,
                        clock.paintedRightPx() <= room + 0.5f);
                    int ink = bandInkRight(clock, room);
                    assertTrue(at + ": inked to " + ink + " in " + room, ink >= 0);
                    assertTrue(at + ": inked to " + ink + " in " + room,
                        ink <= room + INK_SLACK);
                }
            }
        }
    }

    @Test
    public void atTheTallestRow_withTwoTerminals_theClockIsNoWiderThanItsRoom() {
        for (String face : FACES) {
            WallStripRowView row = laidOut(face, WallStripRowPolicy.MAX_HEIGHT_DP, 2);
            TerminalClockWidget clock = (TerminalClockWidget) row.clockView();
            float room = row.rowNow().clockWidthPx;
            assertTrue(face, clock.getWidth() <= Math.round(room));
            assertTrue(face + ": painted " + clock.paintedRightPx() + " in " + room,
                clock.paintedRightPx() <= room + 0.5f);
            int ink = bandInkRight(clock, Math.round(room));
            assertTrue(face + ": inked to " + ink + " in " + room,
                ink <= Math.round(room) + INK_SLACK);
        }
    }
}
