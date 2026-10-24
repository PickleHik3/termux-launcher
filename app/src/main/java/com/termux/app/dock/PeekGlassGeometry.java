package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Where the Peek glass card stands ({@link DockStyle#PEEK_GLASS}, {@link DockSurfaceSpec#PEEK_GLASS}):
 * a glass card rising from the screen's bottom edge, inset from the screen's sides by
 * {@link #SIDE_INSET_FRACTION} of its width, its top corners rounded by {@link #CORNER_RADIUS_DP}
 * and its top edge through the centres of the dock's icons, so each icon stands half above the
 * card. The card runs on past the screen's bottom edge by its own corner radius, so its lower
 * corners are never on screen; the gesture pill draws over it.
 *
 * <p>The icons' reflections fade down the card from the icons' lower edges ({@link Card#mirrorAxisY}),
 * over {@link #REFLECTION_DEPTH_SCALE} of an icon's height and never past the screen's edge.</p>
 *
 * <p>Pure arithmetic: boxes and sizes in, one {@link Card} out, so every rule is held by JVM
 * tests. The activity hands it the row's icon boxes in the glass host's coordinates and cuts the
 * host, the glass program and the strip under the pill to what it answers.</p>
 */
public final class PeekGlassGeometry {

    /** The card's inset from each side of the screen, as a share of the screen's width. */
    public static final float SIDE_INSET_FRACTION = 0.03f;
    /** The card's top corners: generous, held to half the card's width. */
    public static final float CORNER_RADIUS_DP = 28f;
    /**
     * The least room between the card's side and the outermost icon's edge, so an end icon's
     * lower half and its reflection stand on the card. The row takes it as its side inset under
     * Peek glass ({@link #rowSideInsetPx}).
     */
    public static final float ICON_CLEARANCE_DP = 4f;
    /** How far the reflection runs down the card, as a share of the icon's height. */
    public static final float REFLECTION_DEPTH_SCALE = 0.6f;

    /** The Reflection knob's range and its default: a visible middle value. */
    public static final int REFLECTION_MIN = 0;
    public static final int REFLECTION_MAX = 100;
    public static final int REFLECTION_DEFAULT = 50;
    public static final int REFLECTION_STEP = 5;
    /** How strongly the reflection shows at its top edge with the knob at its strongest. */
    public static final float REFLECTION_MAX_ALPHA = 0.7f;
    /** The blur the reflection reaches at its far end, in dp, with the knob anywhere above 0. */
    public static final float REFLECTION_MAX_BLUR_DP = 6f;

    private PeekGlassGeometry() {}

    /** The card's inset from each side of a screen {@code screenWidthPx} wide. */
    public static int sideInsetPx(int screenWidthPx) {
        return Math.round(Math.max(0, screenWidthPx) * SIDE_INSET_FRACTION);
    }

    /**
     * The dock's apps row's side inset under Peek glass on a screen {@code screenWidthPx} wide:
     * the card's inset and the clearance inside it. The row's slots are even shares of what is
     * left and each icon is centred in its slot, so the outermost icon's edge is at least this
     * far from the screen's edge whatever the icon's size.
     */
    public static int rowSideInsetPx(int screenWidthPx, float density) {
        return sideInsetPx(screenWidthPx) + Math.round(ICON_CLEARANCE_DP * Math.max(0f, density));
    }

    /** The card's corner radius for a card {@code cardWidthPx} wide. */
    public static float cornerRadiusPx(float cardWidthPx, float density) {
        return Math.max(0f, Math.min(CORNER_RADIUS_DP * Math.max(0f, density),
            Math.max(0f, cardWidthPx) / 2f));
    }

    /**
     * How strongly the reflection shows at its top edge for the Reflection knob's {@code value}:
     * 0 is off, {@link #REFLECTION_MAX} is {@link #REFLECTION_MAX_ALPHA}.
     */
    public static float reflectionAlpha(int value) {
        int clamped = Math.max(REFLECTION_MIN, Math.min(REFLECTION_MAX, value));
        return REFLECTION_MAX_ALPHA * clamped / (float) REFLECTION_MAX;
    }

    /** The knob's stored value held to its range and step. */
    public static int clampReflection(int value) {
        int clamped = Math.max(REFLECTION_MIN, Math.min(REFLECTION_MAX, value));
        return Math.round(clamped / (float) REFLECTION_STEP) * REFLECTION_STEP;
    }

    /**
     * The card for the row's icons, in the glass host's coordinates, or null where there is no
     * card to cut: no icon, or a host with no size.
     *
     * @param iconBoxes        left, top, right and bottom per icon, in the host's coordinates
     * @param count            how many boxes {@code iconBoxes} holds
     * @param hostLeftPx       where the host's left edge stands from the screen's left edge
     * @param hostWidthPx      the host's width
     * @param hostHeightPx     the host's height
     * @param screenWidthPx    the screen's width, the one the card is inset from
     * @param screenBottomPx   where the screen's bottom edge stands in the host's coordinates:
     *                         the host's height where nothing stands under it, more where the
     *                         strip under the gesture pill continues it
     */
    @Nullable
    public static Card compute(@NonNull float[] iconBoxes, int count, float hostLeftPx,
                               int hostWidthPx, int hostHeightPx, int screenWidthPx,
                               float screenBottomPx, float density) {
        if (count <= 0 || hostWidthPx <= 0 || hostHeightPx <= 0 || screenWidthPx <= 0
            || iconBoxes.length < count * 4) return null;
        float centerSum = 0f;
        float iconBottom = Float.NEGATIVE_INFINITY;
        float iconHeight = 0f;
        for (int i = 0; i < count; i++) {
            float top = iconBoxes[i * 4 + 1];
            float bottom = iconBoxes[i * 4 + 3];
            centerSum += (top + bottom) * 0.5f;
            iconBottom = Math.max(iconBottom, bottom);
            iconHeight = Math.max(iconHeight, bottom - top);
        }
        if (!(iconHeight > 0f)) return null;
        float centerY = centerSum / count;
        float inset = sideInsetPx(screenWidthPx);
        float left = Math.max(0f, inset - hostLeftPx);
        float right = Math.min(hostWidthPx, screenWidthPx - inset - hostLeftPx);
        if (!(right > left)) return null;
        float radius = cornerRadiusPx(right - left, density);
        float screenBottom = Math.max(hostHeightPx, screenBottomPx);
        // Past the screen's edge by the radius, so the rounded lower corners are off screen.
        float bottom = screenBottom + radius;
        // The reflection hangs from the icons' lower edges: mirrored about the card's top edge
        // (their centres) it would fall exactly under the icons and never show.
        float axis = Math.max(centerY, iconBottom);
        float depth = Math.max(0f, Math.min(iconHeight * REFLECTION_DEPTH_SCALE,
            screenBottom - axis));
        return new Card(left, centerY, right, bottom, radius, screenBottom, axis, axis + depth);
    }

    /** One card, in the glass host's coordinates. */
    public static final class Card {
        public final float left;
        /** The card's top edge: the icons' centre line. */
        public final float top;
        public final float right;
        /** The card's bottom: past the screen's edge by {@link #radiusPx}. */
        public final float bottom;
        /** The top corners' radius; the lower ones are off screen. */
        public final float radiusPx;
        /** Where the screen's bottom edge stands. */
        public final float screenBottom;
        /** The line the icons are mirrored about: their lower edges. */
        public final float mirrorAxisY;
        /** Where the reflection has faded out: never past {@link #screenBottom}. */
        public final float reflectionBottom;

        Card(float left, float top, float right, float bottom, float radiusPx,
             float screenBottom, float mirrorAxisY, float reflectionBottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radiusPx = radiusPx;
            this.screenBottom = screenBottom;
            this.mirrorAxisY = mirrorAxisY;
            this.reflectionBottom = reflectionBottom;
        }

        /** The reflection's band: from the mirror line down to where it has faded out. */
        public float reflectionDepth() {
            return Math.max(0f, reflectionBottom - mirrorAxisY);
        }

        /** The same card moved by {@code dx}, {@code dy}: from the host's coordinates to another surface's. */
        @NonNull
        public Card offset(float dx, float dy) {
            return new Card(left + dx, top + dy, right + dx, bottom + dy, radiusPx,
                screenBottom + dy, mirrorAxisY + dy, reflectionBottom + dy);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof Card)) return false;
            Card c = (Card) o;
            return Float.compare(left, c.left) == 0 && Float.compare(top, c.top) == 0
                && Float.compare(right, c.right) == 0 && Float.compare(bottom, c.bottom) == 0
                && Float.compare(radiusPx, c.radiusPx) == 0
                && Float.compare(screenBottom, c.screenBottom) == 0
                && Float.compare(mirrorAxisY, c.mirrorAxisY) == 0
                && Float.compare(reflectionBottom, c.reflectionBottom) == 0;
        }

        @Override
        public int hashCode() {
            int h = Float.floatToIntBits(left);
            h = 31 * h + Float.floatToIntBits(top);
            h = 31 * h + Float.floatToIntBits(right);
            h = 31 * h + Float.floatToIntBits(bottom);
            h = 31 * h + Float.floatToIntBits(radiusPx);
            h = 31 * h + Float.floatToIntBits(screenBottom);
            h = 31 * h + Float.floatToIntBits(mirrorAxisY);
            return 31 * h + Float.floatToIntBits(reflectionBottom);
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Card{%.1f,%.1f,%.1f,%.1f r=%.1f mirror=%.1f..%.1f}",
                left, top, right, bottom, radiusPx, mirrorAxisY, reflectionBottom);
        }
    }
}
