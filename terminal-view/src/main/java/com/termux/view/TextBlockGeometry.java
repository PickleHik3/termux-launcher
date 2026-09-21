package com.termux.view;

import com.termux.terminal.KittyTextSizing;
import com.termux.terminal.TerminalEmulator;

/**
 * Where a kitty text sizing block (OSC 66) lands on screen, and at what size its text is drawn.
 *
 * <p>Everything here is arithmetic on the block's record and the renderer's font metrics, with no
 * Paint, Canvas or Context anywhere, so the one thing the protocol is easy to get wrong — which
 * pixels a block covers and where inside them its text sits — is checked by a plain JVM test
 * instead of by eye on a device.
 *
 * <p>The vertical coordinates follow the renderer's own convention: a row is given by the y of its
 * <em>bottom</em> edge, and its top is that y minus one line spacing.
 */
final class TextBlockGeometry {

    private TextBlockGeometry() {
    }

    /**
     * How many times normal size a block's text is drawn: its scale, cut by the {@code n/d}
     * fraction of the block height when one was asked for. A demoted block — one that did not fit
     * the screen and sits on a single row — draws at normal size, keeping its record so that a
     * later resize can promote it back.
     */
    static float sizeScale(int scale, int numerator, int denominator, boolean demoted) {
        if (demoted) return 1f;
        float size = Math.max(1, scale);
        if (numerator > 0 && denominator > 0)
            size = size * numerator / denominator;
        return size > 0f ? size : 1f;
    }

    /** The left edge of a block, in the same coordinates a run's left edge is computed in. */
    static float left(float horizontalOffset, float fontWidth, int column) {
        return horizontalOffset + column * fontWidth;
    }

    /** How wide a block is in pixels. */
    static float width(float fontWidth, int columns) {
        return columns * fontWidth;
    }

    /**
     * The top edge of a block, given the bottom edge of one of the rows it covers.
     *
     * @param rowBottom      the y the renderer passes for that row.
     * @param rowsBelowAnchor how far that row sits below the block's anchor row.
     */
    static float top(float rowBottom, int lineSpacing, int rowsBelowAnchor) {
        return rowBottom - lineSpacing - rowsBelowAnchor * (float) lineSpacing;
    }

    /** How tall a block is in pixels. */
    static float height(int lineSpacing, int rows) {
        return Math.max(1, rows) * (float) lineSpacing;
    }

    /** The height of the text box drawn inside a block: one row's height at the drawn size. */
    static float boxHeight(int lineSpacing, float sizeScale) {
        return lineSpacing * sizeScale;
    }

    /**
     * Where the text box starts vertically inside the block: at the top, at the bottom, or centred.
     * Only a block with an {@code n/d} fraction has room to move — without one the box is exactly
     * as tall as the block, and all three answers agree.
     */
    static float alignedTop(float blockTop, float blockHeight, float boxHeight,
                            int verticalAlign) {
        final float slack = blockHeight - boxHeight;
        switch (verticalAlign) {
            case KittyTextSizing.ALIGN_END:
                return blockTop + slack;
            case KittyTextSizing.ALIGN_CENTRE:
                return blockTop + slack / 2f;
            default:
                return blockTop;
        }
    }

    /**
     * Where the text starts horizontally inside the block, from the advance it measured at the
     * drawn size. Text wider than its block is left at the block's left edge whichever alignment
     * was asked for, so the clip cuts its tail rather than its head.
     */
    static float alignedLeft(float blockLeft, float blockWidth, float advance,
                             int horizontalAlign) {
        final float slack = blockWidth - advance;
        if (slack <= 0f) return blockLeft;
        switch (horizontalAlign) {
            case KittyTextSizing.ALIGN_END:
                return blockLeft + slack;
            case KittyTextSizing.ALIGN_CENTRE:
                return blockLeft + slack / 2f;
            default:
                return blockLeft;
        }
    }

    /**
     * The baseline the block's text is drawn on, from the top of its text box. This is the
     * renderer's own baseline placement — one line spacing down, less the baseline descent —
     * taken at the drawn size, so a block at scale 1 sits exactly where plain text would.
     */
    static float baseline(float boxTop, int lineSpacing, int baselineDescent, float sizeScale) {
        return boxTop + (lineSpacing - baselineDescent) * sizeScale;
    }

    /**
     * The cursor rectangle to paint on one row of a block, as {@code left, top, right, bottom}.
     *
     * <p>D2: the cursor grows to cover the whole block. A block cursor fills it, so every row it
     * covers paints its full width; a bar stands at the block's left edge down the whole height, at
     * the thickness it has over a plain cell; an underline runs along the bottom of the block, so
     * only its last row paints anything.
     *
     * @return false when this shape paints nothing on this row.
     */
    static boolean cursorRect(int shape, float blockLeft, float blockWidth, float barWidth,
                              float rowTop, float rowBottom, int lineSpacing,
                              boolean lastRowOfBlock, float[] out) {
        if (shape == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) {
            if (!lastRowOfBlock) return false;
            out[0] = blockLeft;
            out[1] = rowBottom - lineSpacing / 4f;
            out[2] = blockLeft + blockWidth;
            out[3] = rowBottom;
            return true;
        }
        out[0] = blockLeft;
        out[1] = rowTop;
        out[2] = shape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
            ? blockLeft + barWidth : blockLeft + blockWidth;
        out[3] = rowBottom;
        return true;
    }
}
