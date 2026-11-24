package com.termux.app;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.page.Side;
import com.termux.app.place.EdgeStackView;
import com.termux.app.place.HomeKeyboardCover;
import com.termux.app.place.Element;
import com.termux.app.place.RestCards;
import com.termux.app.place.StackEnd;
import com.termux.app.place.StackGeometry;
import com.termux.app.place.StackLanes;
import com.termux.app.place.StackScene;
import com.termux.app.wall.PageRef;
import com.termux.app.window.FrameGuards;
import com.termux.app.window.FramePipeline;
import com.termux.app.window.FrameStep;

/**
 * The only writer of the bottom stack's transforms: the container's translation and clip, the
 * rows' and the glass host's sink and cut, the keyboard's clip and the bands under it held up,
 * and the dock plank's tilt composed onto them ({@code crossing ∘ plank}). Each owner of a share
 * hands its numbers in (the crossing's travel and sink, the system IME's lift, the stack's
 * slide, the plank's {@link DockPlankController.Slab}), which only records it and asks the frame
 * pipeline for its {@link FrameStep#STACK} step; the step ({@link #apply}) reads the stack as it
 * is about to be laid out, asks {@link StackScene#at} for the frame, and writes only what differs
 * from what it last wrote. Without a pipeline (a test, before the window) a share is written at
 * once.
 *
 * <p>It registers every property it owns with the pipeline's guards (ADR 0028 section 5): with
 * the motion log on, a live value that is not the one it last wrote counts
 * {@code foreign-writer}, and a value changed on a crossing's landing frame counts
 * {@code landing-wrote}. Nothing here allocates per frame.
 */
public final class StackRenderer implements DockPlankController.SlabSink {

    /** What the renderer reads off the activity. */
    public interface Views {
        /** The window's view of {@code id}; null while it has none. */
        @Nullable View view(int id);
        /** The keyboard's measured height, about to be laid out; 0 while unmeasured. */
        int keyboardDesiredHeightPx();
        /**
         * The foot of that height, below the keys, while the keyboard wears one
         * ({@link com.termux.app.place.BottomInsets#keyboardFootPx}), else 0.
         */
        default int keyboardFootPx() {
            return 0;
        }
        /** Whether bands stand under the keyboard. */
        boolean underKeyboardBandsShown();
        /** Whether the in-app keyboard is up: the plank then tilts the glass host alone. */
        boolean keyboardShown();
        /**
         * The in-app keyboard's keys' height (its foot not counted) while it stands laid out but
         * down, else 0: the stack stands this much lower at rest ({@link StackScene#keyboardTranslationPx}).
         */
        default int keyboardDownLaidOutPx() {
            return 0;
        }
        /**
         * Whether Home's side stands in front, else the Terminal's: the kind the wall committed
         * to ({@code Pages#committedKind}), which is the arrival from the commit on, so the landing
         * frame's rest is the side the crossing arrived at.
         */
        default boolean frontIsHome() {
            return false;
        }
        /**
         * A lane host or a card was just moved across: whatever draws the row from outside the
         * host (the Peek glass reflection) draws it again where the host now stands.
         */
        default void laneMoved(@NonNull View host) {
        }
        /** Whether the app drawer or an editor owns the stack's transforms for now. */
        default boolean othersOwnStack() {
            return false;
        }
    }

    /** The sinking layers by {@link StackGeometry} index, in {@code CrossingViews}' order. */
    private static final int[] LAYER_IDS = {R.id.accessory_surface_host,
        R.id.apps_bar_az_fx_underlay, R.id.accessory_row_stack, R.id.apps_bar_az_fx_overlay,
        R.id.apps_bar_az_label_overlay};
    /** The rows that stand on the plank and are handed its slab when they are not inside it. */
    private static final int[] PLANK_CONTENT_IDS = {R.id.apps_bar_plank_layer,
        com.termux.app.dock.SideRowViews.id(com.termux.app.place.Element.AZ,
            com.termux.app.dock.SideRowViews.Part.ROW),
        R.id.apps_bar_indicator_band};

    /** The row hosts a side's lane may carry, the stack's own children ({@link EdgeStackView#getLane}). */
    private static final int[] LANE_HOST_IDS = {
        com.termux.app.dock.SideRowViews.id(Element.APPS,
            com.termux.app.dock.SideRowViews.Part.BOTTOM_HOST),
        com.termux.app.dock.SideRowViews.id(Element.AZ,
            com.termux.app.dock.SideRowViews.Part.BOTTOM_HOST),
        com.termux.app.dock.SideRowViews.id(Element.EXTRA_KEYS,
            com.termux.app.dock.SideRowViews.Part.BOTTOM_HOST)};

    /** Where the plank's slab goes. */
    static final int PLANK_NONE = 0;
    static final int PLANK_CONTAINER = 1;
    static final int PLANK_HOST = 2;

    @NonNull private final Views mViews;
    /** The pipeline whose {@link FrameStep#STACK} step writes; null writes at once. */
    @Nullable private FramePipeline mFrames;
    /** This writer on the pipeline's guards, with every property it owns; null without one. */
    @Nullable private FrameGuards.Writer mGuard;
    private int mGuardSlots;
    /** The next apply is a crossing's landing frame, which should write nothing new. */
    private boolean mLandingPending;

    private final StackScene.Motion mMotion = new StackScene.Motion();
    /** The motion the scene is asked for: the crossing's with the resting keyboard's share added. */
    private final StackScene.Motion mEffective = new StackScene.Motion();
    private final StackGeometry mGeometry = new StackGeometry();
    private final StackScene.Frame mFrame = new StackScene.Frame();
    /**
     * The crossing's two ends by side and its progress between them, set by the wall's frames and
     * cleared at the landing; with none the scene is the front side at rest. The ends are made
     * once: a frame allocates nothing.
     */
    private final StackEnd mEndHome = new StackEnd(PageRef.HOME);
    private final StackEnd mEndTerminal = new StackEnd(PageRef.TERMINAL);
    private boolean mHasEnds;
    /** The cards stand apart, and Home's drop while they do ({@link #setSidesApart}). */
    private boolean mApart;
    private float mHomeDropPx;
    /**
     * Home's rows are covered by the keyboard ({@link #setHomeCover}) and what the system keyboard
     * takes from the root's room; the drops, below, are this frame's, the scene's
     * ({@link StackScene.Frame#coverHomePx}).
     */
    private boolean mHomeCovered;
    private int mSystemImeTakePx;
    private float mCoverHomeDropPx;
    private float mCoverSharedDropPx;
    /** The keyboard's drawn top in the container's pixels while it shows height, else NaN. */
    private float mCoverKeyboardTopPx = Float.NaN;
    /** The cut last written to each lane host's clip, and to which view; 0 for none. */
    private final int[] mWrittenLaneCut = new int[LANE_HOST_IDS.length];
    private final View[] mWrittenLaneCutView = new View[LANE_HOST_IDS.length];
    /** What was last written to each lane host's translationY, and to which view. */
    private final float[] mWrittenLaneTy = new float[LANE_HOST_IDS.length];
    private boolean mFromHome;
    private boolean mToHome;
    private float mEndsProgress;
    /** What was last written to each lane host's translationX, and to which view. */
    private final View[] mWrittenLaneView = new View[LANE_HOST_IDS.length];
    private final float[] mWrittenLaneTx = new float[LANE_HOST_IDS.length];
    private final DockPlankController.Slab mSlab = new DockPlankController.Slab();
    private int mPlankTarget = PLANK_NONE;

