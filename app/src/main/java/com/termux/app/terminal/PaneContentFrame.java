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
 */
public class PaneContentFrame extends FrameLayout implements TerminalView.PaddingFillListener {

    private float mRequestedRadiusPx;
    private boolean mClipToShape;
    private View mContent;
    /** Set on a DOWN that landed in the clearance, so the rest of that gesture follows it. */
    private boolean mForwardingToContent;

    /** Reused across frames; painting the band allocates nothing once this exists. */
    private final Paint mPaddingFillPaint = new Paint();
    /** The pane's own rounded outline, re-built only when the size or radius actually changes, so
     *  clipping the band to it costs nothing on the frames it does not. */
    private final Path mShapePath = new Path();
    private boolean mShapePathDirty = true;

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
    }

    public PaneContentFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PaneContentFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
        mShapePathDirty = true;
        requestLayout();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mShapePathDirty = true;
    }

    /** The pane's own rounded-rect outline, in this frame's own bounds — the same shape the glass
     *  clips to, whether or not this frame itself is clipping. The band must never poke past it. */
    private Path getPaddingFillClipPath() {
        if (mShapePathDirty) {
            mShapePath.reset();
            float radius = PaneShape.radiusForBounds(mRequestedRadiusPx, getWidth(), getHeight());
            mShapePath.addRoundRect(0f, 0f, getWidth(), getHeight(), radius, radius, Path.Direction.CW);
            mShapePathDirty = false;
        }
        return mShapePath;
    }

    /**
     * Paint the arc's own clearance — the margin {@link #onMeasure} held the terminal off the edge
     * by — with the same edge colours the terminal itself extends into its in-view slack. Drawn
     * just before the terminal child, so it lands over the glass backdrop and under the grid.
     *
     * <p>A plain shell leaves every edge colour transparent (its default background is never
     * painted), so this draws nothing and the glass or wallpaper behind the pane keeps showing.
     */
    private void drawPaddingFillBand(Canvas canvas) {
        if (!(mContent instanceof TerminalView)) return;
        TerminalView terminal = (TerminalView) mContent;
        if (!terminal.isPaddingFillEnabled()) return;
        int columns = terminal.getEdgeColumnCount();
        int rows = terminal.getEdgeRowCount();
        if (columns <= 0 || rows <= 0) return;
        int contentLeft = mContent.getLeft();
        int contentTop = mContent.getTop();
        int contentRight = mContent.getRight();
        int contentBottom = mContent.getBottom();
        // A pane too small to wear any radius, or a content view that already reaches every edge,
        // has no margin at all to fill.
        if (contentLeft <= 0 && contentTop <= 0 && contentRight >= getWidth() && contentBottom >= getHeight())
            return;

        int save = canvas.save();
        canvas.clipPath(getPaddingFillClipPath());
        float columnWidth = terminal.getPaddingColumnWidth();
        float rowHeight = terminal.getPaddingRowHeight();
        // Top and bottom bands: one rect per column, directly above/below where that column's own
        // cells sit, so a coloured status bar or a solid full-screen app reaches the pane's border.
        for (int c = 0; c < columns; c++) {
            float left = contentLeft + terminal.getPaddingColumnLeft(c);
            float right = left + columnWidth;
            fillRect(canvas, left, 0f, right, contentTop, terminal.getEdgeColumnColorTop(c));
            fillRect(canvas, left, contentBottom, right, getHeight(), terminal.getEdgeColumnColorBottom(c));
        }
        // Left and right bands: one rect per row, continuing that row's own edge colour out to the
        // view's flush side.
        for (int r = 0; r < rows; r++) {
            float top = contentTop + terminal.getPaddingRowTop(r);
            float bottom = top + rowHeight;
            fillRect(canvas, 0f, top, contentLeft, bottom, terminal.getEdgeRowColorLeft(r));
            fillRect(canvas, contentRight, top, getWidth(), bottom, terminal.getEdgeRowColorRight(r));
        }
        // The four corners: the small squares the row/column bands above do not reach, each taking
        // the colour of the cell nearest that corner.
        fillRect(canvas, 0f, 0f, contentLeft, contentTop, terminal.getEdgeColumnColorTop(0));
        fillRect(canvas, contentRight, 0f, getWidth(), contentTop, terminal.getEdgeColumnColorTop(columns - 1));
        fillRect(canvas, 0f, contentBottom, contentLeft, getHeight(), terminal.getEdgeColumnColorBottom(0));
        fillRect(canvas, contentRight, contentBottom, getWidth(), getHeight(), terminal.getEdgeColumnColorBottom(columns - 1));
        canvas.restoreToCount(save);
    }

    private void fillRect(Canvas canvas, float left, float top, float right, float bottom, int color) {
        if (color == 0) return;
        mPaddingFillPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, mPaddingFillPaint);
    }

    /**
     * Draw the padding-fill band right before the terminal child is drawn, so it lands after the
     * glass backdrop (drawn in an earlier call, for the child added first) and under the grid.
     */
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == mContent)
            drawPaddingFillBand(canvas);
        return super.drawChild(canvas, child, drawingTime);
    }

    /**
     * Set the terminal's margins from the size this frame is about to take, before the children are
     * measured against it — so the pane lays out once at its cleared size and the PTY is told one
     * size, not the flush one and then the inset one.
     *
     * <p>The margin is the same on all four edges: the terminal bottom-anchors its grid
     * ({@code TerminalView.getVerticalContentOffset()}), so its last row ends flush with the
     * view's bottom edge and this inset is exactly the arc clearance there, just as on the sides.
     * The integral-row leftover sits above the first row instead, where it reads as headroom.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mContent != null && mContent.getLayoutParams() instanceof MarginLayoutParams) {
            int inset = PaneShape.contentInsetForBounds(mRequestedRadiusPx,
                MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            MarginLayoutParams params = (MarginLayoutParams) mContent.getLayoutParams();
            if (params.leftMargin != inset || params.topMargin != inset
                || params.rightMargin != inset || params.bottomMargin != inset) {
                params.leftMargin = inset;
                params.topMargin = inset;
                params.rightMargin = inset;
                params.bottomMargin = inset;
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
