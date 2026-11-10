package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Refresh policy of the weather: stale-while-revalidate, backoff, retry, persistence, sharing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WeatherRefreshTest {

    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    private static final String OK_BODY =
        "{\"current\":{\"temperature_2m\":21.5,\"weather_code\":2}}";

    private Context mContext;
    private long mNow;
    private String mPlaceLabel;
    private final ArrayDeque<Runnable> mQueue = new ArrayDeque<>();
    private final AtomicInteger mRequests = new AtomicInteger();
    /** HTTP status the fake answers with; 200 answers {@link #OK_BODY}, 0 is an I/O error. */
    private volatile int mStatus;

    @Before public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mNow = 1_000_000_000L;
        mPlaceLabel = "Kuwait";
        mStatus = 200;
        WeatherController.resetSharedForTest();
        new File(mContext.getFilesDir(), "weather_last.json").delete();
        WeatherController.setClockForTest(() -> mNow);
        WeatherController.setRetryDelayForTest(0L);
        WeatherController.setPlaceSourceForTest(() ->
            new WeatherController.PlaceSetting(mPlaceLabel, new double[] {29.3, 48.0}));
        WeatherController.setExecutorForTest(mQueue::add);
        WeatherController.setTransportForTest(url -> {
            mRequests.incrementAndGet();
            return new WeatherController.HttpResult(mStatus, mStatus == 200 ? OK_BODY : null);
        });
    }

    @After public void tearDown() {
        WeatherController.resetSharedForTest();
    }

    /** Runs the fetch the controller queued, then the main-thread delivery. */
    private void drain() {
        while (!mQueue.isEmpty()) mQueue.poll().run();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class Recorder implements WeatherController.Listener {
        final List<WeatherController.Weather> got = new ArrayList<>();
        @Override public void onWeatherUpdated(@NonNull WeatherController.Weather w) { got.add(w); }
        WeatherController.Weather last() { return got.get(got.size() - 1); }
    }

    private WeatherController controller(Recorder r) {
        return new WeatherController(mContext, r);
    }

    /** A controller holding a good forecast, with the clock moved {@code ageMs} past it. */
    private WeatherController withForecastAged(Recorder r, long ageMs) {
        WeatherController c = controller(r);
        c.refreshIfStale();
        drain();
        assertTrue(c.cache().valid);
        mNow += ageMs;
        r.got.clear();
        return c;
    }

    @Test public void backoffScheduleDoublesThenCapsAtThirtyMinutes() {
        long[] expected = {1, 2, 4, 8, 16, 30, 30, 30};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i] * MIN, WeatherBackoff.delayMs(i + 1));
        }
        assertEquals(0L, WeatherBackoff.delayMs(0));

        WeatherBackoff b = new WeatherBackoff();
        assertTrue(b.allows("a", 0));
        b.onFailure("a", 0);
        assertFalse(b.allows("a", MIN - 1));
        assertTrue(b.allows("a", MIN));
        b.onFailure("a", MIN);
        assertFalse(b.allows("a", MIN + 2 * MIN - 1));
        assertTrue(b.allows("a", MIN + 2 * MIN));
        assertTrue("another place starts clean", b.allows("b", MIN));
        b.onSuccess();
        assertTrue(b.allows("a", MIN));
        assertEquals(0, b.failures());
    }

    @Test public void aFailedRefreshOverARecentStaleForecastPublishesTheForecast() {
        Recorder r = new Recorder();
        WeatherController c = withForecastAged(r, 31 * MIN);
        mStatus = 503;
        c.refreshIfStale();
        drain();
        assertEquals(1, r.got.size());
        assertTrue(r.last().valid);
        assertSame(c.cache(), r.last());
        assertEquals(21.5, c.cache().currentC, 0.0);
    }

    @Test public void aFailedRefreshOverAForecastOlderThanThreeHoursPublishesTheFailure() {
        Recorder r = new Recorder();
        WeatherController c = withForecastAged(r, 3 * HOUR + MIN);
        mStatus = 503;
        c.refreshIfStale();
        drain();
        assertEquals(1, r.got.size());
        assertFalse(r.last().valid);
        assertEquals("network", r.last().error);
    }

    @Test public void automaticRefreshIsHeldBackInsideTheBackoffAndAllowedAfter() {
        Recorder r = new Recorder();
        WeatherController c = withForecastAged(r, 31 * MIN);
        mStatus = 0;
        c.refreshIfStale();
        drain();
        int afterFailure = mRequests.get();

        mNow += MIN - 1;
        c.refreshIfStale();
        assertTrue("no fetch queued inside the window", mQueue.isEmpty());
        assertEquals(afterFailure, mRequests.get());
        assertTrue("the held refresh still replays the forecast", r.last().valid);

        mNow += 1;
        c.refreshIfStale();
        assertFalse("allowed once the window has passed", mQueue.isEmpty());
        drain();
        assertTrue(mRequests.get() > afterFailure);
    }

    @Test public void forceRefreshBypassesTheBackoffAndSuccessResetsIt() {
        Recorder r = new Recorder();
        WeatherController c = withForecastAged(r, 31 * MIN);
        mStatus = 0;
        c.refreshIfStale();
        drain();

        c.forceRefresh();
        assertFalse("an explicit refresh starts at once", mQueue.isEmpty());
        mStatus = 200;
        drain();
        assertTrue(r.last().valid);

        // Success cleared the failure count: the next failure waits one minute, not two.
        mNow += 31 * MIN;
        mStatus = 0;
        c.refreshIfStale();
        drain();
        mNow += MIN;
        c.refreshIfStale();
        assertFalse(mQueue.isEmpty());
        drain();
    }

    @Test public void aPlaceChangeEmptiesTheCacheAndResetsTheBackoff() {
        Recorder r = new Recorder();
        WeatherController c = withForecastAged(r, 31 * MIN);
        mStatus = 0;
        c.refreshIfStale();
        drain();
        mNow += 1;
        c.refreshIfStale();
        assertTrue("held back for the same place", mQueue.isEmpty());

        mPlaceLabel = "Oslo";
        c.refreshIfStale();
        assertFalse("the old place's forecast is gone", c.cache().valid);
        assertFalse("the new place is not held back", mQueue.isEmpty());
        mStatus = 200;
        drain();
        assertTrue(c.cache().valid);
    }

    @Test public void theForecastRequestIsRetriedOnceOnAServerErrorButNotOnA4xx() {
        Recorder r = new Recorder();
        WeatherController c = controller(r);
        mStatus = 503;
        c.refreshIfStale();
        drain();
        assertEquals(2, mRequests.get());

        mRequests.set(0);
        mNow += HOUR;
        mStatus = 404;
        c.forceRefresh();
        drain();
        assertEquals(1, mRequests.get());
    }

    @Test public void persistenceRoundTripsEveryField() throws Exception {
        WeatherController.Weather w = new WeatherController.Weather();
        w.valid = true;
        w.currentC = 21.5;
        w.feelsLikeC = 19.25;
        w.currentCode = 61;
        w.currentIsDay = false;
        w.humidityPct = 55;
        w.windKmh = 12.5;
        w.uvIndexMax = 7.5;
        w.sunrise = "05:01";
        w.sunset = "17:12";
        w.sunriseTomorrow = "05:02";
        w.locationName = "Kuwait City, Al Asimah";
        w.requestedPlace = "Kuwait";
        w.fetchedAtMs = mNow - 5 * MIN;
        w.hourly.add(new WeatherController.Hourly("2026-07-22T15:00", 30.5, 3, true, 40));
        w.hourly.add(new WeatherController.Hourly("2026-07-22T16:00", Double.NaN, 80, false, -1));
        w.daily.add(new WeatherController.Daily("2026-07-22", 36.5, 24.5, 1));
        File file = new File(mContext.getFilesDir(), "weather_last.json");
        WeatherController.writePersisted(file, "k", w);

        WeatherController.Weather back = WeatherController.readPersisted(file, "k", mNow);
        assertNotNull(back);
        assertTrue(back.valid);
        assertEquals(w.currentC, back.currentC, 0.0);
        assertEquals(w.feelsLikeC, back.feelsLikeC, 0.0);
        assertEquals(w.currentCode, back.currentCode);
        assertEquals(w.currentIsDay, back.currentIsDay);
        assertEquals(w.humidityPct, back.humidityPct, 0.0);
        assertEquals(w.windKmh, back.windKmh, 0.0);
        assertEquals(w.uvIndexMax, back.uvIndexMax, 0.0);
        assertEquals(w.sunrise, back.sunrise);
        assertEquals(w.sunset, back.sunset);
        assertEquals(w.sunriseTomorrow, back.sunriseTomorrow);
        assertEquals(w.locationName, back.locationName);
        assertEquals(w.requestedPlace, back.requestedPlace);
        assertEquals(w.fetchedAtMs, back.fetchedAtMs);
        assertEquals(2, back.hourly.size());
        assertEquals("2026-07-22T15:00", back.hourly.get(0).iso);
        assertEquals(30.5, back.hourly.get(0).tempC, 0.0);
        assertEquals(3, back.hourly.get(0).code);
        assertTrue(back.hourly.get(0).isDay);
        assertEquals(40, back.hourly.get(0).precipProb);
        assertTrue(Double.isNaN(back.hourly.get(1).tempC));
        assertFalse(back.hourly.get(1).isDay);
        assertEquals(-1, back.hourly.get(1).precipProb);
        assertEquals(1, back.daily.size());
        assertEquals("2026-07-22", back.daily.get(0).date);
        assertEquals(36.5, back.daily.get(0).maxC, 0.0);
        assertEquals(24.5, back.daily.get(0).minC, 0.0);
        assertEquals(1, back.daily.get(0).code);

        assertNull("another place's forecast", WeatherController.readPersisted(file, "other", mNow));
        assertNull("a forecast past the maximum age",
            WeatherController.readPersisted(file, "k", mNow + 3 * HOUR));
        java.nio.file.Files.write(file.toPath(), "{not json".getBytes());
        assertNull("corrupt data", WeatherController.readPersisted(file, "k", mNow));
    }

    @Test public void aNewProcessStartsFromTheSavedForecast() {
        Recorder first = new Recorder();
        controller(first).refreshIfStale();
        drain();
        String key = "Kuwait@29.30000,48.00000";
        assertNotNull(WeatherController.readPersisted(
            new File(mContext.getFilesDir(), "weather_last.json"), key, mNow));

        WeatherController.resetSharedForTest();
        WeatherController.setClockForTest(() -> mNow);
        WeatherController.setPlaceSourceForTest(() ->
            new WeatherController.PlaceSetting(mPlaceLabel, new double[] {29.3, 48.0}));
        WeatherController.setExecutorForTest(mQueue::add);
        mNow += 10 * MIN;
        WeatherController c = controller(new Recorder());
        c.refreshIfStale();
        assertTrue("restored without a fetch", c.cache().valid);
        assertEquals(21.5, c.cache().currentC, 0.0);
        assertTrue(mQueue.isEmpty());
    }

    @Test public void twoControllersShareOneFetch() {
        Recorder a = new Recorder();
        Recorder b = new Recorder();
        WeatherController ca = controller(a);
        WeatherController cb = controller(b);
        ca.refreshIfStale();
        cb.refreshIfStale();
        assertEquals("one fetch queued", 1, mQueue.size());
        drain();
        assertEquals(1, mRequests.get());
        assertTrue(a.last().valid);
        assertTrue(b.last().valid);
        assertTrue(cb.cache().valid);
    }

    @Test public void aStoppedControllerIsNotHandedResults() {
        Recorder a = new Recorder();
        Recorder b = new Recorder();
        WeatherController ca = controller(a);
        WeatherController cb = controller(b);
        ca.refreshIfStale();
        cb.refreshIfStale();
        cb.stop();
        drain();
        assertTrue(a.last().valid);
        assertTrue(b.got.isEmpty());
    }
}
