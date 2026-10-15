package com.termux.view;

import android.graphics.Canvas;
import android.graphics.RenderNode;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Whether a frame of a smooth (per-pixel) scroll may replay the grid an earlier frame recorded,
 * instead of rendering it again.
 *
 * <p>Between two row crossings a smooth scroll only moves the grid: the view translates the canvas
 * by the sub-row offset and the rows themselves are the same. So the grid is recorded once, at
 * zero translation, and every frame until the top row, the content or the view's geometry changes
 * draws that recording under the frame's own translation. Everything else the view draws around
 * the grid — the padding fill, the find overlay, the selection handles — stays live.
 *
 * <p>The decision is pure so it can be tested on the JVM; the recording itself lives in
 * {@link Node}, which only exists from API 29, where {@link RenderNode} is public.
 */
final class ScrollRenderCache {

    /** Whether the recording holds anything; false until the first {@link #remember}. */
    private boolean mValid;
    private int mTopRow;
    private int mExtraRows;
    private int mWidth;
    private int mHeight;
    private float mHorizontalOffset;
    private boolean mTransparentFrameClear;
    private int mTransparentOverlayColor;
    private long mGeneration;

    /**
     * Whether this frame may draw the grid from a recording at all. Only a hardware canvas on API
     * 29 or later can replay one, only a smooth scroll moves the grid without changing it, and a
     * travel's revealed rows, a selection or a frozen copy of the pane each need the live render.
     */
    static boolean applies(boolean hardwareCanvas, int sdkInt, boolean smoothScrolling,
                           boolean travelling, int fillRows, boolean selecting,
                           boolean frozenCopy) {
        return hardwareCanvas && sdkInt >= Build.VERSION_CODES.Q && smoothScrolling
            && !travelling && fillRows == 0 && !selecting && !frozenCopy;
    }

    /** Whether the recording was made with exactly these inputs, so it can be drawn again. */
    boolean canReuse(int topRow, int extraRows, int width, int height, float horizontalOffset,
                     boolean transparentFrameClear, int transparentOverlayColor,
                     long generation) {
        return mValid && mTopRow == topRow && mExtraRows == extraRows && mWidth == width
            && mHeight == height && Float.compare(mHorizontalOffset, horizontalOffset) == 0
            && mTransparentFrameClear == transparentFrameClear
            && mTransparentOverlayColor == transparentOverlayColor
            && mGeneration == generation;
    }

    /** The recording now holds a grid drawn with these inputs. */
    void remember(int topRow, int extraRows, int width, int height, float horizontalOffset,
                  boolean transparentFrameClear, int transparentOverlayColor, long generation) {
        mValid = true;
        mTopRow = topRow;
        mExtraRows = extraRows;
        mWidth = width;
        mHeight = height;
        mHorizontalOffset = horizontalOffset;
        mTransparentFrameClear = transparentFrameClear;
        mTransparentOverlayColor = transparentOverlayColor;
        mGeneration = generation;
    }

    /** The recording no longer matches anything; the next frame that applies records afresh. */
    void forget() {
        mValid = false;
    }

    /**
     * The recorded grid. It is sized like the view, so the renderer sees the same canvas width and
     * height it sees when drawing straight into the view, and does not clip, so whatever the
     * renderer draws past the view's edges reaches the screen exactly as it does unrecorded.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    static final class Node {

        @Nullable
        private RenderNode mNode;

        /** Start recording the grid; draw into the returned canvas, then {@link #endRecording}. */
        Canvas beginRecording(int width, int height) {
            if (mNode == null) {
                mNode = new RenderNode("TerminalScrollGrid");
                mNode.setClipToBounds(false);
            }
            mNode.setPosition(0, 0, width, height);
            return mNode.beginRecording(width, height);
        }

        void endRecording() {
            if (mNode != null) mNode.endRecording();
        }

        /** Draw the recording into {@code canvas}, under whatever translation it carries. */
        void draw(Canvas canvas) {
            if (mNode != null) canvas.drawRenderNode(mNode);
        }

        boolean exists() {
            return mNode != null;
        }

        /**
         * Free the recording. Only safe once no display list draws it any more: the view calls this
         * from a draw that did not use it, or once it is detached.
         */
        void discard() {
            if (mNode == null) return;
            mNode.discardDisplayList();
            mNode = null;
        }
    }
}
