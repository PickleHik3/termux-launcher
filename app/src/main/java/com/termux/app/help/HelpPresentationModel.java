package com.termux.app.help;

import com.termux.app.wall.PaneWallPage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What help is showing and what it wants done about it. Commands go in, an {@link Effect} for the
 * renderer comes back, and every question a renderer asks — which entries to list, which single
 * control to point at, which colour it wears — is answered here.
 *
 * <p>Pure: it holds no views and measures nothing. The caller says which controls it could
 * measure this pass ({@code HelpTargets.Snapshot} ids) and re-says it after every layout.
 */
public final class HelpPresentationModel {

    /** Choosing a topic, reading one, or the all-controls overview. */
    public enum Mode { TOPICS, TOPIC, OVERVIEW }

    /** Terminal has enough controls that a chooser is kinder than all of them at once. */
    public static final Mode TERMINAL_DEFAULT_MODE = Mode.TOPICS;
    /** Home is small: the overview is the whole of it. */
    public static final Mode WIDGETS_DEFAULT_MODE = Mode.OVERVIEW;
    /** Display's default, to be flipped after the screen check the spec asks for. */
    public static final Mode DISPLAY_DEFAULT_MODE = Mode.OVERVIEW;

    /** What the renderer is being asked to do, if anything. */
    public enum EffectKind { NONE, DEMONSTRATE, CLOSE, CLOSE_AND_PRACTICE }

    /** A request to the renderer; reading help asks for nothing. */
    public static final class Effect {
        public final EffectKind kind;
        /** The control to demonstrate over, for {@link EffectKind#DEMONSTRATE}. */
        public final String targetId;
        /** The lesson to start, for {@link EffectKind#CLOSE_AND_PRACTICE}. */
        public final String lessonId;

        public static final Effect NONE = new Effect(EffectKind.NONE, null, null);
        public static final Effect CLOSE = new Effect(EffectKind.CLOSE, null, null);

