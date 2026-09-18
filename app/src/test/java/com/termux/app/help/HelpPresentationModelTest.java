package com.termux.app.help;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * What exploration is showing: the controls it measured, the one that is selected, and the topic and
 * colour each of them wears. The model never draws and never measures.
 */
@RunWith(RobolectricTestRunner.class)
public class HelpPresentationModelTest {

    private static final int ACCENT = Color.rgb(0xf0, 0xb4, 0x8a);

    /** Every control this place has a topic for, as if all of them had measured. */
    private Set<String> everything(PaneWallPage place) {
        Set<String> ids = new LinkedHashSet<>();
        for (HelpTopics.Entry entry : HelpTopics.forPlace(place)) {
            if (entry.targetId != null) ids.add(entry.targetId);
        }
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

    private List<String> targetIds(List<HelpTopics.Entry> entries) {
        List<String> ids = new ArrayList<>();
        for (HelpTopics.Entry entry : entries) ids.add(entry.targetId);
        return ids;
    }

    @Test public void everyPlaceOpensWithItsControlsMarkedAndNothingSelected() {
        for (PaneWallPage place : PaneWallPage.values()) {
            HelpPresentationModel model = opened(place);
            assertEquals(place.name(), place, model.place());
            assertNull(place.name(), model.selectedTargetId());
            assertNull(place.name(), model.selectedTopicId());
            assertFalse(place.name(), model.markers().isEmpty());
        }
    }

    /** The markers are the measured controls, in catalogue order — which is their numbering. */
    @Test public void markersAreTheMeasuredControlsInCatalogueOrder() {
        HelpPresentationModel model = new HelpPresentationModel();
        model.open(PaneWallPage.TERMINAL, Arrays.asList("dock", "status", "keys"));
        List<String> ids = targetIds(model.markers());
        assertEquals(Arrays.asList("status", "dock", "keys"), ids);
        for (HelpTopics.Entry entry : model.markers())
            assertTrue(entry.id, model.isMeasured(entry.targetId));
    }

    /** A topic with nothing to point at is read, never marked. */
    @Test public void aTopicWithNoControlIsNeverAMarker() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        for (HelpTopics.Entry entry : model.markers()) assertNotNull(entry.id, entry.targetId);
        assertNull(model.select("copy_paste"));
        assertNull(model.selectedTargetId());
    }

    @Test public void aControlIsSelectedByEitherOfItsNames() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        HelpTopics.Entry byTarget = model.select("sessions");
        assertNotNull(byTarget);
        assertEquals("hierarchy", byTarget.id);
        assertEquals("sessions", model.selectedTargetId());
        assertEquals("hierarchy", model.selectedTopicId());
        model.clearSelection();
        HelpTopics.Entry byTopic = model.select("hierarchy");
        assertNotNull(byTopic);
        assertEquals("sessions", model.selectedTargetId());
    }

    /** The explorer names controls; the lists name topics. One lookup answers both. */
    @Test public void everyTargetIdResolvesToItsOwnTopic() {
        HelpPresentationModel terminal = opened(PaneWallPage.TERMINAL);
        assertEquals("hierarchy", terminal.topicFor("sessions").id);
        assertEquals("panes", terminal.topicFor("divider").id);
        assertEquals("shortcuts", terminal.topicFor("prefix").id);
        assertEquals("windows", terminal.topicFor("windows").id);
        HelpPresentationModel widgets = opened(PaneWallPage.WIDGETS);
        assertEquals("pages", widgets.topicFor("empty").id);
        HelpPresentationModel display = opened(PaneWallPage.DISPLAY);
        assertEquals("display_apps", display.topicFor("windows").id);
    }

    @Test public void aControlThatDidNotMeasureCannotBeSelected() {
        HelpPresentationModel model = new HelpPresentationModel();
        model.open(PaneWallPage.TERMINAL, everythingBut(PaneWallPage.TERMINAL, "dock"));
        assertNotNull(model.select("status"));
        assertNull("a control that is away cannot be pointed at", model.select("dock"));
        assertEquals("the selection is left alone", "status", model.selectedTargetId());
        assertNull(model.select("no_such_thing"));
        assertEquals("status", model.selectedTargetId());
    }

    /**
     * A control that goes away keeps its topic's name, so the view can say which topic to read
     * instead of quietly pointing at another control.
     */
    @Test public void aVanishedControlStopsBeingMeasuredAndKeepsItsTopic() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.select("dock");
        assertTrue(model.selectedMeasured());
        model.remeasure(everythingBut(PaneWallPage.TERMINAL, "dock"));
        assertFalse(model.selectedMeasured());
        assertEquals("dock", model.selectedTopicId());
        assertFalse(targetIds(model.markers()).contains("dock"));
    }

    @Test public void openingAgainClearsTheSelection() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        model.select("status");
        model.open(PaneWallPage.DISPLAY, everything(PaneWallPage.DISPLAY));
        assertNull(model.selectedTargetId());
        assertEquals(PaneWallPage.DISPLAY, model.place());
    }

    /** A control's colour is its own identity, not its position in what happens to be on screen. */
    @Test public void colourFollowsTheControlRatherThanWhatElseIsUp() {
        HelpPresentationModel model = opened(PaneWallPage.TERMINAL);
        int dock = model.markerColor(ACCENT, "dock", false);
        int status = model.markerColor(ACCENT, "status", false);
        assertNotEquals(dock, status);
        model.remeasure(everythingBut(PaneWallPage.TERMINAL, "status", "keys"));
        assertEquals(dock, model.markerColor(ACCENT, "dock", false));
        assertNotEquals("a light wash needs a deeper colour",
            dock, model.markerColor(ACCENT, "dock", true));
        assertNotEquals(0, model.titleColor(ACCENT, "dock", Color.BLACK));
    }

    @Test public void nothingIsSelectedUntilSomethingIs() {
        HelpPresentationModel model = new HelpPresentationModel();
        assertEquals(PaneWallPage.TERMINAL, model.place());
        assertNull(model.selected());
        assertNull(model.selectedTopicId());
        assertFalse(model.selectedMeasured());
        assertTrue(model.markers().isEmpty());
        assertFalse(model.isMeasured(null));
        model.clearSelection();
        assertNull(model.selectedTargetId());
    }
}
