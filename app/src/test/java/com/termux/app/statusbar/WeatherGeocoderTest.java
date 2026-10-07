package com.termux.app.statusbar;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeatherGeocoderTest {

    /** Shaped like an Open-Meteo answer for "Paris" with count=3. */
    private static final String PARIS = "{\"results\":["
        + "{\"id\":2988507,\"name\":\"Paris\",\"latitude\":48.85341,\"longitude\":2.3488,"
        + "\"country_code\":\"FR\",\"admin1\":\"Île-de-France\",\"country\":\"France\"},"
        + "{\"id\":4717560,\"name\":\"Paris\",\"latitude\":33.66094,\"longitude\":-95.55551,"
        + "\"country_code\":\"US\",\"admin1\":\"Texas\",\"country\":\"United States of America\"},"
        + "{\"id\":4647963,\"name\":\"Paris\",\"latitude\":36.302,\"longitude\":-88.32671,"
        + "\"country_code\":\"US\",\"admin1\":\"Tennessee\",\"country\":\"United States of America\"}"
        + "],\"generationtime_ms\":0.9}";

    @Test
    public void searchesOnlyTheNameBeforeTheComma() {
        assertEquals("Berlin", WeatherGeocoder.searchName(" Berlin , Germany"));
        assertEquals("Germany", WeatherGeocoder.qualifier(" Berlin , Germany"));
        assertEquals("Salmiya", WeatherGeocoder.searchName("Salmiya"));
        assertEquals("", WeatherGeocoder.qualifier("Salmiya"));
    }

    @Test
    public void urlEncodesTheNameWithPercentTwentyForSpaces() {
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=Kuwait%20City"
                + "&count=8&language=en&format=json",
            WeatherGeocoder.searchUrl("Kuwait City, Kuwait", "en", 8));
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=S%C3%A3o%20Paulo"
                + "&count=1&language=pt&format=json",
            WeatherGeocoder.searchUrl("São Paulo", "pt", 1));
    }

    @Test
    public void readsEveryResultWithItsLabelAndDetail() throws Exception {
        List<WeatherGeocoder.Result> results = WeatherGeocoder.parse(new JSONObject(PARIS));
        assertEquals(3, results.size());
        WeatherGeocoder.Result first = results.get(0);
        assertEquals(48.85341, first.latitude, 1e-9);
        assertEquals(2.3488, first.longitude, 1e-9);
        assertEquals("Paris, Île-de-France", first.label());
        assertEquals("Île-de-France, France", first.detail());
    }

    @Test
    public void noResultsKeyIsAnEmptyList() throws Exception {
        assertTrue(WeatherGeocoder.parse(new JSONObject("{\"generationtime_ms\":0.4}")).isEmpty());
    }

    @Test
    public void skipsResultsWithoutCoordinates() throws Exception {
        List<WeatherGeocoder.Result> results = WeatherGeocoder.parse(new JSONObject(
            "{\"results\":[{\"name\":\"Nowhere\"},{\"name\":\"Salmiya\",\"latitude\":29.33,"
                + "\"longitude\":48.07,\"admin1\":\"Hawalli\",\"country\":\"Kuwait\"}]}"));
        assertEquals(1, results.size());
        assertEquals("Salmiya, Hawalli", results.get(0).label());
    }

    @Test
    public void labelFallsBackToCountryAndDropsARepeatedRegion() throws Exception {
        List<WeatherGeocoder.Result> results = WeatherGeocoder.parse(new JSONObject(
            "{\"results\":[{\"name\":\"Singapore\",\"latitude\":1.28,\"longitude\":103.85,"
                + "\"country\":\"Singapore\"},{\"name\":\"Monaco\",\"latitude\":43.7,"
                + "\"longitude\":7.4,\"admin1\":\"Monaco\",\"country\":\"Monaco\"}]}"));
        assertEquals("Singapore", results.get(0).label());
        assertEquals("", results.get(0).detail());
        assertEquals("Monaco", results.get(1).label());
    }

    @Test
    public void qualifierMovesMatchingRegionOrCountryFirst() throws Exception {
        List<WeatherGeocoder.Result> results = WeatherGeocoder.parse(new JSONObject(PARIS));
        assertEquals("Texas", WeatherGeocoder.rank(results, "texas").get(0).admin1);
        // A country given as the start of its long name still matches.
        List<WeatherGeocoder.Result> us = WeatherGeocoder.rank(results, "United States");
        assertEquals("Texas", us.get(0).admin1);
        assertEquals("Tennessee", us.get(1).admin1);
        assertEquals("France", us.get(2).country);
        // So does the country code.
        assertEquals("FR", WeatherGeocoder.rank(results, "fr").get(0).countryCode);
        // A qualifier naming none of them keeps the geocoder's order.
        assertEquals("France", WeatherGeocoder.rank(results, "Narnia").get(0).country);
        assertEquals("France", WeatherGeocoder.rank(results, "").get(0).country);
    }
}
