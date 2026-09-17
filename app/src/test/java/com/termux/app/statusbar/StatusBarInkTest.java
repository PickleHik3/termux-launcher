package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.ColorInt;

import com.termux.app.chrome.OnGlass;
import com.termux.app.theme.SchemeTone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * What the status bar's content measures on the glass the user reported it on.
 *
 * <p>Every backdrop here was measured on the reporting device in light mode, and every ratio
 * asserted is the ratio of what actually lands — a colour composited at its own alpha over the
 * band, not the colour the code happened to ask a palette for. So a failure in this file is a
 * statement about what the user is looking at: "the white mode lacks contrast for right widgets on
 * statusbar and alphabets row looks fuzzy and the sessions chip has visibility issue".</p>
 *
 * <p>The before-figures in the assertions' messages are what those elements measured on the phone:
 * CPU 1.03, RAM 1.10, weather ~1.0 and its Home-place line 1.11, the separator dots 1.01, the
 * session chip 1.75 in light mode and 3.90 in dark.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarInkTest {

    /** Measured behind the status bar in light mode on the reporting device. */
    private static final int STATUS_GLASS = 0xFF6A5755;
    /** Measured behind the A&ndash;Z strip: the marginal band, and the harder of the two. */
    private static final int AZ_GLASS = 0xFF657271;
    /** A light wallpaper's glass, the other end of the same problem. */
    private static final int LIGHT_GLASS = 0xFFE6E2DD;

    /** {@code termux_primary}, light: the CPU tier's hue. */
    private static final int LIGHT_PRIMARY = 0xFF345CA8;
    /** {@code termux_secondary}, light: the RAM tier's hue. */
    private static final int LIGHT_SECONDARY = 0xFF556070;
    /** {@code colorTertiary} as the light theme resolves it, near enough: the weather's hue. */
    private static final int LIGHT_TERTIARY = 0xFF6B5778;
    /** {@code termux_on_surface_variant}, light: what a muted widget is drained to. */
    private static final int LIGHT_ON_SURFACE_VARIANT = 0xFF586171;

    /** {@code termux_surface_panel_high}: the chip's fill, light and night. */
    private static final int LIGHT_PANEL = 0xFFE1E7F2;
    private static final int NIGHT_PANEL = 0xFF202837;
    /** {@code termux_on_surface}: the chip's label, light and night. */
    private static final int LIGHT_ON_SURFACE = 0xFF171C24;
    private static final int NIGHT_ON_SURFACE = 0xFFE8EDF7;
    /** {@code termux_primary}, night. */
    private static final int NIGHT_PRIMARY = 0xFFB8C7FF;
    /** The night mode's own glass behind the bar, dark as Android dims a wallpaper for it. */
    private static final int NIGHT_GLASS = 0xFF1B1A17;

    // ------------------------------------------------------------------ the three stat tiers

    /**
     * Every tier's label clears the body-text floor on the band the user reported, in its own hue.
     * Before this round they measured 1.03, 1.10 and ~1.0 — the hierarchy was intact and none of it
     * was readable.
     */
    @Test
    public void everyWidgetTierLabelClearsTheBodyFloorOnTheReportedGlass() {
        for (int glass : new int[] {STATUS_GLASS, AZ_GLASS, LIGHT_GLASS}) {
            assertClears("the CPU label (was 1.03 on the status glass)", glass, LIGHT_PRIMARY,
                OnGlass.TARGET_BODY_TEXT);
            assertClears("the RAM label (was 1.10)", glass, LIGHT_SECONDARY,
                OnGlass.TARGET_BODY_TEXT);
            assertClears("the weather label and its Home-place line (were ~1.0 and 1.11)", glass,
                LIGHT_TERTIARY, OnGlass.TARGET_BODY_TEXT);
        }
    }

    /** The stat icons are graphics, so they are held to the large-text floor and no higher. */
    @Test
    public void everyWidgetTierIconClearsTheGraphicsFloor() {
        for (int glass : new int[] {STATUS_GLASS, AZ_GLASS, LIGHT_GLASS}) {
            assertClears("the CPU icon", glass, LIGHT_PRIMARY, OnGlass.TARGET_LARGE_TEXT);
            assertClears("the RAM icon", glass, LIGHT_SECONDARY, OnGlass.TARGET_LARGE_TEXT);
            assertClears("the weather icon", glass, LIGHT_TERTIARY, OnGlass.TARGET_LARGE_TEXT);
        }
    }

    /**
     * The hierarchy the row keeps is hue and order only. The CPU figure was the one bold, larger
     * figure beside two regular ones and read as the odd one out; every tier is now drawn at the
     * temperature's weight and size, and none of them is dimmer than another.
     */
    @Test
    public void theThreeTiersShareOneWeightAndSizeAndStayDistinguishableByHue() {
        assertEquals("the CPU figure is no heavier than the RAM figure",
            StatusBarInk.weightFor(StatusBarWidgetView.ColorRole.SECONDARY),
            StatusBarInk.weightFor(StatusBarWidgetView.ColorRole.PRIMARY));
        assertEquals("and the RAM figure no heavier than the weather",
            StatusBarInk.weightFor(StatusBarWidgetView.ColorRole.TERTIARY),
            StatusBarInk.weightFor(StatusBarWidgetView.ColorRole.SECONDARY));
        assertEquals("the CPU figure is the temperature's size",
            StatusBarInk.textSizeSpFor(StatusBarWidgetView.ColorRole.TERTIARY),
            StatusBarInk.textSizeSpFor(StatusBarWidgetView.ColorRole.PRIMARY), 0f);
        assertEquals("and so is the RAM figure",
            StatusBarInk.textSizeSpFor(StatusBarWidgetView.ColorRole.TERTIARY),
            StatusBarInk.textSizeSpFor(StatusBarWidgetView.ColorRole.SECONDARY), 0f);
        assertTrue("nothing is drawn bold", StatusBarInk.WEIGHT_PRIMARY < 700);

        // And the tiers are still three different colours on the band, not one ink three times:
        // the toning moves a hue along its own tone axis and never onto another hue's.
        int surface = OnGlass.opaque(STATUS_GLASS);
        int cpu = OnGlass.resolveBare(surface, LIGHT_PRIMARY, OnGlass.TARGET_BODY_TEXT).ink;
        int ram = OnGlass.resolveBare(surface, LIGHT_SECONDARY, OnGlass.TARGET_BODY_TEXT).ink;
        int weather = OnGlass.resolveBare(surface, LIGHT_TERTIARY, OnGlass.TARGET_BODY_TEXT).ink;
        assertNotEquals("CPU and RAM are still told apart by hue", cpu, ram);
        assertNotEquals("RAM and weather too", ram, weather);
        assertNotEquals("and CPU and weather", cpu, weather);

        // No tier is dimmer than another: they are all drawn at full alpha now.
        assertEquals(255, Color.alpha(cpu));
        assertEquals(255, Color.alpha(ram));
        assertEquals(255, Color.alpha(weather));
    }

    /**
     * A muted widget still fades — that is the point of the state — but only as far as the band it
     * is standing on can carry, and never below the graphics floor.
     */
    @Test
    public void aMutedWidgetFadesOnlyAsFarAsTheBandAllows() {
        for (int glass : new int[] {STATUS_GLASS, AZ_GLASS, LIGHT_GLASS}) {
            int surface = OnGlass.opaque(glass);
            int muted = StatusBarInk.inkAtAlpha(surface, LIGHT_ON_SURFACE_VARIANT,
                StatusBarInk.MUTED_ALPHA, OnGlass.TARGET_LARGE_TEXT);
            assertTrue("a muted widget is still read at " + hex(glass),
                StatusBarInk.shownRatio(muted, surface) >= OnGlass.TARGET_LARGE_TEXT);
            assertTrue("and it is never asked to be more solid than it wanted to be",
                Color.alpha(muted) >= StatusBarInk.MUTED_ALPHA);
        }
    }

    // ------------------------------------------------------------------ the separator dots

    /**
     * The dots are 6&nbsp;px of rhythm carrying no information, so they get the decoration tier and
     * not a pixel more: the user was offered the promotion to 3:1 and did not take it. They
     * measured 1.01 before.
     */
    @Test
    public void theSeparatorDotsClearDecorationAndAreNotQuietlyPromoted() {
        for (int glass : new int[] {STATUS_GLASS, AZ_GLASS, LIGHT_GLASS}) {
            int surface = OnGlass.opaque(glass);
            int dot = OnGlass.inkOnBand(
                OnGlass.resolveBare(surface, LIGHT_SECONDARY, OnGlass.TARGET_BODY_TEXT),
                LIGHT_SECONDARY, LIGHT_SECONDARY, OnGlass.TARGET_DECORATION);
            double ratio = SchemeTone.contrastRatio(dot, surface);
            assertTrue("a dot is visible at " + hex(glass) + " (was 1.01): " + ratio,
                ratio >= OnGlass.TARGET_DECORATION);
        }

        // The tier itself is the decision, and it is below the graphics floor on purpose.
        assertTrue(OnGlass.TARGET_DECORATION < OnGlass.TARGET_LARGE_TEXT);
        assertEquals(2.0d, OnGlass.TARGET_DECORATION, .0001d);
    }

    // ------------------------------------------------------------------ the session chip

    /**
     * The chip follows the mode in <em>both</em> modes. Light mode measured 1.75 because a dark
     * label sat on a fill the place accent had been blended 55% towards black; dark mode measured
     * 3.90, under the floor as well.
     */
    @Test
    public void theSessionChipLabelClearsTheBodyFloorInBothModes() {
        StatusBarInk.Chip light = StatusBarInk.chip(STATUS_GLASS, LIGHT_PANEL,
            SessionsIndicatorView.CONTAINER_ALPHA, LIGHT_ON_SURFACE, LIGHT_PRIMARY);
        assertTrue("the chip's label in light mode (was 1.75): " + light,
            light.labelRatio >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("its fill is light, because the mode is", isLighter(light.surface, STATUS_GLASS));

        StatusBarInk.Chip night = StatusBarInk.chip(NIGHT_GLASS, NIGHT_PANEL,
            SessionsIndicatorView.CONTAINER_ALPHA, NIGHT_ON_SURFACE, NIGHT_PRIMARY);
        assertTrue("the chip's label in dark mode (was 3.90): " + night,
            night.labelRatio >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("its fill is dark, because the mode is",
            !isLighter(night.surface, 0xFF808080));
    }

    /**
     * The place the chip belongs to moved to the rim, so the rim has to be a mark rather than a
     * hint — and it has to still be the place's own colour, which is the only thing it says.
     */
    @Test
    public void theSessionChipsRimCarriesThePlaceAndCanBeSeen() {
        for (int accent : new int[] {LIGHT_PRIMARY, 0xFF6B5778, 0xFF7A5230}) {
            StatusBarInk.Chip chip = StatusBarInk.chip(STATUS_GLASS, LIGHT_PANEL,
                SessionsIndicatorView.CONTAINER_ALPHA, LIGHT_ON_SURFACE, accent);
            assertTrue("the rim reads on the chip: " + chip,
                chip.strokeRatio >= OnGlass.TARGET_LARGE_TEXT);
            assertTrue("and it is still recognisably the place's colour",
                SchemeTone.hueDistance(chip.stroke, accent) < 12d);
        }

        // Two places must not arrive at the same rim: that is the whole of what it says.
        StatusBarInk.Chip terminal = StatusBarInk.chip(STATUS_GLASS, LIGHT_PANEL,
            SessionsIndicatorView.CONTAINER_ALPHA, LIGHT_ON_SURFACE, LIGHT_PRIMARY);
        StatusBarInk.Chip widgets = StatusBarInk.chip(STATUS_GLASS, LIGHT_PANEL,
            SessionsIndicatorView.CONTAINER_ALPHA, LIGHT_ON_SURFACE, LIGHT_TERTIARY);
        assertNotEquals(terminal.stroke, widgets.stroke);
    }

    // ------------------------------------------------------------------ the place lens

    /**
     * The lens glyph at every fade the wall can be dragged through, and at the alpha a peeking
     * mark is really drawn with. It measured 1.11 on the Home place's band.
     *
     * <p>The fade is the extra-keys row's own — a neighbour is the place's colour held back to
     * {@link StatusBarLensMetrics#UNFOCUSED_GLYPH_SHARE}, not drained towards a grey — so the floor
     * now has a deeper alpha to carry, and carries it by walking the tone rather than the fade.
     */
    @Test
    public void theLensGlyphReadsAtEveryFadeAndEveryPeek() {
        for (int glass : new int[] {STATUS_GLASS, AZ_GLASS, LIGHT_GLASS}) {
            int surface = OnGlass.opaque(glass);
            int toned = OnGlass.resolveBare(surface, LIGHT_PRIMARY,
                OnGlass.TARGET_LARGE_TEXT * 1.04d).ink;
            for (float presence = 0f; presence <= 1.001f; presence += .05f) {
                for (boolean stopped : new boolean[] {false, true}) {
                    float ink = StatusBarLensMetrics.glyphInkFor(presence, stopped);
                    int alpha = Math.round(StatusBarLensMetrics.GLYPH_ALPHA * ink);
                    int glyph = StatusBarInk.inkAtAlpha(surface, toned, alpha,
                        OnGlass.TARGET_LARGE_TEXT);
                    double ratio = StatusBarInk.shownRatio(glyph, surface);
                    assertTrue("the place mark at " + hex(glass) + ", presence " + presence
                            + ", ink " + ink + " (was 1.11): " + ratio,
                        ratio >= OnGlass.TARGET_LARGE_TEXT);
                }
            }
        }
    }

    /**
     * Draining is a move in colour, not in lightness. That is what the lens always claimed it was
     * doing — "drained in colour, not in legibility" — and now it is true: the fully drained mark
     * has the same luminance as the mark it came from, so it cannot fall through a floor the
     * undrained one cleared.
     */
    @Test
    public void drainingTakesTheColourAndLeavesTheLuminance() {
        for (int seed : new int[] {LIGHT_PRIMARY, 0xFFB8C7FF, 0xFF7A5230, 0xFF000000, 0xFFFFFFFF}) {
            int fully = StatusBarInk.drain(seed, 1f);
            assertEquals("a fully drained colour is a grey", Color.red(fully), Color.green(fully));
            assertEquals(Color.green(fully), Color.blue(fully));
            assertEquals("and it is the grey of the same lightness",
                relativeLuminance(seed), relativeLuminance(fully), .006d);
            assertEquals("nothing is drained at 0", seed, StatusBarInk.drain(seed, 0f));
            assertEquals("the alpha survives", Color.alpha(seed), Color.alpha(fully));
        }
    }

    // ------------------------------------------------------------------ the arithmetic itself

    /** {@link StatusBarInk#inkAtAlpha} returns the smallest alpha that works, never a larger one. */
    @Test
    public void inkAtAlphaSpendsTheLeastAlphaThatClearsTheTarget() {
        int surface = OnGlass.opaque(STATUS_GLASS);
        int resolved = StatusBarInk.inkAtAlpha(surface, LIGHT_PRIMARY, 60,
            OnGlass.TARGET_LARGE_TEXT);
        assertTrue(Color.alpha(resolved) >= 60);
        assertTrue("it clears the target as drawn",
            StatusBarInk.shownRatio(resolved, surface) >= OnGlass.TARGET_LARGE_TEXT);
        if (Color.alpha(resolved) > 60) {
            int oneLess = OnGlass.withAlpha(resolved, Color.alpha(resolved) - 1);
            assertTrue("and one step less would not have",
                StatusBarInk.shownRatio(oneLess, surface) < OnGlass.TARGET_LARGE_TEXT);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void assertClears(String what, @ColorInt int glass, @ColorInt int seed,
                                     double target) {
        int surface = OnGlass.opaque(glass);
        int ink = OnGlass.resolveBare(surface, seed, target).ink;
        double ratio = SchemeTone.contrastRatio(ink, surface);
        assertTrue(what + " on " + hex(glass) + ": " + ratio + " < " + target, ratio >= target);
    }

    private static boolean isLighter(@ColorInt int first, @ColorInt int second) {
        return relativeLuminance(first) > relativeLuminance(second);
    }

    private static double relativeLuminance(@ColorInt int color) {
        return 0.2126d * linear(Color.red(color) / 255d)
            + 0.7152d * linear(Color.green(color) / 255d)
            + 0.0722d * linear(Color.blue(color) / 255d);
    }

    private static double linear(double channel) {
        return channel <= 0.04045d ? channel / 12.92d
            : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    private static String hex(@ColorInt int color) {
        return String.format("#%08X", color);
    }
}
