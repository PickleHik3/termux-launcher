package com.termux.app.chrome.scene;

import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * One of the design-tool scenes (Contour Waves, Nebula Drift, Fractal Bloom) as an AGSL program
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

    /** @param source one of the {@link DesignSceneSources} constants. */
    DesignSceneProgram(@NonNull String source) {
        shader = new RuntimeShader(source);
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

    @Override
    public void setTime(long uptimeMs) {
        shader.setFloatUniform("uTime", (uptimeMs % PERIOD_MS) / 1000f);
    }

    @Override
    public void setEvents(@NonNull SceneEvents events, long nowMs, float density) {
        // No reactions yet; they come with the shared input contract (project-docs/active/live-wallpapers-2026-11-05.md).
    }
}
