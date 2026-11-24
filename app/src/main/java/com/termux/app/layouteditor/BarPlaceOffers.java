package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.FrameState;
import com.termux.app.layouteditor.BarLandingPolicy.Hold;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Places;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.statusbar.StatusPlace;

import java.util.ArrayList;
import java.util.List;

/**
 * The dashed places of a selected bar (#341, #342), with no views: what a tap can do for the bar
 * instead of a drag. Every offer is asked of {@link BarLandingPolicy}, so none is made where a drop
 * would be refused.
 *
 * <p>A bar standing on the frame is offered one place on each edge it is not already on (the gap
 * against the content, which a drag refines), the pane when it is the status bar and the layout
 * has one, and Hide. A bar that is put away is offered the one place that brings it back. Moves
 * into a gap under the keyboard stay with the drag.
 */
public final class BarPlaceOffers {

    private BarPlaceOffers() {}

    /** What an offer's button says; the view maps each to its string (the bar's name fills in). */
    public enum Label {
        /** "Move Extra keys here". */
        MOVE_HERE,
        /** "Put it inside the pane". */
        PUT_IN_PANE,
        /** "Hide". */
        HIDE,
        /** "Bring Extra keys back". */
        BRING_BACK
    }

    /** One dashed place or button: its words and what letting a drop there would do. */
    public static final class Offer {
        @NonNull public final Label label;
        @NonNull public final Bar bar;
        /** The landing the policy gives: the place on the frame, and what tapping writes. */
        @NonNull public final Landing landing;

        Offer(@NonNull Label label, @NonNull Bar bar, @NonNull Landing landing) {
            this.label = label;
            this.bar = bar;
            this.landing = landing;
        }

        /** Hide sits on the bar; every other offer sits where the bar would go. */
        public boolean onBar() {
            return label == Label.HIDE;
        }
    }

    /**
     * The offers for {@code bar}. {@code shown} is whether the bar is on the frame now (not put
     * away); {@code state} is the layout as the editor holds it.
     */
    @NonNull
    public static List<Offer> of(@NonNull FrameState state, @NonNull Bar bar, boolean shown) {
        List<Offer> offers = new ArrayList<>(6);
        Places places = BarLandingPolicy.places(state, bar);
        if (!places.available) return offers;
        if (!shown) {
            // The page bar has no edge to come back to: bringing it back is the Hidden column's
            // own landing, written as a setting.
            Hold back = bar == Bar.PAGE_BAR ? Hold.hidden() : restoreHold(state, bar, places);
            Landing landing = back == null ? null : BarLandingPolicy.land(state, bar, back);
            if (landing != null && landing.allowed)
                offers.add(new Offer(Label.BRING_BACK, bar, landing));
            return offers;
        }
        Edge standing = edgeOf(state, bar);
        for (Edge edge : Edge.values()) {
            if (edge == standing || !places.offers(edge)) continue;
            Landing landing = BarLandingPolicy.land(state, bar,
                Hold.edge(edge, places.gapsOn(edge) - 1));
            if (landing.allowed) offers.add(new Offer(Label.MOVE_HERE, bar, landing));
        }
        if (places.pane && !inPane(state, bar)) {
            Landing landing = BarLandingPolicy.land(state, bar, Hold.pane());
            if (landing.allowed) offers.add(new Offer(Label.PUT_IN_PANE, bar, landing));
        }
        if (places.hide) {
            Landing landing = BarLandingPolicy.land(state, bar, Hold.hidden());
            if (landing.allowed) offers.add(new Offer(Label.HIDE, bar, landing));
        }
        return offers;
    }

    /** Whether the status bar stands as the thin row inside the pane. */
    public static boolean inPane(@NonNull FrameState state, @NonNull Bar bar) {
        return bar == Bar.STATUS_BAR && state.statusPlace == StatusPlace.PANE
            && BarLandingPolicy.places(state, bar).pane;
    }

    /** The edge a bar stands on, or null for the status row in the pane and the page bar. */
    @Nullable
    private static Edge edgeOf(@NonNull FrameState state, @NonNull Bar bar) {
        if (bar == Bar.KEYBOARD) return Edge.BOTTOM;
        if (bar.element() == null || inPane(state, bar)) return null;
        return EdgeStackPolicy.edgeOf(state.layout, bar.element());
    }

    /** The gap a put-away bar comes back to: against the content on the edge it left. */
    @Nullable
    private static Hold restoreHold(@NonNull FrameState state, @NonNull Bar bar,
                                    @NonNull Places places) {
        Edge edge = bar == Bar.KEYBOARD ? Edge.BOTTOM
            : bar.element() == null ? null : EdgeStackPolicy.edgeOf(state.layout, bar.element());
        if (edge == null || !places.offers(edge)) return null;
        return Hold.edge(edge, places.gapsOn(edge) - 1);
    }
}
