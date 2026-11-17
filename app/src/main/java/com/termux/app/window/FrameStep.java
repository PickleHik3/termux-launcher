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
    CHROME_COMMIT,

    /**
     * The glass commit (ADR 0029 section 1), always last: the glass service re-dresses every
     * surface whose kind a policy event moved, back to front, then records the Live layers of
     * this frame. It reads the frame's laid-out places, so it runs only in the pre-draw hook of a
     * frame being drawn (never in the animation phase); while a Live picture is held it is armed
     * ({@link FramePipeline#arm}) and runs on every drawn frame without asking for one.
     */
    GLASS_COMMIT;

    static final FrameStep[] TABLE = values();

    /** Whether this step runs only in the pre-draw hook, against the frame's settled layout. */
    boolean drawStep() {
        return this == GLASS_COMMIT;
    }
}
