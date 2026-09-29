package com.termux.app.terminal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.view.TerminalView;

/**
 * A frozen copy of one pane frame, kept rounded.
 *
 * <p>A copy is drawn without its root's own outline: the live frame is cut to its rounded shape by
 * the renderer (clip to outline), and neither a display-list recording nor a bitmap of the frame
 * carries that cut. The glass, tint, grain and rim inside are all rectangles, so an uncut copy
 * showed square corners around a rounded slab. Both routes therefore cut the copy at the frame's
 * own radius, and neither adds a ground, plate or shadow of its own.
 *
 * <p>Two routes, chosen by {@link #route}. On a hardware window (API 29+) the frame is recorded
 * into a {@link android.graphics.RenderNode}: it costs the draw ops and no pixels, and the
 * refraction program (a RuntimeShader, which a software canvas refuses) survives. Otherwise it is
 * a bitmap, on which the glass draws its plain frost and the copy is clipped to the same rounded
 * rect when drawn. A copy that moves must not carry its own aim over the wallpaper: this one is
 * only ever held in place (the split reveal clips it, never translates it).
 */
final class PaneSnapshot {

    /** How a copy is held. */
    enum Route { RENDER_NODE, BITMAP }

    /** Pure choice, so the API-level fallback can be asserted without a device. */
    static Route route(int sdkInt, boolean hardwareAccelerated) {
        return sdkInt >= Build.VERSION_CODES.Q && hardwareAccelerated
            ? Route.RENDER_NODE : Route.BITMAP;
    }

    @Nullable private final Bitmap mBitmap;
    /** A {@code RenderNode}, held as Object so this class loads below API 29. */
    @Nullable private final Object mNode;
    private final int mWidth;
    private final int mHeight;
    /** 0 for a frame that does not clip to a shape. */
    private final float mRadiusPx;
    @NonNull private final Path mCut = new Path();

    private PaneSnapshot(@Nullable Bitmap bitmap, @Nullable Object node, int width, int height,
                         float radiusPx) {
        mBitmap = bitmap;
        mNode = node;
        mWidth = width;
        mHeight = height;
        mRadiusPx = radiusPx;
    }

    /**
     * Copies {@code frame} as it is drawn right now, or null when there is nothing to copy (not
     * laid out) or no memory for a bitmap.
     *
     * @param requestedRadiusPx the pane radius before it is capped against this frame's size
     * @param terminal the pane's terminal, told to record its glyphs rather than its row nodes
     *     (those are re-recorded the moment the pane reflows); null if there is none
     */
    @Nullable
    static PaneSnapshot capture(@NonNull View frame, float requestedRadiusPx,
                                @Nullable TerminalView terminal) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        if (!frame.isLaidOut() || width <= 0 || height <= 0) return null;
        float radius = frame.getClipToOutline()
            ? PaneShape.radiusForBounds(requestedRadiusPx, width, height) : 0f;
        if (route(Build.VERSION.SDK_INT, frame.isHardwareAccelerated()) == Route.RENDER_NODE) {
            android.graphics.RenderNode node = new android.graphics.RenderNode("PaneSnapshot");
            node.setPosition(0, 0, width, height);
            if (radius > 0f) {
                Outline outline = new Outline();
                outline.setRoundRect(0, 0, width, height, radius);
                node.setOutline(outline);
                node.setClipToOutline(true);
            }
            if (terminal != null) terminal.setRowCacheBypassed(true);
            try {
                Canvas recording = node.beginRecording(width, height);
                try {
                    frame.draw(recording);
                } finally {
                    node.endRecording();
                }
            } finally {
                if (terminal != null) terminal.setRowCacheBypassed(false);
            }
            return new PaneSnapshot(null, node, width, height, radius);
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            frame.draw(new Canvas(bitmap));
            return new PaneSnapshot(bitmap, null, width, height, radius);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** Draws at the canvas origin; the caller has translated to the pane's bounds. */
    void draw(@NonNull Canvas canvas) {
        if (mNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            && canvas.isHardwareAccelerated()) {
            canvas.drawRenderNode((android.graphics.RenderNode) mNode);
        } else if (mBitmap != null && !mBitmap.isRecycled()) {
            int save = canvas.save();
            if (mRadiusPx > 0f) canvas.clipPath(cut());
            canvas.drawBitmap(mBitmap, 0f, 0f, null);
            canvas.restoreToCount(save);
        }
    }

    /** The rounded shape the copy is cut to on the bitmap route; built once. */
    @NonNull
    private Path cut() {
        if (mCut.isEmpty())
            mCut.addRoundRect(new RectF(0f, 0f, mWidth, mHeight), mRadiusPx, mRadiusPx,
                Path.Direction.CW);
        return mCut;
    }

    /** The radius the copy is cut at, 0 when it is square. */
    float radiusPx() {
        return mRadiusPx;
    }

    /** True when the copy is held as a display list. */
    boolean isRenderNode() {
        return mNode != null;
    }

    void release() {
        if (mBitmap != null) mBitmap.recycle();
        if (mNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ((android.graphics.RenderNode) mNode).discardDisplayList();
    }
}
