package com.termux.app.fragments.settings.termux;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * The GPU's renderer string (e.g. "Adreno (TM) 750", "Mali-G715"), read once from a throwaway
 * 1×1 pbuffer GL context and cached for the process. Nothing else names the GPU without a GL
 * context; this costs a few tens of milliseconds the first time, so call it off the main thread.
 * {@code null} when EGL is unavailable or refuses (emulators without GL, Robolectric).
 */
final class TaiGpuName {
    private static volatile boolean read;
    @Nullable private static volatile String cached;

    private TaiGpuName() {
    }

    @Nullable
    @WorkerThread
    static String get() {
        if (read) return cached;
        synchronized (TaiGpuName.class) {
            if (!read) {
                cached = query();
                read = true;
            }
            return cached;
        }
    }

    @Nullable
    private static String query() {
        EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == null || display == EGL14.EGL_NO_DISPLAY) return null;
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null;
            int[] attributes = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] < 1) return null;
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            if (context == null || context == EGL14.EGL_NO_CONTEXT) return null;
            surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return null;
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null;
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            return renderer == null || renderer.trim().isEmpty() ? null : renderer.trim();
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return null;
        } finally {
            try {
                if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
                    if (context != null && context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                    // No eglTerminate: the default display is shared with the UI's renderer.
                }
            } catch (RuntimeException | UnsatisfiedLinkError ignored) {
            }
        }
    }
}
