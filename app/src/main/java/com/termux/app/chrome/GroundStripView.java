package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The wall's ground strip (page-chrome spec C): between the wallpaper and the pane wall, it paints
 * each wall page's own ground (the dim, and on the Terminal page the canvas tint when it is not
 * glass), with a soft seam that travels with the page edge during a slide. The bands come from
 * {@link GroundBands}; this view only draws them, and answers {@link #groundUnder} from the same
 * bands so what the ink measures is what is drawn. Allocates nothing in {@link #onDraw}.
 *
 * <p>The strip is painted over the window's whole rect ({@link #setCover}), as the root's
 * background used to be, and the first and last band run out to its sides.
 *
 * <p>Its band under any point of a page is that page's ground and moves as the page does, so live
 * glass riding a page carries the strip in its local layer while the wall moves
 * ({@link LiveBackdrop.WallGround}).
 */
public final class GroundStripView extends View implements LiveBackdrop.WallGround {

    /** The seam's width, 24 dp. */
    private static final float FEATHER_DP = 24f;
    private static final int MAX_STOPS = 8;

    private final GroundBands mBands = new GroundBands();
    private final int[] mGrounds = new int[MAX_STOPS];
    private int mStops;
    private int mCurrent;
    private float mStride;
    private float mOffsetPx;
    private final float mFeatherPx;
    @NonNull private final Paint mPaint = new Paint();
    @NonNull private final Rect mCover = new Rect();
    @NonNull private final int[] mOwn = new int[2];
    @NonNull private final int[] mWallAt = new int[2];
    @Nullable private View mWall;
    /** Prototype #283: glass that draws these bands over its own frame ({@link GlassGround}). */
    private static final int MAX_RIDERS = 6;
    @NonNull private final View[] mRiders = new View[MAX_RIDERS];
    /** The bands the riders last drew, so a frame that moves nothing they show redraws none. */
    private final GroundBands mRidden = new GroundBands();
    @NonNull private final Paint mGlassPaint = new Paint();
    @NonNull private final int[] mGlassAt = new int[2];

    public GroundStripView(@NonNull Context context) {
        this(context, null);
    }

    public GroundStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mPaint.setStyle(Paint.Style.FILL);
        mGlassPaint.setStyle(Paint.Style.FILL);
        mFeatherPx = FEATHER_DP * context.getResources().getDisplayMetrics().density;
    }

    /** The view whose left edge is the bands' origin: the pane wall, one page wide. */
    public void setWall(@Nullable View wall) {
        mWall = wall;
    }

    /** The screen rect to paint (the window's); empty paints this view's bounds. */
    public void setCover(@NonNull Rect screenRect) {
        if (mCover.equals(screenRect)) return;
        mCover.set(screenRect);
        invalidate();
    }

    /** Sets every stop's ground, in wall order, and the one the wall rests on. */
    public void setGrounds(@NonNull int[] grounds, int count, int current) {
        mStops = Math.max(0, Math.min(MAX_STOPS, count));
        System.arraycopy(grounds, 0, mGrounds, 0, mStops);
        mCurrent = current;
        recompute();
    }

    /** Where the wall is: one page's width and the signed offset from the current page's rest. */
    public void setPosition(int current, float stridePx, float offsetPx) {
        if (current == mCurrent && stridePx == mStride && offsetPx == mOffsetPx) return;
        mCurrent = current;
        mStride = stridePx;
        mOffsetPx = offsetPx;
        recompute();
    }

    private void recompute() {
        mBands.set(mGrounds, mStops, mCurrent, mStride, mOffsetPx, mFeatherPx);
        invalidate();
        // Prototype #283: the glass over the strip draws the bands too, so it redraws when they
        // move. Equal grounds give one band that never moves: a crossing between them costs none.
        if (!mBands.sameAs(mRidden)) {
            mRidden.copyFrom(mBands);
            for (View rider : mRiders) {
                if (rider != null) rider.invalidate();
            }
        }
    }

    /**
     * Prototype #283: {@code glass} draws the ground under it ({@link #drawUnder}) and is
     * redrawn when the bands move. Idempotent; at most {@link #MAX_RIDERS}.
     */
    public void addRider(@NonNull View glass) {
        int free = -1;
        for (int i = 0; i < MAX_RIDERS; i++) {
            if (mRiders[i] == glass) return;
            if (free < 0 && mRiders[i] == null) free = i;
        }
        if (free >= 0) mRiders[free] = glass;
    }

    public void removeRider(@NonNull View glass) {
        for (int i = 0; i < MAX_RIDERS; i++) {
            if (mRiders[i] == glass) mRiders[i] = null;
        }
    }

    /** Whether the strip paints a ground now: shown, with bands. */
    public boolean paintsGround() {
        return getVisibility() == View.VISIBLE && mBands.count() > 0;
    }

    /**
     * Prototype #283: draws the bands into {@code glass}'s canvas over its own rect, each where it
     * stands on screen, so the glass shows the ground of the page beneath it. Allocates nothing.
     *
     * @return the glass's left edge in the wall's px (for the diagnostic), NaN when nothing drew
     */
    public float drawUnder(@NonNull Canvas canvas, @NonNull View glass) {
        int count = mBands.count();
        if (count == 0 || getVisibility() != View.VISIBLE) return Float.NaN;
        glass.getLocationOnScreen(mGlassAt);
        float glassLeftInWall = mGlassAt[0] - wallLeftOnScreen();
        float width = glass.getWidth();
        float height = glass.getHeight();
        for (int i = 0; i < count; i++) {
            int color = mBands.color(i);
            if ((color >>> 24) == 0) continue;
            float x0 = GlassGround.spanLeft(mBands, i, glassLeftInWall, width);
            float x1 = GlassGround.spanRight(mBands, i, glassLeftInWall, width);
            if (x1 <= x0) continue;
            mGlassPaint.setColor(color);
            canvas.drawRect(x0, 0f, x1, height, mGlassPaint);
        }
        return glassLeftInWall;
    }

    /** The ground at {@code wallX} in the wall's px, as {@link #drawUnder} draws it there. */
    public int groundAtWallX(float wallX) {
        return GlassGround.groundAt(mBands, wallX);
    }

    /** How many bands the strip holds now: 1 at rest, more while a seam is in view. */
    public int bandCount() {
        return mBands.count();
    }

    /** Whether any two stops' grounds differ: a seam then travels while the wall moves. */
    @Override
    public boolean groundsDiffer() {
        for (int i = 1; i < mStops; i++) {
            if (mGrounds[i] != mGrounds[0]) return true;
        }
        return false;
    }

    /**
     * The ground under {@code screenRect}: the colour at its centre, as {@link GroundBands}
     * documents. Transparent before the strip has a position.
     */
    public int groundUnder(@NonNull Rect screenRect) {
        if (mBands.count() == 0) return 0;
        float wallLeft = wallLeftOnScreen();
        return mBands.groundUnder(screenRect.left - wallLeft, screenRect.right - wallLeft);
    }

    private float wallLeftOnScreen() {
        View wall = mWall;
        if (wall == null) return 0f;
        wall.getLocationOnScreen(mWallAt);
        return mWallAt[0];
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int count = mBands.count();
        if (count == 0) return;
        getLocationOnScreen(mOwn);
        float wallLeft = wallLeftOnScreen() - mOwn[0];
        boolean covered = !mCover.isEmpty();
        float left = covered ? mCover.left - mOwn[0] : 0f;
        float right = covered ? mCover.right - mOwn[0] : getWidth();
        float top = covered ? mCover.top - mOwn[1] : 0f;
        float bottom = covered ? mCover.bottom - mOwn[1] : getHeight();
        for (int i = 0; i < count; i++) {
            int color = mBands.color(i);
            if ((color >>> 24) == 0) continue;
            float x0 = wallLeft + mBands.start(i);
            float x1 = wallLeft + mBands.end(i);
            if (i == 0) x0 = Math.min(left, x0);
            if (i == count - 1) x1 = Math.max(right, x1);
            mPaint.setColor(color);
            canvas.drawRect(x0, top, x1, bottom, mPaint);
        }
    }
}
