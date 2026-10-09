package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.app.place.PlaceLayout;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The run as data: six lessons in three chapters, in the handoff's order, then the closing card.
 */
public class TourRunTest {

    private static final TourRun.RunContext PHONE = new TourRun.RunContext(PlaceLayout.Edge.BOTTOM);

    private static List<String> ids(List<TourStep> steps) {
        List<String> ids = new ArrayList<>();
        for (TourStep step : steps) ids.add(step.id);
        return ids;
    }

    private static TourStep step(String id) {
        for (TourStep step : TourRun.steps(PHONE)) if (step.id.equals(id)) return step;
        throw new AssertionError("no step " + id);
    }

    @Test public void theRunIsSixLessonsInThreeChaptersThenTheClosingCard() {
        assertEquals(Arrays.asList(TourRun.BORDER_DRAG, TourRun.KEYBOARD, TourRun.STATUS_SWIPE,
            TourRun.FIND_APPS, TourRun.FIND_ACTION, TourRun.FIND_HELP, TourRun.CLOSING),
            ids(TourRun.steps(PHONE)));
        assertEquals(ids(TourRun.steps(PHONE)).subList(0, 6), TourRun.lessons());
        List<Integer> chapters = new ArrayList<>();
        for (TourStep step : TourRun.steps(PHONE)) chapters.add(step.chapter);
        assertEquals(Arrays.asList(0, 0, 1, 1, 2, 2, -1), chapters);
    }

    @Test public void eachLessonWaitsForTheStateItsGestureLeaves() {
        assertEquals(Arrays.asList(TourSignals.PLACE_CHANGED), signals(step(TourRun.BORDER_DRAG)));
        assertEquals(Arrays.asList(TourSignals.KEYBOARD_HIDDEN, TourSignals.KEYBOARD_SHOWN),
            signals(step(TourRun.KEYBOARD)));
        assertEquals(Arrays.asList(TourSignals.STATUS_BAR_EXPANDED),
            signals(step(TourRun.STATUS_SWIPE)));
        assertEquals(Arrays.asList(TourSignals.DRAWER_OPENED), signals(step(TourRun.FIND_APPS)));
        assertEquals(Arrays.asList(TourSignals.PALETTE_OPENED),
            signals(step(TourRun.FIND_ACTION)));
        assertEquals(Arrays.asList(TourSignals.PANE_CORNER_MENU, TourSignals.HELP_OPENED),
            signals(step(TourRun.FIND_HELP)));
        assertEquals(0, step(TourRun.CLOSING).stageCount());
        assertTrue(step(TourRun.CLOSING).isClosingCard());
    }

    private static List<String> signals(TourStep step) {
        List<String> signals = new ArrayList<>();
        for (int i = 0; i < step.stageCount(); i++) signals.add(step.signalAt(i));
        assertNull(step.signalAt(step.stageCount()));
        return signals;
    }

    @Test public void eachStageHasItsOwnTitleSentenceControlAndGesture() {
        TourStep keyboard = step(TourRun.KEYBOARD);
        assertEquals(R.string.tour_title_keyboard_away, keyboard.titleResAt(0));
        assertEquals(R.string.tour_body_keyboard_away, keyboard.bodyResAt(0));
        assertEquals(R.string.tour_title_keyboard_back, keyboard.titleResAt(1));
        assertEquals(R.string.tour_body_keyboard_back, keyboard.bodyResAt(1));
        assertEquals(TourGesture.DRAG_DOWN, keyboard.gestureAt(0));
        assertEquals(TourGesture.SWIPE_UP, keyboard.gestureAt(1));
        assertEquals(TourTargets.KEYBOARD_GRABBER, keyboard.targetIdAt(1));

        TourStep help = step(TourRun.FIND_HELP);
        assertEquals(R.string.tour_title_corner, help.titleResAt(0));
        assertEquals(TourTargets.PANE_CORNER, help.targetIdAt(0));
        assertEquals(TourGesture.HOLD, help.gestureAt(0));
        assertEquals(R.string.tour_title_help, help.titleResAt(1));
        assertEquals(TourTargets.HELP_BUTTON, help.targetIdAt(1));
        assertEquals(TourGesture.TAP, help.gestureAt(1));

        assertEquals(TourTargets.PAGE_BORDER, step(TourRun.BORDER_DRAG).targetIdAt(0));
        assertEquals(TourGesture.HOLD_DRAG, step(TourRun.BORDER_DRAG).gestureAt(0));
        assertEquals(TourTargets.STATUS_GRABBER, step(TourRun.STATUS_SWIPE).targetIdAt(0));
        assertEquals(TourTargets.DOCK, step(TourRun.FIND_APPS).targetIdAt(0));
        assertEquals(TourTargets.SPACE_BAR, step(TourRun.FIND_ACTION).targetIdAt(0));
    }

