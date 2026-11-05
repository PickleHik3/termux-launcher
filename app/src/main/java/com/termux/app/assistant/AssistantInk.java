package com.termux.app.assistant;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetStyle;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetStyle.DarkTones;

/**
 * The Assistant page's ink, by the legibility contract (ADR 0014) the way a Home widget's card
 * takes it: the pane's roles are on the page's one ink polarity (the dark side when the chrome
 * draws pale ink, or when the theme is night), and the pane lays a veil of its own colour over
 * the glass, at the smallest alpha that holds its weakest text to the body target and its accent
 * to the graphic target over what is under it ({@link OnGlass#resolveWidget}). The bubbles and the
 * composer are opaque and keep their own on-colours on that side.
 *
 * <p>Pure and context free; the page asks the chrome's memoised widget band
 * ({@code WidgetInkSource#widgetInk}), which runs the same resolution.</p>
 */
final class AssistantInk {

    private AssistantInk() {
    }

    /** Whether the pane wears the dark side: the theme's night, or the chrome's pale ink. */
    static boolean darkSide(@NonNull AssistantUi.Palette theme,
                            @Nullable ChromeInk.Polarity polarity) {
        return isNight(theme) || polarity == ChromeInk.Polarity.PALE_INK;
    }

    /** Whether the theme's own palette is a dark one: its on-surface ink is the light one. */
    static boolean isNight(@NonNull AssistantUi.Palette theme) {
        return ColorUtils.calculateLuminance(OnGlass.opaque(theme.onSurface)) > 0.5;
    }

    /**
     * {@code theme} on the side asked for: itself for the light side or a palette that is already
     * dark; otherwise every role re-toned to the Material dark scheme's tone for it, hue and chroma
     * kept, as a widget's dark card is ({@link BuiltinWidgetStyle#atTone}).
     */
    @NonNull
    static AssistantUi.Palette onSide(@NonNull AssistantUi.Palette theme, boolean dark) {
        if (!dark || isNight(theme)) return theme;
        AssistantUi.Palette p = new AssistantUi.Palette();
        p.surface = BuiltinWidgetStyle.atTone(theme.surface, DarkTones.CARD);
        p.surfaceHigh = BuiltinWidgetStyle.atTone(theme.surfaceHigh, DarkTones.CONTAINER_HIGH);
        p.surfaceHighest = BuiltinWidgetStyle.atTone(theme.surfaceHighest, DarkTones.CONTAINER_HIGHEST);
        p.onSurface = BuiltinWidgetStyle.atTone(theme.onSurface, DarkTones.ON_SURFACE);
        p.onSurfaceVariant = BuiltinWidgetStyle.atTone(theme.onSurfaceVariant, DarkTones.ON_SURFACE_VARIANT);
        p.primary = BuiltinWidgetStyle.atTone(theme.primary, DarkTones.PRIMARY);
        p.onPrimary = BuiltinWidgetStyle.atTone(theme.onPrimary, DarkTones.ON_PRIMARY);
        p.primaryContainer = BuiltinWidgetStyle.atTone(theme.primaryContainer, DarkTones.CONTAINER_ACCENT);
        p.onPrimaryContainer = BuiltinWidgetStyle.atTone(theme.onPrimaryContainer, DarkTones.ON_CONTAINER_ACCENT);
        p.secondaryContainer = BuiltinWidgetStyle.atTone(theme.secondaryContainer, DarkTones.CONTAINER_ACCENT);
        p.onSecondaryContainer = BuiltinWidgetStyle.atTone(theme.onSecondaryContainer, DarkTones.ON_CONTAINER_ACCENT);
        p.outline = BuiltinWidgetStyle.atTone(theme.outline, DarkTones.OUTLINE);
        p.outlineVariant = BuiltinWidgetStyle.atTone(theme.outlineVariant, DarkTones.OUTLINE_VARIANT);
        p.error = BuiltinWidgetStyle.atTone(theme.error, DarkTones.ERROR);
        p.errorContainer = BuiltinWidgetStyle.atTone(theme.errorContainer, DarkTones.CONTAINER_ACCENT);
        p.onErrorContainer = BuiltinWidgetStyle.atTone(theme.onErrorContainer, DarkTones.ON_CONTAINER_ACCENT);
        p.success = theme.success;
        return p;
    }

    /**
     * The pane's answer over {@code under} (the wallpaper under the launcher's dim, opaque): the
     * veil of the pane's own colour over {@code body} (the glass's tint, alpha included) that holds
     * the palette's weakest text and its accent to {@code textTarget} and {@code graphicTarget}.
     * What {@code WidgetInkSource#widgetInk} answers, memoised, for the page's rect.
     */
    @NonNull
    static OnGlass.Resolution resolve(@ColorInt int under, @ColorInt int body,
                                      @NonNull AssistantUi.Palette palette, double textTarget,
                                      double graphicTarget) {
        return OnGlass.resolveWidget(under, body, palette.onSurfaceVariant, textTarget,
            palette.primary, graphicTarget, veilColor(palette));
    }

    /** The colour the veil moves toward: the pane's own, surfaceContainerHigh on its side. */
    @ColorInt
    static int veilColor(@NonNull AssistantUi.Palette palette) {
        return OnGlass.opaque(palette.surfaceHigh);
    }

    /** Whether two palettes draw the same, so a re-dress with the same answer rebinds nothing. */
    static boolean same(@Nullable AssistantUi.Palette a, @Nullable AssistantUi.Palette b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.surface == b.surface && a.surfaceHigh == b.surfaceHigh
            && a.surfaceHighest == b.surfaceHighest && a.onSurface == b.onSurface
            && a.onSurfaceVariant == b.onSurfaceVariant && a.primary == b.primary
            && a.onPrimary == b.onPrimary && a.primaryContainer == b.primaryContainer
            && a.onPrimaryContainer == b.onPrimaryContainer
            && a.secondaryContainer == b.secondaryContainer
            && a.onSecondaryContainer == b.onSecondaryContainer && a.outline == b.outline
            && a.outlineVariant == b.outlineVariant && a.error == b.error
            && a.errorContainer == b.errorContainer && a.onErrorContainer == b.onErrorContainer
            && a.success == b.success;
    }

    /** No veil: what a pane with no glass, or nothing to measure, lays. */
    static final int NO_VEIL = Color.TRANSPARENT;
}
