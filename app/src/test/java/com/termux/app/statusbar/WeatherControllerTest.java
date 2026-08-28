package com.termux.app.statusbar;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WeatherControllerTest {

    /** Every code Open-Meteo can report, so no branch of the table is left unasserted. */
    private static final int[] WMO_CODES = {
        0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77,
        80, 81, 82, 85, 86, 95, 96, 99};

    /** The bundled Meteocons animation the mapping names, on disk. */
    private static Path assetPath(int code, boolean isDay) {
        String asset = WeatherController.animationAssetFor(code, isDay);
        Path path = Path.of("app/src/main/assets", asset);
        return Files.exists(path) ? path : Path.of("src/main/assets", asset);
    }

    /**
     * The mapping names assets by hand, so a typo or a trimmed icon set would only surface as an
     * empty box on a rainy night. Every code is checked against the shipped files instead.
     */
    @Test
    public void everyWmoCodeMapsToAnAnimationThatShips() {
        for (int code : WMO_CODES) {
            for (boolean isDay : new boolean[] {true, false}) {
                Path path = assetPath(code, isDay);
                assertTrue("code " + code + " day=" + isDay + " -> " + path, Files.exists(path));
            }
        }
        // An unknown code has to resolve to a shipped icon too, not just to a name.
        assertTrue(Files.exists(assetPath(-1, true)));
    }

    @Test
    public void animationAssetsArePathsUnderTheWeatherAssetDirectory() {
        String asset = WeatherController.animationAssetFor(0, true);
        assertEquals("weather/clear-day.json", asset);
        assertTrue(asset.startsWith(WeatherController.WEATHER_ANIMATION_ASSET_DIR + "/"));
    }

    @Test
    public void conditionsThatLookDifferentAfterDarkGetTheirNightCut() {
        // Clear, partly cloudy, fog, drizzle, freezing drizzle, rain, freezing rain, snow,
        // rain and snow showers, thunderstorm and hail-bearing thunderstorm all carry a sun or
        // a moon behind the weather.
        for (int code : new int[] {0, 1, 2, 45, 51, 56, 61, 66, 71, 80, 85, 95, 96}) {
            assertNotEquals("code " + code + " must differ by day/night",
                WeatherController.animationFor(code, true), WeatherController.animationFor(code, false));
        }
        // Overcast, rime fog and snow grains have no sun or moon in frame, so a day/night split
        // there would be a difference the sky does not have.
        for (int code : new int[] {3, 48, 77}) {
            assertEquals("code " + code + " must not differ by day/night",
                WeatherController.animationFor(code, true), WeatherController.animationFor(code, false));
        }
    }

    @Test
    public void distinctConditionsDoNotCollapseOntoOneIcon() {
        // One icon per condition family: freezing rain must not read as rain, hail not as a
        // plain thunderstorm, a shower not as steady rain. The vector set this replaced
        // collapsed all of these onto eight shapes.
        Set<String> daytime = new HashSet<>();
        for (int code : new int[] {0, 2, 3, 45, 48, 51, 56, 61, 66, 71, 77, 80, 85, 95, 96}) {
            assertTrue("duplicate daytime icon for code " + code,
                daytime.add(WeatherController.animationFor(code, true)));
        }
    }

    @Test
    public void anUnknownCodeReadsAsUnknownRatherThanCloudy() {
        String na = WeatherController.animationFor(-1, true);
        assertEquals("not-available", na);
        assertEquals(na, WeatherController.animationFor(1234, false));
        assertFalse(na.equals(WeatherController.animationFor(3, true)));
        assertEquals("—", WeatherController.describe(-1));
    }

    @Test
    public void describeSeparatesHailBearingThunderstorms() {
        assertEquals("Thunderstorm", WeatherController.describe(95));
        assertEquals("Thunderstorm with hail", WeatherController.describe(96));
        assertEquals("Thunderstorm with hail", WeatherController.describe(99));
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

    /**
     * The (label, glyph, day animation, night animation) every code Open-Meteo reports resolved to
     * before the three cascades became one table. Glyphs are Nerd Font private-use code points.
     */
    private static final Object[][] WMO_TABLE = {
        {0, "Clear", "\ue30d", "clear-day", "clear-night"},
        {1, "Mainly clear", "\ue30c", "clear-day", "clear-night"},
        {2, "Partly cloudy", "\ue302", "partly-cloudy-day", "partly-cloudy-night"},
        {3, "Overcast", "\ue312", "overcast", "overcast"},
        {45, "Fog", "\ue313", "fog-day", "fog-night"},
        {48, "Fog", "\ue313", "overcast-fog", "overcast-fog"},
        {51, "Drizzle", "\ue31b", "partly-cloudy-day-drizzle", "partly-cloudy-night-drizzle"},
        {53, "Drizzle", "\ue31b", "partly-cloudy-day-drizzle", "partly-cloudy-night-drizzle"},
        {55, "Drizzle", "\ue31b", "partly-cloudy-day-drizzle", "partly-cloudy-night-drizzle"},
        {56, "Drizzle", "\ue31b", "partly-cloudy-day-sleet", "partly-cloudy-night-sleet"},
        {57, "Drizzle", "\ue31b", "partly-cloudy-day-sleet", "partly-cloudy-night-sleet"},
        {61, "Rain", "\ue318", "overcast-day-rain", "overcast-night-rain"},
        {63, "Rain", "\ue318", "overcast-day-rain", "overcast-night-rain"},
        {65, "Rain", "\ue318", "overcast-day-rain", "overcast-night-rain"},
        {66, "Rain", "\ue318", "overcast-day-sleet", "overcast-night-sleet"},
        {67, "Rain", "\ue318", "overcast-day-sleet", "overcast-night-sleet"},
        {71, "Snow", "\ue31a", "overcast-day-snow", "overcast-night-snow"},
        {73, "Snow", "\ue31a", "overcast-day-snow", "overcast-night-snow"},
        {75, "Snow", "\ue31a", "overcast-day-snow", "overcast-night-snow"},
        {77, "Snow", "\ue31a", "snowflake", "snowflake"},
        {80, "Showers", "\ue319", "partly-cloudy-day-rain", "partly-cloudy-night-rain"},
        {81, "Showers", "\ue319", "partly-cloudy-day-rain", "partly-cloudy-night-rain"},
        {82, "Showers", "\ue319", "partly-cloudy-day-rain", "partly-cloudy-night-rain"},
        {85, "Snow showers", "\ue31a", "partly-cloudy-day-snow", "partly-cloudy-night-snow"},
        {86, "Snow showers", "\ue31a", "partly-cloudy-day-snow", "partly-cloudy-night-snow"},
        {95, "Thunderstorm", "\ue31d", "thunderstorms-day", "thunderstorms-night"},
        {96, "Thunderstorm with hail", "\ue31d", "thunderstorms-day-hail", "thunderstorms-night-hail"},
        {99, "Thunderstorm with hail", "\ue31d", "thunderstorms-day-hail", "thunderstorms-night-hail"},
        {-1, "\u2014", "\ue312", "not-available", "not-available"},
    };

    @Test
    public void everyWmoCodeKeepsItsLabelGlyphAndAnimations() {
        Set<Integer> covered = new HashSet<>();
        for (Object[] row : WMO_TABLE) {
            int code = (Integer) row[0];
            covered.add(code);
            assertEquals("label " + code, row[1], WeatherController.describe(code));
            assertEquals("glyph " + code, row[2], WeatherController.glyphFor(code));
            assertEquals("day " + code, row[3], WeatherController.animationFor(code, true));
            assertEquals("night " + code, row[4], WeatherController.animationFor(code, false));
        }
        for (int code : WMO_CODES) assertTrue("code " + code + " unpinned", covered.contains(code));
    }
}
