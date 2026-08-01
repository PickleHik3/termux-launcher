package com.termux.app.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.text.BreakIterator;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Device acceptance tests for retaining Android Canvas as the terminal text shaper. */
@RunWith(AndroidJUnit4.class)
public class CanvasShapingInstrumentationTest {

    private static final String LOG_TAG = "CanvasShapingProbe";
    private static final float EPSILON = 0.01f;

    private static final Fixture[] FIXTURES = {
        new Fixture("arabic-logical-ltr", "سَلَام", false, 4),
        new Fixture("arabic-rtl-run", "سَلَام", true, 4),
        new Fixture("devanagari-conjunct", "क्षि", false, 1),
        new Fixture("combining-marks", "A\u0301\u0327", false, 1),
        new Fixture("zwj-emoji", "👩‍💻", false, 1),
        new Fixture("regional-indicator-flag", "🇸🇦", false, 1),
        new Fixture("nerd-private-use", "\uE0B0\uF101", false, 2),
        new Fixture("programming-ligature-input", "!= -> => ===", false, 12)
    };

    @Test
    public void icuFindsStableExtendedGraphemeBoundaries() {
        for (Fixture fixture : FIXTURES) {
            int[] first = boundaries(fixture.text);
            int[] second = boundaries(fixture.text);
            assertArrayEquals(fixture.name, first, second);
            assertEquals(fixture.name, fixture.expectedClusters, first.length - 1);
            assertEquals(fixture.name, 0, first[0]);
            assertEquals(fixture.name, fixture.text.length(), first[first.length - 1]);
            assertLegalUtf16Boundaries(fixture, first);
        }
    }

    @Test
    public void textRunAdvancesAreDeterministicAndRecoverablePerGrapheme() {
        Paint paint = shapingPaint();
        for (Fixture fixture : FIXTURES) {
            float[] first = advances(paint, fixture);
            float[] second = advances(paint, fixture);
            assertArrayEquals(fixture.name, first, second, EPSILON);

            float total = paint.getTextRunAdvances(fixture.chars, 0, fixture.chars.length,
                0, fixture.chars.length, fixture.rtl, null, 0);
            float recovered = 0f;
            for (float advance : first) {
                assertFalse(fixture.name + " produced NaN", Float.isNaN(advance));
                assertFalse(fixture.name + " produced infinity", Float.isInfinite(advance));
                assertTrue(fixture.name + " produced a negative advance", advance >= 0f);
                recovered += advance;
            }
            assertEquals(fixture.name, total, recovered, EPSILON);

            int[] boundaries = boundaries(fixture.text);
            StringBuilder clusterReport = new StringBuilder();
            float clusterTotal = 0f;
            for (int i = 0; i + 1 < boundaries.length; i++) {
                float clusterAdvance = sum(first, boundaries[i], boundaries[i + 1]);
                assertTrue(fixture.name + " cluster advance must be non-negative",
                    clusterAdvance >= 0f);
                clusterTotal += clusterAdvance;
                if (i > 0) clusterReport.append(',');
                clusterReport.append(clusterAdvance);
            }
            assertEquals(fixture.name, total, clusterTotal, EPSILON);
            Log.i(LOG_TAG, fixture.name + " rtl=" + fixture.rtl + " utf16="
                + fixture.text.length() + " graphemes=" + (boundaries.length - 1)
                + " total=" + total + " clusters=[" + clusterReport + "]");
        }
    }

    @Test
    public void continuationsDoNotOwnIndependentAdvance() {
        Paint paint = shapingPaint();

        Fixture combining = fixture("combining-marks");
        float[] combiningAdvances = advances(paint, combining);
        assertEquals("acute combining mark", 0f, combiningAdvances[1], EPSILON);
        assertEquals("cedilla combining mark", 0f, combiningAdvances[2], EPSILON);

        Fixture emoji = fixture("zwj-emoji");
        float[] emojiAdvances = advances(paint, emoji);
        assertEquals("ZWJ continuation", 0f, emojiAdvances[2], EPSILON);
        assertEquals("first low surrogate", 0f, emojiAdvances[1], EPSILON);
        assertEquals("second low surrogate", 0f, emojiAdvances[4], EPSILON);

        Fixture flag = fixture("regional-indicator-flag");
        float[] flagAdvances = advances(paint, flag);
        assertEquals("first regional-indicator low surrogate", 0f, flagAdvances[1], EPSILON);
        assertEquals("second regional-indicator low surrogate", 0f, flagAdvances[3], EPSILON);
    }

