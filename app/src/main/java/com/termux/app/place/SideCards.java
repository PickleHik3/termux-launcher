package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * When Home and the Terminal each wear a glass card of their own under Floating (ADR 0032, #302),
 * and the two numbers that follow: the one bottom margin the stack's container keeps at both
 * rests, and how far below its layout Home's side is drawn to make up the difference. Pure: the
 * activity's rest pass and the stack's renderer both read these, so the margin and the drop have
 * one owner.
 *
 * <p>The two cards stand "apart" when the Terminal's card carries the extra-keys row and nothing
 * else: it then travels with the Terminal, and Home's card travels with Home. Every Dock design
 * is the same here (the rule reads layouts, never the design).
 */
public final class SideCards {

    private SideCards() {}

    /** Why the two cards do not stand apart; the first reason that holds. */
    public enum Refusal {
        /** The dock is Docked: its slab is not a card. */
        DOCKED,
        /** The Terminal's layout shows no extra-keys row along the bottom. */
        NO_KEYS_ROW,
        /** The Terminal's extra-keys row stands under the keyboard, in a view of its own. */
        KEYS_NOT_OVER_KEYBOARD,
        /** The Terminal's card over the keyboard carries the apps row or A-Z too, a row Home shares. */
        ROWS_BESIDE_KEYS,
        /** The Terminal's status bar is a band of the dock's plank, so the card spans it too. */
        STATUS_ON_PLANK
    }

    /**
     * Why Home's card and the Terminal's extra-keys card do not stand apart, null when they do.
     * {@code home} is a parameter for symmetry and the diag lines; the rule reads the Terminal's.
     */
    @Nullable
    public static Refusal refusal(@NonNull PlaceLayout home, @NonNull PlaceLayout terminal,
                                  boolean floating, boolean statusOnDockPlank) {
        if (!floating) return Refusal.DOCKED;
        if (!PlaceChromePolicy.extraKeysRowShown(terminal)) return Refusal.NO_KEYS_ROW;
        if (StackLanes.where(terminal, Element.EXTRA_KEYS) != StackLanes.Where.BOTTOM_OVER)
            return Refusal.KEYS_NOT_OVER_KEYBOARD;
        for (Element element : EdgeStackPolicy.overKeyboard(terminal))
            if (element == Element.APPS || element == Element.AZ) return Refusal.ROWS_BESIDE_KEYS;
        if (statusOnDockPlank) return Refusal.STATUS_ON_PLANK;
        return null;
    }

    /** Whether the two cards stand apart: {@link #refusal} is null. */
    public static boolean apart(@NonNull PlaceLayout home, @NonNull PlaceLayout terminal,
                                boolean floating, boolean statusOnDockPlank) {
        return refusal(home, terminal, floating, statusOnDockPlank) == null;
    }

    /**
     * The container's bottom margin at a rest: the Terminal's own while the cards stand apart (at
     * both rests, so a crossing never moves it), else the margin of the page in front.
     */
    public static int containerMarginPx(int homeRestMarginPx, int terminalRestMarginPx,
                                        boolean apart, boolean frontHome) {
        if (apart) return terminalRestMarginPx;
        return frontHome ? homeRestMarginPx : terminalRestMarginPx;
    }

    /**
     * How far below the container's layout Home's side is drawn: what the container's margin
     * holds above Home's own. 0 unless the cards stand apart.
     */
    public static int homeSideDropPx(int homeRestMarginPx, int terminalRestMarginPx,
                                     boolean apart) {
        return apart ? terminalRestMarginPx - homeRestMarginPx : 0;
    }

    /**
     * Whether a side's own rest has its keyboard up: the live keyboard while that side is in
     * front, its own rest's answer otherwise (Home's is down unless its keyboard is always on or
     * remembered). The one answer the rest pass lays the other side out with and a crossing's end
     * toward that side reads, so the side parked off screen is laid out at the rest it lands at.
     * Never the front page's keyboard for the other side: the Terminal's keyboard is not Home's.
     */
    public static boolean restKeyboardUp(boolean sideInFront, boolean liveKeyboardUp,
                                         boolean sideRestKeyboardUp) {
        return sideInFront ? liveKeyboardUp : sideRestKeyboardUp;
    }

    /** {@link #restKeyboardPx} while that side's rest has its keyboard down. */
    public static final int KEYBOARD_DOWN = -1;

    /**
     * A side's rest keyboard as one number: the keys' height it lays out while its keyboard is
     * up ({@code keysUpPx}; 0 for a floating-form keyboard, which its own frame lays out), or
     * {@link #KEYBOARD_DOWN}. Read back with {@link #restKeyboardShown} and
     * {@link #restKeyboardKeysPx}.
     */
    public static int restKeyboardPx(boolean restKeyboardUp, int keysUpPx) {
        return restKeyboardUp ? Math.max(0, keysUpPx) : KEYBOARD_DOWN;
    }

    /** Whether {@link #restKeyboardPx}'s answer has the keyboard up. */
    public static boolean restKeyboardShown(int restKeyboardPx) {
        return restKeyboardPx != KEYBOARD_DOWN;
    }

    /** The keys' height {@link #restKeyboardPx}'s answer lays out: 0 while the keyboard is down. */
    public static int restKeyboardKeysPx(int restKeyboardPx) {
        return Math.max(0, restKeyboardPx);
    }

    /**
     * The page whose card the glass host is dressed as: Home's while the cards stand apart, at
     * every rest and across every crossing (the host is Home's card, parked a screen to the left
     * at the Terminal's rest), the page whose rows the stack stands otherwise.
     */
    @NonNull
    public static PageKind hostDressPage(boolean apart, @NonNull PageKind stackDressPage) {
        return apart ? PageKind.HOME : stackDressPage;
    }
}
