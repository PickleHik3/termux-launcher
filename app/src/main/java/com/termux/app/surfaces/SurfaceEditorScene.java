package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

/**
 * What the surface editor may offer for the arrangement that is actually on screen.
 *
 * <p>The editor used to be written for one arrangement: a status bar along the top, a dock along
 * the bottom, and the free room in between. Every place can now stand its bar on any of the four
 * edges and put its apps and its extra keys in a column, and two things went wrong with that.
 *
 * <p>The band the card and the resting pill park in was measured from the bar wherever it stood: a
 * bar along the bottom put the band's ceiling underneath its floor, and a bar standing in a column
 * put it at the foot of the display, so the band collapsed to nothing and everything the editor
 * draws was parked off the bottom of the screen — the editor was open and there was nothing to see.
 * {@link #freeBandPx} is that band, derived from the edge the bar stands on rather than from the
 * bar's rect alone, and it always leaves a usable strip: a card overhanging its band by a few dp is
 * still a card, one measured to zero is not.
 *
 * <p>And the offer itself has to match the arrangement. A surface that is not on screen has no
 * card, and a handle that describes a shape the surface does not have — a height grip for a bar
 * with no height, pages of apps for a rail that scrolls — is not offered rather than offered dead.
 *
 * <p>Pure: an arrangement and a few pixel counts in, booleans and pixel counts out, so every case
 * is testable without a window.
 */
public final class SurfaceEditorScene {

    /** A drag handle the gesture overlay carries, over and above the surface's own card. */
    public enum Handle {
        /** The dock's size, on its top border. */
        DOCK_HEIGHT,
        /** The keyboard's height, on its top border. */
        KEYBOARD_HEIGHT,
        /** The space under the last key row. */
        KEYBOARD_CHIN
    }

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
        return new SurfaceEditorScene(layout.statusBarEdge,
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

    /**
     * Whether the card raised on a surface offers one of its rows.
     *
     * <p>Only the dock's two rows about its pinned apps are dropped. Both describe the apps row —
     * the size is that row's height, the count is how many apps a page of it holds — and neither
     * has anything to move once the apps stand in a rail: the rail's width is fixed and it scrolls
     * rather than pages. Everything else on the card is the surface's material or its shape, which
     * a dock carrying only the extra keys wears exactly the same.
     */
    public boolean offersRow(@Nullable SurfaceSlot slot, @NonNull String rowId) {
        if (slot != SurfaceSlot.DOCK)
            return true;
        if (SurfaceEditorProperties.ID_SIZE.equals(rowId)
            || SurfaceEditorProperties.ID_APPS.equals(rowId))
            return appsRowShown;
        return true;
    }

    /** Whether the overlay carries one of the surfaces' own drag handles. */
    public boolean offersHandle(@NonNull Handle handle) {
        switch (handle) {
            case DOCK_HEIGHT:
                // The grip rides the dock's top border and drags the pinned apps row's height —
                // the same number the card's size row carries. A rail has no such height.
                return dockRowShown && appsRowShown;
            case KEYBOARD_HEIGHT:
            case KEYBOARD_CHIN:
            default:
                return keyboardShown;
        }
    }

    /**
     * Whether the card parks <em>below</em> the surface it is editing rather than above it. Only a
     * surface fixed to the top of the screen is stood off downward — a bar along the bottom is
     * approached from above, like the dock and the keyboard.
     */
    public boolean surfaceIsAtTop(@Nullable SurfaceSlot slot) {
        return slot == SurfaceSlot.STATUS && statusBarEdge == Edge.TOP;
    }

    // ------------------------------------------------------------------------------- the band

    /**
     * The band the card and the resting pill live in, as {@code {top, bottom}} in the editor
     * host's own coordinates.
     *
     * <p>The ceiling is the system status inset, which a bar standing along the top pushes down to
     * its own lower edge — that bar is the only one the free room begins under. A bar along the
     * bottom lowers the floor to its upper edge instead, and a bar in a column changes neither: a
     * column takes width, not height.
     *
     * <p>The floor is otherwise the top of the accessory stack, which is the dock and the keyboard
     * together, or the foot of the host where nothing is on the stack.
     *
     * <p>Always at least {@code minBandPx} tall and always inside the host: chrome that leaves no
     * room gets a card that overlaps it, never a card parked off the screen.
     *
     * @param statusInsetTopPx where the free room starts before any bar is taken into account
     * @param barTopPx         the status bar surface's upper edge, ignored unless it is on screen
     * @param barBottomPx      its lower edge
     * @param barOnScreen      whether the bar's own rect could be measured at all
     * @param stackTopPx       the top of the accessory stack, or the host's height where it is not
     *                         drawn
     * @param hostHeightPx     the host's own height, which nothing may be parked outside of
     */
    @NonNull
    public int[] freeBandPx(int statusInsetTopPx, int barTopPx, int barBottomPx,
                            boolean barOnScreen, int stackTopPx, int hostHeightPx,
                            int minBandPx) {
        int height = Math.max(0, hostHeightPx);
        int band = Math.max(0, Math.min(minBandPx, height));
        int top = clamp(statusInsetTopPx, 0, height);
        if (barOnScreen && statusBarEdge == Edge.TOP)
            top = Math.max(top, clamp(barBottomPx, 0, height));
        int bottom = clamp(stackTopPx, 0, height);
        if (barOnScreen && statusBarEdge == Edge.BOTTOM)
            bottom = Math.min(bottom, clamp(barTopPx, 0, height));
        if (bottom - top >= band)
            return new int[] {top, bottom};
        // The chrome has left less room than the editor can use. Grow the band back downward, and
        // only then upward, so what it overlaps is the surface furthest from the user's hand.
        bottom = Math.min(height, top + band);
        top = Math.max(0, bottom - band);
        return new int[] {top, bottom};
    }

    /** Everything the offer and the band read, as one number, so a pass that changes nothing is
     *  skipped. */
    public long signature() {
        long signature = statusBarEdge.ordinal();
        signature = signature * 31 + (dockRowShown ? 1 : 0);
        signature = signature * 31 + (appsRowShown ? 1 : 0);
        signature = signature * 31 + (appsRailShown ? 1 : 0);
        signature = signature * 31 + (extraKeysColumnShown ? 1 : 0);
        signature = signature * 31 + (keyboardShown ? 1 : 0);
        return signature * 31 + (floatingDock ? 1 : 0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
