package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.termux.app.statusbar.SystemStatsController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The reading slot picks, remembers and feeds its readings, and runs a source only while it needs it (#428). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ReadingSlotTest {

    private boolean shown = true;
    private ReadingKind remembered = ReadingKind.CPU;
    private boolean samplerWanted;
    private boolean hardware;
    private int weatherAsks;
    private boolean slotShown;
    @Nullable private ReadingValue slotValue;
    private ReadingSlot slot;

    @Before public void setUp() {
        slot = new ReadingSlot(ApplicationProvider.getApplicationContext(), new ReadingSlot.Host() {
            @Override public boolean shown() { return shown; }
            @NonNull @Override public ReadingKind remembered() { return remembered; }
            @Override public void remember(@NonNull ReadingKind kind) { remembered = kind; }
            @Override public boolean fahrenheit() { return false; }
            @Override public void samplerWanted(boolean wanted, boolean hw, boolean card) {
                samplerWanted = wanted;
                hardware = hw;
            }
            @Override public void weatherWanted() { weatherAsks++; }
        }, new ReadingSlot.Sink() {
            @Override public void onSlot(boolean on, @Nullable ReadingValue value) {
                slotShown = on;
                slotValue = value;
            }
            @Override public void onAll(@NonNull ReadingSet set) { }
        });
    }

    private static SystemStatsController.Stats stats(int cpu, long used, long total) {
        SystemStatsController.Stats stats = new SystemStatsController.Stats();
        stats.cpuPercent = cpu;
        stats.memUsedKb = used;
        stats.memTotalKb = total;
        return stats;
    }

    @Test public void aRememberedCpuStartsTheOneSamplerAndShowsItsNumberFromTheFirstSample() {
        slot.refresh();
        assertTrue(samplerWanted);
        assertFalse("no GPU read for a CPU reading", hardware);
        assertEquals("loading, not the next reading", "--", slotValue.full());
        assertEquals(ReadingKind.CPU, slotValue.kind);
        slot.onStats(stats(38, 1, 4), 1000L);
        assertEquals("38%", slotValue.full());
    }

    @Test public void aTapCyclesToTheNextOfferedReadingAndRemembersIt() {
        slot.refresh();
        slot.onStats(stats(38, 1, 4), 1000L);
        slot.next();
        assertEquals("the GPU is loading, so it is the next one", ReadingKind.GPU, remembered);
        slot.onStats(stats(38, 1, 4), 3000L);
        assertEquals("the sampler read no GPU load: memory is next", ReadingKind.RAM, slot.shownKind());
        assertEquals("memory loading until its own sample", "--", slotValue.full());
        slot.onStats(stats(38, 1, 4), 5000L);
        assertEquals("25%", slotValue.full());
    }

    @Test public void aTapFromTheDefaultBatteryOnAFreshSlotReachesTheNextReadingAndStartsItsSource() {
        remembered = ReadingKind.BATTERY;
        slot.refresh();
        assertFalse(samplerWanted);
        slot.next();
        assertEquals(ReadingKind.WEATHER, remembered);
        assertEquals("loading, with its source asked", "--", slotValue.full());
        assertEquals(ReadingKind.WEATHER, slotValue.kind);
        assertEquals(1, weatherAsks);
        slot.next();
        assertEquals("media has no session known: offered and loading", ReadingKind.MEDIA, remembered);
    }

    @Test public void aTapFromTheDefaultReachesTheSamplerReadingsToo() {
        remembered = ReadingKind.BATTERY;
        slot.refresh();
        // weather answers none, media has no session
        slot.next();
        slot.onWeather(new com.termux.app.statusbar.WeatherController.Weather());
        slot.next();
        slot.onTopPaneFeedChanged();
        assertEquals("with weather and media missing the CPU shows", ReadingKind.CPU, slot.shownKind());
        slot.next();
        assertEquals(ReadingKind.GPU, remembered);
        assertTrue(samplerWanted);
        assertTrue(hardware);
    }

    @Test public void aSourceThatStoppedLeavesNoOldNumberBehind() {
        slot.refresh();
        slot.onStats(stats(38, 1, 4), 1000L);
        assertEquals("38%", slotValue.full());
        slot.setInFront(false);
        slot.setInFront(true);
        assertEquals("loading again, not the old 38%", "--", slotValue.full());
    }

    @Test public void aReadingThatNeedsNoSamplerLetsItStop() {
        slot.refresh();
        assertTrue(samplerWanted);
        remembered = ReadingKind.WEATHER;
        slot.refresh();
        assertFalse(samplerWanted);
        assertEquals("the weather is asked for, once", 1, weatherAsks);
    }

    @Test public void theBatteryNeedsNoSamplerEither() {
        remembered = ReadingKind.BATTERY;
        slot.refresh();
        assertFalse(samplerWanted);
    }

    @Test public void turnedOffNothingRunsAndNothingIsShown() {
        slot.refresh();
        shown = false;
        slot.refresh();
        assertFalse(slotShown);
        assertNull(slotValue);
        assertFalse(samplerWanted);
        assertFalse(slot.wantsSampler());
    }

    @Test public void outOfFrontNothingRunsAndComingBackStartsItAgain() {
        slot.refresh();
        slot.setInFront(false);
        assertFalse(samplerWanted);
        slot.setInFront(true);
        assertTrue(samplerWanted);
    }

    @Test public void theCardWantsEverySourceWhileItIsOpen() {
        remembered = ReadingKind.BATTERY;
        slot.refresh();
        slot.setCardOpen(true);
        assertTrue(samplerWanted);
        assertTrue("the card shows the GPU too", hardware);
        slot.setCardOpen(false);
        assertFalse(samplerWanted);
    }

    @Test public void releasingTheBarStopsEverything() {
        slot.refresh();
        slot.release();
        assertFalse(samplerWanted);
        assertNotNull(slot.readings());
    }
}
