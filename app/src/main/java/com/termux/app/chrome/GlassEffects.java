package com.termux.app.chrome;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

/**
 * Builds the dock's and the under-pill strip's glass {@code RenderEffect}: the refraction program
 * fed by whatever picture the backdrop view holds ({@link GlassPicture}). It owns the two programs
 * (the strip's is its own, because an effect copies the uniforms at creation and a dock lens
 * rebuild between the two would hand the strip the dock's rect and the pressed key's lens), and the
 * dock params last built, so a key's lens or a touch light can rebuild the effect without
 * recapturing. Everything the activity owns is read through {@link Host}.
 */
public final class GlassEffects {

    private static final String LOG_TAG = "GlassEffects";

    /** What the activity owns that the effect reads. */
    public interface Host {
        float density();

        /** The dock backdrop view, or null before it is inflated. */
        @Nullable
        View dockBackdrop();

        /** Peek card glass {left, top, right, bottom, radius}, or null while the dock is a slab. */
        @Nullable
        float[] peekGlassRect();

        /**
         * How much of the Peek card the program draws: 1 at Home's rest, 0 at the Terminal's,
         * Home's share across a crossing ({@code StackScene#homeShare}).
         */
        default float peekShare() {
            return 1f;
        }

        /** The Fancier Glass look, or null for the numbers the dock always ran with. */
        @Nullable
        GlassRefraction.Look fancierLook();

        /** What the dock's tint covers of the gathered edge, as the last dress pass worked it out. */
        float dockCover();

        /** Writes the dock style's own glass (soft rim, shelf, beads) on the dock's program. */
        @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
        void applyDockSurfaceGlass(@NonNull GlassRefraction.Program program);

        boolean keyLensActive();

        float keyLensIntensity();

        /**
         * Fills {@code out} with the held key's lens {centreX, centreY, halfX, halfY, radius} in
         * backdrop coordinates. Filled, not returned: the lens rebuilds the effect every frame.
         */
        void keyLens(@NonNull float[] out);
    }

    @NonNull private final Host mHost;
    @Nullable private GlassRefraction.Program mDockRefraction;

    private boolean mParamsValid = false;
    private float mBlurPx, mCapLeft, mCapTop, mCapRight, mCapBottom, mRadiusPx;
    @NonNull private final float[] mLens = new float[5];
    /** The slab's cap and the program's rect, reused: a crossing rebuilds the effect per frame. */
    @NonNull private final float[] mCap = new float[RoundCut.SIZE];
    @NonNull private final float[] mRect = new float[RoundCut.SIZE];

    public GlassEffects(@NonNull Host host) {
        mHost = host;
    }

    /** True while the dock's effect is installed and its params are remembered. */
    public boolean paramsValid() {
        return mParamsValid;
    }

    /** No live backdrop shader any more: the key lens falls back to the bubble border. */
    public void invalidateParams() {
        mParamsValid = false;
    }

    /** The dock's program, or null before the first build (and below Android 13). */
    @Nullable
    public GlassRefraction.Program dockProgram() {
        return mDockRefraction;
    }

    /**
     * Builds the dock's effect and remembers its params for {@link #rebuildForLens}. Null below
     * Android 13 or if the shader fails to compile, so the caller falls back to a plain blur.
     */
    @Nullable
    public RenderEffect buildDockAndRemember(float blurPx, float capLeft, float capTop,
                                             float capRight, float capBottom, float radiusPx) {
        RenderEffect glass = buildDock(blurPx, capLeft, capTop, capRight, capBottom, radiusPx);
        mBlurPx = blurPx;
        mCapLeft = capLeft;
        mCapTop = capTop;
        mCapRight = capRight;
        mCapBottom = capBottom;
        mRadiusPx = radiusPx;
        mParamsValid = (glass != null);
        return glass;
    }

    @Nullable
    public RenderEffect buildDock(float blurPx, float capLeft, float capTop, float capRight,
                                  float capBottom, float radiusPx) {
        return build(blurPx, capLeft, capTop, capRight, capBottom, radiusPx);
    }

