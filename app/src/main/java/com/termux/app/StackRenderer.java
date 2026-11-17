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
import com.termux.app.place.Element;
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
        /** Whether bands stand under the keyboard. */
        boolean underKeyboardBandsShown();
        /** Whether the in-app keyboard is up: the plank then tilts the glass host alone. */
        boolean keyboardShown();
        /**
         * The in-app keyboard's laid-out height while it stands laid out but down, else 0: the
         * stack stands this much lower at rest ({@link StackScene#keyboardTranslationPx}).
         */
        default int keyboardDownLaidOutPx() {
            return 0;
        }
        /** Whether Home's side stands in front (a Home page is committed), else the Workspace's. */
        default boolean frontIsHome() {
            return false;
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
    private float mSlideX;
    /**
     * The crossing's two ends by side and its progress between them, set by the wall's frames and
     * cleared at the landing; with none the scene is the front side at rest. The ends are made
     * once: a frame allocates nothing.
     */
    private final StackEnd mEndHome = new StackEnd(PageRef.HOME);
    private final StackEnd mEndWorkspace = new StackEnd(PageRef.TERMINAL);
    private boolean mHasEnds;
    private boolean mFromHome;
    private boolean mToHome;
    private float mEndsProgress;
    /** What was last written to each lane host's translationX, and to which view. */
    private final View[] mWrittenLaneView = new View[LANE_HOST_IDS.length];
    private final float[] mWrittenLaneTx = new float[LANE_HOST_IDS.length];
    private final DockPlankController.Slab mSlab = new DockPlankController.Slab();
    private int mPlankTarget = PLANK_NONE;

    // What was last written, per view slot (StackScene.V_*), and to which view object.
    private final View[] mWrittenView = new View[StackScene.V_COUNT];
    private final float[] mWrittenTy = new float[StackScene.V_COUNT];
    private final boolean[] mWrittenClipped = new boolean[StackScene.V_COUNT];
    private final boolean[] mClipKnown = new boolean[StackScene.V_COUNT];
    private final Rect[] mWrittenClip = new Rect[StackScene.V_COUNT];
    /** The container's and the host's plank parts as last written (tx, rotations, scales, pivots). */
    private final float[][] mWrittenPlank = new float[StackScene.V_COUNT][7];
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
        for (int v = 0; v < StackScene.V_COUNT; v++) mWrittenClip[v] = new Rect();
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
        float lift = Math.max(0f, imeLiftPx);
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
    public void setSink(float rowsPx, float rowsCutPx, float sheetPx, float sheetCutPx,
                        float heldPx, boolean holdsAz) {
        StackScene.Motion m = mMotion;
        if (mApplied && m.rowsSinkPx == rowsPx && m.rowsCutPx == rowsCutPx
            && m.sheetSinkPx == sheetPx && m.sheetCutPx == sheetCutPx && m.heldSinkPx == heldPx
            && m.holdsAz == holdsAz) return;
        m.layerSink(rowsPx, rowsCutPx, sheetPx, sheetCutPx, heldPx, holdsAz);
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
     * Each side's room, the window's bottom up to where its page's cells end
     * ({@link com.termux.app.place.PageRoom#wallInsetPx}): what the flat body surface is cut to at
     * that side's rest ({@link StackScene#bodyCutPx}). Written at rest, once the rests are known;
     * a crossing blends the two and its landing finds the arriving one in place.
     */
    public void setRooms(int homePx, int workspacePx) {
        if (mApplied && mEndHome.roomPx == homePx && mEndWorkspace.roomPx == workspacePx) return;
        mEndHome.roomPx = homePx;
        mEndWorkspace.roomPx = workspacePx;
        requestApply();
    }

    /** The crossing is over without a landing's own call: the lanes stand at the front rest. */
    public void clearEnds() {
        if (!mHasEnds) return;
        mHasEnds = false;
        requestApply();
    }

    /** The stack drawn {@code px} across from its rest, riding with the arriving page. */
    public void setSlideX(float px) {
        if (mApplied && mSlideX == px) return;
        mSlideX = px;
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

    /** The frame last written: the Peek reflection reads its rows' offset from the host here. */
    public float rowsOverHostPx() {
        return mFrame.ty[StackScene.V_ROWS] - mFrame.ty[StackScene.V_HOST];
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
        readGeometry(container);
        StackScene.withKeyboardRest(mMotion,
            StackScene.keyboardTranslationPx(0f, mViews.keyboardDownLaidOutPx()), mEffective);
        if (mHasEnds) {
            StackScene.at(mFromHome ? mEndHome : mEndWorkspace, mToHome ? mEndHome : mEndWorkspace,
                mEndsProgress, mEffective, mGeometry, mFrame);
        } else {
            StackEnd front = mViews.frontIsHome() ? mEndHome : mEndWorkspace;
            StackScene.at(front, front, 0f, mEffective, mGeometry, mFrame);
        }
        mPlankTarget = plankTarget();

        boolean containerMoved = writeTy(StackScene.V_CONTAINER, container,
            mFrame.ty[StackScene.V_CONTAINER] + plankTy(PLANK_CONTAINER));
        writePlank(StackScene.V_CONTAINER, container, PLANK_CONTAINER, mSlideX);
        containerMoved |= writeClip(StackScene.V_CONTAINER, container);
        if (containerMoved) {
            // The dock's and the keyboard's glass sample the wallpaper through this translation;
            // a transform alone redraws nothing, so they are re-aimed here.
            invalidateIfShown(R.id.accessory_blur_backdrop);
            invalidateIfDressed(R.id.inapp_keyboard_view_host);
            invalidateIfDressed(R.id.accessory_under_keyboard_stack);
        }
        boolean layersMoved = false;
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            int v = StackScene.V_LAYER0 + i;
            View layer = mViews.view(LAYER_IDS[i]);
            if (layer == null) continue;
            boolean host = i == StackGeometry.HOST;
            layersMoved |= writeTy(v, layer, mFrame.ty[v] + (host ? plankTy(PLANK_HOST) : 0f));
            if (host) writePlank(v, layer, PLANK_HOST, 0f);
            layersMoved |= writeClip(v, layer);
        }
        // The dock's glass samples the wallpaper where it is drawn; a transform redraws nothing.
        if (layersMoved) invalidateIfShown(R.id.accessory_blur_backdrop);
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
            }
        }
        View body = mViews.view(R.id.terminal_background);
        if (body != null) {
            watchBody(body);
            writeClip(StackScene.V_BODY, body);
        }
        writeLanes();
        writeContent();
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
     * the container itself already rides ({@link #setSlideX}), so the rows stand where the scene
     * puts them. A row in no lane stands at 0. At rest the front side's lane writes nothing.
     */
    private void writeLanes() {
        View rowStack = mViews.view(R.id.accessory_row_stack);
        if (!(rowStack instanceof EdgeStackView)) return;
        EdgeStackView stack = (EdgeStackView) rowStack;
        for (int i = 0; i < LANE_HOST_IDS.length; i++) {
            View host = mViews.view(LANE_HOST_IDS[i]);
            if (host == null) continue;
            float want = 0f;
            if (host.getParent() == stack) {
                int lane = stack.getLane(host);
                if (lane != StackLanes.STACK) {
                    want = mFrame.sideTx[(lane == StackLanes.HOME ? Side.HOME : Side.WORKSPACE)
                        .ordinal()] - mSlideX;
                }
            }
            if (host != mWrittenLaneView[i]) {
                mWrittenLaneView[i] = host;
                mWrittenLaneTx[i] = Float.NaN;
            }
            if (mWrittenLaneTx[i] == want) continue;
            mWrittenLaneTx[i] = want;
            if (host.getTranslationX() == want) continue;
            host.setTranslationX(want);
            mWriteCount++;
        }
    }

    private int plankTarget() {
        return mViews.keyboardShown() ? PLANK_HOST : PLANK_CONTAINER;
    }

    @Nullable
    private View plankView(int target) {
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
        g.stackPaddingBottomPx = container.getPaddingBottom();
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
        g.keyboardTopPx = keyboard == null ? 0
            : keyboard.getTop() + (column != null ? column.getTop() : 0);
        g.underShown = under != null && mViews.underKeyboardBandsShown();
        g.belowKeyboardInColumnPx = g.keyboardInColumn ? belowKeyboardInColumnPx(column, under) : 0;
        g.underTopPx = under == null ? 0 : under.getTop() + (column != null ? column.getTop() : 0);
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
            g.bodyHeightPx = Math.max(0, parent.getHeight() - parent.getPaddingTop()
                - parent.getPaddingBottom() - margins.topMargin - margins.bottomMargin);
            g.bodyLaidOutRoomPx = margins.bottomMargin;
        }
    }

    /** The body surface whose layout changes ask for a frame: its cut is read off its size. */
    @Nullable private View mWatchedBody;
    private final View.OnLayoutChangeListener mBodyLayout =
        (v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) requestApply();
        };

    private void watchBody(@NonNull View body) {
        if (mWatchedBody == body) return;
        if (mWatchedBody != null) mWatchedBody.removeOnLayoutChangeListener(mBodyLayout);
        mWatchedBody = body;
        body.addOnLayoutChangeListener(mBodyLayout);
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
