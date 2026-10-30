package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which stretches the row has, and where a horizontal drag lands. Pure and tested; the layout is
 * dumb enough to just apply the answer.
 *
 * <p>Where a released drag lands is the one page rule ({@link PagePolicy}), which the Home pages
 * and the app drawer read too.
 */
public final class PaneWallPolicy {

    /** Fraction of a page's width past which a released drag commits: {@link PagePolicy}'s. */
    public static final float DRAG_COMMIT_FRACTION = PagePolicy.COMMIT_FRACTION;
    /**
     * Release speed, in dp per second, that commits regardless of distance: {@link PagePolicy}'s
     * fling. In dp rather than widths, so a tablet is not asked for a faster flick; at two widths
     * a second (over 700 dp/s on a phone) a quick 250 ms flick rarely paged (pong, 2026-09-28).
     */
    public static final float DRAG_COMMIT_VELOCITY_DP_PER_SEC = PagePolicy.FLING_DP_PER_SEC;
    /**
     * How far a flick must have moved the wall, in dp, for its speed to count: a twitch of a
     * held finger is not a page change, however fast it reads. The wall's own input to
     * {@link PagePolicy#turn}.
     */
    public static final float DRAG_COMMIT_FLING_MIN_DISTANCE_DP = 25f;
    /** How much of a drag past the outer page survives as movement. */
    public static final float EDGE_RESISTANCE = 0.35f;
    /**
     * The stretch of the way, from either rest, over which a page's outline fades. Each page draws
     * its own rounded frame, and mid-slide two of them showed side by side; the outlines go over
     * the first few percent of the slide and come back over the last, so there is one edge on
     * screen while the wall moves and the resting look is untouched. Longer than the chrome's
     * landing ({@code PlaceChromeTravel.LANDING}) so the fade reads as a fade and not a blink.
     */
    public static final float OUTLINE_FADE_FRACTION = 0.12f;

    private PaneWallPolicy() {}

    /**
     * The places of the old three-place wall, for the callers that still ask by usage mode (the
     * extra keys' place switches). The wall itself is built from {@link #rowPages}, and the
     * display is a window of the Workspace there, never a page.
     *
     * @param terminalOnly   the terminal-only usage mode: no home surfaces at all
     * @param widgetsEnabled the widget pane is switched on
     * @param displayEnabled the embedded display is built into this edition and switched on
     */
    @NonNull
    public static List<PaneWallPage> availablePages(boolean terminalOnly, boolean widgetsEnabled,
                                                    boolean displayEnabled) {
        List<PaneWallPage> pages = new ArrayList<>(3);
        if (widgetsEnabled && !terminalOnly) pages.add(PaneWallPage.WIDGETS);
        pages.add(PaneWallPage.TERMINAL);
        if (displayEnabled) pages.add(PaneWallPage.DISPLAY);
        return Collections.unmodifiableList(pages);
    }

    /**
     * The wall's row, by the kind of page that stands in each stretch of it (ADR 0008): the Home
     * pages first, always there; then the Workspace's windows, only while the current session's
     * row has one — a terminal window, or the display window (before ADR 0015 A3b). A Home page and
     * a window are each one entry here however many of them there are: the Home pages page inside
     * the widget grid and the windows inside the Workspace's view ({@link WallRow}), and the wall
     * only slides between the stretches.
     *
     * @param hasWindow the current session's row has at least one window, the display's included
     */
    @NonNull
    public static List<PaneWallPage> rowPages(boolean hasWindow) {
        List<PaneWallPage> pages = new ArrayList<>(2);
        pages.add(PaneWallPage.WIDGETS);
        if (hasWindow) pages.add(PaneWallPage.TERMINAL);
        return Collections.unmodifiableList(pages);
    }

