package com.termux.app.x11;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.x11.ICmdEntryInterface;
import com.termux.x11.LorieHost;
import com.termux.x11.LorieView;

/**
 * Owns the connection to a running display server: the Binder it announced itself with, the X
 * socket handed to the view, its logcat pipe, and the fact of whether it is running at all.
 *
 * <p>The server is a separate process on purpose — it is what power users expect from
 * {@code termux-x11}, and it keeps an X server crash away from the home screen. Nothing here
 * starts one: a display exists because someone typed {@code termux-x11 :0}, or because the user
 * turned on the opt-in that runs that command at start-up.
 *
 * <p>One controller exists per activity and is {@link #destroy() destroyed} with it. The server
 * outlives both; its announcement is handed across through {@link X11DisplayReceiver}, so the
 * next activity's controller starts with the Binder rather than waiting for the server to knock.
 */
public final class X11DisplayHostController {

    private static final String LOG_TAG = "X11DisplayHost";
    /** How long to wait before asking the server for its socket again. */
    private static final long CONNECT_RETRY_MS = 250L;

    /** What the Display page and the status-bar tile want to know. */
    public interface Listener {
        /** A server came up, or the one that was there has gone. */
        void onDisplayRunningChanged(boolean running);
    }

    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());
    @NonNull private final LorieHost host;
    /** The server's Binder and the one death link on it. */
    @NonNull private final X11ServerLink link = new X11ServerLink();

    @Nullable private LorieView view;
    @Nullable private Listener listener;
    /** The announcement, kept so a page that attaches later can still reach the server. */
    @Nullable private Bundle announcement;
    private boolean running;
    private boolean destroyed;

    private final Runnable connectRetry = this::tryConnect;

    public X11DisplayHostController(@NonNull Context context,
                                    @NonNull LorieHost.Callbacks callbacks) {
        this.host = new LorieHost(context.getApplicationContext(), callbacks);
        X11DisplayReceiver.register(this);
        // A server that announced itself while no activity was up — or to the activity this one
        // replaces — is taken straight away; the view connects to it when the page attaches.
        Bundle kept = X11DisplayReceiver.takeAnnouncement();
        if (kept != null) onServerAnnounced(kept);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** True while a display server is up and its socket is connected to the view. */
    public boolean isRunning() {
        return running;
    }

    @NonNull
    public LorieHost host() {
        return host;
    }

    /**
     * The page's surface is available: take the view and connect it. Called every time the wall
     * settles on the Display page, because a hidden page detaches its surface.
     */
    public void attachView(@NonNull LorieView view) {
        this.view = view;
        host.setLorieView(view);
        // Re-announce to ourselves: a server that came up while the page was elsewhere already
        // handed us its Binder, and this is the point at which it can be used.
        if (announcement != null) connect(announcement);
        else tryConnect();
    }

    /** The page has gone: drop the view but leave the server and its clients alone. */
    public void detachView() {
        handler.removeCallbacks(connectRetry);
        // The surface itself goes with the page (SurfaceView tears it down on its own); the X
        // socket stays open, so the server and its clients never notice the page went away.
        view = null;
        host.setLorieView(null);
    }

    /**
     * Let go of everything. The server keeps running; it is not ours to stop — and its
     * announcement is left with the receiver for the controller that comes after this one.
     */
    public void destroy() {
        destroyed = true;
        handler.removeCallbacks(connectRetry);
        X11DisplayReceiver.unregister(this);
        if (announcement != null && link.isLinked()) X11DisplayReceiver.keepAnnouncement(announcement);
        link.release();
        announcement = null;
        view = null;
        host.release();
        setRunning(false);
    }

    // ---- The server's announcement ----------------------------------------------------------

    /** Called by {@link X11DisplayReceiver} for every {@code ACTION_START} broadcast. */
    void onServerAnnounced(@NonNull Bundle bundle) {
        if (destroyed || bundle.getBinder(null) == null) return;
        announcement = bundle;
        connect(bundle);
    }

    private void connect(@NonNull Bundle bundle) {
        IBinder binder = bundle.getBinder(null);
        if (binder == null) return;
        boolean known = link.holds(binder);
        if (!link.accept(binder, () -> handler.post(this::onServerDied))) {
            // Dead on arrival: a stale announcement from a server that has already gone.
            if (announcement == bundle) announcement = null;
            scheduleConnect();
            return;
        }
        if (!known) startLogcat();
        tryConnect();
    }

    /**
     * The server exited (`pkill termux-x11`, a crash, the user's own kill). The page falls back
     * to its empty state; it never sees a dead socket.
     */
    private void onServerDied() {
        if (destroyed) return;
        link.release();
        announcement = null;
        LorieView live = view;
        if (live != null) live.connect(-1);
        setRunning(false);
    }

    /**
     * The server's own log, but only for someone who has asked to see logs: taking this pipe
     * makes Android ask the user for access to all device logs, and a home screen must not put
     * that dialog in front of anyone who merely started a display.
     */
    private void startLogcat() {
        if (Logger.getLogLevel() < Logger.LOG_LEVEL_VERBOSE) return;
        ICmdEntryInterface server = link.service();
        LorieView live = view;
        if (server == null || live == null) return;
        try {
            ParcelFileDescriptor logcat = server.getLogcatOutput();
            if (logcat != null) live.startLogcat(logcat.detachFd());
        } catch (Exception e) {
            Logger.logVerbose(LOG_TAG, "No log pipe from the display server: " + e.getMessage());
        }
    }

    /**
     * Ask the server for the X socket and give it to the view. Upstream retries this on a timer
     * because the server opens its port before it is ready to hand the descriptor over.
     */
    private void tryConnect() {
        LorieView live = view;
        if (live == null) return;
        if (live.connected()) {
            handler.removeCallbacks(connectRetry);
            setRunning(true);
            return;
        }
        ICmdEntryInterface server = link.service();
        if (server == null) {
            // No announcement yet: knock on the port so a server that is already up broadcasts.
            live.requestConnection();
            scheduleConnect();
            return;
        }
        try {
            ParcelFileDescriptor fd = server.getXConnection();
            if (fd == null) {
                scheduleConnect();
                return;
            }
            live.connect(fd.detachFd());
            live.triggerCallback();
            live.reloadPreferences(LorieHost.getPrefs());
            setRunning(true);
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Failed to take the X socket: " + e.getMessage());
            link.release();
            scheduleConnect();
        }
    }

    private void scheduleConnect() {
        handler.removeCallbacks(connectRetry);
        handler.postDelayed(connectRetry, CONNECT_RETRY_MS);
    }

    private void setRunning(boolean value) {
        if (running == value) return;
        running = value;
        Listener current = listener;
        if (current != null) current.onDisplayRunningChanged(value);
    }

    /** Re-read the display preferences into a live server. */
    public void reloadPreferences() {
        LorieView live = view;
        if (live != null) live.reloadPreferences(LorieHost.getPrefs());
    }

    /** Hardware keys the page routes into X; true when X took the key. */
    public boolean sendKeyEvent(@NonNull KeyEvent event) {
        LorieView live = view;
        return live != null && live.dispatchKeyEvent(event);
    }
}
