package com.termux.app.dock;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.DockShelfView;
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
 * The Terminal's extra-keys card wearing the dock capsule's glass (ADR 0032, #302): the same
 * steps the activity runs for the host when the host is the capsule, on the card's own views
 * ({@code extra_keys_card_*}). The numbers come from {@link CapsuleGlass}, the shape, the
 * program's slab glass, the shelf rule and the plank layers from {@link DockSurface} and
 * {@link DockController}, so the card and the capsule are one code and cannot drift; what is
 * single-instance state on the host's side is a second instance here (the effect's remembered
 * params, the ledger's {@code KEYS_CARD} record, the program-edge flag, the ground overlay, the
 * Live picture and its glass entry).
 *
 * <p>The card is Live glass only while it can be seen: dressed (and its picture registered) at a
 * rest where the Terminal stands in front, and at the begin of a Home to Terminal crossing so
 * its picture is up the frame the card first shows; released, with its picture dropped, at a rest
 * where it is gone or parked off screen at Home's rest. Never per frame: a crossing writes
 * nothing here.
 */
public final class KeysCardDress {

    /** What only the activity owns that the card's dress reads or writes. */
    public interface Source {
        /** A crossing is live: nothing is released or measured. */
        boolean crossing();
        /** The dock's blur is on for {@code state} (blur set, not standing down on a frame). */
        boolean blurWanted(@NonNull ChromeSpec state);
        /** Whether the card's backdrop shows a picture now, stored or live. */
        boolean pictureUp(@NonNull ImageView backdrop);
        /** The backdrop's live picture at {@code radiusDp}, registered with the glass service; null when not Live. */
        @Nullable Drawable attachLive(@NonNull ImageView backdrop, int radiusDp);
        /** Lets the backdrop's live picture go and drops its glass entry. */
        void releaseLive(@NonNull ImageView backdrop);
        /** The stored blur frame for the radius (null while one is in flight); its rect goes to {@code outRect}. */
        @Nullable Bitmap blurFrame(int radiusDp, @NonNull Rect outRect);
        /** The frost with the dock band's even-out measured under {@code surface}; null measures nothing. */
        @Nullable ColorFilter frostFilter(@Nullable View surface);
        /** The blur's source is the managed wallpaper. */
        boolean managedSource();
        /** A Frost drag is on: the frame up stays until its release. */
        boolean glassDragPreview();
        /** The Fancier Glass look, or null for the numbers the dock always ran with. */
        @Nullable GlassRefraction.Look fancierLook();
        /** The under-pill foot's height; above 0 the backdrop reaches down past the seam. */
        int footPx();
        /** The plate's glass, or null where a joined frame is the glass. */
        @Nullable Drawable plate(float barAlpha);
        /** The theme's outline colour, for the top edge's seam. */
        int outlineColor();
        /** The ground overlay of the page on {@code backdrop}, made once ({@code held} null); null while ground is off. */
        @Nullable GroundUnderGlass ground(@Nullable GroundUnderGlass held, @NonNull ImageView backdrop);
        /** Points {@code backdrop} at the live picture or the stored frame, whichever stands. */
        void installPicture(@NonNull ImageView backdrop, @Nullable SharedFrameDrawable installed,
                            @NonNull Bitmap frame, @NonNull Rect frameRect, int radiusDp,
                            @Nullable Drawable live);
    }

    @NonNull private final DockController mDock;
    @NonNull private final DockController.Host mHost;
    @NonNull private final Source mSource;
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

