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
import com.termux.app.chrome.GlassRefraction;
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
        /** The well kept the widget card's whole body because its ink failed on the bare veil. */
        public boolean bodyKept;
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
    public static boolean wants(@NonNull Context context, @Nullable PaneSurfaceStyle look) {
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
    public static boolean dress(@NonNull Context context, @Nullable PaneSurfaceStyle look,
                         @NonNull View frame, @NonNull PaneGlassBackdropView glass,
                         float radiusPx) {
        return dress(context, look, frame, glass, radiusPx, PageBarGlassStyle.WELL);
    }

    /**
     * {@link #dress(Context, PaneSurfaceStyle, View, PaneGlassBackdropView, float)} in
     * {@code style}: the well wears no refraction; the concave glass wears the concave lens where
     * the Look's Fancier Glass is on ({@link #refractionFor}), and the plain blur where it is off.
     */
    public static boolean dress(@NonNull Context context, @Nullable PaneSurfaceStyle look,
                         @NonNull View frame, @NonNull PaneGlassBackdropView glass,
                         float radiusPx, @NonNull PageBarGlassStyle style) {
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
        // Glass pressed into the page wears no convex cue ({@link InsetGlass}): no raised rim,
        // and never the convex refraction, whose bend and gathered edge light are a lens's
        // standing out. On a slab this thin that bend also folded the picture back on itself
        // and smeared it into vertical streaks. The concave glass bends the other way, within a
        // slope that cannot fold.
        glass.setRim(-1f, look);
        glass.setRefraction(refractionFor(style, look.paneGlassRefraction(), look.paneGlassTintColor()));
        glass.invalidateOutline();
        return true;
    }

    /**
     * The refraction the bar's glass wears: the concave lens over the Look's Fancier Glass
     * ({@code refraction}, null while it is off) for the concave glass; nothing for the well, nor with Fancier Glass off, which is the plain
     * blur. Pure.
     */
    @Nullable
    static GlassRefraction.Look refractionFor(@NonNull PageBarGlassStyle style,
                                              @Nullable GlassRefraction.Look refraction,
                                              @ColorInt int tint) {
        if (style != PageBarGlassStyle.CONCAVE || refraction == null) return null;
        return refraction.withConcave(true);
    }

    /**
     * Measures the ground under {@code rect} (in {@code view}'s own coordinates) on the widget
     * band and solves Home's well on it ({@link #solveWell}); the {@code glass} slab is drawn
     * through the even-out the ink was solved on. {@code glyphTopShare} and
     * {@code glyphBottomShare} are where the glyphs' tops and bottoms stand down the well. With no
     * Look to ask, {@code out} keeps its pale ink and the raw accent, and the slab no correction.
     * {@code floor}: whether the well's floor shade is drawn over the slab (the concave glass
     * draws none, and its glyphs are solved on the bare glass).
     */
    public static void measure(@NonNull View view, @NonNull Rect rect, @Nullable PaneSurfaceStyle look,
                        @ColorInt int accent, boolean glassWorn, @NonNull Ink out,
                        @NonNull WidgetBody.Answer body, @Nullable PaneGlassBackdropView glass,
                        float glyphTopShare, float glyphBottomShare, boolean floor,
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
        solveWell(view.getContext(), source, scratch, glassWorn, look.paneGlassTintColor(), accent,
            glyphTopShare, glyphBottomShare, floor, body, out);
        if (glass != null) glass.setEvenOut(glassWorn ? body.evenOut : null);
    }

    /**
     * Home's well at {@code rootRect} ({@link InsetGlass}). On glass it lays no lift over the
     * slab (the widget card's lift is a raised cue): only the veil the band asks for the card's
     * text ink on the bare tinted glass, at the smallest alpha that does the job. The well's floor
     * then shades it from the top; the ink is checked where the floor is worst for it (the
     * glyphs' tops for a dark ink, their bottoms for a pale one), and if it falls under the large
     * text floor there the widget card's whole body is laid as before ({@link #solve}) and
     * {@link Ink#bodyKept} says so. The glyphs are solved on the floor at the well's middle, by the
     * page marks' rule. Without glass, the opaque card as a widget's.
     */
    public static void solveWell(@NonNull Context context, @NonNull WidgetInkSource source,
                                 @NonNull Rect rootRect, boolean glassWorn, @ColorInt int tint,
                                 @ColorInt int accent, float glyphTopShare,
                                 float glyphBottomShare, @NonNull WidgetBody.Answer body,
                                 @NonNull Ink out) {
        solveWell(context, source, rootRect, glassWorn, tint, accent, glyphTopShare,
            glyphBottomShare, true, body, out);
    }

    /**
     * {@link #solveWell(Context, WidgetInkSource, Rect, boolean, int, int, float, float,
     * WidgetBody.Answer, Ink)} with the floor shade drawn ({@code floor}, the well) or not (the
     * concave glass, whose inks are checked and solved on the bare glass).
     */
    public static void solveWell(@NonNull Context context, @NonNull WidgetInkSource source,
                                 @NonNull Rect rootRect, boolean glassWorn, @ColorInt int tint,
                                 @ColorInt int accent, float glyphTopShare,
                                 float glyphBottomShare, boolean floor,
                                 @NonNull WidgetBody.Answer body, @NonNull Ink out) {
        boolean dark = BuiltinWidgetGlass.darkCard(false, source.widgetPolarity());
        int textInk = BuiltinWidgetStyle.textInkOn(context, dark);
        int card = BuiltinWidgetStyle.cardOn(context, dark);
        out.bodyKept = false;
        if (glassWorn) {
            OnGlass.Resolution answer = source.widgetInk(rootRect, tint, card, textInk,
                BuiltinWidgetStyle.primaryOn(context, dark));
            body.lift = Color.TRANSPARENT;
            body.body = tint;
            body.resolution = answer;
            body.fill = answer.veil;
            body.evenOut = source.widgetEvenOut(rootRect);
            boolean pale = GlassInk.isPaleSide(textInk, answer.surface);
            int worst = InsetGlass.groundAt(answer.surface, pale,
                pale ? glyphBottomShare : glyphTopShare, floor);
            if (OnGlass.ratio(textInk, worst) >= OnGlass.TARGET_LARGE_TEXT) {
                finishWell(answer.surface, answer.veil, textInk, accent, floor, out);
                return;
            }
            out.bodyKept = true;
        }
        solve(context, source, rootRect, glassWorn, tint, accent, body, out);
        finishWell(out.ground, out.veil, textInk, accent, floor, out);
    }

    /** The well's inks on {@code surface} under the floor at its middle, and the side its cues take. */
    private static void finishWell(@ColorInt int surface, @ColorInt int veil, @ColorInt int ink,
                                   @ColorInt int accent, boolean floor, @NonNull Ink out) {
        boolean pale = GlassInk.isPaleSide(ink, surface);
        int ground = InsetGlass.groundAt(surface, pale, 0.5f, floor);
        out.wellDark = pale;
        out.veil = veil;
        out.ink = ink;
        out.ground = ground;
        out.accent = PageMarkInk.current(ground, accent, GlassInk.isPaleSide(ink, ground));
        out.other = PageMarkInk.other(ground, ink, out.accent);
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
