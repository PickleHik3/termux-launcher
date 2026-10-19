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
     * A new Home float's width, as a share of its room's: centred, with the wallpaper showing on
     * either side. On a phone about 390 dp wide (a room of about 374 dp inside an 8 dp Margin) that
     * is about 322 dp, 26 dp of air each side; in landscape (a room of about 800 dp) about 688 dp.
     */
    public static final float BIRTH_WIDTH_FRAC = 0.86f;
    /**
     * How far below its room's top a new Home float's titlebar starts, as a share of the room's
     * height: about 64 dp of wallpaper above it in a portrait room of about 640 dp, 30 dp in a
     * landscape one of about 300 dp.
     */
    public static final float BIRTH_TOP_FRAC = 0.1f;

    /**
     * The shape a Home float is born with in a room not measured yet: a window, never the whole
     * room. {@link #BIRTH_WIDTH_FRAC} of the room's width, centred, its top {@link #BIRTH_TOP_FRAC}
     * down, and its bottom on the room's bottom; the keyboard that comes up with it shrinks it to
     * sit above the keyboard rather than covering its lower rows ({@link #keyboardRoom}), as it
     * does any float; with the keyboard down it ends Home's Margin above the dock.
     * Maximise still fills the whole room ({@link FloatTitlebarPolicy#fullRoom}).
     */
    @NonNull
    public static RectF birthFrac() {
        float side = (1f - BIRTH_WIDTH_FRAC) / 2f;
        return new RectF(side, BIRTH_TOP_FRAC, 1f - side, 1f);
    }

    /**
     * {@link #birthFrac()} in a room {@code roomWidthPx} by {@code roomHeightPx}: never narrower
     * than {@code minWidthPx}, still centred, and never shorter than {@code minHeightPx} or the
     * titlebar's band {@code titlebarBandPx}, its top coming up toward the room's top to keep that
     * height, so the titlebar always lies wholly in the room. A room smaller than the least size
     * gives the whole room on that axis. A room not measured yet (zero size) gives
     * {@link #birthFrac()}.
     */
    @NonNull
    public static RectF birthFrac(int roomWidthPx, int roomHeightPx, int minWidthPx,
                                  int minHeightPx, int titlebarBandPx) {
        if (roomWidthPx <= 0 || roomHeightPx <= 0) return birthFrac();
        float minWidth = Math.min(roomWidthPx, Math.max(1, minWidthPx));
        float width = Math.min(roomWidthPx, Math.max(minWidth, BIRTH_WIDTH_FRAC * roomWidthPx));
        float left = (roomWidthPx - width) / 2f;
        float minHeight = Math.min(roomHeightPx,
            Math.max(1, Math.max(minHeightPx, titlebarBandPx)));
        float top = Math.max(0f, Math.min(BIRTH_TOP_FRAC * roomHeightPx, roomHeightPx - minHeight));
        return new RectF(left / roomWidthPx, top / roomHeightPx, (left + width) / roomWidthPx, 1f);
    }

    /** The small step right a float born over another takes, beside the step down its titlebar sets. */
    public static final int CASCADE_STEP_RIGHT_DP = 16;

    /**
     * The shape a new Home float is born with, as fractions of a room {@code roomWidthPx} by
     * {@code roomHeightPx}: a window ({@link #birthFrac(int, int, int, int, int)}, with
     * {@code stepDownPx} as the titlebar's band) on a page that shows no float, else
     * cascaded off {@code under}, the topmost float shown there. The cascade keeps {@code under}'s
     * size and steps {@code stepDownPx} down (the titlebar's band, so the titlebar under it stays in
     * sight) and {@code stepRightPx} right, clamped to the room: a float that runs past the room's
     * right or bottom edge ends there, and one that would then be smaller than
     * {@code minWidthPx} by {@code minHeightPx} slides back toward the room's top-left at that size.
     * A room not measured yet (zero size) gives {@link #birthFrac()}.
     */
    @NonNull
    public static RectF cascadeFrac(@Nullable RectF under, int roomWidthPx, int roomHeightPx,
                                    int stepRightPx, int stepDownPx, int minWidthPx,
                                    int minHeightPx) {
        if (roomWidthPx <= 0 || roomHeightPx <= 0) return birthFrac();
        if (under == null)
            return birthFrac(roomWidthPx, roomHeightPx, minWidthPx, minHeightPx, stepDownPx);
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
     * Whether a press on a Home float gives a sideways swipe to the page, which pages Home or
     * crosses the seam as a swipe beside the float does: on the float's terminal, and only while
     * the terminal has no use for a sideways drag. The titlebar ({@code onTitlebar}) moves the
     * float and the resize corner ({@code onResizeGrip}) sizes it; a selection under way
     * ({@code selecting}) is extended by the drag; and a program tracking the mouse
     * ({@code mouseTracking}) is sent the drag, as it would be in the Workspace. Taps and vertical
     * swipes on the terminal stay its own whatever this answers.
     */
    public static boolean bodySwipePagesHome(boolean onTitlebar, boolean onResizeGrip,
                                             boolean selecting, boolean mouseTracking) {
        return !onTitlebar && !onResizeGrip && !selecting && !mouseTracking;
    }

    /**
     * How far a terminal widget may rise: what its cursor row asks ({@code wantedPx}), but never
     * past {@code roomTopPx}, so it stays below the status bar. {@code floatTopPx} is its laid-out
     * top. Home floats are not lifted: they take their shape above the keyboard
     * ({@link #keyboardRoom}).
     */
    public static int liftPx(int wantedPx, int floatTopPx, int roomTopPx) {
        return Math.max(0, Math.min(wantedPx, floatTopPx - roomTopPx));
    }

    /**
     * How far a focused terminal widget rises over the keyboard: its cursor row's bottom past
     * the keyboard's top, never higher than {@code ceilingPx} (the room's top, or the top of the
     * grid that clips the widget, whichever is lower), nothing while the keyboard is down. A
     * widget is lifted, never resized: its size is the user's, set in edit mode. All in one
     * coordinate space, read without any lift applied.
     */
    public static int hostedLiftPx(boolean keyboardShown, int cursorRowBottomPx, int keyboardTopPx,
                                   int widgetTopPx, int ceilingPx) {
        if (!keyboardShown || keyboardTopPx < 0) return 0;
        return liftPx(Math.max(0, cursorRowBottomPx - keyboardTopPx), widgetTopPx, ceilingPx);
    }

    /**
     * The ground a widget's terminal stands on: the terminal palette's background, so its ink
     * reads as in the Workspace, at {@code alpha} (whole on a plain card, the card's own
     * see-through over glass). A palette background left transparent is taken at its colour.
     */
    public static int hostedGround(int paletteBackground, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (paletteBackground & 0x00FFFFFF);
    }

    /**
     * The room a Home float shaped {@code frac} is laid out in while the keyboard may be up, in the
     * layer's pixels. With the keyboard up and its top ({@code keyboardTopPx}) above the float's
     * bottom, it is the room in which {@code frac} lands on {@link #keyboardShape}'s rect: the float
     * keeps its own top and ends at the keyboard, or, with too little of it left above the
     * keyboard, rises to stand on the keyboard at {@code minHeightPx}. Any float clear of the
     * keyboard, and every float while it is down, keeps the whole room. The float's fractions stay
     * those of the whole room: the keyboard is no resize, and closing it gives the shape back.
     */
    @NonNull
    public static Rect keyboardRoom(@NonNull Rect room, @Nullable RectF frac, boolean keyboardUp,
                                    int keyboardTopPx, int minHeightPx) {
        if (frac == null || !keyboardUp || keyboardTopPx >= room.bottom || room.height() <= 0)
            return new Rect(room);
        float span = frac.bottom - frac.top;
        if (span <= 0f) return new Rect(room);
        int top = room.top + Math.round(frac.top * room.height());
        int bottom = room.top + Math.round(frac.bottom * room.height());
        if (keyboardTopPx >= bottom) return new Rect(room);
        Rect shaped = keyboardShape(new Rect(room.left, top, room.right, bottom), room,
            keyboardTopPx, minHeightPx);
        int height = Math.max(1, Math.round(shaped.height() / span));
        // Laid out at this room's top + round(frac.top * height): exactly the shape's top.
        int roomTop = shaped.top - Math.round(frac.top * height);
        return new Rect(room.left, roomTop, room.right, roomTop + height);
    }

    /**
     * Where a float laid out at {@code floatPx} (the layer's pixels, inside {@code room}) stands
     * while the keyboard, whose top is {@code keyboardTopPx} (negative while it is down), is up:
     * it keeps its own top and its bottom comes up to the keyboard's top, so its titlebar stays
     * where it was. Where that would leave it shorter than {@code minHeightPx} (or than it already
     * was), it keeps that height instead and rises until it stands on the keyboard, never past
     * the room's top: a room too short above the keyboard for even that leaves it all the room
     * there is. A keyboard below the float changes nothing.
     */
    @NonNull
    public static Rect keyboardShape(@NonNull Rect floatPx, @NonNull Rect room, int keyboardTopPx,
                                     int minHeightPx) {
        int bottom = Math.min(floatPx.bottom, room.bottom);
        if (keyboardTopPx < 0 || keyboardTopPx >= bottom) {
            return new Rect(floatPx.left, floatPx.top, floatPx.right, bottom);
        }
        int above = Math.max(1, keyboardTopPx - room.top);
        int keep = Math.max(1, Math.min(above,
            Math.min(Math.max(0, minHeightPx), floatPx.height())));
        int top = Math.max(room.top, Math.min(floatPx.top, keyboardTopPx - keep));
        return new Rect(floatPx.left, top, floatPx.right, Math.max(top + keep, keyboardTopPx));
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

    // --- Launching ---

    /** What asked for a new terminal, for {@link #launchesAsFloat}. */
    public enum Launch {
        /**
         * A tap on the Terminal entry (the dock, the drawer): it goes to the Workspace while there
         * is one, so it launches a terminal only when the Workspace is empty.
         */
        ENTRY_TAP,
        /** A new terminal or window: a keybind, the palette, the local API, Home's own Terminal items. */
        NEW_TERMINAL,
        /** The Terminal entry's New window: a window of the row, by name. */
        NEW_WINDOW
    }

    /**
     * Whether a new terminal asked for by {@code launch} opens as a Home float on the page in view
     * instead of as a window of the Workspace's row. Every terminal launched while Home is in front
     * ({@code homeInFront}) is a Home float, the first one included and with no window anywhere,
     * so a launch from Home never takes the screen. A tap on the Terminal entry launches only into
     * an empty Workspace ({@code workspaceOnRow} false); with a Workspace it goes there. New window
     * names a window and stays one. Where Home cannot carry a float ({@code canFloat} false:
     * compatibility mode, no Home pages) the row takes it as before.
     */
    public static boolean launchesAsFloat(@NonNull Launch launch, boolean homeInFront,
                                          boolean workspaceOnRow, boolean canFloat) {
        if (!canFloat || !homeInFront) return false;
        switch (launch) {
            case ENTRY_TAP: return !workspaceOnRow;
            case NEW_TERMINAL: return true;
            case NEW_WINDOW:
            default: return false;
        }
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
