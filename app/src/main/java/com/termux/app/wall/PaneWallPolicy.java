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
    /** Release velocity, as page widths per second, that commits regardless of distance. */
    public static final float DRAG_COMMIT_VELOCITY_PAGES = 2.0f;
    /** How much of a drag past the outer page survives as movement. */
    public static final float EDGE_RESISTANCE = 0.35f;

    private PaneWallPolicy() {}

    /**
     * The places this install has, in spatial order. The terminal is always there — it is the
     * home screen.
     *
     * @param terminalOnly   the terminal-only use case: no home surfaces at all
     * @param widgetsEnabled the widgets feature is on
     * @param displayEnabled the embedded display is built in and switched on
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
     */
    public static int settle(float offsetPx, float velocityPxPerSec, int widthPx,
                             boolean previousExists, boolean nextExists) {
        if (widthPx <= 0) return 0;
        float flingPx = widthPx * DRAG_COMMIT_VELOCITY_PAGES;
        // A flick decides on its own, whichever way the finger had already dragged: reversing
        // direction at the end of a drag must not commit the page the drag was heading for.
        if (velocityPxPerSec >= flingPx) return previousExists ? -1 : 0;
        if (velocityPxPerSec <= -flingPx) return nextExists ? 1 : 0;
        float commitPx = widthPx * DRAG_COMMIT_FRACTION;
        if (offsetPx >= commitPx) return previousExists ? -1 : 0;
        if (offsetPx <= -commitPx) return nextExists ? 1 : 0;
        return 0;
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
