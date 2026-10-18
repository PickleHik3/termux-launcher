package com.termux.app.statusbar;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.terminal.FloatTitlebarPolicy;
import com.termux.app.terminal.ProcessGlyphs;
import com.termux.app.terminal.WindowChipInk;

import java.util.Arrays;

/**
 * The status bar's look on a Home page: two pills on the bar's own glass instead of the bar.
 *
 * <ul>
 *   <li><b>The windows pill</b>, at the start: one section per Home float, in the order the
 *       floats were opened. A section shows the window's glyph (the one its chip wears); the
 *       focused float's section also shows its name. It is there while a Home float exists.</li>
 *   <li><b>The stats pill</b>, at the end: CPU, RAM and the weather, whichever the status bar's
 *       settings show. No clock, no battery, no notification dots on Home.</li>
 * </ul>
 *
 * <p>The pills stand in the bar's band on a top or bottom edge. A bar down a side keeps its
 * column of chips on Home ({@link #shown}). When both pills cannot fit, the windows pill wins and
 * the stats pill steps aside; the focused name gives way first, and goes entirely below
 * {@link #MIN_NAME_DP}.
 *
 * <p>Pure: no views. The view measures text, hands the widths in, and draws the answer.
 */
public final class HomePillsPolicy {

    private HomePillsPolicy() {}

    /** The tallest a pill gets, and its least air from the band's edges. */
    public static final float PILL_MAX_HEIGHT_DP = 32f;
    public static final float PILL_BAND_INSET_DP = 2f;
    /** The air between the two pills, at the least. */
    public static final float PILL_GAP_DP = 8f;
    /** A section's air on each side of its glyph, and between the glyph and the focused name. */
    public static final float SECTION_PADDING_DP = 10f;
    public static final float NAME_GAP_DP = 6f;
    /** A name narrower than this is not worth drawing: the section shows its glyph alone. */
    public static final float MIN_NAME_DP = 24f;
    /** The stats pill's air at its ends, between two readings, and between a glyph and its value. */
    public static final float STATS_PADDING_DP = 12f;
    public static final float STATS_ITEM_GAP_DP = 10f;
    public static final float STATS_GLYPH_GAP_DP = 4f;
    /** How long the windows pill takes to move its sections when the focus moves. Once, then still. */
    public static final long FOCUS_MOVE_MS = 180L;
    /** How strong the outline is on the plain panel, out of 255: a resting chip's idle rim. */
    public static final int PLAIN_STROKE_ALPHA = 70;

    /** Whether a Home page wears the pills: on Home, with the bar along the top or the bottom. */
    public static boolean shown(boolean onHome, @NonNull Edge edge) {
        return onHome && !StatusBarGesturePolicy.isVertical(edge);
    }

    /**
     * The band the pills stand in on Home, in dp: the compact bar's row on the style Home wears
     * ({@code floating} for Floating). The live bar claims exactly this band on its edge, which
     * the widget grid gives its room to, and the Layout editor's Home miniature draws the same
     * band above its grid; the pills inside it are {@link #PILL_MAX_HEIGHT_DP} at most.
     */
    public static float bandDp(boolean floating) {
        return StatusBarEdgeGeometry.thicknessDp(Edge.TOP, floating, true);
    }

    // ---------------------------------------------------------------- sections

    /** One window's section: the glyph its chip wears, its name and what is spoken for it. */
    public static final class Section {
        @NonNull public final String glyph;
        @NonNull public final String name;
        @NonNull public final String spoken;

        public Section(@NonNull String glyph, @NonNull String name, @NonNull String spoken) {
            this.glyph = glyph;
            this.name = name;
            this.spoken = spoken;
        }

