package com.termux.ai;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.ExecutorService;

/** The thermal listener's thread lives only between start and stop, so a run leaves none behind. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TaiDeviceConditionsTest {

    @Test
    public void aReaderThatOnlyTakesSnapshotsOwnsNoThread() {
        Context context = ApplicationProvider.getApplicationContext();
        TaiDeviceConditions conditions = new TaiDeviceConditions(context);

        conditions.snapshot();

        assertNull(conditions.listenerExecutorForTest());
    }

    @Test
    public void stoppingTheListenerShutsItsExecutorDownAndAnotherRunGetsAFreshOne() {
        Context context = ApplicationProvider.getApplicationContext();
        TaiDeviceConditions conditions = new TaiDeviceConditions(context);

        conditions.startThermalListener(() -> { });
        ExecutorService first = conditions.listenerExecutorForTest();
        assertNotNull(first);
        assertTrue(!first.isShutdown());

        conditions.stopThermalListener();
        assertTrue(first.isShutdown());
        assertNull(conditions.listenerExecutorForTest());
        // Safe to call again.
        conditions.stopThermalListener();

        conditions.startThermalListener(() -> { });
        ExecutorService second = conditions.listenerExecutorForTest();
        assertNotNull(second);
        assertTrue(first != second && !second.isShutdown());
        conditions.stopThermalListener();
        assertTrue(second.isShutdown());
    }
}
