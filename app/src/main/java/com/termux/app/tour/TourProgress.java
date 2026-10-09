package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The card's progress: one group of segments per chapter, one segment per lesson, and the
 * "1 of 3" the chapter label reads.
 *
 * <p>Counted over the lessons this run actually shows. A lesson the phone has no use for — the
 * border drag on a terminal-only install, where there is no other place to drag to — is not drawn
 * at all rather than drawn as done: a segment the user never earned would say they had. Its chapter
 * keeps the lessons it has left, so the groups re-count and every segment stands for a card the
 * user will see. A chapter left with no lesson at all drops out, and the label counts what is left.
 *
 * <p>Pure, so the whole table is a unit test.
 */
public final class TourProgress {

    /** One segment's state. */
    public enum Segment { DONE, CURRENT, TODO }

    /** One list of segments per chapter shown, in the order the run teaches them. */
    @NonNull public final List<List<Segment>> groups;
    /** The chapter of the card that is up, from 1; 0 when that card is not a lesson. */
    public final int chapterNumber;
    /** How many chapters the run shows. */
    public final int chapterCount;

    private TourProgress(@NonNull List<List<Segment>> groups, int chapterNumber, int chapterCount) {
        this.groups = groups;
        this.chapterNumber = chapterNumber;
        this.chapterCount = chapterCount;
    }

    /**
     * @param steps the run, closing card included
     * @param dropped ids of the cards this run walks past
     * @param currentIndex the card that is up, as an index into {@code steps}
     * @param currentDone whether the card that is up has just been cleared and is saying so
     */
    @NonNull
    public static TourProgress of(@NonNull List<TourStep> steps, @Nullable Set<String> dropped,
                                  int currentIndex, boolean currentDone) {
        List<Integer> chapters = new ArrayList<>();
        List<List<Segment>> groups = new ArrayList<>();
        int chapterNumber = 0;
        for (int i = 0; i < steps.size(); i++) {
            TourStep step = steps.get(i);
            if (step.isClosingCard() || (dropped != null && dropped.contains(step.id))) continue;
            int group = chapters.indexOf(step.chapter);
            if (group < 0) {
                chapters.add(step.chapter);
                groups.add(new ArrayList<>());
                group = groups.size() - 1;
            }
            Segment segment;
            if (i < currentIndex) segment = Segment.DONE;
            else if (i == currentIndex) segment = currentDone ? Segment.DONE : Segment.CURRENT;
            else segment = Segment.TODO;
            groups.get(group).add(segment);
            if (i == currentIndex) chapterNumber = group + 1;
        }
        List<List<Segment>> frozen = new ArrayList<>(groups.size());
        for (List<Segment> group : groups) frozen.add(Collections.unmodifiableList(group));
        return new TourProgress(Collections.unmodifiableList(frozen), chapterNumber,
            groups.size());
    }
}
