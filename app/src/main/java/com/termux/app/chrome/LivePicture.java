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
    @NonNull private final LiveDrawable mDrawable = new LiveDrawable(false);
    /**
     * A second holder's drawable ({@link #aimedDrawable}): the same blur node, aimed at another
     * view's place. A float's titlebar shows the float's one picture through it.
     */
    @NonNull private final LiveDrawable mAimed = new LiveDrawable(true);
    /** Where the second holder's origin stands against the surface's, in px; see {@link #aimAt}. */
    private int mAimedX;
    private int mAimedY;
    /** How far above the surface the recording reaches, in px; see {@link #setReachTop}. */
    private int mReachTopPx;
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
    int mRecordedReach = -1;
    /** The scene frame generation the recording drew (0: none); a new one re-records. */
    int mRecordedSceneGen;
    /** Its place in the budget's order ({@link LiveBackdrop#RANK_CHROME}); set by the recorder. */
    int mRank = LiveBackdrop.RANK_CHROME;
    /** The parallax offset the one picture was recorded at. */
    float mRecordedParallax;

    // ---- Backdrop layers ({@link BackdropLayers}) ------------------------------------------
    /**
     * While the wall moves: the layers composited in one draw, bottom first: {@link #mGround},
     * {@link #mPagesAt} and the blur node, which then holds only the local layer. Drawn by both
     * holders in the blur node's place while {@link #mLayered}.
     */
    @Nullable private RenderNode mShown;
    /** The ground in the root's coordinates, aimed at the surface by its animation matrix. */
    @Nullable private RenderNode mGround;
    /** Over a live scene: the scene's frame over {@link #mSceneRegion}, blurred as the blur node is. */
    @Nullable private RenderNode mSceneGround;
    /** Draws the page node at full scale, aimed by its animation matrix. */
    @Nullable private RenderNode mPagesAt;
    /** The holders draw {@link #mShown}. */
    private boolean mLayered;
    /** {@link #mShown} draws a pages layer. */
    private boolean mShownPages;
    /** The record node holds the local layer only, not the one picture. */
    boolean mRecordHoldsLocal;
    /** The views the local layer was recorded from, and where the surface stood then. */
    @NonNull final ArrayList<View> mRecordedLocal = new ArrayList<>();
    @NonNull final Matrix mLocalMatrix = new Matrix();
    /** What {@link #mGround} draws now: the frame, its rect in the root, the scene's publish. */
    @Nullable private Bitmap mGroundFrame;
    @NonNull private final Rect mGroundRect = new Rect();
    @NonNull final Rect mSceneRegion = new Rect();
    private int mGroundGen = -1;
    @Nullable private ColorFilter mGroundFilter;
    @NonNull private final android.graphics.Paint mGroundPaint =
        new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
    /** The page layer's aim now, in px; pages that have not moved leave the one picture up. */
    float mPageAimPx;

    // ---- The page layer ({@link BackdropLayers}) -------------------------------------
    /**
     * The wall's pages on their own, at a quarter scale, drawn by the record node in the wall's
     * place: a layer over the pages as they are ({@code REFERENCE}), or a node drawing
     * {@link #mFrozen} ({@code FROZEN}). Empty in {@code LIVE}.
     */
    @Nullable private RenderNode mPage;
    /** The pages pictured as the swipe started; held only while the wall moves. */
    @Nullable private Bitmap mFrozen;
    @Nullable private Bitmap mRetiredFrozen;
    /** The capture of {@link #mFrozen} under way ({@link PageLayerCapture}); Android 10+. */
    @Nullable private Object mCapture;
    /** The node a capture renders; kept between swipes so a swipe allocates no node. */
    @Nullable private RenderNode mCaptureRoot;
    /** What the record node was last recorded as: one layer, or around the page layer. */
    @NonNull BackdropLayers.Mode mRecordedMode = BackdropLayers.Mode.LIVE;
    /** What the page layer holds now. */
    @NonNull BackdropLayers.Mode mPageMode = BackdropLayers.Mode.LIVE;
    /** The pages the page layer was recorded from, and their poses then. */
    @NonNull final ArrayList<View> mRecordedPages = new ArrayList<>();
    @NonNull float[] mRecordedPoses = new float[BackdropLayers.POSE_STRIDE * 4];
    int mRecordedPoseCount = -1;
    /** Captures this swipe has started ({@link LiveBackdrop#MAX_CAPTURES_PER_SWIPE}). */
    int mSwipeCaptures;
    /** Where the page layer stands in the wall's coordinates, and what it was recorded to hold. */
    @NonNull final Rect mPageRect = new Rect();
    @NonNull final android.graphics.RectF mPageReach = new android.graphics.RectF();
    /** The views drawn around the page layer, and the wall's place, as the record node has them. */
    @NonNull final ArrayList<View> mRecordedExpand = new ArrayList<>();
    @NonNull final Matrix mRecordedWallMatrix = new Matrix();
    /** Recycles {@link #mRetiredFrozen} a frame after the record node stopped drawing it. */
    @NonNull private final Runnable mRecycleFrozen = this::recycleRetiredFrozen;
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
    @Nullable private RenderNode mRetiredPage;
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
        mAimed.invalidateSelf();
    }

    /**
     * Records {@code px} more of what is beneath, above the surface's top, so a second holder
     * standing there ({@link #aimAt}) has real pixels. Zero when there is none. Re-records on a
     * change only.
     */
    public void setReachTop(int px) {
        int reach = Math.max(0, px);
        if (reach == mReachTopPx) return;
        mReachTopPx = reach;
        forgetRecording();
    }

    int reachTopPx() {
        return mReachTopPx;
    }

    /**
     * The second holder's origin against the surface's, in px: negative above or left of it. Its
     * drawable draws the same picture where that holder stands, without recording again.
     */
    public void aimAt(int originX, int originY) {
        if (originX == mAimedX && originY == mAimedY) return;
        mAimedX = originX;
        mAimedY = originY;
        mAimed.invalidateSelf();
    }

    /** The drawable for a second holder: the one picture, aimed by {@link #aimAt}. */
    @NonNull
    public Drawable aimedDrawable() {
        return mAimed;
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
        return drawable == mDrawable || drawable == mAimed;
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
        mBlur.setRenderEffect(layerEffect());
        if (mSceneGround != null) mSceneGround.setRenderEffect(layerEffect());
    }

    /** The blur node's effect: the blur, then the stored frame's colours, then the frost. */
    @RequiresApi(Build.VERSION_CODES.S)
    @Nullable
    private RenderEffect layerEffect() {
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
        return effect;
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
        mRecordedReach = -1;
        mRecordedPoseCount = -1;
    }

    // ---- The page layer --------------------------------------------------------------------

    /**
     * The page layer's node over {@code rect} (wall coordinates, full scale), sized to its quarter
     * and cut to a layer of its own when {@code layered}; made on first use. Its aim goes back to
     * none.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @NonNull
    RenderNode pageNode(@NonNull Rect rect, boolean layered) {
        if (mPage == null) mPage = new RenderNode("LiveBackdrop.page");
        mPage.setPosition(0, 0, quarter(rect.width()), quarter(rect.height()));
        mPage.setUseCompositingLayer(layered, null);
        mPage.setTranslationX(0f);
        mPageRect.set(rect);
        return mPage;
    }

    /** The node a capture of the page layer renders, sized to its quarter; kept for the next. */
    @RequiresApi(Build.VERSION_CODES.S)
    @NonNull
    RenderNode captureRoot(@NonNull Rect rect) {
        if (mCaptureRoot == null) mCaptureRoot = new RenderNode("LiveBackdrop.pageCapture");
        if (mCaptureContent == null) mCaptureContent = new RenderNode("LiveBackdrop.pageCaptureBlur");
        int w = quarter(rect.width());
        int h = quarter(rect.height());
        mCaptureRoot.setPosition(0, 0, w, h);
        mCaptureContent.setPosition(0, 0, w, h);
        // The pages layer is drawn beside the ground, not under the blur node: it is captured
        // blurred, with the stored frame's colours and the frost, as the blur node draws.
        mCaptureContent.setRenderEffect(layerEffect());
        RecordingCanvas canvas = mCaptureRoot.beginRecording(w, h);
        try {
            canvas.drawRenderNode(mCaptureContent);
        } finally {
            mCaptureRoot.endRecording();
        }
        return mCaptureContent;
    }

    /** What a capture records into, under the blur ({@link #captureRoot}). */
    @Nullable private RenderNode mCaptureContent;

    /** {@code px} at the page layer's quarter scale, rounded up, at least 1. */
    static int quarter(int px) {
        return Math.max(1, (px + SCALE - 1) / SCALE);
    }

    /**
     * Starts a capture of {@link #captureRoot}, recorded over {@code rect}: the swipe's frozen
     * page layer, collected by {@link #collectCapture}. False when it could not start.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    boolean startCapture(@NonNull Rect rect) {
        if (mCaptureRoot == null) return false;
        PageLayerCapture capture = mCapture instanceof PageLayerCapture
            ? (PageLayerCapture) mCapture : new PageLayerCapture();
        mCapture = capture;
        mPageRect.set(rect);
        return capture.start(mCaptureRoot, quarter(rect.width()), quarter(rect.height()));
    }

    /** Whether a capture of the page layer is under way. */
    boolean capturing() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mCapture instanceof PageLayerCapture
            && ((PageLayerCapture) mCapture).pending();
    }

    /**
     * The capture under way, once it has landed: the page layer then draws it, frozen. False while
     * it is still rendering or when it failed ({@link #capturing} tells which).
     */
    @RequiresApi(Build.VERSION_CODES.S)
    boolean collectCapture() {
        if (!(mCapture instanceof PageLayerCapture)) return false;
        Bitmap bitmap = ((PageLayerCapture) mCapture).poll();
        if (bitmap == null) return false;
        retireFrozen();
        mFrozen = bitmap;
        RenderNode page = pageNode(mPageRect, false);
        RecordingCanvas canvas = page.beginRecording(quarter(mPageRect.width()),
            quarter(mPageRect.height()));
        try {
            canvas.drawBitmap(bitmap, 0f, 0f, null);
        } finally {
            page.endRecording();
        }
        mPageMode = BackdropLayers.Mode.FROZEN;
        // The capture's recording is done with; the bitmap is all that is held.
        if (mCaptureContent != null && mCaptureContent.hasDisplayList()) {
            mCaptureContent.discardDisplayList();
        }
        return true;
    }

    /** Moves the page layer by {@code dxPx} in the wall's coordinates. */
    @RequiresApi(Build.VERSION_CODES.S)
    void aimPage(float dxPx) {
        mPageAimPx = dxPx;
        if (mPage != null) mPage.setTranslationX(dxPx / SCALE);
    }

    // ---- Backdrop layers -------------------------------------------------------------------

    /** The scene publish the ground holds, -1 for the stored frame or none. */
    int groundGeneration() {
        return mGroundGen;
    }

    /** Whether the holders draw the layers now, not the one picture. */
    boolean layered() {
        return mLayered;
    }

    /** Whether this is a panel's picture: panels never take backdrop layers. */
    boolean panelRadius() {
        return mPanelRadius;
    }

    /** The blur radius at full scale, in px. */
    float radiusPx() {
        return mRadiusPx;
    }

    /**
     * The ground as the stored frame: {@code frame} over {@code rectInRoot}, drawn with the frost
     * as the stored route draws it (the frame already carries its blur and colours). Recorded
     * again only when the frame, its rect or the frost moved.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    void groundStored(@NonNull Bitmap frame, @NonNull Rect rectInRoot) {
        RenderNode ground = groundNode();
        if (frame == mGroundFrame && mGroundGen == -1 && rectInRoot.equals(mGroundRect)
            && mGroundFilter == mFilter && ground.hasDisplayList()) return;
        mGroundFrame = frame;
        mGroundGen = -1;
        mGroundRect.set(rectInRoot);
        mGroundFilter = mFilter;
        mGroundPaint.setColorFilter(mFilter);
        ground.setPosition(rectInRoot);
        RecordingCanvas canvas = ground.beginRecording(rectInRoot.width(), rectInRoot.height());
        try {
            mGroundDest.set(0, 0, rectInRoot.width(), rectInRoot.height());
            canvas.drawBitmap(frame, null, mGroundDest, mGroundPaint);
        } finally {
            ground.endRecording();
        }
    }

    @NonNull private final Rect mGroundDest = new Rect();

    /**
     * The ground over a live scene: its frame of {@code generation} over {@code sceneInRoot},
     * blurred over {@code region} (root coordinates) as the one picture blurs it, so the hand-off
     * keeps the look. Recorded again for a new frame or a region it does not hold.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    void groundScene(@NonNull Bitmap frame, int generation, @NonNull Rect sceneInRoot,
                     @NonNull Rect region) {
        RenderNode ground = groundNode();
        if (frame == mGroundFrame && generation == mGroundGen && sceneInRoot.equals(mGroundRect)
            && mSceneRegion.contains(region) && ground.hasDisplayList()) return;
        if (!mSceneRegion.contains(region)) mSceneRegion.set(region);
        mGroundFrame = frame;
        mGroundGen = generation;
        mGroundRect.set(sceneInRoot);
        if (mSceneGround == null) {
            mSceneGround = new RenderNode("LiveBackdrop.sceneGround");
            mSceneGround.setRenderEffect(layerEffect());
        }
        int w = quarter(mSceneRegion.width());
        int h = quarter(mSceneRegion.height());
        mSceneGround.setPosition(0, 0, w, h);
        RecordingCanvas scene = mSceneGround.beginRecording(w, h);
        try {
            scene.scale(1f / SCALE, 1f / SCALE);
            scene.translate(-mSceneRegion.left, -mSceneRegion.top);
            scene.drawBitmap(frame, null, sceneInRoot, mScenePaint);
        } finally {
            mSceneGround.endRecording();
        }
        ground.setPosition(mSceneRegion);
        RecordingCanvas canvas = ground.beginRecording(mSceneRegion.width(), mSceneRegion.height());
        try {
            canvas.scale(SCALE, SCALE);
            canvas.drawRenderNode(mSceneGround);
        } finally {
            ground.endRecording();
        }
    }

    @NonNull private final android.graphics.Paint mScenePaint =
        new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);

    @RequiresApi(Build.VERSION_CODES.S)
    @NonNull
    private RenderNode groundNode() {
        if (mGroundContent == null) mGroundContent = new RenderNode("LiveBackdrop.groundContent");
        return mGroundContent;
    }

    /** What the ground draws, in the root's coordinates (the node stands at its rect there). */
    @Nullable private RenderNode mGroundContent;

    /** Where the ground stands: the root's coordinates to the recording's, this frame. */
    @RequiresApi(Build.VERSION_CODES.S)
    void aimGround(@NonNull Matrix rootToRecording) {
        if (mGroundContent == null) return;
        if (mGround == null) {
            mGround = new RenderNode("LiveBackdrop.ground");
            mGround.setClipToBounds(false);
        }
        mGroundByMatrix = aim(mGround, mGroundContent, rootToRecording, mGroundByMatrix);
    }

    /** Where the pages layer stands: its quarter-scale coordinates to the recording's. */
    @RequiresApi(Build.VERSION_CODES.S)
    void aimPagesLayer(@NonNull Matrix pageToRecording) {
        if (mPage == null) return;
        if (mPagesAt == null) {
            mPagesAt = new RenderNode("LiveBackdrop.pagesAt");
            mPagesAt.setClipToBounds(false);
        }
        mPagesByMatrix = aim(mPagesAt, mPage, pageToRecording, mPagesByMatrix);
    }

    /** An aim holder's recording size: larger than any screen, and it costs nothing. */
    private static final int AIM_EXTENT_PX = 8192;

    /** The aim holders are recorded around a matrix (true) or moved by their translation alone. */
    private boolean mGroundByMatrix;
    private boolean mPagesByMatrix;
    @NonNull private final float[] mAimValues = new float[9];

    /**
     * Puts {@code content} at {@code matrix} through {@code holder}. A move (a slide, the
     * parallax) is the holder's translation, a property: nothing is recorded. Any other matrix
     * (a tilted or sunk page carrying the surface) records the holder around it, two operations.
     * Either way the glass's own display list and the blurred layers stay as they are. Returns
     * whether the holder is now recorded around a matrix.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private boolean aim(@NonNull RenderNode holder, @NonNull RenderNode content,
                        @NonNull Matrix matrix, boolean byMatrix) {
        float[] v = mAimValues;
        matrix.getValues(v);
        boolean move = v[Matrix.MSCALE_X] == 1f && v[Matrix.MSKEW_X] == 0f
            && v[Matrix.MSKEW_Y] == 0f && v[Matrix.MSCALE_Y] == 1f && v[Matrix.MPERSP_0] == 0f
            && v[Matrix.MPERSP_1] == 0f && v[Matrix.MPERSP_2] == 1f;
        // The holder never clips (its damage reaches the composite, which does); its recording
        // only has to hold the content wherever the matrix puts it.
        int w = AIM_EXTENT_PX;
        int h = AIM_EXTENT_PX;
        if (move) {
            if (byMatrix || !holder.hasDisplayList()) {
                holder.setPosition(0, 0, w, h);
                RecordingCanvas canvas = holder.beginRecording(w, h);
                try {
                    canvas.drawRenderNode(content);
                } finally {
                    holder.endRecording();
                }
            }
            holder.setTranslationX(v[Matrix.MTRANS_X]);
            holder.setTranslationY(v[Matrix.MTRANS_Y]);
            return false;
        }
        holder.setTranslationX(0f);
        holder.setTranslationY(0f);
        holder.setPosition(0, 0, w, h);
        RecordingCanvas canvas = holder.beginRecording(w, h);
        try {
            canvas.concat(matrix);
            canvas.drawRenderNode(content);
        } finally {
            holder.endRecording();
        }
        return true;
    }

    /**
     * The holders draw the layers from this frame on: the ground, the pages when {@code pages},
     * and the blur node over the local layer, composited in the one draw the glass's effect then
     * bends. Records the composite only when what it holds changed; asks the holders to draw it
     * in this same frame when they drew the one picture.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    void showLayers(boolean pages, int width, int height) {
        if (mRecord == null || mBlur == null || mGround == null) return;
        if (pages && (mPagesAt == null || !mPagesAt.hasDisplayList())) return;
        if (mShown == null) mShown = new RenderNode("LiveBackdrop.layers");
        boolean resized = mShown.getWidth() != width || mShown.getHeight() != height;
        if (!mLayered || pages != mShownPages || resized || !mShown.hasDisplayList()) {
            mShown.setPosition(0, 0, width, height);
            RecordingCanvas canvas = mShown.beginRecording(width, height);
            try {
                canvas.drawRenderNode(mGround);
                if (pages) canvas.drawRenderNode(mPagesAt);
                canvas.scale(SCALE, SCALE);
                canvas.drawRenderNode(mBlur);
            } finally {
                mShown.endRecording();
            }
            mShownPages = pages;
        }
        if (!mLayered) {
            mLayered = true;
            mDrawable.invalidateSelf();
            mAimed.invalidateSelf();
        }
    }

    /**
     * The holders draw the one picture again, from this frame on. The caller records it in the
     * same pre-draw, so the landed frame is the resting glass.
     */
    void showOne() {
        if (!mLayered) return;
        mLayered = false;
        mDrawable.invalidateSelf();
        mAimed.invalidateSelf();
    }

    /** Lets the layers' nodes go; the composite and the ground draw nothing until recorded again. */
    private void dropLayers() {
        mLayered = false;
        mShownPages = false;
        mRecordHoldsLocal = false;
        mRecordedLocal.clear();
        mGroundFrame = null;
        mGroundGen = -1;
        mSceneRegion.setEmpty();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mShown != null && mShown.hasDisplayList()) mShown.discardDisplayList();
            if (mGround != null && mGround.hasDisplayList()) mGround.discardDisplayList();
            if (mGroundContent != null && mGroundContent.hasDisplayList()) {
                mGroundContent.discardDisplayList();
            }
            if (mSceneGround != null && mSceneGround.hasDisplayList()) mSceneGround.discardDisplayList();
            if (mPagesAt != null && mPagesAt.hasDisplayList()) mPagesAt.discardDisplayList();
        }
    }

    /** Bytes the scene ground's blur layer holds now, 0 with none: it counts in this picture's share. */
    long sceneGroundBytes() {
        return mSceneGround == null || mSceneRegion.isEmpty() ? 0L
            : LiveBackdrop.layerBytes(mSceneRegion.width(), mSceneRegion.height());
    }

    /** Whether the page layer holds a picture the record node can draw. */
    boolean hasPageLayer() {
        return mPage != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mPage.hasDisplayList();
    }

    /** The node the record node draws in the wall's place; null with no page layer. */
    @Nullable
    RenderNode pageLayer() {
        return hasPageLayer() ? mPage : null;
    }

    /**
     * Drops the page layer, its frozen picture and any capture under way. The caller records the
     * record node as one layer again in the same pre-draw, so nothing draws them from this frame
     * on.
     */
    void dropPageLayer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mCapture instanceof PageLayerCapture) ((PageLayerCapture) mCapture).cancel();
            if (mPage != null && mPage.hasDisplayList()) mPage.discardDisplayList();
        }
        retireFrozen();
        mPageMode = BackdropLayers.Mode.LIVE;
        mRecordedPages.clear();
        mRecordedPoseCount = -1;
    }

    /**
     * Lets the frozen picture go: the frame the record node last drew it in may still be on its
     * way to the screen, so it is recycled a frame later.
     */
    private void retireFrozen() {
        Bitmap frozen = mFrozen;
        mFrozen = null;
        if (frozen == null) return;
        if (mRetiredFrozen != null) mRetiredFrozen.recycle();
        mRetiredFrozen = frozen;
        if (mSurface.isAttachedToWindow()) mSurface.postOnAnimation(mRecycleFrozen);
        else recycleRetiredFrozen();
    }

    private void recycleRetiredFrozen() {
        if (mRetiredFrozen != null) mRetiredFrozen.recycle();
        mRetiredFrozen = null;
    }

    /** Whether the page layer holds a frozen picture. */
    boolean holdsFrozen() {
        return mFrozen != null;
    }

    /**
     * Drops both nodes' display lists, and with them the blur layer (a node with nothing to draw
     * gives its layer back on the next frame), but keeps the nodes: the surface's own display list
     * still references the blur node, so when the surface shows again and the next recording
     * fills it, the picture is back without the surface being redrawn. For a surface that hid.
     */
    void trim() {
        mReady = false;
        dropLayers();
        dropPageLayer();
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
            // The page layer is drawn by the record node, so it goes with it a frame later; its
            // frozen picture is recycled on that same frame.
            mRetiredPage = mPage;
            mPage = null;
            dropPageLayer();
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
        mPage = null;
        mCaptureRoot = null;
        // The second holder is not the surface: it draws its stored frame from its next draw too.
        mAimed.invalidateSelf();
    }

    private void discardRetired() {
        dropLayers();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mRetiredBlur != null && mRetiredBlur.hasDisplayList()) mRetiredBlur.discardDisplayList();
            if (mRetiredRecord != null && mRetiredRecord.hasDisplayList()) {
                mRetiredRecord.discardDisplayList();
            }
            if (mRetiredPage != null && mRetiredPage.hasDisplayList()) mRetiredPage.discardDisplayList();
        }
        mRetiredPage = null;
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
        return mDrawable.getCallback() != null || mAimed.getCallback() != null;
    }

    /** Draws the blur node at four times, the recording's origin on the surface's. */
    private final class LiveDrawable extends Drawable {
        private int mAlpha = 255;
        /** The second holder's: aimed by {@link #aimAt}, and never sets the picture's colour filter. */
        private final boolean mSecond;

        LiveDrawable(boolean second) {
            mSecond = second;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
            RenderNode blur = mBlur;
            // A software canvas (a screenshot of the view) cannot draw a node: nothing, not a crash.
            if (blur == null || !blur.hasDisplayList() || !(canvas instanceof RecordingCanvas)) return;
            // While the wall moves: the backdrop layers, composited at the recording's scale.
            RenderNode shown = mLayered ? mShown : null;
            boolean layers = shown != null && shown.hasDisplayList();
            int save = canvas.save();
            // The holder (an ImageView) has already moved the canvas to its content box.
            // The recording's origin is the surface's, less the margin and the reach above it.
            if (mSecond) {
                canvas.translate(-mMarginPx - mAimedX, -mMarginPx - mReachTopPx - mAimedY);
            } else {
                canvas.translate(-mMarginPx - mSurface.getPaddingLeft(),
                    -mMarginPx - mReachTopPx - mSurface.getPaddingTop());
            }
            if (layers) {
                ((RecordingCanvas) canvas).drawRenderNode(shown);
            } else {
                canvas.scale(SCALE, SCALE);
                ((RecordingCanvas) canvas).drawRenderNode(blur);
            }
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
            if (mSecond || colorFilter == mFilter) return;
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
