package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The launcher hears every Linux windows host for the life of its process (#392): a host that
 * ends on its own (idle stop, then the runner unbinds) kills the controller's binding for good, and
 * the next host's windows must still reach the row.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxWindowsControllerRelinkTest {

    private final Application app = ApplicationProvider.getApplicationContext();
    private final ComponentName hostName = new ComponentName(app, LinuxWindowsService.class);
    private final List<Long> added = new ArrayList<>();

    /** One host instance: the binder it hands out and the listener the launcher registers with it. */
    private static final class FakeHost {
        final Binder binder = new Binder();
        ILinuxWindowsCallback callback;

        FakeHost() {
            ILinuxWindows windows = (ILinuxWindows) Proxy.newProxyInstance(
                ILinuxWindows.class.getClassLoader(), new Class<?>[] {ILinuxWindows.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addCallback")) callback = (ILinuxWindowsCallback) args[0];
                    if (method.getName().equals("asBinder")) return binder;
                    return zero(method.getReturnType());
                });
            binder.attachInterface(windows, ILinuxWindows.DESCRIPTOR);
        }
    }

    private static Object zero(Class<?> type) {
        return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
    }

    private LinuxWindowsController.Host recordingHost() {
        return (LinuxWindowsController.Host) Proxy.newProxyInstance(
            LinuxWindowsController.Host.class.getClassLoader(),
            new Class<?>[] {LinuxWindowsController.Host.class}, (proxy, method, args) -> {
                if (method.getName().equals("addWindow")) added.add((Long) args[0]);
                return zero(method.getReturnType());
            });
    }

    private ShadowApplication shadowApp() {
        return shadowOf(app);
    }

    @Test
    public void aHostStartedAfterTheLastOneEndedStillPutsItsWindowsInTheRow() throws Exception {
        LinuxWindowsController controller = new LinuxWindowsController(app, recordingHost());

        // The first host: the runner's launch creates it, the controller's binding hears it.
        FakeHost first = new FakeHost();
        shadowApp().setComponentNameAndServiceForBindService(hostName, first.binder);
        controller.start();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull("the controller listens to the first host", first.callback);
        List<ServiceConnection> bound = shadowApp().getBoundServiceConnections();
        assertEquals(1, bound.size());
        ServiceConnection firstBinding = bound.get(0);

        // The next host this process will meet (Robolectric hands a binding its binder at bind time).
        FakeHost second = new FakeHost();
        shadowApp().setComponentNameAndServiceForBindService(hostName, second.binder);

        // The host goes idle, stops itself and the runner unbinds: Android destroys it and ends
        // every binding to it, as ActiveServices.bringDownServiceLocked does.
        firstBinding.onServiceDisconnected(hostName);
        firstBinding.onBindingDied(hostName);
        assertTrue("the dead binding is let go",
            shadowApp().getUnboundServiceConnections().contains(firstBinding));

        // The runner starts a new host for the next app. Only a live binding hears it.
        shadowOf(Looper.getMainLooper()).idle();
        List<ServiceConnection> live = new ArrayList<>(shadowApp().getBoundServiceConnections());
        live.remove(firstBinding);
        assertFalse("the controller binds afresh after its binding died", live.isEmpty());
        assertNotNull("the controller listens to the second host", second.callback);

        // A new app's window reaches the row.
        second.callback.onWindowCreated(7L, "gtk3-demo", "gtk3-demo", 1234, 0, 0L);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Collections.singletonList(7L), added);
    }
}
