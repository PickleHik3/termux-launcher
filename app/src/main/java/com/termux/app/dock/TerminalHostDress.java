package com.termux.app.dock;

import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeEdgeRule;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.GlassAnchor;
import com.termux.app.chrome.GlassEffects;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.chrome.GlassStack;
import com.termux.app.chrome.GroundUnderGlass;
import com.termux.app.chrome.SharedFrameDrawable;
import com.termux.app.chrome.SurfaceDirtyLedger;
import com.termux.app.place.ChromeShape.EdgeKind;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.RestCards;

/**
 * The Terminal's Docked card wearing the dock slab's glass while the cards stand apart (ADR 0032,
 * #304): a second back host ({@code terminal_surface_host}) beside the glass host, dressed the way
 * the activity dresses the host under Docked, on its own views. Its shape is the card the rest's
 * answer cuts ({@link SideCardsController#terminalHostCard}): the outline from its corners, the
 * rim under the glass on its rim edges only ({@code rimUnderGlass(0, rimEdges)}), every other
 * edge (the joins, the screen's) a seam the program's rim rect is pushed past. Docked keeps its
 * own formulas: square corners but where the shape model rounds one, flush to the screen.
 *
 * <p>Like {@link KeysCardDress} (which stays the capsule's), it is Live glass only while it can be
 * seen: dressed at a rest where the Terminal stands in front, and as a Home to Terminal crossing
 * begins; released, with its picture dropped, at a rest where it is gone or parked off screen at
 * Home's rest, and while the stack stands down on a frame. Never per frame.
 */
public final class TerminalHostDress {

    /** What only the activity owns: the keys card's needs, and the stack's stand-down. */
    public interface Source extends KeysCardDress.Source {
        /** The stack stands down on a pane's frame: the frame is the glass, the host wears none. */
        boolean standsDownOnFrame();
        /** The Terminal's Docked card at its rest; null while it does not stand. */
        @Nullable RestCards.Card card();
    }

    @NonNull private final DockController mDock;
    @NonNull private final DockController.Host mHost;
    @NonNull private final Source mSource;
    @NonNull private final GlassEffects mEffects;
    @NonNull private final CardOutline mOutline = new CardOutline();

    private float mCover = -1f;
    private boolean mProgramEdge;
    private boolean mShown;
    private int mRimEdges = ChromeEdgeRule.NONE;
    @Nullable private GroundUnderGlass mGround;
    private int mDressedWidthPx;
    private int mDressedHeightPx;
    @Nullable private View mWatched;
    @NonNull private final int[] mOrigin = new int[2];
    @NonNull private final int[] mRoot = new int[2];
    @NonNull private final float[] mCap = new float[4];
    @NonNull private final Rect mFrameRect = new Rect();

