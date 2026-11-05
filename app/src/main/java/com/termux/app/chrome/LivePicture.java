package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;

/**
 * The live route's picture: what the window draws beneath one glass surface, blurred, drawn by
 * that surface's backdrop view. {@link LiveBackdrop} hands one out per surface and re-records it.
 *
 * <p>Two nodes hold it. The <em>record</em> node is {@link LiveBackdrop#recordBelow}'s recording,
 * in the surface's own coordinates plus a margin of one blur radius on every side (so the blur
 * at the glass's edge has real pixels to reach). The <em>blur</em> node draws the record node at a
 * quarter of its size and carries {@link RenderEffect#createBlurEffect} at a quarter of the radius
 * (and the frost colour filter, chained after it); it is the one GPU layer this picture costs. The
 * drawable draws the blur node back at four times, aimed so the recording's origin lands on the
 * surface's. Both nodes are referenced, not copied: when the record node is re-recorded or anything
 * it references redraws, the frame being drawn picks it up without anyone invalidating the
 * surface.</p>
 *
 * <p>Nodes are made on the first recording and dropped by {@link #release}, so a picture that is
 * handed out but never drawn holds no pixels.</p>
 */
public final class LivePicture implements GlassPicture {

    /** The blur layer's scale against the recording: a quarter on each side, a sixteenth of the pixels. */
    public static final int SCALE = 4;

    @NonNull final View mSurface;
    @NonNull private final LiveDrawable mDrawable = new LiveDrawable();
    private float mRadiusPx;
    private int mMarginPx;
    /** The radius is a panel's {@code RealtimeBlurView} radius, not the stored frame's. */
    private boolean mPanelRadius;
    @Nullable private ColorFilter mFilter;
    /** The wall's dim the last recording was made under ({@link LiveStoredParity}). */
    private int mParityDim;
    /** The Vibrancy and frame tint the stored frames are baked at, which this picture matches. */
    private int mParityVibrancy = 100;
    private int mParityFrameTint = GlassFrameTint.NONE;
    /** {@link LiveStoredParity#matrix} for the three above, or null; rebuilt only when one moves. */
    @Nullable private ColorFilter mParityFilter;

    @Nullable private RenderNode mRecord;
    @Nullable private RenderNode mBlur;
    private int mBlurWidth = -1;
    private int mBlurHeight = -1;

    /** What the last recording was made from; a pre-draw that finds the same skips re-recording. */
    @NonNull final ArrayList<View> mRecordedBeneath = new ArrayList<>();
    @NonNull final Matrix mRecordedMatrix = new Matrix();
    int mRecordedWidth = -1;
    int mRecordedHeight = -1;
    int mRecordedMargin = -1;
    /** The scene frame generation the recording drew (0: none); a new one re-records. */
    int mRecordedSceneGen;
    /** Its place in the budget's order ({@link LiveBackdrop#RANK_CHROME}); set by the recorder. */
    int mRank = LiveBackdrop.RANK_CHROME;
    /** Set once {@link #release} has dropped the nodes: the picture draws nothing ever again. */
    private boolean mReleased;
    /**
     * Whether the nodes hold a whole picture: a recording, and the blur node drawing it. Set by
     * the recorder's first recording ({@link #recorded}), cleared when the nodes are emptied.
     */
    private boolean mReady;
    /** The nodes {@link #release} let go, emptied a frame later by {@link #mDiscardRetired}. */
    @Nullable private RenderNode mRetiredRecord;
    @Nullable private RenderNode mRetiredBlur;
    /**
     * Empties the released nodes once the holder has drawn its stored frame in their place: the
     * frame the holder last recorded may still draw them, so they keep their pictures until the
     * next frame.
     */
    @NonNull private final Runnable mDiscardRetired = this::discardRetired;

    LivePicture(@NonNull View surface, float blurRadiusPx, boolean panelRadius) {
        mSurface = surface;
        mPanelRadius = panelRadius;
        setBlurRadiusPx(blurRadiusPx);
    }

    /**
     * Whether this picture can be shown now: recorded and blurred at least once since it was
     * made or last emptied, and not released. A holder draws its stored frame until it is, and
     * the drawable invalidates its holder the moment it becomes so ({@link #recorded}).
     */
    public boolean ready() {
        return mReady && !mReleased;
    }

    /**
     * The recorder has just recorded this picture whole (inside a pre-draw, before the frame is
     * drawn). The first time since it was empty, the holder is invalidated so this same frame
     * swaps its stored frame for the picture.
     */
    void recorded() {
        if (mReady || mReleased) return;
        mReady = true;
        mDrawable.invalidateSelf();
    }

    @NonNull
    @Override
    public Drawable drawable() {
        return mDrawable;
    }

