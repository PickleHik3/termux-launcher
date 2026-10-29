package com.termux.app.setup;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * A card's own page inside the card sheet: the Display setup, the AI models, the guide. The
 * sheet shows the view the page creates in place of the card list, with the footer hidden, until
 * {@link Host#back()}.
 */
interface SetupCardPage {

    /** What a page may ask of the sheet and the wizard. */
    interface Host {
        @NonNull Context context();

        /** Back to the card list. */
        void back();

        /** Records one of the card's steps as completed (0-based) and refreshes the list. */
        void completeStep(@NonNull String cardId, int step);

        /** Records the whole card as done and goes back to the list. */
        void completeCard(@NonNull String cardId);

        /** Starts an activity, swallowing "no such page" errors. */
        void startSafely(@NonNull android.content.Intent intent);
    }

    /** Builds the page's view; called each time the page is shown. */
    @NonNull View createView(@NonNull Context context, @NonNull SetupCards.Card card,
                             @NonNull Host host);

    /** The page left the screen (Back, sheet closed): stop listeners and timers. */
    default void onHidden() {}
}
