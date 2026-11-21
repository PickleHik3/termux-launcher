package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.GlassAnchor;
import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.WidgetInkSource;
import com.termux.app.launcher.widget.HomeLabelInk;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetGlass;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetView;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * What the page bar's pill and bubble share with a Home widget's slab: the page's glass source
 * found by walking parents, the Look's glass worn or put down on the same terms as a widget's
 * ({@link BuiltinWidgetGlass}), and the ink and accent measured on the ground the pill stands on.
 * Dress passes only, never per frame.
 */
final class PageBarGlass {

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

    /** The card a page of pale ink wears is the dark one, and the other way round. */
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
     * band, the memoised answer a widget at that rect would get, and the inks for it: the neutral
     * ink, and {@code accent} toned until it reads, as the tick strip does. With no Look to ask,
     * {@code out} keeps its pale ink and the raw accent.
     */
    static void measure(@NonNull View view, @NonNull Rect rect, @Nullable PaneSurfaceStyle look,
                        @ColorInt int accent, boolean glassWorn, @NonNull Ink out,
                        @NonNull int[] origin, @NonNull int[] rootScratch, @NonNull Rect scratch) {
        out.accent = accent;
        if (!(look instanceof WidgetInkSource) || rect.isEmpty()) return;
        WidgetInkSource source = (WidgetInkSource) look;
        GlassAnchor.layoutOriginOnScreen(view, origin, rootScratch);
        scratch.set(rect);
        scratch.offset(origin[0], origin[1]);
        boolean pale = source.widgetPolarity() == ChromeInk.Polarity.PALE_INK;
        int textInk = pale ? HomeLabelInk.PALE_INK : HomeLabelInk.DARK_INK;
        int card = pale ? PALE_INK_CARD : DARK_INK_CARD;
        int body = glassWorn ? OnGlass.composite(Color.TRANSPARENT, look.paneGlassTintColor()) : card;
        OnGlass.Resolution answer = source.widgetInk(scratch, body, card, textInk, accent);
        out.ground = answer.surface;
        out.veil = glassWorn ? answer.veil : card;
        out.ink = answer.ink;
        boolean paleSide = GlassInk.isPaleSide(answer.ink, answer.surface);
        out.accent = GlassInk.legibleOn(answer.surface, accent, paleSide,
            OnGlass.TARGET_LARGE_TEXT, 0xFF);
    }
}
