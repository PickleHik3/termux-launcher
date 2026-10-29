package com.termux.app.setup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.AnimationSpeed;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.app.settings.LowMemoryPreset;
import com.termux.app.settings.UseAsSetting;
import com.termux.app.wall.UseAs;
import com.termux.app.x11.GuiAppsSetupGuide;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The choices the wizard holds until Finish, and the snapshot taken when it opened that
 * "Keep my current setup" goes back to. Each wave adds its keys to both.
 */
final class SetupState {

    /** Whether Finish leaves a Setup card for the Linux desktop apps (wave 3 reads it). */
    static final String KEY_WANTS_DESKTOP_APPS = "setup_wants_desktop_apps";
    /** Whether Finish leaves a Setup card for on-device AI (wave 4 reads it). */
    static final String KEY_WANTS_AI = "setup_wants_ai";

    /** The values a section can change, in one place so the snapshot and the wizard share them. */
    static final class Values {
        boolean multiplexer;
        boolean statusShown;
        boolean autotiling;
        boolean focusGrows;
        boolean desktopApps;
        boolean ai;
        boolean fancierGlass;
        boolean motionLight;
        boolean parallax;
        boolean lazy;
        boolean lowMemory;
        @NonNull String animationSpeed = AnimationSpeed.NORMAL;

        @NonNull
        Values copy() {
            Values v = new Values();
            v.multiplexer = multiplexer;
            v.statusShown = statusShown;
            v.autotiling = autotiling;
            v.focusGrows = focusGrows;
            v.desktopApps = desktopApps;
            v.ai = ai;
            v.fancierGlass = fancierGlass;
            v.motionLight = motionLight;
            v.parallax = parallax;
            v.lazy = lazy;
            v.lowMemory = lowMemory;
            v.animationSpeed = animationSpeed;
            return v;
        }
    }

    /** The values the wizard owns, as they stood. */
    static final class Snapshot {
        @NonNull final UseAs useAs;
        @NonNull final String startIn;
        @NonNull final Values values;

        Snapshot(@NonNull UseAs useAs, @NonNull String startIn, @NonNull Values values) {
            this.useAs = useAs;
            this.startIn = startIn;
            this.values = values;
        }
    }

    @NonNull private final Context mContext;
    @NonNull private final Snapshot mSnapshot;
    @NonNull private final Values mValues;
    private final boolean mUpdater;
    @NonNull private UseAs mUseAs;
    @NonNull private DeviceTier.Detected mDetected;
    private boolean mTouched;
    private boolean mGpuRead;
    @Nullable private Runnable mDetectedListener;

    SetupState(@NonNull Context context, boolean updater) {
        mContext = context;
        mUpdater = updater;
        mDetected = DeviceTier.read(context, false);
        mSnapshot = new Snapshot(UseAsSetting.read(context), readStartIn(context),
            readValues(context));
        mUseAs = mSnapshot.useAs;
        mValues = mSnapshot.values.copy();
        if (!updater)
            applyTierDefaults();
        else
            applyTierToUnstored();
        readGpuAsync();
    }

    @NonNull
    UseAs useAs() {
        return mUseAs;
    }

    void setUseAs(@NonNull UseAs useAs) {
        mUseAs = useAs;
    }

    /** The choices of the later sections. A section edits these directly and calls {@link #touch}. */
    @NonNull
    Values values() {
        return mValues;
    }

    /** A person changed something: a late GPU reading no longer re-picks the defaults. */
    void touch() {
        mTouched = true;
    }

    @NonNull
    DeviceTier.Detected detected() {
        return mDetected;
    }

    /** Called on the main thread when the GPU reading changed what the Power screen shows. */
    void setDetectedListener(@Nullable Runnable listener) {
        mDetectedListener = listener;
    }

    /** The Start in Finish will leave: the workspace for Terminal, the stored one for Home. */
    @NonNull
    String startIn() {
        return mUseAs == UseAs.TERMINAL ? TERMUX_APP.LAUNCHER_START_IN_WORKSPACE
            : mSnapshot.startIn;
    }

