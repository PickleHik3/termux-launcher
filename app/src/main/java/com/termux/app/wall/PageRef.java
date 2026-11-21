package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.app.page.PageKind;

/**
 * One page of the wall as a crossing and the chrome name it (#173, spec #167 E): its kind, as a
 * {@link PageKind}, and its {@link WallPages} page id, -1 where the launcher has not said
 * which page of that kind it is. A value: two refs to the same kind and id are equal.
 *
 * <p>A crossing's two endpoints, a resolved arrangement and the dock's rows are named by it, so
 * an endpoint can name any page; until a crossing runs between two pages of one kind (Home ↔
 * Home), the readers that hold one value per kind answer by {@link #isHome}.
 */
public final class PageRef {

    /** Home's page, with no id said. */
    @NonNull public static final PageRef HOME = new PageRef(PageKind.HOME, -1);
    /** The Terminal page, with no id said. */
    @NonNull public static final PageRef TERMINAL = new PageRef(PageKind.TERMINAL, -1);
    /** The Assistant page (ADR 0022): a kind of its own, never read as a window's. */
    @NonNull public static final PageRef ASSISTANT = new PageRef(PageKind.ASSISTANT, -1);

    @NonNull public final PageKind kind;
    /** The page's {@link WallPages} id; -1 while unknown. */
    public final int id;

    private PageRef(@NonNull PageKind kind, int id) {
        this.kind = kind;
        this.id = id < 0 ? -1 : id;
    }

    @NonNull
    public static PageRef of(@NonNull PageKind kind) {
        return of(kind, -1);
    }

    @NonNull
    public static PageRef of(@NonNull PageKind kind, int id) {
        if (id < 0) {
            if (kind == PageKind.HOME) return HOME;
            if (kind == PageKind.TERMINAL) return TERMINAL;
            if (kind == PageKind.ASSISTANT) return ASSISTANT;
        }
        return new PageRef(kind, id);
    }

    /** Whether this is a Home page; a window's page (the Terminal's, a display's) otherwise. */
    public boolean isHome() {
        return kind == PageKind.HOME;
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
