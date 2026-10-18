package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.termux.x11.CmdEntryPoint;
import com.termux.x11.LorieHost;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The server's announcement, and who gets it. A server outlives the activity, so a Binder that
 * arrives while no controller is registered is kept for the next one, and a controller hands
 * its Binder back when its activity goes — a rotation must not cost the page its server.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11DisplayReceiverTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @After public void clearProcessState() {
        X11DisplayReceiver.takeAnnouncement();
        X11DisplayServerState.resetForTest();
    }

    private static Intent announcement(@androidx.annotation.Nullable Binder binder) {
        Intent intent = new Intent(CmdEntryPoint.ACTION_START);
        Bundle bundle = new Bundle();
        if (binder != null) bundle.putBinder(null, binder);
        intent.putExtra(null, bundle);
        return intent;
    }

    @Test public void anIntentWithoutABinderIsIgnored() {
        new X11DisplayReceiver().onReceive(context, announcement(null));
        new X11DisplayReceiver().onReceive(context, new Intent(CmdEntryPoint.ACTION_START));
        new X11DisplayReceiver().onReceive(context, null);

        assertNull("nothing to hand on", X11DisplayReceiver.takeAnnouncement());
    }

    @Test public void aForeignActionIsIgnored() {
        Intent intent = announcement(new Binder());
        intent.setAction("com.example.SOMETHING_ELSE");

        new X11DisplayReceiver().onReceive(context, intent);

        assertNull(X11DisplayReceiver.takeAnnouncement());
    }

    @Test public void anAnnouncementWithNoControllerIsKeptOnce() {
        Binder server = new Binder();

        new X11DisplayReceiver().onReceive(context, announcement(server));

        Bundle kept = X11DisplayReceiver.takeAnnouncement();
        assertNotNull(kept);
        assertSame(server, kept.getBinder(null));
        assertNull("taken is taken", X11DisplayReceiver.takeAnnouncement());
    }

    @Test public void aNewControllerTakesTheKeptAnnouncementAndHandsItBackOnDestroy() {
        Binder server = new Binder();
        new X11DisplayReceiver().onReceive(context, announcement(server));

        X11DisplayHostController controller =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        assertNull("the controller took it", X11DisplayReceiver.takeAnnouncement());

        // The activity goes (a rotation, say); the server does not.
        controller.destroy();
        Bundle kept = X11DisplayReceiver.takeAnnouncement();
        assertNotNull("left for the next controller", kept);
        assertSame(server, kept.getBinder(null));
    }

    @Test public void aRelaunchedActivitysControllerIsOnTheSameRunningServer() {
        X11DisplayHostController first =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        new X11DisplayReceiver().onReceive(context, announcement(new Binder()));
        assertTrue("the server is up and announced", first.isRunning());
        java.util.List<Boolean> heard = new java.util.ArrayList<>();
        first.setListener(heard::add);

        // TermuxActivity.onDestroy on a configuration relaunch (new wallpaper colours): the
        // listener goes first, so the dying activity never acts on a "stopped" display.
        first.setListener(null);
        first.destroy();
        assertTrue("nothing told the old activity the display stopped", heard.isEmpty());

        X11DisplayHostController second =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        assertTrue("the new activity's controller is on the server that kept running",
            second.isRunning());
        second.destroy();
    }

    @Test public void aRelaunchedActivitysListenerHearsTheDisplayIsRunning() {
        X11DisplayHostController first =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        new X11DisplayReceiver().onReceive(context, announcement(new Binder()));
        first.setListener(null);
        first.destroy();

        // The new activity's controller took the kept announcement in its constructor, before
        // the activity could listen: the page must still hear that the display runs, or it stays
        // on its empty state with Firefox hidden behind it.
        X11DisplayHostController second =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        java.util.List<Boolean> heard = new java.util.ArrayList<>();
        second.setListener(heard::add);
        assertTrue("posted, so the activity finishes building first", heard.isEmpty());
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(java.util.Collections.singletonList(true), heard);
        second.setListener(null);
        second.destroy();
    }

    @Test public void aListenerWithNoServerHearsNothing() {
        // A display that was stopped before the relaunch is not brought back to life.
        X11DisplayHostController controller =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        java.util.List<Boolean> heard = new java.util.ArrayList<>();
        controller.setListener(heard::add);
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(controller.isRunning());
        assertTrue(heard.isEmpty());
        controller.destroy();
    }

    @Test public void aListenerReplacedBeforeTheReplayHearsNothing() {
        X11DisplayHostController controller =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        new X11DisplayReceiver().onReceive(context, announcement(new Binder()));
        java.util.List<Boolean> gone = new java.util.ArrayList<>();
        java.util.List<Boolean> current = new java.util.ArrayList<>();
        controller.setListener(gone::add);
        controller.setListener(current::add);
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue("the replaced listener is not called", gone.isEmpty());
        assertEquals(java.util.Collections.singletonList(true), current);

        // A controller let go of before its replay runs tells nobody anything.
        java.util.List<Boolean> late = new java.util.ArrayList<>();
        controller.setListener(late::add);
        controller.setListener(null);
        controller.destroy();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(late.isEmpty());
    }

    @Test public void aDestroyedControllerNoLongerReceives() {
        X11DisplayHostController first =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        X11DisplayHostController second =
            new X11DisplayHostController(context, new LorieHost.Callbacks() { });
        // The old activity is destroyed after the new one registered: its unregistration must
        // not take the new controller's place away.
        first.destroy();

        Binder server = new Binder();
        new X11DisplayReceiver().onReceive(context, announcement(server));

        assertNull("the second controller, not the holder, got it",
            X11DisplayReceiver.takeAnnouncement());
        second.destroy();
        Bundle kept = X11DisplayReceiver.takeAnnouncement();
        assertNotNull(kept);
        assertSame(server, kept.getBinder(null));
    }

    @Test public void theReceiverIsNotExportedAndSitsBehindTheSignaturePermission()
            throws PackageManager.NameNotFoundException {
        ActivityInfo info = context.getPackageManager().getReceiverInfo(
            new ComponentName(context, X11DisplayReceiver.class), 0);

        assertFalse("a foreign app must not be able to hand the home screen a Binder",
            info.exported);
        assertEquals(X11DisplayReceiver.permission(context), info.permission);
    }
}
