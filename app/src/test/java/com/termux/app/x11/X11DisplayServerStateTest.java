package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.x11.CmdEntryPoint;
import com.termux.x11.LorieHost;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.List;

/**
 * New shells' {@code DISPLAY} follows the display server, not the activity. A configuration
 * relaunch (new wallpaper colours, a theme switch) destroys the activity and its controller while
 * the server and its clients keep running: the shells the new activity opens must still get
 * {@code DISPLAY}, and only a real stop takes it away. A display that was stopped is never started
 * by a relaunch.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11DisplayServerStateTest {

    private final Application app = ApplicationProvider.getApplicationContext();
    private final Context context = app;

    @Before public void freshProcess() {
        X11DisplayServerState.resetForTest();
        X11DisplayReceiver.takeAnnouncement();
    }

    @After public void clearProcessState() {
        X11DisplayServerState.resetForTest();
        X11DisplayReceiver.takeAnnouncement();
    }

    private void announce(Binder server) {
        Intent intent = new Intent(CmdEntryPoint.ACTION_START);
        Bundle bundle = new Bundle();
        bundle.putBinder(null, server);
        intent.putExtra(null, bundle);
        new X11DisplayReceiver().onReceive(context, intent);
    }

    private X11DisplayHostController newController() {
        return new X11DisplayHostController(context, new LorieHost.Callbacks() { });
    }

    /** TermuxActivity.onDestroy on a relaunch: the listener goes first, then the controller. */
    private static void destroyForRelaunch(X11DisplayHostController controller) {
        controller.setListener(null);
        controller.destroy();
    }

    private static String displayForNewSession() {
        return TermuxShellEnvironment.getDisplayForNewSessions();
    }

    @Test public void aRelaunchKeepsDisplayForNewSessionsUntilTheServerReallyStops() {
        // The opt-in is on, as the activity reads it on its way up.
        X11DisplayServerState.setDisplayEnvWanted(true);
        X11DisplayHostController first = newController();
        first.setListener(running -> { });
        announce(new Binder());
        assertEquals("a running display reaches new shells", ":0", displayForNewSession());

        destroyForRelaunch(first);
        assertEquals("the activity going is not the display stopping", ":0", displayForNewSession());

        // The new activity syncs its opt-in (onResume) before or without a controller of its own.
        X11DisplayServerState.setDisplayEnvWanted(true);
        assertEquals(":0", displayForNewSession());

        X11DisplayHostController second = newController();
        List<Boolean> heard = new ArrayList<>();
        second.setListener(heard::add);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(second.isRunning());
        assertEquals("a session opened after the relaunch gets DISPLAY", ":0",
            displayForNewSession());

        // A real stop: the server's Binder dies.
        X11DisplayServerState.onServerDied();
        assertFalse(X11DisplayServerState.isRunning());
        assertNull("a stopped display is not handed to new shells", displayForNewSession());
        destroyForRelaunch(second);
    }

    @Test public void aRelaunchWithTheDisplayStoppedGivesNoDisplayAndStartsNothing() {
        X11DisplayServerState.setDisplayEnvWanted(true);
        X11DisplayHostController first = newController();
        first.setListener(running -> { });
        shadowOf(Looper.getMainLooper()).idle();
        destroyForRelaunch(first);

        X11DisplayServerState.setDisplayEnvWanted(true);
        X11DisplayHostController second = newController();
        List<Boolean> heard = new ArrayList<>();
        second.setListener(heard::add);
        shadowOf(Looper.getMainLooper()).idle();

        assertNull("no display, no DISPLAY", displayForNewSession());
        assertFalse(X11DisplayServerState.isRunning());
        assertFalse(second.isRunning());
        assertTrue("nobody is told a display runs", heard.isEmpty());
        ShadowApplication shadow = shadowOf(app);
        assertNull("no display server was started", shadow.getNextStartedService());
        assertNull(shadow.getNextStartedActivity());
        assertTrue("nothing was asked of a server", shadow.getBroadcastIntents().isEmpty());
        destroyForRelaunch(second);
    }

    @Test public void aDisplayStoppedBeforeTheRelaunchStaysStopped() {
        X11DisplayServerState.setDisplayEnvWanted(true);
        X11DisplayHostController first = newController();
        announce(new Binder());
        X11DisplayServerState.onServerDied();
        destroyForRelaunch(first);
        // The stale announcement the old controller may have left is no new server.
        X11DisplayReceiver.takeAnnouncement();

        X11DisplayServerState.setDisplayEnvWanted(true);
        X11DisplayHostController second = newController();
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(displayForNewSession());
        assertFalse(X11DisplayServerState.isRunning());
        destroyForRelaunch(second);
    }

    @Test public void aServerAnnouncedWithNoActivityIsStillRunning() {
        X11DisplayServerState.setDisplayEnvWanted(true);
        announce(new Binder());

        assertTrue(X11DisplayServerState.isRunning());
        assertEquals(":0", displayForNewSession());
    }

    @Test public void theOptInStillDecides() {
        announce(new Binder());
        assertNull("off by default: Termux:X11 never set it", displayForNewSession());

        X11DisplayServerState.setDisplayEnvWanted(true);
        assertEquals(":0", displayForNewSession());

        X11DisplayServerState.setDisplayEnvWanted(false);
        assertNull(displayForNewSession());
    }
}
