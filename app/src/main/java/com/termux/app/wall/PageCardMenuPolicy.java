package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * What Edit pages offers on a page card's long press, and in the Display picker (ADR 0015
 * section 2). Pure: the overview asks which rows to build, in order, and builds them.
 */
public final class PageCardMenuPolicy {

    private PageCardMenuPolicy() {}

    /** One row of a card's menu. */
    public enum Action {
        /** The card's page becomes the home page. */
        SET_HOME,
        /** The page takes a name of its own. */
        RENAME,
        /** A Display page shows the whole display or one Linux app. */
        BIND_APP,
        /** The page leaves the wall, with the undo its removal already has. */
        REMOVE
    }

    /**
     * The rows of the menu for a card of {@code kind}, top to bottom. {@code isHome} is whether
     * it is the home page already; {@code firstTerminal} whether it is the first terminal window,
     * the one that stands for the Terminal page; {@code homePages} how many Home pages there are.
     * Set as home shows only where the card can be home and is not; Rename on Home and Display
     * pages; Bind app on Display pages; Remove wherever the card's red button shows.
     */
    @NonNull
    public static List<Action> actions(@NonNull PageOverviewPolicy.Kind kind, boolean isHome,
                                       boolean firstTerminal, int homePages) {
        List<Action> out = new ArrayList<>(4);
        if (PageOverviewPolicy.showsHomeButton(kind, firstTerminal) && !isHome) {
            out.add(Action.SET_HOME);
        }
        if (kind != PageOverviewPolicy.Kind.TERMINAL) out.add(Action.RENAME);
        if (kind == PageOverviewPolicy.Kind.DISPLAY) out.add(Action.BIND_APP);
        if (PageOverviewPolicy.canDelete(kind, homePages)) out.add(Action.REMOVE);
        return out;
    }

    /**
     * The Linux apps the Display picker lists after "Whole display", in catalog order: every app
     * not already bound to a Display page, since a drawer tap goes to the one page bound to it.
     * {@code keep} (the binding of the page being bound, or null) stays listed.
     */
    @NonNull
    public static List<String> pickableApps(@NonNull List<String> catalog,
                                            @NonNull Collection<String> bound,
                                            @Nullable String keep) {
        if (catalog.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>(catalog.size());
        for (String id : catalog) {
            if (id == null || out.contains(id)) continue;
            if (bound.contains(id) && !id.equals(keep)) continue;
            out.add(id);
        }
        return out;
    }

    /** The name a card shows: the page's own name when it has one, else its default name. */
    @NonNull
    public static String cardName(@Nullable String ownName, @NonNull String defaultName) {
        return ownName == null || ownName.trim().isEmpty() ? defaultName : ownName.trim();
    }
}
