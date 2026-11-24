package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.page.Side;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.RestCards.Card;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.wall.PageRef;

/**
 * One end of a crossing as the bottom stack rests there: its page, its laid-out geometry, its
 * dock Style (Docked and Floating resolve geometry differently on purpose, so the Style is part
 * of the end and never unified away), the look of each region it has, who draws the foot under
 * the system pill, its inks and its even-out matrices. Read by {@link StackEnds#read} as a
 * crossing begins and on each rest change, never mid-crossing.
 */
public final class StackEnd {

    /** The stack's regions. */
    public static final int ROWS = 0;
    public static final int KEYBOARD = 1;
    public static final int UNDER = 2;
    public static final int FOOT = 3;
    public static final int REGION_COUNT = 4;

    /** Who draws the foot under the system pill at this end. */
    public enum StripRule {
        /** The decor strip draws it, the keyboard's foot. */
        KEYBOARD_FOOT,
        /** The dock's glass host reaches down and draws it itself. */
        DOCK_FOOT,
        /** Nothing: the wallpaper shows. */
        BARE
    }

    /** The topology ids a frame reports ({@link StackScene.Frame#topology}). */
    public static final int TOPOLOGY_DOCKED = 0;
    public static final int TOPOLOGY_FLOATING = 1;

    @NonNull public final PageRef page;
    @NonNull public final StackGeometry rest = new StackGeometry();
    /**
     * Where this end's page leaves the wall's cells and what is drawn on every page ends above the
     * window's bottom, its own rest's ({@link PageRoom#wallInsetPx}). The flat body surface is laid
     * out once, at the smallest of the pages' rooms, and cut down to the front page's by the
     * scene ({@link StackScene#bodyCutPx}); a crossing blends the two ends' rooms.
     */
    public int roomPx;
    public boolean floating;
    /**
     * The slab's corner radius and its inset from the window's edge at this end's rest, in px: its
     * Corners and Margin as drawn. A crossing blends them on the material curve
     * ({@link StackScene#shape}); the slab's height is the host layer's rest height and the
     * keyboard's shown height the rest's keyboard height while the end has one.
     */
    public int cornerPx;
    public int insetPx;
    /** Each region's look; null where this end has no such region. */
    @NonNull public final RegionLook[] looks = new RegionLook[REGION_COUNT];
    @NonNull public StripRule strip = StripRule.BARE;
    public int inkArgb;
    /** Each region's even-out colour matrix (20 floats); null for the identity. */
    @NonNull public final float[][] evenOut = new float[REGION_COUNT][];

    /** The views a card stands on ({@link StackView}), by ordinal; made once. */
    static final StackView[] VIEWS = StackView.values();
    public static final int VIEW_COUNT = VIEWS.length;
    static final CardId[] CARD_IDS = CardId.values();
    /** {@link #cardOn}: no card drawn on the view at this rest. */
    public static final int NO_CARD = -1;
    /** {@link #viewSide}: the view carries no card at this rest. */
    public static final int NO_SIDE = -1;
    /** {@link #viewSide}: the view carries the stack's own card, which no side owns. */
    public static final int STACK_SIDE = Side.COUNT;

    /**
     * This rest's glass cards by the view that carries them (ADR 0032 section 5), as
     * {@link #readCards} copies them from the rest's {@link RestCards}: the card each view draws on
     * screen ({@link CardId} ordinal, its first: the dock's card before a foot it wears), or
     * {@link #NO_CARD}, and where it is drawn, screen px.
     */
    public final int[] cardOn = new int[VIEW_COUNT];
    public final float[] cardLeft = new float[VIEW_COUNT];
    public final float[] cardTop = new float[VIEW_COUNT];
    public final float[] cardRight = new float[VIEW_COUNT];
    public final float[] cardBottom = new float[VIEW_COUNT];
    /**
     * Whose card each view carries at this rest, on screen or parked off it with the other side
     * ({@link RestCards#parked}): a {@link Side} ordinal, {@link #STACK_SIDE} or {@link #NO_SIDE}.
     */
    public final int[] viewSide = new int[VIEW_COUNT];
    /**
     * How far below its layout Home's side is drawn, and above its layout the Terminal's, at this
     * rest ({@link SideCards#homeSideDropPx}, {@link SideCards#terminalSideLiftPx}): the static
     * part of a side's card's pose ({@link StackScene.Frame#cardTy}). 0 unless the cards stand apart.
     */
    public float homeDropPx;
    public int terminalLiftPx;

