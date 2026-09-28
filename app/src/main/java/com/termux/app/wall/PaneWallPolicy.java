package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which places the wall has, and where a horizontal drag lands. Pure and tested; the layout is
 * dumb enough to just apply the answer.
 *
 * <p>The commit thresholds are the ones the status bar's pull-down already uses, so a page swipe
 * and a status pull feel like the same gesture at the same speeds.
 */
public final class PaneWallPolicy {

    /** Fraction of a page's width past which a released drag commits to the next page. */
    public static final float DRAG_COMMIT_FRACTION = 0.35f;
    /**
     * Release speed, in dp per second, that commits regardless of distance: the pager's usual
     * (ViewPager's minimum fling velocity). It used to be two page widths a second — over
     * 700 dp/s on a phone — which a quick flick of 250 ms rarely reaches, so short flicks on the
     * bar sprang back and only long drags past {@link #DRAG_COMMIT_FRACTION} paged (pong,
     * 2026-09-28). In dp rather than widths, so a tablet is not asked for a faster flick.
     */
    public static final float DRAG_COMMIT_VELOCITY_DP_PER_SEC = 400f;
    /**
     * How far a flick must have moved the wall, in dp, for its speed to count: a twitch of a
     * held finger is not a page change, however fast it reads.
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
     * The places this install has, in spatial order. The terminal is always there — it is the
     * home screen. The usage mode decides the rest: terminal mode is the terminal alone; a home
     * screen adds the Widgets place while the widget pane is on; the display mode adds the
     * Display place. A place is on the wall only while its own switch is on — the Display place
     * is not built at all while the display is off, whatever the build carries.
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

    /** The page {@code pages} shows at rest on a cold start, and where Home returns to. */
    @NonNull
    public static PaneWallPage homePage() {
        return PaneWallPage.TERMINAL;
    }

    /**
     * Whether the wall wraps. Three places make a ring — past the Display page comes the Widgets
     * page, and the other way round — so every place is one step from every other and the two
     * tiles in the status bar are always "the place to my left" and "the place to my right".
     * Two places cannot form a ring (the one other page cannot be on both sides at once), so a
     * two-page wall stays a line with an end.
     */
    public static boolean isRing(@NonNull List<PaneWallPage> pages) {
        return pages.size() >= 3;
    }

    /**
     * Where {@code page} sits relative to {@code current}, in places: negative to the left,
     * positive to the right, zero for itself. On a ring it is the shorter way round, so on a
     * three-page wall every other page is exactly one place away.
     */
    public static int relativePosition(@NonNull List<PaneWallPage> pages,
                                       @NonNull PaneWallPage current, @NonNull PaneWallPage page) {
        int from = pages.indexOf(current);
        int to = pages.indexOf(page);
        if (from < 0 || to < 0) return 0;
        int distance = to - from;
        if (!isRing(pages)) return distance;
        int count = pages.size();
        distance = ((distance % count) + count) % count;
        if (distance > count / 2) distance -= count;
        return distance;
    }

    /**
     * The neighbour {@code steps} places away over the available pages. On a ring the count
     * wraps; on a line it stops at the outer page, which is then returned itself. Missing pages
     * are simply skipped, so a two-page wall has no dead swipe.
     */
    @NonNull
    public static PaneWallPage neighbour(@NonNull List<PaneWallPage> pages,
                                         @NonNull PaneWallPage page, int steps) {
        int index = pages.indexOf(page);
        if (index < 0) return page;
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
        result.sort((a, b) -> Integer.compare(relativePosition(pages, current, a),
            relativePosition(pages, current, b)));
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
        if (widthPx <= 0) return 0;
        float flingPx = DRAG_COMMIT_VELOCITY_DP_PER_SEC * Math.max(0f, density);
        boolean flung = Math.abs(offsetPx) >= DRAG_COMMIT_FLING_MIN_DISTANCE_DP
            * Math.max(0f, density);
        // A flick decides on its own, whichever way the finger had already dragged: reversing
        // direction at the end of a drag must not commit the page the drag was heading for.
        if (flung && velocityPxPerSec >= flingPx) return previousExists ? -1 : 0;
        if (flung && velocityPxPerSec <= -flingPx) return nextExists ? 1 : 0;
        float commitPx = widthPx * DRAG_COMMIT_FRACTION;
        if (offsetPx >= commitPx) return previousExists ? -1 : 0;
        if (offsetPx <= -commitPx) return nextExists ? 1 : 0;
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
     * Whether the wall standing {@code offsetPx} from {@code current}'s rest shows two places: a
     * drag into a line's outer edge shows the page alone, resisting, and its outline stays.
     */
    public static boolean blendsPlaces(@NonNull List<PaneWallPage> pages,
                                       @NonNull PaneWallPage current, float offsetPx) {
        if (offsetPx == 0f || Float.isNaN(offsetPx)) return false;
        // Pages sitting to the right of their rest show the place to the left of the current one.
        return hasNeighbour(pages, current, offsetPx > 0f ? -1 : 1);
    }

    /** Resolves the {@code page=} argument of {@code wall.go}, or null when it names nothing. */
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
            case "widgets":
                return pages.contains(PaneWallPage.WIDGETS) ? PaneWallPage.WIDGETS : null;
            case "terminal":
                return PaneWallPage.TERMINAL;
            case "display":
                return pages.contains(PaneWallPage.DISPLAY) ? PaneWallPage.DISPLAY : null;
            default:
                return null;
        }
    }
}
