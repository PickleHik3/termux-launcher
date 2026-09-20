package com.termux.app.x11;

import androidx.annotation.NonNull;

/**
 * When the display announces Android's clipboard to X.
 *
 * <p>Sync is on exactly while there is something to sync with: the page is attached, the view is
 * connected to a running server, and the user has left clipboard sharing on. Window focus has no
 * say — the Display page lives on the pane wall, whose window never changes focus, which is why
 * upstream's focus-driven registration never fired.
 *
 * <p>All policy, no views. The transitions are what the caller acts on: arming registers a
 * listener and re-reads the clipboard, so repeating it on every preference broadcast and every
 * connect would announce the same clip over and over.
 */
public final class DisplayClipboardPolicy {

    /** What arming and disarming do — the live view, in production. */
    public interface Sync {
        void activate();
        void deactivate();
    }

    @NonNull private final Sync sync;
    private boolean active;

    public DisplayClipboardPolicy(@NonNull Sync sync) {
        this.sync = sync;
    }

    /** Whether the three conditions for syncing hold. */
    public static boolean shouldSync(boolean attached, boolean connected, boolean prefEnabled) {
        return attached && connected && prefEnabled;
    }

    /** Whether sync is on as far as this policy is concerned. */
    public boolean isActive() {
        return active;
    }

    /**
     * Take the three inputs again and tell the sync only if the answer moved.
     *
     * @return whether this call changed anything
     */
    public boolean apply(boolean attached, boolean connected, boolean prefEnabled) {
        boolean wanted = shouldSync(attached, connected, prefEnabled);
        if (wanted == active) return false;
        active = wanted;
        if (wanted) sync.activate();
        else sync.deactivate();
        return true;
    }

    /** The page, the connection or the activity has gone. */
    public boolean deactivate() {
        return apply(false, false, false);
    }
}
