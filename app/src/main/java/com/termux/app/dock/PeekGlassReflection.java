package com.termux.app.dock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.logger.Logger;

/**
 * The dock's icons reflected on the Peek glass card ({@link DockStyle#PEEK_GLASS}): each icon
 * mirrored about its lower edge ({@link PeekGlassGeometry.Card#mirrorAxisY}), fading and blurring
 * down the card over {@link PeekGlassGeometry.Card#reflectionDepth}. Held once, in the glass
 * host's coordinates, and drawn by a {@link PeekReflectionView} on each surface the card spans: the
 * host and the strip under the gesture pill.
 *
 * <p>Two ways to draw it, one per glass:</p>
 * <ul>
 *   <li><b>Live</b> (Fancier glass active, so Android 13 and up): the icon row is recorded into a
 *   {@link RenderNode}, flipped, and a {@link RuntimeShader} effect on that node fades and blurs
 *   it. The recording draws the row's icons as references to their own render nodes, so a press,
 *   a notification dot or a new icon shows in the reflection on the frame it shows on the icon,
 *   with nothing recorded again; the row is recorded again only when it lays out, when one of its
 *   icons asks to be drawn again (once a frame at most) and on each frame a page swipe moves the
 *   icons. Nothing runs at rest.</li>
 *   <li><b>Still</b> (below Android 13, or Fancier glass off): one small flipped bitmap per icon,
 *   a quarter of its size each way with the fade drawn into it, which the view scales up with
 *   filtering (a cheap blur; Android 12 adds a light RenderEffect blur on the views). Rebuilt a
 *   short settle after the row last changed (an icon or a dot), never per frame; a page swipe
 *   puts it away and the settle brings it back for the page the row lands on.</li>
 * </ul>
 *
 * <p>The long-press app menu is not reflected: it is a window of its own over the activity's, and
 * reaching its pixels would take a capture of that window on every frame it is up.</p>
 */
public final class PeekGlassReflection {

    private static final String LOG_TAG = "PeekGlassReflection";

    /** What the still path holds of an icon, each way: low on purpose, a reflection is soft. */
    @VisibleForTesting static final float STILL_SCALE = 0.25f;
    /** How long after the row last changed the still path captures it. */
    @VisibleForTesting static final long STILL_SETTLE_MS = 160L;
    /** The room either side of an icon a still capture keeps, for a dot standing over its edge. */
    private static final float STILL_SIDE_PAD_DP = 3f;
    /** The light blur the still path's views wear on Android 12. */
    private static final float STILL_BLUR_DP = 1.5f;

    /**
     * The live path's program. {@code content} is the flipped row as the node holds it, in the
     * node's coordinates (the band's top is the mirror line); {@code uSize} the band; the blur
     * grows from nothing at the mirror line to {@code uMaxBlur} at the band's far end, sampled on
     * two rings of eight, and the reflection fades as (1 - t)^2 from {@code uAlpha}.
     */
    private static final String SHADER = ""
        + "uniform shader content;\n"
        + "uniform float2 uSize;\n"
        + "uniform float uAlpha;\n"
        + "uniform float uMaxBlur;\n"
        + "half4 main(float2 p) {\n"
        + "    float t = clamp(p.y / max(uSize.y, 1.0), 0.0, 1.0);\n"
        + "    float r = uMaxBlur * t;\n"
        + "    half4 acc = content.eval(p) * 0.2;\n"
        + "    for (int i = 0; i < 8; i++) {\n"
        + "        float a = float(i) * 0.7853982;\n"
        + "        float2 d = float2(cos(a), sin(a));\n"
        + "        acc += content.eval(p + d * r) * 0.05;\n"
        + "        acc += content.eval(p + d * (r * 0.5)) * 0.05;\n"
        + "    }\n"
        + "    float fade = 1.0 - t;\n"
        + "    return acc * half(uAlpha * fade * fade);\n"
        + "}\n";

