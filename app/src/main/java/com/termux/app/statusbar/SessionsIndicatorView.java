package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.terminal.TerminalNamePolicy;

/**
 * Session label sitting to the left of the window pills. Named sessions show their name; unnamed
 * sessions show their one-based number. Tapping it opens the session switcher.
 */
public final class SessionsIndicatorView extends LinearLayout {

    private final AppCompatTextView mLabel;
    private boolean mCapsuleSurface;
    private float mStatusBarRadiusPx;
    private boolean mShowingSessionNumber = true;

    public SessionsIndicatorView(Context context) {
        this(context, null);
    }

    public SessionsIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        setClipToPadding(false);
        setClipChildren(false);
        setMinimumWidth(dp(18));
        setPaddingRelative(dp(3), 0, dp(3), 0);

        mLabel = new InkCenteredTextView(context);
        mLabel.setGravity(Gravity.CENTER);
        mLabel.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        mLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mLabel.setIncludeFontPadding(false);
        mLabel.setSingleLine(true);
        mLabel.setEllipsize(TextUtils.TruncateAt.END);
        mLabel.setMaxWidth(dp(88));
        addView(mLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        applyColors();
    }

    /** Match the session container to the selected status-bar surface geometry. */
    public void setSurfaceStyle(boolean capsule, float statusBarRadiusPx) {
        float radius = Math.max(0f, statusBarRadiusPx);
        if (mCapsuleSurface == capsule && mStatusBarRadiusPx == radius) return;
        mCapsuleSurface = capsule;
        mStatusBarRadiusPx = radius;
        applyColors();
    }

    /** currentIndex is 0-based; label is the session name or one-based fallback number. */
    public void setSession(@Nullable CharSequence label, int count, int currentIndex) {
        if (count <= 0) {
            mShowingSessionNumber = true;
            mLabel.setText("0");
            setContentDescription(getResources().getString(R.string.termux_sessions_indicator_empty_content_description));
            syncWidthToCurrentLabel();
            requestLayout();
            return;
        }
        int position = Math.max(0, Math.min(currentIndex, count - 1)) + 1;
        String sessionName = TerminalNamePolicy.normalizeSession(label);
        mShowingSessionNumber = TextUtils.isEmpty(sessionName);
        mLabel.setText(mShowingSessionNumber ? Integer.toString(position) : sessionName);
        setContentDescription(mShowingSessionNumber
            ? getResources().getString(
                R.string.termux_sessions_indicator_content_description, position, count)
            : getResources().getString(
                R.string.termux_named_sessions_indicator_content_description,
                position, count, sessionName));
        syncWidthToCurrentLabel();
        requestLayout();
    }

    /** Used by the status-row geometry pass to keep unnamed sessions square at its current height. */
    public boolean isShowingSessionNumber() {
        return mShowingSessionNumber;
    }

