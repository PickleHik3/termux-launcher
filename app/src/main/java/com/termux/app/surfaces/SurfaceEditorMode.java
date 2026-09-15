package com.termux.app.surfaces;

/**
 * How much of the surface editor one session offers. Held for the session, set on entry.
 *
 * <p>The corner tabs of the places that are not the terminal open {@link #ARRANGE}: the same
 * outlines, the same hold-to-move, but the card asks only where the bars stand. {@link #FULL} is
 * the whole editor — the shared layer behind the palette and every look row on every card — and is
 * what the terminal's tab and the Settings row open. A session can move from ARRANGE to FULL when
 * the user asks for more; it never moves back.
 */
public enum SurfaceEditorMode {
    /** Placement only: no palette, no look rows, one row that opens the rest. */
    ARRANGE,
    /** Everything. */
    FULL
}
