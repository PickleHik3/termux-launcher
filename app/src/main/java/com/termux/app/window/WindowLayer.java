package com.termux.app.window;

import com.termux.R;

/**
 * The window's layers, back to front (ADR 0028 section 6). The layer number is the only Z between
 * layers: each layer has one host, hosts stand in the table's order, and elevation only draws a
 * shadow. Inside a layer, order is its host's child order.
 *
 * <p>Ground, pages and chrome stand in the launcher's shell, which the Appearance editor and the
 * setup sheet scale to a miniature; the editor and the notices stand beside the shell in the window
 * root, so the miniature never scales them. The shell's body (the relative layout) holds the pages
 * and, until the stacks move out of it, the chrome that is laid out against them; the chrome host
 * holds the layers that were stacked after it.
 */
public enum WindowLayer {
    /** Wallpaper, the ground strip and the glass sheets drawn behind the shell's content. */
    GROUND(R.id.window_layer_ground),
    /** The wall, and the chrome still laid out against it (top and bottom stacks, A-Z tab, find bar). */
    PAGES(R.id.activity_termux_root_relative_layout),
    /** The Home pill and the page switch panel. */
    CHROME(R.id.window_layer_chrome),
    /** The drawer, the palette, the terminal sheet, the action sheets and the rename chip. */
    SHEETS(R.id.window_layer_sheets),
    /** The Appearance editor and the setup sheet: the card, the bar, the picker. */
    EDITOR(R.id.window_layer_editor),
    /** Notices, the reset cards, the help overlay and the transient popups over the keyboard. */
    NOTICES(R.id.window_layer_notices);

    private final int mHostId;

    WindowLayer(int hostId) {
        mHostId = hostId;
    }

    /** The id of this layer's host view. */
    public int hostId() {
        return mHostId;
    }

    /** Position in the table, 0 at the back. */
    public int number() {
        return ordinal();
    }

    public boolean isAbove(WindowLayer other) {
        return number() > other.number();
    }
}
