package com.termux.app.dock;

import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.KeyboardOverlayPolicy;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.RestCards;
import com.termux.app.place.StackLayout;
import com.termux.app.statusbar.StatusBarEdgeGeometry;
import com.termux.app.wall.PageRef;

/**
 * The two Floating cards at rest (ADR 0032, #302): while Home's dock card and the Terminal's
 * extra-keys card stand apart ({@link com.termux.app.place.SideCards}), Home's card stays on the
 * glass host, laid out at Home's rest, and the Terminal's card is {@link KeysCardLayout} inside
 * its row host. Both are laid out once per rest from one {@link RestCards} answer, so no landing
 * and no crossing lays either out again, and a start on either page needs no visit to the other.
 *
 * <p>The seam between the activity and that answer: the activity fills {@link Host} with raw
 * numbers and views and calls {@link #update} from its geometry pass at rest; this class builds
 * the two rests, reads the answer, writes the keys card and the host's bounds (compare before
 * write), and hands the stack's container margin and the renderer's side numbers back. Nothing
 * here runs while a crossing is live: the activity calls {@link #update} at rest only.
 */
public final class SideCardsController {

    /** What the controller reads off the activity; every answer tolerates a missing view or layout. */
    public interface Host {
        /** The window's view of {@code id}; null while it has none. */
        @Nullable View view(int id);
        /**
         * The page in front, {@link PageRef#HOME} or {@link PageRef#TERMINAL}; null while another
         * page or the Display window is (the cards then never stand apart).
         */
        @Nullable PageRef frontPage();
        /** {@code page}'s layout as that page wears it; null before there is a store. */
        @Nullable PlaceLayout layoutOf(@NonNull PageRef page);
        /** The dock's snapshot for {@code layout} with {@code rowsPage}'s rows standing. */
        @NonNull DockGeometry.Snapshot dockSnapshot(@NonNull PlaceLayout layout,
                                                    @NonNull PageKind rowsPage);
        /** The screen's width, height and density; the window is edge to edge. */
        @NonNull android.util.DisplayMetrics displayMetrics();
        /** The extra-keys row's measured height. */
        int measuredExtraKeysRowPx();
        /** The keyboard as the stack lays it out while it is down, its foot included. */
        int measuredKeyboardPx();
        /**
         * {@code page}'s own rest keyboard while another page is in front, as
         * {@link com.termux.app.place.SideCards#restKeyboardPx} has it: the keys' height while
         * that rest has it up, else {@code KEYBOARD_DOWN}. Its rest's answer, the one a crossing
         * toward it reads ({@link com.termux.app.place.SideCards#restKeyboardUp}), never the
         * front page's keyboard.
         */
        int restKeyboardPx(@NonNull PageRef page);
        /** The band a status bar along the bottom claims on {@code layout}, 0 elsewhere. */
        int bottomStatusBandPx(@NonNull PlaceLayout layout);
        /** The navigation band the content keeps clear at the screen's bottom. */
        int keptBandPx();
        /** The Terminal's flush padding at its own rest, known or worked out. */
        int terminalRestFlushPaddingPx();
        /** Whether the Terminal lays its dock padding at all (one pane, no IME). */
        boolean terminalFlushAllowed();
        boolean statusOnDockPlank();
        boolean keyboardGlassShared();
        /** The renderer's side numbers: whether the cards stand apart, Home's drop, the Terminal's lift. */
        void sidesApart(boolean apart, int homeDropPx, int terminalLiftPx);
    }

    @NonNull private final Host mHost;
    private final StackLayout.FlushRule mOtherFlush;

    private boolean mApart;
    private int mContainerMarginPx;
    private int mHomeDropPx;
    private int mTerminalLiftPx;
    private boolean mHostPinned;
    private int mHostLeftPx;
    private int mHostRightPx;
    private int mHostHeightPx;
    /** From the container's bottom edge up to the host's bottom edge, screen px. */
    private int mHostAboveContainerBottomPx;
    private boolean mKeysCardOn;
    private int mKeysCardLeftPx;
    private int mKeysCardRightPx;
    private int mKeysCardHeightPx;
    private float mKeysCardRadiusPx;
    private boolean mAnswered;
    /** What the glass host wears at Home's rest while the cards stand apart; null otherwise. */
    @Nullable private RestCards.HostLook mHostLook;
    private int mHostLookRevision;
    /** Where the host is drawn at Home's rest, screen px; the host's even-out is measured under it. */
    @Nullable private Box mHostRestRect;
    @Nullable private Box mKeysCardRestRect;