    /** Rebuild + re-apply the dock's effect with the current lens state (no recapture). */
    public void rebuildForLens() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !mParamsValid) {
            if (MotionLog.enabled) MotionLog.log("lens-rebuild", "skipped params=" + mParamsValid); // DIAG(lens)
            return;
        }
        View backdrop = mHost.dockBackdrop();
        if (backdrop == null || backdrop.getVisibility() != View.VISIBLE) {
            if (MotionLog.enabled) MotionLog.log("lens-rebuild", "skipped backdrop="
                + (backdrop == null ? "none" : backdrop.getVisibility())); // DIAG(lens)
            return;
        }
        RenderEffect glass = buildDock(mBlurPx, mCapLeft, mCapTop, mCapRight, mCapBottom, mRadiusPx);
        if (MotionLog.enabled) MotionLog.log("lens-rebuild", "rect=" + mRect[0] + "," + mRect[1]
            + "," + mRect[2] + "," + mRect[3] + " r=" + mRect[4] + " share=" + mHost.peekShare()
            + " backdropTy=" + backdrop.getTranslationY() + " top=" + backdrop.getTop()); // DIAG(lens)
        if (glass != null) {
            backdrop.setRenderEffect(glass);
        }
    }

    /**
     * The program's rect: the slab's {@code cap} with no Peek card, the card's {@code peek} at
     * Home's rest ({@code homeShare} 1), the blend between them across a crossing.
     */
    static void programRect(@NonNull float[] cap, @Nullable float[] peek, float homeShare,
                            @NonNull float[] out) {
        if (peek == null) {
            RoundCut.blend(cap, cap, 0f, out);
            return;
        }
        RoundCut.blend(cap, peek, homeShare, out);
    }

    @Nullable
    private RenderEffect build(float blurPx, float capLeft, float capTop,
                               float capRight, float capBottom, float radiusPx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null;
        }
        try {
            GlassRefraction.Program program = mDockRefraction;
            if (program == null) {
                program = GlassRefraction.Program.create(mHost.density());
                mDockRefraction = program;
            }
            if (program == null) {
                return null;
            }
            // The view's own pixels are the content here, so the aim is identity.
            program.setAim(1f, 1f, 0f, 0f);
            // Peek glass: the dock's glass is the card, its rim on the card's top and sides; a
            // crossing blends it with the slab's by Home's share.
            RoundCut.set(mCap, capLeft, capTop, capRight, capBottom, radiusPx);
            programRect(mCap, mHost.peekGlassRect(), mHost.peekShare(), mRect);
            program.setRect(mRect[RoundCut.LEFT], mRect[RoundCut.TOP], mRect[RoundCut.RIGHT],
                mRect[RoundCut.BOTTOM], mRect[RoundCut.RADIUS]);
            // The user's knobs while Fancier Glass is on, the numbers this dock always ran with
            // otherwise; either way its gathered edge is raised by what the dock's tint covers
            // of it, the one rule every glass surface's edge follows. The cover is the one
            // the last dress pass worked out, so a key's lens rebuilding this per frame reads no
            // preference.
            program.setLook(GlassStack.lookFor(mHost.fancierLook())
                .withCover(Math.max(0f, mHost.dockCover())));
            // The Dock design's own glass (the Tray's soft rim and shelf).
            mHost.applyDockSurfaceGlass(program);
            // Per-key lens state (0 intensity == no lens, dock refraction unchanged).
            if (mHost.keyLensActive() && mHost.keyLensIntensity() > 0f) {
                mHost.keyLens(mLens);
                program.setLens(mLens[0], mLens[1], mLens[2], mLens[3], mLens[4],
                    mHost.keyLensIntensity());
                // A held key's lens owns the dock's glass: an icon's touch light yields to it.
                program.clearTouch();
            } else {
                program.clearLens();
            }
            // Built afresh each time: the effect copies the program's uniforms as they are now.
            RenderEffect shaderEffect = RenderEffect.createRuntimeShaderEffect(program.shader(), "content");
            if (blurPx > 0f) {
                RenderEffect blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP);
                return RenderEffect.createChainEffect(shaderEffect, blur);
            }
            return shaderEffect;
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Glass refraction shader failed; falling back to blur", t);
            return null;
        }
    }
}
