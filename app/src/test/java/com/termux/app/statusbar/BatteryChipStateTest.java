package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.os.BatteryManager;

import org.junit.Test;

public class BatteryChipStateTest {

    private static final int DISCHARGING = BatteryManager.BATTERY_STATUS_DISCHARGING;
    private static final int CHARGING = BatteryManager.BATTERY_STATUS_CHARGING;
    private static final int FULL = BatteryManager.BATTERY_STATUS_FULL;
    private static final int GOOD = BatteryManager.BATTERY_HEALTH_GOOD;
    private static final int OVERHEAT = BatteryManager.BATTERY_HEALTH_OVERHEAT;
    private static final int AC = BatteryManager.BATTERY_PLUGGED_AC;

    private static BatteryChipState of(int level, int status, int plugged, int health, int temp) {
        return BatteryChipState.of(level, 100, status, plugged, health, temp);
    }

    @Test public void plainLevelShowsPercentAndContinuousFill() {
        BatteryChipState s = of(57, DISCHARGING, 0, GOOD, 300);
        assertEquals("57%", s.text());
        assertEquals(0.57f, s.fill(), 1e-6f);
        assertEquals(BatteryChipState.Kind.PLAIN, s.kind());
    }

    @Test public void fillIsNotSteppedToTens() {
        assertEquals(0.43f, of(43, DISCHARGING, 0, GOOD, 300).fill(), 1e-6f);
        assertEquals(0.44f, of(44, DISCHARGING, 0, GOOD, 300).fill(), 1e-6f);
    }

    @Test public void lowAtTheThresholdAndBelowButNotAbove() {
        assertEquals(BatteryChipState.Kind.LOW, of(15, DISCHARGING, 0, GOOD, 300).kind());
        assertEquals(BatteryChipState.Kind.LOW, of(1, DISCHARGING, 0, GOOD, 300).kind());
        assertEquals(BatteryChipState.Kind.PLAIN, of(16, DISCHARGING, 0, GOOD, 300).kind());
    }

    @Test public void chargingBeatsLow() {
        assertEquals(BatteryChipState.Kind.CHARGING, of(8, CHARGING, AC, GOOD, 300).kind());
        assertEquals(BatteryChipState.Kind.CHARGING, of(60, CHARGING, AC, GOOD, 300).kind());
    }

    @Test public void pluggedButNotChargingBelowFullIsPlain() {
        assertEquals(BatteryChipState.Kind.PLAIN,
            of(80, BatteryManager.BATTERY_STATUS_NOT_CHARGING, AC, GOOD, 300).kind());
    }

    @Test public void fullByStatusOrByLevelWhilePlugged() {
        BatteryChipState byStatus = of(97, FULL, AC, GOOD, 300);
        assertEquals(BatteryChipState.Kind.FULL, byStatus.kind());
        assertEquals(1f, byStatus.fill(), 0f);
        assertEquals(BatteryChipState.Kind.FULL, of(100, CHARGING, AC, GOOD, 300).kind());
    }

    @Test public void hundredOnBatteryIsNotFull() {
        assertEquals(BatteryChipState.Kind.PLAIN, of(100, DISCHARGING, 0, GOOD, 300).kind());
    }

    @Test public void hotByHealthOrByTemperature() {
        assertEquals(BatteryChipState.Kind.HOT, of(50, DISCHARGING, 0, OVERHEAT, 300).kind());
        assertEquals(BatteryChipState.Kind.HOT, of(50, DISCHARGING, 0, GOOD, 450).kind());
        assertEquals(BatteryChipState.Kind.PLAIN, of(50, DISCHARGING, 0, GOOD, 449).kind());
    }

    @Test public void hotBeatsFullChargingAndLow() {
        assertEquals(BatteryChipState.Kind.HOT, of(100, FULL, AC, OVERHEAT, 300).kind());
        assertEquals(BatteryChipState.Kind.HOT, of(50, CHARGING, AC, GOOD, 460).kind());
        assertEquals(BatteryChipState.Kind.HOT, of(5, DISCHARGING, 0, GOOD, 460).kind());
    }

    @Test public void hotKeepsItsRealFill() {
        assertEquals(0.5f, of(50, DISCHARGING, 0, OVERHEAT, 300).fill(), 1e-6f);
    }

    @Test public void aScaleOtherThanOneHundredIsNormalised() {
        BatteryChipState s = BatteryChipState.of(127, 255, DISCHARGING, 0, GOOD, 300);
        assertEquals("50%", s.text());
        assertEquals(127f / 255f, s.fill(), 1e-6f);
        assertEquals("100%", BatteryChipState.of(255, 255, FULL, AC, GOOD, 300).text());
    }

    @Test public void aLevelAboveScaleClamps() {
        BatteryChipState s = BatteryChipState.of(120, 100, DISCHARGING, 0, GOOD, 300);
        assertEquals("100%", s.text());
        assertEquals(1f, s.fill(), 0f);
    }

    @Test public void missingLevelOrScaleIsDashesWithAnEmptyBody() {
        assertSame(BatteryChipState.UNKNOWN,
            BatteryChipState.of(-1, 100, CHARGING, AC, OVERHEAT, 999));
        assertSame(BatteryChipState.UNKNOWN,
            BatteryChipState.of(50, -1, DISCHARGING, 0, GOOD, 300));
        assertSame(BatteryChipState.UNKNOWN,
            BatteryChipState.of(50, 0, DISCHARGING, 0, GOOD, 300));
        assertEquals("--", BatteryChipState.UNKNOWN.text());
        assertEquals(0f, BatteryChipState.UNKNOWN.fill(), 0f);
        assertEquals(BatteryChipState.Kind.PLAIN, BatteryChipState.UNKNOWN.kind());
    }

    @Test public void missingOtherExtrasDoNotCrashOrMarkTheChip() {
        BatteryChipState s = BatteryChipState.of(42, 100, -1, -1, -1, -1);
        assertEquals("42%", s.text());
        assertEquals(BatteryChipState.Kind.PLAIN, s.kind());
    }
}
