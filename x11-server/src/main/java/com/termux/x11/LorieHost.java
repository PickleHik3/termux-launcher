package com.termux.x11;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.x11.input.TouchInputHandler;

/**
 * What upstream's {@code MainActivity} was to the display view, minus the activity.
 *
 * <p>Termux:X11 owns a fullscreen, single-instance activity and the view reaches back into it for
 * preferences, a main-thread handler, the input handler and a handful of chrome toggles. The
 * launcher has no such activity — the display is a page of the pane wall — so this is the seam
 * that stands in its place: the same names the vendored view and input classes already use, over
 * a plain Context and a {@link Callbacks} the launcher implements.
 *
 * <p>Keeping the names is deliberate. It is what makes the vendored files diffable against
 * upstream (see {@code x11-server/UPSTREAM.md}), so a nightly merge stays a merge.
 */
public class LorieHost extends ContextWrapper {

    /** The launcher side of the chrome the display view expects a host to own. */
    public interface Callbacks {
        /** The view asks for the keyboard, or asks to put it away. */
        default void toggleKeyboardVisibility() { }
        /** A hardware key arrived on the display surface; true when the host consumed it. */
        default boolean handleKey(@NonNull KeyEvent event) { return false; }
        /** Pointer capture was turned on or off. */
        default void setCapturingEnabled(boolean enabled) { }
        /** A hardware keyboard came or went. */
        default void setExternalKeyboardConnected(boolean connected) { }
        /** The server is gone and the page should fall back to its empty state. */
        default void onDisplayStopped() { }
    }

    /**
     * What the notification's own actions broadcast back, as upstream's activity declared it. The
     * launcher's receiver answers it in the same process.
     */
    public static final String ACTION_CUSTOM = "com.termux.x11.ACTION_CUSTOM";
    /** Stop the running server. */
    public static final String ACTION_STOP = "com.termux.x11.ACTION_STOP";

    /** The main-thread handler upstream exposes as {@code MainActivity.handler}. */
    public static final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable private static LorieHost instance;
    /** Upstream's {@code MainActivity.prefs}; live for as long as a host exists. */
    @Nullable public static Prefs prefs;

    @NonNull private final Callbacks callbacks;
    @Nullable private LorieView view;
    /** Upstream's {@code MainActivity.mInputHandler}. */
    @Nullable public TouchInputHandler mInputHandler;

    public LorieHost(@NonNull Context context, @NonNull Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        prefs = new Prefs(context.getApplicationContext());
        instance = this;
    }

    /** Drop the host: the page has gone away and nothing may reach a stale view through it. */
    public void release() {
        if (instance == this) {
            instance = null;
            prefs = null;
        }
        mInputHandler = null;
        view = null;
    }

    @Nullable
    public static LorieHost getInstance() {
        return instance;
    }

    /**
     * The host a view belongs to. Upstream walks the Context chain to its activity; here there is
     * exactly one host at a time, because there is exactly one Display page.
     */
    @Nullable
    public static LorieHost findActivity(@Nullable Context context) {
        return instance;
    }

    @NonNull
    public static Prefs getPrefs() {
        LorieHost host = instance;
        if (prefs == null && host != null) prefs = new Prefs(host.getApplicationContext());
        if (prefs == null) {
            throw new IllegalStateException("No display host: the X11 page is not attached");
        }
        return prefs;
    }

    public void setLorieView(@Nullable LorieView view) {
        this.view = view;
    }

    @Nullable
    public LorieView getLorieView() {
        return view;
    }

    /** Upstream reads the real (not application-window) metrics to size the X screen. */
    public void getRealMetrics(@NonNull DisplayMetrics metrics) {
        WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = manager == null ? null : manager.getDefaultDisplay();
        if (display != null) display.getRealMetrics(metrics);
    }

    public void toggleKeyboardVisibility() {
        callbacks.toggleKeyboardVisibility();
    }

    public boolean handleKey(@NonNull KeyEvent event) {
        return callbacks.handleKey(event);
    }

    public void setCapturingEnabled(boolean enabled) {
        callbacks.setCapturingEnabled(enabled);
    }

    public void setExternalKeyboardConnected(boolean connected) {
        callbacks.setExternalKeyboardConnected(connected);
    }

    /**
     * Upstream's activity finishes itself when the server goes; the launcher's home screen cannot
     * finish, so the page shows its empty state instead.
     */
    public void finish() {
        callbacks.onDisplayStopped();
    }
}
