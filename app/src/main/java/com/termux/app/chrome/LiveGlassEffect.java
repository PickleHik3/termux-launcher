package com.termux.app.chrome;

import android.graphics.RenderEffect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

/**
 * The refraction as a {@code RenderEffect} over a live picture: the effect route a glass takes
 * when what it bends is a {@link LivePicture} (a node, which an AGSL program cannot take as a
 * child shader) rather than the stored frame (a bitmap, bent on the paint). The content is the
 * picture as the surface draws it, in the surface's own coordinates, so the aim is identity. An
 * effect can take one input only, so the sharp edge is a second pass: an edge builder
 * ({@link #LiveGlassEffect(float, boolean)}) runs the same program over the unblurred picture
 * ({@link LivePicture#sharpDrawable}), drawing only the band faded by its sharp share
 * ({@code uEdgeOnly}), after the picture's colours ({@link LivePicture#sharpColour}); drawn over
 * the frosted pass it is the stored route's mix of the two pictures.
 *
 * <p>An effect copies the program's uniforms when it is made, so it is made again only when one
 * of them moved (size, corner, look, the motion light's lag, the tilt), and handed back as is
 * otherwise: a surface at rest draws the same effect on every frame and allocates nothing.</p>
 */
public final class LiveGlassEffect {

    private static final int KEY_SIZE = 7;

    private final float mDensity;
    /** This builder makes the edge pass ({@code uEdgeOnly}), not the frosted one. */
    private final boolean mEdgeOnly;
    @Nullable private GlassRefraction.Program mProgram;
    private boolean mFailed;
    @Nullable private RenderEffect mEffect;
    @NonNull private final float[] mKey = new float[KEY_SIZE];
    @Nullable private GlassRefraction.Look mKeyLook;
    /** The input effect the current one was chained after, compared by identity. */
    @Nullable private RenderEffect mKeyInput;

    public LiveGlassEffect(float density) {
        this(density, false);
    }

    /** A builder for the frosted pass, or with {@code edgeOnly} for the sharp edge pass. */
    public LiveGlassEffect(float density, boolean edgeOnly) {
        mDensity = density;
        mEdgeOnly = edgeOnly;
    }

    /** The density the program was made for; a surface on another density makes a new one. */
    public float density() {
        return mDensity;
    }

    /**
     * The effect for a {@code width} x {@code height} surface cut at {@code radiusPx}, wearing
     * {@code look}, its light lagging by {@code lagX, lagY} px and tipped by {@code tiltX, tiltY}
     * ({@link GlassRefraction#tiltVector}). The same object while none of those moved. Null when
     * the phone runs no program: the caller keeps the stored frame.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Nullable
    public RenderEffect effect(int width, int height, float radiusPx,
                               @NonNull GlassRefraction.Look look, float lagX, float lagY,
                               float tiltX, float tiltY) {
        return effect(width, height, radiusPx, look, lagX, lagY, tiltX, tiltY, null);
    }

    /**
     * {@link #effect(int, int, float, GlassRefraction.Look, float, float, float, float)} run
     * after {@code input} (the edge pass's colours, {@link LivePicture#sharpColour}), or on the
     * content itself for null. A new input object makes a new effect; the same one keeps it.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Nullable
    public RenderEffect effect(int width, int height, float radiusPx,
                               @NonNull GlassRefraction.Look look, float lagX, float lagY,
                               float tiltX, float tiltY, @Nullable RenderEffect input) {
        if (mFailed) return null;
        if (mEffect != null && look.equals(mKeyLook) && input == mKeyInput
            && sameKey(mKey, width, height, radiusPx, lagX, lagY, tiltX, tiltY)) {
            return mEffect;
        }
        GlassRefraction.Program program = mProgram;
        if (program == null) {
            program = GlassRefraction.Program.create(mDensity);
            if (program == null) {
                mFailed = true;
                return null;
            }
            mProgram = program;
            // Nothing is bound as the clearer picture under an effect: the frosted pass keeps
            // the frost, and the edge pass's content is the sharp picture itself.
            program.setClarityInput(null, 1f, 1f);
            program.setEdgeOnly(mEdgeOnly);
        }
        program.setAim(1f, 1f, 0f, 0f);
        program.setLocal(null);
        program.setRect(0f, 0f, width, height, radiusPx);
        program.setLook(look);
        program.setLag(lagX, lagY);
        program.setTilt(tiltX, tiltY);
        RenderEffect bent = RenderEffect.createRuntimeShaderEffect(program.shader(), "content");
        mEffect = input == null ? bent : RenderEffect.createChainEffect(bent, input);
        mKeyLook = look;
        mKeyInput = input;
        writeKey(mKey, width, height, radiusPx, lagX, lagY, tiltX, tiltY);
        return mEffect;
    }

    /** Forgets the effect, so the next call makes one afresh; the program is kept. */
    public void forget() {
        mEffect = null;
        mKeyLook = null;
        mKeyInput = null;
    }

    @VisibleForTesting
    static void writeKey(@NonNull float[] key, int width, int height, float radiusPx,
                         float lagX, float lagY, float tiltX, float tiltY) {
        key[0] = width;
        key[1] = height;
        key[2] = radiusPx;
        key[3] = lagX;
        key[4] = lagY;
        key[5] = tiltX;
        key[6] = tiltY;
    }

    /** Whether {@code key} already holds these uniforms. Pure. */
    @VisibleForTesting
    static boolean sameKey(@NonNull float[] key, int width, int height, float radiusPx,
                           float lagX, float lagY, float tiltX, float tiltY) {
        return key[0] == width && key[1] == height && key[2] == radiusPx && key[3] == lagX
            && key[4] == lagY && key[5] == tiltX && key[6] == tiltY;
    }

    @VisibleForTesting
    @NonNull
    static float[] newKey() {
        return new float[KEY_SIZE];
    }
}
