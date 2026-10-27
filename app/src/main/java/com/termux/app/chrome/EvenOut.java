package com.termux.app.chrome;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.android.material.color.utilities.ColorUtils;
import com.google.android.material.color.utilities.Hct;
import com.termux.app.terminal.LegibilityPairs;
import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The glass even-out stage, as arithmetic only (ADR 0014, wave 2). A bright or busy wallpaper
 * behind a text pane leaves no ground on which every ink can reach its floor: over a mid-tone
 * wallpaper no accent reaches 3:1 in either ink. Rather than let the palette give up, the stage
 * squeezes the pane's lightness into a narrow band on the ink's own side of the limit, so the
 * palette is solved against a ground it can actually read on.
 *
 * <p>Everything here is in HCT tone, which equals CIE L*, so a tone-to-tone WCAG ratio is exact
 * for any hue. Nothing here draws, reads preferences or touches a View: callers sample a pane with
 * {@code GlassBackdropCache.sampleBitmapLightness}, ask for the {@link #ceiling}, build a
 * {@link Mapping} and render it. The even-out sits after blur and before tint and Opacity, so the
 * band ends are composited with {@link OnGlass#backdrop} exactly as today's single ground is.
 */
public final class EvenOut {

    private EvenOut() {}

    /** One pair's limit: the ground tone past which this ink, at its most extreme tone, fails. */
    public static final class PairLimit {
        @NonNull public final String label;
        /** The most extreme tone the ink may reach on glass. */
        public final double inkTone;
        /** The contrast the pair is held to on glass. */
        public final double floor;
        /** The highest (pale ink) or lowest (dark ink) ground tone that still meets {@link #floor}. */
        public final double groundTone;

        PairLimit(@NonNull String label, double inkTone, double floor, double groundTone) {
            this.label = label;
            this.inkTone = inkTone;
            this.floor = floor;
            this.groundTone = groundTone;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s: ink tone %.1f at %.2f:1 -> ground %.1f",
                label, inkTone, floor, groundTone);
        }
    }

    /** The ground limit of a palette: the most restrictive pair, and every pair for tests. */
    public static final class Ceiling {
        /** The tone the ground may not pass: a ceiling for pale ink, a floor for dark ink. */
        public final double tone;
        @NonNull public final List<PairLimit> pairs;

        Ceiling(double tone, @NonNull List<PairLimit> pairs) {
            this.tone = tone;
            this.pairs = pairs;
        }
    }

    /**
     * The ground limit for {@code palette}: for each background pair of {@link LegibilityPairs}, the
     * most extreme tone its ink may reach on glass, and the ground tone at which that ink only just
     * meets its glass floor. The limit is the most restrictive pair, so a ground inside it keeps
     * every ink at its floor and the palette solve is feasible.
     *
     * <p>The ink's most extreme tone is where the solver may send it: its own slot tone moved by
     * the level's {@code toneReach} towards the far end from the ground, held to the solver's tone
     * range. The grey rises on glass up to {@code glassDimToneMax} (a dark ink's grey falls to 100
     * minus it). Red and blue also have to read on ANSI white, whose top tone is
     * {@link MaterialTerminalColorScheme#WHITE_TONE_MAX}, so for pale ink their tone is capped at
     * the one that keeps {@link LegibilityPairs#PANEL_INK_FLOOR} on that white; a dark ink stands
     * on the opposite side of the white and needs no cap. The floors are the glass ones: the
     * foreground {@code bodyTarget}, normal accents {@code glassAnsiRatio}, bright accents
     * {@code ansiRatio}, the grey {@code dimRatio}.
     */
    @NonNull
    public static Ceiling ceiling(@NonNull Properties palette, @NonNull ChromeInk.Polarity polarity,
                                  @NonNull TerminalContrastLevel level) {
        boolean pale = polarity == ChromeInk.Polarity.PALE_INK;
        List<PairLimit> limits = new ArrayList<>();
        for (LegibilityPairs.Pair pair : LegibilityPairs.ALL) {
            if (pair.ground != LegibilityPairs.BACKGROUND) continue;
            boolean foreground = pair.ink == LegibilityPairs.FOREGROUND;
            String key = foreground ? "foreground" : "color" + pair.ink;
            double slotTone = Hct.fromInt(Color.parseColor(palette.getProperty(key))).getTone();
            double floor;
            double tone;
            if (foreground) {
                floor = level.bodyTarget;
                tone = pale
                    ? Math.min(MaterialTerminalColorScheme.TEXT_TONE_MAX, slotTone + level.toneReach)
                    : Math.max(MaterialTerminalColorScheme.TEXT_TONE_MIN, slotTone - level.toneReach);
            } else if (pair.ink == 8) {
                floor = level.dimRatio;
                tone = pale ? level.glassDimToneMax
                    : Math.max(MaterialTerminalColorScheme.DIM_TONE_MIN, 100d - level.glassDimToneMax);
            } else {
                floor = pair.ink <= 6 ? level.glassAnsiRatio : level.ansiRatio;
                tone = pale
                    ? Math.min(MaterialTerminalColorScheme.ACCENT_TONE_MAX, slotTone + level.toneReach)
                    : Math.max(MaterialTerminalColorScheme.ACCENT_TONE_MIN, slotTone - level.toneReach);
                double onWhite = LegibilityPairs.onWhiteFloor(pair.ink);
                if (pale && onWhite > 0d) {
                    tone = Math.min(tone, toneAtRatio(MaterialTerminalColorScheme.WHITE_TONE_MAX,
                        onWhite, true));
                }
            }
            limits.add(new PairLimit(pair.label(), tone, floor, toneAtRatio(tone, floor, pale)));
        }
        double worst = pale ? 100d : 0d;
        for (PairLimit limit : limits) {
            worst = pale ? Math.min(worst, limit.groundTone) : Math.max(worst, limit.groundTone);
        }
        return new Ceiling(worst, Collections.unmodifiableList(limits));
    }

    /**
     * The tone whose WCAG ratio against {@code tone} is exactly {@code ratio}, on the darker side
     * when {@code darker} and the lighter side otherwise. A ratio that cannot be met inside the
     * tone range pins to its end, 0 or 100.
     */
    private static double toneAtRatio(double tone, double ratio, boolean darker) {
        double y = ColorUtils.yFromLstar(tone) / 100d;
        double other = darker ? (y + 0.05d) / ratio - 0.05d : ratio * (y + 0.05d) - 0.05d;
        return Math.max(0d, Math.min(100d, ColorUtils.lstarFromY(other * 100d)));
    }

    /** The WCAG contrast between two tones. */
    public static double toneRatio(double toneA, double toneB) {
        double a = ColorUtils.yFromLstar(toneA) / 100d + 0.05d;
        double b = ColorUtils.yFromLstar(toneB) / 100d + 0.05d;
        return Math.max(a, b) / Math.min(a, b);
    }

    /** A linear lightness map, in tone, of one band. */
    public static final class Mapping {
        public final double srcLo;
        public final double srcHi;
        public final double dstLo;
        public final double dstHi;
        /** The mean colour whose hue and chroma the band ends keep. */
        @ColorInt public final int meanColor;

        Mapping(double srcLo, double srcHi, double dstLo, double dstHi, @ColorInt int meanColor) {
            this.srcLo = srcLo;
            this.srcHi = srcHi;
            this.dstLo = dstLo;
            this.dstHi = dstHi;
            this.meanColor = meanColor;
        }

        /** True when the map leaves every tone where it was: the stage draws nothing. */
        public boolean isIdentity() {
            return srcLo == dstLo && srcHi == dstHi;
        }

        /** The mapped tone of {@code tone}; tones outside the source range extend the line. */
        public double apply(double tone) {
            if (srcHi <= srcLo) return dstLo;
            return dstLo + (tone - srcLo) * (dstHi - dstLo) / (srcHi - srcLo);
        }
    }

    /**
     * The map that keeps a pane's [p2, p98] inside the ceiling. [p2, p98] goes linearly onto
     * [c - w/2, c + w/2] with {@code w} the level's {@code evenOutWidth} and
     * {@code c = min(mean, ceiling - w/2)} for pale ink; mirrored for dark ink,
     * {@code c = max(mean, floor + w/2)}. When the range already sits on the ink's side of the
     * limit and is no wider than {@code w}, the map is the identity, so a dark wallpaper under
     * pale ink is untouched. It never widens: a range narrower than {@code w} keeps its own width
     * and is only shifted.
     */
    @NonNull
    public static Mapping map(@NonNull GlassBackdropCache.Lightness stats, @NonNull Ceiling ceiling,
                              @NonNull ChromeInk.Polarity polarity,
                              @NonNull TerminalContrastLevel level) {
        boolean pale = polarity == ChromeInk.Polarity.PALE_INK;
        double span = Math.max(0d, stats.p98 - stats.p2);
        boolean fits = pale ? stats.p98 <= ceiling.tone : stats.p2 >= ceiling.tone;
        if (fits && span <= level.evenOutWidth) {
            return new Mapping(stats.p2, stats.p98, stats.p2, stats.p98, stats.meanColor);
        }
        double width = Math.min(level.evenOutWidth, span);
        double center = pale ? Math.min(stats.mean, ceiling.tone - width / 2d)
            : Math.max(stats.mean, ceiling.tone + width / 2d);
        return new Mapping(stats.p2, stats.p98, center - width / 2d, center + width / 2d,
            stats.meanColor);
    }

    /** The darkest and lightest ground of a band, opaque, after tint and Opacity. */
    public static final class BandEnds {
        @ColorInt public final int darkest;
        @ColorInt public final int lightest;

        BandEnds(@ColorInt int darkest, @ColorInt int lightest) {
            this.darkest = darkest;
            this.lightest = lightest;
        }
    }

    /**
     * The ground at each end of the mapped band, composited with {@link OnGlass#backdrop} so the
     * wallpaper dim and the band's tint act on top as they do for today's single mean ground. Each
     * end keeps the mean colour's hue and chroma at the mapped tone; HCT lowers chroma only as far
     * as the gamut at that tone needs.
     */
    @NonNull
    public static BandEnds bandEnds(@NonNull Mapping mapping, @ColorInt int dim, @ColorInt int tint) {
        Hct mean = Hct.fromInt(mapping.meanColor);
        int low = OnGlass.backdrop(Hct.from(mean.getHue(), mean.getChroma(),
            clampTone(mapping.apply(mapping.srcLo))).toInt(), dim, tint);
        int high = OnGlass.backdrop(Hct.from(mean.getHue(), mean.getChroma(),
            clampTone(mapping.apply(mapping.srcHi))).toInt(), dim, tint);
        return ColorUtils.lstarFromArgb(low) <= ColorUtils.lstarFromArgb(high)
            ? new BandEnds(low, high) : new BandEnds(high, low);
    }

    private static double clampTone(double tone) {
        return Math.max(0d, Math.min(100d, tone));
    }

    /**
     * The single ground the palette is solved against: the lighter end for pale ink (the end where
     * pale ink reads worst), the darker for dark ink.
     */
    @ColorInt
    public static int worstEnd(@NonNull BandEnds ends, @NonNull ChromeInk.Polarity polarity) {
        return polarity == ChromeInk.Polarity.PALE_INK ? ends.lightest : ends.darkest;
    }
}
