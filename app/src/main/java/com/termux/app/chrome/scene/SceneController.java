package com.termux.app.chrome.scene;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.wallpaper.LiveWallpaperApplier;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.terminal.DebugChoice;
import com.termux.terminal.DebugSwitch;
import com.termux.terminal.LatencyProbe;
import com.termux.view.TerminalView;

import java.io.File;
import java.util.List;

/**
 * Owns the moving wallpaper's layer for one activity and feeds it the launcher's events. The
 * choice is {@link TERMUX_APP#KEY_WALLPAPER_MOTION} ({@link WallpaperMotion}); with a photo chosen
 * and {@code debug.tl.shader_file} unset every entry point is a field read, and no view exists.
 *
 * <p>The layer is a SurfaceView inserted into the terminal root container directly above the
 * wallpaper backdrop and below everything else the container holds (panes, chrome, widgets), sized
 * and offset by margins to the whole decor. Its surface sits behind the window; the view punches a
 * transparent hole in the window where it is, so everything drawn before it in the tree (the
 * backdrop, the root's background) is cleared there and the scene replaces the system-wallpaper
 * show-through, while everything drawn after it composites over the scene as it did over the
 * wallpaper.</p>
 *
 * <p>At start: a stored AGSL Live scene is migrated once ({@link WallpaperMotion.Migration}), and
 * a pending marker left by an apply that never drew {@link ShaderSafety#PENDING_CLEAR_MS} (the
 * process died) reverts to photo before any surface is made. Video (wave B) has no layer yet.</p>
 */
public final class SceneController implements LatencyProbe.CommitListener {

    private static final String TAG = "SceneController";

    /** What the controller needs from the activity. */
    public interface Host {
        /** The terminal panes currently on screen. */
        @NonNull List<TerminalView> paneViews();

        /** The focused pane, or null. */
        @Nullable TerminalView focusedPane();

        boolean lazyMode();

        /** The preferences the wallpaper choice is stored in, or null before they exist. */
        @Nullable TermuxAppSharedPreferences preferences();
    }

    private static final DebugChoice SHADER_FILE =
        new DebugChoice(SceneFramePolicy.PROP_SHADER_FILE, "", DebugSwitch.SYSTEM_PROPERTIES);
    private static final DebugChoice RATE =
        new DebugChoice(SceneFramePolicy.PROP_RATE, "", DebugSwitch.SYSTEM_PROPERTIES);
    private static final DebugChoice GLASS =
        new DebugChoice(SceneFramePolicy.PROP_GLASS, "lowres", DebugSwitch.SYSTEM_PROPERTIES);
    private static final DebugChoice GLASS_RATE =
        new DebugChoice(SceneFramePolicy.PROP_GLASS_RATE, "", DebugSwitch.SYSTEM_PROPERTIES);

    private static final DebugChoice TRACE_INVAL =
        new DebugChoice("debug.tl.scene_trace_inval", "0", DebugSwitch.SYSTEM_PROPERTIES);

    /** The pending marker is a crash sign only when found as the process starts. */
    private static boolean sPendingChecked;

