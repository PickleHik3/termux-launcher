package com.termux.app.dock;

import android.graphics.Outline;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeEdgeRule;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.chrome.RoundCut;
import com.termux.app.chrome.SurfaceDirtyLedger;
import com.termux.app.place.ChromeShape.EdgeKind;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.RestCards;

/**
 * The Terminal's card wearing the dock slab's glass while the cards stand apart (ADR 0032,
 * #304 under Docked; #305 under Floating with the apps row or A-Z beside the keys): a second back host ({@code terminal_dock_host}) beside the glass host, dressed the way
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
        /**
         * The Terminal's own Peek cut while its card is a Peek card apart from Home's (#307),
         * in the host's coordinates; null otherwise.
         */
        @Nullable PeekGlassGeometry.Card peekCut();
        /** The apps row as the Terminal's reflection reads it, its host's pose its own; null for none. */
        @Nullable PeekGlassReflection.Row peekRow();
        /** The Reflection knob's strength ({@link PeekGlassGeometry#reflectionAlpha}); 0 for none. */
        float peekReflectionAlpha();
    }

    @NonNull private final DockController mDock;
    @NonNull private final DockController.Host mHost;
    @NonNull private final Source mSource;
    @NonNull private final CardOutline mOutline = new CardOutline();

    /** The card's glass radius ({@link #capsuleRadiusPx}): 0 under Docked. */
    private float mRadiusPx;
    private int mRimEdges = ChromeEdgeRule.NONE;
    @NonNull private final GlassBackdropDress mBackdrop;

    public TerminalHostDress(@NonNull DockController dock, @NonNull Source source) {
        mDock = dock;
        mHost = dock.host();
        mSource = source;
        mBackdrop = new GlassBackdropDress(dock, source, new GlassBackdropDress.Owner() {
            @Override public float glassRadiusPx(int heightPx) {
                return mRadiusPx;
            }
            @Override public int seams() {
                // Docked: radius 0, and every edge but the rim's a seam the rim rect is pushed past.
                // Floating (#305, rows beside the keys): the capsule's radius, a rim all round.
                return ChromeEdgeRule.ALL & ~mRimEdges;
            }
            @Override public void dressShape(@NonNull View surface) {
                RestCards.Card card = mSource.card();
                if (card != null) TerminalHostDress.this.dressShape(surface, card);
            }
            @Override public void syncShelf(@Nullable ChromeSpec state) {
                // Docked: the slab does not float, so no shelf under it.
            }
            @Override public boolean cutRect(@NonNull float[] out, int hOver, float reach) {
                PeekGlassGeometry.Card peek = peekCut();
                if (peek == null) return false;
                // The Peek card's rim follows its cut; its bottom runs on off the screen.
                PeekCardAim.cut(peek, mCut);
                GlassRefraction.rimRect(out, hOver + mCut[RoundCut.LEFT], mCut[RoundCut.TOP],
                    hOver + mCut[RoundCut.RIGHT], mCut[RoundCut.BOTTOM], reach,
                    ChromeEdgeRule.BOTTOM);
                return true;
            }
        }, SurfaceDirtyLedger.Backdrop.TERMINAL_HOST, R.id.terminal_dock_blur_backdrop, false);
    }

    /** The Peek card's cut, reused: the outline provider reads it on every outline pass. */
    @NonNull private final float[] mCut = new float[RoundCut.SIZE];
    @NonNull private final float[] mRowAt = new float[2];
    /**
     * The icons' reflection on the Terminal's own Peek card: one more {@link PeekGlassReflection},
     * drawn on the Live path only (the row's render nodes, recorded, never a bitmap), so a second
     * Peek card holds no second picture of the icons; with no Live path it shows none. Kept as
     * the Terminal's rest drew it while its card is parked (the reflection stays on the parked
     * card), let go once the card is no Peek card.
     */
    @Nullable private PeekGlassReflection mReflection;

    /** The Terminal's Peek cut while its card is the Peek card; null otherwise. */
    @Nullable
    private PeekGlassGeometry.Card peekCut() {
        RestCards.Card card = mSource.card();
        return card != null && card.id == RestCards.CardId.PEEK ? mSource.peekCut() : null;
    }

    /** The host clipped to the Peek cut: its top corners rounded, its lower ones off screen. */
    private final ViewOutlineProvider mPeekOutline = new ViewOutlineProvider() {
        @Override public void getOutline(@NonNull View view, @NonNull Outline outline) {
            PeekGlassGeometry.Card peek = peekCut();
            if (peek == null) {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
                return;
            }
            PeekCardAim.cut(peek, mCut);
            outline.setRoundRect(Math.round(mCut[RoundCut.LEFT]), Math.round(mCut[RoundCut.TOP]),
                Math.round(mCut[RoundCut.RIGHT]), Math.round(mCut[RoundCut.BOTTOM]),
                mCut[RoundCut.RADIUS]);
        }
    };

    /** The row's content changed (an icon, a dot, a press): the reflection shows it as the icon does. */
    public void onRowContentChanged() {
        if (mReflection != null) mReflection.onRowContentChanged();
    }

    /** A page swipe moved the icons: the Live reflection rides with them. */
    public void onIconsMoved() {
        if (mReflection != null) mReflection.onIconsMoved();
    }

    /** The host moved across with the Terminal's side: the reflection is drawn where it now stands. */
    public void onLaneMoved() {
        if (mReflection != null) mReflection.onLaneMoved();
    }

    /**
     * The reflection at a rest: drawn for the Terminal's Peek card while it stands in front on
     * the Live path, kept as drawn while the card is parked, let go when there is no Peek card or
     * no Live path (the bound: no second bitmap of the icons, ever).
     */
    private void syncReflection(@Nullable View host, boolean standing) {
        PeekGlassGeometry.Card peek = peekCut();
        float alpha = mSource.peekReflectionAlpha();
        boolean live = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && mSource.fancierLook() != null;
        if (peek == null || alpha <= 0f || !live || !(host instanceof ViewGroup)) {
            if (mReflection != null) mReflection.clear();
            return;
        }
        if (!standing) {
            if (mReflection != null) mReflection.setHeld(true);
            return;
        }
        PeekGlassReflection.Row row = mSource.peekRow();
        View container = mHost.findViewById(R.id.accessory_stack_container);
        float[] rowAt = row == null || container == null ? null
            : DockController.layoutOffsetIn(row.view(), container);
        float[] hostAt = container == null ? null : DockController.layoutOffsetIn(host, container);
        if (row == null || rowAt == null || hostAt == null) {
            if (mReflection != null) mReflection.clear();
            return;
        }
        mRowAt[0] = rowAt[0] - hostAt[0];
        mRowAt[1] = rowAt[1] - hostAt[1];
        if (mReflection == null) mReflection = new PeekGlassReflection(mHost.density());
        mReflection.attachHost((ViewGroup) host, mHost.findViewById(R.id.terminal_dock_background));
        mReflection.attachStrip(null, 0f, 0f);
        mReflection.update(peek, row, mRowAt[0], mRowAt[1], alpha, true);
        mReflection.setHeld(false);
    }

    /** The stack's view of the host whose backdrop is {@code backdropId}; null for any other. */
    @Nullable public static RestCards.StackView stackViewOf(int backdropId) {
        return backdropId == R.id.terminal_dock_blur_backdrop
            ? RestCards.StackView.TERMINAL_HOST : null;
    }

    /** Whether the host's glass is up (dressed and not released). */
    public boolean shown() {
        return mBackdrop.shown();
    }

    /**
     * One rest pass: dresses the host while it stands (the cards apart under Docked, the Terminal
     * in front), releases it where it is gone, parked at Home's rest, or the stack stands down on
     * a frame. A live crossing changes nothing.
     */
    public void update(@NonNull ChromeSpec state) {
        if (mSource.crossing()) return;
        View host = mHost.findViewById(R.id.terminal_dock_host);
        boolean standing = host != null && host.getVisibility() == View.VISIBLE
            && state.toolbarShown && mSource.card() != null && !mSource.standsDownOnFrame()
            && mHost.stackRenderer().terminalHostStands();
        if (!standing) {
            release();
            syncReflection(host, false);
            return;
        }
        dress(state, true);
        syncReflection(host, true);
    }

    /**
     * A Home to Terminal crossing begins: the host, laid out at the Terminal's rest already, is
     * dressed now so its picture is up when it first shows. A rest-like step, once per crossing.
     */
    public void onCrossingBegin(@NonNull ChromeSpec state) {
        View host = mHost.findViewById(R.id.terminal_dock_host);
        if (host == null || host.getVisibility() != View.VISIBLE || !state.toolbarShown
            || mSource.card() == null || mSource.standsDownOnFrame()) return;
        dress(state, false);
    }

    /** Lets the host's glass go: its picture and glass entry, its effect, its layers. */
    public void release() {
        if (!mBackdrop.shown() && !mBackdrop.watching()) return;
        mBackdrop.setShown(false);
        ImageView backdrop = mHost.findViewById(R.id.terminal_dock_blur_backdrop);
        if (backdrop != null) mBackdrop.clearBackdrop(backdrop);
        mBackdrop.hide(R.id.terminal_dock_top_edge_fx);
        mBackdrop.hide(R.id.terminal_dock_background);
        mBackdrop.hide(R.id.terminal_dock_edge_glow_fx);
        mBackdrop.hide(R.id.terminal_dock_specular_fx);
        View host = mHost.findViewById(R.id.terminal_dock_host);
        if (host != null && host.getBackground() != null) host.setBackground(null);
    }

    // ---- The dress -----------------------------------------------------------------------------

    private void dress(@NonNull ChromeSpec state, boolean atRest) {
        View host = mHost.findViewById(R.id.terminal_dock_host);
        ImageView backdrop = mHost.findViewById(R.id.terminal_dock_blur_backdrop);
        RestCards.Card card = mSource.card();
        if (host == null || backdrop == null || card == null) return;
        mBackdrop.watch(host);
        mBackdrop.setShown(true);
        mRimEdges = rimEdges(card);
        mRadiusPx = capsuleRadiusPx(card);
        PeekGlassGeometry.Card peek = peekCut();
        if (peek != null) {
            // The Peek dress (#307): the glass and its rim rounded with the cut.
            PeekCardAim.cut(peek, mCut);
            mRadiusPx = mCut[RoundCut.RADIUS];
        }
        float barAlpha = state.barAlpha;
        View plate = mHost.findViewById(R.id.terminal_dock_background);
        if (plate != null) {
            plate.setBackground(mSource.plate(barAlpha));
            plate.setAlpha(1f);
            if (plate.getVisibility() != View.VISIBLE) plate.setVisibility(View.VISIBLE);
        }
        View specular = mHost.findViewById(R.id.terminal_dock_specular_fx);
        View glow = mHost.findViewById(R.id.terminal_dock_edge_glow_fx);
        mDock.dressPlankLayers(specular, glow, mRadiusPx, mHost.resolveDockAccentColor(),
            barAlpha);
        if (glow != null && glow.getVisibility() != View.VISIBLE) glow.setVisibility(View.VISIBLE);
        View edgeFx = mHost.findViewById(R.id.terminal_dock_top_edge_fx);
        if (edgeFx != null) {
            // The Peek card's top is its cut through the icons, not the host's top: no seam band.
            CapsuleGlass.dressTopEdge(edgeFx, peek == null
                && (mRimEdges & ChromeEdgeRule.TOP) != 0, barAlpha, mSource.outlineColor());
        }
        dressShape(host, card);
        mBackdrop.updateBackdrop(state, host, backdrop, atRest);
    }

    /**
     * The glass radius of the card: a Floating card (a rim on every edge, its four corners one
     * radius, #305) is a capsule and its glass and rim are rounded with it; a Docked slice keeps
     * radius 0 and rounds only its outline.
     */
    static float capsuleRadiusPx(@NonNull RestCards.Card card) {
        for (Edge edge : Edge.values()) if (card.edge(edge) != EdgeKind.RIM) return 0f;
        float r = card.corners.topLeft;
        return r == card.corners.topRight && r == card.corners.bottomRight
            && r == card.corners.bottomLeft ? Math.max(0f, r) : 0f;
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
        int stroke = mBackdrop.programEdge() ? ChromeEdgeRule.NONE : mRimEdges;
        PeekGlassGeometry.Card peek = peekCut();
        if (peek != null) {
            // The Peek dress: the rim moved onto the cut, its bottom left open, the host clipped
            // to the cut (as Home's host wears it).
            PeekCardAim.cut(peek, mCut);
            android.graphics.drawable.Drawable rim = stroke == ChromeEdgeRule.NONE ? null
                : mHost.chrome().glass().rimUnderGlass(mCut[RoundCut.RADIUS], stroke);
            host.setBackground(rim == null || host.getWidth() <= 0 ? null
                : PeekRimDrawable.forCut(rim, mCut, host.getWidth()));
            if (host.getOutlineProvider() != mPeekOutline) host.setOutlineProvider(mPeekOutline);
            else host.invalidateOutline();
            host.setClipToOutline(true);
            return;
        }
        host.setBackground(mHost.chrome().glass().rimUnderGlass(mRadiusPx, stroke));
        boolean changed = mOutline.set(card.corners.topLeft, card.corners.topRight,
            card.corners.bottomRight, card.corners.bottomLeft);
        if (host.getOutlineProvider() != mOutline) {
            host.setOutlineProvider(mOutline);
            host.invalidateOutline();
        } else if (changed) host.invalidateOutline();
        host.setClipToOutline(true);
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
