package com.termux.app.launcher.widget;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.GlassAnchor;
import com.termux.app.chrome.WallpaperInk;
import com.termux.app.chrome.WidgetInkSource;

/**
 * Text a Home page writes straight on the wallpaper — an icon's or a folder's label, the empty
 * page's hint — takes light or dark ink from the wallpaper under it (editor-redesign §8), through
 * the page's Look ({@link WidgetInkSource}, the same sampler the widgets use). One per piece of
 * text: it remembers the wallpaper it last read and reads again only when the text moved, the
 * chrome dropped its samples (a new wallpaper, dim or mode) or the last read found nothing yet.
 *
 * <p>Sampled where the text is laid out, as the widgets are; a wall slide or a wallpaper pan is
 * not a new place (the widgets' rule: {@link GlassAnchor#layoutOriginOnScreen}).</p>
 */
final class HomeLabelInk {

    /** The dark ink a label takes on a light wallpaper; its partner is white, as Home always drew. */
    static final int DARK_INK = 0xFF1F1F1F;
    static final int PALE_INK = Color.WHITE;

    private final Rect rect = new Rect();
    private final int[] location = new int[2];
    private final int[] rootScratch = new int[2];
    private int generation;
    private boolean sampled;
    @ColorInt private int wallpaper = Color.TRANSPARENT;

    /** The page's Look above {@code view}, as a source of wallpaper samples; null off a Home page. */
    @Nullable
    static WidgetInkSource sourceFor(@NonNull View view) {
        if (!view.isAttachedToWindow()) return null;
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof com.termux.app.launcher.widget.builtin.BuiltinWidgetView.GlassSource) {
                Object look = ((com.termux.app.launcher.widget.builtin.BuiltinWidgetView.GlassSource)
                    parent).widgetGlassStyle();
                return look instanceof WidgetInkSource ? (WidgetInkSource) look : null;
            }
        }
        return null;
    }

    /**
     * The wallpaper under {@code text} as far as it is known, reading it if it has to; transparent
     * with nothing to ask (no source, not laid out).
     */
    @ColorInt
    int wallpaperUnder(@NonNull View text, @Nullable WidgetInkSource source) {
        if (source == null || text.getWidth() <= 0 || text.getHeight() <= 0) return wallpaper;
        GlassAnchor.layoutOriginOnScreen(text, location, rootScratch);
        int left = location[0];
        int top = location[1];
        int right = left + text.getWidth();
        int bottom = top + text.getHeight();
        int now = source.wallpaperGeneration();
        if (sampled && now == generation && rect.left == left && rect.top == top
                && rect.right == right && rect.bottom == bottom) {
            return wallpaper;
        }
        rect.set(left, top, right, bottom);
        int read = source.wallpaperSample(rect);
        sampled = Color.alpha(read) != 0;
        int under = sampled ? read : source.wallpaperFallback();
        generation = now;
        if (Color.alpha(under) != 0) wallpaper = under;
        return wallpaper;
    }

    /** The wallpaper last read, or transparent before any. */
    @ColorInt
    int wallpaper() {
        return wallpaper;
    }

    /**
     * Writes {@code label} in the ink the wallpaper under it asks for, with the soft shadow on the
     * far side of that ink when {@code shadow} is on. Nothing changes before a wallpaper is known,
     * so the label keeps Home's white with its dark shadow until then.
     *
     * @return whether anything was applied
     */
    boolean apply(@NonNull TextView label, boolean shadow, float density) {
        if (Color.alpha(wallpaper) == 0) return false;
        int ink = WallpaperInk.bareInk(wallpaper, DARK_INK, PALE_INK);
        if (label.getCurrentTextColor() != ink) label.setTextColor(ink);
        float radius = shadow ? HomeIconLabels.SHADOW_RADIUS_DP * density : 0f;
        float dy = shadow ? HomeIconLabels.SHADOW_DY_DP * density : 0f;
        int color = shadow ? WallpaperInk.labelShadow(ink, wallpaper) : 0;
        if (label.getShadowRadius() != radius || label.getShadowDy() != dy
                || label.getShadowDx() != 0f || label.getShadowColor() != color) {
            label.setShadowLayer(radius, 0f, dy, color);
        }
        return true;
    }
}
