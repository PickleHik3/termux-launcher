package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * One movable piece of a place's chrome. Everything a user can lift off the Layout page's
 * miniature and drop on another edge is one of these four; the terminal canvas, the widget grid
 * and the keyboard are content, not chrome, and have no slot.
 *
 * <p>Each element names the preference key its placement has always been stored under, so the new
 * ordering key can be spelled beside it rather than in a second table
 * ({@link PlaceLayoutStore#orderKeyName}).
 */
public enum Element {

    /** The status bar. Never hidden — the wall's pager rides it — so it only ever moves. */
    STATUS("status_bar"),
    /** The pinned apps: the row along an edge, or the rail standing in a column. */
    APPS("apps_row"),
    /** The alphabets index. While the apps row stands along the bottom it rides that row. */
    AZ("az_bar"),
    /** The terminal's extra keys: the row along an edge, or the column standing on one. */
    EXTRA_KEYS("extra_keys");

    @NonNull private final String mStorageKey;

    Element(@NonNull String storageKey) {
        mStorageKey = storageKey;
    }

    /** The unscoped preference key this element's placement is stored under. */
    @NonNull
    public String storageKey() {
        return mStorageKey;
    }

    /** Whether a user may put this element away entirely. Only the status bar may not. */
    public boolean hideAllowed() {
        return this != STATUS;
    }

    /**
     * Where this element stands in an edge's stack before anyone has re-ordered it — 0 outermost,
     * against the screen edge. These are the stacks the launcher has always drawn, verified
     * against the views themselves rather than chosen:
     *
     * <ul>
     *   <li><b>Top:</b> the status bar, then the alphabets bar — the order the top
     *       {@code EdgeStackView} holds {@code terminal_window_bar_host} and
     *       {@code place_az_bar_host} in.</li>
     *   <li><b>Bottom:</b> the extra keys, the alphabets row, the pinned apps, then the status
     *       bar. {@code activity_termux.xml} chains {@code apps_bar_viewpager} above
     *       {@code apps_bar_az_row} above {@code terminal_toolbar_view_pager}, and a bottom status
     *       bar stands in the bottom {@code EdgeStackView}, which is above the whole dock.</li>
     *   <li><b>Left and right:</b> the status bar, the apps rail, the extra keys column, then the
     *       alphabets bar. A column's lead-in says which: the status column starts at the cutout
     *       (the stack's own cutout padding), then the rail, the extra keys and the alphabets bar
     *       as the next three bands of that stack.</li>
     * </ul>
     *
     * <p>The two side stacks were the one place the shipped launcher did not really stack: a
     * status column and a rail on the same edge shared one column lengthwise instead of standing
     * beside each other. They stack now, which is the change the Layout-freedom work was for; see
     * {@link EdgeStackPolicy#contentInsets}.
     */
    public int defaultOrder(@NonNull Edge edge) {
        switch (edge) {
            case TOP:
                switch (this) {
                    case STATUS: return 0;
                    case AZ: return 1;
                    case APPS: return 2;
                    default: return 3;
                }
            case BOTTOM:
                switch (this) {
                    case EXTRA_KEYS: return 0;
                    case AZ: return 1;
                    case APPS: return 2;
                    default: return 3;
                }
            case LEFT:
            case RIGHT:
            default:
                switch (this) {
                    case STATUS: return 0;
                    case APPS: return 1;
                    case EXTRA_KEYS: return 2;
                    default: return 3;
                }
        }
    }
}
