package com.termux.app.statusbar;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import com.termux.shared.logger.Logger;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches an Open-Meteo forecast (keyless) for the device's last known location and caches it. There
 * is no persistent location polling: the location is read once per refresh via
 * {@link LocationManager#getLastKnownLocation}. Results are cached for {@link #CACHE_TTL_MS}; a
 * refresh within that window replays the cache instead of hitting the network.
 *
 * <p>When the user has picked a place in Settings (status_widget_weather_location and its stored
 * coordinates, chosen from {@link WeatherGeocoder}'s live results), the forecast is fetched for those
 * coordinates instead and the device's location is never read, so the weather works without the
 * location permission. No search is made at fetch time; a label stored without coordinates is the
 * one exception, resolved by a search and cached. The place is read on every refresh and the
 * cache remembers which place it was fetched for: a changed place empties the cache and fetches
 * again, with no restart and no listener on the preference. That covers every controller at once
 * (the status bar's and the home widgets' each hold their own), since each refreshes on its way
 * back from Settings.
 */
public final class WeatherController {

    private static final String LOG_TAG = "WeatherController";

    public static final class Hourly {
        /** Local ISO time, e.g. {@code 2026-07-22T15:00}. */
        @NonNull public final String iso;
        public final double tempC;
        public final int code;
        public final boolean isDay;
        /** Precipitation probability in percent, -1 when the provider omitted it. */
        public final int precipProb;
        Hourly(@NonNull String iso, double tempC, int code, boolean isDay, int precipProb) {
            this.iso = iso;
            this.tempC = tempC;
            this.code = code;
            this.isDay = isDay;
            this.precipProb = precipProb;
        }
    }

    public static final class Daily {
        /** Local ISO date, e.g. {@code 2026-07-22}. */
        @NonNull public final String date;
        public final double maxC;
        public final double minC;
        public final int code;
        Daily(@NonNull String date, double maxC, double minC, int code) {
            this.date = date;
            this.maxC = maxC;
            this.minC = minC;
            this.code = code;
        }
    }

    public static final class Weather {
        public boolean valid;
        public double currentC;
        /** Apparent ("feels like") temperature in Celsius, NaN when the provider omitted it. */
        public double feelsLikeC = Double.NaN;
        public int currentCode;
        public boolean currentIsDay = true;
        /** Current relative humidity in percent, NaN when the provider omitted it. */
        public double humidityPct = Double.NaN;
        /** Current 10m wind speed in km/h, NaN when the provider omitted it. */
        public double windKmh = Double.NaN;
        /** Today's UV index maximum, NaN when the provider omitted it. */
        public double uvIndexMax = Double.NaN;
        @NonNull public List<Hourly> hourly = new ArrayList<>();
        @NonNull public List<Daily> daily = new ArrayList<>();
        /** Today's local sunrise/sunset as {@code HH:mm}, empty when unknown. */
        @NonNull public String sunrise = "";
        @NonNull public String sunset = "";
        /** Nearest place name for the fix, empty when reverse geocoding is unavailable. */
        @NonNull public String locationName = "";
        /**
         * The place picked in Settings this was fetched for, empty when it follows the device. Set
         * on a failed fetch too, so the {@code unknown-place} message can name what was not found.
         */
        @NonNull public String requestedPlace = "";
        public long fetchedAtMs;
        @Nullable public String error;
    }

    public interface Listener {
        void onWeatherUpdated(@NonNull Weather weather);
    }

    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

    /**
     * The place picked in Settings: the label the header shows and the coordinates the forecast is
     * asked for. An empty label means the weather follows the device.
     */
    private static final class PlaceSetting {
        @NonNull final String label;
        /** {latitude, longitude}; null when only a label was stored. */
        @Nullable final double[] coords;

        PlaceSetting(@NonNull String label, @Nullable double[] coords) {
            this.label = label;
            this.coords = coords;
        }

        boolean isDevice() {
            return label.isEmpty();
        }

        /** Two settings that would fetch the same forecast have the same key. */
        @NonNull
        String key() {
            if (coords == null) return label;
            return String.format(Locale.ROOT, "%s@%.5f,%.5f", label, coords[0], coords[1]);
        }
    }

    /**
     * Labels resolved by searching, for a place stored without its coordinates (the picker always
     * stores them, so this is a fallback). Process-wide: the home widgets' controller is dropped and
     * rebuilt as they come and go, and a place does not move.
     */
    private static final Map<String, WeatherGeocoder.Result> sResolvedPlaces = new ConcurrentHashMap<>();
    /**
     * When a label last came back with no match. Kept for {@link #CACHE_TTL_MS}, because the status
     * bar asks for a refresh on every relayout while it has no forecast, and an unknown place would
     * otherwise send a search each time.
     */
    private static final Map<String, Long> sUnknownPlaces = new ConcurrentHashMap<>();

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    // Daemon thread so a controller left over from an activity recreation never keeps the process
    // alive; the pool is GC'd with the controller.
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "weather-fetch");
        t.setDaemon(true);
        return t;
    });
    @Nullable private final Listener mListener;

    private final Weather mCache = new Weather();
    /** {@link PlaceSetting#key()} of the place {@link #mCache} belongs to; main thread only. */
    @Nullable private String mCachePlace;
    private volatile boolean mInFlight;

    public WeatherController(@NonNull Context context, @Nullable Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    @NonNull
    public Weather cache() {
        return mCache;
    }

    /** Fetch only when the cache is missing, older than the TTL, or for another place. */
    public void refreshIfStale() {
        syncPlace(placeSetting());
        long now = nowMs();
        if (mCache.valid && now - mCache.fetchedAtMs < CACHE_TTL_MS) {
            publish(mCache);
            return;
        }
        forceRefresh();
    }

    public void forceRefresh() {
        PlaceSetting place = placeSetting();
        syncPlace(place);
        if (mInFlight) return;
        mInFlight = true;
        mExecutor.execute(() -> {
            Weather result = fetch(place);
            mMainHandler.post(() -> {
                mInFlight = false;
                // The place changed while this was in flight, and the refresh that saw the change
                // was turned away by mInFlight: this answer is for the old place, so fetch again.
                if (!place.key().equals(mCachePlace)) {
                    forceRefresh();
                    return;
                }
                if (result.valid) {
                    copyInto(mCache, result);
                }
                publish(result.valid ? mCache : result);
            });
        });
    }

    public void stop() {
        // No persistent work to cancel; kept for symmetry with the stats controller.
    }

    private void publish(@NonNull Weather weather) {
        if (mListener != null) mListener.onWeatherUpdated(weather);
    }

    /** The place picked in Settings, read fresh: this is how a change there reaches the weather. */
    @NonNull
    private PlaceSetting placeSetting() {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(mContext, false);
        if (preferences == null) return new PlaceSetting("", null);
        return new PlaceSetting(preferences.getStatusWidgetWeatherLocation(),
            preferences.getStatusWidgetWeatherLocationCoords());
    }

    /**
     * A forecast for another place is not a stale forecast for this one, so a changed place empties
     * the cache instead of letting the old place's weather stand in while the new one loads.
     */
    private void syncPlace(@NonNull PlaceSetting place) {
        String key = place.key();
        if (key.equals(mCachePlace)) return;
        if (mCachePlace != null) copyInto(mCache, new Weather());
        mCachePlace = key;
    }

    @NonNull
    private Weather fetch(@NonNull PlaceSetting place) {
        Weather w = new Weather();
        double latitude;
        double longitude;
        Location location = null;
        String placeName = null;
        if (!place.isDevice()) {
            // A picked place never reads the device's location, so it needs no permission.
            w.requestedPlace = place.label;
            if (place.coords != null) {
                latitude = place.coords[0];
                longitude = place.coords[1];
                placeName = place.label;
            } else {
                WeatherGeocoder.Result resolved = resolveLabel(place.label);
                if (resolved == null) {
                    // resolveLabel records a label the search had no match for; anything else
                    // that left it empty-handed was the network.
                    w.error = sUnknownPlaces.containsKey(labelKey(place.label))
                        ? "unknown-place" : "network";
                    return w;
                }
                latitude = resolved.latitude;
                longitude = resolved.longitude;
                placeName = resolved.label();
            }
        } else {
            location = lastKnownLocation();
            if (location == null) {
                w.error = "no-location";
                return w;
            }
            latitude = location.getLatitude();
            longitude = location.getLongitude();
        }
        try {
            String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                    + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                    + ",relative_humidity_2m,wind_speed_10m"
                    + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                    + ",uv_index_max"
                    + "&timezone=auto&forecast_days=7",
                latitude, longitude);
            String body = httpGet(url);
            if (body == null) {
                w.error = "network";
                return w;
            }
            parse(new JSONObject(body), w);
            w.locationName = placeName != null ? placeName : placeName(location);
            w.valid = true;
            w.fetchedAtMs = nowMs();
        } catch (Exception e) {
            w.error = "parse";
        }
        return w;
    }

    /**
     * A stored label's best match, from the cache or a search. Null when the search could not be
     * made, or when it found nothing; the latter is recorded in {@link #sUnknownPlaces}, which is
     * how the caller tells the two apart.
     */
    @Nullable
    private static WeatherGeocoder.Result resolveLabel(@NonNull String label) {
        String key = labelKey(label);
        WeatherGeocoder.Result cached = sResolvedPlaces.get(key);
        if (cached != null) return cached;
        Long missedAt = sUnknownPlaces.get(key);
        if (missedAt != null) {
            if (nowMs() - missedAt < CACHE_TTL_MS) return null;
            sUnknownPlaces.remove(key);
        }
        // One match is enough for a bare name; a qualifier needs a few to choose among.
        int count = WeatherGeocoder.qualifier(label).isEmpty() ? 1 : 10;
        List<WeatherGeocoder.Result> results = WeatherGeocoder.search(label, count);
        if (results == null) return null;
        if (results.isEmpty()) {
            sUnknownPlaces.put(key, nowMs());
            return null;
        }
        sResolvedPlaces.put(key, results.get(0));
        return results.get(0);
    }

    @NonNull
    private static String labelKey(@NonNull String label) {
        return label.toLowerCase(Locale.ROOT) + "|" + WeatherGeocoder.language();
    }

    private static void parse(@NonNull JSONObject root, @NonNull Weather w) throws Exception {
        JSONObject current = root.optJSONObject("current");
        if (current != null) {
            w.currentC = current.optDouble("temperature_2m", Double.NaN);
            w.feelsLikeC = current.optDouble("apparent_temperature", Double.NaN);
            w.currentCode = current.optInt("weather_code", 0);
            w.currentIsDay = current.optInt("is_day", 1) != 0;
            w.humidityPct = current.optDouble("relative_humidity_2m", Double.NaN);
            w.windKmh = current.optDouble("wind_speed_10m", Double.NaN);
        }
        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray time = hourly.optJSONArray("time");
            JSONArray temp = hourly.optJSONArray("temperature_2m");
            JSONArray code = hourly.optJSONArray("weather_code");
            JSONArray isDay = hourly.optJSONArray("is_day");
            JSONArray precip = hourly.optJSONArray("precipitation_probability");
            if (time != null && temp != null && code != null) {
                for (int i = 0; i < time.length(); i++) {
                    w.hourly.add(new Hourly(time.optString(i), temp.optDouble(i), code.optInt(i),
                        isDay == null || isDay.optInt(i, 1) != 0,
                        precip == null ? -1 : precip.optInt(i, -1)));
                }
            }
        }
        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray time = daily.optJSONArray("time");
            JSONArray max = daily.optJSONArray("temperature_2m_max");
            JSONArray min = daily.optJSONArray("temperature_2m_min");
            JSONArray code = daily.optJSONArray("weather_code");
            if (time != null && max != null && min != null && code != null) {
                for (int i = 0; i < time.length(); i++) {
                    w.daily.add(new Daily(time.optString(i), max.optDouble(i), min.optDouble(i), code.optInt(i)));
                }
            }
            // Today's pair only: the card draws the daylight track for the day it is showing.
            w.sunrise = clockOf(daily.optJSONArray("sunrise"));
            w.sunset = clockOf(daily.optJSONArray("sunset"));
            JSONArray uv = daily.optJSONArray("uv_index_max");
            if (uv != null && uv.length() > 0) w.uvIndexMax = uv.optDouble(0, Double.NaN);
        }
    }

    /** {@code HH:mm} out of the first entry of an Open-Meteo local-ISO array. */
    @NonNull
    private static String clockOf(@Nullable JSONArray isoTimes) {
        if (isoTimes == null || isoTimes.length() == 0) return "";
        String iso = isoTimes.optString(0, "");
        int t = iso.indexOf('T');
        return t >= 0 && iso.length() >= t + 6 ? iso.substring(t + 1, t + 6) : "";
    }

    /**
     * Nearest place name for the fix, for the card's header.
     *
     * <p>Best effort by design: Geocoder needs a backend the device may not have, and it is a
     * blocking call, so it runs on the fetch thread and an empty answer simply leaves the header
     * without a place name rather than failing the forecast.
     */
    @NonNull
    private String placeName(@NonNull Location location) {
        String local = platformPlaceName(location);
        if (!local.isEmpty()) return local;
        return remotePlaceName(location);
    }

    /** Android's own reverse geocoder, which needs a backend the device may simply not have. */
    @NonNull
    private String platformPlaceName(@NonNull Location location) {
        if (!Geocoder.isPresent()) {
            Logger.logVerbose(LOG_TAG, "Geocoder unavailable; falling back for the place name");
            return "";
        }
        try {
            List<Address> addresses = new Geocoder(mContext, Locale.getDefault())
                .getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses == null || addresses.isEmpty()) {
                Logger.logVerbose(LOG_TAG, "Geocoder returned no address; falling back");
                return "";
            }
            Address address = addresses.get(0);
            String locality = firstNonEmpty(address.getLocality(), address.getSubAdminArea(),
                address.getSubLocality(), address.getAdminArea());
            if (locality.isEmpty()) return "";
            return withRegion(locality, address.getAdminArea());
        } catch (Exception e) {
            Logger.logVerbose(LOG_TAG, "Geocoder failed (" + e.getClass().getSimpleName()
                + "); falling back");
            return "";
        }
    }

    /**
     * Keyless reverse geocode, used only when the platform geocoder has nothing — which is the
     * common case on a de-Googled device, and was why the card kept reading "Current location".
     *
     * <p>The coordinates are rounded to two decimal places, roughly a kilometre, before they leave
     * the device. That is more than enough to name a city and deliberately not enough to place the
     * user in it, and it means this request carries less than the forecast request already does.
     */
    @NonNull
    private String remotePlaceName(@NonNull Location location) {
        try {
            String url = String.format(Locale.ROOT,
                "https://api.bigdatacloud.net/data/reverse-geocode-client"
                    + "?latitude=%.2f&longitude=%.2f&localityLanguage=%s",
                location.getLatitude(), location.getLongitude(),
                Locale.getDefault().getLanguage());
            String body = httpGet(url);
            if (body == null) return "";
            JSONObject root = new JSONObject(body);
            String locality = firstNonEmpty(root.optString("city"), root.optString("locality"),
                root.optString("principalSubdivision"));
            if (locality.isEmpty()) return "";
            return withRegion(locality, root.optString("principalSubdivision"));
        } catch (Exception e) {
            return "";
        }
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) return candidate;
        }
        return "";
    }

    /** "Kuwait City, Al Asimah", dropping the region when it repeats or adds nothing. */
    @NonNull
    private static String withRegion(@NonNull String locality, @Nullable String region) {
        if (region == null || region.isEmpty() || region.equals(locality)) return locality;
        return locality + ", " + region;
    }

    @Nullable
    private Location lastKnownLocation() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l == null) continue;
                if (best == null || l.getTime() > best.getTime()) best = l;
            }
            return best;
        } catch (SecurityException e) {
            return null;
        }
    }

    /** A GET returning the body, or null on any failure; shared with {@link WeatherGeocoder}. */
    @Nullable
    static String httpGet(@NonNull String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void copyInto(@NonNull Weather dst, @NonNull Weather src) {
        dst.valid = src.valid;
        dst.currentC = src.currentC;
        dst.feelsLikeC = src.feelsLikeC;
        dst.sunrise = src.sunrise;
        dst.sunset = src.sunset;
        dst.locationName = src.locationName;
        dst.requestedPlace = src.requestedPlace;
        dst.currentCode = src.currentCode;
        dst.currentIsDay = src.currentIsDay;
        dst.humidityPct = src.humidityPct;
        dst.windKmh = src.windKmh;
        dst.uvIndexMax = src.uvIndexMax;
        dst.hourly = src.hourly;
        dst.daily = src.daily;
        dst.fetchedAtMs = src.fetchedAtMs;
        dst.error = src.error;
    }

    private static long nowMs() {
        return System.currentTimeMillis();
    }

    // ---- WMO weather-code presentation shared with the card ----

    /**
     * Formats a Celsius reading for display, converting to Fahrenheit when asked. Shared by the
     * compact status-bar widget and the detail card so both always agree on unit and rounding.
     */
    @NonNull
    public static String formatTemp(double celsius, boolean fahrenheit) {
        if (Double.isNaN(celsius)) return "--°";
        return roundedTemp(celsius, fahrenheit) + "°";
    }

    /**
     * Same reading as {@link #formatTemp}, without the degree glyph or unit — for the side status
     * bar's chip, which has no room to spare beside the icon.
     */
    @NonNull
    public static String formatTempBare(double celsius, boolean fahrenheit) {
        if (Double.isNaN(celsius)) return "--";
        return String.valueOf(roundedTemp(celsius, fahrenheit));
    }

    private static long roundedTemp(double celsius, boolean fahrenheit) {
        return Math.round(fahrenheit ? celsius * 9 / 5 + 32 : celsius);
    }

    /**
     * Directory under {@code assets/} holding the bundled Meteocons animations, one Lottie JSON
     * per icon name. MIT licensed; the licence ships beside them.
     */
    public static final String WEATHER_ANIMATION_ASSET_DIR = "weather";

    /**
     * One row of the WMO table: the codes that describe a sky, and everything the UI shows for it.
     *
     * <p>Codes that describe the same sky share a row on purpose — drizzle intensity (51/53/55)
     * is a rate, not a different sky. Conditions with no sun or moon in frame (overcast, rime fog,
     * snow grains) carry one animation for both day and night, because a split there would be a
     * difference the sky does not have. The glyph is the bundled Nerd Font cut for the card's week
     * rows, where a Lottie view per row would be seven animations deep; day variants throughout,
     * because a daily code describes the day, not a moment with an is_day flag.
     */
    enum Condition {
        CLEAR("Clear", "", "clear-day", "clear-night", 0),
        MAINLY_CLEAR("Mainly clear", "", "clear-day", "clear-night", 1),
        PARTLY_CLOUDY("Partly cloudy", "", "partly-cloudy-day", "partly-cloudy-night", 2),
        OVERCAST("Overcast", "", "overcast", "overcast", 3),
        FOG("Fog", "", "fog-day", "fog-night", 45),
        RIME_FOG("Fog", "", "overcast-fog", "overcast-fog", 48),
        DRIZZLE("Drizzle", "", "partly-cloudy-day-drizzle", "partly-cloudy-night-drizzle",
            51, 53, 55),
        FREEZING_DRIZZLE("Drizzle", "", "partly-cloudy-day-sleet", "partly-cloudy-night-sleet",
            56, 57),
        RAIN("Rain", "", "overcast-day-rain", "overcast-night-rain", 61, 63, 65),
        FREEZING_RAIN("Rain", "", "overcast-day-sleet", "overcast-night-sleet", 66, 67),
        SNOW("Snow", "", "overcast-day-snow", "overcast-night-snow", 71, 73, 75),
        SNOW_GRAINS("Snow", "", "snowflake", "snowflake", 77),
        /** Rain showers fall from broken cloud, not an overcast lid. */
        SHOWERS("Showers", "", "partly-cloudy-day-rain", "partly-cloudy-night-rain", 80, 81, 82),
        SNOW_SHOWERS("Snow showers", "", "partly-cloudy-day-snow", "partly-cloudy-night-snow",
            85, 86),
        THUNDERSTORM("Thunderstorm", "", "thunderstorms-day", "thunderstorms-night", 95),
        THUNDERSTORM_HAIL("Thunderstorm with hail", "", "thunderstorms-day-hail",
            "thunderstorms-night-hail", 96, 99),
        /** An unknown code is not "cloudy": it gets the not-available cut and no label. */
        UNKNOWN("—", "", "not-available", "not-available");

        @NonNull final String label;
        @NonNull final String glyph;
        @NonNull final String dayAnimation;
        @NonNull final String nightAnimation;
        @NonNull final int[] codes;

        Condition(@NonNull String label, @NonNull String glyph, @NonNull String dayAnimation,
                  @NonNull String nightAnimation, int... codes) {
            this.label = label;
            this.glyph = glyph;
            this.dayAnimation = dayAnimation;
            this.nightAnimation = nightAnimation;
            this.codes = codes;
        }

        @NonNull
        static Condition forCode(int code) {
            for (Condition condition : values()) {
                for (int c : condition.codes) if (c == code) return condition;
            }
            return UNKNOWN;
        }
    }

    /**
     * Meteocons animation name for a WMO weather code, day/night aware.
     *
     * <p>The Material vector set this replaced had one file per condition, so it collapsed the
     * whole WMO table into eight shapes and only told day from night for a clear or partly clear
     * sky; the Nerd Font set that briefly replaced <em>that</em> read too small at status-bar
     * size. Meteocons carries a day and a night cut of every condition that looks different after
     * dark, at whatever size the view gives it.
     *
     * @param isDay from the provider's own is_day flag, not from the device clock
     * @return the asset's base name, without the {@code .json} suffix
     */
    @NonNull
    public static String animationFor(int code, boolean isDay) {
        Condition condition = Condition.forCode(code);
        return isDay ? condition.dayAnimation : condition.nightAnimation;
    }

    /** The asset path {@code LottieAnimationView.setAnimation(String)} takes. */
    @NonNull
    public static String animationAssetFor(int code, boolean isDay) {
        return WEATHER_ANIMATION_ASSET_DIR + "/" + animationFor(code, isDay) + ".json";
    }

    @NonNull
    public static String describe(int code) {
        return Condition.forCode(code).label;
    }

    /** Bundled Nerd Font weather glyph for a WMO code, for the card's week rows. */
    @NonNull
    public static String glyphFor(int code) {
        return Condition.forCode(code).glyph;
    }
}
