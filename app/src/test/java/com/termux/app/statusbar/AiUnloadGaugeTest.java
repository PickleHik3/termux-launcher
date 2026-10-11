package com.termux.app.statusbar;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The robot as a gauge of the idle window left before the model unloads. */
public class AiUnloadGaugeTest {

    private static final long MINUTE = 60_000L;

    @Test public void theFractionIsWhatIsLeftOfTheWindow() {
        assertEquals(1f, AiUnloadGauge.fraction(0L, 10 * MINUTE, 10 * MINUTE), 0f);
        assertEquals(0.25f, AiUnloadGauge.fraction(0L, 10 * MINUTE, 40 * MINUTE), 1e-6f);
        assertEquals(0f, AiUnloadGauge.fraction(11 * MINUTE, 10 * MINUTE, 10 * MINUTE), 0f);
        assertEquals("never more than full",
            1f, AiUnloadGauge.fraction(0L, 20 * MINUTE, 10 * MINUTE), 0f);
    }

    @Test public void aDeadlineSeenToMoveStartsAFullWindowThere() {
        AiUnloadGauge gauge = new AiUnloadGauge();
        gauge.track(10 * MINUTE, 0L, 10 * MINUTE);
        assertEquals(0.5f, gauge.fraction(5 * MINUTE), 1e-6f);
        // Used again at 5 minutes with a 3 minute window this time (the model's own options):
        // full again, and drains over those 3 minutes.
        gauge.track(8 * MINUTE, 5 * MINUTE, 10 * MINUTE);
        assertEquals(1f, gauge.fraction(5 * MINUTE), 0f);
        assertEquals(0.5f, gauge.fraction(6 * MINUTE + 30_000L), 1e-6f);
    }

    @Test public void theSameDeadlineAgainKeepsDraining() {
        AiUnloadGauge gauge = new AiUnloadGauge();
        gauge.track(10 * MINUTE, 0L, 10 * MINUTE);
        gauge.track(10 * MINUTE, 6 * MINUTE, 10 * MINUTE);
        assertEquals(0.4f, gauge.fraction(6 * MINUTE), 1e-6f);
    }

    @Test public void aDeadlineFirstSeenMidCountdownIsMeasuredAgainstTheSetting() {
        AiUnloadGauge gauge = new AiUnloadGauge();
        // The launcher came up 6 minutes into a 10 minute window.
        gauge.track(10 * MINUTE, 6 * MINUTE, 10 * MINUTE);
        assertEquals(0.4f, gauge.fraction(6 * MINUTE), 1e-6f);
        // A keep-warm further out than the setting starts full rather than overflowing.
        AiUnloadGauge warm = new AiUnloadGauge();
        warm.track(30 * MINUTE, 0L, 10 * MINUTE);
        assertEquals(1f, warm.fraction(0L), 0f);
    }

    @Test public void noIdleTimerMeansNoGaugeUntilThereIsOneAgain() {
        AiUnloadGauge gauge = new AiUnloadGauge();
        gauge.track(10 * MINUTE, 0L, 10 * MINUTE);
        gauge.track(0L, MINUTE, 10 * MINUTE);
        assertTrue(gauge.fraction(MINUTE) < 0f);
        // A reload starts over from the setting.
        gauge.track(12 * MINUTE, 2 * MINUTE, 10 * MINUTE);
        assertEquals(1f, gauge.fraction(2 * MINUTE), 0f);
    }

    @Test public void theFillLineRunsOverTheInkInWholeRows() {
        assertEquals(40, AiUnloadGauge.fillTopRow(0f, 4, 40));
        assertEquals(4, AiUnloadGauge.fillTopRow(1f, 4, 40));
        assertEquals(22, AiUnloadGauge.fillTopRow(0.5f, 4, 40));
        assertEquals(40, AiUnloadGauge.fillTopRow(-1f, 4, 40));
    }

    @Test public void aTenMinuteWindowTickingEverySecondRedrawsOncePerRowNotPerTick() {
        // 15dp at 420dpi is about 39px of icon; the ink is about 30 rows of it.
        int inkTop = Math.round(39 * AiUnloadGauge.ROBOT_INK_TOP);
        int inkBottom = Math.round(39 * AiUnloadGauge.ROBOT_INK_BOTTOM);
        AiUnloadGauge gauge = new AiUnloadGauge();
        gauge.track(10 * MINUTE, 0L, 10 * MINUTE);
        Set<Integer> rows = new HashSet<>();
        int redraws = 0;
        int last = -1;
        for (long now = 0L; now <= 10 * MINUTE; now += 1_000L) {
            int row = AiUnloadGauge.fillTopRow(gauge.fraction(now), inkTop, inkBottom);
            rows.add(row);
            if (row != last) redraws++;
            last = row;
        }
        assertEquals(inkBottom - inkTop + 1, rows.size());
        assertEquals(rows.size(), redraws);
    }
}
