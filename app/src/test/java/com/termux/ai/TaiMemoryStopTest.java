package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A native load the runtime's watch ends for memory is not a crash: the crash marker goes, the next
 * admission keeps its window, the drop so far joins the history when it says more than the plan,
 * and automatic loads of that model on that accelerator wait out a short cooldown.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiMemoryStopTest {
    private static final long MIB = 1024L * 1024L;
    private static final long E4B = 3_659_530_240L;
    private static final long PONG_TOTAL = 11_530_736L * 1024L;

    private Context context;
    private TaiDeviceCapabilities device;
    private TaiModelSpec e4b;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        TaiRuntimeHistory.clear(context);
        TaiRuntimeCrashMarker.clear(context);
        device = TaiDeviceCapabilities.createForTest("pong", "nothing", "sm8475", 35,
            Collections.singletonList("arm64-v8a"), 12L * 1024L * 1024L * 1024L, "test", false);
        e4b = chatSpec("gemma-4-e4b");
        // Pong's three E4B GPU loads at the cleanup window, 2026-10-13.
        for (long mib : new long[] {2119L, 2588L, 3188L}) {
            TaiRuntimeHistory.recordMeasuredLoad(context, e4b, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 2048, mib * MIB);
        }
    }

    /**
     * The test the review asked for: a real crash at 2048 holds the GPU to 1024, under the floor,
     * so E4B is refused; after a guard stop at the same window the next admission at the same free
     * memory goes ahead at 2048 when the user asks, and an automatic one waits out the cooldown.
     */
    @Test
    public void aGuardStopIsNotACrashAndTheNextLoadKeepsItsWindow() throws JSONException {
        long available = 3804L * MIB;
        TaiRuntimeCrashMarker.markLoad(context, e4b, gpuAt2048(), TaiModelSpec.BACKEND_LITERT_LM);
        assertFalse("a crash at 2048 leaves the GPU half of that, under the floor", admit(available, true).fits);

        TaiLoadMeter meter = TaiLoadMeter.startNativeInit(null,
            new TaiLoadMeter.NativeInit(e4b, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 2048));
        assertTrue(TaiLoadMeter.nativeInitRunning());
        TaiRuntimeService.bookMemoryStop(context, TaiLoadMeter.currentNativeInit(), 431L * MIB);
        meter.stop();
        assertFalse(TaiLoadMeter.nativeInitRunning());

        assertNull(TaiRuntimeCrashMarker.read(context));
        JSONObject stop = TaiRuntimeHistory.pressureStop(context, e4b, device);
        assertNotNull(stop);
        assertEquals("gpu", stop.getString("accelerator"));
        assertEquals(2048, stop.getInt("contextWindow"));
        assertEquals(431L * MIB, stop.getLong("availBytes"));

        TaiLoadBudget.Plan explicit = admit(available, true);
        assertTrue(explicit.fits);
        assertEquals("gpu", explicit.accelerator);
        assertEquals(2048, explicit.contextWindow);

        TaiLoadBudget.Plan automatic = admit(available, false);
        assertFalse("an automatic reload waits out the cooldown", automatic.fits);
        assertEquals("gpu", automatic.accelerator);
    }

    /** The drop so far is a lower bound: it raises the plan when it is above it, and never lowers it. */
    @Test
    public void theDropSoFarJoinsTheHistoryOnlyWhenItSaysMore() {
        assertEquals(2588L * MIB, measured());
        TaiRuntimeHistory.recordPressureStop(context, e4b, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 2048,
            3584L * MIB, 431L * MIB);
        // 2119, 2588, 3188, 3584: the upper median is 3188.
        assertEquals(3188L * MIB, measured());
        TaiRuntimeHistory.recordPressureStop(context, e4b, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 2048,
            1000L * MIB, 431L * MIB);
        assertEquals(3188L * MIB, measured());
        // An unknown drop leaves only the stop record.
        TaiRuntimeHistory.recordPressureStop(context, e4b, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 2048,
            -1L, 431L * MIB);
        assertEquals(3188L * MIB, measured());
        assertNotNull(TaiRuntimeHistory.pressureStop(context, e4b, device));
    }

    /** With first-prefill samples on file the plan reads only those, so the stop is booked as one. */
    @Test
    public void theDropSoFarIsBookedInThePhaseThePlanReads() {
        TaiRuntimeHistory.recordMeasuredLoad(context, e4b, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 2048,
            2700L * MIB, TaiRuntimeHistory.PHASE_FIRST_PREFILL, 300);
        assertEquals(2700L * MIB, measured());
        TaiRuntimeHistory.recordPressureStop(context, e4b, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 2048,
            3600L * MIB, 431L * MIB);
        assertEquals(3600L * MIB, measured());
    }

    private long measured() {
        return TaiRuntimeHistory.measuredLoadBytes(context, e4b, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 2048);
    }

    /** The admission decideLoad makes for cleanup's E4B request: crash marker, memory stop, history. */
    private TaiLoadBudget.Plan admit(long available, boolean explicit) throws JSONException {
        JSONObject marker = TaiRuntimeCrashMarker.read(context);
        String crashed = marker != null && e4b.id.equals(marker.optString("modelId"))
            ? TaiLoadPreflight.normalizeAccelerator(marker.optString("accelerator", null)) : null;
        int crashedContext = marker == null ? 0 : marker.optInt("contextWindow", 0);
        JSONObject stop = TaiRuntimeHistory.pressureStop(context, e4b, device);
        long stoppedAtMs = stop == null ? 0L : stop.optLong("stoppedAtMs", 0L);
        String heldBack = TaiPressureWatch.heldBackAfterStop(stoppedAtMs, System.currentTimeMillis(), explicit)
            ? stop.optString("accelerator") : null;
        TaiLoadBudget.History history = (accelerator, window) -> TaiRuntimeHistory.measuredLoadBytes(context, e4b,
            device, TaiModelSpec.BACKEND_LITERT_LM, accelerator, window);
        return TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, E4B, false, PONG_TOTAL,
            available, Arrays.asList("gpu", "cpu"), 2048, crashed, crashedContext, false, history,
            Collections.<TaiResidency.Entry>emptyList()).withHeldBack(heldBack));
    }

    private static TaiRuntimeOptions gpuAt2048() {
        return new TaiRuntimeOptions(null, null, null, null, "gpu", 2048, null, null, null);
    }

    private static TaiModelSpec chatSpec(String id) {
        return new TaiModelSpec(
            id,
            id,
            "Test model",
            "test",
            "/models/" + id + "/model.litertlm",
            "test",
            E4B,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM,
            null,
            null,
            4096,
            0,
            null
        );
    }
}
