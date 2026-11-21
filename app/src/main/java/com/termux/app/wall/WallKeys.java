package com.termux.app.wall;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.termux.app.page.PageKind;

/**
 * The wall keys' one source (ADR 0016 section 7): a wall key (Home, Terminal, Display) is on the
 * extra-keys row exactly when the arranged page list has a page of that kind, whatever page the
 * wall is on and however it got there. Nothing here reads the layout's cells or the display's
 * state, so a Display page is on the row before the display has ever run.
 */
public final class WallKeys {

    private WallKeys() {}

    /**
     * The page kinds the arranged list holds, in the order of their first page. Under Terminal only
     * there is no Home page, so no Home kind, whatever the list still holds of it.
     */
    @NonNull
    public static List<PageKind> kinds(@NonNull WallPages pages, @NonNull UseAs useAs) {
        List<PageKind> out = new ArrayList<>(3);
        for (WallPages.Page page : pages.pages()) {
            PageKind kind = page.kind;
            if (kind == PageKind.HOME && !useAs.showsHomePage()) continue;
            if (!out.contains(kind)) out.add(kind);
        }
        return Collections.unmodifiableList(out);
    }
}
