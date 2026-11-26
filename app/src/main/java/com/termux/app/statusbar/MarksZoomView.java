package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The marks row's zoom while a finger is down: a view of its own in the chrome layer, sized once
 * at touch-down to the frame {@link com.termux.app.wall.pageswitch.MarksZoomPlan} gives, so the
 * zoomed marks can stand taller than the row. It draws nothing of its own: no ground stands behind
 * the marks, which the line paints in its own coordinates over the row. Never touched: the stream
 * stays the line's.
 */
final class MarksZoomView extends FrameLayout {

    /** Draws the line's touch in the line's own coordinates. */
    interface Painter {
        void drawZoom(@NonNull Canvas canvas);
    }

    @Nullable private Painter mPainter;
    /** Where the line's origin lies in this view. */
    private float mLineX;
    private float mLineY;

    MarksZoomView(@NonNull Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Readies the view for one touch: who paints, and where the line's origin lies. Once per touch-down. */
    void place(@NonNull Painter painter, float lineX, float lineY) {
        mPainter = painter;
        mLineX = lineX;
        mLineY = lineY;
    }

    /** Lets the painter go once the touch is over. */
    void release() {
        mPainter = null;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        Painter painter = mPainter;
        if (painter == null) return;
        super.dispatchDraw(canvas);
        int save = canvas.save();
        canvas.translate(mLineX, mLineY);
        painter.drawZoom(canvas);
        canvas.restoreToCount(save);
    }
}
