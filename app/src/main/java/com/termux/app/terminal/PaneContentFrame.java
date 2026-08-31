package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

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
 * whole slab and the terminal is laid out inside the arc's depth on the sides and the top — the
 * bottom already carries the terminal's own slack (see {@link #onMeasure}). The clearance is spent
 * as the child's margin rather than as this frame's padding, because the frame's other child is
 * the glass backdrop and it must still reach the corners the terminal now stays out of.
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
     * <p>The bottom edge is left flush on purpose. TerminalView anchors its rows to the top and
     * sizes them as {@code (height - mFontLineSpacingAndAscent) / mFontLineSpacing}, so at least a
     * line's descent of slack — plus the integral-row remainder — already sits under the last row's
     * background, clear of the arc. Charging the inset there again pays twice, and when the height
     * loss crossed a row boundary it cost a whole extra row of gap between the prompt and the
     * pane's bottom edge. The top row sits flush at y = 0 and gets no such slack, so the top keeps
     * the full clearance.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mContent != null && mContent.getLayoutParams() instanceof MarginLayoutParams) {
            int inset = PaneShape.contentInsetForBounds(mRequestedRadiusPx,
                MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            MarginLayoutParams params = (MarginLayoutParams) mContent.getLayoutParams();
            if (params.leftMargin != inset || params.topMargin != inset
                || params.rightMargin != inset || params.bottomMargin != 0) {
                params.leftMargin = inset;
                params.topMargin = inset;
                params.rightMargin = inset;
                params.bottomMargin = 0;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
