package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Trace;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import com.termux.app.chrome.scene.LiveSceneFrame;
import com.termux.app.chrome.scene.SceneLayerView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;

/**
 * Live blur's recorder: for each glass surface it is given, everything the window draws
 * <em>beneath</em> that surface, in Android's draw order, and never the surface itself or anything
 * above it. So no glass needs hiding while it is recorded, and glass on glass falls out: what is
 * beneath one glass may include another glass.
 *
 * <p>{@link #recordBelow} walks from the surface up to the root it was made with
 * ({@code terminal_root_container}). At each ancestor it draws the children that draw before the
 * one on the path, with their transforms, as that ancestor's own draw would; the launcher-drawn
 * wallpaper ({@link WallpaperBackdropView}) is simply the root's bottom child. Drawing a child into
 * a {@link RecordingCanvas} draws its cached display list: nothing is repainted. An ancestor can
 * only do that for its children from inside (it is {@code ViewGroup.drawChild}, protected), so the
 * containers on a live surface's path implement {@link Container}; an ancestor that does not
 * contributes nothing beneath.</p>
 *
 * <p><b>Ground.</b> Under the root's children every recording first draws the window's ground: the
 * backgrounds of the root and of every view above it up to the decor, whose background is the
 * window background (the opaque mode's surface base; transparent in wallpaper mode, where the
 * ground is the wallpaper and ground strip views inside the root). Without it a recording is only
 * as opaque as the views in it, and the live screen shows through the glass.</p>
 *
 * <p><b>When.</b> Surfaces are re-recorded in a pre-draw of a frame that is already being drawn,
 * and only when what is beneath them moved: the views drawn beneath (a sibling shown, hidden or
 * raised) or where the surface stands among them. Anything that merely redraws beneath (a page
 * sliding inside its parent, a terminal printing) reaches the picture through the node it
 * references, without a new recording. Recording never invalidates a view or schedules a frame:
 * at rest nothing draws, so nothing records and nothing blurs.</p>
 *
 * <p><b>The page layer.</b> While the wall of pages moves, and at rest under a live scene, a
 * surface whose beneath holds the wall ({@link #setWall}) is drawn in two layers
 * ({@link BackdropLayers}): the record node draws the ground and the chrome as always, and
 * in the wall's place a quarter-scale page layer. Under a scene that layer is the pages as they are
 * on a layer of their own, so a scene frame no longer re-blurs them. During a swipe it is one
 * picture of the pages, rendered offscreen as the swipe starts ({@link PageLayerCapture}) and only
 * moved by the pages' travel each frame, so the swipe re-blurs the ground, the chrome and a
 * picture, not the pages. Everything that does not move with the pages (the wallpaper's parallax,
 * the scene, the bars, the dock, the keyboard, the frame line) stays in the record node and is
 * drawn where it stands. Until the picture lands, and whenever the pages stop moving together, the
 * surface keeps its one-layer recording.</p>
 *
 * <p><b>Memory.</b> At most {@link #MAX_LIVE_SURFACES} pictures, each one blur layer and at most one
 * page layer, together at most {@link #MAX_LAYER_BYTES}: {@link #BUDGET_BYTES} in all. Pictures
 * are released when Live goes off, when their surface hides ({@link #detach}) and under memory
 * pressure ({@link #releaseAll}); a frozen page layer is let go as the wall settles.</p>
 */
public final class LiveBackdrop implements ViewTreeObserver.OnPreDrawListener {

    /**
     * The dock, the floats and the widgets on the visible page, in that order of claim
     * ({@link #RANK_CHROME}).
     */
    public static final int MAX_LIVE_SURFACES = 8;

    /** The panel the budget is sized for: 1440 x 3200, the largest phone screen in common use. */
    static final int BUDGET_SCREEN_WIDTH = 1440;
    static final int BUDGET_SCREEN_HEIGHT = 3200;

    /** One picture's blur layer at most: a whole budget screen at a quarter scale, 4 bytes a pixel. */
    public static final long MAX_LAYER_BYTES = layerBytes(BUDGET_SCREEN_WIDTH, BUDGET_SCREEN_HEIGHT);

    /** Every live layer together: about 8.8 MiB. */
    public static final long BUDGET_BYTES = MAX_LIVE_SURFACES * MAX_LAYER_BYTES;

    /**
     * A container on a live surface's path: draws one of its own children, now, into another
     * canvas, exactly as its own draw does (its cached display list, at its transform).
     * Implement it as {@code drawChild(canvas, child, getDrawingTime())}.
     */
    public interface Container {
        void drawChildForBackdrop(@NonNull Canvas canvas, @NonNull View child);
    }

    /**
     * The wall of pages: a container whose children are the pages a swipe moves together, which
     * a live surface over them holds in a page layer of their own ({@link BackdropLayers}).
     */
    public interface Wall extends Container {
        /** Whether the wall is anywhere but at rest: a finger on it, or a slide under way. */
        boolean isMovingForBackdrop();

        /**
         * Draws what the wall draws over its pages (the grabbers, a border's glow) into
         * {@code canvas}, in its children's coordinates, as its own draw does now.
         */
        void drawDecorForBackdrop(@NonNull Canvas canvas);
    }

    /**
     * A view drawn beneath the wall that paints each page's own ground where that page stands
     * (the ground strip), so its drawing travels with the pages: a surface riding a page carries
     * it in its local layer instead of falling back to the one picture
     * ({@link BackdropLayers#holder}).
     */
    public interface WallGround {
        /**
         * Whether the pages' grounds differ, so a seam travels across a surface that stays
         * (the dock, a bar, a float) while the pages move.
         */
        boolean groundsDiffer();
    }

    /** Pictures of the page layer one swipe may start before it goes on without one. */
    static final int MAX_CAPTURES_PER_SWIPE = 3;

    @NonNull private final ViewGroup mRoot;
    @NonNull private final ArrayList<LivePicture> mPictures = new ArrayList<>(MAX_LIVE_SURFACES);
    @Nullable private ViewTreeObserver mObserver;

    // Scratch for one walk; the walk runs on the UI thread only.
    @NonNull private final ArrayList<View> mPath = new ArrayList<>();
    @NonNull private final ArrayList<View> mBeneath = new ArrayList<>();
    @NonNull private final Matrix mMatrix = new Matrix();
    @NonNull private final Matrix mInverse = new Matrix();
    @NonNull private int[] mPreorder = new int[16];
    @NonNull private float[] mZ = new float[16];
    @NonNull private boolean[] mDrawn = new boolean[16];
    @NonNull private int[] mOut = new int[16];
    @NonNull private int[] mOrder = new int[16];
    @NonNull private final int[] mRanks = new int[MAX_LIVE_SURFACES];

    // Scratch for the page layer; UI thread only.
    /** The wall, when it is a view of this root ({@link #setWall}). */
    @Nullable private ViewGroup mWall;
    /** Where the wall stands on the surface's path, or -1 when it is beneath it. */
    private int mWallPathIndex = -1;
    /** The view beneath the surface that holds the wall, down to the wall; empty with the wall on the path. */
    @NonNull private final ArrayList<View> mChain = new ArrayList<>();
    /** For each container in {@link #mChain} but the wall: its children before, null, after, null. */
    @NonNull private final ArrayList<View> mExpand = new ArrayList<>();
    /** The pages the page layer holds, bottom first. */
    @NonNull private final ArrayList<View> mPages = new ArrayList<>();
    @NonNull private float[] mPoses = new float[BackdropLayers.POSE_STRIDE * 4];
    private int mPoseCount;
    /** The wall's children's coordinates to the root's, and back. */
    @NonNull private final Matrix mWallMatrix = new Matrix();
    @NonNull private final Matrix mWallInverse = new Matrix();
    @NonNull private final Matrix mPageMatrix = new Matrix();
    @NonNull private final RectF mNeeded = new RectF();
    @NonNull private final RectF mReach = new RectF();
    @NonNull private final RectF mUnion = new RectF();
    @NonNull private final RectF mBounds = new RectF();
    @NonNull private final Rect mPageRect = new Rect();
    /** {@link #splittable} takes fading pages: off the wall, while it moves (a frozen layer). */
    private boolean mFadingPages;
    @NonNull private final Rect mSlot = new Rect();
    @NonNull private final RectF mSlotBounds = new RectF();
    @NonNull private int[] mSaves = new int[8];
    /** The page layer was recorded or changed this frame: the record node is recorded around it. */
    private boolean mPageChanged;

    public LiveBackdrop(@NonNull ViewGroup root) {
        mRoot = root;
    }

    /**
     * The wall of pages inside this root, whose pages a live surface over them holds in a page
     * layer ({@link BackdropLayers}); null for none, and then every surface keeps one layer.
     */
    public void setWall(@Nullable Wall wall) {
        mWall = wall instanceof ViewGroup ? (ViewGroup) wall : null;
    }

    /**
     * Where a surface's ground layer comes from while the wall moves ({@link BackdropLayers}): the
     * stored pre-blurred frame the Wallpaper kind samples, and the parallax offset it is sampled at.
     */
    public interface GroundSource {
        /**
         * The stored frame for a blur of {@code blurRadiusPx}, already resident, with the screen
         * rect it covers in {@code outScreenRect}; null when there is none yet (the surface then
         * keeps its one picture). Never starts a capture.
         */
        @Nullable
        android.graphics.Bitmap storedFrame(float blurRadiusPx, @NonNull Rect outScreenRect);

        /** {@link WallpaperParallax#offsetPx()} now. */
        float parallaxOffsetPx();
    }

    @Nullable private GroundSource mGroundSource;
    @NonNull private BackdropLayers.SeeThrough mSeeThrough = BackdropLayers.SeeThrough.ALL;

    /** The stored frames' source for the ground layer; null keeps every swipe on the one picture. */
    public void setGroundSource(@Nullable GroundSource source) {
        mGroundSource = source;
    }

    /**
     * "See through" ({@link BackdropLayers#drawsStored}): with
     * {@link BackdropLayers.SeeThrough#ONE} a glass with another live glass above it draws the
     * stored frame instead of recording, at rest too. A change records every
     * picture again on its next frame, which it asks for.
     */
    public void setSeeThrough(@NonNull BackdropLayers.SeeThrough seeThrough) {
        if (seeThrough == mSeeThrough) return;
        mSeeThrough = seeThrough;
        mPlanStamp++;
        for (int i = 0; i < mPictures.size(); i++) {
            LivePicture picture = mPictures.get(i);
            picture.mRecordedBeneath.clear();
            picture.mRecordedLocal.clear();
            picture.mSurface.invalidate();
        }
    }

