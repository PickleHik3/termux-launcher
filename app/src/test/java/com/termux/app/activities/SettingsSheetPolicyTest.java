package com.termux.app.activities;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** How tall and wide Settings' sheet over Home is: resting, raised, capped. */
public class SettingsSheetPolicyTest {

    @Test
    public void aPhoneInPortraitRestsAtSixtyPercent() {
        // 2400 px at 2.75: 873 dp tall, a 24 dp status bar.
        assertEquals(1440, SettingsSheetPolicy.restingHeight(2400, 66, 2.75f));
    }

    @Test
    public void aShortWindowRestsNoLowerThanTheFloor() {
        // 640 dp tall: 60% is 384 dp, under the 400 dp floor.
        assertEquals(800, SettingsSheetPolicy.restingHeight(1280, 48, 2f));
    }

    @Test
    public void aPhoneOnItsSideRestsAtFullHeight() {
        // 411 dp tall: the floor is past the window below the status bar, so it opens full.
        int full = SettingsSheetPolicy.fullHeight(1080, 63);
        assertEquals(1017, full);
        assertEquals(full, SettingsSheetPolicy.restingHeight(1080, 63, 2.625f));
    }

    @Test
    public void raisedItKeepsFullHeight() {
        assertEquals(SettingsSheetPolicy.fullHeight(2400, 66),
            SettingsSheetPolicy.settledHeight(2400, 66, 2.75f, true));
        assertEquals(SettingsSheetPolicy.restingHeight(2400, 66, 2.75f),
            SettingsSheetPolicy.settledHeight(2400, 66, 2.75f, false));
    }

    @Test
    public void fullHeightIsTheWindowBelowTheStatusBar() {
        assertEquals(2334, SettingsSheetPolicy.fullHeight(2400, 66));
        assertEquals(2400, SettingsSheetPolicy.fullHeight(2400, 0));
        assertEquals("a status bar taller than the window leaves nothing", 0,
            SettingsSheetPolicy.fullHeight(40, 66));
    }

    @Test
    public void aPhoneSheetSpansTheWidth() {
        assertEquals(1080, SettingsSheetPolicy.maxWidth(1080, 2.75f, false));
    }

    @Test
    public void aWideWindowCapsTheSheet() {
        // A tablet 1368 dp wide at 2.0.
        assertEquals(1280, SettingsSheetPolicy.maxWidth(2736, 2f, false));
        assertEquals("the list beside the page gets more room", 1680,
            SettingsSheetPolicy.maxWidth(2736, 2f, true));
        assertEquals("never wider than the window", 1500,
            SettingsSheetPolicy.maxWidth(1500, 2f, true));
    }
}