    /**
     * The wall's slots in page-list order ({@link WallPages#slots}), without the Terminal slot
     * while the row has no window. For [Home...][Terminal] this is {@link #rowPages} one-to-one,
     * a Home run being the one WIDGETS entry.
     */
    @NonNull
    public static List<WallPages.Slot> rowSlots(@NonNull WallPages pages, boolean hasWindow) {
        List<WallPages.Slot> out = new ArrayList<>();
        for (WallPages.Slot slot : pages.slots()) {
            if (slot.kind == WallPages.PageKind.TERMINAL && !hasWindow) continue;
            out.add(slot);
        }
        return Collections.unmodifiableList(out);
    }

    /** The kind of page a slot is shown by. */
    @NonNull
    public static PaneWallPage kindOf(@NonNull WallPages.Slot slot) {
        switch (slot.kind) {
            case HOME: return PaneWallPage.WIDGETS;
            case DISPLAY: return PaneWallPage.DISPLAY;
            default: return PaneWallPage.TERMINAL;
        }
    }

    /** The kinds of {@code slots}, in order: what the kind-keyed wall list becomes. */
    @NonNull
    public static List<PaneWallPage> kindsOf(@NonNull List<WallPages.Slot> slots) {
        List<PaneWallPage> out = new ArrayList<>(slots.size());
        for (WallPages.Slot slot : slots) out.add(kindOf(slot));
        return out;
    }

    /**
     * Where a slot sits relative to the current one, in whole pages (negative on the left): a
     * plain line over the slots, whatever kinds they are.
     */
    public static int relativeSlot(int current, int slot) {
        return slot - current;
    }

    /** The slot {@code steps} away from {@code current}, held at the row's ends. */
    public static int neighbourSlot(int slotCount, int current, int steps) {
        return Math.max(0, Math.min(Math.max(0, slotCount - 1), current + steps));
    }

    // ---- The built window (ADR 0015, A2a) ---------------------------------------------------