    // Scratch for the backdrop layers; UI thread only.
    @Nullable private android.graphics.Bitmap mGroundBitmap;
    @NonNull private final Rect mFrameRect = new Rect();
    @NonNull private final Rect mGroundRect = new Rect();
    @NonNull private final Rect mSceneRegion = new Rect();
    @NonNull private final Rect mTmpRect = new Rect();
    @NonNull private final RectF mNeededRoot = new RectF();
    @NonNull private final RectF mNeededWall = new RectF();
    @NonNull private final Matrix mAim = new Matrix();
    @NonNull private final Matrix mPagesAim = new Matrix();
    /** The views of the local layer: beneath, neither the ground nor the pages, kept by See through. */
    @NonNull private final ArrayList<View> mLocal = new ArrayList<>();

    private float parallaxPx() {
        return mGroundSource == null ? 0f : mGroundSource.parallaxOffsetPx();
    }

    /** Bytes of a quarter-scale RGBA layer over a {@code width} x {@code height} recording. */
    public static long layerBytes(int width, int height) {
        long w = Math.max(1, (width + LivePicture.SCALE - 1) / LivePicture.SCALE);
        long h = Math.max(1, (height + LivePicture.SCALE - 1) / LivePicture.SCALE);
        return w * h * 4L;
    }

    /**
     * The live picture for {@code surface}, made if it has none; null when {@link #MAX_LIVE_SURFACES}
     * are already live (the surface keeps its stored frame).
     */
    @Nullable
    public LivePicture attach(@NonNull View surface, float blurRadiusPx) {
        return attach(surface, blurRadiusPx, false);
    }

    /**
     * {@link #attach(View, float)} for a panel ({@link PanelBackdrop}): {@code blurRadiusPx} is the
     * panel's {@code RealtimeBlurView} radius when {@code panelRadius}, calibrated by
     * {@link LivePicture#panelQuarterRadius}, so the panel keeps the strength it had.
     */
    @Nullable
    public LivePicture attach(@NonNull View surface, float blurRadiusPx, boolean panelRadius) {
        return attach(surface, blurRadiusPx, panelRadius, RANK_CHROME);
    }

    /**
     * {@link #attach(View, float)} at a place in the budget's order ({@link #RANK_CHROME},
     * {@link #RANK_FLOAT}, {@link #RANK_WIDGET}). When every picture is taken, a surface of a
     * better rank takes the place of the newest picture of the worst rank below it, which is
     * released and falls back to its stored frame on its next draw; a surface no better than any
     * live one gets null.
     */
    @Nullable
    public LivePicture attach(@NonNull View surface, float blurRadiusPx, int rank) {
        return attach(surface, blurRadiusPx, false, rank);
    }

    @Nullable
    private LivePicture attach(@NonNull View surface, float blurRadiusPx, boolean panelRadius,
                               int rank) {
        LivePicture picture = find(surface);
        if (picture == null) {
            if (mPictures.size() >= MAX_LIVE_SURFACES) {
                int count = mPictures.size();
                for (int i = 0; i < count; i++) mRanks[i] = mPictures.get(i).mRank;
                int evict = evictIndex(mRanks, count, rank);
                if (evict < 0) return null;
                LivePicture evicted = mPictures.remove(evict);
                evicted.release();
                // Its view draws the stored frame again on its next draw; ask for that draw.
                evicted.mSurface.invalidate();
            }
            picture = new LivePicture(surface, blurRadiusPx, panelRadius);
            picture.setStoredParity(recordedDim(surface), mBakeVibrancy, mBakeFrameTint);
            mPictures.add(picture);
            mPlanStamp++;
            listen(true);
        } else {
            picture.setBlurRadiusPx(blurRadiusPx);
        }
        picture.mRank = rank;
        return picture;
    }

    /** The Vibrancy and frame tint the stored frames are baked at ({@link #setStoredBake}). */
    private int mBakeVibrancy = 100;
    private int mBakeFrameTint = GlassFrameTint.NONE;

    /**
     * The Vibrancy and frame tint the stored frames are baked at now
     * ({@code WallpaperBlurCache}), which every picture but a panel's then takes after its blur,
     * so the glass looks the same on either route ({@link LiveStoredParity}).
     */
    public void setStoredBake(int vibrancyPercent, int frameTint) {
        if (vibrancyPercent == mBakeVibrancy && frameTint == mBakeFrameTint) return;
        mBakeVibrancy = vibrancyPercent;
        mBakeFrameTint = frameTint;
        for (int i = 0; i < mPictures.size(); i++) {
            LivePicture picture = mPictures.get(i);
            picture.setStoredParity(recordedDim(picture.mSurface), vibrancyPercent, frameTint);
        }
    }

    /**
     * The wall's dim a recording beneath {@code surface} carries: the launcher-drawn wallpaper's
     * ({@link WallpaperBackdropView}), when it is among the views drawn beneath. Transparent when
     * it is not. Collects {@code surface}'s beneath to find it.
     */
    private int recordedDim(@NonNull View surface) {
        if (!collect(surface)) return 0;
        return dimBeneath();
    }

    /** {@link #recordedDim} over the views {@link #collect} just found. Allocates nothing. */
    private int dimBeneath() {
        for (int i = 0; i < mBeneath.size(); i++) {
            View below = mBeneath.get(i);
            // A scene on: the photo is left out of the recording, and so is its dim.
            if (below instanceof WallpaperBackdropView) {
                return LiveSceneFrame.current() != null ? 0 : ((WallpaperBackdropView) below).dimColor();
            }
        }
        return 0;
    }

    /**
     * The budget's order, best first. Chrome is the dock, the nav strip and the panels (drawer,
     * palette, sheet, menus): one each, always in front of whatever else is live. Then the
     * floating windows, which stand over the page. Then the built-in widgets of the page on
     * screen, which fill what is left; past that they keep the stored frame.
     */
    public static final int RANK_CHROME = 0;
    public static final int RANK_FLOAT = 1;
    public static final int RANK_WIDGET = 2;

    /**
     * Which of {@code count} live pictures, at {@code ranks}, gives its place to a surface of
     * {@code rank}: the newest of the worst rank, when that rank is worse than {@code rank};
     * -1 when none is. Pure.
     */
    @VisibleForTesting
    static int evictIndex(@NonNull int[] ranks, int count, int rank) {
        int worst = -1;
        for (int i = 0; i < count; i++) {
            if (ranks[i] > rank && (worst < 0 || ranks[i] >= ranks[worst])) worst = i;
        }
        return worst;
    }

    /** Drops {@code surface}'s picture and its layer. */
    public void detach(@NonNull View surface) {
        LivePicture picture = find(surface);
        if (picture == null) return;
        picture.release();
        mPictures.remove(picture);
        mPlanStamp++;
        if (mPictures.isEmpty()) listen(false);
    }

    /** Drops every picture and every layer: Live went off, or memory is short. */
    public void releaseAll() {
        for (int i = 0; i < mPictures.size(); i++) mPictures.get(i).release();
        mPictures.clear();
        mPlanStamp++;
        listen(false);
    }

    /** How many surfaces are live now. */
    public int size() {
        return mPictures.size();
    }

    /** Whether {@code drawable} is a live picture this recorder hands out. */
    public boolean shows(@Nullable Drawable drawable) {
        if (drawable == null) return false;
        for (int i = 0; i < mPictures.size(); i++) {
            if (mPictures.get(i).owns(drawable)) return true;
        }
        return false;
    }

    @Nullable
    private LivePicture find(@NonNull View surface) {
        for (int i = 0; i < mPictures.size(); i++) {
            if (mPictures.get(i).mSurface == surface) return mPictures.get(i);
        }
        return null;
    }

    private void listen(boolean on) {
        if (on) {
            if (mObserver != null && mObserver.isAlive()) return;
            mObserver = mRoot.getViewTreeObserver();
            mObserver.addOnPreDrawListener(this);
            mObserver.addOnGlobalLayoutListener(mLayoutListener);
            // Whatever moved while nobody listened: plan whole.
            mPlanStamp++;
        } else {
            if (mObserver != null && mObserver.isAlive()) {
                mObserver.removeOnPreDrawListener(this);
                mObserver.removeOnGlobalLayoutListener(mLayoutListener);
            }
            // A dead observer was merged into the live one when the root attached.
            ViewTreeObserver current = mRoot.getViewTreeObserver();
            if (current != mObserver && current.isAlive()) {
                current.removeOnPreDrawListener(this);
                current.removeOnGlobalLayoutListener(mLayoutListener);
            }
            mObserver = null;
        }
    }

