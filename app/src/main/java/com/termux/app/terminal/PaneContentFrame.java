package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
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
}
