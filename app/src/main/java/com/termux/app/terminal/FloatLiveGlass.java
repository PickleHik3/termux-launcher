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
}
