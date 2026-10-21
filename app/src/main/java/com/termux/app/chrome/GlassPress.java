package com.termux.app.chrome;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.WeakHashMap;
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
 *
 * <p>With a {@link GlassTouchLight} the glass under the tile also lights under the finger: the
 * finger's position, carried from the tile into the glass view's coordinates, drives the light
 * from down to release. One light can serve every tile on a card; the tile whose finger lit it
 * last is the one whose release lets it go.</p>
 */
public final class GlassPress {

    private GlassPress() {}

    /** The tile whose finger last lit each light: a release on any other tile leaves the light alone. */
    private static final WeakHashMap<GlassTouchLight, View> HOLDERS = new WeakHashMap<>();

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
     * {@link #attach(View, Supplier)}, and the glass under the tile lights under the finger:
     * {@code light} takes the finger's position in {@code glassView}'s own coordinates (the view
     * holding the glass's frost; for a {@link SharedFrameDrawable} those are the drawable's), from
     * down through every move to the release or cancel. A null light is the plain press.
     */
    @SuppressWarnings("ClickableViewAccessibility")
    public static void attach(@NonNull View tile, @NonNull Supplier<GlassMotion> motion,
                              @Nullable GlassTouchLight light, @NonNull View glassView) {
        if (light == null) {
            attach(tile, motion);
            return;
        }
        // Where the glass view's origin is on screen, taken at the down: the glass does not move
        // under a resting finger, and the raw position already carries the tile's press scale.
        int[] glassOrigin = new int[2];
        tile.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (ValueAnimator.areAnimatorsEnabled())
                    GlassMotionPlayer.press(view, motion.get(), true);
                glassView.getLocationOnScreen(glassOrigin);
                HOLDERS.put(light, view);
                light.down(event.getRawX() - glassOrigin[0], event.getRawY() - glassOrigin[1]);
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (HOLDERS.get(light) == view)
                    light.move(event.getRawX() - glassOrigin[0], event.getRawY() - glassOrigin[1]);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                GlassMotionPlayer.press(view, motion.get(), false);
                if (HOLDERS.get(light) == view) {
                    HOLDERS.remove(light);
                    light.up();
                }
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

    /**
     * {@link #attachClickables(ViewGroup, Supplier)}, each control also lighting the glass under
     * the finger through {@link #attach(View, Supplier, GlassTouchLight, View)}.
     */
    public static void attachClickables(@NonNull ViewGroup root, @NonNull Supplier<GlassMotion> motion,
                                        @Nullable GlassTouchLight light, @NonNull View glassView) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView && !(child instanceof EditText) && child.isClickable())
                attach(child, motion, light, glassView);
            else if (child instanceof ViewGroup)
                attachClickables((ViewGroup) child, motion, light, glassView);
        }
    }
}
