package com.termux.app.surfaces;

import androidx.annotation.NonNull;

/**
 * A question's one answer. The unsaved-changes dialog is dismissed after a button (which has
 * answered) and also with no button at all (Back, a tap outside, the surface closing under it);
 * whoever asked is told exactly once, and a dismissal that answered nothing counts as "keep".
 */
final class SingleAnswer {

    private boolean mAnswered;

    /** An answer: runs {@code action} unless the question has been answered already. */
    @NonNull Runnable answer(@NonNull Runnable action) {
        return () -> {
            if (mAnswered) return;
            mAnswered = true;
            action.run();
        };
    }

    /** The dialog went away: runs {@code keep} if nothing answered it. */
    void dismissed(@NonNull Runnable keep) {
        answer(keep).run();
    }
}
