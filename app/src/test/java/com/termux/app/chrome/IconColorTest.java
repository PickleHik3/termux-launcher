package com.termux.app.chrome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/** The icon colour vote, on raw pixels: what wins, and when the fallback answers instead. */
public class IconColorTest {

    private static final int FALLBACK = 0xFF7D5260;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFF808080;
    private static final int GREEN = 0xFF25D366; // quantises to 0xFF22DD66
    private static final int PALE_PINK = 0xFFFFC8DC; // quantises to 0xFFFFCCDD
    private static final int RED = 0xFFE01020;

    private static int[] fill(int total, int... colorsAndCounts) {
        int[] pixels = new int[total];
        Arrays.fill(pixels, 0x00000000);
        int at = 0;
        for (int i = 0; i < colorsAndCounts.length; i += 2) {
            for (int n = 0; n < colorsAndCounts[i + 1]; n++) pixels[at++] = colorsAndCounts[i];
        }
        return pixels;
    }

    @Test
    public void aChromaticGlyphOnAWhitePlateWins() {
        int[] pixels = fill(1600, WHITE, 1200, GREEN, 300);
        assertEquals(0xFF22DD66, IconColor.fromPixels(pixels, FALLBACK, false));
        assertEquals(0xFF22DD66, IconColor.fromPixels(pixels, FALLBACK, true));
    }

    @Test
    public void aGreyOrMonochromeIconFallsBack() {
        assertEquals(FALLBACK, IconColor.fromPixels(fill(1600, GREY, 1600), FALLBACK, true));
        assertEquals(FALLBACK, IconColor.fromPixels(fill(1600, WHITE, 1600), FALLBACK, false));
        assertEquals("fully transparent", FALLBACK,
            IconColor.fromPixels(fill(1600), FALLBACK, true));
        assertEquals("a speck of colour in a grey icon is still grey", FALLBACK,
            IconColor.fromPixels(fill(1600, GREY, 1500, RED, 20), FALLBACK, true));
    }

    @Test
    public void aNearWhiteWinnerGivesWayToTheMostSaturatedBucketForATint() {
        int[] pixels = fill(1600, PALE_PINK, 1000, RED, 200);
        int ripple = IconColor.fromPixels(pixels, FALLBACK, false);
        int tint = IconColor.fromPixels(pixels, FALLBACK, true);
        assertEquals("the launch ripple keeps the heaviest bucket", 0xFFFFCCDD, ripple);
        assertEquals("the card wash takes the saturated secondary", 0xFFEE1122, tint);
    }

    @Test
    public void aPaleIconWithNothingElseKeepsItsPaleColour() {
        int[] pixels = fill(1600, PALE_PINK, 1600);
        assertEquals(0xFFFFCCDD, IconColor.fromPixels(pixels, FALLBACK, true));
    }

    @Test
    public void hsvMatchesTheFrameworkConvention() {
        float[] hsv = new float[3];
        IconColor.hsv(0xFFFF0000, hsv);
        assertEquals(0f, hsv[0], .01f);
        assertEquals(1f, hsv[1], .001f);
        assertEquals(1f, hsv[2], .001f);
        IconColor.hsv(0xFF00FF00, hsv);
        assertEquals(120f, hsv[0], .01f);
        IconColor.hsv(0xFF808080, hsv);
        assertEquals(0f, hsv[1], .001f);
    }
}