    public TerminalHostDress(@NonNull DockController dock, @NonNull Source source) {
        mDock = dock;
        mHost = dock.host();
        mSource = source;
        mEffects = new GlassEffects(new GlassEffects.Host() {
            @Override public float density() {
                return mHost.density();
            }
            @Nullable @Override public View dockBackdrop() {
                return mHost.findViewById(R.id.terminal_surface_blur_backdrop);
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
                // Docked: the slab does not float, so no shelf under it.
                DockSurface.applySlabGlass(program, mDock.keysCardSlabSpec(), false);
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

    /** Whether the host's glass is up (dressed and not released). */
    public boolean shown() {
        return mShown;
    }

    /**
     * One rest pass: dresses the host while it stands (the cards apart under Docked, the Terminal
     * in front), releases it where it is gone, parked at Home's rest, or the stack stands down on
     * a frame. A live crossing changes nothing.
     */
    public void update(@NonNull ChromeSpec state) {
        if (mSource.crossing()) return;
        View host = mHost.findViewById(R.id.terminal_surface_host);
        boolean standing = host != null && host.getVisibility() == View.VISIBLE
            && state.toolbarShown && mSource.card() != null && !mSource.standsDownOnFrame()
            && mHost.stackRenderer().terminalHostStands();
        if (!standing) {
            release();
            return;
        }
        dress(state, true);
    }

    /**
     * A Home to Terminal crossing begins: the host, laid out at the Terminal's rest already, is
     * dressed now so its picture is up when it first shows. A rest-like step, once per crossing.
     */
    public void onCrossingBegin(@NonNull ChromeSpec state) {
        View host = mHost.findViewById(R.id.terminal_surface_host);
        if (host == null || host.getVisibility() != View.VISIBLE || !state.toolbarShown
            || mSource.card() == null || mSource.standsDownOnFrame()) return;
        dress(state, false);
    }

    /** Lets the host's glass go: its picture and glass entry, its effect, its layers. */
    public void release() {
        if (!mShown && mWatched == null) return;
        mShown = false;
        ImageView backdrop = mHost.findViewById(R.id.terminal_surface_blur_backdrop);
        if (backdrop != null) clearBackdrop(backdrop);
        hide(R.id.terminal_surface_top_edge_fx);
        hide(R.id.terminal_surface_background);
        hide(R.id.terminal_surface_edge_glow_fx);
        hide(R.id.terminal_surface_specular_fx);
        View host = mHost.findViewById(R.id.terminal_surface_host);
        if (host != null && host.getBackground() != null) host.setBackground(null);
    }

    // ---- The dress -----------------------------------------------------------------------------

    private void dress(@NonNull ChromeSpec state, boolean atRest) {
        View host = mHost.findViewById(R.id.terminal_surface_host);
        ImageView backdrop = mHost.findViewById(R.id.terminal_surface_blur_backdrop);
        RestCards.Card card = mSource.card();
        if (host == null || backdrop == null || card == null) return;
        watch(host);
        mShown = true;
        mRimEdges = rimEdges(card);
        float barAlpha = state.barAlpha;
        View plate = mHost.findViewById(R.id.terminal_surface_background);
        if (plate != null) {
            plate.setBackground(mSource.plate(barAlpha));
            plate.setAlpha(1f);
            if (plate.getVisibility() != View.VISIBLE) plate.setVisibility(View.VISIBLE);
        }
        View specular = mHost.findViewById(R.id.terminal_surface_specular_fx);
        View glow = mHost.findViewById(R.id.terminal_surface_edge_glow_fx);
        mDock.dressPlankLayers(specular, glow, 0f, mHost.resolveDockAccentColor(), barAlpha);
        if (glow != null && glow.getVisibility() != View.VISIBLE) glow.setVisibility(View.VISIBLE);
        View edgeFx = mHost.findViewById(R.id.terminal_surface_top_edge_fx);
        if (edgeFx != null) {
            CapsuleGlass.dressTopEdge(edgeFx, (mRimEdges & ChromeEdgeRule.TOP) != 0, barAlpha,
                mSource.outlineColor());
        }
        dressShape(host, card);
        updateBackdrop(state, host, backdrop, atRest);
    }

    /** The rim edges of the card: those facing the opening; joins and the screen's are seams. */
    public static int rimEdges(@NonNull RestCards.Card card) {
        int edges = ChromeEdgeRule.NONE;
        for (Edge edge : Edge.values()) {
            if (card.edge(edge) == EdgeKind.RIM) edges |= ChromeEdgeRule.edgeBit(edge);
        }
        return edges;
    }

    /** The card's slice of the frame: the rim under the glass on its rim edges, its outline. */
    private void dressShape(@NonNull View host, @NonNull RestCards.Card card) {
        // The program's gathered light is the edge wherever it runs; a rim under it would be a
        // second one.
        int stroke = mProgramEdge ? ChromeEdgeRule.NONE : mRimEdges;
        host.setBackground(mHost.chrome().glass().rimUnderGlass(0f, stroke));
        boolean changed = mOutline.set(card.corners.topLeft, card.corners.topRight,
            card.corners.bottomRight, card.corners.bottomLeft);
        if (host.getOutlineProvider() != mOutline) host.setOutlineProvider(mOutline);
        else if (changed) host.invalidateOutline();
        host.setClipToOutline(true);
    }

    private void setProgramEdge(@NonNull View host, boolean drawsEdge) {
        if (mProgramEdge == drawsEdge) return;
        mProgramEdge = drawsEdge;
        RestCards.Card card = mSource.card();
        if (card != null) dressShape(host, card);
    }

    private void updateBackdrop(@NonNull ChromeSpec state, @NonNull View host,
                                @NonNull ImageView backdrop, boolean atRest) {
        View measured = atRest ? host : null;
        backdrop.setAlpha(1f);
        if (host.getWidth() <= 0 || host.getHeight() <= 0) {
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
        // The foot under the gesture pill is the host's own bottom: the backdrop reaches past it.
        int seamOver = CapsuleGlass.seamOverscanPx(mSource.footPx(), hOver);
        int width = host.getWidth();
        int height = host.getHeight();
        CapsuleGlass.applyOverscan(backdrop, width, height, hOver, seamOver);
        // The laid-out origin: the lane's slide and lift are the anchor's, not the key.
        GlassAnchor.layoutOriginOnScreen(host, mOrigin, mRoot);
        Rect target = CapsuleGlass.targetRect(mOrigin, width, height, hOver, seamOver);
        SharedFrameDrawable installed = SharedFrameDrawable.of(backdrop.getDrawable());
        SurfaceDirtyLedger ledger = mHost.chrome().ledger();
        float cover = mDock.dockGlassCover();
        boolean drag = mSource.glassDragPreview();
        if (!ledger.isDirty(SurfaceDirtyLedger.Backdrop.TERMINAL_HOST) && cover == mCover
            && (drag || ledger.lastRadiusDp(SurfaceDirtyLedger.Backdrop.TERMINAL_HOST)
                == state.blurRadiusDp)
            && ledger.lastManagedSource(SurfaceDirtyLedger.Backdrop.TERMINAL_HOST) == managed
            && ledger.matchesLastRect(SurfaceDirtyLedger.Backdrop.TERMINAL_HOST, target)
            && mSource.pictureUp(backdrop)) {
            backdrop.setColorFilter(mSource.frostFilter(drag ? null : measured));
            backdrop.setVisibility(View.VISIBLE);
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
                ledger.markDirty(SurfaceDirtyLedger.Backdrop.TERMINAL_HOST);
                setProgramEdge(host, false);
            }
            return;
        }
        android.graphics.drawable.Drawable live = mSource.attachLive(backdrop,
            state.blurRadiusDp);
        mGround = mSource.ground(mGround, backdrop);
        mSource.installPicture(backdrop, installed, frame, mFrameRect, state.blurRadiusDp, live);
        mCover = cover;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Docked: radius 0, and every edge but the rim's a seam the rim rect is pushed past.
            int seams = ChromeEdgeRule.ALL & ~mRimEdges;
            CapsuleGlass.capRect(mCap, hOver, width, height, seamOver,
                GlassRefraction.seamReachPx(GlassStack.lookFor(mSource.fancierLook()),
                    mHost.density(), 0f), seams);
            RenderEffect glass = mEffects.buildDockAndRemember(0f, mCap[0], mCap[1], mCap[2],
                mCap[3], 0f);
            backdrop.setRenderEffect(glass);
        }
        backdrop.setColorFilter(mSource.frostFilter(measured));
        backdrop.setVisibility(View.VISIBLE);
        setProgramEdge(host, mEffects.paramsValid());
        ledger.recordApplied(SurfaceDirtyLedger.Backdrop.TERMINAL_HOST, state.blurRadiusDp,
            managed, target);
    }

    private void clearBackdrop(@NonNull ImageView backdrop) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) backdrop.setRenderEffect(null);
        mSource.releaseLive(backdrop);
        backdrop.setImageDrawable(null);
        backdrop.setVisibility(View.GONE);
        mEffects.invalidateParams();
        mHost.chrome().ledger().reset(SurfaceDirtyLedger.Backdrop.TERMINAL_HOST);
        mProgramEdge = false;
        mCover = -1f;
    }

    private void hide(int id) {
        View view = mHost.findViewById(id);
        if (view != null && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    /** Asks for the chrome pass once when the host's size moves while it is dressed. */
    private void watch(@NonNull View host) {
        if (mWatched == host) return;
        mWatched = host;
        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
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

    /**
     * The card's outline: square, or rounded on one edge's two corners (Docked rounds only the
     * corners the shape model gives the screen's rounding). The rect runs past the square edge by
     * the radius so those corners stay square; any other mix rounds at the largest.
     */
    static final class CardOutline extends ViewOutlineProvider {
        private float mTopLeft = -1f;
        private float mTopRight = -1f;
        private float mBottomRight = -1f;
        private float mBottomLeft = -1f;

        boolean set(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            if (topLeft == mTopLeft && topRight == mTopRight && bottomRight == mBottomRight
                && bottomLeft == mBottomLeft) return false;
            mTopLeft = topLeft;
            mTopRight = topRight;
            mBottomRight = bottomRight;
            mBottomLeft = bottomLeft;
            return true;
        }

        @Override public void getOutline(@NonNull View view, @NonNull Outline outline) {
            int w = view.getWidth();
            int h = view.getHeight();
            float top = Math.max(mTopLeft, mTopRight);
            float bottom = Math.max(mBottomLeft, mBottomRight);
            if (top <= 0f && bottom <= 0f) {
                outline.setRect(0, 0, w, h);
            } else if (bottom <= 0f) {
                outline.setRoundRect(0, 0, w, h + (int) Math.ceil(top), top);
            } else if (top <= 0f) {
                outline.setRoundRect(0, -(int) Math.ceil(bottom), w, h, bottom);
            } else {
                outline.setRoundRect(0, 0, w, h, Math.max(top, bottom));
            }
        }
    }
}
