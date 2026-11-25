package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * When Home and the Terminal each wear a glass card of their own (ADR 0032: #302 under Floating,
 * #304 under Docked),
 * and the two numbers that follow: the one bottom margin the stack's container keeps at both
 * rests, and how far below its layout Home's side is drawn to make up the difference. Pure: the
 * activity's rest pass and the stack's renderer both read these, so the margin and the drop have
 * one owner.
 *
 * <p>The two cards always stand "apart" between Home and the Terminal (#305): the Terminal's
 * card, if it has one, travels with the Terminal, and Home's card (the Peek card or the apps
 * row's) travels with Home; nothing shares the glass host. Every layout, Dock design and Style
 * is the same here. Under Floating with the keys alone the Terminal's card is the extra-keys band
 * ({@link #keysAlone}); otherwise it is a second back host cut by its Style's dock formula
 * ({@code RestCards.StackView#TERMINAL_HOST}), the same card the one host wore at that rest, and
 * a Terminal with no card at its rest shows none.
 */
public final class SideCards {

    private SideCards() {}

    /**
     * Whether the Terminal's extra-keys row is its only row over the keyboard: Floating then
     * carries its card in the keys row's own host ({@code RestCards.StackView#KEYS_CARD}). With
     * the apps row or A-Z beside the keys (#305) the card spans those rows too and stands on the
     * Terminal's own back host under either Style.
     */
    public static boolean keysAlone(@NonNull PlaceLayout terminal) {
        if (!PlaceChromePolicy.extraKeysRowShown(terminal)
            || StackLanes.where(terminal, Element.EXTRA_KEYS) != StackLanes.Where.BOTTOM_OVER)
            return false;
        for (Element element : EdgeStackPolicy.overKeyboard(terminal))
            if (element == Element.APPS || element == Element.AZ) return false;
        return true;
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
     * page is in front; else the rest's own ({@code BottomInsets#stackPaddingPx},
     * {@code RestCards#stackPaddingPx}: the pass's flush inset unless the dock wears the foot).
     */
    public static int containerPaddingPx(int stackPaddingPx, boolean apart) {
        return apart ? 0 : Math.max(0, stackPaddingPx);
    }

    /**
     * How far above its layout the Terminal's side is drawn while the cards stand apart: the
     * padding the container no longer pads at the Terminal's own rest
     * ({@code BottomInsets#stackPaddingPx} of that rest, {@code RestCards#stackPaddingPx}: the
     * lower half of its flush padding while its keyboard is down, none while the Docked dock
     * wears the foot and the keyboard column's margin holds that padding), so the Terminal's lane rows, its extra-keys card and its
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
     * lift (up), and the stack's own lane, which is the front page's and holds the rows both
     * sides show (#305), Home's drop by Home's share and the lift by the Terminal's
     * ({@link #columnLiftPx}), so a shared row stands at each rest where that side's rows stand.
     * 0 while the cards do not stand apart.
     *
     * @param terminalShare how much of the scene is the Terminal's: 1 at its rest, 0 at Home's,
     *                      between across a crossing
     */
    public static float laneTyPx(int lane, boolean apart, float homeDropPx, int terminalLiftPx,
                                 float terminalShare) {
        return laneTyPx(lane, apart, homeDropPx, terminalLiftPx, terminalShare, 0f, 0f);
    }

    /**
     * {@link #laneTyPx(int, boolean, float, int, float)} with the keyboard's cover on Home
     * (#309): Home's lane stands {@code coverHomePx} lower still, so its rows stand at their
     * keyboard-down rect, and the stack's lane {@code coverSharedPx} (Home's share of that drop),
     * which it carries for the front page; the Terminal's lane rides the keyboard and gets none.
     * The stack's lane is the crossing rule's GLIDE ({@link CrossingPath#glide}) between the two
     * sides' rest offsets.
     * While the cards do not stand apart the layers carry the cover and every lane is 0.
     */
    public static float laneTyPx(int lane, boolean apart, float homeDropPx, int terminalLiftPx,
                                 float terminalShare, float coverHomePx, float coverSharedPx) {
        if (!apart) return 0f;
        if (lane == StackLanes.HOME) return homeDropPx + coverHomePx;
        if (lane == StackLanes.TERMINAL) return -Math.max(0, terminalLiftPx);
        // A row both sides show in one place GLIDEs (CrossingPath): laid out once, each side's
        // rest an offset from that layout (Home's drop, the Terminal's lift), blended by the
        // Terminal's share, which is already on the material curve.
        return CrossingPath.glide(homeDropPx, -Math.max(0, terminalLiftPx), terminalShare)
            + coverSharedPx;
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
