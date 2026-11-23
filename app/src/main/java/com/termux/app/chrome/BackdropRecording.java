package com.termux.app.chrome;

import android.view.SurfaceView;
import android.view.View;

/**
 * The one rule for what a glass backdrop or page-layer recording may hold. A SurfaceView's
 * RenderNode carries a position listener, so whichever renderer draws it moves the real surface to
 * where that renderer draws it (the capture's quarter scale puts the wallpaper at the window's
 * top-left for a frame). A recording therefore never draws one by reference; the skipped view
 * was a transparent hole in it, and its look comes from a stand-in (the scene's live frame, the
 * pane's glass slab). Main thread only; no allocation.
 */
public final class BackdropRecording {

    private static int sDepth;

    private BackdropRecording() {}

    private static final java.util.ArrayList<SurfaceView> sSurfaces = new java.util.ArrayList<>(2);

    /** A SurfaceView joined the window: containers above it are descended, never drawn whole. */
    public static void register(SurfaceView surface) {
        if (!sSurfaces.contains(surface)) sSurfaces.add(surface);
    }

    public static void unregister(SurfaceView surface) {
        sSurfaces.remove(surface);
    }

    private static final java.util.ArrayList<View> sLeftOut = new java.util.ArrayList<>(1);

    /**
     * A view that stands in for a ground only on screen (the frost under a window switch's gap,
     * {@link GroundFrostView}): while it is left out, recordings skip it as they skip a SurfaceView,
     * so glass beneath it keeps sampling what it sampled before. Pair with {@link #keepIn}.
     */
    public static void leaveOut(View view) {
        if (!sLeftOut.contains(view)) sLeftOut.add(view);
    }

    public static void keepIn(View view) {
        sLeftOut.remove(view);
    }

    /**
     * Whether {@code view} is the ancestor of a SurfaceView or of a view left out, so drawing it
     * whole would record that view by reference. Walks each one's parents; no allocation.
     */
    public static boolean holdsSurface(View view) {
        return holds(sSurfaces, view) || holds(sLeftOut, view);
    }

    private static boolean holds(java.util.ArrayList<? extends View> held, View view) {
        for (int i = 0; i < held.size(); i++) {
            android.view.ViewParent parent = held.get(i).getParent();
            while (parent != null) {
                if (parent == view) return true;
                parent = parent.getParent();
            }
        }
        return false;
    }

    /** Whether a recording may draw {@code view} by reference. */
    public static boolean mayRecord(View view) {
        return !(view instanceof SurfaceView) && !sLeftOut.contains(view);
    }

    /** Opens a stretch in which a recording draws a view subtree; pair with {@link #end}. */
    public static void begin() {
        sDepth++;
    }

    public static void end() {
        if (sDepth > 0) sDepth--;
    }

    /** Whether {@code child} must be left out of the draw now going on: inside a recording and not recordable. */
    public static boolean skips(View child) {
        return sDepth > 0 && !mayRecord(child);
    }

    /**
     * Draws {@code view}'s content into {@code canvas} (in the view's own coordinates), as
     * {@code view.draw(canvas)} would, but never a SurfaceView or a view left out, however deep.
     * {@code draw} draws each child by its existing display list, so a SurfaceView below a child
     * that did not draw again would be recorded by reference whatever {@link #skips} says: a
     * container that holds one ({@link #holdsSurface}) is descended instead, its background and
     * then each child at its place, the same rule {@code LiveBackdrop} applies to the views
     * beneath a surface. A wall page holding a Workspace window with the Display in it is one.
     * Main thread; allocates nothing.
     */
    public static void drawWhole(android.graphics.Canvas canvas, View view) {
        if (!(view instanceof android.view.ViewGroup) || !holdsSurface(view)) {
            view.draw(canvas);
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        android.graphics.drawable.Drawable background = group.getBackground();
        if (background != null) background.draw(canvas);
        int save = canvas.save();
        canvas.translate(-group.getScrollX(), -group.getScrollY());
        try {
            int count = group.getChildCount();
            boolean ordered = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q;
            for (int i = 0; i < count; i++) {
                View child = group.getChildAt(ordered ? group.getChildDrawingOrder(i) : i);
                if (child == null || child.getVisibility() != View.VISIBLE || !mayRecord(child)) {
                    continue;
                }
                int childSave = canvas.save();
                canvas.translate(child.getLeft(), child.getTop());
                android.graphics.Matrix own = child.getMatrix();
                if (!own.isIdentity()) canvas.concat(own);
                if (child.getAlpha() < 1f) {
                    canvas.saveLayerAlpha(0f, 0f, child.getWidth(), child.getHeight(),
                        Math.round(255f * Math.max(0f, child.getAlpha())));
                }
                drawWhole(canvas, child);
                canvas.restoreToCount(childSave);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
