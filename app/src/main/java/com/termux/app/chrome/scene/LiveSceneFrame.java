package com.termux.app.chrome.scene;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one live frame every glass surface samples in place of the blur cache's still frame while the
 * wallpaper scene is on ({@code debug.tl.scene} = contour, {@code debug.tl.scene_glass} = lowres):
 * the scene's low-resolution, blurred second pass as a hardware bitmap and the one
 * {@link BitmapShader} over it. Glass keeps its own anchoring, refraction, tint, veil and corners;
 * only the image it samples changes, and {@link #current()} returning null puts the still frame
 * back exactly as it was.
 *
 * <p>Threads: the scene thread {@link #publish}es and {@link #retire}s (one volatile write each);
 * the main thread reads {@link #current()} on every glass draw (one volatile read, no
 * allocation), keeps the rect and the list of surfaces that drew the current frame, and is the only
 * thread that invalidates them. A publish posts one coalesced runnable to the main thread, which
 * invalidates the surfaces that drew since the last one: so the launcher window repaints for the
 * scene at the glass rate, only where glass is visible, and never at the scene's own rate.</p>
 *
 * <p>The frame covers the scene layer's screen rect ({@link #rect()}), so the glass's usual aim
 * (frame rect against the surface's own origin) places it as it places the still frame. There is
 * no parallax: the scene does not pan.</p>
 */
public final class LiveSceneFrame {

    /** One published frame: the bitmap, the shader over it, and which publish it was. */
    public static final class Frame {
        @NonNull public final Bitmap bitmap;
        @NonNull public final BitmapShader shader;
        public final int generation;

        Frame(@NonNull Bitmap bitmap, @NonNull BitmapShader shader, int generation) {
            this.bitmap = bitmap;
            this.shader = shader;
            this.generation = generation;
        }
    }

    /** A glass surface that draws the frame and can be asked to draw it again. */
    public interface Surface {
        void invalidateLive();
    }

    private static final int MAX_SURFACES = 32;
    /** The blur radius is the largest one asked for in a window this long, so it follows a slider. */
    private static final long RADIUS_WINDOW_MS = 3000L;

    @Nullable private static volatile Frame sFrame;
    /** Process-wide, so a frame from a recreated pass never reads as one a surface already took. */
    @NonNull private static final java.util.concurrent.atomic.AtomicInteger sGeneration =
        new java.util.concurrent.atomic.AtomicInteger();
    private static volatile int sRadiusDp;
    private static int sWindowMaxDp;
    private static long sWindowStartMs;

    // Main thread only from here.
    private static int sLeft, sTop, sRight, sBottom;
    @Nullable private static Rect sRect;
    @NonNull private static final Surface[] sDrawn = new Surface[MAX_SURFACES];
    private static int sDrawnCount;
    @Nullable private static Handler sMain;
    @NonNull private static final AtomicBoolean sInvalPending = new AtomicBoolean();
    @NonNull private static final Runnable INVALIDATE = LiveSceneFrame::invalidateDrawn;
    /** The window the glass lives in, swept once when the live frame comes or goes. */
    @Nullable private static java.lang.ref.WeakReference<android.view.View> sRoot;
    /**
     * The next invalidation redraws the whole tree once: glass that has not drawn since the frame
     * changed source is in no list yet, and lists itself only from a draw.
     */
    private static volatile boolean sSweep;

    private LiveSceneFrame() {}

    /** The frame glass samples now, or null: glass draws its still frame. Main thread, per draw. */
    @Nullable
    public static Frame current() {
        return sFrame;
    }

    /** The screen rect the frame covers: the scene layer's. Main thread; one shared instance, read only. */
    @NonNull
    public static Rect rect() {
        Rect r = sRect;
        if (r == null) {
            r = new Rect();
            sRect = r;
        }
        r.set(sLeft, sTop, sRight, sBottom);
        return r;
    }

    /** Main thread: where the scene layer is on screen. */
    public static void setRect(int left, int top, int right, int bottom) {
        sLeft = left;
        sTop = top;
        sRight = right;
        sBottom = bottom;
    }

    /**
     * Main thread, from the blur cache: a glass surface asked for a frame at {@code blurRadiusDp}.
     * The live frame is blurred at one radius for every surface, the largest one asked for in the
     * last few seconds. Touches no Android class, so the cache stays testable.
     */
    public static void noteBlurRadius(int blurRadiusDp) {
        if (blurRadiusDp <= 0) return;
        long now = System.nanoTime() / 1_000_000L;
        if (sRadiusDp == 0) sRadiusDp = blurRadiusDp;
        if (now - sWindowStartMs > RADIUS_WINDOW_MS) {
            if (sWindowMaxDp > 0) sRadiusDp = sWindowMaxDp;
            sWindowMaxDp = 0;
            sWindowStartMs = now;
        }
        if (blurRadiusDp > sWindowMaxDp) sWindowMaxDp = blurRadiusDp;
    }

    /** The radius, in dp, the live frame is blurred at; 0 until glass has asked for any. */
    public static int blurRadiusDp() {
        return sRadiusDp;
    }

    /** Any thread: a new frame is ready; the visible glass is invalidated once, on the main thread. */
    static void publish(@NonNull Bitmap bitmap, @NonNull BitmapShader shader) {
        if (sFrame == null) sSweep = true;
        sFrame = new Frame(bitmap, shader, sGeneration.incrementAndGet());
        postInvalidate();
    }

    /** Any thread: no live frame any more (scene off, surface gone); glass goes back to the still one. */
    static void retire() {
        if (sFrame == null) return;
        sFrame = null;
        sSweep = true;
        postInvalidate();
    }

    /** Main thread: the scene is gone; glass is back on the still frame and told so now. */
    public static void clear() {
        sFrame = null;
        sSweep = true;
        invalidateDrawn();
    }

    /**
     * Main thread, from a glass draw that used the live frame: this surface is visible and is to be
     * invalidated at the next publish. A surface is listed once; the list is emptied by each publish
     * and refilled by the draws it causes.
     */
    public static void noteDrawn(@NonNull Surface surface) {
        for (int i = 0; i < sDrawnCount; i++) {
            if (sDrawn[i] == surface) return;
        }
        if (sDrawnCount < MAX_SURFACES) sDrawn[sDrawnCount++] = surface;
    }

    /** Main thread: the root of the window whose glass reads the live frame. */
    public static void setRoot(@NonNull android.view.View root) {
        java.lang.ref.WeakReference<android.view.View> ref = sRoot;
        if (ref == null || ref.get() != root) sRoot = new java.lang.ref.WeakReference<>(root);
    }

    private static void sweep(@NonNull android.view.View view) {
        view.invalidate();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) sweep(group.getChildAt(i));
        }
    }

    private static void postInvalidate() {
        if (!sInvalPending.compareAndSet(false, true)) return;
        Handler main = sMain;
        if (main == null) {
            main = new Handler(Looper.getMainLooper());
            sMain = main;
        }
        main.post(INVALIDATE);
    }

    private static void invalidateDrawn() {
        sInvalPending.set(false);
        Trace.beginSection("Scene.glass.publish");
        try {
            // An invalidation only schedules the draw, so the list is safe to walk and empty after:
            // the surfaces list themselves again from the draws it causes.
            if (sSweep) {
                sSweep = false;
                android.view.View root = sRoot == null ? null : sRoot.get();
                if (root != null) sweep(root);
            }
            int n = sDrawnCount;
            for (int i = 0; i < n; i++) {
                Surface s = sDrawn[i];
                sDrawn[i] = null;
                s.invalidateLive();
            }
            sDrawnCount = 0;
        } finally {
            Trace.endSection();
        }
    }
}
