package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Commands in, rendered state and requested effects out; the model never draws or measures. */
@RunWith(RobolectricTestRunner.class)
public class HelpPresentationModelTest {

    private static final int ACCENT = Color.rgb(0xf0, 0xb4, 0x8a);

    private Set<String> everything(PaneWallPage place) {
        Set<String> ids = new LinkedHashSet<>();
        for (HelpTopics.Entry entry : HelpTopics.forPlace(place)) ids.add(entry.id);
        return ids;
    }

    private Set<String> everythingBut(PaneWallPage place, String... missing) {
        Set<String> ids = everything(place);
        ids.removeAll(Arrays.asList(missing));
        return ids;
    }

    private HelpPresentationModel opened(PaneWallPage place) {
        HelpPresentationModel model = new HelpPresentationModel();
        model.open(place, everything(place));
        return model;
    }

    @Test public void theTerminalOpensOnTheTopicChooserAndHomeOnTheOverview() {
        assertEquals(HelpPresentationModel.Mode.TOPICS, opened(PaneWallPage.TERMINAL).mode());
        assertEquals(HelpPresentationModel.Mode.OVERVIEW, opened(PaneWallPage.WIDGETS).mode());
        assertEquals(HelpPresentationModel.Mode.TOPICS, HelpPresentationModel.TERMINAL_DEFAULT_MODE);
        assertEquals(HelpPresentationModel.Mode.OVERVIEW, HelpPresentationModel.WIDGETS_DEFAULT_MODE);
        assertEquals(HelpPresentationModel.DISPLAY_DEFAULT_MODE, opened(PaneWallPage.DISPLAY).mode());
        assertTrue(opened(PaneWallPage.TERMINAL).isOpen());
    }

