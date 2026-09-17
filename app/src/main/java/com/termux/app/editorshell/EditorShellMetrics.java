package com.termux.app.editorshell;

import androidx.annotation.NonNull;

/**
 * Every size the Appearance and Layout editors are built from, in one place.
 *
 * <p>The two editors used to inherit their widths from the card, and the card from the screen, so a
 * landscape screen gave a 0–40 dp corner slider nearly 900 dp of travel and a portrait one gave the
 * same control a tenth of that. The rule here runs the other way: the control kit declares its
 * widths, the row is the sum of its columns, the pane is the row, and the card is the panes. What
 * is left over on a wide screen becomes air around the card — which is the live place the editor is
 * for — rather than stretched controls.
 *
 * <p>Pure arithmetic on dp, pixels and counts. No views, no {@code Context}, no resources, so every
 * case the two reference devices raise is assertable without inflating an editor.
 */
public final class EditorShellMetrics {

    private EditorShellMetrics() {}

    // ------------------------------------------------------------------------------- the row

    /** The platform's minimum touch target, and therefore the row's floor. */
    public static final int ROW_MIN_HEIGHT_DP = 48;
    /** Breathing room above and below the row's content, so a tall font scale grows the row. */
    public static final int ROW_VERTICAL_PADDING_DP = 6;
    /** Holds the longest shipped label ("Wallpaper", "Opens on entry") at a 1.3x font scale. */
    public static final int LABEL_COLUMN_DP = 96;
    /** Holds "100%", "24 dp" and the Layout editor's "68%". */
    public static final int VALUE_COLUMN_DP = 52;
    /** The link chip's drawn size; its touch target is the row. */
    public static final int CHIP_DP = 28;
    /** Label to control. */
    public static final int GAP_LABEL_DP = 12;
    /** Control to value. */
    public static final int GAP_CONTROL_DP = 12;
    /** Value to chip. */
    public static final int GAP_VALUE_DP = 8;

    /** Everything in a row that is not the control: the two side columns, the chip and the gaps. */
    public static final int ROW_FURNITURE_DP = LABEL_COLUMN_DP + GAP_LABEL_DP + GAP_CONTROL_DP
        + VALUE_COLUMN_DP + GAP_VALUE_DP + CHIP_DP;

    /**
     * A segment row's furniture. A toggle group has nothing to print on its right — the chosen
     * segment is the value — so it keeps the label column and takes the rest of the row.
     */
    public static final int SEGMENT_ROW_FURNITURE_DP = LABEL_COLUMN_DP + GAP_LABEL_DP;

    // ---------------------------------------------------------------------------- the slider

    /** Below this a track is a smudge rather than a control. */
    public static final int TRACK_MIN_DP = 96;
    /**
     * The widest track worth having. The finest control in either editor is the keyboard's key
     * radius at 240 steps of a tenth of a dp, so 280 dp is the smallest track on which every step
     * of every control is individually addressable. Past it extra width buys nothing and costs
     * the thumb a longer sweep for the same answer — a wider card gets a second column instead.
     */
    public static final int TRACK_MAX_DP = 280;

    // --------------------------------------------------------------------------- the segments

    /**
     * The segment width a line is given wherever its column can afford it. It is a floor and not a
     * guarantee: a three-way group in a 154 dp control column cannot have 264 dp of segments, and
     * an even share of the column it has beats a group that runs off the side of the card.
     */
    public static final int SEGMENT_MIN_DP = 88;
    public static final int SEGMENT_MAX_DP = 160;
    /** Fixed, not wrapped: auto-sizing switches off entirely the moment a dimension wraps. */
    public static final int SEGMENT_HEIGHT_DP = 44;

    // ------------------------------------------------------------------------------ the panes

