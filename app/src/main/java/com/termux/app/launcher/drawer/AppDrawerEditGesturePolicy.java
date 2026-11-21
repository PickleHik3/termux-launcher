package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import com.termux.app.wall.PagePolicy;

/**
 * What a finger does in the app drawer while it is being edited. One instance serves every
 * gesture: {@link #begin} at the down, {@link #evaluate} on each move and the release. Pure;
 * modelled on {@link AppDrawerSectionGesturePolicy}, whose rules for a horizontal drag it
 * repeats with that policy's own constants and {@link PagePolicy#slopPx}, so the two cannot both
 * claim a drag: the views feed {@link #blocksPageSwipe()} to the pager as its {@code pickupArmed}.
 *
 * <p>The rules, when editing:
 * <ul>
 *   <li>An app icon lifts at once; so does a Categories tile, whatever part of it was touched.</li>
 *   <li>A folder cell waits: a release inside the slop is a tap ({@link Action#OPEN_FOLDER}); a
 *       move past the slop, or a still hold for {@link #HOLD_LIFT_MS}, lifts the folder.</li>
 *   <li>A gap or a pane edge scrolls, and in the Default drawer a clearly horizontal drag towards
 *       the other section is {@link Action#PAGE_SWIPE}. The section switch always is.</li>
 *   <li>The search bar is ignored. The A-Z index and the corner keep their own handling.</li>
 *   <li>Outside edit mode everything is {@link Action#PASS_THROUGH}.</li>
 * </ul>
 */
public final class AppDrawerEditGesturePolicy {

    /** What the down landed on. */
    public enum Touched {
        APP_CELL, FOLDER_CELL, TILE_HEADING, TILE_BODY, TILE_PREVIEW_ICON, GAP, SECTION_SWITCH,
        PANE_EDGE, SEARCH_BAR, AZ_INDEX, CORNER
    }

    public enum Action {
        /** Not decided yet: the finger has not moved or waited enough. */
        PENDING,
        /** Not editing, or something that keeps its own handling: today's behaviour. */
        PASS_THROUGH,
        LIFT_APP, LIFT_FOLDER, LIFT_TILE, OPEN_FOLDER,
        PAGE_SWIPE, SCROLL, IGNORE
    }

    /** A still finger on a folder cell this long lifts it, as its long press does. */
    public static final long HOLD_LIFT_MS = 400L;

    @NonNull private Action mAction = Action.PASS_THROUGH;
    private boolean mFolderDown;
    private boolean mSwipeDomain;
    private int mDirection = -1;

    /**
     * Starts a gesture at the down.
     *
     * @param categoriesOverview Categories shows its tiles (nothing expanded or in transition)
     * @param rtl                the layout is right to left
     * @return the answer at the down: never {@link Action#OPEN_FOLDER}
     */
    @NonNull
    public Action begin(boolean editing, @NonNull AppDrawerViewType type,
                        @NonNull AppDrawerSection section, @NonNull Touched touched, boolean rtl,
                        boolean categoriesOverview) {
        mFolderDown = false;
        mSwipeDomain = false;
        if (!editing) return mAction = Action.PASS_THROUGH;
        boolean isDefault = type == AppDrawerViewType.DEFAULT;
        boolean tiles = type == AppDrawerViewType.CATEGORIES
            || (isDefault && section == AppDrawerSection.CATEGORIES);
        switch (touched) {
            case SEARCH_BAR:
                return mAction = Action.IGNORE;
            case AZ_INDEX:
            case CORNER:
                return mAction = Action.PASS_THROUGH;
            case SECTION_SWITCH:
                return mAction = isDefault ? Action.PAGE_SWIPE : Action.IGNORE;
            case APP_CELL:
                return mAction = tiles ? Action.IGNORE : Action.LIFT_APP;
            case FOLDER_CELL:
                if (tiles) return mAction = Action.IGNORE;
                mFolderDown = true;
                return mAction = Action.PENDING;
            case TILE_HEADING:
            case TILE_BODY:
            case TILE_PREVIEW_ICON:
                return mAction = tiles && categoriesOverview ? Action.LIFT_TILE : Action.IGNORE;
            case GAP:
            case PANE_EDGE:
            default:
                // Where the pager would claim: the Default drawer, Categories only in its overview.
                mSwipeDomain = isDefault && (!tiles || categoriesOverview);
                int ltr = section == AppDrawerSection.APPS ? -1 : 1;
                mDirection = rtl ? -ltr : ltr;
                return mAction = Action.PENDING;
        }
    }

    @NonNull
    public Action action() {
        return mAction;
    }

    /**
     * Whether the down landed on something that lifts or may lift: the pager's {@code pickupArmed}.
     * True from the down on a cell or tile until a folder's tap settles it.
     */
    public boolean blocksPageSwipe() {
        return mAction == Action.LIFT_APP || mAction == Action.LIFT_TILE
            || mAction == Action.LIFT_FOLDER || (mAction == Action.PENDING && mFolderDown);
    }

    /**
     * @param dx       finger travel from the down, positive to the right
     * @param dy       finger travel from the down, positive down
     * @param released the finger lifted
     * @return the answer so far; once it is not {@link Action#PENDING} it is latched
     */
    @NonNull
    public Action evaluate(float dx, float dy, long elapsedMs, float slopPx, boolean released) {
        if (mAction != Action.PENDING) return mAction;
        if (Float.isNaN(dx) || Float.isNaN(dy)) return mAction;
        float adx = Math.abs(dx);
        float ady = Math.abs(dy);
        if (mFolderDown) {
            if (Math.max(adx, ady) > slopPx || elapsedMs >= HOLD_LIFT_MS) {
                mAction = Action.LIFT_FOLDER;
            } else if (released) {
                mAction = Action.OPEN_FOLDER;
            }
            return mAction;
        }
        float along = dx * mDirection;
        if (mSwipeDomain && along > 0f && along >= PagePolicy.slopPx(adx, ady, slopPx)
            && adx > ady * AppDrawerSectionGesturePolicy.SECTION_DOMINANCE) {
            mAction = Action.PAGE_SWIPE;
        } else if (ady >= slopPx && ady > adx * AppDrawerSectionGesturePolicy.CHILD_DOMINANCE) {
            mAction = Action.SCROLL;
        } else if (along < 0f && -along >= slopPx
            && adx > ady * AppDrawerSectionGesturePolicy.SECTION_DOMINANCE) {
            mAction = Action.SCROLL;
        } else if (!mSwipeDomain && Math.max(adx, ady) > slopPx) {
            mAction = Action.SCROLL;
        }
        return mAction;
    }
}
