package com.termux.app.statusbar;

import androidx.annotation.ColorInt;

import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.PageMarkInk;
import com.termux.app.terminal.WindowChipInk;

/**
 * The inks of the Marks line on the status bar's ground: the current mark in the place's accent,
 * the other marks in the ground's neutral ink a tier under it ({@link PageMarkInk}, so the page
 * bar's marks and these read alike), and attention in the error colour, each solved on the band the
 * line stands on and on that band's side ({@code pale}). The band is what
 * {@code ChromeInk.onGlass} resolved for the bar, so light, dark, black, Material You and the
 * launcher's scheme chrome all arrive through it. Pure; asked when the band or the palette moves,
 * never per frame.
 */
public final class MarksLineInk {

    @ColorInt public final int current;
    @ColorInt public final int other;
    @ColorInt public final int attention;

    private MarksLineInk(int current, int other, int attention) {
        this.current = current;
        this.other = other;
        this.attention = attention;
    }

    /**
     * Solved on {@code band}, the bar's opaque ground.
     *
     * @param pale whether the chrome's ink is the pale one on this band
     * @param neutral the band's neutral ink seed ({@link WindowChipInk#neutralSeed})
     * @param accent the place's accent
     * @param error the error role, the attention colour
     */
    public static MarksLineInk onBand(@ColorInt int band, boolean pale, @ColorInt int neutral,
                                      @ColorInt int accent, @ColorInt int error) {
        int ground = OnGlass.opaque(band);
        int current = PageMarkInk.current(ground, accent, pale);
        int other = PageMarkInk.other(ground, neutral, current);
        int attention = WindowChipInk.towardPolarity(ground, error, pale,
            OnGlass.TARGET_LARGE_TEXT);
        return new MarksLineInk(current, other, attention);
    }

    /**
     * The authored inks, for a bar nothing can measure yet (no chrome, or a band not laid out):
     * the roles themselves, as the chips wear them, until a measurement lands.
     */
    public static MarksLineInk authored(@ColorInt int neutral, @ColorInt int accent,
                                        @ColorInt int error) {
        return new MarksLineInk(accent, neutral, error);
    }
}
