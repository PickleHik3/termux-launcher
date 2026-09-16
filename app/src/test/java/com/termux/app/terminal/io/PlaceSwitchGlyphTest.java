package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import com.google.android.material.color.utilities.Hct;
import com.termux.shared.termux.extrakeys.ExtraKeyColorRole;
import com.termux.shared.termux.extrakeys.PlaceSwitchGlyph;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

/**
 * The colour rule behind the three place switches: keep the hue the user's role gave the key, carry
 * as much chroma as that hue can hold, and land on a tone the glass can be read against — then, and
 * only then, turn two switches apart when the palette put them on the same hue.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceSwitchGlyphTest {

    /** Material's own roles are read off a real theme, exactly as a key row resolves them. */
    private final android.content.Context context = new android.view.ContextThemeWrapper(
        RuntimeEnvironment.getApplication(),
        com.google.android.material.R.style.Theme_Material3_DayNight);

    @Test
    public void theVividColourStaysOnTheHueItWasGiven() {
        for (double hue = 0d; hue < 360d; hue += 15d) {
            // HCT drifts a degree or so on its own round trip through sRGB, so the hue to hold on
            // to is the one the source colour actually has, not the one it was asked for.
            int source = Hct.from(hue, 20d, 40d).toInt();
            double asked = Hct.fromInt(source).getHue();
            double kept = Hct.fromInt(PlaceSwitchGlyph.vividFor(source, true)).getHue();
            assertTrue("hue " + asked + " came back as " + kept,
                PlaceSwitchGlyph.hueDistance(asked, kept) <= 2d);
        }
    }

    @Test
    public void everyHueIsCarriedAsFarAsSrgbWillCarryIt() {
        // sRGB cannot hold chroma 80 at a legible tone for most hues, so the rule asks for it and
        // takes the hue's own maximum where it cannot be had. What must never happen is settling
        // for less than that maximum: that is the washed-out look this whole change is about.
        for (double hue = 0d; hue < 360d; hue += 15d) {
            int source = Hct.from(hue, 16d, 40d).toInt();
            double asked = Hct.fromInt(source).getHue();
            double carried = Hct.fromInt(PlaceSwitchGlyph.vividFor(source, true)).getChroma();
            double ceiling = Hct.fromInt(
                Hct.from(asked, 200d, PlaceSwitchGlyph.TONE_ON_DARK_GLASS).toInt()).getChroma();
            assertTrue("hue " + asked + " carried " + carried + " of " + ceiling,
                carried + 1.5d >= Math.min(PlaceSwitchGlyph.VIVID_CHROMA, ceiling));
            assertTrue("and never more than the role asked for",
                carried <= PlaceSwitchGlyph.VIVID_CHROMA + 0.5d);
        }
    }

    @Test
    public void theToneIsSetForTheGlassTheGlyphSitsOn() {
        int source = ExtraKeyColorRole.PRIMARY.background(context);

        double onDark = Hct.fromInt(PlaceSwitchGlyph.vividFor(source, true)).getTone();
        assertEquals(PlaceSwitchGlyph.TONE_ON_DARK_GLASS, onDark, 1d);
        assertTrue("light enough to read off dark glass", onDark >= 70d && onDark <= 85d);

        double onLight = Hct.fromInt(PlaceSwitchGlyph.vividFor(source, false)).getTone();
        assertEquals(PlaceSwitchGlyph.TONE_ON_LIGHT_GLASS, onLight, 1d);
        assertTrue("and dark enough to read off light glass", onLight < 50d);
    }

    @Test
    public void theRowsOwnLabelColourSaysWhichWayTheGlassRuns() {
        assertTrue("a white label means the glass behind it is dark",
            PlaceSwitchGlyph.isDarkGlass(Color.WHITE));
        assertTrue(PlaceSwitchGlyph.isDarkGlass(0xFFE0E0E0));
        assertTrue(!PlaceSwitchGlyph.isDarkGlass(Color.BLACK));
        assertTrue(!PlaceSwitchGlyph.isDarkGlass(0xFF1B1B1F));
    }

    @Test
    public void aColourWithNoHueIsLeftExactlyAsItWasChosen() {
        // The two fixed roles: a user who asked for black asked for black, and there is no hue
        // there to raise without inventing one they never picked.
        assertEquals(Color.BLACK, PlaceSwitchGlyph.vividFor(Color.BLACK, true));
        assertEquals(Color.WHITE, PlaceSwitchGlyph.vividFor(Color.WHITE, true));
        assertEquals(0xFF808080, PlaceSwitchGlyph.vividFor(0xFF808080, false));
    }

    @Test
    public void theThreeDefaultRolesComeOutAsThreeColours() {
        // primary / secondary / tertiary are what the shipped row gives the three switches, and a
        // Material scheme draws all three off one seed: measured on the baseline theme they are
        // hue 299 / 300 / 359, so two of them are the same colour at different chroma. Making them
        // vivid on their own hues would collapse that pair outright; the spread is what stops it.
        int[] glyph = PlaceSwitchGlyph.vividRow(defaultRoles(), true);

        for (int first = 0; first < glyph.length; first++) {
            for (int second = first + 1; second < glyph.length; second++) {
                double apart = PlaceSwitchGlyph.hueDistance(
                    Hct.fromInt(glyph[first]).getHue(), Hct.fromInt(glyph[second]).getHue());
                assertTrue("roles " + first + " and " + second + " are only " + apart + "° apart",
                    apart >= PlaceSwitchGlyph.MIN_HUE_SEPARATION - 1d);
            }
        }
        assertEquals("the first switch is never moved off the hue its role gave it",
            Hct.fromInt(defaultRoles()[0]).getHue(), Hct.fromInt(glyph[0]).getHue(), 2d);
    }

    @Test
    public void rolesThatAlreadyHaveHuesOfTheirOwnAreLeftOnThem() {
        int[] wellSpread = {
            Hct.from(30d, 40d, 40d).toInt(),
            Hct.from(150d, 40d, 40d).toInt(),
            Hct.from(270d, 40d, 40d).toInt()
        };
        int[] glyph = PlaceSwitchGlyph.vividRow(wellSpread, true);
        for (int i = 0; i < glyph.length; i++) {
            assertEquals("switch " + i, Hct.fromInt(wellSpread[i]).getHue(),
                Hct.fromInt(glyph[i]).getHue(), 2d);
            assertEquals("and is exactly what the single-colour rule gives it",
                PlaceSwitchGlyph.vividFor(wellSpread[i], true), glyph[i]);
        }
    }

    @Test
    public void aRowWithNowhereLeftToSpreadStillAnswersForEveryKey() {
        // More place switches than the circle has 60° slots for: the rule must run out of room
        // rather than out of patience, and every key must still come back with a colour.
        int[] many = new int[12];
        for (int i = 0; i < many.length; i++)
            many[i] = Hct.from(200d, 40d, 40d).toInt();
        int[] glyph = PlaceSwitchGlyph.vividRow(many, true);
        assertEquals(many.length, glyph.length);
        for (int color : glyph)
            assertEquals("opaque", 255, Color.alpha(color));
    }

    private int[] defaultRoles() {
        return new int[] {
            ExtraKeyColorRole.PRIMARY.background(context),
            ExtraKeyColorRole.SECONDARY.background(context),
            ExtraKeyColorRole.TERTIARY.background(context)
        };
    }
}
