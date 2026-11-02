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

    /** A pane's red/blue split at its screen's edge, in px; nothing at the centre. */
    public static final float PANE_ABERR_PX = 0.6f;
    /** The chrome's red/blue split, in px, the same everywhere on it. */
    public static final float CHROME_ABERR_PX = 0.3f;

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
    /**
     * The screen inside the card the effect is drawn on, {@code left, top, right, bottom}: the
     * whole card on chrome, the rect inside a pane's bezel ({@link PaneBezel}). Between the
     * screen and the card the view is drawn as it is.
     */
    public float screenLeft;
    public float screenTop;
    public float screenRight;
    public float screenBottom;
    /** The screen's corner radius, already capped at half its shorter side. */
    public float screenRadius;
    /**
     * The rect the bend and the vignette are worked out over, {@code left, top, right, bottom}:
     * the whole view on chrome, the pane's laid-out screen on a pane, so a travel that shortens
     * the screen cuts the picture off rather than squeezing it.
     */
    public float bendLeft;
    public float bendTop;
    public float bendRight;
    public float bendBottom;
    /** The red/blue split in px: at the screen's edge, or everywhere while {@link #aberrFlat} is 1. */
    public float aberr;
    /** 1 for a split that is the same everywhere (flat chrome), 0 for one growing from the centre. */
    public float aberrFlat;
    /** 1 draws the glass's recess and sheen inside the screen (a pane), 0 leaves them out. */
    public float recess;
    /** 0..1: how far the picture loses brightness and colour; 0 for none. */
    public float dim;
    /** The live screen event's {@link PaneScreenEvent.Kind#id}, 0 for none (always 0 on chrome). */
    public float event;
    /** The live screen event's progress, 0..1. */
    public float eventT;
    /** The live screen event's random, 0..1. */
    public float seed;

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
        this.screenLeft = this.cardLeft;
        this.screenTop = this.cardTop;
        this.screenRight = this.cardRight;
        this.screenBottom = this.cardBottom;
        this.screenRadius = this.radius;
        this.bendLeft = 0f;
        this.bendTop = 0f;
        this.bendRight = width;
        this.bendBottom = height;
        this.aberr = 0f;
        this.aberrFlat = 0f;
        this.recess = 0f;
        this.dim = 0f;
        this.event = 0f;
        this.eventT = 0f;
        this.seed = 0f;
        return this;
    }

    /**
     * The live screen event, after {@link #set} (which clears it): its kind {@code id}, its
     * {@code progress} 0..1 and its {@code seed} 0..1.
     *
     * @return this, for chaining
     */
    @NonNull
    public RetroUniforms event(int id, float progress, float seed) {
        this.event = id;
        this.eventT = progress;
        this.seed = seed;
        return this;
    }

    /**
     * The surface's own CRT parts, after {@link #set} (which clears them): the red/blue split
     * {@code aberr} in px, flat ({@code aberrFlat} 1) or growing from the centre (0), the glass's
     * {@code recess} and sheen (1 on, 0 off), and how far the picture is {@code dim}med (0..1).
     *
     * @return this, for chaining
     */
    @NonNull
    public RetroUniforms surface(float aberr, float aberrFlat, float recess, float dim) {
        this.aberr = aberr;
        this.aberrFlat = aberrFlat;
        this.recess = recess;
        this.dim = dim;
        return this;
    }

    /**
     * Cuts the effect to a screen inside the card, after {@link #set}: the effect is drawn inside
     * it and the view as it is between it and the card. The bend is worked out over the screen,
     * down to {@code bendBottom} (its laid-out bottom, below the drawn one while a travel
     * shortens it). Held inside the card.
     *
     * @return this, for chaining
     */
    @NonNull
    public RetroUniforms screen(float left, float top, float right, float bottom, float radius,
                                float bendBottom) {
        this.screenLeft = Math.max(cardLeft, Math.min(left, cardRight));
        this.screenTop = Math.max(cardTop, Math.min(top, cardBottom));
        this.screenRight = Math.max(screenLeft, Math.min(right, cardRight));
        this.screenBottom = Math.max(screenTop, Math.min(bottom, cardBottom));
        this.screenRadius = cappedRadius(radius, screenRight - screenLeft,
            screenBottom - screenTop);
        this.bendLeft = screenLeft;
        this.bendTop = screenTop;
        this.bendRight = screenRight;
        this.bendBottom = Math.max(screenBottom, bendBottom);
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
        screenLeft = other.screenLeft;
        screenTop = other.screenTop;
        screenRight = other.screenRight;
        screenBottom = other.screenBottom;
        screenRadius = other.screenRadius;
        bendLeft = other.bendLeft;
        bendTop = other.bendTop;
        bendRight = other.bendRight;
        bendBottom = other.bendBottom;
        aberr = other.aberr;
        aberrFlat = other.aberrFlat;
        recess = other.recess;
        dim = other.dim;
        event = other.event;
        eventT = other.eventT;
        seed = other.seed;
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
            && Float.compare(vignette, u.vignette) == 0
            && Float.compare(screenLeft, u.screenLeft) == 0
            && Float.compare(screenTop, u.screenTop) == 0
            && Float.compare(screenRight, u.screenRight) == 0
            && Float.compare(screenBottom, u.screenBottom) == 0
            && Float.compare(screenRadius, u.screenRadius) == 0
            && Float.compare(bendLeft, u.bendLeft) == 0 && Float.compare(bendTop, u.bendTop) == 0
            && Float.compare(bendRight, u.bendRight) == 0
            && Float.compare(bendBottom, u.bendBottom) == 0
            && Float.compare(aberr, u.aberr) == 0 && Float.compare(aberrFlat, u.aberrFlat) == 0
            && Float.compare(recess, u.recess) == 0 && Float.compare(dim, u.dim) == 0
            && Float.compare(event, u.event) == 0 && Float.compare(eventT, u.eventT) == 0
            && Float.compare(seed, u.seed) == 0;
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
        h = 31 * h + Float.floatToIntBits(vignette);
        h = 31 * h + Float.floatToIntBits(screenLeft);
        h = 31 * h + Float.floatToIntBits(screenTop);
        h = 31 * h + Float.floatToIntBits(screenRight);
        h = 31 * h + Float.floatToIntBits(screenBottom);
        h = 31 * h + Float.floatToIntBits(screenRadius);
        h = 31 * h + Float.floatToIntBits(bendLeft);
        h = 31 * h + Float.floatToIntBits(bendTop);
        h = 31 * h + Float.floatToIntBits(bendRight);
        h = 31 * h + Float.floatToIntBits(bendBottom);
        h = 31 * h + Float.floatToIntBits(aberr);
        h = 31 * h + Float.floatToIntBits(aberrFlat);
        h = 31 * h + Float.floatToIntBits(recess);
        h = 31 * h + Float.floatToIntBits(dim);
        h = 31 * h + Float.floatToIntBits(event);
        h = 31 * h + Float.floatToIntBits(eventT);
        return 31 * h + Float.floatToIntBits(seed);
    }
}