        /**
         * A section from a window chip's label: the leading process glyph (one private-use code
         * point and a space, as the window bar's factories write it), or the default terminal
         * glyph for a label without one, and the rest as the name.
         */
        @NonNull
        public static Section of(@NonNull String label, @NonNull String spoken) {
            int end = leadingGlyphEnd(label);
            if (end == 0) return new Section(ProcessGlyphs.DEFAULT, label.trim(), spoken);
            String name = end < label.length() ? label.substring(end + 1).trim() : "";
            return new Section(label.substring(0, end), name, spoken);
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Section)) return false;
            Section that = (Section) other;
            return glyph.equals(that.glyph) && name.equals(that.name) && spoken.equals(that.spoken);
        }

        @Override public int hashCode() {
            return (glyph.hashCode() * 31 + name.hashCode()) * 31 + spoken.hashCode();
        }

        @NonNull @Override public String toString() {
            return "Section{" + glyph + " " + name + "}";
        }
    }

    /** The length of the leading private-use glyph a chip label starts with, or 0. */
    static int leadingGlyphEnd(@NonNull String label) {
        if (label.isEmpty()) return 0;
        int codePoint = label.codePointAt(0);
        if (Character.getType(codePoint) != Character.PRIVATE_USE) return 0;
        int end = Character.charCount(codePoint);
        if (end == label.length()) return end;
        return label.charAt(end) == ' ' ? end : 0;
    }

    // ---------------------------------------------------------------- geometry

    /** Where the pills stand in the band, in the band's px. */
    public static final class Layout {
        public final int pillTop;
        public final int pillBottom;
        public final float radius;
        public final boolean windowsShown;
        public final float windowsLeft;
        public final float windowsRight;
        /** Each section's left and right edges, in order; empty without floats. */
        @NonNull public final float[] sectionLeft;
        @NonNull public final float[] sectionRight;
        /** The focused section, or -1. */
        public final int focused;
        /** The room the focused name has; 0 draws no name. */
        public final float nameRoom;
        public final boolean statsShown;
        public final float statsLeft;
        public final float statsRight;

        Layout(int pillTop, int pillBottom, float radius, boolean windowsShown, float windowsLeft,
               float windowsRight, @NonNull float[] sectionLeft, @NonNull float[] sectionRight,
               int focused, float nameRoom, boolean statsShown, float statsLeft,
               float statsRight) {
            this.pillTop = pillTop;
            this.pillBottom = pillBottom;
            this.radius = radius;
            this.windowsShown = windowsShown;
            this.windowsLeft = windowsLeft;
            this.windowsRight = windowsRight;
            this.sectionLeft = sectionLeft;
            this.sectionRight = sectionRight;
            this.focused = focused;
            this.nameRoom = nameRoom;
            this.statsShown = statsShown;
            this.statsLeft = statsLeft;
            this.statsRight = statsRight;
        }

        public int sectionCount() { return sectionLeft.length; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Layout)) return false;
            Layout that = (Layout) other;
            return pillTop == that.pillTop && pillBottom == that.pillBottom
                && radius == that.radius && windowsShown == that.windowsShown
                && windowsLeft == that.windowsLeft && windowsRight == that.windowsRight
                && Arrays.equals(sectionLeft, that.sectionLeft)
                && Arrays.equals(sectionRight, that.sectionRight) && focused == that.focused
                && nameRoom == that.nameRoom && statsShown == that.statsShown
                && statsLeft == that.statsLeft && statsRight == that.statsRight;
        }

        @Override public int hashCode() {
            return Arrays.hashCode(sectionRight) * 31 + focused;
        }

        @NonNull @Override public String toString() {
            return "Layout{windows=" + windowsShown + " " + windowsLeft + ".." + windowsRight
                + ", sections=" + Arrays.toString(sectionRight) + ", focused=" + focused
                + ", name=" + nameRoom + ", stats=" + statsShown + " " + statsLeft + ".."
                + statsRight + "}";
        }
    }

    /** No pills: nothing to draw and nothing to touch. */
    public static final Layout EMPTY = new Layout(0, 0, 0f, false, 0f, 0f, new float[0],
        new float[0], -1, 0f, false, 0f, 0f);

    /**
     * The pills in a band {@code bandWidthPx} by {@code bandHeightPx}, kept {@code insetStartPx}
     * and {@code insetEndPx} in from its ends.
     *
     * @param glyphPx       each section's glyph width, in order
     * @param focused       the focused section, or -1
     * @param namePx        the focused section's name at full width, or 0
     * @param statsPx       the stats pill's whole width at its natural size, or 0 for none
     */
    @NonNull
    public static Layout layout(float density, int bandWidthPx, int bandHeightPx, int insetStartPx,
                                int insetEndPx, @NonNull float[] glyphPx, int focused,
                                float namePx, float statsPx) {
        int pillHeight = Math.min(Math.round(PILL_MAX_HEIGHT_DP * density),
            bandHeightPx - 2 * Math.round(PILL_BAND_INSET_DP * density));
        float start = Math.max(0, insetStartPx);
        float end = bandWidthPx - Math.max(0, insetEndPx);
        float room = end - start;
        if (pillHeight <= 0 || room <= 0f) return EMPTY;
        int top = (bandHeightPx - pillHeight) / 2;
        int bottom = top + pillHeight;
        float radius = pillHeight / 2f;

        int count = glyphPx.length;
        float padding = SECTION_PADDING_DP * density;
        float[] iconOnly = new float[count];
        float iconsWidth = 0f;
        for (int i = 0; i < count; i++) {
            iconOnly[i] = 2f * padding + Math.max(0f, glyphPx[i]);
            iconsWidth += iconOnly[i];
        }
        float stats = Math.max(0f, statsPx);
        float pillGap = PILL_GAP_DP * density;

        if (count == 0) {
            boolean statsShown = stats > 0f;
            float statsLeft = Math.max(start, end - stats);
            return new Layout(top, bottom, radius, false, 0f, 0f, new float[0], new float[0], -1,
                0f, statsShown, statsShown ? statsLeft : 0f, statsShown ? end : 0f);
        }

        // The windows pill wins: the stats step aside when even its glyphs alone leave no room.
        boolean statsShown = stats > 0f && iconsWidth + pillGap + stats <= room;
        float windowsRoom = statsShown ? room - stats - pillGap : room;
        int focus = focused >= 0 && focused < count ? focused : -1;
        float nameGap = NAME_GAP_DP * density;
        float nameRoom = 0f;
        if (focus >= 0 && namePx > 0f) {
            nameRoom = Math.min(namePx, windowsRoom - iconsWidth - nameGap);
            if (nameRoom < MIN_NAME_DP * density) nameRoom = 0f;
        }
        float[] left = new float[count];
        float[] right = new float[count];
        float x = start;
        for (int i = 0; i < count; i++) {
            left[i] = x;
            x += iconOnly[i];
            if (i == focus && nameRoom > 0f) x += nameGap + nameRoom;
            right[i] = x;
        }
        // More floats than the band holds: the pill stops at the room and clips what is past it.
        float windowsRight = Math.min(x, start + windowsRoom);
        return new Layout(top, bottom, radius, true, start, windowsRight, left, right, focus,
            nameRoom, statsShown, statsShown ? end - stats : 0f, statsShown ? end : 0f);
    }

    /** The stats pill's natural width from its readings' widths (glyph and value of each). */
    public static float statsWidth(float density, @NonNull float[] glyphPx,
                                   @NonNull float[] valuePx) {
        int count = Math.min(glyphPx.length, valuePx.length);
        if (count == 0) return 0f;
        float width = 2f * STATS_PADDING_DP * density;
        for (int i = 0; i < count; i++) {
            if (i > 0) width += STATS_ITEM_GAP_DP * density;
            width += glyphPx[i] + STATS_GLYPH_GAP_DP * density + valuePx[i];
        }
        return width;
    }

    /**
     * The step between two layouts at {@code fraction} of the way, for the one move a focus change
     * makes. Only the windows pill moves, and only between layouts with the same sections; any
     * other pair lands on {@code to} at once.
     */
    @NonNull
    public static Layout between(@NonNull Layout from, @NonNull Layout to, float fraction) {
        float t = Math.max(0f, Math.min(1f, fraction));
        if (t >= 1f || from.sectionCount() != to.sectionCount() || !from.windowsShown
            || !to.windowsShown || from.pillTop != to.pillTop || from.pillBottom != to.pillBottom) {
            return to;
        }
        int count = to.sectionCount();
        float[] left = new float[count];
        float[] right = new float[count];
        for (int i = 0; i < count; i++) {
            left[i] = lerp(from.sectionLeft[i], to.sectionLeft[i], t);
            right[i] = lerp(from.sectionRight[i], to.sectionRight[i], t);
        }
        return new Layout(to.pillTop, to.pillBottom, to.radius, true,
            lerp(from.windowsLeft, to.windowsLeft, t), lerp(from.windowsRight, to.windowsRight, t),
            left, right, to.focused, to.nameRoom, to.statsShown, to.statsLeft, to.statsRight);
    }

    /** The section under {@code (x, y)}, or -1 off the windows pill. */
    public static int sectionAt(@NonNull Layout layout, float x, float y) {
        if (!layout.windowsShown || y < layout.pillTop || y >= layout.pillBottom) return -1;
        if (x < layout.windowsLeft || x >= layout.windowsRight) return -1;
        for (int i = 0; i < layout.sectionCount(); i++) {
            if (x >= layout.sectionLeft[i] && x < layout.sectionRight[i]) return i;
        }
        return -1;
    }

    /** Whether {@code (x, y)} is on either pill. Off them the band is the page's. */
    public static boolean onPill(@NonNull Layout layout, float x, float y) {
        if (y < layout.pillTop || y >= layout.pillBottom) return false;
        if (layout.windowsShown && x >= layout.windowsLeft && x < layout.windowsRight) return true;
        return layout.statsShown && x >= layout.statsLeft && x < layout.statsRight;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    // ---------------------------------------------------------------- ink

    /**
     * The pills' ink, for glyphs, names and readings alike: a Home float's titlebar rule
     * ({@link FloatTitlebarPolicy#glyphInk}, as on a focused float), so the floats and the pills
     * above them read in one ink. On glass, once the chrome's polarity is published, it is the
     * window bar's chip label ink on that polarity; on the plain panel, or before any polarity is
     * published, {@code onSurface} made legible on {@code surface}.
     */
    @ColorInt
    public static int ink(boolean glass, @Nullable ChromeInk.Polarity polarity,
                          @ColorInt int onSurface, @ColorInt int surfaceBase,
                          @ColorInt int surface) {
        return FloatTitlebarPolicy.glyphInk(glass, polarity, onSurface, surfaceBase, surface, true);
    }

    /**
     * The pills' outline: on glass with a published polarity, a resting window chip's outline (the
     * polarity's neutral walked until it clears {@link OnGlass#TARGET_LARGE_TEXT} on
     * {@code band}); on the plain panel, or unmeasured, {@code ink} at
     * {@link #PLAIN_STROKE_ALPHA}.
     */
    @ColorInt
    public static int stroke(boolean glass, @Nullable ChromeInk.Polarity polarity,
                             @ColorInt int onSurface, @ColorInt int surfaceBase,
                             @ColorInt int band, @ColorInt int ink) {
        if (glass && polarity != null) {
            boolean pale = polarity == ChromeInk.Polarity.PALE_INK;
            return WindowChipInk.towardPolarity(OnGlass.opaque(band),
                WindowChipInk.neutralSeed(onSurface, surfaceBase, pale), pale,
                OnGlass.TARGET_LARGE_TEXT);
        }
        return OnGlass.withAlpha(ink, PLAIN_STROKE_ALPHA);
    }
}
