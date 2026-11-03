package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.google.android.material.color.utilities.Hct;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Properties;

/**
 * The grey text and Contrast bug (editor-redesign §9): the terminal's dim text (ANSI bright
 * black, slot 8: comments, autosuggestions, a prompt's grey) hardly moved between Softer, Default
 * and Harder, because its tone was 45 or 50 at every level and its floor a fixed 3.0:1. On pong's
 * dark theme it read about 3.0, 3.3 and 3.7:1 against the background; a light theme 3.5, 4.2 and
 * 4.5:1. Now Harder sets it clearly further from the ground than Default, and Softer clearly
 * nearer, on both polarities and on glass over a dark wallpaper, while Default keeps the grey it
 * always had.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalContrastGreyTest {

    /** pong: surface, on_surface, primary, error (as MaterialTerminalColorSchemeTest's). */
    private static final MaterialTerminalColorScheme.Seeds PONG = new MaterialTerminalColorScheme.Seeds(
        0xFF0C141B, 0xFFDCE3ED, 0xFF93CCFF, 0xFFF2B8B5);
    /** A light theme's seeds. */
    private static final MaterialTerminalColorScheme.Seeds DAYLIGHT = new MaterialTerminalColorScheme.Seeds(
        0xFFF8F9FF, 0xFF191C20, 0xFF3A608F, 0xFFBA1A1A);
    /** A dark wallpaper's ground behind a glass pane. */
    private static final int DUSK = 0xFF1B1A17;

    /** Harder's grey reads at least this much more contrast than Default's, and Default's than Softer's. */
    private static final double HARDER_STEP = 1.5d;
    private static final double SOFTER_STEP = 1.3d;

    @Test
    public void harderDarkensOrLightensTheGreyAndSofterTheReverseOnAnOpaqueGround() {
        for (MaterialTerminalColorScheme.Seeds seeds
                : new MaterialTerminalColorScheme.Seeds[] {PONG, DAYLIGHT}) {
            String theme = seeds == PONG ? "dark" : "light";
            double softer = greyRatio(seeds, TerminalContrastLevel.SOFTER, Color.TRANSPARENT);
            double normal = greyRatio(seeds, TerminalContrastLevel.DEFAULT, Color.TRANSPARENT);
            double harder = greyRatio(seeds, TerminalContrastLevel.HARDER, Color.TRANSPARENT);
            assertTrue(theme + ": Harder's grey " + harder + " stands clear of Default's " + normal,
                harder >= normal * HARDER_STEP);
            assertTrue(theme + ": Softer's grey " + softer + " sits clearly under Default's " + normal,
                normal >= softer * SOFTER_STEP);
        }
    }

    @Test
    public void theGreyFollowsTheLevelOnGlassOverADarkWallpaper() {
        double softer = greyRatio(PONG, TerminalContrastLevel.SOFTER, DUSK);
        double normal = greyRatio(PONG, TerminalContrastLevel.DEFAULT, DUSK);
        double harder = greyRatio(PONG, TerminalContrastLevel.HARDER, DUSK);
        assertTrue("on glass Harder " + harder + " over Default " + normal,
            harder >= normal * HARDER_STEP);
        assertTrue("on glass Default " + normal + " over Softer " + softer,
            normal >= softer * SOFTER_STEP);
    }

    @Test
    public void harderMovesTheGreyTowardTheTextAndSofterTowardTheGround() {
        for (MaterialTerminalColorScheme.Seeds seeds
                : new MaterialTerminalColorScheme.Seeds[] {PONG, DAYLIGHT}) {
            boolean dark = seeds == PONG;
            double softer = greyTone(seeds, TerminalContrastLevel.SOFTER);
            double normal = greyTone(seeds, TerminalContrastLevel.DEFAULT);
            double harder = greyTone(seeds, TerminalContrastLevel.HARDER);
            if (dark) {
                assertTrue("dark: Harder lightens the grey (" + harder + " over " + normal + ")",
                    harder >= normal + 10d);
                assertTrue("dark: Softer darkens it (" + softer + " under " + normal + ")",
                    softer <= normal - 5d);
            } else {
                assertTrue("light: Harder darkens the grey (" + harder + " under " + normal + ")",
                    harder <= normal - 10d);
                assertTrue("light: Softer lightens it (" + softer + " over " + normal + ")",
                    softer >= normal + 5d);
            }
        }
    }

    @Test
    public void theGreyStaysDimTextAtEveryLevel() {
        for (MaterialTerminalColorScheme.Seeds seeds
                : new MaterialTerminalColorScheme.Seeds[] {PONG, DAYLIGHT}) {
            for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
                Properties palette = MaterialTerminalColorScheme.build(seeds, level,
                    Color.TRANSPARENT, null);
                int background = color(palette, "background");
                double grey = ratio(color(palette, "color8"), background);
                double text = ratio(color(palette, "foreground"), background);
                assertTrue(level.value + ": the grey " + grey + " reads under the text " + text,
                    grey < text);
                assertTrue(level.value + ": the grey meets its level's ratio",
                    grey >= level.dimRatio - 0.05d);
                double tone = Hct.fromInt(color(palette, "color8")).getTone();
                assertTrue(level.value + ": the grey stays between black and white",
                    tone > Hct.fromInt(color(palette, "color0")).getTone()
                        && tone < Hct.fromInt(color(palette, "color7")).getTone());
            }
        }
    }

    @Test
    public void defaultKeepsTheGreyItAlwaysHad() {
        assertEquals(45d, greyTone(PONG, TerminalContrastLevel.DEFAULT), 1.5d);
        assertEquals(50d, greyTone(DAYLIGHT, TerminalContrastLevel.DEFAULT), 1.5d);
        assertEquals(3.0d, MaterialTerminalColorScheme.ansiFloor(8, TerminalContrastLevel.DEFAULT),
            0d);
    }

    private static double greyRatio(MaterialTerminalColorScheme.Seeds seeds,
                                    TerminalContrastLevel level, int ground) {
        Properties palette = MaterialTerminalColorScheme.build(seeds, level, ground, null);
        int on = Color.alpha(ground) == 0 ? color(palette, "background") : ground;
        return ratio(color(palette, "color8"), on);
    }

    private static double greyTone(MaterialTerminalColorScheme.Seeds seeds,
                                   TerminalContrastLevel level) {
        Properties palette = MaterialTerminalColorScheme.build(seeds, level, Color.TRANSPARENT,
            null);
        return Hct.fromInt(color(palette, "color8")).getTone();
    }

    private static double ratio(int ink, int ground) {
        return MaterialTerminalColorScheme.contrastRatio(ink, ground);
    }

    private static int color(Properties palette, String key) {
        return Color.parseColor(palette.getProperty(key));
    }
}