    /** The extra-keys card's pose as last drawn: a move of it alone re-aims its glass. */
    private float mDrawnKeysCardTx = Float.NaN;
    private float mDrawnKeysCardTy = Float.NaN;
    /**
     * The Terminal's own host and whether it is the slab, looked up once per {@link #apply}
     * ({@link #terminalHostStands}) for every write of that frame.
     */
    @Nullable private View mFrameTerminalHost;
    private boolean mFrameTerminalHostStands;
    private static final int HOST_VIEW = RestCards.StackView.HOST.ordinal();
    private static final int TERMINAL_HOST_VIEW = RestCards.StackView.TERMINAL_HOST.ordinal();
    private static final int KEYS_CARD_VIEW = RestCards.StackView.KEYS_CARD.ordinal();

    /**
     * A card view's own slot, after the scene's ({@link StackScene#V_COUNT}), by its
     * {@link RestCards.StackView} ordinal: the Terminal's extra-keys card and its own host (#304).
     */
    private static final int SLOT_KEYS_CARD = StackScene.V_COUNT + KEYS_CARD_VIEW;
    private static final int SLOT_TERMINAL_HOST = StackScene.V_COUNT + TERMINAL_HOST_VIEW;
    private static final int FOOT_VIEW = RestCards.StackView.FOOT.ordinal();
    private static final int SLOT_FOOT = StackScene.V_COUNT + FOOT_VIEW;
    private static final int SLOTS = StackScene.V_COUNT + StackEnd.VIEW_COUNT;
    // What was last written, per view slot (StackScene.V_*), and to which view object.
    private final View[] mWrittenView = new View[SLOTS];
    private final float[] mWrittenTy = new float[SLOTS];
    private final boolean[] mWrittenClipped = new boolean[SLOTS];
    private final boolean[] mClipKnown = new boolean[SLOTS];
    private final Rect[] mWrittenClip = new Rect[SLOTS];
    /** The container's and the host's plank parts as last written (tx, rotations, scales, pivots). */
    private final float[][] mWrittenPlank = new float[SLOTS][7];
    private final View[] mWrittenContent = new View[PLANK_CONTENT_IDS.length];
    private final DockPlankController.Slab mWrittenContentSlab = new DockPlankController.Slab();
    private int mWrittenContentTarget = -1;
    private final float[] mOffset = new float[2];
    private final Rect mTmpClip = new Rect();
    private final Rect mTmpLive = new Rect();
    private final float[] mTmpPlank = new float[7];
    /** Whether a frame was applied yet: until then every share is written as it comes. */
    private boolean mApplied;
    /** The number of view writes made so far: a test's and a trace's count. */
    int mWriteCount;

    public StackRenderer(@NonNull Views views) {
        mViews = views;
        for (int v = 0; v < SLOTS; v++) mWrittenClip[v] = new Rect();
    }

