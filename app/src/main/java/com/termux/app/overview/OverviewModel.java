package com.termux.app.overview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The wall as Overview shows it (spec overview-2026-11-24): one card per wall stop, in wall order,
 * the Terminal page as one card per window of the current session, and the card of the page
 * Overview was opened from marked; under the wall, a row for each Workspace whose place holds more
 * than one session (#357). Pure: it reads the page bar's model, which already holds the wall's
 * order and where each stop goes, so the two cannot disagree; the view only applies it.
 */
public final class OverviewModel {

    /** One card of the strip. */
    public static final class Card {
        @NonNull public final PageKind kind;
        /** The card's name: its own, else the kind and number ("Home 1", "Terminal 2"). */
        @NonNull public final String label;
        /** The page Overview was opened from; it wears the ring. */
        public final boolean origin;
        /** Where a tap goes; the origin's closes Overview and nothing else. */
        @NonNull public final PageBarModel.Target target;
        /** The number a Home page or window carries, from 1; 0 for the rest. */
        public final int number;
        /** The page set as home: a Home page or the Assistant page that opens first. */
        public final boolean home;

        Card(@NonNull PageKind kind, @NonNull String label, boolean origin,
             @NonNull PageBarModel.Target target, int number, boolean home) {
            this.kind = kind;
            this.label = label;
            this.origin = origin;
            this.target = target;
            this.number = number;
            this.home = home;
        }
    }

    /** A session waiting in a Workspace's place, as the place reports it (#357). */
    public static final class Waiting {
        /** Its own name; null for none, and the card shows its number. */
        @Nullable public final String name;
        /** Its number in the place, from 1. */
        public final int number;

        public Waiting(@Nullable String name, int number) {
            this.name = name == null || name.trim().isEmpty() ? null : name;
            this.number = number;
        }
    }

    /** Where the sessions waiting in each Workspace's place come from. */
    public interface Sessions {
        /**
         * The sessions waiting in the place of the Workspace window at row place {@code place},
         * in row order; empty for none (and for a Linux window).
         */
        @NonNull List<Waiting> waiting(int place);
    }

    /** One card of a session row. */
    public static final class Session {
        /** The strip position of the Workspace card whose place it waits in. */
        public final int card;
        /** Its position in the place's row, which a swap asks for. */
        public final int slot;
        @Nullable public final String name;
        public final int number;

        Session(int card, int slot, @NonNull Waiting waiting) {
            this.card = card;
            this.slot = slot;
            this.name = waiting.name;
            this.number = waiting.number;
        }
    }

    /** The row under the wall of a Workspace with more than one session. */
    public static final class Row {
        /** The strip position of its Workspace card. */
        public final int card;
        /** The Workspace's name, for "Sessions of name". */
        @NonNull public final String name;
        @NonNull public final List<Session> sessions;

        Row(int card, @NonNull String name, @NonNull List<Session> sessions) {
            this.card = card;
            this.name = name;
            this.sessions = Collections.unmodifiableList(sessions);
        }
    }

    @NonNull private final List<Card> mCards;
    private final int mOrigin;
    @NonNull private final List<Row> mRows;

    private OverviewModel(@NonNull List<Card> cards, int origin, @NonNull List<Row> rows) {
        mCards = Collections.unmodifiableList(cards);
        mOrigin = origin;
        mRows = Collections.unmodifiableList(rows);
    }

    @NonNull
    public static OverviewModel of(@NonNull PageBarModel wall) {
        return of(wall, -1);
    }

    /**
     * As {@link #of(PageBarModel)}, marking the Home page or Assistant page whose wall page id is
     * {@code homePageId} (-1 for none) as the home page.
     */
    @NonNull
    public static OverviewModel of(@NonNull PageBarModel wall, int homePageId) {
        return of(wall, homePageId, null);
    }

    /**
     * As {@link #of(PageBarModel, int)}, with a session row, in wall order, for each Workspace
     * card whose place {@code sessions} reports sessions waiting in; none when it is null.
     */
    @NonNull
    public static OverviewModel of(@NonNull PageBarModel wall, int homePageId,
                                   @Nullable Sessions sessions) {
        List<PageBarModel.Entry> entries = wall.entries();
        int origin = wall.currentIndex();
        if (origin < 0) {
            // The wall is on a page the strip does not hold: the window to return to stands in.
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).returnTo) {
                    origin = i;
                    break;
                }
            }
        }
        if (origin < 0 && !entries.isEmpty()) origin = 0;
        List<Card> cards = new ArrayList<>(entries.size());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            PageBarModel.Entry e = entries.get(i);
            boolean home = homePageId >= 0 && e.target.pageId == homePageId
                && (e.kind == PageKind.HOME || e.kind == PageKind.ASSISTANT);
            cards.add(new Card(e.kind, e.label, i == origin, e.target, e.number, home));
            if (sessions == null || e.kind != PageKind.TERMINAL || e.target.index < 0) continue;
            List<Waiting> waiting = sessions.waiting(e.target.index);
            if (waiting.isEmpty()) continue;
            List<Session> row = new ArrayList<>(waiting.size());
            for (int slot = 0; slot < waiting.size(); slot++) {
                row.add(new Session(i, slot, waiting.get(slot)));
            }
            rows.add(new Row(i, e.label, row));
        }
        // The minimised windows wait after the wall's stops; a tap brings one back to its place.
        for (PageBarModel.Entry e : wall.minimised()) {
            cards.add(new Card(e.kind, e.label, false, e.target, e.number, false));
        }
        return new OverviewModel(cards, origin, rows);
    }

    /** The cards in wall order. */
    @NonNull public List<Card> cards() {
        return mCards;
    }

    /** The index of the origin card; -1 when there are no cards. */
    public int originIndex() {
        return mOrigin;
    }

    /** The session rows under the wall, in wall order; empty when no place holds more than one. */
    @NonNull public List<Row> rows() {
        return mRows;
    }

    /** The row of the Workspace card at strip position {@code card}; null when it has none. */
    @Nullable public Row rowOf(int card) {
        for (Row row : mRows) if (row.card == card) return row;
        return null;
    }
}
