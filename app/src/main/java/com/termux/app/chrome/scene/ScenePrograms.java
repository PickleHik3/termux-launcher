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

    /** Makes a program at {@code scale} pixels per full-resolution pixel: 1, or 1/8 for the glass pass. */
    interface Factory {
        @NonNull SceneProgram create(float scale);
    }

    private ScenePrograms() {}

    /** Whether {@code value} names a scene. */
    static boolean isKind(String value) {
        return CONTOUR.equals(value) || FILAMENT.equals(value) || CONTOUR_WAVES.equals(value)
            || NEBULA.equals(value) || BLOOM.equals(value)
            || AURORA.equals(value) || PARTICLES.equals(value);
    }

    @NonNull
    static Factory factory(@NonNull String kind, @NonNull FilamentSource source) {
        if (FILAMENT.equals(kind)) return scale -> new FilamentProgram(scale, source);
        if (CONTOUR_WAVES.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.CONTOUR_WAVES);
        if (NEBULA.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.NEBULA);
        if (BLOOM.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.BLOOM);
        if (AURORA.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.AURORA);
        if (PARTICLES.equals(kind)) return scale -> new DesignSceneProgram(DesignSceneSources.PARTICLES);
        return ContourProgram::new;
    }
}
