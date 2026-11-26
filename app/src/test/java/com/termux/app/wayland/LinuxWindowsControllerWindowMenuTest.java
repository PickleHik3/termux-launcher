package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.os.Binder;
import android.os.Build;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * An app asking for its window menu opens the window's controls as if its mark were held (the rim
 * tab that used to open a menu is gone), and the Scale control brings out the rail only for a
 * window that takes a scale of its own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxWindowsControllerWindowMenuTest {

    private final Application app = ApplicationProvider.getApplicationContext();
    private final List<Long> controlsAsked = new ArrayList<>();
    private LinuxWindowsController controller;
    private ILinuxWindowsCallback callback;

    private static Object zero(Class<?> type) {
        return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
    }

    @Before
    public void attach() {
        Binder binder = new Binder();
        ILinuxWindows windows = (ILinuxWindows) Proxy.newProxyInstance(
            ILinuxWindows.class.getClassLoader(), new Class<?>[] {ILinuxWindows.class},
            (proxy, method, args) -> {
                if (method.getName().equals("addCallback")) callback = (ILinuxWindowsCallback) args[0];
                if (method.getName().equals("asBinder")) return binder;
                return zero(method.getReturnType());
            });
        binder.attachInterface(windows, ILinuxWindows.DESCRIPTOR);
        shadowOf(app).setComponentNameAndServiceForBindService(
            new ComponentName(app, LinuxWindowsService.class), binder);
        LinuxWindowsController.Host host = (LinuxWindowsController.Host) Proxy.newProxyInstance(
            LinuxWindowsController.Host.class.getClassLoader(),
            new Class<?>[] {LinuxWindowsController.Host.class}, (proxy, method, args) -> {
                if (method.getName().equals("showWindowControls")) controlsAsked.add((Long) args[0]);
                if (method.getName().equals("iconLookup")) return LinuxWindowIcons.Lookup.NONE;
                return zero(method.getReturnType());
            });
        controller = new LinuxWindowsController(app, host);
        controller.start();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(callback);
    }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void theWindowInFrontGetsItsControlsWhenItsAppAsksForItsMenu() throws Exception {
        callback.onWindowCreated(8L, "Demo", "gtk3-demo", 1234, 0, 0L);
        idle();
        controller.bind(8L);
        controller.setInFront(true);
        callback.onWindowMenuRequest(8L);
        idle();
        assertEquals(1, controlsAsked.size());
        assertEquals(8L, (long) controlsAsked.get(0));
    }

    @Test
    public void aWindowBehindTheFrontOneIsNotAskedAboutAndAnUnknownOneNeither() throws Exception {
        callback.onWindowCreated(8L, "Demo", "gtk3-demo", 1234, 0, 0L);
        callback.onWindowCreated(9L, "Other", "gtk3-demo", 1234, 0, 0L);
        idle();
        controller.bind(8L);
        controller.setInFront(true);
        callback.onWindowMenuRequest(9L);
        callback.onWindowMenuRequest(77L);
        idle();
        assertTrue("controls: " + controlsAsked, controlsAsked.isEmpty());
    }

    @Test
    public void scaleBringsOutTheRailOnlyForAWindowThatTakesAScaleOfItsOwn() throws Exception {
        callback.onWindowCreated(8L, "Demo", "gtk3-demo", 1234, 0, 0L);
        callback.onWindowCreated(9L, "xterm", "xterm", 1234, ILinuxWindows.WINDOW_X11, 0L);
        idle();
        assertTrue(controller.windowTakesScale(8L));
        assertFalse("an X window shares the server's scale", controller.windowTakesScale(9L));
        assertFalse(controller.windowTakesScale(77L));
        controller.bind(8L);
        controller.setInFront(true);
        assertTrue(controller.showScaleRail(8L));
        assertFalse("no rail for an X window", controller.showScaleRail(9L));
        assertFalse("none for a window that is gone", controller.showScaleRail(77L));
    }
}
