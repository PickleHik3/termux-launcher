package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * One card of the first-boot run: the chapter it belongs to, and what it asks for at each stage.
 *
 * <p>A lesson is cleared by observing the launcher do the thing, never by a Next button, so its
 * stages are an ordered list and the card asks for the next one it has not seen yet. "Put the
 * keyboard away, then bring it back" is one lesson with two stages, each with its own title,
 * sentence, control and gesture; the card swaps between them without celebrating the first.
 *
 * <p>The closing card has no stages at all: it ends the run on its own Start.
 */
public final class TourStep {

    /**
     * Which side of its control a card asks to stand on.
     *
     * <p>Almost every card takes {@link #AUTO}: the half of the screen the control is standing in
     * is a better answer than anything a card could have decided for itself. The exception is a
     * card about a surface that sprouts from one end of the screen — the palette — where the side
     * is part of what the card is.
     */
    public enum Placement {
        /** Whichever half of the overlay the control is standing in decides. */
        AUTO,
        /** Above the control, whenever that side can hold the card. */
        ABOVE,
        /** Below the control, whenever that side can hold the card. */
        BELOW
    }

    /** What kind of card this is. */
    public enum Kind {
        /** A lesson: one control and one gesture per stage, cleared by watching the user do it. */
        LESSON,
        /** The last card, which ends the run on its own action. */
        CLOSING
    }

    /** One stage of a lesson: what the card says, what it points at, and what clears it. */
    public static final class Stage {
        @StringRes public final int titleRes;
        @StringRes public final int bodyRes;
        @NonNull public final String targetId;
        @NonNull public final String signal;
        @NonNull public final TourGesture gesture;

        public Stage(@StringRes int titleRes, @StringRes int bodyRes, @NonNull String targetId,
                     @NonNull String signal, @NonNull TourGesture gesture) {
            if (titleRes == 0 || bodyRes == 0)
                throw new IllegalArgumentException("a stage needs a title and a sentence");
            this.titleRes = titleRes;
            this.bodyRes = bodyRes;
            this.targetId = targetId;
            this.signal = signal;
            this.gesture = gesture;
        }
    }

    /** Stable id, used by prefs, by help's practice and by the tests; never shown. */
    @NonNull public final String id;

    @NonNull public final Kind kind;

    /** The chapter this lesson belongs to, from 0; -1 for the closing card. */
    public final int chapter;

    /** The chapter's name, as its label reads it; 0 for the closing card. */
    @StringRes public final int chapterRes;

    /** The side of its control this card asks to stand on. */
    @NonNull public final Placement placement;

    @NonNull private final Stage[] stages;

    private TourStep(@NonNull String id, @NonNull Kind kind, int chapter, @StringRes int chapterRes,
                     @NonNull Placement placement, @NonNull Stage[] stages) {
        this.id = id;
        this.kind = kind;
        this.chapter = chapter;
        this.chapterRes = chapterRes;
        this.placement = placement;
        this.stages = stages.clone();
    }

    /** A lesson in {@code chapter}, walked one stage at a time. */
    @NonNull
    public static TourStep lesson(@NonNull String id, int chapter, @StringRes int chapterRes,
                                  @NonNull Placement placement, @NonNull Stage... stages) {
        if (stages.length == 0)
            throw new IllegalArgumentException("lesson " + id + " asks for nothing");
        if (chapter < 0 || chapterRes == 0)
            throw new IllegalArgumentException("lesson " + id + " belongs to no chapter");
        return new TourStep(id, Kind.LESSON, chapter, chapterRes, placement, stages);
    }

    /** The card the run ends on. */
    @NonNull
    public static TourStep closing(@NonNull String id) {
        return new TourStep(id, Kind.CLOSING, -1, 0, Placement.AUTO, new Stage[0]);
    }

    /** Whether this is the closing card, which ends the run on its own action. */
    public boolean isClosingCard() {
        return kind == Kind.CLOSING;
    }

    /** How many stages this lesson walks; 0 for the closing card. */
    public int stageCount() {
        return stages.length;
    }

    /** The signal this lesson is waiting for at {@code stage}, or null past its last stage. */
    public String signalAt(int stage) {
        return stage >= 0 && stage < stages.length ? stages[stage].signal : null;
    }

    /** The control to glow at {@code stage}; nothing on the closing card. */
    @NonNull
    public String targetIdAt(int stage) {
        Stage at = stageAt(stage);
        return at == null ? TourTargets.NONE : at.targetId;
    }

    /** The gesture to demonstrate at {@code stage}. */
    @NonNull
    public TourGesture gestureAt(int stage) {
        Stage at = stageAt(stage);
        return at == null ? TourGesture.NONE : at.gesture;
    }

    /** The title at {@code stage}, or 0 on the closing card. */
    @StringRes
    public int titleResAt(int stage) {
        Stage at = stageAt(stage);
        return at == null ? 0 : at.titleRes;
    }

    /** The sentence at {@code stage}, or 0 on the closing card. */
    @StringRes
    public int bodyResAt(int stage) {
        Stage at = stageAt(stage);
        return at == null ? 0 : at.bodyRes;
    }

    /** The stage at {@code stage}, the last one past the end, or null on the closing card. */
    private Stage stageAt(int stage) {
        if (stages.length == 0) return null;
        return stages[Math.max(0, Math.min(stage, stages.length - 1))];
    }

    /**
     * Whether this card's controls live on the terminal place. The status bar is on every place
     * and so is the page's own border with the two pills on it; everything else — the keyboard,
     * the panes, the dock — is the terminal's, and a card asking for it while the wall rests on
     * another place is asking for a control that is not there.
     */
    public boolean taughtOnTheTerminal() {
        for (Stage stage : stages)
            if (!isOnEveryPlace(stage.targetId)) return true;
        return false;
    }

    private static boolean isOnEveryPlace(@NonNull String target) {
        return TourTargets.STATUS_BAR.equals(target) || TourTargets.NONE.equals(target)
            || TourTargets.PAGE_BORDER.equals(target)
            || TourTargets.KEYBOARD_GRABBER.equals(target)
            || TourTargets.STATUS_GRABBER.equals(target);
    }
}
