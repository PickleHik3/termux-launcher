package com.termux.app.place;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.List;

/**
 * The one host every movable bar stands in: a stack on a screen edge, holding its bars in the order
 * {@link EdgeStackPolicy#stack} gives them. Four of these — one per edge — are all the chrome hosts
 * the launcher has; a bar is a single view that gets re-parented from one stack to another rather
 * than a host per place it might go.
 *
 * <p>The edge decides the axis: a stack on a side runs across the screen, so its bars stand beside
 * one another and it is a horizontal row of columns; a stack on the top or the bottom runs down, so
 * its bars lie on one another and it is a vertical column of rows.
 *
 * <p>{@link #setStack} is given the bars <em>outermost first</em>, the way the policy counts them —
 * 0 is the band against the glass. On the top and the left edges that is also the first child, but
 * on the bottom and the right the outermost band is the <em>last</em> one a {@link LinearLayout}
 * lays out, so the order is reversed on the way in. Callers never have to know which.
 *
 * <p>A stack holds nothing but bands: every bar in it is a plain band of its own thickness, and the
 * edge's whole reach is the display cutout — kept once by the padded content root every stack now
 * stands inside — plus those bands, which is exactly what {@link EdgeStackPolicy#contentInsets}
 * answers.
 *
 * <p>A stack that is one sheet of glass also draws the hairlines between its bands
 * ({@link #setSeparatorCount}), over the boundaries it laid its children out on rather than as
 * bands of their own, so a separator costs the stack no height.
 */
public class EdgeStackView extends LinearLayout {

    @NonNull private Edge mEdge = Edge.TOP;

    /** How many hairlines to draw, from {@link EdgeStackPolicy#separatorsFor}; 0 draws none. */
    private int mSeparatorCount;
    private int mSeparatorColor = Color.TRANSPARENT;
    private int mSeparatorThicknessPx;
    private int mSeparatorInsetPx;
    @Nullable private Paint mSeparatorPaint;
    /** The draw pass's own buffer for {@link #separatorCenters}, so a frame allocates nothing. */
    @Nullable private int[] mSeparatorScratch;

    public EdgeStackView(@NonNull Context context) {
        this(context, null);
    }

