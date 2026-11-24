package com.termux.app.dock;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.GlassAnchor;
import com.termux.app.chrome.GlassEffects;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.chrome.GlassStack;
import com.termux.app.chrome.GroundUnderGlass;
import com.termux.app.chrome.SharedFrameDrawable;
import com.termux.app.chrome.SurfaceDirtyLedger;

/**
 * The backdrop half both cards that wear the dock slab's glass share ({@link KeysCardDress},
 * {@link TerminalHostDress}): the Live picture, the program's effect, the ledger record, the
 * ground overlay and the layout watch. What differs per card is parameterised: the ledger key,
 * the backdrop's id, whether the slab's shelf is under it, and through {@link Owner} the glass
 * radius, the seams, the shape and the shelf. Docked and Floating stay distinct through those
 * answers; no formula is shared between them.
 */
final class GlassBackdropDress {

    /** What differs between the cards that share this backdrop. */
    interface Owner {
        /** The glass radius for a surface {@code heightPx} tall. */
        float glassRadiusPx(int heightPx);
        /** The seam edges the rim rect is pushed past ({@code ChromeEdgeRule} bits). */
        int seams();
        /** Dresses the surface's rim again after the program's edge flag changed. */
        void dressShape(@NonNull View surface);
        /** Follows the shelf for {@code state}; null drops it. */
        void syncShelf(@Nullable ChromeSpec state);
    }

    @NonNull private final DockController mDock;
    @NonNull private final DockController.Host mHost;
    @NonNull private final KeysCardDress.Source mSource;
    @NonNull private final Owner mOwner;
    @NonNull private final SurfaceDirtyLedger.Backdrop mKey;
    /** The card's own effect and its remembered params (the host's are the dock's). */
    @NonNull private final GlassEffects mEffects;

    private float mCover = -1f;
    private boolean mProgramEdge;
    private boolean mShown;
    @Nullable private GroundUnderGlass mGround;
    private int mDressedWidthPx;
    private int mDressedHeightPx;
    @Nullable private View mWatched;
    @NonNull private final int[] mOrigin = new int[2];
    @NonNull private final int[] mRoot = new int[2];
    @NonNull private final float[] mCap = new float[4];
    @NonNull private final Rect mFrameRect = new Rect();

    GlassBackdropDress(@NonNull DockController dock, @NonNull KeysCardDress.Source source,
                       @NonNull Owner owner, @NonNull SurfaceDirtyLedger.Backdrop key,
                       int backdropId, boolean slabShelf) {
        mDock = dock;
        mHost = dock.host();
        mSource = source;
        mOwner = owner;
        mKey = key;
        mEffects = new GlassEffects(new GlassEffects.Host() {
            @Override public float density() {
                return mHost.density();
            }
            @Nullable @Override public View dockBackdrop() {
                return mHost.findViewById(backdropId);
            }
            @Nullable @Override public float[] peekGlassRect() {
                return null;
            }
            @Nullable @Override public GlassRefraction.Look fancierLook() {
                return mSource.fancierLook();
            }
            @Override public float dockCover() {
                return mCover;
            }
            @Override public void applyDockSurfaceGlass(@NonNull GlassRefraction.Program program) {
                DockSurface.applySlabGlass(program, mDock.keysCardSlabSpec(), slabShelf);
            }
            @Override public boolean keyLensActive() {
                return false;
            }
            @Override public float keyLensIntensity() {
                return 0f;
            }
            @Override public void keyLens(@NonNull float[] out) {
            }
        });
    }

    boolean shown() {
        return mShown;
    }

    void setShown(boolean shown) {
        mShown = shown;
    }

    /** Whether a surface is watched: the glass has been dressed once and not fully let go. */
    boolean watching() {
        return mWatched != null;
    }

    boolean programEdge() {
        return mProgramEdge;
    }

    boolean paramsValid() {
        return mEffects.paramsValid();
    }

