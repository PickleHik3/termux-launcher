package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import com.termux.app.chrome.OnGlass;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class MarksLineInkTest {

    private static final int ERROR = 0xFFB3261E;

    @Test public void onADarkBandTheMarksAreLightAndTheCurrentOneReadsAtBodyText() {
        int band = 0xFF101418;
        MarksLineInk ink = MarksLineInk.onBand(band, true, Color.WHITE, ERROR);
        assertTrue(OnGlass.ratio(ink.current, band) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("the others stay readable", OnGlass.ratio(ink.other, band) >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue("attention reads as a graphic", OnGlass.ratio(ink.attention, band) >= OnGlass.TARGET_LARGE_TEXT);
    }

    @Test public void onALightBandTheSameRulesHoldWithDarkInk() {
        int band = 0xFFF2F4F8;
        MarksLineInk ink = MarksLineInk.onBand(band, false, 0xFF1B1B1F, ERROR);
        assertTrue(OnGlass.ratio(ink.current, band) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(OnGlass.ratio(ink.other, band) >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue(OnGlass.ratio(ink.attention, band) >= OnGlass.TARGET_LARGE_TEXT);
    }

    @Test public void theCurrentMarkIsTheStrongestOfThem() {
        int band = 0xFF202830;
        MarksLineInk ink = MarksLineInk.onBand(band, true, Color.WHITE, ERROR);
        assertTrue(OnGlass.ratio(ink.current, band) >= OnGlass.ratio(
            com.termux.app.chrome.GlassInk.effective(ink.other, OnGlass.opaque(band)), band));
    }

    @Test public void onlyTheBurstHasColour() {
        int band = 0xFF101418;
        MarksLineInk ink = MarksLineInk.onBand(band, true, Color.WHITE, ERROR);
        assertEquals("the current mark is the neutral ink, not an accent",
            Color.red(ink.current), Color.blue(ink.current), 8);
        assertTrue(Color.red(ink.attention) > Color.blue(ink.attention));
    }

    @Test public void authoredInksAreTheRolesThemselves() {
        MarksLineInk ink = MarksLineInk.authored(Color.WHITE, ERROR);
        assertEquals(Color.WHITE, ink.current);
        assertEquals(Color.WHITE, ink.other);
        assertEquals(ERROR, ink.attention);
    }
}