        public static Effect demonstrate(String targetId) {
            return new Effect(EffectKind.DEMONSTRATE, targetId, null);
        }
        public static Effect practice(String lessonId) {
            return new Effect(EffectKind.CLOSE_AND_PRACTICE, null, lessonId);
        }
        private Effect(EffectKind kind, String targetId, String lessonId) {
            this.kind = kind;
            this.targetId = targetId;
            this.lessonId = lessonId;
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Effect)) return false;
            Effect that = (Effect) other;
            return kind == that.kind && equal(targetId, that.targetId) && equal(lessonId, that.lessonId);
        }
        @Override public int hashCode() {
            int hash = kind.hashCode();
            hash = 31 * hash + (targetId == null ? 0 : targetId.hashCode());
            return 31 * hash + (lessonId == null ? 0 : lessonId.hashCode());
        }
        @Override public String toString() {
            return kind + (targetId != null ? "(" + targetId + ")" : "")
                + (lessonId != null ? "(" + lessonId + ")" : "");
        }
        private static boolean equal(String a, String b) { return a == null ? b == null : a.equals(b); }
    }

    private PaneWallPage place = PaneWallPage.TERMINAL;
    private Mode mode = TERMINAL_DEFAULT_MODE;
    private String selectedId;
    private boolean basicsOnly;
    private boolean open;
    private int page;
    private int pageCount = 1;
    private Set<String> measurable = Collections.emptySet();

    /** The mode a place opens in. One constant each, so a screen check can flip one. */
    public static Mode defaultMode(PaneWallPage place) {
        switch (place) {
            case WIDGETS: return WIDGETS_DEFAULT_MODE;
            case DISPLAY: return DISPLAY_DEFAULT_MODE;
            default: return TERMINAL_DEFAULT_MODE;
        }
    }

    // ---- commands -------------------------------------------------------------------------

    /** Open help for a place, with the controls the caller could measure this pass. */
    public Effect open(PaneWallPage place, Collection<String> measurableIds) {
        this.place = place == null ? PaneWallPage.TERMINAL : place;
        this.mode = defaultMode(this.place);
        this.selectedId = null;
        this.basicsOnly = false;
        this.open = true;
        this.page = 0;
        this.pageCount = 1;
        this.measurable = copy(measurableIds);
        return Effect.NONE;
    }

    /** Read one topic. Browsing help never touches the launcher, so nothing is requested. */
    public Effect selectTopic(String id) {
        if (HelpTopics.entry(place, id) == null) return Effect.NONE;
        selectedId = id;
        mode = Mode.TOPIC;
        return Effect.NONE;
    }

    /** Back to the chooser. */
    public Effect backToTopics() {
        selectedId = null;
        mode = Mode.TOPICS;
        return Effect.NONE;
    }

    /** List the everyday topics only. */
    public Effect showBasics() {
        basicsOnly = true;
        selectedId = null;
        mode = Mode.TOPICS;
        page = 0;
        return Effect.NONE;
    }

    /** The all-controls overview, from the first page. */
    public Effect showAll() {
        basicsOnly = false;
        selectedId = null;
        mode = Mode.OVERVIEW;
        page = 0;
        return Effect.NONE;
    }

    /** The page before this one; the first page stays the first page. */
    public Effect previous() {
        page = Math.max(0, page - 1);
        return Effect.NONE;
    }

    /** The page after this one; the last page stays the last page. */
    public Effect next() {
        page = Math.min(pageCount - 1, page + 1);
        return Effect.NONE;
    }

    /** How many pages the layout needed; the reader is brought back inside a shorter run. */
    public Effect setPageCount(int count) {
        pageCount = Math.max(1, count);
        page = Math.min(page, pageCount - 1);
        return Effect.NONE;
    }

    /** Play the gesture over the selected control, with help still up. */
    public Effect showGesture() {
        return canShowGesture() ? Effect.demonstrate(selectedId) : Effect.NONE;
    }

    /** Close help and hand the user the topic's one-step practice. */
    public Effect tryIt() {
        if (!canTryIt()) return Effect.NONE;
        String lessonId = selected().lessonId;
        open = false;
        return Effect.practice(lessonId);
    }

    /** Dismiss help. */
    public Effect close() {
        open = false;
        return Effect.CLOSE;
    }

    /** The controls the caller could measure on the latest layout pass. */
    public Effect remeasure(Collection<String> measurableIds) {
        measurable = copy(measurableIds);
        return Effect.NONE;
    }

    // ---- queries --------------------------------------------------------------------------

    public Mode mode() { return mode; }
    public PaneWallPage place() { return place; }
    public String selectedId() { return selectedId; }
    public boolean basicsOnly() { return basicsOnly; }
    public boolean isOpen() { return open; }
    public int page() { return page; }
    public int pageCount() { return pageCount; }

    /**
     * The topics to list: this place's catalogue, everyday only when the basics filter is on. A
     * topic whose control is away is still listed — it explains itself rather than vanishing.
     */
    public List<HelpTopics.Entry> entries() {
        List<HelpTopics.Entry> all = HelpTopics.forPlace(place);
        if (!basicsOnly) return all;
        List<HelpTopics.Entry> everyday = new ArrayList<>();
        for (HelpTopics.Entry entry : all) {
            if (entry.group == HelpTopics.Group.EVERYDAY) everyday.add(entry);
        }
        return Collections.unmodifiableList(everyday);
    }

    /** The topic being read, or null. */
    public HelpTopics.Entry selected() { return HelpTopics.entry(place, selectedId); }

    /** Whether the caller could measure this control on the last pass. */
    public boolean isMeasurable(String id) { return id != null && measurable.contains(id); }

    /** Whether the topic being read has its control on screen. */
    public boolean selectedMeasurable() { return isMeasurable(selectedId); }

    /**
     * The one control to point at, or null when it is not on screen. Never another control: a
     * missing topic is explained, not redirected.
     */
    public String highlightTargetId() { return selectedMeasurable() ? selectedId : null; }

    /** The line that says how to bring the control back, or 0 when it is on screen. */
    public int revealRes() {
        HelpTopics.Entry entry = selected();
        return entry == null || selectedMeasurable() ? 0 : entry.revealRes;
    }

    /** A topic worth reading instead while this one's control is away, or null. */
    public String relatedTopicId() {
        HelpTopics.Entry entry = selected();
        return entry == null || selectedMeasurable() ? null : entry.relatedId;
    }

    /** In topic mode the one highlight wears the place accent. */
    public int topicHighlightColor(int accent) { return accent; }

    /**
     * An entry's overview colour, from its identity in the place's full catalogue, so a control
     * keeps its colour whatever else is on screen.
     */
    public int overviewColor(int accent, HelpTopics.Entry entry) {
        return HelpPalette.boxColor(accent, entry.identityIndex, HelpTopics.sizeFor(entry.place));
    }

    /**
     * The section label for the page being read: the group of the first entry on it. Pages are
     * geometric, so the entries are apportioned evenly over the count the renderer reported.
     */
    /** The entries the overview boxes and cards: {@link #entries()} minus the chooser-only topics. */
    public List<HelpTopics.Entry> overviewEntries() {
        List<HelpTopics.Entry> out = new ArrayList<>();
        for (HelpTopics.Entry entry : entries()) {
            if (!HelpTopics.topicOnly(entry.id)) out.add(entry);
        }
        return Collections.unmodifiableList(out);
    }

    public int sectionLabelRes() {
        if (mode != Mode.OVERVIEW) return 0;
        List<HelpTopics.Entry> entries = overviewEntries();
        if (entries.isEmpty()) return 0;
        int first = (int) ((long) page * entries.size() / Math.max(1, pageCount));
        return entries.get(Math.min(first, entries.size() - 1)).group.labelRes;
    }

    /**
     * The section label for a page the renderer has actually packed: the group of the entry whose
     * card sits first on it. Pages are filled by collision rather than by count, so the even split
     * above is only the answer when the renderer cannot name that entry.
     */
    public int sectionLabelResFor(String firstEntryIdOnPage) {
        if (mode != Mode.OVERVIEW) return 0;
        HelpTopics.Entry entry = HelpTopics.entry(place, firstEntryIdOnPage);
        return entry == null ? sectionLabelRes() : entry.group.labelRes;
    }

    /** Show gesture belongs to a topic whose control is on screen. */
    public boolean canShowGesture() { return mode == Mode.TOPIC && selectedMeasurable(); }

    /** Try it needs a control on screen and a lesson to hand the user. */
    public boolean canTryIt() {
        HelpTopics.Entry entry = selected();
        return mode == Mode.TOPIC && selectedMeasurable() && entry != null && entry.lessonId != null;
    }

    private static Set<String> copy(Collection<String> ids) {
        return ids == null ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }
}
