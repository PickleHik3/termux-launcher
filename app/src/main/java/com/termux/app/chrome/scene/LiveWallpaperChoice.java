package com.termux.app.chrome.scene;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The retired Live picker's choice: a scene (or none) and its four knobs. Immutable and pure;
 * {@link #read} and {@link #write} go through the persisted preferences, which clamp on read.
 * The AGSL scenes are deleted (spec shader-video-wallpapers-2026-11-09), so there are no kinds and
 * every choice reads as none; the class stays only so the picker compiles until wave C replaces it
 * with {@link WallpaperMotion}.
 */
public final class LiveWallpaperChoice {

    /** The user-facing kinds, in the order the picker shows them: none since the scenes went. */
    @NonNull
    public static final String[] USER_KINDS = {};

    public static final float MIN_SPEED = TERMUX_APP.MIN_LIVE_WALLPAPER_SPEED;
    public static final float MAX_SPEED = TERMUX_APP.MAX_LIVE_WALLPAPER_SPEED;
    public static final float DEFAULT_SPEED = TERMUX_APP.DEFAULT_VALUE_LIVE_WALLPAPER_SPEED;
    public static final int[] RATES_HZ = {15, 30, 60};
    public static final int DEFAULT_RATE_HZ = TERMUX_APP.DEFAULT_VALUE_LIVE_WALLPAPER_RATE_HZ;
    public static final float MIN_DENSITY = TERMUX_APP.MIN_LIVE_WALLPAPER_DENSITY;
    public static final float MAX_DENSITY = TERMUX_APP.MAX_LIVE_WALLPAPER_DENSITY;
    public static final int MAX_PALETTE = TERMUX_APP.MAX_LIVE_WALLPAPER_PALETTE;

    /** The preference keys a change of the choice writes; a listener filters on these. */
    @NonNull
    public static final String[] KEYS = {
        TERMUX_APP.KEY_LIVE_WALLPAPER_SCENE, TERMUX_APP.KEY_LIVE_WALLPAPER_SPEED,
        TERMUX_APP.KEY_LIVE_WALLPAPER_RATE_HZ, TERMUX_APP.KEY_LIVE_WALLPAPER_DENSITY,
        TERMUX_APP.KEY_LIVE_WALLPAPER_PALETTE,
    };

    /** The scene kind, or null for none. */
    @Nullable public final String scene;
    public final float speed;
    public final int rateHz;
    public final float density;
    public final int palette;

    public LiveWallpaperChoice(@Nullable String scene, float speed, int rateHz, float density, int palette) {
        this.scene = parseScene(scene);
        this.speed = clampSpeed(speed);
        this.rateHz = clampRate(rateHz);
        this.density = clampDensity(density);
        this.palette = clampPalette(palette);
    }

    /** No scene, default knobs. */
    @NonNull
    public static LiveWallpaperChoice none() {
        return new LiveWallpaperChoice(null, DEFAULT_SPEED, DEFAULT_RATE_HZ, 0f, 0);
    }

    /** The same knobs on another scene. */
    @NonNull
    public LiveWallpaperChoice withScene(@Nullable String kind) {
        return new LiveWallpaperChoice(kind, speed, rateHz, density, palette);
    }

    @NonNull
    public LiveWallpaperChoice withSpeed(float value) {
        return new LiveWallpaperChoice(scene, value, rateHz, density, palette);
    }

    @NonNull
    public LiveWallpaperChoice withRateHz(int hz) {
        return new LiveWallpaperChoice(scene, speed, hz, density, palette);
    }

    @NonNull
    public LiveWallpaperChoice withDensity(float value) {
        return new LiveWallpaperChoice(scene, speed, rateHz, value, palette);
    }

    @NonNull
    public LiveWallpaperChoice withPalette(int value) {
        return new LiveWallpaperChoice(scene, speed, rateHz, density, value);
    }

    // ---- Persistence ----------------------------------------------------------------------------

    @NonNull
    public static LiveWallpaperChoice read(@NonNull TermuxAppSharedPreferences prefs) {
        return new LiveWallpaperChoice(prefs.getLiveWallpaperSceneRaw(), prefs.getLiveWallpaperSpeed(),
            prefs.getLiveWallpaperRateHz(), prefs.getLiveWallpaperDensity(), prefs.getLiveWallpaperPalette());
    }

    public void write(@NonNull TermuxAppSharedPreferences prefs) {
        prefs.setLiveWallpaperSceneRaw(scene);
        prefs.setLiveWallpaperSpeed(speed);
        prefs.setLiveWallpaperRateHz(rateHz);
        prefs.setLiveWallpaperDensity(density);
        prefs.setLiveWallpaperPalette(palette);
    }

    /** Whether {@code key} is one of the choice's preference keys. */
    public static boolean isChoiceKey(@Nullable String key) {
        if (key == null) return false;
        for (String k : KEYS) if (k.equals(key)) return true;
        return false;
    }

    // ---- Support and per-scene capabilities -----------------------------------------------------

    /** Moving wallpapers need OpenGL ES 3 and hardware-buffer bitmaps: API 29 and later. Below it nothing installs. */
    public static boolean isSupported() {
        return isSupported(Build.VERSION.SDK_INT);
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.Q;
    }

    /** The scene to draw: {@link #scene} on a supported device, null otherwise or with none chosen. */
    @Nullable
    public String effectiveScene() {
        return isSupported() ? scene : null;
    }

    public static boolean supportsDensity(@Nullable String kind) {
        return false;
    }

    public static boolean supportsPalette(@Nullable String kind) {
        return false;
    }

    /** The palette names of {@code kind}: none since the scenes went. */
    @NonNull
    public static @StringRes int[] paletteNames(@Nullable String kind) {
        return new int[0];
    }

    // ---- Parsing and clamping -------------------------------------------------------------------

    /** The user-facing kind {@code value} names, or null (none, empty, unknown, or a debug-only kind). */
    @Nullable
    public static String parseScene(@Nullable String value) {
        if (value == null) return null;
        String v = value.trim();
        for (String kind : USER_KINDS) if (kind.equals(v)) return kind;
        return null;
    }

    public static float clampSpeed(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return DEFAULT_SPEED;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, v));
    }

    /** 15, 30 or 60; anything else is 30. */
    public static int clampRate(int hz) {
        return hz == 15 || hz == 60 ? hz : DEFAULT_RATE_HZ;
    }

    public static float clampDensity(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        return Math.max(MIN_DENSITY, Math.min(MAX_DENSITY, v));
    }

    public static int clampPalette(int v) {
        return Math.max(0, Math.min(MAX_PALETTE, v));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LiveWallpaperChoice)) return false;
        LiveWallpaperChoice c = (LiveWallpaperChoice) o;
        return java.util.Objects.equals(scene, c.scene) && speed == c.speed && rateHz == c.rateHz
            && density == c.density && palette == c.palette;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(scene, speed, rateHz, density, palette);
    }
}
