package com.termux.app.tour;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One card of the first-boot tour: what it says, what it points at, and which gestures clear it.
 *
 * <p>A step is cleared by observing the launcher do the thing, never by a Next button, so the
 * signals are an ordered list: the card asks for the next one it has not seen yet. "Drag the
 * status bar down, then up" is one step with two signals and two traces, not two cards.
 *
 * <p>The second line is the "then" half of such a step — it appears once the first signal has
 * landed, so the card never asks for two things at once.
 *
 * <p>A lesson may end with one stage that is only shown: {@link #endsShown} adds a last stage with
 * no signal behind it, for the one thing the run says without waiting to see it done. The terminal's
 * hold needs a program that follows the mouse before it shows anything at all, so the run shows the
 * gesture and moves on when the user says they have read it.
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

    /** What kind of card this is, which is what decides the buttons it offers. */
    public enum Kind {
        /** A lesson: one control, one gesture per stage, cleared by watching the user do it. */
        LESSON,
        /** A question with an answer per button and no gesture at all. */
        CHOICE,
        /** The first card, which offers the run rather than being part of it. */
        WELCOME,
        /** The last card, which ends the run on its own action. */
        CLOSING
    }

    /** The buttons a lesson always offers. */
    private static final TourAction[] LESSON_ACTIONS =
        {TourAction.BACK, TourAction.SKIP_STEP, TourAction.END_TOUR};

    /** The closing card's one action. */
    private static final TourAction[] CLOSING_ACTIONS = {TourAction.START_USING};

    /** The welcome card's two answers. */
    private static final TourAction[] WELCOME_ACTIONS =
        {TourAction.TAKE_THE_TOUR, TourAction.NOT_NOW};

    /** Stable id, used by prefs and by the tests; never shown. */
    public final String id;

    /** Which kind of card this is. */
    public final Kind kind;

    /**
     * String resource for the small line above the card's title, or 0 on a card with no title.
     * Only the two cards that are read rather than performed — the welcome and the closing one —
     * carry one; a lesson is a single sentence beside the control it names.
     */
    public final int kickerRes;

    /** String resource for the card's title, or 0 on a card that is one sentence. */
    public final int titleRes;

    /**
     * Drawable resource for a picture the card shows under its sentence, or 0 on a card with
     * none. One card shows the user the thing it is asking about — the row of keys this release
     * ships — because a row of keys is quicker looked at than described.
     */
    public final int imageRes;

    /** String resource for the card's sentence. */
    public final int copyRes;

    /** String resource for the follow-up sentence, or 0 when the step asks for one gesture. */
    public final int secondLineRes;

    /**
     * One sentence per stage, the first of which is {@link #copyRes}. A 0 in the list means "keep
     * saying what the last stage said", which is what a step with one sentence and two gestures —
     * "drag the status bar down, then up" — is.
     */
    private final int[] copyLines;

    /** The control the card glows while it is asking for its first gesture. */
    public final String targetId;

    /** The signals that clear this step, in the order the user performs them. */
    private final String[] signals;

    /** One gesture per signal, for the finger trace; a stepless card carries a single NONE. */
    private final TourGesture[] gestures;

    /**
     * One target per stage. A step whose two halves point at different controls — "tap +", then
     * "tap the chip it made" — would otherwise glow the control the user has already used while
     * asking about another one, which is worse than glowing nothing.
     */
    private final String[] targets;

    /**
     * Whether this card rests at the top of the screen instead of against its control.
     *
     * <p>For the one card whose gesture happens all over the screen: the A-Z scrub filters the app
     * row and throws a preview up beside the finger, and a card anchored to the row it names sits
     * exactly on top of both. A card the user cannot see past is worse than one they have to
     * glance up at.
     */
    public final boolean topAnchored;

    /**
     * Whether this lesson's last stage is only shown: it names a gesture and a control, waits for
     * no signal at all, and is left by the user's own Done.
     */
    public final boolean endsShown;

    /**
     * Whether this card's targets are a keyboard chord rather than one target per stage.
     *
     * <p>A chord card has a single signal — the thing the chord does — and three or four keys to
     * point at on the way there, so its glow cannot be indexed by the stage. It is indexed by what
     * the keyboard has latched instead; {@link TourChordGlow} is the whole rule.
     */
    public final boolean chordGlow;

    /** The side of its control this card asks to stand on. */
    public final Placement placement;

    /** The buttons this card offers, in the order they are read. */
    private final List<TourAction> actions;

    public TourStep(String id, int copyRes, int secondLineRes, String targetId,
                    String[] signals, TourGesture[] gestures) {
        this(id, copyRes, secondLineRes, new String[] {targetId}, signals, gestures);
    }

    public TourStep(String id, int copyRes, int secondLineRes, String[] targetIds,
                    String[] signals, TourGesture[] gestures) {
        this(id, copyRes, secondLineRes, targetIds, signals, gestures, false);
    }

    public TourStep(String id, int copyRes, int secondLineRes, String[] targetIds,
                    String[] signals, TourGesture[] gestures, boolean topAnchored) {
        this(id, copyRes, secondLineRes, targetIds, signals, gestures, topAnchored, false);
    }

    public TourStep(String id, int copyRes, int secondLineRes, String[] targetIds,
                    String[] signals, TourGesture[] gestures, boolean topAnchored,
                    boolean chordGlow) {
        this(id, new int[] {copyRes, secondLineRes}, targetIds, signals, gestures, topAnchored,
            chordGlow);
    }

    /**
     * The general shape: a sentence per stage. A card that walks three controls — the +, the chip
     * it made, the × the chip reveals — asks for one of them at a time, so it needs a sentence per
     * stage rather than a first line and a second one.
     */
    public TourStep(String id, int[] copyLines, String[] targetIds, String[] signals,
                    TourGesture[] gestures, boolean topAnchored, boolean chordGlow) {
        this(id, copyLines, targetIds, signals, gestures, topAnchored, chordGlow, false);
    }

    /**
     * The same shape, for a card that asks to stand on a side of its control rather than letting
     * the overlay's halves decide.
     */
    public TourStep(String id, int[] copyLines, String[] targetIds, String[] signals,
                    TourGesture[] gestures, boolean topAnchored, boolean chordGlow,
                    Placement placement) {
        this(id, signals.length == 0 ? Kind.CLOSING : Kind.LESSON, 0, 0, copyLines, targetIds,
            signals, gestures, topAnchored, chordGlow, null, false, placement);
    }

    /** The same shape, for a lesson whose last stage is only shown. */
    public TourStep(String id, int[] copyLines, String[] targetIds, String[] signals,
                    TourGesture[] gestures, boolean topAnchored, boolean chordGlow,
                    boolean endsShown) {
        this(id, signals.length == 0 ? Kind.CLOSING : Kind.LESSON, copyLines, targetIds, signals,
            gestures, topAnchored, chordGlow, null, endsShown);
    }

    /**
     * The whole shape: a kind, a sentence per stage, and the buttons the card offers. A card that
     * names no buttons takes the ones its kind always offers; a choice has to name its own,
     * because its answers are the card.
     */
    public TourStep(String id, Kind kind, int[] copyLines, String[] targetIds, String[] signals,
                    TourGesture[] gestures, boolean topAnchored, boolean chordGlow,
                    TourAction[] actions) {
        this(id, kind, copyLines, targetIds, signals, gestures, topAnchored, chordGlow, actions,
            false);
    }

    /** The whole shape, for a card that ends on a stage it only shows. */
    public TourStep(String id, Kind kind, int[] copyLines, String[] targetIds, String[] signals,
                    TourGesture[] gestures, boolean topAnchored, boolean chordGlow,
                    TourAction[] actions, boolean endsShown) {
        this(id, kind, 0, 0, copyLines, targetIds, signals, gestures, topAnchored, chordGlow,
            actions, endsShown);
    }

    /**
     * The same shape for a card that is read rather than performed: a small line, a title and
     * then the sentence. The welcome and closing cards share it, so the run opens and closes on
     * the same object.
     */
    public TourStep(String id, Kind kind, int kickerRes, int titleRes, int[] copyLines,
                    String[] targetIds, String[] signals, TourGesture[] gestures,
                    boolean topAnchored, boolean chordGlow, TourAction[] actions,
                    boolean endsShown) {
        this(id, kind, kickerRes, titleRes, copyLines, targetIds, signals, gestures, topAnchored,
            chordGlow, actions, endsShown, Placement.AUTO);
    }

    /** The whole shape, for a card that also asks for a side of its control. */
    public TourStep(String id, Kind kind, int kickerRes, int titleRes, int[] copyLines,
                    String[] targetIds, String[] signals, TourGesture[] gestures,
                    boolean topAnchored, boolean chordGlow, TourAction[] actions,
                    boolean endsShown, Placement placement) {
        this(id, kind, kickerRes, titleRes, 0, copyLines, targetIds, signals, gestures,
            topAnchored, chordGlow, actions, endsShown, placement);
    }

    /** The whole shape, for the one card that shows a picture of what it is asking about. */
    public TourStep(String id, Kind kind, int kickerRes, int titleRes, int imageRes,
                    int[] copyLines, String[] targetIds, String[] signals, TourGesture[] gestures,
                    boolean topAnchored, boolean chordGlow, TourAction[] actions,
                    boolean endsShown, Placement placement) {
        if (endsShown && signals.length == 0)
            throw new IllegalArgumentException("step " + id + " shows a stage it never reaches");
        if (endsShown && gestures.length <= signals.length)
            throw new IllegalArgumentException("step " + id + " has no gesture for its shown stage");
        if (gestures.length < Math.max(1, signals.length))
            throw new IllegalArgumentException("step " + id + " has fewer gestures than signals");
        if (targetIds.length == 0)
            throw new IllegalArgumentException("step " + id + " has no target at all");
        if (copyLines.length == 0 || copyLines[0] == 0)
            throw new IllegalArgumentException("step " + id + " has no copy at all");
        this.id = id;
        this.kickerRes = kickerRes;
        this.titleRes = titleRes;
        this.imageRes = imageRes;
        this.copyLines = copyLines.clone();
        this.copyRes = copyLines[0];
        this.secondLineRes = copyLines.length > 1 ? copyLines[1] : 0;
        this.targets = targetIds.clone();
        this.targetId = this.targets[0];
        this.signals = signals.clone();
        this.gestures = gestures.clone();
        this.topAnchored = topAnchored;
        this.chordGlow = chordGlow;
        this.endsShown = endsShown;
        this.placement = placement == null ? Placement.AUTO : placement;
        if (kind == Kind.CHOICE && signals.length != 0)
            throw new IllegalArgumentException("choice " + id + " is cleared by a button, not a"
                + " gesture");
        TourAction[] offered = actions != null ? actions : defaultActions(kind);
        if (offered.length == 0)
            throw new IllegalArgumentException("card " + id + " offers no way on");
        this.kind = kind;
        this.actions = Collections.unmodifiableList(Arrays.asList(offered.clone()));
    }

    private static TourAction[] defaultActions(Kind kind) {
        switch (kind) {
            case CLOSING: return CLOSING_ACTIONS;
            case WELCOME: return WELCOME_ACTIONS;
            case LESSON: return LESSON_ACTIONS;
            default: return new TourAction[0];
        }
    }

    /** Whether this card carries a title above its sentence. */
    public boolean hasTitle() {
        return titleRes != 0;
    }

    /** Whether this card shows a picture under its sentence. */
    public boolean hasImage() {
        return imageRes != 0;
    }

    /** The buttons this card offers, in order. */
    public List<TourAction> actions() {
        return actions;
    }

    /** How many controls this card names; past the end, the last one stands for the rest. */
    public int targetCount() {
        return targets.length;
    }

    /**
     * The control to glow at {@code stage}. A step that named one target keeps it for every stage;
     * the last one named stands for anything past the end.
     */
    public String targetIdAt(int stage) {
        if (stage < 0) return targets[0];
        return stage < targets.length ? targets[stage] : targets[targets.length - 1];
    }

    /** How many signals clear this step; 0 for the closing card, which ends on its button. */
    public int signalCount() {
        return signals.length;
    }

    /**
     * How many stages this card walks: one per signal, and one more when it ends on a stage that
     * is only shown.
     */
    public int stageCount() {
        return signals.length + (endsShown ? 1 : 0);
    }

    /**
     * Whether {@code stage} is the one this card only shows. Nothing the launcher reports can
     * clear it — {@link #signalAt} answers null there — so the card waits for the user's Done.
     */
    public boolean isShownOnlyStage(int stage) {
        return endsShown && stage == signals.length;
    }

    /** The signal this step is waiting for at {@code stage}, or null when it waits for none. */
    public String signalAt(int stage) {
        return stage >= 0 && stage < signals.length ? signals[stage] : null;
    }

    /** The gesture to trace at {@code stage}. */
    public TourGesture gestureAt(int stage) {
        if (stage < 0) return gestures[0];
        return stage < gestures.length ? gestures[stage] : gestures[gestures.length - 1];
    }

    /** Whether the follow-up sentence is the one to show at {@code stage}. */
    public boolean showsSecondLineAt(int stage) {
        return secondLineRes != 0 && stage > 0;
    }

    /**
     * The sentence to show at {@code stage}: the last one this step names at or before it, so a
     * step whose second half asks for another gesture over the same control keeps its one line.
     */
    public int copyResAt(int stage) {
        int res = copyLines[0];
        for (int i = 1; i <= stage && i < copyLines.length; i++)
            if (copyLines[i] != 0) res = copyLines[i];
        return res;
    }

    /**
     * Whether this card's controls live on the terminal place. The status bar is on every place
     * and a card pointing at nothing can be read anywhere; everything else — the window row's +,
     * the keyboard, the panes, the dock — is the terminal's, and a card asking for it while the
     * wall rests on the display or the widgets is asking for a control that is not there.
     */
    public boolean taughtOnTheTerminal() {
        for (String target : targets)
            if (!TourTargets.STATUS_BAR.equals(target) && !TourTargets.NONE.equals(target))
                return true;
        return false;
    }

    /** Whether this is the closing card, which ends the run on its own action. */
    public boolean isClosingCard() {
        return kind == Kind.CLOSING;
    }

    /** Whether this is the card the run is offered on, which is not one of its steps. */
    public boolean isWelcomeCard() {
        return kind == Kind.WELCOME;
    }

    /** Whether this card is a question the user answers with one of its buttons. */
    public boolean isChoiceCard() {
        return kind == Kind.CHOICE;
    }
}
