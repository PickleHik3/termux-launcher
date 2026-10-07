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
 * drawn, halving what was left. A fully peeked mark reached the glass at 31% of full ink, and a
 * stopped Display at 19%. The drain is now 15%, the dissolve keeps half its ink at the mark's outer
 * edge, and {@link #PEEK_INK_FLOOR} catches the stopped Display before it falls through — so
 * {@link Mark#effectiveInk} stands at or above {@link #EFFECTIVE_INK_FLOOR} for every peeking mark,
 * running or not. The hierarchy that says which place you are on is carried where it costs no
 * legibility: the neighbour keeps the place's own colour and is faded in it, and only the mark at
 * home wears a glow.
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
    /** What a Display that is not running keeps of its ink. */
    public static final float STOPPED_DISPLAY_INK = 0.6f;
    /** No peeking mark carries less ink than this, whatever else has dimmed it. */
    public static final float PEEK_INK_FLOOR = 0.7f;
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
     * Every mark the bar shows, in the order the places sit side by side. A place that has left —
     * dissolved past the end it was travelling towards — is simply absent, and nothing on it can be
     * drawn or tapped. A wall of one place — terminal mode — shows no marks at all: there is
     * nowhere to go, so a mark would be a pager for nothing.
     */
    @NonNull
    public static List<Mark> marks(@NonNull Bar bar, @NonNull List<PaneWallPage> pages,
                                   @NonNull PaneWallPage current, float offsetPx, int wallWidthPx,
                                   boolean displayRunning) {
        return marks(bar, pages, current, offsetPx, wallWidthPx, displayRunning, new MarkBuffer());
    }

    /** {@link #marks(Bar, List, PaneWallPage, float, int, boolean)}, written into {@code buffer}. */
    @NonNull
    public static List<Mark> marks(@NonNull Bar bar, @NonNull List<PaneWallPage> pages,
                                   @NonNull PaneWallPage current, float offsetPx, int wallWidthPx,
                                   boolean displayRunning, @NonNull MarkBuffer buffer) {
        buffer.clear();
        if (bar.widthPx <= 0 || bar.heightPx <= 0 || wallWidthPx <= 0 || pages.size() < 2) {
            return buffer.marks();
        }
        // The two axes the lens is laid out in: the places queue along the bar's length, and the
        // line they share runs across it. On a row that is x and y; on a column it is y and x.
        float alongStart = bar.vertical ? bar.alongStartPx : 0f;
        float along = (bar.vertical ? bar.heightPx : bar.widthPx) - alongStart
            - (bar.vertical ? bar.alongEndPx : 0f);
        float across = bar.vertical ? bar.widthPx : bar.heightPx;
        if (along <= 0f) return buffer.marks();

        // In the expanded bar the home mark takes the clock's band and its line; its neighbours
        // share the line, smaller, half past the ends. The compact row is the place's own content
        // and carries no home mark: there the neighbours alone peek in, and a mark fades as it
        // arrives at home, so the row is never crowded.
        float expandedHome = bar.homeSizePx > 0f
            ? Math.min(bar.dp(ICON_DP), bar.homeSizePx) : bar.dp(ICON_DP);
        float slotMiddle = bar.bottom
            ? across - bar.dp(SLOT_HEIGHT_DP) / 2f : bar.dp(SLOT_HEIGHT_DP) / 2f;
        float expandedLine = bar.homeCenterYPx >= 0f && !bar.vertical ? bar.homeCenterYPx : slotMiddle;
        float homeSize = lerp(bar.dp(COMPACT_ICON_DP), expandedHome, bar.expansion);
        float peekSize = lerp(bar.dp(COMPACT_ICON_DP), expandedHome * PEEK_SHARE, bar.expansion);
        // A column's line is simply its middle: there is no clock band beside the marks there.
        float line = bar.vertical ? across / 2f : lerp(across / 2f, expandedLine, bar.expansion);
        float homeX = bar.dp(HOME_X_DP);
        float slop = bar.dp(TOUCH_SLOP_DP);
        float minTarget = bar.dp(TOUCH_TARGET_DP);

        for (int index = 0; index < pages.size(); index++) {
            PaneWallPage page = pages.get(index);
            float t = StatusBarLensPolicy.distance(pages, current, page, offsetPx, wallWidthPx);
            float presence = StatusBarLensPolicy.presence(t);
            // Whole at home only while the bar is expanded; in the compact row the mark at home is
            // not there, so an arriving mark dissolves over its last stretch.
            float alpha = StatusBarLensPolicy.alpha(t) * lerp(presence, 1f, bar.expansion);
            if (alpha <= VANISHED_ALPHA) continue;
            // A mark on its way between home and an end takes the size of the end it is nearer, so
            // the arriving one grows as it takes the home spot and the leaving one shrinks.
            float size = lerp(homeSize, peekSize, presence) * StatusBarLensPolicy.scale(t);
            float nearPeek = -size / 2f;
            float farPeek = along - size / 2f;
            float travelled = alongStart
                + StatusBarLensPolicy.iconX(t, homeX, nearPeek, farPeek, size) + size / 2f;
            float centerX = bar.vertical ? line : travelled;
            float centerY = bar.vertical ? travelled : line;

            boolean stoppedDisplay = page == PaneWallPage.DISPLAY && !displayRunning;
            float ink = alpha * inkFor(presence, stoppedDisplay);
            boolean fades = presence > 0.05f && t != 0f;
            float fadeOuter = fades ? 1f - FADE_DEPTH * presence : 1f;
            // The glyph is drawn at the mark's middle, which is halfway along the dissolve: that is
            // the ink a finger's owner actually has to read.
            float effectiveInk = ink * (fadeOuter + 1f) / 2f;
            float glow = alpha * (1f - presence) * bar.expansion;
            float radius = bar.chipRadiusPx >= 0f
                ? Math.min(size / 2f, bar.chipRadiusPx * (size / bar.dp(COMPACT_ICON_DP)))
                : size * FALLBACK_RADIUS_SHARE;

            Mark mark = buffer.next();
            mark.tile.set(centerX - size / 2f, centerY - size / 2f,
                centerX + size / 2f, centerY + size / 2f);
            float[] alongSpan = span(travelled, size / 2f + slop, minTarget,
                alongStart, alongStart + along, buffer.mAlongSpan);
            float[] acrossSpan = span(line, size / 2f + slop, minTarget, 0f, across,
                buffer.mAcrossSpan);
            if (bar.vertical) {
                mark.target.set(acrossSpan[0], alongSpan[0], acrossSpan[1], alongSpan[1]);
            } else {
                mark.target.set(alongSpan[0], acrossSpan[0], alongSpan[1], acrossSpan[1]);
            }

            mark.set(page, t, presence, page == current, centerX, centerY, size, radius,
                size * GLYPH_SHARE, alpha * glyphInkFor(presence, stoppedDisplay), ink,
                effectiveInk, fadeOuter, glow, fades, t < 0f);
        }
        return buffer.marks();
    }

    /**
     * How much ink a mark carries at this distance from home. The place on screen is painted in
     * full — or at {@link #STOPPED_DISPLAY_INK} when it is a Display with nothing running, which is
     * what it has always been — and a neighbour gives up {@link #PEEK_INK_DRAIN} of that, but never
     * falls below {@link #PEEK_INK_FLOOR}. The floor is what a stopped Display peeking past the
     * bar's end lands on, and the only reason it exists.
     */
    public static float inkFor(float presence, boolean stoppedDisplay) {
        float atHome = stoppedDisplay ? STOPPED_DISPLAY_INK : 1f;
        float peeking = Math.max(PEEK_INK_FLOOR, (1f - PEEK_INK_DRAIN) * atHome);
        return lerp(atHome, peeking, Math.max(0f, Math.min(1f, presence)));
    }

    /**
     * How bright a mark's glyph is at this distance from home, before the travel alpha: the place
     * in front at full strength — or at {@link #STOPPED_DISPLAY_INK} when it is a Display with
     * nothing running — fading to {@link #UNFOCUSED_GLYPH_SHARE} of that as it reaches an end.
     * Which is the row's rule for its place switches, applied to the mark as it travels.
     */
    public static float glyphInkFor(float presence, boolean stoppedDisplay) {
        float atHome = stoppedDisplay ? STOPPED_DISPLAY_INK : 1f;
        return atHome * lerp(1f, UNFOCUSED_GLYPH_SHARE, Math.max(0f, Math.min(1f, presence)));
    }

    /** The smallest target the bar has room for on an axis it spans {@code extent} of. */
    public static float minTargetPx(float density, float extent) {
        return Math.min(TOUCH_TARGET_DP * density, Math.max(0f, extent));
    }

    /**
     * One axis of a target: the mark plus its air, kept inside the bar, and then grown until it is
     * at least {@code minLength} long. Growth goes the way there is room — inward from an end for
     * the two neighbours, whose outer halves are past the bar and cannot be touched at all.
     */
    private static float[] span(float center, float half, float minLength, float lo, float hi,
                                float[] out) {
        float low = Math.max(lo, center - half);
        float high = Math.min(hi, center + half);
        float need = Math.min(minLength, hi - lo);
        if (high - low >= need) return pair(out, low, high);
        if (low <= lo) return pair(out, lo, Math.min(hi, lo + need));
        if (high >= hi) return pair(out, Math.max(lo, hi - need), hi);
        float extra = (need - (high - low)) / 2f;
        float grown = Math.max(lo, low - extra);
        return pair(out, grown, Math.min(hi, grown + need));
    }

    private static float[] pair(float[] out, float first, float second) {
        out[0] = first;
        out[1] = second;
        return out;
    }

    /** What the row keeps clear at its start, from the bar's end: the home mark and its gap. */
    public static int leadingCellWidthPx(float density) {
        return Math.round((HOME_X_DP + ICON_DP + ICON_GAP_DP) * density);
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
