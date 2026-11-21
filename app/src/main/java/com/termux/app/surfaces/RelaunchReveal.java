package com.termux.app.surfaces;

import androidx.annotation.Nullable;

import com.termux.app.window.FramePipeline;
import com.termux.app.window.Hold;

/**
 * The first frame of an activity a relaunch built while the Appearance editor was open (the
 * Material You recolour after a wallpaper is applied): it is held back until the editor stands
 * on screen as it was carried, so the first frame drawn is the editor, never the plain home with
 * the editor cut in a few frames later.
 *
 * <p>This is the window's one draw-holding exception (ADR 0028 section 4): a {@link Hold} on the
 * frame pipeline's first frame, with a time backstop and a count of frames declined; the reopen
 * signals it when it shows (or will not). A held window draws nothing, so without a pending
 * handover nothing is held.</p>
 */
public final class RelaunchReveal {

    /**
     * The longest the first frame waits for the reopened editor, ms: the editor's wallpaper and
     * the picker load in about this long on pong; past it the home draws and the editor follows.
     */
    static final long BACKSTOP_MS = 300L;
    /** The most frames declined while waiting, at any refresh rate. */
    static final int MAX_HELD_FRAMES = 40;

    private RelaunchReveal() {}

    /** Whether the first frame is held at all: only with a carried surface to reopen. */
    static boolean holds(boolean handoverPending) {
        return handoverPending;
    }

    /**
     * Holds the pipeline's first frame while a handover is pending, until {@link Hold#signal} or a
     * backstop; null when nothing is held (no handover, or the first frame is already behind).
     */
    @Nullable
    public static Hold hold(@Nullable FramePipeline frames, boolean handoverPending) {
        if (frames == null || !holds(handoverPending))
            return null;
        return frames.holdFirstFrame("relaunch-reveal", BACKSTOP_MS, MAX_HELD_FRAMES);
    }
}
