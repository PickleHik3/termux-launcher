package com.termux.app.chrome.scene;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/** Which program a {@code debug.tl.scene} value runs, and how one is made at a given scale. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class ScenePrograms {

    static final String CONTOUR = "contour";
    static final String FILAMENT = "filament";
    static final String CONTOUR_WAVES = "contour-waves";
    static final String NEBULA = "nebula";
    static final String BLOOM = "bloom";
    static final String AURORA = "aurora";
    static final String PARTICLES = "particles";
    static final String COASTAL_SUNSET = "coastal-sunset";

    /** Makes a program at {@code scale} pixels per full-resolution pixel: 1, or 1/8 for the glass pass. */
    interface Factory {
        @NonNull SceneProgram create(float scale);
    }

    /** The debug override of the render scale: {@code 1} or {@code 0.5}; unset follows {@link #renderScale}. */
    static final String PROP_SCALE = "debug.tl.scene_scale";

    private ScenePrograms() {}

    /**
     * Pixels per screen pixel, each way, that a kind's surface buffer is drawn at: the soft scenes
     * (Aurora, Nebula Drift, Fractal Bloom) at half, thin lines and crisp points at full.
     */
    static float renderScale(String kind) {
        if (NEBULA.equals(kind) || BLOOM.equals(kind) || AURORA.equals(kind)) return 0.5f;
        return 1f;
    }

    /** The scale to use for {@code kind} given the {@link #PROP_SCALE} value: {@code 1} or {@code 0.5} win, anything else is the kind's own. */
    static float resolveScale(String kind, String override) {
        if (override != null) {
            String v = override.trim();
            if ("1".equals(v) || "1.0".equals(v)) return 1f;
            if ("0.5".equals(v) || ".5".equals(v)) return 0.5f;
        }
        return renderScale(kind);
    }

    /** A buffer edge for a view edge of {@code px} at {@code scale}: rounded, at least 1. */
    static int bufferSize(int px, float scale) {
        return Math.max(1, Math.round(px * scale));
    }

    /** Whether {@code value} names a scene. */
    static boolean isKind(String value) {
        return CONTOUR.equals(value) || FILAMENT.equals(value) || CONTOUR_WAVES.equals(value)
            || NEBULA.equals(value) || BLOOM.equals(value)
            || AURORA.equals(value) || PARTICLES.equals(value) || COASTAL_SUNSET.equals(value);
    }

    @NonNull
    static Factory factory(@NonNull String kind, @NonNull FilamentSource source,
                           @NonNull SceneArtwork artwork) {
        if (FILAMENT.equals(kind)) return scale -> new FilamentProgram(scale, source);
        if (CONTOUR_WAVES.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.CONTOUR_WAVES);
        if (NEBULA.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.NEBULA);
        if (BLOOM.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.BLOOM);
        if (AURORA.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.AURORA);
        if (PARTICLES.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.PARTICLES);
        if (COASTAL_SUNSET.equals(kind)) return scale -> new CoastalSunsetProgram(artwork);
        return ContourProgram::new;
    }
}
