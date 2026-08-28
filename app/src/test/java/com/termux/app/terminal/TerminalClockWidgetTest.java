package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalClockWidgetTest {

    @Test
    public void snapshot_usesPadded24HourTimeAndSpecifiedDateFormat() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(utc);
        calendar.clear();
        calendar.set(2026, Calendar.JULY, 22, 4, 5, 6);

        TerminalClockWidget.ClockSnapshot snapshot =
            TerminalClockWidget.snapshot(calendar.getTimeInMillis(), utc);

        assertEquals("04", snapshot.hh);
        assertEquals("05", snapshot.mm);
        assertEquals("06", snapshot.ss);
        assertEquals("WED 22 JUL", snapshot.date);
        assertEquals("", snapshot.period);
    }

    @Test
    public void snapshot_amPmMode_usesTwelveHourTimeAndPeriod() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(utc);
        calendar.clear();
        calendar.set(2026, Calendar.JULY, 22, 16, 5, 6);

        TerminalClockWidget.ClockSnapshot snapshot =
            TerminalClockWidget.snapshot(calendar.getTimeInMillis(), utc, true);

        assertEquals("04", snapshot.hh);
        assertEquals("PM", snapshot.period);
    }

    @Test
    public void style_acceptsSixVariantsAndFallsBackToFlip() {
        TerminalClockWidget widget = new TerminalClockWidget(
            ApplicationProvider.getApplicationContext(), null);

        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD);
        assertEquals("lcd", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL);
        assertEquals("minimal", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED);
        assertEquals("led", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE);
        assertEquals("tape", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB);
        assertEquals("slab", widget.getStyle());
        widget.setStyle("unknown");
        assertEquals("flip", widget.getStyle());
    }

    @Test
    public void alignment_acceptsThreeValuesAndFallsBackToLeft() {
        TerminalClockWidget widget = new TerminalClockWidget(
            ApplicationProvider.getApplicationContext(), null);

        assertEquals("left", widget.getAlignment());
        widget.setAlignment(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER);
        assertEquals("center", widget.getAlignment());
        widget.setAlignment(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT);
        assertEquals("right", widget.getAlignment());
        widget.setAlignment("unknown");
        assertEquals("left", widget.getAlignment());
        widget.setAlignment(null);
        assertEquals("left", widget.getAlignment());
    }

    @Test
    public void lazyMode_reportsEveryDigitSettledOnTheTickThatChangedIt() {
        TerminalClockWidget widget = new TerminalClockWidget(
            ApplicationProvider.getApplicationContext(), null);
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD);
        long wall = 1_700_000_000_000L;
        widget.updateTime(wall, 1_000L);
        widget.updateTime(wall + 1_000L, 2_000L);

        // Animating: the seconds' ones digit has just changed, so it is at the start of its fold.
        assertEquals(0f, widget.secondsProgress(1, 2_000L, 340L), 0f);

        widget.setLazyMode(true);
        widget.updateTime(wall + 2_000L, 3_000L);

        // Lazy: the same tick must read as settled, or the single frame it paints is the digit
        // dropped 4dp and faded out until the next second.
        assertEquals(1f, widget.secondsProgress(1, 3_000L, 340L), 0f);
        assertEquals(1f, widget.progress(0, 3_000L, 340L), 0f);

        widget.setLazyMode(false);
        widget.updateTime(wall + 3_000L, 4_000L);
        assertEquals(0f, widget.secondsProgress(1, 4_000L, 340L), 0f);
    }
}
