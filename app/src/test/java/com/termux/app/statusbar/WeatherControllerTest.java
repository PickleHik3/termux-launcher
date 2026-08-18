package com.termux.app.statusbar;

import com.termux.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class WeatherControllerTest {

    @Test
    public void clearAndPartlyCloudy_followDayNightCycle() {
        assertEquals(R.drawable.ic_weather_clear_day, WeatherController.iconFor(0, true));
        assertEquals(R.drawable.ic_weather_clear_night, WeatherController.iconFor(0, false));
        assertEquals(R.drawable.ic_weather_partly_cloudy_day, WeatherController.iconFor(2, true));
        assertEquals(R.drawable.ic_weather_partly_cloudy_night, WeatherController.iconFor(2, false));
        assertNotEquals(WeatherController.iconFor(0, true), WeatherController.iconFor(0, false));
    }

    @Test
    public void formatTemp_convertsAndRoundsConsistentlyInBothUnits() {
        // Shared by the compact status-bar widget and the detail card — same math, same rounding.
        assertEquals("22°", WeatherController.formatTemp(21.6, false));
        assertEquals("71°", WeatherController.formatTemp(21.6, true));   // 70.88 F rounds up
        assertEquals("0°", WeatherController.formatTemp(0.0, false));
        assertEquals("32°", WeatherController.formatTemp(0.0, true));
        assertEquals("-4°", WeatherController.formatTemp(-3.9, false));
        assertEquals("25°", WeatherController.formatTemp(-3.9, true));   // 24.98 F rounds up
        assertEquals("--°", WeatherController.formatTemp(Double.NaN, false));
        assertEquals("--°", WeatherController.formatTemp(Double.NaN, true));
    }

    @Test
    public void wmoPrecipitationCodes_useMaterialWeatherFamilies() {
        assertEquals(R.drawable.ic_weather_drizzle, WeatherController.iconFor(53, true));
        assertEquals(R.drawable.ic_weather_rain, WeatherController.iconFor(63, false));
        assertEquals(R.drawable.ic_weather_snow, WeatherController.iconFor(75, true));
        assertEquals(R.drawable.ic_weather_thunderstorm, WeatherController.iconFor(95, false));
    }
}
