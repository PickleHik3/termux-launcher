package com.termux.app.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.app.theme.SchemeTone;

/**
 * A weather picture drawn in two tints: the base layer (cloud, rain, snow, fog) in the ink and
 * the accent layer (sun, moon, stars, bolt) in the accent. Each layer is a mutated copy of the
 * shared vector, loaded when the icon changes and never in {@link #draw}. A flip of the ink only
 * re-tints the two layers, so a light/dark change redraws correctly without a reload.
 *
 * <p>The view's own image tint is ignored on purpose ({@link #setTintList} stays a no-op): one
 * tint over both layers would flatten the picture to one colour.
 */
public final class WeatherIconDrawable extends Drawable {
    /** Loads one layer's vector by resource id; a test hands in its own. */
    interface LayerLoader { @Nullable Drawable load(@DrawableRes int res); }

    @NonNull private final LayerLoader mLoader;
    @Nullable private WeatherIcons.Icon mIcon;
    @Nullable private Drawable mBase;
    @Nullable private Drawable mAccent;
    @ColorInt private int mInk;
    @ColorInt private int mAccentColor;
    /** The accent actually drawn: {@link #legibleAccent} of what was asked for. */
    @ColorInt private int mAccentTint;
    /** The ink is light from this HCT tone up, dark below it. */
    static final double INK_SIDE_TONE = 50d;
    /** The least tone an accent has beside a light ink, and the most beside a dark one. */
    static final double ACCENT_TONE_ON_LIGHT_INK = 80d;
    static final double ACCENT_TONE_ON_DARK_INK = 40d;
    private int mAlpha = 255;

    public WeatherIconDrawable(@NonNull Context context) {
        this(loaderFor(context.getApplicationContext() != null ? context.getApplicationContext()
            : context));
    }

    @NonNull private static LayerLoader loaderFor(@NonNull Context appContext) {
        return res -> ContextCompat.getDrawable(appContext, res);
    }

    WeatherIconDrawable(@NonNull LayerLoader loader) {
        mLoader = loader;
    }

    /**
     * Shows {@code icon} with its base layer in {@code ink} and its accent layer in
     * {@code accent}. Layers are reloaded only when the icon differs from the one shown.
     */
    public void set(@NonNull WeatherIcons.Icon icon, @ColorInt int ink, @ColorInt int accent) {
        boolean reload = mIcon != icon;
        boolean retint = reload || ink != mInk || accent != mAccentColor;
        mIcon = icon;
        mInk = ink;
        mAccentColor = accent;
        if (retint) mAccentTint = legibleAccent(accent, ink);
        if (reload) {
            mBase = layer(icon.base);
            mAccent = layer(icon.accent);
            Rect bounds = getBounds();
            if (mBase != null) mBase.setBounds(bounds);
            if (mAccent != null) mAccent.setBounds(bounds);
        }
        if (retint) {
            if (mBase != null) mBase.setTint(ink);
            if (mAccent != null) mAccent.setTint(mAccentTint);
            invalidateSelf();
        }
    }

    @Nullable private Drawable layer(@DrawableRes int res) {
        if (res == 0) return null;
        Drawable drawable = mLoader.load(res);
        if (drawable == null) return null;
        drawable = drawable.mutate();
        drawable.setAlpha(mAlpha);
        return drawable;
    }

    @Nullable public WeatherIcons.Icon icon() { return mIcon; }

    @Nullable Drawable baseLayer() { return mBase; }

    @Nullable Drawable accentLayer() { return mAccent; }

    @ColorInt public int inkTint() { return mInk; }

    @ColorInt public int accentTint() { return mAccentTint; }

    /**
     * {@code accent} put on the ink's side of the tone scale, as Material does for a primary on a
     * dark or a light surface: beside a light ink (tone 50 or more) the accent is raised to at
     * least {@link #ACCENT_TONE_ON_LIGHT_INK}, beside a dark ink lowered to at most
     * {@link #ACCENT_TONE_ON_DARK_INK}, keeping hue and chroma as far as the gamut allows
     * ({@link SchemeTone#toneShift}). An accent already on the right side is returned unchanged,
     * and alpha is the accent's own. No ground is assumed: the ink was measured against the real
     * one. Call on a colour change only, never per draw.
     */
    @ColorInt
    static int legibleAccent(@ColorInt int accent, @ColorInt int ink) {
        int opaqueAccent = accent | 0xFF000000;
        double tone = SchemeTone.tone(opaqueAccent);
        boolean lightInk = SchemeTone.tone(ink | 0xFF000000) >= INK_SIDE_TONE;
        if (lightInk ? tone >= ACCENT_TONE_ON_LIGHT_INK : tone <= ACCENT_TONE_ON_DARK_INK) {
            return accent;
        }
        int moved = SchemeTone.toneShift(opaqueAccent,
            (lightInk ? ACCENT_TONE_ON_LIGHT_INK : ACCENT_TONE_ON_DARK_INK) - tone);
        return (moved & 0x00FFFFFF) | (Color.alpha(accent) << 24);
    }

    @Override public void draw(@NonNull Canvas canvas) {
        if (mBase != null) mBase.draw(canvas);
        if (mAccent != null) mAccent.draw(canvas);
    }

    @Override protected void onBoundsChange(@NonNull Rect bounds) {
        if (mBase != null) mBase.setBounds(bounds);
        if (mAccent != null) mAccent.setBounds(bounds);
    }

    @Override public int getIntrinsicWidth() {
        return mBase != null ? mBase.getIntrinsicWidth()
            : mAccent != null ? mAccent.getIntrinsicWidth() : -1;
    }

    @Override public int getIntrinsicHeight() {
        return mBase != null ? mBase.getIntrinsicHeight()
            : mAccent != null ? mAccent.getIntrinsicHeight() : -1;
    }

    @Override public void setAlpha(int alpha) {
        mAlpha = alpha;
        if (mBase != null) mBase.setAlpha(alpha);
        if (mAccent != null) mAccent.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { }

    @Override public void setTintList(@Nullable ColorStateList tint) { }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
