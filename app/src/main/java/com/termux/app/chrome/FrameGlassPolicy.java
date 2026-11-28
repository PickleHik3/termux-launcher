package com.termux.app.chrome;

/**
 * Which chrome sheets stand down for a joined Docked frame. The frame is one continuous glass
 * surface behind the whole screen, so every sheet that lies inside it (the status bar, the dock's
 * rows, the off-dock planks, the keyboard's slab) gives up its own glass: a second wash or blur
 * there would show as a seam where the tones differ. Anything outside the gate wears its own glass
 * exactly as before.
 */
public final class FrameGlassPolicy {

    private FrameGlassPolicy() {}

    /**
     * Whether the frame's glass is the one sheet, and the sheets inside it stand down.
     *
     * @param wallpaperMode the launcher paints over the wallpaper (not opaque mode)
     * @param paneGlassActive the pane glass is on, the gate the frame glass itself is drawn under
     * @param joinedFrameCard the shape model has a joined full-screen frame card
     */
    public static boolean sheetsStandDown(boolean wallpaperMode, boolean paneGlassActive,
                                          boolean joinedFrameCard) {
        return wallpaperMode && paneGlassActive && joinedFrameCard;
    }

    /**
     * Whether the joined frame's glass is drawn behind the screen ({@code docked_frame_glass}):
     * where the shape model has the joined card ({@code joinedFrameCard}), and under Terl
     * ({@code terlBar}, the control bar installed) wherever the Style is Docked. Terl puts every
     * v1 bar away, so the Docked shape is the frame with every bar put away, whose glass is the
     * wall's ground under the Workspaces and Home; the in-app keyboard joining the shape split it
     * into edge cards and the glass went, so the wallpaper turned bright with the keyboard up
     * (round 5). Only the glass is held: whether the sheets inside stand down on it stays the
     * shape's ({@link #sheetsStandDown}), so the keyboard keeps its own Terl glass over it.
     */
    public static boolean glassShows(boolean paneGlassActive, boolean terlBar, boolean docked,
                                     boolean joinedFrameCard) {
        return paneGlassActive && ((terlBar && docked) || joinedFrameCard);
    }
}
