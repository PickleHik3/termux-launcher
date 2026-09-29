package com.termux.app.chrome;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.function.Supplier;

/**
 * Press feedback for a glass tile: it scales to the motion's press scale while held and springs
 * back on release, through {@link GlassMotionPlayer#press}. The motion is read on every touch, so
 * a preset that changes it takes effect on the next press; under {@link GlassMotion#CLASSIC} there
 * is no press scale and the touch is left alone.
 *
 * <p>The listener never consumes an event, so click, long press and a parent's scroll or drag
 * behave as before; a cancel (a parent taking the gesture over) releases like an up. It replaces
 * the tile's touch listener, so it goes only on tiles that have none.</p>
 */
public final class GlassPress {

    private GlassPress() {}

    /** Gives {@code tile} press feedback in whatever motion {@code motion} names at touch time. */
    @SuppressWarnings("ClickableViewAccessibility")
    public static void attach(@NonNull View tile, @NonNull Supplier<GlassMotion> motion) {
        tile.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (ValueAnimator.areAnimatorsEnabled())
                    GlassMotionPlayer.press(view, motion.get(), true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                GlassMotionPlayer.press(view, motion.get(), false);
            }
            return false;
        });
    }

    /**
     * {@link #attach} on every clickable text control under {@code root}: the rows and buttons a
     * card is built from. Fields and anything that is not a plain label are left alone.
     */
    public static void attachClickables(@NonNull ViewGroup root, @NonNull Supplier<GlassMotion> motion) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView && !(child instanceof EditText) && child.isClickable())
                attach(child, motion);
            else if (child instanceof ViewGroup) attachClickables((ViewGroup) child, motion);
        }
    }
}
