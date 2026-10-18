package com.termux.app.terminal;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The arithmetic of a <b>Home float</b> (docs/GLOSSARY.md): a window of the current session drawn
 * floating over one Home page instead of in the Workspace's row. Pure, so the room it is born into,
 * which page shows it, its memory of each orientation and the chip it is tucked into read as
 * arithmetic without a window to float.
 */
public final class HomeFloatPolicy {

    private HomeFloatPolicy() {}

    /** A window's Home page while it is not a Home float: it is in the Workspace's row. */
    public static final int NOT_HOME_FLOAT = -1;

    /**
     * The shape a Home float is born with: the whole room, between the status bar and the dock
     * with Home's Margin of air inside them ({@link #room}).
     */
    @NonNull
    public static RectF birthFrac() {
        return FloatTitlebarPolicy.fullRoom();
    }

    /** The small step right a float born over another takes, beside the step down its titlebar sets. */
    public static final int CASCADE_STEP_RIGHT_DP = 16;

    /**
     * The shape a new Home float is born with, as fractions of a room {@code roomWidthPx} by
     * {@code roomHeightPx}: full-size ({@link #birthFrac}) on a page that shows no float, else
     * cascaded off {@code under}, the topmost float shown there. The cascade keeps {@code under}'s
     * size and steps {@code stepDownPx} down (the titlebar's band, so the titlebar under it stays in
     * sight) and {@code stepRightPx} right, clamped to the room: a float that runs past the room's
     * right or bottom edge ends there, and one that would then be smaller than
     * {@code minWidthPx} by {@code minHeightPx} slides back toward the room's top-left at that size.
     * A room not measured yet (zero size) gives the full-size birth.
     */
    @NonNull
    public static RectF cascadeFrac(@Nullable RectF under, int roomWidthPx, int roomHeightPx,
                                    int stepRightPx, int stepDownPx, int minWidthPx,
                                    int minHeightPx) {
        if (under == null || roomWidthPx <= 0 || roomHeightPx <= 0) return birthFrac();
        float[] x = cascadeSpan(under.left * roomWidthPx, under.right * roomWidthPx, roomWidthPx,
            stepRightPx, minWidthPx);
        float[] y = cascadeSpan(under.top * roomHeightPx, under.bottom * roomHeightPx, roomHeightPx,
            stepDownPx, minHeightPx);
        return new RectF(x[0] / roomWidthPx, y[0] / roomHeightPx, x[1] / roomWidthPx,
            y[1] / roomHeightPx);
    }

    /** One axis of {@link #cascadeFrac}, in pixels: the span {@code [start, end)} stepped on and clamped. */
    @NonNull
    private static float[] cascadeSpan(float start, float end, int roomPx, int stepPx, int minPx) {
        float min = Math.max(1, Math.min(minPx, roomPx));
        float size = Math.max(min, Math.min(roomPx, end - start));
        float from = Math.max(0f, Math.min(start + Math.max(0, stepPx), roomPx - min));
        float to = Math.max(from + min, Math.min(roomPx, from + size));
        return new float[]{from, to};
    }

    /**
     * The room Home floats are laid out in, in the float layer's own pixels: the layer's width, and
     * its height between {@code topPx} (what the status bar covers) and {@code bottomPx} from its
     * bottom (what the dock covers), inset on all four sides by {@code marginPx}, Home's Margin:
     * the same air the widget grid keeps from the page's edge and the dock's cards keep round
     * them. Never inverted: a room squeezed to nothing keeps one pixel each way, the Margin giving
     * way evenly on both sides; an unmeasured layer (zero width) stays zero wide.
     */
    @NonNull
    public static Rect room(int layerWidthPx, int layerHeightPx, int topPx, int bottomPx,
                            int marginPx) {
        int width = Math.max(0, layerWidthPx);
        int height = Math.max(0, layerHeightPx);
        int top = Math.max(0, Math.min(height, topPx));
        int bottom = Math.max(top + 1, height - Math.max(0, bottomPx));
        int margin = Math.max(0, marginPx);
        int side = width == 0 ? 0 : Math.min(margin, (width - 1) / 2);
        int edge = Math.min(margin, (bottom - top - 1) / 2);
        return new Rect(side, top + edge, width - side, bottom - edge);
    }

