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

        /** The Fancier Glass look, or null for the numbers the dock always ran with. */
        @Nullable
        GlassRefraction.Look fancierLook();

        /** What the dock's tint covers of the gathered edge, as the last dress pass worked it out. */
        float dockCover();

        /** The same for the strip under the pill. */
        float navStripCover();

        /** Writes the dock style's own glass (soft rim, shelf, beads) on the dock's program. */
        @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
        void applyDockSurfaceGlass(@NonNull GlassRefraction.Program program);

        boolean keyLensActive();

        float keyLensIntensity();

        /** The held key's lens {centreX, centreY, halfX, halfY, radius} in backdrop coordinates. */
        @NonNull
        float[] keyLens();
    }

    @NonNull private final Host mHost;
    @Nullable private GlassRefraction.Program mDockRefraction;
    @Nullable private GlassRefraction.Program mNavStripRefraction;

    private boolean mParamsValid = false;
    private float mBlurPx, mCapLeft, mCapTop, mCapRight, mCapBottom, mRadiusPx;

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
        return build(false, blurPx, capLeft, capTop, capRight, capBottom, radiusPx);
    }

    /** The under-pill strip's effect: its own program, square, and never a key's lens. */
    @Nullable
    public RenderEffect buildNavStrip(float left, float top, float right, float bottom) {
        return build(true, 0f, left, top, right, bottom, 0f);
    }

    /** Rebuild + re-apply the dock's effect with the current lens state (no recapture). */
    public void rebuildForLens() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !mParamsValid) {
            return;
        }
        View backdrop = mHost.dockBackdrop();
        if (backdrop == null || backdrop.getVisibility() != View.VISIBLE) {
            return;
        }
        RenderEffect glass = buildDock(mBlurPx, mCapLeft, mCapTop, mCapRight, mCapBottom, mRadiusPx);
        if (glass != null) {
            backdrop.setRenderEffect(glass);
        }
    }

    @Nullable
    private RenderEffect build(boolean navStrip, float blurPx, float capLeft, float capTop,
                               float capRight, float capBottom, float radiusPx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null;
        }
        try {
            GlassRefraction.Program program = navStrip ? mNavStripRefraction : mDockRefraction;
            if (program == null) {
                program = GlassRefraction.Program.create(mHost.density());
                if (navStrip) mNavStripRefraction = program;
                else mDockRefraction = program;
            }
            if (program == null) {
                return null;
            }
            // The view's own pixels are the content here, so the aim is identity.
            program.setAim(1f, 1f, 0f, 0f);
            // Peek glass: the dock's glass is the card, its rim on the card's top and sides.
            float[] peek = navStrip ? null : mHost.peekGlassRect();
            if (peek != null) {
                program.setRect(peek[0], peek[1], peek[2], peek[3], peek[4]);
            } else {
                program.setRect(capLeft, capTop, capRight, capBottom, radiusPx);
            }
            // The user's knobs while Fancier Glass is on, the numbers this dock always ran with
            // otherwise; either way its gathered edge is raised by what the dock's tint covers
            // of it, the one rule every glass surface's edge follows. The cover is the one
            // the last dress pass worked out, so a key's lens rebuilding this per frame reads no
            // preference.
            program.setLook(GlassStack.lookFor(mHost.fancierLook())
                .withCover(Math.max(0f, navStrip ? mHost.navStripCover() : mHost.dockCover())));
            // The Dock design's own glass (the Tray's soft rim and shelf); the keyboard's strip
            // under the pill wears none of it.
            if (navStrip) {
                program.setDockSurface(0f, 0f);
            } else {
                mHost.applyDockSurfaceGlass(program);
            }
            // Per-key lens state (0 intensity == no lens, dock refraction unchanged).
            if (!navStrip && mHost.keyLensActive() && mHost.keyLensIntensity() > 0f) {
                float[] lens = mHost.keyLens();
                program.setLens(lens[0], lens[1], lens[2], lens[3], lens[4], mHost.keyLensIntensity());
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
