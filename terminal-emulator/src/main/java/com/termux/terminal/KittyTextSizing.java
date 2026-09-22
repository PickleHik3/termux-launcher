package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The text sizing escape, {@code OSC 66}.
 *
 * <p>A program says "draw this text bigger than a cell" and the terminal reserves a rectangle of
 * cells for it. The wire form is {@code ESC ] 66 ; key=value:key=value ; text ST}, the same shape
 * as {@link KittyNotifications}: colon separated single-letter keys, then one semicolon, then the
 * text, which may itself contain semicolons.
 *
 * <p>This class is a pure parser plus the bit packing of the per-cell size record. It holds no
 * session state, so the emulator can call it from the OSC dispatch and the buffer can call it from
 * reflow without either of them owning it.
 *
 * @see <a href="https://sw.kovidgoyal.net/kitty/text-sizing-protocol/">the protocol</a>
 */
public final class KittyTextSizing {

    /** The protocol caps a single escape's text at this many UTF-8 bytes; the rest is dropped. */
    public static final int MAX_PAYLOAD_BYTES = 4096;

    /** Largest {@code s} the protocol allows. Bigger values are clamped down to it. */
    public static final int MAX_SCALE = 7;

    /** Largest {@code w} the protocol allows. */
    public static final int MAX_WIDTH = 7;

    /** {@code v=0} top, {@code h=0} left. */
    public static final int ALIGN_START = 0;

    /** {@code v=1} bottom, {@code h=1} right. */
    public static final int ALIGN_END = 1;

    /** {@code v=2}/{@code h=2} centre. */
    public static final int ALIGN_CENTRE = 2;

    private KittyTextSizing() {
    }

    /** One parsed {@code OSC 66}: how big to draw, and what to draw. */
    public static final class Request {

        /** {@code s}, 1 to {@link #MAX_SCALE}. The block is this many rows tall. */
        public final int scale;

        /** {@code w}, 0 to {@link #MAX_WIDTH}. 0 means "measure the text yourself". */
        public final int width;

        /** {@code n} of the {@code n/d} fraction of the block height to draw in, 0 to 15. */
        public final int numerator;

        /** {@code d} of the {@code n/d} fraction, 0 to 15. */
        public final int denominator;

        /** {@code v}, one of the {@code ALIGN_} values. */
        public final int verticalAlign;

        /** {@code h}, one of the {@code ALIGN_} values. */
        public final int horizontalAlign;

        /** The text to draw, already cut to {@link #MAX_PAYLOAD_BYTES}. */
        @NonNull
        public final String text;

        Request(int scale, int width, int numerator, int denominator, int verticalAlign,
                int horizontalAlign, @NonNull String text) {
            this.scale = scale;
            this.width = width;
            this.numerator = numerator;
            this.denominator = denominator;
            this.verticalAlign = verticalAlign;
            this.horizontalAlign = horizontalAlign;
            this.text = text;
        }

        /** Whether the fraction asks for part of the block height rather than all of it. */
        public boolean isFraction() {
            return numerator > 0 && denominator > 0 && numerator < denominator;
        }
    }

    /**
     * Take one {@code OSC 66} escape. {@code args} is everything after {@code 66;} - the metadata,
     * a semicolon, then the text. Returns null when there is nothing to draw.
     */
    @Nullable
    public static Request parse(@NonNull String args) {
        String metadata;
        String text;
        int separator = args.indexOf(';');
        if (separator < 0) {
            // Malformed: the protocol requires both semicolons. There is no text to draw.
            metadata = args;
            text = "";
        } else {
            metadata = args.substring(0, separator);
            text = args.substring(separator + 1);
        }
        if (text.isEmpty()) return null;

        int scale = 1;
        int width = 0;
        int numerator = 0;
        int denominator = 0;
        int verticalAlign = ALIGN_START;
        int horizontalAlign = ALIGN_START;

        int start = 0;
        while (start <= metadata.length()) {
            int end = metadata.indexOf(':', start);
            if (end < 0) end = metadata.length();
            String pair = metadata.substring(start, end);
            int equals = pair.indexOf('=');
            if (equals == 1) {
                int value = parseNonNegative(pair.substring(equals + 1));
                if (value >= 0) {
                    switch (pair.charAt(0)) {
                        case 's':
                            scale = clamp(value, 1, MAX_SCALE);
                            break;
                        case 'w':
                            width = clamp(value, 0, MAX_WIDTH);
                            break;
                        case 'n':
                            numerator = clamp(value, 0, 15);
                            break;
                        case 'd':
                            denominator = clamp(value, 0, 15);
                            break;
                        case 'v':
                            verticalAlign = clamp(value, 0, 2);
                            break;
                        case 'h':
                            horizontalAlign = clamp(value, 0, 2);
                            break;
                        default:
                            // Unknown keys are ignored, so a newer program still draws something.
                            break;
                    }
                }
            }
            start = end + 1;
        }
        return new Request(scale, width, numerator, denominator, verticalAlign, horizontalAlign,
            cutToBytes(text, MAX_PAYLOAD_BYTES));
    }

