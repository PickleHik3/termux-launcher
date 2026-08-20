package com.termux.app.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.font.NerdFontSpans;

/**
 * Compact trailing status-bar widget: a native vector icon plus a short value, e.g. a CPU/RAM
 * percentage or a temperature. Sized to sit on the same row as the window pills. Tapping it
 * is expected to open an anchored detail card; the widget itself is the anchor view.
 */
public final class StatusBarWidgetView extends LinearLayout {

    public enum ColorRole { PRIMARY, SECONDARY, TERTIARY }

    private final ImageView mIcon;
    private final TextView mGlyph;
    @Nullable private LottieAnimationView mAnimation;
    private final TextView mValue;
    @Nullable private String mAnimationAsset;
    private final Runnable mSettleAnimation = this::settleAnimation;
    private boolean mAccent;
    private boolean mMuted;
    @NonNull private ColorRole mColorRole = ColorRole.PRIMARY;

    public StatusBarWidgetView(Context context) {
        this(context, null);
    }

    public StatusBarWidgetView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setFocusable(true);
        setClipToPadding(false);
        setClipChildren(false);
        setPadding(dp(5), dp(1), dp(5), dp(1));
        setMinimumWidth(dp(34));

        mIcon = new ImageView(context);
        mIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mIcon, new LayoutParams(dp(15), dp(15)));

        // The glyph alternative to the vector icon, drawn with the bundled symbols face. One
        // Nerd Font name covers states a vector set would need a file each for — which is what
        // makes a full day/night weather mapping practical.
        mGlyph = new TextView(context);
        mGlyph.setGravity(Gravity.CENTER);
        mGlyph.setIncludeFontPadding(false);
        mGlyph.setSingleLine(true);
        mGlyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        mGlyph.setTypeface(NerdFontSpans.typeface(context));
        mGlyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mGlyph.setVisibility(GONE);
        addView(mGlyph, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mValue = new TextView(context);
        mValue.setGravity(Gravity.CENTER_VERTICAL);
        mValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mValue.setIncludeFontPadding(false);
        mValue.setSingleLine(true);
        LayoutParams valueParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(dp(2));
        addView(mValue, valueParams);

        applyColors();
    }

    /** Material vector resource shown before the value. */
    public void setIconResource(@DrawableRes int drawableRes) {
        mIcon.setImageResource(drawableRes);
        mIcon.setVisibility(VISIBLE);
        mGlyph.setVisibility(GONE);
        hideAnimation();
    }

    /**
     * A bundled Lottie animation shown before the value instead of an icon or a glyph. The view is
     * created on first use: only the weather widget has one, and inflating a Lottie view for the
     * CPU and RAM widgets that will never play anything is pure cost.
     *
     * @param assetPath a path under {@code assets/}, e.g. {@code weather/clear-day.json}
     */
    public void setIconAnimation(@NonNull String assetPath) {
        if (mAnimation == null) {
            mAnimation = new LottieAnimationView(getContext());
            // Plays once per condition change and holds its last frame. Looping pins the whole
            // activity to the panel's full refresh rate for as long as the status bar is up:
            // measured on Pong at 1215 frames/10s and 47% of a core, against 431 frames and 33%
            // with this line — and that 33% is the flip clock, which is there either way. The
            // card's headline icon still loops, because it only runs while the card is open.
            mAnimation.setRepeatCount(0);
            mAnimation.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            // Meteocons are multi-colour by design, so unlike the vector icons this one is not
            // tinted to the widget's Material role.
            addView(mAnimation, indexOfChild(mValue), new LayoutParams(dp(19), dp(19)));
        }
        if (!assetPath.equals(mAnimationAsset)) {
            mAnimationAsset = assetPath;
            mAnimation.setAnimation(assetPath);
            mAnimation.playAnimation();
        }
        mAnimation.setVisibility(VISIBLE);
        mIcon.setVisibility(GONE);
        mGlyph.setVisibility(GONE);
    }

    /**
     * A Nerd Font code point shown before the value instead of a vector icon. Falls back to
     * whatever icon is already set when the bundled face failed to load, so a widget is never
     * left drawing a tofu box where its icon was.
     */
    public void setIconGlyph(@NonNull CharSequence glyph) {
        if (mGlyph.getTypeface() == null) return;
        mGlyph.setText(glyph);
        mGlyph.setVisibility(VISIBLE);
        mIcon.setVisibility(GONE);
        hideAnimation();
        applyColors();
    }

    /**
     * Loops the icon animation for {@code durationMs}, then lets the current pass finish and holds
     * the last frame again.
     *
     * <p>The steady state deliberately does not loop — measured on Pong at 1215 frames/10s and 47%
     * of a core against 431 frames and 33% held still — so this is the bounded exception: a few
     * seconds of movement when the user has just arrived, where the animation is the point, and
     * then back to a still icon for the hours it sits there afterwards.
     */
    public void replayIconAnimation(long durationMs) {
        if (mAnimation == null || mAnimationAsset == null) return;
        if (getVisibility() != VISIBLE || mAnimation.getVisibility() != VISIBLE) return;
        removeCallbacks(mSettleAnimation);
        mAnimation.setRepeatCount(LottieDrawable.INFINITE);
        mAnimation.playAnimation();
        postDelayed(mSettleAnimation, durationMs);
    }

    /** Back to play-once: the running pass finishes and the icon holds its last frame. */
    private void settleAnimation() {
        if (mAnimation != null) mAnimation.setRepeatCount(0);
    }

    private void hideAnimation() {
        if (mAnimation == null) return;
        removeCallbacks(mSettleAnimation);
        mAnimation.setRepeatCount(0);
        mAnimation.setVisibility(GONE);
        mAnimation.pauseAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mSettleAnimation);
        settleAnimation();
        super.onDetachedFromWindow();
    }

    public void setValue(@NonNull CharSequence value) {
        mValue.setText(value);
    }

    /** Gives CPU, memory and weather distinct wallpaper-derived Material roles. */
    public void setColorRole(@NonNull ColorRole colorRole) {
        if (mColorRole == colorRole) return;
        mColorRole = colorRole;
        applyColors();
    }

    /** Accent styling used while the widget's detail card is open. */
    public void setAccent(boolean accent) {
        if (mAccent == accent) return;
        mAccent = accent;
        applyColors();
    }

    /**
     * Drains the colour out of the widget while keeping it on screen — the AI glyph's few seconds
     * of afterlife once its model unloaded, so the state reads as "was, isn't" rather than as a
     * widget that blinked out.
     */
    public void setMuted(boolean muted) {
        if (mMuted == muted) return;
        mMuted = muted;
        applyColors();
    }

    private void applyColors() {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int secondary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        int tertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, primary);
        int roleColor = mColorRole == ColorRole.SECONDARY ? secondary
            : mColorRole == ColorRole.TERTIARY ? tertiary : primary;
        if (mMuted) {
            roleColor = MaterialColors.getColor(context,
                com.termux.shared.R.attr.termuxColorOnSurfaceVariant, roleColor);
        }

        // The trailing stats read as one status cluster. Color and dot separators provide the
        // grouping, so individual pill backgrounds only add visual noise.
        setBackground(null);

        int alpha = mMuted ? 120 : mAccent ? 255 : 238;
        ImageViewCompat.setImageTintList(mIcon,
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(roleColor, alpha)));
        mGlyph.setTextColor(ColorUtils.setAlphaComponent(roleColor, alpha));
        mValue.setTextColor(ColorUtils.setAlphaComponent(roleColor, alpha));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
