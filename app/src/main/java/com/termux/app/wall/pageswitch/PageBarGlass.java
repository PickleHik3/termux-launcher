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
import com.termux.app.chrome.InsetGlass;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.PageMarkInk;
import com.termux.app.chrome.WallpaperInk;
import com.termux.app.chrome.WidgetBody;
import com.termux.app.chrome.WidgetInkSource;
import com.termux.app.launcher.widget.HomeLabelInk;
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
    public static final class Ink {
        /** The opaque ground the glyphs stand on. */
        @ColorInt public int ground = Color.TRANSPARENT;
        /** The veil the pill needs under the glyphs; transparent for none. */
        @ColorInt public int veil = Color.TRANSPARENT;
        /** The neutral ink of the pill's body (the card's weakest text ink), opaque. */
        @ColorInt public int ink = Color.WHITE;
        /** The other pages' glyphs: {@link #ink} at its alpha ({@link PageMarkInk#other}). */
        @ColorInt public int other = Color.WHITE;
        /** The current page's glyph: the accent made legible on {@link #ground}. */
        @ColorInt public int accent = Color.WHITE;
        /** Whether the Home pill's well stands on a dark ground ({@link InsetGlass}); true until measured. */
        public boolean wellDark = true;
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

    /**
     * The ground under the key's pop-up wearing raised glass: its tint (at the raised opacity)
     * over the wallpaper sampled under it, or over the card {@code ink} reads on while nothing was
     * sampled. Opaque.
     */
    @ColorInt
    static int raisedGround(@ColorInt int tint, @ColorInt int wallpaper, @ColorInt int ink) {
        int under = Color.alpha(wallpaper) == 0 ? cardFor(ink) : OnGlass.opaque(wallpaper);
        return OnGlass.opaque(OnGlass.composite(tint, under));
    }

    /**
     * The key's pop-up inks on raised glass, solved on {@code ground}: the neutral the more legible
     * of the sheets' ink and its opposite pole ({@link #cardFor}), the current page and the others
     * by the page marks' rule ({@link PageMarkInk}).
     */
    static void raisedInk(@ColorInt int ground, @ColorInt int fixedInk, @ColorInt int accent,
                          @NonNull Ink out) {
        int ink = OnGlass.mostLegibleInk(ground, fixedInk, cardFor(fixedInk));
        out.ground = ground;
        out.veil = Color.TRANSPARENT;
        out.ink = ink;
        out.accent = PageMarkInk.current(ground, accent, GlassInk.isPaleSide(ink, ground));
        out.other = PageMarkInk.other(ground, ink, out.accent);
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
        // A well pressed into the page has no raised rim ({@link InsetGlass}).
        glass.setRim(-1f, look);
        glass.invalidateOutline();
        return true;
    }

    /**
     * Measures the ground under {@code rect} (in {@code view}'s own coordinates) on the widget
     * band, as a widget's card at that rect is measured, and the glyph inks on it ({@link #solve}).
     * The {@code glass} slab is drawn through the even-out the ink was solved on. With no Look to
     * ask, {@code out} keeps its pale ink and the raw accent, and the slab no correction.
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
            body, out);
        sink(out, accent);
        if (glass != null) glass.setEvenOut(glassWorn ? body.evenOut : null);
    }

    /**
     * The pill's body and glyph inks at {@code rootRect}. The body is a widget's card on the
     * widget's side (the dark card at night or on the page's pale ink,
     * {@link BuiltinWidgetGlass#darkCard}, {@link BuiltinWidgetStyle#cardOn}) on the Look's glass
     * tinted {@code tint} or solid, solved by the widgets' own recipe ({@link WidgetBody#solve})
     * with the card's weakest text ink and its accent, as a widget asks. The glyphs are then
     * solved on that ground, on the card's side: the current page in {@code accent} and the
     * others in the card's text ink ({@link PageMarkInk}).
     */
    public static void solve(@NonNull Context context, @NonNull WidgetInkSource source,
                             @NonNull Rect rootRect, boolean glassWorn, @ColorInt int tint,
                             @ColorInt int accent, @NonNull WidgetBody.Answer body,
                             @NonNull Ink out) {
        // Night is the card's own, as a widget's style keeps it (cardOn keeps a night card dark).
        boolean dark = BuiltinWidgetGlass.darkCard(false, source.widgetPolarity());
        int textInk = BuiltinWidgetStyle.textInkOn(context, dark);
        WidgetBody.solve(source, rootRect, glassWorn, BuiltinWidgetStyle.cardOn(context, dark),
            tint, textInk, BuiltinWidgetStyle.primaryOn(context, dark), body);
        OnGlass.Resolution answer = body.resolution;
        out.ground = answer.surface;
        // On glass the fill over the slab is the lift and the veil, as a widget's card draws it;
        // without glass the opaque card the ink was measured for.
        out.veil = glassWorn ? body.fill : body.body;
        // The card's own text ink, not the answer's binding ink (which may be the accent).
        out.ink = textInk;
        out.accent = PageMarkInk.current(answer.surface, accent,
            GlassInk.isPaleSide(textInk, answer.surface));
        out.other = PageMarkInk.other(answer.surface, textInk, out.accent);
    }

    /**
     * The Home pill pressed into the page ({@link InsetGlass}): the ground {@link #solve} measured
     * under the well's shade, and the glyph inks solved again on that ground by the page marks'
     * rule, with the card's text ink kept. {@link Ink#wellDark} says which side the cues take.
     */
    static void sink(@NonNull Ink out, @ColorInt int accent) {
        if (Color.alpha(out.ground) == 0) return;
        boolean dark = GlassInk.isPaleSide(out.ink, out.ground);
        out.wellDark = dark;
        out.ground = InsetGlass.groundIn(out.ground, dark);
        out.accent = PageMarkInk.current(out.ground, accent, GlassInk.isPaleSide(out.ink, out.ground));
        out.other = PageMarkInk.other(out.ground, out.ink, out.accent);
    }

    /**
     * The ink of the bubble's label, which stands on the bare wallpaper above the pill: the
     * bare-text ink Home's labels take on {@code wallpaper}, or {@code fallback} while nothing
     * was read.
     */
    @ColorInt
    public static int labelInk(@ColorInt int wallpaper, @ColorInt int fallback) {
        if (Color.alpha(wallpaper) == 0) return fallback;
        return WallpaperInk.bareInk(wallpaper, HomeLabelInk.DARK_INK, HomeLabelInk.PALE_INK);
    }
}
