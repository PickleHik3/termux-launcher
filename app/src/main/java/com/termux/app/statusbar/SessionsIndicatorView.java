package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.Locale;

/**
 * Session label sitting to the left of the window pills. Named sessions show their name; unnamed
 * sessions show their one-based number. Tapping it opens the session switcher.
 */
public final class SessionsIndicatorView extends LinearLayout {

    private final OpticallyCenteredTextView mLabel;
    private boolean mCapsuleSurface;
    private float mStatusBarRadiusPx;
    private boolean mNumericSession = true;

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

        mLabel = new OpticallyCenteredTextView(context);
        mLabel.setGravity(Gravity.CENTER);
        mLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mLabel.setIncludeFontPadding(false);
        mLabel.setSingleLine(true);
        mLabel.setEllipsize(TextUtils.TruncateAt.END);
        mLabel.setMaxWidth(dp(88));
        addView(mLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

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
            mNumericSession = true;
            mLabel.setOpticalCenteringEnabled(true);
            mLabel.setText("0");
            setContentDescription(getResources().getString(R.string.termux_sessions_indicator_empty_content_description));
            syncWidthToCurrentLabel();
            requestLayout();
            return;
        }
        int position = Math.max(0, Math.min(currentIndex, count - 1)) + 1;
        boolean usePositionFallback = TextUtils.isEmpty(label);
        mNumericSession = usePositionFallback || TextUtils.isDigitsOnly(label);
        mLabel.setOpticalCenteringEnabled(mNumericSession);
        mLabel.setText(usePositionFallback ? String.format(Locale.ROOT, "%d", position) : label);
        setContentDescription(getResources().getString(
            R.string.termux_sessions_indicator_content_description, position, count));
        syncWidthToCurrentLabel();
        requestLayout();
    }

    /** Used by the status-row geometry pass to keep unnamed sessions square at its current height. */
    public boolean isNumericSession() {
        return mNumericSession;
    }

    private void syncWidthToCurrentLabel() {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) return;
        int targetWidth = mNumericSession && params.height > 0
            ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (params.width == targetWidth) return;
        params.width = targetWidth;
        setLayoutParams(params);
    }

    /** Numeric indicators stay circular/square as the expanded and collapsed row heights change. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mNumericSession || getMeasuredHeight() <= 0) return;
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

    /** Centers visible glyph pixels, not the font's asymmetric advance box (notably numeral 1). */
    private static final class OpticallyCenteredTextView extends TextView {
        private final Rect mInkBounds = new Rect();
        private boolean mOpticalCenteringEnabled;

        OpticallyCenteredTextView(Context context) {
            super(context);
        }

        void setOpticalCenteringEnabled(boolean enabled) {
            if (mOpticalCenteringEnabled == enabled) return;
            mOpticalCenteringEnabled = enabled;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            CharSequence value = getText();
            if (!mOpticalCenteringEnabled || TextUtils.isEmpty(value)) {
                super.onDraw(canvas);
                return;
            }
            String text = value.toString();
            getPaint().getTextBounds(text, 0, text.length(), mInkBounds);
            float advanceCenter = getPaint().measureText(text) / 2f;
            float inkCenter = (mInkBounds.left + mInkBounds.right) / 2f;
            int checkpoint = canvas.save();
            canvas.translate(advanceCenter - inkCenter, 0f);
            super.onDraw(canvas);
            canvas.restoreToCount(checkpoint);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
