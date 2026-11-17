package com.termux.app.window;

/**
 * The frame table: the steps the one pre-draw hook runs, in this order (ADR 0028 section 1). A new
 * piece of code that touches a view a scene owns joins here as a step rather than hooking a frame
 * of its own; the snapshot, the scenes, the layers' writers and the glass commit join after the
 * chrome commit as their steps land.
 */
public enum FrameStep {

    /**
     * The accessory chrome's commit: one whole build and apply of the chrome spec. Asked for
     * before the frame's animation phase it runs there, so the frame's layout takes what it
     * writes; asked for later it runs in the pre-draw hook and the next frame lays it out.
     */
    CHROME_COMMIT;

    static final FrameStep[] TABLE = values();
}
