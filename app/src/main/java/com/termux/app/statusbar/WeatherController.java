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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches an Open-Meteo forecast (keyless) for the device's last known location and caches it. There
 * is no persistent location polling: the location is read once per refresh via
 * {@link LocationManager#getLastKnownLocation}. Results are cached for {@link #CACHE_TTL_MS}; a
 * refresh within that window replays the cache instead of hitting the network.
 */
public final class WeatherController {

    private static final String LOG_TAG = "WeatherController";

    public static final class Hourly {
        /** Local ISO time, e.g. {@code 2026-07-22T15:00}. */
        @NonNull public final String iso;
        public final double tempC;
        public final int code;
        public final boolean isDay;
        Hourly(@NonNull String iso, double tempC, int code, boolean isDay) {
            this.iso = iso;
            this.tempC = tempC;
            this.code = code;
            this.isDay = isDay;
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
        @NonNull public List<Hourly> hourly = new ArrayList<>();
        @NonNull public List<Daily> daily = new ArrayList<>();
        /** Today's local sunrise/sunset as {@code HH:mm}, empty when unknown. */
        @NonNull public String sunrise = "";
        @NonNull public String sunset = "";
        /** Nearest place name for the fix, empty when reverse geocoding is unavailable. */
        @NonNull public String locationName = "";
        public long fetchedAtMs;
        @Nullable public String error;
    }

    public interface Listener {
        void onWeatherUpdated(@NonNull Weather weather);
    }

    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

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
    private volatile boolean mInFlight;

    public WeatherController(@NonNull Context context, @Nullable Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    @NonNull
    public Weather cache() {
        return mCache;
    }

    /** Fetch only when the cache is missing or older than the TTL. */
    public void refreshIfStale() {
        long now = nowMs();
        if (mCache.valid && now - mCache.fetchedAtMs < CACHE_TTL_MS) {
            publish(mCache);
            return;
        }
        forceRefresh();
    }

    public void forceRefresh() {
        if (mInFlight) return;
        mInFlight = true;
        mExecutor.execute(() -> {
            Weather result = fetch();
            mMainHandler.post(() -> {
                mInFlight = false;
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

    @NonNull
    private Weather fetch() {
        Weather w = new Weather();
        Location location = lastKnownLocation();
        if (location == null) {
            w.error = "no-location";
            return w;
        }
        try {
            String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                    + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                    + "&hourly=temperature_2m,weather_code,is_day"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                    + "&timezone=auto&forecast_days=7",
                location.getLatitude(), location.getLongitude());
            String body = httpGet(url);
            if (body == null) {
                w.error = "network";
                return w;
            }
            parse(new JSONObject(body), w);
            w.locationName = placeName(location);
            w.valid = true;
            w.fetchedAtMs = nowMs();
        } catch (Exception e) {
            w.error = "parse";
        }
        return w;
    }

    private static void parse(@NonNull JSONObject root, @NonNull Weather w) throws Exception {
        JSONObject current = root.optJSONObject("current");
        if (current != null) {
            w.currentC = current.optDouble("temperature_2m", Double.NaN);
            w.feelsLikeC = current.optDouble("apparent_temperature", Double.NaN);
            w.currentCode = current.optInt("weather_code", 0);
            w.currentIsDay = current.optInt("is_day", 1) != 0;
        }
        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray time = hourly.optJSONArray("time");
            JSONArray temp = hourly.optJSONArray("temperature_2m");
            JSONArray code = hourly.optJSONArray("weather_code");
            JSONArray isDay = hourly.optJSONArray("is_day");
            if (time != null && temp != null && code != null) {
                for (int i = 0; i < time.length(); i++) {
                    w.hourly.add(new Hourly(time.optString(i), temp.optDouble(i), code.optInt(i),
                        isDay == null || isDay.optInt(i, 1) != 0));
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

    @Nullable
    private static String httpGet(@NonNull String urlString) {
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
        dst.currentCode = src.currentCode;
        dst.currentIsDay = src.currentIsDay;
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
        return Math.round(fahrenheit ? celsius * 9 / 5 + 32 : celsius) + "°";
    }

    /**
     * Directory under {@code assets/} holding the bundled Meteocons animations, one Lottie JSON
     * per icon name. MIT licensed; the licence ships beside them.
     */
    public static final String WEATHER_ANIMATION_ASSET_DIR = "weather";

    /**
     * Meteocons animation name for a WMO weather code, day/night aware.
     *
     * <p>The Material vector set this replaced had one file per condition, so it collapsed the
     * whole WMO table into eight shapes and only told day from night for a clear or partly clear
     * sky; the Nerd Font set that briefly replaced <em>that</em> read too small at status-bar
     * size. Meteocons carries a day and a night cut of every condition that looks different after
     * dark, at whatever size the view gives it.
     *
     * <p>Codes that describe the same sky share a name on purpose — drizzle intensity (51/53/55)
     * is a rate, not a different sky. Conditions with no sun or moon in frame (overcast, rime fog,
     * snow grains) return one name for both, because a day/night split there would be a
     * difference the sky does not have.
     *
     * @param isDay from the provider's own is_day flag, not from the device clock
     * @return the asset's base name, without the {@code .json} suffix
     */
    @NonNull
    public static String animationFor(int code, boolean isDay) {
        switch (code) {
            case 0:                       // clear sky
            case 1:                       // mainly clear
                return isDay ? "clear-day" : "clear-night";
            case 2:                       // partly cloudy
                return isDay ? "partly-cloudy-day" : "partly-cloudy-night";
            case 3:                       // overcast
                return "overcast";
            case 45:                      // fog
                return isDay ? "fog-day" : "fog-night";
            case 48:                      // depositing rime fog
                return "overcast-fog";
            case 51: case 53: case 55:    // drizzle
                return isDay ? "partly-cloudy-day-drizzle" : "partly-cloudy-night-drizzle";
            case 56: case 57:             // freezing drizzle
                return isDay ? "partly-cloudy-day-sleet" : "partly-cloudy-night-sleet";
            case 61: case 63: case 65:    // rain
                return isDay ? "overcast-day-rain" : "overcast-night-rain";
            case 66: case 67:             // freezing rain
                return isDay ? "overcast-day-sleet" : "overcast-night-sleet";
            case 71: case 73: case 75:    // snow
                return isDay ? "overcast-day-snow" : "overcast-night-snow";
            case 77:                      // snow grains
                return "snowflake";
            case 80: case 81: case 82:    // rain showers: broken cloud, not an overcast lid
                return isDay ? "partly-cloudy-day-rain" : "partly-cloudy-night-rain";
            case 85: case 86:             // snow showers
                return isDay ? "partly-cloudy-day-snow" : "partly-cloudy-night-snow";
            case 95:                      // thunderstorm
                return isDay ? "thunderstorms-day" : "thunderstorms-night";
            case 96: case 99:             // thunderstorm with hail
                return isDay ? "thunderstorms-day-hail" : "thunderstorms-night-hail";
            default:
                return "not-available";   // an unknown code is not "cloudy"
        }
    }

    /** The asset path {@code LottieAnimationView.setAnimation(String)} takes. */
    @NonNull
    public static String animationAssetFor(int code, boolean isDay) {
        return WEATHER_ANIMATION_ASSET_DIR + "/" + animationFor(code, isDay) + ".json";
    }

    @NonNull
    public static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code == 96 || code == 99) return "Thunderstorm with hail";
        if (code >= 95) return "Thunderstorm";
        return "—";
    }
}
