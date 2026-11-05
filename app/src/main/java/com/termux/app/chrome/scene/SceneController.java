package com.termux.app.chrome.scene;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.DebugChoice;
import com.termux.terminal.DebugSwitch;
import com.termux.terminal.LatencyProbe;
import com.termux.view.TerminalView;

import java.util.List;

/**
 * Owns the wallpaper scene prototype's layer for one activity and feeds it the launcher's events.
 * Debug properties only (see {@link SceneFramePolicy}); with {@code debug.tl.scene} unset every
 * entry point is a field read, and no view exists.
 *
 * <p>The layer is a SurfaceView inserted into the terminal root container directly above the
 * wallpaper backdrop and below everything else the container holds (panes, chrome, widgets), sized
 * and offset by margins to the whole decor. Its surface sits behind the window; the view punches a
 * transparent hole in the window where it is, so everything drawn before it in the tree (the
 * backdrop, the root's background) is cleared there and the scene replaces the system-wallpaper
 * show-through, while everything drawn after it composites over the scene as it did over the
 * wallpaper.</p>
 */
public final class SceneController implements LatencyProbe.CommitListener {

    /** What the controller needs from the activity. */
    public interface Host {
        /** The terminal panes currently on screen. */
        @NonNull List<TerminalView> paneViews();

        /** The focused pane, or null. */
        @Nullable TerminalView focusedPane();

        boolean lazyMode();

        /** The preferences the live wallpaper choice is stored in, or null before they exist. */
        @Nullable TermuxAppSharedPreferences preferences();
    }

    private static final DebugChoice SCENE =
        new DebugChoice(SceneFramePolicy.PROP_SCENE, "off", DebugSwitch.SYSTEM_PROPERTIES);
    private static final DebugChoice RATE =
        new DebugChoice(SceneFramePolicy.PROP_RATE, "", DebugSwitch.SYSTEM_PROPERTIES);
    private static final DebugChoice GLASS =
        new DebugChoice(SceneFramePolicy.PROP_GLASS, "lowres", DebugSwitch.SYSTEM_PROPERTIES);
    private static final DebugChoice GLASS_RATE =
        new DebugChoice(SceneFramePolicy.PROP_GLASS_RATE, "", DebugSwitch.SYSTEM_PROPERTIES);

    private static final DebugChoice TRACE_INVAL =
        new DebugChoice("debug.tl.scene_trace_inval", "0", DebugSwitch.SYSTEM_PROPERTIES);

