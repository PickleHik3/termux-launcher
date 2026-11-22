package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The <b>Drawer tile</b> (#97; once the Taskbar design's tile, spec "Taskbar"): a tile like the
 * apps', which opens the app drawer, held at the start, the middle or the end of the dock's apps
 * row, with any Dock design where the layout turns it on. The user moves it with a long-press and
 * a drag; where it lands is persisted per layout as one of {@link Position#value}.
 *
 * <p>The row holds the tile plus {@code apps} app tiles, in the user's own order and never
 * sorted: the tile only takes a slot, and the apps keep their order around it. Pure: the dock
 * asks which slot the tile takes and which app each other slot holds.</p>
 */
public final class DrawerTilePolicy {

    /** Where the tile stands in the row. */
    public enum Position {
        START("start"), MIDDLE("middle"), END("end");

        /** What is stored for it. */
        @NonNull public final String value;

        Position(@NonNull String value) {
            this.value = value;
        }
    }

    /** Where the tile stands until the user moves it. */
    @NonNull public static final Position DEFAULT = Position.START;

    private DrawerTilePolicy() {}

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

    /** How many apps fit beside the tile in a row of {@code slots} tiles. */
    public static int appSlots(int slots) {
        return Math.max(0, slots - 1);
    }

    /**
     * The tile's slot in a row of the tile and {@code apps} apps. The middle is the centre
     * slot; with an odd count of apps the extra one stands after the tile.
     */
    public static int tileSlot(@NonNull Position position, int apps) {
        int n = Math.max(0, apps);
        switch (position) {
            case START: return 0;
            case END: return n;
            default: return n / 2;
        }
    }

    /**
     * The app at {@code slot}, as its index among the apps, or -1 for the tile's slot and a
     * slot outside the row.
     */
    public static int appAt(int slot, @NonNull Position position, int apps) {
        int n = Math.max(0, apps);
        if (slot < 0 || slot > n) return -1;
        int home = tileSlot(position, n);
        if (slot == home) return -1;
        return slot < home ? slot : slot - 1;
    }

    /** The slot app {@code app} takes, or -1 for an app outside the row. */
    public static int slotOf(int app, @NonNull Position position, int apps) {
        int n = Math.max(0, apps);
        if (app < 0 || app >= n) return -1;
        return app < tileSlot(position, n) ? app : app + 1;
    }

    /**
     * Where the tile lands when the drag lets go with its centre at {@code fraction} of the
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
    // ------------------------------------------------------------------ Columns
    //
    // The tile is dragged to any column of the row now, like an app icon. A layout that never
    // moved it keeps one of the three anchors above, read as a column of the row it stands in
    // ({@link Place#columnIn}), so a first launch lays the row as it always did; a drop stores
    // the column itself.

    /**
     * Where a layout keeps its tile: one of the three anchors (a layout from before columns, or
     * never dragged), or a column of the row.
     */
    public static final class Place {
        /** The anchor, or null for a column. */
        @Nullable public final Position anchor;
        /** The column, or -1 for an anchor. */
        public final int column;

        private Place(@Nullable Position anchor, int column) {
            this.anchor = anchor;
            this.column = column;
        }

        @NonNull public static Place of(@NonNull Position anchor) {
            return new Place(anchor, -1);
        }

        @NonNull public static Place at(int column) {
            return new Place(null, Math.max(0, column));
        }

        /**
         * The column it takes in a row of {@code columns} columns holding {@code apps} apps: an
         * anchor where it always stood (the start 0; the end after the apps, so before a
         * palette tile at the row's end; the middle the apps' centre), a column held inside the
         * row.
         */
        public int columnIn(int apps, int columns) {
            int last = Math.max(0, columns - 1);
            if (anchor != null) return Math.min(last, tileSlot(anchor, apps));
            return Math.max(0, Math.min(last, column));
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Place)) return false;
            Place other = (Place) o;
            return anchor == other.anchor && column == other.column;
        }

        @Override public int hashCode() {
            return anchor != null ? anchor.hashCode() : 31 + column;
        }

        @NonNull @Override public String toString() {
            return persisted(this);
        }
    }

    /** Where the tile stands until the user moves it, as a {@link Place}. */
    @NonNull public static final Place DEFAULT_PLACE = Place.of(DEFAULT);

    /**
     * The stored value read back as a place: an anchor's value, or a column's decimal index;
     * anything else, or nothing stored, is {@link #DEFAULT_PLACE}.
     */
    @NonNull
    public static Place parsePlace(@Nullable String stored) {
        int column = storedColumn(stored);
        if (column >= 0) return Place.at(column);
        return Place.of(parse(stored));
    }

    /** Whether {@code stored} is a value {@link #parsePlace} reads (an anchor or a column). */
    public static boolean isPlaceValue(@Nullable String stored) {
        if (storedColumn(stored) >= 0) return true;
        if (stored == null) return false;
        String value = stored.trim().toLowerCase(Locale.ROOT);
        for (Position position : Position.values()) {
            if (position.value.equals(value)) return true;
        }
        return false;
    }

    /** The value to store for {@code place}. */
    @NonNull
    public static String persisted(@NonNull Place place) {
        return place.anchor != null ? place.anchor.value : Integer.toString(place.column);
    }

    private static int storedColumn(@Nullable String stored) {
        if (stored == null) return -1;
        String value = stored.trim();
        if (value.isEmpty() || value.length() > 4) return -1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return -1;
        }
        return Integer.parseInt(value);
    }

    /**
     * The app at {@code column} of a row of {@code apps} apps around two tiles at {@code tileA}
     * and {@code tileB} (-1 for a tile the row does not hold; either order), as its index among
     * the apps, or -1 for a tile's column and a column outside the row.
     */
    public static int appAt(int column, int tileA, int tileB, int apps) {
        int columns = Math.max(0, apps) + (tileA >= 0 ? 1 : 0) + (tileB >= 0 ? 1 : 0);
        if (column < 0 || column >= columns || column == tileA || column == tileB) return -1;
        int app = column;
        if (tileA >= 0 && tileA < column) app--;
        if (tileB >= 0 && tileB < column) app--;
        return app;
    }

    /** The column app {@code app} takes around the two tiles, or -1 for an app outside the row. */
    public static int slotOf(int app, int tileA, int tileB, int apps) {
        int n = Math.max(0, apps);
        if (app < 0 || app >= n) return -1;
        int low = Math.min(tileA, tileB);
        int high = Math.max(tileA, tileB);
        int column = app;
        if (low >= 0 && column >= low) column++;
        if (high >= 0 && column >= high) column++;
        return column;
    }

    /**
     * The column a dragged tile lands on with its centre at {@code fraction} of the row's width
     * (0 the row's start, 1 its end) across {@code columns} even columns: the nearest. Mirror the
     * fraction for a right-to-left row before asking.
     */
    public static int dropColumn(float fraction, int columns) {
        int n = Math.max(1, columns);
        if (Float.isNaN(fraction)) return 0;
        return Math.max(0, Math.min(n - 1, (int) Math.floor(fraction * n)));
    }

    /**
     * The two tiles' columns, {@code {moved, other}}, once the tile at {@code from} is dropped on
     * {@code to}: on the other tile's column the two swap, so they never share one; the other
     * tile at -1 (none) stays none.
     */
    @NonNull
    public static int[] dropped(int to, int from, int other) {
        if (other >= 0 && to == other) return new int[] {to, from};
        return new int[] {to, other};
    }

    /**
     * Both tiles' columns, {@code {drawer, palette}}, as wanted ({@code drawer} and {@code
     * palette} each -1 for a tile the row does not hold), where they meet on one column (a
     * column kept from a wider row, a palette turned on over a dragged Drawer tile): the palette
     * keeps it and the Drawer tile takes the column before it, or after it at the row's start.
     */
    @NonNull
    public static int[] apart(int drawer, int palette, int columns) {
        if (drawer < 0 || palette < 0 || drawer != palette) return new int[] {drawer, palette};
        int moved = palette > 0 ? palette - 1 : Math.min(Math.max(0, columns - 1), palette + 1);
        return new int[] {moved, palette};
    }
}
