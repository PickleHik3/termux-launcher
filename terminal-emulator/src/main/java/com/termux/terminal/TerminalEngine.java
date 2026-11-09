package com.termux.terminal;

/**
 * Which terminal engine a new session gets. The user's choice (the Terminal engine setting) and the
 * debug property {@code debug.tl.terminal_core=ghostty} (kept for scripts) each ask for Ghostty on
 * their own; either one is enough. The request is honoured only when the native library loaded and
 * the crash guard has not switched the Ghostty core off. Otherwise the session is Standard.
 */
public final class TerminalEngine {

    /** Stored values of the setting. */
    public static final String STANDARD = "standard";
    public static final String GHOSTTY = GhosttyCore.CORE_GHOSTTY;

    private TerminalEngine() {}

    /** Whether a stored setting value asks for Ghostty; anything unknown is Standard. */
    public static boolean isGhosttyPreference(String preference) {
        return GHOSTTY.equals(preference);
    }

    /** The precedence rule: asked for by the setting or the property, loadable, and not blocked. */
    public static boolean useGhostty(boolean preferenceAsksGhostty, boolean propertyAsksGhostty,
                                     boolean libraryAvailable, boolean crashGuardBlocks) {
        return (preferenceAsksGhostty || propertyAsksGhostty) && libraryAvailable && !crashGuardBlocks;
    }

    /**
     * Whether this build and device can run the Ghostty engine; the Terminal engine row shows only
     * when it can. The first call loads the library once and remembers the answer.
     */
    public static boolean isAvailable() {
        return GhosttyNative.isAvailable();
    }
}
