package com.termux.app.setup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.ReducedMotion;

import java.util.ArrayList;
import java.util.List;

/**
 * A stylised phone frame with rounded blocks and a touch ring, played from a {@link Scene} (data
 * built by {@link GuideScenes}). Geometry is in fractions of the frame; the ring's w is its
 * radius as a fraction of the frame width. Nothing bounces.
 */
final class GestureDemoView extends View {

    enum Kind { WALLPAPER, STATUS, CLOCK, DOCK, TERMINAL, KEYBOARD, SHEET, FIELD, MENU, POPOVER,
        PILL, TINT, DOT, OUTLINE, RING }

    static final int INOUT = 0, DECEL = 1, LINEAR = 2;
    static final float KEEP = Float.NaN;

    /** One keyframe: x, y (left/top; centre for the ring), w, h, alpha; reached with an easing. */
    static final class Key {
        final int t, ease;
        final float x, y, w, h, a;
        Key(int t, int ease, float x, float y, float w, float h, float a) {
            this.t = t; this.ease = ease; this.x = x; this.y = y; this.w = w; this.h = h; this.a = a;
        }
    }

    static final class Block {
        final Kind kind;
        @Nullable final String label;
        final List<Key> keys = new ArrayList<>();

        Block(Kind kind, @Nullable String label, float x, float y, float w, float h) {
            this.kind = kind;
            this.label = label;
            keys.add(new Key(0, LINEAR, x, y, w, h, 1f));
        }

        /** Starts invisible. */
        Block hidden() {
            Key k = keys.get(0);
            keys.set(0, new Key(0, LINEAR, k.x, k.y, k.w, k.h, 0f));
            return this;
        }

        /** Reaches the given state at time t; KEEP leaves a value as it was. */
        Block to(int t, int ease, float x, float y, float w, float h, float a) {
            Key p = keys.get(keys.size() - 1);
            keys.add(new Key(t, ease, pick(x, p.x), pick(y, p.y), pick(w, p.w), pick(h, p.h),
                pick(a, p.a)));
            return this;
        }

        private static float pick(float v, float old) { return Float.isNaN(v) ? old : v; }

        int endMs() { return keys.get(keys.size() - 1).t; }
    }

    static final class Scene {
        final List<Block> blocks = new ArrayList<>();
        final Block ring = new Block(Kind.RING, null, 0.5f, 0.5f, 0.07f, 0f).hidden();
        /** The still shown under reduced motion; -1 for the end. */
        int posterMs = -1;

        Block add(Kind kind, float x, float y, float w, float h) {
            return add(kind, null, x, y, w, h);
        }

        Block add(Kind kind, @Nullable String label, float x, float y, float w, float h) {
            Block b = new Block(kind, label, x, y, w, h);
            blocks.add(b);
            return b;
        }

        int totalMs() {
            int end = ring.endMs();
            for (Block b : blocks) end = Math.max(end, b.endMs());
            return end;
        }
    }

    private static final int REST_MS = 1000;
    private static final float ASPECT = 0.52f;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mFrame = new RectF();
    private final RectF mR = new RectF();
    private final Path mClip = new Path();
    private final float[] mOut = new float[5];
    private final float mDp;
    @Nullable private Scene mScene;
    @Nullable private ValueAnimator mAnimator;
    private float mTimeMs;

    GestureDemoView(@NonNull Context context) {
        super(context);
        mDp = context.getResources().getDisplayMetrics().density;
        mStroke.setStyle(Paint.Style.STROKE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setScene(@NonNull Scene scene) {
        stop();
        mScene = scene;
        mTimeMs = 0f;
        invalidate();
    }

    void start() {
        stop();
        if (mScene == null) return;
        if (ReducedMotion.isEnabled(getContext())) {
            mTimeMs = mScene.posterMs >= 0 ? mScene.posterMs : mScene.totalMs();
            invalidate();
            return;
        }
        final int total = mScene.totalMs() + REST_MS;
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(total);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.addUpdateListener(a -> {
            mTimeMs = (float) a.getAnimatedValue() * total;
            invalidate();
        });
        mAnimator.start();
    }

    void stop() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(250 * mDp));
    }

    private static float ease(int kind, float f) {
        switch (kind) {
            case INOUT: return (float) (0.5 - 0.5 * Math.cos(Math.PI * f));
            case DECEL: return 1f - (1f - f) * (1f - f);
            default: return f;
        }
    }