    /** Whether Finish moves Start in: only Terminal does. */
    boolean startInChanges() {
        return mUseAs == UseAs.TERMINAL;
    }

    @NonNull
    Snapshot snapshot() {
        return mSnapshot;
    }

    /** Writes every choice as Finish does. */
    void apply() {
        // Only a changed Use as is written: the same value would still refresh the wall.
        if (mUseAs != mSnapshot.useAs)
            UseAsSetting.write(mContext, mUseAs);
        if (mUseAs == UseAs.TERMINAL) {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mContext);
            if (prefs != null)
                prefs.setLauncherStartIn(TERMUX_APP.LAUNCHER_START_IN_WORKSPACE);
        }
        writeValues(mContext, mValues, true);
    }

    /** Puts back what the snapshot holds, writing only what moved. */
    void restore(@NonNull Snapshot snapshot) {
        if (UseAsSetting.read(mContext) != snapshot.useAs)
            UseAsSetting.write(mContext, snapshot.useAs);
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mContext);
        if (prefs != null && !prefs.getLauncherStartIn().equals(snapshot.startIn))
            prefs.setLauncherStartIn(snapshot.startIn);
        writeValues(mContext, snapshot.values, false);
        mUseAs = snapshot.useAs;
    }

    // ---- tier ------------------------------------------------------------------------------

    /** A fresh install: the tier picks every Power, AI and Panes default. */
    private void applyTierDefaults() {
        DeviceTier.Defaults d = DeviceTier.defaults(mDetected.tier);
        mValues.ai = d.ai;
        mValues.fancierGlass = d.fancierGlass;
        mValues.motionLight = d.motionLight;
        mValues.parallax = d.parallax;
        mValues.lazy = d.lazy;
        mValues.lowMemory = d.lowMemory;
        mValues.animationSpeed = d.animationSpeed;
        mValues.autotiling = d.autotiling;
    }

    /** An updater keeps what is stored; only the AI choice, which is not stored yet, follows the tier. */
    private void applyTierToUnstored() {
        SharedPreferences store = store(mContext);
        if (store == null || !store.contains(KEY_WANTS_AI))
            mValues.ai = DeviceTier.defaults(mDetected.tier).ai;
    }

    private void readGpuAsync() {
        Thread thread = new Thread(() -> {
            DeviceTier.Detected detected;
            try {
                detected = DeviceTier.read(mContext, true);
            } catch (RuntimeException e) {
                return;
            }
            new Handler(Looper.getMainLooper()).post(() -> onGpuRead(detected));
        }, "setup-device-tier");
        thread.setDaemon(true);
        thread.start();
    }

    private void onGpuRead(@NonNull DeviceTier.Detected detected) {
        mGpuRead = true;
        boolean tierMoved = detected.tier != mDetected.tier;
        mDetected = detected;
        if (tierMoved && !mTouched) {
            if (!mUpdater)
                applyTierDefaults();
            else
                applyTierToUnstored();
        }
        if (mDetectedListener != null)
            mDetectedListener.run();
    }

    boolean gpuRead() {
        return mGpuRead;
    }

    // ---- stored values ----------------------------------------------------------------------

    @Nullable
    private static SharedPreferences store(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        return prefs == null ? null : prefs.getSharedPreferences();
    }

    @NonNull
    private static Values readValues(@NonNull Context context) {
        Values v = new Values();
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        if (prefs == null)
            return v;
        v.multiplexer = !prefs.isCompatibilityModeEnabled();
        PlaceLayoutStore places = new PlaceLayoutStore(prefs);
        v.statusShown = !places.slot(PlaceOrientation.PORTRAIT, Element.STATUS).hidden;
        v.autotiling = prefs.isDwindleDefaultLayoutEnabled();
        v.focusGrows = prefs.isFocusedPaneGrowsEnabled();
        v.fancierGlass = prefs.isFancierGlassEnabled();
        v.motionLight = prefs.isFancierGlassMotionLightEnabled();
        v.parallax = prefs.isWallpaperParallaxEnabled();
        v.lazy = prefs.isLazyModeEnabled();
        v.lowMemory = prefs.isLowMemoryPresetEnabled();
        v.animationSpeed = AnimationSpeed.get(context);
        SharedPreferences store = prefs.getSharedPreferences();
        v.desktopApps = store != null && store.contains(KEY_WANTS_DESKTOP_APPS)
            ? store.getBoolean(KEY_WANTS_DESKTOP_APPS, false)
            : GuiAppsSetupGuide.isSetUp(context);
        v.ai = store != null && store.getBoolean(KEY_WANTS_AI, false);
        return v;
    }

    /**
     * Writes what differs from what is stored now. Low-memory goes first: turning it on changes
     * other keys, and the explicit choices below it then win.
     *
     * @param applying Finish (true) writes the Panes sub-choices only with the Multiplexer on; a
     *                 restore writes them whenever they moved
     */
    private static void writeValues(@NonNull Context context, @NonNull Values v, boolean applying) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        if (prefs == null)
            return;
        if (prefs.isLowMemoryPresetEnabled() != v.lowMemory) {
            if (v.lowMemory)
                LowMemoryPreset.turnOn(prefs);
            else
                LowMemoryPreset.turnOff(prefs);
        }
        if (prefs.isFancierGlassEnabled() != v.fancierGlass)
            prefs.setFancierGlassEnabled(v.fancierGlass);
        if (prefs.isFancierGlassMotionLightEnabled() != v.motionLight)
            prefs.setFancierGlassMotionLightEnabled(v.motionLight);
        if (prefs.isWallpaperParallaxEnabled() != v.parallax)
            prefs.setWallpaperParallaxEnabled(v.parallax);
        if (prefs.isLazyModeEnabled() != v.lazy)
            prefs.setLazyModeEnabled(v.lazy);
        if (!AnimationSpeed.get(context).equals(v.animationSpeed))
            AnimationSpeed.set(context, v.animationSpeed);

        if (prefs.isCompatibilityModeEnabled() == v.multiplexer)
            prefs.setCompatibilityModeEnabled(!v.multiplexer);
        if (v.multiplexer || !applying) {
            if (prefs.isDwindleDefaultLayoutEnabled() != v.autotiling)
                prefs.setDwindleDefaultLayoutEnabled(v.autotiling);
            if (prefs.isFocusedPaneGrowsEnabled() != v.focusGrows)
                prefs.setFocusedPaneGrowsEnabled(v.focusGrows);
            PlaceLayoutStore places = new PlaceLayoutStore(prefs);
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                Slot slot = places.slot(orientation, Element.STATUS);
                if (slot.hidden == v.statusShown)
                    places.setSlot(orientation, Element.STATUS, slot.withHidden(!v.statusShown));
            }
        }

        SharedPreferences store = prefs.getSharedPreferences();
        if (store != null) {
            SharedPreferences.Editor editor = store.edit();
            boolean changed = false;
            if (!store.contains(KEY_WANTS_DESKTOP_APPS)
                || store.getBoolean(KEY_WANTS_DESKTOP_APPS, false) != v.desktopApps) {
                editor.putBoolean(KEY_WANTS_DESKTOP_APPS, v.desktopApps);
                changed = true;
            }
            if (!store.contains(KEY_WANTS_AI) || store.getBoolean(KEY_WANTS_AI, false) != v.ai) {
                editor.putBoolean(KEY_WANTS_AI, v.ai);
                changed = true;
            }
            if (changed)
                editor.apply();
        }
    }

    @NonNull
    private static String readStartIn(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        return prefs == null ? TERMUX_APP.DEFAULT_LAUNCHER_START_IN : prefs.getLauncherStartIn();
    }
}
