package com.termux.app.chrome.scene;

import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Coastal Sunset as one AGSL program (API 33+): {@link CoastalSunsetSource} over the bundled photo
 * of a {@link SceneArtwork}, sampled with linear filtering. Like {@link DesignSceneProgram}: one
 * compile per instance, size only when it changes, the scene clock integrated from the caller's at
 * the speed knob. The motion uniform ramps from 0 over the first {@link #MOTION_RAMP_MS} of scene
 * time, so a still frame (t = 0: the thumbnail, the saved still, the battery hold) shows the
 * untouched photo and a starting scene does not jump.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class CoastalSunsetProgram implements SceneProgram {

    /** The wrap of the time uniform, one hour (the waves are not periodic; see {@link DesignSceneProgram}). */
    static final long PERIOD_MS = 3_600_000L;
    /** Scene time over which the motion fades in from the still photo. */
    static final float MOTION_RAMP_MS = 1_500f;

    @NonNull private final RuntimeShader mShader = new RuntimeShader(CoastalSunsetSource.AGSL);
    private int mWidth = -1, mHeight = -1;
    private float mSpeed = 1f;
    private double mSceneMs;
    private long mLastMs = Long.MIN_VALUE;
    private float mMotion = -1f;

    CoastalSunsetProgram(@NonNull SceneArtwork artwork) {
        BitmapShader photo = new BitmapShader(artwork.bitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        photo.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        mShader.setInputShader("artwork", photo);
        mShader.setFloatUniform("imageSize", SceneArtwork.COASTAL_SUNSET_WIDTH, SceneArtwork.COASTAL_SUNSET_HEIGHT);
        mShader.setFloatUniform("time", 0f);
        setMotion(0f);
    }

    @NonNull @Override
    public RuntimeShader shader() {
        return mShader;
    }

    @Override
    public void setSize(int width, int height, float density) {
        if (width == mWidth && height == mHeight) return;
        mWidth = width;
        mHeight = height;
        mShader.setFloatUniform("resolution", width, height);
    }

    @Override
    public void setKnobs(float speed, float density, int palette) {
        mSpeed = LiveWallpaperChoice.clampSpeed(speed);
    }

    @Override
    public void restart() {
        mSceneMs = 0d;
        mLastMs = Long.MIN_VALUE;
        mShader.setFloatUniform("time", 0f);
        setMotion(0f);
    }

    @Override
    public void setTime(long uptimeMs) {
        mSceneMs = DesignSceneProgram.advance(mSceneMs, mLastMs, uptimeMs, mSpeed);
        mLastMs = uptimeMs;
        mShader.setFloatUniform("time", (float) ((mSceneMs % PERIOD_MS) / 1000d));
        setMotion(motionFor(mSceneMs));
    }

    /** 0 at scene time 0, 1 from {@link #MOTION_RAMP_MS} on. */
    static float motionFor(double sceneMs) {
        return (float) Math.max(0d, Math.min(1d, sceneMs / MOTION_RAMP_MS));
    }

    private void setMotion(float m) {
        if (m == mMotion) return;
        mMotion = m;
        mShader.setFloatUniform("motion", m);
    }

    @Override
    public void setEvents(@NonNull SceneEvents events, long nowMs, float density) {
        // No reactions: the photo does not answer the terminal.
    }
}
