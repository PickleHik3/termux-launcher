package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.wall.PageRef;

/**
 * One end of a crossing as the bottom stack rests there: its page, its laid-out geometry, its
 * dock Style (Docked and Floating resolve geometry differently on purpose, so the Style is part
 * of the end and never unified away), the look of each region it has, who draws the foot under
 * the system pill, its inks and its even-out matrices. Read by {@link StackEnds#read} as a
 * crossing begins and on each rest change, never mid-crossing.
 */
public final class StackEnd {

    /** The stack's regions. */
    public static final int ROWS = 0;
    public static final int KEYBOARD = 1;
    public static final int UNDER = 2;
    public static final int FOOT = 3;
    public static final int REGION_COUNT = 4;

    /** Who draws the foot under the system pill at this end. */
    public enum StripRule {
        /** The decor strip draws it, the keyboard's foot. */
        KEYBOARD_FOOT,
        /** The dock's glass host reaches down and draws it itself. */
        DOCK_FOOT,
        /** Nothing: the wallpaper shows. */
        BARE
    }

    /** The topology ids a frame reports ({@link StackScene.Frame#topology}). */
    public static final int TOPOLOGY_DOCKED = 0;
    public static final int TOPOLOGY_FLOATING = 1;

    @NonNull public final PageRef page;
    @NonNull public final StackGeometry rest = new StackGeometry();
    public boolean floating;
    /** Each region's look; null where this end has no such region. */
    @NonNull public final RegionLook[] looks = new RegionLook[REGION_COUNT];
    @NonNull public StripRule strip = StripRule.BARE;
    public int inkArgb;
    /** Each region's even-out colour matrix (20 floats); null for the identity. */
    @NonNull public final float[][] evenOut = new float[REGION_COUNT][];

    public StackEnd(@NonNull PageRef page) {
        this.page = page;
    }

    /** Whether this end has {@code region}. */
    public boolean has(int region) {
        return looks[region] != null;
    }

    public int topology() {
        return floating ? TOPOLOGY_FLOATING : TOPOLOGY_DOCKED;
    }

    @Nullable
    public RegionLook look(int region) {
        return looks[region];
    }
}