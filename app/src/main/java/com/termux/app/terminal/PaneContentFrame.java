package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.view.TerminalView;

/**
 * One pane's frame: the single owner of the shape the pane wears and of the clearance that shape
 * owes the terminal inside it.
 *
 * <p>Every way a pane is edged rounds its corners — the glass slab's radius, a float's card, the
 * focus stroke's own arc — while the terminal fills the frame corner to corner with rectangular
 * cell backgrounds. Left flush, the first and last column of the top and bottom rows sit under the
 * arc, which is how a prompt that paints its own background to the very edge came out clipped.
 *
 * <p>So the shape and its clearance are set together, from one radius: the glass keeps filling the
 * whole slab and the terminal is laid out inside the arc's depth. The clearance is spent as the
 * child's margin rather than as this frame's padding, because the frame's other child is the glass
 * backdrop and it must still reach the corners the terminal now stays out of.
 *
 * <p>The rim a pane wears is part of the same geometry. Its stroke is painted just inside the
 * outline, so the band this frame fills behind the terminal stops at the stroke's inner edge — the
 * fill and the rim are both anti-aliased against the same arc, and a fill that ran out to the
 * outline showed through as a dark hairline outside the rim wherever the two edges' coverage
 * disagreed — and along a straight edge the stroke, not the arc, is what the text is held off.
 */
public class PaneContentFrame extends FrameLayout implements TerminalView.PaddingFillListener {

    /** How far off the rim's inner edge the text is kept along a straight edge. */
    private static final float EDGE_GAP_DP = 2f;

    private float mRequestedRadiusPx;
    private boolean mClipToShape;
    /** Width of the border drawn on this frame's foreground, 0 while it wears none. */
    private float mRimStrokePx;
    private View mContent;
    /** Set on a DOWN that landed in the clearance, so the rest of that gesture follows it. */
    private boolean mForwardingToContent;

    /** Reused across frames; painting the band allocates nothing once this exists. */
    private final Paint mPaddingFillPaint = new Paint();
    /** The rim's inner edge, re-built only when the size, radius or stroke actually changes, so
     *  clipping the band to it costs nothing on the frames it does not. */
    private final Path mBandClipPath = new Path();
    private boolean mBandClipPathDirty = true;

