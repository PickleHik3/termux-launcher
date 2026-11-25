package com.termux.privileged.lane;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;

/**
 * A bind that never calls back must not leave the lane "binding" for good: the one timer gives it
 * up, and a dead or empty binding is released the same way.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class PrivilegedLaneBindingTest {

    private static PrivilegedLaneBinding fresh() throws Exception {
        Constructor<PrivilegedLaneBinding> ctor = PrivilegedLaneBinding.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void advance(long ms) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS);
    }

    @Test
    public void aBindThatNeverConnectsIsGivenUpAfterTheTimeout() throws Exception {
        PrivilegedLaneBinding binding = fresh();
        assertNotNull(binding.beginAttempt());
        assertTrue(binding.isBinding());
        assertNull("one attempt at a time", binding.beginAttempt());

        advance(PrivilegedLaneBinding.BIND_TIMEOUT_MS - 1);
        assertTrue(binding.isBinding());
        advance(1);
        assertFalse(binding.isBinding());

        assertNotNull("the next attempt may bind afresh", binding.beginAttempt());
    }

    private static java.util.List<Boolean> record(PrivilegedLaneBinding binding) {
        java.util.List<Boolean> removes = new java.util.ArrayList<>();
        binding.unbinder = (c, remove) -> removes.add(remove);
        return removes;
    }

    @Test
    public void onlyTheTimeoutStopsTheServiceProcess() throws Exception {
        PrivilegedLaneBinding binding = fresh();
        java.util.List<Boolean> removes = record(binding);
        binding.beginAttempt();
        advance(PrivilegedLaneBinding.BIND_TIMEOUT_MS);
        assertEquals(java.util.Collections.singletonList(true), removes);
    }

    @Test
    public void lossesAndStaleConnectionsOnlyDropOurConnection() throws Exception {
        PrivilegedLaneBinding binding = fresh();
        java.util.List<Boolean> removes = record(binding);
        ComponentName n = new ComponentName("p", "c");
        binding.beginAttempt().onBindingDied(n);
        binding.beginAttempt().onNullBinding(n);
        binding.beginAttempt().onServiceDisconnected(n);
        assertEquals(java.util.Arrays.asList(false, false, false), removes);
    }

    @Test
    public void aDeadBindingIsReleasedAtOnce() throws Exception {
        PrivilegedLaneBinding binding = fresh();
        PrivilegedLaneBinding.LaneConnection c = binding.beginAttempt();
        c.onBindingDied(new ComponentName("p", "c"));
        assertFalse(binding.isBinding());
    }

    @Test
    public void aNullBindingIsReleasedAtOnce() throws Exception {
        PrivilegedLaneBinding binding = fresh();
        PrivilegedLaneBinding.LaneConnection c = binding.beginAttempt();
        c.onNullBinding(new ComponentName("p", "c"));
        assertFalse(binding.isBinding());
    }

    @Test
    public void aLateCallFromAnAbandonedBindingIsIgnored() throws Exception {
        PrivilegedLaneBinding binding = fresh();
        PrivilegedLaneBinding.LaneConnection old = binding.beginAttempt();
        advance(PrivilegedLaneBinding.BIND_TIMEOUT_MS);
        assertNotNull(binding.beginAttempt());

        old.onBindingDied(new ComponentName("p", "c"));

        assertTrue("the newer attempt is untouched", binding.isBinding());
    }
}
