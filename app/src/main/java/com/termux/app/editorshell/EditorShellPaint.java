package com.termux.app.editorshell;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * The material both editors' cards are made of: one fill, one rim, one shadow.
 *
 * <p>The two cards used to build their own backgrounds from the same tokens in two places, and they
 * had drifted: a panel-high fill, a 24dp corner and an outline-variant stroke in one, the same three
 * restated in the other. Panel-high is a raised <em>panel</em> colour — over a terminal it read as a
 * beige slab rather than as a sheet of the same room, which is what "not production ready" was
 * pointing at. The rule here is one step: the surface the user's own colour scheme gives, lifted a
 * little towards the text on it, under a rim of that same text at a twelfth of its strength.
 *
 * <p>No colours are named here. Both editors resolve their tokens from the theme and hand them
 * over, so a card follows the terminal's palette the way everything else in the launcher does.
 */
public final class EditorShellPaint {

    private EditorShellPaint() {}

    /** How far the card's fill is lifted off the surface, towards the ink on it. */
    public static final float CARD_LIFT = 0.04f;
    /** The rim's strength, as a share of the on-surface ink. */
    public static final float CARD_RIM_ALPHA = 0.12f;
    /** The card's corner. */
    public static final int CARD_CORNER_DP = 28;
    /** How far the card stands off what is behind it, which is what casts its shadow. */
    public static final int CARD_ELEVATION_DP = 12;

    /** The card's fill: the surface the scheme gives, lifted towards the ink on it. */
    @ColorInt
    public static int cardFill(@ColorInt int surface, @ColorInt int onSurface) {
        return ColorUtils.blendARGB(surface, onSurface, CARD_LIFT);
    }

    /**
     * The background either editor's card is drawn with.
     *
     * @param surface   the scheme's surface colour — on a terminal, its background
     * @param onSurface the ink that stands on it, which is also what the rim is made of
     * @param density   the display density, for the corner and the rim's width
     */
    @NonNull
    public static Drawable cardBackground(@ColorInt int surface, @ColorInt int onSurface,
                                          float density) {
        return cardBackground(surface, onSurface, density, CARD_CORNER_DP);
    }

    /** The same material at a stated corner, for a card that is not the editor's own sheet. */
    @NonNull
    public static Drawable cardBackground(@ColorInt int surface, @ColorInt int onSurface,
                                          float density, int cornerDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(cardFill(surface, onSurface));
        background.setCornerRadius(EditorShellMetrics.px(cornerDp, density));
        background.setStroke(Math.max(1, EditorShellMetrics.px(1, density)),
            ColorUtils.setAlphaComponent(onSurface, Math.round(CARD_RIM_ALPHA * 255f)));
        return background;
    }

    /**
     * Stands the card off the screen behind it, with a shadow soft enough to read as depth rather
     * than as a second outline.
     *
     * <p>The platform casts its shadow downwards from the outline; a card parked at the bottom of
     * the screen shows mostly the part that spreads upwards past its own top edge, which is the
     * edge that has to say the card is in front of the place behind it.
     */
    public static void applyCardElevation(@NonNull View card, float density) {
        card.setElevation(EditorShellMetrics.px(CARD_ELEVATION_DP, density));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            card.setOutlineSpotShadowColor(Color.argb(140, 0, 0, 0));
            card.setOutlineAmbientShadowColor(Color.argb(90, 0, 0, 0));
        }
    }
}
