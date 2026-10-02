package com.termux.app.chrome.wallpaper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.SharedFrameDrawable;
import com.termux.app.chrome.WallpaperBackdropView;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.wall.PaneControlsView;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The activity's side of a generated background (issue #41): owns the {@link WallpaperDirector} and
 * the {@link AnimatedWallpaperClock}, reads the phone (power saver, thermal, battery, screen, unlock)
 * and the launcher (Fancier Glass, Lazy mode, reduced motion, which picture and backdrop) into the
 * Director's {@link WallpaperDirector.Conditions}, and points the glass readers at the live frames
 * while it plays. The rules themselves are the Director's; this class only feeds it.
 *
 * <p>The activity forwards its lifecycle to the {@code on...} methods and calls {@link #refresh}
 * when something the conditions read may have changed. Below API 34 every method is a no-op and the
 * status reads {@code api}. Main thread only, except the {@link AnimatedWallpaperStatus} getters,
 * which read values published by the last {@link #refresh}.</p>
 *
 * <p>The moment methods take rects and points in shared-frame pixels (the frame rect the blur
 * cache captures); the activity calls them for pane, page, tap and bell events.</p>
 */
@SuppressLint("NewApi")
public final class GeneratedWallpaperHost implements AnimatedWallpaperStatus, AnimatedWallpaperClock.Host {

    private static final String LOG_TAG = "GeneratedWallpaperHost";
    /** The reader scan runs at most this often while frames play. */
    private static final long READER_SYNC_MS = 500L;
    /** The fallback lock fires this long after the settle time, so the clock's own signal wins. */
    private static final long LOCK_FALLBACK_SLACK_MS = 60L;
    /** The lock never waits longer than this in total, fallback included. */
    private static final long LOCK_MAX_DELAY_MS = 400L;
    /** A lock the system did not act on is undone (the picture blooms back) after this. */
    private static final long LOCK_RECOVER_MS = 1500L;

    /** What the host cannot know by itself; the activity answers from its own state. */
    public interface Environment {
        /** Fancier Glass is active right now (switch, floor, managed picture). */
        boolean fancierGlassActive();

        /** The launcher's own managed copy of the wallpaper is the picture on screen. */
        boolean managedPictureOnScreen();

        /** The launcher paints the backdrop itself. */
        boolean selfDrawnBackdrop();

        boolean lazyMode();

        boolean reducedMotion();

        /** The view behind everything that draws the live shader, or null. */
        @Nullable
        WallpaperBackdropView backdrop();

        /** The wallpaper capture frame rect in screen pixels (the blur cache's frame). */
        @NonNull
        Rect frameRect();

        /** The blur radius in dp the resident frame was cut at, or -1 when it is not a resident frame. */
        int blurRadiusDpOf(@Nullable Bitmap frame);
    }

    @NonNull private final Activity mActivity;
    @NonNull private final Environment mEnv;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final boolean mSupported = Build.VERSION.SDK_INT >= 34;
    @NonNull private final LockOnce mLock = new LockOnce();

    @Nullable private WallpaperDirector mDirector;
    @Nullable private AnimatedWallpaperClock mClock;
    @Nullable private AnimatedWallpaper mWallpaper;
    @Nullable private int[] mPalette;

    private boolean mVisible;
    private boolean mScreenOn = true;
    private boolean mWasScreenOff;
    private boolean mPowerSave;
    private boolean mBatteryLowBroadcast;
    private int mBatteryPercent = -1;
    private int mPlugged;
    private boolean mLowWhileDischarging;
    @NonNull private WallpaperDirector.Thermal mThermal = WallpaperDirector.Thermal.NONE;
    private boolean mRendererHealthy = true;
    /** The lock method ran and the screen has not gone off yet. */
    private boolean mLockHandedOver;

    private boolean mScreenReceiverRegistered;
    private boolean mDeviceListenersRegistered;
    @Nullable private PowerManager.OnThermalStatusChangedListener mThermalListener;

    /** Whether readers may be pointed at live frames: the gate is open, paused or playing. */
    private boolean mLiveEnabled;
    /** {@link #mLiveEnabled} as of the last {@link #applyLive}, to catch the gate closing. */
    private boolean mWasLive;
    private volatile boolean mPlaying;
    @Nullable private volatile String mReason = "inactive";

    private long mLastReaderSyncMs;
    @NonNull private List<SharedFrameDrawable> mDrawables = new ArrayList<>();
    @NonNull private final List<View> mReaderViews = new ArrayList<>();
    @NonNull private float[] mRadii = new float[0];

    private final Runnable mLockFallback = this::onLockDue;
    private final Runnable mLockRecover = this::recoverLock;

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    mScreenOn = false;
                    mWasScreenOff = true;
                    mLockHandedOver = false;
                    mHandler.removeCallbacks(mLockRecover);
                    mHandler.removeCallbacks(mLockFallback);
                    mLock.cancel();
                    publishAndKick();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    mScreenOn = true;
                    publishAndKick();
                    break;
                case Intent.ACTION_USER_PRESENT:
                    // Only an unlock that follows a screen-off: this also fires for a swipe-away
                    // keyguard that was never up.
                    if (!mWasScreenOff) break;
                    mWasScreenOff = false;
                    WallpaperDirector director = mDirector;
                    if (director != null) director.unlock(System.nanoTime());
                    kick();
                    break;
                default:
                    break;
            }
        }
    };

    private final BroadcastReceiver mDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            switch (intent.getAction()) {
                case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED:
                    mPowerSave = readPowerSave();
                    break;
                case Intent.ACTION_BATTERY_LOW:
                    mBatteryLowBroadcast = true;
                    break;
                case Intent.ACTION_BATTERY_OKAY:
                    mBatteryLowBroadcast = false;
                    break;
                case Intent.ACTION_BATTERY_CHANGED:
                    readBattery(intent);
                    break;
                default:
                    // Plugged or unplugged: the sticky battery intent carries the new state.
                    readBattery(mActivity.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
                    break;
            }
            publishAndKick();
        }
    };

    public GeneratedWallpaperHost(@NonNull Activity activity, @NonNull Environment env) {
        mActivity = activity;
        mEnv = env;
    }

    // --- lifecycle ---

    /** The activity was created: listen for screen off and unlock, and answer the status route. */
    public void onCreate() {
        GeneratedWallpaperApplier.setStatusProvider(this);
        if (!mSupported) return;
        GeneratedWallpaperApplier.setChangedListener(this::refresh);
        PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
        mScreenOn = pm == null || pm.isInteractive();
        if (!mScreenReceiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            try {
                mActivity.registerReceiver(mScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                mScreenReceiverRegistered = true;
            } catch (RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Failed to register screen receiver: " + e.getMessage());
            }
        }
        refresh();
    }

    /** The activity is visible and started: read the device, register its listeners and play. */
    public void onStart() {
        if (!mSupported) return;
        mVisible = true;
        registerDeviceListeners();
        recoverLock();
        refresh();
        AnimatedWallpaperClock clock = mClock;
        if (clock != null) clock.start();
    }

    /** Back in front (also after a picker shown over the activity). */
    public void onResume() {
        if (!mSupported) return;
        mVisible = true;
        refresh();
        AnimatedWallpaperClock clock = mClock;
        if (clock != null) clock.start();
    }

    public void onStop() {
        if (!mSupported) return;
        mVisible = false;
        unregisterDeviceListeners();
        mHandler.removeCallbacks(mLockFallback);
        mLock.cancel();
        publishConditions();
        AnimatedWallpaperClock clock = mClock;
        if (clock != null) clock.stop();
    }

    /**
     * Frees the renderer under real pressure: the running-low levels while in front, and the
     * background levels from moderate up. A plain UI-hidden or background trim is not pressure and
     * leaves the 30 s release to {@link AnimatedWallpaperClock#stop}.
     */
    public void onTrimMemory(int level) {
        AnimatedWallpaperClock clock = mClock;
        if (clock == null) return;
        boolean pressure = level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE
            || (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                && level < android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        if (pressure) clock.trimMemory();
    }

    /** A rotation or resize: the frame rect moved with it. */
    public void onConfigurationChanged() {
        refresh();
    }

    public void onDestroy() {
        if (GeneratedWallpaperApplier.statusProvider() == this) {
            GeneratedWallpaperApplier.setStatusProvider(null);
        }
        if (!mSupported) return;
        GeneratedWallpaperApplier.setChangedListener(null);
        mHandler.removeCallbacksAndMessages(null);
        mLock.cancel();
        unregisterDeviceListeners();
        if (mScreenReceiverRegistered) {
            try {
                mActivity.unregisterReceiver(mScreenReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
            mScreenReceiverRegistered = false;
        }
        tearDown();
    }

    // --- conditions ---

    /**
     * Something the conditions read may have changed (Fancier Glass, Lazy mode, the picture, the
     * stored background or its colours, the frame rect): re-read the stored background, publish the
     * conditions, re-aim the readers and wake the clock.
     */
    public void refresh() {
        if (!mSupported) return;
        syncWallpaper();
        AnimatedWallpaperClock clock = mClock;
        if (clock != null) {
            Rect frame = mEnv.frameRect();
            clock.setFrameSize(frame.width(), frame.height());
        }
        publishConditions();
        applyLive();
        kick();
    }

    /** The capture frame rect the activity just used; hands its size to the clock. */
    public void syncFrameSize(@NonNull Rect frameRect) {
        AnimatedWallpaperClock clock = mClock;
        if (clock != null) clock.setFrameSize(frameRect.width(), frameRect.height());
    }

    /** The activity, which carries the launcher's scheme theme for reading Material colours. */
    @NonNull
    public Activity themedContext() {
        return mActivity;
    }

    /** A new palette for the running background (the stored colours are re-read by {@link #refresh} too). */
    public void setPalette(@Nullable int[] argb4) {
        WallpaperDirector director = mDirector;
        if (director == null || argb4 == null || argb4.length != 4) return;
        mPalette = argb4.clone();
        director.setPalette(argb4);
        kick();
    }

    /** The launcher's colour scheme was reloaded: nothing to do, the wallpaper keeps its own palette. */
    public void onStylingReloaded() {
    }

    /** A photo became the wallpaper: forget the generated background and go still. */
    public void onPhotoApplied() {
        if (!mSupported) return;
        GeneratedWallpaperApplier.clear(mActivity);
        refresh();
    }

    private void syncWallpaper() {
        TermuxAppSharedPreferences prefs = prefs();
        AnimatedWallpaper wallpaper = prefs == null ? null
            : AnimatedWallpapers.byId(prefs.getManagedWallpaperAnimatedId());
        WallpaperBackdropView backdrop = mEnv.backdrop();
        if (backdrop == null) wallpaper = null;
        int[] colors = null;
        if (wallpaper != null) {
            colors = prefs.getManagedWallpaperAnimatedColors();
            if (colors == null) colors = wallpaper.ownPalette();
        }
        if (wallpaper == mWallpaper) {
            if (mDirector != null && colors != null && !Arrays.equals(colors, mPalette)) {
                mPalette = colors.clone();
                mDirector.setPalette(colors);
            }
            return;
        }
        tearDown();
        mWallpaper = wallpaper;
        if (wallpaper == null) return;
        // A new background gets a new clock, so an earlier render failure does not carry over.
        mRendererHealthy = true;
        mPalette = colors.clone();
        WallpaperDirector director = new WallpaperDirector(wallpaper.periodSeconds(), colors);
        AnimatedWallpaperClock clock = new AnimatedWallpaperClock(this, director, backdrop,
            LiveWallpaperFrames.get(), mActivity.getResources().getDisplayMetrics().density);
        mDirector = director;
        mClock = clock;
        clock.setWallpaper(wallpaper);
        if (mVisible) clock.start();
    }

    private void tearDown() {
        AnimatedWallpaperClock clock = mClock;
        mClock = null;
        mDirector = null;
        mWallpaper = null;
        mPalette = null;
        mHandler.removeCallbacks(mLockFallback);
        mLock.cancel();
        if (clock != null) {
            clock.setWallpaper(null);
            clock.stop();
        }
        publishConditions();
        applyLive();
    }

    private void publishConditions() {
        WallpaperDirector director = mDirector;
        if (director == null) {
            mReason = mSupported ? "inactive" : "api";
            mPlaying = false;
            mLiveEnabled = false;
            return;
        }
        TermuxAppSharedPreferences prefs = prefs();
        WallpaperDirector.Conditions.Builder b = WallpaperDirector.Conditions.playing();
        b.fancierGlassActive = mEnv.fancierGlassActive();
        b.sdkSupported = mSupported;
        b.animatedIdStored = mWallpaper != null;
        b.managedPictureOnScreen = mEnv.managedPictureOnScreen();
        b.selfDrawnBackdrop = mEnv.selfDrawnBackdrop();
        b.visible = mVisible;
        b.screenOn = mScreenOn;
        b.powerSave = mPowerSave;
        b.batteryLowDischarging = mLowWhileDischarging;
        b.lazyMode = mEnv.lazyMode();
        b.reducedMotion = mEnv.reducedMotion();
        b.killSwitch = prefs != null && prefs.isAnimatedWallpaperDisabled();
        b.rendererHealthy = mRendererHealthy;
        b.thermal = mThermal;
        director.setConditions(b.build(), System.nanoTime());
        String reason = director.reason();
        mReason = reason;
        mPlaying = reason == null;
        mLiveEnabled = reason == null || "paused".equals(reason);
    }

    private void publishAndKick() {
        publishConditions();
        applyLive();
        kick();
    }

    /** Re-aims the readers at the conditions just published; a gate that closed puts the stills back. */
    private void applyLive() {
        syncReaders(true);
        if (mWasLive && !mLiveEnabled) goStill();
        mWasLive = mLiveEnabled;
    }

    private void kick() {
        AnimatedWallpaperClock clock = mClock;
        if (clock != null) clock.kick();
    }

    @Nullable
    private TermuxAppSharedPreferences prefs() {
        return TermuxAppSharedPreferences.build(mActivity.getApplicationContext(), false);
    }

    // --- device listeners ---

    private void registerDeviceListeners() {
        if (mDeviceListenersRegistered) return;
        PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
        mPowerSave = readPowerSave();
        IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        try {
            Intent sticky = mActivity.registerReceiver(mDeviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            readBattery(sticky);
            mDeviceListenersRegistered = true;
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Failed to register device receiver: " + e.getMessage());
        }
        if (pm != null) {
            try {
                mThermal = WallpaperDeviceReadings.thermal(pm.getCurrentThermalStatus());
                PowerManager.OnThermalStatusChangedListener listener = status -> {
                    mThermal = WallpaperDeviceReadings.thermal(status);
                    publishAndKick();
                };
                pm.addThermalStatusListener(mActivity.getMainExecutor(), listener);
                mThermalListener = listener;
            } catch (RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Thermal status unavailable: " + e.getMessage());
            }
        }
    }

    private void unregisterDeviceListeners() {
        if (mDeviceListenersRegistered) {
            try {
                mActivity.unregisterReceiver(mDeviceReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
            mDeviceListenersRegistered = false;
        }
        PowerManager.OnThermalStatusChangedListener listener = mThermalListener;
        mThermalListener = null;
        if (listener != null) {
            PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
            try {
                if (pm != null) pm.removeThermalStatusListener(listener);
            } catch (RuntimeException ignored) {
                // Nothing to undo.
            }
        }
    }

    private boolean readPowerSave() {
        PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isPowerSaveMode();
    }

    private void readBattery(@Nullable Intent battery) {
        if (battery != null) {
            mBatteryPercent = WallpaperDeviceReadings.percent(
                battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
            mPlugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        }
        mLowWhileDischarging = WallpaperDeviceReadings.lowWhileDischarging(
            mBatteryPercent, mPlugged, mBatteryLowBroadcast);
    }

    // --- moments (shared-frame pixels) ---

    /** A pane opened or split; {@code seam} is the new pane's rect. */
    public void paneOpened(@NonNull RectF seam) {
        WallpaperDirector director = mDirector;
        if (director == null) return;
        director.paneOpened(seam.left, seam.top, seam.right, seam.bottom, System.nanoTime());
        kick();
    }

    /** A pane closed; {@code seam} is the rect it left. */
    public void paneClosed(@NonNull RectF seam) {
        WallpaperDirector director = mDirector;
        if (director == null) return;
        director.paneClosed(seam.left, seam.top, seam.right, seam.bottom, System.nanoTime());
        kick();
    }

    /** The wall changed page; {@code dir} is -1 or +1, the direction of the slide. */
    public void pageChanged(int dir) {
        WallpaperDirector director = mDirector;
        if (director == null) return;
        director.pageChanged(dir < 0 ? -1 : 1, System.nanoTime());
        kick();
    }

    /** A touch on bare wallpaper on the Home place. */
    public void touch(float x, float y) {
        WallpaperDirector director = mDirector;
        if (director == null) return;
        director.touch(x, y, System.nanoTime());
        kick();
    }

    /** A bell rang in the pane at {@code pane}. */
    public void bell(@NonNull RectF pane) {
        WallpaperDirector director = mDirector;
        if (director == null) return;
        director.bell(pane.left, pane.top, pane.right, pane.bottom, System.nanoTime());
        kick();
    }

    // --- lock ---

    /**
     * The user asked to lock (the A-Z index double tap). While the background plays, the picture
     * settles first and {@code lock} runs when the Director says so, at most about 400 ms later;
     * paused, unsupported or under reduced motion it runs at once. A second call while one is
     * pending makes the lock due at once. {@code lock} runs exactly once per request.
     */
    public void requestLock(@NonNull Runnable lock) {
        WallpaperDirector director = mDirector;
        if (!mSupported || director == null) {
            lock.run();
            return;
        }
        long now = System.nanoTime();
        if (mLock.isPending()) {
            director.lockNow(now);
            kick();
            return;
        }
        long delayMs = director.lockRequested(now);
        if (delayMs <= 0L) {
            lock.run();
            return;
        }
        mLock.arm(() -> {
            lock.run();
            mLockHandedOver = true;
            mHandler.removeCallbacks(mLockRecover);
            mHandler.postDelayed(mLockRecover, LOCK_RECOVER_MS);
        });
        mHandler.removeCallbacks(mLockFallback);
        mHandler.postDelayed(mLockFallback,
            Math.min(delayMs + LOCK_FALLBACK_SLACK_MS, LOCK_MAX_DELAY_MS));
        kick();
    }

    /** The Director's lock is due (from the clock, or the fallback timer, whichever comes first). */
    @Override
    public void onLockDue() {
        mHandler.removeCallbacks(mLockFallback);
        mLock.fire();
    }

    /** The lock method did not put the screen off: bring the picture back from its rest pose. */
    private void recoverLock() {
        mHandler.removeCallbacks(mLockRecover);
        if (!mLockHandedOver || !mScreenOn) return;
        mLockHandedOver = false;
        WallpaperDirector director = mDirector;
        if (director != null) director.unlock(System.nanoTime());
        kick();
    }

    // --- AnimatedWallpaperClock.Host ---

    @Override
    public void invalidateFrameReaders() {
        if (!mLiveEnabled) {
            // A frame landed after the gate closed: put the stills back.
            goStill();
            return;
        }
        invalidateReaders();
    }

    @NonNull
    @Override
    public float[] liveRadiiDp() {
        syncReaders(false);
        return mRadii;
    }

    @Override
    public void onRendererUnhealthy() {
        mRendererHealthy = false;
        publishConditions();
        applyLive();
        goStill();
    }

    // --- AnimatedWallpaperStatus ---

    @Override
    public boolean playing() {
        return mPlaying;
    }

    @Nullable
    @Override
    public String reason() {
        return mReason;
    }

    // --- readers ---

    /**
     * Points every shared-frame reader at the live radius its still was blurred at while frames
     * may show (0 otherwise). Readers are found rather than registered: the drawables through
     * {@link SharedFrameDrawable#instances()}, the pane slabs and corner tabs by walking the window.
     * Throttled unless {@code force}, so a new reader waits at most {@link #READER_SYNC_MS}.
     */
    private void syncReaders(boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - mLastReaderSyncMs < READER_SYNC_MS) return;
        mLastReaderSyncMs = now;
        mDrawables = SharedFrameDrawable.instances();
        mReaderViews.clear();
        collectReaderViews(mActivity.getWindow().getDecorView());
        List<Float> radii = new ArrayList<>();
        for (SharedFrameDrawable drawable : mDrawables) {
            float radius = liveRadiusOf(drawable.frame());
            drawable.setLiveRadiusDp(radius);
            if (radius > 0f) radii.add(radius);
        }
        for (View view : mReaderViews) {
            float radius;
            if (view instanceof PaneGlassBackdropView) {
                radius = liveRadiusOf(((PaneGlassBackdropView) view).stillFrame());
                ((PaneGlassBackdropView) view).setLiveRadiusDp(radius);
            } else {
                radius = liveRadiusOf(((PaneControlsView) view).stillFrame());
                ((PaneControlsView) view).setLiveRadiusDp(radius);
            }
            if (radius > 0f) radii.add(radius);
        }
        float[] next = new float[radii.size()];
        for (int i = 0; i < next.length; i++) next[i] = radii.get(i);
        mRadii = next;
    }

    private float liveRadiusOf(@Nullable Bitmap still) {
        if (!mLiveEnabled) return 0f;
        return Math.max(0, mEnv.blurRadiusDpOf(still));
    }

    private void collectReaderViews(@NonNull View view) {
        if (view instanceof PaneGlassBackdropView || view instanceof PaneControlsView) {
            mReaderViews.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectReaderViews(group.getChildAt(i));
        }
    }

    private void invalidateReaders() {
        for (SharedFrameDrawable drawable : mDrawables) drawable.invalidateSelf();
        for (View view : mReaderViews) view.invalidate();
    }

    /** Drops the live frames and the backdrop shader so every reader draws its still. */
    private void goStill() {
        LiveWallpaperFrames.get().clear();
        WallpaperBackdropView backdrop = mEnv.backdrop();
        if (backdrop != null) backdrop.setLiveShader(null);
        invalidateReaders();
    }
}
