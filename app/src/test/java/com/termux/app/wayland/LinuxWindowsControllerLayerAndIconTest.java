package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.graphics.Bitmap;
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
 * What the launcher does with the two window kinds beside an app's own window: a layer surface
 * (overlay or top layer) is shown over the page but is no window of the row, has no chip; an
 * icon an app sets at run time is the window's icon until it takes it away.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxWindowsControllerLayerAndIconTest {

    private static final int LAYER_FLAGS = ILinuxWindows.WINDOW_LAYER
        | (ILinuxWindows.WINDOW_CLASS_TRANSIENT << ILinuxWindows.WINDOW_CLASS_SHIFT);

    private final Application app = ApplicationProvider.getApplicationContext();
    private final List<Long> added = new ArrayList<>();
    private final List<Long> focused = new ArrayList<>();
    private int chipRefreshes;
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
                if (method.getName().equals("setFocus")) focused.add((Long) args[0]);
                if (method.getName().equals("asBinder")) return binder;
                return zero(method.getReturnType());
            });
        binder.attachInterface(windows, ILinuxWindows.DESCRIPTOR);
        shadowOf(app).setComponentNameAndServiceForBindService(
            new ComponentName(app, LinuxWindowsService.class), binder);
        LinuxWindowsController.Host host = (LinuxWindowsController.Host) Proxy.newProxyInstance(
            LinuxWindowsController.Host.class.getClassLoader(),
            new Class<?>[] {LinuxWindowsController.Host.class}, (proxy, method, args) -> {
                if (method.getName().equals("addWindow")) added.add((Long) args[0]);
                if (method.getName().equals("chipsChanged")) chipRefreshes++;
                return zero(method.getReturnType());
            });
        controller = new LinuxWindowsController(app, host);
        controller.start();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull("the controller listens to the host", callback);
    }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void aLayerSurfaceIsNeverAWindowOfTheRowAndHasNoChip() throws Exception {
        callback.onWindowCreated(7L, "", "fuzzel", 1234, LAYER_FLAGS, 0L);
        callback.onWindowLayer(7L, ILinuxWindows.LAYER_ANCHOR_TOP, ILinuxWindows.LAYER_KEYBOARD_NONE,
            0, 0, 0, 0, 600, 400);
        idle();
        assertTrue("no place in the row", added.isEmpty());
        assertTrue("no chip", controller.chips().isEmpty());
        assertNull(controller.chip(7L));
        assertNull("no page mark", controller.iconKey(7L));
        // An app's own window still joins the row.
        callback.onWindowCreated(8L, "Demo", "gtk3-demo", 1234, 0, 0L);
        idle();
        assertEquals(1, added.size());
        assertEquals(8L, (long) added.get(0));
        assertEquals(1, controller.chips().size());
        assertEquals(8L, controller.chips().get(0).id);
        // A layer surface closing leaves the row as it was.
        callback.onWindowDestroyed(7L);
        idle();
        assertEquals(1, controller.chips().size());
    }

    @Test
    public void aLayerSurfaceThatTakesTheKeyboardGetsItAndOneThatDoesNotLeavesItAlone() throws Exception {
        callback.onWindowCreated(7L, "", "fuzzel", 1234, LAYER_FLAGS, 0L);
        callback.onWindowLayer(7L, ILinuxWindows.LAYER_ANCHOR_TOP, ILinuxWindows.LAYER_KEYBOARD_ON_DEMAND,
            0, 0, 0, 0, 600, 400);
        callback.onWindowCreated(9L, "", "mako", 1234, LAYER_FLAGS, 0L);
        callback.onWindowLayer(9L, ILinuxWindows.LAYER_ANCHOR_TOP | ILinuxWindows.LAYER_ANCHOR_RIGHT,
            ILinuxWindows.LAYER_KEYBOARD_NONE, 10, 10, 0, 0, 300, 100);
        idle();
        assertEquals(1, focused.size());
        assertEquals(7L, (long) focused.get(0));
    }

    @Test
    public void aLayerEventForAWindowThatIsNotALayerOrIsGoneChangesNothing() throws Exception {
        callback.onWindowCreated(8L, "Demo", "gtk3-demo", 1234, 0, 0L);
        callback.onWindowLayer(8L, 0, ILinuxWindows.LAYER_KEYBOARD_EXCLUSIVE, 0, 0, 0, 0, 10, 10);
        callback.onWindowLayer(99L, 0, ILinuxWindows.LAYER_KEYBOARD_EXCLUSIVE, 0, 0, 0, 0, 10, 10);
        idle();
        assertTrue(focused.isEmpty());
        assertEquals(1, controller.chips().size());
    }

    @Test
    public void anIconAnAppSetsIsTheWindowsIconUntilItTakesItAway() throws Exception {
        callback.onWindowCreated(8L, "Demo", "gtk3-demo", 1234, 0, 0L);
        idle();
        // No run-time icon and no entry icon (the host's default answers none): null.
        assertNull(controller.windowIcon(8L));
        String before = controller.iconKey(8L);
        int version = controller.iconVersion();
        int refreshes = chipRefreshes;

        int[] argb = new int[16 * 16];
        java.util.Arrays.fill(argb, 0xFFFF0000);
        callback.onWindowIcon(8L, "", 16, argb);
        idle();
        Bitmap icon = controller.windowIcon(8L);
        assertNotNull("the run-time icon", icon);
        assertNotEquals("the mark key changes with the icon", before, controller.iconKey(8L));
        assertNotEquals(version, controller.iconVersion());
        assertTrue("the window bar was told", chipRefreshes > refreshes);

        String during = controller.iconKey(8L);
        callback.onWindowIcon(8L, "", 0, new int[0]);
        idle();
        assertNull("back to the entry's icon, which is none here", controller.windowIcon(8L));
        assertNotEquals(during, controller.iconKey(8L));
    }

    @Test
    public void theIconGoesWithItsWindowAndAnIconForAGoneWindowChangesNothing() throws Exception {
        callback.onWindowCreated(8L, "Demo", "gtk3-demo", 1234, 0, 0L);
        callback.onWindowIcon(8L, "", 8, new int[64]);
        idle();
        assertNotNull(controller.windowIcon(8L));
        callback.onWindowDestroyed(8L);
        idle();
        assertNull(controller.windowIcon(8L));
        assertNull(controller.iconKey(8L));
        int version = controller.iconVersion();
        callback.onWindowIcon(8L, "", 8, new int[64]);
        idle();
        assertEquals(version, controller.iconVersion());
        assertFalse(controller.chips().stream().anyMatch(c -> c.id == 8L));
    }

    @Test
    public void aShortOrOversizedIconIsIgnoredRatherThanTrusted() throws Exception {
        callback.onWindowCreated(8L, "Demo", "gtk3-demo", 1234, 0, 0L);
        idle();
        callback.onWindowIcon(8L, "", 16, new int[10]);
        callback.onWindowIcon(8L, "", 4096, new int[16]);
        idle();
        assertNull(controller.windowIcon(8L));
    }
}
