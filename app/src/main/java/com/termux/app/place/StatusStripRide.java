package com.termux.app.place;

/**
 * Where the strip behind the system status bar stands while the status bar rides with the
 * Workspace's page across the seam: a strip dressed as the bar's own glass running up under the
 * system bar is one piece with the bar and goes where the bar goes; any other strip stays. Asked on
 * every frame the bar rides and on the pass that shows the strip, so a strip shown mid-ride never
 * stands at its rest while the bar is still on its way in. Builds nothing.
 */
public final class StatusStripRide {

    private StatusStripRide() {}

    /**
     * The strip's translation for a bar at {@code barX} from its rest.
     *
     * @param stripShown      whether the strip is shown at all
     * @param dressedForBar   whether the strip was last dressed as the bar's continuation (its
     *                        lead is the window bar), rather than a sheet's or nothing's
     */
    public static float stripX(float barX, boolean stripShown, boolean dressedForBar) {
        if (Float.isNaN(barX) || !stripShown || !dressedForBar) return 0f;
        return barX;
    }
}
