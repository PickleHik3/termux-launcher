package com.termux.app;

import androidx.annotation.NonNull;

import com.termux.app.page.Side;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.place.StackEnd;

/**
 * The card tables a rest pass hands the stack's renderer ({@link StackRenderer#setCards}), filled
 * by hand for a test: while the cards stand apart Home's card is on the glass host at Home's rest
 * and parked at the Terminal's, and the Terminal's card the other way round.
 */
final class StackRendererCards {

    private static final Box BOX = new Box(0f, 0f, 1080f, 100f);

    private StackRendererCards() {}

    /** Home's card on the host, the Terminal's on {@code terminalView}, at both ends. */
    static void apart(@NonNull StackRenderer renderer, @NonNull StackView terminalView) {
        for (boolean home : new boolean[] {true, false}) {
            StackEnd end = renderer.end(home);
            end.clearCards();
            end.setCard(StackView.HOST, CardId.DOCK, Side.HOME.ordinal(), home, BOX);
            end.setCard(terminalView, CardId.DOCK, Side.TERMINAL.ordinal(), !home, BOX);
        }
    }

    /** No card table at either end: the host carries the stack's own card. */
    static void together(@NonNull StackRenderer renderer) {
        renderer.end(true).clearCards();
        renderer.end(false).clearCards();
    }
}
