package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;
import com.termux.app.terminal.PaneShape;

import java.util.List;

/**
 * Where the Clock's Flip face puts its glass tiles (the developer's "C1 · Glass tiles clock",
 * drawn for a 4-column widget with 366×154 dp of room): an hours tile and a minutes tile of
 * equal width, each showing its pair of digits across a seam with a notch at each end, and at
 * four columns with Seconds on a narrow third tile, the period over the seconds.
 *
 * <p>The rules where the design is silent are this plan's: 24-hour time puts the seconds alone
 * on the third tile, centred on its seam; with Seconds off, or at two columns, there are two
 * tiles, a 12-hour period standing small in the hours tile's top corner; the 1×1 is one tile,
 * the hours over the seam and the minutes under it. Every length is the design's at its 154 dp
 * height, times the widget's {@link #scale}; the digits are a share of their tile's height,
 * held to its width.</p>
 *
 * <p>{@link #room} cuts the card: the tiles take what the date, the alarm and the zones leave,
 * and those stand on tiles of their own, one under the face and one beside it. Android-free.</p>
 */
final class ClockTilesPlan {
    /** The design's tile height, which every other length below is drawn against. */
    static final float DESIGN_HEIGHT_DP = 154f;
    /** The space between two tiles. */
    static final float GAP_DP = 10f;
    /** The third tile's width (where its seconds and period fit in it; else as wide as they need). */
    static final float SIDE_WIDTH_DP = 60f;
    /** A pair of digits' size, as a share of its tile's height (100 px on the 154). */
    static final float DIGIT_SHARE = 100f / DESIGN_HEIGHT_DP;
    /** The 1×1's pairs, each in its own half: a share of the half's height. */
    static final float STACK_DIGIT_SHARE = .85f;
    /** The period's size (18 px on the 154), and the third tile's seconds (30 px). */
    static final float PERIOD_SHARE = 18f / DESIGN_HEIGHT_DP;
    static final float SECONDS_SHARE = 30f / DESIGN_HEIGHT_DP;
    /** The seconds' ink, as a share of the digits' (82%). */
    static final float SECONDS_INK = .82f;
    /** A pair tile's notches at the ends of its seam, and the third tile's smaller ones. */
    static final float NOTCH_WIDTH_DP = 6f;
    static final float NOTCH_HEIGHT_DP = 12f;
    static final float SIDE_NOTCH_WIDTH_DP = 4f;
    static final float SIDE_NOTCH_HEIGHT_DP = 8f;
    /** Each of the seam's two hairlines, a dark one over a light one. */
    static final float SEAM_LINE_DP = 1f;
    /** The digits keep this many notch widths clear of each side of their tile. */
    static final float SIDE_AIR_NOTCHES = 2f;
    /** The corner period's inset from the tile's top and leading edges, at least. */
    static final float PERIOD_PAD_DP = 12f;
    /** A corner period may shrink its digits to no less than this share of their size alone. */
    static final float PERIOD_KEEP = ClockFaceView.META_KEEP;

    /** What a tile shows. */
    enum Shows {
        /** The hour's two digits, centred across the seam. */
        HOURS,
        /** The minute's two digits. */
        MINUTES,
        /** The third tile: the period over the seconds (12-hour), or the seconds alone. */
        SIDE,
        /** The 1×1's one tile: the hours over the seam, the minutes under it. */
        STACK
    }

    /** The type the tiles are sized by; px in, px out. */
    interface Measure {
        /** Two tabular digits' advance at {@code sizePx}. */
        float pairWidthPx(float sizePx);
        /** How tall a digit's ink stands at {@code sizePx}. */
        float figureHeightPx(float sizePx);
        /** The wider of the AM and PM labels as the period draws them, at {@code sizePx}. */
        float periodWidthPx(float sizePx);
    }

    /** One tile, in px of the face's box. */
    static final class Tile {
        @NonNull final Shows shows;
        final float left, top, right, bottom;
        /** The tile's corner: the Look's, held to its size. */
        final float radius;
        final float notchWidth, notchHeight;
        /** The size its pair (or pairs) of digits are drawn at; 0 on the third tile. */
        final float digitPx;

        Tile(@NonNull Shows shows, float left, float top, float right, float bottom, float radius,
             float notchWidth, float notchHeight, float digitPx) {
            this.shows = shows;
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.radius = radius;
            this.notchWidth = notchWidth; this.notchHeight = notchHeight;
            this.digitPx = digitPx;
        }

        float width() { return right - left; }
        float height() { return bottom - top; }
        float centerX() { return (left + right) / 2f; }
        /** The seam's line, across the tile's middle. */
        float seamY() { return (top + bottom) / 2f; }
    }

    @NonNull final Tile[] tiles;
    /** The seconds, on the third tile; false where there is none. */
    final boolean seconds;
    final float secondsPx;
    /** Where the seconds' ink is centred, on the third tile. */
    final float secondsCenterY;
    /** The 12-hour period: on the third tile, in the hours tile's corner, or not at all. */
    final boolean periodOnSide;
    final boolean periodInCorner;
    final float periodPx;
    /** The period's ink centre on the third tile, or its ink's top-leading corner in the corner. */
    final float periodX, periodY;

