package com.termux.app.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.theme.TextScale;

/**
 * Compact trailing status-bar widget: a native vector icon plus a short value, e.g. a CPU/RAM
 * percentage or a temperature. Sized to sit on the same row as the window pills. Tapping it
 * is expected to open an anchored detail card; the widget itself is the anchor view.
 */
public final class StatusBarWidgetView extends LinearLayout implements TextScale.Follower {

    public enum ColorRole { PRIMARY, SECONDARY, TERTIARY }

    /**
     * The icon's box, every mark's alike (CPU, RAM, the AI robot, the bell). The 24-unit Roundline
     * icons ink about 20 of it, 12.5dp here: the size the 11.5sp glyph read at.
     */
    private static final int ICON_BOX_DP = 15;

    /** Dresses this widget as the CPU reading: its icon and its tier, the primary role. */
    public void dressAsCpu() {
        setColorRole(ColorRole.PRIMARY);
        setIconResource(R.drawable.ic_rl_cpu);
    }

    /** Dresses this widget as the GPU load reading: its icon and its tier, the primary role. */
    public void dressAsGpu() {
        setColorRole(ColorRole.PRIMARY);
        setIconResource(R.drawable.ic_rl_gpu);
    }

    /** The font toggle's icon now shown, 0 before it has been dressed. */
    @DrawableRes private int mFontToggleIcon;

    /**
     * Dresses this widget as the font toggle: its tier (primary) and one of two marks, the second
     * while the focused pane is Compact. The mark is set only when it changes.
     */
    public void dressAsFontToggle(boolean compact) {
        setColorRole(ColorRole.PRIMARY);
        int icon = compact ? R.drawable.ic_rl_text_size_compact : R.drawable.ic_rl_text_size;
        if (mFontToggleIcon == icon) return;
        if (mFontToggleIcon == 0) setValue("");
        mFontToggleIcon = icon;
        setIconResource(icon);
    }

    /** Dresses this widget as the memory reading: its icon and its tier, the secondary role. */
    public void dressAsRam() {
        setColorRole(ColorRole.SECONDARY);
        setIconResource(R.drawable.ic_rl_memory);
    }

    /**
     * Dresses this widget as the battery reading: its tier (the tertiary role) and a drawn battery
     * in the icon's box instead of a glyph. Returns the picture to set the reading on; repeat calls
     * return the same one.
     */
    @NonNull
    BatteryIconDrawable dressAsBattery() {
        setColorRole(ColorRole.TERTIARY);
        if (mBatteryIcon == null) mBatteryIcon = new BatteryIconDrawable();
        setIconDrawable(mBatteryIcon);
        return mBatteryIcon;
    }

