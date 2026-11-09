package com.termux.app.chrome.scene;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One OpenGL ES 3 context and the EGL surfaces drawn with it, all on the thread that made it.
 * The context is current on a 1x1 pbuffer whenever no window surface is (the offscreen work and
 * the teardown need a current context). ES 2 is only tried to say why a device cannot run shaders.
 */
final class SceneEgl {

    /** EGL_OPENGL_ES3_BIT_KHR. */
    private static final int EGL_OPENGL_ES3_BIT = 0x40;

    /** Why a context could not be made. */
    static final class EglException extends RuntimeException {
        EglException(@NonNull String message) {
            super(message);
        }
    }

    @NonNull final EGLDisplay display;
    @NonNull final EGLConfig config;
    @NonNull final EGLContext context;
    @NonNull private final EGLSurface mPbuffer;
    @Nullable private EGLSurface mCurrent;
    private boolean mReleased;

    SceneEgl() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) throw new EglException("no EGL display");
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw new EglException("eglInitialize failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
        EGLConfig config = chooseConfig(display, EGL_OPENGL_ES3_BIT);
        if (config == null) {
            boolean es2 = chooseConfig(display, EGL14.EGL_OPENGL_ES2_BIT) != null;
            throw new EglException(es2 ? "this device has OpenGL ES 2 only; shaders need ES 3"
                : "no EGL config for OpenGL ES");
        }
        EGLContext context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
            new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            throw new EglException("no OpenGL ES 3 context: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
        EGLSurface pbuffer = EGL14.eglCreatePbufferSurface(display, config,
            new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        if (pbuffer == null || pbuffer == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, context);
            throw new EglException("no pbuffer surface: 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
        this.display = display;
        this.config = config;
        this.context = context;
        mPbuffer = pbuffer;
        if (!makeCurrent(pbuffer)) {
            release();
            throw new EglException("eglMakeCurrent failed");
        }
    }

    @Nullable
    private static EGLConfig chooseConfig(@NonNull EGLDisplay display, int renderable) {
        int[] attribs = {
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, renderable,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] <= 0) {
            return null;
        }
        return configs[0];
    }

    /** A window surface over {@code surface} (a {@code Surface}); null when EGL refuses it. */
    @Nullable
    EGLSurface windowSurface(@NonNull Object surface) {
        EGLSurface s = EGL14.eglCreateWindowSurface(display, config, surface,
            new int[] {EGL14.EGL_NONE}, 0);
        return s == null || s == EGL14.EGL_NO_SURFACE ? null : s;
    }

    boolean makeCurrent(@NonNull EGLSurface surface) {
        if (surface == mCurrent) return true;
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            mCurrent = null;
            return false;
        }
        mCurrent = surface;
        return true;
    }

    /** Current on the pbuffer: no window surface is in use. */
    boolean makeCurrentOffscreen() {
        return makeCurrent(mPbuffer);
    }

    /** Presents the window surface; returns {@link EGL14#EGL_SUCCESS} or the EGL error. */
    int swap(@NonNull EGLSurface surface) {
        if (EGL14.eglSwapBuffers(display, surface)) return EGL14.EGL_SUCCESS;
        return EGL14.eglGetError();
    }

    void destroySurface(@Nullable EGLSurface surface) {
        if (surface == null || mReleased) return;
        if (surface == mCurrent) makeCurrentOffscreen();
        EGL14.eglDestroySurface(display, surface);
    }

    void release() {
        if (mReleased) return;
        mReleased = true;
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        mCurrent = null;
        EGL14.eglDestroySurface(display, mPbuffer);
        EGL14.eglDestroyContext(display, context);
        EGL14.eglReleaseThread();
        // The display is not terminated: other EGL users in the process share it.
    }
}
