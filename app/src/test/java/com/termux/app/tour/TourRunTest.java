package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.R;
import com.termux.app.place.PlaceLayout;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The run as data: six lessons in three chapters, taught on the wall (Home, the Workspace,
 * anywhere), then the closing card.
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
        assertEquals(Arrays.asList(TourRun.PAGE_SWIPE, TourRun.FIND_APPS, TourRun.KEYBOARD,
            TourRun.STATUS_SWIPE, TourRun.FIND_ACTION, TourRun.FIND_HELP, TourRun.CLOSING),
            ids(TourRun.steps(PHONE)));
        assertEquals(ids(TourRun.steps(PHONE)).subList(0, 6), TourRun.lessons());
        List<Integer> chapters = new ArrayList<>();
        for (TourStep step : TourRun.steps(PHONE)) chapters.add(step.chapter);
        assertEquals(Arrays.asList(0, 0, 1, 1, 2, 2, -1), chapters);
    }

    @Test public void theClosingCardFollowsTheSixthLesson() {
        List<TourStep> steps = TourRun.steps(PHONE);
        assertEquals(7, steps.size());
        assertEquals(TourRun.FIND_HELP, steps.get(5).id);
        assertFalse(steps.get(5).isClosingCard());
        assertTrue(steps.get(6).isClosingCard());
        assertEquals(TourRun.CLOSING, steps.get(6).id);
        for (int i = 0; i < 6; i++) assertFalse(steps.get(i).id, steps.get(i).isClosingCard());
    }

    @Test public void eachLessonWaitsForTheStateItsGestureLeaves() {
        assertEquals(Arrays.asList(TourSignals.SEAM_TO_WORKSPACE, TourSignals.SEAM_TO_HOME),
            signals(step(TourRun.PAGE_SWIPE)));
        assertEquals(Arrays.asList(TourSignals.DRAWER_OPENED), signals(step(TourRun.FIND_APPS)));
        assertEquals(Arrays.asList(TourSignals.KEYBOARD_HIDDEN, TourSignals.KEYBOARD_SHOWN),
            signals(step(TourRun.KEYBOARD)));
        assertEquals(Arrays.asList(TourSignals.STATUS_BAR_EXPANDED,
            TourSignals.STATUS_BAR_COLLAPSED), signals(step(TourRun.STATUS_SWIPE)));
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

    /**
     * The apps lesson clears on the drawer's pull and on a tap on the dock's A-Z letters, which
     * lays the letter's apps out in the row; nothing else does, and no other lesson takes either.
     */
    @Test public void theAppsLessonClearsOnTheDrawerOrTheLetters() {
        TourStep apps = step(TourRun.FIND_APPS);
        assertTrue(apps.clearedBy(0, TourSignals.DRAWER_OPENED));
        assertTrue(apps.clearedBy(0, TourSignals.AZ_APPS_SHOWN));
        assertEquals(TourSignals.AZ_APPS_SHOWN, apps.alsoSignalAt(0));
        assertFalse(apps.clearedBy(0, TourSignals.APP_LAUNCHED));
        assertFalse(apps.clearedBy(0, null));
        assertFalse("past the last stage", apps.clearedBy(1, TourSignals.DRAWER_OPENED));
        for (String id : Arrays.asList(TourRun.PAGE_SWIPE, TourRun.KEYBOARD, TourRun.STATUS_SWIPE,
                TourRun.FIND_ACTION, TourRun.FIND_HELP)) {
            TourStep other = step(id);
            for (int i = 0; i < other.stageCount(); i++) {
                assertNull(id, other.alsoSignalAt(i));
                assertFalse(id, other.clearedBy(i, TourSignals.AZ_APPS_SHOWN));
            }
        }
    }

    @Test public void eachStageHasItsOwnTitleSentenceControlAndGesture() {
        TourStep pageSwipe = step(TourRun.PAGE_SWIPE);
        assertEquals(R.string.tour_title_to_workspace, pageSwipe.titleResAt(0));
        assertEquals(R.string.tour_body_to_workspace, pageSwipe.bodyResAt(0));
        assertEquals(TourTargets.HOME_PAGE, pageSwipe.targetIdAt(0));
        assertEquals(TourGesture.SWIPE_LEFT, pageSwipe.gestureAt(0));
        assertEquals(R.string.tour_title_border_drag, pageSwipe.titleResAt(1));
        assertEquals(R.string.tour_body_border_drag, pageSwipe.bodyResAt(1));
        assertEquals(TourTargets.PAGE_BORDER, pageSwipe.targetIdAt(1));
        assertEquals(TourGesture.SWIPE_RIGHT, pageSwipe.gestureAt(1));

        TourStep keyboard = step(TourRun.KEYBOARD);
        assertEquals(R.string.tour_title_keyboard_away, keyboard.titleResAt(0));
        assertEquals(R.string.tour_body_keyboard_away, keyboard.bodyResAt(0));
        assertEquals(R.string.tour_title_keyboard_back, keyboard.titleResAt(1));
        assertEquals(R.string.tour_body_keyboard_back, keyboard.bodyResAt(1));
        assertEquals(TourGesture.DRAG_DOWN, keyboard.gestureAt(0));
        assertEquals(TourGesture.SWIPE_UP, keyboard.gestureAt(1));
        assertEquals(TourTargets.KEYBOARD_GRABBER, keyboard.targetIdAt(0));
        assertEquals(TourTargets.KEYBOARD_GRABBER, keyboard.targetIdAt(1));

        TourStep status = step(TourRun.STATUS_SWIPE);
        assertEquals(R.string.tour_title_status, status.titleResAt(0));
        assertEquals(R.string.tour_title_status_fold, status.titleResAt(1));
        assertEquals(R.string.tour_body_status_fold, status.bodyResAt(1));
        assertEquals(TourTargets.STATUS_GRABBER, status.targetIdAt(0));
        assertEquals(TourGesture.DRAG_DOWN, status.gestureAt(0));
        assertEquals(TourGesture.SWIPE_UP, status.gestureAt(1));

        TourStep help = step(TourRun.FIND_HELP);
        assertEquals(R.string.tour_title_corner, help.titleResAt(0));
        assertEquals(TourTargets.PANE_CORNER, help.targetIdAt(0));
        assertEquals(TourGesture.HOLD, help.gestureAt(0));
        assertEquals(R.string.tour_title_help, help.titleResAt(1));
        assertEquals(TourTargets.HELP_BUTTON, help.targetIdAt(1));
        assertEquals(TourGesture.TAP, help.gestureAt(1));

        assertEquals(TourTargets.DOCK, step(TourRun.FIND_APPS).targetIdAt(0));
        assertEquals(TourTargets.HOME_PAGE, step(TourRun.FIND_ACTION).targetIdAt(0));
        assertEquals(TourGesture.SWIPE_UP, step(TourRun.FIND_ACTION).gestureAt(0));
    }

    /**
     * Every stage points at a control the overlay knows how to find: an id declared on {@link
     * TourTargets}, which is what {@code TourViewTargets} answers for. A stage naming anything else
     * would show its card without a glow on every phone.
     */
    @Test public void everyStagesTargetIsADeclaredTarget() throws IllegalAccessException {
        Set<String> declared = new HashSet<>();
        for (Field field : TourTargets.class.getFields()) {
            if (field.getType() == String.class && Modifier.isStatic(field.getModifiers()))
                declared.add((String) field.get(null));
        }
        for (PlaceLayout.Edge edge : PlaceLayout.Edge.values()) {
            for (TourStep step : TourRun.steps(new TourRun.RunContext(edge))) {
                for (int stage = 0; stage < step.stageCount(); stage++) {
                    String target = step.targetIdAt(stage);
                    assertTrue(step.id + ":" + stage + " points at \"" + target + "\"",
                        declared.contains(target));
                    assertFalse(step.id + ":" + stage + " points at nothing",
                        TourTargets.NONE.equals(target));
                }
            }
        }
    }

    @Test public void theChaptersAreHomeTheWorkspaceAndAnywhere() {
        assertEquals(R.string.tour_chapter_home, step(TourRun.PAGE_SWIPE).chapterRes);
        assertEquals(R.string.tour_chapter_home, step(TourRun.FIND_APPS).chapterRes);
        assertEquals(R.string.tour_chapter_workspace, step(TourRun.KEYBOARD).chapterRes);
        assertEquals(R.string.tour_chapter_workspace, step(TourRun.STATUS_SWIPE).chapterRes);
        assertEquals(R.string.tour_chapter_anywhere, step(TourRun.FIND_ACTION).chapterRes);
        assertEquals(R.string.tour_chapter_anywhere, step(TourRun.FIND_HELP).chapterRes);
    }

    @Test public void eachStageIsTaughtOnTheHalfOfTheWallItsControlStandsOn() {
        TourStep pageSwipe = step(TourRun.PAGE_SWIPE);
        assertEquals(TourStep.Side.HOME, pageSwipe.sideAt(0));
        assertEquals(TourStep.Side.WORKSPACE, pageSwipe.sideAt(1));
        assertEquals(TourStep.Side.HOME, step(TourRun.FIND_APPS).sideAt(0));
        for (String id : Arrays.asList(TourRun.KEYBOARD, TourRun.STATUS_SWIPE, TourRun.FIND_HELP)) {
            TourStep lesson = step(id);
            for (int stage = 0; stage < lesson.stageCount(); stage++)
                assertEquals(id + ":" + stage, TourStep.Side.WORKSPACE, lesson.sideAt(stage));
        }
        assertEquals(TourStep.Side.ANYWHERE, step(TourRun.FIND_ACTION).sideAt(0));
        assertEquals(TourStep.Side.ANYWHERE, step(TourRun.CLOSING).sideAt(0));
    }

    @Test public void eachLessonStartsOnTheHalfItsFirstStageIsTaughtOn() {
        assertEquals(TourStep.Side.HOME, TourRun.startSide(step(TourRun.PAGE_SWIPE)));
        assertEquals(TourStep.Side.HOME, TourRun.startSide(step(TourRun.FIND_APPS)));
        assertEquals(TourStep.Side.WORKSPACE, TourRun.startSide(step(TourRun.KEYBOARD)));
        assertEquals(TourStep.Side.WORKSPACE, TourRun.startSide(step(TourRun.STATUS_SWIPE)));
        // Read on either half, it starts where its glow is: Home's free area.
        assertEquals(TourStep.Side.HOME, TourRun.startSide(step(TourRun.FIND_ACTION)));
        assertEquals(TourStep.Side.WORKSPACE, TourRun.startSide(step(TourRun.FIND_HELP)));
        // The closing card shows wherever the last lesson ended.
        assertEquals(TourStep.Side.ANYWHERE, TourRun.startSide(step(TourRun.CLOSING)));
        assertEquals(TourStep.Side.ANYWHERE, TourRun.startSide(null));
    }

    @Test public void noLessonStartsOnAHalfItsFirstStageIsNotReadOn() {
        for (TourStep step : TourRun.steps(PHONE)) {
            if (step.isClosingCard()) continue;
            assertTrue(step.id, step.isTaughtOn(0, TourRun.startSide(step)));
        }
    }

    @Test public void aStageIsReadOnItsOwnHalfAndAnywhereWhileTheWallHasNotSaid() {
        TourStep pageSwipe = step(TourRun.PAGE_SWIPE);
        assertTrue(pageSwipe.isTaughtOn(0, TourStep.Side.HOME));
        assertFalse(pageSwipe.isTaughtOn(0, TourStep.Side.WORKSPACE));
        assertTrue(pageSwipe.isTaughtOn(1, TourStep.Side.WORKSPACE));
        assertFalse(pageSwipe.isTaughtOn(1, TourStep.Side.HOME));
        assertTrue(pageSwipe.isTaughtOn(0, null));
        assertTrue(step(TourRun.FIND_ACTION).isTaughtOn(0, TourStep.Side.HOME));
        assertTrue(step(TourRun.FIND_ACTION).isTaughtOn(0, TourStep.Side.WORKSPACE));
        assertTrue(step(TourRun.CLOSING).isTaughtOn(0, TourStep.Side.WORKSPACE));
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

    @Test public void thePaletteCardAsksToStandAboveHomesFreeArea() {
        assertEquals(TourStep.Placement.ABOVE, step(TourRun.FIND_ACTION).placement);
        assertEquals(TourStep.Placement.AUTO, step(TourRun.KEYBOARD).placement);
        assertEquals(TourStep.Placement.AUTO, step(TourRun.PAGE_SWIPE).placement);
    }

    @Test public void aWallWithNoWindowBesideHomeSkipsOnlyTheSeam() {
        assertEquals(Collections.emptyList(), TourRun.droppedFor(true, true));
        assertEquals(Collections.singletonList(TourRun.PAGE_SWIPE),
            TourRun.droppedFor(true, false));
    }

    @Test public void aWallWithoutHomeSkipsTheHomeLessons() {
        List<String> expected = Arrays.asList(TourRun.PAGE_SWIPE, TourRun.FIND_APPS);
        assertEquals(expected, TourRun.droppedFor(false, true));
        assertEquals(expected, TourRun.droppedFor(false, false));
        // What is left is the Workspace and anywhere: nothing taught on Home.
        Set<String> dropped = new HashSet<>(expected);
        for (TourStep step : TourRun.steps(PHONE)) {
            if (dropped.contains(step.id)) continue;
            for (int stage = 0; stage < step.stageCount(); stage++)
                assertTrue(step.id, step.sideAt(stage) != TourStep.Side.HOME);
        }
    }

    @Test public void lessonIdsAreTheOnesHelpsPracticeNames() {
        assertEquals("page_swipe", TourRun.PAGE_SWIPE);
        assertEquals("find_apps", TourRun.FIND_APPS);
        assertEquals("keyboard", TourRun.KEYBOARD);
        assertEquals("status_swipe", TourRun.STATUS_SWIPE);
        assertEquals("find_action", TourRun.FIND_ACTION);
        assertEquals("find_help", TourRun.FIND_HELP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aLessonMustAskForSomething() {
        TourStep.lesson("empty", 0, R.string.tour_chapter_home, TourStep.Placement.AUTO);
    }
}