    /**
     * The role colour a tier is written in, from {@code context}'s theme (the launcher's scheme
     * chrome included): the seed every reading's measured ink is toned from.
     */
    @ColorInt
    public static int seedFor(@NonNull Context context, @NonNull ColorRole role) {
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        switch (role) {
            case SECONDARY:
                return MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorSecondary,
                    ContextCompat.getColor(context, R.color.termux_secondary));
            case TERTIARY:
                return MaterialColors.getColor(context,
                    com.termux.shared.R.attr.termuxColorTertiary, primary);
            default:
                return primary;
        }
    }

    private final GaugeIcon mIcon;
    @Nullable private BatteryIconDrawable mBatteryIcon;
    /** The weather's two-tint picture, built on first use: only the weather widget has one. */
    @Nullable private WeatherIconDrawable mWeatherIcon;
    @Nullable private WeatherIcons.Icon mWeatherShown;
    private final TextView mValue;
    private boolean mAccent;
    private boolean mMuted;
    @NonNull private ColorRole mColorRole = ColorRole.PRIMARY;
    /**
     * What the chrome measured this widget's band and decided its label, its icon and its muted
     * state have to be drawn in. Null until the bar has been measured — before the first wallpaper
     * sample, and in a preview or a test that never wires the chrome — and then the widget draws
     * what it always drew.
     */
    @Nullable private Integer mLabelInk;
    @Nullable private Integer mIconInk;
    @Nullable private Integer mMutedInk;
    /** The deck look's display colour while a terminal effect is on; null otherwise (wave 8). */
    @Nullable private Integer mDeckDisplay;
    /** The stats share a floor so a value ticking between widths does not shift its neighbours. */
    private static final int MIN_WIDTH_WITH_VALUE_DP = 34;
    /** The widget's own room at each end along a row, and what it keeps of it in a tight row. */
    private static final int SIDE_PADDING_DP = 5;
    private static final int TIGHT_SIDE_PADDING_DP = 2;
    /** Whether the row is short of room and the readings stand closer together. */
    private boolean mTight;
    private static final long VALUE_FADE_MS = 120L;

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
        setMinimumWidth(dp(MIN_WIDTH_WITH_VALUE_DP));

        mIcon = new GaugeIcon(context);
        mIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mIcon, new LayoutParams(dp(ICON_BOX_DP), dp(ICON_BOX_DP)));

        mValue = new TextView(context);
        mValue.setGravity(Gravity.CENTER_VERTICAL);
        mValue.setIncludeFontPadding(false);
        mValue.setSingleLine(true);
        LayoutParams valueParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(dp(2));
        addView(mValue, valueParams);

        applyTier();
        applyColors();
    }

    /**
     * Stacks the mark over its value instead of setting them side by side, for the status bar
     * standing in a column: there the widget has a finger's width and a whole column of height.
     */
    public void setStacked(boolean stacked) {
        int orientation = stacked ? VERTICAL : HORIZONTAL;
        if (getOrientation() == orientation) return;
        setOrientation(orientation);
        setGravity(stacked ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        applySizing();
        mValue.setGravity(stacked ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        LayoutParams valueParams = (LayoutParams) mValue.getLayoutParams();
        valueParams.setMarginStart(stacked ? 0 : dp(2));
        valueParams.topMargin = stacked ? dp(1) : 0;
        mValue.setLayoutParams(valueParams);
    }

    /** Material vector resource shown before the value. */
    public void setIconResource(@DrawableRes int drawableRes) {
        mIcon.setImageResource(drawableRes);
        mIcon.setVisibility(VISIBLE);
        mWeatherShown = null;
    }

    /**
     * A drawn picture shown before the value, in the icon's box. It takes the widget's icon ink as
     * its tint. Only on a change, so the repeat calls of a refresh pass cost nothing.
     */
    public void setIconDrawable(@NonNull Drawable drawable) {
        if (mIcon.getDrawable() != drawable) mIcon.setImageDrawable(drawable);
        if (mIcon.getVisibility() != VISIBLE) mIcon.setVisibility(VISIBLE);
        if (drawable != mWeatherIcon) mWeatherShown = null;
    }

    /**
     * The weather's picture before the value, in the icon's box: its cloud in the widget's icon ink
     * and its sun, moon or bolt in the accent. Only on a change, so the repeat calls of a refresh
     * pass cost nothing.
     */
    public void setWeatherIcon(@NonNull WeatherIcons.Icon icon) {
        if (mWeatherIcon == null) mWeatherIcon = new WeatherIconDrawable(getContext());
        if (mWeatherShown == icon && mIcon.getDrawable() == mWeatherIcon) return;
        setIconDrawable(mWeatherIcon);
        mWeatherShown = icon;
        applyColors();
    }

    /** Re-tints the weather's two layers with the ink and accent {@link #applyColors} settled on. */
    private void tintWeather(@ColorInt int ink, @ColorInt int accent) {
        if (mWeatherShown != null && mWeatherIcon != null) mWeatherIcon.set(mWeatherShown, ink, accent);
    }

    /**
     * The text after the icon. An empty value leaves the widget an icon alone: the text and its
     * margin go, and so does the minimum width the stats share, which would otherwise hold
     * twenty-odd dp of nothing to the right of the glyph.
     */
    public void setValue(@NonNull CharSequence value) {
        boolean empty = android.text.TextUtils.isEmpty(value);
        int visibility = empty ? GONE : VISIBLE;
        if (mValue.getVisibility() != visibility) mValue.setVisibility(visibility);
        applySizing();
        if (android.text.TextUtils.equals(mValue.getText(), value)) return;
        mValue.setText(value);
    }

    /**
     * {@link #setValue}, with the old text fading out and the new one in, for a change the user
     * did not ask for (the weather shortening to make room), so it does not jump.
     */
    public void setValueFading(@NonNull CharSequence value) {
        if (android.text.TextUtils.equals(mValue.getText(), value)
                || !isAttachedToWindow() || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            setValue(value);
            return;
        }
        mValue.animate().cancel();
        mValue.animate().alpha(0f).setDuration(VALUE_FADE_MS).withEndAction(() -> {
            setValue(value);
            mValue.animate().alpha(1f).setDuration(VALUE_FADE_MS).start();
        }).start();
    }

    /** How wide {@code value} draws in the value's text. */
    public float valueWidthOf(@NonNull CharSequence value) {
        return mValue.getPaint().measureText(value, 0, value.length());
    }

    /** The widget less its text: the icon and the gap after it. */
    public int widthWithoutValue() {
        return mValue.getVisibility() == VISIBLE ? getWidth() - mValue.getWidth() : getWidth();
    }

    /** Gives CPU, memory and weather distinct wallpaper-derived Material roles. */
    public void setColorRole(@NonNull ColorRole colorRole) {
        if (mColorRole == colorRole) return;
        mColorRole = colorRole;
        applyTier();
        applyColors();
    }

    /** Which tier this widget is, so a caller resolving the bar's ink knows which hue to ask for. */
    @NonNull
    public ColorRole colorRole() {
        return mColorRole;
    }

    /**
     * What the chrome says this widget's label, icon and muted state read as on the band it is
     * standing on — each already resolved against the measured backdrop at its own contrast tier,
     * so the widget spends no alpha of its own on any of them.
     *
     * <p>The hierarchy between the three widgets does not live here: it is {@link StatusBarInk}'s
     * weight and size, applied in {@link #applyTier()}, and it costs no contrast. This is only the
     * colour, and every tier's colour clears its own floor.</p>
     */
    public void setInk(@ColorInt int labelInk, @ColorInt int iconInk, @ColorInt int mutedInk) {
        if (mLabelInk != null && mLabelInk == labelInk && mIconInk != null && mIconInk == iconInk
            && mMutedInk != null && mMutedInk == mutedInk) {
            return;
        }
        mLabelInk = labelInk;
        mIconInk = iconInk;
        mMutedInk = mutedInk;
        applyColors();
    }

    /**
     * The deck look (wave 8): the reading lit in the display window's colour with a soft glow
     * while a terminal effect is on; {@code null} puts the chrome's ink back. Sizes unchanged.
     */
    public void setDeckLook(@Nullable com.termux.app.terminal.DeckLook deck) {
        Integer display = deck == null ? null : deck.display;
        if (java.util.Objects.equals(mDeckDisplay, display)) return;
        mDeckDisplay = display;
        float glow = display == null ? 0f : dp(4);
        int glowColour = display == null ? 0 : ColorUtils.setAlphaComponent(display, 153);
        mValue.setShadowLayer(glow, 0f, 0f, glowColour);
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
            com.termux.shared.R.attr.termuxColorTertiary, primary);
        int roleColor = mColorRole == ColorRole.SECONDARY ? secondary
            : mColorRole == ColorRole.TERTIARY ? tertiary : primary;
        if (mMuted) {
            roleColor = MaterialColors.getColor(context,
                com.termux.shared.R.attr.termuxColorOnSurfaceVariant, roleColor);
        }

        // The trailing stats read as one status cluster. Color and dot separators provide the
        // grouping, so individual pill backgrounds only add visual noise.
        if (getBackground() != null) setBackground(null);

        // The deck's display window: every reading lit in the display colour, a muted one dimmed.
        if (mDeckDisplay != null) {
            int lit = mMuted ? ColorUtils.setAlphaComponent(mDeckDisplay, 120) : mDeckDisplay;
            ImageViewCompat.setImageTintList(mIcon, ColorStateList.valueOf(lit));
            tintWeather(lit, lit);
            mIcon.setEmptyColor(ColorUtils.setAlphaComponent(mDeckDisplay, 90));
            mValue.setTextColor(lit);
            return;
        }

        // Measured ink wins outright. It was resolved on what this band is really drawn over and
        // it carries whatever alpha that leaves room for, so laying the tier's old alpha over it
        // would spend exactly the contrast it was resolved to find.
        if (mLabelInk != null && mIconInk != null && mMutedInk != null) {
            int label = mMuted ? mMutedInk : mLabelInk;
            int icon = mMuted ? mMutedInk : mIconInk;
            ImageViewCompat.setImageTintList(mIcon, ColorStateList.valueOf(icon));
            tintWeather(icon, mMuted ? mMutedInk : primary);
            mIcon.setEmptyColor(mMutedInk);
            mValue.setTextColor(label);
            return;
        }

        // A gauge's empty part is the grey the unload tail wears.
        mIcon.setEmptyColor(ColorUtils.setAlphaComponent(MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, roleColor), 120));
        int alpha = mMuted ? 120 : mAccent ? 255 : 238;
        ImageViewCompat.setImageTintList(mIcon,
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(roleColor, alpha)));
        tintWeather(ColorUtils.setAlphaComponent(roleColor, alpha),
            ColorUtils.setAlphaComponent(mMuted ? roleColor : primary, alpha));
        mValue.setTextColor(ColorUtils.setAlphaComponent(roleColor, alpha));
    }

    @Override public void onTextScaleChanged() {
        TextScale.apply(mValue, StatusBarInk.textSizeSpFor(mColorRole));
    }

    /**
     * The tier's weight and size. Since 2026-09-17 all three tiers share the temperature's, so
     * this is one answer applied three times; the hierarchy is hue and order.
     */
    private void applyTier() {
        TextScale.apply(mValue, StatusBarInk.textSizeSpFor(mColorRole));
        int weight = StatusBarInk.weightFor(mColorRole);
        // The two-argument form so a heavy tier would be bold even where the platform has no bold
        // cut of the medium face: TextView then asks the paint for it rather than dropping it. At
        // today's one shared weight (400) this resolves to the plain face for every tier.
        mValue.setTypeface(
            Typeface.create(weight >= 500 ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL),
            weight >= 700 ? Typeface.BOLD : Typeface.NORMAL);
    }

    /**
     * Turns the vector icon into a vertical gauge: the part below {@code fraction} of its ink
     * (between {@code inkTop} and {@code inkBottom}, fractions of the icon's height) in the
     * icon's own colour, the part above in the grey a muted widget wears. A negative fraction
     * puts the plain icon back. Redrawn only when the fill line crosses a pixel row.
     */
    public void setIconGauge(float fraction, float inkTop, float inkBottom) {
        mIcon.setGauge(fraction, inkTop, inkBottom);
    }

    /**
     * Stands closer to its neighbours along a row that is short of room: each end keeps
     * {@value #TIGHT_SIDE_PADDING_DP}dp instead of {@value #SIDE_PADDING_DP}, and the floor the
     * stats share shrinks by the same, so the figure itself never moves inside the widget. A
     * widget standing in a column is not affected.
     */
    public void setTight(boolean tight) {
        if (mTight == tight) return;
        mTight = tight;
        applySizing();
    }

    /** What tightening saves along a row, at both ends together; nothing in a column. */
    public int tightSavingPx() {
        return getOrientation() == VERTICAL ? 0
            : 2 * (dp(SIDE_PADDING_DP) - dp(TIGHT_SIDE_PADDING_DP));
    }

    /** The room before the icon along a row, tight or not. */
    public int leadingPaddingPx(boolean tight) {
        if (getOrientation() == VERTICAL) return getPaddingStart();
        return dp(tight ? TIGHT_SIDE_PADDING_DP : SIDE_PADDING_DP);
    }

    /**
     * Padding and the shared floor for the form the widget is in. Only on a change:
     * View.setMinimumWidth and setPadding request a layout every time they are called, and this
     * runs from the stats cluster's layout listener (through setValue) and from the row's measure.
     * Unconditional, it scheduled a layout from every layout for as long as the Widgets place was
     * up (2026-10-07).
     */
    private void applySizing() {
        boolean stacked = getOrientation() == VERTICAL;
        int side = dp(stacked ? 1 : mTight ? TIGHT_SIDE_PADDING_DP : SIDE_PADDING_DP);
        int across = dp(stacked ? 3 : 1);
        if (getPaddingLeft() != side || getPaddingRight() != side
            || getPaddingTop() != across || getPaddingBottom() != across) {
            setPadding(side, across, side, across);
        }
        // An icon alone (no value) has no figure to share a floor with.
        int minimumWidth = stacked || mValue.getVisibility() != VISIBLE ? 0
            : dp(MIN_WIDTH_WITH_VALUE_DP) - (mTight ? tightSavingPx() : 0);
        if (getMinimumWidth() != minimumWidth) setMinimumWidth(minimumWidth);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * The vector icon, drawn once whole or, as a gauge, twice: the empty part above the fill line
     * through a layer tinted grey, the filled part below it as the icon is.
     */
    private static final class GaugeIcon extends androidx.appcompat.widget.AppCompatImageView {
        private final android.graphics.Paint mEmptyPaint = new android.graphics.Paint();
        @Nullable private Integer mEmptyColor;
        private float mFraction = -1f;
        private float mInkTop;
        private float mInkBottom = 1f;
        /** The fill line's pixel row as last drawn, or -1 for the plain icon. */
        private int mFillTop = -1;

        GaugeIcon(Context context) {
            super(context);
        }

        void setEmptyColor(@ColorInt int color) {
            if (mEmptyColor != null && mEmptyColor == color) return;
            mEmptyColor = color;
            android.graphics.ColorFilter filter = new android.graphics.PorterDuffColorFilter(
                color, android.graphics.PorterDuff.Mode.SRC_IN);
            mEmptyPaint.setColorFilter(filter);
            if (mFillTop > 0) invalidate();
        }

        void setGauge(float fraction, float inkTop, float inkBottom) {
            mFraction = fraction;
            mInkTop = inkTop;
            mInkBottom = inkBottom;
            int row = fillTop(getHeight());
            if (row == mFillTop) return;
            mFillTop = row;
            invalidate();
        }

        private int fillTop(int height) {
            if (mFraction < 0f || height <= 0) return -1;
            return AiUnloadGauge.fillTopRow(mFraction, Math.round(height * mInkTop),
                Math.round(height * mInkBottom));
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            mFillTop = fillTop(h);
        }

        @Override protected void onDraw(Canvas canvas) {
            int top = mFillTop;
            if (top < 0) {
                super.onDraw(canvas);
                return;
            }
            int width = getWidth();
            int height = getHeight();
            if (top > 0) {
                int layer = canvas.saveLayer(0f, 0f, width, top, mEmptyPaint);
                super.onDraw(canvas);
                canvas.restoreToCount(layer);
            }
            if (top < height) {
                int clip = canvas.save();
                canvas.clipRect(0, top, width, height);
                super.onDraw(canvas);
                canvas.restoreToCount(clip);
            }
        }
    }

}
