package com.termux.app.setup;

/**
 * Whether the setup wizard is due, and whether it runs for someone who already uses the app.
 *
 * <p>The wizard has its own version. Raising {@link #CURRENT} shows it again to everyone who has
 * finished an older one. Pure: the stored values go in, the answer comes out.
 */
public final class SetupWizardGate {

    /** The wizard's version. */
    public static final int CURRENT = 1;

    /** The answer: whether to show, and whether this person has used the app before. */
    public static final class Decision {
        public final boolean show;
        public final boolean updater;

        Decision(boolean show, boolean updater) {
            this.show = show;
            this.updater = updater;
        }
    }

    private SetupWizardGate() {}

    /**
     * @param storedVersion the wizard version stored as done, 0 for never
     * @param tourCompleted whether the old first-boot tour was ever finished
     * @param useAsWritten  whether Use as was ever stored
     */
    public static Decision decide(int storedVersion, boolean tourCompleted, boolean useAsWritten) {
        return new Decision(storedVersion < CURRENT, tourCompleted || useAsWritten);
    }
}
