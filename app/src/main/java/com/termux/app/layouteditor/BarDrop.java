package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;

/**
 * What letting go of a bar writes into the layout. Pure: it answers with the arrangement the
 * draft takes ({@link LayoutEditorController#writeSlots}), so Undo and Discard treat the drop like
 * any other editor write.
 *
 * <p>Only a drop into a gap on an edge is a slot write. Hiding is {@code setShown(element,
 * false)}, the Hidden column's drop (#339); the pane is the status bar's place (#342); the
 * keyboard and Home's page bar have no slot, so neither answers with a layout.
 */
public final class BarDrop {

    private BarDrop() {}

    /**
     * The layout after {@code bar} lands as {@code landing} says, or null when the landing is not
     * a slot write (refused, hidden, over the pane, or a bar with no slot).
     */
    @Nullable
    public static PlaceLayout layoutAfter(@NonNull PlaceLayout arranged, @NonNull Bar bar,
                                          @NonNull Landing landing) {
        Element element = bar.element();
        if (!landing.allowed || landing.zone != Zone.EDGE || landing.edge == null
            || element == null)
            return null;
        return EdgeStackPolicy.withDrop(arranged, element, landing.edge, landing.index,
            landing.underKeyboard);
    }
}
