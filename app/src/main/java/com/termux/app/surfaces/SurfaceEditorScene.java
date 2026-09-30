package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

/**
 * What the Appearance editor may offer for the arrangement that is actually on screen.
 *
 * <p>Every place can stand its bar on any of the four edges and put its apps and its extra keys in
 * a column, so the offer has to match the arrangement: a surface that is not on screen is not a
 * tap target in the editor's frame.
 *
 * <p>Pure: an arrangement in, booleans out, so every case is testable without a window.
 */
public final class SurfaceEditorScene {

    @NonNull public final Edge statusBarEdge;
    /** Whether anything at all lands on the dock band, which is what decides it is drawn. */
    public final boolean dockRowShown;
    /** Whether the pinned apps are the dock's own row, rather than a rail or nowhere. */
    public final boolean appsRowShown;
    /** Whether the pinned apps stand in a rail on a screen edge. */
    public final boolean appsRailShown;
    /** Whether the extra keys stand in a column on a screen edge. */
    public final boolean extraKeysColumnShown;
    public final boolean keyboardShown;
    public final boolean floatingDock;

    private SurfaceEditorScene(@NonNull Edge statusBarEdge, boolean dockRowShown,
                               boolean appsRowShown, boolean appsRailShown,
                               boolean extraKeysColumnShown, boolean keyboardShown,
                               boolean floatingDock) {
        this.statusBarEdge = statusBarEdge;
        this.dockRowShown = dockRowShown;
        this.appsRowShown = appsRowShown;
        this.appsRailShown = appsRailShown;
        this.extraKeysColumnShown = extraKeysColumnShown;
        this.keyboardShown = keyboardShown;
        this.floatingDock = floatingDock;
    }

    /**
     * The scene one place's arrangement makes, with the two states that are not the arrangement's:
     * whether the embedded keyboard is up, and which dock style is on.
     */
    @NonNull
    public static SurfaceEditorScene of(@NonNull PlaceLayout layout, boolean keyboardShown,
                                        boolean floatingDock) {
        return new SurfaceEditorScene(layout.slot(com.termux.app.place.Element.STATUS).edge,
            PlaceChromePolicy.dockShown(layout),
            PlaceChromePolicy.appsRowShown(layout),
            PlaceChromePolicy.appsRailShown(layout),
            PlaceChromePolicy.extraKeysColumnShown(layout),
            keyboardShown, floatingDock);
    }

    // ---------------------------------------------------------------------------- the surfaces

    /** The bar stands in a column down one side rather than in a row along an end. */
    public boolean statusBarStandsInAColumn() {
        return statusBarEdge.isOnSide();
    }

    /**
     * Whether the surface is on screen to be outlined, touched and edited.
     *
     * <p>The status bar is never hidden and the terminal is always there. The dock is the band its
     * rows stand in, and only that band carries the glass: a rail of pinned apps and a column of
     * extra keys draw their icons and their keys straight onto the wallpaper, so with the dock band
     * away there is no dock surface for a material row to move. The keyboard exists while it is up.
     */
    public boolean offersSurface(@Nullable SurfaceSlot slot) {
        if (slot == null)
            return true;
        switch (slot) {
            case DOCK:
                return dockRowShown;
            case KEYBOARD:
                return keyboardShown;
            case STATUS:
            case CANVAS:
            default:
                return true;
        }
    }

    /** Everything the offer reads, as one number, so a pass that changes nothing is skipped. */
    public long signature() {
        long signature = statusBarEdge.ordinal();
        signature = signature * 31 + (dockRowShown ? 1 : 0);
        signature = signature * 31 + (appsRowShown ? 1 : 0);
        signature = signature * 31 + (appsRailShown ? 1 : 0);
        signature = signature * 31 + (extraKeysColumnShown ? 1 : 0);
        signature = signature * 31 + (keyboardShown ? 1 : 0);
        return signature * 31 + (floatingDock ? 1 : 0);
    }

    @NonNull
    @Override
    public String toString() {
        return "SurfaceEditorScene{status=" + statusBarEdge
            + ", dockRow=" + dockRowShown
            + ", appsRow=" + appsRowShown
            + ", rail=" + appsRailShown
            + ", keysColumn=" + extraKeysColumnShown
            + ", keyboard=" + keyboardShown
            + ", floating=" + floatingDock
            + "}";
    }
}