    /** Parse an unsigned decimal, or -1 when the value is not one. */
    private static int parseNonNegative(@NonNull String value) {
        if (value.isEmpty() || value.length() > 6) return -1;
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return -1;
            result = result * 10 + (c - '0');
        }
        return result;
    }

    private static int clamp(int value, int low, int high) {
        return (value < low) ? low : Math.min(value, high);
    }

    /** Cut a string to at most {@code maxBytes} UTF-8 bytes, never splitting a code point. */
    @NonNull
    static String cutToBytes(@NonNull String text, int maxBytes) {
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int size = utf8Length(codePoint);
            if (bytes + size > maxBytes) return text.substring(0, i);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        return text;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) return 1;
        if (codePoint < 0x800) return 2;
        if (codePoint < 0x10000) return 3;
        return 4;
    }

    // --- Block geometry -------------------------------------------------------------------

    /**
     * Split text into graphemes as this terminal understands them: a base character plus whatever
     * zero-width marks follow it. With {@code w=0} each of these becomes a block of its own.
     */
    @NonNull
    static List<String> splitGraphemes(@NonNull String text) {
        List<String> graphemes = new ArrayList<>();
        int clusterStart = -1;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int next = i + Character.charCount(codePoint);
            if (WcWidth.width(codePoint) > 0 || clusterStart < 0) {
                if (clusterStart >= 0) graphemes.add(text.substring(clusterStart, i));
                clusterStart = i;
            }
            i = next;
        }
        if (clusterStart >= 0) graphemes.add(text.substring(clusterStart));
        return graphemes;
    }

    /** The number of terminal cells a string would take at normal size. */
    static int measureCells(@NonNull String text) {
        int cells = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int width = WcWidth.width(codePoint);
            if (width > 0) cells += width;
            i += Character.charCount(codePoint);
        }
        return Math.max(cells, text.isEmpty() ? 0 : 1);
    }

    /** Cut text to at most {@code cells} terminal cells, keeping whole graphemes. */
    @NonNull
    static String cutToCells(@NonNull String text, int cells) {
        int used = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int width = WcWidth.width(codePoint);
            if (width > 0) {
                // Zero-width marks after the last grapheme that fits belong to it, so the cut is
                // only ever made just before a base character.
                if (used + width > cells) return text.substring(0, i);
                used += width;
            }
            i += Character.charCount(codePoint);
        }
        return text;
    }

    // --- The per-cell size record --------------------------------------------------------
    //
    // One int per column, zero for a plain cell. A block's top-left cell is the anchor: it holds
    // the text and has both offsets zero. Every other cell of the block holds only its offset back
    // to the anchor, so a cell can find its block without a separate list.
    //
    //   bit  0      present
    //   bits 1-3    scale, 1 to 7
    //   bits 4-6    width in cells before scaling, 1 to 7
    //   bits 7-12   x offset from the anchor, 0 to 48
    //   bits 13-15  y offset from the anchor, 0 to 6
    //   bits 16-19  n
    //   bits 20-23  d
    //   bits 24-25  v
    //   bits 26-27  h
    //   bit  28     demoted - the block did not fit the screen width, so it sits on one row at
    //               normal size and keeps the rest of the record for when it fits again.

    private static final int PRESENT_BIT = 1;
    private static final int SCALE_SHIFT = 1;
    private static final int WIDTH_SHIFT = 4;
    private static final int OFFSET_X_SHIFT = 7;
    private static final int OFFSET_Y_SHIFT = 13;
    private static final int NUMERATOR_SHIFT = 16;
    private static final int DENOMINATOR_SHIFT = 20;
    private static final int VERTICAL_SHIFT = 24;
    private static final int HORIZONTAL_SHIFT = 26;
    private static final int DEMOTED_BIT = 1 << 28;
    private static final int OFFSETS_MASK = (0x3F << OFFSET_X_SHIFT) | (0x7 << OFFSET_Y_SHIFT);

    /** The largest x offset the record can hold; scale 7 times width 7 stays inside it. */
    static final int MAX_OFFSET_X = 0x3F;

    /** Build the record every cell of one block carries, before its offsets are stamped in. */
    static int packRecord(int scale, int width, int numerator, int denominator,
                          int verticalAlign, int horizontalAlign, boolean demoted) {
        return PRESENT_BIT
            | (clamp(scale, 1, MAX_SCALE) << SCALE_SHIFT)
            | (clamp(width, 1, MAX_WIDTH) << WIDTH_SHIFT)
            | (clamp(numerator, 0, 15) << NUMERATOR_SHIFT)
            | (clamp(denominator, 0, 15) << DENOMINATOR_SHIFT)
            | (clamp(verticalAlign, 0, 2) << VERTICAL_SHIFT)
            | (clamp(horizontalAlign, 0, 2) << HORIZONTAL_SHIFT)
            | (demoted ? DEMOTED_BIT : 0);
    }

    static int withOffsets(int record, int offsetX, int offsetY) {
        return (record & ~OFFSETS_MASK)
            | ((offsetX & 0x3F) << OFFSET_X_SHIFT)
            | ((offsetY & 0x7) << OFFSET_Y_SHIFT);
    }

    static int withDemoted(int record, boolean demoted) {
        return demoted ? (record | DEMOTED_BIT) : (record & ~DEMOTED_BIT);
    }

    static boolean isPresent(int record) {
        return (record & PRESENT_BIT) != 0;
    }

    static boolean isAnchor(int record) {
        return isPresent(record) && (record & OFFSETS_MASK) == 0;
    }

    static int scaleOf(int record) {
        return isPresent(record) ? ((record >>> SCALE_SHIFT) & 0x7) : 1;
    }

    static int widthOf(int record) {
        return isPresent(record) ? ((record >>> WIDTH_SHIFT) & 0x7) : 1;
    }

    static int offsetXOf(int record) {
        return (record >>> OFFSET_X_SHIFT) & 0x3F;
    }

    static int offsetYOf(int record) {
        return (record >>> OFFSET_Y_SHIFT) & 0x7;
    }

    static int numeratorOf(int record) {
        return (record >>> NUMERATOR_SHIFT) & 0xF;
    }

    static int denominatorOf(int record) {
        return (record >>> DENOMINATOR_SHIFT) & 0xF;
    }

    static int verticalAlignOf(int record) {
        return (record >>> VERTICAL_SHIFT) & 0x3;
    }

    static int horizontalAlignOf(int record) {
        return (record >>> HORIZONTAL_SHIFT) & 0x3;
    }

    static boolean isDemoted(int record) {
        return (record & DEMOTED_BIT) != 0;
    }

    /** Whether two records describe the same block, ignoring where in it each cell sits. */
    static boolean sameBlock(int a, int b) {
        return (a & ~OFFSETS_MASK) == (b & ~OFFSETS_MASK);
    }

    /** How many rows a block with this record covers. A demoted block is always one row. */
    static int rowsOf(int record) {
        return isDemoted(record) ? 1 : scaleOf(record);
    }

    /** How many columns a block with this record covers. A demoted block is not scaled. */
    static int columnsOf(int record) {
        return isDemoted(record) ? widthOf(record) : scaleOf(record) * widthOf(record);
    }
}
