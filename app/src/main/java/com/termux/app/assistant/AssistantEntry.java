package com.termux.app.assistant;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

/**
 * The one way into the Assistant page (ADR 0022 section 1): the drawer's entry, a pinned dock
 * icon, and later the share sheet, the palette and the widget all call {@link #open}. With the
 * page on the wall it goes there; without one it adds it after Home 1 (after the page in front
 * when the wall has no Home page), lands on it and offers Undo. The wall and the notice are the
 * {@link Host}'s, so this holds only the decision.
 */
public final class AssistantEntry {

    /**
     * Intent action for the launcher activity: open the Assistant page, adding it first when the
     * wall has none. Sent to the launcher with an explicit component, so no manifest filter.
     */
    public static final String ACTION_OPEN_ASSISTANT =
        TermuxConstants.TERMUX_PACKAGE_NAME + ".app.open_assistant";

    /** What {@link #open} did. */
    public enum Outcome { WENT, ADDED, FAILED }

    /** The wall and the notice, as the entry needs them. */
    public interface Host {
        boolean hasAssistantPage();
        /** The id of the first Home page; -1 when the wall has none. */
        int firstHomePageId();
        /** The id of the page in front; -1 when not known. */
        int currentPageId();
        /** Go to the Assistant page; false when there is none. */
        boolean goToAssistantPage(boolean animate);
        /** Adds the page after {@code afterId} with its layout and look seeded; its id, or -1. */
        int addAssistantPage(int afterId);
        /** Takes the page off the wall; the chats are kept. */
        void removeAssistantPage(int id);
        /** Go to page {@code id}. */
        void goToPage(int id, boolean animate);
        /** "Assistant page added" with an Undo that runs {@code undo}. */
        void showAdded(@NonNull Runnable undo);
    }

    private AssistantEntry() {}

    @NonNull
    public static Outcome open(@NonNull Host host) {
        if (host.hasAssistantPage()) {
            return host.goToAssistantPage(true) ? Outcome.WENT : Outcome.FAILED;
        }
        final int origin = host.currentPageId();
        int home = host.firstHomePageId();
        final int added = host.addAssistantPage(home >= 0 ? home : origin);
        if (added < 0) return Outcome.FAILED;
        host.goToAssistantPage(true);
        host.showAdded(() -> {
            host.removeAssistantPage(added);
            if (origin >= 0) host.goToPage(origin, true);
        });
        return Outcome.ADDED;
    }
}
