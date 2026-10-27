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
 */
public final class GroundStripView extends View {

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

    public GroundStripView(@NonNull Context context) {
        this(context, null);
    }

    public GroundStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mPaint.setStyle(Paint.Style.FILL);
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
