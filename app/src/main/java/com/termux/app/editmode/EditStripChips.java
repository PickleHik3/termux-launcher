package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.editmode.EditModeController.Card;

/**
 * Edit mode's strip chips (Keyboard, All glass, Wallpaper, Home, Bar): each opens its own card,
 * over whatever card is open, so the chips stand beside an open card too; the open card's chip is
 * lit. The Keyboard chip stands only while the keyboard has no outline on screen to tap (its own
 * card open, it stays); the Bar chip only where there is a bar.
 *
 * <p>Pure.
 */
public final class EditStripChips {

    private EditStripChips() {}

    /**
     * Whether {@code chip}'s chip shows with {@code open} open (null at rest).
     *
     * @param keyboardOffScreen the keyboard has no outline to tap
     * @param available its surface can be edited here (the bar's card has a bar)
     */
    public static boolean shown(@NonNull Card chip, @Nullable Card open, boolean keyboardOffScreen,
                                boolean available) {
        if (!available) return false;
        if (chip == Card.KEYBOARD) return keyboardOffScreen || open == Card.KEYBOARD;
        return true;
    }

    /** Whether {@code chip}'s chip is lit: its card is the open one. */
    public static boolean lit(@NonNull Card chip, @Nullable Card open) {
        return chip == open;
    }

    /**
     * How wide the strip's change count may stand: its own width {@code wantedPx}, capped at
     * {@code capPx}, and never more than the row's {@code innerPx} leaves after the buttons
     * ({@code buttonsPx}) and the chips' least width ({@code chipsMinPx}); 0 when nothing is left.
     */
    public static int countWidthPx(int wantedPx, int innerPx, int buttonsPx, int chipsMinPx, int capPx) {
        int room = innerPx - Math.max(0, buttonsPx) - Math.max(0, chipsMinPx);
        return Math.max(0, Math.min(Math.min(Math.max(0, wantedPx), Math.max(0, capPx)), room));
    }
}
