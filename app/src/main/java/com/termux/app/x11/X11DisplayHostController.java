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
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
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
    /** Where the text-focus policy's decisions go, so a device run can be read from logcat. */
    private static final String TEXT_FOCUS_LOG_TAG = "X11TextFocus";
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

    /** Whether the keyboard should follow the text fields inside the X session, and when. */
    @NonNull private final DisplayTextFocusPolicy textFocus;
    /** The launcher's end of the policy, handed in by the activity that owns the keyboard. */
    @Nullable private DisplayTextFocusPolicy.Keyboard keyboard;
    /** The tap window's pending decision, so it can be dropped. */
    @Nullable private Runnable tapWindow;

    @Nullable private LorieView view;
    @Nullable private Listener listener;
    /** The announcement, kept so a page that attaches later can still reach the server. */
    @Nullable private Bundle announcement;
    private boolean running;
    private boolean destroyed;

    private final Runnable connectRetry = this::tryConnect;

    /** A preference changed — from the settings page or {@code termux-x11-preference}. */
    private final android.content.BroadcastReceiver preferencesReceiver =
        new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context context, android.content.Intent intent) {
                if (!destroyed) reloadPreferences();
            }
        };
    @NonNull private final Context appContext;

    public X11DisplayHostController(@NonNull Context context,
                                    @NonNull LorieHost.Callbacks callbacks) {
        this.appContext = context.getApplicationContext();
        this.host = new LorieHost(appContext, callbacks);
        this.textFocus = new DisplayTextFocusPolicy(
            new DisplayTextFocusPolicy.Keyboard() {
                @Override public void showKeyboardForTextFocus() {
                    if (keyboard != null) keyboard.showKeyboardForTextFocus();
                }
                @Override public void hideKeyboardForTextFocus() {
                    if (keyboard != null) keyboard.hideKeyboardForTextFocus();
                }
                @Override public boolean isKeyboardUp() {
                    return keyboard != null && keyboard.isKeyboardUp();
                }
            },
            new DisplayTextFocusPolicy.Scheduler() {
                @Override public void schedule(long delayMs, @NonNull Runnable action) {
                    tapWindow = action;
                    handler.postDelayed(action, delayMs);
                }
                @Override public void cancel() {
                    if (tapWindow != null) handler.removeCallbacks(tapWindow);
                    tapWindow = null;
                }
            },
            message -> android.util.Log.d(TEXT_FOCUS_LOG_TAG, message));
        // The broadcast is sent to this package only, and the app targets sdk 28, so the plain
        // registration is right below Android 13; from 13 on the flag says the same thing.
        android.content.IntentFilter filter = new android.content.IntentFilter(
            com.termux.x11.LoriePreferences.ACTION_PREFERENCES_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(preferencesReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(preferencesReceiver, filter);
        }
        X11DisplayReceiver.register(this);
        // A server that announced itself while no activity was up — or to the activity this one
        // replaces — is taken straight away; the view connects to it when the page attaches.
        Bundle kept = X11DisplayReceiver.takeAnnouncement();
        if (kept != null) onServerAnnounced(kept);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** True while a display server is up — announced and alive — attached to the page or not. */
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
        // The server repeats the current cursor name when a host attaches, so the policy starts
        // with the name the pointer is actually showing rather than with nothing.
        view.setCursorNameListener(name -> handler.post(() -> textFocus.onCursorName(name)));
        // Re-announce to ourselves: a server that came up while the page was elsewhere already
        // handed us its Binder, and this is the point at which it can be used.
        if (announcement != null) connect(announcement);
        else tryConnect();
    }

    /** The page has gone: drop the view but leave the server and its clients alone. */
    public void detachView() {
        handler.removeCallbacks(connectRetry);
        LorieView attached = view;
        if (attached != null) attached.setCursorNameListener(null);
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
        try {
            appContext.unregisterReceiver(preferencesReceiver);
        } catch (IllegalArgumentException ignored) {
            // Never registered, or already gone with the process.
        }
        X11DisplayReceiver.unregister(this);
        LorieView attached = view;
        if (attached != null) attached.setCursorNameListener(null);
        textFocus.onPlaceLeft();
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
        // A live Binder is a running display, whether or not a page is attached to it yet: the
        // place switch's dot and the stop control must not wait for the page to be looked at.
        setRunning(true);
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

    /**
     * Re-read the display preferences into the live view: filtering and the clipboard on the view,
     * the touch mode on the input handler, and a fresh layout pass so a new resolution mode is
     * applied to the X screen.
     */
    public void reloadPreferences() {
        reloadTextFocusPreferences();
        LorieView live = view;
        if (live == null) return;
        com.termux.x11.Prefs prefs = LorieHost.getPrefs();
        live.reloadPreferences(prefs);
        if (host.mInputHandler != null) host.mInputHandler.reloadPreferences(prefs);
        live.requestLayout();
    }

    /**
     * The display the running server answers on, as {@code DISPLAY} wants it — read from the X
     * socket the server opened, {@code :0} when there is nothing better to go on.
     */
    @NonNull
    public static String displayName() {
        java.io.File dir = new java.io.File(
            com.termux.shared.termux.TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, ".X11-unix");
        String[] sockets = dir.list((d, name) -> name.startsWith("X") && name.length() > 1);
        if (sockets != null && sockets.length > 0) {
            java.util.Arrays.sort(sockets);
            return ":" + sockets[0].substring(1);
        }
        return ":0";
    }

    // ---- The keyboard that follows text fields ------------------------------------------------

    /** The launcher's end of the policy: what raises and lowers the in-app keyboard. */
    public void setTextFocusKeyboard(@Nullable DisplayTextFocusPolicy.Keyboard keyboard) {
        this.keyboard = keyboard;
    }

    /**
     * The wall rests on the Display place. {@code keyboardRaisedOnEnter} says the place asked for
     * the keyboard as it arrived, which pins it. A page that re-attaches while the wall has not
     * moved is not a fresh arrival and leaves the policy as it stands.
     */
    public void onDisplayPlaceEntered(boolean keyboardRaisedOnEnter) {
        reloadTextFocusPreferences();
        if (textFocus.isOnPlace()) return;
        textFocus.onPlaceEntered(keyboardRaisedOnEnter);
    }

    /** The wall left the Display place. */
    public void onDisplayPlaceLeft() {
        if (textFocus.isOnPlace()) textFocus.onPlaceLeft();
    }

    /** A tap landed on the display's own picture, or on mouse mode's touchpad. */
    public void onDisplayTap() {
        textFocus.onDisplayTap();
    }

    /**
     * Mouse mode's touchpad took the keyboard frame, or gave it back. While it is there its taps
     * are the display's taps whatever the touch mode, so the policy reads them.
     */
    public void setPadUp(boolean up) {
        textFocus.setPadUp(up);
    }

    /**
     * The user put the keyboard up or down themselves. Up is theirs and pins the policy off; down
     * hands the keyboard back to it.
     */
    public void onUserKeyboardIntent(boolean shown) {
        textFocus.onUserKeyboardIntent(shown);
    }

    /**
     * An input method on the Linux side says a text field took focus, or lost it — signal B, from
     * {@code keyboard.show --source focus}. False when the policy is inert and the caller should
     * do the plain thing itself.
     */
    public boolean onTextFocusSignal(boolean focused) {
        return textFocus.onTextFocusSignal(focused);
    }

    /** For tests and for anyone reading the state in a debugger. */
    @NonNull
    public DisplayTextFocusPolicy textFocusPolicy() {
        return textFocus;
    }

    /** The touch mode and the setting, both of which can move while the place is on screen. */
    private void reloadTextFocusPreferences() {
        com.termux.x11.Prefs prefs = LorieHost.getPrefs();
        if (prefs != null) {
            try {
                textFocus.setTouchMode(Integer.parseInt(prefs.touchMode.get()));
            } catch (NumberFormatException ignored) {
                // An unreadable mode is not Touchscreen, so the policy sleeps.
                textFocus.setTouchMode(0);
            }
        }
        TermuxAppSharedPreferences launcher = TermuxAppSharedPreferences.build(appContext);
        textFocus.setEnabled(launcher == null || launcher.isX11KeyboardFollowsTextEnabled());
    }

    /** Hardware keys the page routes into X; true when X took the key. */
    public boolean sendKeyEvent(@NonNull KeyEvent event) {
        LorieView live = view;
        return live != null && live.dispatchKeyEvent(event);
    }
}