    /** The dock's apps row as the reflection reads it. */
    public interface Row {
        /** The row's view, the one whose drawing is reflected. */
        @NonNull View view();

        /** The row's icon boxes, left, top, right, bottom each, in the row's coordinates. */
        int iconBoxes(@NonNull float[] out);

        /** Whether a page swipe or a page switch draws the icons away from their places now. */
        boolean iconsMoving();
    }

    private final float mDensity;
    @Nullable private Row mRow;
    @Nullable private PeekGlassGeometry.Card mCard;
    /** Where the row's origin stands in the host's coordinates. */
    private float mRowDx, mRowDy;
    private float mAlpha;
    private boolean mLive;
    /** The live path failed once (its program would not build): the still path from then on. */
    private boolean mLiveFailed;
    private boolean mShown;

    @Nullable private PeekReflectionView mHostView;
    @Nullable private PeekReflectionView mStripView;
    /** The still path's blur as the views last wore it: 1 on, 0 off, -1 not set yet. */
    private int mBlurState = -1;

    // The live path.
    @Nullable private Object mNode;
    @Nullable private Object mShader;
    private float mNodeAlpha = -1f;
    private int mNodeWidth = -1, mNodeHeight = -1;
    private boolean mRecordPending;

    // The still path.
    @NonNull private Bitmap[] mStill = new Bitmap[0];
    @NonNull private RectF[] mStillAt = new RectF[0];
    private boolean mStillSuspended;
    @NonNull private final Paint mStillPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    @NonNull private final float[] mBoxes = new float[64 * 4];

    @NonNull private final Runnable mRecord = this::recordFromCallback;
    @NonNull private final Runnable mCapture = this::captureStill;

    public PeekGlassReflection(float density) {
        mDensity = Math.max(0.1f, density);
    }

    // ------------------------------------------------------------------ the surfaces

