package com.termux.app.overview;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The wall as Overview shows it (spec overview-2026-11-24): one card per wall stop, in wall order,
 * the Terminal page as one card per window of the current session, and the card of the page
 * Overview was opened from marked. Pure: it reads the page bar's model, which already holds the
 * wall's order and where each stop goes, so the two cannot disagree; the view only applies it.
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

        Card(@NonNull PageKind kind, @NonNull String label, boolean origin,
             @NonNull PageBarModel.Target target) {
            this.kind = kind;
            this.label = label;
            this.origin = origin;
            this.target = target;
        }
    }

    @NonNull private final List<Card> mCards;
    private final int mOrigin;

    private OverviewModel(@NonNull List<Card> cards, int origin) {
        mCards = Collections.unmodifiableList(cards);
        mOrigin = origin;
    }

    @NonNull
    public static OverviewModel of(@NonNull PageBarModel wall) {
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
        for (int i = 0; i < entries.size(); i++) {
            PageBarModel.Entry e = entries.get(i);
            cards.add(new Card(e.kind, e.label, i == origin, e.target));
        }
        return new OverviewModel(cards, origin);
    }

    /** The cards in wall order. */
    @NonNull public List<Card> cards() {
        return mCards;
    }

    /** The index of the origin card; -1 when there are no cards. */
    public int originIndex() {
        return mOrigin;
    }
}
