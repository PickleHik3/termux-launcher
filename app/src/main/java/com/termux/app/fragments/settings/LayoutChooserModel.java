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

/**
 * What a bar dropped on the miniature writes, and the word each of its positions goes by. Pure: a
 * store in, a write out, nothing drawn, so the picture and the Layout editor's drops are testable
 * on their own.
 *
 * <p>What the layout offers that no bar can be dragged into — the keyboard's height, chin and
 * type, the grid's cells — is a handle or a chip on the layout canvas, written through
 * {@link com.termux.app.layouteditor.LayoutEditorPlan} one orientation at a time.
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
    public static boolean applyDrop(@NonNull PlaceLayoutStore places,
                                    @NonNull PlaceOrientation orientation,
                                    @NonNull MiniatureDragPolicy.Bar bar, @Nullable Edge edge,
                                    int index) {
        return applyDrop(places, orientation, bar, edge, index, false);
    }

    /**
     * As {@link #applyDrop(PlaceLayoutStore, PlaceOrientation, MiniatureDragPolicy.Bar, Edge,
     * int)}, into a gap on either side of the keyboard: {@code underKeyboard} puts the bar in the
     * bottom edge's group under it, where {@code index} counts from the screen edge up to the
     * keyboard. A bar that may not stand there is refused, so the picture springs it back.
     */
    public static boolean applyDrop(@NonNull PlaceLayoutStore places,
                                    @NonNull PlaceOrientation orientation,
                                    @NonNull MiniatureDragPolicy.Bar bar, @Nullable Edge edge,
                                    int index, boolean underKeyboard) {
        Element element = bar.element();
        if (underKeyboard && (edge != Edge.BOTTOM || !element.underKeyboardAllowed()))
            return false;
        PlaceLayout layout = places.resolve(orientation);
        PlaceLayout next;
        if (edge == null) {
            // An element that may not hide has no tray; none refuses today.
            if (!element.hideAllowed()) return false;
            next = EdgeStackPolicy.withAway(layout, element);
        } else if (index < 0) {
            next = layout.withSlot(element,
                Slot.on(edge, element).withUnderKeyboard(underKeyboard));
        } else {
            next = EdgeStackPolicy.withDrop(layout, element, edge, index, underKeyboard);
        }
        for (Element each : Element.values()) {
            Slot slot = next.slot(each);
            if (!slot.equals(layout.slot(each))) places.setSlot(orientation, each, slot);
        }
        return true;
    }

    /** A bar dropped on an edge without a gap picked: the band it has always taken there. */
    public static boolean applyDrop(@NonNull PlaceLayoutStore places,
                                    @NonNull PlaceOrientation orientation,
                                    @NonNull MiniatureDragPolicy.Bar bar, @Nullable Edge edge) {
        return applyDrop(places, orientation, bar, edge, -1);
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
