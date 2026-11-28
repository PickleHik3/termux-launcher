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
     * Whether the joined frame's glass stands behind the screen. Under Terl ({@code terlBar}, the
     * control bar installed) v1's bars are all put away, so the Docked shape is the frame with
     * every bar put away: that frame's glass is the wall's ground under the Workspaces and Home.
     * The in-app keyboard rides the control bar there, not v1's Docked stack; counted as a bottom
     * piece it split the frame into edge cards and the glass went, so the wallpaper turned bright
     * whenever the keyboard showed (round 5). Under Terl a Docked frame ({@code docked}) stands
     * whole; elsewhere, and under Floating, the shape model's {@code joinedFrameCard} decides as
     * before.
     */
    public static boolean frameCard(boolean terlBar, boolean docked, boolean joinedFrameCard) {
        return (terlBar && docked) || joinedFrameCard;
    }
}
