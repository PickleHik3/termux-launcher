package com.termux.app.statusbar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * Media widget for the reserved half of the widget slot. It renders the active {@code MediaSession} in
 * one of two forms: the full row (40dp art, title, subtitle, progress hairline, transport) or the
 * single-line strip used when a pinned notification also claims the slot.
 *
 * <p>Transport glyphs are 24dp visually; their hit rects are expanded to 40dp so the controls stay
 * usable inside a 96dp bar.
 */
public final class MediaWidgetView extends View {

    /** Full row height, per the design grid: 40dp art with 2dp of breathing room. */
    public static final float FULL_HEIGHT_DP = 44f;
    /** Collapsed strip height used in the contention layout. */
    public static final float STRIP_HEIGHT_DP = 20f;

    public enum Form {
        FULL,
        STRIP
    }

    private static final int TARGET_NONE = 0;
    private static final int TARGET_PREVIOUS = 1;
    private static final int TARGET_PLAY_PAUSE = 2;
    private static final int TARGET_NEXT = 3;
    private static final int TARGET_OWNER = 4;

    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Matrix mShaderMatrix = new Matrix();
    private final Rect mPreviousRect = new Rect();
    private final Rect mPlayPauseRect = new Rect();
    private final Rect mNextRect = new Rect();
    private final Rect mOwnerRect = new Rect();

    private Form mForm = Form.FULL;
    @Nullable private TopPaneMediaState mState;
    @Nullable private Drawable mAppIcon;
    private int mPressedTarget = TARGET_NONE;

    private int mOnSurface;
    private int mOnSurfaceVariant;
    private int mPrimary;

    public MediaWidgetView(Context context) {
        this(context, null);
    }

