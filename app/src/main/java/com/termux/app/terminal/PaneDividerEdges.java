package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A split pane's share of the Docked divider (SPEC 3.7): one line between two panes, drawn half
 * from each side. Each pane draws half the line's thickness along those of its own edges that face
 * another pane, in the line's colour, or in the active colour when it is the focused pane, so the
 * focused pane wears it along its own edges of that line only. The edges that run against the
 * opening's border (a bar's inner edge, or the screen) draw nothing: the bars carry that line.
 *
 * <p>Which edges face a pane is read from where the frame stands inside the pane host, on each
 * draw, since a split moves them.
 */
final class PaneDividerEdges extends android.graphics.drawable.Drawable {
    /** The line's whole thickness in dp; each pane draws half. */
    static final float LINE_DP = 1f;

    private final View mFrame;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mHalfPx;
    /** The colour's own alpha, which the wall's travel alpha scales rather than replaces. */
    private final int mColourAlpha;
    private final int[] mFrameAt = new int[2];
    private final int[] mHostAt = new int[2];
    @Nullable private View mHost;

    PaneDividerEdges(@NonNull View frame, int colour, float density) {
        mFrame = frame;
        mPaint.setColor(colour);
        mColourAlpha = android.graphics.Color.alpha(colour);
        mPaint.setStyle(Paint.Style.FILL);
        mHalfPx = LINE_DP * density / 2f;
    }

    /**
     * Whether an edge of a frame at {@code at}..{@code at + size} faces another pane, given the
     * host's extent: it does when it stands clear of the host's own edge. Pure, for the tests.
     */
    static boolean facesPane(int at, int size, int hostAt, int hostSize, boolean far,
                             int tolerancePx) {
        return far
            ? at + size < hostAt + hostSize - tolerancePx
            : at > hostAt + tolerancePx;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mHost == null) mHost = mFrame.getRootView().findViewById(com.termux.R.id.terminal_pane_host);
        View host = mHost;
        if (host == null) return;
        mFrame.getLocationInWindow(mFrameAt);
        host.getLocationInWindow(mHostAt);
        int w = getBounds().width();
        int h = getBounds().height();
        int tolerance = 2;
        float t = mHalfPx;
        if (facesPane(mFrameAt[0], w, mHostAt[0], host.getWidth(), false, tolerance))
            canvas.drawRect(0f, 0f, t, h, mPaint);
        if (facesPane(mFrameAt[0], w, mHostAt[0], host.getWidth(), true, tolerance))
            canvas.drawRect(w - t, 0f, w, h, mPaint);
        if (facesPane(mFrameAt[1], h, mHostAt[1], host.getHeight(), false, tolerance))
            canvas.drawRect(0f, 0f, w, t, mPaint);
        if (facesPane(mFrameAt[1], h, mHostAt[1], host.getHeight(), true, tolerance))
            canvas.drawRect(0f, h - t, w, h, mPaint);
    }

    @Override public void setAlpha(int alpha) {
        mPaint.setAlpha(Math.round(mColourAlpha * alpha / 255f));
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
