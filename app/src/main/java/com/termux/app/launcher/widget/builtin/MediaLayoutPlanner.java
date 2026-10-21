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

    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetSpan.FOUR_BY_TWO;

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
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, WidgetTokens.insetDp(span));
        List<Part> parts = new ArrayList<>();
        if (span == BuiltinWidgetSpan.ONE_BY_ONE) {
            parts.add(Part.touch("art", inner));
            float side = Math.min(CONTROL_DP, Math.min(inner.width, inner.height));
            parts.add(Part.touch("play", new Box(inner.centerX() - side / 2f,
                inner.centerY() - side / 2f, side, side)));
        } else {
            tall(span, inner, fontScale, content, parts);
        }
        return new WidgetLayoutPlan(BuiltinWidgetKind.MEDIA, span, MAX_SPAN, fontScale, card, inner,
            parts);
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
                parts.add(Part.touch("art", new Box(inner.left,
                    inner.top + (inner.height - side) / 2f, side, side)));
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
