package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
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
     * A bar dropped on the miniature, written as slots: the edge and the gap in that edge's stack
     * it landed in, or {@code null} for the tray, which is where a bar goes to be hidden.
     *
     * <p>A drop into a gap re-numbers every band on that edge, since orders are per element and a
     * stack is read by comparing them ({@link EdgeStackPolicy#withDrop}). Only the slots that
     * actually moved are written, so a bar dropped back where it already stood leaves the store —
     * and the editor's unsaved-changes question — exactly as it found it.
     *
     * @param index the position in the edge's stack, 0 outermost, or negative for the band that
     *     element has always taken on that edge
     * @return whether the drop was a legal one — a bar dropped somewhere it cannot stand writes
     *     nothing, so the picture springs it back instead.
     */
    public static boolean applyDrop(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                                    @NonNull PlaceOrientation orientation,
                                    @NonNull MiniatureDragPolicy.Bar bar, @Nullable Edge edge,
                                    int index) {
        Element element = bar.element();
        PlaceLayout layout = places.resolve(place, orientation);
        PlaceLayout next;
        if (edge == null) {
            // The status bar is never hidden, so the tray is not one of its targets.
            if (!element.hideAllowed()) return false;
            next = EdgeStackPolicy.withAway(layout, element);
        } else if (index < 0) {
            next = layout.withSlot(element, Slot.on(edge, element));
        } else {
            next = EdgeStackPolicy.withDrop(layout, element, edge, index);
        }
        for (Element each : Element.values()) {
            Slot slot = next.slot(each);
            if (!slot.equals(layout.slot(each))) places.setSlot(place, orientation, each, slot);
        }
        return true;
    }

    /** A bar dropped on an edge without a gap picked: the band it has always taken there. */
    public static boolean applyDrop(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                                    @NonNull PlaceOrientation orientation,
                                    @NonNull MiniatureDragPolicy.Bar bar, @Nullable Edge edge) {
        return applyDrop(places, place, orientation, bar, edge, -1);
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
}
