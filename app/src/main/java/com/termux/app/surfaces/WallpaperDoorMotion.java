package com.termux.app.surfaces;

/**
 * How the Wallpaper sheet comes and goes with the editor when a door opens the editor onto it
 * ({@link AppearanceSurfaceController#resumeWallpaper}): one motion in and one motion out.
 *
 * <p>In: the picker is built while the editor waits for its wallpaper and its frame, so nothing is
 * inflated once the frame starts to shrink; the editor then opens with the sheet rising over it,
 * and the card sheet and the bar under it stay unseen until the sheet covers them. Out: Done on
 * the sheet rides it down with the editor's exit (the same spatial-default motion as the frame's
 * growth and the card sheet's fall), and it is taken off only once it is off screen.</p>
 */
final class WallpaperDoorMotion {

    /**
     * How long a door waits for the picker to be built and able to draw before the editor opens
     * without it; the sheet then rises over the editor once it is ready, as from the Appearance
     * card's Wallpaper row.
     */
    static final long PICKER_WAIT_MS = 600L;

    private WallpaperDoorMotion() {}

    /**
     * Whether the card sheet and the bar stay unseen while the editor opens: only when the picker
     * rises with the editor's entry ({@code pickerRises}), is ready to rise at once, and rises
     * with a slide. A picker still loading would leave the editor bare for as long as it took.
     */
    static boolean veilsChrome(boolean pickerRises, boolean pickerReady, boolean reducedMotion) {
        return pickerRises && pickerReady && !reducedMotion;
    }

    /**
     * Whether a closing surface rides the picker down before taking it off: when it stands over
     * the editor ({@code pickerShown}, its page on screen) and motion is on. Otherwise it is taken
     * off at once, as when it was already put down.
     */
    static boolean ridesPickerDown(boolean pickerShown, boolean pickerOnScreen,
                                   boolean reducedMotion) {
        return pickerShown && pickerOnScreen && !reducedMotion;
    }
}
