package com.termux.app.setup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The pages the cards open. A card without one shows the sheet's stub page. */
final class SetupCardPages {
    private SetupCardPages() {}

    @Nullable
    static SetupCardPage create(@NonNull String cardId) {
        switch (cardId) {
            // Wave 4 and 5 add: SetupCards.DISPLAY, SetupCards.AI, SetupCards.GUIDE.
            default:
                return null;
        }
    }
}
