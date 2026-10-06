package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * The numbers one view's retro effect is drawn with, in that view's own px. Pure arithmetic and a
 * reusable holder: {@link RetroEffectBinder} fills a scratch one before every frame and wraps a new
 * effect only when it no longer {@link #equals} the one on screen, so a surface at rest allocates
 * nothing.
 *
 * <p>The screen is one display: a scanline (or a TFT cell edge) falls on the same window row
 * whichever surface it is drawn on. Each view therefore carries its {@link #phase} — where its own
 * 0 falls inside the screen's period — and the shaders add it to every coordinate before they
 * take the period. A surface that is scaled with its parent (the Appearance editor's frame) keeps
 * the lines in its own units, so every surface scaled together still lines up with every other.
 */
public final class RetroUniforms {

    /** The scanline period and the TFT cell, in dp; the shaders read it as this times uDensity. */
    public static final float PERIOD_DP = 3f;

    public float width;
    public float height;
    /** Where the view's own 0 falls in the screen's period, along x and along y. */
    public float phaseX;
    public float phaseY;
    public float density;
    /** The card the effect is cut to, {@code left, top, right, bottom} in the view's own px. */
    public float cardLeft;
    public float cardTop;
    public float cardRight;
    public float cardBottom;
    /** The card's corner radius, already capped at half its shorter side. */
    public float radius;
    /** The CRT's barrel bend: {@link PaneRetroEffect#CRT_BEND} on a pane card, 0 on flat chrome. */
    public float bend;
    /** How far the corners darken, 0 for none. */
    public float vignette;

    /** The screen's period in px. */
    public static float periodPx(float density) {
        return PERIOD_DP * density;
    }

    /**
     * Where in a period of {@code periodPx} the coordinate {@code originPx} falls, in
     * {@code [0, periodPx)}: what the shaders add to a view's own coordinates so that its rows
     * are the screen's rows.
     */
    public static float phase(float originPx, float periodPx) {
        if (!(periodPx > 0f) || Float.isNaN(originPx) || Float.isInfinite(originPx)) return 0f;
        float m = originPx % periodPx;
        if (m < 0f) m += periodPx;
        return m >= periodPx ? 0f : m;
    }

    /**
     * Fills every field for a view {@code width} by {@code height} px whose top-left stands at
     * ({@code windowX}, {@code windowY}) in the window, drawn at {@code scaleX}/{@code scaleY}
     * (its own scale and every ancestor's, multiplied), cut to the given card.
     *
     * @return this, for chaining
     */
    @NonNull
    public RetroUniforms set(float width, float height, float windowX, float windowY,
                             float scaleX, float scaleY, float density,
                             float cardLeft, float cardTop, float cardRight, float cardBottom,
                             float radius, float bend, float vignette) {
        float period = periodPx(density);
        this.width = width;
        this.height = height;
        // windowX = origin * scale in window px, and the view's own unit is 1/scale of one: the
        // origin in its own units is what lines its rows up with an equally scaled neighbour's.
        this.phaseX = phase(windowX / usableScale(scaleX), period);
        this.phaseY = phase(windowY / usableScale(scaleY), period);
        this.density = density;
        this.cardLeft = cardLeft;
        this.cardTop = cardTop;
        this.cardRight = Math.max(cardLeft, cardRight);
        this.cardBottom = Math.max(cardTop, cardBottom);
        this.radius = cappedRadius(radius, this.cardRight - cardLeft, this.cardBottom - cardTop);
        this.bend = bend;
        this.vignette = vignette;
        return this;
    }

    /** The radius a card {@code w} by {@code h} can hold: never negative, never past half a side. */
    public static float cappedRadius(float radius, float w, float h) {
        if (!(radius > 0f)) return 0f;
        return Math.min(radius, Math.max(0f, Math.min(w, h)) * 0.5f);
    }

    private static float usableScale(float scale) {
        return scale > 0f && !Float.isInfinite(scale) ? scale : 1f;
    }

    public void copyFrom(@NonNull RetroUniforms other) {
        width = other.width;
        height = other.height;
        phaseX = other.phaseX;
        phaseY = other.phaseY;
        density = other.density;
        cardLeft = other.cardLeft;
        cardTop = other.cardTop;
        cardRight = other.cardRight;
        cardBottom = other.cardBottom;
        radius = other.radius;
        bend = other.bend;
        vignette = other.vignette;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RetroUniforms)) return false;
        RetroUniforms u = (RetroUniforms) o;
        return Float.compare(width, u.width) == 0 && Float.compare(height, u.height) == 0
            && Float.compare(phaseX, u.phaseX) == 0 && Float.compare(phaseY, u.phaseY) == 0
            && Float.compare(density, u.density) == 0
            && Float.compare(cardLeft, u.cardLeft) == 0 && Float.compare(cardTop, u.cardTop) == 0
            && Float.compare(cardRight, u.cardRight) == 0
            && Float.compare(cardBottom, u.cardBottom) == 0
            && Float.compare(radius, u.radius) == 0 && Float.compare(bend, u.bend) == 0
            && Float.compare(vignette, u.vignette) == 0;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(width);
        h = 31 * h + Float.floatToIntBits(height);
        h = 31 * h + Float.floatToIntBits(phaseX);
        h = 31 * h + Float.floatToIntBits(phaseY);
        h = 31 * h + Float.floatToIntBits(density);
        h = 31 * h + Float.floatToIntBits(cardLeft);
        h = 31 * h + Float.floatToIntBits(cardTop);
        h = 31 * h + Float.floatToIntBits(cardRight);
        h = 31 * h + Float.floatToIntBits(cardBottom);
        h = 31 * h + Float.floatToIntBits(radius);
        h = 31 * h + Float.floatToIntBits(bend);
        return 31 * h + Float.floatToIntBits(vignette);
    }
}
