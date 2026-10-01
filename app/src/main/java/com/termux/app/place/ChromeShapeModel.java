package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.Card;
import com.termux.app.place.ChromeShape.Corners;
import com.termux.app.place.ChromeShape.Divider;
import com.termux.app.place.ChromeShape.EdgeKind;
import com.termux.app.place.ChromeShape.EdgeRun;
import com.termux.app.place.ChromeShape.Opening;
import com.termux.app.place.ChromeShape.Pane;
import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShape.PieceId;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one shape model for the chrome (ADR 0007, SPEC section 3.7): from the layout and the Style it
 * yields every chrome shape, and nothing else decides a chrome corner. The live chrome's outline
 * providers, glass and rim and the layout canvas's fill, selection outline, lifted copy and
 * placeholder all read the {@link ChromeShape} it returns.
 *
 * <p>Pure: no view, no context, and no unit. The caller supplies the frame's size and each piece's
 * thickness in whatever space it works in (pixels live, canvas units in the editor) and gets rects
 * back in the same space. Corners, Margin, the screen radius, the pane-gap cap and the divider's
 * thickness are numbers in that space too. Floating spends Corners and Margin; Docked spends the
 * screen radius and no gap at all.
 *
 * <p>The keyboard is part of the arrangement while {@link PlaceLayout#keyboardShown}; a caller
 * drawing the closed-keyboard frame passes {@code layout.withKeyboardShown(false)}.
 *
 * <p>Rules beyond SPEC 3.7 that this class settles, so slices 2 and 3 need not:
 * <ul>
 *   <li>A rounded corner under Docked is one that coincides with a corner of the screen; the
 *       corners facing the opening or the gap of a split keyboard stay square.</li>
 *   <li>Under Floating, pieces sharing a card are joined: the edge between them is
 *       {@link EdgeKind#JOIN}, the card's boundary {@link EdgeKind#RIM}.</li>
 *   <li>A docked-form keyboard under Floating, and each half of a split keyboard, is a card in
 *       its edge's stack; under-keyboard rows below it are a card of their own. With the keyboard
 *       hidden or floating, nothing stands between the two groups of the bottom edge and they read
 *       as one stack, in the order {@link EdgeStackPolicy#stack} gives.</li>
 *   <li>The floating keyboard is a card over the content under either Style. Its radius is
 *       Corners, its edges are {@link EdgeKind#RIM}, and it rests centred on the opening's
 *       bottom, Margin above it under Floating and flush under Docked, unless the caller gives the
 *       rect the user moved it to.</li>
 *   <li>Split halves are as wide as {@link Input.Builder#splitHalfWidth}, each against its screen
 *       edge (a Margin in under Floating); between them the frame is open.</li>
 *   <li>Panes divide the opening equally along one axis. Under Docked they tile it exactly and
 *       the divider is a segment on each seam; under Floating they are cards with
 *       {@code min(Margin, paneGapCap)} between.</li>
 * </ul>
 */
public final class ChromeShapeModel {

    private ChromeShapeModel() {}

    /** How split panes lie in the opening. */
    public enum SplitAxis {
        /** Panes standing next to each other, with vertical seams. */
        SIDE_BY_SIDE,
        /** Panes standing one above the other, with horizontal seams. */
        STACKED
    }

    /**
     * Each piece's thickness: its height as a row, its width as a column, and the keyboard's
     * height. A piece with no figure given takes none.
     */
    public static final class Thickness {
        private final float[] mRow = new float[Element.values().length];
        private final float[] mColumn = new float[Element.values().length];
        private float mKeyboard;

        /** The band {@code element} claims on {@code edge}: its width in a column, height in a row. */
        public float of(@NonNull Element element, @NonNull Edge edge) {
            return edge.isOnSide() ? mColumn[element.ordinal()] : mRow[element.ordinal()];
        }

        public float keyboard() {
            return mKeyboard;
        }

        @NonNull
        public static Thickness of(float statusRow, float statusColumn, float appsRow,
                                   float appsColumn, float azRow, float azColumn,
                                   float extraKeysRow, float extraKeysColumn, float keyboard) {
            Thickness t = new Thickness();
            t.mRow[Element.STATUS.ordinal()] = statusRow;
            t.mColumn[Element.STATUS.ordinal()] = statusColumn;
            t.mRow[Element.APPS.ordinal()] = appsRow;
            t.mColumn[Element.APPS.ordinal()] = appsColumn;
            t.mRow[Element.AZ.ordinal()] = azRow;
            t.mColumn[Element.AZ.ordinal()] = azColumn;
            t.mRow[Element.EXTRA_KEYS.ordinal()] = extraKeysRow;
            t.mColumn[Element.EXTRA_KEYS.ordinal()] = extraKeysColumn;
            t.mKeyboard = keyboard;
            return t;
        }
    }

    /** Everything the model reads. Build one with {@link ChromeShapeModel#input}. */
    public static final class Input {
        @NonNull final PlaceLayout layout;
        @NonNull final LayoutStyle style;
        final float width;
        final float height;
        @NonNull final Thickness thickness;
        final float corners;
        final float margin;
        final float screenRadius;
        final float paneGapCap;
        final float dividerThickness;
        final int paneCount;
        @NonNull final SplitAxis axis;
        final float splitHalfWidth;
        final float floatingKeyboardWidth;
        @Nullable final Box floatingKeyboardBox;

        private Input(@NonNull Builder b) {
            layout = b.layout;
            style = b.style;
            width = b.width;
            height = b.height;
            thickness = b.thickness;
            corners = b.corners;
            margin = b.margin;
            screenRadius = b.screenRadius;
            paneGapCap = b.paneGapCap;
            dividerThickness = b.dividerThickness;
            paneCount = Math.max(1, b.paneCount);
            axis = b.axis;
            splitHalfWidth = b.splitHalfWidth;
            floatingKeyboardWidth = b.floatingKeyboardWidth;
            floatingKeyboardBox = b.floatingKeyboardBox;
        }

        @NonNull
        public PlaceLayout layout() {
            return layout;
        }

        /** The same question about another arrangement: a drag's preview, say. */
        @NonNull
        public Input withLayout(@NonNull PlaceLayout other) {
            return toBuilder().layout(other).build();
        }

        /** The same question under the other Style. */
        @NonNull
        public Input withStyle(@NonNull LayoutStyle other) {
            return toBuilder().style(other).build();
        }

        /** Builds an {@link Input}; see {@link ChromeShapeModel#input}. */
        public static final class Builder {
            @NonNull private PlaceLayout layout;
            @NonNull private LayoutStyle style;
            private final float width;
            private final float height;
            @NonNull private final Thickness thickness;
            private float corners;
            private float margin;
            private float screenRadius;
            private float paneGapCap = Float.MAX_VALUE;
            private float dividerThickness = 1f;
            private int paneCount = 1;
            @NonNull private SplitAxis axis = SplitAxis.SIDE_BY_SIDE;
            private float splitHalfWidth;
            private float floatingKeyboardWidth;
            @Nullable private Box floatingKeyboardBox;

            Builder(@NonNull PlaceLayout layout, @NonNull LayoutStyle style, float width,
                    float height, @NonNull Thickness thickness) {
                this.layout = layout;
                this.style = style;
                this.width = width;
                this.height = height;
                this.thickness = thickness;
            }

            @NonNull public Builder layout(@NonNull PlaceLayout value) {
                layout = value;
                return this;
            }

            @NonNull public Builder style(@NonNull LayoutStyle value) {
                style = value;
                return this;
            }

            /** The Corners radius every Floating card takes; also the floating keyboard's. */
            @NonNull public Builder corners(float value) {
                corners = value;
                return this;
            }

            /** Margin, the air between and around Floating cards. Docked spends none. */
            @NonNull public Builder margin(float value) {
                margin = value;
                return this;
            }

            /** The device's screen radius, with the caller's 28dp fallback already applied. */
            @NonNull public Builder screenRadius(float value) {
                screenRadius = value;
                return this;
            }

            /** The most air between Floating pane cards: 24dp in the caller's units. */
            @NonNull public Builder paneGapCap(float value) {
                paneGapCap = value;
                return this;
            }

            /** How thick a Docked divider line draws: 1dp in the caller's units. */
            @NonNull public Builder dividerThickness(float value) {
                dividerThickness = value;
                return this;
            }

            /** How many panes divide the opening, and along which axis. */
            @NonNull public Builder panes(int count, @NonNull SplitAxis value) {
                paneCount = count;
                axis = value;
                return this;
            }

            /** How wide each half of a split keyboard is; 40% of the frame when not given. */
            @NonNull public Builder splitHalfWidth(float value) {
                splitHalfWidth = value;
                return this;
            }

            /** How wide the floating keyboard stands by default; 90% of the opening when not given. */
            @NonNull public Builder floatingKeyboardWidth(float value) {
                floatingKeyboardWidth = value;
                return this;
            }

            /** Where the user has moved the floating keyboard to, overriding the default rest. */
            @NonNull public Builder floatingKeyboardBox(@Nullable Box value) {
                floatingKeyboardBox = value;
                return this;
            }

            @NonNull public Input build() {
                return new Input(this);
            }
        }

        @NonNull
        public Builder toBuilder() {
            Builder b = new Builder(layout, style, width, height, thickness);
            b.corners = corners;
            b.margin = margin;
            b.screenRadius = screenRadius;
            b.paneGapCap = paneGapCap;
            b.dividerThickness = dividerThickness;
            b.paneCount = paneCount;
            b.axis = axis;
            b.splitHalfWidth = splitHalfWidth;
            b.floatingKeyboardWidth = floatingKeyboardWidth;
            b.floatingKeyboardBox = floatingKeyboardBox;
            return b;
        }
    }

    /**
     * Starts an {@link Input}: the arrangement, the Style, the frame's size and each piece's
     * thickness. Everything else defaults to nothing: no Corners, no Margin, a square screen, no
     * cap on the pane gap, one pane.
     */
    @NonNull
    public static Input.Builder input(@NonNull PlaceLayout layout, @NonNull LayoutStyle style,
                                      float width, float height, @NonNull Thickness thickness) {
        return new Input.Builder(layout, style, width, height, thickness);
    }

    // ---------------------------------------------------------------- the model

    private static final class Cand {
        final PieceId id;
        final Box box;
        final int group;
        final boolean overlay;

        Cand(PieceId id, Box box, int group, boolean overlay) {
            this.id = id;
            this.box = box;
            this.group = group;
            this.overlay = overlay;
        }
    }

    /** One band of an edge's stack waiting to be placed. */
    private static final class Band {
        final PieceId id;
        final float thickness;
        /** The band is the keyboard, or its split pair: always a card of its own under Floating. */
        final boolean keyboard;
        final boolean split;

        Band(PieceId id, float thickness, boolean keyboard, boolean split) {
            this.id = id;
            this.thickness = thickness;
            this.keyboard = keyboard;
            this.split = split;
        }
    }

    /** One card's worth of bands, outermost first. */
    private static final class Run {
        final List<Band> bands = new ArrayList<>(3);
    }

    /** Every chrome shape for one layout under one Style. */
    @NonNull
    public static ChromeShape shape(@NonNull Input in) {
        final boolean floating = in.style == LayoutStyle.FLOATING;
        final float w = Math.max(0f, in.width);
        final float h = Math.max(0f, in.height);
        final float eps = 1e-4f * Math.max(1f, Math.max(w, h));
        final float m = floating ? Math.max(0f, in.margin) : 0f;
        final PlaceLayout layout = in.layout;
        final boolean keyboardOverlay = layout.keyboardShown
            && layout.keyboardForm == KeyboardForm.FLOATING;
        final boolean keyboardInStack = layout.keyboardShown && !keyboardOverlay;

        // The stacks, outermost first. Along the bottom the rows under the keyboard stand nearest
        // the screen's edge, then the keyboard, then the rows over it.
        List<Band> top = bandsOf(EdgeStackPolicy.stack(layout, Edge.TOP), Edge.TOP, in);
        List<Band> left = bandsOf(EdgeStackPolicy.stack(layout, Edge.LEFT), Edge.LEFT, in);
        List<Band> right = bandsOf(EdgeStackPolicy.stack(layout, Edge.RIGHT), Edge.RIGHT, in);
        List<Band> bottom = new ArrayList<>(bandsOf(EdgeStackPolicy.underKeyboard(layout),
            Edge.BOTTOM, in));
        if (keyboardInStack) {
            boolean split = layout.keyboardForm == KeyboardForm.SPLIT;
            bottom.add(new Band(PieceId.KEYBOARD, in.thickness.keyboard(), true, split));
        }
        bottom.addAll(bandsOf(EdgeStackPolicy.overKeyboard(layout), Edge.BOTTOM, in));

        // Group the bands into cards. Docked is one frame; Floating breaks at the status bar and
        // the keyboard, which are cards of their own, and shares one card among the dock's rows.
        List<Run> topRuns = cardsOf(top, floating);
        List<Run> leftRuns = cardsOf(left, floating);
        List<Run> rightRuns = cardsOf(right, floating);
        List<Run> bottomRuns = cardsOf(bottom, floating);

        List<Cand> cands = new ArrayList<>(8);
        // Card ids: the Docked frame is 0; Floating cards and anything over the frame count on.
        int[] nextGroup = {1};
        final int frameGroup = 0;

        // Top stack, down from the top edge.
        float y = m;
        y = placeRows(topRuns, true, y, w, m, floating, frameGroup, nextGroup, in, cands);
        float yTop = y;
        // Bottom stack, up from the bottom edge.
        float yb = h - m;
        yb = placeRows(bottomRuns, false, yb, w, m, floating, frameGroup, nextGroup, in, cands);
        float yBottom = yb;
        // Side columns stand between them.
        float x = m;
        x = placeColumns(leftRuns, true, x, yTop, yBottom, m, floating, frameGroup, nextGroup,
            cands);
        float xLeft = x;
        float xr = w - m;
        xr = placeColumns(rightRuns, false, xr, yTop, yBottom, m, floating, frameGroup, nextGroup,
            cands);
        float xRight = xr;

        Box openingBox = new Box(xLeft, yTop, Math.max(xLeft, xRight), Math.max(yTop, yBottom));
        List<Cand> framePieces = new ArrayList<>(cands);

        // The floating keyboard, a card over the content.
        if (keyboardOverlay) {
            Box kb = in.floatingKeyboardBox != null ? in.floatingKeyboardBox
                : defaultOverlayBox(openingBox, in, m);
            cands.add(new Cand(PieceId.KEYBOARD, kb, nextGroup[0]++, true));
        }

        // Corners, runs, cards.
        List<Piece> pieces = new ArrayList<>(cands.size());
        List<Card> cards = new ArrayList<>(4);
        final Corners screenCorners = uniform(Math.max(0f,
            Math.min(in.screenRadius, Math.min(w, h) / 2f)));
        if (floating) {
            // Each card's outline is the union of its members.
            List<Integer> seen = new ArrayList<>();
            for (Cand c : cands) {
                if (seen.contains(c.group)) continue;
                seen.add(c.group);
                float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE,
                    b = -Float.MAX_VALUE;
                List<PieceId> members = new ArrayList<>(3);
                for (Cand o : cands) {
                    if (o.group != c.group) continue;
                    l = Math.min(l, o.box.left);
                    t = Math.min(t, o.box.top);
                    r = Math.max(r, o.box.right);
                    b = Math.max(b, o.box.bottom);
                    members.add(o.id);
                }
                Box outline = new Box(l, t, r, b);
                float radius = clampRadius(in.corners, outline);
                cards.add(new Card(c.group, outline, uniform(radius), null, false, members));
            }
            for (Cand c : cands) {
                Box outline = null;
                float radius = 0f;
                for (Card card : cards) {
                    if (card.id == c.group) {
                        outline = card.box;
                        radius = card.corners.topLeft;
                    }
                }
                Corners corners = cornersAgainst(c.box, outline, radius, eps);
                pieces.add(new Piece(c.id, c.box, c.group, corners, c.overlay,
                    runsOf(c, cands, true, w, h, eps)));
            }
        } else {
            if (!framePieces.isEmpty()) {
                List<PieceId> members = new ArrayList<>(framePieces.size());
                for (Cand c : framePieces) members.add(c.id);
                cards.add(new Card(frameGroup, new Box(0f, 0f, w, h), screenCorners,
                    openingBox.width() > 0f && openingBox.height() > 0f ? openingBox : null, true,
                    members));
            }
            for (Cand c : cands) {
                if (c.overlay) {
                    float radius = clampRadius(in.corners, c.box);
                    cards.add(new Card(c.group, c.box, uniform(radius), null, false,
                        Collections.singletonList(c.id)));
                    pieces.add(new Piece(c.id, c.box, c.group, uniform(radius), true,
                        runsOf(c, cands, true, w, h, eps)));
                } else {
                    pieces.add(new Piece(c.id, c.box, c.group,
                        cornersAgainst(c.box, new Box(0f, 0f, w, h), screenCorners.topLeft, eps),
                        false, runsOf(c, framePieces, false, w, h, eps)));
                }
            }
        }

        Opening opening = openingOf(openingBox, framePieces, floating, in, w, h, eps,
            screenCorners);

        // The panes.
        List<Pane> panes = new ArrayList<>(in.paneCount);
        List<Divider> dividers = new ArrayList<>(Math.max(0, in.paneCount - 1));
        float gap = floating ? Math.min(m, Math.max(0f, in.paneGapCap)) : 0f;
        boolean across = in.axis == SplitAxis.SIDE_BY_SIDE;
        float span = across ? openingBox.width() : openingBox.height();
        float each = Math.max(0f, (span - gap * (in.paneCount - 1)) / in.paneCount);
        for (int i = 0; i < in.paneCount; i++) {
            float start = (across ? openingBox.left : openingBox.top) + i * (each + gap);
            Box box = across
                ? new Box(start, openingBox.top, start + each, openingBox.bottom)
                : new Box(openingBox.left, start, openingBox.right, start + each);
            if (floating) {
                panes.add(new Pane(i, box, uniform(clampRadius(in.corners, box)), nextGroup[0]++,
                    true));
            } else {
                panes.add(new Pane(i, box, cornersAgainst(box, new Box(0f, 0f, w, h),
                    screenCorners.topLeft, eps), frameGroup, false));
                if (i > 0) {
                    dividers.add(across
                        ? new Divider(start, openingBox.top, start, openingBox.bottom,
                            in.dividerThickness)
                        : new Divider(openingBox.left, start, openingBox.right, start,
                            in.dividerThickness));
                }
            }
        }
        return new ChromeShape(pieces, cards, opening, panes, dividers);
    }

    /**
     * The shape {@code element} would have if dropped at {@code edge} at stack position
     * {@code index}, with everything it displaces reflowed around it: what a lifted copy takes on
     * while it hovers that target, and the dashed placeholder draws. {@code index} counts from the
     * screen edge inwards as {@link EdgeStackPolicy#withDrop} does, within the side of the
     * keyboard it lands on. Never null: a dropped element is shown.
     */
    @NonNull
    public static Piece shapeIfDropped(@NonNull Input in, @NonNull Element element,
                                       @NonNull Edge edge, int index, boolean underKeyboard) {
        ChromeShape dropped = shape(in.withLayout(
            EdgeStackPolicy.withDrop(in.layout, element, edge, index, underKeyboard)));
        Piece piece = dropped.piece(PieceId.of(element));
        if (piece == null) throw new IllegalStateException("a dropped element is shown: " + element);
        return piece;
    }

    // ---------------------------------------------------------------- bands and cards

    @NonNull
    private static List<Band> bandsOf(@NonNull List<Element> stack, @NonNull Edge edge,
                                      @NonNull Input in) {
        List<Band> bands = new ArrayList<>(stack.size());
        for (Element element : stack)
            bands.add(new Band(PieceId.of(element), in.thickness.of(element, edge), false, false));
        return bands;
    }

    /** Groups a stack's bands into the cards they share, outermost first. */
    @NonNull
    private static List<Run> cardsOf(@NonNull List<Band> bands, boolean floating) {
        List<Run> runs = new ArrayList<>(bands.size());
        if (!floating) {
            Run all = new Run();
            all.bands.addAll(bands);
            if (!bands.isEmpty()) runs.add(all);
        } else {
            Run current = null;
            for (Band band : bands) {
                boolean alone = band.keyboard || band.id == PieceId.STATUS;
                boolean lastAlone = current != null
                    && (current.bands.get(current.bands.size() - 1).keyboard
                    || current.bands.get(current.bands.size() - 1).id == PieceId.STATUS);
                if (current == null || alone || lastAlone) {
                    current = new Run();
                    runs.add(current);
                }
                current.bands.add(band);
            }
        }
        return runs;
    }

    /**
     * Places a stack's rows, from the top edge down or from the bottom edge up, and returns where
     * the next thing starts: past the last card and the air after it.
     */
    private static float placeRows(@NonNull List<Run> runs, boolean fromTop, float cursor, float w,
                                   float m, boolean floating, int frameGroup, @NonNull int[] group,
                                   @NonNull Input in, @NonNull List<Cand> out) {
        float x0 = m;
        float x1 = w - m;
        for (Run run : runs) {
            int card = floating ? group[0]++ : frameGroup;
            for (Band band : run.bands) {
                float t = band.thickness;
                float yStart = fromTop ? cursor : cursor - t;
                float yEnd = yStart + t;
                if (band.split) {
                    float half = splitHalf(in, w, m, floating);
                    int splitCard = floating ? group[0]++ : frameGroup;
                    out.add(new Cand(PieceId.KEYBOARD_LEFT, new Box(x0, yStart, x0 + half, yEnd),
                        card, false));
                    out.add(new Cand(PieceId.KEYBOARD_RIGHT, new Box(x1 - half, yStart, x1, yEnd),
                        splitCard, false));
                } else {
                    out.add(new Cand(band.id, new Box(x0, yStart, x1, yEnd), card, false));
                }
                cursor = fromTop ? yEnd : yStart;
            }
            cursor = fromTop ? cursor + m : cursor - m;
        }
        return cursor;
    }

    /** Places a side stack's columns, from the left edge in or from the right edge in. */
    private static float placeColumns(@NonNull List<Run> runs, boolean fromLeft, float cursor,
                                      float yTop, float yBottom, float m, boolean floating,
                                      int frameGroup, @NonNull int[] group,
                                      @NonNull List<Cand> out) {
        float y1 = Math.max(yTop, yBottom);
        for (Run run : runs) {
            int card = floating ? group[0]++ : frameGroup;
            for (Band band : run.bands) {
                float t = band.thickness;
                float xStart = fromLeft ? cursor : cursor - t;
                float xEnd = xStart + t;
                out.add(new Cand(band.id, new Box(xStart, yTop, xEnd, y1), card, false));
                cursor = fromLeft ? xEnd : xStart;
            }
            cursor = fromLeft ? cursor + m : cursor - m;
        }
        return cursor;
    }

    private static float splitHalf(@NonNull Input in, float w, float m, boolean floating) {
        float most = floating ? (w - 3f * m) / 2f : w / 2f;
        float half = in.splitHalfWidth > 0f ? in.splitHalfWidth : 0.4f * w;
        return Math.max(0f, Math.min(half, most));
    }

    @NonNull
    private static Box defaultOverlayBox(@NonNull Box opening, @NonNull Input in, float m) {
        float width = in.floatingKeyboardWidth > 0f ? in.floatingKeyboardWidth
            : 0.9f * opening.width();
        width = Math.min(width, opening.width());
        float height = in.thickness.keyboard();
        float centre = (opening.left + opening.right) / 2f;
        float bottom = opening.bottom - m;
        return new Box(centre - width / 2f, bottom - height, centre + width / 2f, bottom);
    }

    // ---------------------------------------------------------------- corners

    private static float clampRadius(float radius, @NonNull Box box) {
        return Math.max(0f, Math.min(radius, Math.min(box.width(), box.height()) / 2f));
    }

    @NonNull
    private static Corners uniform(float radius) {
        return new Corners(radius, radius, radius, radius);
    }

    /**
     * A box's corners given the outline it sits in: round where its corner is one of the
     * outline's, square everywhere else. The radius is the outline's own, not the box's: a thin
     * bar at a screen corner carries the frame's radius and is drawn under the frame's clip.
     */
    @NonNull
    private static Corners cornersAgainst(@NonNull Box box, @NonNull Box outline, float r,
                                          float eps) {
        boolean left = Math.abs(box.left - outline.left) <= eps;
        boolean right = Math.abs(box.right - outline.right) <= eps;
        boolean top = Math.abs(box.top - outline.top) <= eps;
        boolean bottom = Math.abs(box.bottom - outline.bottom) <= eps;
        return new Corners(left && top ? r : 0f, right && top ? r : 0f,
            right && bottom ? r : 0f, left && bottom ? r : 0f);
    }

    // ---------------------------------------------------------------- edges

    private static final Edge[] EDGES = {Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT};

    @NonNull
    private static List<EdgeRun> runsOf(@NonNull Cand c, @NonNull List<Cand> others,
                                        boolean floating, float w, float h, float eps) {
        List<EdgeRun> runs = new ArrayList<>(6);
        for (Edge edge : EDGES) {
            if (c.overlay) {
                runs.add(new EdgeRun(edge, spanStart(c.box, edge), spanEnd(c.box, edge),
                    EdgeKind.RIM));
                continue;
            }
            runs.addAll(edgeRuns(c, edge, others, floating, w, h, eps));
        }
        return runs;
    }

    @NonNull
    private static List<EdgeRun> edgeRuns(@NonNull Cand c, @NonNull Edge edge,
                                          @NonNull List<Cand> others, boolean floating, float w,
                                          float h, float eps) {
        float line = coord(c.box, edge);
        float from = spanStart(c.box, edge);
        float to = spanEnd(c.box, edge);
        List<EdgeRun> runs = new ArrayList<>(3);
        if (to - from <= eps) return runs;
        if (!floating && onScreenBoundary(edge, line, w, h, eps)) {
            runs.add(new EdgeRun(edge, from, to, EdgeKind.SCREEN));
            return runs;
        }
        List<float[]> joins = new ArrayList<>(3);
        for (Cand o : others) {
            if (o == c || o.overlay) continue;
            if (floating && o.group != c.group) continue;
            if (Math.abs(coord(o.box, opposite(edge)) - line) > eps) continue;
            float s = Math.max(from, spanStart(o.box, edge));
            float e = Math.min(to, spanEnd(o.box, edge));
            if (e - s > eps) joins.add(new float[] {s, e});
        }
        Collections.sort(joins, (a, b) -> Float.compare(a[0], b[0]));
        EdgeKind rest = floating ? EdgeKind.RIM : EdgeKind.OPENING;
        float cursor = from;
        for (float[] join : joins) {
            if (join[1] <= cursor + eps) continue;
            if (join[0] - cursor > eps) addRun(runs, edge, cursor, join[0], rest, eps);
            addRun(runs, edge, Math.max(cursor, join[0]), join[1], EdgeKind.JOIN, eps);
            cursor = join[1];
        }
        if (to - cursor > eps) addRun(runs, edge, cursor, to, rest, eps);
        return runs;
    }

    /** Appends a run, folding it into the previous one when they are the same kind and touch. */
    private static void addRun(@NonNull List<EdgeRun> runs, @NonNull Edge edge, float from,
                               float to, @NonNull EdgeKind kind, float eps) {
        if (!runs.isEmpty()) {
            EdgeRun last = runs.get(runs.size() - 1);
            if (last.kind == kind && Math.abs(last.to - from) <= eps) {
                runs.set(runs.size() - 1, new EdgeRun(edge, last.from, to, kind));
                return;
            }
        }
        runs.add(new EdgeRun(edge, from, to, kind));
    }

    @NonNull
    private static Opening openingOf(@NonNull Box box, @NonNull List<Cand> frame,
                                     boolean floating, @NonNull Input in, float w, float h,
                                     float eps, @NonNull Corners screenCorners) {
        if (floating) {
            return new Opening(box, uniform(clampRadius(in.corners, box)), true, EdgeKind.RIM,
                EdgeKind.RIM, EdgeKind.RIM, EdgeKind.RIM);
        }
        return new Opening(box, cornersAgainst(box, new Box(0f, 0f, w, h), screenCorners.topLeft,
            eps), false,
            openingEdge(box, Edge.TOP, frame, eps),
            openingEdge(box, Edge.BOTTOM, frame, eps),
            openingEdge(box, Edge.LEFT, frame, eps),
            openingEdge(box, Edge.RIGHT, frame, eps));
    }

    /** A bar's inner edge runs along this side of the opening, or else the screen's does. */
    @NonNull
    private static EdgeKind openingEdge(@NonNull Box box, @NonNull Edge edge,
                                        @NonNull List<Cand> frame, float eps) {
        float line = coord(box, edge);
        float from = spanStart(box, edge);
        float to = spanEnd(box, edge);
        for (Cand o : frame) {
            if (Math.abs(coord(o.box, opposite(edge)) - line) > eps) continue;
            if (Math.min(to, spanEnd(o.box, edge)) - Math.max(from, spanStart(o.box, edge)) > eps)
                return EdgeKind.JOIN;
        }
        return EdgeKind.SCREEN;
    }

    private static boolean onScreenBoundary(@NonNull Edge edge, float line, float w, float h,
                                            float eps) {
        switch (edge) {
            case TOP:
            case LEFT:
                return Math.abs(line) <= eps;
            case BOTTOM:
                return Math.abs(line - h) <= eps;
            case RIGHT:
            default:
                return Math.abs(line - w) <= eps;
        }
    }

    private static float coord(@NonNull Box b, @NonNull Edge edge) {
        switch (edge) {
            case TOP: return b.top;
            case BOTTOM: return b.bottom;
            case LEFT: return b.left;
            case RIGHT:
            default: return b.right;
        }
    }

    private static float spanStart(@NonNull Box b, @NonNull Edge edge) {
        return edge.isOnSide() ? b.top : b.left;
    }

    private static float spanEnd(@NonNull Box b, @NonNull Edge edge) {
        return edge.isOnSide() ? b.bottom : b.right;
    }

    @NonNull
    private static Edge opposite(@NonNull Edge edge) {
        switch (edge) {
            case TOP: return Edge.BOTTOM;
            case BOTTOM: return Edge.TOP;
            case LEFT: return Edge.RIGHT;
            case RIGHT:
            default: return Edge.LEFT;
        }
    }
}
