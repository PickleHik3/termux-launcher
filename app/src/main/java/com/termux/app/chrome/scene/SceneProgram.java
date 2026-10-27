package com.termux.app.chrome.scene;

import android.graphics.RuntimeShader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * One wallpaper scene as an AGSL program: compiled once per instance, then sized, timed and fed
 * the reactions of {@link SceneEvents} through uniforms. The full-resolution layer and the
 * low-resolution glass pass each hold one, at their own scale (see {@link ScenePrograms.Factory}).
 * Scene thread only.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
interface SceneProgram {

    /** The shader to put on the full-surface paint. */
    @NonNull RuntimeShader shader();

    /** The static uniforms, only when the size or density changed; {@code width}/{@code height} at this program's scale. */
    void setSize(int width, int height, float density);

    /** The time uniform for the frame about to be drawn (uptime ms; the program wraps it). */
    void setTime(long uptimeMs);

    /** The reaction uniforms for {@code nowMs}, scaled to this program's resolution. */
    void setEvents(@NonNull SceneEvents events, long nowMs, float density);
}
