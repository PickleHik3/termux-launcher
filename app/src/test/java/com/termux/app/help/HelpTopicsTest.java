package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.tour.TourRun;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The one catalogue both help presentations read: complete, stable and self-consistent. */
public class HelpTopicsTest {

    private List<String> ids(PaneWallPage place) {
        List<String> ids = new ArrayList<>();
        for (HelpTopics.Entry entry : HelpTopics.forPlace(place)) ids.add(entry.id);
        return ids;
    }

    @Test public void everyEntryCarriesAPlaceAGroupAndCopy() {
        assertFalse(HelpTopics.all().isEmpty());
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            assertNotNull(entry.id);
            assertFalse(entry.id.isEmpty());
            assertNotNull(entry.place);
            assertNotNull(entry.group);
            assertNotEquals(0, entry.purposeRes);
            assertNotEquals(0, entry.actionRes);
            assertNotEquals(0, entry.revealRes);
            assertNotEquals(0, entry.group.labelRes);
        }
    }

    @Test public void idsAreUniqueWithinAPlace() {
        for (PaneWallPage place : PaneWallPage.values()) {
            List<String> ids = ids(place);
            assertEquals(ids.size(), new HashSet<>(ids).size());
        }
    }

    @Test public void identityIndicesNumberThePlacesFullCatalogue() {
        for (PaneWallPage place : PaneWallPage.values()) {
            List<HelpTopics.Entry> entries = HelpTopics.forPlace(place);
            assertEquals(entries.size(), HelpTopics.sizeFor(place));
            Set<Integer> seen = new HashSet<>();
            for (HelpTopics.Entry entry : entries) {
                assertEquals(place, entry.place);
                assertTrue(entry.identityIndex >= 0 && entry.identityIndex < entries.size());
                assertTrue(seen.add(entry.identityIndex));
            }
        }
    }

    @Test public void everyPracticeIdNamesARealLesson() {
        boolean any = false;
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.lessonId == null) continue;
            any = true;
            assertTrue(entry.lessonId, HelpTopics.LESSON_IDS.contains(entry.lessonId));
        }
        assertTrue(any);
        assertEquals(4, HelpTopics.LESSON_IDS.size());
    }

    @Test public void everyLessonIdNamesALessonTheRunActuallyHas() {
        // The catalogue's ids and the run's are the same four strings, and nothing hands practice
        // to a card the run cannot start.
        assertEquals(TourRun.lessons().size(), HelpTopics.LESSON_IDS.size());
        for (String lessonId : HelpTopics.LESSON_IDS) {
            assertTrue(lessonId, TourRun.lessons().contains(lessonId));
        }
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.lessonId == null) continue;
            assertTrue(entry.id, HelpTopics.LESSON_IDS.contains(entry.lessonId));
        }
    }

    @Test public void theFourLessonsAreReachedFromTheTopicsThatTeachThem() {
        assertEquals(HelpTopics.LESSON_FIND_APPS, HelpTopics.entry(PaneWallPage.TERMINAL, "dock").lessonId);
        assertEquals(HelpTopics.LESSON_FIND_ACTION, HelpTopics.entry(PaneWallPage.TERMINAL, "space").lessonId);
        assertEquals(HelpTopics.LESSON_FIND_HELP, HelpTopics.entry(PaneWallPage.TERMINAL, "corners").lessonId);
        assertEquals(HelpTopics.LESSON_KEYBOARD, HelpTopics.entry(PaneWallPage.TERMINAL, "keys").lessonId);
        assertNull(HelpTopics.entry(PaneWallPage.TERMINAL, "sessions").lessonId);
        assertNull(HelpTopics.entry(PaneWallPage.TERMINAL, "divider").lessonId);
        // The keyboard lesson shows and hides the keyboard; the prefix key and the chords are a
        // different control, so neither hands practice to it.
        assertNull(HelpTopics.entry(PaneWallPage.TERMINAL, "prefix").lessonId);
        assertNull(HelpTopics.entry(PaneWallPage.TERMINAL, "shortcuts").lessonId);
    }

    @Test public void theTerminalKeepsEveryControlItMeasuresToday() {
        List<String> ids = ids(PaneWallPage.TERMINAL);
        for (String id : new String[] {"dock", "az", "status", "sessions", "windows", "stats",
                                       "divider", "prefix", "space"}) {
            assertTrue(id, ids.contains(id));
        }
        assertTrue(ids.contains("corners"));
        assertTrue(ids.contains("keys"));
        assertTrue(ids.contains("shortcuts"));
    }

    @Test public void theTerminalGroupsTheAdvancedMaterialApart() {
        assertEquals(HelpTopics.Group.EVERYDAY, HelpTopics.entry(PaneWallPage.TERMINAL, "dock").group);
        assertEquals(HelpTopics.Group.EVERYDAY, HelpTopics.entry(PaneWallPage.TERMINAL, "az").group);
        assertEquals(HelpTopics.Group.EVERYDAY, HelpTopics.entry(PaneWallPage.TERMINAL, "status").group);
        assertEquals(HelpTopics.Group.EVERYDAY, HelpTopics.entry(PaneWallPage.TERMINAL, "corners").group);
        assertEquals(HelpTopics.Group.EVERYDAY, HelpTopics.entry(PaneWallPage.TERMINAL, "sessions").group);
        assertEquals(HelpTopics.Group.EVERYDAY, HelpTopics.entry(PaneWallPage.TERMINAL, "windows").group);
        assertEquals(HelpTopics.Group.KEYBOARD, HelpTopics.entry(PaneWallPage.TERMINAL, "keys").group);
        assertEquals(HelpTopics.Group.KEYBOARD, HelpTopics.entry(PaneWallPage.TERMINAL, "prefix").group);
        assertEquals(HelpTopics.Group.KEYBOARD, HelpTopics.entry(PaneWallPage.TERMINAL, "space").group);
        assertEquals(HelpTopics.Group.KEYBOARD, HelpTopics.entry(PaneWallPage.TERMINAL, "settings").group);
        assertEquals(HelpTopics.Group.MULTITASKING, HelpTopics.entry(PaneWallPage.TERMINAL, "divider").group);
        assertEquals(HelpTopics.Group.MULTITASKING, HelpTopics.entry(PaneWallPage.TERMINAL, "shortcuts").group);
    }

    @Test public void homeAndDisplayAreEverydayApartFromTheKeyboardsOwnTopic() {
        for (PaneWallPage place : new PaneWallPage[] {PaneWallPage.WIDGETS, PaneWallPage.DISPLAY}) {
            for (HelpTopics.Entry entry : HelpTopics.forPlace(place)) {
                assertEquals(entry.id, "settings".equals(entry.id)
                    ? HelpTopics.Group.KEYBOARD : HelpTopics.Group.EVERYDAY, entry.group);
            }
        }
        assertTrue(ids(PaneWallPage.DISPLAY).containsAll(
            java.util.Arrays.asList("status", "windows", "stats", "start", "scale", "touchpad")));
        assertTrue(ids(PaneWallPage.WIDGETS).containsAll(
            java.util.Arrays.asList("status", "widget", "empty")));
    }

    @Test public void everyPlaceSaysWhereTheLauncherSettingsAre() {
        // The cog is a keyboard key, and the keyboard is up wherever the user is, so the topic is
        // the same one on all three places.
        for (PaneWallPage place : PaneWallPage.values()) {
            HelpTopics.Entry entry = HelpTopics.entry(place, "settings");
            assertNotNull(place.toString(), entry);
            assertEquals(HelpTopics.Group.KEYBOARD, entry.group);
            assertFalse(HelpTopics.topicOnly(entry.id));
        }
    }

    @Test public void aRelatedTopicIsAnotherTopicOfTheSamePlace() {
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            if (entry.relatedId == null) continue;
            assertNotEquals(entry.id, entry.relatedId);
            assertNotNull(entry.id, HelpTopics.entry(entry.place, entry.relatedId));
        }
    }

    @Test public void lookupIsPerPlaceAndForgivingOfAnUnknownId() {
        assertNotNull(HelpTopics.entry(PaneWallPage.TERMINAL, "status"));
        assertNotNull(HelpTopics.entry(PaneWallPage.WIDGETS, "status"));
        assertNull(HelpTopics.entry(PaneWallPage.WIDGETS, "sessions"));
        assertNull(HelpTopics.entry(PaneWallPage.TERMINAL, "nothing_by_this_name"));
    }

    @Test public void aPlacesEntriesCarryDistinctCopyForDistinctControls() {
        for (PaneWallPage place : PaneWallPage.values()) {
            Set<Integer> purposes = new HashSet<>();
            for (HelpTopics.Entry entry : HelpTopics.forPlace(place)) {
                assertTrue(entry.id, purposes.add(entry.purposeRes));
            }
        }
    }
}
