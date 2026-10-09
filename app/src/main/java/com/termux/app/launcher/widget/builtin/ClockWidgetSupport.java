package com.termux.app.launcher.widget.builtin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** What the analog and digital clocks share: the clock they read, dates, the tap, the second. */
final class ClockWidgetSupport {
    /** The design's sample moment, which the picker card shows: 13:40 on Tue 6 Oct in Kuwait. */
    static final ZonedDateTime SAMPLE_NOW =
        ZonedDateTime.of(2026, 10, 6, 13, 40, 12, 0, ZoneId.of("Asia/Kuwait"));
    static final String SAMPLE_SUNRISE = "05:38";
    static final String SAMPLE_SUNSET = "17:22";

    /** The Nerd glyphs the clocks use: a clock face, the sun, the moon. */
    static final String GLYPH_CLOCK = "";
    static final String GLYPH_SUN = "";
    static final String GLYPH_MOON = "";

    private ClockWidgetSupport() { }

    /** Now in the phone's zone, or the sample moment for a picker card. */
    @NonNull static ZonedDateTime now(boolean preview) {
        return preview ? SAMPLE_NOW : ZonedDateTime.now(ZoneId.systemDefault());
    }

    /**
     * {@code now} as the user's locale writes {@code skeleton}: "EEEdMMM" is "Tue 6 Oct" in
     * Britain and "Tue, Oct 6" in the States.
     */
    @NonNull static String date(@NonNull String skeleton, @NonNull ZonedDateTime now) {
        Locale locale = Locale.getDefault();
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton);
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setTimeZone(TimeZone.getTimeZone(now.getZone()));
        return format.format(new Date(now.toInstant().toEpochMilli()));
    }

    /** The clock app's alarms screen, or the date and time settings where there is no clock app. */
    static void openClockApp(@NonNull Context context) {
        Intent alarms = new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(alarms);
            return;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Fall through to the system's own date and time page.
        }
        try {
            context.startActivity(new Intent(Settings.ACTION_DATE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Nothing to open; the tap does nothing rather than fail.
        }
    }

    /** {@code view} spans the width of its column. */
    @NonNull static <V extends android.view.View> V wide(@NonNull V view) {
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /**
     * Calls {@code onSecond} just after each whole second while running. Run it only while the
     * widget is started and on screen: it is the one repaint a clock makes between minutes.
     */
    static final class SecondTicker {
        @NonNull private final Handler handler;
        @NonNull private final Runnable onSecond;
        private boolean running;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!running) return;
                onSecond.run();
                schedule();
            }
        };

        SecondTicker(@NonNull Handler handler, @NonNull Runnable onSecond) {
            this.handler = handler;
            this.onSecond = onSecond;
        }

        boolean isRunning() { return running; }

        void setRunning(boolean value) {
            if (value == running) return;
            running = value;
            handler.removeCallbacks(tick);
            if (value) schedule();
        }

        private void schedule() {
            long millis = System.currentTimeMillis() % 1000L;
            handler.postDelayed(tick, 1000L - millis + 2L);
        }
    }
}
