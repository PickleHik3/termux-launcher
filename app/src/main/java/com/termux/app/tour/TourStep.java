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

    /** The control this card glows, as a {@link TourTargets} id. */
    public final String targetId;

    /** The signals that clear this step, in the order the user performs them. */
    private final String[] signals;

    /** One gesture per signal, for the finger trace; a stepless card carries a single NONE. */
    private final TourGesture[] gestures;

    public TourStep(String id, int copyRes, int secondLineRes, String targetId,
                    String[] signals, TourGesture[] gestures) {
        if (gestures.length < Math.max(1, signals.length))
            throw new IllegalArgumentException("step " + id + " has fewer gestures than signals");
        this.id = id;
        this.copyRes = copyRes;
        this.secondLineRes = secondLineRes;
        this.targetId = targetId;
        this.signals = signals.clone();
        this.gestures = gestures.clone();
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
}
