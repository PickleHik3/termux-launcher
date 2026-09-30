package com.termux.app.chrome.wallpaper;

import android.graphics.RuntimeShader;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Binds the uniform contract every built-in declares (see {@link MomentAgsl#HEAD}). The one place
 * that names {@link RuntimeShader} for the generated backgrounds, so the registry and the
 * background classes stay loadable on API 26.
 *
 * <p>Moment mapping: for PANE_OPEN, PANE_CLOSE and BELL the rect is (x, y, x + w, y + h); for
 * TOUCH the point is (x, y) in rect.xy; for PAGE_CHANGE the direction x goes in rect.x. State is
 * (kind, progress). No allocation beyond the varargs the framework itself takes.</p>
 */
@RequiresApi(33)
public final class WallpaperUniforms {

    private WallpaperUniforms() {}

    private static final String[] PALETTE = {"uPalette0", "uPalette1", "uPalette2", "uPalette3"};

    /** Compiles this background's program. Throws when the driver refuses it; the caller catches. */
    @NonNull
    public static RuntimeShader newShader(@NonNull AnimatedWallpaper w) {
        return new RuntimeShader(w.agsl());
    }

    /** Writes one live frame. {@code frameW}/{@code frameH} are the full shared-frame size in pixels. */
    public static void apply(@NonNull RuntimeShader s, @NonNull WallpaperDirector.Frame f,
                             float frameW, float frameH) {
        s.setFloatUniform("uResolution", frameW, frameH);
        s.setFloatUniform("uTime", f.timeSeconds);
        s.setFloatUniform("uEnergy", f.energy);
        s.setFloatUniform("uDim", f.dim);
        setPalette(s, f.palette);
        setMoment(s, 0, f.moments != null && f.moments.length > 0 ? f.moments[0] : null);
        setMoment(s, 1, f.moments != null && f.moments.length > 1 ? f.moments[1] : null);
    }

    /** The rest pose: energy 0, dim 0, time 0, no moments. What the system's still is drawn with. */
    public static void applyRest(@NonNull RuntimeShader s, @NonNull int[] palette,
                                 float frameW, float frameH) {
        s.setFloatUniform("uResolution", frameW, frameH);
        s.setFloatUniform("uTime", 0f);
        s.setFloatUniform("uEnergy", 0f);
        s.setFloatUniform("uDim", 0f);
        setPalette(s, palette);
        setMoment(s, 0, null);
        setMoment(s, 1, null);
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
