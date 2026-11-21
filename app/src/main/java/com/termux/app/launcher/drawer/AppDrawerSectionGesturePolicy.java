package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PagePolicy;

/**
 * Who owns a horizontal drag in the Default drawer: the section pager, or whatever is under the
 * finger. Pure; modelled on {@link AppDrawerGestureArbiter}: one frozen snapshot at the down, a
 * one-way latch out of {@link Claim#PENDING}, asymmetric dominance with a neutral cone between.
 *
 * <p>The rules, all read off the {@link Down} snapshot except the pickup:
 * <ul>
 *   <li>{@link Claim#SECTION} only towards the other section: Apps' other side is the layout's
 *       end, Categories' is its start.</li>
 *   <li>Never from the A-Z column, from chrome above the pages, while a query is typed or asked
 *       for, in a drawer that is not interactive, or in a Categories presentation that is not its
 *       overview (a category expanded or mid-transition owns its drags).</li>
 *   <li>Never once a pickup or long press has armed ({@code pickupArmed} on each move).</li>
 *   <li>A clearly vertical move latches {@link Claim#CHILD}: scrolling, the drawer's close and
 *       overpull, and a category's collapse stay exactly as they are.</li>
 *   <li>A down while the pager is settling catches it: {@link Claim#SECTION} at once.</li>
 * </ul>
 */
public final class AppDrawerSectionGesturePolicy {

    public enum Claim { PENDING, SECTION, CHILD }

    /** What the down landed on. */
    public enum Target { APPS_GRID, AZ_COLUMN, CATEGORIES, CHROME }

    /** How far the drag must be dominated by its horizontal component for the section to claim. */
    public static final float SECTION_DOMINANCE = 1.2f;
    /** How far it must be dominated by its vertical component for a child to keep it. */
    public static final float CHILD_DOMINANCE = 1.5f;

    /** Everything the claim depends on, sampled once at the down. */
    public static final class Down {
        @NonNull public final AppDrawerSection section;
        @NonNull public final Target target;
        @NonNull public final AppDrawerCategoryTouchRegions.Part part;
        public final boolean categoriesOverview;
        public final boolean searching;
        public final boolean interactive;
        public final boolean pagerSettling;
        public final boolean rtl;

        public Down(@NonNull AppDrawerSection section, @NonNull Target target,
                    @NonNull AppDrawerCategoryTouchRegions.Part part, boolean categoriesOverview,
                    boolean searching, boolean interactive, boolean pagerSettling, boolean rtl) {
            this.section = section;
            this.target = target;
            this.part = part;
            this.categoriesOverview = categoriesOverview;
            this.searching = searching;
            this.interactive = interactive;
            this.pagerSettling = pagerSettling;
            this.rtl = rtl;
        }

        /** Can this down start a section drag at all, once the finger moves? */
        boolean mayStart() {
            if (!interactive || searching) return false;
            switch (target) {
                case APPS_GRID:
                    return section == AppDrawerSection.APPS;
                case CATEGORIES:
                    if (section != AppDrawerSection.CATEGORIES || !categoriesOverview) return false;
                    return part == AppDrawerCategoryTouchRegions.Part.OVERVIEW_LIST
                        || part == AppDrawerCategoryTouchRegions.Part.EXPAND_ACTION
                        || part == AppDrawerCategoryTouchRegions.Part.EMPTY_CHROME;
                case AZ_COLUMN:
                case CHROME:
                default:
                    return false;
            }
        }

        /** A settling pager is caught by a down on its pages, wherever on them. */
        boolean catches() {
            return interactive && !searching && pagerSettling
                && (target == Target.APPS_GRID || target == Target.CATEGORIES);
        }
    }

    @NonNull private Claim mClaim = Claim.CHILD;
    private int mDirection = -1;

    /** Starts a gesture at the down; a null snapshot is a gesture that is never the section's. */
    @NonNull
    public Claim begin(@Nullable Down down) {
        if (down == null) {
            mClaim = Claim.CHILD;
        } else if (down.catches()) {
            mClaim = Claim.SECTION;
            mDirection = directionOf(down);
        } else if (down.mayStart()) {
            mClaim = Claim.PENDING;
            mDirection = directionOf(down);
        } else {
            mClaim = Claim.CHILD;
        }
        return mClaim;
    }

    public void reset() {
        mClaim = Claim.CHILD;
    }

    @NonNull
    public Claim claim() {
        return mClaim;
    }

    /**
     * @param dx          finger travel from the down, positive to the right
     * @param dy          finger travel from the down, positive down
     * @param pickupArmed a pickup or long press has armed on the cell under the finger
     */
    @NonNull
    public Claim evaluate(float dx, float dy, float slopPx, boolean pickupArmed) {
        if (mClaim != Claim.PENDING) return mClaim;
        if (Float.isNaN(dx) || Float.isNaN(dy)) return mClaim;
        if (pickupArmed) {
            mClaim = Claim.CHILD;
            return mClaim;
        }
        float adx = Math.abs(dx);
        float ady = Math.abs(dy);
        float along = dx * mDirection;
        if (along > 0f && along >= PagePolicy.slopPx(adx, ady, slopPx)
            && adx > ady * SECTION_DOMINANCE) {
            mClaim = Claim.SECTION;
        } else if (ady >= slopPx && ady > adx * CHILD_DOMINANCE) {
            mClaim = Claim.CHILD;
        } else if (along < 0f && -along >= slopPx && adx > ady * SECTION_DOMINANCE) {
            // Dragged the way there is no page: nothing here to take it.
            mClaim = Claim.CHILD;
        }
        return mClaim;
    }

    /** The sign of a finger's dx that moves towards the other section. */
    static int directionOf(@NonNull Down down) {
        // LTR: Apps' other page is to the right, so the finger goes left (-1); Categories' is left.
        int ltr = down.section == AppDrawerSection.APPS ? -1 : 1;
        return down.rtl ? -ltr : ltr;
    }
}