    @Test public void openingLandsOnTheFirstPageWithNothingSelected() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        assertEquals(HelpPresentationModel.Effect.NONE,
            model.open(PaneWallPage.TERMINAL, everything(PaneWallPage.TERMINAL)));
        assertEquals(0, model.page());
        assertEquals(1, model.pageCount());
        assertNull(model.selectedId());
        assertNull(model.selected());
        assertFalse(model.basicsOnly());
        assertEquals(PaneWallPage.TERMINAL, model.place());
    }

    @Test public void selectingATopicShowsOneTargetInThePlaceAccentAndAsksForNothing() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        assertEquals(HelpPresentationModel.Effect.NONE, model.selectTopic("sessions"));
        assertEquals(HelpPresentationModel.Mode.TOPIC, model.mode());
        assertEquals("sessions", model.selectedId());
        assertNotNull(model.selected());
        assertEquals("sessions", model.selected().id);
        assertEquals("sessions", model.highlightTargetId());
        assertTrue(model.selectedMeasurable());
        assertEquals(ACCENT, model.topicHighlightColor(ACCENT));
        assertEquals(0, model.revealRes());
        assertNull(model.relatedTopicId());
    }

    @Test public void anUnknownTopicChangesNothing() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.selectTopic("sessions");
        assertEquals(HelpPresentationModel.Effect.NONE, model.selectTopic("no_such_topic"));
        assertEquals("sessions", model.selectedId());
        assertEquals(HelpPresentationModel.Effect.NONE, model.selectTopic("widget"));
        assertEquals("sessions", model.selectedId());
    }

    @Test public void backToTopicsDropsTheSelection() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.selectTopic("sessions");
        assertEquals(HelpPresentationModel.Effect.NONE, model.backToTopics());
        assertEquals(HelpPresentationModel.Mode.TOPICS, model.mode());
        assertNull(model.selectedId());
    }

    @Test public void aTopicWhoseControlIsGoneExplainsItselfAndPointsAtNoOtherControl() {
        HelpPresentationModel model = new HelpPresentationModel();
        model.open(PaneWallPage.TERMINAL, everythingBut(PaneWallPage.TERMINAL, "space", "prefix", "keys"));
        model.selectTopic("space");
        assertEquals("space", model.selectedId());
        assertFalse(model.selectedMeasurable());
        assertNull(model.highlightTargetId());
        assertEquals(HelpTopics.entry(PaneWallPage.TERMINAL, "space").revealRes, model.revealRes());
        assertNotEquals(0, model.revealRes());
        assertFalse(model.canShowGesture());
        assertFalse(model.canTryIt());
        assertEquals(HelpPresentationModel.Effect.NONE, model.showGesture());
        assertEquals(HelpPresentationModel.Effect.NONE, model.tryIt());
    }

    @Test public void aMissingTopicMayOfferARelatedTopicButNeverAnotherTarget() {
        HelpPresentationModel model = new HelpPresentationModel();
        model.open(PaneWallPage.DISPLAY, everythingBut(PaneWallPage.DISPLAY, "windows"));
        model.selectTopic("windows");
        assertNull(model.highlightTargetId());
        assertEquals(HelpTopics.entry(PaneWallPage.DISPLAY, "windows").relatedId, model.relatedTopicId());
        assertEquals("start", model.relatedTopicId());
        model.remeasure(everything(PaneWallPage.DISPLAY));
        assertTrue(model.selectedMeasurable());
        assertEquals("windows", model.highlightTargetId());
        assertNull(model.relatedTopicId());
        assertEquals(0, model.revealRes());
    }

    @Test public void showBasicsListsOnlyTheEverydayTopics() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        assertEquals(HelpPresentationModel.Effect.NONE, model.showBasics());
        assertTrue(model.basicsOnly());
        assertEquals(HelpPresentationModel.Mode.TOPICS, model.mode());
        assertFalse(model.entries().isEmpty());
        for (HelpTopics.Entry entry : model.entries()) {
            assertEquals(entry.id, HelpTopics.Group.EVERYDAY, entry.group);
        }
        assertTrue(model.entries().size() < HelpTopics.sizeFor(PaneWallPage.TERMINAL));
    }

    @Test public void showAllOpensTheWholeCatalogueOnTheFirstOverviewPage() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.showBasics();
        model.setPageCount(3);
        model.next();
        assertEquals(HelpPresentationModel.Effect.NONE, model.showAll());
        assertEquals(HelpPresentationModel.Mode.OVERVIEW, model.mode());
        assertFalse(model.basicsOnly());
        assertEquals(0, model.page());
        assertEquals(HelpTopics.forPlace(PaneWallPage.TERMINAL), model.entries());
        assertNotEquals(0, model.sectionLabelRes());
        assertEquals(HelpTopics.Group.EVERYDAY.labelRes, model.sectionLabelRes());
    }

    @Test public void theListNeverHidesATopicWhoseControlIsMissing() {
        HelpPresentationModel model = new HelpPresentationModel();
        model.open(PaneWallPage.TERMINAL, everythingBut(PaneWallPage.TERMINAL, "space", "prefix", "keys"));
        model.showAll();
        assertEquals(HelpTopics.forPlace(PaneWallPage.TERMINAL), model.entries());
        assertFalse(model.isMeasurable("space"));
        assertTrue(model.isMeasurable("dock"));
    }

    @Test public void previousAndNextClampToThePagesTheRouterReports() {
        HelpPresentationModel model = opened(PaneWallPage.WIDGETS);
        assertEquals(HelpPresentationModel.Effect.NONE, model.setPageCount(2));
        assertEquals(2, model.pageCount());
        assertEquals(HelpPresentationModel.Effect.NONE, model.previous());
        assertEquals(0, model.page());
        model.next();
        assertEquals(1, model.page());
        model.next();
        assertEquals(1, model.page());
        model.previous();
        assertEquals(0, model.page());
        model.setPageCount(1);
        assertEquals(0, model.page());
        model.setPageCount(0);
        assertEquals(1, model.pageCount());
    }

    @Test public void aShorterPageCountBringsTheReaderBackInsideIt() {
        HelpPresentationModel model = opened(PaneWallPage.WIDGETS);
        model.setPageCount(4);
        model.next();
        model.next();
        assertEquals(2, model.page());
        model.setPageCount(2);
        assertEquals(1, model.page());
    }

    @Test public void theSectionLabelNamesTheGroupOnThePage() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.showAll();
        List<HelpTopics.Entry> entries = model.entries();
        model.setPageCount(entries.size());
        Set<Integer> labels = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(entries.get(i).id, entries.get(i).group.labelRes, model.sectionLabelRes());
            labels.add(model.sectionLabelRes());
            model.next();
        }
        assertTrue(labels.contains(HelpTopics.Group.KEYBOARD.labelRes));
        assertTrue(labels.contains(HelpTopics.Group.MULTITASKING.labelRes));
    }

    @Test public void showGestureAsksForADemonstrationAndLeavesHelpOpen() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.selectTopic("dock");
        assertTrue(model.canShowGesture());
        assertEquals(HelpPresentationModel.Effect.demonstrate("dock"), model.showGesture());
        assertTrue(model.isOpen());
        assertEquals(HelpPresentationModel.Mode.TOPIC, model.mode());
        assertEquals("dock", model.selectedId());
    }

    @Test public void tryItClosesHelpAndStartsTheTopicsLesson() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.selectTopic("dock");
        assertTrue(model.canTryIt());
        assertEquals(HelpPresentationModel.Effect.practice(HelpTopics.LESSON_FIND_APPS), model.tryIt());
        assertFalse(model.isOpen());
    }

    @Test public void aTopicWithNoLessonOffersNoPractice() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.selectTopic("sessions");
        assertTrue(model.canShowGesture());
        assertFalse(model.canTryIt());
        assertEquals(HelpPresentationModel.Effect.NONE, model.tryIt());
        assertTrue(model.isOpen());
    }

    @Test public void gesturesAndPracticeBelongToTheTopicViewOnly() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        assertFalse(model.canShowGesture());
        assertFalse(model.canTryIt());
        assertEquals(HelpPresentationModel.Effect.NONE, model.showGesture());
        model.showAll();
        assertFalse(model.canShowGesture());
        assertEquals(HelpPresentationModel.Effect.NONE, model.showGesture());
    }

    @Test public void closeAsksForDismissal() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        assertEquals(HelpPresentationModel.Effect.CLOSE, model.close());
        assertFalse(model.isOpen());
    }

    @Test public void anOverviewColourIsTheEntrysOwnWhateverElseIsOnScreen() {
        HelpPresentationModel full = opened(PaneWallPage.TERMINAL);
        HelpPresentationModel sparse = new HelpPresentationModel();
        sparse.open(PaneWallPage.TERMINAL, new HashSet<>(Arrays.asList("dock", "status")));
        full.showAll();
        sparse.showAll();
        HelpTopics.Entry status = HelpTopics.entry(PaneWallPage.TERMINAL, "status");
        assertEquals(full.overviewColor(ACCENT, status), sparse.overviewColor(ACCENT, status));
        assertEquals(HelpPalette.boxColor(ACCENT, status.identityIndex,
            HelpTopics.sizeFor(PaneWallPage.TERMINAL)), full.overviewColor(ACCENT, status));
    }

    @Test public void everyEntryOfAPlaceGetsItsOwnOverviewColour() {
        HelpPresentationModel model = opened(PaneWallPage.DISPLAY);
        Set<Integer> colours = new HashSet<>();
        List<HelpTopics.Entry> entries = HelpTopics.forPlace(PaneWallPage.DISPLAY);
        for (HelpTopics.Entry entry : entries) colours.add(model.overviewColor(ACCENT, entry));
        assertEquals(entries.size(), colours.size());
    }

    @Test public void reopeningOnAnotherPlaceForgetsTheLastPlacesTopic() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.selectTopic("sessions");
        model.open(PaneWallPage.WIDGETS, everything(PaneWallPage.WIDGETS));
        assertEquals(PaneWallPage.WIDGETS, model.place());
        assertNull(model.selectedId());
        assertEquals(HelpTopics.forPlace(PaneWallPage.WIDGETS), model.entries());
    }
}
