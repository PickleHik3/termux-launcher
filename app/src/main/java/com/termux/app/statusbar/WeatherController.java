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
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
 * (the status bar's and the home widgets' each hold their own), since each is refreshed on the
 * way back from Settings: the status bar's by the activity's start, the widgets' by the widget
 * pane's ({@code BuiltinWidgetServices.onHostStart}).
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
        /** Tomorrow's local sunrise as {@code HH:mm}, empty when unknown: the next one after dark. */
        @NonNull public String sunriseTomorrow = "";
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

    /** How long a forecast is replayed before it is fetched again; older data is stale. */
    public static final long CACHE_TTL_MS = 30 * 60 * 1000L;

    /**
     * The place picked in Settings: the label the header shows and the coordinates the forecast is
     * asked for. An empty label means the weather follows the device.
     */
    static final class PlaceSetting {
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

    /**
     * The last forecast any controller fetched, and the place it is for. A controller made later
     * (the Home widgets' own, made when the first Weather card appears, such as the second page
     * layer of a swipe) starts from it instead of empty. Main thread only.
     */
    private static final Weather sLastValid = new Weather();
    @Nullable private static String sLastValidPlace;

    /** A forecast older than this is never shown in place of a failure, nor restored after a restart. */
    public static final long MAX_KEPT_AGE_MS = 3 * 60 * 60 * 1000L;
    /** Pause before the one retry of a forecast request that failed on I/O or a 5xx. */
    private static final long RETRY_DELAY_MS = 1500L;
    /** A fetch still "in flight" after this long is presumed lost, so it cannot wedge refreshes. */
    private static final long IN_FLIGHT_LOST_MS = 60_000L;
    private static final String PERSIST_FILE = "weather_last.json";

    // ---- shared by every controller in the process (main thread only unless noted) ----

    /** Made on first use: the class also loads in plain JVM tests, where there is no Looper. */
    @Nullable private static Handler sMainHandler;
    // Daemon thread so a fetch never keeps the process alive.
    private static final ExecutorService sFetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "weather-fetch");
        t.setDaemon(true);
        return t;
    });
    /** Controllers to hand a finished fetch to; weak, and removed by {@link #stop()}. */
    private static final List<WeakReference<WeatherController>> sLive = new ArrayList<>();
    private static final WeatherBackoff sBackoff = new WeatherBackoff();
    private static boolean sInFlight;
    private static long sInFlightSinceMs;
    private static boolean sSeeded;

    // ---- test seams ----

    /** One HTTP exchange: {@code code} 0 means the request failed before an answer. */
    static final class HttpResult {
        final int code;
        @Nullable final String body;
        HttpResult(int code, @Nullable String body) {
            this.code = code;
            this.body = body;
        }
    }

    interface Transport { @NonNull HttpResult get(@NonNull String url); }
    interface Clock { long nowMs(); }
    interface PlaceSource { @NonNull PlaceSetting get(); }

    private static Transport sTransport = WeatherController::realGet;
    private static Clock sClock = System::currentTimeMillis;
    private static Executor sExecutor = sFetchExecutor;
    private static long sRetryDelayMs = RETRY_DELAY_MS;
    @Nullable private static PlaceSource sPlaceSource;

    static void setTransportForTest(@Nullable Transport transport) {
        sTransport = transport != null ? transport : WeatherController::realGet;
    }
    static void setClockForTest(@Nullable Clock clock) {
        sClock = clock != null ? clock : System::currentTimeMillis;
    }
    static void setExecutorForTest(@Nullable Executor executor) {
        sExecutor = executor != null ? executor : sFetchExecutor;
    }
    static void setPlaceSourceForTest(@Nullable PlaceSource source) {
        sPlaceSource = source;
    }
    static void setRetryDelayForTest(long ms) {
        sRetryDelayMs = ms;
    }
    /** Forget everything shared, as a fresh process would. */
    static void resetSharedForTest() {
        sLive.clear();
        sBackoff.onSuccess();
        sInFlight = false;
        sSeeded = false;
        copyInto(sLastValid, new Weather());
        sLastValidPlace = null;
        sTransport = WeatherController::realGet;
        sClock = System::currentTimeMillis;
        sExecutor = sFetchExecutor;
        sRetryDelayMs = RETRY_DELAY_MS;
        sPlaceSource = null;
    }

    private final Context mContext;
    @Nullable private final Listener mListener;

    private final Weather mCache = new Weather();
    /** {@link PlaceSetting#key()} of the place {@link #mCache} belongs to; main thread only. */
    @Nullable private String mCachePlace;
    /** The last failure this controller saw, replayed while a backoff holds the next attempt. */
    @Nullable private Weather mLastFailure;

    public WeatherController(@NonNull Context context, @Nullable Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    @NonNull
    public Weather cache() {
        return mCache;
    }

    /**
     * Fetch only when the cache is missing, older than the TTL, or for another place, and not
     * before the failure backoff allows. A refresh that is held back replays what is known.
     */
    public void refreshIfStale() {
        PlaceSetting place = placeSetting();
        syncPlace(place);
        register();
        long now = nowMs();
        if (mCache.valid && now - mCache.fetchedAtMs < CACHE_TTL_MS) {
            publish(mCache);
            return;
        }
        if (!sBackoff.allows(place.key(), now)) {
            if (keptForecast(now)) publish(mCache);
            else if (mLastFailure != null) publish(mLastFailure);
            return;
        }
        startFetch(place);
    }

    /** An explicit refresh: a tap, a permission grant. It ignores the failure backoff. */
    public void forceRefresh() {
        PlaceSetting place = placeSetting();
        syncPlace(place);
        register();
        startFetch(place);
    }

    /** The surface is gone: stop being handed fetch results until the next refresh. */
    public void stop() {
        sLive.removeIf(ref -> {
            WeatherController c = ref.get();
            return c == null || c == this;
        });
    }

    private void register() {
        for (WeakReference<WeatherController> ref : sLive) {
            if (ref.get() == this) return;
        }
        sLive.add(new WeakReference<>(this));
    }

    /** The cache holds a forecast for the current place young enough to stand in for a failure. */
    private boolean keptForecast(long now) {
        return mCache.valid && now - mCache.fetchedAtMs < MAX_KEPT_AGE_MS;
    }

    private void startFetch(@NonNull PlaceSetting place) {
        long now = nowMs();
        // Another fetch is under way (this controller's or another's): its answer reaches us.
        if (sInFlight && now - sInFlightSinceMs < IN_FLIGHT_LOST_MS) return;
        sInFlight = true;
        sInFlightSinceMs = now;
        final String key = place.key();
        final File file = persistFile(mContext);
        sExecutor.execute(() -> {
            Weather result;
            try {
                result = fetch(place);
            } catch (Throwable t) {
                result = new Weather();
                result.error = "network";
            }
            if (result.valid) writePersisted(file, key, result);
            final Weather done = result;
            mainHandler().post(() -> onFetchDone(key, done));
        });
    }

    @NonNull
    private static Handler mainHandler() {
        Handler h = sMainHandler;
        if (h == null) sMainHandler = h = new Handler(Looper.getMainLooper());
        return h;
    }

    private static void onFetchDone(@NonNull String key, @NonNull Weather result) {
        sInFlight = false;
        if (result.valid) {
            copyInto(sLastValid, result);
            sLastValidPlace = key;
            sBackoff.onSuccess();
        } else {
            sBackoff.onFailure(key, nowMs());
        }
        List<WeatherController> targets = new ArrayList<>();
        for (int i = sLive.size() - 1; i >= 0; i--) {
            WeatherController c = sLive.get(i).get();
            if (c == null) sLive.remove(i); else targets.add(0, c);
        }
        for (WeatherController c : targets) c.deliver(key, result);
    }

    private void deliver(@NonNull String key, @NonNull Weather result) {
        if (!key.equals(mCachePlace)) {
            // The place changed while this was in flight: this answer is for the old one.
            PlaceSetting current = placeSetting();
            syncPlace(current);
            if (!key.equals(mCachePlace)) {
                startFetch(current);
                return;
            }
        }
        if (result.valid) {
            copyInto(mCache, result);
            mLastFailure = null;
            publish(mCache);
        } else if (keptForecast(nowMs())) {
            publish(mCache);
        } else {
            mLastFailure = result;
            publish(result);
        }
    }

    private void publish(@NonNull Weather weather) {
        if (mListener != null) mListener.onWeatherUpdated(weather);
    }

    /** The place picked in Settings, read fresh: this is how a change there reaches the weather. */
    @NonNull
    private PlaceSetting placeSetting() {
        if (sPlaceSource != null) return sPlaceSource.get();
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
        mLastFailure = null;
        seedFromDisk(key);
        if (!mCache.valid && sLastValid.valid && key.equals(sLastValidPlace)) {
            copyInto(mCache, sLastValid);
        }
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
            String body = forecastGet(url);
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
            w.sunriseTomorrow = clockOf(daily.optJSONArray("sunrise"), 1);
            JSONArray uv = daily.optJSONArray("uv_index_max");
            if (uv != null && uv.length() > 0) w.uvIndexMax = uv.optDouble(0, Double.NaN);
        }
    }

    /** {@code HH:mm} out of the first entry of an Open-Meteo local-ISO array. */
    @NonNull
    private static String clockOf(@Nullable JSONArray isoTimes) {
        return clockOf(isoTimes, 0);
    }

    /** {@code HH:mm} out of entry {@code index} of an Open-Meteo local-ISO array. */
    @NonNull
    private static String clockOf(@Nullable JSONArray isoTimes, int index) {
        if (isoTimes == null || isoTimes.length() <= index) return "";
        String iso = isoTimes.optString(index, "");
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

    /**
     * The forecast request, with one quick retry when it failed on I/O or a 5xx (a flaky link often
     * answers the second time). A 4xx is the request's fault and is not retried.
     */
    @Nullable
    private static String forecastGet(@NonNull String url) {
        HttpResult r = sTransport.get(url);
        if (r.code == 0 || r.code >= 500) {
            try {
                Thread.sleep(sRetryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            r = sTransport.get(url);
        }
        return r.code == 200 ? r.body : null;
    }

    /** A GET returning the body, or null on any failure; shared with {@link WeatherGeocoder}. */
    @Nullable
    static String httpGet(@NonNull String urlString) {
        HttpResult r = sTransport.get(urlString);
        return r.code == 200 ? r.body : null;
    }

    @NonNull
    private static HttpResult realGet(@NonNull String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) return new HttpResult(code, null);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return new HttpResult(200, sb.toString());
        } catch (Exception e) {
            return new HttpResult(0, null);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ---- last forecast on disk, so a restart has something to show until a fetch lands ----

    @NonNull
    private static File persistFile(@NonNull Context context) {
        return new File(context.getFilesDir(), PERSIST_FILE);
    }

    /** Once per process: restore the saved forecast if it is for this place and young enough. */
    private void seedFromDisk(@NonNull String key) {
        if (sSeeded) return;
        sSeeded = true;
        if (sLastValid.valid) return;
        Weather saved = readPersisted(persistFile(mContext), key, nowMs());
        if (saved == null) return;
        copyInto(sLastValid, saved);
        sLastValidPlace = key;
    }

    /** Written off the main thread on each successful fetch; a failure to write loses nothing. */
    static void writePersisted(@NonNull File file, @NonNull String key, @NonNull Weather w) {
        try {
            JSONObject o = new JSONObject();
            o.put("place", key);
            o.put("fetchedAtMs", w.fetchedAtMs);
            o.put("currentIsDay", w.currentIsDay);
            o.put("currentCode", w.currentCode);
            putNum(o, "currentC", w.currentC);
            putNum(o, "feelsLikeC", w.feelsLikeC);
            putNum(o, "humidityPct", w.humidityPct);
            putNum(o, "windKmh", w.windKmh);
            putNum(o, "uvIndexMax", w.uvIndexMax);
            o.put("sunrise", w.sunrise);
            o.put("sunset", w.sunset);
            o.put("sunriseTomorrow", w.sunriseTomorrow);
            o.put("locationName", w.locationName);
            o.put("requestedPlace", w.requestedPlace);
            JSONArray hourly = new JSONArray();
            for (Hourly h : w.hourly) {
                JSONObject j = new JSONObject();
                j.put("iso", h.iso);
                putNum(j, "tempC", h.tempC);
                j.put("code", h.code);
                j.put("isDay", h.isDay);
                j.put("precipProb", h.precipProb);
                hourly.put(j);
            }
            o.put("hourly", hourly);
            JSONArray daily = new JSONArray();
            for (Daily d : w.daily) {
                JSONObject j = new JSONObject();
                j.put("date", d.date);
                putNum(j, "maxC", d.maxC);
                putNum(j, "minC", d.minC);
                j.put("code", d.code);
                daily.put(j);
            }
            o.put("daily", daily);
            File tmp = new File(file.getPath() + ".tmp");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Logger.logVerbose(LOG_TAG, "Could not save the forecast: " + e.getMessage());
        }
    }

    /** JSON has no NaN: a missing reading is left out and reads back as NaN. */
    private static void putNum(@NonNull JSONObject o, @NonNull String name, double v) throws Exception {
        if (!Double.isNaN(v) && !Double.isInfinite(v)) o.put(name, v);
    }

    /** The saved forecast when it is for {@code key} and under {@link #MAX_KEPT_AGE_MS}, else null. */
    @Nullable
    static Weather readPersisted(@NonNull File file, @NonNull String key, long nowMs) {
        try {
            if (!file.isFile()) return null;
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            JSONObject o = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!key.equals(o.optString("place", null))) return null;
            long fetchedAt = o.getLong("fetchedAtMs");
            long age = nowMs - fetchedAt;
            if (fetchedAt <= 0L || age < 0L || age >= MAX_KEPT_AGE_MS) return null;
            Weather w = new Weather();
            w.fetchedAtMs = fetchedAt;
            w.currentIsDay = o.optBoolean("currentIsDay", true);
            w.currentCode = o.optInt("currentCode", 0);
            w.currentC = o.optDouble("currentC", Double.NaN);
            w.feelsLikeC = o.optDouble("feelsLikeC", Double.NaN);
            w.humidityPct = o.optDouble("humidityPct", Double.NaN);
            w.windKmh = o.optDouble("windKmh", Double.NaN);
            w.uvIndexMax = o.optDouble("uvIndexMax", Double.NaN);
            w.sunrise = o.optString("sunrise", "");
            w.sunset = o.optString("sunset", "");
            w.sunriseTomorrow = o.optString("sunriseTomorrow", "");
            w.locationName = o.optString("locationName", "");
            w.requestedPlace = o.optString("requestedPlace", "");
            JSONArray hourly = o.getJSONArray("hourly");
            for (int i = 0; i < hourly.length(); i++) {
                JSONObject j = hourly.getJSONObject(i);
                w.hourly.add(new Hourly(j.getString("iso"), j.optDouble("tempC", Double.NaN),
                    j.getInt("code"), j.optBoolean("isDay", true), j.optInt("precipProb", -1)));
            }
            JSONArray daily = o.getJSONArray("daily");
            for (int i = 0; i < daily.length(); i++) {
                JSONObject j = daily.getJSONObject(i);
                w.daily.add(new Daily(j.getString("date"), j.optDouble("maxC", Double.NaN),
                    j.optDouble("minC", Double.NaN), j.getInt("code")));
            }
            w.valid = true;
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    private static void copyInto(@NonNull Weather dst, @NonNull Weather src) {
        dst.valid = src.valid;
        dst.currentC = src.currentC;
        dst.feelsLikeC = src.feelsLikeC;
        dst.sunrise = src.sunrise;
        dst.sunset = src.sunset;
        dst.sunriseTomorrow = src.sunriseTomorrow;
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
        return sClock.nowMs();
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
