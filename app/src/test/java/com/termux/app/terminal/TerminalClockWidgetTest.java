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
    public void style_acceptsFourVariantsAndFallsBackToFlip() {
        TerminalClockWidget widget = new TerminalClockWidget(
            ApplicationProvider.getApplicationContext(), null);

        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD);
        assertEquals("lcd", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL);
        assertEquals("minimal", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED);
        assertEquals("led", widget.getStyle());
        widget.setStyle("unknown");
        assertEquals("flip", widget.getStyle());
    }
}
