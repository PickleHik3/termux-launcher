package com.termux.ai;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * A bind that never calls back must not leave the client waiting on the same dead attempt: the
 * attempt is given up when its wait ends, and the next call binds afresh.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TaiRuntimeServiceClientBindTimeoutTest {

    /** Accepts every bind and never calls back. */
    private static final class SilentContext extends ContextWrapper {
        int binds;
        int unbinds;

        SilentContext(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public boolean bindService(Intent service, ServiceConnection conn, int flags) {
            binds++;
            return true;
        }

        @Override
        public void unbindService(ServiceConnection conn) {
            unbinds++;
        }
    }

    @Test
    public void aBindThatNeverConnectsIsReleasedAndTheNextCallBindsAfresh() throws Exception {
        SilentContext context = new SilentContext(ApplicationProvider.getApplicationContext());
        TaiRuntimeServiceClient client = new TaiRuntimeServiceClient(context, Looper.getMainLooper());
        Method ensureConnected = TaiRuntimeServiceClient.class.getDeclaredMethod("ensureConnected");
        ensureConnected.setAccessible(true);

        assertNull(ensureConnected.invoke(client));
        assertEquals(1, context.binds);
        assertEquals("the dead attempt is released", 1, context.unbinds);
        assertFalse((boolean) field(client, "binding"));

        assertNull(ensureConnected.invoke(client));
        assertEquals("the next call binds afresh", 2, context.binds);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