    public EdgeStackView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Edge edge = Edge.TOP;
        if (attrs != null) {
            TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.EdgeStackView);
            try {
                int ordinal = values.getInt(R.styleable.EdgeStackView_edgeStackEdge,
                    Edge.TOP.ordinal());
                Edge[] edges = Edge.values();
                if (ordinal >= 0 && ordinal < edges.length) edge = edges[ordinal];
            } finally {
                values.recycle();
            }
        }
        setEdge(edge);
    }

    /** The edge this stack holds. */
    @NonNull
    public Edge getEdge() {
        return mEdge;
    }

    /** Moves the whole stack to another edge, which turns it and reverses the order it draws in. */
    public void setEdge(@NonNull Edge edge) {
        mEdge = edge;
        setOrientation(edge.isOnSide() ? HORIZONTAL : VERTICAL);
    }

    /**
     * Stands these bars on this edge, outermost first, adopting any that are still children of
     * another stack. Nothing that is not in the list is taken out: every bar belongs to exactly one
     * edge, so one pass over all four stacks leaves each of them holding only its own.
     *
     * @return whether anything actually moved, which the crops cut against these views have to be
     *     told about
     */
    public boolean setStack(@NonNull List<View> outermostFirst) {
        boolean reversed = mEdge == Edge.BOTTOM || mEdge == Edge.RIGHT;
        int count = outermostFirst.size();
        boolean moved = false;
        for (int position = 0; position < count; position++) {
            View bar = outermostFirst.get(reversed ? count - 1 - position : position);
            if (bar == null) continue;
            ViewGroup parent = bar.getParent() instanceof ViewGroup
                ? (ViewGroup) bar.getParent() : null;
            if (parent == this && indexOfChild(bar) == position) continue;
            if (parent != null) parent.removeView(bar);
            addView(bar, Math.min(position, getChildCount()));
            moved = true;
        }
        return moved;
    }

    /**
     * How many hairlines this stack draws between its bands — {@link
     * EdgeStackPolicy#separatorsFor}'s answer, which is one per gap and none at either end. A
     * stack whose bands each carry their own glass is left at zero.
     */
    public void setSeparatorCount(int count) {
        int wanted = Math.max(0, count);
        if (mSeparatorCount == wanted) return;
        mSeparatorCount = wanted;
        invalidate();
    }

    /** The count last given, for the arrangement tests and for a caller re-asking. */
    public int getSeparatorCount() {
        return mSeparatorCount;
    }

    /**
     * The hairline's look: the dock's own outline colour at the material's alpha, a hairline thick,
     * and held off the sheet's sides by the same inset the bands themselves keep.
     */
    public void setSeparatorAppearance(int color, int thicknessPx, int insetPx) {
        int thickness = Math.max(0, thicknessPx);
        int inset = Math.max(0, insetPx);
        if (mSeparatorColor == color && mSeparatorThicknessPx == thickness
            && mSeparatorInsetPx == inset) return;
        mSeparatorColor = color;
        mSeparatorThicknessPx = thickness;
        mSeparatorInsetPx = inset;
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        drawSeparators(canvas);
    }

    /**
     * One hairline in each gap between two bands that are actually drawn, walking outwards from the
     * first child. A band that is gone or has collapsed to nothing is not a band: two hairlines
     * would land on the same pixel and the stack would look like it had a rim.
     */
    private void drawSeparators(@NonNull Canvas canvas) {
        if (mSeparatorCount <= 0 || mSeparatorThicknessPx <= 0
            || Color.alpha(mSeparatorColor) == 0) return;
        boolean column = getOrientation() == HORIZONTAL;
        if (mSeparatorPaint == null) {
            mSeparatorPaint = new Paint();
            mSeparatorPaint.setStyle(Paint.Style.FILL);
        }
        mSeparatorPaint.setColor(mSeparatorColor);
        if (mSeparatorScratch == null || mSeparatorScratch.length < mSeparatorCount)
            mSeparatorScratch = new int[mSeparatorCount];
        int count = fillSeparatorCenters(mSeparatorScratch);
        for (int at = 0; at < count; at++) {
            int gap = mSeparatorScratch[at];
            int from = gap - (mSeparatorThicknessPx / 2);
            int to = from + mSeparatorThicknessPx;
            if (column) {
                canvas.drawRect(from, mSeparatorInsetPx, to,
                    getHeight() - mSeparatorInsetPx, mSeparatorPaint);
            } else {
                canvas.drawRect(mSeparatorInsetPx, from,
                    getWidth() - mSeparatorInsetPx, to, mSeparatorPaint);
            }
        }
    }

    /**
     * Where each hairline sits along the stack's axis, in the stack's own coordinates, walking
     * outwards from the first child: <b>the middle of the visible gap between the two bands'
     * content</b>, not the boundary their bounds happen to share.
     *
     * <p>The bands of one sheet do not carry the same air. The apps row keeps its icons off its own
     * rims and the extra keys do not, so a line drawn on the child boundary sat hard against the
     * keys and a comfortable distance from the icons — a seam that looked like it belonged to one
     * of the two rows. Splitting the gap is the one rule that gives every pair the same answer
     * whatever air each side happens to keep, and it moves nothing: a band is the size it was.
     *
     * <p>Two bands with no air between them leave no gap to split, so the line lands exactly where
     * it always did. That is every stack the shipped screen draws, which is why the default dock is
     * unchanged to the pixel.
     */
    @NonNull
    public int[] separatorCenters() {
        int[] centers = new int[Math.max(0, mSeparatorCount)];
        int drawn = fillSeparatorCenters(centers);
        return drawn == centers.length ? centers : java.util.Arrays.copyOf(centers, drawn);
    }

    /** {@link #separatorCenters} into a buffer the draw pass keeps, so a frame allocates nothing. */
    private int fillSeparatorCenters(@NonNull int[] out) {
        boolean column = getOrientation() == HORIZONTAL;
        int wanted = Math.min(out.length, Math.max(0, mSeparatorCount));
        View previous = null;
        int drawn = 0;
        for (int index = 0; index < getChildCount() && drawn < wanted; index++) {
            View child = getChildAt(index);
            if (child.getVisibility() == GONE) continue;
            if ((column ? child.getWidth() : child.getHeight()) <= 0) continue;
            if (previous != null) {
                out[drawn++] = (contentEnd(previous, column) + contentStart(child, column)) / 2;
            }
            previous = child;
        }
        return drawn;
    }

    /**
     * Where a band's content begins along the axis, in the stack's coordinates: the band's own
     * padding, or — a band being a host and the bar inside it being the content — the air that bar
     * keeps at the same end. One level, and the same level for every band, so the rule never has to
     * know which band it is looking at.
     */
    private static int contentStart(@NonNull View band, boolean column) {
        int start = column ? band.getLeft() : band.getTop();
        int inset = column ? band.getPaddingLeft() : band.getPaddingTop();
        View bar = drawnChildAt(band, column, true);
        if (bar != null) {
            inset = (column ? bar.getLeft() : bar.getTop())
                + (column ? bar.getPaddingLeft() : bar.getPaddingTop());
        }
        int extent = column ? band.getWidth() : band.getHeight();
        return start + Math.max(0, Math.min(inset, extent));
    }

    /** {@link #contentStart}'s answer at the band's other end. */
    private static int contentEnd(@NonNull View band, boolean column) {
        int end = column ? band.getRight() : band.getBottom();
        int extent = column ? band.getWidth() : band.getHeight();
        int inset = column ? band.getPaddingRight() : band.getPaddingBottom();
        View bar = drawnChildAt(band, column, false);
        if (bar != null) {
            inset = extent - (column ? bar.getRight() : bar.getBottom())
                + (column ? bar.getPaddingRight() : bar.getPaddingBottom());
        }
        return end - Math.max(0, Math.min(inset, extent));
    }

    /**
     * The bar standing at one end of a band: the drawn child reaching nearest that end. A child
     * that is gone or has collapsed to nothing is not a bar — the apps row's own host holds a
     * collapsed pager whenever the row stands somewhere else.
     */
    @Nullable
    private static View drawnChildAt(@NonNull View band, boolean column, boolean atStart) {
        if (!(band instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) band;
        View found = null;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child.getVisibility() == GONE) continue;
            if ((column ? child.getWidth() : child.getHeight()) <= 0) continue;
            if (found == null) {
                found = child;
                continue;
            }
            if (atStart) {
                int mine = column ? child.getLeft() : child.getTop();
                int best = column ? found.getLeft() : found.getTop();
                if (mine < best) found = child;
            } else {
                int mine = column ? child.getRight() : child.getBottom();
                int best = column ? found.getRight() : found.getBottom();
                if (mine > best) found = child;
            }
        }
        return found;
    }
}
