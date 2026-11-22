package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class WeatherIconsTest {
    private static final int[] WMO_CODES = {
        0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77,
        80, 81, 82, 85, 86, 95, 96, 99};

    /** code, day name, night name: the accepted table, by the drawables' file names. */
    private static final Object[][] TABLE = {
        {0, "clear_day", "clear_night"},
        {1, "mainly_clear_day", "mainly_clear_night"},
        {2, "partly_cloudy_day", "partly_cloudy_night"},
        {3, "overcast", "overcast"},
        {45, "fog_day", "fog_night"},
        {48, "overcast_rime_fog", "overcast_rime_fog"},
        {51, "partly_cloudy_day_drizzle", "partly_cloudy_night_drizzle"},
        {53, "partly_cloudy_day_drizzle", "partly_cloudy_night_drizzle"},
        {55, "overcast_day_drizzle", "overcast_night_drizzle"},
        {56, "partly_cloudy_day_sleet", "partly_cloudy_night_sleet"},
        {57, "partly_cloudy_day_sleet", "partly_cloudy_night_sleet"},
        {61, "overcast_day_rain", "overcast_night_rain"},
        {63, "overcast_day_rain", "overcast_night_rain"},
        {65, "overcast_day_rain_heavy", "overcast_night_rain_heavy"},
        {66, "overcast_day_sleet", "overcast_night_sleet"},
        {67, "overcast_day_sleet", "overcast_night_sleet"},
        {71, "overcast_day_snow", "overcast_night_snow"},
        {73, "overcast_day_snow", "overcast_night_snow"},
        {75, "overcast_day_snow_heavy", "overcast_night_snow_heavy"},
        {77, "snowflake", "snowflake"},
        {80, "partly_cloudy_day_rain", "partly_cloudy_night_rain"},
        {81, "partly_cloudy_day_rain", "partly_cloudy_night_rain"},
        {82, "partly_cloudy_day_rain_heavy", "partly_cloudy_night_rain_heavy"},
        {85, "partly_cloudy_day_snow", "partly_cloudy_night_snow"},
        {86, "partly_cloudy_day_snow_heavy", "partly_cloudy_night_snow_heavy"},
        {95, "thunderstorms_day", "thunderstorms_night"},
        {96, "thunderstorms_day_hail", "thunderstorms_night_hail"},
        {99, "thunderstorms_day_hail", "thunderstorms_night_hail"},
    };

    private static Path drawable(String file) {
        Path path = Path.of("app/src/main/res/drawable", file);
        return Files.exists(path) ? path : Path.of("src/main/res/drawable", file);
    }

    @Test
    public void everyWmoCodeMapsToTheAcceptedPictureByDayAndNight() {
        for (Object[] row : TABLE) {
            int code = (Integer) row[0];
            assertEquals("day " + code, row[1], WeatherIcons.forCode(code, true).name);
            assertEquals("night " + code, row[2], WeatherIcons.forCode(code, false).name);
        }
        for (int code : WMO_CODES) {
            boolean covered = false;
            for (Object[] row : TABLE) covered |= (Integer) row[0] == code;
            assertTrue("code " + code + " unpinned", covered);
        }
    }

    @Test
    public void everyIconHasAShippedLayerAndEachNamedLayerShips() {
        for (int code : WMO_CODES) {
            for (boolean isDay : new boolean[] {true, false}) {
                WeatherIcons.Icon icon = WeatherIcons.forCode(code, isDay);
                assertTrue("code " + code + " has no layer", icon.base != 0 || icon.accent != 0);
                if (icon.base != 0) {
                    assertTrue(icon.name, Files.exists(drawable("ic_rl_wx_" + icon.name + "_base.xml")));
                }
                if (icon.accent != 0) {
                    assertTrue(icon.name,
                        Files.exists(drawable("ic_rl_wx_" + icon.name + "_accent.xml")));
                }
            }
        }
    }

    @Test
    public void anUnknownCodeIsNotAvailableRatherThanCloudy() {
        assertSame(WeatherIcons.NOT_AVAILABLE, WeatherIcons.forCode(-1, true));
        assertSame(WeatherIcons.NOT_AVAILABLE, WeatherIcons.forCode(1234, false));
        assertEquals("not_available", WeatherIcons.NOT_AVAILABLE.name);
        assertNotEquals(WeatherIcons.OVERCAST.name, WeatherIcons.NOT_AVAILABLE.name);
    }

    @Test
    public void aPictureWithoutASunOrMoonHasNoAccentAndClearSkiesHaveNoBase() {
        assertEquals(0, WeatherIcons.OVERCAST.accent);
        assertEquals(0, WeatherIcons.WIND.accent);
        assertEquals(0, WeatherIcons.CLEAR_DAY.base);
        assertNotEquals(0, WeatherIcons.CLEAR_DAY.accent);
        assertNotEquals(0, WeatherIcons.THUNDERSTORMS_DAY.accent);
    }

    @Test
    public void aKeyFindsItsIconAndNothingElseDoes() {
        assertSame(WeatherIcons.WIND, WeatherIcons.forKey(WeatherIcons.WIND.key));
        assertNotNull(WeatherIcons.forKey(WeatherIcons.forCode(61, false).key));
        assertNull(WeatherIcons.forKey(""));
        assertNull(WeatherIcons.forKey(WeatherIcons.KEY_PREFIX + "nope"));
    }
}
