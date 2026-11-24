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
     * wrong edges, (rows) the rows view's band; (a) has none. The comment
     * beside each entry says why, which fixtures hit it (any keyboard state) and one pair of
     * rects (the leaving rest's laid-out box, then the arriving rest's). A ticket that fixes a
     * case removes its entry. A key ending in {@code SHARED-ROW} is a case (either Style) whose Terminal
     * card carries a row Home shares (apps or A-Z beside the keys): the two-card rule refuses it
     * ({@link SideCards.Refusal#ROWS_BESIDE_KEYS}) and the developer decides.
     */
    static final Set<String> KNOWN_FAILURES = new TreeSet<>(Arrays.asList(
        // Peek stands flush on Home (no edge gap) while the Terminal's Floating dock keeps its gap, so the container's bottom moves. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=false/false [0.0,1466.0 - 1080.0,2400.0] -> [0.0,1450.0 - 1080.0,2384.0]
        "(b) CONTAINER BOTTOM FLOATING PEEK_GLASS HOME->TERMINAL SHARED-ROW",
        // Peek stands flush on Home (no edge gap) while the Terminal's Floating dock keeps its gap, so the container's bottom moves. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=false/false [0.0,1450.0 - 1080.0,2384.0] -> [0.0,1466.0 - 1080.0,2400.0]
        "(b) CONTAINER BOTTOM FLOATING PEEK_GLASS TERMINAL->HOME SHARED-ROW",
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
        // Docked on Home the keyboard no longer shares the host (#309): keyboard down the host owns the foot under the gesture pill and runs to the screen's edge, keyboard up it stays above the column and the keyboard wears the foot. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (GLASS_TRAY) up=false/true [0.0,1516.0 - 1080.0,2400.0] -> [0.0,1516.0 - 1080.0,1667.0]
        "(b) HOST BOTTOM DOCKED GLASS_TRAY HOME->HOME SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot, a-z on the dock. e.g. tl16 (GLASS_TRAY) up=false/true [0.0,1579.0 - 1080.0,1730.0] -> [0.0,1481.0 - 1080.0,2400.0]
        "(b) HOST BOTTOM DOCKED GLASS_TRAY HOME->TERMINAL SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot, a-z on the dock. e.g. tl16 (GLASS_TRAY) up=true/false [0.0,1481.0 - 1080.0,2400.0] -> [0.0,1579.0 - 1080.0,1730.0]
        "(b) HOST BOTTOM DOCKED GLASS_TRAY TERMINAL->HOME SHARED-ROW",
        // Docked on Home the keyboard no longer shares the host (#309): keyboard down the host owns the foot under the gesture pill and runs to the screen's edge, keyboard up it stays above the column and the keyboard wears the foot. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (LENS_BEADS) up=false/true [0.0,1516.0 - 1080.0,2400.0] -> [0.0,1516.0 - 1080.0,1667.0]
        "(b) HOST BOTTOM DOCKED LENS_BEADS HOME->HOME SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot, a-z on the dock. e.g. tl16 (LENS_BEADS) up=false/true [0.0,1579.0 - 1080.0,1730.0] -> [0.0,1481.0 - 1080.0,2400.0]
        "(b) HOST BOTTOM DOCKED LENS_BEADS HOME->TERMINAL SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot, a-z on the dock. e.g. tl16 (LENS_BEADS) up=true/false [0.0,1481.0 - 1080.0,2400.0] -> [0.0,1579.0 - 1080.0,1730.0]
        "(b) HOST BOTTOM DOCKED LENS_BEADS TERMINAL->HOME SHARED-ROW",
        // Docked on Home the keyboard no longer shares the host (#309): keyboard down the host owns the foot under the gesture pill and runs to the screen's edge, keyboard up it stays above the column and the keyboard wears the foot. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (PEEK_GLASS) up=false/true [0.0,1516.0 - 1080.0,2400.0] -> [0.0,1516.0 - 1080.0,1667.0]
        "(b) HOST BOTTOM DOCKED PEEK_GLASS HOME->HOME SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot, a-z on the dock. e.g. tl16 (PEEK_GLASS) up=false/true [0.0,1579.0 - 1080.0,1730.0] -> [0.0,1481.0 - 1080.0,2400.0]
        "(b) HOST BOTTOM DOCKED PEEK_GLASS HOME->TERMINAL SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot, a-z on the dock. e.g. tl16 (PEEK_GLASS) up=true/false [0.0,1481.0 - 1080.0,2400.0] -> [0.0,1579.0 - 1080.0,1730.0]
        "(b) HOST BOTTOM DOCKED PEEK_GLASS TERMINAL->HOME SHARED-ROW",
        // Docked on Home the keyboard no longer shares the host (#309): keyboard down the host owns the foot under the gesture pill and runs to the screen's edge, keyboard up it stays above the column and the keyboard wears the foot. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (WALL_STRIP) up=false/true [0.0,1516.0 - 1080.0,2400.0] -> [0.0,1516.0 - 1080.0,1667.0]
        "(b) HOST BOTTOM DOCKED WALL_STRIP HOME->HOME SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot, a-z on the dock. e.g. tl16 (WALL_STRIP) up=false/true [0.0,1579.0 - 1080.0,1730.0] -> [0.0,1481.0 - 1080.0,2400.0]
        "(b) HOST BOTTOM DOCKED WALL_STRIP HOME->TERMINAL SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot, a-z on the dock. e.g. tl16 (WALL_STRIP) up=true/false [0.0,1481.0 - 1080.0,2400.0] -> [0.0,1579.0 - 1080.0,1730.0]
        "(b) HOST BOTTOM DOCKED WALL_STRIP TERMINAL->HOME SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (GLASS_TRAY) up=false/false [32.0,1502.0 - 1048.0,1651.0] -> [32.0,1374.0 - 1048.0,1636.0]
        "(b) HOST BOTTOM FLOATING GLASS_TRAY HOME->TERMINAL SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (GLASS_TRAY) up=false/false [32.0,1374.0 - 1048.0,1636.0] -> [32.0,1502.0 - 1048.0,1651.0]
        "(b) HOST BOTTOM FLOATING GLASS_TRAY TERMINAL->HOME SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (LENS_BEADS) up=false/false [32.0,1502.0 - 1048.0,1651.0] -> [32.0,1374.0 - 1048.0,1636.0]
        "(b) HOST BOTTOM FLOATING LENS_BEADS HOME->TERMINAL SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (LENS_BEADS) up=false/false [32.0,1374.0 - 1048.0,1636.0] -> [32.0,1502.0 - 1048.0,1651.0]
        "(b) HOST BOTTOM FLOATING LENS_BEADS TERMINAL->HOME SHARED-ROW",
        // Floating Peek on Home owns the foot with the keyboard down (DockPlacement.floats is false while the card stands, so DockSpec.footExists), and the host runs to the screen's edge; keyboard up it stops at its column. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (PEEK_GLASS) up=false/true [0.0,1518.0 - 1080.0,2400.0] -> [0.0,1518.0 - 1080.0,1667.0]
        "(b) HOST BOTTOM FLOATING PEEK_GLASS HOME->HOME SHARED-ROW",
        // Peek card on Home stands on the screen's edge, the Floating card on the Terminal keeps its gap, so the host's bottom moves. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=false/false [0.0,1581.0 - 1080.0,1730.0] -> [32.0,1467.0 - 1048.0,1714.0]
        "(b) HOST BOTTOM FLOATING PEEK_GLASS HOME->TERMINAL SHARED-ROW",
        // Peek card on Home stands on the screen's edge, the Floating card on the Terminal keeps its gap, so the host's bottom moves. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=false/false [32.0,1467.0 - 1048.0,1714.0] -> [0.0,1581.0 - 1080.0,1730.0]
        "(b) HOST BOTTOM FLOATING PEEK_GLASS TERMINAL->HOME SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (WALL_STRIP) up=false/false [32.0,1502.0 - 1048.0,1651.0] -> [32.0,1374.0 - 1048.0,1636.0]
        "(b) HOST BOTTOM FLOATING WALL_STRIP HOME->TERMINAL SHARED-ROW",
        // the host stops at its column's top, which differs between Home and the Terminal (bands under the keyboard on the Terminal only). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16 with a foot. e.g. tl16 with a foot (WALL_STRIP) up=false/false [32.0,1374.0 - 1048.0,1636.0] -> [32.0,1502.0 - 1048.0,1651.0]
        "(b) HOST BOTTOM FLOATING WALL_STRIP TERMINAL->HOME SHARED-ROW",
        // Peek spans the screen on Home, the Floating card on the Terminal is inset by Margin: one host view, two widths (ADR 0032). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=false/false [0.0,1581.0 - 1080.0,1730.0] -> [32.0,1467.0 - 1048.0,1714.0]
        "(b) HOST SIDES FLOATING PEEK_GLASS HOME->TERMINAL SHARED-ROW",
        // Peek spans the screen on Home, the Floating card on the Terminal is inset by Margin: one host view, two widths (ADR 0032). Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=false/false [32.0,1467.0 - 1048.0,1714.0] -> [0.0,1581.0 - 1080.0,1730.0]
        "(b) HOST SIDES FLOATING PEEK_GLASS TERMINAL->HOME SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot. e.g. tl16 (GLASS_TRAY) up=false/false [0.0,1579.0 - 1080.0,1730.0] -> [0.0,1481.0 - 1080.0,1730.0]
        "(b) HOST TOP DOCKED GLASS_TRAY HOME->TERMINAL SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot. e.g. tl16 (GLASS_TRAY) up=false/false [0.0,1481.0 - 1080.0,1730.0] -> [0.0,1579.0 - 1080.0,1730.0]
        "(b) HOST TOP DOCKED GLASS_TRAY TERMINAL->HOME SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot. e.g. tl16 (LENS_BEADS) up=false/false [0.0,1579.0 - 1080.0,1730.0] -> [0.0,1481.0 - 1080.0,1730.0]
        "(b) HOST TOP DOCKED LENS_BEADS HOME->TERMINAL SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot. e.g. tl16 (LENS_BEADS) up=false/false [0.0,1481.0 - 1080.0,1730.0] -> [0.0,1579.0 - 1080.0,1730.0]
        "(b) HOST TOP DOCKED LENS_BEADS TERMINAL->HOME SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=false/false [0.0,1579.0 - 1080.0,1730.0] -> [0.0,1481.0 - 1080.0,1730.0]
        "(b) HOST TOP DOCKED PEEK_GLASS HOME->TERMINAL SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=false/false [0.0,1481.0 - 1080.0,1730.0] -> [0.0,1579.0 - 1080.0,1730.0]
        "(b) HOST TOP DOCKED PEEK_GLASS TERMINAL->HOME SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot. e.g. tl16 (WALL_STRIP) up=false/false [0.0,1579.0 - 1080.0,1730.0] -> [0.0,1481.0 - 1080.0,1730.0]
        "(b) HOST TOP DOCKED WALL_STRIP HOME->TERMINAL SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: tl16, tl16 with a foot. e.g. tl16 (WALL_STRIP) up=false/false [0.0,1481.0 - 1080.0,1730.0] -> [0.0,1579.0 - 1080.0,1730.0]
        "(b) HOST TOP DOCKED WALL_STRIP TERMINAL->HOME SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (GLASS_TRAY) up=false/false [32.0,1565.0 - 1048.0,1714.0] -> [32.0,1467.0 - 1048.0,1714.0]
        "(b) HOST TOP FLOATING GLASS_TRAY HOME->TERMINAL SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (GLASS_TRAY) up=false/false [32.0,1467.0 - 1048.0,1714.0] -> [32.0,1565.0 - 1048.0,1714.0]
        "(b) HOST TOP FLOATING GLASS_TRAY TERMINAL->HOME SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (LENS_BEADS) up=false/false [32.0,1565.0 - 1048.0,1714.0] -> [32.0,1467.0 - 1048.0,1714.0]
        "(b) HOST TOP FLOATING LENS_BEADS HOME->TERMINAL SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (LENS_BEADS) up=false/false [32.0,1467.0 - 1048.0,1714.0] -> [32.0,1565.0 - 1048.0,1714.0]
        "(b) HOST TOP FLOATING LENS_BEADS TERMINAL->HOME SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (WALL_STRIP) up=false/false [32.0,1565.0 - 1048.0,1714.0] -> [32.0,1467.0 - 1048.0,1714.0]
        "(b) HOST TOP FLOATING WALL_STRIP HOME->TERMINAL SHARED-ROW",
        // the host starts below the status band, slab inset and lane slack, which differ per page. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (WALL_STRIP) up=false/false [32.0,1467.0 - 1048.0,1714.0] -> [32.0,1565.0 - 1048.0,1714.0]
        "(b) HOST TOP FLOATING WALL_STRIP TERMINAL->HOME SHARED-ROW",
        // the Terminal's bands under the keyboard stand above the kept band, so its column ends at the band's top, while Home's keyboard (a card of its own since #309) wears the band and its column runs to the screen's edge. Fixtures: pong with a foot. e.g. pong with a foot (GLASS_TRAY) up=true/true [0.0,1679.0 - 1080.0,2412.0] -> [0.0,1528.0 - 1080.0,2349.0]
        "(b) KEYBOARD_COLUMN BOTTOM DOCKED GLASS_TRAY HOME->TERMINAL",
        // the Terminal's bands under the keyboard stand above the kept band, so its column ends at the band's top, while Home's keyboard (a card of its own since #309) wears the band and its column runs to the screen's edge. Fixtures: pong with a foot. e.g. pong with a foot (GLASS_TRAY) up=true/true [0.0,1528.0 - 1080.0,2349.0] -> [0.0,1679.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN BOTTOM DOCKED GLASS_TRAY TERMINAL->HOME",
        // the Terminal's bands under the keyboard stand above the kept band, so its column ends at the band's top, while Home's keyboard (a card of its own since #309) wears the band and its column runs to the screen's edge. Fixtures: pong with a foot. e.g. pong with a foot (LENS_BEADS) up=true/true [0.0,1679.0 - 1080.0,2412.0] -> [0.0,1528.0 - 1080.0,2349.0]
        "(b) KEYBOARD_COLUMN BOTTOM DOCKED LENS_BEADS HOME->TERMINAL",
        // the Terminal's bands under the keyboard stand above the kept band, so its column ends at the band's top, while Home's keyboard (a card of its own since #309) wears the band and its column runs to the screen's edge. Fixtures: pong with a foot. e.g. pong with a foot (LENS_BEADS) up=true/true [0.0,1528.0 - 1080.0,2349.0] -> [0.0,1679.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN BOTTOM DOCKED LENS_BEADS TERMINAL->HOME",
        // the Terminal's bands under the keyboard stand above the kept band, so its column ends at the band's top, while Home's keyboard (a card of its own since #309) wears the band and its column runs to the screen's edge. Fixtures: pong with a foot. e.g. pong with a foot (PEEK_GLASS) up=true/true [0.0,1679.0 - 1080.0,2412.0] -> [0.0,1528.0 - 1080.0,2349.0]
        "(b) KEYBOARD_COLUMN BOTTOM DOCKED PEEK_GLASS HOME->TERMINAL",
        // the Terminal's bands under the keyboard stand above the kept band, so its column ends at the band's top, while Home's keyboard (a card of its own since #309) wears the band and its column runs to the screen's edge. Fixtures: pong with a foot. e.g. pong with a foot (PEEK_GLASS) up=true/true [0.0,1528.0 - 1080.0,2349.0] -> [0.0,1679.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN BOTTOM DOCKED PEEK_GLASS TERMINAL->HOME",
        // the Terminal's bands under the keyboard stand above the kept band, so its column ends at the band's top, while Home's keyboard (a card of its own since #309) wears the band and its column runs to the screen's edge. Fixtures: pong with a foot. e.g. pong with a foot (WALL_STRIP) up=true/true [0.0,1679.0 - 1080.0,2412.0] -> [0.0,1528.0 - 1080.0,2349.0]
        "(b) KEYBOARD_COLUMN BOTTOM DOCKED WALL_STRIP HOME->TERMINAL",
        // the Terminal's bands under the keyboard stand above the kept band, so its column ends at the band's top, while Home's keyboard (a card of its own since #309) wears the band and its column runs to the screen's edge. Fixtures: pong with a foot. e.g. pong with a foot (WALL_STRIP) up=true/true [0.0,1528.0 - 1080.0,2349.0] -> [0.0,1679.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN BOTTOM DOCKED WALL_STRIP TERMINAL->HOME",
        // Peek's flush edge on Home against the Floating gap on the Terminal moves the column's bottom. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=true/true [0.0,1730.0 - 1080.0,2400.0] -> [0.0,1714.0 - 1080.0,2384.0]
        "(b) KEYBOARD_COLUMN BOTTOM FLOATING PEEK_GLASS HOME->TERMINAL SHARED-ROW",
        // Peek's flush edge on Home against the Floating gap on the Terminal moves the column's bottom. Left for the developer to decide: the Terminal's card carries a row Home shares. Fixtures: a-z on the dock, tl16, tl16 with a foot. e.g. tl16 (PEEK_GLASS) up=true/true [0.0,1714.0 - 1080.0,2384.0] -> [0.0,1730.0 - 1080.0,2400.0]
        "(b) KEYBOARD_COLUMN BOTTOM FLOATING PEEK_GLASS TERMINAL->HOME SHARED-ROW",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (Home's Docked keyboard has a card of its own since #309, no longer one surface with the dock). Fixtures: pong, split keyboard, split pane, status bar at the bottom. e.g. pong (GLASS_TRAY) up=true/true [0.0,1742.0 - 1080.0,2412.0] -> [0.0,1591.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN TOP DOCKED GLASS_TRAY HOME->TERMINAL",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (Home's Docked keyboard has a card of its own since #309, no longer one surface with the dock). Fixtures: pong, split keyboard, split pane, status bar at the bottom. e.g. pong (GLASS_TRAY) up=true/true [0.0,1591.0 - 1080.0,2412.0] -> [0.0,1742.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN TOP DOCKED GLASS_TRAY TERMINAL->HOME",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (Home's Docked keyboard has a card of its own since #309, no longer one surface with the dock). Fixtures: pong, split keyboard, split pane, status bar at the bottom. e.g. pong (LENS_BEADS) up=true/true [0.0,1742.0 - 1080.0,2412.0] -> [0.0,1591.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN TOP DOCKED LENS_BEADS HOME->TERMINAL",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (Home's Docked keyboard has a card of its own since #309, no longer one surface with the dock). Fixtures: pong, split keyboard, split pane, status bar at the bottom. e.g. pong (LENS_BEADS) up=true/true [0.0,1591.0 - 1080.0,2412.0] -> [0.0,1742.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN TOP DOCKED LENS_BEADS TERMINAL->HOME",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (Home's Docked keyboard has a card of its own since #309, no longer one surface with the dock). Fixtures: pong, split keyboard, split pane, status bar at the bottom. e.g. pong (PEEK_GLASS) up=true/true [0.0,1742.0 - 1080.0,2412.0] -> [0.0,1591.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN TOP DOCKED PEEK_GLASS HOME->TERMINAL",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (Home's Docked keyboard has a card of its own since #309, no longer one surface with the dock). Fixtures: pong, split keyboard, split pane, status bar at the bottom. e.g. pong (PEEK_GLASS) up=true/true [0.0,1591.0 - 1080.0,2412.0] -> [0.0,1742.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN TOP DOCKED PEEK_GLASS TERMINAL->HOME",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (Home's Docked keyboard has a card of its own since #309, no longer one surface with the dock). Fixtures: pong, split keyboard, split pane, status bar at the bottom. e.g. pong (WALL_STRIP) up=true/true [0.0,1742.0 - 1080.0,2412.0] -> [0.0,1591.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN TOP DOCKED WALL_STRIP HOME->TERMINAL",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (Home's Docked keyboard has a card of its own since #309, no longer one surface with the dock). Fixtures: pong, split keyboard, split pane, status bar at the bottom. e.g. pong (WALL_STRIP) up=true/true [0.0,1591.0 - 1080.0,2412.0] -> [0.0,1742.0 - 1080.0,2412.0]
        "(b) KEYBOARD_COLUMN TOP DOCKED WALL_STRIP TERMINAL->HOME",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves. Fixtures: pong, split pane, status bar at the bottom. e.g. pong (GLASS_TRAY) up=true/true [0.0,1726.0 - 1080.0,2396.0] -> [0.0,1566.0 - 1080.0,2396.0]
        "(b) KEYBOARD_COLUMN TOP FLOATING GLASS_TRAY HOME->TERMINAL",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves. Fixtures: pong, split pane, status bar at the bottom. e.g. pong (GLASS_TRAY) up=true/true [0.0,1566.0 - 1080.0,2396.0] -> [0.0,1726.0 - 1080.0,2396.0]
        "(b) KEYBOARD_COLUMN TOP FLOATING GLASS_TRAY TERMINAL->HOME",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves. Fixtures: pong, split pane, status bar at the bottom. e.g. pong (LENS_BEADS) up=true/true [0.0,1726.0 - 1080.0,2396.0] -> [0.0,1566.0 - 1080.0,2396.0]
        "(b) KEYBOARD_COLUMN TOP FLOATING LENS_BEADS HOME->TERMINAL",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves. Fixtures: pong, split pane, status bar at the bottom. e.g. pong (LENS_BEADS) up=true/true [0.0,1566.0 - 1080.0,2396.0] -> [0.0,1726.0 - 1080.0,2396.0]
        "(b) KEYBOARD_COLUMN TOP FLOATING LENS_BEADS TERMINAL->HOME",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (the Peek design's bottom step no longer hides it). Fixtures: pong, pong with a foot, split keyboard, split pane, status bar at the bottom. e.g. pong (PEEK_GLASS) up=true/true [0.0,1726.0 - 1080.0,2396.0] -> [0.0,1566.0 - 1080.0,2396.0]
        "(b) KEYBOARD_COLUMN TOP FLOATING PEEK_GLASS HOME->TERMINAL",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves (the Peek design's bottom step no longer hides it). Fixtures: pong, pong with a foot, split keyboard, split pane, status bar at the bottom. e.g. pong (PEEK_GLASS) up=true/true [0.0,1566.0 - 1080.0,2396.0] -> [0.0,1726.0 - 1080.0,2396.0]
        "(b) KEYBOARD_COLUMN TOP FLOATING PEEK_GLASS TERMINAL->HOME",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves. Fixtures: pong, split pane, status bar at the bottom. e.g. pong (WALL_STRIP) up=true/true [0.0,1726.0 - 1080.0,2396.0] -> [0.0,1566.0 - 1080.0,2396.0]
        "(b) KEYBOARD_COLUMN TOP FLOATING WALL_STRIP HOME->TERMINAL",
        // the column holds the bands under the keyboard on the Terminal only, so its top moves. Fixtures: pong, split pane, status bar at the bottom. e.g. pong (WALL_STRIP) up=true/true [0.0,1566.0 - 1080.0,2396.0] -> [0.0,1726.0 - 1080.0,2396.0]
        "(b) KEYBOARD_COLUMN TOP FLOATING WALL_STRIP TERMINAL->HOME",
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
        "(rows) BAND WALL_STRIP front HOME"));

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

    /**
     * Under either Style, the cases whose Terminal card carries a row Home shares (apps or A-Z beside
     * the keys): the two-card rule refuses them and the developer decides. Their keys end in
     * {@code SHARED-ROW}, apart from the ones the two cards fixed.
     */
    private static String sharedRow(LayoutFixtures.Fixture f, boolean floating) {
        return SideCards.refusal(f.layoutOf(PageRef.HOME),
            f.layoutOf(PageRef.TERMINAL), floating, false) == SideCards.Refusal.ROWS_BESIDE_KEYS
            ? " SHARED-ROW" : "";
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
        if (keyboard != null && keyboard.view != StackView.KEYBOARD_COLUMN) keyboard = null;
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
            // A Peek card is Home's.
            if (a.card(CardId.PEEK) != null) assertTrue(at, pair[0].isHome());
            if (b.card(CardId.PEEK) != null) assertTrue(at, pair[1].isHome());
        }
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
                    + "->" + name(pair[1]) + sharedRow(f, floating);
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
        boolean keyboards = over.id == CardId.KEYBOARD
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
                EdgeKind a = upper.edge(Edge.BOTTOM);
                EdgeKind b = lower.edge(Edge.TOP);
                String kind = null;
                if (gap > 0f && gap < 2f * f.density) kind = "HAIRLINE";
                else if (gap <= 0f && (a == EdgeKind.JOIN) != (b == EdgeKind.JOIN)) kind = "EDGES";
                if (kind == null) continue;
                found.add("(c) " + upper.id + "/" + lower.id + " " + kind + " " + style(floating)
                    + " " + f.homeDesign + " " + name(page) + sharedRow(f, floating), f.name + " up=" + up + " "
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
                found.add("(d) BOTTOM " + style(floating) + " " + f.homeDesign + " " + name(page)
                    + sharedRow(f, floating), f.name + " up=" + up + " alone " + alone.cards()
                    + " | beside " + beside.cards());
                break;
            }
        }
        assertKnown("(d)", found);
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
