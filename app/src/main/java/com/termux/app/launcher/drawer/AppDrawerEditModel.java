package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * The app drawer's edit mode as one immutable value. It says whether the drawer is being edited,
 * how it was entered, and what the views show because of it (search dimmed, outlines on the
 * cells, the corner tab held open with the grid-size button and the tick). One session covers
 * both sections of the Default drawer. Changes save as they are made, so every way out is the
 * same plain {@link #OFF}; there is no discard. Pure.
 */
public final class AppDrawerEditModel {

    /** How edit mode was entered. */
    public enum Entry { NONE, CORNER_BUTTON, TILE_LONG_PRESS }

    /** Why edit mode ended; every reason ends it the same way. */
    public enum Leave { TICK, BACK, DRAWER_CLOSED, VIEW_TYPE_CHANGED }

    /** One rung of Back, in the order {@link #backStep} walks them. */
    public enum BackStep {
        /** Back is not the drawer's: it is not open. */
        UNCLAIMED,
        LEAVE_EDIT, CLEAR_QUERY, COLLAPSE_CATEGORY, CLOSE_DRAWER
    }

    /** Not editing. */
    public static final AppDrawerEditModel OFF = new AppDrawerEditModel(Entry.NONE);
    private static final AppDrawerEditModel FROM_CORNER = new AppDrawerEditModel(Entry.CORNER_BUTTON);
    private static final AppDrawerEditModel FROM_TILE = new AppDrawerEditModel(Entry.TILE_LONG_PRESS);

    @NonNull private final Entry mEntry;

    private AppDrawerEditModel(@NonNull Entry entry) {
        mEntry = entry;
    }

    /** The state after entering by {@code entry}; {@link Entry#NONE} is {@link #OFF}. */
    @NonNull
    public static AppDrawerEditModel enter(@NonNull Entry entry) {
        switch (entry) {
            case CORNER_BUTTON: return FROM_CORNER;
            case TILE_LONG_PRESS: return FROM_TILE;
            default: return OFF;
        }
    }

    /** The state after the tick, Back, the drawer closing or the view type changing. */
    @NonNull
    public AppDrawerEditModel leave(@NonNull Leave reason) {
        return OFF;
    }

    /** The view type changed: edit mode does not carry over to another layout. */
    @NonNull
    public AppDrawerEditModel onViewTypeChanged(@NonNull AppDrawerViewType previous,
                                                @NonNull AppDrawerViewType next) {
        return previous == next ? this : leave(Leave.VIEW_TYPE_CHANGED);
    }

    @NonNull
    public Entry entry() {
        return mEntry;
    }

    public boolean isEditing() {
        return mEntry != Entry.NONE;
    }

    /** A long-press on a tile's heading also lifts that tile in the same gesture. */
    public boolean liftsTileOnEntry() {
        return mEntry == Entry.TILE_LONG_PRESS;
    }

    /** The search bar does nothing while editing. */
    public boolean searchEnabled() {
        return !isEditing();
    }

    public boolean searchDimmed() {
        return isEditing();
    }

    /** The thin static outline around each cell or tile. */
    public boolean outlinesVisible() {
        return isEditing();
    }

    /** The corner tab stays open while editing. */
    public boolean tabPinnedOpen() {
        return isEditing();
    }

    /** Outside edit mode the tab shows settings and edit. */
    public boolean showsSettingsButton() {
        return !isEditing();
    }

    public boolean showsEditButton() {
        return !isEditing();
    }

    /**
     * In edit mode the tab shows the grid-size button, except where Categories is what is on
     * screen: it has no grid size.
     *
     * @param section only read for {@link AppDrawerViewType#DEFAULT}
     */
    public boolean showsGridSizeButton(@NonNull AppDrawerViewType type,
                                       @NonNull AppDrawerSection section) {
        if (!isEditing()) return false;
        if (type == AppDrawerViewType.CATEGORIES) return false;
        return !(type == AppDrawerViewType.DEFAULT && section == AppDrawerSection.CATEGORIES);
    }

    public boolean showsTickButton() {
        return isEditing();
    }

    /**
     * What one press of Back does in the drawer. Leaving edit mode comes first: it is the
     * outermost state the user put the drawer in, and nothing inside it (search is dead, a
     * category cannot be expanded) is waiting on that press. Below it the existing order holds:
     * clear a typed query, collapse an expanded or mid-transition category, close the drawer.
     */
    @NonNull
    public static BackStep backStep(boolean open, boolean editing, boolean hasQuery,
                                    boolean categoryExpandedOrMoving) {
        if (!open) return BackStep.UNCLAIMED;
        if (editing) return BackStep.LEAVE_EDIT;
        if (hasQuery) return BackStep.CLEAR_QUERY;
        if (categoryExpandedOrMoving) return BackStep.COLLAPSE_CATEGORY;
        return BackStep.CLOSE_DRAWER;
    }
}