    /** Fills mOut with x, y, w, h, alpha of the block at time t. */
    private void eval(Block b, float t) {
        List<Key> ks = b.keys;
        Key from = ks.get(0), to = from;
        float f = 0f;
        Key last = ks.get(ks.size() - 1);
        if (t >= last.t) {
            from = to = last;
        } else if (t > from.t) {
            for (int i = 1; i < ks.size(); i++) {
                if (t < ks.get(i).t) {
                    from = ks.get(i - 1);
                    to = ks.get(i);
                    f = ease(to.ease, (t - from.t) / (float) (to.t - from.t));
                    break;
                }
            }
        }
        mOut[0] = from.x + (to.x - from.x) * f;
        mOut[1] = from.y + (to.y - from.y) * f;
        mOut[2] = from.w + (to.w - from.w) * f;
        mOut[3] = from.h + (to.h - from.h) * f;
        mOut[4] = from.a + (to.a - from.a) * f;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (mScene == null) return;
        float h = getHeight() - 8 * mDp;
        float w = h * ASPECT;
        float l = (getWidth() - w) / 2f;
        mFrame.set(l, 4 * mDp, l + w, 4 * mDp + h);
        float corner = 28 * mDp;
        int surface = SetupUi.surface(this);
        int onSurface = SetupUi.onSurface(this);
        int accent = SetupUi.accent(this);
        mFill.setStyle(Paint.Style.FILL);
        mFill.setColor(surface);
        canvas.drawRoundRect(mFrame, corner, corner, mFill);
        canvas.save();
        mClip.reset();
        mClip.addRoundRect(mFrame, corner, corner, Path.Direction.CW);
        canvas.clipPath(mClip);
        for (Block b : mScene.blocks) drawBlock(canvas, b, onSurface, accent, surface);
        drawBlock(canvas, mScene.ring, onSurface, accent, surface);
        canvas.restore();
        mStroke.setStrokeWidth(1.5f * mDp);
        mStroke.setColor(SetupUi.onSurfaceVariant(this));
        canvas.drawRoundRect(mFrame, corner, corner, mStroke);
    }

    private void drawBlock(Canvas canvas, Block b, int onSurface, int accent, int surface) {
        eval(b, mTimeMs);
        float alpha = Math.max(0f, Math.min(1f, mOut[4]));
        if (alpha <= 0.01f) return;
        float fw = mFrame.width(), fh = mFrame.height();
        if (b.kind == Kind.RING) {
            float cx = mFrame.left + mOut[0] * fw, cy = mFrame.top + mOut[1] * fh;
            float r = mOut[2] * fw;
            mFill.setStyle(Paint.Style.FILL);
            mFill.setColor(withAlpha(accent, 70 * alpha));
            canvas.drawCircle(cx, cy, r, mFill);
            mStroke.setStrokeWidth(2 * mDp);
            mStroke.setColor(withAlpha(accent, 255 * alpha));
            canvas.drawCircle(cx, cy, r, mStroke);
            return;
        }
        mR.set(mFrame.left + mOut[0] * fw, mFrame.top + mOut[1] * fh,
            mFrame.left + (mOut[0] + mOut[2]) * fw, mFrame.top + (mOut[1] + mOut[3]) * fh);
        float radius = Math.min(10 * mDp, Math.min(mR.width(), mR.height()) / 2f);
        switch (b.kind) {
            case WALLPAPER: fillRect(canvas, withAlpha(accent, 30 * alpha), 0); break;
            case TINT: fillRect(canvas, withAlpha(onSurface, 70 * alpha), 0); break;
            case TERMINAL:
                fillRect(canvas, withAlpha(onSurface, 34 * alpha), radius);
                lines(canvas, withAlpha(onSurface, 70 * alpha));
                break;
            case KEYBOARD: fillRect(canvas, withAlpha(onSurface, 48 * alpha), radius); break;
            case SHEET: fillRect(canvas, withAlpha(onSurface, 52 * alpha), radius); break;
            case POPOVER: fillRect(canvas, withAlpha(onSurface, 80 * alpha), radius); break;
            case MENU: fillRect(canvas, withAlpha(onSurface, 70 * alpha), radius); break;
            case FIELD: fillRect(canvas, withAlpha(surface, 255 * alpha), radius); break;
            case PILL:
                fillRect(canvas, withAlpha(onSurface, 90 * alpha), mR.height() / 2f);
                label(canvas, b, surface, alpha);
                break;
            case DOT:
                fillRect(canvas, withAlpha(accent, 255 * alpha), mR.height() / 2f);
                label(canvas, b, surface, alpha);
                break;
            case OUTLINE:
                mStroke.setStrokeWidth(2 * mDp);
                mStroke.setColor(withAlpha(accent, 255 * alpha));
                canvas.drawRoundRect(mR, radius, radius, mStroke);
                break;
            default: // STATUS, CLOCK, DOCK
                fillRect(canvas, withAlpha(onSurface, 42 * alpha), radius);
        }
    }

    private void fillRect(Canvas canvas, int color, float radius) {
        mFill.setStyle(Paint.Style.FILL);
        mFill.setColor(color);
        canvas.drawRoundRect(mR, radius, radius, mFill);
    }

    /** A few placeholder text bars inside a terminal block. */
    private void lines(Canvas canvas, int color) {
        mFill.setColor(color);
        float pad = 8 * mDp, bar = 4 * mDp, gap = 10 * mDp;
        int n = 0;
        for (float y = mR.top + pad; y + bar < mR.bottom - pad && n < 4; y += gap, n++) {
            float len = (mR.width() - 2 * pad) * (n % 2 == 0 ? 0.6f : 0.8f);
            canvas.drawRoundRect(mR.left + pad, y, mR.left + pad + len, y + bar, bar / 2f,
                bar / 2f, mFill);
        }
    }

    private void label(Canvas canvas, Block b, int color, float alpha) {
        if (b.label == null) return;
        mTextPaint.setColor(withAlpha(color, 255 * alpha));
        mTextPaint.setTextSize(Math.min(11 * mDp, mR.height() * 0.6f));
        canvas.drawText(b.label, mR.centerX(),
            mR.centerY() - (mTextPaint.ascent() + mTextPaint.descent()) / 2f, mTextPaint);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