    private void syncWidthToCurrentLabel() {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) return;
        int targetWidth = mShowingSessionNumber && params.height > 0
            ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (params.width == targetWidth) return;
        params.width = targetWidth;
        setLayoutParams(params);
    }

    /** Numeric indicators stay circular/square as the expanded and collapsed row heights change. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mShowingSessionNumber || getMeasuredHeight() <= 0) return;
        // LinearLayout may issue a second EXACT width pass using the previous collapsed width.
        // Numeric indicators are intentionally height-authoritative so that stale pass cannot
        // turn a 22dp expanded circle into a 20x22dp oval.
        int squareWidth = getMeasuredHeight();
        if (squareWidth != getMeasuredWidth()) {
            setMeasuredDimension(squareWidth, getMeasuredHeight());
        }
    }

    private void applyColors() {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int secondary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        int tertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, primary);
        int tertiaryContainer = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiaryContainer, secondary);
        int onTertiaryContainer = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnTertiaryContainer,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
                ContextCompat.getColor(context, R.color.termux_on_surface)));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(mCapsuleSurface ? mStatusBarRadiusPx : 0f);
        chip.setColor(ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(tertiaryContainer, tertiary, .22f), 198));
        chip.setStroke(dp(1), tertiary);
        setBackground(chip);

        mLabel.setTextColor(onTertiaryContainer);
    }

    /** Alpha-weighted visual center of the rendered label, relative to its text origin. */
    static float opticalCenterOffset(Paint paint, CharSequence text) {
        if (text == null || text.length() == 0) return 0f;
        String value = text.toString();
        Rect inkBounds = new Rect();
        paint.getTextBounds(value, 0, value.length(), inkBounds);
        if (inkBounds.isEmpty()) return 0f;

        int padding = 2;
        int bitmapWidth = inkBounds.width() + padding * 2;
        int bitmapHeight = inkBounds.height() + padding * 2;
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || bitmapWidth > 512 || bitmapHeight > 256) {
            return paint.measureText(value) / 2f - inkBounds.exactCenterX();
        }

        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        try {
            Paint samplePaint = new Paint(paint);
            samplePaint.setColor(Color.WHITE);
            samplePaint.setAlpha(255);
            samplePaint.setShader(null);
            samplePaint.setColorFilter(null);
            samplePaint.clearShadowLayer();
            samplePaint.setStyle(Paint.Style.FILL);
            samplePaint.setTextAlign(Paint.Align.LEFT);
            float originX = padding - inkBounds.left;
            float baseline = padding - inkBounds.top;
            new Canvas(bitmap).drawText(value, originX, baseline, samplePaint);

            int[] pixels = new int[bitmapWidth * bitmapHeight];
            bitmap.getPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight);
            float visualCenter = alphaWeightedCenterX(pixels, bitmapWidth, originX);
            return Float.isNaN(visualCenter) ? 0f
                : paint.measureText(value) / 2f - visualCenter;
        } finally {
            bitmap.recycle();
        }
    }

    /** Returns the horizontal alpha centroid relative to {@code originX}. */
    static float alphaWeightedCenterX(int[] pixels, int width, float originX) {
        if (pixels == null || width <= 0) return Float.NaN;
        long totalAlpha = 0L;
        double weightedX = 0d;
        for (int index = 0; index < pixels.length; index++) {
            int alpha = Color.alpha(pixels[index]);
            if (alpha == 0) continue;
            totalAlpha += alpha;
            weightedX += alpha * ((index % width) + .5d);
        }
        return totalAlpha == 0L ? Float.NaN
            : (float) (weightedX / totalAlpha - originX);
    }

    /** TextView centers the advance box; this cached draw offset centers the visible ink mass. */
    private static final class InkCenteredTextView extends AppCompatTextView {

        @Nullable private String mCachedText;
        @Nullable private Typeface mCachedTypeface;
        private float mCachedTextSize = Float.NaN;
        private float mCachedTextScaleX = Float.NaN;
        private float mCachedTextSkewX = Float.NaN;
        private float mCachedOffset;

        InkCenteredTextView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float offset = resolveOpticalOffset();
            if (offset == 0f) {
                super.onDraw(canvas);
                return;
            }
            int saveCount = canvas.save();
            canvas.translate(offset, 0f);
            super.onDraw(canvas);
            canvas.restoreToCount(saveCount);
        }

        private float resolveOpticalOffset() {
            Paint paint = getPaint();
            String text = getText() == null ? "" : getText().toString();
            Typeface typeface = paint.getTypeface();
            float textSize = paint.getTextSize();
            float textScaleX = paint.getTextScaleX();
            float textSkewX = paint.getTextSkewX();
            if (text.equals(mCachedText) && typeface == mCachedTypeface
                && Float.compare(textSize, mCachedTextSize) == 0
                && Float.compare(textScaleX, mCachedTextScaleX) == 0
                && Float.compare(textSkewX, mCachedTextSkewX) == 0) {
                return mCachedOffset;
            }
            mCachedText = text;
            mCachedTypeface = typeface;
            mCachedTextSize = textSize;
            mCachedTextScaleX = textScaleX;
            mCachedTextSkewX = textSkewX;
            mCachedOffset = opticalCenterOffset(paint, text);
            return mCachedOffset;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
