package com.termux.app.terminal;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.OnGlass;

import java.util.Arrays;

/**
 * The chips minimised Home floats come back from (developer, 2026-10-19): one small glass chip
 * per minimised float, from every Home page, in a row centred just above the dock. A chip shows
 * the float's glyph and its short name, as its window chip does; a tap brings the float back onto
 * its page, and Minimise's flight lands on the chip's place. Home wears no status bar, so these
 * are the only way back to a minimised float on Home.
 *
 * <ul>
 *   <li>The row stands {@link #ROW_AIR_DP} above the dock's top, or above the keyboard's while a
 *       keyboard is up, so it moves with the dock. While it shows, the floats' room gives up its
 *       band ({@link #bandPx}), so no float covers a chip and no chip covers a float's last row.</li>
 *   <li>Names give way first: when the row with names does not fit the layer, every chip shows
 *       its glyph alone; when even that does not fit, the chips share the width evenly, never
 *       narrower than their glyph, and any that still do not fit are left off the end.</li>
 * </ul>
 *
 * <p>Pure: the view measures the text and hands the widths in, then draws the answer.
 */
public final class HomeFloatChipsPolicy {

    private HomeFloatChipsPolicy() {}

    /** A chip's height: a thumb's press on a glyph and a line of text. */
    public static final int CHIP_HEIGHT_DP = 28;
    /** The air between the row and the dock (or the keyboard) under it. */
    public static final int ROW_AIR_DP = 6;
    /** The air between two chips. */
    public static final int CHIP_GAP_DP = 6;
    /** The least air the row keeps from the layer's sides. */
    public static final int ROW_SIDE_DP = 8;
    /** A chip's air either side of its content, and between its glyph and its name. */
    public static final int CHIP_PADDING_DP = 10;
    public static final int NAME_GAP_DP = 5;
    /** The widest a short name gets; a longer one is ellipsized to this. */
    public static final int NAME_MAX_DP = 72;
    /** How strong a chip's outline is on the plain panel, out of 255: a resting chip's idle rim. */
    public static final int PLAIN_STROKE_ALPHA = 70;

    /**
     * What the floats' room gives up above the dock while the row shows ({@code shown}): the
     * chips' height and the air under them, in px. The room keeps Home's Margin above that, as it
     * does above the dock.
     */
    public static int bandPx(float density, boolean shown) {
        return shown ? Math.round((CHIP_HEIGHT_DP + ROW_AIR_DP) * density) : 0;
    }

    /**
     * Where the row's top stands, on screen: {@link #ROW_AIR_DP} above the edge that covers Home
     * from below, the keyboard's top while one is up ({@code keyboardTopPx} at or above 0), else
     * the dock's top.
     */
    public static int rowTopPx(float density, int dockTopPx, int keyboardTopPx) {
        int edge = keyboardTopPx >= 0 ? keyboardTopPx : dockTopPx;
        return edge - Math.round((CHIP_HEIGHT_DP + ROW_AIR_DP) * density);
    }

    // ---------------------------------------------------------------- labels

    /** One chip's content: the glyph its window chip wears, its short name, and what is spoken. */
    public static final class Chip {
        @NonNull public final String glyph;
        @NonNull public final String name;
        @NonNull public final String spoken;

        public Chip(@NonNull String glyph, @NonNull String name, @NonNull String spoken) {
            this.glyph = glyph;
            this.name = name;
            this.spoken = spoken;
        }