    public SideCardsController(@NonNull Host host) {
        mHost = host;
        mOtherFlush = (content, margin) -> mHost.terminalRestFlushPaddingPx();
    }

    /** Whether the cards stand apart at the last rest. */
    public boolean apart() {
        return mApart;
    }

    /**
     * The glass host's dress while the cards stand apart: Home's rest, worked out from the
     * answer whichever page is in front, so the host reads no value off the page in front
     * ({@link com.termux.app.place.SideCards#hostDressPage}). Null when it is dressed from the
     * front page, as it is whenever the cards stand together.
     */
    @Nullable
    public RestCards.HostLook hostLook() {
        return mHostLook;
    }

    /** Moves whenever {@link #hostLook} changes, so the host's glass is built again once. */
    public int hostLookRevision() {
        return mHostLookRevision;
    }

    /**
     * Where the host is drawn at Home's rest (screen px, as the answer has it), whether or not
     * it is parked there now; null unless the host is dressed as Home's rest.
     */
    @Nullable
    public Box hostRestRect() {
        return mHostRestRect;
    }

    /** Where the Terminal's extra-keys card is drawn at its rest while apart; null otherwise. */
    @Nullable
    public Box keysCardRestRect() {
        return mKeysCardRestRect;
    }

    /** Whether the glass host is laid out from the answer: Home's card, while the cards stand apart. */
    public boolean hostPinned() {
        return mHostPinned;
    }

    /**
     * The container's bottom margin: the Terminal's own at both rests while the cards stand
     * apart, so no landing moves it; {@code frontMarginPx}, the front's, otherwise.
     */
    public int containerMarginPx(int frontMarginPx) {
        return mApart ? mContainerMarginPx : frontMarginPx;
    }

    /** The exact height the host is laid out at while {@link #hostPinned}. */
    public int hostHeightPx() {
        return mHostHeightPx;
    }

