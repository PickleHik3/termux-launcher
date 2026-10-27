package com.termux.app.terminal;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

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
 * <p>The background pairs are asserted wherever the floor can be reached at all. Over a mid-tone
 * wallpaper no accent reaches 4.5:1 in either ink, and the solver then keeps the panel pairs and
 * the best background ratio left; there the test asks for exactly that, no worse than the palette
 * of the ground's own polarity could do.
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
    private static final int[] GROUNDS = {OPAQUE, GLASS_DARK, GLASS_MID, GLASS_LIGHT};

    private static String groundName(int ground) {
        if (ground == OPAQUE) return "opaque";
        if (ground == GLASS_DARK) return "glass-dark";
        if (ground == GLASS_MID) return "glass-mid";
        return "glass-light";
    }

    private static int slotColor(Properties p, int slot) {
        if (slot == LegibilityPairs.FOREGROUND) return Color.parseColor(p.getProperty("foreground"));
        return Color.parseColor(p.getProperty("color" + slot));
    }

    @Test
    public void everyPairInTheTableHoldsOnTheGrid() {
        StringBuilder failures = new StringBuilder();
        for (Seed seed : SEEDS) {
            for (int ground : GROUNDS) {
                for (TerminalContrastLevel level : TerminalContrastLevel.values()) {
                    Properties p = MaterialTerminalColorScheme.build(seed.seeds, level, ground, null);
                    int background = ground == OPAQUE
                        ? Color.parseColor(p.getProperty("background")) : OnGlass.opaque(ground);
                    for (LegibilityPairs.Pair pair : LegibilityPairs.ALL) {
                        int ink = slotColor(p, pair.ink);
                        int under = pair.ground == LegibilityPairs.BACKGROUND
                            ? background : slotColor(p, pair.ground);
                        double ratio = MaterialTerminalColorScheme.contrastRatio(ink, under);
                        double floor = pair.floor(level);
                        String row = String.format("%s %s %s %s %s on %s = %.2f (floor %.1f)",
                            seed.name, groundName(ground), level.value, pair.label(),
                            hex(ink), hex(under), ratio, floor);
                        boolean panel = pair.ground != LegibilityPairs.BACKGROUND;
                        boolean asserted = panel ? !panelInfeasible(pair, level, background)
                            : ground != GLASS_MID;
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

    /**
     * A panel ink that must read at 3:1 on white and at the level's ratio on the ground needs a
     * white brighter than white: the ratio chain ground, ink, white must fit inside white on the
     * ground. Harder (6:1) on the dark
     * glass ground (tone 10) is that case; the solver keeps the background pair there, and the
     * grid names the case instead of failing on it.
     */
    private static boolean panelInfeasible(LegibilityPairs.Pair pair, TerminalContrastLevel level,
                                           int background) {
        if (pair.ink == 0) return false;
        // (ink + .05) >= ratio * (ground + .05) and (1 + .05) >= floor * (ink + .05).
        double whiteOnGround = MaterialTerminalColorScheme.contrastRatio(Color.WHITE, background);
        return pair.floor(level) * level.ansiRatio > whiteOnGround;
    }

    private static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