    @NonNull private final Activity mActivity;
    @NonNull private final FrameLayout mContainer;
    @Nullable private final View mBackdrop;
    @NonNull private final Host mHost;
    @NonNull private final int[] mLoc = new int[2];
    @Nullable private SceneLayerView mLayer;
    private boolean mAttached;
    @Nullable private SceneInvalTrace mInvalTrace;
    /** The persisted choice as of the last attach or {@link #refresh}; read here, never per touch. */
    @NonNull private WallpaperMotion mMotion = WallpaperMotion.photo();
    /** The stored shader's settings, read when the choice changes. */
    @Nullable private ShaderStore.Meta mMeta;
    /** The debug file a layer gave up on: not tried again until the property changes. */
    @Nullable private String mDebugGaveUp;
    /** The current gesture began over the keyboard: its moves are typing, not the scene's. */
    private boolean mGestureOverKeys;
    @Nullable private SharedPreferences mStore;
    private final SharedPreferences.OnSharedPreferenceChangeListener mChoiceListener =
        (prefs, key) -> {
            if (TERMUX_APP.KEY_WALLPAPER_MOTION.equals(key)) refresh();
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

    /** Shaders need OpenGL ES 3 and {@code Bitmap.wrapHardwareBuffer} for the glass: API 29 and later. */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /** From onStart. */
    public void attach() {
        mAttached = true;
        LatencyProbe.setCommitListener(this);
        listenForChoice();
        migrateLiveScene();
        readChoice();
        checkPending();
        poll();
    }

    /**
     * Re-reads the persisted choice and applies it now: a different shader switches the layer, the
     * same shader takes the new rate and quality in place. Cheap when nothing changed. Triggered by
     * this controller's own preference listener (while attached); a caller may also call it directly.
     */
    public void refresh() {
        if (!mAttached) return;
        readChoice();
        poll();
    }

    @Nullable
    private SharedPreferences store() {
        TermuxAppSharedPreferences prefs = mHost.preferences();
        return prefs == null ? null : prefs.getSharedPreferences();
    }

    private void readChoice() {
        SharedPreferences store = store();
        WallpaperMotion motion = store == null ? WallpaperMotion.photo() : ShaderStore.readMotion(store);
        if (motion.kind == WallpaperMotion.Kind.SHADER && motion.id != null
            && (!motion.equals(mMotion) || mMeta == null)) {
            mMeta = ShaderStore.readMeta(mActivity, motion.id);
        } else if (motion.kind != WallpaperMotion.Kind.SHADER) {
            mMeta = null;
        }
        mMotion = motion;
    }

    /** The one-time move off the AGSL scenes: clear the old keys, put the remembered photo back. */
    private void migrateLiveScene() {
        SharedPreferences store = store();
        if (store == null) return;
        String old = store.getString(TERMUX_APP.KEY_LIVE_WALLPAPER_SCENE, null);
        if (WallpaperMotion.Migration.plan(old) != WallpaperMotion.Migration.Plan.CLEAR_AND_RESTORE_PHOTO) return;
        Log.i(TAG, "Live scene " + old + " is gone: clearing its keys and putting the photo back");
        SharedPreferences.Editor edit = store.edit();
        for (String key : WallpaperMotion.Migration.OLD_KEYS) edit.remove(key);
        edit.apply();
        LiveWallpaperApplier.putBackRememberedPhoto(mActivity, null);
    }

    /** Once per process: a marker means the last apply never drew 3 s, so go back to photo. */
    private void checkPending() {
        if (sPendingChecked) return;
        sPendingChecked = true;
        if (!ShaderStore.hasPending(mActivity)) return;
        if (!mMotion.moves()) {
            ShaderStore.clearPending(mActivity);
            return;
        }
        Log.w(TAG, "pending marker found at start: " + mMotion + " never ran 3 s, back to photo");
        revertToPhoto();
    }

    private void listenForChoice() {
        SharedPreferences store = store();
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

    /** Every window touch: the pointer for {@code iMouse}; a touch-down also re-reads the properties (at most once a second). */
    public void onWindowTouch(@NonNull MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            poll();
            // Taps on the keyboard and the extra keys are typing, and the scene does not see typing.
            mGestureOverKeys = overKeys(mActivity.getWindow().getDecorView(), ev.getRawX(), ev.getRawY());
        }
        SceneLayerView layer = mLayer;
        if (layer == null || mGestureOverKeys) return;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                layer.touchDown(ev.getRawX(), ev.getRawY());
                break;
            case MotionEvent.ACTION_MOVE:
                layer.touchMove(ev.getRawX(), ev.getRawY());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                layer.touchUp(ev.getRawX(), ev.getRawY());
                break;
            default:
                break;
        }
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

    /** A terminal bell. The shader contract has no bell input; kept for the activity's call site. */
    public void onBell(@Nullable TerminalView pane) {}

    /** The focused window's chip text. The shader contract has no window name; kept for the activity's call site. */
    public void onFocusedWindow(@Nullable String label) {}

    /**
     * A key was committed. The scene does not react to typing (the keyboard, the echo and the trail
     * already move with every key); instead it holds still while typing goes on, so it costs
     * nothing on the render thread exactly when latency matters.
     */
    @Override
    public void onCommit() {
        SceneLayerView layer = mLayer;
        if (layer != null) layer.holdForTyping();
    }

    /** What should run now: the debug file, else the stored shader, else nothing. */
    @Nullable
    private SceneLayerView.Spec currentSpec(long now) {
        String path = SHADER_FILE.get(now);
        if (path != null && !path.trim().isEmpty()) {
            path = path.trim();
            if (path.equals(mDebugGaveUp)) return null;
            return new SceneLayerView.Spec("file:" + path, new File(path), null,
                ShaderSafety.Quality.HALF, true);
        }
        mDebugGaveUp = null;
        if (mMotion.kind != WallpaperMotion.Kind.SHADER || mMotion.id == null || mMeta == null) return null;
        File channel0 = ShaderStore.textureFile(mActivity, mMotion.id, mMeta);
        return new SceneLayerView.Spec(mMotion.format(), ShaderStore.sourceFile(mActivity, mMotion.id),
            channel0, mMeta.quality, false);
    }

    private void poll() {
        if (!mAttached) return;
        long now = SystemClock.uptimeMillis();
        SceneLayerView.Spec spec = isSupported() ? currentSpec(now) : null;
        // A different shader is a different layer.
        if (mLayer != null && (spec == null || !mLayer.key().equals(spec.key))) removeLayer();
        if (spec != null && mLayer == null) installLayer(spec);
        SceneLayerView layer = mLayer;
        if (layer == null || spec == null) return;
        String rateProp = RATE.get(now);
        int choiceRate = spec.debugFile || mMeta == null ? SceneFramePolicy.DEFAULT_RATE_HZ : mMeta.rateHz;
        int sceneRate = rateProp == null || rateProp.isEmpty()
            ? choiceRate : SceneFramePolicy.parseRate(rateProp);
        layer.setRateHz(sceneRate);
        layer.setQuality(spec.quality);
        layer.setPowerHold(mPowerHold);
        layer.setGlassLowRes(SceneFramePolicy.glassLowRes(GLASS.get(now)));
        layer.setGlassRateHz(SceneFramePolicy.parseGlassRate(GLASS_RATE.get(now), sceneRate));
    }

    private void installLayer(@NonNull SceneLayerView.Spec spec) {
        SceneLayerView layer = new SceneLayerView(mActivity, spec, mHost::lazyMode,
            reason -> onLayerReverted(spec, reason));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, 0);
        int index = mBackdrop == null ? 0 : mContainer.indexOfChild(mBackdrop) + 1;
        mLayer = layer;
        watchPower();
        layer.setPowerHold(mPowerHold);
        pushLook(layer);
        mContainer.addView(layer, Math.max(0, index), lp);
        mContainer.addOnLayoutChangeListener(mPlace);
        place();
        installInvalTrace(layer);
    }

