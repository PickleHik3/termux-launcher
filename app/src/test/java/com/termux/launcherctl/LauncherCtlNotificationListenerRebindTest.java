package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.ComponentName;
import android.os.Build;

import com.termux.app.statusbar.TopPaneFeed;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A listener the system unbinds stays off until it is asked back, so a disconnect asks for a
 * rebind; a listener being destroyed asks for nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherCtlNotificationListenerRebindTest {

    private final Consumer<ComponentName> original = LauncherCtlNotificationListener.sRebinder;

    @After
    public void tearDown() {
        LauncherCtlNotificationListener.sRebinder = original;
        TopPaneFeed.setListenerConnected(false);
    }

    @Test
    public void aDisconnectAsksTheSystemToRebind() {
        AtomicReference<ComponentName> asked = new AtomicReference<>();
        LauncherCtlNotificationListener.sRebinder = asked::set;
        ServiceController<LauncherCtlNotificationListener> service =
            Robolectric.buildService(LauncherCtlNotificationListener.class).create();

        service.get().onListenerConnected();
        service.get().onListenerDisconnected();

        ComponentName name = asked.get();
        assertNotNull(name);
        assertEquals(LauncherCtlNotificationListener.class.getName(), name.getClassName());
    }

    @Test
    public void aDestroyedInstanceAsksForNothing() {
        AtomicReference<ComponentName> asked = new AtomicReference<>();
        LauncherCtlNotificationListener.sRebinder = asked::set;
        ServiceController<LauncherCtlNotificationListener> service =
            Robolectric.buildService(LauncherCtlNotificationListener.class).create();
        LauncherCtlNotificationListener listener = service.get();

        listener.onListenerConnected();
        service.destroy();
        listener.onListenerDisconnected();

        assertNull(asked.get());
    }

    @Test
    public void aRebindThatThrowsDoesNotBreakTheDisconnect() {
        LauncherCtlNotificationListener.sRebinder = name -> {
            throw new SecurityException("not granted");
        };
        ServiceController<LauncherCtlNotificationListener> service =
            Robolectric.buildService(LauncherCtlNotificationListener.class).create();

        service.get().onListenerConnected();
        service.get().onListenerDisconnected();
    }
}
