package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.editmode.EditModeController.Card;

/**
 * Edit mode's strip chips (Keyboard, All glass, Wallpaper, Home, Bar, Windows): each opens its own
 * card, over whatever card is open, so the chips stand beside an open card too; the open card's
 * chip is lit. The Keyboard chip stands only while the keyboard has no outline on screen to tap
 * (its own card open, it stays); the Bar and Windows chips only where there is a bar or windows.
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
     * How the strip's row stands (terl-integration-2): every chip whole, in order, on as many lines
     * as it takes; Undo, Revert and Done at the end of the last chips' line when they fit beside
     * it, else on a line of their own below. The change count takes only what that line leaves
     * (its own width, capped), down to none: it yields to the chips and never crushes them.
     *
     * @param innerPx the row's width inside its padding
     * @param chipPx each shown chip's own width, in order
     * @param buttonsPx Undo, Revert and Done together
     * @param countPx the count's own width
     * @param countCapPx the count's widest
     */
    @NonNull
    public static Flow flow(int innerPx, @NonNull int[] chipPx, int buttonsPx, int countPx, int countCapPx) {
        int inner = Math.max(0, innerPx);
        int[] line = new int[chipPx.length];
        int[] x = new int[chipPx.length];
        int[] width = new int[chipPx.length];
        int current = 0;
        int used = 0;
        boolean any = false;
        for (int i = 0; i < chipPx.length; i++) {
            int w = Math.min(inner, Math.max(0, chipPx[i]));
            if (any && used + w > inner) {
                current++;
                used = 0;
            }
            line[i] = current;
            x[i] = used;
            width[i] = w;
            used += w;
            any = true;
        }
        int buttons = Math.max(0, buttonsPx);
        int actionsLine = current;
        int lead = used;
        if (any && used + buttons > inner) {
            actionsLine = current + 1;
            lead = 0;
        }
        int room = Math.max(0, inner - lead - buttons);
        int count = Math.max(0, Math.min(Math.min(Math.max(0, countPx), Math.max(0, countCapPx)), room));
        return new Flow(line, x, width, actionsLine, lead, count);
    }

    /** Where {@link #flow} stands the strip's pieces; x in px from the row's inner start. */
    public static final class Flow {
        /** Each chip's line, from 0 at the top. */
        @NonNull public final int[] chipLine;
        @NonNull public final int[] chipX;
        @NonNull public final int[] chipWidth;
        /** The line the count and the buttons stand on: the last one. */
        public final int actionsLine;
        /** Where the count starts on that line: after the chips that share it. */
        public final int countX;
        /** The count's width; 0 when the line leaves it nothing. */
        public final int countWidth;

        Flow(@NonNull int[] chipLine, @NonNull int[] chipX, @NonNull int[] chipWidth, int actionsLine,
             int countX, int countWidth) {
            this.chipLine = chipLine;
            this.chipX = chipX;
            this.chipWidth = chipWidth;
            this.actionsLine = actionsLine;
            this.countX = countX;
            this.countWidth = countWidth;
        }

        /** How many lines the strip stands on. */
        public int lines() {
            return actionsLine + 1;
        }
    }
}
