package com.termux.app.chrome.scene;

import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * One of the design-tool scenes (Contour Waves, Nebula Drift, Fractal Bloom, Aurora, Particles) as an AGSL program
 * (API 33+): the source comes from {@link DesignSceneSources}, the only uniforms are the size in
 * pixels and the time. Compiled once per instance; the full-resolution layer and the low-resolution
 * glass pass hold one each, so each sees its own {@code uResolution}.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class DesignSceneProgram implements SceneProgram {

    /**
     * The wrap of the time uniform, one hour. The designs are not periodic, so a visible jump
     * happens once per wrap; the shader is untouched to keep its look.
     */
    static final long PERIOD_MS = 3_600_000L;

    @NonNull private final RuntimeShader shader;
    private int mWidth = -1, mHeight = -1;
    private final boolean mHasDensity, mHasPalette;
    private float mSpeed = 1f;
    /** The scene clock in ms, integrated from the caller's clock at {@link #mSpeed}. */
    private double mSceneMs;
    /** The caller's clock at the last {@link #setTime}, or {@link Long#MIN_VALUE} before the first after a restart. */
    private long mLastMs = Long.MIN_VALUE;
    /** A gap longer than this between two draws (a paused layer) counts as no time at all. */
    static final long MAX_STEP_MS = 5_000L;

    /** @param source one of the {@link DesignSceneSources} constants. */
    DesignSceneProgram(@NonNull String source) {
        shader = new RuntimeShader(source);
        // AGSL throws on an undeclared uniform, so the two knobs are set only where the source declares them.
        mHasDensity = source.contains("uniform float uDensity;");
        mHasPalette = source.contains("uniform float uPalette;");
        if (mHasDensity) shader.setFloatUniform("uDensity", 0f);
        if (mHasPalette) shader.setFloatUniform("uPalette", 0f);
    }

    @NonNull @Override
    public RuntimeShader shader() {
        return shader;
    }

    /** {@code width}/{@code height} are already at this program's scale; density is unused. */
    @Override
    public void setSize(int width, int height, float density) {
        if (width == mWidth && height == mHeight) return;
        mWidth = width;
        mHeight = height;
        shader.setFloatUniform("uResolution", width, height);
    }

    /** Speed scales the clock's steps, never its total, so a change of speed does not jump the picture. */
    @Override
    public void setKnobs(float speed, float density, int palette) {
        mSpeed = LiveWallpaperChoice.clampSpeed(speed);
        if (mHasDensity) shader.setFloatUniform("uDensity", LiveWallpaperChoice.clampDensity(density));
        if (mHasPalette) shader.setFloatUniform("uPalette", LiveWallpaperChoice.clampPalette(palette));
    }

    @Override
    public void restart() {
        mSceneMs = 0d;
        mLastMs = Long.MIN_VALUE;
        shader.setFloatUniform("uTime", 0f);
    }

    /** {@code uptimeMs} is the caller's clock: only its steps count, scaled by the speed. */
    @Override
    public void setTime(long uptimeMs) {
        mSceneMs = advance(mSceneMs, mLastMs, uptimeMs, mSpeed);
        mLastMs = uptimeMs;
        shader.setFloatUniform("uTime", (float) ((mSceneMs % PERIOD_MS) / 1000d));
    }

    /** The scene clock after one step of the caller's clock; the first step (no last reading) and a backwards or stalled step add nothing. */
    static double advance(double sceneMs, long lastMs, long nowMs, float speed) {
        if (lastMs == Long.MIN_VALUE) return sceneMs;
        long step = nowMs - lastMs;
        if (step <= 0L) return sceneMs;
        return sceneMs + Math.min(step, MAX_STEP_MS) * (double) speed;
    }

    @Override
    public void setEvents(@NonNull SceneEvents events, long nowMs, float density) {
        // No reactions yet; they come with the shared input contract (project-docs/active/live-wallpapers-2026-11-05.md).
    }
}
