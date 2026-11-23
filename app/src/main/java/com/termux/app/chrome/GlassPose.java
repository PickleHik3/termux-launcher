package com.termux.app.chrome;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.home.HomePageTransition;

/**
 * A glass slab's pose: the only motion input its glass answers ({@link GlassPoseOptics}). How far
 * it is turned, as the sine of the turn pointing at the edge going away ({@link #tiltX},
 * {@link #tiltY}), and the scale of the page it stands on ({@link #scale}). A translation is not
 * part of it: a slab sliding in its own plane looks the same.
 *
 * <p>The page transition's {@link HomePageTransition.Transform} is the one owner of a page's
 * pose ({@link #of(HomePageTransition.Transform, GlassPose)}). A slab's draw reads the same pose
 * back off the view properties that transform was applied as ({@link #read}), which also carries
 * the turns no transition makes: the plank's tip, a held border's press, a pane's press rig and
 * the stack's turn. Both meet in {@link #fromViewProperties}, so the two give the same pose
 * (GlassPoseTest holds that for every transition).</p>
 *
 * <p>Mutable on purpose: a surface keeps one and refills it on each draw, which allocates
 * nothing. At rest it is exactly {@link #isRest}: no turn, scale 1.</p>
 */
public final class GlassPose {

    /**
     * A layout whose children are pages a transition poses (the wall, Home's widget pane): it
     * answers the scale the transition gives a child page, without any scale it holds for another
     * reason (the wall's sink under a hold). Only these scales are a slab's pose; a view held
     * small at rest, such as the Appearance editor's preview, keeps the glass's rest look.
     */
    public interface Pages {
        /** The scale the page transition gives {@code page}, a child of this layout; 1 unposed. */
        float glassPoseScale(@NonNull View page);
    }

    /**
     * A turn under this, in degrees, is flat ({@link #tiltVector}): a press rig's spring settles
     * toward 0 without ever landing on it, so a slab is called flat there and draws the rest look
     * exactly.
     */
    public static final float TILT_REST_DEG = 0.05f;
    /** A page scale this close to 1 is unscaled. */
    public static final float SCALE_REST = 1e-3f;

    /** The sine of the turn about the slab's own y axis: positive takes the right edge away. */
    public float tiltX;
    /** The sine of the turn about its x axis, as a y in its own px axes: positive takes the bottom edge away. */
    public float tiltY;
    /** The scale of the page the slab stands on; 1 unscaled. */
    public float scale = 1f;

    /** Back to rest. */
    public void reset() {
        tiltX = 0f;
        tiltY = 0f;
        scale = 1f;
    }

    public void set(@NonNull GlassPose other) {
        tiltX = other.tiltX;
        tiltY = other.tiltY;
        scale = other.scale;
    }

    /** No turn and no scale: the rest look, which asks for nothing. */
    public boolean isRest() {
        return tiltX == 0f && tiltY == 0f && scale == 1f;
    }

    public boolean same(@NonNull GlassPose other) {
        return tiltX == other.tiltX && tiltY == other.tiltY && scale == other.scale;
    }

    /**
     * {@code out} for a slab whose views on the way up add to {@code rotationXDeg} and
     * {@code rotationYDeg} (as {@code View.setRotationX}/{@code setRotationY} take them) and whose
     * page a transition scales by {@code pageScale}. Exactly at rest under
     * {@link #TILT_REST_DEG} and {@link #SCALE_REST}, and for anything not finite.
     */
    public static void fromViewProperties(float rotationXDeg, float rotationYDeg, float pageScale,
                                          @NonNull GlassPose out) {
        float[] tilt = TILT;
        tiltVector(rotationXDeg, rotationYDeg, tilt);
        out.tiltX = tilt[0];
        out.tiltY = tilt[1];
        out.scale = Float.isFinite(pageScale) && pageScale > 0f
            && Math.abs(pageScale - 1f) >= SCALE_REST ? pageScale : 1f;
    }

    /** Scratch for {@link #fromViewProperties}; poses are read on the main thread only. */
    private static final float[] TILT = new float[2];

    /** {@code out} for a page the transition poses as {@code t}. */
    public static void of(@NonNull HomePageTransition.Transform t, @NonNull GlassPose out) {
        fromViewProperties(0f, t.rotationY, t.scale, out);
    }

    /**
     * {@code out} for {@code view} now: the rotations of every view on the way up added (a
     * Carousel or stack page's turn, the plank's tip, a held border's press, a pane's press rig),
     * and the page scale a {@link Pages} parent gives the page the view is on. Walks the tree
     * without allocating.
     */
    public static void read(@NonNull View view, @NonNull GlassPose out) {
        float rotationX = 0f;
        float rotationY = 0f;
        float scale = 1f;
        View current = view;
        while (true) {
            rotationX += current.getRotationX();
            rotationY += current.getRotationY();
            ViewParent parent = current.getParent();
            if (parent instanceof Pages) scale *= ((Pages) parent).glassPoseScale(current);
            if (!(parent instanceof View)) break;
            current = (View) parent;
        }
        fromViewProperties(rotationX, rotationY, scale, out);
    }

    /** The view a drawable draws for, through any drawables holding it; null when none. */
    @Nullable
    public static View hostOf(@NonNull Drawable drawable) {
        Drawable.Callback callback = drawable.getCallback();
        for (int i = 0; i < 8 && callback instanceof Drawable; i++) {
            callback = ((Drawable) callback).getCallback();
        }
        return callback instanceof View ? (View) callback : null;
    }

    /**
     * The turn's sines for rotations adding to {@code rotationXDeg} and {@code rotationYDeg},
     * into {@code out}: a vector in the slab's own px axes (y down) pointing at the edge going
     * away from the viewer. A positive rotationY takes the right edge away (as {@code PlankTilt}
     * writes it), a positive rotationX the top edge (as the press rig's {@code -(ny - 0.5)} leans
     * the pressed bottom away). Exactly {@code (0, 0)} under {@link #TILT_REST_DEG}, or for a
     * non-finite angle.
     */
    public static void tiltVector(float rotationXDeg, float rotationYDeg, @NonNull float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        if (!Float.isFinite(rotationXDeg) || !Float.isFinite(rotationYDeg)) return;
        float x = (float) Math.sin(Math.toRadians(rotationYDeg));
        float y = (float) -Math.sin(Math.toRadians(rotationXDeg));
        if (Math.hypot(x, y) < Math.sin(Math.toRadians(TILT_REST_DEG))) return;
        out[0] = x;
        out[1] = y;
    }
}
