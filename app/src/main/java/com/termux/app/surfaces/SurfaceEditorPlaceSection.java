package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceArrangeModel.Element;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * Which of a place's arrangement controls lead one surface's card, in the order they stand in.
 *
 * <p>The editor has four surfaces and a place has more bars than that, so the division is by where
 * a bar actually stands. The dock band is a surface, and the bars standing on it are its Place
 * section. A bar that has left the band — a rail of pinned apps, a column of extra keys, an A–Z
 * index on an edge of its own, or a bar that is away entirely — draws no glass and therefore has no
 * card, so it is offered on the canvas: the one surface every place always has, which is also what
 * keeps a hidden bar from being a one-way door. The status bar and the keyboard each own their own.
 *
 * <p>Pure: an arrangement in, an ordered list out, so every case is testable without a window.
 */
public final class SurfaceEditorPlaceSection {

    private SurfaceEditorPlaceSection() {}

    /**
     * The Place section of one card. Empty for the shared layer, which is not a place, and for a
     * surface whose place has nothing to arrange there.
     *
     * @param slot  the surface the card is open on, or null for the shared layer
     * @param place the place the editor is arranging
     */
    @NonNull
    public static List<Element> elementsFor(@Nullable SurfaceSlot slot, @NonNull PaneWallPage place,
                                            @NonNull PlaceLayout layout) {
        List<Element> elements = new ArrayList<>(4);
        if (slot == null) return elements;
        switch (slot) {
            case STATUS:
                elements.add(Element.STATUS_BAR);
                return elements;
            case KEYBOARD:
                elements.add(Element.KEYBOARD);
                return elements;
            case DOCK:
                if (PlaceChromePolicy.appsRowShown(layout)) elements.add(Element.PINNED_APPS);
                if (PlaceChromePolicy.azRowOnDock(layout)) elements.add(Element.AZ_INDEX);
                if (PlaceChromePolicy.extraKeysRowShown(layout)) elements.add(Element.EXTRA_KEYS);
                return elements;
            case CANVAS:
            default:
                // The canvas is the place's own body: its grid leads, and then whatever is not
                // standing on the dock band, so there is always a way back to it.
                if (Element.WIDGET_GRID.isOn(place)) elements.add(Element.WIDGET_GRID);
                if (!PlaceChromePolicy.appsRowShown(layout)) elements.add(Element.PINNED_APPS);
                if (!PlaceChromePolicy.azRowOnDock(layout)) elements.add(Element.AZ_INDEX);
                if (!PlaceChromePolicy.extraKeysRowShown(layout)) elements.add(Element.EXTRA_KEYS);
                return elements;
        }
    }
}
