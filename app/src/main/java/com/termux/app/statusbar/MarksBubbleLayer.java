package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.MarksBubble;
import com.termux.app.wall.pageswitch.MarksLook;

/**
 * The Marks slide's bubble: a capsule with the page's disc form at its round head and its name
 * after it, one line, ellipsized. It is a drawable laid on a layer host's overlay for the length of
 * a slide, so it can stand past the status bar's bounds (the host's frame is the window's). Where
 * it stands comes from {@link MarksBubble}; this only draws. Drawing allocates nothing; the name is
 * ellipsized when it or the room changes, not per frame.
 */
final class MarksBubbleLayer extends Drawable {

    /** The capsule's height and the gap off the bar, in dp. */
    static final float HEIGHT_DP = 40f;
    static final float GAP_DP = 8f;
    static final float MARGIN_DP = 16f;
    private static final float PADDING_DP = 14f;
    private static final float MAX_WIDTH_DP = 220f;

    private final float mHeight;
    private final float mPadding;
    private final float mMaxWidth;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float mBaseline;
    private final MarksLook mLook = new MarksLook();
    private final RectF mRect = new RectF();

    @NonNull private PageKind mKind = PageKind.HOME;
    private int mNumber;
    @NonNull private String mLabel = "";
    @NonNull private CharSequence mShown = "";
    private float mShownFor = -1f;
    private float mTextWidth;
    private float mWidth;
    private float mLeft;
    private float mTop;
    private float mAppear = 1f;
    @ColorInt private int mDisc;

    MarksBubbleLayer(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        mHeight = HEIGHT_DP * density;
        mPadding = PADDING_DP * density;
        mMaxWidth = MAX_WIDTH_DP * density;
        mText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f,
            context.getResources().getDisplayMetrics()));
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Paint.FontMetrics fm = mText.getFontMetrics();
        mBaseline = -(fm.ascent + fm.descent) / 2f;
    }

    /** The gap off the bar and the margin inside the host, in px. */
    float gap() {
        return GAP_DP * mHeight / HEIGHT_DP;
    }

    float margin() {
        return MARGIN_DP * mHeight / HEIGHT_DP;
    }

    float height() {
        return mHeight;
    }

    float width() {
        return mWidth;
    }

    void setInks(@ColorInt int fill, @ColorInt int text, @ColorInt int disc) {
        mFill.setColor(fill);
        mText.setColor(text);
        mDisc = disc;
    }

    /** What the bubble names; re-measures only when the name or the host's room differs. */
    void setPage(@NonNull PageKind kind, int number, @NonNull String label, float hostWidth) {
        mKind = kind;
        mNumber = number;
        float room = Math.min(mMaxWidth, hostWidth - 2f * margin());
        if (!label.equals(mLabel) || room != mShownFor) {
            mLabel = label;
            mShownFor = room;
            mTextWidth = mText.measureText(label);
            float avail = Math.max(0f, room - mHeight - mPadding / 2f);
            mShown = mTextWidth <= avail ? label
                : TextUtils.ellipsize(label, mText, avail, TextUtils.TruncateAt.END);
            mTextWidth = Math.min(mTextWidth, avail);
        }
        mWidth = MarksBubble.widthFor(mHeight, mTextWidth, mPadding, room);
    }

    void moveTo(float left, float top) {
        mLeft = left;
        mTop = top;
    }

    void setAppear(float appear) {
        mAppear = appear;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mAppear <= 0f) return;
        float cx = mLeft + mWidth / 2f;
        float cy = mTop + mHeight / 2f;
        int save = canvas.save();
        canvas.scale(mAppear, mAppear, cx, cy);
        mRect.set(mLeft, mTop, mLeft + mWidth, mTop + mHeight);
        mFill.setAlpha(Math.round(255 * Math.min(1f, mAppear)));
        canvas.drawRoundRect(mRect, mHeight / 2f, mHeight / 2f, mFill);
        mLook.draw(canvas, mKind, mNumber, true, 0f, mLeft + mHeight / 2f, cy, mHeight * 0.55f, mDisc);
        if (mShown.length() > 0) {
            canvas.drawText(mShown, 0, mShown.length(), mLeft + mHeight, cy + mBaseline, mText);
        }
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
