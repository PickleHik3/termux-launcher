package com.termux.privileged;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Shizuku announces a binder that starts after the launcher only to the listeners registered when
 * it does. A backend that finds no Shizuku must keep them, or the launcher notices a later Shizuku
 * only when some screen asks again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ShizukuBackendStandbyTest {

    @Test
    public void standingDownKeepsListeningForALaterBinder() {
        ShizukuBackend backend = new ShizukuBackend();
        backend.registerListeners();

        backend.standDown();

        assertTrue(backend.areListenersRegistered());
        assertFalse(backend.isAvailable());
        assertFalse(backend.hasPermission());
    }

    @Test
    public void cleanupStillLetsGo() {
        ShizukuBackend backend = new ShizukuBackend();
        backend.registerListeners();

        backend.cleanup();

        assertFalse(backend.areListenersRegistered());
    }
}
