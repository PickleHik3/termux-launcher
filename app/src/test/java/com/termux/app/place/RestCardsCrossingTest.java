package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockLayout;
import com.termux.app.page.Side;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.EdgeKind;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.RestCards.Card;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.wall.PageRef;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Every pair of rests a crossing can join, held to three properties of the glass cards.
 *
 * <p>The model of a crossing (ADR 0028): a crossing lays out nothing. Every view stands, through
 * the whole crossing, at the leaving rest's layout, and the landing lays each view out at the
 * arriving rest's. A view's laid-out box on the last crossing frame is the leaving rest's and on
 * the landing the arriving rest's. The glass host is one view whether it carries the dock's card
 * or the Peek card at either end.
 *
 * <ul>
 *   <li>(a) The scene's slab and keyboard numbers are the leaving rest's at p = 0 and the arriving
 *       rest's at p = 1, and Home's share is 1 at an end whose page is Home.</li>
 *   <li>(b) A view laid out at both ends, carrying a card at both, is laid out the same size at
 *       both: a difference is a re-size at the landing.</li>
 *   <li>(c) Two cards that touch within a rest meet on a join or a seam, never on a hairline of
 *       wallpaper (0 &lt; gap &lt; 2dp) and never join to a rim or screen edge across no gap.
 *       Only cards stacked one over the other are paired: every card is a full-width row.
 *       A pair where the keyboard's card draws over the other ({@link RestCards#covered}: on
 *       Home, with its keyboard raised on demand, the keyboard covers the dock) is skipped:
 *       the cards overlap by design and meet on no edge.</li>
 *   <li>(e) Run through the scene ({@link StackScene#at}) with each end's card table
 *       ({@link StackEnd#readCards}): the cards' poses at p = 0 and p = 1 are each end's rest's,
 *       so the landing writes nothing; every card a rest draws stands on screen there (no slide)
 *       and every card it parks a wall off; no view is handed a different card by the two ends.</li>
 *   <li>(f) The A–Z bar, the foot and the keyboard are each a card of their rest (#307).</li>
 *   <li>(g) A foot both ends draw at one rect is one view's: one owner draws the band under the
 *       gesture pill.</li>
 * </ul>
 *
 * <p>Both ends wear one Style and one Dock design (a global setting). The pairs are Home to
 * Terminal, Terminal to Home and Home to Home (Home 1 to Home 2: one layout, keyboard states
 * apart), keyboard up or down at each end, on every fixture in every design. What fails today is
 * named in {@link #KNOWN_FAILURES}, aggregated over fixtures and keyboard states; the sets are
 * asserted inside each test, so a filtered run checks them too and a ticket that fixes a case
 * has to remove its entry.
 */
public class RestCardsCrossingTest {

    /**
     * What fails today: (b) what re-sizes a view at the landing, (c) cards that meet on the
     * wrong edges, (rows) the rows view's band, (e) a view handed two cards, (g) a foot two
     * views wear; (a) and (f) have none. The comment
     * beside each entry says why, which fixtures hit it (any keyboard state) and one pair of
     * rects (the leaving rest's laid-out box, then the arriving rest's). A ticket that fixes a
     * case removes its entry. Since #305 a Terminal card with the apps row or A-Z beside the keys
     * stands apart too, on the Terminal's own back host under either Style, so no case is left
     * whose glass host is shared between Home and the Terminal.
     */
    static final Set<String> KNOWN_FAILURES = new TreeSet<>(Arrays.asList(
        // the Terminal's stack is taller than Home's (bottom status band, rows), so the container's top moves. Fixtures: status bar at the bottom. e.g. status bar at the bottom (GLASS_TRAY) up=false/false [0.0,1493.0 - 1080.0,2412.0] -> [0.0,1419.0 - 1080.0,2412.0]
        "(b) CONTAINER TOP DOCKED GLASS_TRAY HOME->TERMINAL",
        // the Terminal's stack is taller than Home's (bottom status band, rows), so the container's top moves. Fixtures: status bar at the bottom. e.g. status bar at the bottom (GLASS_TRAY) up=false/false [0.0,1419.0 - 1080.0,2412.0] -> [0.0,1493.0 - 1080.0,2412.0]
        "(b) CONTAINER TOP DOCKED GLASS_TRAY TERMINAL->HOME",
        // the Terminal's stack is taller than Home's (bottom status band, rows), so the container's top moves. Fixtures: status bar at the bottom. e.g. status bar at the bottom (LENS_BEADS) up=false/false [0.0,1493.0 - 1080.0,2412.0] -> [0.0,1419.0 - 1080.0,2412.0]
        "(b) CONTAINER TOP DOCKED LENS_BEADS HOME->TERMINAL",
        // the Terminal's stack is taller than Home's (bottom status band, rows), so the container's top moves. Fixtures: status bar at the bottom. e.g. status bar at the bottom (LENS_BEADS) up=false/false [0.0,1419.0 - 1080.0,2412.0] -> [0.0,1493.0 - 1080.0,2412.0]
        "(b) CONTAINER TOP DOCKED LENS_BEADS TERMINAL->HOME",
        // the Terminal's stack is taller than Home's (bottom status band, rows), so the container's top moves. Fixtures: status bar at the bottom. e.g. status bar at the bottom (PEEK_GLASS) up=false/false [0.0,1493.0 - 1080.0,2412.0] -> [0.0,1419.0 - 1080.0,2412.0]
        "(b) CONTAINER TOP DOCKED PEEK_GLASS HOME->TERMINAL",
        // the Terminal's stack is taller than Home's (bottom status band, rows), so the container's top moves. Fixtures: status bar at the bottom. e.g. status bar at the bottom (PEEK_GLASS) up=false/false [0.0,1419.0 - 1080.0,2412.0] -> [0.0,1493.0 - 1080.0,2412.0]
        "(b) CONTAINER TOP DOCKED PEEK_GLASS TERMINAL->HOME",
        // the Terminal's stack is taller than Home's (bottom status band, rows), so the container's top moves. Fixtures: status bar at the bottom. e.g. status bar at the bottom (WALL_STRIP) up=false/false [0.0,1493.0 - 1080.0,2412.0] -> [0.0,1419.0 - 1080.0,2412.0]
        "(b) CONTAINER TOP DOCKED WALL_STRIP HOME->TERMINAL",
        // the Terminal's stack is taller than Home's (bottom status band, rows), so the container's top moves. Fixtures: status bar at the bottom. e.g. status bar at the bottom (WALL_STRIP) up=false/false [0.0,1419.0 - 1080.0,2412.0] -> [0.0,1493.0 - 1080.0,2412.0]
        "(b) CONTAINER TOP DOCKED WALL_STRIP TERMINAL->HOME",
        // the Terminal's stack and Home's differ in height, so the container's top moves. Fixtures: floating keyboard, pong. e.g. pong (GLASS_TRAY) up=false/false [0.0,1479.0 - 1080.0,2396.0] -> [0.0,1468.0 - 1080.0,2396.0]
        "(b) CONTAINER TOP FLOATING GLASS_TRAY HOME->TERMINAL",
        // the Terminal's stack and Home's differ in height, so the container's top moves. Fixtures: floating keyboard, pong. e.g. pong (GLASS_TRAY) up=false/false [0.0,1468.0 - 1080.0,2396.0] -> [0.0,1479.0 - 1080.0,2396.0]
        "(b) CONTAINER TOP FLOATING GLASS_TRAY TERMINAL->HOME",
        // the Terminal's stack and Home's differ in height, so the container's top moves. Fixtures: floating keyboard, pong. e.g. pong (LENS_BEADS) up=false/false [0.0,1479.0 - 1080.0,2396.0] -> [0.0,1468.0 - 1080.0,2396.0]
        "(b) CONTAINER TOP FLOATING LENS_BEADS HOME->TERMINAL",
        // the Terminal's stack and Home's differ in height, so the container's top moves. Fixtures: floating keyboard, pong. e.g. pong (LENS_BEADS) up=false/false [0.0,1468.0 - 1080.0,2396.0] -> [0.0,1479.0 - 1080.0,2396.0]
        "(b) CONTAINER TOP FLOATING LENS_BEADS TERMINAL->HOME",
        // the Terminal's stack and Home's differ in height, so the container's top moves (the Peek design's bottom step no longer hides it). Fixtures: floating keyboard, pong, pong with a foot, split keyboard, split pane, status bar at the bottom. e.g. pong (PEEK_GLASS) up=false/false [0.0,1479.0 - 1080.0,2396.0] -> [0.0,1468.0 - 1080.0,2396.0]
        "(b) CONTAINER TOP FLOATING PEEK_GLASS HOME->TERMINAL",
        // the Terminal's stack and Home's differ in height, so the container's top moves (the Peek design's bottom step no longer hides it). Fixtures: floating keyboard, pong, pong with a foot, split keyboard, split pane, status bar at the bottom. e.g. pong (PEEK_GLASS) up=false/false [0.0,1468.0 - 1080.0,2396.0] -> [0.0,1479.0 - 1080.0,2396.0]
        "(b) CONTAINER TOP FLOATING PEEK_GLASS TERMINAL->HOME",
        // the Terminal's stack and Home's differ in height, so the container's top moves. Fixtures: floating keyboard, pong. e.g. pong (WALL_STRIP) up=false/false [0.0,1479.0 - 1080.0,2396.0] -> [0.0,1468.0 - 1080.0,2396.0]
        "(b) CONTAINER TOP FLOATING WALL_STRIP HOME->TERMINAL",
        // the Terminal's stack and Home's differ in height, so the container's top moves. Fixtures: floating keyboard, pong. e.g. pong (WALL_STRIP) up=false/false [0.0,1468.0 - 1080.0,2396.0] -> [0.0,1479.0 - 1080.0,2396.0]
        "(b) CONTAINER TOP FLOATING WALL_STRIP TERMINAL->HOME",
        // The shape model parts a split keyboard into two halves 0.4 of the width each, so the extra-keys row's bottom over the parting is a rim, while the keyboard's one host across both halves joins it. Fixtures: split keyboard. e.g. split keyboard (GLASS_TRAY) up=true [0.0,1493.0 - 1080.0,1591.0] RIM | [0.0,1591.0 - 1080.0,2261.0] JOIN
        "(c) DOCK/KEYBOARD EDGES DOCKED GLASS_TRAY TERMINAL",
        // The shape model parts a split keyboard into two halves 0.4 of the width each, so the extra-keys row's bottom over the parting is a rim, while the keyboard's one host across both halves joins it. Fixtures: split keyboard. e.g. split keyboard (LENS_BEADS) up=true [0.0,1493.0 - 1080.0,1591.0] RIM | [0.0,1591.0 - 1080.0,2261.0] JOIN
        "(c) DOCK/KEYBOARD EDGES DOCKED LENS_BEADS TERMINAL",
        // The shape model parts a split keyboard into two halves 0.4 of the width each, so the extra-keys row's bottom over the parting is a rim, while the keyboard's one host across both halves joins it. Fixtures: split keyboard. e.g. split keyboard (PEEK_GLASS) up=true [0.0,1493.0 - 1080.0,1591.0] RIM | [0.0,1591.0 - 1080.0,2261.0] JOIN
        "(c) DOCK/KEYBOARD EDGES DOCKED PEEK_GLASS TERMINAL",
        // The shape model parts a split keyboard into two halves 0.4 of the width each, so the extra-keys row's bottom over the parting is a rim, while the keyboard's one host across both halves joins it. Fixtures: split keyboard. e.g. split keyboard (WALL_STRIP) up=true [0.0,1493.0 - 1080.0,1591.0] RIM | [0.0,1591.0 - 1080.0,2261.0] JOIN
        "(c) DOCK/KEYBOARD EDGES DOCKED WALL_STRIP TERMINAL",
        // The floating-form keyboard parked at the bottom of its room lies over the bands under the keyboard: an overlay card, not a row, whose rim meets their sheet's join. Fixtures: floating keyboard. e.g. floating keyboard (GLASS_TRAY) up=true [54.0,1742.0 - 1026.0,2412.0] RIM | [0.0,2261.0 - 1080.0,2412.0] JOIN
        "(c) KEYBOARD/UNDER EDGES DOCKED GLASS_TRAY TERMINAL",
        // The floating-form keyboard parked at the bottom of its room lies over the bands under the keyboard: an overlay card, not a row, whose rim meets their sheet's join. Fixtures: floating keyboard. e.g. floating keyboard (LENS_BEADS) up=true [54.0,1742.0 - 1026.0,2412.0] RIM | [0.0,2261.0 - 1080.0,2412.0] JOIN
        "(c) KEYBOARD/UNDER EDGES DOCKED LENS_BEADS TERMINAL",
        // The floating-form keyboard parked at the bottom of its room lies over the bands under the keyboard: an overlay card, not a row, whose rim meets their sheet's join. Fixtures: floating keyboard. e.g. floating keyboard (PEEK_GLASS) up=true [54.0,1742.0 - 1026.0,2412.0] RIM | [0.0,2261.0 - 1080.0,2412.0] JOIN
        "(c) KEYBOARD/UNDER EDGES DOCKED PEEK_GLASS TERMINAL",
        // The floating-form keyboard parked at the bottom of its room lies over the bands under the keyboard: an overlay card, not a row, whose rim meets their sheet's join. Fixtures: floating keyboard. e.g. floating keyboard (WALL_STRIP) up=true [54.0,1742.0 - 1026.0,2412.0] RIM | [0.0,2261.0 - 1080.0,2412.0] JOIN
        "(c) KEYBOARD/UNDER EDGES DOCKED WALL_STRIP TERMINAL",
        // The rows view with the A-Z row on both sides' docks lays Home's apps and A-Z rows (219, its apps band the page ticks taller) under the Terminal's extra keys in their lane: 317, taller than either side's own rows (Home 219, the Terminal 291). Fixtures: a-z on the dock. e.g. a-z on the dock (GLASS_TRAY) front HOME band 317 home 219 terminal 291
        "(rows) BAND GLASS_TRAY front HOME",
        // The rows view with the A-Z row on both sides' docks lays Home's apps and A-Z rows (219, its apps band the page ticks taller) under the Terminal's extra keys in their lane: 317, taller than either side's own rows (Home 219, the Terminal 291). Fixtures: a-z on the dock. e.g. a-z on the dock (LENS_BEADS) front HOME band 317 home 219 terminal 291
        "(rows) BAND LENS_BEADS front HOME",
        // The rows view with the A-Z row on both sides' docks lays Home's apps and A-Z rows (219, its apps band the page ticks taller) under the Terminal's extra keys in their lane: 317, taller than either side's own rows (Home 219, the Terminal 291). Fixtures: a-z on the dock. e.g. a-z on the dock (PEEK_GLASS) front HOME band 317 home 219 terminal 291
        "(rows) BAND PEEK_GLASS front HOME",
        // The rows view with the A-Z row on both sides' docks lays Home's apps and A-Z rows (219, its apps band the page ticks taller) under the Terminal's extra keys in their lane: 317, taller than either side's own rows (Home 219, the Terminal 291). Fixtures: a-z on the dock. e.g. a-z on the dock (WALL_STRIP) front HOME band 317 home 219 terminal 291
        "(rows) BAND WALL_STRIP front HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(b) AZ_EDGE BOTTOM DOCKED GLASS_TRAY HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(b) AZ_EDGE BOTTOM DOCKED LENS_BEADS HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(b) AZ_EDGE BOTTOM DOCKED PEEK_GLASS HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(b) AZ_EDGE BOTTOM DOCKED WALL_STRIP HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(b) AZ_EDGE BOTTOM FLOATING GLASS_TRAY HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(b) AZ_EDGE BOTTOM FLOATING LENS_BEADS HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(b) AZ_EDGE BOTTOM FLOATING PEEK_GLASS HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(b) AZ_EDGE BOTTOM FLOATING WALL_STRIP HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(e) MORPH AZ_EDGE DOCKED GLASS_TRAY HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(e) MORPH AZ_EDGE DOCKED LENS_BEADS HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(e) MORPH AZ_EDGE DOCKED PEEK_GLASS HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(e) MORPH AZ_EDGE DOCKED WALL_STRIP HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(e) MORPH AZ_EDGE FLOATING GLASS_TRAY HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(e) MORPH AZ_EDGE FLOATING LENS_BEADS HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(e) MORPH AZ_EDGE FLOATING PEEK_GLASS HOME->HOME",
        // Home to Home with the keyboard up at one end only: the A-Z bar on a side edge runs down to the stack's top, which Home's keyboard moves, and both ends are Home's side, so one card per side (#307 round 3) cannot part them; holding Home's bar at its keyboard-down length would change Home's look with the keyboard up. Fixtures: a-z on the left edge, a-z on Home's left edge only.
        "(e) MORPH AZ_EDGE FLOATING WALL_STRIP HOME->HOME",
        // Where the Terminal's keyboard is up on the Terminal's host (the unified Docked glass) the host wears the foot with the container undrawn, while Home's keyboard-down host wears it with the container drawn the keyboard's height lower: one foot view would be laid out at two rects (tl16 1667..1730 vs 2337..2400) and a crossing between them moves the container under it, so each host keeps its own foot there (#307). Fixtures: tl16 with a foot, Terminal keyboard up.
        "(g) FOOT OWNERS HOST/TERMINAL_HOST DOCKED GLASS_TRAY HOME->TERMINAL",
        // As above: the Terminal's unified keyboard-up host and Home's keyboard-down host lay a foot out at two rects. Fixtures: tl16 with a foot, Terminal keyboard up.
        "(g) FOOT OWNERS HOST/TERMINAL_HOST DOCKED LENS_BEADS HOME->TERMINAL",
        // As above. Fixtures: tl16 with a foot, Terminal keyboard up.
        "(g) FOOT OWNERS HOST/TERMINAL_HOST DOCKED WALL_STRIP HOME->TERMINAL",
        // As above. Fixtures: tl16 with a foot, Terminal keyboard up.
        "(g) FOOT OWNERS TERMINAL_HOST/HOST DOCKED GLASS_TRAY TERMINAL->HOME",
        // As above. Fixtures: tl16 with a foot, Terminal keyboard up.
        "(g) FOOT OWNERS TERMINAL_HOST/HOST DOCKED LENS_BEADS TERMINAL->HOME",
        // As above. Fixtures: tl16 with a foot, Terminal keyboard up.
        "(g) FOOT OWNERS TERMINAL_HOST/HOST DOCKED WALL_STRIP TERMINAL->HOME",
        // Home's Peek card wears no foot, so with Home's keyboard up the keyboard wears it, at the rect the Terminal's host's foot stands at: two owners. The foot's own view (#307) takes the hosts' feet only; the keyboard's foot is the keyboard's material and stays the keyboard's. Fixtures: pong with a foot, tl16 with a foot, floating keyboard with a foot.
        "(g) FOOT OWNERS KEYBOARD_COLUMN/TERMINAL_HOST DOCKED PEEK_GLASS HOME->TERMINAL",
        // Home's Peek card wears no foot, so with Home's keyboard up the keyboard wears it, at the rect the Terminal's host's foot stands at: two owners. The foot's own view (#307) takes the hosts' feet only; the keyboard's foot is the keyboard's material and stays the keyboard's. Fixtures: pong with a foot, tl16 with a foot, floating keyboard with a foot.
        "(g) FOOT OWNERS TERMINAL_HOST/KEYBOARD_COLUMN DOCKED PEEK_GLASS TERMINAL->HOME"));

    private static final PageRef[][] PAIRS = {
        {PageRef.HOME, PageRef.TERMINAL},
        {PageRef.TERMINAL, PageRef.HOME},
        {PageRef.HOME, PageRef.HOME}};

    private static final boolean[] BOOLS = {false, true};

    private static RestCards rest(LayoutFixtures.Fixture f, PageRef page, boolean up,
                                  boolean floating) {
        return RestCards.of(f.cardsRest(page, up, floating));
    }

    /**
     * One end of a crossing: {@code page}'s rest with the other side at its own rest
     * ({@link SideCards#restKeyboardUp}): the other end's keyboard when the other end is that
     * side, else down (Home to Home leaves the Terminal's rest where it was).
     */
    private static RestCards end(LayoutFixtures.Fixture f, PageRef page, boolean up,
                                 PageRef otherEnd, boolean otherEndUp, boolean floating) {
        boolean otherUp = otherEnd.isHome() != page.isHome() && otherEndUp;
        return RestCards.of(f.cardsRest(page, up, otherUp, floating));
    }

    /**
     * The dock's glass drawn on screen: the host's, or the Terminal's card while it stands apart
     * (the extra-keys card under Floating, the Terminal's own host under Docked).
     */
    @Nullable
    private static Card hostCard(RestCards cards) {
        for (Card card : cards.cards())
            if (card.view == StackView.HOST || card.view == StackView.KEYS_CARD
                || card.view == StackView.TERMINAL_HOST) return card;
        return null;
    }


    private static String style(boolean floating) {
        return floating ? "FLOATING" : "DOCKED";
    }

    private static String name(PageRef page) {
        return page.isHome() ? "HOME" : "TERMINAL";
    }

    /** Keys found, each with one representative case. */
    private static final class Found {
        final Map<String, String> keys = new TreeMap<>();

        void add(String key, String where) {
            if (!keys.containsKey(key)) keys.put(key, where);
            else if (!keys.get(key).contains(where) && keys.get(key).length() < 400)
                keys.put(key, keys.get(key) + "; " + where);
        }
    }

    private static void assertKnown(String property, Found found) {
        Set<String> mine = new TreeSet<>();
        for (String k : KNOWN_FAILURES) if (k.startsWith(property)) mine.add(k);
        Set<String> fresh = new TreeSet<>(found.keys.keySet());
        fresh.removeAll(mine);
        Set<String> fixed = new TreeSet<>(mine);
        fixed.removeAll(found.keys.keySet());
        StringBuilder msg = new StringBuilder(property + " failures: new " + fresh
            + ", no longer failing " + fixed);
        for (String k : fresh) msg.append("\n  ").append(k).append(" :: ").append(found.keys.get(k));
        assertTrue(msg.toString(), fresh.isEmpty() && fixed.isEmpty());
    }

    // ---- (a) ---------------------------------------------------------------------------

    private static StackEnd endOf(LayoutFixtures.Fixture f, PageRef page, boolean up,
                                  boolean floating, RestCards cards) {
        StackEnd end = new StackEnd(page);
        end.floating = floating;
        end.readCards(cards);
        end.homeDropPx = cards.homeSideDropPx();
        end.terminalLiftPx = cards.terminalSideLiftPx();
        end.rest.containerWidthPx = f.widthPx;
        Card host = hostCard(cards);
        StackGeometry g = end.rest;
        if (host != null) {
            g.layerPresent[StackGeometry.HOST] = true;
            g.layerHeightPx[StackGeometry.HOST] = Math.round(host.box.height());
            end.cornerPx = Math.round(host.corners.topLeft);
            end.insetPx = Math.round(host.box.left);
        }
        // The stack's keyboard; a floating-form keyboard is its own frame, not the column's.
        Card keyboard = cards.card(CardId.KEYBOARD);
        if (keyboard != null && keyboard.view != StackView.KEYBOARD_COLUMN
            && keyboard.view != StackView.TERMINAL_KEYBOARD) keyboard = null;
        boolean unified = host != null && host.pieces.contains(PieceId.KEYBOARD);
        int kb = keyboard != null ? Math.round(keyboard.box.height())
            : unified ? f.pass(page, up, floating).keyboardHeightPx : 0;
        g.keyboardPresent = kb > 0;
        g.keyboardInColumn = kb > 0;
        g.keyboardHeightPx = kb;
        if (kb > 0) end.looks[StackEnd.KEYBOARD] = new RegionLook();
        return end;
    }

    @Test public void everyCardIsItsRestsAtTheEndsOfACrossing() {
        Found found = new Found();
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : BOOLS)
                for (PageRef[] pair : PAIRS)
                    for (boolean fromUp : BOOLS) for (boolean toUp : BOOLS) {
            String at = f.name + " " + style(floating) + " " + name(pair[0]) + "->"
                + name(pair[1]) + " up=" + fromUp + "/" + toUp;
            RestCards a = end(f, pair[0], fromUp, pair[1], toUp, floating);
            RestCards b = end(f, pair[1], toUp, pair[0], fromUp, floating);
            StackEnd from = endOf(f, pair[0], fromUp, floating, a);
            StackEnd to = endOf(f, pair[1], toUp, floating, b);
            StackScene.Motion motion = new StackScene.Motion();
            StackScene.Frame frame = new StackScene.Frame();
            StackScene.at(from, to, 0f, motion, from.rest, frame);
            assertEquals(at + " height at 0", from.slabHeightPx(), frame.slabHeightPx, 0f);
            assertEquals(at + " corner at 0", from.cornerPx, frame.slabCornerPx, 0f);
            assertEquals(at + " inset at 0", from.insetPx, frame.slabInsetPx, 0f);
            assertEquals(at + " keyboard at 0", from.keyboardShownPx(), frame.keyboardShownPx, 0f);
            StackScene.at(from, to, 1f, motion, from.rest, frame);
            assertEquals(at + " height at 1", to.slabHeightPx(), frame.slabHeightPx, 0f);
            assertEquals(at + " corner at 1", to.cornerPx, frame.slabCornerPx, 0f);
            assertEquals(at + " inset at 1", to.insetPx, frame.slabInsetPx, 0f);
            assertEquals(at + " keyboard at 1", to.keyboardShownPx(), frame.keyboardShownPx, 0f);
            assertEquals(at + " home share at 0", pair[0].isHome() ? 1f : 0f,
                StackScene.homeShare(pair[0].isHome(), pair[1].isHome(), 0f), 0f);
            assertEquals(at + " home share at 1", pair[1].isHome() ? 1f : 0f,
                StackScene.homeShare(pair[0].isHome(), pair[1].isHome(), 1f), 0f);
            // A Peek card is Home's: on the Terminal only as Home's own, on the one host (#305).
            if (a.card(CardId.PEEK) != null) assertTrue(at, pair[0].isHome() || !a.apart());
            if (b.card(CardId.PEEK) != null) assertTrue(at, pair[1].isHome() || !b.apart());
            String tail = " " + style(floating) + " " + f.homeDesign + " " + name(pair[0]) + "->"
                + name(pair[1]);
            StackScene.Frame rest = new StackScene.Frame();
            for (float p : new float[] {0f, 0.5f, 1f}) {
                StackScene.at(from, to, p, motion, from.rest, frame);
                StackEnd end = p == 0f ? from : p == 1f ? to : null;
                if (end != null) StackScene.at(end, end, 0f, motion, end.rest, rest);
                for (int v = 0; v < StackEnd.VIEW_COUNT; v++) {
                    String view = " " + StackView.values()[v];
                    if (frame.cardMorphs[v]) found.add("(e) MORPH" + view + tail, at);
                    if (end == null) continue;
                    if (frame.cardTx[v] != rest.cardTx[v] || frame.cardTy[v] != rest.cardTy[v])
                        found.add("(e) LANDING" + view + tail, at + " p=" + p);
                    if (end.cardOn[v] != StackEnd.NO_CARD && frame.cardTx[v] != 0f)
                        found.add("(e) OFF SCREEN" + view + tail, at + " p=" + p);
                    if (end.cardOn[v] == StackEnd.NO_CARD && end.viewSide[v] >= 0
                        && end.viewSide[v] < Side.COUNT
                        && Math.abs(frame.cardTx[v]) != f.widthPx)
                        found.add("(e) PARKED ON SCREEN" + view + tail, at + " p=" + p);
                }
                if (end != null && frame.slabView != rest.slabView)
                    found.add("(e) SLAB" + tail, at + " p=" + p);
            }
        }
        assertKnown("(e)", found);
    }

    // ---- (b) ---------------------------------------------------------------------------

    /** A view carries a card when it draws one on screen or stands laid out for a parked one. */
    private static boolean carries(RestCards cards, StackView view) {
        if (view == StackView.CONTAINER) return true;
        for (Card card : cards.cards()) if (card.view == view) return true;
        for (Card card : cards.parked()) if (card.view == view) return true;
        return false;
    }

    @Test public void noViewIsLaidOutADifferentSizeAtTheLandingThanOnTheLastCrossingFrame() {
        Found found = new Found();
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : BOOLS)
                for (PageRef[] pair : PAIRS)
                    for (boolean fromUp : BOOLS) for (boolean toUp : BOOLS) {
            RestCards a = end(f, pair[0], fromUp, pair[1], toUp, floating);
            RestCards b = end(f, pair[1], toUp, pair[0], fromUp, floating);
            for (StackView view : StackView.values()) {
                Box was = a.laidOut(view);
                Box now = b.laidOut(view);
                if (was == null || now == null || !carries(a, view) || !carries(b, view)) continue;
                String tail = " " + style(floating) + " " + f.homeDesign + " " + name(pair[0])
                    + "->" + name(pair[1]);
                String where = f.name + " up=" + fromUp + "/" + toUp + " " + was + " -> " + now;
                if (was.left != now.left || was.right != now.right)
                    found.add("(b) " + view + " SIDES" + tail, where);
                if (view == StackView.KEYS_CARD) {
                    // The keys card rides the Terminal's lane: the flush rule lifts the lane
                    // (and the card with it) by the padding while the keyboard is down, a
                    // translation a crossing does. What it may never do is change size (ADR 0032).
                    if (was.height() != now.height())
                        found.add("(b) " + view + " BOTTOM" + tail, where);
                } else if (was.bottom != now.bottom)
                    found.add("(b) " + view + " BOTTOM" + tail, where);
                else if (was.top != now.top) found.add("(b) " + view + " TOP" + tail, where);
            }
        }
        assertKnown("(b)", found);
    }

    // ---- (c) ---------------------------------------------------------------------------

    /**
     * One of the two is covered by the other: the keyboard's card (or the foot it stands on)
     * draws over it.
     */
    private static boolean coveredBy(RestCards cards, Card a, Card b) {
        return drawsOver(a, cards, b) || drawsOver(b, cards, a);
    }

    private static boolean drawsOver(Card over, RestCards cards, Card under) {
        // On Home the keyboard's column is the Terminal's (#307, 2026-11-25): the bands under the
        // keys draw over Home's held rows too.
        boolean keyboards = over.id == CardId.KEYBOARD || over.id == CardId.UNDER
            || over.id == CardId.FOOT && over.view == StackView.KEYBOARD_COLUMN;
        return keyboards && cards.covered().contains(under);
    }

    @Test public void cardsThatTouchMeetOnAJoinOrASeamNeverAHairline() {
        Found found = new Found();
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : BOOLS)
                for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL})
                    for (boolean up : BOOLS) {
            RestCards cards = rest(f, page, up, floating);
            List<Card> list = new ArrayList<>(cards.cards());
            for (int i = 0; i < list.size(); i++) for (int j = i + 1; j < list.size(); j++) {
                Card upper = list.get(i);
                Card lower = list.get(j);
                if (lower.box.top < upper.box.top) { Card t = upper; upper = lower; lower = t; }
                if (coveredBy(cards, upper, lower)) continue;
                float across = Math.min(upper.box.right, lower.box.right)
                    - Math.max(upper.box.left, lower.box.left);
                if (across <= 0f) continue;
                float gap = lower.box.top - upper.box.bottom;
                // The kinds over the stretch the two share: a card's edge has runs (a Docked
                // A-Z column joins the status bar's bottom over its own width only, #307).
                float from = Math.max(upper.box.left, lower.box.left);
                float to = Math.min(upper.box.right, lower.box.right);
                EdgeKind a = upper.kindOver(Edge.BOTTOM, from, to);
                EdgeKind b = lower.kindOver(Edge.TOP, from, to);
                String kind = null;
                if (gap > 0f && gap < 2f * f.density) kind = "HAIRLINE";
                else if (gap <= 0f && (a == EdgeKind.JOIN) != (b == EdgeKind.JOIN)) kind = "EDGES";
                if (kind == null) continue;
                found.add("(c) " + upper.id + "/" + lower.id + " " + kind + " " + style(floating)
                    + " " + f.homeDesign + " " + name(page), f.name + " up=" + up + " "
                    + upper.box + " " + a + " | " + lower.box + " " + b);
            }
        }
        assertKnown("(c)", found);
    }

    // ---- (d) ---------------------------------------------------------------------------

    /**
     * Where the on-screen cards reach the screen's bottom edge, as merged runs along x: a card
     * whose box runs down to the edge covers its width there.
     */
    private static List<float[]> bottomCover(RestCards cards, int heightPx) {
        List<float[]> runs = new ArrayList<>();
        for (Card card : cards.cards()) {
            if (card.box.bottom < heightPx || card.box.top >= heightPx) continue;
            runs.add(new float[] {card.box.left, card.box.right});
        }
        runs.sort((a, b) -> Float.compare(a[0], b[0]));
        List<float[]> merged = new ArrayList<>();
        for (float[] run : runs) {
            float[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && run[0] <= last[1]) last[1] = Math.max(last[1], run[1]);
            else merged.add(new float[] {run[0], run[1]});
        }
        return merged;
    }

    private static boolean covers(List<float[]> runs, float left, float right) {
        for (float[] run : runs) if (run[0] <= left && run[1] >= right) return true;
        return false;
    }

    /**
     * (d) A card against the screen's edge: wherever a rest standing alone (one side, as before
     * the two cards) covers the screen's bottom edge, the same rest with the other side beside it
     * covers it too. A side's card that stops short of the edge leaves a strip of bare wallpaper
     * under the gesture pill that no pair of cards in (c) can see.
     */
    @Test public void aCardThatReachesTheScreensBottomEdgeAloneStillReachesItBesideTheOtherSide() {
        Found found = new Found();
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : BOOLS)
                for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL})
                    for (boolean up : BOOLS) {
            RestCards alone = RestCards.of(f.cardsRestOf(page, up, floating));
            RestCards beside = rest(f, page, up, floating);
            List<float[]> was = bottomCover(alone, f.heightPx);
            List<float[]> now = bottomCover(beside, f.heightPx);
            for (float[] run : was) {
                if (covers(now, run[0], run[1])) continue;
                found.add("(d) BOTTOM " + style(floating) + " " + f.homeDesign + " " + name(page),
                    f.name + " up=" + up + " alone " + alone.cards()
                    + " | beside " + beside.cards());
                break;
            }
        }
        assertKnown("(d)", found);
    }

    // ---- (f) the pieces --------------------------------------------------------------------

    /**
     * (f) The A–Z bar, the foot and the keyboard are each a card of the answer (#307): wherever a
     * rest stands the A–Z bar as a band (on the dock or on an edge of its own) a card carries it,
     * and as its pull tab none does; a keyboard up has a card; a rest has at most one foot.
     */
    @Test public void theAzBarTheFootAndTheKeyboardAreEachACardOfTheirRest() {
        Found found = new Found();
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : BOOLS)
                for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL})
                    for (boolean up : BOOLS) {
            RestCards cards = rest(f, page, up, floating);
            String tail = " " + style(floating) + " " + f.homeDesign + " " + name(page);
            String at = f.name + " up=" + up + " " + cards.cards();
            RestCards.AzPlacement az = cards.azPlacement();
            boolean band = az == RestCards.AzPlacement.DOCK_ROW
                || az == RestCards.AzPlacement.EDGE;
            boolean carried = cards.carrierOf(PieceId.AZ) != null;
            if (band && !carried) found.add("(f) AZ NO CARD " + az + tail, at);
            if (!band && carried) found.add("(f) AZ CARD " + az + tail, at);
            if (az == RestCards.AzPlacement.EDGE && carried
                    && cards.carrierOf(PieceId.AZ).id != CardId.AZ)
                found.add("(f) AZ NOT ITS OWN" + tail, at);
            int feet = 0;
            // A foot the keyboard's card draws over (Home's held host under its cover) is hidden.
            for (Card card : cards.cards())
                if (card.id == CardId.FOOT && !cards.covered().contains(card)) feet++;
            if (feet > 1) found.add("(f) TWO FEET" + tail, at);
            // A keyboard the fixture lays out: the floating-form fixture's Home wears the
            // docked form at no height, which no real rest has.
            boolean keyboardLaidOut = f.keyboardLaidOutPx() > 0
                || f.layoutOf(page).keyboardForm == PlaceLayout.KeyboardForm.FLOATING;
            if (up && keyboardLaidOut && cards.card(CardId.KEYBOARD) == null
                    && (cards.carrierOf(PieceId.KEYBOARD) == null
                        && cards.carrierOf(PieceId.KEYBOARD_LEFT) == null))
                found.add("(f) KEYBOARD NO CARD" + tail, at);
        }
        assertKnown("(f)", found);
    }

    /**
     * (g) One owner draws the band under the gesture pill through a crossing: where both ends
     * draw a foot at one rect, one view carries it at both, so it is a shared card that stays
     * put, never two feet sliding over each other.
     */
    @Test public void aFootBothEndsDrawIsOneCardOnOneView() {
        Found found = new Found();
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (boolean floating : BOOLS)
                for (PageRef[] pair : PAIRS)
                    for (boolean fromUp : BOOLS) for (boolean toUp : BOOLS) {
            RestCards a = end(f, pair[0], fromUp, pair[1], toUp, floating);
            RestCards b = end(f, pair[1], toUp, pair[0], fromUp, floating);
            Card fa = shownFoot(a);
            Card fb = shownFoot(b);
            if (fa == null || fb == null || !fa.box.equals(fb.box)) continue;
            if (fa.view != fb.view)
                found.add("(g) FOOT OWNERS " + fa.view + "/" + fb.view + " " + style(floating)
                    + " " + f.homeDesign + " " + name(pair[0]) + "->" + name(pair[1]),
                    f.name + " up=" + fromUp + "/" + toUp);
        }
        assertKnown("(g)", found);
    }

    /**
     * The foot a rest draws under the pill: the column's shared foot where one stands (it draws
     * over Home's held host and its foot, #307 2026-11-25), else the rest's first foot.
     */
    @Nullable
    private static Card shownFoot(RestCards cards) {
        Card column = cards.columnFoot();
        return column != null ? column : cards.card(CardId.FOOT);
    }

    // ---- accessory_row_stack -----------------------------------------------------------

    /** The over-keyboard rows' height {@code page}'s own rest stands. */
    private static int overRows(LayoutFixtures.Fixture f, PageRef page) {
        StackLayout.Input pass = f.pass(page, false, false);
        DockLayout dock = StackLayout.layout(pass, new DockGeometry()).dock;
        int total = 0;
        for (Element e : EdgeStackPolicy.overKeyboard(f.layoutOf(page)))
            total += RestCards.bandPx(e, dock, pass);
        return total;
    }

    /**
     * The rows view lays out the taller of the two sides' stacks (EdgeStackView#onMeasure calls
     * StackLanes#layoutBand with every row's height, its lane and the front lane): both sides'
     * rows are laid out at once, a side's lane rows apart from the other's, so the band is the
     * taller side's rows. With no lane the rows are one column of the front's.
     */
    @Test public void theRowsViewIsLaidOutAtTheTallerOfTheTwoSidesRowsWhenTheyHaveLanes() {
        Found found = new Found();
        for (LayoutFixtures.Fixture f : LayoutFixtures.cardFixtures())
            for (PageRef front : new PageRef[] {PageRef.HOME, PageRef.TERMINAL}) {
            PageRef other = front.isHome() ? PageRef.TERMINAL : PageRef.HOME;
            PlaceLayout home = f.layoutOf(PageRef.HOME);
            PlaceLayout terminal = f.layoutOf(PageRef.TERMINAL);
            int count = StackLanes.ROWS.length;
            int[] heights = new int[count];
            int[] lanes = new int[count];
            int[] tops = new int[count];
            for (int i = 0; i < count; i++) {
                Element e = StackLanes.ROWS[i];
                lanes[i] = StackLanes.laneOf(e, home, terminal);
                PageRef owner = lanes[i] == StackLanes.HOME ? PageRef.HOME
                    : lanes[i] == StackLanes.TERMINAL ? PageRef.TERMINAL : front;
                StackLayout.Input pass = f.pass(owner, false, false);
                DockLayout dock = StackLayout.layout(pass, new DockGeometry()).dock;
                heights[i] = EdgeStackPolicy.overKeyboard(f.layoutOf(owner)).contains(e)
                    ? RestCards.bandPx(e, dock, pass) : 0;
            }
            int band = StackLanes.layoutBand(heights, lanes, count,
                StackLanes.laneOf(Side.of(front.isHome())), tops);
            String at = f.name + " front " + name(front) + " band " + band + " home "
                + overRows(f, PageRef.HOME) + " terminal " + overRows(f, PageRef.TERMINAL);
            if (StackLanes.hasLanes(f.layoutOf(front), f.layoutOf(other),
                    Side.of(front.isHome()))
                    && band != Math.max(overRows(f, PageRef.HOME), overRows(f, PageRef.TERMINAL))) {
                found.add("(rows) BAND " + f.homeDesign + " front " + name(front), at);
            }
        }
        assertKnown("(rows)", found);
    }
}
