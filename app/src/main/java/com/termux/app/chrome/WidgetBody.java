package com.termux.app.chrome;

import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Rect;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The body a Home widget's card lays over the Look's slab, and the ink measured on it: the lift
 * in the card's own colour ({@link OnGlass#widgetLiftAlpha255}), the body that makes over the
 * slab's tint, the widget band's answer for it ({@link WidgetInkSource#widgetInk}), the one fill
 * the card draws over the slab (lift and veil, {@link OnGlass#stackAlpha}) and the even-out
 * correction the slab is drawn through ({@link WidgetInkSource#widgetEvenOut}). On the solid card
 * the body is the card.
 *
 * <p>One recipe for everything that dresses like a widget: the built-in widgets and the page
 * bar's pill. Asked on a dress pass, never per frame; the answer is written into a reused
 * {@link Answer}.</p>
 */
public final class WidgetBody {

    /** One dress pass's answer; reused by its owner. */
    public static final class Answer {
        /** The lift over the slab, in the card's colour; transparent on the solid card. */
        @ColorInt public int lift = Color.TRANSPARENT;
        /** What the card lays over the wallpaper: lift over tint on glass, the card when solid. */
        @ColorInt public int body = Color.TRANSPARENT;
        /** The one fill the card draws over the slab (lift and veil); transparent when solid. */
        @ColorInt public int fill = Color.TRANSPARENT;
        /** The widget band's answer, or null while nothing could be asked. */
        @Nullable public OnGlass.Resolution resolution;
        /** The correction the slab is drawn through; null for none (and always on the solid card). */
        @Nullable public ColorMatrix evenOut;
    }

    private WidgetBody() {
    }

    /** The lift a card of {@code card}'s colour lays over a slab tinted {@code tint}. */
    @ColorInt
    public static int lift(@ColorInt int card, @ColorInt int tint) {
        return OnGlass.withAlpha(card, OnGlass.widgetLiftAlpha255(Color.alpha(tint)));
    }

    /**
     * Solves the card at {@code rootRect}: on the Look's glass tinted {@code tint} when
     * {@code glassWorn}, else on the opaque {@code card}. {@code textInk} is the card's weakest
     * text ink and {@code graphicInk} the ink of its graphics, as {@link ChromeInk#widget} takes
     * them.
     */
    public static void solve(@NonNull WidgetInkSource source, @NonNull Rect rootRect,
                             boolean glassWorn, @ColorInt int card, @ColorInt int tint,
                             @ColorInt int textInk, @ColorInt int graphicInk,
                             @NonNull Answer out) {
        int lift = glassWorn ? lift(card, tint) : Color.TRANSPARENT;
        int body = glassWorn ? OnGlass.composite(lift, tint) : card;
        OnGlass.Resolution answer = source.widgetInk(rootRect, body, card, textInk, graphicInk);
        out.lift = lift;
        out.body = body;
        out.resolution = answer;
        out.fill = glassWorn ? OnGlass.stackAlpha(lift, answer.veil) : Color.TRANSPARENT;
        out.evenOut = glassWorn ? source.widgetEvenOut(rootRect) : null;
    }
}
