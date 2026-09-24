package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.graphics.Paint;

import androidx.annotation.NonNull;

import com.termux.terminal.KittyPlacement;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where the kitty layer puts a picture and how it is drawn: one draw per placement, at the exact
 * float rectangle its cell and pixel offset give once the reported whole-pixel cell is scaled to
 * the real one, with bitmap filtering, in the pass its z belongs to.
 */
public class KittyLayerPainterTest {

    private static final float EPSILON = 0.0001f;

    /** A reported cell of 10x20 against a drawn one of 10.5x21: the phone's case of a float advance. */
    private static final int CELL_WIDTH = 10;
    private static final int CELL_HEIGHT = 20;
    private static final float FONT_WIDTH = 10.5f;
    private static final float LINE_SPACING = 21f;
    private static final float FIRST_ROW_TOP = 4f;
    private static final float HORIZONTAL_OFFSET = 1f;

    private static final class Draw {
        final KittyPlacement placement;
        final float[] destination;
        final float[] clip;
        final int paintFlags;

        Draw(KittyPlacement placement, float[] destination, float[] clip, int paintFlags) {
            this.placement = placement;
            this.destination = destination.clone();
            this.clip = clip.clone();
            this.paintFlags = paintFlags;
        }
    }

    private static final class Recorder implements KittyLayerPainter.Target {
        final List<Draw> draws = new ArrayList<>();

        @Override
        public void drawPlacement(@NonNull KittyPlacement placement, @NonNull float[] destination,
                                  @NonNull float[] clip, int paintFlags) {
            draws.add(new Draw(placement, destination, clip, paintFlags));
        }
    }

    private static KittyPlacement placement(int z, int column, int row, int offsetX, int offsetY,
                                            int width, int height) {
        return KittyPlacement.forTest(5, 1, null, z, column, row, 0, 0, 40, 20, width, height,
            offsetX, offsetY, CELL_WIDTH, CELL_HEIGHT);
    }

    @Test
    public void aPlacementAtAPixelOffsetIsOneFilteredDrawAtItsExactRectangle() {
        KittyPlacement picture = placement(0, 2, 3, 3, 4, 40, 20);
        KittyLayerPainter painter = new KittyLayerPainter();
        painter.collect(Arrays.asList(picture));
        Recorder recorder = new Recorder();

        int drawn = painter.paint(recorder, false, 1, FONT_WIDTH, LINE_SPACING, FIRST_ROW_TOP,
            HORIZONTAL_OFFSET);

        assertEquals(1, drawn);
        assertEquals(1, recorder.draws.size());
        Draw draw = recorder.draws.get(0);
        assertSame(picture, draw.placement);
        assertEquals(Paint.FILTER_BITMAP_FLAG, draw.paintFlags & Paint.FILTER_BITMAP_FLAG);
        float scaleX = FONT_WIDTH / CELL_WIDTH;
        float scaleY = LINE_SPACING / CELL_HEIGHT;
        float cellLeft = HORIZONTAL_OFFSET + 2 * FONT_WIDTH;
        float cellTop = FIRST_ROW_TOP + (3 - 1) * LINE_SPACING;
        assertEquals(cellLeft + 3 * scaleX, draw.destination[0], EPSILON);
        assertEquals(cellTop + 4 * scaleY, draw.destination[1], EPSILON);
        assertEquals(cellLeft + 3 * scaleX + 40 * scaleX, draw.destination[2], EPSILON);
        assertEquals(cellTop + 4 * scaleY + 20 * scaleY, draw.destination[3], EPSILON);
        // The clip is the cells covered: 43 px across is five cells, 24 px down is two rows.
        assertEquals(cellLeft, draw.clip[0], EPSILON);
        assertEquals(cellTop, draw.clip[1], EPSILON);
        assertEquals(cellLeft + 5 * FONT_WIDTH, draw.clip[2], EPSILON);
        assertEquals(cellTop + 2 * LINE_SPACING, draw.clip[3], EPSILON);
    }

    @Test
    public void aPictureLaidOutToTheGridStaysOnTheGridOnScreen() {
        // Four reported cells wide and two tall must cover exactly four drawn cells and two rows.
        KittyPlacement picture = placement(0, 0, 0, 0, 0, 4 * CELL_WIDTH, 2 * CELL_HEIGHT);
        KittyLayerPainter painter = new KittyLayerPainter();
        painter.collect(Arrays.asList(picture));
        Recorder recorder = new Recorder();
        painter.paint(recorder, false, 0, FONT_WIDTH, LINE_SPACING, 0f, 0f);
        Draw draw = recorder.draws.get(0);
        assertEquals(4 * FONT_WIDTH, draw.destination[2] - draw.destination[0], EPSILON);
        assertEquals(2 * LINE_SPACING, draw.destination[3] - draw.destination[1], EPSILON);
    }

    @Test
    public void negativeZIsDrawnUnderTheTextAndTheRestOverItInZOrder() {
        KittyPlacement under = placement(-1, 0, 0, 0, 0, 10, 20);
        KittyPlacement overHigh = placement(5, 0, 0, 0, 0, 10, 20);
        KittyPlacement overLow = placement(0, 0, 0, 0, 0, 10, 20);
        KittyLayerPainter painter = new KittyLayerPainter();
        painter.collect(Arrays.asList(overHigh, under, overLow));

        Recorder underPass = new Recorder();
        painter.paint(underPass, true, 0, FONT_WIDTH, LINE_SPACING, 0f, 0f);
        assertEquals(1, underPass.draws.size());
        assertSame(under, underPass.draws.get(0).placement);

        Recorder overPass = new Recorder();
        painter.paint(overPass, false, 0, FONT_WIDTH, LINE_SPACING, 0f, 0f);
        assertEquals(2, overPass.draws.size());
        assertSame(overLow, overPass.draws.get(0).placement);
        assertSame(overHigh, overPass.draws.get(1).placement);
    }

    @Test
    public void aPlacementAnchoredAboveTheViewIsDrawnFromAboveTheTop() {
        KittyPlacement picture = placement(0, 0, -2, 0, 0, 10, 3 * CELL_HEIGHT);
        KittyLayerPainter painter = new KittyLayerPainter();
        painter.collect(Arrays.asList(picture));
        Recorder recorder = new Recorder();
        painter.paint(recorder, false, 0, FONT_WIDTH, LINE_SPACING, 0f, 0f);
        assertEquals(-2 * LINE_SPACING, recorder.draws.get(0).destination[1], EPSILON);
    }

    @Test
    public void theCanvasTargetsPaintFilters() {
        assertEquals(Paint.FILTER_BITMAP_FLAG, KittyLayerPainter.PAINT_FLAGS);
    }
}
