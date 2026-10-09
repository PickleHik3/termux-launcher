package com.termux.app.tour;

import static org.junit.Assert.assertEquals;

import com.termux.app.place.PlaceLayout;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Three groups of two, one segment per lesson: done, current and still to come, and a chapter
 * label that counts what the run actually shows.
 */
public class TourProgressTest {

    private static final List<TourStep> RUN =
        TourRun.steps(new TourRun.RunContext(PlaceLayout.Edge.BOTTOM));
    private static final TourProgress.Segment D = TourProgress.Segment.DONE;
    private static final TourProgress.Segment C = TourProgress.Segment.CURRENT;
    private static final TourProgress.Segment T = TourProgress.Segment.TODO;

    @Test public void theFirstLessonIsCurrentAndTheRestAreToCome() {
        TourProgress progress = TourProgress.of(RUN, null, 0, false);
        assertEquals(Arrays.asList(Arrays.asList(C, T), Arrays.asList(T, T), Arrays.asList(T, T)),
            progress.groups);
        assertEquals(1, progress.chapterNumber);
        assertEquals(3, progress.chapterCount);
    }

    @Test public void theChapterNumberFollowsTheLesson() {
        assertEquals(2, TourProgress.of(RUN, null, 2, false).chapterNumber);
        assertEquals(2, TourProgress.of(RUN, null, 3, false).chapterNumber);
        TourProgress last = TourProgress.of(RUN, null, 5, false);
        assertEquals(3, last.chapterNumber);
        assertEquals(Arrays.asList(Arrays.asList(D, D), Arrays.asList(D, D), Arrays.asList(D, C)),
            last.groups);
    }

    @Test public void aClearedLessonIsDoneWhileItSaysSo() {
        assertEquals(Arrays.asList(D, D), TourProgress.of(RUN, null, 1, true).groups.get(0));
    }

    @Test public void onTheClosingCardEverythingIsDoneAndThereIsNoChapter() {
        TourProgress closing = TourProgress.of(RUN, null, 6, false);
        assertEquals(Arrays.asList(Arrays.asList(D, D), Arrays.asList(D, D), Arrays.asList(D, D)),
            closing.groups);
        assertEquals(0, closing.chapterNumber);
    }

    @Test public void aDroppedLessonIsNotDrawnAndItsChapterRecounts() {
        TourProgress progress = TourProgress.of(RUN,
            Collections.singleton(TourRun.BORDER_DRAG), 1, false);
        assertEquals(Arrays.asList(Collections.singletonList(C), Arrays.asList(T, T),
            Arrays.asList(T, T)), progress.groups);
        assertEquals(1, progress.chapterNumber);
        assertEquals(3, progress.chapterCount);
    }
}