    public StackEnd(@NonNull PageRef page) {
        this.page = page;
        clearCards();
    }

    /** No card on any view. */
    public void clearCards() {
        for (int v = 0; v < VIEW_COUNT; v++) {
            cardOn[v] = NO_CARD;
            viewSide[v] = NO_SIDE;
            cardLeft[v] = cardTop[v] = cardRight[v] = cardBottom[v] = 0f;
        }
    }

    /**
     * {@code view} carries card {@code id} of {@code side} ({@link #viewSide}) at this rest, drawn
     * at {@code box} while {@code onScreen}, else parked with its side. A view keeps the first
     * card it is given.
     */
    public void setCard(@NonNull StackView view, @NonNull CardId id, int side, boolean onScreen,
                        @NonNull Box box) {
        int v = view.ordinal();
        if (viewSide[v] != NO_SIDE) return;
        viewSide[v] = side;
        if (!onScreen) return;
        cardOn[v] = id.ordinal();
        cardLeft[v] = box.left;
        cardTop[v] = box.top;
        cardRight[v] = box.right;
        cardBottom[v] = box.bottom;
    }

    /**
     * Copies {@code cards}' table: the cards it draws, and the other side's it parks. While the
     * cards stand apart the glass host carries Home's card and the Terminal's own views (its host,
     * the extra-keys card) the Terminal's; any other card is the stack's. Null clears it. At rest
     * only: it allocates nothing, but a crossing reads both ends as they were when it began.
     */
    public void readCards(@Nullable RestCards cards) {
        clearCards();
        if (cards == null) return;
        boolean apart = cards.apart();
        for (Card card : cards.cards()) setCard(card.view, card.id, sideOf(card.view, apart), true, card.box);
        for (Card card : cards.parked()) setCard(card.view, card.id, sideOf(card.view, apart), false, card.box);
    }

    /** Whose card {@code view} carries ({@link #viewSide}) while the cards stand {@code apart} or not. */
    public static int sideOf(@NonNull StackView view, boolean apart) {
        return apart && view.side != null ? view.side.ordinal() : STACK_SIDE;
    }

    /** Whether {@code o} draws the same card on view {@code v} at the same rect. */
    boolean sameCard(@NonNull StackEnd o, int v) {
        return cardOn[v] != NO_CARD && cardOn[v] == o.cardOn[v]
            && near(cardLeft[v], o.cardLeft[v]) && near(cardTop[v], o.cardTop[v])
            && near(cardRight[v], o.cardRight[v]) && near(cardBottom[v], o.cardBottom[v]);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.5f;
    }

    /** How far below its layout this rest draws {@code side}'s cards (a Side ordinal); 0 for the stack's. */
    float sideRestTyPx(int side) {
        if (side == Side.HOME.ordinal()) return homeDropPx;
        if (side == Side.TERMINAL.ordinal()) return -Math.max(0, terminalLiftPx);
        return 0f;
    }

    /** Whether this end has {@code region}. */
    public boolean has(int region) {
        return looks[region] != null;
    }

    /** The slab's laid-out height at this end's rest ({@link StackGeometry#HOST}). */
    public int slabHeightPx() {
        return rest.layerPresent[StackGeometry.HOST] ? rest.layerHeightPx[StackGeometry.HOST] : 0;
    }

    /** The keyboard's height as this end shows it at rest: 0 where it has none up. */
    public int keyboardShownPx() {
        return has(KEYBOARD) && rest.keyboardPresent ? rest.keyboardHeightPx : 0;
    }

    public int topology() {
        return floating ? TOPOLOGY_FLOATING : TOPOLOGY_DOCKED;
    }

    @Nullable
    public RegionLook look(int region) {
        return looks[region];
    }
}