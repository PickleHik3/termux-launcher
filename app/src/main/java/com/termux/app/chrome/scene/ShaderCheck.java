package com.termux.app.chrome.scene;

import android.opengl.GLES20;
import android.os.Trace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Collections;
import java.util.List;

/**
 * The gate a shader passes before it becomes the wallpaper (spec decision 8): it compiles, links
 * and draws one frame offscreen on a context of its own. Call on a worker thread; nothing is kept.
 * The errors come back on the user's lines ({@link ShaderErrors}); an empty list is a pass.
 */
public final class ShaderCheck {

    /** The offscreen frame's size: enough to run every fragment path a few times, cheap to draw. */
    static final int SIZE_PX = 64;

    private ShaderCheck() {}

    @WorkerThread
    @NonNull
    public static List<ShaderErrors.Error> run(@NonNull String source) {
        Trace.beginSection("Scene.shaderCheck");
        SceneGl gl = null;
        try {
            gl = new SceneGl();
            gl.setShader(GlShader.build(source));
            ShaderInputs in = new ShaderInputs();
            in.setDate(System.currentTimeMillis());
            gl.renderScene(in, SIZE_PX, SIZE_PX, 0L);
            GLES20.glFinish();
            if (gl.outOfMemory()) {
                return Collections.singletonList(new ShaderErrors.Error(-1, "Out of graphics memory."));
            }
            return Collections.emptyList();
        } catch (GlShader.CompileException e) {
            return e.errors;
        } catch (SceneEgl.EglException e) {
            return Collections.singletonList(new ShaderErrors.Error(-1, message(e)));
        } catch (RuntimeException e) {
            return Collections.singletonList(new ShaderErrors.Error(-1, message(e)));
        } finally {
            if (gl != null) gl.release();
            Trace.endSection();
        }
    }

    @NonNull
    private static String message(@NonNull Throwable e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    /** Whether {@code errors} is a pass. */
    public static boolean passed(@Nullable List<ShaderErrors.Error> errors) {
        return errors != null && errors.isEmpty();
    }
}