    /** A live picture has no stored frame; the recording is its frame. */
    @Override
    public void setFrame(@NonNull Bitmap frame, @NonNull Rect frameRect, boolean crossfade) {}

    /**
     * What the stored frame bakes that a recording does not have ({@link LiveStoredParity}): the
     * wall's dim the recording carries, to be taken back out, and the Vibrancy and frame tint the
     * stored frames are baked at. A panel's picture matches its old {@code RealtimeBlurView}, which
     * recorded the window as this does, so it takes none of it. Rebuilds the effect only on a change.
     */
    void setStoredParity(int dimColor, int vibrancyPercent, int frameTint) {
        if (mPanelRadius) return;
        if (dimColor == mParityDim && vibrancyPercent == mParityVibrancy
            && frameTint == mParityFrameTint) return;
        mParityDim = dimColor;
        mParityVibrancy = vibrancyPercent;
        mParityFrameTint = frameTint;
        rebuildParity();
    }

    /** {@link #setStoredParity}'s dim alone: the recorder reads it from what it records. */
    void setRecordedDim(int dimColor) {
        setStoredParity(dimColor, mParityVibrancy, mParityFrameTint);
    }

    private void rebuildParity() {
        float[] matrix = LiveStoredParity.matrix(mParityDim, mParityVibrancy, mParityFrameTint,
            mRadiusPx > 0f);
        mParityFilter = matrix == null ? null : new android.graphics.ColorMatrixColorFilter(matrix);
        if (mBlur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) applyEffect();
    }

    /** The parity filter this picture's effect carries now, or null; for tests. */
    @Nullable ColorFilter parityFilter() {
        return mParityFilter;
    }

    /** The blur radius at full scale, in px; the next recording follows it. */
    public void setBlurRadiusPx(float radiusPx) {
        float radius = Math.max(0f, radiusPx);
        if (radius == mRadiusPx && mMarginPx > 0) return;
        boolean blursMoved = (radius > 0f) != (mRadiusPx > 0f);
        mRadiusPx = radius;
        mMarginPx = Math.max(1, (int) Math.ceil(radius));
        forgetRecording();
        if (blursMoved && !mPanelRadius) rebuildParity();
        else if (mBlur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) applyEffect();
    }

    /**
     * Whether the recorder has let this picture go (Live off, the surface detached, memory short):
     * it draws nothing any more, and its holder goes back to the stored frame until it is handed a
     * new one.
     */
    public boolean released() {
        return mReleased;
    }

    /** The surface this picture shows what is beneath. */
    @NonNull
    public View surface() {
        return mSurface;
    }

    int marginPx() {
        return mMarginPx;
    }

    /** Whether {@code drawable} is this picture's. */
    boolean owns(@Nullable Drawable drawable) {
        return drawable == mDrawable;
    }

    /** The node a recording goes into, sized to {@code width} x {@code height}, made on first use. */
    @RequiresApi(Build.VERSION_CODES.S)
    @NonNull
    RenderNode recordNode(int width, int height) {
        if (mRecord == null) mRecord = new RenderNode("LiveBackdrop.record");
        mRecord.setPosition(0, 0, width, height);
        RenderNode record = mRecord;
        ensureBlurNode(width, height);
        return record;
    }