    /** {@code tlDark} and {@code tlColor0..2} from the activity's Material roles (the scheme writes them when it is on). */
    private void pushLook(@NonNull SceneLayerView layer) {
        boolean dark = (mActivity.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int accent = MaterialColors.getColor(mActivity, androidx.appcompat.R.attr.colorPrimary, 0xff6750a4);
        int surface = MaterialColors.getColor(mActivity, com.google.android.material.R.attr.colorSurface,
            dark ? 0xff141218 : 0xfffef7ff);
        int onSurface = MaterialColors.getColor(mActivity, com.google.android.material.R.attr.colorOnSurface,
            dark ? 0xffe6e0e9 : 0xff1d1b20);
        layer.setLook(dark, accent, surface, onSurface);
    }

    /** The layer cannot go on (a second lost context, no ES 3, a stored shader that no longer builds). */
    private void onLayerReverted(@NonNull SceneLayerView.Spec spec, @NonNull String reason) {
        Log.w(TAG, "shader " + spec.key + " stopped: " + reason);
        if (spec.debugFile) {
            mDebugGaveUp = spec.source.getPath();
            removeLayer();
            return;
        }
        revertToPhoto();
    }

    /** Back to photo with a one-line notice; the preference listener removes the layer. */
    private void revertToPhoto() {
        TermuxAppSharedPreferences prefs = mHost.preferences();
        if (prefs != null) ShaderStore.apply(mActivity, prefs.getSharedPreferences(), WallpaperMotion.photo());
        else ShaderStore.clearPending(mActivity);
        mMotion = WallpaperMotion.photo();
        mMeta = null;
        removeLayer();
        Toast.makeText(mActivity, R.string.wallpaper_motion_reverted, Toast.LENGTH_SHORT).show();
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

    /** The running layer's hold reason for the detail page ({@link ShaderSafety#REASON_TOO_HEAVY}), or null. */
    @Nullable
    public String heldReason() {
        SceneLayerView layer = mLayer;
        return layer == null ? null : layer.heldReason();
    }

    // ---- Battery saver and heat -----------------------------------------------------------------

    /** While a layer exists: listens for battery saver and thermal changes (SceneFramePolicy.holdAtStill decides). */
    private void watchPower() {
        if (mPowerWatched || !isSupported()) return;
        mPowerWatched = true;
        IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mActivity.registerReceiver(mPowerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            mActivity.registerReceiver(mPowerReceiver, filter);
        }
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
}
