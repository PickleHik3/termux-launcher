package com.termux.view;

import android.graphics.RenderNode;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * The two {@link RenderNode}s each visible row is recorded into, and nothing else.
 *
 * <p>Two per row, not one, because the render loop paints every row's cell backgrounds and cursor
 * block before it paints any glyph: ink that overhangs its cell — Nerd Font symbols routinely
 * overhang — has to land on the next row's fill rather than under it. Replaying one node per row
 * would put each row's background back on top of the row above's glyphs. So the backgrounds are
 * replayed as one pass and the glyphs as another, exactly as they are drawn.
 *
 * <p>Recording and drawing both happen on the UI thread. {@code RenderNode} is documented as usable
 * from any thread but only from one, and only from the thread it is drawn with, which rules out
 * recording a row on a worker: this is a way to skip work, not to move it.
 */
@RequiresApi(Build.VERSION_CODES.Q)
final class TerminalRowNodes {

    private static final RenderNode[] NONE = new RenderNode[0];

    private RenderNode[] mBackgrounds = NONE;
    private RenderNode[] mGlyphs = NONE;

    /**
     * Hold exactly this many rows, dropping the display list of every row that no longer exists.
     * A pane that shrinks must not keep the recordings of the rows it lost.
     */
    void resize(int rows) {
        if (mBackgrounds.length == rows) return;
        RenderNode[] backgrounds = new RenderNode[rows];
        RenderNode[] glyphs = new RenderNode[rows];
        final int kept = Math.min(mBackgrounds.length, rows);
        System.arraycopy(mBackgrounds, 0, backgrounds, 0, kept);
        System.arraycopy(mGlyphs, 0, glyphs, 0, kept);
        for (int i = kept; i < mBackgrounds.length; i++) {
            mBackgrounds[i].discardDisplayList();
            mGlyphs[i].discardDisplayList();
        }
        for (int i = kept; i < rows; i++) {
            backgrounds[i] = new RenderNode("TerminalRowBackground");
            glyphs[i] = new RenderNode("TerminalRowGlyphs");
        }
        mBackgrounds = backgrounds;
        mGlyphs = glyphs;
    }

    RenderNode background(int row) {
        return mBackgrounds[row];
    }

    RenderNode glyphs(int row) {
        return mGlyphs[row];
    }

    int size() {
        return mBackgrounds.length;
    }

    /** Drop every display list held, for when the renderer behind them is replaced. */
    void discard() {
        for (RenderNode node : mBackgrounds) node.discardDisplayList();
        for (RenderNode node : mGlyphs) node.discardDisplayList();
        mBackgrounds = NONE;
        mGlyphs = NONE;
    }
}
