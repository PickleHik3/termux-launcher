package com.termux.app.x11;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

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
 */
public final class X11DisplayReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "X11DisplayReceiver";

    /** Where the running controller registers itself, so a receiver instance can find it. */
    @Nullable private static X11DisplayHostController controller;

    public static void setController(@Nullable X11DisplayHostController value) {
        controller = value;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !com.termux.x11.CmdEntryPoint.ACTION_START.equals(intent.getAction())) {
            return;
        }
        X11DisplayHostController host = controller;
        if (host == null) {
            // The server is up and the page is not; it keeps knocking every second, so the next
            // broadcast after the page attaches is the one that lands.
            Logger.logVerbose(LOG_TAG, "A display server announced itself with no page attached");
            return;
        }
        host.onServerAnnounced(intent);
    }

    /** The permission this receiver is behind, as the manifest declares it. */
    @NonNull
    public static String permission(@NonNull Context context) {
        return context.getPackageName() + ".permission.X11_DISPLAY";
    }
}