    @Test public void theChaptersAreNamedAsTheHandoffNamesThem() {
        assertEquals(R.string.tour_chapter_getting_around, step(TourRun.BORDER_DRAG).chapterRes);
        assertEquals(R.string.tour_chapter_getting_around, step(TourRun.KEYBOARD).chapterRes);
        assertEquals(R.string.tour_chapter_status_and_apps, step(TourRun.STATUS_SWIPE).chapterRes);
        assertEquals(R.string.tour_chapter_status_and_apps, step(TourRun.FIND_APPS).chapterRes);
        assertEquals(R.string.tour_chapter_commands_and_help, step(TourRun.FIND_ACTION).chapterRes);
        assertEquals(R.string.tour_chapter_commands_and_help, step(TourRun.FIND_HELP).chapterRes);
    }

    @Test public void theDrawerSentenceFollowsTheEdgeTheAppsRowStandsOn() {
        assertEquals(R.string.tour_body_apps, findApps(PlaceLayout.Edge.BOTTOM).bodyResAt(0));
        assertEquals(R.string.tour_body_apps, findApps(PlaceLayout.Edge.TOP).bodyResAt(0));
        assertEquals(R.string.tour_body_apps_left_rail,
            findApps(PlaceLayout.Edge.LEFT).bodyResAt(0));
        assertEquals(R.string.tour_body_apps_right_rail,
            findApps(PlaceLayout.Edge.RIGHT).bodyResAt(0));
    }

    private static TourStep findApps(PlaceLayout.Edge edge) {
        for (TourStep step : TourRun.steps(new TourRun.RunContext(edge)))
            if (step.id.equals(TourRun.FIND_APPS)) return step;
        throw new AssertionError("no drawer lesson");
    }

    @Test public void thePaletteCardAsksToStandAboveTheSpaceBar() {
        assertEquals(TourStep.Placement.ABOVE, step(TourRun.FIND_ACTION).placement);
        assertEquals(TourStep.Placement.AUTO, step(TourRun.KEYBOARD).placement);
    }

    @Test public void onlyTheBorderAndPillLessonsCanBeTaughtAwayFromTheTerminal() {
        assertFalse(step(TourRun.BORDER_DRAG).taughtOnTheTerminal());
        assertFalse(step(TourRun.KEYBOARD).taughtOnTheTerminal());
        assertFalse(step(TourRun.STATUS_SWIPE).taughtOnTheTerminal());
        assertTrue(step(TourRun.FIND_APPS).taughtOnTheTerminal());
        assertTrue(step(TourRun.FIND_ACTION).taughtOnTheTerminal());
        assertTrue(step(TourRun.FIND_HELP).taughtOnTheTerminal());
        assertFalse(step(TourRun.CLOSING).taughtOnTheTerminal());
    }

    @Test public void lessonIdsAreTheOnesHelpsPracticeNames() {
        assertEquals("border_drag", TourRun.BORDER_DRAG);
        assertEquals("keyboard", TourRun.KEYBOARD);
        assertEquals("status_swipe", TourRun.STATUS_SWIPE);
        assertEquals("find_apps", TourRun.FIND_APPS);
        assertEquals("find_action", TourRun.FIND_ACTION);
        assertEquals("find_help", TourRun.FIND_HELP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aLessonMustAskForSomething() {
        TourStep.lesson("empty", 0, R.string.tour_chapter_getting_around,
            TourStep.Placement.AUTO);
    }
}
