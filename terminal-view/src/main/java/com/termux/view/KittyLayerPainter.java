package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.termux.terminal.KittyPlacement;
import com.termux.terminal.TerminalEmulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Draws the kitty graphics layer: every placement that reaches into the rows in view, each as one
 * filtered bitmap draw at its exact pixel rectangle.
 *
 * <p>A placement's rectangle is given in the cell pixels the terminal reported to the program
 * ({@code CSI 16 t}), which are whole numbers, while the renderer's cell is a float as wide as the
 * font's advance. So the rectangle is scaled once, by the true ratio between the two, and drawn
 * with bitmap filtering: a script word in a picture keeps smooth hairlines, and a picture laid out
 * to the grid by the program stays on the grid on screen. There is no per-cell slicing, so there
 * are no seams.</p>
 *
 * <p>Two passes per frame: {@code z < 0} after the cell backgrounds and before the text, so the text
 * stands on the picture, and {@code z >= 0} after everything, over the text — kitty's layering.
 * Within a pass placements go in z order, and in anchor order within one z.</p>
 */
final class KittyLayerPainter {

    /** The paint every placement is drawn with: bitmap filtering, nothing else. */
    static final int PAINT_FLAGS = Paint.FILTER_BITMAP_FLAG;

    /** Where a placement's pixels go. The renderer's own target draws into a canvas. */
    interface Target {
        void drawPlacement(@NonNull KittyPlacement placement, @NonNull float[] destination,
                           @NonNull float[] clip, int paintFlags);
    }

    private static final Comparator<KittyPlacement> BY_Z =
        (a, b) -> Integer.compare(a.getZ(), b.getZ());

    private final ArrayList<KittyPlacement> mVisible = new ArrayList<>();
    private final float[] mDestination = new float[4];
    private final float[] mClip = new float[4];

    /** Pick up the placements reaching into {@code rowCount} rows from external row {@code topRow}. */
    void collect(@NonNull TerminalEmulator emulator, int topRow, int rowCount) {
        emulator.collectKittyPlacements(topRow, rowCount, mVisible);
        if (mVisible.size() > 1) Collections.sort(mVisible, BY_Z);
    }

    /** Take a ready list instead, as a test does. */
    void collect(@NonNull java.util.List<KittyPlacement> placements) {
        mVisible.clear();
        mVisible.addAll(placements);
        if (mVisible.size() > 1) Collections.sort(mVisible, BY_Z);
    }

    boolean isEmpty() {
        return mVisible.isEmpty();
    }

    /**
     * Draw one pass of the collected placements.
     *
     * @param underText the {@code z < 0} pass when set, the {@code z >= 0} pass otherwise.
     * @param firstRowTop the y of the top edge of row {@code topRow}.
     * @return how many placements were drawn.
     */
    int paint(@NonNull Target target, boolean underText, int topRow, float fontWidth,
              float lineSpacing, float firstRowTop, float horizontalOffset) {
        int drawn = 0;
        for (int i = 0, n = mVisible.size(); i < n; i++) {
            KittyPlacement placement = mVisible.get(i);
            if ((placement.getZ() < 0) != underText) continue;
            if (placement.getSourceWidth() <= 0 || placement.getSourceHeight() <= 0) continue;
            geometry(placement, topRow, fontWidth, lineSpacing, firstRowTop, horizontalOffset,
                mDestination, mClip);
            target.drawPlacement(placement, mDestination, mClip, PAINT_FLAGS);
            drawn++;
        }
        return drawn;
    }

    /**
     * Where a placement lands on screen. {@code destination} gets the scaled picture rectangle
     * {left, top, right, bottom}; {@code clip} the cells it covers, which cuts it only at the right
     * screen edge.
     */
    static void geometry(@NonNull KittyPlacement placement, int topRow, float fontWidth,
                         float lineSpacing, float firstRowTop, float horizontalOffset,
                         @NonNull float[] destination, @NonNull float[] clip) {
        float scaleX = fontWidth / Math.max(1, placement.getCellWidth());
        float scaleY = lineSpacing / Math.max(1, placement.getCellHeight());
        float cellLeft = horizontalOffset + placement.getColumn() * fontWidth;
        float cellTop = firstRowTop + (placement.getRow() - topRow) * lineSpacing;
        destination[0] = cellLeft + placement.getOffsetX() * scaleX;
        destination[1] = cellTop + placement.getOffsetY() * scaleY;
        destination[2] = destination[0] + placement.getWidth() * scaleX;
        destination[3] = destination[1] + placement.getHeight() * scaleY;
        clip[0] = cellLeft;
        clip[1] = cellTop;
        clip[2] = cellLeft + placement.getColumns() * fontWidth;
        clip[3] = cellTop + placement.getRows() * lineSpacing;
    }

    /** The renderer's target: one clipped, filtered drawBitmap per placement. */
    static final class CanvasTarget implements Target {
        private final Paint mPaint = new Paint(PAINT_FLAGS);
        private final Rect mSource = new Rect();
        private final RectF mDestination = new RectF();
        private Canvas mCanvas;

        CanvasTarget into(Canvas canvas) {
            mCanvas = canvas;
            return this;
        }

        @Override
        public void drawPlacement(@NonNull KittyPlacement placement, @NonNull float[] destination,
                                  @NonNull float[] clip, int paintFlags) {
            Bitmap bitmap = placement.getBitmap();
            if (bitmap == null || bitmap.isRecycled() || mCanvas == null) return;
            mSource.set(placement.getSourceX(), placement.getSourceY(),
                placement.getSourceX() + placement.getSourceWidth(),
                placement.getSourceY() + placement.getSourceHeight());
            mDestination.set(destination[0], destination[1], destination[2], destination[3]);
            // Filtering alone smooths a picture drawn near its own size; one drawn at under half
            // of it would skip source pixels and shimmer, which mip levels prevent.
            if ((mDestination.width() * 2 < placement.getSourceWidth()
                || mDestination.height() * 2 < placement.getSourceHeight()) && !bitmap.hasMipMap())
                bitmap.setHasMipMap(true);
            int saved = mCanvas.save();
            mCanvas.clipRect(clip[0], clip[1], clip[2], clip[3]);
            mCanvas.drawBitmap(bitmap, mSource, mDestination, mPaint);
            mCanvas.restoreToCount(saved);
        }
    }
}
