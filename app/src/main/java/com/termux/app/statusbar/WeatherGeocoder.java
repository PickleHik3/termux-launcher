package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Open-Meteo's keyless place search, shared by the Settings place picker (live results as the user
 * types) and {@link WeatherController} (which only searches when a stored place has lost its
 * coordinates). Everything but {@link #search} is pure, so the URL and the answer's reading are
 * tested without a network.
 *
 * <p>Open-Meteo matches place names, not addresses: "Berlin, Germany" as a whole is not a name it
 * knows. So only what comes before the first comma is searched for, and the rest is a qualifier that
 * moves the results naming that region or country to the top.
 */
public final class WeatherGeocoder {

    /** How many matches the picker lists under its field. */
    public static final int PICKER_COUNT = 8;

    private WeatherGeocoder() {}

    /** One match: what the header calls it, the line under it in the picker, and where it is. */
    public static final class Result {
        @NonNull public final String name;
        @NonNull public final String admin1;
        @NonNull public final String country;
        @NonNull public final String countryCode;
        public final double latitude;
        public final double longitude;

        Result(@NonNull String name, @NonNull String admin1, @NonNull String country,
               @NonNull String countryCode, double latitude, double longitude) {
            this.name = name;
            this.admin1 = admin1;
            this.country = country;
            this.countryCode = countryCode;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /**
         * "Salmiya, Hawalli": the name with its region, or its country when it has no region. This
         * is what is stored and what the weather card's header shows, so it says which Salmiya.
         */
        @NonNull
        public String label() {
            String region = !admin1.isEmpty() ? admin1 : country;
            if (name.isEmpty()) return region;
            if (region.isEmpty() || region.equals(name)) return name;
            return name + ", " + region;
        }

        /** "Hawalli, Kuwait", the picker's second line; a part equal to the name is left out. */
        @NonNull
        public String detail() {
            List<String> parts = new ArrayList<>();
            if (!admin1.isEmpty() && !admin1.equals(name)) parts.add(admin1);
            if (!country.isEmpty() && !country.equals(name) && !parts.contains(country)) parts.add(country);
            return String.join(", ", parts);
        }
    }

    /** What is searched for: the text before the first comma, trimmed. */
    @NonNull
    public static String searchName(@NonNull String typed) {
        int comma = typed.indexOf(',');
        return (comma < 0 ? typed : typed.substring(0, comma)).trim();
    }

    /** What follows the first comma ("Germany", "Texas"), trimmed; empty when there is none. */
    @NonNull
    public static String qualifier(@NonNull String typed) {
        int comma = typed.indexOf(',');
        return comma < 0 ? "" : typed.substring(comma + 1).trim();
    }

    /** The search request for what was typed. */
    @NonNull
    public static String searchUrl(@NonNull String typed, @NonNull String language, int count) {
        String name;
        try {
            // URLEncoder writes a space as '+', which is form encoding; a query string wants %20.
            name = URLEncoder.encode(searchName(typed), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return "https://geocoding-api.open-meteo.com/v1/search?name=" + name
            + "&count=" + count + "&language=" + language + "&format=json";
    }

    /**
     * The matches in an answer, in the geocoder's order, skipping any without coordinates. An
     * answer with no {@code results} key (Open-Meteo's way of saying "nothing") is an empty list.
     */
    @NonNull
    public static List<Result> parse(@NonNull JSONObject root) {
        List<Result> out = new ArrayList<>();
        JSONArray results = root.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            double latitude = r.optDouble("latitude", Double.NaN);
            double longitude = r.optDouble("longitude", Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) continue;
            out.add(new Result(r.optString("name"), r.optString("admin1"), r.optString("country"),
                r.optString("country_code"), latitude, longitude));
        }
        return out;
    }

    /**
     * The matches whose region, country or country code starts with the qualifier first ("United
     * States" finds "United States of America"), the rest after them, each group in the geocoder's
     * order. With no qualifier the list is returned as it came.
     */
    @NonNull
    public static List<Result> rank(@NonNull List<Result> results, @NonNull String qualifier) {
        String wanted = qualifier.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) return results;
        List<Result> named = new ArrayList<>();
        List<Result> rest = new ArrayList<>();
        for (Result r : results) {
            (namesRegion(wanted, r.admin1, r.country, r.countryCode) ? named : rest).add(r);
        }
        named.addAll(rest);
        return named;
    }

    private static boolean namesRegion(@NonNull String wanted, @NonNull String... fields) {
        for (String field : fields) {
            if (!field.isEmpty() && field.toLowerCase(Locale.ROOT).startsWith(wanted)) return true;
        }
        return false;
    }

    /** The device's language for the result names, English when it has none. */
    @NonNull
    public static String language() {
        String language = Locale.getDefault().getLanguage();
        return language == null || language.isEmpty() ? "en" : language;
    }

    /**
     * Searches, blocking: ranked matches, an empty list when the geocoder found nothing, or null
     * when it could not be reached or its answer could not be read. Never call on the main thread.
     */
    @WorkerThread
    @Nullable
    public static List<Result> search(@NonNull String typed, int count) {
        if (searchName(typed).length() < 2) return Collections.emptyList();
        String body = WeatherController.httpGet(searchUrl(typed, language(), count));
        if (body == null) return null;
        try {
            return rank(parse(new JSONObject(body)), qualifier(typed));
        } catch (Exception e) {
            return null;
        }
    }
}
