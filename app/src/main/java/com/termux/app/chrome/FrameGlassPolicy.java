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
     * Whether the frame's glass is the one sheet, and the sheets inside it stand down. Never under
     * Terl ({@code terlBar}, the control bar installed): v1's Docked frame glass is not drawn there,
     * so nothing stands down on it and the keyboard keeps its own glass (the developer, 11-28).
     *
     * @param wallpaperMode the launcher paints over the wallpaper (not opaque mode)
     * @param paneGlassActive the pane glass is on, the gate the frame glass itself is drawn under
     * @param terlBar the control bar is installed
     * @param joinedFrameCard the shape model has a joined full-screen frame card
     */
    public static boolean sheetsStandDown(boolean wallpaperMode, boolean paneGlassActive,
                                          boolean terlBar, boolean joinedFrameCard) {
        return !terlBar && wallpaperMode && paneGlassActive && joinedFrameCard;
    }

    /**
     * Whether the joined frame's glass is drawn behind the screen ({@code docked_frame_glass}):
     * where the shape model has the joined card, and never under Terl ({@code terlBar}). Terl has
     * no Docked or Floating: the wall's dim under it is the page's Wallpaper dim (ADR 0031
     * section 4), and v1's Docked frame glass is not drawn (the developer, 11-28, reversing round
     * 5's Terl and Docked gate).
     */
    public static boolean glassShows(boolean paneGlassActive, boolean terlBar,
                                     boolean joinedFrameCard) {
        return !terlBar && paneGlassActive && joinedFrameCard;
    }
}
