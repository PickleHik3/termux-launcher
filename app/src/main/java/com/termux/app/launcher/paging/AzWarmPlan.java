package com.termux.app.launcher.paging;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which icons to render ahead when a finger lands on an A–Z letter: the first page of that
 * letter's matches, exactly what the row will draw on release, and nothing past it. Pure, so the
 * "one page, never more" rule is tested without a view.
 */
public final class AzWarmPlan {

    private AzWarmPlan() {}

    /** The entries of A–Z page 0 for {@code ranked} at {@code slots} per page; never null. */
    @NonNull
    public static <T> List<T> firstPage(@NonNull List<T> ranked, int slots) {
        int end = DockPagingModel.azPageEnd(ranked.size(), 0, slots);
        if (end <= 0) return Collections.emptyList();
        return new ArrayList<>(ranked.subList(DockPagingModel.azPageStart(ranked.size(), 0, slots), end));
    }

    /**
     * Whether landing on {@code letter} should start a warm: only when it is a different letter
     * from the one last warmed in this touch, so a finger resting on a letter ranks it once.
     */
    public static boolean shouldWarm(char letter, @NonNull Character lastWarmed) {
        return lastWarmed != letter;
    }
}
