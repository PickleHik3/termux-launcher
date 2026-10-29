package com.termux.app.setup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The pages the cards open. A card without one shows the sheet's stub page. */
final class SetupCardPages {
    private SetupCardPages() {}

    @Nullable
    static SetupCardPage create(@NonNull String cardId) {
        switch (cardId) {
            case SetupCards.DISPLAY:
                return new DisplayCardPage();
            case SetupCards.AI:
                return new AiCardPage();
            // Wave 5 adds: SetupCards.GUIDE.
            default:
                return null;
        }
    }
}
