package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fits the bar's entries into {@code maxSlots} slots. When they do not fit, whole runs of
 * consecutive same-kind entries fold into one slot: the windows' run first, then the other runs
 * longest first (a tie folds the rightmost, so Home 1.. on the left stays open). A run of one
 * never folds.
 */
public final class PageBarFold {

    /** One slot: entries {@link #first}..{@code first + count - 1}, all of one kind. */
    public static final class Slot {
        public final int first;
        public final int count;
        @NonNull public final PageKind kind;
        /** What the glyph shows: the entry's number, or the member count when folded. */
        public final int number;
        /** Any member is current. */
        public final boolean current;
        /** Which member is current, 0-based within the slot; -1 when none. */
        public final int currentMember;

        Slot(int first, int count, @NonNull PageKind kind, int number, boolean current,
             int currentMember) {
            this.first = first;
            this.count = count;
            this.kind = kind;
            this.number = number;
            this.current = current;
            this.currentMember = currentMember;
        }

        public boolean folded() {
            return count > 1;
        }
    }

    public static final class Result {
        @NonNull public final List<Slot> slots;
        /** Every run is folded and the slots still do not fit; the layout lets them shrink. */
        public final boolean overflow;

        Result(@NonNull List<Slot> slots, boolean overflow) {
            this.slots = slots;
            this.overflow = overflow;
        }
    }

    private PageBarFold() {
    }

    @NonNull
    public static Result fold(@NonNull List<PageBarModel.Entry> entries, int maxSlots) {
        int n = entries.size();
        // Runs of consecutive same-kind entries: start[r], len[r].
        int[] start = new int[n];
        int[] len = new int[n];
        boolean[] folded = new boolean[n];
        int runs = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0 && entries.get(i).kind == entries.get(i - 1).kind) {
                len[runs - 1]++;
            } else {
                start[runs] = i;
                len[runs] = 1;
                runs++;
            }
        }
        int slots = n;
        while (slots > maxSlots) {
            int pick = -1;
            for (int r = 0; r < runs; r++) {
                if (folded[r] || len[r] < 2) continue;
                if (pick < 0) {
                    pick = r;
                    continue;
                }
                boolean windows = entries.get(start[r]).kind == PageKind.TERMINAL;
                boolean pickWindows = entries.get(start[pick]).kind == PageKind.TERMINAL;
                if (windows != pickWindows) {
                    if (windows) pick = r;
                } else if (len[r] >= len[pick]) {
                    pick = r;
                }
            }
            if (pick < 0) break;
            folded[pick] = true;
            slots -= len[pick] - 1;
        }
        List<Slot> out = new ArrayList<>(slots);
        for (int r = 0; r < runs; r++) {
            if (folded[r]) {
                out.add(slot(entries, start[r], len[r]));
            } else {
                for (int i = start[r]; i < start[r] + len[r]; i++) out.add(slot(entries, i, 1));
            }
        }
        return new Result(Collections.unmodifiableList(out), slots > maxSlots);
    }

    private static Slot slot(List<PageBarModel.Entry> entries, int first, int count) {
        int member = -1;
        for (int m = 0; m < count; m++) {
            if (entries.get(first + m).current) {
                member = m;
                break;
            }
        }
        PageBarModel.Entry head = entries.get(first);
        return new Slot(first, count, head.kind, count > 1 ? count : head.number, member >= 0, member);
    }
}
