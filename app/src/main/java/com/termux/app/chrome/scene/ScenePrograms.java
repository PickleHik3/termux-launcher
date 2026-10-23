package com.termux.app.chrome.scene;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/** Which program a {@code debug.tl.scene} value runs, and how one is made at a given scale. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
final class ScenePrograms {

    static final String CONTOUR = "contour";
    static final String FILAMENT = "filament";

    /** Makes a program at {@code scale} pixels per full-resolution pixel: 1, or 1/8 for the glass pass. */
    interface Factory {
        @NonNull SceneProgram create(float scale);
    }

    private ScenePrograms() {}

    @NonNull
    static Factory factory(@NonNull String kind, @NonNull FilamentSource source) {
        if (FILAMENT.equals(kind)) return scale -> new FilamentProgram(scale, source);
        return ContourProgram::new;
    }
}
