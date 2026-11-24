package com.termux.app.window;

/**
 * The frame table: the steps the one pre-draw hook runs, in this order (ADR 0028 section 1). A new
 * piece of code that touches a view a scene owns joins here as a step rather than hooking a frame
 * of its own; the snapshot, the scenes, the layers' writers and the glass commit join after the
 * chrome commit as their steps land.
 */
public enum FrameStep {

    /**
     * The wall's landing settle, left by the landed frame for the next one ({@code
     * LandedFrameSettle}): asked with {@link FramePipeline#requestNextFrame}, so it never runs in
     * the frame that landed.
     */
    PAGE_SETTLE,

    /**
     * The structure point (ADR 0030 section 1): the front page's one-time on-front and off-front
     * work, run once when the recorded front changed, before the chrome commit so what it changes
     * is in this frame's structure.
     */
    PAGE_FRONT,

    /**
     * The chrome geometry pass {@code GeometryScheduler} booked: structure written before the
     * frame's layout, so the layout takes what it writes.
     */
    GEOMETRY,

    /**
     * The accessory chrome's commit: one whole build and apply of the chrome spec. Asked for
     * before the frame's animation phase it runs there, so the frame's layout takes what it
     * writes; asked for later it runs in the pre-draw hook and the next frame lays it out.
     */
    CHROME_COMMIT,

    /**
     * The bottom stack's writer: the container's translation and clip, the rows' and the glass
     * slab's sink and cut, the keyboard's clip, the bands under it and the plank's tilt, written
     * from the stack's scene after the chrome commit has written the stack's structure. Its owned
     * properties are on the guards.
     */
    STACK,

    /**
     * The top stack's writer: the status bar host's ride, the strip behind the system bar, the
     * window chips' slide and the top band's blend, written from {@code TopStackScene}. Its owned
     * properties are on the guards.
     */
    TOP_STACK,

    /**
     * The Home exit's tick (ADR 0034): the group's alpha and scale, written once a frame by
     * {@code HomeExit} while it runs, asking for the next frame itself until it ends.
     */
    HOME_EXIT,

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

    /**
     * Whether this step is a structure point: it runs only in a frame's animation phase (or a
     * flush, or the first frame / a windowless pass), never in a late pre-draw run, where a layout
     * it requests would draw the new look on the old layout. Asked late, it waits for the next
     * frame's animation phase.
     */
    boolean structureStep() {
        return this == PAGE_FRONT;
    }
}
