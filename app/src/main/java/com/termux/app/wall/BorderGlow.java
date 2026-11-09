package com.termux.app.wall;

import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * The border's glow: the border a finger is on, lit along that one edge of the page, so the press
 * answers before it takes. It comes up the moment a finger lands where it could page the wall
 * ({@link BorderDrag#holdPages}) — "you are on it". On a border outside the page's text, where a
 * sideways slide pages at once ({@link BorderDrag#slidePages}), it is full from the touch
 * ({@link #light}): nothing is waiting to happen there. Where the band reaches past the
 * clearance into the page, and only the hold pages, it brightens over the hold ({@link #press}),
 * so a glow nearly full says it is about to take. Once the wall claims the finger it stays full
 * until the drag ends; a press given up or claimed by a swipe first (moved, joined, lifted, left to the content, handed
 * to a grabber) fades it out quickly. A press that can only become the keyboard's or the status
 * bar's swipe has its grabber ({@link BorderGrabber}) and no glow. Under reduced motion it is
 * full at once and gone at once.
 *
 * <p>Drawn by the wall ({@link PaneWallLayout#dispatchDraw}) in the accent the grabbers use, as a
 * band along the held edge of the page as drawn — slid and sunk with it. On the top and side
 * borders it is lit at the edge and through the page's clearance before its text, and fades out
 * inward: it lies wholly inside the page's frame ({@link #band}) and is clipped to it, never
 * spilling into the status bar beyond. The bottom border's lies just below it instead
 * ({@link #belowBand}): under the pane the finger pressed, lit at that pane's bottom edge and
 * fading out down into the air before the dock, the keyboard or the screen's edge, clipped to that
 * air so it never reaches what is below; a page with no air below keeps it inside. And it stays
 * on the edge the finger pressed: should the page grow after the press (the content reaching
 * under the dock or the keyboard), the glow keeps the frame it was lit on ({@link #heldExtent}).
 * It asks for frames only while it rises or fades; at rest and at full nothing repaints. Its
 * level is a function of the clock, so the timing is pure and tested without a view.
 */
public final class BorderGlow {

    /** How bright it is the moment the finger lands, as a share of full: "you are on it". */
    static final float START_LEVEL = 0.35f;
    /** How long a press given up takes to fade out. */
    static final long FADE_MS = 140L;
    /** Its alpha at full, under the accent's own. */
    static final float FULL_ALPHA = 0.8f;
    /**
     * How far from the page's edge it reaches in all, in dp: lit through {@link #INSET_DP} and
     * fading to nothing over the rest. Inward, except along the bottom border, where it reaches
     * down into the air below.
     */
    public static final float DEPTH_DP = 10f;
    /**
     * How far inside the page's edge it is lit at full, in dp: the clearance a pane keeps before
     * its text, the same reach the side borders take into it.
     */
    public static final float INSET_DP = BorderDrag.SIDE_REACH_DP;
    /**
     * How far from each end of the edge it fades to nothing, in dp: the default corner radius, so
     * the glow runs the whole edge and dims into the rounded corner instead of standing past it.
     */
    public static final float TAPER_DP = 24f;

    /** Frame-source counter for the idle audit: borderGlow. */
    private static final com.termux.terminal.FrameProbe.Source BORDERGLOW = com.termux.terminal.FrameProbe.source("borderGlow");

    private enum Phase { REST, RISING, FULL, FADING }

    @NonNull private final View mHost;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    /** Maps the unit gradient onto the band, so a frame allocates nothing. */
    private final Matrix mMatrix = new Matrix();
    /** The unit gradient from 0 to 1 down y, for the top and bottom edges, and across x. */
    @Nullable private Shader mDown;
    @Nullable private Shader mAcross;
    private int mColor;
    @NonNull private Phase mPhase = Phase.REST;
    @NonNull private BorderDrag.Border mSide = BorderDrag.Border.NONE;
    /** When the rise or the fade under way began, on the uptime clock. */
    private long mSinceMs;
    private long mHoldMs;
    /** The level a fade began at. */
    private float mFromLevel;

    BorderGlow(@NonNull View host) {
        mHost = host;
        mPaint.setStyle(Paint.Style.FILL);
        refreshColor();
    }

    /** Re-read the accent: the scheme or the theme may have moved under it. */
    void refreshColor() {
        int color = BorderGrabber.accent(mHost.getContext());
        if (color == mColor && mDown != null) return;
        mColor = color;
        int clear = color & 0x00FFFFFF;
        int[] colors = {color, color, clear};
        float[] stops = {0f, INSET_DP / DEPTH_DP, 1f};
        mDown = new LinearGradient(0f, 0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP);
        mAcross = new LinearGradient(0f, 0f, 1f, 0f, colors, stops, Shader.TileMode.CLAMP);
        if (mPhase != Phase.REST) mHost.invalidate();
    }

    /**
     * A finger landed on {@code side} at {@code nowMs} where a hold of {@code holdMs} could page:
     * lit at once, and rising to full over the hold, or full at once under reduced motion.
     */
    void press(@NonNull BorderDrag.Border side, long nowMs, long holdMs, boolean reducedMotion) {
        if (side == BorderDrag.Border.NONE) return;
        mSide = side;
        mSinceMs = nowMs;
        mHoldMs = Math.max(0L, holdMs);
        mPhase = reducedMotion ? Phase.FULL : Phase.RISING;
        mHost.invalidate();
    }

    /**
     * A finger landed on {@code side} where a sideways slide pages at once: full from the touch,
     * with no rise, since there is no hold to count down.
     */
    void light(@NonNull BorderDrag.Border side) {
        if (side == BorderDrag.Border.NONE) return;
        mSide = side;
        mPhase = Phase.FULL;
        mHost.invalidate();
    }

    /** The wall claimed the finger: full, and held there until the drag ends. */
    void claim() {
        if (mPhase != Phase.RISING) return;
        mPhase = Phase.FULL;
        mHost.invalidate();
    }

    /**
     * The press was given up, or its drag ended, at {@code nowMs}: it fades from wherever it
     * stands, or is gone at once under reduced motion. Nothing for a glow not held.
     */
    void release(long nowMs, boolean reducedMotion) {
        if (!isHeld()) return;
        float from = level(nowMs);
        if (reducedMotion || !(from > 0f)) {
            mPhase = Phase.REST;
        } else {
            mPhase = Phase.FADING;
            mFromLevel = from;
            mSinceMs = nowMs;
        }
        mHost.invalidate();
    }

    /** Gone at once, with no frame asked for: the wall left the window, or the page went away. */
    void reset() {
        mPhase = Phase.REST;
        mSide = BorderDrag.Border.NONE;
    }

    /** Whether a press holds it: rising, or full under a claimed drag. */
    boolean isHeld() {
        return mPhase == Phase.RISING || mPhase == Phase.FULL;
    }

    /** Whether there is anything to draw: held, or fading. */
    boolean isShowing() {
        return mPhase != Phase.REST;
    }

    /** The edge it lights. */
    @NonNull
    BorderDrag.Border side() {
        return mSide;
    }

    /** Its level at {@code nowMs}: 0 at rest, 1 at full. */
    float level(long nowMs) {
        switch (mPhase) {
            case RISING: return riseLevel(nowMs - mSinceMs, mHoldMs);
            case FULL: return 1f;
            case FADING: return fadeLevel(mFromLevel, nowMs - mSinceMs);
            default: return 0f;
        }
    }

    /** Whether it is still changing at {@code nowMs}, and so wants another frame. */
    boolean needsFrame(long nowMs) {
        switch (mPhase) {
            case RISING: return nowMs - mSinceMs < mHoldMs;
            case FADING: return nowMs - mSinceMs < FADE_MS;
            default: return false;
        }
    }

    /**
     * Draw it along its edge of a page drawn at {@code left, top, right, bottom} and
     * {@code scale}, at {@code visibility} of its alpha (the page's outline fading as it slides
     * away), and ask the host for the next frame while it is still rising or fading.
     *
     * @return whether a next frame was asked for
     */
    boolean draw(@NonNull Canvas canvas, float left, float top, float right, float bottom,
                 float scale, float visibility, long nowMs) {
        float level = level(nowMs);
        if (mPhase == Phase.FADING && !needsFrame(nowMs)) {
            mPhase = Phase.REST;
            level = 0f;
        }
        if (!(level > 0f) || !(visibility > 0f) || mDown == null || mAcross == null) return false;
        float density = mHost.getResources().getDisplayMetrics().density;
        if (!band(mRect, mSide, left, top, right, bottom, scale, density)) return false;
        // From the page's edge, lit, inward to the band's far side, clear; clipped to the frame,
        // so not even the rounding's edge pixels fall outside it.
        float lit;
        float clear;
        switch (mSide) {
            case TOP: lit = mRect.top; clear = mRect.bottom; break;
            case BOTTOM: lit = mRect.bottom; clear = mRect.top; break;
            case LEFT: lit = mRect.left; clear = mRect.right; break;
            default: lit = mRect.right; clear = mRect.left; break;
        }
        return paint(canvas, lit, clear, left, top, right, bottom, level, visibility, density,
            nowMs);
    }

    /**
     * Draw the bottom border's glow below the pane the finger pressed ({@link #belowBand}): across
     * {@code paneLeft..paneRight}, from that pane's bottom edge {@code edge} down into the air
     * that ends at {@code airBottom}, all in the page's own space at {@code scale}. Where there is
     * no air below the edge it is the band inside the page's frame ({@code left, top, right,
     * bottom}) as {@link #draw} draws it, so the press still answers. Any other side is
     * {@link #draw}'s.
     *
     * @return whether a next frame was asked for
     */
    boolean drawBelow(@NonNull Canvas canvas, float left, float top, float right, float bottom,
                      float paneLeft, float paneRight, float edge, float airBottom, float scale,
                      float visibility, long nowMs) {
        float density = mHost.getResources().getDisplayMetrics().density;
        if (mSide != BorderDrag.Border.BOTTOM
            || !belowBand(mRect, paneLeft, paneRight, edge, airBottom, scale, density)) {
            return draw(canvas, left, top, right, bottom, scale, visibility, nowMs);
        }
        float level = level(nowMs);
        if (mPhase == Phase.FADING && !needsFrame(nowMs)) {
            mPhase = Phase.REST;
            level = 0f;
        }
        if (!(level > 0f) || !(visibility > 0f) || mDown == null || mAcross == null) return false;
        // Lit at the pane's edge, clear toward what is below; clipped to the air itself, so it
        // never reaches the pane above or the dock, keyboard or screen edge below.
        return paint(canvas, mRect.top, mRect.bottom, mRect.left, mRect.top, mRect.right,
            mRect.bottom, level, visibility, density, nowMs);
    }

    /**
     * Paints {@link #mRect} as the glow along {@link #mSide}, lit at {@code lit} and clear at
     * {@code clear} across the edge, its ends tapered along it, at {@code level} and clipped to
     * {@code clipLeft, clipTop, clipRight, clipBottom}; asks for the next frame while it changes.
     */
    private boolean paint(@NonNull Canvas canvas, float lit, float clear, float clipLeft,
                          float clipTop, float clipRight, float clipBottom, float level,
                          float visibility, float density, long nowMs) {
        boolean down = mSide == BorderDrag.Border.TOP || mSide == BorderDrag.Border.BOTTOM;
        mMatrix.setScale(down ? 1f : clear - lit, down ? clear - lit : 1f);
        mMatrix.postTranslate(down ? 0f : lit, down ? lit : 0f);
        Shader shader = down ? mDown : mAcross;
        shader.setLocalMatrix(mMatrix);
        // The ends fade along the edge: the lit-to-clear gradient across it, kept only where the
        // taper along it is opaque. Built per frame, and frames only run while it changes.
        float length = down ? mRect.width() : mRect.height();
        float taper = taperStop(TAPER_DP * density, length);
        int[] mask = {0x00000000, 0xFF000000, 0xFF000000, 0x00000000};
        float[] at = {0f, taper, 1f - taper, 1f};
        Shader along = down
            ? new LinearGradient(mRect.left, 0f, mRect.right, 0f, mask, at, Shader.TileMode.CLAMP)
            : new LinearGradient(0f, mRect.top, 0f, mRect.bottom, mask, at, Shader.TileMode.CLAMP);
        mPaint.setShader(new ComposeShader(shader, along, PorterDuff.Mode.DST_IN));
        mPaint.setAlpha(Math.round(255f * alpha(level, visibility)));
        float radius = Math.min(mRect.width(), mRect.height()) / 2f;
        int save = canvas.save();
        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        canvas.restoreToCount(save);
        boolean more = needsFrame(nowMs);
        if (more) {
            BORDERGLOW.tick();
            mHost.postInvalidateOnAnimation();
        }
        return more;
    }

    // ---- Pure ---------------------------------------------------------------------------------

    /** The level {@code elapsedMs} into a hold of {@code holdMs}: lit at once, full at the hold. */
    static float riseLevel(long elapsedMs, long holdMs) {
        if (holdMs <= 0L) return 1f;
        float t = clamp01(elapsedMs / (float) holdMs);
        return START_LEVEL + (1f - START_LEVEL) * t;
    }

    /** The level {@code elapsedMs} into a fade from {@code from}: 0 at {@link #FADE_MS}. */
    static float fadeLevel(float from, long elapsedMs) {
        return clamp01(from) * (1f - clamp01(elapsedMs / (float) FADE_MS));
    }

    /** The share of the accent's alpha it is drawn at, for {@code level} and {@code visibility}. */
    static float alpha(float level, float visibility) {
        return clamp01(level) * FULL_ALPHA * clamp01(visibility);
    }

    /**
     * The band along {@code side} of a page framed at {@code left, top, right, bottom} and drawn
     * at {@code scale}: from that edge {@link #DEPTH_DP} inward (never past the middle of the
     * page), and along the whole edge, corner to corner ({@link #TAPER_DP} fades its ends). It
     * lies wholly inside the frame: nothing of it is outside the edge it lights. False, with
     * {@code out} untouched, where there is none.
     */
    static boolean band(@NonNull RectF out, @NonNull BorderDrag.Border side, float left, float top,
                        float right, float bottom, float scale, float density) {
        if (!(right > left) || !(bottom > top)) return false;
        float s = Float.isNaN(scale) || scale <= 0f ? 1f : scale;
        float depth = DEPTH_DP * density * s;
        float l;
        float t;
        float r;
        float b;
        switch (side) {
            case TOP:
                l = left; r = right; t = top; b = top + Math.min(depth, (bottom - top) / 2f);
                break;
            case BOTTOM:
                l = left; r = right; t = bottom - Math.min(depth, (bottom - top) / 2f); b = bottom;
                break;
            case LEFT:
                l = left; r = left + Math.min(depth, (right - left) / 2f); t = top; b = bottom;
                break;
            case RIGHT:
                l = right - Math.min(depth, (right - left) / 2f); r = right; t = top; b = bottom;
                break;
            default: return false;
        }
        if (!(r > l) || !(b > t)) return false;
        out.set(l, t, r, b);
        return true;
    }

    /**
     * The bottom border's band below the pane a finger pressed, in the page's own space at
     * {@code scale}: across the pane's span {@code left..right}, from its bottom edge {@code edge}
     * down {@link #DEPTH_DP}, never past {@code airBottom}, where the air below the page ends at
     * the dock, the keyboard or the screen's edge. Derived from the pane's laid-out edge and the
     * air measured below it, so it sits in the gap whatever the Margin, the dock's style, the
     * keyboard or the orientation make of it. False, {@code out} untouched, where there is no air
     * below the edge (a page flush with what is below it) or no span.
     */
    static boolean belowBand(@NonNull RectF out, float left, float right, float edge,
                             float airBottom, float scale, float density) {
        if (!(right > left) || !(airBottom > edge)) return false;
        float s = Float.isNaN(scale) || scale <= 0f ? 1f : scale;
        float b = Math.min(airBottom, edge + DEPTH_DP * density * s);
        if (!(b > edge)) return false;
        out.set(left, edge, right, b);
        return true;
    }

    /**
     * How far the frame the glow is drawn in reaches along one axis: the page's extent when the
     * press lit it ({@code atPressPx}), or its extent now ({@code nowPx}) where that is less. A
     * page that grows after the press — the content reaching under the dock or the keyboard —
     * leaves the glow on the edge the finger pressed instead of carrying it off to the new one;
     * one that shrinks keeps it inside itself. With no extent recorded, the extent now.
     */
    static float heldExtent(float atPressPx, float nowPx) {
        if (!(atPressPx > 0f)) return nowPx;
        return Math.min(atPressPx, nowPx);
    }

    /**
     * Where along an edge of {@code lengthPx} the fade at each end stops, as a fraction: the taper
     * over the length, never past the middle, so a short edge still peaks once.
     */
    static float taperStop(float taperPx, float lengthPx) {
        if (!(lengthPx > 0f)) return 0.5f;
        return Math.min(0.5f, Math.max(0f, taperPx) / lengthPx);
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
