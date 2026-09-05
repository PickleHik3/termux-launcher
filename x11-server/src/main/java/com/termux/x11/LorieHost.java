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

import com.termux.x11.input.InputEventSender;
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
        /** An X client connected or the last one went away. */
        default void onClientConnectedStateChanged(boolean connected) { }
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
    /** What the handler and hardware keys send through; it writes into the view's socket. */
    @Nullable private InputEventSender mInjector;

    public LorieHost(@NonNull Context context, @NonNull Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        prefs = new Prefs(context.getApplicationContext());
        instance = this;
    }

    /** Drop the host: the page has gone away and nothing may reach a stale view through it. */
    public void release() {
        if (instance == this) instance = null;
        // The preferences stay: the page's view outlives its host (the wall keeps the Display
        // place whether or not a display can run there) and measures itself through them.
        mInputHandler = null;
        mInjector = null;
        view = null;
    }

    /**
     * Make the preferences readable before any host exists. The launcher's Display page is on
     * the wall — and so measured, which reads the display resolution preference — while the
     * display is switched off and no host has been built; upstream's view could never be in
     * that position, because its activity is the host.
     */
    public static void primePrefs(@NonNull Context context) {
        if (prefs == null) prefs = new Prefs(context.getApplicationContext());
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
        if (view == null) return;
        // A page can be inflated before its host exists, so the view's own lookup may have come
        // back empty; hand it the host here rather than relying on construction order.
        view.activity = this;
        // What upstream's MainActivity.onCreate wired: every pointer event on the view goes
        // through the touch handler, which turns it into X input by the configured touch mode.
        // Without this the display draws but nothing on it can be touched.
        mInjector = new InputEventSender(this, view);
        TouchInputHandler handler = new TouchInputHandler(this, mInjector);
        mInputHandler = handler;
        view.setOnTouchListener((v, e) -> handler.handleTouchEvent(view, view, e));
        view.setOnHoverListener((v, e) -> handler.handleTouchEvent(view, view, e));
        view.setOnGenericMotionListener((v, e) -> handler.handleTouchEvent(view, view, e));
        view.setOnCapturedPointerListener((v, e) -> handler.handleTouchEvent(view, view, e));
        if (prefs != null) handler.reloadPreferences(prefs);
        // The handler maps view pixels onto the X screen with the transform the view reports as
        // its viewport changes; without it every touch is scaled by zero and lands at the origin.
        view.setCallback(handler::handleInputTransformChanged);
    }

    @Nullable
    public LorieView getLorieView() {
        return view;
    }

    /**
     * The X server has gained or lost its first client. Called from the server thread over JNI —
     * the name and signature are part of that contract (see {@code ci/x11-patch/}) — so it hands
     * over to the main thread before anything looks at a view.
     */
    @androidx.annotation.Keep
    public void clientConnectedStateChanged() {
        handler.post(() -> callbacks.onClientConnectedStateChanged(
            view != null && view.connected()));
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

    /**
     * A hardware key on the display's view: X gets it, the way upstream's activity handed it to
     * its input handler; the launcher's own chords were already taken above this view.
     */
    public boolean handleKey(@NonNull KeyEvent event) {
        if (callbacks.handleKey(event)) return true;
        InputEventSender injector = mInjector;
        return injector != null && injector.sendKeyEvent(event);
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
