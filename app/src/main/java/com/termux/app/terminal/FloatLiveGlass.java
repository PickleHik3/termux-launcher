package com.termux.app.terminal;

import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * When a floating window's glass slab shows Live blur's picture of what is beneath it, rather than
 * the stored frame. Live blur being on, and a picture being free, are the host's answer; this is
 * the float's side of it. A tiled pane never asks.
 */
final class FloatLiveGlass {

    private FloatLiveGlass() {}

    /**
     * Whether the slab takes the live picture: its float can be seen (attached, shown, not
     * minimised or on another Home page), the slab is still inside that float (not docked back
     * into the tiled tree), and the slab itself is shown (glass on, not a widget's terminal).
     * Pure.
     */
    static boolean wants(boolean floatShown, boolean slabInFloat, boolean slabShown) {
        return floatShown && slabInFloat && slabShown;
    }

    /** Whether {@code view} sits inside {@code ancestor}. Walks the parents; allocates nothing. */
    static boolean inside(@NonNull View view, @Nullable View ancestor) {
        if (ancestor == null) return false;
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent == ancestor) return true;
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * How far above the slab the float's one recording must reach so its titlebar, standing at
     * {@code titlebarTop}, has pixels: both tops in the float's coordinates. Zero when the strip
     * is not above the slab. Pure.
     */
    static int reachAbove(int slabTop, int titlebarTop) {
        return Math.max(0, slabTop - titlebarTop);
    }

    /**
     * {@code view}'s top-left in {@code ancestor}'s coordinates, written to {@code out}; false,
     * with {@code out} untouched, when {@code view} is not inside it. Allocates nothing.
     */
    static boolean offsetIn(@NonNull View view, @Nullable View ancestor, @NonNull int[] out) {
        if (ancestor == null) return false;
        int x = view.getLeft();
        int y = view.getTop();
        ViewParent parent = view.getParent();
        while (parent != null && parent != ancestor) {
            if (!(parent instanceof View)) return false;
            View p = (View) parent;
            x += p.getLeft() - p.getScrollX();
            y += p.getTop() - p.getScrollY();
            parent = p.getParent();
        }
        if (parent == null) return false;
        out[0] = x;
        out[1] = y;
        return true;
    }
}
