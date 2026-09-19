package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.tour.TourGesture;
import com.termux.app.tour.TourRun;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The one catalogue the whole help centre reads: complete, stable and self-consistent. */
public class HelpTopicsTest {

    /** Every target id {@code HelpTargets.measure()} can emit, per place. */
    private static final List<String> TERMINAL_TARGETS = Arrays.asList("windows", "stats", "status",
        "settings", "sessions", "divider", "dock", "az", "corners", "keys", "prefix", "space");
    private static final List<String> DISPLAY_TARGETS = Arrays.asList("windows", "stats", "status",
        "settings", "scale", "touchpad", "start", "setup");
    private static final List<String> WIDGETS_TARGETS = Arrays.asList("status", "settings",
        "widget", "empty");

    private static List<String> targetsOf(PaneWallPage place) {
        switch (place) {
            case TERMINAL: return TERMINAL_TARGETS;
            case DISPLAY: return DISPLAY_TARGETS;
            default: return WIDGETS_TARGETS;
        }
    }

    private List<String> ids(PaneWallPage place) {
        List<String> ids = new ArrayList<>();
        for (HelpTopics.Entry entry : HelpTopics.forPlace(place)) ids.add(entry.id);
        return ids;
    }

    @Test public void everyTopicCarriesAGroupAKindAndItsThreeSentences() {
        assertFalse(HelpTopics.all().isEmpty());
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            assertNotNull(entry.id);
            assertFalse(entry.id.isEmpty());
            assertNotNull(entry.id, entry.group);
            assertNotNull(entry.id, entry.kind);
            assertNotEquals(entry.id, 0, entry.titleRes);
            assertNotEquals(entry.id, 0, entry.summaryRes);
            assertNotEquals(entry.id, 0, entry.actionRes);
            assertNotEquals(entry.id, 0, entry.group.labelRes);
            assertNotEquals(entry.id, 0, entry.group.sectionRes);
            assertTrue(entry.id, entry.stepsRes.size() <= 3);
            for (int step : entry.stepsRes) assertNotEquals(entry.id, 0, step);
        }
    }

    @Test public void idsAreUniqueAcrossTheWholeCatalogue() {
        List<String> ids = new ArrayList<>();
        for (HelpTopics.Entry entry : HelpTopics.all()) ids.add(entry.id);
        assertEquals(ids.size(), new HashSet<>(ids).size());
        for (String id : ids) assertNotNull(id, HelpTopics.entry(id));
        assertNull(HelpTopics.entry("nothing_by_this_name"));
        assertNull(HelpTopics.entry((String) null));
    }

    @Test public void theSevenSectionsAreAllAuthoredAndTheLastOneIsTwoShelves() {
        for (HelpTopics.Group group : HelpTopics.Group.values()) {
            assertFalse(group.name(), HelpTopics.inGroup(group).isEmpty());
        }
        // Linux display and Something missing? are one section of the guide, read as two lists.
        assertEquals(HelpTopics.Group.DISPLAY.sectionRes, HelpTopics.Group.MISSING.sectionRes);
        assertNotEquals(HelpTopics.Group.DISPLAY.labelRes, HelpTopics.Group.MISSING.labelRes);
        assertEquals(8, HelpTopics.Group.values().length);
        assertTrue("the guide is about thirty-five topics", HelpTopics.all().size() >= 35);
    }

    @Test public void theCatalogueIsListedGroupByGroup() {
        int group = -1;
        Set<Integer> seen = new HashSet<>();
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.group.ordinal() == group) continue;
            group = entry.group.ordinal();
            assertTrue(entry.id + " reopens its group", seen.add(group));
        }
    }

    @Test public void onlyTheSomethingMissingShelfIsFixes() {
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            assertEquals(entry.id, entry.group == HelpTopics.Group.MISSING
                ? HelpTopics.Kind.FIX : HelpTopics.Kind.GUIDE, entry.kind);
        }
        assertFalse(HelpTopics.inGroup(HelpTopics.Group.MISSING).isEmpty());
    }

    @Test public void everyTargetTheLauncherMeasuresHasExactlyOneTopicOnThatPlace() {
        for (PaneWallPage place : PaneWallPage.values()) {
            for (String targetId : targetsOf(place)) {
                HelpTopics.Entry entry = HelpTopics.forTarget(place, targetId);
                assertNotNull(place + "/" + targetId, entry);
                assertEquals(targetId, entry.targetId);
                int bound = 0;
                for (HelpTopics.Entry other : HelpTopics.forPlace(place)) {
                    if (targetId.equals(other.targetId)) bound++;
                }
                assertEquals(place + "/" + targetId, 1, bound);
            }
        }
    }

    @Test public void aTopicIsBoundOnlyToPlacesThatMeasureItsTarget() {
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.targetId == null) {
                assertTrue(entry.id, entry.places.isEmpty());
                continue;
            }
            assertFalse(entry.id, entry.places.isEmpty());
            for (PaneWallPage place : entry.places) {
                assertTrue(entry.id + " on " + place, targetsOf(place).contains(entry.targetId));
            }
        }
    }

    @Test public void theSameControlOnSeveralPlacesIsOneTopic() {
        HelpTopics.Entry status = HelpTopics.entry("status");
        assertEquals(3, status.places.size());
        for (PaneWallPage place : PaneWallPage.values()) {
            assertTrue(place.name(), status.onPlace(place));
            assertEquals(status, HelpTopics.forTarget(place, "status"));
            assertNotNull(place.name(), HelpTopics.forTarget(place, "settings"));
        }
        // The same target id means two different things on two places, so it is two topics.
        assertEquals("windows", HelpTopics.forTarget(PaneWallPage.TERMINAL, "windows").id);
        assertEquals("display_apps", HelpTopics.forTarget(PaneWallPage.DISPLAY, "windows").id);
    }

    @Test public void everyPlacesCatalogueIsTheTopicsWhoseControlItHas() {
        assertEquals(12, HelpTopics.sizeFor(PaneWallPage.TERMINAL));
        assertEquals(8, HelpTopics.sizeFor(PaneWallPage.DISPLAY));
        assertEquals(4, HelpTopics.sizeFor(PaneWallPage.WIDGETS));
        for (PaneWallPage place : PaneWallPage.values()) {
            List<String> ids = ids(place);
            assertEquals(ids.size(), new HashSet<>(ids).size());
            Set<Integer> indices = new HashSet<>();
            for (HelpTopics.Entry entry : HelpTopics.forPlace(place)) {
                assertTrue(entry.id, entry.onPlace(place));
                int index = HelpTopics.identityIndex(place, entry.id);
                assertTrue(entry.id, index >= 0 && index < ids.size());
                assertTrue(entry.id, indices.add(index));
                assertEquals(entry.id, index, HelpTopics.identityIndex(place, entry.targetId));
            }
        }
        // The display's own Set up topic, which had no catalogue entry before.
        assertEquals("setup", HelpTopics.forTarget(PaneWallPage.DISPLAY, "setup").id);
        assertNull(HelpTopics.forTarget(PaneWallPage.WIDGETS, "sessions"));
        assertNull(HelpTopics.forTarget(PaneWallPage.TERMINAL, "widget"));
    }

    @Test public void aLookupTakesEitherATopicIdOrATargetId() {
        assertEquals("display_apps", HelpTopics.entry(PaneWallPage.DISPLAY, "windows").id);
        assertEquals("display_apps", HelpTopics.entry(PaneWallPage.DISPLAY, "display_apps").id);
        assertEquals("hierarchy", HelpTopics.entry(PaneWallPage.TERMINAL, "sessions").id);
        assertEquals("shortcuts", HelpTopics.entry(PaneWallPage.TERMINAL, "prefix").id);
        // A topic with no control at all still resolves: it is read, not pointed at.
        assertEquals("copy_paste", HelpTopics.entry(PaneWallPage.WIDGETS, "copy_paste").id);
        assertNull(HelpTopics.entry(PaneWallPage.WIDGETS, "sessions"));
        assertNull(HelpTopics.entry(PaneWallPage.TERMINAL, "nothing_by_this_name"));
    }

    @Test public void aGestureIsOfferedOnlyWhereOneIsImplemented() {
        // HelpOverlayView.gestureFor knows four controls; every other topic offers no Show gesture.
        Set<String> withGesture = new HashSet<>();
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.gesture == null) continue;
            withGesture.add(entry.id);
            assertNotNull(entry.id, entry.targetId);
        }
        assertEquals(new HashSet<>(Arrays.asList("dock", "status", "space", "az")), withGesture);
        assertEquals(TourGesture.DRAG_DOWN, HelpTopics.entry("dock").gesture);
        assertEquals(TourGesture.SWIPE_RIGHT, HelpTopics.entry("status").gesture);
        assertEquals(TourGesture.SWIPE_UP, HelpTopics.entry("space").gesture);
        assertEquals(TourGesture.SCRUB, HelpTopics.entry("az").gesture);
    }

    @Test public void everyPracticeIdNamesALessonTheRunActuallyHas() {
        assertEquals(TourRun.lessons().size(), HelpTopics.LESSON_IDS.size());
        for (String lessonId : HelpTopics.LESSON_IDS) {
            assertTrue(lessonId, TourRun.lessons().contains(lessonId));
        }
        Set<String> used = new HashSet<>();
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.lessonId == null) continue;
            assertTrue(entry.id, HelpTopics.LESSON_IDS.contains(entry.lessonId));
            used.add(entry.lessonId);
        }
        assertEquals("every lesson is reachable from a topic", 5, used.size());
    }

    @Test public void theFiveLessonsAreReachedFromTheTopicsThatTeachThem() {
        assertEquals(HelpTopics.LESSON_FIND_HELP, HelpTopics.entry("corners").lessonId);
        assertEquals(HelpTopics.LESSON_FIND_APPS, HelpTopics.entry("dock").lessonId);
        assertEquals(HelpTopics.LESSON_PIN_APPS, HelpTopics.entry("organize_apps").lessonId);
        assertEquals(HelpTopics.LESSON_KEYBOARD, HelpTopics.entry("keyboard").lessonId);
        assertEquals(HelpTopics.LESSON_FIND_ACTION, HelpTopics.entry("palette").lessonId);
        assertNull(HelpTopics.entry("hierarchy").lessonId);
        assertNull(HelpTopics.entry("shortcuts").lessonId);
        assertNull(HelpTopics.entry("workspaces").lessonId);
    }

    @Test public void relatedTopicsAndGlossaryTermsAllExist() {
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            for (String relatedId : entry.relatedIds) {
                assertNotEquals(entry.id, entry.id, relatedId);
                assertNotNull(entry.id + " -> " + relatedId, HelpTopics.entry(relatedId));
            }
            for (String termId : entry.termIds) {
                assertNotNull(entry.id + " -> " + termId, HelpGlossary.term(termId));
            }
            assertEquals(entry.id, new HashSet<>(entry.relatedIds).size(), entry.relatedIds.size());
        }
    }

    @Test public void aHiddenControlSaysHowToBringItBackAndTheRestDoNotPretendTo() {
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.targetId == null) assertEquals(entry.id, 0, entry.revealRes);
        }
        assertNotEquals(0, HelpTopics.entry("space").revealRes);
        assertNotEquals(0, HelpTopics.entry("dock").revealRes);
        // An action that changes a mode or a layout says how to come back out of it.
        assertNotEquals(0, HelpTopics.entry("mouse_mode").wayBackRes);
        assertNotEquals(0, HelpTopics.entry("float_pane").wayBackRes);
        assertNotEquals(0, HelpTopics.entry("layout_editor").wayBackRes);
    }

    @Test public void everyTopicIsSearchableAndTheOnesWithPagesPointAtRealFiles() {
        int withAliases = 0;
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.aliasesRes != 0) withAliases++;
            if (entry.docPath == null) continue;
            assertTrue(entry.id + ": " + entry.docPath, entry.docPath.endsWith(".md")
                || entry.docPath.contains(".md#"));
            assertFalse(entry.id, entry.docPath.startsWith("/"));
        }
        assertEquals("every topic carries aliases", HelpTopics.all().size(), withAliases);
    }

    @Test public void theOldOverviewStillLeavesItsTwoTopicsUnboxed() {
        assertTrue(HelpTopics.topicOnly("keys"));
        assertTrue(HelpTopics.topicOnly("corners"));
        assertFalse(HelpTopics.topicOnly("dock"));
        assertFalse(HelpTopics.topicOnly(null));
        assertTrue(ids(PaneWallPage.TERMINAL).containsAll(Arrays.asList("keys", "corners")));
    }
}