    private static int boundSlotOf(@NonNull List<WallPages.Slot> slots,
                                   @NonNull WallPages.PageKind kind, int current, int bound) {
        if (isKind(slots, current, kind)) return current;
        boolean left = isKind(slots, current - 1, kind);
        boolean right = isKind(slots, current + 1, kind);
        if ((left && bound == current - 1) || (right && bound == current + 1)) return bound;
        if (left) return current - 1;
        if (right) return current + 1;
        if (isKind(slots, bound, kind)) return bound;
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).kind == kind) return i;
        return -1;
    }

    private static boolean isKind(@NonNull List<WallPages.Slot> slots, int i,
                                  @NonNull WallPages.PageKind kind) {
        return i >= 0 && i < slots.size() && slots.get(i).kind == kind;
    }

    /**
     * Where the wall goes when the page it rests on leaves the row: the nearest page to its left
     * that is still there: the last window closing lands on Home. A page the old row did not have
     * hands over to Home.
     *
     * @param before the row as it was
     * @param after  the row as it is now
     * @param gone   the page the wall rested on, no longer in {@code after}
     */
    @NonNull
    public static PaneWallPage pageAfterRemoval(@NonNull List<PaneWallPage> before,
                                                @NonNull List<PaneWallPage> after,
                                                @NonNull PaneWallPage gone) {
        for (int i = before.indexOf(gone) - 1; i >= 0; i--) {
            if (after.contains(before.get(i))) return before.get(i);
        }
        if (after.contains(homePage())) return homePage();
        return after.isEmpty() ? homePage() : after.get(0);
    }

    /** The stretch the home press lands on, and where a page taken away hands the wall: Home. */
    @NonNull
    public static PaneWallPage homePage() {
        return PaneWallPage.WIDGETS;
    }

    /**
     * How far {@code page} stands in from the wall's edge on one side, in px, from the terminal
     * page's margin there ({@code terminalMarginPx}, the chrome shape's opening plus the terminal
     * border's own air) and that air ({@code terminalFrameAirPx}). A terminal window keeps its
     * whole margin. The display window's card stands at the opening itself, the margin less the
     * air: the wall asks for it as a page it does not know ({@code null}). A Home page
     * has no frame, so it stands at none: it reaches the wall's
     * edge and its content keeps Home's Margin as its own rim, the widget grid's, which makes the
     * air from the screen's edge to the first cell exactly one Margin, as the dock's cards and the
     * Home floats' room have it. Standing it in the opening as well had the grid's rim inside the
     * opening's Margin: two Margins of air, and the caps worked out on a grid a Margin narrower.
     * {@code null} is a page the wall does not know, kept at the opening: the display window's.
     */
    public static int pageInsetPx(@Nullable PaneWallPage page, int terminalMarginPx,
                                  int terminalFrameAirPx) {
        if (page == PaneWallPage.TERMINAL) return terminalMarginPx;
        if (page == homePage()) return 0;
        return Math.max(0, terminalMarginPx - Math.max(0, terminalFrameAirPx));
    }

    /**
     * Whether the wall wraps. It used to, with three places; the row is a line now, Home pages on
     * the left and the Workspace on the right, so it never does. Kept so the arithmetic below
     * reads the same for a line of any length.
     */
    public static boolean isRing(@NonNull List<PaneWallPage> pages) {
        return false;
    }

    /**
     * The neighbour {@code steps} places away over the available pages. On a ring the count
     * wraps; on a line it stops at the outer page, which is then returned itself. Missing pages
     * are simply skipped, so a two-page wall has no dead swipe.
     */
    @NonNull
    public static PaneWallPage neighbour(@NonNull List<PaneWallPage> pages,
                                         @NonNull PaneWallPage page, int steps) {
        return neighbourAt(pages, pages.indexOf(page), steps, page);
    }

    /**
     * The page kind {@code steps} entries from entry {@code index} of {@code pages}, counted as
     * {@link #neighbour} counts: a line stops at the outer entry. {@code fallback} when
     * {@code index} is not in the list. For a list with one entry per cell, where a kind can
     * stand in several cells and only the index says which one the wall is on.
     */
    @NonNull
    public static PaneWallPage neighbourAt(@NonNull List<PaneWallPage> pages, int index,
                                           int steps, @NonNull PaneWallPage fallback) {
        if (index < 0 || index >= pages.size()) return fallback;
        int count = pages.size();
        int target = isRing(pages)
            ? (((index + steps) % count) + count) % count
            : Math.max(0, Math.min(count - 1, index + steps));
        return pages.get(target);
    }

    /**
     * The places the status bar offers as tiles from {@code current}: every other page, ordered
     * by where it lies — the one to the left first, the one to the right last — so a tile sits
     * on the side its page slides in from. On a ring of three that is always one of each; on a
     * two-page wall it is the single other page, on whichever side it is.
     */
    @NonNull
    public static List<PaneWallPage> tiles(@NonNull List<PaneWallPage> pages,
                                           @NonNull PaneWallPage current) {
        List<PaneWallPage> result = new ArrayList<>(pages.size());
        for (PaneWallPage page : pages) if (page != current) result.add(page);
        result.sort((a, b) -> Integer.compare(pages.indexOf(a), pages.indexOf(b)));
        return Collections.unmodifiableList(result);
    }

    /** True while a swipe in {@code steps}'s direction has somewhere to go. */
    public static boolean hasNeighbour(@NonNull List<PaneWallPage> pages,
                                       @NonNull PaneWallPage page, int steps) {
        return neighbour(pages, page, steps) != page;
    }

    /**
     * How far the wall actually moves for a finger that has travelled {@code dxPx}. A drag toward
     * a page that does not exist resists instead of sliding away from the wall, and no drag ever
     * exposes more than one page of travel.
     *
     * @param dxPx finger travel since the touch went down, positive to the right
     */
    public static float offsetForDrag(float dxPx, int widthPx, boolean previousExists,
                                      boolean nextExists) {
        if (widthPx <= 0 || dxPx == 0f) return 0f;
        // Moving the finger right brings the page on the left into view, and vice versa.
        boolean towardsMissingPage = dxPx > 0f ? !previousExists : !nextExists;
        float travel = Math.min(Math.abs(dxPx), widthPx);
        if (towardsMissingPage) travel = Math.min(travel * EDGE_RESISTANCE, widthPx * EDGE_RESISTANCE);
        return dxPx > 0f ? travel : -travel;
    }

    /**
     * Where a released drag lands: {@code -1} for the page on the left, {@code +1} for the page on
     * the right, {@code 0} to spring back to the current one.
     *
     * @param offsetPx          the wall's current offset, as {@link #offsetForDrag} returned it
     * @param velocityPxPerSec  release velocity, positive to the right
     * @param density           the screen's density, which the flick's speed and its least
     *                          distance are measured in
     */
    public static int settle(float offsetPx, float velocityPxPerSec, int widthPx, float density,
                             boolean previousExists, boolean nextExists) {
        return settle(offsetPx, velocityPxPerSec, widthPx, density, previousExists, nextExists,
            0f);
    }

    /**
     * {@link #settle}, for a drag that began only once the finger had already gone {@code
     * leadPx} sideways (positive to the right): a slide off a border is claimed past the touch
     * slop and dragged from there, so the wall never jumps by the slop. That lead counts toward
     * the flick's least distance, which is the finger's travel, not the wall's: a quick 25 dp
     * flick pages from a border as it does anywhere, rather than needing the slop on top.
     */
    public static int settle(float offsetPx, float velocityPxPerSec, int widthPx, float density,
                             boolean previousExists, boolean nextExists, float leadPx) {
        if (widthPx <= 0) return 0;
        float d = Math.max(0f, density);
        // Positive offset shows the page on the left.
        int way = PagePolicy.turn(offsetPx, offsetPx + leadPx, velocityPxPerSec, widthPx, d,
            DRAG_COMMIT_FLING_MIN_DISTANCE_DP * d);
        if (way > 0) return previousExists ? -1 : 0;
        if (way < 0) return nextExists ? 1 : 0;
        return 0;
    }

    /**
     * How fast the wall itself is moving for a finger released at {@code velocityPxPerSec},
     * which is what its settle carries on from: the finger's own speed, except toward a page
     * that does not exist, where the wall only ever took {@link #EDGE_RESISTANCE} of the finger's
     * travel ({@link #offsetForDrag}) and so of its speed too.
     *
     * @param offsetPx the wall's offset at the release, as {@link #offsetForDrag} returned it
     */
    public static float wallVelocity(float offsetPx, float velocityPxPerSec,
                                     boolean previousExists, boolean nextExists) {
        if (Float.isNaN(velocityPxPerSec)) return 0f;
        boolean resisting = offsetPx > 0f ? !previousExists : offsetPx < 0f && !nextExists;
        return resisting ? velocityPxPerSec * EDGE_RESISTANCE : velocityPxPerSec;
    }

    /**
     * How much of every page's outline shows for the wall standing {@code offsetPx} from a rest,
     * 0 to 1: all of it at either rest, none once the wall is {@link #OUTLINE_FADE_FRACTION} of a
     * width from both. A function of position alone, so a drag, a fling, a reversal and the
     * commit at release (which moves the offset by a whole width) all read the same number.
     */
    public static float outlineAlpha(float offsetPx, int widthPx) {
        if (widthPx <= 0 || Float.isNaN(offsetPx)) return 1f;
        float way = Math.abs(offsetPx) / widthPx;
        way -= (float) Math.floor(way);
        float fromNearestRest = Math.min(way, 1f - way);
        return 1f - Math.max(0f, Math.min(1f, fromNearestRest / OUTLINE_FADE_FRACTION));
    }

    /**
     * How much of a frame that belongs to one page alone shows — the terminal's own frame line,
     * chrome laid over the wall rather than a rim the page carries — for that page standing
     * {@code pageTranslationPx} from its rest: all of it at rest, fading over the first
     * {@link #OUTLINE_FADE_FRACTION} of the page's departure exactly as a rim does, and none once
     * the page is past half a width out, where the rim's curve is already rising for the page
     * arriving in its place.
     */
    public static float pageOutlineAlpha(float pageTranslationPx, int widthPx) {
        if (widthPx <= 0 || Float.isNaN(pageTranslationPx)) return 1f;
        if (Math.abs(pageTranslationPx) >= widthPx / 2f) return 0f;
        return outlineAlpha(pageTranslationPx, widthPx);
    }

    /**
     * Whether a page whose own alpha is {@code pageAlpha}, as the wall last placed it, is being
     * faded rather than only moved: posed by a transition that fades it (Stack, Depth, Fade), or
     * parked off screen at 0. Its outline then goes with its fade instead of ahead of it.
     */
    public static boolean fadesWithPage(float pageAlpha) {
        return pageAlpha < 1f;
    }

    /**
     * How much of the terminal page's rims show, which its panes draw inside the page: the wall's
     * outline fade ({@link #outlineAlpha}) while the page only moves, so two outlines never show
     * side by side; all of them while the transition fades the page ({@link #fadesWithPage}),
     * since they then fade with the page, as its text and its glass do. Faded on the outline's
     * curve instead, the rims were gone a few percent into the way while the rest of the pane
     * was still on screen, and came back only on the last few percent of an arrival.
     */
    public static float terminalRimAlpha(float outlineAlpha, float pageAlpha) {
        return fadesWithPage(pageAlpha) ? 1f : outlineAlpha;
    }

    /**
     * The same for the terminal's frame line, laid over the wall rather than drawn in the page:
     * {@link #pageOutlineAlpha} while the page only moves, and the page's own alpha while the
     * transition fades it, so the glass edge and what it frames come and go together.
     */
    public static float terminalFrameLineAlpha(float pageOutlineAlpha, float pageAlpha) {
        return fadesWithPage(pageAlpha) ? Math.max(0f, pageAlpha) : pageOutlineAlpha;
    }

    /**
     * Whether the wall standing {@code offsetPx} from {@code current}'s rest shows two places: a
     * drag into a line's outer edge shows the page alone, resisting, and its outline stays.
     */
    public static boolean blendsPlaces(@NonNull List<PaneWallPage> pages,
                                       @NonNull PaneWallPage current, float offsetPx) {
        if (offsetPx == 0f || Float.isNaN(offsetPx)) return false;
        // Pages sitting to the right of their rest show the place to the left of the current one.
        return hasNeighbour(pages, current, offsetPx > 0f ? -1 : 1);
    }

    /** Whether {@code name} names Home outright ({@code home}, {@code widgets}), not a neighbour. */
    public static boolean namesHome(@Nullable String name) {
        if (name == null) return false;
        String value = name.trim().toLowerCase(java.util.Locale.ROOT);
        return "home".equals(value) || "widgets".equals(value);
    }

    /**
     * Resolves the {@code page=} argument of {@code wall.go}, or null when it names nothing.
     * {@code display} names a window, not a page: the activity opens or brings in the display
     * window for it before asking here.
     */
    @Nullable
    public static PaneWallPage parsePage(@NonNull List<PaneWallPage> pages,
                                         @NonNull PaneWallPage current, @Nullable String name) {
        if (name == null) return null;
        String value = name.trim().toLowerCase(java.util.Locale.ROOT);
        switch (value) {
            case "left":
                return neighbour(pages, current, -1);
            case "right":
                return neighbour(pages, current, 1);
            case "home":
            case "widgets":
                return pages.contains(PaneWallPage.WIDGETS) ? PaneWallPage.WIDGETS : null;
            case "workspace":
            case "terminal":
                return pages.contains(PaneWallPage.TERMINAL) ? PaneWallPage.TERMINAL : null;
            default:
                return null;
        }
    }
}