    /** Between two panes, and the only gutter the body has. */
    public static final int GUTTER_DP = 24;
    /** The narrowest row worth drawing: label, the minimum track, value and chip. */
    public static final int ROW_MIN_INNER_DP = ROW_FURNITURE_DP + TRACK_MIN_DP;
    /** The widest, at the maximum track. A pane never grows past it. */
    public static final int ROW_MAX_INNER_DP = ROW_FURNITURE_DP + TRACK_MAX_DP;
    /** Two panes need two whole rows and the gutter between them. */
    public static final int TWO_PANE_MIN_CONTENT_DP = (2 * ROW_MIN_INNER_DP) + GUTTER_DP;

    /** The card's margin from each screen edge. */
    public static final int CARD_SIDE_MARGIN_DP = 10;
    /** The card's own padding inside those margins, on each side. */
    public static final int CARD_SIDE_PADDING_DP = 16;
    /** The air a leading pane keeps around the content it is sized from. */
    public static final int LEADING_PANE_AIR_DP = 32;

    // ----------------------------------------------------------------------------- the header

    public static final int HEADER_DP = 56;
    /** What the header comes down to where the card has little height to spend on chrome. */
    public static final int HEADER_COMPACT_DP = 44;
    /** Below this much card height the header goes compact. */
    public static final int HEADER_COMPACT_BELOW_DP = 280;
    /** The one slot under the header for the choice that changes what the whole card shows. */
    public static final int CHOOSER_DP = 60;
    /** Below this much body the chooser unpins and scrolls with the rows. */
    public static final int CHOOSER_PIN_MIN_BODY_DP = 200;

    // ---------------------------------------------------------------------------- the overflow

    /** How much of the next row stays visible under the cut, so the cut never lands on glyphs. */
    public static final int PEEK_DP = 16;
    /** Long enough to cover the peek and the top of the row behind it. */
    public static final int FADE_DP = 24;

    // ----------------------------------------------------------------------------- the section

    public static final int SECTION_MIN_HEIGHT_DP = 24;
    /** A section reads as a break because of the air above it, not a rule. */
    public static final int SECTION_TOP_MARGIN_DP = 12;
    public static final int SECTION_BOTTOM_MARGIN_DP = 4;

    // ------------------------------------------------------------------------- the preset tile

    public static final int PRESET_TILE_WIDTH_DP = 72;
    public static final int PRESET_TILE_HEIGHT_DP = 40;
    public static final int PRESET_TILE_GAP_DP = 8;
    /** The tile, its name under it, and the air the row stands in. */
    public static final int PRESET_ROW_DP = 60;

    // --------------------------------------------------------------------------------- pixels

