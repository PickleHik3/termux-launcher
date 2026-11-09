package com.termux.app.chrome.scene;

/**
 * A texture source the scene samples: Shadertoy's {@code iChannelN}. Every method runs on the scene
 * thread with the GL context current, except where noted.
 *
 * <p>Life: {@link #attach} once (makes the texture), then {@link #update} once per drawn frame,
 * {@link #pause}/{@link #resume} as the layer stops and starts drawing, {@link #release} when the
 * layer or the context goes (a lost context makes a new channel; release must then not touch GL
 * beyond deleting its own names).</p>
 *
 * <p>Implementations: {@link ImageChannel} (a still image, {@code GL_TEXTURE_2D}). Wave B adds the
 * video and GIF channel: a {@code SurfaceTexture} over an {@code GL_TEXTURE_EXTERNAL_OES} texture
 * ({@link #external} true) that {@code MediaPlayer} or an {@code AnimatedImageDrawable} drawn on its
 * {@code Surface} feeds; {@link #update} calls {@code updateTexImage} when a frame is waiting. An
 * external texture can be sampled only by a program that declares {@code samplerExternalOES}
 * (wave B's passthrough program, spec decision 2); the renderer binds it to that target.</p>
 */
public interface SceneChannel {

    /** Makes the texture; the context is current. */
    void attach();

    /** The GL texture name, valid after {@link #attach}. */
    int textureId();

    /** {@code GL_TEXTURE_EXTERNAL_OES} when true, {@code GL_TEXTURE_2D} otherwise. */
    boolean external();

    /** Once per drawn frame: brings the texture up to date; true when a new frame arrived. */
    boolean update(long frameTimeNanos);

    /**
     * Writes the matrix that maps a target pixel's uv (0..1, origin bottom-left) to this channel's
     * texture coordinate, with the content centre-cropped to cover a {@code targetW} x
     * {@code targetH} target and its rotation undone; false (and {@code out16} untouched) for a
     * channel that has none.
     */
    default boolean transform(@androidx.annotation.NonNull float[] out16, int targetW, int targetH) {
        return false;
    }

    /** The content's size in pixels; 0 until known. */
    int width();

    int height();

    /** The layer stopped drawing (covered, screen off, a hold): a video stops at its position. */
    void pause();

    /** The layer draws again: a video goes on from where it stopped. */
    void resume();

    /** Frees the texture and anything feeding it. */
    void release();
}
