package com.termux.app.dock;

import android.view.View;

/** How the launcher hides the slot a lifted dock icon left: its alpha, 0 while the icon is up. */
public final class LiftSourceShade {

    /** Hides a slot by zeroing its alpha, shows it by restoring it. */
    public static final LiftSourceSlot.Shade<View> ALPHA =
        (slot, shown) -> slot.setAlpha(shown ? 1f : 0f);

    private LiftSourceShade() {}
}
