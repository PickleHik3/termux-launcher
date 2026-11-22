package com.termux.app.terminal;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.google.android.material.color.utilities.Hct;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.EvenOut;
import com.termux.app.chrome.GlassBackdropCache;
import com.termux.app.chrome.OnGlass;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Properties;

/**
 * The terminal palette against {@link LegibilityPairs}, over a grid of themes, grounds and levels.
 *
 * <p>It grew from Lamgerr's report (10-25): termux-change-repo's mirror list was pale red on light
 * grey. dialog paints its box as ANSI white (slot 7) and draws black, red and blue on it, and the
 * dark recipe's red and blue read on that white at 1.00:1. Every pair in the table is asserted
 * here, and a failure names the seed, ground, level, pair, both colours and the ratio, so a miss
 * can't ship silently.
 *
 * <p>The mid glass grounds are the even-out stage's worst ends (ADR 0014, wave 2) for a mid-tone
 * wallpaper like pong's cloud pane under pale ink and a light one under dark ink, not the raw mid
 * colour: over a raw mid-tone no accent reaches its floor in either ink, which is what the stage
 * exists to prevent. On glass the floors are the glass ones, and red and blue are held by the
 * panel pair only (best effort on the ground).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LegibilityContractTest {

    private static final class Seed {
        final String name;
        final MaterialTerminalColorScheme.Seeds seeds;

        Seed(String name, int surface, int onSurface, int primary, int error) {
            this.name = name;
            this.seeds = new MaterialTerminalColorScheme.Seeds(surface, onSurface, primary, error);
        }
    }

    private static final Seed[] SEEDS = {
        new Seed("pong", 0xFF0C141B, 0xFFDCE3ED, 0xFF93CCFF, 0xFFF2B8B5),
        new Seed("daylight", 0xFFF8F9FF, 0xFF191C20, 0xFF3A608F, 0xFFBA1A1A),
        // Saturated: a deep violet surface with a hot pink primary, on the dark recipe.
        new Seed("violet", 0xFF2B0A3D, 0xFFF3DAFF, 0xFFFF2DA0, 0xFFFF5449),
        // Saturated and light: a mint surface with a green primary, on the light recipe.
        new Seed("mint", 0xFFE6FFF0, 0xFF002111, 0xFF00A86B, 0xFFB3261E),
        // Grey: no hue anywhere, so the accents rely on the band's chroma floor alone.
        new Seed("grey", 0xFF202020, 0xFFE0E0E0, 0xFF9E9E9E, 0xFFCF6679),
    };

    private static final int OPAQUE = Color.TRANSPARENT;
    private static final int GLASS_DARK = 0xFF1B1A17;
    private static final int GLASS_MID = 0xFF7D7A78;
    private static final int GLASS_LIGHT = 0xFFE9E6E2;
    /** Stand-ins resolved per seed and level into the even-out worst end. */
    private static final int MID_PALE = 1;
    private static final int MID_DARK = 2;
    private static final int[] GROUNDS = {OPAQUE, GLASS_DARK, GLASS_LIGHT, MID_PALE, MID_DARK};

    private static String groundName(int ground) {
        if (ground == OPAQUE) return "opaque";
        if (ground == GLASS_DARK) return "glass-dark";
        if (ground == MID_PALE) return "glass-mid-pale";
        if (ground == MID_DARK) return "glass-mid-dark";
        return "glass-light";
    }

    /**
     * The worst end of a mid wallpaper's evened-out band: the recipe's ground limit for the
     * polarity, the 2a mapping of pong's cloud-pane statistics (or a light wallpaper's under dark
     * ink), and the band end the ink reads worst on, with the pane's tint transparent.
     */
    private static int midWorstEnd(Seed seed, TerminalContrastLevel level, boolean pale) {
        Properties recipe = MaterialTerminalColorScheme.build(seed.seeds, level,
            pale ? 0xFF0A0A0A : 0xFFF5F5F5, null);
        ChromeInk.Polarity polarity = pale ? ChromeInk.Polarity.PALE_INK : ChromeInk.Polarity.DARK_INK;
        EvenOut.Ceiling ceiling = EvenOut.ceiling(recipe, polarity, level);
        GlassBackdropCache.Lightness stats = pale
            ? new GlassBackdropCache.Lightness(13.5d, 39.5d, 67.8d, Hct.from(250d, 6d, 39.5d).toInt())
            : new GlassBackdropCache.Lightness(40d, 75d, 95d, Hct.from(250d, 6d, 75d).toInt());
        EvenOut.Mapping mapping = EvenOut.map(stats, ceiling, polarity, level);
        return EvenOut.worstEnd(EvenOut.bandEnds(mapping, Color.TRANSPARENT, Color.TRANSPARENT),
            polarity);
    }

    private static int slotColor(Properties p, int slot) {
        if (slot == LegibilityPairs.FOREGROUND) return Color.parseColor(p.getProperty("foreground"));
        return Color.parseColor(p.getProperty("color" + slot));
    }

    @Test
    public void everyPairInTheTableHoldsOnTheGrid() {
        StringBuilder failures = new StringBuilder();
        for (Seed seed : SEEDS) {
            for (int groundCase : GROUNDS) {
                for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
                    int ground = groundCase == MID_PALE ? midWorstEnd(seed, level, true)
                        : groundCase == MID_DARK ? midWorstEnd(seed, level, false) : groundCase;
                    boolean glass = ground != OPAQUE;
                    boolean band = groundCase == MID_PALE || groundCase == MID_DARK;
                    Properties p = MaterialTerminalColorScheme.build(seed.seeds, level, ground, null);
                    int background = ground == OPAQUE
                        ? Color.parseColor(p.getProperty("background")) : OnGlass.opaque(ground);
                    for (LegibilityPairs.Pair pair : LegibilityPairs.ALL) {
                        int ink = slotColor(p, pair.ink);
                        int under = pair.ground == LegibilityPairs.BACKGROUND
                            ? background : slotColor(p, pair.ground);
                        double ratio = MaterialTerminalColorScheme.contrastRatio(ink, under);
                        double floor = pair.ground == LegibilityPairs.BACKGROUND
                            && pair.ink != LegibilityPairs.FOREGROUND
                            ? (glass && pair.ink != 1 && pair.ink != 4
                                ? pair.floor(level, true)
                                : LegibilityPairs.backgroundFloor(pair.ink, level, slotColor(p, 7),
                                    background))
                            : pair.floor(level, glass);
                        String row = String.format("%s %s %s %s %s on %s = %.2f (floor %.1f)",
                            seed.name, groundName(groundCase), level.value, pair.label(),
                            hex(ink), hex(under), ratio, floor);
                        boolean panel = pair.ground != LegibilityPairs.BACKGROUND;
                        // Red and blue on the evened band: the panel pair holds them, the ground
                        // is best effort (ADR 0014, decided 2026-10-27).
                        boolean bestEffort = band && !panel && (pair.ink == 1 || pair.ink == 4);
                        boolean asserted = !bestEffort;
                        if (band && seed.name.equals("pong") && !panel
                            && (pair.ink == LegibilityPairs.FOREGROUND || pair.ink == 8
                                || pair.ink == 1 || pair.ink == 2)) {
                            System.out.println("[legibility-mid] " + row);
                        }
                        if (asserted && ratio + .01 < floor) failures.append(row).append('\n');
                        if (pair.ground == 7 && pair.ink == 1
                            || pair.ink == 4 && pair.ground == 7) {
                            System.out.println("[legibility] " + row);
                        }
                    }
                }
            }
        }
        assertTrue("pairs under their floor:\n" + failures, failures.length() == 0);
    }

    private static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
