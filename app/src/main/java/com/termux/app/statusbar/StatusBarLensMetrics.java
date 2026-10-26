package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the status bar's place marks are made of, worked out away from the canvas: how big
 * each mark is, where it sits, how much ink it carries, and the patch of bar a finger may land on
 * to reach it. {@link StatusBarLensView} paints the answer and hit-tests it; it decides none of it.
 *
 * <p>Since #90 (decided 2026-10-21) the bar wears no place marks on either half: {@link #marks}
 * answers none and the row keeps no leading cell for one ({@link #leadingCellWidthPx}). The
 * sizes and the ink below stay as the lens's vocabulary; the notes that follow are the history
 * of the marks the bar used to wear.
 *
 * <p>The movement model lives next door in {@link StatusBarLensPolicy} and is untouched here: a
 * place is one bar-width from its neighbours, the two neighbours rest half past the bar's ends, and
 * a drag carries the arriving mark home as the one at home leaves. What this class adds is the part
 * that was buried in the view — the sizes, the ink and the target — so the floor a peeking mark is
 * held to can be stated and tested rather than eyeballed.
 *
 * <h3>Legibility floor</h3>
 *
 * A neighbour is quieter than the place you are on; it is not meant to be a puzzle. Two things used
 * to take turns dimming it, and they multiplied: the mark lost 38% of its ink to being a neighbour,
 * and the dissolve that carries it past the bar's end ran to nothing exactly where its glyph is
 * drawn, halving what was left. A fully peeked mark reached the glass at 31% of full ink. The
 * drain is now 15% and the dissolve keeps half its ink at the mark's outer edge, so
 * {@link Mark#effectiveInk} stands at or above {@link #EFFECTIVE_INK_FLOOR} for every peeking
 * mark. The hierarchy that says which place you are on is carried where it costs no legibility:
 * the neighbour keeps the place's own colour and is faded in it, and only the mark at home wears
 * a glow.
 *
 * <h3>The same three colours as the row</h3>
 *
 * A place is the colour its switch wears in the extra-keys row — the lens is handed that colour
 * rather than deriving one — and the row's own heuristic comes with it: the place in front is full
 * colour and the ones behind it are the same colour at
 * {@link com.termux.shared.termux.extrakeys.PlaceSwitchGlyph#UNFOCUSED_ALPHA}. That fade is
 * {@link Mark#glyphInk}; it replaced draining the neighbour's colour towards a grey, which said the
 * same thing in a colour the row never shows.
 */
public final class StatusBarLensMetrics {

    // ---------------------------------------------------------------- sizes

    /** The home mark's largest size in the expanded bar; the clock's band can ask for less. */
    public static final float ICON_DP = 36f;
    /** A neighbour's size as a share of the home mark's. */
    public static final float PEEK_SHARE = 0.84f;
    /** The gap between a mark and what comes after it. */
    public static final float ICON_GAP_DP = 8f;
    /** Every mark's size in the compact bar, where the row carries no home mark at all. */
    public static final float COMPACT_ICON_DP = 24f;
    /** The home mark's leading edge, clear of the neighbour peeking past the bar's near end. */
    public static final float HOME_X_DP = 20f;
    /** The expanded bar's slot height; the home mark centres on it until the clock says where. */
    public static final float SLOT_HEIGHT_DP = StatusBarEdgeGeometry.SLOT_DOCKED_DP;
    /** How far the home mark's glow reaches past its edge, as a share of its size. */
    public static final float GLOW_REACH = 0.55f;
    /** The glyph's size as a share of the mark's. */
    public static final float GLYPH_SHARE = 0.5f;
    /** The mark's corner when the bar has not said what its chips wear. */
    public static final float FALLBACK_RADIUS_SHARE = 0.32f;

    // ---------------------------------------------------------------- ink

    /**
     * What a neighbour's glyph keeps of its colour's strength: the share the extra-keys row fades
     * its own unfocused place switches by, so the mark in the bar and the key in the row are the
     * same colour at the same strength. The floor is applied after it — a mark that cannot read at
     * this alpha on the measured band is lifted until it can, which is the lens's own business.
     */
    public static final float UNFOCUSED_GLYPH_SHARE =
        com.termux.shared.termux.extrakeys.PlaceSwitchGlyph.UNFOCUSED_ALPHA / 255f;
    /** How much ink a mark loses to being a neighbour rather than the place on screen. */
    public static final float PEEK_INK_DRAIN = 0.15f;
    /** How much of its ink the dissolve takes at a fully peeked mark's outer edge. */
    public static final float FADE_DEPTH = 0.5f;
    /** The floor {@link Mark#effectiveInk} is held to for a peeking mark. */
    public static final float EFFECTIVE_INK_FLOOR = 0.5f;
    /** Below this a mark has left; it is not drawn and nothing can be tapped on it. */
    public static final float VANISHED_ALPHA = 0.01f;

    /** The three weights a mark is painted with, before its ink is applied. */
    public static final int FILL_ALPHA = 31;
    public static final int STROKE_ALPHA = 84;
    public static final int GLYPH_ALPHA = 255;

    // ---------------------------------------------------------------- target

    /** The platform's smallest comfortable target, and what a peeking mark is grown to. */
    public static final float TOUCH_TARGET_DP = 48f;
    /** The air a mark keeps around itself before the minimum target is applied. */
    public static final float TOUCH_SLOP_DP = 8f;
    /**
     * How far in from the bar's end a peeking neighbour's own target reaches: half the largest
     * peeking mark plus its air. The lens grows the target further, to {@link #TOUCH_TARGET_DP},
     * but only where nothing laid over the bar claims the touch; a small target placed beside a
     * neighbour keeps out of this reach, so a tap that just misses it never pages the wall.
     */
    public static final float NEIGHBOUR_REACH_DP = ICON_DP * PEEK_SHARE / 2f + TOUCH_SLOP_DP;

    private StatusBarLensMetrics() {}

    /** A rectangle in the lens view's own coordinates. */
    public static final class Box {
        public float left;
        public float top;
        public float right;
        public float bottom;

        public Box(float left, float top, float right, float bottom) {
            set(left, top, right, bottom);
        }

        /** Refilled by {@link #marks(Bar, List, PaneWallPage, float, int, boolean, MarkBuffer)}. */
        void set(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() { return right - left; }

        public float height() { return bottom - top; }

        public boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        @NonNull @Override public String toString() {
            return "Box{" + left + "," + top + "," + right + "," + bottom + "}";
        }
    }

    /** The bar the marks are laid out in: its size, its density, and how it stands. */
    public static final class Bar {
        public final int widthPx;
        public final int heightPx;
        public final float density;
        /** The bar stands in a column, so the places queue down its height. */
        public final boolean vertical;
        /** The bar stands along the bottom, so the clock's line is at its foot. */
        public final boolean bottom;
        /** 0 in the compact bar, 1 in the expanded one, in between while it folds. */
        public final float expansion;
        /** What a column's surface reaches past its content at each end: the system bars. */
        public final int alongStartPx;
        public final int alongEndPx;
        /** Where the clock puts the home mark, and how tall it lets it be; negative for unset. */
        public final float homeCenterYPx;
        public final float homeSizePx;
        /** The corner the bar's chips wear; negative when the bar has not said. */
        public final float chipRadiusPx;

        public Bar(int widthPx, int heightPx, float density, boolean vertical, boolean bottom,
                   float expansion, int alongStartPx, int alongEndPx,
                   float homeCenterYPx, float homeSizePx, float chipRadiusPx) {
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.density = density;
            this.vertical = vertical;
            this.bottom = bottom;
            this.expansion = Math.max(0f, Math.min(1f, expansion));
            this.alongStartPx = Math.max(0, alongStartPx);
            this.alongEndPx = Math.max(0, alongEndPx);
            this.homeCenterYPx = homeCenterYPx;
            this.homeSizePx = homeSizePx;
            this.chipRadiusPx = chipRadiusPx;
        }

        /** A plain row or column at rest in one of the two forms the bar persists in. */
        public static Bar of(int widthPx, int heightPx, float density, boolean vertical,
                             @NonNull TopStatusBarState state) {
            return new Bar(widthPx, heightPx, density, vertical, false, expansionOf(state),
                0, 0, -1f, -1f, -1f);
        }

        /**
         * Whether {@link #Bar(int, int, float, boolean, boolean, float, int, int, float, float, float)}
         * would build this very bar from these arguments, so a view that redraws per frame can keep
         * the one it has.
         */
        boolean sameAs(int widthPx, int heightPx, float density, boolean vertical, boolean bottom,
                       float expansion, int alongStartPx, int alongEndPx, float homeCenterYPx,
                       float homeSizePx, float chipRadiusPx) {
            return this.widthPx == widthPx && this.heightPx == heightPx
                && this.density == density && this.vertical == vertical && this.bottom == bottom
                && this.expansion == Math.max(0f, Math.min(1f, expansion))
                && this.alongStartPx == Math.max(0, alongStartPx)
                && this.alongEndPx == Math.max(0, alongEndPx)
                && this.homeCenterYPx == homeCenterYPx && this.homeSizePx == homeSizePx
                && this.chipRadiusPx == chipRadiusPx;
        }

        float dp(float value) { return value * density; }
    }

    /** How far the bar is unfolded in each of the two forms it rests in. */
    public static float expansionOf(@NonNull TopStatusBarState state) {
        return state == TopStatusBarState.COMPACT ? 0f : 1f;
    }

    /** One place's mark: where it is, how it is painted, and what a finger may land on. */
    public static final class Mark {
        public PaneWallPage page;
        /** Bar widths from the place on screen: 0 at home, ±1 at the ends. */
        public float t;
        /** How far from home the mark is, 0 at home and 1 at either end. */
        public float presence;
        /** Whether this is the place on screen. */
        public boolean home;
        public float centerX;
        public float centerY;
        public float sizePx;
        public float radiusPx;
        public float glyphSizePx;
        /**
         * The strength the mark's glyph carries: full at home, faded to
         * {@link #UNFOCUSED_GLYPH_SHARE} as the mark reaches an end, the way the extra-keys row
         * fades the place switches behind the one in front. The tile's fill and line keep
         * {@link #ink}; the glyph is the mark's colour statement, so it is the one that follows the
         * row.
         */
        public float glyphInk;
        /** The ink the mark is painted with, before the dissolve. */
        public float ink;
        /** The ink that reaches the glass where the glyph is drawn, dissolve included. */
        public float effectiveInk;
        /** How much of its ink the mark keeps at its outer edge; 1 when it does not dissolve. */
        public float fadeOuterAlpha;
        /** The glow the mark at home wears; 0 for a neighbour. */
        public float glow;
        /** The mark dissolves towards the end it peeks past, and which end that is. */
        public boolean fades;
        public boolean fadesFromNearEnd;
        public final Box tile;
        /** The patch of bar that reaches this place, at least the platform's minimum. */
        public final Box target;

        /** The mark's two boxes, owned for its whole life and refilled with it. */
        Mark() {
            tile = new Box(0f, 0f, 0f, 0f);
            target = new Box(0f, 0f, 0f, 0f);
        }

        void set(PaneWallPage page, float t, float presence, boolean home, float centerX,
                 float centerY, float sizePx, float radiusPx, float glyphSizePx, float glyphInk,
                 float ink, float effectiveInk, float fadeOuterAlpha, float glow, boolean fades,
                 boolean fadesFromNearEnd) {
            this.page = page;
            this.t = t;
            this.presence = presence;
            this.home = home;
            this.centerX = centerX;
            this.centerY = centerY;
            this.sizePx = sizePx;
            this.radiusPx = radiusPx;
            this.glyphSizePx = glyphSizePx;
            this.glyphInk = glyphInk;
            this.ink = ink;
            this.effectiveInk = effectiveInk;
            this.fadeOuterAlpha = fadeOuterAlpha;
            this.glow = glow;
            this.fades = fades;
            this.fadesFromNearEnd = fadesFromNearEnd;
        }
    }

    /**
     * Where {@link #marks(Bar, List, PaneWallPage, float, int, boolean, MarkBuffer)} writes: the
     * marks of the last call, refilled in place by the next one. The lens view holds one so a wall
     * drag, which lays the marks out on every frame, allocates nothing doing it. A list read from
     * it is good until the buffer's next use.
     */
    public static final class MarkBuffer {
        private final ArrayList<Mark> mPool = new ArrayList<>();
        private final ArrayList<Mark> mMarks = new ArrayList<>();
        private final List<Mark> mView = Collections.unmodifiableList(mMarks);
        final float[] mAlongSpan = new float[2];
        final float[] mAcrossSpan = new float[2];

        void clear() {
            mMarks.clear();
        }

        @NonNull
        Mark next() {
            int index = mMarks.size();
            if (index == mPool.size()) mPool.add(new Mark());
            Mark mark = mPool.get(index);
            mMarks.add(mark);
            return mark;
        }

        @NonNull
        List<Mark> marks() {
            return mView;
        }
    }

    /**
     * Every mark the bar shows: none. With the status bar on both halves every place mark went
     * (decided 2026-10-21, #90): the house, the Workspace's prompt beside the clock and the
     * Display's. The bar is the clock, the readings and the window chips; the swipe and the home
     * press are the ways between the places. Kept as the lens's one question so its view paints
     * and hit-tests nothing without a second rule of its own.
     */
    @NonNull
    public static List<Mark> marks(@NonNull Bar bar, @NonNull List<PaneWallPage> pages,
                                   @NonNull PaneWallPage current, float offsetPx,
                                   int wallWidthPx) {
        return marks(bar, pages, current, offsetPx, wallWidthPx, new MarkBuffer());
    }

    /** {@link #marks(Bar, List, PaneWallPage, float, int)}, written into {@code buffer}. */
    @NonNull
    public static List<Mark> marks(@NonNull Bar bar, @NonNull List<PaneWallPage> pages,
                                   @NonNull PaneWallPage current, float offsetPx, int wallWidthPx,
                                   @NonNull MarkBuffer buffer) {
        buffer.clear();
        return buffer.marks();
    }

    /**
     * How much ink a mark carries at this distance from home. The place on screen is painted in
     * full, and a neighbour gives up {@link #PEEK_INK_DRAIN} of that.
     */
    public static float inkFor(float presence) {
        return lerp(1f, 1f - PEEK_INK_DRAIN, Math.max(0f, Math.min(1f, presence)));
    }

    /**
     * How bright a mark's glyph is at this distance from home, before the travel alpha: the place
     * in front at full strength, fading to {@link #UNFOCUSED_GLYPH_SHARE} as it reaches an end.
     * Which is the row's rule for its place switches, applied to the mark as it travels.
     */
    public static float glyphInkFor(float presence) {
        return lerp(1f, UNFOCUSED_GLYPH_SHARE, Math.max(0f, Math.min(1f, presence)));
    }

    /** The smallest target the bar has room for on an axis it spans {@code extent} of. */
    public static float minTargetPx(float density, float extent) {
        return Math.min(TOUCH_TARGET_DP * density, Math.max(0f, extent));
    }

    /**
     * What the row keeps clear at its start, from the bar's end, for a mark beside the clock:
     * nothing, since the bar wears none (#90). The gap it held is closed up.
     */
    public static int leadingCellWidthPx(float density) {
        return 0;
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
