package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The Taskbar's Home tile (spec "Taskbar"): a tile like the apps', which opens the drawer, held at
 * the start, the middle or the end of the dock's row. The user moves it with a long-press and a
 * drag; where it lands is persisted as one of {@link Position#value}.
 *
 * <p>The row holds the Home tile plus {@code apps} app tiles, in the user's own order and never
 * sorted: the Home tile only takes a slot, and the apps keep their order around it. Pure: the dock
 * asks which slot the Home tile takes and which app each other slot holds.</p>
 */
public final class TaskbarHomeTilePolicy {

    /** Where the Home tile stands in the row. */
    public enum Position {
        START("start"), MIDDLE("middle"), END("end");

        /** What is stored for it. */
        @NonNull public final String value;

        Position(@NonNull String value) {
            this.value = value;
        }
    }

    /** Where the Home tile stands until the user moves it. */
    @NonNull public static final Position DEFAULT = Position.START;

    private TaskbarHomeTilePolicy() {}

    /** The stored value read back; anything unknown, or nothing stored, is {@link #DEFAULT}. */
    @NonNull
    public static Position parse(@Nullable String stored) {
        if (stored == null) return DEFAULT;
        String value = stored.trim().toLowerCase(Locale.ROOT);
        for (Position position : Position.values()) {
            if (position.value.equals(value)) return position;
        }
        return DEFAULT;
    }

    /** The value to store for {@code position}. */
    @NonNull
    public static String persisted(@NonNull Position position) {
        return position.value;
    }

    /** How many apps fit beside the Home tile in a row of {@code slots} tiles. */
    public static int appSlots(int slots) {
        return Math.max(0, slots - 1);
    }

    /**
     * The Home tile's slot in a row of the tile and {@code apps} apps. The middle is the centre
     * slot; with an odd count of apps the extra one stands after the tile.
     */
    public static int homeSlot(@NonNull Position position, int apps) {
        int n = Math.max(0, apps);
        switch (position) {
            case START: return 0;
            case END: return n;
            default: return n / 2;
        }
    }

    /**
     * The app at {@code slot}, as its index among the apps, or -1 for the Home tile's slot and a
     * slot outside the row.
     */
    public static int appAt(int slot, @NonNull Position position, int apps) {
        int n = Math.max(0, apps);
        if (slot < 0 || slot > n) return -1;
        int home = homeSlot(position, n);
        if (slot == home) return -1;
        return slot < home ? slot : slot - 1;
    }

    /** The slot app {@code app} takes, or -1 for an app outside the row. */
    public static int slotOf(int app, @NonNull Position position, int apps) {
        int n = Math.max(0, apps);
        if (app < 0 || app >= n) return -1;
        return app < homeSlot(position, n) ? app : app + 1;
    }

    /**
     * Where the Home tile lands when the drag lets go with its centre at {@code fraction} of the
     * row's width (0 the row's start, 1 its end): the nearest of the three, the row read in
     * thirds. Mirror the fraction for a right-to-left row before asking.
     */
    @NonNull
    public static Position dropAt(float fraction) {
        if (Float.isNaN(fraction)) return DEFAULT;
        if (fraction < 1f / 3f) return Position.START;
        if (fraction < 2f / 3f) return Position.MIDDLE;
        return Position.END;
    }
}
