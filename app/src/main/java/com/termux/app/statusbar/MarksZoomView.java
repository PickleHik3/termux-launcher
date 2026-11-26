package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The marks row's zoom while a finger is down: a view of its own in the chrome layer, sized once
 * at touch-down to the frame {@link com.termux.app.wall.pageswitch.MarksZoomPlan} gives, so the
 * zoomed marks can stand taller than the row, over the pane's first text line. It draws the ground
 * (an optional dark band, then Global's glass) and hands the canvas to the line, which draws its
 * marks and the title in its own coordinates. It holds no state of the touch; a move only
 * invalidates it. Not focusable, never touched: the stream stays the line's.
 */
final class MarksZoomView extends View {

    /** Draws the line's touch in the line's own coordinates. */
    interface Painter {
        void drawZoom(@NonNull Canvas canvas);
    }

    @Nullable private Painter mPainter;
    @Nullable private Drawable mGround;
    private final RectF mGroundRect = new RectF();
    private float mGroundRadius;
    private final Paint mBand = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Where the line's origin lies in this view. */
    private float mLineX;
    private float mLineY;

    MarksZoomView(@NonNull Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    /**
     * Dresses the zoom for one touch: the ground's rect and radius in this view's coordinates, the
     * glass to wear (null for none), the band under it ({@code bandArgb}, 0 for none) and where the
     * line's origin lies. Once per touch-down.
     */
    void dress(@NonNull Painter painter, @Nullable Drawable ground, float left, float top,
               float right, float bottom, float radius, int bandArgb, float lineX, float lineY) {
        mPainter = painter;
        if (mGround != ground) {
            if (mGround != null) mGround.setCallback(null);
            mGround = ground;
            if (ground != null) ground.setCallback(this);
        }
        mGroundRect.set(left, top, right, bottom);
        mGroundRadius = radius;
        mBand.setColor(bandArgb);
        mLineX = lineX;
        mLineY = lineY;
        if (ground != null) {
            ground.setBounds(Math.round(left), Math.round(top), Math.round(right),
                Math.round(bottom));
        }
        invalidate();
    }

    /** Lets the painter and the glass go once the touch is over. */
    void release() {
        mPainter = null;
        if (mGround != null) mGround.setCallback(null);
        mGround = null;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == mGround || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Painter painter = mPainter;
        if (painter == null) return;
        if (mBand.getColor() != 0) {
            canvas.drawRoundRect(mGroundRect, mGroundRadius, mGroundRadius, mBand);
        }
        Drawable ground = mGround;
        if (ground != null) ground.draw(canvas);
        int save = canvas.save();
        canvas.translate(mLineX, mLineY);
        painter.drawZoom(canvas);
        canvas.restoreToCount(save);
    }
}
