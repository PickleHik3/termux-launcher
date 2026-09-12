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
 * <p>The editor has four surfaces and a place has more bars than that, so each bar has one home
 * card, and it keeps that home whatever its value is — hiding a bar, or standing it in a rail or a
 * column, must not move its control to another card, because the next thing the user does is look
 * for it where it was. The dock band's three bars — the pinned apps, the A–Z index and the extra
 * keys — are the dock card's Place section in every arrangement. The status bar and the keyboard
 * each own their own card, and the widget grid is the home canvas's.
 *
 * <p>The one exception is forced: with nothing standing on the band there is no dock glass to tap,
 * so the dock has no card to reach, and the canvas — the one surface every place always has —
 * offers the three bars instead, which is what keeps hiding the last of them from being a one-way
 * door. They return to the dock card the moment one of them stands on the band again.
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
                if (PlaceChromePolicy.dockShown(layout)) addDockBars(elements);
                return elements;
            case CANVAS:
            default:
                // The canvas is the place's own body: its grid leads, and the dock's bars follow
                // only while the band is empty and has no card of its own to reach.
                if (Element.WIDGET_GRID.isOn(place)) elements.add(Element.WIDGET_GRID);
                if (!PlaceChromePolicy.dockShown(layout)) addDockBars(elements);
                return elements;
        }
    }

    /** The dock band's three bars, in the order they stand in, whatever each one is set to. */
    private static void addDockBars(@NonNull List<Element> elements) {
        elements.add(Element.PINNED_APPS);
        elements.add(Element.AZ_INDEX);
        elements.add(Element.EXTRA_KEYS);
    }
}
