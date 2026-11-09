package com.termux.app.chrome.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The still of a shader: its frame at {@code iTime = 0}, rendered offscreen on a context of its
 * own (FBO + {@code glReadPixels}) as a software ARGB_8888 bitmap that
 * {@code WallpaperManager.setBitmap} accepts (Home and Lock show it). Call on a worker thread: the
 * GPU wait can take a while. Nothing is kept: the context, its textures and the read buffer are
 * let go before it returns. The launcher uniforms (colours, keyboard, page) are at their defaults.
 */
public final class SceneStill {

    /** The longest side, in pixels, a still is rendered at (GPU texture limits and memory). */
    static final int MAX_SIDE_PX = 4096;

    private SceneStill() {}

    /** The size actually rendered: both sides at least 1 and the longer at most {@link #MAX_SIDE_PX}, aspect kept. */
    @NonNull
    static int[] fitSize(int widthPx, int heightPx) {
        int w = Math.max(1, widthPx), h = Math.max(1, heightPx);
        int longest = Math.max(w, h);
        if (longest > MAX_SIDE_PX) {
            float k = MAX_SIDE_PX / (float) longest;
            w = Math.max(1, Math.round(w * k));
            h = Math.max(1, Math.round(h * k));
        }
        return new int[] {w, h};
    }

    /**
     * The still of the active choice ({@link ShaderStore#readMotion}): for a video or GIF a frame
     * through {@link VideoStill}; null when it is neither a stored shader nor a video, the device cannot run shaders (below API 29), or the shader does not build.
     *
     * @throws IllegalStateException when the GPU could not draw it
     */
    @WorkerThread
    @Nullable
    public static Bitmap renderActive(@NonNull Context context, int widthPx, int heightPx) {
        return renderActive(context, widthPx, heightPx, null);
    }

    /** As above, with the look the caller resolved (from an activity); null resolves from {@code context}. */
    @WorkerThread
    @Nullable
    public static Bitmap renderActive(@NonNull Context context, int widthPx, int heightPx,
                                      @Nullable ShaderLook look) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
        if (prefs == null) return null;
        WallpaperMotion motion = ShaderStore.readMotion(prefs.getSharedPreferences());
        if (motion.kind == WallpaperMotion.Kind.VIDEO && motion.id != null
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File video = VideoImport.file(context, motion.id);
            return video.isFile() ? VideoStill.render(video, widthPx, heightPx) : null;
        }
        if (motion.kind != WallpaperMotion.Kind.SHADER || motion.id == null) return null;
        String source = ShaderStore.readText(ShaderStore.sourceFile(context, motion.id));
        if (source == null) return null;
        ShaderStore.Meta meta = ShaderStore.readMeta(context, motion.id);
        return render(source, ShaderStore.textureFile(context, motion.id, meta), widthPx, heightPx,
            look != null ? look : ShaderLook.resolve(context));
    }

    /**
     * @param channel0 the iChannel0 image, or null for none
     * @param look     tlDark and tlColor0..2, or null to leave them 0
     * @return a mutable software ARGB_8888 bitmap, or null below API 29 or when the shader does not build
     * @throws IllegalStateException when the GPU could not draw it
     */
    @WorkerThread
    @Nullable
    public static Bitmap render(@NonNull String source, @Nullable File channel0, int widthPx, int heightPx,
                                @Nullable ShaderLook look) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        int[] size = fitSize(widthPx, heightPx);
        int width = size[0], height = size[1];
        Trace.beginSection("Scene.still");
        SceneGl gl = null;
        try {
            try {
                gl = new SceneGl();
            } catch (SceneEgl.EglException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            try {
                gl.setShader(GlShader.build(source));
            } catch (GlShader.CompileException e) {
                return null;
            }
            if (channel0 != null) gl.setChannel(0, new ImageChannel(channel0));
            ShaderInputs in = new ShaderInputs();
            in.setDate(System.currentTimeMillis());
            in.pageOffset = 0f;
            in.battery = 1f;
            if (look != null) look.applyTo(in);
            gl.renderScene(in, width, height, 0L);
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
            gl.readScene(pixels);
            if (gl.outOfMemory()) throw new IllegalStateException("out of graphics memory drawing the still");
            return toBitmap(pixels, width, height);
        } finally {
            if (gl != null) gl.release();
            Trace.endSection();
        }
    }

    /** GL rows are bottom first: flipped into a top-first bitmap, alpha forced opaque. */
    @NonNull
    private static Bitmap toBitmap(@NonNull ByteBuffer glPixels, int width, int height) {
        int row = width * 4;
        byte[] src = new byte[row * height];
        glPixels.position(0);
        glPixels.get(src);
        byte[] dst = new byte[src.length];
        for (int y = 0; y < height; y++) {
            System.arraycopy(src, (height - 1 - y) * row, dst, y * row, row);
        }
        for (int i = 3; i < dst.length; i += 4) dst[i] = (byte) 0xff;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(dst));
        return bitmap;
    }
}
