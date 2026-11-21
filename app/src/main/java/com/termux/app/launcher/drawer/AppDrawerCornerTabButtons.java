package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Which buttons the drawer's corner tab carries, in reading order, as {@link AppDrawerEditModel}
 * answers them. Pure: the tab turns each id into a drawn action, so putting a button in or out
 * is a change here and in the tab's one id switch, never a rewrite of the tab.
 */
final class AppDrawerCornerTabButtons {

    static final int SETTINGS = 1;
    static final int EDIT = 2;
    static final int GRID_SIZE = 3;
    static final int TICK = 4;

    /**
     * Whether the Edit button is shown at all. Edit mode is not wired to a view yet, so the model
     * always says "not editing" and the button would do nothing; the change that wires edit mode
     * flips this to true.
     */
    static final boolean EDIT_BUTTON_SHIPPED = false;

    private AppDrawerCornerTabButtons() {
    }

    /** The ids to show: edit, grid size and tick come before the settings gear. */
    @NonNull
    static int[] ids(@NonNull AppDrawerEditModel model, @NonNull AppDrawerViewType type,
                     @NonNull AppDrawerSection section, boolean editShipped) {
        int[] out = new int[4];
        int n = 0;
        if (model.showsEditButton() && editShipped) out[n++] = EDIT;
        if (model.showsGridSizeButton(type, section)) out[n++] = GRID_SIZE;
        if (model.showsTickButton()) out[n++] = TICK;
        if (model.showsSettingsButton()) out[n++] = SETTINGS;
        return java.util.Arrays.copyOf(out, n);
    }
}
