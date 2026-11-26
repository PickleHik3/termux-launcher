package com.termux.app.overview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One card of the Saved row (#359): the saved session's name and the layout it is drawn from
 * (CardFacts, never a bitmap); null facts draw a plain face, for a file that would not read.
 */
public final class SavedCard {
    @NonNull public final String name;
    @Nullable public final CardFacts facts;

    public SavedCard(@NonNull String name, @Nullable CardFacts facts) {
        this.name = name;
        this.facts = facts;
    }
}
