package com.termux.app.statusbar;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.DrawableRes;
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
        public int currentCode;
        public boolean currentIsDay = true;
        @NonNull public List<Hourly> hourly = new ArrayList<>();
        @NonNull public List<Daily> daily = new ArrayList<>();
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
                    + "&current=temperature_2m,weather_code,is_day"
                    + "&hourly=temperature_2m,weather_code,is_day"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&timezone=auto&forecast_days=7",
                location.getLatitude(), location.getLongitude());
            String body = httpGet(url);
            if (body == null) {
                w.error = "network";
                return w;
            }
            parse(new JSONObject(body), w);
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
        }
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

    /** Material vector icon for a WMO weather code, including clear/partly-cloudy day/night. */
    @DrawableRes
    public static int iconFor(int code, boolean isDay) {
        if (code == 0) return isDay ? R.drawable.ic_weather_clear_day : R.drawable.ic_weather_clear_night;
        if (code == 1 || code == 2) {
            return isDay ? R.drawable.ic_weather_partly_cloudy_day
                : R.drawable.ic_weather_partly_cloudy_night;
        }
        if (code == 3) return R.drawable.ic_weather_cloudy;
        if (code == 45 || code == 48) return R.drawable.ic_weather_fog;
        if (code >= 51 && code <= 57) return R.drawable.ic_weather_drizzle;
        if (code >= 61 && code <= 67) return R.drawable.ic_weather_rain;
        if (code >= 71 && code <= 77) return R.drawable.ic_weather_snow;
        if (code >= 80 && code <= 82) return R.drawable.ic_weather_rain;
        if (code >= 85 && code <= 86) return R.drawable.ic_weather_snow;
        if (code >= 95) return R.drawable.ic_weather_thunderstorm;
        return R.drawable.ic_weather_cloudy;
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
        if (code >= 95) return "Thunderstorm";
        return "—";
    }
}
