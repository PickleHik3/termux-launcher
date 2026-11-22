package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The order the status bar's row draws its items in ({@link StatusRowItem}), as the user set it in
 * the Status bar card, and the dots between them. The preference is a comma list of item keys;
 * reading it drops unknown and repeated keys and appends any missing item in shipped order, so an
 * empty value, an older build's list or a hand-edited one always reads as all nine items.
 */
public final class StatusRowOrder {

    private StatusRowOrder() {}

    /** The shipped order: the order the items are declared in. */
    @NonNull
    public static StatusRowItem[] defaults() {
        return StatusRowItem.values();
    }

    /** The order {@code stored} names, normalised as the class describes. */
    @NonNull
    public static StatusRowItem[] parse(@Nullable String stored) {
        StatusRowItem[] all = StatusRowItem.values();
        StatusRowItem[] out = new StatusRowItem[all.length];
        boolean[] seen = new boolean[all.length];
        int n = 0;
        if (stored != null) {
            for (String part : stored.split(",")) {
                StatusRowItem item = StatusRowItem.forKey(part.trim());
                if (item == null || seen[item.ordinal()]) continue;
                seen[item.ordinal()] = true;
                out[n++] = item;
            }
        }
        for (StatusRowItem item : all) {
            if (!seen[item.ordinal()]) out[n++] = item;
        }
        return out;
    }

    /** {@code order} as the preference stores it. */
    @NonNull
    public static String format(@NonNull StatusRowItem[] order) {
        StringBuilder out = new StringBuilder();
        for (StatusRowItem item : order) {
            if (out.length() > 0) out.append(',');
            out.append(item.key);
        }
        return out.toString();
    }

    /** The one spelling of {@code stored}'s order: equal orders read as equal strings. */
    @NonNull
    public static String normalize(@Nullable String stored) {
        return format(parse(stored));
    }

    /** {@code order} with the item at {@code from} moved to {@code to}, the rest kept in order. */
    @NonNull
    public static StatusRowItem[] move(@NonNull StatusRowItem[] order, int from, int to) {
        StatusRowItem[] out = order.clone();
        if (from < 0 || from >= out.length || to < 0 || to >= out.length || from == to)
            return out;
        StatusRowItem moving = out[from];
        if (from < to) System.arraycopy(out, from + 1, out, from, to - from);
        else System.arraycopy(out, to, out, to + 1, from - to);
        out[to] = moving;
        return out;
    }

    /**
     * The dots of a row whose items, in order, are shown as {@code shown}: the dot before item
     * {@code i} shows exactly when item {@code i} shows and some shown item stands before it, so
     * two shown neighbours have one dot between them and none is left at either end.
     */
    public static void dots(@NonNull boolean[] shown, @NonNull boolean[] out) {
        boolean before = false;
        for (int i = 0; i < shown.length; i++) {
            out[i] = shown[i] && before;
            before |= shown[i];
        }
    }
}
