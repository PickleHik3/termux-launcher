package com.termux.app.chrome.wallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Binds the uniform contract every built-in declares (see {@link MomentAgsl#HEAD}). The one place
 * that names {@link RuntimeShader} for the generated backgrounds, so the registry and the
 * background classes stay loadable on API 26.
 *
 * <p>Moment mapping: for PANE_OPEN, PANE_CLOSE and BELL the rect is (x, y, x + w, y + h); for
 * TOUCH the point is (x, y) in rect.xy; for PAGE_CHANGE the direction x goes in rect.x. State is
 * (kind, progress). No allocation beyond the varargs the framework itself takes.</p>
 *
 * <p>A {@link LivingStill} also gets its five child shaders (the photo, depth and the three masks,
 * linear-filtered {@link BitmapShader}s over the process-wide decoded {@link LivingStillTextures})
 * and its recipe uniforms when the shader is built; each frame then adds the cover mapping (which
 * depends on the frame size) and the focus.</p>
 */
@RequiresApi(33)
public final class WallpaperUniforms {

    private WallpaperUniforms() {}

    private static final String[] PALETTE = {"uPalette0", "uPalette1", "uPalette2", "uPalette3"};

    /**
     * What a living still's shader needs per frame beyond its construction: the photo's size. It
     * also keeps the decoded set reachable for as long as the shader lives, which is what the
     * weak cache in {@link LivingStillTextures} counts on.
     */
    private static final class Living {
        final float imageW;
        final float imageH;
        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        final LivingStillTextures textures;

        Living(LivingStillTextures textures) {
            this.textures = textures;
            this.imageW = textures.image.getWidth();
            this.imageH = textures.image.getHeight();
        }
    }

    /** Living-still shaders by identity; weak, so a released shader frees its entry and its pictures. */
    private static final Map<RuntimeShader, Living> LIVING =
        Collections.synchronizedMap(new WeakHashMap<RuntimeShader, Living>());

    /** Compiles this background's program. Throws when the driver refuses it; the caller catches. */
    @NonNull
    public static RuntimeShader newShader(@NonNull AnimatedWallpaper w) {
        RuntimeShader shader = new RuntimeShader(w.agsl());
        if (w instanceof LivingStill) bindLiving(shader, (LivingStill) w);
        return shader;
    }

    /** Writes one live frame. {@code frameW}/{@code frameH} are the full shared-frame size in pixels. */
    public static void apply(@NonNull RuntimeShader s, @NonNull WallpaperDirector.Frame f,
                             float frameW, float frameH) {
        s.setFloatUniform("uResolution", frameW, frameH);
        s.setFloatUniform("uTime", f.timeSeconds);
        s.setFloatUniform("uPhase", f.phaseSeconds);
        s.setFloatUniform("uEnergy", f.energy);
        s.setFloatUniform("uDim", f.dim);
        setPalette(s, f.palette);
        setMoment(s, 0, f.moments != null && f.moments.length > 0 ? f.moments[0] : null);
        setMoment(s, 1, f.moments != null && f.moments.length > 1 ? f.moments[1] : null);
        setLivingFrame(s, frameW, frameH, f.focus);
    }

    /** The rest pose: energy 0, dim 0, time and phase 0, no moments. What the system's still is drawn with. */
    public static void applyRest(@NonNull RuntimeShader s, @NonNull int[] palette,
                                 float frameW, float frameH) {
        s.setFloatUniform("uResolution", frameW, frameH);
        s.setFloatUniform("uTime", 0f);
        s.setFloatUniform("uPhase", 0f);
        s.setFloatUniform("uEnergy", 0f);
        s.setFloatUniform("uDim", 0f);
        setPalette(s, palette);
        setMoment(s, 0, null);
        setMoment(s, 1, null);
        setLivingFrame(s, frameW, frameH, 0f);
    }

    // --- living stills ---

    /**
     * Decodes (once per process) and binds a living still: the children, and the recipe uniforms,
     * which never change for a given still. Throws {@link IllegalStateException} when a picture
     * cannot be read, which the callers treat as a shader that did not build.
     */
    private static void bindLiving(@NonNull RuntimeShader s, @NonNull LivingStill still) {
        final LivingStillTextures t;
        try {
            t = LivingStillTextures.acquire(still.manifest());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the living still " + still.id(), e);
        }
        s.setInputShader("uImage", linear(t.image));
        s.setInputShader("uDepth", linear(t.depth));
        s.setInputShader("uMaskA", linear(t.maskA));
        s.setInputShader("uMaskB", linear(t.maskB));
        s.setInputShader("uMaskC", linear(t.maskC));
        s.setFloatUniform("uImageSize", t.image.getWidth(), t.image.getHeight());
        s.setFloatUniform("uDepthSize", t.depth.getWidth(), t.depth.getHeight());
        s.setFloatUniform("uMapSize", t.maskA.getWidth(), t.maskA.getHeight());
        for (Map.Entry<String, float[]> e : still.recipeUniforms().entrySet()) {
            s.setFloatUniform(e.getKey(), e.getValue());
        }
        s.setFloatUniform("uFocus", 0f);
        s.setFloatUniform("uCover", t.image.getWidth(), t.image.getHeight());
        LIVING.put(s, new Living(t));
    }

    @NonNull
    private static BitmapShader linear(@NonNull Bitmap bitmap) {
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        return shader;
    }

    /** The cover mapping for this frame size and the eased focus; a no-op for every other background. */
    private static void setLivingFrame(@NonNull RuntimeShader s, float frameW, float frameH, float focus) {
        @Nullable Living living = LIVING.get(s);
        if (living == null) return;
        float[] cover = LivingStill.coverSize(frameW, frameH, living.imageW, living.imageH);
        s.setFloatUniform("uCover", cover[0], cover[1]);
        s.setFloatUniform("uFocus", focus);
    }

    private static void setPalette(RuntimeShader s, int[] palette) {
        for (int i = 0; i < PALETTE.length; i++) {
            int c = palette != null && i < palette.length ? palette[i] : 0xFF000000;
            s.setColorUniform(PALETTE[i], c);
        }
    }

    private static void setMoment(RuntimeShader s, int slot, WallpaperDirector.Moment m) {
        String rect = slot == 0 ? "uMomentRect0" : "uMomentRect1";
        String state = slot == 0 ? "uMomentState0" : "uMomentState1";
        if (m == null || m.kind == 0) {
            s.setFloatUniform(rect, 0f, 0f, 0f, 0f);
            s.setFloatUniform(state, 0f, 0f);
            return;
        }
        if (m.kind == 3 || m.kind == 4) { // PAGE_CHANGE direction in x, TOUCH point in x,y
            s.setFloatUniform(rect, m.x, m.y, m.x, m.y);
        } else {
            s.setFloatUniform(rect, m.x, m.y, m.x + m.w, m.y + m.h);
        }
        s.setFloatUniform(state, (float) m.kind, m.progress);
    }
}
