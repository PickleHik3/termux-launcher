package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

/**
 * What a bar dropped on the miniature writes, and the word each of its positions goes by. Pure: a
 * store in, a write out, nothing drawn, so the picture and the Layout editor's drops are testable
 * on their own.
 *
 * <p>What a place offers that no bar can be dragged into — its keyboard, its grid — is
 * {@link com.termux.app.place.PlaceArrangeModel}'s, which answers for one orientation at a time,
 * the way an editor standing on the live screen needs.
 */
public final class LayoutChooserModel {

    private LayoutChooserModel() {}

    /**
     * A bar dropped on the miniature, written through the same keys its row writes: the edge it
     * landed on, or {@code null} for the tray, which is where a bar goes to be hidden.
     *
     * @return whether anything was written — a bar dropped somewhere it cannot stand writes
     *     nothing, so the picture springs it back instead.
     */
    public static boolean applyDrop(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                                    @NonNull PlaceOrientation orientation,
                                    @NonNull MiniatureDragPolicy.Bar bar, @Nullable Edge edge) {
        switch (bar) {
            case STATUS_BAR:
                // The status bar is never hidden, so the tray is not one of its targets.
                if (edge == null) return false;
                places.setStatusBarEdge(place, orientation, edge);
                return true;
            case APPS_ROW: {
                RowPlacement placement = rowPlacement(edge);
                if (placement == null) return false;
                places.setAppsRow(place, orientation, placement);
                return true;
            }
            case EXTRA_KEYS: {
                RowPlacement placement = rowPlacement(edge);
                if (placement == null) return false;
                places.setExtraKeys(place, orientation, placement);
                return true;
            }
            case AZ_INDEX:
                if (edge == null) {
                    places.setAzRowShown(place, orientation, false);
                    return true;
                }
                // Brought back out of the tray onto an edge, the index is both shown again and
                // standing where it was dropped.
                places.setAzRowShown(place, orientation, true);
                places.setAzBarEdge(place, orientation, edge);
                return true;
            default:
                return false;
        }
    }

    /** The row placement an edge means, or null for the tray; a row has no top position. */
    @Nullable
    private static RowPlacement rowPlacement(@Nullable Edge edge) {
        if (edge == null) return RowPlacement.HIDDEN;
        switch (edge) {
            case BOTTOM: return RowPlacement.BOTTOM;
            case LEFT: return RowPlacement.LEFT;
            case RIGHT: return RowPlacement.RIGHT;
            case TOP:
            default: return null;
        }
    }

    /** The word for an edge. Shared with the miniature, which names the same positions. */
    @StringRes
    static int edgeLabel(@NonNull Edge edge) {
        switch (edge) {
            case BOTTOM: return R.string.settings_x11_extra_keys_side_bottom;
            case LEFT: return R.string.settings_dock_rail_side_left;
            case RIGHT: return R.string.settings_dock_rail_side_right;
            case TOP:
            default: return R.string.settings_layout_edge_top;
        }
    }

    /** The word for a row's placement, hidden included. Shared with the miniature. */
    @StringRes
    static int rowLabel(@NonNull RowPlacement placement) {
        switch (placement) {
            case LEFT: return R.string.settings_dock_rail_side_left;
            case RIGHT: return R.string.settings_dock_rail_side_right;
            case HIDDEN: return R.string.settings_layout_row_hidden;
            case BOTTOM:
            default: return R.string.settings_x11_extra_keys_side_bottom;
        }
    }
}
