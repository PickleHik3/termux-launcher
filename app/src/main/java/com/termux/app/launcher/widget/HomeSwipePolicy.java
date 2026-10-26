package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import com.termux.app.wall.PagePolicy;

/**
 * Which way a finger on a Home page's free area is going, and what its lift asks for (spec §5.1).
 * The first movement past the touch slop decides, as a launcher does: mostly sideways pages Home,
 * mostly vertical is the palette gesture (up opens the palette with the keyboard, down puts them
 * away). A tie goes vertical, so a diagonal never turns a page by accident.
 *
 * <p>Pure, so the rule is tested as arithmetic; {@link WidgetPaneView} reads the finger.
 */
public final class HomeSwipePolicy {

    /** What the finger's travel so far says the gesture is. */
    public enum Axis { UNDECIDED, HORIZONTAL, VERTICAL }

    private HomeSwipePolicy() {}

    /**
     * The axis for a finger that has moved ({@code dx}, {@code dy}) since it went down: undecided
     * until one way has gone past {@code slopPx}, then whichever way is longer, vertical on a tie.
     * Sideways is the page's, so it waits for the page's slop ({@link PagePolicy#pastSlop}): four
     * of them for a finger more than 30 degrees off the axis, which lets a diagonal thumb become
     * the vertical swipe rather than a turn.
     */
    @NonNull
    public static Axis axis(float dx, float dy, float slopPx) {
        float ax = Math.abs(dx), ay = Math.abs(dy);
        if (Float.isNaN(ax) || Float.isNaN(ay)) return Axis.UNDECIDED;
        if (ay > slopPx && ay >= ax) return Axis.VERTICAL;
        if (ax > ay && PagePolicy.pastSlop(ax, ay, slopPx)) return Axis.HORIZONTAL;
        return Axis.UNDECIDED;
    }

    /**
     * What a vertical swipe released after travelling {@code dy} (positive down) at
     * {@code velocityYPxPerSec} (positive down) asks for: -1 up, +1 down, each once it has gone
     * {@code commitPx} or is flicked on the same way at {@code flingPxPerSec}; 0 for a release
     * short of both, or flicked back against its own travel. The wall's keyboard swipe rule
     * ({@code BorderDrag.keyboardRelease}), so the gesture lands the same wherever it starts.
     */
    public static int release(float dy, float velocityYPxPerSec, float commitPx,
                              float flingPxPerSec) {
        if (dy == 0f || Float.isNaN(dy)) return 0;
        boolean up = dy < 0f;
        float along = up ? -velocityYPxPerSec : velocityYPxPerSec;
        if (Float.isNaN(along)) along = 0f;
        float fling = Math.max(0f, flingPxPerSec);
        if (fling > 0f && along <= -fling) return 0;
        boolean far = Math.abs(dy) >= commitPx;
        boolean flung = fling > 0f && along >= fling;
        if (!far && !flung) return 0;
        return up ? -1 : 1;
    }
}
