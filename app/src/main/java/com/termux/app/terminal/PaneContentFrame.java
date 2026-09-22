package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.termux.R;

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
public class PaneContentFrame extends FrameLayout {

    private float mRequestedRadiusPx;
    private boolean mClipToShape;
    private View mContent;
    /** Set on a DOWN that landed in the clearance, so the rest of that gesture follows it. */
    private boolean mForwardingToContent;

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
    }

    /**
     * The child that owes the shape its clearance. A terminal pane finds its own on inflation; a
     * page whose content arrives later — the wall's widget grid — names it here.
     */
    public void setPaneContent(@Nullable View content) {
        if (mContent == content) return;
        mContent = content;
        requestLayout();
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
        requestLayout();
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
