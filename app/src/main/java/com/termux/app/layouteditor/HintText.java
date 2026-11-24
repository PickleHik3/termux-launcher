package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.Scope;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;

import java.util.Objects;

/**
 * The editor's one hint at the foot of the Hidden column (#343): which line it says, for which bar
 * and how far the change reaches. Pure; the view maps {@link Line} to a string and the bar and
 * {@link Scope} to their names, so the wording lives in strings only. A hint is a value: the view
 * sets its text only when the new one is not equal to the one showing.
 */
public final class HintText {

    /** The sentences the hint can say. */
    public enum Line {
        /** At rest, nothing hidden. */
        REST,
        /** At rest, something is hidden: it can be dragged back. */
        REST_HIDDEN,
        /** A bar is selected and shown; it can move and hide. */
        SELECTED_MOVABLE,
        /** A bar is selected and shown; it can only be hidden. */
        SELECTED_HIDE_ONLY,
        /** A hidden bar is selected. */
        SELECTED_HIDDEN,
        /** Held over a place it may not go. */
        DRAG_REFUSED,
        /** Held over an edge slot. */
        DRAG_PLACE,
        /** Held over the pane. */
        DRAG_PANE,
        /** Held over the Hidden column. */
        DRAG_HIDE
    }

    @NonNull public final Line line;
    /** The bar the line names; null for the rest and refused lines. */
    @Nullable public final Bar bar;
    /** How far the change reaches; null when the line says none. */
    @Nullable public final Scope scope;

    private HintText(@NonNull Line line, @Nullable Bar bar, @Nullable Scope scope) {
        this.line = line;
        this.bar = bar;
        this.scope = scope;
    }

    /** Nothing selected, nothing held. */
    @NonNull
    public static HintText atRest(boolean anythingHidden) {
        return new HintText(anythingHidden ? Line.REST_HIDDEN : Line.REST, null, null);
    }

    /**
     * {@code bar} is selected. {@code moveScope} and {@code hideScope} are the policy's
     * {@code Places.scope} and {@code Places.hideScope}; a hidden bar reads the hide one.
     */
    @NonNull
    public static HintText selected(@NonNull Bar bar, boolean hidden, @Nullable Scope moveScope,
                                    @Nullable Scope hideScope) {
        if (hidden)
            return new HintText(Line.SELECTED_HIDDEN, bar, hideScope);
        if (moveScope != null)
            return new HintText(Line.SELECTED_MOVABLE, bar, moveScope);
        return new HintText(Line.SELECTED_HIDE_ONLY, bar, hideScope);
    }

    /** {@code bar} is held where letting go would do what {@code landing} says. */
    @NonNull
    public static HintText dragging(@NonNull Bar bar,
                                    @Nullable BarLandingPolicy.Landing landing) {
        if (landing == null || !landing.allowed)
            return new HintText(Line.DRAG_REFUSED, null, null);
        Scope scope = landing.scope;
        if (landing.zone == Zone.HIDDEN)
            return new HintText(Line.DRAG_HIDE, bar, scope);
        if (landing.zone == Zone.PANE)
            return new HintText(Line.DRAG_PANE, bar, scope);
        return new HintText(Line.DRAG_PLACE, bar, scope);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof HintText))
            return false;
        HintText that = (HintText) other;
        return line == that.line && bar == that.bar && scope == that.scope;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, bar, scope);
    }
}