    /** {@code updateAccessoryRenderEffectBackdrop} for the slab's glass, on the card's views. */
    void updateBackdrop(@NonNull ChromeSpec state, @NonNull View surface,
                        @NonNull ImageView backdrop, boolean atRest) {
        // The surface the even-out is measured under: the surface at a rest, none at a crossing's begin.
        View measured = atRest ? surface : null;
        backdrop.setAlpha(1f);
        if (surface.getWidth() <= 0 || surface.getHeight() <= 0) {
            if (mSource.blurWanted(state) && mSource.pictureUp(backdrop)) {
                backdrop.setVisibility(View.VISIBLE);
            } else {
                clearBackdrop(backdrop);
            }
            return;
        }
        if (!mSource.blurWanted(state)) {
            clearBackdrop(backdrop);
            return;
        }
        boolean managed = mSource.managedSource();
        int hOver = CapsuleGlass.horizontalOverscanPx(
            mHost.dpToPx(Math.max(0, state.blurRadiusDp)), mHost.density());
        // The foot under the gesture pill is the surface's own bottom: the backdrop reaches past it.
        int seamOver = CapsuleGlass.seamOverscanPx(mSource.footPx(), hOver);
        int width = surface.getWidth();
        int height = surface.getHeight();
        CapsuleGlass.applyOverscan(backdrop, width, height, hOver, seamOver);
        // The laid-out origin: the lane's slide and the rows' lift are the anchor's, not the key.
        GlassAnchor.layoutOriginOnScreen(surface, mOrigin, mRoot);
        Rect target = CapsuleGlass.targetRect(mOrigin, width, height, hOver, seamOver);
        SharedFrameDrawable installed = SharedFrameDrawable.of(backdrop.getDrawable());
        SurfaceDirtyLedger ledger = mHost.chrome().ledger();
        float cover = mDock.dockGlassCover();
        boolean drag = mSource.glassDragPreview();
        if (!ledger.isDirty(mKey) && cover == mCover
            && (drag || ledger.lastRadiusDp(mKey) == state.blurRadiusDp)
            && ledger.lastManagedSource(mKey) == managed
            && ledger.matchesLastRect(mKey, target)
            && mSource.pictureUp(backdrop)) {
            backdrop.setColorFilter(mSource.frostFilter(drag ? null : measured));
            backdrop.setVisibility(View.VISIBLE);
            mOwner.syncShelf(state);
            return;
        }
        Bitmap frame = mSource.blurFrame(state.blurRadiusDp, mFrameRect);
        if (frame == null) {
            if ((installed != null && installed.frameRect().equals(mFrameRect))
                || mSource.pictureUp(backdrop)) {
                backdrop.invalidate();
                backdrop.setVisibility(View.VISIBLE);
            } else {
                backdrop.setImageDrawable(null);
                backdrop.setVisibility(View.GONE);
                ledger.markDirty(mKey);
                setProgramEdge(surface, false);
            }
            return;
        }
        Drawable live = mSource.attachLive(backdrop, state.blurRadiusDp);
        mGround = mSource.ground(mGround, backdrop);
        mSource.installPicture(backdrop, installed, frame, mFrameRect, state.blurRadiusDp, live);
        mCover = cover;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float radius = mOwner.glassRadiusPx(height);
            CapsuleGlass.capRect(mCap, hOver, width, height, seamOver,
                GlassRefraction.seamReachPx(GlassStack.lookFor(mSource.fancierLook()),
                    mHost.density(), radius), mOwner.seams());
            RenderEffect glass = mEffects.buildDockAndRemember(0f, mCap[0], mCap[1], mCap[2],
                mCap[3], radius);
            backdrop.setRenderEffect(glass);
        }
        backdrop.setColorFilter(mSource.frostFilter(measured));
        backdrop.setVisibility(View.VISIBLE);
        setProgramEdge(surface, mEffects.paramsValid());
        mOwner.syncShelf(state);
        ledger.recordApplied(mKey, state.blurRadiusDp, managed, target);
    }

    void setProgramEdge(@NonNull View surface, boolean drawsEdge) {
        if (mProgramEdge == drawsEdge) return;
        mProgramEdge = drawsEdge;
        mOwner.dressShape(surface);
    }

    void clearBackdrop(@NonNull ImageView backdrop) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop.setRenderEffect(null);
        mSource.releaseLive(backdrop);
        backdrop.setImageDrawable(null);
        backdrop.setVisibility(View.GONE);
        mEffects.invalidateParams();
        mHost.chrome().ledger().reset(mKey);
        mProgramEdge = false;
        mCover = -1f;
        mOwner.syncShelf(null);
    }

    void hide(int id) {
        View view = mHost.findViewById(id);
        if (view != null && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    /** Asks for the chrome pass once when the surface's size moves while it is dressed. */
    void watch(@NonNull View surface) {
        if (mWatched == surface) return;
        mWatched = surface;
        surface.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int h = b - t;
            if (!mShown || (w == mDressedWidthPx && h == mDressedHeightPx)) return;
            mDressedWidthPx = w;
            mDressedHeightPx = h;
            ChromeRenderer chrome = mHost.chrome();
            if (chrome != null) {
                chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME
                    | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
            }
        });
    }
}
