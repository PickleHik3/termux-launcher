package com.termux.app.x11;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

/**
 * How a display server announces itself. {@code termux-x11 :0} starts the server in its own
 * process, which broadcasts a Binder the moment an X client knocks on the port; this hands it to
 * the {@link X11DisplayHostController} that owns the Display page.
 *
 * <p>Declared in the manifest, never exported, and guarded by a signature-level permission. The
 * server process runs under the launcher's own uid and signature, so it holds that permission; a
 * foreign app cannot hand the home screen a Binder.
 *
 * <p>The controller lives and dies with the activity; the server does not. An announcement that
 * arrives between two activities — during a rotation, say — is kept here, in the process, so the
 * next controller starts with the Binder instead of waiting for the server to knock again.
 */
public final class X11DisplayReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "X11DisplayReceiver";

    /** Where the running controller registers itself, so a receiver instance can find it. */
    @Nullable private static X11DisplayHostController controller;
    /** The last announcement nobody was there to take. */
    @Nullable private static Bundle pendingAnnouncement;

    /** The controller that owns the page now. */
    static void register(@NonNull X11DisplayHostController value) {
        controller = value;
    }

    /**
     * The controller is going away. Only its own registration is cleared: a successor may already
     * have registered, and an activity being destroyed must not take the new one's place away.
     */
    static void unregister(@NonNull X11DisplayHostController value) {
        if (controller == value) controller = null;
    }

    /** Keep an announcement for the next controller; null forgets it. */
    static void keepAnnouncement(@Nullable Bundle announcement) {
        pendingAnnouncement = announcement;
    }

    /** Hand over the kept announcement, if any, and forget it. */
    @Nullable
    static Bundle takeAnnouncement() {
        Bundle kept = pendingAnnouncement;
        pendingAnnouncement = null;
        return kept;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !com.termux.x11.CmdEntryPoint.ACTION_START.equals(intent.getAction())) {
            return;
        }
        Bundle bundle = intent.getBundleExtra(null);
        if (bundle == null || bundle.getBinder(null) == null) return;
        X11DisplayHostController host = controller;
        if (host == null) {
            // The server is up and no page owns it yet. Kept for whoever comes next; the server
            // keeps knocking every second anyway, so nothing is lost if this is stale by then.
            Logger.logVerbose(LOG_TAG, "A display server announced itself with no page attached");
            pendingAnnouncement = bundle;
            return;
        }
        host.onServerAnnounced(bundle);
    }

    /** The permission this receiver is behind, as the manifest declares it. */
    @NonNull
    public static String permission(@NonNull Context context) {
        return context.getPackageName() + ".permission.X11_DISPLAY";
    }
}
