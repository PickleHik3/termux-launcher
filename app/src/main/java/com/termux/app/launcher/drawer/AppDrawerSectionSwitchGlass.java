package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * When the section switch wears refracting glass and how round it is, pure. The glass is the page
 * bar's slab ({@code PaneGlassBackdropView}) laid under the switch with the switch's own rect; it
 * is wanted only while the drawer is open on the Default type, the only type that shows the
 * switch.
 */
final class AppDrawerSectionSwitchGlass {

    private AppDrawerSectionSwitchGlass() {
    }

    /** Whether the glass is wanted: the switch is shown (Default) and the drawer is open. */
    static boolean wanted(@NonNull AppDrawerViewType type, boolean drawerOpen) {
        return drawerOpen && type == AppDrawerViewType.DEFAULT;
    }

    /** The glass's corner radius: a capsule, half the switch's height. */
    static float radiusPx(int switchHeightPx) {
        return Math.max(0, switchHeightPx) * 0.5f;
    }
}
