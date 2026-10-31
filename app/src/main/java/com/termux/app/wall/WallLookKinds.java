package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;

/**
 * The look grid's page kinds read from the wall (ADR 0018 §3): a Home page is Home kind, the
 * Terminal page Terminal kind, and every Display page Display kind, whether or not a window is
 * bound to it. The wall itself and a page the wall does not hold have no kind.
 */
public final class WallLookKinds implements LookGrid.PageKinds {

    @NonNull private final WallPagesStore store;

    public WallLookKinds(@NonNull WallPagesStore store) {
        this.store = store;
    }

    @Nullable
    @Override
    public LookGrid.Kind kindOf(int pageId) {
        if (pageId < 0) return null;
        WallPages.Page page = store.current().page(pageId);
        return page == null ? null : of(page.kind);
    }

    /** The grid's kind for a wall page kind. */
    @NonNull
    public static LookGrid.Kind of(@NonNull WallPages.PageKind kind) {
        switch (kind) {
            case HOME: return LookGrid.Kind.HOME;
            case DISPLAY: return LookGrid.Kind.DISPLAY;
            case TERMINAL:
            default:
                return LookGrid.Kind.TERMINAL;
        }
    }
}