    /**
     * A Home float's shape {@code candidate}, fractions of its room ({@code roomWidthPx} by
     * {@code roomHeightPx}), held inside that room: a float never hangs off any side of it, so its
     * titlebar, its buttons and its text stay on screen in either orientation. A float as wide (or
     * tall) as its room cannot move along that axis at all; a smaller one moves up to the room's
     * edges and no further. Never smaller than {@code minWidthPx} by {@code minHeightPx}, nor
     * larger than the room.
     *
     * <p>A move ({@code resizing} false) keeps the float's size and stops it at the edge it runs
     * into. A resize keeps its top-left corner (inside the room, with room for the minimum size)
     * and stops its bottom-right corner at the room's edge, so dragging the corner past the edge
     * does not shove the float the other way.
     *
     * <p>Stricter than {@link TerminalPaneController#clampFloatFractions} on purpose: that clamp
     * lets a window's floating pane hang off sideways while 48 dp of its handle stays reachable,
     * and on a Home float, whose room is the whole screen's width, that let a full-width float be
     * dragged until its titlebar and text were clipped off the screen.
     */
    @NonNull
    public static RectF clampInRoom(@NonNull RectF candidate, float roomWidthPx, float roomHeightPx,
                                    float minWidthPx, float minHeightPx, boolean resizing) {
        if (roomWidthPx <= 0f || roomHeightPx <= 0f) return new RectF(candidate);
        float minWidth = Math.max(0f, Math.min(1f, minWidthPx / roomWidthPx));
        float minHeight = Math.max(0f, Math.min(1f, minHeightPx / roomHeightPx));
        if (resizing) {
            float left = clamp(candidate.left, 0f, 1f - minWidth);
            float top = clamp(candidate.top, 0f, 1f - minHeight);
            float right = clamp(candidate.right, left + minWidth, 1f);
            float bottom = clamp(candidate.bottom, top + minHeight, 1f);
            return new RectF(left, top, right, bottom);
        }
        float width = clamp(candidate.width(), minWidth, 1f);
        float height = clamp(candidate.height(), minHeight, 1f);
        float left = clamp(candidate.left, 0f, 1f - width);
        float top = clamp(candidate.top, 0f, 1f - height);
        return new RectF(left, top, Math.min(1f, left + width), Math.min(1f, top + height));
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    /**
     * How far the focused float rises over the keyboard: what its cursor row asks
     * ({@code wantedPx}), but never past the room's top, so the titlebar stays below the status
     * bar with its buttons in reach. {@code floatTopPx} and {@code roomTopPx} are the float's
     * laid-out top and the room's top in the layer's pixels. Only a float that stops short of the
     * room's bottom is lifted; one that reaches it shrinks instead ({@link #keyboardRoom}).
     */
    public static int liftPx(int wantedPx, int floatTopPx, int roomTopPx) {
        return Math.max(0, Math.min(wantedPx, floatTopPx - roomTopPx));
    }

    /** How far above the room's bottom a float may end and still reach it: rounding, not a placement. */
    private static final float FILL_EPSILON = 1e-4f;

    /**
     * Whether a float shaped {@code frac} reaches its room's bottom: full-size, maximised, or
     * cascaded off one of those, and not moved or resized off the bottom since. Such a float meets
     * the keyboard by shrinking above it ({@link #keyboardRoom}); any other is lifted.
     */
    public static boolean reachesBottom(@Nullable RectF frac) {
        return frac != null && frac.bottom >= 1f - FILL_EPSILON;
    }

    /**
     * The room the focused float shaped {@code frac} is laid out in while the keyboard may be up,
     * in the layer's pixels. For a float that reaches {@code room}'s bottom, with the keyboard up
     * and its top ({@code keyboardTopPx}) inside the room, it is the room in which {@code frac}
     * lands on {@link #keyboardShrunk}'s rect: the float keeps its own top and ends at the
     * keyboard, so a full-size float ends there as a tiled pane does and a cascaded one keeps its
     * titlebar below the one it was cascaded off. Any other float keeps the whole room and is
     * lifted instead. The float's fractions stay those of the whole room: the keyboard is no resize.
     */
    @NonNull
    public static Rect keyboardRoom(@NonNull Rect room, @Nullable RectF frac, boolean keyboardUp,
                                    int keyboardTopPx, int minHeightPx) {
        if (!reachesBottom(frac) || !keyboardUp || keyboardTopPx >= room.bottom
            || room.height() <= 0) return new Rect(room);
        float span = frac.bottom - frac.top;
        if (span <= 0f) return new Rect(room);
        int top = room.top + Math.round(frac.top * room.height());
        int bottom = room.top + Math.round(frac.bottom * room.height());
        Rect shrunk = keyboardShrunk(new Rect(room.left, top, room.right, bottom), room,
            keyboardTopPx, minHeightPx);
        int height = Math.max(1, Math.round(shrunk.height() / span));
        // Laid out at this room's top + round(frac.top * height): exactly the float's own top.
        int roomTop = shrunk.top - Math.round(frac.top * height);
        return new Rect(room.left, roomTop, room.right, roomTop + height);
    }

    /**
     * Where a float laid out at {@code floatPx} (the layer's pixels, inside {@code room}) sits while
     * it shrinks above the keyboard, whose top is {@code keyboardTopPx} (negative while the keyboard
     * is down): it keeps its own top edge, so its titlebar stays where it was, and only its bottom
     * comes up to the keyboard's top, never so far that it is shorter than {@code minHeightPx} (or
     * than it already was). A float whose top is the room's top ends at the keyboard, as a tiled
     * pane does; a keyboard below the float changes nothing.
     */
    @NonNull
    public static Rect keyboardShrunk(@NonNull Rect floatPx, @NonNull Rect room, int keyboardTopPx,
                                      int minHeightPx) {
        int bottom = Math.min(floatPx.bottom, room.bottom);
        if (keyboardTopPx < 0 || keyboardTopPx >= bottom) {
            return new Rect(floatPx.left, floatPx.top, floatPx.right, bottom);
        }
        int keep = Math.max(1, Math.min(Math.max(0, minHeightPx), floatPx.height()));
        return new Rect(floatPx.left, floatPx.top, floatPx.right,
            Math.max(floatPx.top + keep, keyboardTopPx));
    }

    /**
     * {@code frac}, fractions of {@code from}, as fractions of {@code to}: the same pixels. A drag
     * on a float shrunk above the keyboard starts from what it shows, in its whole room's terms.
     */
    @NonNull
    public static RectF reframe(@NonNull RectF frac, @NonNull Rect from, @NonNull Rect to) {
        if (to.width() <= 0 || to.height() <= 0 || from.equals(to)) return new RectF(frac);
        float left = from.left + frac.left * from.width();
        float top = from.top + frac.top * from.height();
        float right = from.left + frac.right * from.width();
        float bottom = from.top + frac.bottom * from.height();
        return new RectF((left - to.left) / to.width(), (top - to.top) / to.height(),
            (right - to.left) / to.width(), (bottom - to.top) / to.height());
    }

    /** Whether Home page {@code page} shows a float pinned to {@code floatPage}. */
    public static boolean shownOn(int floatPage, boolean minimised, int page) {
        return !minimised && floatPage >= 0 && floatPage == page;
    }

    /** Whether a room this size is a landscape one, which is what a float's memory is keyed by. */
    public static boolean isLandscape(float widthPx, float heightPx) {
        return widthPx > heightPx;
    }

    // --- Saved sessions ---

    /**
     * The Home page a saved Home float loads back onto when Home has {@code homeCount} pages: the
     * page it was saved on, or the last page when that one no longer exists.
     * {@link #NOT_HOME_FLOAT} when there are no Home pages at all: it loads as a tiled window.
     */
    public static int restoredPage(int savedPage, int homeCount) {
        if (homeCount <= 0) return NOT_HOME_FLOAT;
        return Math.max(0, Math.min(savedPage, homeCount - 1));
    }

    /**
     * Which of a saved session's windows load back as Home floats: every one saved as a float
     * ({@code savedAsFloat}), while there are Home pages to float over ({@code homePages});
     * without them all load into the row. A session saved as floats only loads as floats only,
     * leaving no window in the row: the Workspace may be empty.
     */
    @NonNull
    public static boolean[] restoredAsFloats(@NonNull boolean[] savedAsFloat, boolean homePages) {
        boolean[] asFloat = new boolean[savedAsFloat.length];
        if (!homePages) return asFloat;
        System.arraycopy(savedAsFloat, 0, asFloat, 0, savedAsFloat.length);
        return asFloat;
    }

    /**
     * The index in the row of a saved session's current window {@code savedCurrent}, once the
     * windows marked in {@code asFloat} have gone to Home: the same window when it stays in the
     * row, else the row's window before it (its first when none is before it), or -1 when no
     * window is left in the row. The wall is not slid to Home for a current window that floats.
     */
    public static int restoredRowIndex(@NonNull boolean[] asFloat, int savedCurrent) {
        int row = 0;
        for (boolean floats : asFloat) if (!floats) row++;
        if (row == 0) return -1;
        int current = Math.max(0, Math.min(savedCurrent, asFloat.length - 1));
        int before = 0;
        for (int i = 0; i < current; i++) if (!asFloat[i]) before++;
        return asFloat[current] ? Math.max(0, before - 1) : before;
    }

    /**
     * A float's shape in each orientation, for Saved sessions: {@code frac}, its live shape, under
     * the orientation it was last laid out in ({@code memory}'s, else {@code landscapeNow}), and
     * the shape {@code memory} keeps for the other one, if it has been shaped there. Index 0 is
     * portrait, 1 landscape; null where no shape is known.
     */
    @NonNull
    public static RectF[] savedShapes(@Nullable ShapeMemory memory, @Nullable RectF frac,
                                      boolean landscapeNow) {
        Boolean laidOut = memory == null ? null : memory.orientation();
        boolean landscape = laidOut != null ? laidOut : landscapeNow;
        RectF other = memory == null ? null : memory.remembered(!landscape);
        RectF live = frac == null ? null : new RectF(frac);
        return landscape ? new RectF[]{other, live} : new RectF[]{live, other};
    }

    /**
     * The words a float's chip carries after its label: the float's page, as the page dots count
     * it ("· 2"), so floats from every page can share one row of chips.
     */
    @NonNull
    public static String chipPageMark(int floatPage) {
        return " · " + (Math.max(0, floatPage) + 1);
    }

    /**
     * A float's size and spot in each orientation, while the process lives. Today's floats keep
     * fractions of their room, which carry proportionally across a turn; this adds the turn as a
     * key, so a float shaped in portrait and again in landscape comes back to each shape.
     */
    public static final class ShapeMemory {
        @Nullable private Boolean mLandscape;
        @Nullable private RectF mPortraitFrac, mPortraitRestore;
        @Nullable private RectF mLandscapeFrac, mLandscapeRestore;
        private boolean mPortraitKnown, mLandscapeKnown;

        /**
         * The room is {@code landscape} now. On a turn, what the float has ({@code frac} and the
         * shape Maximise restores, {@code restoreFrac}) is kept under the orientation it leaves,
         * and the shape it last had in the new one is handed back. Null keeps the float as it is:
         * no turn, or a first visit to this orientation.
         */
        @Nullable
        public Shape turn(boolean landscape, @Nullable RectF frac, @Nullable RectF restoreFrac) {
            Boolean previous = mLandscape;
            mLandscape = landscape;
            if (previous == null || previous == landscape) return null;
            if (previous) {
                mLandscapeFrac = copy(frac);
                mLandscapeRestore = copy(restoreFrac);
                mLandscapeKnown = true;
            } else {
                mPortraitFrac = copy(frac);
                mPortraitRestore = copy(restoreFrac);
                mPortraitKnown = true;
            }
            if (landscape ? !mLandscapeKnown : !mPortraitKnown) return null;
            return landscape ? new Shape(copy(mLandscapeFrac), copy(mLandscapeRestore))
                : new Shape(copy(mPortraitFrac), copy(mPortraitRestore));
        }

        /**
         * The orientation the float's live shape belongs to: the one it was last laid out in
         * (true for landscape), or null before its first layout.
         */
        @Nullable
        public Boolean orientation() {
            return mLandscape;
        }

        /**
         * The shape kept for an orientation the float has left ({@code landscape} or portrait),
         * or null when it has not been shaped there. The orientation it is laid out in now keeps
         * its shape on the float itself, not here.
         */
        @Nullable
        public RectF remembered(boolean landscape) {
            if (mLandscape != null && mLandscape == landscape) return null;
            return landscape ? (mLandscapeKnown ? copy(mLandscapeFrac) : null)
                : (mPortraitKnown ? copy(mPortraitFrac) : null);
        }

        /**
         * Remembers {@code frac} for {@code landscape} or portrait as if the float had been shaped
         * there and left it: a float loaded from Saved sessions takes that shape on its first turn
         * into that orientation. Not maximised there: Maximise's restore is not saved.
         */
        public void seed(boolean landscape, @NonNull RectF frac) {
            if (landscape) {
                mLandscapeFrac = new RectF(frac);
                mLandscapeRestore = null;
                mLandscapeKnown = true;
            } else {
                mPortraitFrac = new RectF(frac);
                mPortraitRestore = null;
                mPortraitKnown = true;
            }
        }

        @Nullable
        private static RectF copy(@Nullable RectF rect) {
            return rect == null ? null : new RectF(rect);
        }
    }

    /** A remembered shape: the float's fractions, and what Maximise restores (null unless maximised). */
    public static final class Shape {
        @Nullable public final RectF frac;
        @Nullable public final RectF restoreFrac;

        Shape(@Nullable RectF frac, @Nullable RectF restoreFrac) {
            this.frac = frac;
            this.restoreFrac = restoreFrac;
        }
    }
}
