package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.termux.app.statusbar.WeatherController;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class WeatherWidgetModelTest {
    private static final List<String> HOURS = Arrays.asList(
        "2026-10-06T12:00", "2026-10-06T13:00", "2026-10-06T14:00", "2026-10-06T15:00");

    private static Calendar at(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.OCTOBER, 6, hour, minute, 0);
        return now;
    }

    @Test public void theNextSunTimeIsTheSunriseBeforeDawnTheSunsetByDay() {
        assertEquals(WeatherWidgetModel.Sun.RISE,
            WeatherWidgetModel.nextSun("06:58", "19:42", "06:59", at(5, 0)));
        assertEquals(WeatherWidgetModel.Sun.SET,
            WeatherWidgetModel.nextSun("06:58", "19:42", "06:59", at(13, 40)));
    }

    @Test public void afterSunsetTheNextSunTimeIsTomorrowsSunrise() {
        assertEquals(WeatherWidgetModel.Sun.RISE,
            WeatherWidgetModel.nextSun("06:58", "19:42", "06:59", at(21, 0)));
        // Without tomorrow's pair there is nothing to say after dark.
        assertEquals(WeatherWidgetModel.Sun.NONE,
            WeatherWidgetModel.nextSun("06:58", "19:42", "", at(21, 0)));
        assertEquals(WeatherWidgetModel.Sun.NONE,
            WeatherWidgetModel.nextSun("06:58", "19:42", at(21, 0)));
    }

    @Test public void dataIsStaleOnceOlderThanTheCacheLifetime() {
        long now = 10_000_000_000L;
        long ttl = WeatherController.CACHE_TTL_MS;
        assertEquals(-1, WeatherWidgetModel.staleMinutes(now - ttl + 1, now));
        assertEquals((int) (ttl / 60000L), WeatherWidgetModel.staleMinutes(now - ttl, now));
        assertEquals(40, WeatherWidgetModel.staleMinutes(now - 40 * 60000L - 5000L, now));
        assertEquals(-1, WeatherWidgetModel.staleMinutes(0L, now));
    }

    @Test public void currentHourIsTheProvidersKeyForThisHour() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.OCTOBER, 6, 13, 40, 12);
        assertEquals("2026-10-06T13:00", WeatherWidgetModel.currentHourIso(now));
    }

    @Test public void theStripStartsWithTheHourAfterThisOne() {
        // 13:40 shows 14, 15, ... as the design does.
        assertEquals(2, WeatherWidgetModel.firstUpcoming(HOURS, "2026-10-06T13:00"));
        assertEquals(0, WeatherWidgetModel.firstUpcoming(HOURS, "2026-10-06T11:00"));
    }

    @Test public void aForecastWhollyInThePastShowsItsHeadAndAnEmptyOneNothing() {
        assertEquals(0, WeatherWidgetModel.firstUpcoming(HOURS, "2026-10-07T09:00"));
        assertEquals(-1, WeatherWidgetModel.firstUpcoming(Collections.emptyList(), "2026-10-06T13:00"));
    }

    @Test public void hourLabelIsTheHourOfTheIsoTime() {
        assertEquals("14", WeatherWidgetModel.hourLabel("2026-10-06T14:00"));
        assertEquals("garbage", WeatherWidgetModel.hourLabel("garbage"));
    }

    @Test public void rangePlacesADayOnTheSharedScale() {
        float[] range = WeatherWidgetModel.range(24, 36, 20, 40);
        assertEquals(0.2f, range[0], 0.0001f);
        assertEquals(0.8f, range[1], 0.0001f);
        float[] coldest = WeatherWidgetModel.range(22, 33, 22, 36);
        assertEquals(0f, coldest[0], 0.0001f);
        assertEquals(11f / 14f, coldest[1], 0.0001f);
    }

    @Test public void aFlatScaleDrawsTheBarWhole() {
        float[] range = WeatherWidgetModel.range(30, 30, 30, 30);
        assertEquals(0f, range[0], 0.0001f);
        assertEquals(1f, range[1], 0.0001f);
    }

    @Test public void shortPlaceKeepsTheCityOnly() {
        assertEquals("Kuwait City", WeatherWidgetModel.shortPlace("Kuwait City, Al Asimah"));
        assertEquals("Kochi", WeatherWidgetModel.shortPlace("Kochi"));
        assertEquals("", WeatherWidgetModel.shortPlace(null));
    }

    @Test public void clearSkiesAreSunByDayAndMoonByNightEverythingElseIsCloud() {
        assertEquals(WeatherWidgetModel.Tone.SUN, WeatherWidgetModel.tone(0, true));
        assertEquals(WeatherWidgetModel.Tone.MOON, WeatherWidgetModel.tone(1, false));
        assertEquals(WeatherWidgetModel.Tone.CLOUD, WeatherWidgetModel.tone(3, true));
        assertEquals(WeatherWidgetModel.Tone.CLOUD, WeatherWidgetModel.tone(61, false));
    }

    @Test public void nightSkiesGetANightGlyphAndTheRestTheSharedTable() {
        assertEquals(WeatherController.glyphFor(0), WeatherWidgetModel.glyph(0, true));
        assertEquals(WeatherWidgetModel.GLYPH_NIGHT_CLEAR, WeatherWidgetModel.glyph(0, false));
        assertEquals(WeatherWidgetModel.GLYPH_NIGHT_PARTLY_CLOUDY, WeatherWidgetModel.glyph(2, false));
        assertEquals(WeatherController.glyphFor(61), WeatherWidgetModel.glyph(61, false));
        assertNotEquals(WeatherWidgetModel.glyph(0, true), WeatherWidgetModel.glyph(0, false));
    }

    @Test public void theSampleIsTheDesignsCard() {
        WeatherWidgetModel sample = WeatherWidgetModel.sample();
        assertEquals("34°", sample.temp);
        assertEquals(5, sample.hours.size());
        assertEquals("14", sample.hours.get(0).label);
        assertEquals(5, sample.days.size());
        assertEquals(WeatherWidgetModel.Tone.MOON, sample.hours.get(4).tone);
    }
}
