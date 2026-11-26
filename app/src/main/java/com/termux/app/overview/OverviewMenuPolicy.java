package com.termux.app.overview;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.wall.PageCardMenuPolicy;
import com.termux.app.wall.PageOverviewPages;
import com.termux.app.wall.PageOverviewPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a held card of Overview offers and how its removal goes (spec overview-2026-11-24,
 * "Cards"). Pure. A Home page and the Assistant page ask {@link PageCardMenuPolicy} for their rows
 * and {@link PageOverviewPolicy} for the removal rule, the rules Edit pages already keeps; a
 * Workspace card is one window, which the page-level policies do not know, so its two rows and its
 * rule are here. The rows the spec adds later (New session, Save) go in {@link #rows} beside them.
 */
public final class OverviewMenuPolicy {

    private OverviewMenuPolicy() { }

    /** One row of a card's menu. */
    public enum Row {
        /** A Workspace's place gains a session, which stands in front (#357). */
        NEW_SESSION,
        SET_HOME,
        RENAME,
        /** A Home page leaves the wall. */
        REMOVE,
        /** A Workspace window or the Assistant page closes. */
        CLOSE
    }

    /**
     * The rows for a card of {@code kind}, top to bottom. {@code isHome} is whether it is the home
     * page already, {@code homePages} how many Home pages the wall has, {@code hasWindow} whether a
     * Workspace card stands for a window (an empty Terminal page has none to hold).
     */
    @NonNull
    public static List<Row> rows(@NonNull PageKind kind, boolean isHome, int homePages,
                                 boolean hasWindow) {
        switch (kind) {
            case HOME: {
                List<Row> out = new ArrayList<>(3);
                for (PageCardMenuPolicy.Action a : PageCardMenuPolicy.actions(
                        PageOverviewPolicy.Kind.HOME_PAGE, isHome, homePages, false)) {
                    if (a == PageCardMenuPolicy.Action.SET_HOME) out.add(Row.SET_HOME);
                    else if (a == PageCardMenuPolicy.Action.RENAME) out.add(Row.RENAME);
                    else if (a == PageCardMenuPolicy.Action.REMOVE) out.add(Row.REMOVE);
                }
                return out;
            }
            case ASSISTANT: {
                // Rename and Close only: isHome is passed as true so Set as home is not offered.
                List<Row> out = new ArrayList<>(2);
                for (PageCardMenuPolicy.Action a : PageCardMenuPolicy.actions(
                        PageOverviewPolicy.Kind.ASSISTANT, true, homePages, false)) {
                    if (a == PageCardMenuPolicy.Action.RENAME) out.add(Row.RENAME);
                    else if (a == PageCardMenuPolicy.Action.REMOVE) out.add(Row.CLOSE);
                }
                return out;
            }
            case TERMINAL:
                // The spec's Workspace menu: New session, (Save, #359), Rename, Close.
                return hasWindow ? java.util.Arrays.asList(Row.NEW_SESSION, Row.RENAME, Row.CLOSE)
                    : Collections.<Row>emptyList();
            default:
                return Collections.emptyList();
        }
    }

    /** The rows of a held session card in a Workspace's row (#358): Rename and Close. */
    @NonNull
    public static List<Row> sessionRows() {
        return java.util.Arrays.asList(Row.RENAME, Row.CLOSE);
    }

    /**
     * How taking the card away goes: {@link PageOverviewPolicy.Removal#WITH_UNDO} when it holds
     * anything ({@code contents}: a Home page's items and floats, a window's panes), at once when
     * it is empty, refused when the rows offer no removal.
     */
    @NonNull
    public static PageOverviewPolicy.Removal removal(@NonNull PageKind kind, int homePages,
                                                     int contents) {
        if (kind == PageKind.TERMINAL) {
            return contents > 0 ? PageOverviewPolicy.Removal.WITH_UNDO
                : PageOverviewPolicy.Removal.AT_ONCE;
        }
        if (kind != PageKind.HOME && kind != PageKind.ASSISTANT) {
            return PageOverviewPolicy.Removal.REFUSED;
        }
        return PageOverviewPolicy.removal(PageOverviewPages.cardKind(kind), homePages, contents);
    }

    /**
     * The place card {@code index} of the strip has once the card at {@code removed} is really
     * gone (-1 for none): the cards after it move up one.
     */
    public static int indexAfterRemoval(int index, int removed) {
        return removed >= 0 && index > removed ? index - 1 : index;
    }
}
