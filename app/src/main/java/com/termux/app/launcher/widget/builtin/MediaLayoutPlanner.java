package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Ink;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Measure;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;
import com.termux.app.launcher.widget.builtin.WidgetTokens.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * The Media widget's layout plan at every span (spec #101, ticket #119). Words are drawn text
 * parts; the art, the transport buttons, the progress bar and the clock labels are TOUCH parts
 * whose views the widget places in their boxes ({@link WidgetPlanHost}). Every transport button
 * is a 48 dp target; the art is cut at the inner radius by its view. Where the room is short, the
 * least useful line goes first: the art of a 2×2, then the progress, the source and the artist.
 * Three modes: a session is playing or paused, nothing is playing but there is a last app to
 * resume, or there is neither. Android-free.
 *
 * <p>Part ids: text {@code source}, {@code title}, {@code artist}; views {@code art}, {@code bar},
 * {@code elapsed}, {@code total}, {@code prev}, {@code play}, {@code next}.</p>
 */
final class MediaLayoutPlanner {
    private MediaLayoutPlanner() { }

    /** The kind declares it ({@link BuiltinWidgetKind#maxSpan}); past it the widget only gains room. */
    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetKind.MEDIA.maxSpan;

    /** What the card is showing. */
    enum Mode { PLAYING, RESUME, IDLE }

    /** What the plan is built from. Strings are already the words to show. */
    static final class Content {
        @NonNull final Mode mode;
        /** The track, "Resume Spotify" or "Nothing playing". */
        @NonNull final String title;
        @NonNull final String artist;
        /** The app's name, for the 4×2's source line. */
        @NonNull final String source;
        /** Whether the track's length is known, so a progress bar makes sense. */
        final boolean hasProgress;

        Content(@NonNull Mode mode, @NonNull String title, @NonNull String artist,
                @NonNull String source, boolean hasProgress) {
            this.mode = mode; this.title = title; this.artist = artist; this.source = source;
            this.hasProgress = hasProgress;
        }
    }

    private static final float CONTROL_DP = WidgetTokens.TOUCH_TARGET_DP;
    /** The art is not drawn smaller than this. */
    static final float ART_MIN_DP = 40f;
    private static final float BAR_DP = 4f;
    private static final float GAP_S = WidgetTokens.GAP_TIGHT_DP;
    private static final float GAP_M = WidgetTokens.GAP_DP;
    private static final float GAP_L = WidgetTokens.GAP_LOOSE_DP;

