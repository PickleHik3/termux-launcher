package com.termux.app.terminal;

import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

/**
 * Where a hosted Home float is drawn instead of the float layer: a terminal widget on a Home
 * page ({@link TerminalPaneController#attachHostedFloat}). The widget is the float's frame — its
 * card, its corner, its Glass switch — and the grid moves and sizes it like any widget, so a new
 * cell size is a new terminal size.
 */
public interface HostedFloatSlot {
    /** The frame the pane fills. */
    @NonNull FrameLayout paneFrame();

    /** What rises over the keyboard while the pane is focused: the widget as the grid lays it out. */
    @NonNull View liftTarget();

    /** The radius the widget's card is cut to; the terminal keeps its text clear of it. */
    float cornerRadiusPx();

    /**
     * How opaque the terminal's own ground is over the card: whole on a plain card, see-through on
     * a glass one so the wallpaper shows, as the card's own fill does.
     */
    int groundAlpha();

    /**
     * {@link #groundAlpha()} for a terminal drawn in {@code paletteForeground} on
     * {@code paletteBackground}: at least that, and as much more of the background as the
     * foreground needs to read on what the widget stands on. The terminal's ink is the palette's
     * and never moves, so this is its veil.
     */
    default int groundAlpha(int paletteBackground, int paletteForeground) {
        return groundAlpha();
    }
}
