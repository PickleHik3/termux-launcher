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
    /**
     * Where this end's page leaves the wall's cells and what is drawn on every page ends above the
     * window's bottom, its own rest's ({@link PageRoom#wallInsetPx}). The flat body surface is laid
     * out once, at the smallest of the pages' rooms, and cut down to the front page's by the
     * scene ({@link StackScene#bodyCutPx}); a crossing blends the two ends' rooms.
     */
    public int roomPx;
    public boolean floating;
    /**
     * The slab's corner radius and its inset from the window's edge at this end's rest, in px: its
     * Corners and Margin as drawn. A crossing blends them on the material curve
     * ({@link StackScene#shape}); the slab's height is the host layer's rest height and the
     * keyboard's shown height the rest's keyboard height while the end has one.
     */
    public int cornerPx;
    public int insetPx;
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

    /** The slab's laid-out height at this end's rest ({@link StackGeometry#HOST}). */
    public int slabHeightPx() {
        return rest.layerPresent[StackGeometry.HOST] ? rest.layerHeightPx[StackGeometry.HOST] : 0;
    }

    /** The keyboard's height as this end shows it at rest: 0 where it has none up. */
    public int keyboardShownPx() {
        return has(KEYBOARD) && rest.keyboardPresent ? rest.keyboardHeightPx : 0;
    }

    public int topology() {
        return floating ? TOPOLOGY_FLOATING : TOPOLOGY_DOCKED;
    }

    @Nullable
    public RegionLook look(int region) {
        return looks[region];
    }
}