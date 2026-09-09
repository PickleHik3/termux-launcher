package com.termux.view;

import android.graphics.RenderNode;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * The three {@link RenderNode}s each visible row is recorded into, and nothing else.
 *
 * <p>Three per row, not one, because the render loop paints every row's cell backgrounds and cursor
 * block before it paints any glyph: ink that overhangs its cell — Nerd Font symbols routinely
 * overhang — has to land on the next row's fill rather than under it. Replaying one node per row
 * would put each row's background back on top of the row above's glyphs. So the backgrounds are
 * replayed as one pass and the glyphs as another, exactly as they are drawn.
 *
 * <p>The third node is the reason the other two can be cached at all on a row showing an image. A
 * kitty placeholder or a sixel cell draws from pixels that are replaced without the row's text ever
 * moving, so such a row used to be re-shaped and re-measured on every animation frame. Splitting
 * the image draws out means only this node is re-recorded then, and it is replayed last — over the
 * glyphs — which is the order kitty paints in and the order the direct path already used.
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
    private RenderNode[] mImages = NONE;

    /**
     * Hold exactly this many rows, dropping the display list of every row that no longer exists.
     * A pane that shrinks must not keep the recordings of the rows it lost.
     */
    void resize(int rows) {
        if (mBackgrounds.length == rows) return;
        RenderNode[] backgrounds = new RenderNode[rows];
        RenderNode[] glyphs = new RenderNode[rows];
        RenderNode[] images = new RenderNode[rows];
        final int kept = Math.min(mBackgrounds.length, rows);
        System.arraycopy(mBackgrounds, 0, backgrounds, 0, kept);
        System.arraycopy(mGlyphs, 0, glyphs, 0, kept);
        System.arraycopy(mImages, 0, images, 0, kept);
        for (int i = kept; i < mBackgrounds.length; i++) {
            mBackgrounds[i].discardDisplayList();
            mGlyphs[i].discardDisplayList();
            mImages[i].discardDisplayList();
        }
        for (int i = kept; i < rows; i++) {
            backgrounds[i] = new RenderNode("TerminalRowBackground");
            glyphs[i] = new RenderNode("TerminalRowGlyphs");
            images[i] = new RenderNode("TerminalRowImages");
        }
        mBackgrounds = backgrounds;
        mGlyphs = glyphs;
        mImages = images;
    }

    RenderNode background(int row) {
        return mBackgrounds[row];
    }

    RenderNode glyphs(int row) {
        return mGlyphs[row];
    }

    RenderNode images(int row) {
        return mImages[row];
    }

    int size() {
        return mBackgrounds.length;
    }

    /** Drop every display list held, for when the renderer behind them is replaced. */
    void discard() {
        for (RenderNode node : mBackgrounds) node.discardDisplayList();
        for (RenderNode node : mGlyphs) node.discardDisplayList();
        for (RenderNode node : mImages) node.discardDisplayList();
        mBackgrounds = NONE;
        mGlyphs = NONE;
        mImages = NONE;
    }
}