    @NonNull private final Activity mActivity;
    @NonNull private final FrameLayout mContainer;
    @Nullable private final View mBackdrop;
    @NonNull private final Host mHost;
    @NonNull private final float[] mCursor = new float[2];
    @NonNull private final int[] mLoc = new int[2];
    @Nullable private SceneLayerView mLayer;
    @Nullable private String mWindowName;
    private boolean mAttached;
    @Nullable private SceneInvalTrace mInvalTrace;
    /** The persisted choice as of the last attach or {@link #refresh}; read here, never per touch. */
    @NonNull private LiveWallpaperChoice mChoice = LiveWallpaperChoice.none();
    @Nullable private SharedPreferences mStore;
    private final SharedPreferences.OnSharedPreferenceChangeListener mChoiceListener =
        (prefs, key) -> {
            if (LiveWallpaperChoice.isChoiceKey(key)) refresh();
        };
    private boolean mPowerWatched;
    private boolean mPowerHold;
    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updatePowerHold();
        }
    };
    @Nullable private PowerManager.OnThermalStatusChangedListener mThermalListener;

    private final View.OnLayoutChangeListener mPlace =
        (v, l, t, r, b, ol, ot, or, ob) -> place();

    public SceneController(@NonNull Activity activity, @NonNull FrameLayout container,
                           @Nullable View backdrop, @NonNull Host host) {
        mActivity = activity;
        mContainer = container;
        mBackdrop = backdrop;
        mHost = host;
    }

    /** From onStart. */
    public void attach() {
        mAttached = true;
        LatencyProbe.setCommitListener(this);
        listenForChoice();
        readChoice();
        poll();
    }

    /**
     * Re-reads the persisted choice and applies it now: a different scene switches the layer, the
     * same scene takes the new knobs in place. Cheap when nothing changed. Triggered by this
     * controller's own preference listener (while attached); a caller may also call it directly.
     */
    public void refresh() {
        if (!mAttached) return;
        readChoice();
        poll();
    }

    private void readChoice() {
        TermuxAppSharedPreferences prefs = mHost.preferences();
        mChoice = prefs == null ? LiveWallpaperChoice.none() : LiveWallpaperChoice.read(prefs);
    }

    private void listenForChoice() {
        TermuxAppSharedPreferences prefs = mHost.preferences();
        SharedPreferences store = prefs == null ? null : prefs.getSharedPreferences();
        if (store == mStore) return;
        unlistenForChoice();
        if (store == null) return;
        store.registerOnSharedPreferenceChangeListener(mChoiceListener);
        mStore = store;
    }

    private void unlistenForChoice() {
        SharedPreferences store = mStore;
        mStore = null;
        if (store != null) store.unregisterOnSharedPreferenceChangeListener(mChoiceListener);
    }

    /** From onStop: the layer goes away with the screen it was drawn on. */
    public void detach() {
        mAttached = false;
        LatencyProbe.clearCommitListener(this);
        unlistenForChoice();
        removeLayer();
    }

    /** Every window touch; a touch-down also re-reads the properties (at most once a second). */
    public void onWindowTouch(@NonNull MotionEvent ev) {
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        poll();
        SceneLayerView layer = mLayer;
        if (layer == null) return;
        // Taps on the keyboard and the extra keys are typing, and scenes do not react to typing.
        if (overKeys(mActivity.getWindow().getDecorView(), ev.getRawX(), ev.getRawY())) return;
        layer.touchDown(ev.getRawX(), ev.getRawY());
    }

    /** Whether a shown keyboard or extra-keys view lies under the screen point. */
    private boolean overKeys(@NonNull View view, float x, float y) {
        if (!view.isShown()) return false;
        if (view instanceof juloo.keyboard2.Keyboard2View
                || view.getClass().getSimpleName().contains("ExtraKeys")) {
            view.getLocationOnScreen(mLoc);
            return x >= mLoc[0] && x < mLoc[0] + view.getWidth()
                && y >= mLoc[1] && y < mLoc[1] + view.getHeight();
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (overKeys(group.getChildAt(i), x, y)) return true;
            }
        }
        return false;
    }

    /** A terminal bell from {@code pane}. */
    public void onBell(@Nullable TerminalView pane) {
        SceneLayerView layer = mLayer;
        if (layer == null || pane == null || pane.getWidth() <= 0) return;
        pane.getLocationOnScreen(mLoc);
        float hw = pane.getWidth() / 2f, hh = pane.getHeight() / 2f;
        layer.bell(mLoc[0] + hw, mLoc[1] + hh, hw, hh);
    }

    /**
     * A key was committed. Scenes do not react to typing (the keyboard, the echo and the trail
     * already move with every key); instead the scene holds still while typing goes on, so it
     * costs nothing on the render thread exactly when latency matters.
     */
    @Override
    public void onCommit() {
        SceneLayerView layer = mLayer;
        if (layer != null) layer.holdForTyping();
    }

    private void poll() {
        if (!mAttached) return;
        long now = SystemClock.uptimeMillis();
        // A set debug property overrides the user's choice; without one the choice decides.
        String prop = SCENE.get(now);
        String value = SceneFramePolicy.sceneOn(prop) ? prop : mChoice.effectiveScene();
        boolean on = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && SceneFramePolicy.sceneOn(value);
        // A different scene is a different layer: the property switches it on the next poll.
        if (mLayer != null && (!on || !mLayer.kind().equals(value))) removeLayer();
        if (on && mLayer == null) installLayer(value);
        SceneLayerView layer = mLayer;
        if (layer == null) return;
        String rateProp = RATE.get(now);
        int sceneRate = rateProp == null || rateProp.isEmpty()
            ? mChoice.rateHz : SceneFramePolicy.parseRate(rateProp);
        layer.setRateHz(sceneRate);
        layer.setKnobs(mChoice.speed, mChoice.density, mChoice.palette);
        layer.setPowerHold(mPowerHold);
        layer.setGlassLowRes(SceneFramePolicy.glassLowRes(GLASS.get(now)));
        layer.setGlassRateHz(SceneFramePolicy.parseGlassRate(GLASS_RATE.get(now), sceneRate));
    }

    private void installLayer(@NonNull String kind) {
        SceneLayerView layer = new SceneLayerView(mActivity, kind, this::paneRects, mHost::lazyMode);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, 0);
        int index = mBackdrop == null ? 0 : mContainer.indexOfChild(mBackdrop) + 1;
        mLayer = layer;
        watchPower();
        layer.setPowerHold(mPowerHold);
        mContainer.addView(layer, Math.max(0, index), lp);
        mContainer.addOnLayoutChangeListener(mPlace);
        place();
        installInvalTrace(layer);
        pushWindowName(layer);
    }

    /**
     * The focused window's chip text changed (Filament spells it). Cheap and idempotent: the
     * activity calls it on every window-chip refresh, and only a different name goes further.
     */
    public void onFocusedWindow(@Nullable String label) {
        String name = FilamentLayout.clean(label);
        if (name.equals(mWindowName)) return;
        mWindowName = name;
        SceneLayerView layer = mLayer;
        if (layer != null) pushWindowName(layer);
    }

    private void pushWindowName(@NonNull SceneLayerView layer) {
        TerminalView pane = mHost.focusedPane();
        android.graphics.Typeface face =
            pane == null || pane.mRenderer == null ? null : pane.mRenderer.getTypeface();
        layer.setWindowName(mWindowName, face);
    }

    @Nullable private java.util.List<android.view.ViewGroup.LayoutParams> mInvalTraceParams;

    /** Debug builds with debug.tl.scene_trace_inval=1: wrap the content root's children in the invalidation recorder. */
    private void installInvalTrace(@NonNull SceneLayerView layer) {
        if (mInvalTrace != null || !"1".equals(TRACE_INVAL.get(SystemClock.uptimeMillis()))) return;
        if ((mActivity.getApplicationInfo().flags
            & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return;
        }
        android.view.ViewGroup content = mActivity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        // The content root can hold overlays beside the main tree: the recorder wraps them all.
        SceneInvalTrace trace = new SceneInvalTrace(mActivity, layer::drawCount);
        java.util.ArrayList<View> children = new java.util.ArrayList<>();
        java.util.ArrayList<android.view.ViewGroup.LayoutParams> params = new java.util.ArrayList<>();
        for (int i = 0; i < content.getChildCount(); i++) {
            children.add(content.getChildAt(i));
            params.add(content.getChildAt(i).getLayoutParams());
        }
        content.removeAllViews();
        for (int i = 0; i < children.size(); i++) {
            android.view.ViewGroup.LayoutParams clp = params.get(i);
            trace.addView(children.get(i), new FrameLayout.LayoutParams(clp.width, clp.height));
        }
        content.addView(trace, new FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        mInvalTrace = trace;
        mInvalTraceParams = params;
        android.util.Log.i("TLSceneInval", "recorder attached over " + children.size() + " content children");
    }

    private void removeInvalTrace() {
        SceneInvalTrace trace = mInvalTrace;
        if (trace == null) return;
        mInvalTrace = null;
        android.view.ViewGroup content = (android.view.ViewGroup) trace.getParent();
        java.util.List<android.view.ViewGroup.LayoutParams> params = mInvalTraceParams;
        mInvalTraceParams = null;
        if (content == null || params == null || trace.getChildCount() != params.size()) return;
        java.util.ArrayList<View> children = new java.util.ArrayList<>();
        for (int i = 0; i < trace.getChildCount(); i++) children.add(trace.getChildAt(i));
        trace.removeAllViews();
        content.removeView(trace);
        for (int i = 0; i < children.size(); i++) content.addView(children.get(i), params.get(i));
    }

    private void removeLayer() {
        SceneLayerView layer = mLayer;
        if (layer == null) return;
        mLayer = null;
        unwatchPower();
        mContainer.removeOnLayoutChangeListener(mPlace);
        removeInvalTrace();
        layer.destroy();
        mContainer.removeView(layer);
        LiveSceneFrame.clear();
    }

    // ---- Battery saver and heat -----------------------------------------------------------------

    /** While a layer exists: listens for battery saver and thermal changes (SceneFramePolicy.holdAtStill decides). */
    private void watchPower() {
        if (mPowerWatched || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        mPowerWatched = true;
        mActivity.registerReceiver(mPowerReceiver,
            new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        PowerManager pm = mActivity.getSystemService(PowerManager.class);
        if (pm != null) {
            mThermalListener = status -> updatePowerHold();
            pm.addThermalStatusListener(mActivity.getMainExecutor(), mThermalListener);
        }
        mPowerHold = currentPowerHold();
    }

    private void unwatchPower() {
        if (!mPowerWatched) return;
        mPowerWatched = false;
        try {
            mActivity.unregisterReceiver(mPowerReceiver);
        } catch (IllegalArgumentException ignored) {
            // Not registered: nothing to undo.
        }
        PowerManager pm = mActivity.getSystemService(PowerManager.class);
        PowerManager.OnThermalStatusChangedListener l = mThermalListener;
        mThermalListener = null;
        if (pm != null && l != null) pm.removeThermalStatusListener(l);
    }

    private boolean currentPowerHold() {
        PowerManager pm = mActivity.getSystemService(PowerManager.class);
        if (pm == null) return false;
        return SceneFramePolicy.holdAtStill(pm.isPowerSaveMode(), pm.getCurrentThermalStatus());
    }

    private void updatePowerHold() {
        mPowerHold = currentPowerHold();
        SceneLayerView layer = mLayer;
        if (layer != null) layer.setPowerHold(mPowerHold);
    }

    /** Sizes the layer to the whole decor: margins pull it out to the window's origin. */
    private void place() {
        SceneLayerView layer = mLayer;
        if (layer == null) return;
        View root = mContainer.getRootView();
        mContainer.getLocationOnScreen(mLoc);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layer.getLayoutParams();
        int w = root.getWidth(), h = root.getHeight();
        int left = -mLoc[0], top = -mLoc[1];
        if (lp.width == w && lp.height == h && lp.leftMargin == left && lp.topMargin == top) return;
        lp.width = w;
        lp.height = h;
        lp.leftMargin = left;
        lp.topMargin = top;
        layer.setLayoutParams(lp);
    }

    /** The open panes' screen rects as (cx, cy, hw, hh); read a few times a second. */
    private int paneRects(@NonNull float[] out) {
        List<TerminalView> panes = mHost.paneViews();
        int n = 0;
        for (int i = 0; i < panes.size() && n < SceneEvents.MAX_BASINS; i++) {
            TerminalView v = panes.get(i);
            if (v == null || !v.isShown() || v.getWidth() <= 0) continue;
            v.getLocationOnScreen(mLoc);
            float hw = v.getWidth() / 2f, hh = v.getHeight() / 2f;
            out[n * 4] = mLoc[0] + hw;
            out[n * 4 + 1] = mLoc[1] + hh;
            out[n * 4 + 2] = hw;
            out[n * 4 + 3] = hh;
            n++;
        }
        return n;
    }
}