    /**
     * Joins {@code frames}: its {@link FrameStep#STACK} step writes from here on, and the
     * properties this writer owns register with its guards. Once per pipeline.
     */
    public void attach(@NonNull FramePipeline frames) {
        if (mFrames == frames) return;
        mFrames = frames;
        frames.bind(FrameStep.STACK, this::apply);
        FrameGuards.Writer guard = frames.guards().writer("stack");
        own(guard, StackScene.V_CONTAINER, R.id.accessory_stack_container);
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            own(guard, StackScene.V_LAYER0 + i, LAYER_IDS[i]);
        }
        own(guard, StackScene.V_UNDER, R.id.accessory_under_keyboard_stack);
        own(guard, StackScene.V_KEYBOARD, R.id.inapp_keyboard_container);
        own(guard, StackScene.V_BODY, R.id.terminal_background);
        guard.own("accessory_stack_container.translationX", () -> {
            View view = mViews.view(R.id.accessory_stack_container);
            return view == null ? 0f : view.getTranslationX();
        });
        for (int lane = 0; lane < LANE_HOST_IDS.length; lane++) {
            final int id = LANE_HOST_IDS[lane];
            guard.own("lane_host_" + lane + ".translationX", () -> {
                View view = mViews.view(id);
                return view == null ? 0f : view.getTranslationX();
            });
        }
        mGuardSlots = 2 * StackScene.V_COUNT + 1 + LANE_HOST_IDS.length;
        mGuard = guard;
    }

    /**
     * Registers slot {@code v}'s two properties: its translationY (the keyboard's is not the
     * stack's) and the bottom of its clip, -1 for none.
     */
    private void own(@NonNull FrameGuards.Writer guard, int v, int id) {
        String name = OWNED_NAMES[v];
        guard.own(name + ".translationY", () -> {
            View view = mViews.view(id);
            return view == null || v == StackScene.V_KEYBOARD ? 0f : view.getTranslationY();
        });
        guard.own(name + ".clipBottom", () -> {
            View view = mViews.view(id);
            if (view == null || v == StackScene.V_UNDER || !view.getClipBounds(mTmpGuard)) return -1f;
            return mTmpGuard.bottom;
        });
    }

    private final Rect mTmpGuard = new Rect();
    /** The guards' names of the views by slot ({@link StackScene#V_CONTAINER} ..). */
    private static final String[] OWNED_NAMES = {"accessory_stack_container",
        "accessory_surface_host", "apps_bar_az_fx_underlay", "accessory_row_stack",
        "apps_bar_az_fx_overlay", "apps_bar_az_label_overlay", "inapp_keyboard_container",
        "accessory_under_keyboard_stack", "terminal_background"};

    /** A share changed: the step writes it on this frame (at once without a pipeline). */
    private void requestApply() {
        FramePipeline frames = mFrames;
        if (frames != null) frames.requestFrame(FrameStep.STACK);
        else apply();
    }

    /**
     * The crossing landed: the next apply is its landing frame. At progress 1 the scene already
     * stands the arriving rest, so that apply should change nothing; any change is counted.
     */
    public void onLanding() {
        mHasEnds = false;
        mLandingPending = true;
        requestApply();
    }

    // ---- The shares handed in ---------------------------------------------------------------

    /** The wall's travel: the stack drawn {@code dockPx} lower, {@code keyboardPx} of it the keyboard's. */
    public void setTravel(float dockPx, float keyboardPx, float imeLiftPx) {
        // Negative while the system keyboard rides in: the stack is drawn lower than laid out.
        float lift = imeLiftPx;
        // A share that has not moved changes nothing the last apply did not already write; a
        // layout pass that moved the stack's bottom asks with apply() itself.
        if (mMotion.dockTravelPx == dockPx && mMotion.keyboardSharePx == keyboardPx
            && mMotion.imeLiftPx == lift && mApplied) {
            requestApply();
            return;
        }
        mMotion.dockTravelPx = dockPx;
        mMotion.keyboardSharePx = keyboardPx;
        mMotion.imeLiftPx = lift;
        requestApply();
    }

    /** The layers' sink and cut ({@link StackScene.Motion#layerSink}). */
    public void setSink(float rowsPx, float rowsCutPx, float sheetPx, float sheetCutPx) {
        StackScene.Motion m = mMotion;
        if (mApplied && m.rowsSinkPx == rowsPx && m.rowsCutPx == rowsCutPx
            && m.sheetSinkPx == sheetPx && m.sheetCutPx == sheetCutPx) return;
        m.layerSink(rowsPx, rowsCutPx, sheetPx, sheetCutPx);
        requestApply();
    }

    /**
     * The crossing's ends and where it is between them: {@code progress} 0 is the side it began
     * on ({@code fromHome}), 1 the side it arrives on. Each side's lane slides with its page
     * ({@link StackScene#sideSlide}); the shared rows do not. The landing clears it.
     */
    public void setEnds(boolean fromHome, boolean toHome, float progress) {
        if (mHasEnds && mFromHome == fromHome && mToHome == toHome && mEndsProgress == progress
            && mApplied) return;
        mHasEnds = true;
        mFromHome = fromHome;
        mToHome = toHome;
        mEndsProgress = progress;
        requestApply();
    }

    /**
     * Whether Home's card and the Terminal's extra-keys card stand apart (SideCards), and how far
     * below the container's layout Home's side is drawn then ({@code SideCards#homeSideDropPx}).
     * Written by the pass at rest only. While apart the glass host is Home's card alone: it takes
     * no shift, slides with Home's lane, and stands the drop lower with Home's other layers and
     * lane rows; the plank tilts the Terminal's card, not the parked host, while the Terminal is
     * in front.
     */
    public void setSidesApart(boolean apart, float homeDropPx) {
        setSidesApart(apart, homeDropPx, 0);
    }

    /**
     * {@link #setSidesApart(boolean, float)} with the Terminal side's lift
     * ({@code SideCards#terminalSideLiftPx}, the Terminal's own rest's whichever page is in
     * front): the flush inset the container no longer pads while apart. The Terminal's lane rows
     * are drawn that much higher at every frame, the keyboard's column by the Terminal's share
     * ({@code SideCards#columnLiftPx}), and the keyboard's cut counts the column's lift as it
     * counted the padding, so the keyboard hides where it did. No layout carries it.
     */
    public void setSidesApart(boolean apart, float homeDropPx, int terminalLiftPx) {
        float drop = apart ? homeDropPx : 0f;
        int lift = apart ? Math.max(0, terminalLiftPx) : 0;
        if (mApart == apart && mHomeDropPx == drop && mTerminalLiftPx == lift) return;
        mApart = apart;
        mHomeDropPx = drop;
        mTerminalLiftPx = lift;
        // Each side's card stands its rest's own offset (StackScene's cards): the same numbers.
        mEndHome.homeDropPx = mEndTerminal.homeDropPx = drop;
        mEndHome.terminalLiftPx = mEndTerminal.terminalLiftPx = lift;
        requestApply();
    }

    /**
     * Each side's rest's glass cards ({@link StackEnd#readCards}), written by the rest pass at rest
     * only: the scene pairs them by id across a crossing and poses each card view. Null for a side
     * with no answer.
     */
    public void setCards(@Nullable RestCards home, @Nullable RestCards terminal) {
        mEndHome.readCards(home);
        mEndTerminal.readCards(terminal);
        requestApply();
    }

    /** {@code home}'s side's end of the scene; a test fills its card table ({@link StackEnd#setCard}). */
    @NonNull
    StackEnd end(boolean home) {
        return home ? mEndHome : mEndTerminal;
    }

    /** The Terminal side's lift while apart ({@link #setSidesApart(boolean, float, int)}). */
    private int mTerminalLiftPx;
    /** This frame's Terminal share of the scene (1 at its rest) and the column's lift from it. */
    private float mTerminalShare;
    private float mColumnLiftPx;

    /**
     * Each side's room, the window's bottom up to where its page's cells end
     * ({@link com.termux.app.place.PageRoom#wallInsetPx}): what the flat body surface is cut to at
     * that side's rest ({@link StackScene#bodyCutPx}). Written at rest, once the rests are known;
     * a crossing blends the two and its landing finds the arriving one in place.
     */
    public void setRooms(int homePx, int terminalPx) {
        if (mApplied && mEndHome.roomPx == homePx && mEndTerminal.roomPx == terminalPx) return;
        mEndHome.roomPx = homePx;
        mEndTerminal.roomPx = terminalPx;
        requestApply();
    }

    /** The crossing is over without a landing's own call: the lanes stand at the front rest. */
    public void clearEnds() {
        if (!mHasEnds) return;
        mHasEnds = false;
        requestApply();
    }

    @Override public int slabWidth() {
        View target = plankView(plankTarget());
        return target == null ? 0 : target.getWidth();
    }

    @Override public int slabHeight() {
        View target = plankView(plankTarget());
        return target == null ? 0 : target.getHeight();
    }

    /** The plank's springs moved: its slab is composed onto the crossing's on this frame. */
    @Override public void onSlab(@NonNull DockPlankController.Slab slab) {
        mSlab.set(slab);
        requestApply();
    }

    /** The view the plank tilts now, for its touch's hit test; null before the stack is inflated. */
    @Nullable
    public View plankTargetView() {
        return plankView(plankTarget());
    }

    /**
     * Whether Home's rows are covered by the keyboard instead of lifted by it (#309), and what the
     * system keyboard takes from the root's room (its padding), which Home's stack stands back
     * down by. Written by the activity at rest; the defaults draw as before. A write only on a
     * change.
     */
    public void setHomeCover(boolean homeRowsCovered, int systemImeTakePx) {
        int take = Math.max(0, systemImeTakePx);
        if (mHomeCovered == homeRowsCovered && mSystemImeTakePx == take) return;
        mHomeCovered = homeRowsCovered;
        mSystemImeTakePx = take;
        requestApply();
    }

    /** One rest change or chrome commit at rest: written unless another surface owns the stack. */
    public void applyAtRest() {
        if (mViews.othersOwnStack()) return;
        requestApply();
    }

    // ---- The frame ---------------------------------------------------------------------------

    /** Reads the stack, asks the scene for this frame, and writes what changed. */
    public void apply() {
        View container = mViews.view(R.id.accessory_stack_container);
        if (container == null) return;
        mApplied = true;
        FrameGuards.Writer guard = mGuard;
        boolean landing = mLandingPending;
        mLandingPending = false;
        if (guard != null) guard.check();
        // The scene's two ends: the crossing's, or the side in front at rest.
        final StackEnd from;
        final StackEnd to;
        final float p;
        if (mHasEnds) {
            from = end(mFromHome);
            to = end(mToHome);
            p = mEndsProgress;
        } else {
            from = to = end(mViews.frontIsHome());
            p = 0f;
        }
        final float homeShare = StackScene.homeShare(from, to, p);
        mTerminalShare = 1f - homeShare;
        mColumnLiftPx = com.termux.app.place.SideCards.columnLiftPx(mApart, mTerminalLiftPx,
            mTerminalShare);
        readGeometry(container);
        // A crossing's own travel carries the laid-out keyboard at the seam's reveal
        // (PlaceChromeTravel#keyboardLaidOutPx), so the rest's share joins only at rest.
        StackScene.withKeyboardRest(mMotion, mHasEnds ? 0f
            : StackScene.keyboardTranslationPx(0f, mViews.keyboardDownLaidOutPx()), mEffective);
        // Home's cover (#309): the rows stand at their keyboard-down rect whatever the keyboard
        // does, and the system keyboard does not raise Home's stack.
        final boolean covered = mHomeCovered;
        final int keyboardPx = mGeometry.keyboardPresent && mGeometry.keyboardInColumn
            ? mGeometry.keyboardHeightPx : 0;
        final float coverShare = mEffective.keyboardSharePx;
        mEffective.homeCoverPx = covered
            ? HomeKeyboardCover.rowsDropPx(false, keyboardPx, coverShare) : 0f;
        StackScene.at(from, to, p, mEffective, mGeometry, mFrame);
        mPlankTarget = plankTarget();
        mFrameTerminalHost = mViews.view(R.id.terminal_dock_host);
        mFrameTerminalHostStands = hostStands(mFrameTerminalHost);
        mCoverHomeDropPx = mFrame.coverHomePx;
        mCoverSharedDropPx = mFrame.coverSharedPx;
        mCoverKeyboardTopPx = covered && keyboardPx > 0 && coverShare < keyboardPx
            ? mGeometry.keyboardTopPx : Float.NaN;
        final float imeStand = homeShare * HomeKeyboardCover.imeStandPx(!covered,
            mSystemImeTakePx, 0, mEffective.imeLiftPx);
        boolean containerMoved = writeTy(StackScene.V_CONTAINER, container,
            mFrame.ty[StackScene.V_CONTAINER] + imeStand + plankTy(PLANK_CONTAINER));
        writePlank(StackScene.V_CONTAINER, container, PLANK_CONTAINER, 0f);
        containerMoved |= writeClip(StackScene.V_CONTAINER, container);
        if (containerMoved) {
            // The dock's and the keyboard's glass sample the wallpaper through this translation;
            // a transform alone redraws nothing, so they are re-aimed here.
            invalidateIfShown(R.id.accessory_blur_backdrop);
            invalidateIfShown(R.id.extra_keys_card_blur_backdrop);
            invalidateIfShown(R.id.terminal_dock_blur_backdrop);
            invalidateIfShown(R.id.dock_foot_blur_backdrop);
            invalidateIfDressed(R.id.inapp_keyboard_view_host);
            invalidateIfDressed(R.id.accessory_under_keyboard_stack);
        }
        boolean layersMoved = false;
        // The host carries the card the scene poses on it: Home's, sliding with Home and standing
        // where Home's side stands, or one both ends draw alike, which keeps its rest. The plank
        // tilts the card the scene names (the Terminal's own while it stands).
        final boolean keysTilt = mFrameTerminalHostStands || keysCardStands();
        final float hostTx = mFrame.cardTx[HOST_VIEW];
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            int v = StackScene.V_LAYER0 + i;
            View layer = mViews.view(LAYER_IDS[i]);
            if (layer == null) continue;
            boolean host = i == StackGeometry.HOST;
            // Home's drop: the host and the A-Z effect layers, not the rows (their hosts carry it).
            float drop = mApart && i != StackGeometry.ROWS ? mHomeDropPx : 0f;
            // The cover's drop: apart, Home's own layers its whole drop and the rows (whose hosts
            // carry it) none; else every layer Home's share of it.
            float cover = mApart ? (i == StackGeometry.ROWS ? 0f : mCoverHomeDropPx)
                : mCoverSharedDropPx;
            if (host) cover = mFrame.cardCoverPx[HOST_VIEW];
            float layerTy = host ? mFrame.cardTy[HOST_VIEW] + (keysTilt ? 0f : plankTy(PLANK_HOST))
                : mFrame.ty[v] + drop + cover;
            layersMoved |= writeTy(v, layer, layerTy);
            if (cover > 0f && !Float.isNaN(mCoverKeyboardTopPx)) {
                int h = mGeometry.layerHeightPx[i];
                StackScene.addBottomCut(mFrame, v, mGeometry.layerWidthPx[i], h,
                    mGeometry.displayHeightPx, HomeKeyboardCover.coverCutPx(
                        mGeometry.layerTopPx[i] + h + layerTy, mCoverKeyboardTopPx));
            }
            if (host) {
                float txWas = layer.getTranslationX();
                writePlank(v, layer, keysTilt ? PLANK_NONE : PLANK_HOST, hostTx);
                if (layer.getTranslationX() != txWas) {
                    layersMoved = true;
                    mViews.laneMoved(layer);
                }
            }
            layersMoved |= writeClip(v, layer);
        }
        View keysCard = mViews.view(R.id.extra_keys_card);
        if (keysCard != null) {
            // The card rides its lane; its glass samples where the scene draws it, so a move of
            // the card alone re-aims it.
            boolean keysStands = keysCardStands();
            boolean moved = writeTy(SLOT_KEYS_CARD, keysCard, keysStands ? plankTy(PLANK_HOST) : 0f);
            writePlank(SLOT_KEYS_CARD, keysCard, keysStands ? PLANK_HOST : PLANK_NONE, 0f);
            float tx = mFrame.cardTx[KEYS_CARD_VIEW];
            float ty = mFrame.cardTy[KEYS_CARD_VIEW];
            if (moved || tx != mDrawnKeysCardTx || ty != mDrawnKeysCardTy) {
                mDrawnKeysCardTx = tx;
                mDrawnKeysCardTy = ty;
                invalidateIfShown(R.id.extra_keys_card_blur_backdrop);
            }
        }
        writeTerminalHost(keysTilt);
        writeFoot();
        writeAzEdges();
        // The dock's glass samples the wallpaper where it is drawn; a transform redraws nothing.
        if (layersMoved) invalidateIfShown(R.id.accessory_blur_backdrop);
        // The keyboard's column is the front page's: it carries the Terminal's lift by its share.
        View column = mViews.view(R.id.accessory_keyboard_column);
        if (column != null && column.getTranslationY() != -mColumnLiftPx) {
            column.setTranslationY(-mColumnLiftPx);
            mWriteCount++;
            invalidateIfDressed(R.id.inapp_keyboard_view_host);
            invalidateIfDressed(R.id.accessory_under_keyboard_stack);
        }
        View under = mViews.view(R.id.accessory_under_keyboard_stack);
        if (under != null && writeTy(StackScene.V_UNDER, under, mFrame.ty[StackScene.V_UNDER])) {
            if (under.getBackground() != null) under.invalidate();
        }
        View keyboard = mViews.view(R.id.inapp_keyboard_container);
        if (keyboard != null) {
            writeClip(StackScene.V_KEYBOARD, keyboard);
            // A down keyboard takes no touch: its translation leaves a strip of it on the screen.
            if (keyboard instanceof com.termux.app.terminal.inappkeyboard.KeyboardContainerLayout) {
                ((com.termux.app.terminal.inappkeyboard.KeyboardContainerLayout) keyboard)
                    .setTouchRefused(mViews.keyboardDownLaidOutPx() > 0);
                // While the keyboard is up its box is its own on every page: no touch of it
                // falls through to the dock under it (Home) or the wall's page swipe.
                ((com.termux.app.terminal.inappkeyboard.KeyboardContainerLayout) keyboard)
                    .setCoversBelow(com.termux.app.terminal.inappkeyboard.KeyboardContainerLayout
                        .ownsItsBox(mViews.keyboardDownLaidOutPx()));
            }
        }
        View body = mViews.view(R.id.terminal_background);
        if (body != null) {
            watchBody(body);
            writeClip(StackScene.V_BODY, body);
        }
        writeLanes();
        writeContent();
        // DIAG(kb-cut): the keyboard's motion and cut on each crossing frame; revert before merge.
        if (mHasEnds && com.termux.app.chrome.MotionLog.enabled) {
            View kb = mViews.view(R.id.inapp_keyboard_container);
            com.termux.app.chrome.MotionLog.log("kb-cut", "p=" + mEndsProgress + " share="
                + mEffective.keyboardSharePx + " travel=" + mEffective.dockTravelPx + " kbH="
                + mGeometry.keyboardHeightPx + " foot=" + mGeometry.keyboardFootPx + " pad="
                + mGeometry.stackPaddingBottomPx + " below=" + mGeometry.belowKeyboardInColumnPx
                + " inColumn=" + mGeometry.keyboardInColumn + " clipped="
                + mFrame.clipped[StackScene.V_KEYBOARD] + " clipBottom="
                + mFrame.clipBottom[StackScene.V_KEYBOARD] + " containerTy="
                + mFrame.ty[StackScene.V_CONTAINER] + " kbVis=" + (kb == null ? -1 : kb.getVisibility())
                + " kbTop=" + mGeometry.keyboardTopPx + " containerH=" + mGeometry.containerHeightPx);
        }
        if (guard != null) {
            // On a landing frame every change is counted: the scene at progress 1 is the rest.
            FrameGuards guards = mFrames.guards();
            if (landing) guards.setLanding(true);
            for (int slot = 0; slot < mGuardSlots; slot++) guard.wrote(slot);
            if (landing) guards.setLanding(false);
        }
    }

    /**
     * Each side's lane rows across by their side's slide: the scene's {@code sideTx} less the part
     * the container's own, so the rows stand where the scene puts them. A row in no lane stands at 0. At rest the front side's lane writes nothing.
     */
    private void writeLanes() {
        View rowStack = mViews.view(R.id.accessory_row_stack);
        if (!(rowStack instanceof EdgeStackView)) return;
        EdgeStackView stack = (EdgeStackView) rowStack;
        for (int i = 0; i < LANE_HOST_IDS.length; i++) {
            View host = mViews.view(LANE_HOST_IDS[i]);
            if (host == null) continue;
            float want = 0f;
            float wantTy = 0f;
            if (host.getParent() == stack) {
                int lane = stack.getLane(host);
                // Home's lane its drop, the Terminal's its lift, both whichever page is in front.
                wantTy = com.termux.app.place.SideCards.laneTyPx(lane, mApart, mHomeDropPx,
                    mTerminalLiftPx, mTerminalShare, mCoverHomeDropPx, mCoverSharedDropPx);
                if (lane != StackLanes.STACK) {
                    want = mFrame.sideTx[(lane == StackLanes.HOME ? Side.HOME : Side.TERMINAL)
                        .ordinal()];
                }
            }
            if (host != mWrittenLaneView[i]) {
                mWrittenLaneView[i] = host;
                mWrittenLaneTx[i] = Float.NaN;
                mWrittenLaneTy[i] = Float.NaN;
            }
            if (mWrittenLaneTy[i] != wantTy) {
                mWrittenLaneTy[i] = wantTy;
                if (host.getTranslationY() != wantTy) {
                    host.setTranslationY(wantTy);
                    mWriteCount++;
                    mViews.laneMoved(host);
                }
            }
            writeLaneCut(i, host, lane(stack, host), wantTy);
            if (mWrittenLaneTx[i] == want) continue;
            mWrittenLaneTx[i] = want;
            if (host.getTranslationX() == want) continue;
            host.setTranslationX(want);
            mWriteCount++;
            mViews.laneMoved(host);
        }
    }

    private static int lane(@NonNull EdgeStackView stack, @NonNull View host) {
        return host.getParent() == stack ? stack.getLane(host) : StackLanes.STACK;
    }

    /**
     * While the cards stand apart, the lane host the keyboard covers is cut at the keyboard's top
     * ({@link HomeKeyboardCover#coverCutPx}) the way the layers are, in the host's own pixels; the
     * cut goes with the drop. Written only on a change.
     */
    private void writeLaneCut(int i, @NonNull View host, int lane, float laneTy) {
        int cut = 0;
        float drop = lane == StackLanes.HOME ? mCoverHomeDropPx
            : lane == StackLanes.STACK ? mCoverSharedDropPx : 0f;
        if (mApart && drop > 0f && !Float.isNaN(mCoverKeyboardTopPx)
            && host.getParent() instanceof View) {
            View rows = (View) host.getParent();
            int h = host.getHeight();
            cut = HomeKeyboardCover.coverCutPx(rows.getTop() + mFrame.ty[StackScene.V_ROWS]
                + host.getTop() + h + laneTy, mCoverKeyboardTopPx);
        }
        if (host != mWrittenLaneCutView[i]) {
            mWrittenLaneCutView[i] = host;
            mWrittenLaneCut[i] = -1;
        }
        if (mWrittenLaneCut[i] == cut) return;
        if (mWrittenLaneCut[i] == -1 && cut == 0) {
            mWrittenLaneCut[i] = 0;
            return;
        }
        mWrittenLaneCut[i] = cut;
        if (cut <= 0) {
            host.setClipBounds(null);
        } else {
            int w = Math.max(1, host.getWidth());
            int h = host.getHeight();
            mTmpClip.set(-w, -Math.max(h, mGeometry.displayHeightPx), 2 * w, Math.max(0, h - cut));
            host.setClipBounds(mTmpClip);
        }
        mWriteCount++;
        mViews.laneMoved(host);
    }

    private int plankTarget() {
        return mViews.keyboardShown() ? PLANK_HOST : PLANK_CONTAINER;
    }

    /**
     * The Terminal's Docked host (#304) as the scene poses the Terminal's card: the rows' layer's
     * translation, the Terminal side's slide and its lift, as the extra-keys row host draws them,
     * so the host travels with the Terminal's rows; the plank tilts it while it stands. Its glass
     * samples where the scene draws it ({@link #cardDrawnX}).
     */
    private void writeTerminalHost(boolean tilts) {
        View host = mFrameTerminalHost;
        if (host == null) return;
        float tx = mFrame.cardTx[TERMINAL_HOST_VIEW];
        float ty = mFrame.cardTy[TERMINAL_HOST_VIEW];
        boolean stands = tilts && mFrameTerminalHostStands;
        boolean moved = writeTy(SLOT_TERMINAL_HOST, host, ty + (stands ? plankTy(PLANK_HOST) : 0f));
        float txWas = host.getTranslationX();
        writePlank(SLOT_TERMINAL_HOST, host, stands ? PLANK_HOST : PLANK_NONE, tx);
        boolean across = host.getTranslationX() != txWas;
        // The Terminal's own Peek card's reflection is drawn where the host now stands (#307).
        if (across) mViews.laneMoved(host);
        if (moved || across) invalidateIfShown(R.id.terminal_dock_blur_backdrop);
    }

    /**
     * The foot's own view (#307) as the scene poses the shared foot: the host's sheet layer's
     * translation and the cover's shared drop, never a side's slide, so a crossing leaves it
     * still. Its glass samples where it is drawn, so a move of it re-aims the picture.
     */
    private void writeFoot() {
        View foot = mViews.view(R.id.dock_foot_host);
        if (foot == null) return;
        if (writeTy(SLOT_FOOT, foot, mFrame.cardTy[FOOT_VIEW]))
            invalidateIfShown(R.id.dock_foot_blur_backdrop);
    }

    private static final int AZ_EDGE_VIEW = RestCards.StackView.AZ_EDGE.ordinal();
    private static final int TERMINAL_AZ_EDGE_VIEW = RestCards.StackView.TERMINAL_AZ_EDGE.ordinal();

    /**
     * The A–Z bar's edge hosts across a Home ⇄ Terminal crossing (#307): the edge stack's host is
     * the leaving side's card and slides out with it, the crossing host the arriving side's and
     * slides in with it ({@code AzEdgeCrossingCard}), each at the scene's slide of its side's
     * view. A card both ends draw at one rect, a Home to Home crossing and a rest stand at 0.
     */
    private void writeAzEdges() {
        boolean across = mHasEnds && mFromHome != mToHome;
        float leaving = across
            ? mFrame.cardTx[mFromHome ? AZ_EDGE_VIEW : TERMINAL_AZ_EDGE_VIEW] : 0f;
        float arriving = across
            ? mFrame.cardTx[mToHome ? AZ_EDGE_VIEW : TERMINAL_AZ_EDGE_VIEW] : 0f;
        writeTx(mViews.view(R.id.place_az_bar_host), leaving);
        writeTx(mViews.view(R.id.place_az_bar_crossing_host), arriving);
    }

    /** {@code view}'s translationX, written only on a change. */
    private void writeTx(@Nullable View view, float tx) {
        if (view == null || view.getTranslationX() == tx) return;
        view.setTranslationX(tx);
        mWriteCount++;
    }

    /**
     * Where the scene draws {@code view}'s card on the frame last written, across and down from
     * its layout, the plank's tilt not counted: the card's glass samples the wallpaper there
     * ({@code GlassAnchor#layout}). 0 for a view with no card.
     */
    public float cardDrawnX(@NonNull RestCards.StackView view) {
        return mFrame.cardTx[view.ordinal()];
    }

    /** {@link #cardDrawnX} down. */
    public float cardDrawnY(@NonNull RestCards.StackView view) {
        return mFrame.cardTy[view.ordinal()];
    }

    /**
     * How far below its layout the scene draws the bands under the keyboard on the frame last
     * written: the keyboard column's lift and the bands' own hold, the two translations this
     * writer puts between the container and them. Their glass samples the wallpaper there.
     */
    public float underDrawnY() {
        return mFrame.ty[StackScene.V_UNDER] - mColumnLiftPx;
    }

    /** Whether the Terminal's own card (the extra-keys card or its host) is the dock's slab now. */
    public boolean terminalCardStands() {
        return keysCardStands() || terminalHostStands();
    }

    /**
     * Whether the Terminal's own host is the dock's slab now (#304, #305): the scene names it
     * ({@link StackScene.Frame#slabView}) and it is shown. The plank's tilt, its specular and its
     * rim glow follow it.
     */
    public boolean terminalHostStands() {
        return hostStands(mViews.view(R.id.terminal_dock_host));
    }

    /** {@link #terminalHostStands} for the Terminal's host {@code host}, already looked up. */
    private boolean hostStands(@Nullable View host) {
        return mFrame.slabView == TERMINAL_HOST_VIEW && host != null
            && host.getVisibility() == View.VISIBLE;
    }

    /**
     * Whether the Terminal's extra-keys card is the dock's slab now: the scene names it and it is
     * not gone. The plank's tilt, its specular and its rim glow follow it.
     */
    public boolean keysCardStands() {
        if (mFrame.slabView != KEYS_CARD_VIEW) return false;
        View card = mViews.view(R.id.extra_keys_card);
        return card != null && card.getVisibility() != View.GONE;
    }

    @Nullable
    private View plankView(int target) {
        if (target == PLANK_HOST && terminalHostStands())
            return mViews.view(R.id.terminal_dock_host);
        if (target == PLANK_HOST && keysCardStands()) return mViews.view(R.id.extra_keys_card);
        return target == PLANK_HOST ? mViews.view(R.id.accessory_surface_host)
            : target == PLANK_CONTAINER ? mViews.view(R.id.accessory_stack_container) : null;
    }

    private float plankTy(int target) {
        return mPlankTarget == target ? mSlab.translationY : 0f;
    }

    private void readGeometry(@NonNull View container) {
        StackGeometry g = mGeometry;
        android.util.DisplayMetrics metrics = container.getResources().getDisplayMetrics();
        g.displayWidthPx = metrics.widthPixels;
        g.displayHeightPx = metrics.heightPixels;
        g.containerWidthPx = container.getWidth();
        // The height it is about to be laid out at: a pre-roll has just written it, and the
        // traversal that applies it runs before this frame draws.
        ViewGroup.LayoutParams params = container.getLayoutParams();
        g.containerHeightPx = params != null && params.height > 0 ? params.height
            : Math.max(0, container.getHeight());
        // While apart the flush inset is the column's lift, not the padding: counted the same.
        final int columnLift = Math.round(mColumnLiftPx);
        g.stackPaddingBottomPx = container.getPaddingBottom() + columnLift;
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            View layer = mViews.view(LAYER_IDS[i]);
            g.layerPresent[i] = layer != null;
            g.layerTopPx[i] = layer == null ? 0 : layer.getTop();
            g.layerWidthPx[i] = layer == null ? 0 : layer.getWidth();
            g.layerHeightPx[i] = layer == null ? 0 : layer.getHeight();
        }
        View keyboard = mViews.view(R.id.inapp_keyboard_container);
        View column = mViews.view(R.id.accessory_keyboard_column);
        View under = mViews.view(R.id.accessory_under_keyboard_stack);
        g.keyboardPresent = keyboard != null && keyboard.getVisibility() != View.GONE;
        g.keyboardInColumn = g.keyboardPresent && column != null && keyboard.getParent() == column;
        g.keyboardWidthPx = keyboard == null ? 0 : keyboard.getWidth();
        // The height it is about to be laid out at: a pre-roll has just measured it.
        int measured = mViews.keyboardDesiredHeightPx();
        g.keyboardHeightPx = measured > 0 ? measured
            : keyboard == null ? 0 : Math.max(0, keyboard.getHeight());
        g.keyboardFootPx = Math.min(g.keyboardHeightPx, Math.max(0, mViews.keyboardFootPx()));
        g.keyboardTopPx = keyboard == null ? 0
            : keyboard.getTop() + (column != null ? column.getTop() : 0) - columnLift;
        g.underShown = under != null && mViews.underKeyboardBandsShown();
        g.belowKeyboardInColumnPx = g.keyboardInColumn ? belowKeyboardInColumnPx(column, under) : 0;
        g.underTopPx = under == null ? 0
            : under.getTop() + (column != null ? column.getTop() : 0) - columnLift;
        g.underBottomPx = under == null ? 0 : g.underTopPx + under.getHeight();
        g.footTopPx = 0;
        g.footBottomPx = 0;
        // The body surface at the size it is about to be laid out at (its parent's less its
        // margins), and the room that layout stands for.
        View body = mViews.view(R.id.terminal_background);
        g.bodyWidthPx = 0;
        g.bodyHeightPx = 0;
        g.bodyLaidOutRoomPx = 0;
        if (body != null && body.getParent() instanceof View
            && body.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            View parent = (View) body.getParent();
            ViewGroup.MarginLayoutParams margins =
                (ViewGroup.MarginLayoutParams) body.getLayoutParams();
            g.bodyWidthPx = Math.max(0, parent.getWidth() - parent.getPaddingLeft()
                - parent.getPaddingRight() - margins.leftMargin - margins.rightMargin);
            // The parent's height with the top margin it was laid out with, never one written
            // since: the canvas reaching under the status bar moves the margin at once and the
            // parent's height on the next layout (PageTopInsets), so the two read together are off
            // by the reach for the frame between. The bottom is the room's, written before the
            // layout it asks for.
            int top = parent == mBodyParent && mBodyParentTopMarginPx >= 0
                ? mBodyParentTopMarginPx : margins.topMargin;
            g.bodyHeightPx = Math.max(0, parent.getHeight() - parent.getPaddingTop()
                - parent.getPaddingBottom() - top - margins.bottomMargin);
            g.bodyLaidOutRoomPx = margins.bottomMargin;
        }
    }

    /** The body surface whose layout changes ask for a frame: its cut is read off its size. */
    @Nullable private View mWatchedBody;
    private final View.OnLayoutChangeListener mBodyLayout =
        (v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) requestApply();
        };

    /** The body's parent, and the body's top margin as that parent was last laid out; -1 unknown. */
    @Nullable private View mBodyParent;
    private int mBodyParentTopMarginPx = -1;
    /** The parent laid the body out (even GONE): its top margin now matches its height. */
    private final View.OnLayoutChangeListener mBodyParentLayout =
        (v, l, t, r, b, ol, ot, or, ob) -> {
            View body = mWatchedBody;
            if (body == null || body.getParent() != v
                || !(body.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
            int top = ((ViewGroup.MarginLayoutParams) body.getLayoutParams()).topMargin;
            if (top == mBodyParentTopMarginPx && b - t == ob - ot) return;
            mBodyParentTopMarginPx = top;
            requestApply();
        };

    private void watchBody(@NonNull View body) {
        if (mWatchedBody == body) return;
        if (mWatchedBody != null) mWatchedBody.removeOnLayoutChangeListener(mBodyLayout);
        if (mBodyParent != null) mBodyParent.removeOnLayoutChangeListener(mBodyParentLayout);
        mWatchedBody = body;
        body.addOnLayoutChangeListener(mBodyLayout);
        mBodyParent = body.getParent() instanceof View ? (View) body.getParent() : null;
        mBodyParentTopMarginPx = -1;
        if (mBodyParent != null) mBodyParent.addOnLayoutChangeListener(mBodyParentLayout);
    }

    /**
     * How far the keyboard column's bottom stands under the keyboard: the bands under it with
     * their air, and the column's own padding.
     */
    static int belowKeyboardInColumnPx(@NonNull View column, @Nullable View under) {
        int below = column.getPaddingBottom();
        if (under == null || under.getVisibility() == View.GONE || under.getParent() != column)
            return below;
        below += under.getHeight();
        ViewGroup.LayoutParams params = under.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            below += ((ViewGroup.MarginLayoutParams) params).topMargin
                + ((ViewGroup.MarginLayoutParams) params).bottomMargin;
        }
        return below;
    }

    // ---- Writes ------------------------------------------------------------------------------

    /** A view that is not the one last written to starts afresh: everything is written. */
    private void fresh(int v, @NonNull View view) {
        if (mWrittenView[v] == view) return;
        mWrittenView[v] = view;
        mWrittenTy[v] = Float.NaN;
        mClipKnown[v] = false;
        for (int i = 0; i < 7; i++) mWrittenPlank[v][i] = Float.NaN;
    }

    private boolean writeTy(int v, @NonNull View view, float ty) {
        fresh(v, view);
        if (mWrittenTy[v] == ty) return false;
        mWrittenTy[v] = ty;
        if (view.getTranslationY() == ty) return false;
        view.setTranslationY(ty);
        mWriteCount++;
        return true;
    }

    private boolean writeClip(int v, @NonNull View view) {
        fresh(v, view);
        Rect written = mWrittenClip[v];
        if (!mFrame.clipped[v]) {
            if (mClipKnown[v] && !mWrittenClipped[v]) return false;
            mClipKnown[v] = true;
            mWrittenClipped[v] = false;
            if (!view.getClipBounds(mTmpLive)) return false;
            view.setClipBounds(null);
            mWriteCount++;
            return true;
        }
        mTmpClip.set(mFrame.clipLeft[v], mFrame.clipTop[v], mFrame.clipRight[v],
            mFrame.clipBottom[v]);
        if (mClipKnown[v] && mWrittenClipped[v] && written.equals(mTmpClip)) return false;
        mClipKnown[v] = true;
        mWrittenClipped[v] = true;
        written.set(mTmpClip);
        view.setClipBounds(mTmpClip);
        mWriteCount++;
        return true;
    }

    /**
     * The plank's parts of {@code view}'s transform beyond its translationY: the slab's slide,
     * tilt, dip and pivot while the plank tilts this view, neutral otherwise, plus {@code baseX}
     * (the stack's slide, on the container). A resting plank on its own target writes nothing.
     */
    private void writePlank(int v, @NonNull View view, int target, float baseX) {
        boolean tilts = mPlankTarget == target;
        DockPlankController.Slab s = mSlab;
        float[] want = mTmpPlank;
        want[0] = baseX + (tilts ? s.translationX : 0f);
        want[1] = tilts ? s.rotationX : 0f;
        want[2] = tilts ? s.rotationY : 0f;
        want[3] = tilts ? s.scaleX : 1f;
        want[4] = tilts ? s.scaleY : 1f;
        // The pivot moves nothing while neither rotation nor scale does.
        boolean pivotFree = want[1] == 0f && want[2] == 0f && want[3] == 1f && want[4] == 1f;
        float[] written = mWrittenPlank[v];
        want[5] = pivotFree ? written[5] : s.pivotX;
        want[6] = pivotFree ? written[6] : s.pivotY;
        for (int i = 0; i < 7; i++) {
            if (written[i] == want[i] || (Float.isNaN(want[i]) && Float.isNaN(written[i])))
                continue;
            written[i] = want[i];
            if (Float.isNaN(want[i])) continue;
            switch (i) {
                case 0: if (view.getTranslationX() != want[0]) { view.setTranslationX(want[0]); mWriteCount++; } break;
                case 1: if (view.getRotationX() != want[1]) { view.setRotationX(want[1]); mWriteCount++; } break;
                case 2: if (view.getRotationY() != want[2]) { view.setRotationY(want[2]); mWriteCount++; } break;
                case 3: if (view.getScaleX() != want[3]) { view.setScaleX(want[3]); mWriteCount++; } break;
                case 4: if (view.getScaleY() != want[4]) { view.setScaleY(want[4]); mWriteCount++; } break;
                case 5: if (view.getPivotX() != want[5]) { view.setPivotX(want[5]); mWriteCount++; } break;
                default: if (view.getPivotY() != want[6]) { view.setPivotY(want[6]); mWriteCount++; } break;
            }
        }
    }

    /**
     * The rows standing on the plank: inside the container they inherit its tilt and stand
     * neutral; beside the glass host (the keyboard up) each is handed the slab verbatim with the
     * plane's pivot mapped into its own coordinates. Written only when the slab or the target
     * changed, so a surface that lifts these rows meanwhile (the app drawer) keeps them.
     */
    private void writeContent() {
        boolean changed = mWrittenContentTarget != mPlankTarget
            || !mWrittenContentSlab.sameAs(mSlab);
        for (int i = 0; i < PLANK_CONTENT_IDS.length; i++) {
            View layer = mViews.view(PLANK_CONTENT_IDS[i]);
            if (layer != mWrittenContent[i]) {
                mWrittenContent[i] = layer;
                if (layer != null) {
                    layer.setCameraDistance(layer.getResources().getDisplayMetrics().density * 2600f);
                    changed = true;
                }
            }
        }
        if (!changed) return;
        mWrittenContentTarget = mPlankTarget;
        mWrittenContentSlab.set(mSlab);
        View host = plankView(PLANK_HOST);
        for (View layer : mWrittenContent) {
            if (layer == null) continue;
            boolean detached = mPlankTarget == PLANK_HOST && host != null && !isInside(layer, host);
            if (!detached || mSlab.isNeutral()) {
                if (DockPlankController.isNeutral(layer)) continue;
                DockPlankController.resetLayer(layer);
                mWriteCount++;
                continue;
            }
            if (layer.getVisibility() != View.VISIBLE || layer.getWidth() <= 0
                || layer.getHeight() <= 0) continue;
            DockPlankController.layoutOffsetFromPlank(layer, host, mOffset);
            mSlab.applyTo(layer, mOffset[0], mOffset[1]);
            mWriteCount++;
        }
    }

    private static boolean isInside(@NonNull View view, @NonNull View ancestor) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == ancestor) return true;
        }
        return false;
    }

    private void invalidateIfShown(int id) {
        View view = mViews.view(id);
        if (view != null && view.getVisibility() == View.VISIBLE) view.invalidate();
    }

    private void invalidateIfDressed(int id) {
        View view = mViews.view(id);
        if (view != null && view.getBackground() != null) view.invalidate();
    }
}