    /** Rounds a dp to whole pixels the way every view in the tree does. */
    public static int px(float dp, float density) {
        return Math.round(dp * Math.max(0.01f, density));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------------------ the answers

    /** One row's columns, in pixels at this density. */
    public static final class RowMetrics {
        public final int minHeightPx;
        public final int verticalPaddingPx;
        public final int labelWidthPx;
        public final int valueWidthPx;
        public final int chipSizePx;
        public final int labelGapPx;
        public final int controlGapPx;
        public final int valueGapPx;
        /** Everything the control column does not get. */
        public final int furniturePx;

        RowMetrics(float density) {
            minHeightPx = px(ROW_MIN_HEIGHT_DP, density);
            verticalPaddingPx = px(ROW_VERTICAL_PADDING_DP, density);
            labelWidthPx = px(LABEL_COLUMN_DP, density);
            valueWidthPx = px(VALUE_COLUMN_DP, density);
            chipSizePx = px(CHIP_DP, density);
            labelGapPx = px(GAP_LABEL_DP, density);
            controlGapPx = px(GAP_CONTROL_DP, density);
            valueGapPx = px(GAP_VALUE_DP, density);
            furniturePx = px(ROW_FURNITURE_DP, density);
        }

        /** What is left for the control once the row's furniture has taken its columns. */
        public int controlWidthPx(int rowInnerWidthPx) {
            return Math.max(0, rowInnerWidthPx - furniturePx);
        }
    }

    /**
     * The row's columns at this density. No font-scale argument: every column here is a dp and
     * stays one, and the only thing a font scale moves is the row's measured height, which the
     * overflow maths re-reads from the view rather than predicting.
     */
    @NonNull
    public static RowMetrics rowMetrics(float density) {
        return new RowMetrics(density);
    }

    /** The slider's track: never a smudge, never a sweep, whatever the card is doing. */
    public static int trackWidthPx(int availableControlPx, float density) {
        return clamp(availableControlPx, px(TRACK_MIN_DP, density), px(TRACK_MAX_DP, density));
    }

    /** What a toggle group has to lay its segments in, given the row it stands in. */
    public static int segmentControlWidthPx(int rowInnerWidthPx, float density) {
        return Math.max(0, rowInnerWidthPx - px(SEGMENT_ROW_FURNITURE_DP, density));
    }

    /**
     * One segment's width. Every segment on a line is this wide, so two questions never read as one
     * ragged row of five answers — which is what Docked/Floating at 238 dp beside
     * Solid/Glass/Frost at 192 dp read as before.
     */
    public static int segmentWidthPx(int controlPx, int segmentCount, float density) {
        int count = Math.max(1, segmentCount);
        int even = Math.max(0, controlPx) / count;
        return clamp(even, Math.min(px(SEGMENT_MIN_DP, density), even),
            px(SEGMENT_MAX_DP, density));
    }

    /** How the body's width is divided, and how many ways. */
    public static final class PaneSplit {
        public final int paneCount;
        public final int leadingWidthPx;
        /** Zero while there is only one pane. */
        public final int trailingWidthPx;
        public final int gutterPx;

        PaneSplit(int paneCount, int leadingWidthPx, int trailingWidthPx, int gutterPx) {
            this.paneCount = paneCount;
            this.leadingWidthPx = leadingWidthPx;
            this.trailingWidthPx = trailingWidthPx;
            this.gutterPx = gutterPx;
        }

        /** What the panes take between them, gutter excluded. */
        public int totalPaneWidthPx() {
            return leadingWidthPx + trailingWidthPx;
        }
    }

    /**
     * Whether the body stands in one column or two, and how wide each is.
     *
     * <p>Two panes only where two whole rows and a gutter fit; never three, because a third column
     * puts related rows three eye movements apart and no section in either editor is more than four
     * rows deep. A pane never grows past {@link #ROW_MAX_INNER_DP} — the leftover width is air
     * around the card, through which the surfaces being edited stay visible.
     *
     * @param leadingNaturalPx the width the leading pane's own content asks for — the Layout
     *     editor's miniature. Zero or less splits the panes evenly, which is what the Appearance
     *     editor wants.
     */
    @NonNull
    public static PaneSplit paneSplit(int contentWidthPx, int leadingNaturalPx, float density) {
        int content = Math.max(0, contentWidthPx);
        int gutter = px(GUTTER_DP, density);
        int maxPane = px(ROW_MAX_INNER_DP, density);
        if (content < px(TWO_PANE_MIN_CONTENT_DP, density))
            return new PaneSplit(1, Math.min(maxPane, content), 0, gutter);
        int room = content - gutter;
        if (leadingNaturalPx <= 0) {
            int pane = Math.min(maxPane, room / 2);
            return new PaneSplit(2, pane, pane, gutter);
        }
        int leading = clamp(leadingNaturalPx + px(LEADING_PANE_AIR_DP, density),
            Math.round(0.25f * content), Math.round(0.5f * content));
        int trailing = Math.min(maxPane, Math.max(0, room - leading));
        return new PaneSplit(2, leading, trailing, gutter);
    }

    /**
     * The width the body's panes leave once the card's own margins and padding are taken off.
     * This is what {@link #paneSplit} is asked about.
     */
    public static int contentWidthPx(int screenWidthPx, float density) {
        return Math.max(0, screenWidthPx
            - px(2 * CARD_SIDE_MARGIN_DP, density) - px(2 * CARD_SIDE_PADDING_DP, density));
    }

    /**
     * How wide the card stands: its panes, its gutter and its own padding, and never wider than
     * the screen minus its margins. This is the line where the card stops inheriting the screen.
     */
    public static int cardWidthPx(int screenWidthPx, @NonNull PaneSplit split, float density) {
        int widest = Math.max(0, screenWidthPx - px(2 * CARD_SIDE_MARGIN_DP, density));
        int asked = split.totalPaneWidthPx() + ((split.paneCount - 1) * split.gutterPx)
            + px(2 * CARD_SIDE_PADDING_DP, density);
        return Math.min(widest, asked);
    }

    /** The header's height: full, or compact where the card has little height to spend. */
    public static int headerHeightPx(int availableHeightPx, float density) {
        return availableHeightPx < px(HEADER_COMPACT_BELOW_DP, density)
            ? px(HEADER_COMPACT_DP, density) : px(HEADER_DP, density);
    }

    /**
     * Whether the chooser row stays pinned under the header. On a short landscape screen 60 dp of
     * pinned chrome is nearly a third of the body, so below the threshold it unpins and becomes the
     * body's first row instead of costing rows that are the controls.
     */
    public static boolean chooserPinned(int bodyHeightPx, float density) {
        return bodyHeightPx >= px(CHOOSER_PIN_MIN_BODY_DP, density);
    }

    /**
     * How many of the leading sections go in the leading pane.
     *
     * <p>Panes are filled section-major and a section never straddles the gutter: related rows
     * three eye movements apart is the thing two columns are supposed to fix, not cause. The
     * boundary is wherever the two panes come out closest in height, and the trailing pane always
     * gets at least one section — a second column with nothing in it is worse than one column.
     *
     * @param rowsPerSection the row count of each section, in the order they are drawn
     */
    public static int sectionsInLeadingPane(@NonNull int[] rowsPerSection) {
        int sections = rowsPerSection.length;
        if (sections <= 1)
            return sections;
        int total = 0;
        for (int rows : rowsPerSection)
            total += rows;
        int best = 1;
        int bestGap = Integer.MAX_VALUE;
        int leading = 0;
        for (int boundary = 1; boundary < sections; boundary++) {
            leading += rowsPerSection[boundary - 1];
            int gap = Math.abs(leading - (total - leading));
            if (gap < bestGap) {
                bestGap = gap;
                best = boundary;
            }
        }
        return best;
    }

    /** How tall the body stands, in whole rows, and whether there is more below. */
    public static final class BodyCap {
        public final int capPx;
        public final int wholeRows;
        public final boolean overflows;

        BodyCap(int capPx, int wholeRows, boolean overflows) {
            this.capPx = capPx;
            this.wholeRows = wholeRows;
            this.overflows = overflows;
        }
    }

    /**
     * The body's height, rounded down to whole rows plus a peek.
     *
     * <p>A cap taken as raw arithmetic cuts wherever it lands, which in practice is through the
     * middle of a row's glyphs — and a row sliced in half reads as a rendering fault rather than as
     * "there is more below". Quantising to the measured row pitch puts the cut a fixed
     * {@value #PEEK_DP} dp into the next row instead: a sliver of a label, which is legible as a
     * list that continues.
     *
     * @param rowPitchPx the <em>measured</em> height of a row, not the nominal 48 dp, so a large
     *     font scale or a tall locale stays in step
     */
    @NonNull
    public static BodyCap bodyCap(int availablePx, int contentPx, int rowPitchPx, int peekPx) {
        int available = Math.max(0, availablePx);
        int pitch = Math.max(1, rowPitchPx);
        int peek = Math.max(0, peekPx);
        if (contentPx <= available)
            return new BodyCap(available, (contentPx + pitch - 1) / pitch, false);
        int wholeRows = Math.max(1, (available - peek) / pitch);
        int quantised = (wholeRows * pitch) + peek;
        // Where the room cannot hold one whole row and the peek, the honest answer is the room.
        return new BodyCap(Math.min(available, quantised), wholeRows, true);
    }
}
