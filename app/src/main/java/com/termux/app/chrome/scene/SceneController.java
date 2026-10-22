package com.termux.app.chrome.scene;

import android.app.Activity;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * <p>The layer is inserted into the terminal root container directly above the wallpaper backdrop
 * and below everything else the container holds (panes, chrome, widgets), sized and offset by
 * margins to the whole decor so it fills the window the way the backdrop's painted frame does. It
 * is opaque, so the system-wallpaper show-through is replaced while the scene is on.</p>
 */
public final class SceneController implements LatencyProbe.CommitListener {

    /** What the controller needs from the activity. */
    public interface Host {
        /** The terminal panes currently on screen. */
        @NonNull List<TerminalView> paneViews();

        /** The focused pane, or null. */
        @Nullable TerminalView focusedPane();

        boolean lazyMode();
    }

    private static final DebugChoice SCENE =
        new DebugChoice(SceneFramePolicy.PROP_SCENE, "off", DebugSwitch.SYSTEM_PROPERTIES);
    private static final DebugChoice RATE =
        new DebugChoice(SceneFramePolicy.PROP_RATE, "30", DebugSwitch.SYSTEM_PROPERTIES);
    private static final DebugChoice GLASS =
        new DebugChoice(SceneFramePolicy.PROP_GLASS, "lowres", DebugSwitch.SYSTEM_PROPERTIES);

    @NonNull private final Activity mActivity;
    @NonNull private final FrameLayout mContainer;
    @Nullable private final View mBackdrop;
    @NonNull private final Host mHost;
    @NonNull private final float[] mCursor = new float[2];
    @NonNull private final int[] mLoc = new int[2];
    @Nullable private SceneLayerView mLayer;
    private boolean mAttached;

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
        poll();
    }

    /** From onStop: the layer goes away with the screen it was drawn on. */
    public void detach() {
        mAttached = false;
        LatencyProbe.clearCommitListener(this);
        removeLayer();
    }

    /** Every window touch; a touch-down also re-reads the properties (at most once a second). */
    public void onWindowTouch(@NonNull MotionEvent ev) {
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        poll();
        SceneLayerView layer = mLayer;
        if (layer != null) layer.touchDown(ev.getRawX(), ev.getRawY());
    }

    /** A terminal bell from {@code pane}. */
    public void onBell(@Nullable TerminalView pane) {
        SceneLayerView layer = mLayer;
        if (layer == null || pane == null || pane.getWidth() <= 0) return;
        pane.getLocationOnScreen(mLoc);
        float hw = pane.getWidth() / 2f, hh = pane.getHeight() / 2f;
        layer.bell(mLoc[0] + hw, mLoc[1] + hh, hw, hh);
    }

    /** A key was committed: the spark starts at the focused pane's cursor. */
    @Override
    public void onCommit() {
        SceneLayerView layer = mLayer;
        if (layer == null) return;
        TerminalView pane = mHost.focusedPane();
        if (pane == null || !pane.getCursorCenter(mCursor)) return;
        pane.getLocationOnScreen(mLoc);
        layer.spark(mLoc[0] + mCursor[0], mLoc[1] + mCursor[1]);
    }

    private void poll() {
        if (!mAttached) return;
        long now = SystemClock.uptimeMillis();
        boolean on = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && SceneFramePolicy.sceneOn(SCENE.get(now));
        if (on && mLayer == null) installLayer();
        else if (!on && mLayer != null) removeLayer();
        SceneLayerView layer = mLayer;
        if (layer == null) return;
        layer.setRateHz(SceneFramePolicy.parseRate(RATE.get(now)));
        layer.setGlassLowRes(SceneFramePolicy.glassLowRes(GLASS.get(now)));
    }

    private void installLayer() {
        SceneLayerView layer = new SceneLayerView(mActivity, this::paneRects, mHost::lazyMode);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, 0);
        int index = mBackdrop == null ? 0 : mContainer.indexOfChild(mBackdrop) + 1;
        mLayer = layer;
        mContainer.addView(layer, Math.max(0, index), lp);
        mContainer.addOnLayoutChangeListener(mPlace);
        place();
    }

    private void removeLayer() {
        SceneLayerView layer = mLayer;
        if (layer == null) return;
        mLayer = null;
        mContainer.removeOnLayoutChangeListener(mPlace);
        layer.destroy();
        mContainer.removeView(layer);
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