    /**
     * Pins the glass host to the container's bottom at Home's rest: the answer's side margins and
     * height, and the gap above the container's bottom edge. {@code containerPaddingBottomPx} is
     * the container's own padding, which the pin stands above: 0 while pinned (apart, the
     * container pads nothing: {@link #containerPaddingPx}), so the gap is never negative.
     *
     * @return whether a field changed (the caller then sets the params)
     */
    public boolean layOutHost(@NonNull RelativeLayout.LayoutParams params,
                              int containerPaddingBottomPx) {
        int bottomMargin = Math.max(0, mHostAboveContainerBottomPx - containerPaddingBottomPx);
        boolean changed = params.getRule(RelativeLayout.ABOVE) != 0
            || params.getRule(RelativeLayout.ALIGN_PARENT_TOP) != 0
            || params.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM) != RelativeLayout.TRUE
            || params.leftMargin != mHostLeftPx || params.rightMargin != mHostRightPx
            || params.topMargin != 0 || params.bottomMargin != bottomMargin
            || params.width != android.view.ViewGroup.LayoutParams.MATCH_PARENT
            || params.height != mHostHeightPx;
        if (!changed) return false;
        params.removeRule(RelativeLayout.ABOVE);
        params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        params.leftMargin = mHostLeftPx;
        params.rightMargin = mHostRightPx;
        params.topMargin = 0;
        params.bottomMargin = bottomMargin;
        params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = mHostHeightPx;
        return true;
    }

    /**
     * One rest: works the answer out from {@code frontPass} (the pass the activity just laid the
     * front side out with) and the other side's rest, lays out the keys card, and hands the
     * renderer its numbers. Called at rest only: a live crossing lays nothing out.
     *
     * @return whether the host's bounds, the container's margin or the apart state moved
     */
    public boolean update(@NonNull StackLayout.Input frontPass) {
        RestCards answer = answerOf(frontPass);
        boolean apart = answer != null && answer.apart();
        boolean moved = !mAnswered || apart != mApart;
        mAnswered = true;
        mApart = apart;
        int margin = apart ? answer.containerMarginPx() : 0;
        int drop = apart ? answer.homeSideDropPx() : 0;
        moved |= margin != mContainerMarginPx || drop != mHomeDropPx;
        mContainerMarginPx = margin;
        mHomeDropPx = drop;

        // Home's card on the glass host, at Home's rest at both rests.
        boolean pinned = false;
        int left = 0;
        int right = 0;
        int height = 0;
        int above = 0;
        Box host = apart ? answer.laidOut(RestCards.StackView.HOST) : null;
        if (host != null) {
            pinned = true;
            left = Math.round(host.left);
            right = Math.round(mHost.displayMetrics().widthPixels - host.right);
            height = Math.round(host.height());
            above = Math.round(mHost.displayMetrics().heightPixels - margin - host.bottom);
        }
        // Dressed as Home's rest only where the host is Home's card (laid out from the answer).
        RestCards.HostLook look = pinned ? answer.homeHostLook() : null;
        if (!sameLook(look, mHostLook)) {
            moved = true;
            mHostLookRevision++;
        }
        mHostLook = look;
        mHostRestRect = pinned && look != null ? answer.homeHostRect() : null;
        moved |= pinned != mHostPinned || left != mHostLeftPx || right != mHostRightPx
            || height != mHostHeightPx || above != mHostAboveContainerBottomPx;
        mHostPinned = pinned;
        mHostLeftPx = left;
        mHostRightPx = right;
        mHostHeightPx = height;
        mHostAboveContainerBottomPx = above;

        mTerminalLiftPx = apart ? answer.terminalSideLiftPx() : 0;
        applyKeysCard(answer);
        mHost.sidesApart(apart, drop, mTerminalLiftPx);
        return moved;
    }

    /**
     * The container's bottom padding ({@link com.termux.app.place.SideCards#containerPaddingPx}):
     * none while apart, else {@code stackPaddingPx}, the rest's ({@code BottomInsets#stackPaddingPx}).
     */
    public int containerPaddingPx(int stackPaddingPx) {
        return com.termux.app.place.SideCards.containerPaddingPx(stackPaddingPx, mApart);
    }

    /**
     * How far above its layout the renderer draws the Terminal's side: the Terminal's own rest's
     * lift while apart, whichever page is in front
     * ({@link com.termux.app.place.SideCards#terminalSideLiftPx}), else 0.
     */
    public int terminalSideLiftPx() {
        return mTerminalLiftPx;
    }

    private static boolean sameLook(@Nullable RestCards.HostLook a, @Nullable RestCards.HostLook b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.design == b.design && a.peek == b.peek && a.floats == b.floats
            && a.foot == b.foot && a.keyboardUp == b.keyboardUp
            && java.util.Objects.equals(a.peekCut, b.peekCut);
    }

    /** The Terminal's card in its row host: from the answer when the cards stand apart, gone otherwise. */
    private void applyKeysCard(@Nullable RestCards answer) {
        View view = mHost.view(R.id.extra_keys_card);
        if (!(view instanceof KeysCardLayout)) return;
        KeysCardLayout card = (KeysCardLayout) view;
        Box box = answer != null && answer.apart()
            ? answer.laidOut(RestCards.StackView.KEYS_CARD) : null;
        RestCards.Card cardAnswer = box == null ? null
            : answer.cardOn(RestCards.StackView.KEYS_CARD);
        if (box == null || cardAnswer == null) {
            mKeysCardOn = false;
            mKeysCardRestRect = null;
            if (card.getVisibility() != View.GONE) card.setVisibility(View.GONE);
            return;
        }
        mKeysCardOn = true;
        mKeysCardRestRect = cardAnswer.box;
        mKeysCardLeftPx = Math.round(box.left);
        mKeysCardRightPx = Math.round(mHost.displayMetrics().widthPixels - box.right);
        mKeysCardHeightPx = Math.round(box.height());
        mKeysCardRadiusPx = cardAnswer.corners.topLeft;
        card.setBounds(mKeysCardLeftPx, mKeysCardRightPx, mKeysCardHeightPx);
        card.setCornerRadiusPx(mKeysCardRadiusPx);
        if (card.getVisibility() != View.VISIBLE) card.setVisibility(View.VISIBLE);
    }

    /** Whether the keys card stands now (the last rest). */
    public boolean keysCardOn() {
        return mKeysCardOn;
    }

    // ---- The rests ---------------------------------------------------------------------------

    @Nullable
    private RestCards answerOf(@NonNull StackLayout.Input frontPass) {
        PageRef front = mHost.frontPage();
        if (front == null) return null;
        boolean frontHome = front.isHome();
        PageRef otherPage = frontHome ? PageRef.TERMINAL : PageRef.HOME;
        PlaceLayout otherLayout = mHost.layoutOf(otherPage);
        if (otherLayout == null) return null;
        RestCards.Rest frontRest = restOf(frontPass, front);
        RestCards.Rest otherRest = restOf(otherPassOf(frontPass, otherLayout, otherPage),
            otherPage);
        frontRest.other = otherRest;
        return RestCards.of(frontRest);
    }

    /** The other side's pass, filled the way the activity fills the front's for that side's rest. */
    @NonNull
    private StackLayout.Input otherPassOf(@NonNull StackLayout.Input front,
                                          @NonNull PlaceLayout layout, @NonNull PageRef page) {
        PageKind kind = page.isHome() ? PageKind.HOME : PageKind.TERMINAL;
        StackLayout.Input in = new StackLayout.Input(mHost.dockSnapshot(layout, kind));
        in.toolbarShown = PlaceChromePolicy.dockShown(layout);
        in.extraKeysRowEnabled = PlaceChromePolicy.extraKeysRowShown(layout);
        in.toolbarHeightPx = in.extraKeysRowEnabled ? mHost.measuredExtraKeysRowPx() : 0;
        // The other side's own keyboard, not the front's: toggling the Terminal's keyboard leaves
        // Home's parked rest where it was, and each side is laid out at the rest it lands at.
        int keyboard = mHost.restKeyboardPx(page);
        in.keyboardShown = com.termux.app.place.SideCards.restKeyboardShown(keyboard);
        in.keyboardHeightPx = com.termux.app.place.SideCards.restKeyboardKeysPx(keyboard);
        in.statusBandPx = mHost.bottomStatusBandPx(layout);
        in.imeLiftPx = front.imeLiftPx;
        in.ceilingBeforeMarginPx = front.ceilingBeforeMarginPx;
        in.keyboardOverlays = KeyboardOverlayPolicy.overlays(kind, layout);
        in.dockOverlays = page.isHome();
        in.systemImeTakePx = front.systemImeTakePx;
        in.flushAllowed = !page.isHome() && mHost.terminalFlushAllowed();
        in.flush = mOtherFlush;
        return in;
    }

    @NonNull
    private RestCards.Rest restOf(@NonNull StackLayout.Input pass, @NonNull PageRef page) {
        android.util.DisplayMetrics metrics = mHost.displayMetrics();
        float density = metrics.density;
        RestCards.Rest rest = new RestCards.Rest(pass, page, metrics.widthPixels,
            metrics.heightPixels, density, mHost.measuredExtraKeysRowPx(),
            mHost.measuredKeyboardPx(),
            StatusBarEdgeGeometry.thicknessPx(Edge.TOP, false, false, density),
            StatusBarEdgeGeometry.thicknessPx(Edge.TOP, true, false, density));
        rest.keptBandPx = mHost.keptBandPx();
        rest.restFlushPaddingPx = mHost.terminalRestFlushPaddingPx();
        rest.statusOnDockPlank = mHost.statusOnDockPlank();
        rest.keyboardGlassShared = mHost.keyboardGlassShared();
        return rest;
    }
}
