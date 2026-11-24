package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.GlassMaterial;

import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Vibrancy and the Tint's hue shift are the program's inputs (#321): the look carries them, the
 * program applies them to the backdrop it samples, and the result is the colour the stored frame
 * used to be baked to, whichever route (stored frame or live picture) handed it the backdrop.
 */
public class GlassColourShaderTest {

    /** A frame tint in a warm hue at a third of the way, as the Tint amount bakes one. */
    private static final int TINT = GlassFrameTint.of(60, 10, 0xFF4F378B, 0xFFD0BCFF);
    private static final int[] VIBRANCY = {70, 100, 130, 150};
    private static final int[][] WALLPAPER = {
        {255, 255, 255}, {0, 0, 0}, {128, 128, 128}, {200, 60, 30}, {30, 90, 210}, {250, 240, 20},
    };

    private static GlassRefraction.Look look() {
        return GlassRefraction.Look.DEFAULT.withShine(GlassMaterial.SHINE_DEFAULT);
    }

    @Test
    public void theTintUnderTestIsReal() {
        assertNotEquals(GlassFrameTint.NONE, TINT);
    }

    @Test
    public void theProgramDeclaresAndAppliesTheColour() {
        String agsl = GlassRefraction.AGSL;
        for (String name : new String[] {"uColourOn", "uColourR", "uColourG", "uColourB"}) {
            assertTrue(name + " is declared",
                Pattern.compile("uniform \\w+ " + name + ";").matcher(agsl).find());
        }
        // Every backdrop sample takes it: the bent and sharp picture, the concave lens and the
        // reflected tap.
        assertTrue(agsl.contains("    col = glassColour(col);\n"));
        assertTrue(agsl.contains("return glassColour(content.eval(framePx(fragCoord + cn * concavePull("));
        assertTrue(agsl.contains("half4 envS = glassColour(content.eval(framePx(envAt)));"));
        // Off, it reads nothing and hands the sample back.
        assertTrue(agsl.contains("if (uColourOn <= 0.0) { return c; }"));
    }

    @Test
    public void aLookWithoutColourLeavesTheBackdropAlone() {
        GlassRefraction.Look plain = look();
        assertFalse(plain.coloursBackdrop());
        assertNull(plain.colourMatrix());
        assertSame("no colour is the same look", plain, plain.withColour(100, GlassFrameTint.NONE));
        assertFalse(GlassRefraction.colourApplies(plain, true));
    }

    @Test
    public void theColourIsPartOfTheLookAndSurvivesItsOtherChanges() {
        GlassRefraction.Look coloured = look().withColour(130, TINT);
        assertTrue(coloured.coloursBackdrop());
        assertNotEquals("a colour change re-dresses every glass", look(), coloured);
        assertNotEquals(look().hashCode(), coloured.hashCode());
        assertEquals(coloured, look().withColour(130, TINT));
        GlassRefraction.Look concave = coloured.withConcave(true);
        assertEquals(130, concave.vibrancyPercent);
        assertEquals(TINT, concave.frameTint);
        GlassRefraction.Look faced = coloured.withFaceTerms(20, 10, 30);
        assertEquals(TINT, faced.frameTint);
        GlassRefraction.Look shone = coloured.withShine(GlassMaterial.SHINE_DEFAULT + 10);
        assertEquals(130, shone.vibrancyPercent);
        assertEquals("held to the range", 150, look().withColour(400, TINT).vibrancyPercent);
    }

    @Test
    public void onlyABackdropTakesTheColour() {
        GlassRefraction.Look coloured = look().withColour(130, TINT);
        assertTrue("a bound frame or a live picture", GlassRefraction.colourApplies(coloured, true));
        assertFalse("a view's own icons and text", GlassRefraction.colourApplies(coloured, false));
        assertFalse(GlassRefraction.colourApplies(null, true));
    }

    @Test
    public void theShadersColourIsTheBakeTheStoredFrameUsedToCarry() {
        float[] out = new float[3];
        for (int vibrancy : VIBRANCY) {
            for (int tint : new int[] {GlassFrameTint.NONE, TINT}) {
                float[] matrix = look().withColour(vibrancy, tint).colourMatrix();
                float[] bake = GlassVibrancy.bakeMatrix(vibrancy, tint);
                for (int[] rgb : WALLPAPER) {
                    float[] baked = LiveStoredParity.apply(bake, rgb[0], rgb[1], rgb[2]);
                    GlassRefraction.colourOf(matrix, rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f,
                        1f, out);
                    String at = "vibrancy " + vibrancy + " tint " + Integer.toHexString(tint);
                    for (int c = 0; c < 3; c++) {
                        float want = Math.max(0f, Math.min(255f, baked[c]));
                        assertEquals(at, want, out[c] * 255f, 0.01f);
                    }
                }
            }
        }
    }

    @Test
    public void theInkCheckMeasuresTheColourTheProgramShows() {
        // A raw frame's pixel through apply() is the pixel the baked frame held: the ink check
        // reads the same colour on either route.
        float[] matrix = GlassVibrancy.bakeMatrix(100, TINT);
        for (int[] rgb : WALLPAPER) {
            int color = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            assertEquals(Integer.toHexString(color), GlassFrameTint.apply(TINT, color),
                GlassVibrancy.apply(matrix, color));
        }
    }

    @Test
    public void premultipliedSamplesStayUnderTheirAlpha() {
        float[] out = new float[3];
        float[] matrix = look().withColour(150, TINT).colourMatrix();
        GlassRefraction.colourOf(matrix, 0.5f, 0.1f, 0.05f, 0.5f, out);
        for (float c : out) assertTrue(c >= 0f && c <= 0.5f);
    }
}
