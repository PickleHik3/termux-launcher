package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.GlassAnchor;
import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.WidgetBody;
import com.termux.app.chrome.WidgetInkSource;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetGlass;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetStyle;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetView;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * What the page bar's pill and bubble share with a Home widget's slab: the page's glass source
 * found by walking parents, the Look's glass worn or put down on the same terms as a widget's
 * ({@link BuiltinWidgetGlass}), and the pill dressed as a widget's card is ({@link WidgetBody}):
 * the widget's card on the widget's side (night or the page's pale ink), its lift over the slab,
 * the slab drawn through the same even-out, and the ink and accent measured on that body.
 * Dress passes only, never per frame.
 */
public final class PageBarGlass {

    /** The ground and the inks measured for one rect, from {@link #measure}. */
    static final class Ink {
        /** The opaque ground the glyphs stand on. */
        @ColorInt int ground = Color.TRANSPARENT;
        /** The veil the pill needs under the glyphs; transparent for none. */
        @ColorInt int veil = Color.TRANSPARENT;
        /** The neutral glyph and label ink. */
        @ColorInt int ink = Color.WHITE;
        /** The accent, made legible on {@link #ground}. */
        @ColorInt int accent = Color.WHITE;
    }

    /**
     * The card the key's pop-up lays under its fixed ink ({@link #cardFor}): dark under a pale
     * ink, pale under a dark one. A Home pill wears the widget's card instead ({@link #solve}).
     */
    private static final int PALE_INK_CARD = 0xFF1F1F1F;
    private static final int DARK_INK_CARD = 0xFFF2F2F2;

    private PageBarGlass() {
    }

    /** The opaque card {@code ink} is read on: dark under a pale ink, pale under a dark one. */
    @ColorInt
    static int cardFor(@ColorInt int ink) {
        double luminance = androidx.core.graphics.ColorUtils.calculateLuminance(ink);
        return luminance > 0.5 ? PALE_INK_CARD : DARK_INK_CARD;
    }

    @Nullable
    static BuiltinWidgetView.GlassSource sourceFor(@NonNull View view) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof BuiltinWidgetView.GlassSource) {
                return (BuiltinWidgetView.GlassSource) parent;
            }
        }
        return null;
    }

    /** Whether the Look's glass could be worn here (a widget's slab test, without its switch). */
    static boolean wants(@NonNull Context context, @Nullable PaneSurfaceStyle look) {
        if (!PaneGlass.isActive(look)) return false;
        BuiltinWidgetGlass.SystemAsks asks = BuiltinWidgetGlass.SystemAsks.current(context);
        return BuiltinWidgetGlass.holdsFrame(true, false, asks.batterySaver, asks.outlineText,
            asks.highContrast);
    }

    /**
     * Dresses {@code glass} in the Look's glass at {@code radiusPx}, or hides it where the solid
     * fallback applies.
     *
     * @return whether the glass shows
     */
    static boolean dress(@NonNull Context context, @Nullable PaneSurfaceStyle look,
                         @NonNull View frame, @NonNull PaneGlassBackdropView glass,
                         float radiusPx) {
        boolean onWallpaper = PaneGlass.isActive(look);
        WidgetInkSource ink = look instanceof WidgetInkSource ? (WidgetInkSource) look : null;
        boolean worn = false;
        if (onWallpaper) {
            BuiltinWidgetGlass.SystemAsks asks = BuiltinWidgetGlass.SystemAsks.current(context);
            worn = BuiltinWidgetGlass.glassWorn(true, false, true,
                ink == null || ink.widgetFrameReady(), asks.batterySaver, asks.outlineText,
                asks.highContrast);
        }
        if (!worn) {
            if (glass.getVisibility() != View.GONE) glass.setVisibility(View.GONE);
            return false;
        }
        PaneGlass.applyAtRadius(look, frame, glass, radiusPx);
        glass.setRim(radiusPx, look);
        glass.invalidateOutline();
        return true;
    }

    /**
     * Measures the ground under {@code rect} (in {@code view}'s own coordinates) on the widget
     * band, as a widget's card at that rect is measured ({@link #solve}), and the inks for it:
     * the neutral ink, and {@code accent} toned until it reads, as the tick strip does. The
     * {@code glass} slab is drawn through the even-out the ink was solved on. With no Look to ask,
     * {@code out} keeps its pale ink and the raw accent, and the slab no correction.
     */
    static void measure(@NonNull View view, @NonNull Rect rect, @Nullable PaneSurfaceStyle look,
                        @ColorInt int accent, boolean glassWorn, @NonNull Ink out,
                        @NonNull WidgetBody.Answer body, @Nullable PaneGlassBackdropView glass,
                        @NonNull int[] origin, @NonNull int[] rootScratch, @NonNull Rect scratch) {
        out.accent = accent;
        if (!(look instanceof WidgetInkSource) || rect.isEmpty()) {
            if (glass != null) glass.setEvenOut(null);
            return;
        }
        WidgetInkSource source = (WidgetInkSource) look;
        GlassAnchor.layoutOriginOnScreen(view, origin, rootScratch);
        scratch.set(rect);
        scratch.offset(origin[0], origin[1]);
        solve(view.getContext(), source, scratch, glassWorn, look.paneGlassTintColor(), accent,
            body);
        OnGlass.Resolution answer = body.resolution;
        if (glass != null) glass.setEvenOut(glassWorn ? body.evenOut : null);
        out.ground = answer.surface;
        // On glass the fill over the slab is the lift and the veil, as a widget's card draws it;
        // without glass the opaque card the ink was measured for.
        out.veil = glassWorn ? body.fill : body.body;
        out.ink = answer.ink;
        boolean paleSide = GlassInk.isPaleSide(answer.ink, answer.surface);
        out.accent = GlassInk.legibleOn(answer.surface, accent, paleSide,
            OnGlass.TARGET_LARGE_TEXT, 0xFF);
    }

    /**
     * The pill's body and ink at {@code rootRect}: a widget's card on the widget's side (the dark
     * card at night or on the page's pale ink, {@link BuiltinWidgetGlass#darkCard},
     * {@link BuiltinWidgetStyle#cardOn}), with its
     * weakest text ink, on the Look's glass tinted {@code tint} or solid, solved by the widgets'
     * own recipe ({@link WidgetBody#solve}) with the pill's {@code accent} as the graphic ink.
     */
    public static void solve(@NonNull Context context, @NonNull WidgetInkSource source,
                             @NonNull Rect rootRect, boolean glassWorn, @ColorInt int tint,
                             @ColorInt int accent, @NonNull WidgetBody.Answer out) {
        // Night is the card's own, as a widget's style keeps it (cardOn keeps a night card dark).
        boolean dark = BuiltinWidgetGlass.darkCard(false, source.widgetPolarity());
        WidgetBody.solve(source, rootRect, glassWorn, BuiltinWidgetStyle.cardOn(context, dark),
            tint, BuiltinWidgetStyle.textInkOn(context, dark), accent, out);
    }
}
