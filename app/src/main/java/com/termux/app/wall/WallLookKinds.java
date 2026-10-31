package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;

import java.util.List;

/**
 * The look grid's page kinds read from the wall (ADR 0018 §3): a Home page is Home kind, the
 * Terminal page Terminal kind, and every Display page Display kind, whether or not a window is
 * bound to it. The wall itself, a page pending removal and a page the wall does not hold have no
 * kind.
 *
 * <p>{@link #kindOf} runs inside every {@link LookGrid#resolve}, many times per chrome pass, so it
 * reads a table built once per wall value: the store hands out one immutable {@link WallPages}
 * until the wall changes, and the table is rebuilt only when that reference does. At rest a
 * lookup is a reference compare and a scan of two small arrays, with no allocation.</p>
 */
public final class WallLookKinds implements LookGrid.PageKinds {

    @NonNull private final WallPagesStore store;

    /** The wall value the table was built from, and the table: page ids and their kinds. */
    @Nullable private WallPages tableOf;
    @NonNull private int[] ids = new int[0];
    @NonNull private LookGrid.Kind[] kinds = new LookGrid.Kind[0];

    public WallLookKinds(@NonNull WallPagesStore store) {
        this.store = store;
    }

    @Nullable
    @Override
    public synchronized LookGrid.Kind kindOf(int pageId) {
        if (pageId < 0) return null;
        WallPages pages = store.current();
        if (pages != tableOf) rebuild(pages);
        for (int i = 0; i < ids.length; i++)
            if (ids[i] == pageId) return kinds[i];
        return null;
    }

    private void rebuild(@NonNull WallPages pages) {
        List<WallPages.Page> all = pages.allPages();
        int count = 0;
        for (WallPages.Page page : all) if (pages.page(page.id) != null) count++;
        int[] newIds = new int[count];
        LookGrid.Kind[] newKinds = new LookGrid.Kind[count];
        int i = 0;
        for (WallPages.Page page : all) {
            if (pages.page(page.id) == null) continue;
            newIds[i] = page.id;
            newKinds[i] = of(page.kind);
            i++;
        }
        ids = newIds;
        kinds = newKinds;
        tableOf = pages;
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
