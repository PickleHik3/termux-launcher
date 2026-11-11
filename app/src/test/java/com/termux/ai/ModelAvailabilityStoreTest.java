package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** {@link TaiModelStore}'s write methods tell the real Model availability. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ModelAvailabilityStoreTest {
    private Context context;
    private TaiModelStore store;
    private ModelAvailability availability;
    private int heard;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        ModelAvailability.resetForTesting();
        TaiDownloadHub.resetForTesting();
        store = new TaiModelStore(context);
        availability = ModelAvailability.get(context);
    }

    private static JSONObject record(String status, long bytes) throws Exception {
        return new JSONObject().put("id", "download-a").put("modelId", "a").put("status", status)
            .put("bytesRead", bytes).put("totalBytes", 100L).put("updatedAtMs", System.currentTimeMillis());
    }

    @Test
    public void aStoreWriteTellsListenersOnTheMainThread() throws Exception {
        availability.addListener(() -> heard++);
        shadowOf(Looper.getMainLooper()).idle();
        int base = heard;

        store.upsertDownload(record(TaiModelStore.STATE_QUEUED, 0L));
        assertEquals("delivery waits for the main looper", base, heard);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("a new record is news", heard > base);

        int afterNew = heard;
        store.upsertDownload(record(TaiModelStore.STATE_FAILED, 50L));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("a status change is news", heard > afterNew);
    }

    @Test
    public void deletingAModelTellsListenersAndKeepsTheListCurrent() throws Exception {
        assertFalse(availability.isOnPhone("a"));
        availability.addListener(() -> heard++);
        shadowOf(Looper.getMainLooper()).idle();
        int base = heard;
        store.deleteUserModel("a");
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(heard > base);
        assertEquals(0, availability.models().size());
    }
}