        /**
         * A chip from a window chip's label: the leading process glyph (one private-use code point
         * and a space, as the window bar's factories write it), or the default terminal glyph for a
         * label without one, and the rest as the name.
         */
        @NonNull
        public static Chip of(@NonNull String label, @NonNull String spoken) {
            int end = leadingGlyphEnd(label);
            if (end == 0) return new Chip(ProcessGlyphs.DEFAULT, label.trim(), spoken);
            String name = end < label.length() ? label.substring(end + 1).trim() : "";
            return new Chip(label.substring(0, end), name, spoken);
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Chip)) return false;
            Chip that = (Chip) other;
            return glyph.equals(that.glyph) && name.equals(that.name) && spoken.equals(that.spoken);
        }

        @Override public int hashCode() {
            return (glyph.hashCode() * 31 + name.hashCode()) * 31 + spoken.hashCode();
        }

        @NonNull @Override public String toString() {
            return "Chip{" + glyph + " " + name + "}";
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

    // ---------------------------------------------------------------- the row

    /** Where each chip stands in the row, from the row's left, and how wide the row is. */
    public static final class Layout {
        /** Each shown chip's left and right edges; chips past {@link #shown} are not drawn. */
        @NonNull public final float[] left;
        @NonNull public final float[] right;
        /** How many chips, from the first, are shown. */
        public final int shown;
        /** Whether the chips show their names, or their glyphs alone. */
        public final boolean names;
        /** The room each name has; 0 with no names. */
        public final float nameRoom;
        public final float rowWidth;

        Layout(@NonNull float[] left, @NonNull float[] right, int shown, boolean names,
               float nameRoom, float rowWidth) {
            this.left = left;
            this.right = right;
            this.shown = shown;
            this.names = names;
            this.nameRoom = nameRoom;
            this.rowWidth = rowWidth;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Layout)) return false;
            Layout that = (Layout) other;
            return shown == that.shown && names == that.names && nameRoom == that.nameRoom
                && rowWidth == that.rowWidth && Arrays.equals(left, that.left)
                && Arrays.equals(right, that.right);
        }

        @Override public int hashCode() {
            return Arrays.hashCode(left) * 31 + shown;
        }
    }

    public static final Layout EMPTY = new Layout(new float[0], new float[0], 0, false, 0f, 0f);

    /**
     * The row for chips whose glyphs measure {@code glyphWidths} and names {@code nameWidths}, in
     * a layer {@code layerWidthPx} wide: names while every chip fits with its name (each held to
     * {@link #NAME_MAX_DP}), else glyphs alone, else the width shared evenly, never narrower than
     * a chip's glyph; chips that still do not fit are left off the end.
     */
    @NonNull
    public static Layout layout(float density, int layerWidthPx, @NonNull float[] glyphWidths,
                                @NonNull float[] nameWidths) {
        int count = glyphWidths.length;
        if (count == 0 || layerWidthPx <= 0) return EMPTY;
        float available = Math.max(0f, layerWidthPx - 2f * ROW_SIDE_DP * density);
        float padding = CHIP_PADDING_DP * density;
        float gap = CHIP_GAP_DP * density;
        float nameGap = NAME_GAP_DP * density;
        float nameMax = NAME_MAX_DP * density;
        float[] widths = new float[count];
        float total = gap * (count - 1);
        boolean anyName = false;
        for (int i = 0; i < count; i++) {
            float name = i < nameWidths.length ? Math.min(nameMax, nameWidths[i]) : 0f;
            anyName |= name > 0f;
            widths[i] = 2f * padding + glyphWidths[i] + (name > 0f ? nameGap + name : 0f);
            total += widths[i];
        }
        boolean names = anyName && total <= available;
        if (!names) {
            total = gap * (count - 1);
            for (int i = 0; i < count; i++) {
                widths[i] = 2f * padding + glyphWidths[i];
                total += widths[i];
            }
            if (total > available) {
                float share = (available - gap * (count - 1)) / count;
                total = gap * (count - 1);
                for (int i = 0; i < count; i++) {
                    widths[i] = Math.max(glyphWidths[i], share);
                    total += widths[i];
                }
            }
        }
        float[] left = new float[count];
        float[] right = new float[count];
        float x = 0f;
        int shown = 0;
        for (int i = 0; i < count; i++) {
            if (i > 0) x += gap;
            if (x + widths[i] > available + 0.5f && shown > 0) break;
            left[i] = x;
            right[i] = x + widths[i];
            x = right[i];
            shown = i + 1;
        }
        return new Layout(Arrays.copyOf(left, count), Arrays.copyOf(right, count), shown, names,
            names ? nameMax : 0f, x);
    }

    /** The chip under {@code x} (from the row's left), or -1; the gaps between chips are no chip. */
    public static int chipAt(@NonNull Layout layout, float x) {
        for (int i = 0; i < layout.shown; i++) {
            if (x >= layout.left[i] && x < layout.right[i]) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- ink

    /**
     * The chips' glyphs and names: the float titlebar's ink, which is the window bar's label ink on
     * the chrome's polarity on glass, and the on-surface ink made legible on the plain panel.
     */
    @ColorInt
    public static int ink(boolean glass, @Nullable ChromeInk.Polarity polarity,
                          @ColorInt int onSurface, @ColorInt int surfaceBase,
                          @ColorInt int surface) {
        return FloatTitlebarPolicy.glyphInk(glass, polarity, onSurface, surfaceBase, surface, true);
    }

    /**
     * A chip's outline: on glass with a published polarity, a resting window chip's outline (the
     * polarity's neutral walked until it clears {@link OnGlass#TARGET_LARGE_TEXT} on
     * {@code surface}); on the plain panel, or unmeasured, {@code ink} at
     * {@link #PLAIN_STROKE_ALPHA}.
     */
    @ColorInt
    public static int stroke(boolean glass, @Nullable ChromeInk.Polarity polarity,
                             @ColorInt int onSurface, @ColorInt int surfaceBase,
                             @ColorInt int surface, @ColorInt int ink) {
        if (glass && polarity != null) {
            boolean pale = polarity == ChromeInk.Polarity.PALE_INK;
            return WindowChipInk.towardPolarity(OnGlass.opaque(surface),
                WindowChipInk.neutralSeed(onSurface, surfaceBase, pale), pale,
                OnGlass.TARGET_LARGE_TEXT);
        }
        return OnGlass.withAlpha(ink, PLAIN_STROKE_ALPHA);
    }
}
