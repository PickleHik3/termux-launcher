package com.termux.app.dock;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Terminal's extra-keys card while it stands apart from Home's dock card (ADR 0032, #302): a
 * round rect inside the extra-keys row's host, so it rides the Terminal's lane and the rows' own
 * motion, and is laid out once per rest from the answer ({@link SideCardsController}).
 *
 * <p>It never sizes its row. The card's height is its LayoutParams' exact height (the answer's,
 * which may run below the row: the flush padding under the keys); the row host
 * ({@link KeysRowHostLayout}) leaves it out of its own height, in the same single pass.
 */
public final class KeysCardLayout extends FrameLayout {

    private float mCornerRadiusPx;

    private final ViewOutlineProvider mOutline = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCornerRadiusPx);
        }
    };

    public KeysCardLayout(@NonNull Context context) {
        this(context, null);
    }

    public KeysCardLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOutlineProvider(mOutline);
        setClipToOutline(true);
    }

    /** The card's corner radius; the outline is cut again only when it moves. */
    public void setCornerRadiusPx(float radiusPx) {
        if (mCornerRadiusPx == radiusPx) return;
        mCornerRadiusPx = radiusPx;
        invalidateOutline();
    }

    public float cornerRadiusPx() {
        return mCornerRadiusPx;
    }

    /**
     * Where the card stands in its row host: its margins from the host's sides and its exact
     * height. The top is the host's. Written only when one differs.
     *
     * @return whether a layout was asked for
     */
    public boolean setBounds(int leftMarginPx, int rightMarginPx, int heightPx) {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (!(params instanceof MarginLayoutParams)) return false;
        MarginLayoutParams margins = (MarginLayoutParams) params;
        int height = Math.max(0, heightPx);
        if (margins.leftMargin == leftMarginPx && margins.rightMargin == rightMarginPx
            && margins.topMargin == 0 && margins.bottomMargin == 0 && margins.height == height
            && margins.width == ViewGroup.LayoutParams.MATCH_PARENT) return false;
        margins.leftMargin = leftMarginPx;
        margins.rightMargin = rightMarginPx;
        margins.topMargin = 0;
        margins.bottomMargin = 0;
        margins.width = ViewGroup.LayoutParams.MATCH_PARENT;
        margins.height = height;
        setLayoutParams(margins);
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateOutline();
    }
}
