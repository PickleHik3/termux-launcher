package com.termux.app.launcher.widget.builtin;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * One widget's corner, live: the Look's Corners as the widget wears them now, and the inner
 * shapes its layout cut from them (a container rounded to the corner less its inset, a clip, a
 * view that draws with it). A Corners slider moving under the widget lands here and re-dresses
 * those shapes in place, redraws only: no rebuild, no layout.
 *
 * <p>The layout registers its shapes as it builds ({@link BuiltinWidgetUi#innerRounded},
 * {@link BuiltinWidgetUi#followCorner}); a rebuild forgets them ({@link #forgetShapes}). They are
 * held weakly: a widget that redraws a list in place between rebuilds leaves its old rows to the
 * collector rather than to this.</p>
 */
public final class BuiltinWidgetCorner {

    private final float density;
    private float radiusPx;
    /** Each inner shape and how far inside the card it sits, in dp. */
    private final Map<GradientDrawable, Integer> shapes = new WeakHashMap<>();
    private final Map<View, Boolean> followers = new WeakHashMap<>();

    public BuiltinWidgetCorner(float radiusPx, float density) {
        this.radiusPx = Math.max(0f, radiusPx);
        this.density = density;
    }

    /** The Look's corner, in px, before any card holds it to its size. */
    public float radiusPx() { return radiusPx; }

    /** The corner less {@code insetDp}, never below zero: what a shape that far in is cut at. */
    public float innerRadiusPx(int insetDp) {
        return Math.max(0f, radiusPx - insetDp * density);
    }

    /**
     * The corner moved to {@code next}: every inner shape takes it, every follower redraws.
     *
     * @return whether it moved at all; float noise from the same dp does not
     */
    public boolean set(float next) {
        next = Math.max(0f, next);
        if (!BuiltinWidgetGlass.cornersMoved(radiusPx, next)) return false;
        radiusPx = next;
        for (Map.Entry<GradientDrawable, Integer> shape : shapes.entrySet())
            shape.getKey().setCornerRadius(innerRadiusPx(shape.getValue()));
        for (View view : followers.keySet()) {
            view.invalidateOutline();
            view.invalidate();
        }
        return true;
    }

    /** {@code shape}, cut at the corner less {@code insetDp} now and whenever it moves. */
    @NonNull GradientDrawable track(@NonNull GradientDrawable shape, int insetDp) {
        shape.setCornerRadius(innerRadiusPx(insetDp));
        shapes.put(shape, insetDp);
        return shape;
    }

    /** {@code view} reads the corner as it draws or outlines; it redraws whenever it moves. */
    @NonNull <V extends View> V follow(@NonNull V view) {
        followers.put(view, Boolean.TRUE);
        return view;
    }

    /** The layout was built again: what the old one registered is gone with it. */
    void forgetShapes() {
        shapes.clear();
        followers.clear();
    }

    /** How many shapes and followers are registered; for tests. */
    int trackedCount() { return shapes.size() + followers.size(); }
}