    public KeysCardDress(@NonNull DockController dock, @NonNull Source source) {
        mDock = dock;
        mHost = dock.host();
        mSource = source;
        mEffects = new GlassEffects(new GlassEffects.Host() {
            @Override public float density() {
                return mHost.density();
            }
            @Nullable @Override public View dockBackdrop() {
                return mHost.findViewById(R.id.extra_keys_card_blur_backdrop);
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
                DockSurface.applySlabGlass(program, mDock.keysCardSlabSpec(), true);
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

    /** Whether the card's glass is up (dressed and not released). */
    public boolean shown() {
        return mShown;
    }

    /**
     * One rest pass: dresses the card while it stands (the cards apart, the Terminal in front),
     * releases it where it is gone or parked at Home's rest. A live crossing changes nothing.
     */
    public void update(@NonNull ChromeSpec state) {
        if (mSource.crossing()) return;
        View card = mHost.findViewById(R.id.extra_keys_card);
        boolean standing = card != null && card.getVisibility() == View.VISIBLE
            && state.toolbarShown && mHost.stackRenderer().keysCardStands();
        if (!standing) {
            release();
            return;
        }
        dress(state, true);
    }

    /**
     * A Home to Terminal crossing begins: the card, laid out at Home's rest already, is dressed
     * now so its picture is up when it first shows. A rest-like step, once per crossing.
     */
    public void onCrossingBegin(@NonNull ChromeSpec state) {
        View card = mHost.findViewById(R.id.extra_keys_card);
        if (card == null || card.getVisibility() != View.VISIBLE || !state.toolbarShown) return;
        // Parked off screen still: nothing is measured under it, the rest's pass does that.
        dress(state, false);
    }

    /** Lets the card's glass go: its picture and glass entry, its effect, its layers. */
    public void release() {
        if (!mShown && mWatched == null) return;
        mShown = false;
        ImageView backdrop = mHost.findViewById(R.id.extra_keys_card_blur_backdrop);
        if (backdrop != null) clearBackdrop(backdrop);
        hide(R.id.extra_keys_card_top_edge_fx);
        hide(R.id.extra_keys_card_background);
        hide(R.id.extra_keys_card_edge_glow_fx);
        hide(R.id.extra_keys_card_specular_fx);
        syncShelf(null);
    }

    // ---- The dress -----------------------------------------------------------------------------

    private void dress(@NonNull ChromeSpec state, boolean atRest) {
        View card = mHost.findViewById(R.id.extra_keys_card);
        ImageView backdrop = mHost.findViewById(R.id.extra_keys_card_blur_backdrop);
        if (card == null || backdrop == null) return;
        watch(card);
        mShown = true;
        float radiusPx = mDock.resolveDockCapsuleCornerRadiusPx(cardHeightPx(card));
        float barAlpha = state.barAlpha;
        // The plate, as doApplyChromeSpec dresses the host's sheet.
        View plate = mHost.findViewById(R.id.extra_keys_card_background);
        if (plate != null) {
            plate.setBackground(mSource.plate(barAlpha));
            plate.setAlpha(1f);
            if (plate.getVisibility() != View.VISIBLE) plate.setVisibility(View.VISIBLE);
        }
        // Edge glow and specular, the plank's reactive layers.
        View specular = mHost.findViewById(R.id.extra_keys_card_specular_fx);
        View glow = mHost.findViewById(R.id.extra_keys_card_edge_glow_fx);
        mDock.dressPlankLayers(specular, glow, radiusPx, mHost.resolveDockAccentColor(), barAlpha);
        // At rest the rim glow stands lit; the plank's own controller owns it once it is bound.
        if (glow != null && glow.getVisibility() != View.VISIBLE) glow.setVisibility(View.VISIBLE);
        View edgeFx = mHost.findViewById(R.id.extra_keys_card_top_edge_fx);
        if (edgeFx != null) CapsuleGlass.dressTopEdge(edgeFx, true, barAlpha, mSource.outlineColor());
        dressShape(card, radiusPx);
        updateBackdrop(state, card, backdrop, atRest);
    }

    /** The rim under the glass (or the outline alone where the program draws the edge), as the capsule's. */
    private void dressShape(@NonNull View card, float radiusPx) {
        card.setBackground(mDock.surface().capsuleOutline(radiusPx, mProgramEdge));
    }

    private void setProgramEdge(@NonNull View card, boolean drawsEdge) {
        if (mProgramEdge == drawsEdge) return;
        mProgramEdge = drawsEdge;
        dressShape(card, mDock.resolveDockCapsuleCornerRadiusPx(cardHeightPx(card)));
    }

    /** {@code updateAccessoryRenderEffectBackdrop} for the capsule, on the card's views. */
    private void updateBackdrop(@NonNull ChromeSpec state, @NonNull View card,
                                @NonNull ImageView backdrop, boolean atRest) {
        // The surface the even-out is measured under: the card at a rest, none at a crossing's begin.
        View measured = atRest ? card : null;
        backdrop.setAlpha(1f);
        if (card.getWidth() <= 0 || card.getHeight() <= 0) {
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
        int seamOver = CapsuleGlass.seamOverscanPx(mSource.footPx(), hOver);
        int width = card.getWidth();
        int height = card.getHeight();
        CapsuleGlass.applyOverscan(backdrop, width, height, hOver, seamOver);
        // The laid-out origin: the lane's slide and the rows' lift are the anchor's, not the key.
        GlassAnchor.layoutOriginOnScreen(card, mOrigin, mRoot);
        Rect target = CapsuleGlass.targetRect(mOrigin, width, height, hOver, seamOver);
        SharedFrameDrawable installed = SharedFrameDrawable.of(backdrop.getDrawable());
        SurfaceDirtyLedger ledger = mHost.chrome().ledger();
        float cover = mDock.dockGlassCover();
        boolean drag = mSource.glassDragPreview();
        if (!ledger.isDirty(SurfaceDirtyLedger.Backdrop.KEYS_CARD) && cover == mCover
            && (drag || ledger.lastRadiusDp(SurfaceDirtyLedger.Backdrop.KEYS_CARD) == state.blurRadiusDp)
            && ledger.lastManagedSource(SurfaceDirtyLedger.Backdrop.KEYS_CARD) == managed
            && ledger.matchesLastRect(SurfaceDirtyLedger.Backdrop.KEYS_CARD, target)
            && mSource.pictureUp(backdrop)) {
            backdrop.setColorFilter(mSource.frostFilter(drag ? null : measured));
            backdrop.setVisibility(View.VISIBLE);
            syncShelf(state);
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
                ledger.markDirty(SurfaceDirtyLedger.Backdrop.KEYS_CARD);
                setProgramEdge(card, false);
            }
            return;
        }
        Drawable live = mSource.attachLive(backdrop, state.blurRadiusDp);
        mGround = mSource.ground(mGround, backdrop);
        mSource.installPicture(backdrop, installed, frame, mFrameRect, state.blurRadiusDp, live);
        mCover = cover;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The capsule: the program's rect and radius as the host's capsule computes them.
            float capsuleRadius = mDock.resolveDockCapsuleCornerRadiusPx(height);
            CapsuleGlass.capRect(mCap, hOver, width, height, seamOver,
                GlassRefraction.seamReachPx(GlassStack.lookFor(mSource.fancierLook()),
                    mHost.density(), capsuleRadius), 0);
            RenderEffect glass = mEffects.buildDockAndRemember(0f, mCap[0], mCap[1], mCap[2],
                mCap[3], capsuleRadius);
            backdrop.setRenderEffect(glass);
        }
        backdrop.setColorFilter(mSource.frostFilter(measured));
        backdrop.setVisibility(View.VISIBLE);
        setProgramEdge(card, mEffects.paramsValid());
        syncShelf(state);
        ledger.recordApplied(SurfaceDirtyLedger.Backdrop.KEYS_CARD, state.blurRadiusDp, managed,
            target);
    }

    /** {@link DockSurface#syncDockShelf} on the card's shelf: it follows the card by itself. */
    private void syncShelf(@Nullable ChromeSpec state) {
        DockShelfView shelf = mHost.findViewById(R.id.extra_keys_card_shelf_fx);
        if (shelf == null) return;
        View card = mHost.findViewById(R.id.extra_keys_card);
        boolean show = state != null && card != null && DockSurface.shelfShown(
            mEffects.paramsValid(), mHost.dockRows().surfaceShown(), state.keyboardShown, true,
            mDock.keysCardSlabSpec().contactShadow);
        shelf.setBeads(null);
        shelf.follow(show ? card : null);
    }

    private void clearBackdrop(@NonNull ImageView backdrop) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop.setRenderEffect(null);
        mSource.releaseLive(backdrop);
        backdrop.setImageDrawable(null);
        backdrop.setVisibility(View.GONE);
        mEffects.invalidateParams();
        mHost.chrome().ledger().reset(SurfaceDirtyLedger.Backdrop.KEYS_CARD);
        mProgramEdge = false;
        mCover = -1f;
        syncShelf(null);
    }

    private void hide(int id) {
        View view = mHost.findViewById(id);
        if (view != null && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    private static int cardHeightPx(@NonNull View card) {
        ViewGroup.LayoutParams params = card.getLayoutParams();
        return params != null && params.height > 0 ? params.height : card.getHeight();
    }

    /** Asks for the chrome pass once when the card's size moves while it is dressed. */
    private void watch(@NonNull View card) {
        if (mWatched == card) return;
        mWatched = card;
        card.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
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
