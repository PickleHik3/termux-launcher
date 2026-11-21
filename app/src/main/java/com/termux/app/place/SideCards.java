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
     * The container's bottom padding: none while the cards stand apart, so its inner box is the
     * same at both rests and Home's card, pinned to its bottom, stands at Home's rest whichever
     * page is in front; else the pass's flush inset ({@code StackLayout.Result#flushBottomInsetPx}).
     */
    public static int containerPaddingPx(int flushBottomInsetPx, boolean apart) {
        return apart ? 0 : Math.max(0, flushBottomInsetPx);
    }

    /**
     * How far above its layout the Terminal's side is drawn while the cards stand apart: the
     * padding the container no longer pads at the Terminal's own rest
     * ({@code BottomInsets#stackPaddingPx} of that rest: the lower half of its flush
     * padding while its keyboard is down), so the Terminal's lane rows, its extra-keys card and its
     * keyboard column are drawn where the padding stood them. 0 otherwise. Like
     * {@link #homeSideDropPx}, a number of one side's rest, read from the Terminal's rest whichever
     * page is in front: nothing in the layout carries it (the keyboard column, which Home's rows
     * stand on, never re-margins for it), so a landing on Home moves none of Home's views.
     */
    public static int terminalSideLiftPx(int terminalRestStackPaddingPx, boolean apart) {
        return apart ? Math.max(0, terminalRestStackPaddingPx) : 0;
    }

    /**
     * The translation the stack's renderer draws a lane's rows at while the cards stand apart
     * ({@code lane} a {@link StackLanes} constant): Home's lane its drop, the Terminal's lane its
     * lift (up), and the stack's own lane, which is the front page's, its share of the lift
     * ({@link #columnLiftPx}). 0 while the cards do not stand apart.
     *
     * @param terminalShare how much of the scene is the Terminal's: 1 at its rest, 0 at Home's,
     *                      between across a crossing
     */
    public static float laneTyPx(int lane, boolean apart, float homeDropPx, int terminalLiftPx,
                                 float terminalShare) {
        if (!apart) return 0f;
        if (lane == StackLanes.HOME) return homeDropPx;
        if (lane == StackLanes.TERMINAL) return -Math.max(0, terminalLiftPx);
        return -columnLiftPx(true, terminalLiftPx, terminalShare);
    }

    /**
     * How far above its layout the keyboard's column is drawn while the cards stand apart: the
     * column is the front page's (its keyboard and the bands under it), so it carries the
     * Terminal's lift by the Terminal's share of the scene, all of it at the Terminal's rest and
     * none at Home's. The scene's value at a landing is the arriving rest's, so the landing's own
     * rest pass writes nothing new.
     */
    public static float columnLiftPx(boolean apart, int terminalLiftPx, float terminalShare) {
        if (!apart) return 0f;
        float share = terminalShare < 0f ? 0f : terminalShare > 1f ? 1f : terminalShare;
        return Math.max(0, terminalLiftPx) * share;
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
