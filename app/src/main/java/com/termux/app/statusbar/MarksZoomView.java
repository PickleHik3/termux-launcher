package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The marks row's zoom while a finger is down: a view of its own in the chrome layer, sized once
 * at touch-down to the frame {@link com.termux.app.wall.pageswitch.MarksZoomPlan} gives, so the
 * zoomed marks can stand taller than the row. No ground stands behind the marks; the line paints
 * them in its own coordinates. The page's name stands in a glass capsule: two small child views
 * (the capsule and its other half, which comes in from the row's start while the first leaves past
 * its end), each wearing Global's glass over the stored backdrop and moved by translation, so the
 * glass stays aligned with the screen. They are drawn clipped to the line, then the marks and the
 * name on top. Never touched: the stream stays the line's.
 */
final class MarksZoomView extends FrameLayout {

    /** Draws the line's touch in the line's own coordinates. */
    interface Painter {
        void drawZoom(@NonNull Canvas canvas);
    }

    static final int CAPSULES = 2;
    /** The most window controls a held mark opens (Minimise, Maximise, Move, Close). */
    static final int CONTROLS = 4;

    @Nullable private Painter mPainter;
    /** Where the line's origin lies in this view. */
    private float mLineX;
    private float mLineY;
    private float mClipL;
    private float mClipT;
    private float mClipR;
    private float mClipB;
    @NonNull private final View[] mCapsules = new View[CAPSULES];
    @NonNull private final View[] mControls = new View[CONTROLS];

    MarksZoomView(@NonNull Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < CAPSULES; i++) {
            View capsule = new View(context);
            capsule.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            capsule.setVisibility(INVISIBLE);
            addView(capsule, new LayoutParams(0, 0, Gravity.TOP | Gravity.LEFT));
            mCapsules[i] = capsule;
        }
    }

    /** Control {@code index}'s round glass, for the glass's anchor; made at the first hold that opens controls. */
    @NonNull
    View controlView(int index) {
        View control = mControls[index];
        if (control == null) {
            control = new View(getContext());
            control.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            control.setVisibility(INVISIBLE);
            addView(control, new LayoutParams(0, 0, Gravity.TOP | Gravity.LEFT));
            mControls[index] = control;
        }
        return control;
    }

    /** Dresses control {@code index} with its glass; null for none. */
    void setControlGlass(int index, @Nullable Drawable ground) {
        controlView(index).setBackground(ground);
    }

    /**
     * Puts control {@code index}'s glass at ({@code left}, {@code top}) in this view, {@code size}
     * across; hidden when not {@code visible}. A layout is asked only when the size changed.
     */
    void placeControl(int index, float left, float top, int size, boolean visible) {
        View control = mControls[index];
        if (control == null && !visible) return;
        if (control == null) control = controlView(index);
        int shown = visible ? VISIBLE : INVISIBLE;
        if (control.getVisibility() != shown) control.setVisibility(shown);
        if (!visible) return;
        LayoutParams params = (LayoutParams) control.getLayoutParams();
        if (params.width != size || params.height != size) {
            params.width = size;
            params.height = size;
            control.setLayoutParams(params);
        }
        control.setTranslationX(left);
        control.setTranslationY(top);
    }

    /**
     * Readies the view for one touch: who paints, where the line's origin lies and the line's rect,
     * which the capsules are clipped to, all in this view's coordinates. Once per touch-down.
     */
    void place(@NonNull Painter painter, float lineX, float lineY, float clipLeft, float clipTop,
               float clipRight, float clipBottom) {
        mPainter = painter;
        mLineX = lineX;
        mLineY = lineY;
        mClipL = clipLeft;
        mClipT = clipTop;
        mClipR = clipRight;
        mClipB = clipBottom;
    }

    /** Capsule {@code index}'s view, for the glass's anchor. */
    @NonNull
    View capsuleView(int index) {
        return mCapsules[index];
    }

    /** Dresses capsule {@code index} with its glass; null for none. */
    void setCapsuleGlass(int index, @Nullable Drawable ground) {
        mCapsules[index].setBackground(ground);
    }

    /**
     * Puts capsule {@code index} at ({@code left}, {@code top}) in this view, {@code width} by
     * {@code height}, at {@code alpha}; hidden when not {@code visible}. A layout is asked only
     * when the size changed.
     */
    void placeCapsule(int index, float left, float top, int width, int height, float alpha,
                      boolean visible) {
        View capsule = mCapsules[index];
        int shown = visible ? VISIBLE : INVISIBLE;
        if (capsule.getVisibility() != shown) capsule.setVisibility(shown);
        if (!visible) return;
        LayoutParams params = (LayoutParams) capsule.getLayoutParams();
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            capsule.setLayoutParams(params);
        }
        capsule.setTranslationX(left);
        capsule.setTranslationY(top);
        if (capsule.getAlpha() != alpha) capsule.setAlpha(alpha);
    }

    /** Lets the painter and the capsules' glass go once the touch is over. */
    void release() {
        mPainter = null;
        for (View capsule : mCapsules) {
            capsule.setBackground(null);
            capsule.setVisibility(INVISIBLE);
        }
        for (View control : mControls) {
            if (control == null) continue;
            control.setBackground(null);
            control.setVisibility(INVISIBLE);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        Painter painter = mPainter;
        if (painter == null) return;
        int save = canvas.save();
        canvas.clipRect(mClipL, mClipT, mClipR, mClipB);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
        save = canvas.save();
        canvas.translate(mLineX, mLineY);
        painter.drawZoom(canvas);
        canvas.restoreToCount(save);
    }
}
