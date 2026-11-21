package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * The page bar's bubble: a round glass disc over the pill holding the focused glyph at
 * {@link PageBarLayout#FOCUS_SCALE}, with the page's name above it, one line, ellipsized to the
 * pane less the side insets. It is a child of the pane, made on the first slide and kept; the bar
 * moves it by translation and binds what it shows, so nothing lays out while a finger slides.
 * The view is as wide as the pane and as tall as label plus disc; only the disc and the text draw.
 */
final class PageBarBubbleView extends ViewGroup {

    private static final float LABEL_TEXT_SP = 13f;

    private final PageBarGlyphPainter mPainter;
    private final PaneGlassBackdropView mGlass;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float mDensity;
    private final float mDiameter;
    private final float mAscent;
    private final float mHeight;

    @NonNull private String mLabel = "";
    @Nullable private CharSequence mEllipsized;
    private float mEllipsizedFor = -1f;
    @NonNull private PageKind mKind = PageKind.HOME;
    private int mNumber;
    @ColorInt private int mAccent;
    @ColorInt private int mInk;
    /** The pill's fill the disc wears ({@link #dress}). */
    @ColorInt private int mBodyFill;
    /** The label's ink, measured on the wallpaper it stands on ({@link #dress}). */
    @ColorInt private int mLabelInk = android.graphics.Color.WHITE;
    /** The disc's centre x within the pane, clamped by the layout. */
    private float mCenterX;
    private boolean mGlassShown;

    PageBarBubbleView(@NonNull Context context, @NonNull PageBarGlyphPainter painter) {
        super(context);
        mPainter = painter;
        mDensity = context.getResources().getDisplayMetrics().density;
        mDiameter = PageBarLayout.BUBBLE_DIAMETER_DP * mDensity;
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(mDensity);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mText.setTextSize(android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP,
            context.getResources().getDisplayMetrics()));
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mText.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = mText.getFontMetrics();
        mAscent = -fm.ascent;
        mHeight = mAscent + PageBarLayout.LABEL_GAP_DP * mDensity + mDiameter;
        mGlass = new PaneGlassBackdropView(context);
        mGlass.setClipToOutline(true);
        mGlass.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        com.termux.app.terminal.PaneGlass.followLayout(mGlass);
        mGlass.setVisibility(GONE);
        addView(mGlass);
    }

    /** The height this view asks for, for the layout params its parent gives it. */
    int heightPx() {
        return Math.round(mHeight);
    }

    /** Where this view's top stands so the disc's centre is {@code discCenterY} in the parent. */
    float topForDiscCenter(float discCenterY) {
        return discCenterY - (mAscent + PageBarLayout.LABEL_GAP_DP * mDensity + mDiameter / 2f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.round(mHeight));
        int d = Math.round(mDiameter);
        mGlass.measure(MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(d, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int top = Math.round(mAscent + PageBarLayout.LABEL_GAP_DP * mDensity);
        mGlass.layout(0, top, mGlass.getMeasuredWidth(), top + mGlass.getMeasuredHeight());
        placeGlass();
    }

    /**
     * Dresses the disc as the pill is dressed: the Look's glass drawn through {@code evenOut} with
     * the pill's {@code fill} over it (lift and veil), or, where the solid fallback applies, the
     * pill's card ({@code fill}) under the mock's translucent disc. The label above stands on the
     * wallpaper and takes {@code labelInk}. A dress pass, never per frame.
     */
    void dress(@Nullable PaneSurfaceStyle look, @ColorInt int fill,
               @Nullable android.graphics.ColorMatrix evenOut, @ColorInt int labelInk) {
        mGlassShown = PageBarGlass.dress(getContext(), look, this, mGlass, mDiameter / 2f);
        mGlass.setEvenOut(mGlassShown ? evenOut : null);
        mGlass.invalidateOutline();
        mBodyFill = fill;
        mLabelInk = labelInk;
        invalidate();
    }

    /**
     * What the bubble shows now. Invalidates only when something it draws changed.
     *
     * @param centerX the disc's centre x in the pane
     */
    void bind(@NonNull String label, @NonNull PageKind kind, int number, float centerX,
              @ColorInt int accent, @ColorInt int ink) {
        boolean changed = !label.equals(mLabel) || kind != mKind || number != mNumber
            || accent != mAccent || ink != mInk;
        boolean moved = centerX != mCenterX;
        if (!label.equals(mLabel)) {
            mLabel = label;
            mEllipsizedFor = -1f;
        }
        mKind = kind;
        mNumber = number;
        mAccent = accent;
        mInk = ink;
        mCenterX = centerX;
        if (moved) placeGlass();
        if (changed || moved) invalidate();
    }

    private void placeGlass() {
        float x = mCenterX - mDiameter / 2f;
        if (mGlass.getTranslationX() != x) {
            mGlass.setTranslationX(x);
            if (mGlass.getVisibility() == VISIBLE) mGlass.invalidateGlassPosition();
        }
    }

    /** Gives the glass back: its live picture, if it holds one, and its frame. */
    void release() {
        if (mGlass.livePicture() != null) mGlass.setLivePicture(null);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        float discLeft = mCenterX - mDiameter / 2f;
        float discTop = mAscent + PageBarLayout.LABEL_GAP_DP * mDensity;
        float discCenterY = discTop + mDiameter / 2f;
        if (android.graphics.Color.alpha(mBodyFill) != 0) {
            // The pill's body: its lift and veil over the glass, or its card when solid.
            mFill.setColor(mBodyFill);
            canvas.drawCircle(mCenterX, discCenterY, mDiameter / 2f, mFill);
        }
        if (!mGlassShown) {
            // The solid fallback: the pill's own translucent fill, drawn as a disc.
            mFill.setColor(com.termux.app.chrome.OnGlass.withAlpha(mInk,
                PageBarView.MOCK_FILL_ALPHA));
            canvas.drawCircle(mCenterX, discCenterY, mDiameter / 2f, mFill);
            mStroke.setColor(com.termux.app.chrome.OnGlass.withAlpha(mInk,
                PageBarView.MOCK_STROKE_ALPHA));
            canvas.drawCircle(mCenterX, discCenterY, mDiameter / 2f - 0.5f, mStroke);
        }
        float glyph = PageBarLayout.GLYPH_DP * mDensity * PageBarLayout.FOCUS_SCALE;
        mPainter.draw(canvas, mKind, mNumber, discLeft + mDiameter / 2f, discTop + mDiameter / 2f,
            glyph, mAccent);
        float inset = PageBarLayout.SIDE_INSET_DP * mDensity;
        float room = Math.max(0f, getWidth() - 2f * inset);
        if (mEllipsizedFor != room) {
            mEllipsized = TextUtils.ellipsize(mLabel, mText, room, TextUtils.TruncateAt.END);
            mEllipsizedFor = room;
        }
        CharSequence text = mEllipsized;
        if (text == null || text.length() == 0) return;
        float width = mText.measureText(text, 0, text.length());
        float x = Math.max(inset, Math.min(getWidth() - inset - width, mCenterX - width / 2f));
        mText.setColor(mLabelInk);
        canvas.drawText(text, 0, text.length(), x, mAscent, mText);
    }

}