    private ClockTilesPlan(@NonNull Tile[] tiles, boolean seconds, float secondsPx,
                           float secondsCenterY, boolean periodOnSide, boolean periodInCorner,
                           float periodPx, float periodX, float periodY) {
        this.tiles = tiles;
        this.seconds = seconds;
        this.secondsPx = secondsPx;
        this.secondsCenterY = secondsCenterY;
        this.periodOnSide = periodOnSide;
        this.periodInCorner = periodInCorner;
        this.periodPx = periodPx;
        this.periodX = periodX;
        this.periodY = periodY;
    }

    /** The widget's scale against the design: its card's height over the design's, never above 1. */
    static float scale(float cardHeightDp) {
        return Math.max(0f, Math.min(1f, cardHeightDp / DESIGN_HEIGHT_DP));
    }

    /** Whether the face shows seconds at {@code span}: only the four-column third tile does. */
    static boolean showsSeconds(@NonNull BuiltinWidgetSpan span, boolean seconds) {
        return seconds && span.isWide();
    }

    /**
     * The tiles in a {@code widthPx}×{@code heightPx} box.
     *
     * @param scale the widget's {@link #scale}
     * @param labelPx the Label role's size, the least a period or the seconds is drawn at
     * @param lookRadiusPx the Look's corner, which each tile wears held to its own size
     */
    @NonNull static ClockTilesPlan plan(float widthPx, float heightPx, float density, float scale,
                                        float labelPx, float lookRadiusPx,
                                        @NonNull BuiltinWidgetSpan span, boolean is24,
                                        boolean seconds, @NonNull Measure measure) {
        float w = Math.max(0f, widthPx), h = Math.max(0f, heightPx);
        float unit = scale * density;
        float notchW = NOTCH_WIDTH_DP * unit, notchH = NOTCH_HEIGHT_DP * unit;
        float pairPerPx = measure.pairWidthPx(100f) / 100f;
        float periodPx = Math.max(labelPx, PERIOD_SHARE * h);
        if (span == BuiltinWidgetSpan.ONE_BY_ONE) {
            float radius = radius(lookRadiusPx, w, h);
            float digit = fitWidth(STACK_DIGIT_SHARE * h / 2f, w, notchW, pairPerPx);
            Corner corner = is24 ? null : corner(0f, 0f, w, h / 4f, digit, radius, unit, periodPx,
                pairPerPx, measure);
            if (corner != null) digit = corner.digitPx;
            Tile tile = new Tile(Shows.STACK, 0f, 0f, w, h, radius, notchW, notchH, digit);
            return withCorner(new Tile[] {tile}, corner, periodPx);
        }
        float gap = GAP_DP * unit;
        boolean side = showsSeconds(span, seconds);
        if (!side) {
            float pairW = Math.max(0f, (w - gap) / 2f);
            float radius = radius(lookRadiusPx, pairW, h);
            float digit = fitWidth(DIGIT_SHARE * h, pairW, notchW, pairPerPx);
            Corner corner = is24 ? null : corner(0f, 0f, pairW, h / 2f, digit, radius, unit,
                periodPx, pairPerPx, measure);
            if (corner != null) digit = corner.digitPx;
            Tile hours = new Tile(Shows.HOURS, 0f, 0f, pairW, h, radius, notchW, notchH, digit);
            Tile minutes = new Tile(Shows.MINUTES, w - pairW, 0f, w, h, radius, notchW, notchH,
                digit);
            return withCorner(new Tile[] {hours, minutes}, corner, periodPx);
        }
        float sideNotchW = SIDE_NOTCH_WIDTH_DP * unit, sideNotchH = SIDE_NOTCH_HEIGHT_DP * unit;
        float secondsPx = Math.max(labelPx, SECONDS_SHARE * h);
        float content = Math.max(measure.pairWidthPx(secondsPx),
            is24 ? 0f : measure.periodWidthPx(periodPx));
        float sideW = Math.min(w, Math.max(SIDE_WIDTH_DP * unit,
            content + 2f * SIDE_AIR_NOTCHES * sideNotchW));
        float pairW = Math.max(0f, (w - sideW - 2f * gap) / 2f);
        float radius = radius(lookRadiusPx, pairW, h);
        float digit = fitWidth(DIGIT_SHARE * h, pairW, notchW, pairPerPx);
        Tile hours = new Tile(Shows.HOURS, 0f, 0f, pairW, h, radius, notchW, notchH, digit);
        Tile minutes = new Tile(Shows.MINUTES, pairW + gap, 0f, 2f * pairW + gap, h, radius,
            notchW, notchH, digit);
        Tile third = new Tile(Shows.SIDE, w - sideW, 0f, w, h, radius(lookRadiusPx, sideW, h),
            sideNotchW, sideNotchH, 0f);
        float secondsY = is24 ? third.seamY() : h * 3f / 4f;
        return new ClockTilesPlan(new Tile[] {hours, minutes, third}, true, secondsPx, secondsY,
            !is24, false, periodPx, third.centerX(), h / 4f);
    }