    @Test
    public void ligaturesCanBeDisabledAndRestoredAtACursorBoundary() {
        Paint paint = shapingPaint();
        Fixture arrow = new Fixture("cursor-arrow", "->", false, 2);
        float[] enabled = advances(paint, arrow);

        TerminalFontConfig.Result config = TerminalFontConfig.parse(
            "font_features regular -calt\n", true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        paint.setFontFeatureSettings(config.features(TerminalFontConfig.FontTarget.REGULAR));
        float[] caltDisabled = advances(paint, arrow);
        paint.setFontFeatureSettings("'liga' 0, 'calt' 0");
        float[] disabled = advances(paint, arrow);
        paint.setFontFeatureSettings(null);
        float[] restored = advances(paint, arrow);

        assertEquals("the platform font should expose a real -> ligature for this probe",
            0f, enabled[1], EPSILON);
        assertTrue("Kitty-compatible calt disabling should break the programming ligature",
            caltDisabled[1] > 0f);
        assertTrue("disabling liga/calt should restore an advance to the second character",
            disabled[1] > 0f);
        assertArrayEquals("restoring Paint state must restore shaping", enabled, restored, EPSILON);
        Log.i(LOG_TAG, "cursor-arrow enabled=[" + enabled[0] + ',' + enabled[1]
            + "] calt-disabled=[" + caltDisabled[0] + ',' + caltDisabled[1]
            + "] disabled=[" + disabled[0] + ',' + disabled[1] + "]");
    }

    @Test
    public void variableAxesAreValidatedAgainstTheLoadedPathFace() {
        File variableFont = new File("/system/fonts/RobotoFlex-Regular.ttf");
        assertTrue("Android test device must expose its standard Roboto Flex face",
            variableFont.isFile());
        TerminalFontConfig.Result config = TerminalFontConfig.parse(
            "font_family path=/system/fonts/RobotoFlex-Regular.ttf\n"
                + "font_variations regular wght=825 wdth=90\n", true);
        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);
        assertTrue(faces.errors.toString(), faces.errors.isEmpty());

        Paint paint = shapingPaint();
        paint.setTypeface(faces.regular);
        String settings = config.variations(TerminalFontConfig.FontTarget.REGULAR);
        assertTrue("Roboto Flex must accept its weight and width axes",
            paint.setFontVariationSettings(settings));
        assertEquals(settings, paint.getFontVariationSettings());
        assertTrue(paint.setFontVariationSettings(null));
        assertEquals(null, paint.getFontVariationSettings());
    }

    private static Paint shapingPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(64f);
        return paint;
    }

    private static float[] advances(Paint paint, Fixture fixture) {
        float[] result = new float[fixture.chars.length];
        float total = paint.getTextRunAdvances(fixture.chars, 0, fixture.chars.length,
            0, fixture.chars.length, fixture.rtl, result, 0);
        assertTrue(fixture.name + " must have a visible total advance", total > 0f);
        return result;
    }

    private static int[] boundaries(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        List<Integer> result = new ArrayList<>();
        for (int boundary = iterator.first(); boundary != BreakIterator.DONE;
             boundary = iterator.next()) {
            result.add(boundary);
        }
        int[] array = new int[result.size()];
        for (int i = 0; i < result.size(); i++) array[i] = result.get(i);
        return array;
    }

    private static void assertLegalUtf16Boundaries(Fixture fixture, int[] boundaries) {
        for (int boundary : boundaries) {
            if (boundary > 0 && boundary < fixture.text.length()) {
                assertFalse(fixture.name + " split a surrogate pair at " + boundary,
                    Character.isHighSurrogate(fixture.text.charAt(boundary - 1))
                        && Character.isLowSurrogate(fixture.text.charAt(boundary)));
                int next = fixture.text.codePointAt(boundary);
                assertFalse(fixture.name + " split before a combining mark at " + boundary,
                    isCombining(next));
                assertFalse(fixture.name + " split next to a ZWJ at " + boundary,
                    next == 0x200D || fixture.text.codePointBefore(boundary) == 0x200D);
            }
        }
    }

    private static boolean isCombining(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
            || type == Character.COMBINING_SPACING_MARK
            || type == Character.ENCLOSING_MARK;
    }

    private static float sum(float[] values, int start, int end) {
        float result = 0f;
        for (int i = start; i < end; i++) result += values[i];
        return result;
    }

    private static Fixture fixture(String name) {
        for (Fixture fixture : FIXTURES) if (fixture.name.equals(name)) return fixture;
        throw new IllegalArgumentException("Unknown fixture " + name);
    }

    private static final class Fixture {
        final String name;
        final String text;
        final char[] chars;
        final boolean rtl;
        final int expectedClusters;

        Fixture(String name, String text, boolean rtl, int expectedClusters) {
            this.name = name;
            this.text = text;
            this.chars = text.toCharArray();
            this.rtl = rtl;
            this.expectedClusters = expectedClusters;
        }
    }
}
