package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;

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
