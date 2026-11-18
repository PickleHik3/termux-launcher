package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.page.Side;

/**
 * Who carries each row through a crossing, read from both ends' layouts as the crossing begins
 * (ADR 0030 section 4 and its amendment). A row both ends show in the same place (the
 * same edge and, along the bottom, the same side of the keyboard) belongs to the stack, like the
 * slab and the keyboard, and stays put; a row only one end shows goes with that end's side; a row
 * the two ends show in different places goes with the side that owns it ({@link Side#owns}). Within a side nothing moves: a layout that differs changes in
 * place at the landing. Each side's extra-keys bar always goes with its own side. The status bar
 * is shared chrome, never a side's row (along the bottom it keeps the landing of the shared stack).
 */
public final class SideRows {

    private SideRows() {}

    /** What carries a row on a crossing's frames. */
    public enum Carrier {
        /** The shared stack: the row stays put. */
        STACK,
        /** Home's side: the row slides with Home. */
        HOME,
        /** The Workspace's side: the row slides with the Workspace. */
        WORKSPACE,
        /** Neither end shows it. */
        NONE
    }

    /** The carrier of {@code element} from {@code from}'s side and layout to {@code to}'s. */
    @NonNull
    public static Carrier carrier(@NonNull Element element, @NonNull Side from,
                                  @NonNull PlaceLayout fromLayout, @NonNull Side to,
                                  @NonNull PlaceLayout toLayout) {
        if (from == to || element == Element.STATUS) return Carrier.STACK;
        // Home has its own extra-keys bar in the pill layer, so the one in the stack is always the
        // Workspace's: it slides with the Workspace whether or not Home's layout lists the row.
        if (element == Element.EXTRA_KEYS) return Carrier.WORKSPACE;
        Slot a = fromLayout.slot(element);
        Slot b = toLayout.slot(element);
        boolean fromShows = !a.hidden;
        boolean toShows = !b.hidden;
        if (fromShows && toShows) {
            if (samePlace(a, b, element, fromLayout, toLayout)) return Carrier.STACK;
            return of(Side.HOME.owns(rowOf(element)) ? Side.HOME : Side.WORKSPACE);
        }
        if (fromShows) return of(from);
        if (toShows) return of(to);
        return Carrier.NONE;
    }

    /**
     * Whether both layouts stand a shown row in the same place: the same edge and, along the
     * bottom, the same side of the keyboard. Read through {@link StackLanes#where}; if that
     * answers NONE for a row the slot shows, the slot's own edge and side decide.
     */
    private static boolean samePlace(@NonNull Slot a, @NonNull Slot b, @NonNull Element element,
                                     @NonNull PlaceLayout fromLayout, @NonNull PlaceLayout toLayout) {
        StackLanes.Where wa = StackLanes.where(fromLayout, element);
        StackLanes.Where wb = StackLanes.where(toLayout, element);
        if (wa != StackLanes.Where.NONE && wb != StackLanes.Where.NONE) return wa == wb;
        if (a.edge != b.edge) return false;
        return a.edge != PlaceLayout.Edge.BOTTOM
            || EdgeStackPolicy.standsUnderKeyboard(fromLayout, element)
                == EdgeStackPolicy.standsUnderKeyboard(toLayout, element);
    }

    /**
     * The carrier of {@code side}'s own extra-keys bar: always its side. When both ends show one,
     * both travel (ADR 0030 section 2); {@link #carrier} leaves the element to the bars.
     */
    @NonNull
    public static Carrier extraKeysCarrier(@NonNull Side side) {
        return of(side);
    }

    /**
     * Whether a landing from {@code from}'s side to {@code to}'s may move rows to new edges: only
     * between pages of one side whose layouts differ. A Home and Workspace landing writes nothing.
     */
    public static boolean landingMayMoveRows(@NonNull Side from, @NonNull Side to,
                                             boolean layoutsDiffer) {
        return from == to && layoutsDiffer;
    }

    /** How far across a row {@code carrier} carries is drawn, given each side's slide. */
    public static float across(@NonNull Carrier carrier, @NonNull float[] sideTx) {
        switch (carrier) {
            case HOME: return sideTx[Side.HOME.ordinal()];
            case WORKSPACE: return sideTx[Side.WORKSPACE.ordinal()];
            default: return 0f;
        }
    }

    @NonNull
    private static Carrier of(@NonNull Side side) {
        return side == Side.HOME ? Carrier.HOME : Carrier.WORKSPACE;
    }

    @NonNull
    private static Side.Row rowOf(@NonNull Element element) {
        return element == Element.AZ ? Side.Row.AZ : Side.Row.APPS;
    }
}
