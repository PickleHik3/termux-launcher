package com.termux.app.chrome.wallpaper;

import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.app.wall.WallParallax;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The lock-screen live wallpaper (project-docs/active/animated-wallpaper/lock-live-wallpaper.md):
 * draws the Lock slot's living still, calmer and dimmed, on the keyguard. The home screen
 * keeps the launcher's self-drawn animation over a still, so the glass never samples a live
 * system wallpaper.
 *
 * <p>Enabled from API 34 only ({@code @bool/lock_live_wallpaper_enabled}), the floor of the
 * rest-pose still and the in-app animation. It runs in the app process, so it reads the slot
 * preferences directly and re-reads them on every change (an
 * {@link SharedPreferences.OnSharedPreferenceChangeListener}) and every time it becomes visible.</p>
 *
 * <p>Unlock seam (decision 1): the engine settles to the rest pose, the picture of the home still,
 * on the earliest of: {@link Engine#onCommand} with {@link #COMMAND_KEYGUARD_GOING_AWAY}, which
 * Android 14+ sends to wallpaper engines when the keyguard starts to go away; the keyguard reading
 * unlocked ({@link KeyguardManager#isKeyguardLocked()}, polled once per drawn frame, no
 * permission); {@link Intent#ACTION_USER_PRESENT}. Hidden (screen off, AOD, an app on top) it
 * draws nothing.</p>
 */
public final class LockLiveWallpaperService extends WallpaperService {

    private static final String LOG_TAG = "LockLiveWallpaper";

    /**
     * WallpaperManager.COMMAND_KEYGUARD_GOING_AWAY (not in the public SDK; the command arrives
     * through the public {@link Engine#onCommand}). Sent when the unlock starts.
     */
    static final String COMMAND_KEYGUARD_GOING_AWAY = "android.wallpaper.keyguardgoingaway";
    /** WallpaperManager.COMMAND_GOING_TO_SLEEP, likewise. */
    static final String COMMAND_GOING_TO_SLEEP = "android.wallpaper.goingtosleep";
    /** WallpaperManager.COMMAND_WAKING_UP, likewise. */
    static final String COMMAND_WAKING_UP = "android.wallpaper.wakingup";

    /** Frame pacing slack: a frame this early still counts as on time. */
    private static final long PACE_SLACK_NANOS = 2_000_000L;

    /** This service's component, which {@link WallpaperSlots} compares with the system's lock wallpaper. */
    @NonNull
    public static ComponentName component(@NonNull Context ctx) {
        return new ComponentName(ctx.getPackageName(), LockLiveWallpaperService.class.getName());
    }

    @Override
    public Engine onCreateEngine() {
        if (Build.VERSION.SDK_INT < 34) {
            // Not enabled below 34; a plain engine draws nothing should a launcher bind it anyway.
            return new Engine();
        }
        return new LockEngine();
    }

    @RequiresApi(34)
    private final class LockEngine extends Engine implements Choreographer.FrameCallback {

        private final Paint mPaint = new Paint();
        @Nullable private TermuxAppSharedPreferences mPrefs;
        @Nullable private AnimatedWallpaper mWallpaper;
        @Nullable private RuntimeShader mShader;
        /** A living still's effects map, drawn off the main thread a tick ahead of the composite; else null. */
        @Nullable private LivingEffectsRenderer mEffects;
        @Nullable private LockWallpaperDirector mDirector;
        private boolean mMotion = true;
        private boolean mKilled;

        private int mWidth;
        private int mHeight;
        private boolean mVisible;
        private boolean mFrameScheduled;
        private long mLastDrawNanos = -1;
        private boolean mDrewOnce;

        private WallpaperDirector.Thermal mThermal = WallpaperDirector.Thermal.NONE;
        private boolean mPowerSave;
        private boolean mBatteryLowBroadcast;
        private int mBatteryPercent = -1;
        private int mPlugged;
        private boolean mLowWhileDischarging;
        @Nullable private PowerManager.OnThermalStatusChangedListener mThermalListener;
        private boolean mReceiverRegistered;

        /** Held strongly: SharedPreferences keeps its listeners weakly. */
        private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener = (sp, key) -> {
            if (key == null
                || TERMUX_APP.KEY_WALLPAPER_LOCK_CHOICE.equals(key)
                || TERMUX_APP.KEY_WALLPAPER_LOCK_MOTION.equals(key)
                || TERMUX_APP.KEY_MANAGED_WALLPAPER_ANIMATED.equals(key)
                || TERMUX_APP.KEY_ANIMATED_WALLPAPER_DISABLED.equals(key)) {
                loadSlot();
                redraw();
            }
        };

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                switch (intent.getAction()) {
                    case Intent.ACTION_USER_PRESENT:
                        unlockStarted("user_present");
                        return;
                    case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED:
                        mPowerSave = readPowerSave();
                        break;
                    case Intent.ACTION_BATTERY_LOW:
                        mBatteryLowBroadcast = true;
                        readBattery(null);
                        break;
                    case Intent.ACTION_BATTERY_OKAY:
                        mBatteryLowBroadcast = false;
                        readBattery(null);
                        break;
                    case Intent.ACTION_BATTERY_CHANGED:
                        readBattery(intent);
                        break;
                    default:
                        break;
                }
                updateFps();
                schedule();
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(false);
            setOffsetNotificationsEnabled(false);
            mPrefs = TermuxAppSharedPreferences.build(getApplicationContext(), false);
            if (mPrefs != null && mPrefs.getSharedPreferences() != null) {
                mPrefs.getSharedPreferences().registerOnSharedPreferenceChangeListener(mPrefsListener);
            }
            loadSlot();
            if (!isPreview()) {
                listenToDevice();
            }
        }

        @Override
        public void onDestroy() {
            stopFrames();
            releaseEffects();
            if (mPrefs != null && mPrefs.getSharedPreferences() != null) {
                mPrefs.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(mPrefsListener);
            }
            stopListeningToDevice();
            super.onDestroy();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            redraw();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            stopFrames();
            mVisible = false;
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            LockWallpaperDirector director = mDirector;
            if (!visible) {
                if (director != null) director.becameHidden();
                stopFrames();
                return;
            }
            loadSlot();
            director = mDirector;
            if (director != null) {
                director.becameVisible(System.nanoTime());
                // Visible on an unlocked phone (the unlock transition, or the engine also holding
                // the home screen): the rest pose, as the home still.
                if (!isPreview() && !keyguardLocked()) director.unlockStarted(System.nanoTime());
            }
            redraw();
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (COMMAND_KEYGUARD_GOING_AWAY.equals(action)) {
                unlockStarted("keyguard_going_away");
            } else if (COMMAND_GOING_TO_SLEEP.equals(action)) {
                stopFrames();
            } else if (COMMAND_WAKING_UP.equals(action)) {
                redraw();
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        @Override
        public WallpaperColors onComputeColors() {
            AnimatedWallpaper w = mWallpaper;
            if (w == null) return null;
            int[] p = WallpaperPaletteCapture.own(w);
            return new WallpaperColors(Color.valueOf(p[0]), Color.valueOf(p[1]), Color.valueOf(p[2]));
        }

        // --- slot ---

        /** Reads the Lock slot and builds the shader for its background (null for a photo). */
        private void loadSlot() {
            // A retired background in the slot would draw black; heal it before reading.
            WallpaperSlots.dropRetiredBackgrounds(getApplicationContext());
            TermuxAppSharedPreferences prefs = mPrefs;
            String id = null;
            boolean motion = true;
            boolean killed = false;
            if (prefs != null) {
                WallpaperSlots.Choice lock = WallpaperSlotPlan.lockChoice(prefs.getWallpaperLockChoice());
                id = WallpaperSlotPlan.resolveLockId(lock, prefs.getManagedWallpaperAnimatedId());
                motion = prefs.isWallpaperLockMotionEnabled();
                killed = prefs.isAnimatedWallpaperDisabled();
            }
            mMotion = motion;
            mKilled = killed;
            AnimatedWallpaper w = AnimatedWallpapers.byId(getApplicationContext(), id);
            if (w == mWallpaper && (w == null || mShader != null)) {
                updateFps();
                return;
            }
            mWallpaper = w;
            mShader = null;
            releaseEffects();
            if (w != null) {
                try {
                    mShader = WallpaperUniforms.newShader(w);
                    if (w instanceof LivingStill) {
                        mEffects = new LivingEffectsRenderer(WallpaperUniforms.newEffectsShader((LivingStill) w));
                    }
                } catch (RuntimeException e) {
                    mShader = null;
                    releaseEffects();
                    Logger.logStackTraceWithMessage(LOG_TAG, "The " + w.id() + " shader did not compile", e);
                }
                int[] palette = WallpaperPaletteCapture.own(w);
                if (mDirector == null) {
                    mDirector = new LockWallpaperDirector(w.periodSeconds(), palette);
                } else {
                    mDirector.setBackground(w.periodSeconds(), palette);
                }
                if (mVisible) mDirector.becameVisible(System.nanoTime());
            }
            mPaint.setShader(mShader);
            updateFps();
            Logger.logInfo(LOG_TAG, "Lock slot: " + (w == null ? "photo" : w.id()) + (motion ? "" : " (motion off)"));
            try {
                notifyColorsChanged();
            } catch (RuntimeException ignored) {
            }
        }

        // --- frames ---

        private void unlockStarted(@NonNull String signal) {
            LockWallpaperDirector director = mDirector;
            if (director == null || isPreview() || director.settling()) return;
            Logger.logDebug(LOG_TAG, "Unlock seen: " + signal);
            director.unlockStarted(System.nanoTime());
            schedule();
        }

        private void updateFps() {
            int fps = LockWallpaperDirector.fps(mThermal, mLowWhileDischarging, mPowerSave,
                !ValueAnimator.areAnimatorsEnabled(), mKilled || !mMotion);
            if (mDirector != null) mDirector.setFps(fps);
        }

        /** Draws one frame now and keeps the clock going if the picture moves. */
        private void redraw() {
            mLastDrawNanos = -1;
            schedule();
        }

        private void schedule() {
            if (!mVisible || mFrameScheduled) return;
            mFrameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void stopFrames() {
            if (mFrameScheduled) {
                Choreographer.getInstance().removeFrameCallback(this);
                mFrameScheduled = false;
            }
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            mFrameScheduled = false;
            if (!mVisible || mWidth <= 0 || mHeight <= 0) return;
            // Screen off or dozing (AOD): draw nothing; waking up or the next visibility redraws.
            if (!isPreview() && !interactive()) return;
            LockWallpaperDirector director = mDirector;
            RuntimeShader shader = mShader;
            if (mWallpaper == null || shader == null || director == null) {
                // A photo (Same as Home with a photo at home) or a refused shader: the slot
                // flow puts the picture on the lock screen with FLAG_LOCK, which replaces this
                // engine. Until then, one plain frame.
                if (!mDrewOnce) drawPlain();
                return;
            }
            if (!mMotion || mKilled) {
                drawRest(shader);
                return;
            }
            if (!isPreview() && !director.settling() && !keyguardLocked()) {
                director.unlockStarted(frameTimeNanos);
                Logger.logDebug(LOG_TAG, "Unlock seen: keyguard_unlocked");
            }
            WallpaperDirector.Frame frame = director.frame(frameTimeNanos);
            boolean due = mLastDrawNanos < 0 || frame.fps <= 0
                || frameTimeNanos - mLastDrawNanos >= 1_000_000_000L / frame.fps - PACE_SLACK_NANOS;
            if (due) {
                draw(shader, frame);
                mLastDrawNanos = frameTimeNanos;
            }
            if (frame.fps > 0) schedule();
        }

        /** The shared-frame width: the home still is rendered this wide and the system shows its centre. */
        private int frameWidth() {
            return Math.max(mWidth, WallParallax.pickerWidthPx(mWidth));
        }

        private void draw(@NonNull RuntimeShader shader, @NonNull WallpaperDirector.Frame frame) {
            int frameW = frameWidth();
            WallpaperUniforms.apply(shader, frame, frameW, mHeight);
            LivingEffectsRenderer effects = mEffects;
            if (effects != null) {
                // The split render: the map is drawn at a quarter of the frame off this thread, and the
                // composite below samples the newest finished one (a tick behind, which does not show).
                try {
                    effects.advance(frame, frameW, mHeight);
                    effects.bindLatest(shader);
                } catch (RuntimeException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Lock effects map failed", e);
                    releaseEffects();
                    WallpaperUniforms.setEffects(shader, null, 1, 1);
                }
            }
            paint(frameW);
        }

        private void releaseEffects() {
            LivingEffectsRenderer effects = mEffects;
            mEffects = null;
            if (effects != null) effects.release();
        }

        private void drawRest(@NonNull RuntimeShader shader) {
            int frameW = frameWidth();
            WallpaperUniforms.applyRest(shader, WallpaperPaletteCapture.own(mWallpaper), frameW, mHeight);
            paint(frameW);
        }

        private void paint(int frameW) {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockHardwareCanvas();
                if (canvas == null) return;
                float offset = (frameW - mWidth) / 2f;
                canvas.save();
                canvas.translate(-offset, 0f);
                canvas.drawRect(offset, 0f, offset + mWidth, mHeight, mPaint);
                canvas.restore();
                mDrewOnce = true;
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Lock frame failed", e);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }

        private void drawPlain() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockHardwareCanvas();
                if (canvas == null) return;
                // The wallpaper's own ground, not UI: no theme reaches a wallpaper surface.
                canvas.drawColor(Color.BLACK);
                mDrewOnce = true;
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Lock plain frame failed", e);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }

        // --- the phone ---

        private boolean interactive() {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        }

        private boolean keyguardLocked() {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            return km == null || km.isKeyguardLocked();
        }

        private void listenToDevice() {
            mPowerSave = readPowerSave();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_BATTERY_LOW);
            filter.addAction(Intent.ACTION_BATTERY_OKAY);
            try {
                readBattery(registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED));
                mReceiverRegistered = true;
            } catch (RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Device receiver unavailable: " + e.getMessage());
            }
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                try {
                    mThermal = WallpaperDeviceReadings.thermal(pm.getCurrentThermalStatus());
                    PowerManager.OnThermalStatusChangedListener listener = status -> {
                        mThermal = WallpaperDeviceReadings.thermal(status);
                        updateFps();
                        schedule();
                    };
                    pm.addThermalStatusListener(getMainExecutor(), listener);
                    mThermalListener = listener;
                } catch (RuntimeException e) {
                    Logger.logWarn(LOG_TAG, "Thermal status unavailable: " + e.getMessage());
                }
            }
            updateFps();
        }

        private void stopListeningToDevice() {
            if (mReceiverRegistered) {
                try {
                    unregisterReceiver(mReceiver);
                } catch (RuntimeException ignored) {
                }
                mReceiverRegistered = false;
            }
            PowerManager.OnThermalStatusChangedListener listener = mThermalListener;
            mThermalListener = null;
            if (listener != null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                try {
                    if (pm != null) pm.removeThermalStatusListener(listener);
                } catch (RuntimeException ignored) {
                }
            }
        }

        private boolean readPowerSave() {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
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
    }
}
