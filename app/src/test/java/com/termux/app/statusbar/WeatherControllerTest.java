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
    public void wmoPrecipitationCodes_useMaterialWeatherFamilies() {
        assertEquals(R.drawable.ic_weather_drizzle, WeatherController.iconFor(53, true));
        assertEquals(R.drawable.ic_weather_rain, WeatherController.iconFor(63, false));
        assertEquals(R.drawable.ic_weather_snow, WeatherController.iconFor(75, true));
        assertEquals(R.drawable.ic_weather_thunderstorm, WeatherController.iconFor(95, false));
    }
}