    /** Re-capped on every ask: a divider drag resizes the frame without re-dressing the pane. */
    private final ViewOutlineProvider mShapeOutline = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                PaneShape.radiusForBounds(mRequestedRadiusPx, view.getWidth(), view.getHeight()));
        }
    };

    public PaneContentFrame(Context context) {
        super(context);
        initPaddingFillPaint();
    }

    public PaneContentFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaddingFillPaint();
    }

    public PaneContentFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaddingFillPaint();
    }

    /** Off by default already, but stated explicitly: this band's rects share edges — with each
     *  other and with the terminal's own in-view slack — that are snapped to whole device pixels,
     *  and anti-aliasing would blend a translucent hairline into exactly those shared edges. */
    private void initPaddingFillPaint() {
        mPaddingFillPaint.setAntiAlias(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.terminal_view);
        wirePaddingFillListener();
    }

    /**
     * The child that owes the shape its clearance. A terminal pane finds its own on inflation; a
     * page whose content arrives later — the wall's widget grid — names it here.
     */
    public void setPaneContent(@Nullable View content) {
        if (mContent == content) return;
        if (mContent instanceof TerminalView)
            ((TerminalView) mContent).setPaddingFillListener(null);
        mContent = content;
        wirePaddingFillListener();
        requestLayout();
    }

    /** Tell the terminal child, if this is one, to notify this frame when its edge colours move —
     *  a child's own invalidate does not re-record this frame's display list on its own. */
    private void wirePaddingFillListener() {
        if (mContent instanceof TerminalView)
            ((TerminalView) mContent).setPaddingFillListener(this);
    }

    /** {@link TerminalView.PaddingFillListener}: the terminal's edge colours moved since last
     *  frame, so the band this frame paints around it needs to be repainted too. */
    @Override
    public void onPaddingFillColorsChanged() {
        invalidate();
    }

    /**
     * The shape this pane is drawn with.
     *
     * @param requestedRadiusPx the radius asked for against a full-height pane; the live size caps
     *     it (see {@link PaneShape#radiusForBounds}). 0 is a square pane, which clears nothing.
     * @param clipToShape whether this frame clips to that shape. A float clips on its own wrapper
     *     and a plain focus stroke does not clip at all, but both round the same corners and so
     *     owe the terminal the same clearance.
     */
    public void setPaneShape(float requestedRadiusPx, boolean clipToShape) {
        if (mRequestedRadiusPx == requestedRadiusPx && mClipToShape == clipToShape)
            return;
        mRequestedRadiusPx = requestedRadiusPx;
        mClipToShape = clipToShape;
        setOutlineProvider(clipToShape ? mShapeOutline : ViewOutlineProvider.BOUNDS);
        setClipToOutline(clipToShape);
        invalidateOutline();
        mBandClipPathDirty = true;
        requestLayout();
    }

    /**
     * The width of the border this frame's foreground draws, or 0 while it draws none. The band
     * behind the terminal stops at that border's inner edge, and along a straight edge the text is
     * held this far plus a hair off the frame — a square pane with a rim would otherwise lay its
     * first column under the stroke, since a square corner owes it no arc clearance.
     */
    public void setRimStrokePx(float strokePx) {
        float stroke = Math.max(0f, strokePx);
        if (mRimStrokePx == stroke)
            return;
        mRimStrokePx = stroke;
        mBandClipPathDirty = true;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBandClipPathDirty = true;
    }

    /**
     * The rim's inner edge, in this frame's own bounds: the pane's rounded outline inset by the
     * stroke, at the radius the stroke's inner side turns ({@code GlassRimRenderer} strokes the
     * outline's radius less half its width, centred half its width in, so its inner edge is the
     * full stroke in at the radius less the full stroke). With no rim it is the outline itself —
     * the same shape the glass clips to, whether or not this frame is clipping. Nothing painted
     * behind the terminal may poke past it.
     */
    private Path getBandClipPath() {
        if (mBandClipPathDirty) {
            mBandClipPath.reset();
            float radius = PaneShape.radiusForBounds(mRequestedRadiusPx, getWidth(), getHeight());
            float inset = mRimStrokePx;
            float innerRadius = Math.max(0f, radius - inset);
            mBandClipPath.addRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                innerRadius, innerRadius, Path.Direction.CW);
            mBandClipPathDirty = false;
        }
        return mBandClipPath;
    }

    /**
     * Paint the arc's own clearance — the margin {@link #onMeasure} held the terminal off the edge
     * by — with the same edge colours the terminal itself extends into its in-view slack. Drawn
     * just before the terminal child, so it lands over the glass backdrop and under the grid.
     *
     * <p>A plain shell leaves every edge colour transparent (its default background is never
     * painted), so this draws nothing and the glass or wallpaper behind the pane keeps showing.
     *
     * @return whether a band was painted, which is when the grid's own corners have to be held to
     *     the same clip (see {@link #drawChild})
     */
    private boolean drawPaddingFillBand(Canvas canvas) {
        if (!(mContent instanceof TerminalView)) return false;
        TerminalView terminal = (TerminalView) mContent;
        if (!terminal.isPaddingFillEnabled()) return false;
        int columns = terminal.getEdgeColumnCount();
        int rows = terminal.getEdgeRowCount();
        if (columns <= 0 || rows <= 0) return false;
        int contentLeft = mContent.getLeft();
        int contentTop = mContent.getTop();
        int contentRight = mContent.getRight();
        int contentBottom = mContent.getBottom();
        // A pane too small to wear any radius, or a content view that already reaches every edge,
        // has no margin at all to fill.
        if (contentLeft <= 0 && contentTop <= 0 && contentRight >= getWidth() && contentBottom >= getHeight())
            return false;

        int save = canvas.save();
        canvas.clipPath(getBandClipPath());
        // Top and bottom bands: one rect per run of equal-coloured columns, directly above/below
        // where those columns' own cells sit, so a coloured status bar or a solid full-screen app
        // reaches the pane's border. Edges come from getPaddingColumnLeft, the same rounding
        // TerminalRenderer#drawCellRect snaps a cell's own left/right edge to, so a band's inner
        // edge meets the grid with no gap and its outer neighbours meet each other the same way;
        // coalescing equal-coloured runs means two same-coloured columns share no internal edge
        // at all, however that rounding falls.
        int c = 0;
        while (c < columns) {
            int topColor = terminal.getEdgeColumnColorTop(c);
            int bottomColor = terminal.getEdgeColumnColorBottom(c);
            int runEnd = c + 1;
            while (runEnd < columns && terminal.getEdgeColumnColorTop(runEnd) == topColor
                && terminal.getEdgeColumnColorBottom(runEnd) == bottomColor) runEnd++;
            float left = contentLeft + terminal.getPaddingColumnLeft(c);
            float right = contentLeft + terminal.getPaddingColumnLeft(runEnd);
            fillRect(canvas, left, 0f, right, contentTop, topColor);
            fillRect(canvas, left, contentBottom, right, getHeight(), bottomColor);
            c = runEnd;
        }
        // Left and right bands: one rect per run of equal-coloured rows, continuing that row's own
        // edge colour out to the view's flush side, with the same run-coalescing and edge-rounding.
        int r = 0;
        while (r < rows) {
            int leftColor = terminal.getEdgeRowColorLeft(r);
            int rightColor = terminal.getEdgeRowColorRight(r);
            int runEnd = r + 1;
            while (runEnd < rows && terminal.getEdgeRowColorLeft(runEnd) == leftColor
                && terminal.getEdgeRowColorRight(runEnd) == rightColor) runEnd++;
            float top = contentTop + terminal.getPaddingRowTop(r);
            float bottom = contentTop + terminal.getPaddingRowTop(runEnd);
            fillRect(canvas, 0f, top, contentLeft, bottom, leftColor);
            fillRect(canvas, contentRight, top, getWidth(), bottom, rightColor);
            r = runEnd;
        }
        // The four corners: the small squares the row/column bands above do not reach, each taking
        // the colour of the cell nearest that corner.
        fillRect(canvas, 0f, 0f, contentLeft, contentTop, terminal.getEdgeColumnColorTop(0));
        fillRect(canvas, contentRight, 0f, getWidth(), contentTop, terminal.getEdgeColumnColorTop(columns - 1));
        fillRect(canvas, 0f, contentBottom, contentLeft, getHeight(), terminal.getEdgeColumnColorBottom(0));
        fillRect(canvas, contentRight, contentBottom, getWidth(), getHeight(), terminal.getEdgeColumnColorBottom(columns - 1));
        canvas.restoreToCount(save);
        return true;
    }

    private void fillRect(Canvas canvas, float left, float top, float right, float bottom, int color) {
        if (color == 0) return;
        mPaddingFillPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, mPaddingFillPaint);
    }

    /**
     * Refresh the terminal's edge colours before anything this frame draws this pass reads them —
     * {@link #drawChild} below paints the band from them for the terminal child, and doing this
     * first is what keeps band and grid always the same frame's colours. Without it the band, drawn
     * before the terminal child's own {@code onDraw} had a chance to recompute them, painted with
     * whatever the last frame left behind; a change that arrived while this pane was off screen (an
     * alpha-faded wall page keeps drawing, so this still ran, but nothing invalidated it to catch
     * up) then showed the new grid over the old band for one more frame after the pane returned.
     *
     * <p>{@link com.termux.view.TerminalView#computeEdgeColorsIfEnabled} is allocation-free and
     * O(rows + columns); the terminal's own {@code onDraw} calls it again right after, redundantly
     * but just as cheaply, since nothing about padding fill depends on draw order to be correct on
     * its own account.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mContent instanceof TerminalView) ((TerminalView) mContent).computeEdgeColorsIfEnabled();
        super.dispatchDraw(canvas);
    }

    /**
     * Draw the padding-fill band right before the terminal child is drawn, so it lands after the
     * glass backdrop (drawn in an earlier call, for the child added first) and under the grid.
     *
     * <p>While a band is painted the grid is drawn under the band's own clip too. The terminal's
     * box is inset to clear the outline's arc, not the stroke's inner arc, so a corner cell's own
     * background can still reach a couple of pixels under the rim's inner half on the diagonal —
     * fill and grid are one surface there, and cutting both at the same edge is what keeps that
     * corner from being the one place the rim reads darker.
     */
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child != mContent || !drawPaddingFillBand(canvas))
            return super.drawChild(canvas, child, drawingTime);
        int save = canvas.save();
        canvas.clipPath(getBandClipPath());
        try {
            return super.drawChild(canvas, child, drawingTime);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /** Where the terminal child's first row of cells starts below its own top edge, if it is one. */
    private float contentHeadroomPx() {
        return mContent instanceof TerminalView ? ((TerminalView) mContent).getFirstRowTopPx() : 0f;
    }

    /**
     * Set the terminal's margins from the size this frame is about to take, before the children are
     * measured against it — so the pane lays out once at its cleared size and the PTY is told one
     * size, not the flush one and then the inset one.
     *
     * <p>The sides and the bottom get the arc's full clearance ({@link PaneShape#contentInsetPx}):
     * the terminal bottom-anchors its grid ({@code TerminalView.getVerticalContentOffset()}), so
     * its last row ends flush with the view's bottom edge and the last row's cells are what meet
     * the bottom arcs, just as the first and last columns' cells meet the side arcs at whatever
     * height the grid's centring leaves them. The top gets less: the first row's cells start a
     * fixed headroom below the view's top ({@code TerminalView.getFirstRowTopPx()}, the renderer's
     * ascent allowance), so that much of the top's clearance is already paid, and the margin only
     * makes up what the arc still needs above it ({@link PaneShape#edgeInsetPx}). The integral-row
     * leftover on the normal buffer sits above the first row too, and only adds to that.
     *
     * <p>Along a straight edge none of this is what holds the text off the border — the rim's
     * stroke is, plus a hair — so every margin is floored at that; the floor only ever bites at a
     * radius too small for its own arc to clear the stroke.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mContent != null && mContent.getLayoutParams() instanceof MarginLayoutParams) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            float radius = PaneShape.radiusForBounds(mRequestedRadiusPx, width, height);
            float edgeFloor = mRimStrokePx > 0f
                ? mRimStrokePx + EDGE_GAP_DP * getResources().getDisplayMetrics().density : 0f;
            int side = Math.max(PaneShape.contentInsetPx(radius), (int) Math.ceil(edgeFloor));
            int top = side;
            if (mContent instanceof TerminalView) {
                float headroom = contentHeadroomPx();
                top = Math.max(PaneShape.edgeInsetPx(radius, side, headroom),
                    (int) Math.ceil(edgeFloor - headroom));
                top = Math.max(0, top);
            }
            MarginLayoutParams params = (MarginLayoutParams) mContent.getLayoutParams();
            if (params.leftMargin != side || params.topMargin != top
                || params.rightMargin != side || params.bottomMargin != side) {
                params.leftMargin = side;
                params.topMargin = top;
                params.rightMargin = side;
                params.bottomMargin = side;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Hand a gesture that starts in the clearance to the content inside it.
     *
     * <p>The band the margin leaves belongs to no view — this frame is not clickable and the
     * terminal is laid out inside it — so a press on the pane's outermost pixels reached nothing at
     * all. It reads as terminal, being inside the pane's own edge, so the terminal is given it.
     *
     * <p>The coordinates are carried straight over into the child's space and left out of range on
     * purpose: negative, or past the far edge, is how the terminal hears "the first column" and
     * "the last row" ({@code TerminalEmulator.sendMouseEvent} pins a mouse report to the edge cell,
     * and {@code TerminalView} floors its own mapping the same way). This is already what happens
     * in a pane corner, where the interaction overlay forwards by the same offset-a-copy route;
     * {@link android.view.TouchDelegate} could not do it, since it re-centres the event on the
     * view it forwards to.
     *
     * <p>Only a gesture that <em>starts</em> in the clearance: one that starts on the child is
     * dispatched normally and reaches it exactly once. The corner overlay sits above the panes and
     * consumes the gestures it claims, so this never sees those either.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN)
            mForwardingToContent = isInClearance(event.getX(), event.getY());
        if (!mForwardingToContent)
            return super.dispatchTouchEvent(event);
        MotionEvent copy = MotionEvent.obtain(event);
        copy.offsetLocation(getScrollX() - mContent.getLeft(), getScrollY() - mContent.getTop());
        try {
            mContent.dispatchTouchEvent(copy);
        } finally {
            copy.recycle();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                mForwardingToContent = false;
        }
        // Kept whatever the terminal made of it: the clearance is the pane's own edge, and a press
        // there must not fall through to whatever the pane is sitting on.
        return true;
    }

    /** Whether a point inside this frame lies in the band the content is held off the edge by. */
    private boolean isInClearance(float x, float y) {
        if (mContent == null || mContent.getParent() != this
            || mContent.getVisibility() == GONE)
            return false;
        return x < mContent.getLeft() || x >= mContent.getRight()
            || y < mContent.getTop() || y >= mContent.getBottom();
    }
}
