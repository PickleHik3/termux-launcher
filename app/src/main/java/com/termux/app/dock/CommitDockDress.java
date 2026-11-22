package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * The dock is dressed once per landing. A landing's commit runs the place pass, which dresses the
 * dock when the arriving layout's Dock design or Drawer tile differs from the one applied, and
 * then the Dock design step, which dresses it again when the two pages' designs differ: on the
 * same inputs, one dress after the other in the same frame (about 1.7 ms each on pong). The
 * activity {@link #begin}s each commit, notes every dress made inside it with what it was made
 * for, and asks {@link #decide} at the design step.
 *
 * <p>Pure: nothing here reads the views. Outside a commit nothing is ever worn.
 */
public final class CommitDockDress {

    /** What the design step does. */
    public enum Action {
        /** A pictured dip already dressed the slab for this page. */
        NONE,
        /** One dress on both pages: only the Drawer tile's place is synced. */
        SYNC_DRAWER_TILE,
        /** The designs differ and the commit has not yet dressed the dock for these inputs. */
        CANCEL_EASE_AND_RESTYLE,
        /** The designs differ, and this commit's place pass already dressed the dock for them. */
        ALREADY_WORN,
    }

    /**
     * Everything a dock dress reads that a commit can move between the place pass and the design
     * step: the page and the rows it dresses, the design, Search field and Drawer tile, the store's
     * revision, the status bar's fold and edge, the keyboard, and how many half re-dresses ran.
     */
    public static final class Inputs {
        @Nullable final Object page;
        @Nullable final Object rowsPage;
        @Nullable final Object design;
        final boolean searchField;
        final boolean drawerTile;
        final int revision;
        final boolean statusCompact;
        @Nullable final Object statusEdge;
        final boolean keyboardUp;
        final int halfRedresses;

        public Inputs(@Nullable Object page, @Nullable Object rowsPage, @Nullable Object design,
                      boolean searchField, boolean drawerTile, int revision, boolean statusCompact,
                      @Nullable Object statusEdge, boolean keyboardUp, int halfRedresses) {
            this.page = page;
            this.rowsPage = rowsPage;
            this.design = design;
            this.searchField = searchField;
            this.drawerTile = drawerTile;
            this.revision = revision;
            this.statusCompact = statusCompact;
            this.statusEdge = statusEdge;
            this.keyboardUp = keyboardUp;
            this.halfRedresses = halfRedresses;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Inputs)) return false;
            Inputs other = (Inputs) o;
            return searchField == other.searchField && drawerTile == other.drawerTile
                && revision == other.revision && statusCompact == other.statusCompact
                && keyboardUp == other.keyboardUp && halfRedresses == other.halfRedresses
                && Objects.equals(page, other.page) && Objects.equals(rowsPage, other.rowsPage)
                && Objects.equals(design, other.design)
                && Objects.equals(statusEdge, other.statusEdge);
        }

        @Override
        public int hashCode() {
            return Objects.hash(page, rowsPage, design, searchField, drawerTile, revision,
                statusCompact, statusEdge, keyboardUp, halfRedresses);
        }
    }

    private boolean mOpen;
    @Nullable private Inputs mDressed;

    /** A commit starts: nothing is dressed in it yet. */
    public void begin() {
        mOpen = true;
        mDressed = null;
    }

    /** The commit is over. */
    public void end() {
        mOpen = false;
        mDressed = null;
    }

    /** Whether a commit is under way, so a dress is worth noting. */
    public boolean open() {
        return mOpen;
    }

    /** The dock was dressed for {@code inputs}; noted only inside a commit. */
    public void dressed(@NonNull Inputs inputs) {
        if (mOpen) mDressed = inputs;
    }

    /** Whether this commit already dressed the dock for exactly {@code now}. */
    public boolean worn(@NonNull Inputs now) {
        return mOpen && mDressed != null && mDressed.equals(now);
    }

    /**
     * The design step's action. {@code travelledHere}: a pictured dip dressed the slab for the
     * page. {@code sameDress}: both pages wear one design, Search field and Drawer tile.
     * {@code worn}: this commit already dressed the dock for the inputs as they stand.
     * {@code easeRunning}: a band ease is under way, so the dress made beside it stood at the
     * ease's start and the landing has to dress it again at its end.
     */
    @NonNull
    public static Action decide(boolean travelledHere, boolean sameDress, boolean worn,
                                boolean easeRunning) {
        if (travelledHere) return Action.NONE;
        if (sameDress) return Action.SYNC_DRAWER_TILE;
        if (worn && !easeRunning) return Action.ALREADY_WORN;
        return Action.CANCEL_EASE_AND_RESTYLE;
    }
}