    /**
     * Stands this reflection's view in the glass host, right over {@code under} (the tint) so it
     * draws on the glass and under nothing of the row's, and answers it.
     */
    @NonNull
    public View attachHost(@NonNull ViewGroup host, @Nullable View under) {
        PeekReflectionView view = mHostView;
        if (view == null) {
            view = new PeekReflectionView(host.getContext(), this);
            mHostView = view;
        }
        if (view.getParent() != host) {
            detach(view);
            mBlurState = -1;
            int index = under != null && under.getParent() == host
                ? host.indexOfChild(under) + 1 : host.getChildCount();
            host.addView(view, Math.min(index, host.getChildCount()), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        view.setOrigin(0f, 0f);
        syncViews();
        return view;
    }

    /**
     * Stands this reflection's view on top of the strip under the gesture pill ({@code strip}),
     * whose origin stands at {@code originX}, {@code originY} in the host's coordinates; null lets
     * it go.
     */
    public void attachStrip(@Nullable ViewGroup strip, float originX, float originY) {
        if (strip == null) {
            if (mStripView != null) detach(mStripView);
            return;
        }
        PeekReflectionView view = mStripView;
        if (view == null) {
            view = new PeekReflectionView(strip.getContext(), this);
            mStripView = view;
        }
        if (view.getParent() != strip) {
            detach(view);
            mBlurState = -1;
            strip.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        }
        view.setOrigin(originX, originY);
        syncViews();
    }

    private static void detach(@NonNull View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
    }

    // ------------------------------------------------------------------ what it reflects

    /**
     * The card, the row and where it stands, and how strongly to show: anything that moved is
     * drawn again (live) or captured again after the settle (still); the same values do nothing.
     *
     * @param alpha the reflection's strength at its top ({@link PeekGlassGeometry#reflectionAlpha})
     * @param live  whether the live path draws it: Android 13 and Fancier glass active
     */
    public void update(@NonNull PeekGlassGeometry.Card card, @NonNull Row row, float rowDx,
                       float rowDy, float alpha, boolean live) {
        boolean liveNow = live && !mLiveFailed
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
        boolean geometryMoved = !card.equals(mCard) || row != mRow
            || Float.compare(rowDx, mRowDx) != 0 || Float.compare(rowDy, mRowDy) != 0
            || liveNow != mLive || !mShown;
        boolean alphaMoved = Float.compare(alpha, mAlpha) != 0;
        mCard = card;
        mRow = row;
        mRowDx = rowDx;
        mRowDy = rowDy;
        mAlpha = alpha;
        boolean pathChanged = liveNow != mLive || !mShown;
        mLive = liveNow;
        mShown = alpha > 0f;
        mStillPaint.setAlpha(Math.round(255f * Math.max(0f, Math.min(1f, alpha))));
        if (!mShown) {
            dropDrawings();
            syncViews();
            return;
        }
        if (pathChanged) dropDrawings();
        syncViews();
        if (mLive) {
            if (geometryMoved || alphaMoved) record();
        } else {
            if (geometryMoved) requestStillCapture();
            else if (alphaMoved) invalidateViews();
        }
    }

    /** Nothing reflected, the views put away, and every drawing let go. */
    public void clear() {
        mShown = false;
        mCard = null;
        mRow = null;
        dropDrawings();
        syncViews();
    }

    /** Lets the views go as well; for the activity's end. */
    public void release() {
        clear();
        if (mHostView != null) detach(mHostView);
        if (mStripView != null) detach(mStripView);
    }

    /** One of the row's icons asked to be drawn again: an icon arrived, a dot or a press changed. */
    public void onRowContentChanged() {
        if (!mShown || mRow == null) return;
        if (mLive) {
            if (mRecordPending) return;
            mRecordPending = true;
            mRow.view().postOnAnimation(mRecord);
        } else if (!mRow.iconsMoving()) {
            requestStillCapture();
        }
    }

    /**
     * A frame of a page swipe or a page switch moved the icons, from the callback that moved
     * them, before the frame draws: the live reflection is recorded again at once, so it rides
     * with the icons; the still one is put away until the row is back at rest.
     */
    public void onIconsMoved() {
        Row row = mRow;
        if (!mShown || row == null) return;
        if (mLive) {
            record();
            return;
        }
        boolean moving = row.iconsMoving();
        if (moving && !mStillSuspended) {
            mStillSuspended = true;
            row.view().removeCallbacks(mCapture);
            invalidateViews();
        } else if (!moving && mStillSuspended) {
            mStillSuspended = false;
            requestStillCapture();
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the reflection on a view whose origin stands at {@code originX}, {@code originY}. */
    void drawOn(@NonNull Canvas canvas, float originX, float originY) {
        PeekGlassGeometry.Card card = mCard;
        if (!mShown || card == null) return;
        int save = canvas.save();
        canvas.translate(-originX, -originY);
        if (mLive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Live.draw(canvas, mNode);
        } else if (!mStillSuspended) {
            for (int i = 0; i < mStill.length; i++) {
                Bitmap bitmap = mStill[i];
                if (bitmap != null) canvas.drawBitmap(bitmap, null, mStillAt[i], mStillPaint);
            }
        }
        canvas.restoreToCount(save);
    }

    private void syncViews() {
        boolean show = mShown && mCard != null;
        int blur = show && !mLive ? 1 : 0;
        boolean blurMoved = blur != mBlurState;
        mBlurState = blur;
        for (PeekReflectionView view : new PeekReflectionView[] {mHostView, mStripView}) {
            if (view == null) continue;
            int visibility = show ? View.VISIBLE : View.GONE;
            if (view.getVisibility() != visibility) view.setVisibility(visibility);
            if (blurMoved && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Still.blur(view, blur == 1, STILL_BLUR_DP * mDensity);
        }
    }

    private void invalidateViews() {
        if (mHostView != null) mHostView.invalidate();
        if (mStripView != null) mStripView.invalidate();
    }

    private void dropDrawings() {
        Row row = mRow;
        if (row != null) {
            row.view().removeCallbacks(mCapture);
            row.view().removeCallbacks(mRecord);
        }
        mRecordPending = false;
        mStill = new Bitmap[0];
        mStillAt = new RectF[0];
        mStillSuspended = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mNode != null) {
            Live.discard(mNode);
        }
        mNode = null;
        mNodeAlpha = -1f;
        mNodeWidth = -1;
        mNodeHeight = -1;
        invalidateViews();
    }

    // ------------------------------------------------------------------ the live path

    private void recordFromCallback() {
        mRecordPending = false;
        record();
    }

    private void record() {
        if (!mLive || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        PeekGlassGeometry.Card card = mCard;
        Row row = mRow;
        if (card == null || row == null || card.reflectionDepth() < 1f) return;
        View view = row.view();
        if (!view.isAttachedToWindow() || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        try {
            if (mShader == null) mShader = Live.shader();
            if (mNode == null) mNode = Live.node();
            int width = Math.max(1, Math.round(card.right - card.left));
            int height = Math.max(1, Math.round(card.reflectionDepth()));
            float maxBlur = PeekGlassGeometry.REFLECTION_MAX_BLUR_DP * mDensity;
            if (width != mNodeWidth || height != mNodeHeight || Float.compare(mAlpha, mNodeAlpha) != 0) {
                Live.effect(mNode, mShader, width, height, mAlpha, maxBlur);
                mNodeWidth = width;
                mNodeHeight = height;
                mNodeAlpha = mAlpha;
            }
            Live.record(mNode, view, card, mRowDx, mRowDy, width, height);
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG,
                "Peek glass live reflection failed; still bitmaps from here on", t);
            mLiveFailed = true;
            mLive = false;
            dropDrawings();
            syncViews();
            requestStillCapture();
            return;
        }
        invalidateViews();
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Live {

        @NonNull
        static Object node() {
            RenderNode node = new RenderNode("PeekGlassReflection");
            node.setClipToBounds(true);
            return node;
        }

        @NonNull
        static Object shader() {
            return new RuntimeShader(SHADER);
        }

        static void effect(@NonNull Object node, @NonNull Object shader, int width, int height,
                           float alpha, float maxBlurPx) {
            RuntimeShader program = (RuntimeShader) shader;
            program.setFloatUniform("uSize", width, height);
            program.setFloatUniform("uAlpha", Math.max(0f, Math.min(1f, alpha)));
            program.setFloatUniform("uMaxBlur", Math.max(0f, maxBlurPx));
            // A RenderEffect copies the uniforms as they are: built again for each change.
            ((RenderNode) node).setRenderEffect(
                RenderEffect.createRuntimeShaderEffect(program, "content"));
        }

        /**
         * The row flipped about the card's mirror line into the node, which stands at the band in
         * the host's coordinates: a row point at {@code y} lands at {@code axis - (rowDy + y)}
         * below the band's top. The row's own slide (a page switch's translation) is drawn in.
         */
        static void record(@NonNull Object nodeObject, @NonNull View row,
                           @NonNull PeekGlassGeometry.Card card, float rowDx, float rowDy,
                           int width, int height) {
            RenderNode node = (RenderNode) nodeObject;
            int left = Math.round(card.left);
            int top = Math.round(card.mirrorAxisY);
            node.setPosition(left, top, left + width, top + height);
            RecordingCanvas canvas = node.beginRecording(width, height);
            try {
                canvas.translate(rowDx + row.getTranslationX() - left, card.mirrorAxisY - rowDy);
                canvas.scale(1f, -1f);
                row.draw(canvas);
            } finally {
                node.endRecording();
            }
        }

        static void draw(@NonNull Canvas canvas, @Nullable Object node) {
            if (node == null || !(canvas instanceof RecordingCanvas)) return;
            RenderNode renderNode = (RenderNode) node;
            if (!renderNode.hasDisplayList()) return;
            ((RecordingCanvas) canvas).drawRenderNode(renderNode);
        }

        static void discard(@NonNull Object node) {
            ((RenderNode) node).discardDisplayList();
        }
    }

    // ------------------------------------------------------------------ the still path

    private void requestStillCapture() {
        Row row = mRow;
        if (row == null || mLive || !mShown) return;
        View view = row.view();
        view.removeCallbacks(mCapture);
        view.postDelayed(mCapture, STILL_SETTLE_MS);
    }

    private void captureStill() {
        Row row = mRow;
        PeekGlassGeometry.Card card = mCard;
        if (row == null || card == null || mLive || !mShown) return;
        View view = row.view();
        // A row mid-swipe shows icons in passing: the settle after it asks again.
        if (row.iconsMoving() || !view.isAttachedToWindow() || view.getWidth() <= 0) return;
        float depth = card.reflectionDepth();
        if (depth < 1f) return;
        int count = Math.min(row.iconBoxes(mBoxes), mBoxes.length / 4);
        Bitmap[] bitmaps = new Bitmap[Math.max(0, count)];
        RectF[] at = new RectF[bitmaps.length];
        float pad = STILL_SIDE_PAD_DP * mDensity;
        for (int i = 0; i < count; i++) {
            float l = mBoxes[i * 4] - pad;
            float r = mBoxes[i * 4 + 2] + pad;
            float fullWidth = r - l;
            if (!(fullWidth > 0f)) continue;
            at[i] = new RectF(l + mRowDx, card.mirrorAxisY, r + mRowDx, card.mirrorAxisY + depth);
            bitmaps[i] = Still.capture(view, l, fullWidth, depth, card.mirrorAxisY - mRowDy);
        }
        mStill = bitmaps;
        mStillAt = at;
        mStillSuspended = false;
        invalidateViews();
    }

    private static final class Still {

        /**
         * One icon's reflection: the row's column {@code fullWidth} wide from {@code left},
         * flipped about {@code axisInRow} (the mirror line in the row's coordinates), the fade
         * drawn in, at {@link #STILL_SCALE}. Null where the phone cannot draw it.
         */
        @Nullable
        static Bitmap capture(@NonNull View row, float left, float fullWidth, float depth,
                              float axisInRow) {
            int width = Math.max(1, (int) Math.ceil(fullWidth * STILL_SCALE));
            int height = Math.max(1, (int) Math.ceil(depth * STILL_SCALE));
            try {
                Picture picture = new Picture();
                Canvas canvas = picture.beginRecording(width, height);
                canvas.scale(width / fullWidth, height / depth);
                int layer = canvas.saveLayer(0f, 0f, fullWidth, depth, null);
                int save = canvas.save();
                canvas.clipRect(0f, 0f, fullWidth, depth);
                canvas.translate(-left, axisInRow);
                canvas.scale(1f, -1f);
                row.draw(canvas);
                canvas.restoreToCount(save);
                Paint fade = new Paint();
                fade.setShader(new LinearGradient(0f, 0f, 0f, depth,
                    new int[] {0xFF000000, 0x40000000, 0x00000000}, new float[] {0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
                fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                canvas.drawRect(0f, 0f, fullWidth, depth, fade);
                canvas.restoreToCount(layer);
                picture.endRecording();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Rasterised by the renderer, which also draws the icons' hardware bitmaps.
                    return Bitmap.createBitmap(picture, width, height, Bitmap.Config.HARDWARE);
                }
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                new Canvas(bitmap).drawPicture(picture);
                return bitmap;
            } catch (Throwable t) {
                // Android 8 cannot draw an icon's hardware bitmap in software: no reflection.
                Logger.logDebug(LOG_TAG, "Peek glass still reflection unavailable: " + t);
                return null;
            }
        }

        /** Android 12's light blur on a still reflection's view, or none. */
        @RequiresApi(Build.VERSION_CODES.S)
        static void blur(@NonNull View view, boolean on, float radiusPx) {
            view.setRenderEffect(on && radiusPx > 0f
                ? RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.DECAL) : null);
        }
    }
}