    public MediaWidgetView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(false);
        resolveColors();
    }

    private void resolveColors() {
        Context context = getContext();
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mOnSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, mOnSurface);
        mPrimary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
    }

    public void setForm(@NonNull Form form) {
        if (mForm == form) return;
        mForm = form;
        invalidate();
    }

    @NonNull
    public Form getForm() {
        return mForm;
    }

    public void setState(@Nullable TopPaneMediaState state, @Nullable Drawable appIcon) {
        mState = state;
        mAppIcon = appIcon;
        invalidate();
    }

    @Nullable
    public TopPaneMediaState getState() {
        return mState;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        TopPaneMediaState state = mState;
        if (state == null || getWidth() <= 0 || getHeight() <= 0) return;
        if (mForm == Form.STRIP) drawStrip(canvas, state);
        else drawFull(canvas, state);
    }

    private void drawFull(Canvas canvas, @NonNull TopPaneMediaState state) {
        float art = dp(40f);
        float top = (getHeight() - art) / 2f;
        drawArtwork(canvas, state, 0f, top, art, dp(4f), true);

        float transportWidth = dp(24f + 7f + 26f + 7f + 24f);
        float textLeft = art + dp(8f);
        float textRight = Math.max(textLeft, getWidth() - transportWidth - dp(8f));
        float textWidth = textRight - textLeft;

        mOwnerRect.set(0, Math.round(top), Math.round(textRight), Math.round(top + art));

        if (textWidth > dp(24f)) {
            // Title, subtitle and hairline share the 40dp art band with 6dp gaps.
            drawSingleLine(canvas, state.title, textLeft, top + dp(11.5f), textWidth, sp(10.5f),
                mediumTypeface(), mOnSurface, 255);
            drawSingleLine(canvas, state.subtitle(), textLeft, top + dp(27.5f), textWidth, sp(9.5f),
                Typeface.DEFAULT, mOnSurfaceVariant, 204);
            drawProgress(canvas, textLeft, top + dp(36.5f), textWidth, state.progress());
        }

        float transportLeft = getWidth() - transportWidth;
        drawTransport(canvas, transportLeft, getHeight() / 2f, state.playing, 24f, 26f, 7f, 14f, 255);
    }

    private void drawStrip(Canvas canvas, @NonNull TopPaneMediaState state) {
        mRect.set(0f, 0f, getWidth(), getHeight());
        mFillPaint.setShader(null);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(mPrimary, 20));
        canvas.drawRoundRect(mRect, dp(8f), dp(8f), mFillPaint);

        float art = dp(12f);
        float top = (getHeight() - art) / 2f;
        drawArtwork(canvas, state, dp(4f), top, art, dp(2f), false);

        float transportWidth = dp(18f * 3f + 5f * 2f);
        float textLeft = dp(4f) + art + dp(6f);
        float textRight = Math.max(textLeft, getWidth() - transportWidth - dp(6f));
        mOwnerRect.set(0, 0, Math.round(textRight), getHeight());
        if (textRight - textLeft > dp(16f)) {
            drawSingleLineCentered(canvas, state.stripLabel(), textLeft, getHeight() / 2f,
                textRight - textLeft, sp(8.5f), Typeface.MONOSPACE, mOnSurfaceVariant, 230);
        }
        drawTransport(canvas, getWidth() - transportWidth - dp(2f), getHeight() / 2f, state.playing,
            18f, 18f, 5f, 9f, 217);
    }

    private void drawArtwork(Canvas canvas, @NonNull TopPaneMediaState state, float left, float top,
                             float size, float radius, boolean stroke) {
        mRect.set(left, top, left + size, top + size);
        Bitmap art = state.art;
        if (art != null && !art.isRecycled() && art.getWidth() > 0 && art.getHeight() > 0) {
            BitmapShader shader = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float scale = size / Math.min(art.getWidth(), art.getHeight());
            mShaderMatrix.reset();
            mShaderMatrix.setScale(scale, scale);
            mShaderMatrix.postTranslate(left - (art.getWidth() * scale - size) / 2f,
                top - (art.getHeight() * scale - size) / 2f);
            shader.setLocalMatrix(mShaderMatrix);
            mFillPaint.setShader(shader);
            canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
            mFillPaint.setShader(null);
        } else {
            mFillPaint.setShader(null);
            mFillPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, 20));
            canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
            Drawable icon = mAppIcon;
            if (icon != null) {
                float inset = size * .18f;
                icon.setBounds(Math.round(left + inset), Math.round(top + inset),
                    Math.round(left + size - inset), Math.round(top + size - inset));
                icon.draw(canvas);
            }
        }
        if (!stroke) return;
        mFillPaint.setStyle(Paint.Style.STROKE);
        mFillPaint.setStrokeWidth(dp(1f));
        mFillPaint.setColor(Color.argb(15, 255, 255, 255));
        mRect.inset(dp(.5f), dp(.5f));
        canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
        mFillPaint.setStyle(Paint.Style.FILL);
    }

    private void drawProgress(Canvas canvas, float left, float top, float width, float progress) {
        mFillPaint.setShader(null);
        mRect.set(left, top, left + width, top + dp(2f));
        mFillPaint.setColor(Color.argb(36, 230, 238, 246));
        canvas.drawRoundRect(mRect, dp(1f), dp(1f), mFillPaint);
        if (progress <= 0f) return;
        mRect.set(left, top, left + width * progress, top + dp(2f));
        mFillPaint.setColor(mPrimary);
        canvas.drawRoundRect(mRect, dp(1f), dp(1f), mFillPaint);
    }

    private void drawTransport(Canvas canvas, float left, float centerY, boolean playing,
                               float boxDp, float playBoxDp, float gapDp, float glyphDp, int alpha) {
        float box = dp(boxDp), playBox = dp(playBoxDp), gap = dp(gapDp), glyph = dp(glyphDp);
        float x = left;
        setRect(mPreviousRect, x, centerY, box);
        drawGlyph(canvas, R.drawable.ic_media_skip_previous, mPreviousRect, glyph, alpha,
            mPressedTarget == TARGET_PREVIOUS);
        x += box + gap;

        setRect(mPlayPauseRect, x, centerY, playBox);
        mFillPaint.setShader(null);
        mFillPaint.setColor(Color.argb(mPressedTarget == TARGET_PLAY_PAUSE ? 51 : 31, 230, 238, 246));
        canvas.drawCircle(mPlayPauseRect.centerX(), mPlayPauseRect.centerY(), playBox / 2f, mFillPaint);
        drawGlyph(canvas, playing ? R.drawable.ic_media_pause : R.drawable.ic_media_play_arrow,
            mPlayPauseRect, glyph, alpha, false);
        x += playBox + gap;

        setRect(mNextRect, x, centerY, box);
        drawGlyph(canvas, R.drawable.ic_media_skip_next, mNextRect, glyph, alpha,
            mPressedTarget == TARGET_NEXT);
    }

    private void setRect(Rect out, float left, float centerY, float size) {
        out.set(Math.round(left), Math.round(centerY - size / 2f),
            Math.round(left + size), Math.round(centerY + size / 2f));
    }

    private void drawGlyph(Canvas canvas, int drawableRes, Rect box, float glyph, int alpha,
                           boolean pressed) {
        Drawable icon = AppCompatResources.getDrawable(getContext(), drawableRes);
        if (icon == null) return;
        icon = icon.mutate();
        icon.setTint(mOnSurface);
        icon.setAlpha(pressed ? Math.min(255, alpha + 40) : alpha);
        int half = Math.round(glyph / 2f);
        icon.setBounds(box.centerX() - half, box.centerY() - half,
            box.centerX() + half, box.centerY() + half);
        icon.draw(canvas);
    }

    private void drawSingleLineCentered(Canvas canvas, String text, float left, float centerY,
                                        float width, float textSize, Typeface typeface, int color,
                                        int alpha) {
        mTextPaint.setTypeface(typeface);
        mTextPaint.setTextSize(textSize);
        float baseline = centerY - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
        drawSingleLine(canvas, text, left, baseline, width, textSize, typeface, color, alpha);
    }

    private void drawSingleLine(Canvas canvas, String text, float left, float baseline, float width,
                                float textSize, Typeface typeface, int color, int alpha) {
        if (text.isEmpty() || width <= 0f) return;
        mTextPaint.setTypeface(typeface);
        mTextPaint.setTextSize(textSize);
        mTextPaint.setColor(color);
        mTextPaint.setAlpha(alpha);
        CharSequence clipped = TextUtils.ellipsize(text, mTextPaint, width, TextUtils.TruncateAt.END);
        canvas.drawText(clipped, 0, clipped.length(), left, baseline, mTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mState == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mPressedTarget = hitTarget(event.getX(), event.getY());
                if (mPressedTarget == TARGET_NONE) return false;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                return mPressedTarget != TARGET_NONE;
            case MotionEvent.ACTION_UP: {
                int target = mPressedTarget;
                mPressedTarget = TARGET_NONE;
                invalidate();
                if (target == TARGET_NONE || hitTarget(event.getX(), event.getY()) != target) {
                    return true;
                }
                performAction(target);
                return true;
            }
            default:
                mPressedTarget = TARGET_NONE;
                invalidate();
                return false;
        }
    }

    private void performAction(int target) {
        TopPaneMediaState state = mState;
        switch (target) {
            case TARGET_PREVIOUS:
                TopPaneFeed.skipPrevious();
                break;
            case TARGET_PLAY_PAUSE:
                if (state == null) return;
                boolean play = !state.playing;
                TopPaneFeed.togglePlayPause(play);
                TopPaneFeed.applyOptimisticPlayState(play);
                break;
            case TARGET_NEXT:
                TopPaneFeed.skipNext();
                break;
            case TARGET_OWNER:
                launchOwner();
                break;
            default:
                break;
        }
    }

    private void launchOwner() {
        TopPaneMediaState state = mState;
        if (state == null || state.packageName.isEmpty()) return;
        Intent launch = getContext().getPackageManager()
            .getLaunchIntentForPackage(state.packageName);
        if (launch == null) return;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(launch);
        } catch (Exception ignored) {
        }
    }

    /** Transport rects are expanded to a 40dp minimum so the 24dp glyphs stay tappable. */
    private int hitTarget(float x, float y) {
        if (inExpanded(mPlayPauseRect, x, y)) return TARGET_PLAY_PAUSE;
        if (inExpanded(mPreviousRect, x, y)) return TARGET_PREVIOUS;
        if (inExpanded(mNextRect, x, y)) return TARGET_NEXT;
        if (mOwnerRect.contains(Math.round(x), Math.round(y))) return TARGET_OWNER;
        return TARGET_NONE;
    }

    private boolean inExpanded(Rect rect, float x, float y) {
        if (rect.isEmpty()) return false;
        float minimum = dp(40f);
        float growX = Math.max(0f, (minimum - rect.width()) / 2f);
        float growY = Math.max(0f, (minimum - rect.height()) / 2f);
        return x >= rect.left - growX && x <= rect.right + growX
            && y >= rect.top - growY && y <= rect.bottom + growY;
    }

    private static Typeface mediumTypeface() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
