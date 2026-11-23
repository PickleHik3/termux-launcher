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
 * picture as the slab's node draws it: the surface and the live picture's margin round it, so the
 * outline stands {@code marginPx} in from the node's edge, and a turned slab's shifted picture is
 * held to that margin ({@link #heldReachPx}), never reading past what the node holds (a band of
 * nothing, where the wallpaper showed through unfrosted). The aim is identity, and no clearer
 * frame is bound, so the edge's clarity term stays at zero.
 *
 * <p>An effect copies the program's uniforms when it is made, so it is made again only when one
 * of them moved (size, corner, look, the page's pose), and handed back as is otherwise: a surface
 * at rest draws the same effect on every frame and allocates nothing.</p>
 */
public final class LiveGlassEffect {

    private static final int KEY_SIZE = 7;

    private final float mDensity;
    @Nullable private GlassRefraction.Program mProgram;
    private boolean mFailed;
    @Nullable private RenderEffect mEffect;
    @NonNull private final float[] mKey = new float[KEY_SIZE];
    @Nullable private GlassRefraction.Look mKeyLook;

    public LiveGlassEffect(float density) {
        mDensity = density;
    }

    /** The density the program was made for; a surface on another density makes a new one. */
    public float density() {
        return mDensity;
    }

    /**
     * The effect for a {@code width} x {@code height} surface cut at {@code radiusPx}, wearing
     * {@code look}, posed as {@code pose} ({@link GlassPose}), on a node that reaches
     * {@code marginPx} past the surface on every side. The same object while none of those moved.
     * Null when the phone runs no program: the caller keeps the stored frame.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Nullable
    public RenderEffect effect(int width, int height, int marginPx, float radiusPx,
                               @NonNull GlassRefraction.Look look, @NonNull GlassPose pose) {
        if (mFailed) return null;
        int margin = Math.max(0, marginPx);
        if (mEffect != null && look.equals(mKeyLook)
            && sameKey(mKey, width, height, margin, radiusPx, pose.tiltX, pose.tiltY, pose.scale)) {
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
            // Nothing is bound as the clearer picture under an effect: the term stays at zero.
            program.setClarityInput(null, 1f, 1f);
        }
        program.setAim(1f, 1f, 0f, 0f);
        program.setLocal(null);
        // In the node's px: the outline stands margin in. Every sample is kept inside the node,
        // and a turn's shift is held to the margin past the outline.
        program.setRect(margin, margin, margin + width, margin + height, radiusPx);
        program.setFrameSize(width + 2f * margin, height + 2f * margin);
        program.setShiftReach(heldReachPx(margin));
        program.setLook(look);
        program.setPose(pose);
        mEffect = RenderEffect.createRuntimeShaderEffect(program.shader(), "content");
        mKeyLook = look;
        writeKey(mKey, width, height, margin, radiusPx, pose.tiltX, pose.tiltY, pose.scale);
        return mEffect;
    }

    /**
     * How far past the outline a sample of the slab's node may read, px: the margin the node
     * holds, less the program's {@code frameInset} keep. A turn's shift is held to it. Pure.
     */
    static float heldReachPx(int marginPx) {
        return Math.max(0f, marginPx - GlassRefraction.FRAME_MARGIN_PX);
    }

    /** Forgets the effect, so the next call makes one afresh; the program is kept. */
    public void forget() {
        mEffect = null;
        mKeyLook = null;
    }

    @VisibleForTesting
    static void writeKey(@NonNull float[] key, int width, int height, int marginPx, float radiusPx,
                         float tiltX, float tiltY, float scale) {
        key[0] = width;
        key[1] = height;
        key[2] = radiusPx;
        key[3] = tiltX;
        key[4] = tiltY;
        key[5] = scale;
        key[6] = marginPx;
    }

    /** Whether {@code key} already holds these uniforms. Pure. */
    @VisibleForTesting
    static boolean sameKey(@NonNull float[] key, int width, int height, int marginPx,
                           float radiusPx, float tiltX, float tiltY, float scale) {
        return key[0] == width && key[1] == height && key[2] == radiusPx && key[3] == tiltX
            && key[4] == tiltY && key[5] == scale && key[6] == marginPx;
    }

    @VisibleForTesting
    @NonNull
    static float[] newKey() {
        return new float[KEY_SIZE];
    }
}
