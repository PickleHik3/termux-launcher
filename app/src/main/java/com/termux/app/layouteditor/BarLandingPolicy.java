package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.statusbar.StatusPlace;

import java.util.List;

/**
 * Where a bar held over the editor's frame may land, and what letting go would reach. The drag
 * (#338), the dashed places of a selected bar (#341), the hint (#343) and the status bar's move
 * into the pane (#342) all ask this one policy, so none of them keeps its own list.
 *
 * <p>Pure, no views: the frame turns a finger into a {@link Hold} (over the Hidden column, over the
 * pane, or over one gap of one edge's stack) and draws the {@link Landing} it gets back. The
 * policy reads the layout as the store holds it (a resolved {@link PlaceLayout}, the layout's
 * {@link LayoutKind}, the status bar's {@link StatusPlace}) and never writes.
 *
 * <p>Edges: every bar a layout stacks may stand on every edge in both orientations, as the store
 * allows today ({@link EdgeStackPolicy#targets}); a layout may place its bars on either edge. The
 * status bar alone may also stand over the pane, on a layout that has panes (not Home), and not
 * under the keyboard. The keyboard has the bottom edge only. Home's page bar has no edge: it has no
 * slot in the store, so it can be put away and brought back and nothing else.
 *
 * <p>Scope ({@link Scope}): the status bar's edge and its place are one value for every page; which
 * bars show, their edge and their order belong to the layout being edited. Putting the status bar
 * away is the layout's too (its put-away mark is in the layout's own key).
 *
 * <p>This sits beside {@link LayoutEditorPlan} rather than under {@code place/}: it answers the
 * editor's question about a held bar, not the launcher's about what is on screen.
 */
public final class BarLandingPolicy {

    private BarLandingPolicy() {}

    /** A bar the user may hold. */
    public enum Bar {
        STATUS_BAR(Element.STATUS),
        APPS_ROW(Element.APPS),
        AZ_INDEX(Element.AZ),
        EXTRA_KEYS(Element.EXTRA_KEYS),
        KEYBOARD(null),
        /** Home's page bar: a Home setting, with no slot of its own in the layout store. */
        PAGE_BAR(null);

        @Nullable private final Element mElement;

        Bar(@Nullable Element element) {
            mElement = element;
        }

        /** The layout store's element for this bar, or null for the keyboard and the page bar. */
        @Nullable
        public Element element() {
            return mElement;
        }
    }

    /**
     * How far a change reaches, for the hint's "Changes ..." clause: every page, the pages of the
     * Home layout, the pages of the Workspace (Terminal) layout, or the Minimal layout.
     */
    public enum Scope {
        EVERY_PAGE, HOME_PAGES, WORKSPACE_PAGES, MINIMAL_LAYOUT;

        /** The scope of the layout of {@code kind}; a retired kind reads as the Terminal's. */
        @NonNull
        public static Scope ofLayout(@NonNull LayoutKind kind) {
            switch (kind.live()) {
                case HOME: return HOME_PAGES;
                case TERMINAL_MINIMAL: return MINIMAL_LAYOUT;
                default: return WORKSPACE_PAGES;
            }
        }
    }

    /** What a held bar is over. */
    public enum Zone { EDGE, PANE, HIDDEN }

    /**
     * Everything the policy reads: the layout as stored for the orientation being edited, the kind
     * of layout it is, and where the status bar stands ({@link StatusPlace}, one value for every
     * page).
     */
    public static final class FrameState {
        @NonNull final PlaceLayout layout;
        @NonNull final PlaceOrientation orientation;
        @NonNull final LayoutKind kind;
        @NonNull final StatusPlace statusPlace;

        public FrameState(@NonNull PlaceLayout layout, @NonNull PlaceOrientation orientation,
                          @NonNull LayoutKind kind, @NonNull StatusPlace statusPlace) {
            this.layout = layout;
            this.orientation = orientation;
            this.kind = kind.live();
            this.statusPlace = statusPlace;
        }
    }

    /**
     * Where the held bar is, as the frame found it. Geometry stays with the frame: an edge hold
     * names the gap in that edge's stack (0 outermost, as {@link EdgeStackPolicy.Drop#index}) the
     * finger is nearest.
     */
    public static final class Hold {
        @NonNull public final Zone zone;
        @Nullable public final Edge edge;
        public final int index;
        public final boolean underKeyboard;

        private Hold(@NonNull Zone zone, @Nullable Edge edge, int index, boolean underKeyboard) {
            this.zone = zone;
            this.edge = edge;
            this.index = index;
            this.underKeyboard = underKeyboard;
        }

        @NonNull public static Hold hidden() { return new Hold(Zone.HIDDEN, null, 0, false); }

        @NonNull public static Hold pane() { return new Hold(Zone.PANE, null, 0, false); }

        /** The gap at {@code index} of {@code edge}'s stack (over the keyboard on the bottom). */
        @NonNull
        public static Hold edge(@NonNull Edge edge, int index) {
            return new Hold(Zone.EDGE, edge, index, false);
        }

        /** The gap at {@code index} among the bottom bands under the keyboard. */
        @NonNull
        public static Hold underKeyboard(int index) {
            return new Hold(Zone.EDGE, Edge.BOTTOM, index, true);
        }
    }

