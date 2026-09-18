package com.termux.app.help;

import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What "Explore this screen" is showing: the place, the controls the view could measure this pass,
 * and which one of them is selected. Reading lives in the help panel, so nothing here knows about
 * modes, pages or buttons.
 *
 * <p>Pure: it holds no views and measures nothing. The view says which controls it measured and
 * re-says it after every layout; every answer about a control — its topic, its colour, whether it
 * is still on screen — comes from here.
 */
public final class HelpPresentationModel {

    /**
     * The controls the overview draws a card for, in the order it draws them. Everything else help
     * can explain is read in the guide rather than pointed at, so that the screen the reader is
     * looking at stays legible; add an id here to give that control a card of its own.
     *
     * <p>The extra keys row is not one of them: its keys carry their own labels, one per cap.
     */
    public static final List<String> OVERVIEW_TARGET_IDS = Collections.unmodifiableList(
        Arrays.asList("dock", "status", "prefix", "settings"));

    private PaneWallPage place = PaneWallPage.TERMINAL;
    private String selectedTargetId;
    private Set<String> measured = Collections.emptySet();

    // ---- what the view measured -------------------------------------------------------------

    /** Start exploring a place with the controls measured on this pass; nothing is selected. */
    public void open(PaneWallPage place, Collection<String> measuredTargetIds) {
        this.place = place == null ? PaneWallPage.TERMINAL : place;
        this.selectedTargetId = null;
        this.measured = copy(measuredTargetIds);
    }

    /** The controls the view could measure on the latest layout pass. */
    public void remeasure(Collection<String> measuredTargetIds) {
        this.measured = copy(measuredTargetIds);
    }

    public PaneWallPage place() { return place; }

    /** Whether the view could measure this control on the last pass. */
    public boolean isMeasured(String targetId) {
        return targetId != null && measured.contains(targetId);
    }

    // ---- the markers ------------------------------------------------------------------------

    /**
     * One entry per measured control that has something to read, in catalogue order — the order the
     * markers are numbered in, so a control keeps its number and its colour while help is up.
     */
    public List<HelpTopics.Entry> markers() {
        List<HelpTopics.Entry> out = new ArrayList<>();
        for (HelpTopics.Entry entry : HelpTopics.forPlace(place)) {
            if (entry.targetId != null && measured.contains(entry.targetId)) out.add(entry);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The overview's cards: the curated controls the view could measure this pass, in the curated
     * order. A control the place does not have, or that this pass could not measure, has no card.
     */
    public List<HelpTopics.Entry> overview() {
        List<HelpTopics.Entry> out = new ArrayList<>();
        for (String targetId : OVERVIEW_TARGET_IDS) {
            if (!measured.contains(targetId)) continue;
            HelpTopics.Entry entry = HelpTopics.forTarget(place, targetId);
            if (entry != null) out.add(entry);
        }
        return Collections.unmodifiableList(out);
    }

    /** The topic explaining a measured control, by its target id or its own id; null when none. */
    public HelpTopics.Entry topicFor(String idOrTargetId) {
        return HelpTopics.entry(place, idOrTargetId);
    }

    // ---- the selection ----------------------------------------------------------------------

    /**
     * Select the control a marker or a tap landed on, named either way. Returns the topic that is
     * now selected, or null when nothing measured on this place answers to that name — and then the
     * previous selection is left alone.
     */
    public HelpTopics.Entry select(String idOrTargetId) {
        HelpTopics.Entry entry = topicFor(idOrTargetId);
        if (entry == null || entry.targetId == null || !measured.contains(entry.targetId)) return null;
        selectedTargetId = entry.targetId;
        return entry;
    }

    public void clearSelection() { selectedTargetId = null; }

    /** The selected control's target id, or null. */
    public String selectedTargetId() { return selectedTargetId; }

    /** The selected topic, or null when nothing is selected. */
    public HelpTopics.Entry selected() {
        return selectedTargetId == null ? null : HelpTopics.forTarget(place, selectedTargetId);
    }

    /** The selected topic's own id, which is what the reading panel is asked for. */
    public String selectedTopicId() {
        HelpTopics.Entry entry = selected();
        return entry == null ? null : entry.id;
    }

    /** Whether the selected control was still measured on the last pass. */
    public boolean selectedMeasured() { return isMeasured(selectedTargetId); }

    // ---- colour -----------------------------------------------------------------------------

    /**
     * A control's colour, from its identity in this place's catalogue rather than from what else is
     * on screen, so the same control is the same colour on every pass.
     */
    public int markerColor(int accent, String idOrTargetId, boolean lightMode) {
        return HelpPalette.boxColor(accent, HelpTopics.identityIndex(place, idOrTargetId),
            HelpTopics.sizeFor(place), lightMode);
    }

    /** The same identity, for the title on the card. */
    public int titleColor(int accent, String idOrTargetId, int cardFill) {
        return HelpPalette.titleColor(accent, HelpTopics.identityIndex(place, idOrTargetId),
            HelpTopics.sizeFor(place), cardFill);
    }

    private static Set<String> copy(Collection<String> ids) {
        return ids == null ? Collections.<String>emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }
}
