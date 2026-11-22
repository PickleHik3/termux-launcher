package com.termux.ai;

import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivityManager;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import android.app.Application;

/** A low-memory kill of the runtime process is reported as one; anything else keeps today's wording. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class TaiRuntimeExitReasonTest {
    private final Context context = ApplicationProvider.getApplicationContext();
    private final String runtimeProcess = context.getPackageName() + TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX;

    private void exited(String process, int reason) {
        ShadowActivityManager shadow = Shadows.shadowOf(
            (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE));
        shadow.addApplicationExitInfo(process, 1234, reason, 0);
    }

    @Test
    public void lowMemoryRecord_isNamed() {
        exited(runtimeProcess, ApplicationExitInfo.REASON_LOW_MEMORY);
        assertEquals("low_memory", TaiRuntimeExitReason.lookup(context, 0L));
    }

    @Test
    public void crashRecord_isNamed() {
        exited(runtimeProcess, ApplicationExitInfo.REASON_CRASH_NATIVE);
        assertEquals("crash", TaiRuntimeExitReason.lookup(context, 0L));
    }

    @Test
    public void noRecord_isNull() {
        assertNull(TaiRuntimeExitReason.lookup(context, 0L));
    }

    @Test
    public void recordOlderThanSince_isIgnored() {
        exited(runtimeProcess, ApplicationExitInfo.REASON_LOW_MEMORY);
        assertNull(TaiRuntimeExitReason.lookup(context, System.currentTimeMillis() + 60_000L));
    }

    @Test
    public void otherProcess_isIgnored() {
        exited(context.getPackageName(), ApplicationExitInfo.REASON_LOW_MEMORY);
        assertNull(TaiRuntimeExitReason.lookup(context, 0L));
    }

    @Test
    public void unnamedReason_isNull() {
        exited(runtimeProcess, ApplicationExitInfo.REASON_USER_REQUESTED);
        assertNull(TaiRuntimeExitReason.lookup(context, 0L));
    }

    @Test
    public void clientDeath_withLowMemoryRecord_carriesReasonAndMessage() throws Exception {
        exited(runtimeProcess, ApplicationExitInfo.REASON_LOW_MEMORY);
        JSONObject error = died();
        assertEquals("tai_runtime_crashed", error.optString("error"));
        assertEquals("low_memory", error.optString("exitReason"));
        assertTrue(error.optString("message"), error.optString("message").contains("free memory"));
    }

    @Test
    public void clientDeath_withoutRecord_keepsTheOldMessage() throws Exception {
        JSONObject error = died();
        assertFalse(error.has("exitReason"));
        assertTrue(error.optString("message").startsWith("AI runtime process died while loading"));
    }

    private JSONObject died() throws Exception {
        TaiRuntimeServiceClient client = new TaiRuntimeServiceClient(context, Looper.getMainLooper());
        Method method = TaiRuntimeServiceClient.class.getDeclaredMethod("runtimeDied", String.class);
        method.setAccessible(true);
        return (JSONObject) method.invoke(client, TaiRuntimeIpc.OP_LOAD_MODEL);
    }
}