    /**
     * What letting go would do. When {@link #allowed} is false nothing else is meaningful but
     * {@link #zone}; the hint says "Not here". The slot line is the gap: {@link #edge},
     * {@link #index} and {@link #underKeyboard} for an edge, the top of the pane for
     * {@link Zone#PANE}, none for the Hidden column.
     */
    public static final class Landing {
        public final boolean allowed;
        @NonNull public final Zone zone;
        @Nullable public final Edge edge;
        public final int index;
        public final boolean underKeyboard;
        /**
         * For the status bar, the place it takes: {@link StatusPlace#PANE} over the pane, the strip
         * on any edge; null for every other bar and for hiding.
         */
        @Nullable public final StatusPlace statusPlace;
        /** How far the change reaches; null when not allowed. */
        @Nullable public final Scope scope;

        Landing(boolean allowed, @NonNull Zone zone, @Nullable Edge edge, int index,
                boolean underKeyboard, @Nullable StatusPlace statusPlace, @Nullable Scope scope) {
            this.allowed = allowed;
            this.zone = zone;
            this.edge = edge;
            this.index = index;
            this.underKeyboard = underKeyboard;
            this.statusPlace = statusPlace;
            this.scope = scope;
        }

        static Landing refused(@NonNull Hold hold) {
            return new Landing(false, hold.zone, hold.edge, hold.index, hold.underKeyboard, null,
                null);
        }
    }

    /** Every place one bar may be held over, for the dashed places and the hint at rest. */
    public static final class Places {
        /** Whether the bar exists on this layout at all (the page bar is Home's alone). */
        public final boolean available;
        /** Over the pane: the status bar only, and not on Home. */
        public final boolean pane;
        /** Over the Hidden column. */
        public final boolean hide;
        /** The gaps under the keyboard on the bottom edge. */
        public final int underKeyboardGaps;
        /** How far moving the bar reaches; null when it cannot move. */
        @Nullable public final Scope scope;
        /** How far putting it away or bringing it back reaches; null when it cannot. */
        @Nullable public final Scope hideScope;
        private final int[] mGaps;

        Places(boolean available, boolean pane, boolean hide, int underKeyboardGaps,
               @Nullable Scope scope, @Nullable Scope hideScope, int[] gaps) {
            this.available = available;
            this.pane = pane;
            this.hide = hide;
            this.underKeyboardGaps = underKeyboardGaps;
            this.scope = scope;
            this.hideScope = hideScope;
            mGaps = gaps;
        }

        /** How many gaps this edge offers (over the keyboard on the bottom). */
        public int gapsOn(@NonNull Edge edge) {
            return mGaps[edge.ordinal()];
        }

        public boolean offers(@NonNull Edge edge) {
            return gapsOn(edge) > 0;
        }
    }

    private static final Places NOWHERE = new Places(false, false, false, 0, null, null, new int[4]);

    /** Everywhere {@code bar} may be held over, given the frame as it stands. */
    @NonNull
    public static Places places(@NonNull FrameState state, @NonNull Bar bar) {
        Scope layoutScope = Scope.ofLayout(state.kind);
        int[] gaps = new int[Edge.values().length];
        switch (bar) {
            case PAGE_BAR:
                if (state.kind != LayoutKind.HOME) return NOWHERE;
                return new Places(true, false, true, 0, null, layoutScope, gaps);
            case KEYBOARD:
                gaps[Edge.BOTTOM.ordinal()] = 1;
                return new Places(true, false, true, 0, layoutScope, layoutScope, gaps);
            default:
                break;
        }
        Element element = bar.element();
        if (element == null) return NOWHERE;
        for (EdgeStackPolicy.Drop drop : EdgeStackPolicy.targets(state.layout, element,
            state.orientation)) {
            gaps[drop.edge.ordinal()]++;
        }
        int under = EdgeStackPolicy.underKeyboardTargets(state.layout, element).size();
        boolean status = bar == Bar.STATUS_BAR;
        boolean pane = status && state.kind != LayoutKind.HOME;
        return new Places(true, pane, element.hideAllowed(), under,
            status ? Scope.EVERY_PAGE : layoutScope, layoutScope, gaps);
    }

    /** What letting go of {@code bar} at {@code hold} would do. */
    @NonNull
    public static Landing land(@NonNull FrameState state, @NonNull Bar bar, @NonNull Hold hold) {
        Places places = places(state, bar);
        if (!places.available) return Landing.refused(hold);
        switch (hold.zone) {
            case HIDDEN:
                return places.hide
                    ? new Landing(true, Zone.HIDDEN, null, 0, false, null, places.hideScope)
                    : Landing.refused(hold);
            case PANE:
                return places.pane
                    ? new Landing(true, Zone.PANE, null, 0, false, StatusPlace.PANE, places.scope)
                    : Landing.refused(hold);
            default:
                break;
        }
        Edge edge = hold.edge;
        if (edge == null || hold.index < 0) return Landing.refused(hold);
        int gaps = hold.underKeyboard ? places.underKeyboardGaps : places.gapsOn(edge);
        if (hold.index >= gaps) return Landing.refused(hold);
        return new Landing(true, Zone.EDGE, edge, hold.index, hold.underKeyboard,
            bar == Bar.STATUS_BAR ? StatusPlace.STRIP : null, places.scope);
    }

    /** The gaps' edges in {@link Edge} order, for a caller outlining one region per edge. */
    @NonNull
    public static List<Edge> edges(@NonNull Places places) {
        List<Edge> edges = new java.util.ArrayList<>(4);
        for (Edge edge : Edge.values()) if (places.offers(edge)) edges.add(edge);
        return edges;
    }
}
