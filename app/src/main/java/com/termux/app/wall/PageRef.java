package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One page of the wall as a crossing and the chrome name it (#173, spec #167 E): its kind, as a
 * {@link PaneWallPage}, and its {@link WallPages} page id, -1 where the launcher has not said
 * which page of that kind it is. A value: two refs to the same kind and id are equal.
 *
 * <p>A crossing's two endpoints, a resolved arrangement and the dock's rows are named by it, so
 * an endpoint can name any page; until a crossing runs between two pages of one kind (Home ↔
 * Home), the readers that hold one value per kind answer by {@link #isHome}.
 */
public final class PageRef {

    /** Home's page, with no id said. */
    @NonNull public static final PageRef HOME = new PageRef(PaneWallPage.WIDGETS, -1);
    /** The Terminal page, with no id said. */
    @NonNull public static final PageRef TERMINAL = new PageRef(PaneWallPage.TERMINAL, -1);

    @NonNull public final PaneWallPage kind;
    /** The page's {@link WallPages} id; -1 while unknown. */
    public final int id;

    private PageRef(@NonNull PaneWallPage kind, int id) {
        this.kind = kind;
        this.id = id < 0 ? -1 : id;
    }

    @NonNull
    public static PageRef of(@NonNull PaneWallPage kind) {
        return of(kind, -1);
    }

    @NonNull
    public static PageRef of(@NonNull PaneWallPage kind, int id) {
        if (id < 0) {
            if (kind == PaneWallPage.WIDGETS) return HOME;
            if (kind == PaneWallPage.TERMINAL) return TERMINAL;
        }
        return new PageRef(kind, id);
    }

    /** Whether this is a Home page; a window's page (the Terminal's, a display's) otherwise. */
    public boolean isHome() {
        return kind == PaneWallPage.WIDGETS;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof PageRef)) return false;
        PageRef that = (PageRef) other;
        return kind == that.kind && id == that.id;
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + id;
    }

    @NonNull
    @Override
    public String toString() {
        return kind + (id < 0 ? "" : "#" + id);
    }
}
