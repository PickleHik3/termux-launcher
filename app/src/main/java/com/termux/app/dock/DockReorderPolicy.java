package com.termux.app.dock;

import com.termux.app.launcher.widget.HomeFolderPolicy;

/**
 * The live reorder of the dock's pinned row while an icon is carried over it. The icons on the
 * page in view part around a gap once the finger has hovered a slot for {@link #PART_HOVER_MS},
 * and a drop lands in the slot under the finger; resting on an icon for {@link #FOLDER_REST_MS}
 * arms it to take the carried icon as a folder. Pure: times are passed in, nothing reads a clock.
 *
 * <p>One instance serves a whole drag and is reused for the next ({@link #begin}). The items are
 * the page's drawn icons by page-local slot; the carried icon's own slot is the {@code source}
 * when it was lifted from this page. The others, in order, are the list L, and the shown gap is
 * an insertion position in L: at rest it is the source's slot (nothing moves), or -1 for an icon
 * from outside (no gap until one is committed).
 */
public final class DockReorderPolicy {

    /** How long a finger hovers a slot before the icons part to open it. */
    public static final long PART_HOVER_MS = 650L;
    /** The parting's slide. */
    public static final long SLIDE_MS = 150L;
    /** How long a finger rests on an icon before it takes the carried icon as a folder: Home's. */
    public static final long FOLDER_REST_MS = HomeFolderPolicy.MERGE_HOVER_MS;

    /** What a release does. */
    public enum Kind {
        /** Nothing moves: the icon goes back where it was, or there was nothing under it. */
        NONE,
        /** The icon goes in at {@link #index()}, an insertion position among the other icons. */
        INSERT,
        /** The icon joins the item {@link #index()} as a folder. */
        MERGE
    }

    private static final int CAND_NONE = 0;
    private static final int CAND_INSERT = 1;
    private static final int CAND_MERGE = 2;

    private int count;
    private int perPage = 1;
    private int source = -1;
    private boolean outsideFull;
    private boolean[] mergeable = new boolean[8];

    private int gap = -1;
    private int armed = -1;

    private int candKind = CAND_NONE;
    /** The merge candidate's item, or the insertion candidate's position in L. */
    private int candValue = -1;
    /** The insertion a merge candidate falls back to: the item's slot, by the side hovered. */
    private int candInsert = -1;
    private long candSince;

    private Kind kind = Kind.NONE;
    private int index = -1;

    /**
     * Starts a drag over a page.
     *
     * @param count     how many items the page in view draws
     * @param perPage   how many a page holds
     * @param source    the page-local slot of the item lifted from this page, or -1
     * @param mergeable whether item i can take the carried item as a folder; read here, not kept
     */
    public void begin(int count, int perPage, int source, boolean[] mergeable) {
        this.count = Math.max(0, count);
        this.perPage = Math.max(1, perPage);
        this.source = source >= 0 && source < this.count ? source : -1;
        this.outsideFull = this.source < 0 && this.count >= this.perPage;
        if (this.mergeable.length < this.count) this.mergeable = new boolean[this.count];
        for (int i = 0; i < this.count; i++) {
            this.mergeable[i] = mergeable != null && i < mergeable.length && mergeable[i];
        }
        leave();
    }

    /** Back to rest: the gap at the source (or none), nothing armed, no candidate. */
    public void leave() {
        gap = source;
        armed = -1;
        candKind = CAND_NONE;
        candValue = -1;
        candInsert = -1;
        kind = Kind.NONE;
        index = -1;
    }

    /**
     * The finger is over page-local {@code slot} (as drawn now), {@code ratio} across it.
     *
     * @return whether the gap or the armed item changed
     */
    public boolean hover(int slot, float ratio, long nowMs) {
        int listSize = listSize();
        int max = outsideFull ? perPage - 1 : listSize;
        int newKind;
        int value;
        int insert;
        if (slot < 0) slot = 0;
        if (gap >= 0 && slot == gap) {
            newKind = CAND_INSERT;
            insert = gap;
            value = gap;
        } else {
            int p = gap >= 0 && slot > gap ? slot - 1 : slot;
            if (p >= listSize) {
                newKind = CAND_INSERT;
                insert = listSize;
                value = insert;
            } else {
                int item = itemAt(p);
                insert = p + (ratio >= 0.5f ? 1 : 0);
                if (DockDropPolicy.onIcon(ratio) && mergeable[item]) {
                    newKind = CAND_MERGE;
                    value = item;
                } else {
                    newKind = CAND_INSERT;
                    value = insert;
                }
            }
        }
        insert = Math.max(0, Math.min(max, insert));
        if (newKind == CAND_INSERT) value = insert;
        boolean changed = false;
        if (newKind != candKind || value != candValue) {
            candKind = newKind;
            candValue = value;
            candSince = nowMs;
            if (armed >= 0) {
                armed = -1;
                changed = true;
            }
        }
        candInsert = insert;
        return changed;
    }

    /** Commits what is due; returns whether the gap or the armed item changed. */
    public boolean tick(long nowMs) {
        long due = nextDueMs();
        if (due < 0 || nowMs < due) return false;
        if (candKind == CAND_MERGE) {
            armed = candValue;
        } else {
            gap = candValue;
        }
        return true;
    }

    /** When {@link #tick} next has something to commit, or -1 when nothing is pending. */
    public long nextDueMs() {
        if (candKind == CAND_INSERT && candValue != gap) return candSince + PART_HOVER_MS;
        if (candKind == CAND_MERGE && armed != candValue) return candSince + FOLDER_REST_MS;
        return -1L;
    }

    /** The shown gap, an insertion position in L; -1 for none. */
    public int gap() {
        return gap;
    }

    /** The item armed to take the carried one as a folder, or -1. */
    public int armed() {
        return armed;
    }

    /** The page-local slot item {@code item} is shown at; the source keeps its own. */
    public int shownSlot(int item) {
        if (item == source) return item;
        int p = listPos(item);
        return gap >= 0 && p >= gap ? p + 1 : p;
    }

    /** Whether item {@code item} is parted off the end of the page. */
    public boolean pushed(int item) {
        return item != source && shownSlot(item) >= perPage;
    }

    /**
     * Settles a release: MERGE on the armed item while it is still the candidate; otherwise an
     * insert at the candidate's insertion, which lands in the slot under the finger even before
     * the parting showed; NONE when the icon would go back where it was or nothing was under it.
     * Read the result with {@link #kind()} and {@link #index()}.
     */
    public Kind release() {
        if (candKind == CAND_NONE) {
            kind = Kind.NONE;
            index = -1;
        } else if (candKind == CAND_MERGE && armed == candValue) {
            kind = Kind.MERGE;
            index = candValue;
        } else {
            int at = candKind == CAND_MERGE ? candInsert : candValue;
            if (source >= 0 && at == source) {
                kind = Kind.NONE;
                index = -1;
            } else {
                kind = Kind.INSERT;
                index = at;
            }
        }
        return kind;
    }

    public Kind kind() {
        return kind;
    }

    /** For INSERT the insertion position in L, for MERGE the item. */
    public int index() {
        return index;
    }

    /** How many items L holds: the page's items without the source. */
    public int listSize() {
        return source >= 0 ? count - 1 : count;
    }

    /** The item at position {@code p} of L. */
    public int itemAt(int p) {
        return source >= 0 && p >= source ? p + 1 : p;
    }

    /** The position in L of {@code item} (not the source). */
    public int listPos(int item) {
        return source >= 0 && item > source ? item - 1 : item;
    }
}