    /** The Look's corner held to a {@code w}×{@code h} tile, as a pane's is. */
    private static float radius(float lookRadiusPx, float w, float h) {
        return PaneShape.radiusForBounds(lookRadiusPx, Math.round(w), Math.round(h));
    }

    /** {@code wanted}, or smaller where a pair at it would not clear the tile's sides. */
    private static float fitWidth(float wanted, float tileW, float notchW, float pairPerPx) {
        float room = Math.max(0f, tileW - 2f * SIDE_AIR_NOTCHES * notchW);
        return pairPerPx <= 0f ? wanted : Math.max(0f, Math.min(wanted, room / pairPerPx));
    }

    /** A 12-hour period in a tile's top corner, and the digits' size that clears it. */
    private static final class Corner {
        final float x, y, digitPx;
        Corner(float x, float y, float digitPx) { this.x = x; this.y = y; this.digitPx = digitPx; }
    }

    /**
     * The period in the top-leading corner of the tile at ({@code left}, {@code top}),
     * {@code tileW} wide, whose hours are centred on {@code hoursCenterY}: inset by
     * {@link #PERIOD_PAD_DP} or the corner's arc, whichever is more. Where it would touch the
     * hours' ink the digits shrink to clear it, down to {@link #PERIOD_KEEP} of their size; past
     * that the period goes (null).
     */
    private static Corner corner(float left, float top, float tileW, float hoursCenterY,
                                 float digit, float radius, float unit, float periodPx,
                                 float pairPerPx, @NonNull Measure measure) {
        float pad = Math.max(PERIOD_PAD_DP * unit, PaneShape.contentInsetPx(radius));
        float x = left + pad, y = top + pad;
        float right = x + measure.periodWidthPx(periodPx);
        float bottom = y + measure.figureHeightPx(periodPx);
        if (right > left + tileW - pad) return null;
        // Clear beside the hours, or above them.
        float inkLeft = left + tileW / 2f - pairPerPx * digit / 2f;
        if (right + pad <= inkLeft) return new Corner(x, y, digit);
        float figure = measure.figureHeightPx(digit);
        if (hoursCenterY - figure / 2f >= bottom + pad) return new Corner(x, y, digit);
        float figurePerPx = digit <= 0f ? 0f : figure / digit;
        float room = 2f * (hoursCenterY - bottom - pad);
        float kept = figurePerPx <= 0f ? 0f : room / figurePerPx;
        if (kept < PERIOD_KEEP * digit) return null;
        return new Corner(x, y, kept);
    }

    @NonNull private static ClockTilesPlan withCorner(@NonNull Tile[] tiles, Corner corner,
                                                      float periodPx) {
        return new ClockTilesPlan(tiles, false, 0f, 0f, false, corner != null, periodPx,
            corner == null ? 0f : corner.x, corner == null ? 0f : corner.y);
    }

    // ----- the card -------------------------------------------------------------------------

    /**
     * Cuts a {@link ClockLayoutPlanner} plan's card into tiles: the face's room, returned, and
     * into {@code ground} a tile for the zone columns (the card's height, from the card's inset
     * left of them to its right edge) and one for the date and alarm (the face's width, from the
     * card's inset above them to its bottom edge). Each stands {@link #GAP_DP} off the next; the
     * face alone takes the whole card. In dp.
     */
    @NonNull static Box room(@NonNull WidgetLayoutPlan plan, float scale,
                             @NonNull List<Box> ground) {
        float cardW = plan.card.width, cardH = plan.card.height;
        float inset = plan.inset.left;
        float gap = GAP_DP * scale;
        float zonesLeft = Float.MAX_VALUE;
        float textsTop = Float.MAX_VALUE;
        for (WidgetLayoutPlan.Part part : plan.parts) {
            if (part.id.startsWith("zone.")) zonesLeft = Math.min(zonesLeft, part.box.left);
            else if (isUnder(part.id)) textsTop = Math.min(textsTop, part.box.top);
        }
        float right = cardW;
        if (zonesLeft != Float.MAX_VALUE) {
            float left = Math.max(0f, zonesLeft - inset);
            ground.add(new Box(left, 0f, cardW - left, cardH));
            right = Math.max(0f, left - gap);
        }
        float bottom = cardH;
        if (textsTop != Float.MAX_VALUE) {
            float top = Math.max(0f, textsTop - inset);
            ground.add(new Box(0f, top, right, cardH - top));
            bottom = Math.max(0f, top - gap);
        }
        return new Box(0f, 0f, right, bottom);
    }

    private static boolean isUnder(@NonNull String id) {
        return id.equals(ClockLayoutPlanner.DATE) || id.equals(ClockLayoutPlanner.ALARM)
            || id.equals(ClockLayoutPlanner.ALARM_GLYPH);
    }
}