    /** Re-cuts the quarter-scale blur node when the recording changes size; else it stands as is. */
    @RequiresApi(Build.VERSION_CODES.S)
    private void ensureBlurNode(int width, int height) {
        int w = Math.max(1, (width + SCALE - 1) / SCALE);
        int h = Math.max(1, (height + SCALE - 1) / SCALE);
        boolean made = mBlur == null;
        if (made) mBlur = new RenderNode("LiveBackdrop.blur");
        if (!made && w == mBlurWidth && h == mBlurHeight && mBlur.hasDisplayList()) return;
        mBlurWidth = w;
        mBlurHeight = h;
        mBlur.setPosition(0, 0, w, h);
        if (made) applyEffect();
        RecordingCanvas canvas = mBlur.beginRecording(w, h);
        try {
            canvas.scale(1f / SCALE, 1f / SCALE);
            canvas.drawRenderNode(mRecord);
        } finally {
            mBlur.endRecording();
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void applyEffect() {
        if (mBlur == null) return;
        float r = mPanelRadius ? panelQuarterRadius(mRadiusPx) : renderEffectRadius(mRadiusPx) / SCALE;
        RenderEffect effect = r > 0f
            ? RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) : null;
        // The stored frame's colours (the dim out, Vibrancy and the frame tint in), then the frost.
        if (mParityFilter != null) {
            RenderEffect parity = RenderEffect.createColorFilterEffect(mParityFilter);
            effect = effect == null ? parity : RenderEffect.createChainEffect(parity, effect);
        }
        if (mFilter != null) {
            RenderEffect tint = RenderEffect.createColorFilterEffect(mFilter);
            effect = effect == null ? tint : RenderEffect.createChainEffect(tint, effect);
        }
        mBlur.setRenderEffect(effect);
    }

    /**
     * The {@code RenderEffect} blur radius, at full scale, that blurs as much as the stored frame
     * does at {@code storedRadiusPx}, so switching Live on keeps the glass's strength. The stored
     * frame blurs with RenderScript's intrinsic (sigma = 0.4 r + 0.6 at its downsample factor f,
     * {@code WallpaperBlurRenderer}), so 0.4 r + 0.6 f full-scale; Skia reads a RenderEffect radius
     * as sigma = 0.57735 r + 0.5, here on a 1/{@link #SCALE} node, so 0.57735 R + 0.5 SCALE.
     * Pure.
     */
    static float renderEffectRadius(float storedRadiusPx) {
        if (storedRadiusPx <= 0f) return 0f;
        float f = Math.max(1f, (float) Math.ceil(storedRadiusPx / STORED_SCRIPT_CAP_PX));
        float sigma = STORED_SIGMA_SLOPE * storedRadiusPx + STORED_SIGMA_BASE * f;
        return Math.max(0f, (sigma - SKIA_SIGMA_BASE * SCALE) / SKIA_SIGMA_SLOPE);
    }

    /**
     * The blur radius on the quarter-scale node that blurs as much as a panel's old
     * {@code RealtimeBlurView} at {@code panelRadiusPx}, so a panel keeps its strength. On Android
     * 12+ that view drew the window at a quarter scale, scaled it back up and ran a full-scale
     * {@link RenderEffect} at the radius itself (sigma = 0.57735 R + 0.5); here the effect runs on a
     * 1/{@link #SCALE} node, where sigma counts {@link #SCALE} times: 0.57735 r + 0.5 = sigma /
     * SCALE. Both sides downsample and upscale the same way, so only the blur is matched. Pure.
     */
    static float panelQuarterRadius(float panelRadiusPx) {
        if (panelRadiusPx <= 0f) return 0f;
        float sigma = SKIA_SIGMA_SLOPE * panelRadiusPx + SKIA_SIGMA_BASE;
        return Math.max(0f, (sigma / SCALE - SKIA_SIGMA_BASE) / SKIA_SIGMA_SLOPE);
    }

    /**
     * The RenderScript radius that blurs a software Snapshot (below Android 12) as much as the GPU
     * Snapshot blurs a panel at {@code panelRadiusPx}, on a bitmap at 1/{@code scale}. The GPU path
     * comes to sigma = 0.57735 R + 0.5 at full scale ({@link #panelQuarterRadius}), so sigma /
     * {@code scale} on the bitmap; RenderScript's intrinsic reads a radius as sigma = 0.4 r + 0.6,
     * as {@link #renderEffectRadius} calibrates against the stored frame. 0 for no blur. Pure.
     */
    static float softwareScriptRadius(float panelRadiusPx, int scale) {
        if (panelRadiusPx <= 0f) return 0f;
        float sigma = (SKIA_SIGMA_SLOPE * panelRadiusPx + SKIA_SIGMA_BASE) / Math.max(1, scale);
        return Math.max(0f, (sigma - STORED_SIGMA_BASE) / STORED_SIGMA_SLOPE);
    }

    /**
     * A software Snapshot's downsample for a {@code width} x {@code height} recording: a quarter,
     * as the GPU Snapshot, or coarser when RenderScript's {@link #STORED_SCRIPT_CAP_PX} cannot
     * reach the radius there, or the bitmap would pass {@link #SOFTWARE_SNAPSHOT_MAX_BITMAP_BYTES}.
     * Pure.
     */
    static int softwareScale(float panelRadiusPx, int width, int height) {
        int scale = SCALE;
        while (scale < MAX_SOFTWARE_SCALE
            && (softwareScriptRadius(panelRadiusPx, scale) > STORED_SCRIPT_CAP_PX
                || softwareBitmapBytes(width, height, scale) > SOFTWARE_SNAPSHOT_MAX_BITMAP_BYTES)) {
            scale++;
        }
        return scale;
    }

    /** Bytes of a software Snapshot's ARGB bitmap over a {@code width} x {@code height} recording at 1/{@code scale}. */
    static long softwareBitmapBytes(int width, int height, int scale) {
        long w = Math.max(1, (width + scale - 1) / scale);
        long h = Math.max(1, (height + scale - 1) / scale);
        return w * h * 4L;
    }

    /** One software Snapshot's bitmap at most: a budget screen at a quarter, as one live layer. */
    static final long SOFTWARE_SNAPSHOT_MAX_BITMAP_BYTES = LiveBackdrop.MAX_LAYER_BYTES;

    /**
     * One open panel's software Snapshot at most: its bitmap, and RenderScript's input and output
     * allocations of the same size, all three held while the panel is open and given back on close.
     */
    public static final long SOFTWARE_SNAPSHOT_BUDGET_BYTES = 3 * SOFTWARE_SNAPSHOT_MAX_BITMAP_BYTES;

    /** A bound on {@link #softwareScale}'s search; nothing real comes near it. */
    static final int MAX_SOFTWARE_SCALE = 64;

    /** RenderScript's intrinsic blur: sigma = 0.4 r + 0.6, radius capped at 25 px. */
    static final float STORED_SIGMA_SLOPE = 0.4f;
    static final float STORED_SIGMA_BASE = 0.6f;
    static final float STORED_SCRIPT_CAP_PX = 25f;
    /** Skia's radius to sigma: 0.57735 r + 0.5. */
    static final float SKIA_SIGMA_SLOPE = 0.57735f;
    static final float SKIA_SIGMA_BASE = 0.5f;

    /** Lets the next pre-draw re-record whatever it finds. */
    void forgetRecording() {
        mRecordedBeneath.clear();
        mRecordedWidth = -1;
        mRecordedHeight = -1;
        mRecordedMargin = -1;
    }

    /**
     * Drops both nodes' display lists, and with them the blur layer (a node with nothing to draw
     * gives its layer back on the next frame), but keeps the nodes: the surface's own display list
     * still references the blur node, so when the surface shows again and the next recording
     * fills it, the picture is back without the surface being redrawn. For a surface that hid.
     */
    void trim() {
        mReady = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mBlur != null && mBlur.hasDisplayList()) mBlur.discardDisplayList();
            if (mRecord != null && mRecord.hasDisplayList()) mRecord.discardDisplayList();
        }
        mBlurWidth = -1;
        mBlurHeight = -1;
        forgetRecording();
    }

    /**
     * {@link #trim} and the nodes too: the picture is done with (Live off, memory short, the
     * holder let go). The holder draws its stored frame from its next draw on, but the frame it
     * recorded last may still draw these nodes, so while it is on screen they are emptied a frame
     * later rather than now; emptied now, that frame would show the glass with nothing in it.
     */
    void release() {
        if (mReleased) return;
        mReleased = true;
        mReady = false;
        if (mSurface.isAttachedToWindow() && (mBlur != null || mRecord != null)) {
            mRetiredBlur = mBlur;
            mRetiredRecord = mRecord;
            mBlurWidth = -1;
            mBlurHeight = -1;
            forgetRecording();
            mSurface.postOnAnimation(mDiscardRetired);
        } else {
            trim();
        }
        mBlur = null;
        mRecord = null;
    }

    private void discardRetired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mRetiredBlur != null && mRetiredBlur.hasDisplayList()) mRetiredBlur.discardDisplayList();
            if (mRetiredRecord != null && mRetiredRecord.hasDisplayList()) {
                mRetiredRecord.discardDisplayList();
            }
        }
        mRetiredBlur = null;
        mRetiredRecord = null;
    }

    /** Whether {@link #release} still holds nodes it will empty on the next frame. Tests. */
    boolean retiring() {
        return mRetiredBlur != null || mRetiredRecord != null;
    }

    /** Whether the blur layer can exist now: what the budget counts. */
    boolean holdsLayer() {
        return mBlur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mBlur.hasDisplayList();
    }

    /** Whether a view still draws this picture: a holder clears the callback when it lets go. */
    boolean held() {
        return mDrawable.getCallback() != null;
    }

    /** Draws the blur node at four times, the recording's origin on the surface's. */
    private final class LiveDrawable extends Drawable {
        private int mAlpha = 255;

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
            RenderNode blur = mBlur;
            // A software canvas (a screenshot of the view) cannot draw a node: nothing, not a crash.
            if (blur == null || !blur.hasDisplayList() || !(canvas instanceof RecordingCanvas)) return;
            int save = canvas.save();
            // The holder (an ImageView) has already moved the canvas to its content box.
            canvas.translate(-mMarginPx - mSurface.getPaddingLeft(),
                -mMarginPx - mSurface.getPaddingTop());
            canvas.scale(SCALE, SCALE);
            ((RecordingCanvas) canvas).drawRenderNode(blur);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
        }

        @Override
        public int getAlpha() {
            return mAlpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            if (colorFilter == mFilter) return;
            mFilter = colorFilter;
            if (mBlur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) applyEffect();
        }

        @Nullable
        @Override
        public ColorFilter getColorFilter() {
            return mFilter;
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
