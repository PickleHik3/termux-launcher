package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.google.android.material.color.utilities.Hct;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Properties;

/**
 * The Contrast row on a fixed scheme (wallpaper colours off): Default leaves the scheme as loaded,
 * Harder lifts the foreground's and the accents' contrast on the ground, Softer lowers it, hue
 * kept; and the live refresh rebuilds when only the level moved.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SchemeContrastTest {

    /** A mid-dark glass ground, where white text has room both ways. */
    private static final int GLASS = 0xff3a4450;

    @After
    public void resetScheme() {
        TerminalColors.COLOR_SCHEME.updateWith(new Properties());
    }

    // --- The built-in scheme ---

    @Test
    public void defaultLeavesTheBuiltInSchemeUntouched() {
        Properties out = SchemeContrast.apply(new Properties(), GLASS, TerminalContrastLevel.DEFAULT);
        assertTrue(out.isEmpty());
        out = SchemeContrast.apply(new Properties(), Color.TRANSPARENT, TerminalContrastLevel.DEFAULT);
        assertTrue(out.isEmpty());
    }

    @Test
    public void softerLowersTheBuiltInForegroundOnItsOwnBackground() {
        int before = resolved(new Properties())[TextStyle.COLOR_INDEX_FOREGROUND];
        int after = resolved(SchemeContrast.apply(new Properties(), Color.TRANSPARENT,
            TerminalContrastLevel.SOFTER))[TextStyle.COLOR_INDEX_FOREGROUND];
        assertEquals(0xffffffff, before);
        assertTrue(ratio(after, Color.BLACK) < ratio(before, Color.BLACK));
        assertTrue("never under Softer's floor", ratio(after, Color.BLACK) >= 3.0d);
    }

    @Test
    public void harderAndSofterMoveTheBuiltInSchemeOnGlass() {
        int[] base = resolved(new Properties());
        int[] softer = resolved(SchemeContrast.apply(new Properties(), GLASS,
            TerminalContrastLevel.SOFTER));
        int[] harder = resolved(SchemeContrast.apply(new Properties(), GLASS,
            TerminalContrastLevel.HARDER));
        int fg = TextStyle.COLOR_INDEX_FOREGROUND;
        assertTrue(ratio(softer[fg], GLASS) < ratio(base[fg], GLASS));
        // White is already as far from a dark ground as it goes: Harder keeps it.
        assertEquals(base[fg], harder[fg]);
        // The accents have room: dim blue (6495ed) on this ground.
        assertTrue(ratio(harder[4], GLASS) > ratio(base[4], GLASS));
        assertTrue(ratio(softer[4], GLASS) < ratio(base[4], GLASS));
        assertHueKept(base[4], harder[4]);
        assertHueKept(base[4], softer[4]);
    }

    @Test
    public void harderLiftsTheBuiltInForegroundOnALightGround() {
        int light = 0xffb8c4d0;
        int base = resolved(new Properties())[TextStyle.COLOR_INDEX_FOREGROUND];
        int harder = resolved(SchemeContrast.apply(new Properties(), light,
            TerminalContrastLevel.HARDER))[TextStyle.COLOR_INDEX_FOREGROUND];
        assertTrue(ratio(harder, light) > ratio(base, light));
    }

    // --- A user colors.properties ---

    private static Properties sample() {
        Properties p = new Properties();
        p.setProperty("background", "#282a36");
        p.setProperty("foreground", "#c0c0c0");
        p.setProperty("color1", "#ff5555");
        p.setProperty("color4", "#6272a4");
        p.setProperty("color8", "#6272a4");
        p.setProperty("cursor", "#f8f8f2");
        return p;
    }

    @Test
    public void defaultLeavesAUserSchemeAsLoaded() {
        assertEquals(sample(), SchemeContrast.apply(sample(), GLASS, TerminalContrastLevel.DEFAULT));
        assertEquals(sample(), SchemeContrast.apply(sample(), Color.TRANSPARENT,
            TerminalContrastLevel.DEFAULT));
    }

    @Test
    public void harderRaisesAndSofterLowersAUserScheme() {
        int bg = 0xff282a36;
        int[] base = resolved(sample());
        int[] softer = resolved(SchemeContrast.apply(sample(), Color.TRANSPARENT,
            TerminalContrastLevel.SOFTER));
        int[] harder = resolved(SchemeContrast.apply(sample(), Color.TRANSPARENT,
            TerminalContrastLevel.HARDER));
        int fg = TextStyle.COLOR_INDEX_FOREGROUND;
        assertTrue(ratio(harder[fg], bg) > ratio(base[fg], bg));
        assertTrue(ratio(softer[fg], bg) < ratio(base[fg], bg));
        assertTrue(ratio(harder[4], bg) > ratio(base[4], bg));
        assertHueKept(base[1], harder[1]);
        // The background and cursor are the user's.
        assertEquals(base[TextStyle.COLOR_INDEX_BACKGROUND], harder[TextStyle.COLOR_INDEX_BACKGROUND]);
        assertEquals("#f8f8f2", SchemeContrast.apply(sample(), Color.TRANSPARENT,
            TerminalContrastLevel.HARDER).getProperty("cursor"));
    }

    @Test
    public void harderOnGlassMeasuresTheGlassNotTheSchemeBackground() {
        int light = 0xffd0d4d8;
        int[] base = resolved(sample());
        int[] harder = resolved(SchemeContrast.apply(sample(), light, TerminalContrastLevel.HARDER));
        int fg = TextStyle.COLOR_INDEX_FOREGROUND;
        assertTrue(ratio(harder[fg], light) > ratio(base[fg], light));
    }

    // --- The live refresh ---

    @Test
    public void theLiveRefreshFollowsTheLevelWithWallpaperColoursOff() throws Exception {
        FakeTerminalHost host = new FakeTerminalHost(FakeTerminalHost.testContext(),
            FakeTerminalHost.testProperties());
        TermuxAppSharedPreferences prefs = host.preferences();
        prefs.setTerminalDynamicColorsEnabled(false);
        prefs.setTerminalContrastLevel(TerminalContrastLevel.DEFAULT.value);
        TermuxTerminalSessionActivityClient client =
            new TermuxTerminalSessionActivityClient(FakeTerminalHost.testContext(), host);
        client.applyTerminalColors();
        assertEquals(0xffffffff, foreground());

        // Nothing moved: the refresh leaves the palette alone.
        TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = 0xff123456;
        client.refreshMaterialTerminalColorsIfNeeded();
        assertEquals(0xff123456, foreground());

        // Only the level moved: the refresh rebuilds at once.
        prefs.setTerminalContrastLevel(TerminalContrastLevel.SOFTER.value);
        client.refreshMaterialTerminalColorsIfNeeded();
        assertNotEquals(0xff123456, foreground());
        assertTrue(ratio(foreground(), Color.BLACK) < 21d);

        // And back, as Discard and Undo do.
        prefs.setTerminalContrastLevel(TerminalContrastLevel.DEFAULT.value);
        client.refreshMaterialTerminalColorsIfNeeded();
        assertEquals(0xffffffff, foreground());
    }

    @Test
    public void theRendererFloorFollowsTheLevelWithWallpaperColoursOff() throws Exception {
        FakeTerminalHost host = new FakeTerminalHost(FakeTerminalHost.testContext(),
            FakeTerminalHost.testProperties());
        TermuxAppSharedPreferences prefs = host.preferences();
        prefs.setTerminalDynamicColorsEnabled(false);
        prefs.setTerminalContrastLevel(TerminalContrastLevel.SOFTER.value);
        assertEquals(3.0d, TermuxTerminalSessionActivityClient.terminalContrastFloor(prefs), 0d);
        prefs.setTerminalContrastLevel(TerminalContrastLevel.HARDER.value);
        assertEquals(7.0d, TermuxTerminalSessionActivityClient.terminalContrastFloor(prefs), 0d);
    }

    private static int foreground() {
        return TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND];
    }

    private static int[] resolved(Properties props) {
        TerminalColorScheme scheme = new TerminalColorScheme();
        scheme.updateWith(props);
        return scheme.mDefaultColors;
    }

    private static double ratio(int a, int b) {
        return MaterialTerminalColorScheme.contrastRatio(a | 0xff000000, b | 0xff000000);
    }

    private static void assertHueKept(int before, int after) {
        double a = Hct.fromInt(before).getHue();
        double b = Hct.fromInt(after).getHue();
        double d = Math.abs(a - b) % 360d;
        assertTrue("hue " + a + " -> " + b, Math.min(d, 360d - d) < 8d);
    }
}
