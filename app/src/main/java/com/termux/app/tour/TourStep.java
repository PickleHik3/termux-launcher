package com.termux.app.tour;

/**
 * One card of the first-boot tour: what it says, what it points at, and which gestures clear it.
 *
 * <p>A step is cleared by observing the launcher do the thing, never by a Next button, so the
 * signals are an ordered list: the card asks for the next one it has not seen yet. "Drag the
 * status bar down, then up" is one step with two signals and two traces, not two cards.
 *
 * <p>The second line is the "then" half of such a step — it appears once the first signal has
 * landed, so the card never asks for two things at once.
 */
public final class TourStep {

    /** Stable id, used by prefs and by the tests; never shown. */
    public final String id;

    /** String resource for the card's sentence. */
    public final int copyRes;

    /** String resource for the follow-up sentence, or 0 when the step asks for one gesture. */
    public final int secondLineRes;

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
     * Whether this card's targets are a keyboard chord rather than one target per stage.
     *
     * <p>A chord card has a single signal — the thing the chord does — and three or four keys to
     * point at on the way there, so its glow cannot be indexed by the stage. It is indexed by what
     * the keyboard has latched instead; {@link TourChordGlow} is the whole rule.
     */
    public final boolean chordGlow;

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
        if (gestures.length < Math.max(1, signals.length))
            throw new IllegalArgumentException("step " + id + " has fewer gestures than signals");
        if (targetIds.length == 0)
            throw new IllegalArgumentException("step " + id + " has no target at all");
        this.id = id;
        this.copyRes = copyRes;
        this.secondLineRes = secondLineRes;
        this.targets = targetIds.clone();
        this.targetId = this.targets[0];
        this.signals = signals.clone();
        this.gestures = gestures.clone();
        this.topAnchored = topAnchored;
        this.chordGlow = chordGlow;
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

    /** Whether the card at {@code stage} is the closing card, which ends on its own buttons. */
    public boolean isClosingCard() {
        return signals.length == 0;
    }
}
