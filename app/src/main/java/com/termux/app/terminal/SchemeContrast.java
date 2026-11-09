package com.termux.app.terminal;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.chrome.OnGlass;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TextStyle;

import java.util.Properties;

/**
 * The Contrast row for a terminal that is not on wallpaper colours: a user {@code colors.properties}
 * or the built-in scheme. {@link MaterialTerminalColorScheme} builds its palette at the level; a
 * fixed scheme cannot be rebuilt, so this moves its foreground and ANSI colours along their own
 * HCT tone axis (hue kept, lightness changed) toward the level's contrast against what the text
 * stands on.
 *
 * <p>Default leaves the scheme exactly as loaded. Softer and Harder scale each colour's own
 * contrast by the level's body target over Default's (3/4.5 and 7/4.5), so a scheme keeps its
 * character and only reads softer or harder. Softer never takes a colour below the level's floor
 * unless it already sat there; Harder lifts every colour to at least the level's floor. Slots the
 * legibility table exempts from the background (ANSI black, which is meant to vanish into it)
 * are left alone.
 */
public final class SchemeContrast {

    private SchemeContrast() {}

    /**
     * {@code scheme} with its foreground and ANSI 1-15 moved toward {@code level} on
     * {@code ground}: the measured glass when it is not {@link Color#TRANSPARENT}, else the
     * scheme's own background. Only the keys whose colour moved are written; everything else is
     * returned as loaded. {@code scheme} itself is not changed.
     */
    @NonNull
    public static Properties apply(@NonNull Properties scheme, @ColorInt int ground,
                                   @NonNull TerminalContrastLevel level) {
        Properties result = new Properties();
        result.putAll(scheme);
        if (level == TerminalContrastLevel.DEFAULT) return result;

        // The scheme as the terminal will resolve it, defaults filled in where the file is silent.
        TerminalColorScheme resolved = new TerminalColorScheme();
        resolved.updateWith(TermuxTerminalSessionActivityClient.colorKeysOnly(scheme));
        int[] colors = resolved.mDefaultColors;
        boolean glass = Color.alpha(ground) != 0;
        int on = glass ? OnGlass.opaque(ground) : colors[TextStyle.COLOR_INDEX_BACKGROUND] | 0xff000000;
        double scale = level.bodyTarget / TerminalContrastLevel.DEFAULT.bodyTarget;

        int foreground = colors[TextStyle.COLOR_INDEX_FOREGROUND];
        int movedForeground = move(foreground, on, scale, level.bodyTarget, 0d);
        if (movedForeground != foreground) result.setProperty("foreground", hex(movedForeground));

        for (int slot = 1; slot < 16; slot++) {
            double floor = MaterialTerminalColorScheme.ansiFloor(slot, level, glass);
            if (floor <= 0d) continue;
            int color = colors[slot];
            boolean dimText = slot == 8;
            int moved = move(color, on, scale, floor,
                dimText ? 0d : MaterialTerminalColorScheme.ACCENT_CHROMA_KEPT);
            if (moved != color) result.setProperty("color" + slot, hex(moved));
        }
        return result;
    }

    /**
     * {@code color} moved to {@code scale} times its own contrast on {@code on}: up to at least
     * {@code floor} when {@code scale} is above one, down no further than {@code floor} (or where
     * it already stood, if lower) when below. Hue kept; only tones keeping {@code minChromaKept}
     * of the colour's chroma are taken.
     */
    @ColorInt
    @VisibleForTesting
    static int move(@ColorInt int color, @ColorInt int on, double scale, double floor,
                    double minChromaKept) {
        color |= 0xff000000;
        double ratio = MaterialTerminalColorScheme.contrastRatio(color, on);
        if (scale > 1d) {
            double target = Math.min(21d, Math.max(ratio * scale, floor));
            return MaterialTerminalColorScheme.contrastTone(color, on, target, 100d, 0d, 100d,
                minChromaKept);
        }
        double target = Math.max(ratio * scale, Math.min(ratio, floor));
        return soften(color, on, target, minChromaKept);
    }

    /**
     * {@code color} moved along its tone axis toward {@code on}, to the tone with the least
     * contrast that still reads at {@code target}; the colour itself when it reads at or under it.
     * The search stays on the colour's own side of the ground, so it never changes polarity.
     */
    @ColorInt
    private static int soften(@ColorInt int color, @ColorInt int on, double target,
                              double minChromaKept) {
        if (MaterialTerminalColorScheme.contrastRatio(color, on) <= target) return color;
        Hct source = Hct.fromInt(color);
        double groundTone = Hct.fromInt(on).getTone();
        int step = source.getTone() > groundTone ? -1 : 1;
        int best = color;
        int start = (int) Math.round(source.getTone());
        for (int tone = start; step < 0 ? tone >= groundTone : tone <= groundTone; tone += step) {
            int candidate = Hct.from(source.getHue(), source.getChroma(), tone).toInt();
            if (Hct.fromInt(candidate).getChroma() < source.getChroma() * minChromaKept) continue;
            if (MaterialTerminalColorScheme.contrastRatio(candidate, on) < target) break;
            best = candidate;
        }
        return best;
    }

    private static String hex(@ColorInt int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }
}