    private static final int RANK_ESSENTIAL = Integer.MAX_VALUE;
    private static final int RANK_TITLE = 90;
    private static final int RANK_ARTIST = 30;
    private static final int RANK_SOURCE = 20;
    private static final int RANK_PROGRESS = 15;

    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale,
                                          @NonNull Content content, @NonNull Measure measure) {
        Box card = new Box(0f, 0f, widthDp, heightDp);
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, insetDp(span, widthDp, heightDp));
        List<Part> parts = new ArrayList<>();
        if (span == BuiltinWidgetSpan.ONE_BY_ONE) {
            parts.add(Part.touch("art", inner));
            float side = Math.min(CONTROL_DP, Math.min(inner.width, inner.height));
            parts.add(Part.touch("play", new Box(inner.centerX() - side / 2f,
                inner.centerY() - side / 2f, side, side)));
        } else if (span.rows == 2) {
            if (isPolaroid(span, widthDp, heightDp)) {
                polaroid(inner, fontScale, content, measure, parts);
            } else {
                artAndColumn(inner, fontScale, content, span == BuiltinWidgetSpan.TWO_BY_TWO, parts);
            }
        } else {
            tall(span, inner, fontScale, content, parts);
        }
        return new WidgetLayoutPlan(BuiltinWidgetKind.MEDIA, span, MAX_SPAN, fontScale, card, inner,
            parts);
    }

    /** The text column beside the art is never narrower than this. */
    private static final float TEXT_BESIDE_MIN_DP = 80f;
    /** The clock labels sit under the bar only where the card is this wide. */
    private static final float CLOCKS_MIN_WIDTH_DP = 300f;
    /** The 2x2's thin frame: the polaroid's border around the art. */
    static final int POLAROID_INSET_DP = 8;
    /** The polaroid's progress bar is held in from the frame by this much on each side. */
    private static final float POLAROID_BAR_INSET_DP = 6f;
    /** Slack added to a measured line so rounding never ellipsizes it. */
    private static final float MEASURE_SLACK_DP = 1f;

    /** A 2x2-span card is a polaroid while its inner height is at least this share of its inner width. */
    static final float POLAROID_MIN_ASPECT = 0.85f;

    /**
     * Whether a card of this size is the polaroid: a 2x2 span whose room, inside the thin frame,
     * is upright. A wider card of the same span takes the art-and-column layout of the 4x2.
     */
    static boolean isPolaroid(@NonNull BuiltinWidgetSpan span, float widthDp, float heightDp) {
        if (span != BuiltinWidgetSpan.TWO_BY_TWO) return false;
        float innerWidth = widthDp - 2f * POLAROID_INSET_DP;
        float innerHeight = heightDp - 2f * POLAROID_INSET_DP;
        return innerHeight >= POLAROID_MIN_ASPECT * innerWidth;
    }

    /** The content inset of the Media card: thin on a polaroid, the shared one elsewhere. */
    static int insetDp(@NonNull BuiltinWidgetSpan span, float widthDp, float heightDp) {
        return isPolaroid(span, widthDp, heightDp) ? POLAROID_INSET_DP : WidgetTokens.insetDp(span);
    }

    /**
     * The 2x2 as a polaroid: a square art across the top inside a thin frame, and below it the
     * title, the artist, the progress bar and the controls, centred. Where the room is short the
     * art shrinks first (square, centred); under its floor the artist, the progress and the title
     * go, in that order, and past that the art goes and the rest centres vertically.
     */
    private static void polaroid(@NonNull Box inner, float fontScale, @NonNull Content content,
                                 @NonNull Measure measure, @NonNull List<Part> parts) {
        boolean resume = content.mode == Mode.RESUME;
        boolean playing = content.mode == Mode.PLAYING;
        float titleSp = WidgetTokens.sizeDp(Role.TITLE, fontScale);
        float labelSp = WidgetTokens.sizeDp(Role.LABEL, fontScale);
        float titleH = WidgetTokens.lineDp(titleSp);
        float labelH = WidgetTokens.lineDp(labelSp);
        boolean progress = playing && content.hasProgress;
        boolean artist = playing && !content.artist.isEmpty();
        boolean title = true;
        float control = Math.min(CONTROL_DP, inner.height);

        float band = 0f;
        float side = 0f;
        for (int attempt = 0; attempt < 4; attempt++) {
            band = (title ? titleH + GAP_S : 0f) + (artist ? labelH + GAP_S : 0f)
                + (progress ? BAR_DP + GAP_M : 0f) + control;
            side = Math.min(inner.width, inner.height - GAP_M - band);
            if (side >= ART_MIN_DP) break;
            if (artist) artist = false;
            else if (progress) progress = false;
            else if (title) title = false;
        }
        boolean art = side >= ART_MIN_DP;
        float y;
        if (art) {
            parts.add(Part.touch("art", new Box(inner.centerX() - side / 2f, inner.top, side, side)));
            // The band rides at the foot; the art takes what it leaves.
            y = inner.bottom() - band;
        } else {
            band = (title ? titleH + GAP_S : 0f) + (artist ? labelH + GAP_S : 0f)
                + (progress ? BAR_DP + GAP_M : 0f) + control;
            y = inner.top + Math.max(0f, (inner.height - band) / 2f);
        }
        if (title) {
            parts.add(centred("title", inner, y, titleH, content.title, Role.TITLE, titleSp,
                Ink.STRONG, measure));
            y += titleH + GAP_S;
        }
        if (artist) {
            parts.add(centred("artist", inner, y, labelH, content.artist, Role.LABEL, labelSp,
                Ink.SOFT, measure));
            y += labelH + GAP_S;
        }
        if (progress) {
            float barWidth = Math.max(0f, inner.width - 2f * POLAROID_BAR_INSET_DP);
            parts.add(Part.touch("bar", new Box(inner.left + POLAROID_BAR_INSET_DP, y, barWidth,
                BAR_DP)));
            y += BAR_DP + GAP_M;
        }
        controlsRow(parts, inner.left, inner.width, y, control, resume);
    }

    /** A line of text centred across {@code inner}: its box is as wide as the words, or the inner width. */
    @NonNull private static Part centred(@NonNull String id, @NonNull Box inner, float top, float height,
                                         @NonNull String text, @NonNull Role role, float sizeDp,
                                         @NonNull Ink ink, @NonNull Measure measure) {
        float width = Math.min(inner.width,
            measure.widthDp(text, role, false, sizeDp) + MEASURE_SLACK_DP);
        return Part.text(id, new Box(inner.centerX() - width / 2f, top, width, height), text, role,
            false, sizeDp, ink);
    }

    /** The controls are shrunk toward this before prev and next are dropped. */
    private static final float CONTROL_MIN_DP = 40f;

    /**
     * Prev, play and next centred in {@code width}: three at up to {@code control} each, then
     * three shrunk toward {@link #CONTROL_MIN_DP}, then play alone. The row never overflows.
     */
    private static void controlsRow(@NonNull List<Part> parts, float left, float width, float top,
                                    float control, boolean resume) {
        float centre = left + width / 2f;
        float size = Math.min(control, width / 3f);
        if (resume || size < CONTROL_MIN_DP - 0.01f) {
            float play = Math.min(control, width);
            parts.add(Part.touch("play", new Box(centre - play / 2f, top + (control - play) / 2f,
                play, play)));
            return;
        }
        float y = top + (control - size) / 2f;
        float x = centre - 1.5f * size;
        parts.add(Part.touch("prev", new Box(x, y, size, size)));
        parts.add(Part.touch("play", new Box(x + size, y, size, size)));
        parts.add(Part.touch("next", new Box(x + 2f * size, y, size, size)));
    }

    /**
     * The 4×2: the art square at the card's inner height on the start side, and beside it a
     * column of words (source, title, artist) over a foot of progress and the controls. The
     * clocks sit under the bar only where the column is wide enough. Where the column is short
     * the clocks, the source, the progress and the artist go, in that order.
     */
    private static void artAndColumn(@NonNull Box inner, float fontScale, @NonNull Content content,
                                     boolean squareArt, @NonNull List<Part> parts) {
        boolean resume = content.mode == Mode.RESUME;
        boolean playing = content.mode == Mode.PLAYING;
        float titleSp = WidgetTokens.sizeDp(Role.TITLE, fontScale);
        float labelSp = WidgetTokens.sizeDp(Role.LABEL, fontScale);
        float titleH = WidgetTokens.lineDp(titleSp);
        float labelH = WidgetTokens.lineDp(labelSp);

        // A wide 2x2 keeps room in its column for three controls and a square art; the 4x2's art
        // is as tall as the card and the column's width comes first.
        float columnMin = squareArt ? Math.max(TEXT_BESIDE_MIN_DP, 3f * CONTROL_MIN_DP)
            : TEXT_BESIDE_MIN_DP;
        float side = Math.min(inner.height, inner.width - GAP_L - columnMin);
        Box column = inner;
        if (side >= ART_MIN_DP) {
            // The 4x2: square at the inner height; a tall card narrower than that gives the column
            // its width first and the art is cut to the narrower box. The 2x2: a square, centred.
            Box artBox = squareArt
                ? new Box(inner.left, inner.top + (inner.height - side) / 2f, side, side)
                : new Box(inner.left, inner.top, side, inner.height);
            parts.add(Part.touch("art", artBox));
            column = new Box(inner.left + side + GAP_L, inner.top,
                inner.width - side - GAP_L, inner.height);
        }
        boolean progress = playing && content.hasProgress;
        boolean clocks = progress && column.width >= CLOCKS_MIN_WIDTH_DP;
        boolean source = playing && !content.source.isEmpty();
        boolean artist = playing && !content.artist.isEmpty();
        float control = Math.min(CONTROL_DP, column.height);

        float footer = 0f;
        float textH = 0f;
        for (int attempt = 0; attempt < 5; attempt++) {
            footer = control + (progress ? GAP_M + BAR_DP + (clocks ? GAP_S + labelH : 0f) : 0f);
            textH = titleH + (artist ? GAP_S + labelH : 0f) + (source ? GAP_M + labelH : 0f);
            if (textH + GAP_M <= column.height - footer + 0.01f) break;
            if (clocks) clocks = false;
            else if (source) source = false;
            else if (progress) progress = false;
            else if (artist) artist = false;
        }
        boolean title = textH + GAP_M <= column.height - footer + 0.01f;
        if (!title) { source = false; artist = false; }

        float y = column.top;
        if (source && title) {
            parts.add(Part.text("source", new Box(column.left, y, column.width, labelH),
                content.source, Role.LABEL, false, labelSp, Ink.SOFT));
            y += labelH + GAP_M;
        }
        if (title) {
            parts.add(Part.text("title", new Box(column.left, y, column.width, titleH),
                content.title, Role.TITLE, false, titleSp, Ink.STRONG));
        }
        y += titleH;
        if (artist) {
            parts.add(Part.text("artist", new Box(column.left, y + GAP_S, column.width, labelH),
                content.artist, Role.LABEL, false, labelSp, Ink.SOFT));
        }
        float footTop = column.bottom() - footer;
        if (progress) {
            parts.add(Part.touch("bar", new Box(column.left, footTop, column.width, BAR_DP)));
            if (clocks) {
                float clockTop = footTop + BAR_DP + GAP_S;
                float half = column.width / 2f;
                parts.add(Part.touch("elapsed", new Box(column.left, clockTop, half, labelH)));
                parts.add(Part.touch("total", new Box(column.left + half, clockTop, half, labelH)));
            }
        }
        controlsRow(parts, column.left, column.width, column.bottom() - control, control, resume);
    }

    /** One row of the text-and-controls column. */
    private static final class Item {
        final String id;
        final int rank;
        final float height;
        float gapAbove;
        boolean kept = true;
        float top;

        Item(String id, int rank, float height, float gapAbove) {
            this.id = id; this.rank = rank; this.height = height; this.gapAbove = gapAbove;
        }
    }

    private static void tall(@NonNull BuiltinWidgetSpan span, @NonNull Box inner, float fontScale,
                             @NonNull Content content, @NonNull List<Part> parts) {
        boolean resume = content.mode == Mode.RESUME;
        boolean artBeside = span != BuiltinWidgetSpan.TWO_BY_TWO;
        int wanted = resume ? 1 : span.columns == 2 && span.rows == 1 ? 2 : 3;

        // The art beside the column: square to the card's height while the column keeps its controls.
        Box column = inner;
        if (artBeside) {
            float side = Math.min(inner.height, inner.width - GAP_L - wanted * CONTROL_DP);
            if (side >= ART_MIN_DP) {
                // As tall as the card; narrower than that when the column needs the width.
                parts.add(Part.touch("art", new Box(inner.left, inner.top, side, inner.height)));
                column = new Box(inner.left + side + GAP_L, inner.top,
                    inner.width - side - GAP_L, inner.height);
            }
        }
        int buttons = resume ? 1
            : Math.max(1, Math.min(wanted, (int) Math.floor((column.width + 0.01f) / CONTROL_DP)));

        float titleSp = WidgetTokens.sizeDp(Role.TITLE, fontScale);
        float labelSp = WidgetTokens.sizeDp(Role.LABEL, fontScale);
        float titleH = WidgetTokens.lineDp(titleSp);
        float labelH = WidgetTokens.lineDp(labelSp);
        boolean playing = content.mode == Mode.PLAYING;
        boolean mergedArtist = span == BuiltinWidgetSpan.FOUR_BY_ONE && !content.artist.isEmpty()
            && playing;
        String titleText = mergedArtist ? content.title + " · " + content.artist : content.title;
        boolean artistLine = playing && !content.artist.isEmpty() && !mergedArtist;
        boolean progress = playing && content.hasProgress && span.rows == 2;
        boolean clocks = progress && span == BuiltinWidgetSpan.FOUR_BY_TWO;
        boolean source = playing && span == BuiltinWidgetSpan.FOUR_BY_TWO
            && !content.source.isEmpty();

        List<Item> items = new ArrayList<>();
        if (source) items.add(new Item("source", RANK_SOURCE, labelH, 0f));
        items.add(new Item("title", RANK_TITLE, titleH, source ? GAP_M : 0f));
        if (artistLine) items.add(new Item("artist", RANK_ARTIST, labelH, GAP_S));
        if (progress) {
            items.add(new Item("progress", RANK_PROGRESS,
                clocks ? BAR_DP + GAP_S + labelH : BAR_DP, GAP_M));
        }
        float control = Math.min(CONTROL_DP, column.height);
        items.add(new Item("controls", RANK_ESSENTIAL, control, GAP_M));

        float room = column.height;
        while (used(items) > room + 0.01f) {
            Item drop = null;
            for (Item item : items) {
                if (item.kept && item.rank < RANK_TITLE && (drop == null || item.rank < drop.rank)) {
                    drop = item;
                }
            }
            if (drop == null) break;
            drop.kept = false;
        }
        // Still too tall (one row): the controls sit against the title before the title goes,
        // and the title goes before the controls do (a large font on one row).
        if (used(items) > room + 0.01f) {
            for (Item item : items) if (item.id.equals("controls")) item.gapAbove = 0f;
        }
        if (used(items) > room + 0.01f) {
            for (Item item : items) if (item.id.equals("title")) item.kept = false;
        }

        float used = used(items);
        // A 2x2's art takes what the rest leaves, if that is enough to show it.
        float artHeight = artBeside ? 0f : room - used - GAP_M;
        boolean artAbove = !artBeside && artHeight >= ART_MIN_DP;
        float y = column.top;
        if (artAbove) {
            parts.add(Part.touch("art", new Box(column.left, y, column.width, artHeight)));
            y += artHeight + GAP_M;
        } else if (artBeside) {
            y += Math.max(0f, (room - used) / 2f);
        }
        boolean first = true;
        for (Item item : items) {
            if (!item.kept) continue;
            y += first ? 0f : item.gapAbove;
            item.top = y;
            y += item.height;
            first = false;
        }

        for (Item item : items) {
            if (!item.kept) continue;
            Box line = new Box(column.left, item.top, column.width, item.height);
            switch (item.id) {
                case "source":
                    parts.add(Part.text("source", line, content.source, Role.LABEL, false, labelSp,
                        Ink.SOFT));
                    break;
                case "title":
                    parts.add(Part.text("title", line, titleText, Role.TITLE, false, titleSp,
                        Ink.STRONG));
                    break;
                case "artist":
                    parts.add(Part.text("artist", line, content.artist, Role.LABEL, false, labelSp,
                        Ink.SOFT));
                    break;
                case "progress":
                    parts.add(Part.touch("bar", new Box(column.left, item.top, column.width, BAR_DP)));
                    if (clocks) {
                        float top = item.top + BAR_DP + GAP_S;
                        float half = column.width / 2f;
                        parts.add(Part.touch("elapsed", new Box(column.left, top, half, labelH)));
                        parts.add(Part.touch("total", new Box(column.left + half, top, half, labelH)));
                    }
                    break;
                case "controls":
                    controls(parts, column, item.top, control, buttons, resume, artBeside);
                    break;
                default:
                    break;
            }
        }
    }

    /** The height the kept items use, gaps included. */
    private static float used(@NonNull List<Item> items) {
        float used = 0f;
        boolean first = true;
        for (Item item : items) {
            if (!item.kept) continue;
            used += first ? 0f : item.gapAbove;
            used += item.height;
            first = false;
        }
        return used;
    }

    private static void controls(@NonNull List<Part> parts, @NonNull Box column, float top, float control,
                                 int buttons, boolean resume, boolean artBeside) {
        if (resume) {
            parts.add(Part.touch("play", new Box(column.right() - control, top, control,
                control)));
            return;
        }
        float x = artBeside ? column.left : column.centerX() - buttons * control / 2f;
        if (buttons >= 3) {
            parts.add(Part.touch("prev", new Box(x, top, control, control)));
            x += control;
        }
        parts.add(Part.touch("play", new Box(x, top, control, control)));
        x += control;
        if (buttons >= 2) parts.add(Part.touch("next", new Box(x, top, control, control)));
    }
}