    /**
     * Runs inside a frame already being drawn: re-records each shown surface whose beneath moved.
     * Invalidates nothing, but for a picture's first whole recording, whose holder is asked to
     * redraw in this same frame to swap its stored frame for it ({@link LivePicture#recorded}).
     */
    @Override
    public boolean onPreDraw() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        ViewGroup wall = mWall;
        boolean moving = wall != null && ((Wall) wall).isMovingForBackdrop();
        if (moving != mPlanMoving) {
            // A motion starts with a whole plan, and the landed frame plans whole again.
            mPlanMoving = moving;
            mPlanStamp++;
        }
        markGlassAbove(moving);
        try {
            preDrawPictures(moving);
        } finally {
            mPlanning = null;
        }
        if (mPictures.isEmpty()) listen(false);
        return true;
    }

    private void preDrawPictures(boolean moving) {
        for (int i = 0; i < mPictures.size(); i++) {
            LivePicture picture = mPictures.get(i);
            View surface = picture.mSurface;
            mPlanning = null;
            if (!picture.held()) {
                // Its view put up another picture: nothing draws this one any more.
                picture.release();
                mPictures.remove(i--);
                continue;
            }
            if (!surface.isShown() || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
                // Hidden: no layer while it is; the next shown frame records it again.
                picture.trim();
                continue;
            }
            if (!collectPlanned(picture.mMotionPlan, surface, moving)) continue;
            // The dim the recording carries is taken back out after the blur; a dim moved is a
            // new effect on the same recording, never a re-record.
            picture.setRecordedDim(dimBeneath());
            int margin = picture.marginPx();
            int reach = picture.reachTopPx();
            if (BackdropLayers.drawsStored(mSeeThrough, picture.mGlassAbove)
                && drawStored(picture, surface, margin, reach)) {
                // Back to recording, it records whole again.
                picture.mRecordHoldsLocal = false;
                picture.mRecordedBeneath.clear();
                continue;
            }
            BackdropLayers.Mode mode = pageLayerMode(picture, surface, margin, reach);
            int plan = layersPlan(picture, surface, margin, reach, mode);
            if ((plan & BackdropLayers.LAYERS) != 0
                && drawLayers(picture, surface, margin, reach, plan)) {
                continue;
            }
            if (picture.layered() || picture.mRecordHoldsLocal) {
                // Back to the one picture: recorded again below, in this same frame.
                Trace.beginSection("Glass.layers.toOne");
                picture.showOne();
                picture.mRecordHoldsLocal = false;
                picture.mRecordedBeneath.clear();
                Trace.endSection();
            }
            // A frozen pages layer is blurred already: it is drawn only beside the ground, so the
            // one picture records the pages as they are.
            boolean split = mode == BackdropLayers.Mode.REFERENCE;
            if (picture.mRecordedMode == mode
                && picture.mRecordedReach == reach
                && picture.mRecordedWidth == surface.getWidth()
                && picture.mRecordedHeight == surface.getHeight()
                && picture.mRecordedMargin == margin
                && picture.mRecordedMatrix.equals(mMatrix)
                && sameViews(picture.mRecordedBeneath, mBeneath)
                && picture.mRecordedSceneGen == sceneGeneration()
                && (!split || (!mPageChanged && sameViews(picture.mRecordedExpand, mExpand)
                    && picture.mRecordedWallMatrix.equals(mWallMatrix)))) {
                continue;
            }
            int width = surface.getWidth() + 2 * margin;
            int height = surface.getHeight() + 2 * margin + reach;
            RenderNode node = picture.recordNode(width, height);
            if (!(split ? recordSplit(node, width, height, margin, reach, picture)
                : record(node, width, height, margin, reach))) continue;
            picture.mRecordedMode = mode;
            picture.mRecordedBeneath.clear();
            picture.mRecordedBeneath.addAll(mBeneath);
            picture.mRecordedExpand.clear();
            if (split) picture.mRecordedExpand.addAll(mExpand);
            picture.mRecordedWallMatrix.set(mWallMatrix);
            picture.mRecordedMatrix.set(mMatrix);
            picture.mRecordedWidth = surface.getWidth();
            picture.mRecordedHeight = surface.getHeight();
            picture.mRecordedMargin = margin;
            picture.mRecordedReach = reach;
            picture.mRecordedSceneGen = sceneGeneration();
            picture.mRecordedParallax = parallaxPx();
            // The first whole recording: its holder swaps the stored frame for it in this frame.
            picture.recorded();
        }
    }

    // ---- The motion plan -------------------------------------------------------------------

    /**
     * What one picture's walks of the tree found while the wall moves, kept for the rest of the
     * motion: the views on its path and beneath it ({@link #collect}), the views around the wall
     * and its pages ({@link #splittable}), and which views beneath the wall cross its band
     * ({@link #layersPlan}). A swipe moves the pages by their transforms and changes none of
     * these, so a moving frame reads the kept lists and only works out where things stand now:
     * the surface's matrix, the pages' poses, the aims. Only the planning is kept: every
     * recording still draws the views' live display lists, so the terminal and the clock keep
     * drawing under the glass.
     *
     * <p>Kept while {@link #mPlanStamp} holds, which moves on a layout pass, on the wall starting
     * or stopping, on a picture coming or going and on a See through change; a view beneath
     * that stops drawing, or a path view that changes parent, also plans whole again. A view
     * shown again without a layout (INVISIBLE to VISIBLE) or a change of elevation mid-swipe is
     * picked up at the landing frame, which always plans whole.</p>
     */
    static final class MotionPlan {
        static final int NONE = Integer.MIN_VALUE;

        int stamp = NONE;
        @NonNull final ArrayList<View> path = new ArrayList<>();
        @NonNull final ArrayList<View> beneath = new ArrayList<>();

        int chainStamp = NONE;
        @NonNull final ArrayList<View> chain = new ArrayList<>();
        @NonNull final ArrayList<View> expand = new ArrayList<>();
        @NonNull final ArrayList<View> wallChildren = new ArrayList<>();

        int bandStamp = NONE;
        int bandTop;
        int bandBottom;
        @NonNull boolean[] bandKnown = new boolean[16];
        @NonNull boolean[] band = new boolean[16];

        /** Nothing kept: the next frame plans whole, and no view is held. */
        void forget() {
            stamp = NONE;
            chainStamp = NONE;
            bandStamp = NONE;
            path.clear();
            beneath.clear();
            chain.clear();
            expand.clear();
            wallChildren.clear();
        }
    }

    /**
     * Whether a plan made at {@code planStamp} still holds at {@code stampNow}: only while the
     * wall moves, and only for the stamp it was made at. Pure.
     */
    @VisibleForTesting
    static boolean planHolds(int planStamp, int stampNow, boolean moving) {
        return moving && planStamp != MotionPlan.NONE && planStamp == stampNow;
    }

    /** Moves on anything that may change what a picture's walks find; see {@link MotionPlan}. */
    private int mPlanStamp;
    /** Whether the last pre-draw found the wall moving. */
    private boolean mPlanMoving;
    /** The plan of the picture being drawn while the wall moves; null otherwise. */
    @Nullable private MotionPlan mPlanning;
    /** The stamp {@link #markGlassAbove} last marked at, and over how many pictures. */
    private int mGlassAboveStamp = MotionPlan.NONE;
    private int mGlassAboveCount = -1;
    /** A layout pass may have moved, shown, hidden or re-ordered any view: plan whole again. */
    @NonNull private final ViewTreeObserver.OnGlobalLayoutListener mLayoutListener =
        () -> mPlanStamp++;
    /** The wall's children, as {@link #collectLevel} orders them; a pages layer's candidates. */
    @NonNull private final ArrayList<View> mWallChildren = new ArrayList<>();

    /**
     * {@link #collect} for {@code plan}'s picture: while the wall moves and the plan holds, the
     * kept path and views beneath with the surface's matrix worked out afresh; otherwise a whole
     * walk, kept for the next frame while the wall moves. Sets {@link #mPlanning}.
     */
    @VisibleForTesting
    boolean collectPlanned(@NonNull MotionPlan plan, @NonNull View surface, boolean moving) {
        mPlanning = moving ? plan : null;
        if (planHolds(plan.stamp, mPlanStamp, moving) && plan.path.size() > 0
                && plan.path.get(0) == surface && restorePlan(plan)) {
            return true;
        }
        if (!moving) {
            if (plan.stamp != MotionPlan.NONE || !plan.path.isEmpty()) plan.forget();
            return collect(surface);
        }
        Trace.beginSection("LiveBackdrop.plan");
        try {
            plan.forget();
            if (!collect(surface)) return false;
            copyViews(mPath, plan.path);
            copyViews(mBeneath, plan.beneath);
            plan.stamp = mPlanStamp;
            return true;
        } finally {
            Trace.endSection();
        }
    }

    /**
     * {@link #mPath} and {@link #mBeneath} from {@code plan}, and {@link #mMatrix} worked out
     * along the path now. False when the kept lists no longer describe the tree: a path view
     * moved to another parent, or a view beneath stopped drawing.
     */
    private boolean restorePlan(@NonNull MotionPlan plan) {
        ArrayList<View> path = plan.path;
        int last = path.size() - 1;
        if (path.get(last) != mRoot) return false;
        for (int i = 0; i < last; i++) {
            if (path.get(i).getParent() != path.get(i + 1)) return false;
        }
        ArrayList<View> beneath = plan.beneath;
        for (int i = 0; i < beneath.size(); i++) {
            View below = beneath.get(i);
            if (below != null && below.getVisibility() != View.VISIBLE
                && below.getAnimation() == null) {
                return false;
            }
        }
        copyViews(path, mPath);
        copyViews(beneath, mBeneath);
        pathMatrix();
        return true;
    }

    /** {@code from} into {@code to}, nulls too; allocates nothing once {@code to} has grown. */
    private static void copyViews(@NonNull ArrayList<View> from, @NonNull ArrayList<View> to) {
        to.clear();
        for (int i = 0; i < from.size(); i++) to.add(from.get(i));
    }

    /**
     * Records everything drawn beneath {@code surface} into {@code out}, in the surface's own
     * coordinates offset by {@code marginPx} on each side. False, with {@code out} untouched, when
     * the surface is not inside the root or its transform cannot be undone.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public boolean recordBelow(@NonNull View surface, @NonNull RenderNode out, int marginPx) {
        if (!collect(surface)) return false;
        int width = Math.max(1, surface.getWidth() + 2 * marginPx);
        int height = Math.max(1, surface.getHeight() + 2 * marginPx);
        out.setPosition(0, 0, width, height);
        return record(out, width, height, marginPx, 0);
    }

    /** The root this recorder records within. */
    @NonNull
    public ViewGroup root() {
        return mRoot;
    }

    /**
     * Records the whole root, as the window draws it, beneath {@code surface} in another window
     * (a popup menu): everything in the root is beneath a window above it. In {@code surface}'s
     * coordinates offset by {@code marginPx}, placed by the two views' screen positions. False,
     * with {@code out} untouched, when the root cannot draw its children ({@link Container}) or
     * either view is not on screen.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public boolean recordWindowBeneath(@NonNull View surface, @NonNull RenderNode out, int marginPx) {
        if (!canDrawWindowBeneath(surface)) return false;
        int width = Math.max(1, surface.getWidth() + 2 * marginPx);
        int height = Math.max(1, surface.getHeight() + 2 * marginPx);
        out.setPosition(0, 0, width, height);
        RecordingCanvas canvas = out.beginRecording(width, height);
        try {
            drawWindow(surface, canvas, marginPx, true);
        } finally {
            out.endRecording();
        }
        return true;
    }

    /**
     * {@link #recordBelow} drawn now into a software {@code canvas} (a software Snapshot, below
     * Android 12): every view beneath is really redrawn ({@code View.draw}), never the surface or
     * anything above it. False, with nothing drawn, as {@link #recordBelow}. The canvas is
     * restored however the draw ends.
     */
    public boolean drawBelow(@NonNull View surface, @NonNull Canvas canvas, int marginPx) {
        if (!collect(surface) || !mMatrix.invert(mInverse)) return false;
        int save = canvas.save();
        try {
            drawPath(canvas, marginPx, 0, false);
        } finally {
            canvas.restoreToCount(save);
        }
        return true;
    }

    /** {@link #recordWindowBeneath} drawn now into a software {@code canvas}, as {@link #drawBelow}. */
    public boolean drawWindowBeneath(@NonNull View surface, @NonNull Canvas canvas, int marginPx) {
        if (!canDrawWindowBeneath(surface)) return false;
        int save = canvas.save();
        try {
            drawWindow(surface, canvas, marginPx, false);
        } finally {
            canvas.restoreToCount(save);
        }
        return true;
    }

    private boolean canDrawWindowBeneath(@NonNull View surface) {
        return mRoot instanceof Container && mRoot.isAttachedToWindow() && surface.isAttachedToWindow();
    }

    /** The whole root into {@code canvas}, placed by the two views' screen positions. */
    private void drawWindow(@NonNull View surface, @NonNull Canvas canvas, int marginPx, boolean z) {
        mRoot.getLocationOnScreen(mLocation);
        int rootX = mLocation[0];
        int rootY = mLocation[1];
        surface.getLocationOnScreen(mLocation);
        mBeneath.clear();
        collectLevel(mRoot, null, mBeneath);
        try {
            canvas.translate(marginPx + rootX - mLocation[0], marginPx + rootY - mLocation[1]);
            drawGround(canvas);
            canvas.translate(-mRoot.getScrollX(), -mRoot.getScrollY());
            if (z) enableZ(canvas);
            for (int i = 0; i < mBeneath.size(); i++) {
                drawBeneath((Container) mRoot, canvas, mBeneath.get(i));
            }
            if (z) disableZ(canvas);
        } finally {
            mBeneath.clear();
        }
    }

    /**
     * The ground under the root's children, bottom first: the background of each view from the
     * decor down to the root, at its place. {@code canvas} is in the root's own coordinates.
     */
    private void drawGround(@NonNull Canvas canvas) {
        mRoot.getLocationInWindow(mRootLocation);
        drawGroundOf(canvas, mRoot);
    }

    private void drawGroundOf(@NonNull Canvas canvas, @NonNull View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof View) drawGroundOf(canvas, (View) parent);
        Drawable background = view.getBackground();
        if (background == null || view.getVisibility() != View.VISIBLE) return;
        view.getLocationInWindow(mLocation);
        int save = canvas.save();
        canvas.translate(mLocation[0] - mRootLocation[0], mLocation[1] - mRootLocation[1]);
        background.draw(canvas);
        canvas.restoreToCount(save);
    }

    /** The live scene frame's generation, 0 with no scene: a recording made over another is stale. */
    private static int sceneGeneration() {
        LiveSceneFrame.Frame frame = LiveSceneFrame.current();
        return frame == null ? 0 : frame.generation;
    }

    @NonNull private final android.graphics.Paint mScenePaint =
        new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
    @NonNull private final android.graphics.Rect mSceneDest = new android.graphics.Rect();

    /**
     * Draws one view beneath a surface. With a scene on, the scene's SurfaceView cannot be
     * recorded, so its live frame is drawn over its bounds in its place (the frame covers the
     * layer's screen rect, as the stored-frame branch of the slab aims it) and the photo under it
     * is left out: the scene frame is the one ground. Otherwise the view draws itself.
     */
    private void drawBeneath(@NonNull Container parent, @NonNull Canvas canvas, @NonNull View below) {
        LiveSceneFrame.Frame frame = LiveSceneFrame.current();
        LiveGround.Draw how = LiveGround.choose(frame != null,
            below instanceof SceneLayerView, below instanceof WallpaperBackdropView);
        if (how == LiveGround.Draw.SKIP) return;
        if (how == LiveGround.Draw.SCENE_FRAME && frame != null) {
            if (!frame.bitmap.isRecycled() && below.getVisibility() == View.VISIBLE) {
                mSceneDest.set(below.getLeft(), below.getTop(), below.getRight(), below.getBottom());
                canvas.drawBitmap(frame.bitmap, null, mSceneDest, mScenePaint);
            }
            return;
        }
        parent.drawChildForBackdrop(canvas, below);
    }

    /** Shadows and Z order among the views drawn, as a parent's own hardware draw has them. */
    private static void enableZ(@NonNull Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) canvas.enableZ();
    }

    private static void disableZ(@NonNull Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) canvas.disableZ();
    }

    @NonNull private final int[] mLocation = new int[2];
    @NonNull private final int[] mRootLocation = new int[2];

    /**
     * Fills {@link #mPath} (surface first, root last), {@link #mBeneath} (per level from the root
     * down, each level's views bottom first, then a null) and {@link #mMatrix} (surface to root).
     */
    private boolean collect(@NonNull View surface) {
        mPath.clear();
        mBeneath.clear();
        View view = surface;
        while (view != mRoot) {
            mPath.add(view);
            ViewParent parent = view.getParent();
            if (!(parent instanceof ViewGroup)) return false;
            view = (View) parent;
        }
        mPath.add(mRoot);
        for (int i = mPath.size() - 1; i >= 1; i--) {
            ViewGroup parent = (ViewGroup) mPath.get(i);
            View child = mPath.get(i - 1);
            if (parent instanceof Container) collectLevel(parent, child, mBeneath);
            mBeneath.add(null);
        }
        pathMatrix();
        return true;
    }

    /** {@link #mMatrix}, the surface's coordinates to the root's, along {@link #mPath} now. */
    private void pathMatrix() {
        mMatrix.reset();
        for (int i = mPath.size() - 1; i >= 1; i--) {
            ViewGroup parent = (ViewGroup) mPath.get(i);
            View child = mPath.get(i - 1);
            mMatrix.preTranslate(child.getLeft() - parent.getScrollX(),
                child.getTop() - parent.getScrollY());
            Matrix own = child.getMatrix();
            if (!own.isIdentity()) mMatrix.preConcat(own);
        }
    }

    /** The surface's matrix {@link #collect} or {@link #collectPlanned} last found. */
    @VisibleForTesting
    @NonNull
    Matrix collectedMatrix() {
        return mMatrix;
    }

    /** The views beneath {@link #collect} or {@link #collectPlanned} last found. */
    @VisibleForTesting
    @NonNull
    ArrayList<View> collectedBeneath() {
        return mBeneath;
    }

    /** Moves the plan stamp, as a layout pass does. */
    @VisibleForTesting
    void forgetPlansForTest() {
        mPlanStamp++;
    }

    /**
     * {@code parent}'s children drawn before {@code pathChild}, bottom first, into {@code out}; every
     * child it draws when {@code pathChild} is null.
     */
    @VisibleForTesting
    void collectLevel(@NonNull ViewGroup parent, @Nullable View pathChild, @NonNull ArrayList<View> out) {
        int count = parent.getChildCount();
        if (mPreorder.length < count) {
            mPreorder = new int[count];
            mZ = new float[count];
            mDrawn = new boolean[count];
            mOut = new int[count];
            mOrder = new int[count];
        }
        boolean customOrder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        for (int i = 0; i < count; i++) {
            mPreorder[i] = customOrder ? parent.getChildDrawingOrder(i) : i;
            View child = parent.getChildAt(i);
            mZ[i] = child.getZ();
            // ViewGroup.dispatchDraw's own test: shown, or animating out.
            mDrawn[i] = child.getVisibility() == View.VISIBLE || child.getAnimation() != null;
        }
        int n = beneath(count, mPreorder, mZ, mDrawn,
            pathChild == null ? EVERY_CHILD : parent.indexOfChild(pathChild), mOrder, mOut);
        for (int i = 0; i < n; i++) out.add(parent.getChildAt(mOut[i]));
    }

    /**
     * Which children draw beneath the one at {@code pathIndex}, bottom first, as
     * {@code ViewGroup}'s draw orders them: the drawing order ({@code preorder}, container indices
     * by drawing position), then a stable sort by Z, so a child raised by elevation draws after
     * every lower one whatever its place. Only children that draw at all ({@code drawn}) are
     * written; the path child and everything after it never are. With {@link #EVERY_CHILD} for
     * {@code pathIndex}, every child that draws is written. Pure.
     *
     * @param order scratch of at least {@code count}, for the sorted drawing order
     * @return how many container indices were written to {@code out}; 0 when the path child is
     *         not among them
     */
    @VisibleForTesting
    static int beneath(int count, @NonNull int[] preorder, @NonNull float[] z,
                       @NonNull boolean[] drawn, int pathIndex, @NonNull int[] order,
                       @NonNull int[] out) {
        int sorted = 0;
        for (int i = 0; i < count; i++) {
            int index = preorder[i];
            int at = sorted;
            while (at > 0 && z[order[at - 1]] > z[index]) at--;
            System.arraycopy(order, at, order, at + 1, sorted - at);
            order[at] = index;
            sorted++;
        }
        int written = 0;
        for (int i = 0; i < sorted; i++) {
            int index = order[i];
            if (index == pathIndex) return written;
            if (drawn[index]) out[written++] = index;
        }
        return pathIndex == EVERY_CHILD ? written : 0;
    }

    /** {@link #beneath}'s path index for "no path child": every child that draws. */
    static final int EVERY_CHILD = Integer.MIN_VALUE;

    /** Draws {@link #mBeneath} into {@code out} along {@link #mPath}, as {@link #collect} found them. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private boolean record(@NonNull RenderNode out, int width, int height, int marginPx,
                           int reachTopPx) {
        if (!mMatrix.invert(mInverse)) return false;
        RecordingCanvas canvas = out.beginRecording(width, height);
        try {
            drawPath(canvas, marginPx, reachTopPx, true);
        } finally {
            out.endRecording();
        }
        return true;
    }

    /**
     * Draws {@link #mBeneath} along {@link #mPath} into {@code canvas}, with {@link #mInverse}
     * already set. {@code z} for a recording canvas, where the parent's own draw orders by Z with
     * shadows; a software canvas has neither, and {@link #collectLevel} has ordered by Z already.
     */
    private void drawPath(@NonNull Canvas canvas, int marginPx, int reachTopPx, boolean z) {
        canvas.translate(marginPx, marginPx + reachTopPx);
        canvas.concat(mInverse);
        drawGround(canvas);
        int next = 0;
        for (int i = mPath.size() - 1; i >= 1; i--) {
            ViewGroup parent = (ViewGroup) mPath.get(i);
            View child = mPath.get(i - 1);
            int save = canvas.save();
            canvas.translate(-parent.getScrollX(), -parent.getScrollY());
            if (z) enableZ(canvas);
            for (View below = mBeneath.get(next++); below != null; below = mBeneath.get(next++)) {
                drawBeneath((Container) parent, canvas, below);
            }
            if (z) disableZ(canvas);
            canvas.restoreToCount(save);
            canvas.translate(child.getLeft() - parent.getScrollX(),
                child.getTop() - parent.getScrollY());
            Matrix own = child.getMatrix();
            if (!own.isIdentity()) canvas.concat(own);
        }
    }

    // ---- The page layer --------------------------------------------------------------------

    /**
     * How {@code picture} is drawn this frame ({@link BackdropLayers}), its page layer
     * recorded, captured, collected or re-aimed to match. Runs after {@link #collect}. LIVE at
     * rest without a scene before anything is walked, and LIVE for a frame whose page layer is not
     * ready (a capture still rendering): the surface keeps its one-layer recording until it is.
     */
    @NonNull
    private BackdropLayers.Mode pageLayerMode(@NonNull LivePicture picture,
                                                    @NonNull View surface, int margin, int reach) {
        mPageChanged = false;
        mExpand.clear();
        mWallMatrix.reset();
        BackdropLayers.Mode want = BackdropLayers.Mode.LIVE;
        ViewGroup wall = mWall;
        boolean moving = wall != null && ((Wall) wall).isMovingForBackdrop();
        boolean scene = LiveSceneFrame.current() != null;
        if (wall != null && (moving || scene)) {
            want = BackdropLayers.mode(splittable(wall, surface, margin, reach, moving), moving,
                scene);
        }
        if (want != BackdropLayers.Mode.FROZEN) picture.mSwipeCaptures = 0;
        if (want == BackdropLayers.Mode.LIVE) {
            if (picture.mPageMode != BackdropLayers.Mode.LIVE || picture.capturing()) {
                picture.dropPageLayer();
            }
            return want;
        }
        return preparePageLayer(picture, want) ? want : BackdropLayers.Mode.LIVE;
    }

    /**
     * Whether the wall's pages beneath {@code surface} can go in a page layer: they are beneath
     * it, every page drawn is opaque or gone, the views around the wall can draw their children
     * one by one, and the two layers fit the picture's budget. Fills the page layer's scratch:
     * {@link #mPages} and their poses, {@link #mChain} and {@link #mExpand}, the wall's matrix,
     * what the surface needs ({@link #mNeeded}), the reach a new layer holds ({@link #mReach}) and
     * its rect ({@link #mPageRect}).
     */
    private boolean splittable(@NonNull ViewGroup wall, @NonNull View surface, int margin,
                               int reach, boolean moving) {
        mFadingPages = false;
        mChain.clear();
        mPages.clear();
        mPoseCount = 0;
        mUnion.setEmpty();
        if (!wall.isAttachedToWindow() || !wallMatrix(wall)) return false;
        mWallPathIndex = mPath.indexOf(wall);
        if (mWallPathIndex == 0) return false;
        // Where the surface stands in the wall's children's coordinates.
        mPageMatrix.set(mWallInverse);
        mPageMatrix.preConcat(mMatrix);
        if (mWallPathIndex > 0) {
            // On the wall: the pages drawn before the surface's, and the levels below it, move
            // with the surface, so the layer moves with it too.
            int at = levelStart(mWallPathIndex);
            for (View page = mBeneath.get(at); page != null; page = mBeneath.get(++at)) {
                if (!addPage(page)) return false;
            }
            boolean content = !mPages.isEmpty();
            for (int i = at + 1; !content && i < mBeneath.size(); i++) content = mBeneath.get(i) != null;
            if (!content) return false;
            addPose(mPageMatrix, 0f, 0f, 1f);
            unionBounds(mPath.get(mWallPathIndex - 1));
        } else {
            // Beneath it: the view that holds the wall, opened down to the wall. The walks are
            // kept for the motion ({@link MotionPlan}); the alphas are read on every frame.
            MotionPlan plan = mPlanning;
            if (plan != null && planHolds(plan.chainStamp, mPlanStamp, moving)) {
                copyViews(plan.chain, mChain);
                copyViews(plan.expand, mExpand);
                copyViews(plan.wallChildren, mWallChildren);
            } else {
                if (!openWall(wall)) return false;
                if (plan != null && moving) {
                    copyViews(mChain, plan.chain);
                    copyViews(mExpand, plan.expand);
                    copyViews(mWallChildren, plan.wallChildren);
                    plan.chainStamp = mPlanStamp;
                }
            }
            mFadingPages = moving;
            for (int d = 0; d < mChain.size(); d++) {
                if (mChain.get(d).getAlpha() != 1f) return false;
            }
            boolean opaque = true;
            for (int i = 0; i < mWallChildren.size() && opaque; i++) {
                opaque = addPage(mWallChildren.get(i));
            }
            if (!opaque || mPages.isEmpty()) return false;
        }
        // The recording reaches reach px above the surface (a float's titlebar draws it), so
        // the page layer has to cover that strip too.
        mNeeded.set(-margin, -margin - reach, surface.getWidth() + margin,
            surface.getHeight() + margin);
        mPageMatrix.mapRect(mNeeded);
        long base = layerBytes(surface.getWidth() + 2 * margin,
            surface.getHeight() + 2 * margin + reach);
        // Over a live scene a surface beneath which the pages travel keeps a blurred scene ground
        // of its own recording's size beside the pages layer ({@link LivePicture#groundScene}).
        if (mWallPathIndex < 0 && LiveSceneFrame.current() != null) base *= 2;
        // A pixel each way for the layer's rect rounding out.
        int neededWidth = (int) Math.ceil(mNeeded.width()) + 2;
        int neededHeight = (int) Math.ceil(mNeeded.height()) + 2;
        int padX = 0;
        int padY = 0;
        if (mWallPathIndex < 0) {
            // A swipe carries the pages up to a width either way under a surface that stays.
            // A frozen layer holds each page, and the decor, as a picture of its own.
            int slots = moving ? BackdropLayers.frozenSlots(mPages.size()) : 1;
            if (slots > LivePicture.MAX_SLOTS) return false;
            padX = BackdropLayers.pad(base, neededWidth, neededHeight, true, wall.getWidth(),
                slots);
            if (padX < 0) return false;
            padY = BackdropLayers.pad(base, neededWidth + 2 * padX, neededHeight, false,
                neededHeight, slots);
        } else if (!BackdropLayers.fits(base, neededWidth, neededHeight)) {
            return false;
        }
        mReach.set(mNeeded);
        mReach.inset(-padX, -padY);
        mBounds.set(mReach);
        if (!mBounds.intersect(mUnion)) return false;
        mBounds.roundOut(mPageRect);
        return !mPageRect.isEmpty();
    }

    /**
     * The walks of {@link #splittable} for a surface beneath the wall: {@link #mChain}, the view
     * beneath the surface that holds the wall down to the wall; {@link #mExpand}, each chain
     * container's children around the next; {@link #mWallChildren}, the wall's children. False
     * when the wall is not beneath the surface or a container on the way cannot draw its
     * children one by one. Reads no alpha: those change as the pages slide.
     */
    private boolean openWall(@NonNull ViewGroup wall) {
        mWallChildren.clear();
        View top = wall;
        mChain.add(wall);
        ViewParent parent = wall.getParent();
        while (parent instanceof ViewGroup && mPath.indexOf(parent) < 0) {
            top = (View) parent;
            mChain.add(top);
            parent = top.getParent();
        }
        if (!(parent instanceof ViewGroup) || mBeneath.indexOf(top) < 0) return false;
        java.util.Collections.reverse(mChain);
        int last = mChain.size() - 1;
        for (int d = 0; d <= last; d++) {
            View view = mChain.get(d);
            if (!(view instanceof Container)) return false;
            if (d == last) break;
            int from = mExpand.size();
            collectLevel((ViewGroup) view, null, mExpand);
            View next = mChain.get(d + 1);
            int index = -1;
            for (int i = from; i < mExpand.size() && index < 0; i++) {
                if (mExpand.get(i) == next) index = i;
            }
            if (index < 0) return false;
            mExpand.set(index, null);
            mExpand.add(null);
        }
        collectLevel(wall, null, mWallChildren);
        return true;
    }

    /**
     * {@code page} into {@link #mPages} with its pose; true for a page gone; false for one fading.
     * While {@link #mFadingPages}, a page at any alpha goes in, a faded-out one too: the frozen
     * layer pictures it whole and composites it at its alpha now, so a page fading in or out as
     * the pages slide neither refuses the layer nor captures it again.
     */
    private boolean addPage(@NonNull View page) {
        float alpha = page.getAlpha();
        if (!mFadingPages) {
            if (alpha <= 0f) return true;
            if (alpha < 1f) return false;
        }
        mPages.add(page);
        mPageMatrix.setTranslate(page.getLeft(), page.getTop());
        Matrix own = page.getMatrix();
        if (!own.isIdentity()) mPageMatrix.preConcat(own);
        addPose(mPageMatrix, page.getLeft(), page.getTop(), alpha);
        unionBounds(page);
        // mPageMatrix is the surface's place on the wall again for the caller.
        mPageMatrix.set(mWallInverse);
        mPageMatrix.preConcat(mMatrix);
        return true;
    }

    /** {@code page}'s bounds in the wall's children's coordinates, into {@link #mUnion}. */
    private void unionBounds(@NonNull View page) {
        mBounds.set(0f, 0f, page.getWidth(), page.getHeight());
        Matrix own = page.getMatrix();
        if (!own.isIdentity()) own.mapRect(mBounds);
        mBounds.offset(page.getLeft(), page.getTop());
        mUnion.union(mBounds);
    }

    private void addPose(@NonNull Matrix matrix, float left, float top, float alpha) {
        int at = mPoseCount * BackdropLayers.POSE_STRIDE;
        if (mPoses.length < at + BackdropLayers.POSE_STRIDE) {
            mPoses = java.util.Arrays.copyOf(mPoses, 2 * (at + BackdropLayers.POSE_STRIDE));
        }
        matrix.getValues(mValues);
        System.arraycopy(mValues, 0, mPoses, at, 9);
        mPoses[at + BackdropLayers.POSE_LEFT] = left;
        mPoses[at + BackdropLayers.POSE_TOP] = top;
        mPoses[at + BackdropLayers.POSE_ALPHA] = alpha;
        mPoseCount++;
    }

    @NonNull private final float[] mValues = new float[9];

    /** The wall's children's coordinates to the root's into {@link #mWallMatrix}; false if not inside. */
    private boolean wallMatrix(@NonNull ViewGroup wall) {
        mWallMatrix.setTranslate(-wall.getScrollX(), -wall.getScrollY());
        View view = wall;
        while (view != mRoot) {
            ViewParent parent = view.getParent();
            if (!(parent instanceof ViewGroup)) return false;
            ViewGroup group = (ViewGroup) parent;
            Matrix own = view.getMatrix();
            if (!own.isIdentity()) mWallMatrix.postConcat(own);
            mWallMatrix.postTranslate(view.getLeft() - group.getScrollX(),
                view.getTop() - group.getScrollY());
            view = group;
        }
        return mWallMatrix.invert(mWallInverse);
    }

    /** Where the level whose parent is {@code mPath.get(pathIndex)} starts in {@link #mBeneath}. */
    private int levelStart(int pathIndex) {
        int level = mPath.size() - 1 - pathIndex;
        int at = 0;
        while (level > 0) {
            if (mBeneath.get(at++) == null) level--;
        }
        return at;
    }

    /**
     * Records, captures, collects or re-aims {@code picture}'s page layer for {@code want}. Whether
     * it can be drawn this frame. Sets {@link #mPageChanged} when the record node has to be
     * recorded around a new one.
     */
    private boolean preparePageLayer(@NonNull LivePicture picture,
                                     @NonNull BackdropLayers.Mode want) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        boolean frozen = want == BackdropLayers.Mode.FROZEN;
        float shift = BackdropLayers.shift(picture.mRecordedPoses, picture.mRecordedPoseCount,
            mPoses, mPoseCount, frozen);
        boolean samePages = sameViews(picture.mRecordedPages, mPages);
        float aim = BackdropLayers.aim(want, shift);
        RectF reach = picture.mPageReach;
        boolean covered = BackdropLayers.covers(reach.left, reach.top, reach.right,
            reach.bottom, mNeeded.left, mNeeded.top, mNeeded.right, mNeeded.bottom, aim);
        if (picture.capturing()) {
            // A capture under way pictures the pages as they stood when it started.
            if (want == BackdropLayers.Mode.FROZEN && !BackdropLayers.needsRecord(want,
                    want, samePages, shift, covered)) {
                if (!picture.collectCapture()) return false;
                mPageChanged = true;
                picture.aimPage(aim);
                picture.alphaPages(mPoses, mPoseCount);
                return true;
            }
            picture.dropPageLayer();
            shift = Float.NaN;
        }
        if (picture.hasPageLayer() && !BackdropLayers.needsRecord(want, picture.mPageMode,
                samePages, shift, covered)) {
            picture.aimPage(aim);
            if (frozen) picture.alphaPages(mPoses, mPoseCount);
            return true;
        }
        if (want == BackdropLayers.Mode.FROZEN
                && picture.mSwipeCaptures >= MAX_CAPTURES_PER_SWIPE) {
            // The pages keep moving apart this swipe: one layer until it settles.
            if (picture.mPageMode != BackdropLayers.Mode.LIVE) picture.dropPageLayer();
            return false;
        }
        picture.dropPageLayer();
        Trace.beginSection("LiveBackdrop.pageLayer");
        try {
            picture.mRecordedPages.addAll(mPages);
            int floats = mPoseCount * BackdropLayers.POSE_STRIDE;
            if (picture.mRecordedPoses.length < floats) picture.mRecordedPoses = new float[floats];
            System.arraycopy(mPoses, 0, picture.mRecordedPoses, 0, floats);
            picture.mRecordedPoseCount = mPoseCount;
            picture.mPageReach.set(mReach);
            if (want == BackdropLayers.Mode.REFERENCE) {
                RenderNode page = picture.pageNode(mPageRect, true);
                RecordingCanvas canvas = page.beginRecording(LivePicture.quarter(mPageRect.width()),
                    LivePicture.quarter(mPageRect.height()));
                try {
                    drawPages(canvas, mPageRect, false);
                } finally {
                    page.endRecording();
                }
                picture.mPageMode = want;
                mPageChanged = true;
                return true;
            }
            if (!recordSlots(picture)) {
                picture.dropPageLayer();
                return false;
            }
            picture.mSwipeCaptures++;
            picture.startCapture(mPageRect);
            // It lands on a later frame (collectCapture); this one keeps one layer.
            return false;
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Lays out and records the frozen layer's slots: under a surface off the wall, each page at
     * full opacity over its bounds (and a blur's reach beyond, so its edge fades as it does in
     * one picture), then the wall's decor over {@link #mPageRect}; on the wall, one slot as the
     * page layer always was. False when the slots do not fit.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private boolean recordSlots(@NonNull LivePicture picture) {
        picture.clearSlots();
        ViewGroup wall = mWall;
        if (wall == null) return false;
        if (mWallPathIndex < 0) {
            int gutter = picture.slotGutterPx();
            for (int i = 0; i < mPages.size(); i++) {
                View page = mPages.get(i);
                mSlotBounds.set(0f, 0f, page.getWidth(), page.getHeight());
                Matrix own = page.getMatrix();
                if (!own.isIdentity()) own.mapRect(mSlotBounds);
                mSlotBounds.offset(page.getLeft(), page.getTop());
                mSlotBounds.inset(-gutter, -gutter);
                if (!mSlotBounds.intersect(mPageRect.left, mPageRect.top, mPageRect.right,
                        mPageRect.bottom)) continue;
                mSlotBounds.roundOut(mSlot);
                if (mSlot.isEmpty()) continue;
                RenderNode node = picture.addSlot(i, mSlot, BackdropLayers.pageAlpha(mPoses, i));
                if (node == null) return false;
                RecordingCanvas canvas = node.beginRecording(LivePicture.quarter(mSlot.width()),
                    LivePicture.quarter(mSlot.height()));
                try {
                    canvas.scale(1f / LivePicture.SCALE, 1f / LivePicture.SCALE);
                    canvas.translate(-mSlot.left, -mSlot.top);
                    canvas.translate(page.getLeft(), page.getTop());
                    if (!own.isIdentity()) canvas.concat(own);
                    page.draw(canvas);
                } finally {
                    node.endRecording();
                }
            }
            RenderNode decor = picture.addSlot(LivePicture.SLOT_DECOR, mPageRect, 1f);
            if (decor == null) return false;
            RecordingCanvas canvas = decor.beginRecording(LivePicture.quarter(mPageRect.width()),
                LivePicture.quarter(mPageRect.height()));
            try {
                canvas.scale(1f / LivePicture.SCALE, 1f / LivePicture.SCALE);
                canvas.translate(-mPageRect.left, -mPageRect.top);
                ((Wall) wall).drawDecorForBackdrop(canvas);
            } finally {
                decor.endRecording();
            }
            return true;
        }
        RenderNode whole = picture.addSlot(LivePicture.SLOT_DECOR, mPageRect, 1f);
        if (whole == null) return false;
        RecordingCanvas canvas = whole.beginRecording(LivePicture.quarter(mPageRect.width()),
            LivePicture.quarter(mPageRect.height()));
        try {
            drawPages(canvas, mPageRect, true);
        } finally {
            whole.endRecording();
        }
        return true;
    }

    /**
     * The page layer's content into {@code canvas}, a quarter of {@code rect} (the wall's
     * children's coordinates): the pages {@link #splittable} found, then the wall's own drawing
     * over them for a surface beneath the wall, or the levels below the wall for one on it.
     * {@code frozen} draws each page's own drawing at the pose it has now, so the page's travel
     * after this is not in the picture; otherwise each page is drawn as its parent draws it.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private void drawPages(@NonNull RecordingCanvas canvas, @NonNull Rect rect, boolean frozen) {
        ViewGroup wall = mWall;
        if (wall == null) return;
        canvas.scale(1f / LivePicture.SCALE, 1f / LivePicture.SCALE);
        canvas.translate(-rect.left, -rect.top);
        if (!frozen) canvas.enableZ();
        for (int i = 0; i < mPages.size(); i++) {
            View page = mPages.get(i);
            if (frozen) {
                int save = canvas.save();
                canvas.translate(page.getLeft(), page.getTop());
                Matrix own = page.getMatrix();
                if (!own.isIdentity()) canvas.concat(own);
                page.draw(canvas);
                canvas.restoreToCount(save);
            } else {
                ((Container) wall).drawChildForBackdrop(canvas, page);
            }
        }
        if (!frozen) canvas.disableZ();
        if (mWallPathIndex < 0) {
            ((Wall) wall).drawDecorForBackdrop(canvas);
            return;
        }
        View pathPage = mPath.get(mWallPathIndex - 1);
        canvas.translate(pathPage.getLeft(), pathPage.getTop());
        Matrix own = pathPage.getMatrix();
        if (!own.isIdentity()) canvas.concat(own);
        int next = levelStart(mWallPathIndex - 1);
        for (int i = mWallPathIndex - 1; i >= 1; i--) {
            ViewGroup parent = (ViewGroup) mPath.get(i);
            View child = mPath.get(i - 1);
            int save = canvas.save();
            canvas.translate(-parent.getScrollX(), -parent.getScrollY());
            canvas.enableZ();
            for (View below = mBeneath.get(next++); below != null; below = mBeneath.get(next++)) {
                drawBeneath((Container) parent, canvas, below);
            }
            canvas.disableZ();
            canvas.restoreToCount(save);
            canvas.translate(child.getLeft() - parent.getScrollX(),
                child.getTop() - parent.getScrollY());
            own = child.getMatrix();
            if (!own.isIdentity()) canvas.concat(own);
        }
    }

    /** {@link #record} with the page layer in the wall's place. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private boolean recordSplit(@NonNull RenderNode out, int width, int height, int marginPx,
                                int reachTopPx, @NonNull LivePicture picture) {
        RenderNode page = picture.pageLayer();
        if (page == null || !mMatrix.invert(mInverse)) return false;
        RecordingCanvas canvas = out.beginRecording(width, height);
        try {
            canvas.translate(marginPx, marginPx + reachTopPx);
            canvas.concat(mInverse);
            drawGround(canvas);
            int next = 0;
            for (int i = mPath.size() - 1; i >= 1; i--) {
                ViewGroup parent = (ViewGroup) mPath.get(i);
                View child = mPath.get(i - 1);
                int save = canvas.save();
                canvas.translate(-parent.getScrollX(), -parent.getScrollY());
                if (i == mWallPathIndex) {
                    // The page layer stands for this level and every one below it.
                    drawPageSlot(canvas, page, picture.mPageRect);
                    canvas.restoreToCount(save);
                    break;
                }
                canvas.enableZ();
                for (View below = mBeneath.get(next++); below != null; below = mBeneath.get(next++)) {
                    if (!mChain.isEmpty() && below == mChain.get(0)) {
                        canvas.disableZ();
                        drawAroundWall(canvas, page, picture.mPageRect);
                        canvas.enableZ();
                    } else {
                        drawBeneath((Container) parent, canvas, below);
                    }
                }
                canvas.disableZ();
                canvas.restoreToCount(save);
                canvas.translate(child.getLeft() - parent.getScrollX(),
                    child.getTop() - parent.getScrollY());
                Matrix own = child.getMatrix();
                if (!own.isIdentity()) canvas.concat(own);
            }
        } finally {
            out.endRecording();
        }
        return true;
    }

    /**
     * The view beneath the surface that holds the wall ({@link #mChain}), opened down to the wall:
     * each container as its parent would place, clip and paint it, its children drawn one by one
     * around the next, and the page layer where the wall's pages are. A container's own drawing
     * other than its background is not drawn; none of the ones around the wall has any.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private void drawAroundWall(@NonNull RecordingCanvas canvas, @NonNull RenderNode page,
                                @NonNull Rect rect) {
        int last = mChain.size() - 1;
        if (mSaves.length <= last) mSaves = new int[last + 1];
        int next = 0;
        for (int d = 0; d <= last; d++) {
            ViewGroup view = (ViewGroup) mChain.get(d);
            mSaves[d] = canvas.save();
            canvas.translate(view.getLeft(), view.getTop());
            Matrix own = view.getMatrix();
            if (!own.isIdentity()) canvas.concat(own);
            if (view.getClipChildren()) canvas.clipRect(0, 0, view.getWidth(), view.getHeight());
            Drawable background = view.getBackground();
            if (background != null) background.draw(canvas);
            canvas.translate(-view.getScrollX(), -view.getScrollY());
            if (view.getClipToPadding() && (view.getPaddingLeft() != 0 || view.getPaddingTop() != 0
                    || view.getPaddingRight() != 0 || view.getPaddingBottom() != 0)) {
                canvas.clipRect(view.getScrollX() + view.getPaddingLeft(),
                    view.getScrollY() + view.getPaddingTop(),
                    view.getScrollX() + view.getWidth() - view.getPaddingRight(),
                    view.getScrollY() + view.getHeight() - view.getPaddingBottom());
            }
            if (d == last) {
                drawPageSlot(canvas, page, rect);
                break;
            }
            canvas.enableZ();
            for (View child = mExpand.get(next++); child != null; child = mExpand.get(next++)) {
                ((Container) view).drawChildForBackdrop(canvas, child);
            }
            canvas.disableZ();
            // Skip this level's children after the next container; drawn on the way back up.
            while (mExpand.get(next++) != null) { }
        }
        for (int d = last - 1; d >= 0; d--) {
            canvas.restoreToCount(mSaves[d + 1]);
            int at = afterStart(d);
            canvas.enableZ();
            for (View child = mExpand.get(at++); child != null; child = mExpand.get(at++)) {
                ((Container) mChain.get(d)).drawChildForBackdrop(canvas, child);
            }
            canvas.disableZ();
        }
        canvas.restoreToCount(mSaves[0]);
    }

    /** Where the children after the next container start, for {@link #mChain}'s level {@code d}. */
    private int afterStart(int d) {
        int nulls = 2 * d + 1;
        int at = 0;
        while (nulls > 0) {
            if (mExpand.get(at++) == null) nulls--;
        }
        return at;
    }

    /** The page layer over {@code rect}, in the wall's children's coordinates, drawn back at full scale. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private static void drawPageSlot(@NonNull RecordingCanvas canvas, @NonNull RenderNode page,
                                     @NonNull Rect rect) {
        int save = canvas.save();
        canvas.translate(rect.left, rect.top);
        canvas.scale(LivePicture.SCALE, LivePicture.SCALE);
        canvas.drawRenderNode(page);
        canvas.restoreToCount(save);
    }

    // ---- Backdrop layers -------------------------------------------------------------------

    /**
     * {@link BackdropLayers#plan} for {@code picture} this frame, from what {@link #collect} and
     * {@link #pageLayerMode} found. Fills the layers' scratch: {@link #mLocal}, the ground's frame
     * ({@link #mGroundBitmap}, {@link #mFrameRect}) or the scene's region, {@link #mInverse} and
     * the wall's matrix. 0 at rest, for a panel and below Android 12: the one picture.
     */
    private int layersPlan(@NonNull LivePicture picture, @NonNull View surface, int margin,
                           int reach, @NonNull BackdropLayers.Mode mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || picture.panelRadius()) return 0;
        ViewGroup wall = mWall;
        if (wall == null || !wall.isAttachedToWindow() || !((Wall) wall).isMovingForBackdrop()) {
            return 0;
        }
        Trace.beginSection("Glass.layers.plan");
        try {
            if (!wallMatrix(wall) || !mMatrix.invert(mInverse)) return 0;
            int wallAt = mPath.indexOf(wall);
            if (wallAt == 0) return 0;
            BackdropLayers.Place place = wallAt > 0 ? BackdropLayers.Place.PAGE
                : picture.mRank == RANK_FLOAT ? BackdropLayers.Place.FLOAT
                : BackdropLayers.Place.CHROME;
            boolean onPage = place == BackdropLayers.Place.PAGE;
            mNeededRoot.set(-margin, -margin - reach, surface.getWidth() + margin,
                surface.getHeight() + margin);
            mMatrix.mapRect(mNeededRoot);
            mNeededWall.set(mNeededRoot);
            mWallInverse.mapRect(mNeededWall);
            View holder = onPage ? null : wallHolder(wall);
            mRoot.getLocationInWindow(mRootLocation);
            boolean pagesBeneath = false;
            boolean fixed = false;
            mLocal.clear();
            int next = 0;
            for (int i = mPath.size() - 1; i >= 1; i--) {
                BackdropLayers.Tier tier = i > wallAt ? BackdropLayers.Tier.BENEATH_WALL
                    : i == wallAt ? BackdropLayers.Tier.PAGE : BackdropLayers.Tier.ON_PAGE;
                for (View below = mBeneath.get(next++); below != null; below = mBeneath.get(next++)) {
                    boolean ground = below instanceof WallpaperBackdropView
                        || below instanceof SceneLayerView;
                    boolean rides = below instanceof WallGround;
                    // Measured only where the answer turns on it.
                    boolean beneathWall = onPage && tier == BackdropLayers.Tier.BENEATH_WALL;
                    boolean band = beneathWall && !ground && !rides && bandOf(next - 1, below);
                    boolean overlaps = onPage && tier == BackdropLayers.Tier.PAGE && !ground
                        && overlapsNeeded(below);
                    boolean seams = rides && ((WallGround) below).groundsDiffer();
                    switch (BackdropLayers.holder(place, tier, ground, below == holder, rides,
                            seams, band, overlaps)) {
                        case PAGES:
                            pagesBeneath = true;
                            break;
                        case LOCAL:
                            mLocal.add(below);
                            break;
                        case FIXED:
                            fixed = true;
                            break;
                        default:
                            break;
                    }
                }
            }
            LiveSceneFrame.Frame scene = LiveSceneFrame.current();
            boolean groundReady;
            if (scene != null) {
                groundReady = !scene.bitmap.isRecycled() && sceneRegion(onPage, picture, surface,
                    margin, reach, pagesBeneath);
                mGroundBitmap = null;
            } else {
                android.graphics.Bitmap frame = mGroundSource == null ? null
                    : mGroundSource.storedFrame(picture.radiusPx(), mFrameRect);
                groundReady = frame != null && !frame.isRecycled() && !mFrameRect.isEmpty();
                mGroundBitmap = groundReady ? frame : null;
            }
            boolean frozen = mode == BackdropLayers.Mode.FROZEN;
            boolean displaced = !mMatrix.equals(picture.mRecordedMatrix)
                || parallaxPx() != picture.mRecordedParallax
                || (frozen && Math.abs(picture.mPageAimPx) > BackdropLayers.SHIFT_EPSILON_PX);
            BackdropLayers.Move pagesMove;
            boolean pagesReady;
            if (onPage) {
                // A lower page under a surface on a page: whether it rides along is not tracked.
                pagesMove = BackdropLayers.Move.TURN;
                pagesReady = false;
            } else if (frozen) {
                // pageLayerMode answers FROZEN only for a pages layer that moved by one shift.
                pagesMove = BackdropLayers.Move.SHIFT;
                pagesReady = picture.holdsFrozen() && picture.hasPageLayer();
            } else {
                pagesMove = picture.capturing() ? BackdropLayers.Move.SHIFT : BackdropLayers.Move.TURN;
                pagesReady = false;
            }
            boolean localChanged = !picture.mRecordHoldsLocal
                || !sameViews(picture.mRecordedLocal, mLocal)
                || picture.mRecordedWidth != surface.getWidth()
                || picture.mRecordedHeight != surface.getHeight()
                || picture.mRecordedMargin != margin || picture.mRecordedReach != reach
                || (!onPage && !mLocal.isEmpty() && !mMatrix.equals(picture.mLocalMatrix));
            int plan = BackdropLayers.plan(place, true, displaced, groundReady, scene != null,
                scene != null && scene.generation != picture.groundGeneration(), pagesBeneath,
                pagesMove, pagesReady, localChanged, fixed);
            if (displaced && (plan & BackdropLayers.LAYERS) == 0) {
                // The one picture, recorded live: a trace shows how often a swipe falls back.
                Trace.beginSection("Glass.layers.fallback");
                Trace.endSection();
            }
            return plan;
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Draws {@code picture}'s backdrop layers for {@code plan}: records the stale ones, aims the
     * ground and the pages, and has the holders draw the composite. Whether they do.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private boolean drawLayers(@NonNull LivePicture picture, @NonNull View surface, int margin,
                               int reach, int plan) {
        int width = surface.getWidth() + 2 * margin;
        int height = surface.getHeight() + 2 * margin + reach;
        Trace.beginSection("Glass.layers.draw");
        try {
            if ((plan & BackdropLayers.STALE_LOCAL) != 0) {
                Trace.beginSection("Glass.layers.local");
                try {
                    RenderNode node = picture.recordNode(width, height);
                    RecordingCanvas canvas = node.beginRecording(width, height);
                    try {
                        canvas.translate(margin, margin + reach);
                        canvas.concat(mInverse);
                        drawLocal(canvas);
                    } finally {
                        node.endRecording();
                    }
                } finally {
                    Trace.endSection();
                }
                picture.mRecordHoldsLocal = true;
                picture.mRecordedLocal.clear();
                picture.mRecordedLocal.addAll(mLocal);
                picture.mLocalMatrix.set(mMatrix);
                picture.mRecordedWidth = surface.getWidth();
                picture.mRecordedHeight = surface.getHeight();
                picture.mRecordedMargin = margin;
                picture.mRecordedReach = reach;
            }
            mRoot.getLocationOnScreen(mLocation);
            LiveSceneFrame.Frame scene = LiveSceneFrame.current();
            mAim.set(mInverse);
            if (scene != null) {
                mGroundRect.set(LiveSceneFrame.rect());
                mGroundRect.offset(-mLocation[0], -mLocation[1]);
                // Recorded again only for a new frame or a region it does not hold.
                Trace.beginSection("Glass.layers.ground");
                picture.groundScene(scene.bitmap, scene.generation, mGroundRect, mSceneRegion);
                Trace.endSection();
            } else {
                android.graphics.Bitmap frame = mGroundBitmap;
                if (frame == null) return false;
                mGroundRect.set(mFrameRect);
                mGroundRect.offset(-mLocation[0], -mLocation[1]);
                picture.groundStored(frame, mGroundRect);
                // The parallax carries the wallpaper left under the surface.
                mAim.preTranslate(-parallaxPx(), 0f);
            }
            mAim.postTranslate(margin, margin + reach);
            picture.aimGround(mAim);
            boolean pages = (plan & BackdropLayers.PAGES) != 0;
            if (pages) {
                Rect rect = picture.mPageRect;
                mPagesAim.set(mWallMatrix);
                mPagesAim.preTranslate(rect.left, rect.top);
                mPagesAim.preScale(LivePicture.SCALE, LivePicture.SCALE);
                mPagesAim.postConcat(mInverse);
                mPagesAim.postTranslate(margin, margin + reach);
                picture.aimPagesLayer(mPagesAim);
            }
            picture.showLayers(pages, width, height);
            if (!picture.layered()) return false;
            picture.recorded();
            return true;
        } finally {
            Trace.endSection();
        }
    }

    /** {@link #mLocal} along {@link #mPath}, as {@link #drawPath} draws every view beneath. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private void drawLocal(@NonNull RecordingCanvas canvas) {
        int next = 0;
        for (int i = mPath.size() - 1; i >= 1; i--) {
            ViewGroup parent = (ViewGroup) mPath.get(i);
            View child = mPath.get(i - 1);
            int save = canvas.save();
            canvas.translate(-parent.getScrollX(), -parent.getScrollY());
            canvas.enableZ();
            for (View below = mBeneath.get(next++); below != null; below = mBeneath.get(next++)) {
                if (mLocal.contains(below)) drawBeneath((Container) parent, canvas, below);
            }
            canvas.disableZ();
            canvas.restoreToCount(save);
            canvas.translate(child.getLeft() - parent.getScrollX(),
                child.getTop() - parent.getScrollY());
            Matrix own = child.getMatrix();
            if (!own.isIdentity()) canvas.concat(own);
        }
    }

    /**
     * The scene ground's region into {@link #mSceneRegion}, in the root's coordinates: what the
     * surface's recording covers, across the root's width for a surface that rides a page. False
     * when its blur layer does not fit the picture's share beside its other layers.
     */
    private boolean sceneRegion(boolean onPage, @NonNull LivePicture picture,
                                @NonNull View surface, int margin, int reach, boolean pages) {
        mBounds.set(mNeededRoot);
        if (onPage) {
            mBounds.left = 0f;
            mBounds.right = mRoot.getWidth();
        }
        mBounds.roundOut(mSceneRegion);
        if (!mSceneRegion.intersect(0, 0, mRoot.getWidth(), mRoot.getHeight())) return false;
        long bytes = layerBytes(surface.getWidth() + 2 * margin,
            surface.getHeight() + 2 * margin + reach)
            + layerBytes(mSceneRegion.width(), mSceneRegion.height());
        if (pages && !onPage) bytes += layerBytes(picture.mPageRect.width(), picture.mPageRect.height());
        return bytes <= MAX_LAYER_BYTES;
    }

    /**
     * {@link #inBand} for the view at {@code index} in {@link #mBeneath}, kept for the motion
     * while the band stays where it is ({@link MotionPlan}): a page sliding sideways sweeps the
     * same band on every frame, and the chrome beneath the wall does not move with it.
     */
    private boolean bandOf(int index, @NonNull View view) {
        MotionPlan plan = mPlanning;
        if (plan == null) return inBand(view);
        int top = (int) Math.floor(mNeededRoot.top);
        int bottom = (int) Math.ceil(mNeededRoot.bottom);
        int size = mBeneath.size();
        if (plan.bandKnown.length < size) {
            plan.bandKnown = new boolean[Math.max(size, 2 * plan.bandKnown.length)];
            plan.band = new boolean[plan.bandKnown.length];
            plan.bandStamp = MotionPlan.NONE;
        }
        if (plan.bandStamp != mPlanStamp || plan.stamp != mPlanStamp || plan.bandTop != top
                || plan.bandBottom != bottom) {
            java.util.Arrays.fill(plan.bandKnown, false);
            plan.bandStamp = mPlanStamp;
            plan.bandTop = top;
            plan.bandBottom = bottom;
        }
        if (!plan.bandKnown[index]) {
            plan.band[index] = inBand(view);
            plan.bandKnown[index] = true;
        }
        return plan.band[index];
    }

    /** Whether {@code view}, beneath the wall, crosses the band a surface on a page sweeps. */
    private boolean inBand(@NonNull View view) {
        if (!view.getGlobalVisibleRect(mTmpRect)) return false;
        mTmpRect.offset(-mRootLocation[0], -mRootLocation[1]);
        return mTmpRect.bottom > mNeededRoot.top && mTmpRect.top < mNeededRoot.bottom;
    }

    /** Whether {@code page}, a page drawn before the surface's own, lies under the surface. */
    private boolean overlapsNeeded(@NonNull View page) {
        mBounds.set(0f, 0f, page.getWidth(), page.getHeight());
        Matrix own = page.getMatrix();
        if (!own.isIdentity()) own.mapRect(mBounds);
        mBounds.offset(page.getLeft(), page.getTop());
        return RectF.intersects(mBounds, mNeededWall);
    }

    /** The view beneath a surface off the wall that holds the wall: the wall, or an ancestor. */
    @Nullable
    private View wallHolder(@NonNull ViewGroup wall) {
        View view = wall;
        while (true) {
            ViewParent parent = view.getParent();
            if (!(parent instanceof View)) return null;
            if (mPath.indexOf(parent) >= 0) return view;
            view = (View) parent;
        }
    }

    /**
     * "See through" for this frame: marks each picture another live surface stands over
     * ({@link LivePicture#mGlassAbove}), found among that surface's views beneath. Only with
     * {@link BackdropLayers.SeeThrough#ONE}; allocates nothing. While the wall moves, marked
     * once per plan ({@link MotionPlan}): which glass stands over which does not change as the
     * pages slide.
     */
    private void markGlassAbove(boolean moving) {
        int count = mPictures.size();
        if (planHolds(mGlassAboveStamp, mPlanStamp, moving) && mGlassAboveCount == count) return;
        mGlassAboveStamp = moving ? mPlanStamp : MotionPlan.NONE;
        mGlassAboveCount = count;
        for (int i = 0; i < count; i++) mPictures.get(i).mGlassAbove = false;
        if (mSeeThrough == BackdropLayers.SeeThrough.ALL || count < 2) return;
        for (int i = 0; i < count; i++) {
            View top = mPictures.get(i).mSurface;
            if (!top.isShown() || !collect(top)) continue;
            for (int b = 0; b < mBeneath.size(); b++) {
                View below = mBeneath.get(b);
                if (below == null) continue;
                for (int j = 0; j < count; j++) {
                    LivePicture other = mPictures.get(j);
                    if (j != i && within(other.mSurface, below)) other.mGlassAbove = true;
                }
            }
        }
    }

    /**
     * Draws {@code picture} as the Wallpaper kind, the stored frame (or the scene's) at its offset,
     * through the ground layer alone: a glass with another glass above it under One layer.
     * Whether it does; false keeps the one picture.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private boolean drawStored(@NonNull LivePicture picture, @NonNull View surface, int margin,
                               int reach) {
        if (!mMatrix.invert(mInverse)) return false;
        int width = surface.getWidth() + 2 * margin;
        int height = surface.getHeight() + 2 * margin + reach;
        mRoot.getLocationOnScreen(mLocation);
        LiveSceneFrame.Frame scene = LiveSceneFrame.current();
        mAim.set(mInverse);
        if (scene != null) {
            if (scene.bitmap.isRecycled()) return false;
            mNeededRoot.set(-margin, -margin - reach, surface.getWidth() + margin,
                surface.getHeight() + margin);
            mMatrix.mapRect(mNeededRoot);
            mNeededRoot.roundOut(mSceneRegion);
            if (!mSceneRegion.intersect(0, 0, mRoot.getWidth(), mRoot.getHeight())) return false;
            mGroundRect.set(LiveSceneFrame.rect());
            mGroundRect.offset(-mLocation[0], -mLocation[1]);
            picture.groundScene(scene.bitmap, scene.generation, mGroundRect, mSceneRegion);
        } else {
            android.graphics.Bitmap frame = mGroundSource == null ? null
                : mGroundSource.storedFrame(picture.radiusPx(), mFrameRect);
            if (frame == null || frame.isRecycled() || mFrameRect.isEmpty()) return false;
            mGroundRect.set(mFrameRect);
            mGroundRect.offset(-mLocation[0], -mLocation[1]);
            picture.groundStored(frame, mGroundRect);
            mAim.preTranslate(-parallaxPx(), 0f);
        }
        mAim.postTranslate(margin, margin + reach);
        picture.recordNode(width, height);
        picture.aimGround(mAim);
        picture.showGround(width, height);
        if (!picture.layered()) return false;
        picture.recorded();
        return true;
    }

    /** Whether {@code view} is {@code ancestor} or inside it. */
    private static boolean within(@NonNull View view, @NonNull View ancestor) {
        for (Object at = view; at instanceof View; at = ((View) at).getParent()) {
            if (at == ancestor) return true;
        }
        return false;
    }

    /** Whether {@code a} and {@code b} hold the same views in the same order; allocates nothing. */
    private static boolean sameViews(@NonNull ArrayList<View> a, @NonNull ArrayList<View> b) {
        int count = a.size();
        if (count != b.size()) return false;
        for (int i = 0; i < count; i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }
}
