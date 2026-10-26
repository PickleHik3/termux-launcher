package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.builtin.NotificationsWidgetData.Group;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Ink;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Measure;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;
import com.termux.app.launcher.widget.builtin.WidgetTokens.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * The Notifications widget's layout plan (spec #101, "Notifications"). A one-row span shows the
 * newest row — an app icon, the app's name in the Title weight and a body of two lines; a
 * two-row span adds a header ("Messages 5") and one row per person or app: icon, sender in the
 * Title weight, a count badge where there is more than one, and a body of two lines. A body
 * takes every line it has, up to two; where the room is short the second line goes first, then
 * the title, then whole rows from the bottom. The smallest span offered is 2×1; an existing
 * 1×1 draws a glance, legible and never scaled: a bell with the count and the top sender's name.
 * Android-free.
 *
 * <p>Part ids: {@code icon.N}, {@code badge.N} (where the badge sits, drawn by the view) with
 * {@code count.N} (its number), {@code title.N}, {@code body.N.M}, {@code rule.N}, {@code row.N}
 * (where a tap opens the app), {@code header.title}, {@code header.count}, {@code clear} with
 * {@code clear.touch}, and {@code glyph}, {@code message.N}, {@code hint} for a notice.</p>
 */
final class NotificationsLayoutPlanner {
    private NotificationsLayoutPlanner() { }

    /** The kind declares it ({@link BuiltinWidgetKind#maxSpan}); past it the widget only gains room. */
    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetKind.NOTIFICATIONS.maxSpan;

    /** The bell of a notice and of an icon that cannot be drawn. */
    static final String GLYPH_BELL = "";

    private static final float ICON_GLANCE_DP = 24f;

    private static final int MAX_BODY_LINES = 2;
    private static final float ICON_ROW_DP = 36f;
    private static final float ICON_LIST_DP = 28f;
    private static final float NOTICE_GLYPH_DP = 24f;
    private static final float RULE_DP = 1f;
    /** The narrowest a column of text beside an icon is kept at, or the icon goes. */
    private static final float TEXT_COLUMN_MIN_DP = 72f;
    private static final float TOUCH_DP = WidgetTokens.TOUCH_TARGET_DP;

    private static final float GAP_S = WidgetTokens.GAP_TIGHT_DP;
    private static final float GAP_M = WidgetTokens.GAP_DP;
    private static final float GAP_L = WidgetTokens.GAP_LOOSE_DP;

    /** The words the plan shows, from the app's strings; a test passes English ones. */
    interface Words {
        /** "Messages", over a list that starts with people. */
        @NonNull String headerMessages();
        /** "Notifications", over a list with no message in it. */
        @NonNull String headerNotifications();
        /** "Clear all". */
        @NonNull String clearAll();
        /** A count as shown: "3", "99+". */
        @NonNull String count(int count);
    }

    /** What a card with nothing to list says: a bell, a message, and perhaps a hint under it. */
    static final class Notice {
        @NonNull final String message;
        @NonNull final String hint;

        Notice(@NonNull String message, @NonNull String hint) {
            this.message = message; this.hint = hint;
        }
    }

    /**
     * The plan for {@code span} in a {@code widthDp}×{@code heightDp} card at {@code fontScale}.
     * {@code notice} is drawn when it is not null or {@code groups} is empty; {@code canClear}
     * offers Clear all on the wide two-row span.
     */
    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale,
                                          @NonNull List<Group> groups, @Nullable Notice notice,
                                          boolean canClear, @NonNull Words words,
                                          @NonNull Measure measure) {
        BuiltinWidgetSpan drawn = span;
        Box card = new Box(0f, 0f, widthDp, heightDp);
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, WidgetTokens.insetDp(drawn));
        Builder b = new Builder(drawn, fontScale, words, measure);
        if (notice != null || groups.isEmpty()) {
            b.notice(inner, notice == null ? new Notice("", "") : notice);
        } else if (drawn == BuiltinWidgetSpan.ONE_BY_ONE) {
            b.glance(inner, groups);
        } else if (drawn.isTall()) {
            b.tall(inner, groups, canClear && drawn.isWide());
        } else {
            b.oneRow(inner, groups.get(0));
        }
        // The whole card opens the history; a row opens its app.
        b.parts.add(Part.touch("touch", card));
        return new WidgetLayoutPlan(BuiltinWidgetKind.NOTIFICATIONS, span, MAX_SPAN, fontScale,
            card, inner, b.parts);
    }

    private static final class Builder {
        @NonNull final BuiltinWidgetSpan span;
        final float fontScale;
        @NonNull final Words words;
        @NonNull final Measure measure;
        final List<Part> parts = new ArrayList<>();

        Builder(@NonNull BuiltinWidgetSpan span, float fontScale, @NonNull Words words,
                @NonNull Measure measure) {
            this.span = span; this.fontScale = fontScale; this.words = words; this.measure = measure;
        }

        float size(@NonNull Role role) { return WidgetTokens.sizeDp(role, fontScale); }

        float line(@NonNull Role role) { return WidgetTokens.lineDp(size(role)); }

        float width(@NonNull String text, @NonNull Role role, boolean numeric) {
            return measure.widthDp(text, role, numeric, size(role));
        }

        void text(@NonNull String id, @NonNull String text, @NonNull Role role, boolean numeric,
                  @NonNull Ink ink, float left, float top, float room) {
            float w = Math.min(width(text, role, numeric), Math.max(0f, room));
            parts.add(Part.text(id, new Box(left, top, w, line(role)), text, role, numeric,
                size(role), ink));
        }

        /** The text of a notification's body: "Mum: are you coming?" for a person's message. */
        @NonNull static String body(@NonNull Group g, boolean withSender) {
            String text = g.newest.text;
            if (!g.person() || !withSender) return text.isEmpty() ? g.newest.who : text;
            return text.isEmpty() ? g.newest.who : g.newest.who + ": " + text;
        }

        /** A count badge's size in dp: a pill as tall as a label, wide enough for its number. */
        @NonNull float[] badgeSize(@NonNull String count) {
            float h = line(Role.LABEL) + GAP_S;
            return new float[] { Math.max(h, width(count, Role.LABEL, true) + 2f * GAP_M), h };
        }

        /** The badge of group {@code n} with its number, its box top right of {@code rowBox}. */
        void badge(int n, @NonNull String count, float right, float top) {
            float[] size = badgeSize(count);
            parts.add(Part.touch("badge." + n, new Box(right - size[0], top, size[0], size[1])));
            float w = Math.min(width(count, Role.LABEL, true), size[0]);
            parts.add(Part.text("count." + n, new Box(right - (size[0] + w) / 2f,
                top + (size[1] - line(Role.LABEL)) / 2f, w, line(Role.LABEL)), count, Role.LABEL,
                true, size(Role.LABEL), Ink.MOON));
        }

        // ----- 1×1 ------------------------------------------------------------------------

        /**
         * A bell with the count beside it, over the top sender's name; the name goes first where
         * the card is short, then the bell shrinks to what is left.
         */
        void glance(@NonNull Box inner, @NonNull List<Group> groups) {
            int total = 0;
            for (Group g : groups) total += g.count;
            String count = words.count(total);
            Role countRole = line(Role.TITLE) <= inner.height ? Role.TITLE : Role.LABEL_FORCED;
            Group top = groups.get(0);
            String name = top.person() ? top.newest.who : top.newest.app;
            float rowH = Math.min(Math.max(ICON_GLANCE_DP, line(countRole)), inner.height);
            float nameH = line(Role.LABEL);
            boolean showName = rowH + GAP_S + nameH <= inner.height + WidgetLayoutPlan.EPSILON;
            float height = rowH + (showName ? GAP_S + nameH : 0f);
            float y = inner.top + (inner.height - height) / 2f;
            float countFull = width(count, countRole, true);
            float bell = Math.min(Math.min(ICON_GLANCE_DP, rowH),
                Math.max(0f, inner.width - GAP_S - countFull));
            float countW = Math.min(countFull, Math.max(0f, inner.width - bell - GAP_S));
            float rowW = bell + GAP_S + countW;
            float x = inner.left + (inner.width - rowW) / 2f;
            parts.add(Part.glyph("glyph", new Box(x, y + (rowH - bell) / 2f, bell, bell), GLYPH_BELL,
                Ink.STRONG));
            parts.add(Part.text("header.count", new Box(x + bell + GAP_S,
                y + (rowH - line(countRole)) / 2f, countW, line(countRole)), count, countRole, true,
                size(countRole), Ink.STRONG));
            if (showName) {
                float w = Math.min(width(name, Role.LABEL, false), inner.width);
                parts.add(Part.text("title.0", new Box(inner.centerX() - w / 2f, y + rowH + GAP_S, w,
                    nameH), name, Role.LABEL, false, size(Role.LABEL), Ink.SOFT));
            }
        }

        // ----- one row --------------------------------------------------------------------

        /** The body's role: Body, or the forced label where not even one line of it fits. */
        Role bodyRole = Role.BODY;

        void oneRow(@NonNull Box inner, @NonNull Group g) {
            bodyRole = line(Role.BODY) <= inner.height ? Role.BODY : Role.LABEL_FORCED;
            float icon = Math.min(ICON_ROW_DP, inner.height);
            boolean showIcon = inner.width - icon - GAP_L >= TEXT_COLUMN_MIN_DP;
            float left = showIcon ? inner.left + icon + GAP_L : inner.left;
            float room = inner.right() - left;
            String count = g.count > 1 ? words.count(g.count) : "";
            float badgeW = count.isEmpty() ? 0f : badgeSize(count)[0];
            String body = body(g, true);

            boolean title = true;
            int lines = MAX_BODY_LINES;
            List<String> wrapped = WidgetLayoutPlan.wrap(body, room, lines, measure, bodyRole,
                size(bodyRole));
            lines = Math.max(1, wrapped.size());
            // The second line goes first, then the title.
            while (stack(title, lines) > inner.height + WidgetLayoutPlan.EPSILON) {
                if (lines > 1) lines--;
                else if (title) title = false;
                else break;
                wrapped = WidgetLayoutPlan.wrap(body, room, lines, measure, bodyRole, size(bodyRole));
            }
            float height = stack(title, wrapped.size());
            float top = inner.top + (inner.height - height) / 2f;
            if (showIcon) {
                parts.add(Part.touch("icon.0", new Box(inner.left, inner.centerY() - icon / 2f,
                    icon, icon)));
            }
            float y = top;
            if (title) {
                float titleRoom = room - (count.isEmpty() ? 0f : badgeW + GAP_M);
                text("title.0", g.newest.app, Role.TITLE, false, Ink.STRONG, left, y, titleRoom);
                if (!count.isEmpty()) {
                    badge(0, count, inner.right(), y + (line(Role.TITLE) - badgeSize(count)[1]) / 2f);
                }
                y += line(Role.TITLE);
            }
            for (int i = 0; i < wrapped.size(); i++) {
                text("body.0." + i, wrapped.get(i), bodyRole, false, Ink.SOFT, left, y, room);
                y += line(bodyRole);
            }
            parts.add(Part.touch("row.0", new Box(inner.left, top, inner.width, height)));
        }

        float stack(boolean title, int bodyLines) {
            return (title ? line(Role.TITLE) : 0f) + bodyLines * line(bodyRole);
        }

        // ----- two rows -------------------------------------------------------------------

        void tall(@NonNull Box inner, @NonNull List<Group> groups, boolean canClear) {
            bodyRole = line(Role.TITLE) + line(Role.BODY) <= inner.height ? Role.BODY : Role.LABEL_FORCED;
            float icon = ICON_LIST_DP;
            float left = inner.left + icon + GAP_L;
            float room = inner.right() - left;
            float headerH = line(Role.TITLE);
            float headerBlock = headerH + 2f * GAP_S + RULE_DP;
            float firstMin = line(Role.TITLE) + line(bodyRole);
            boolean header = headerBlock + firstMin <= inner.height + WidgetLayoutPlan.EPSILON;
            float y = inner.top;
            if (header) {
                header(inner, groups, canClear);
                parts.add(Part.rule("rule.header", new Box(inner.left, y + headerH + GAP_S,
                    inner.width, RULE_DP)));
                y += headerBlock;
            }
            for (int i = 0; i < groups.size(); i++) {
                Group g = groups.get(i);
                float ruleBefore = i > 0 ? RULE_DP + 2f * GAP_S : 0f;
                float avail = inner.bottom() - y - ruleBefore;
                String count = g.count > 1 ? words.count(g.count) : "";
                float badgeW = count.isEmpty() ? 0f : badgeSize(count)[0];
                String body = body(g, false);
                boolean title = true;
                List<String> wrapped = WidgetLayoutPlan.wrap(body, room, MAX_BODY_LINES, measure,
                    bodyRole, size(bodyRole));
                int lines = Math.max(1, wrapped.size());
                if (i > 0) {
                    // A later row is kept whole or left out; the first one gives way to fit.
                    if (stack(true, lines) > avail + WidgetLayoutPlan.EPSILON) break;
                } else {
                    while (stack(title, lines) > avail + WidgetLayoutPlan.EPSILON) {
                        if (lines > 1) lines--;
                        else if (title) title = false;
                        else break;
                        wrapped = WidgetLayoutPlan.wrap(body, room, lines, measure, bodyRole,
                            size(bodyRole));
                    }
                }
                if (i > 0) {
                    parts.add(Part.rule("rule." + i, new Box(inner.left, y + GAP_S, inner.width,
                        RULE_DP)));
                    y += ruleBefore;
                }
                float height = stack(title, wrapped.size());
                float side = Math.min(icon, height);
                parts.add(Part.touch("icon." + i, new Box(inner.left, y, side, side)));
                float ty = y;
                if (title) {
                    float titleRoom = room - (count.isEmpty() ? 0f : badgeW + GAP_M);
                    text("title." + i, g.person() ? g.newest.who : g.newest.app, Role.TITLE, false,
                        Ink.STRONG, left, ty, titleRoom);
                    if (!count.isEmpty()) {
                        badge(i, count, inner.right(),
                            ty + (line(Role.TITLE) - badgeSize(count)[1]) / 2f);
                    }
                    ty += line(Role.TITLE);
                }
                for (int j = 0; j < wrapped.size(); j++) {
                    text("body." + i + "." + j, wrapped.get(j), bodyRole, false, Ink.SOFT, left,
                        ty, room);
                    ty += line(bodyRole);
                }
                parts.add(Part.touch("row." + i, new Box(inner.left, y, inner.width, height)));
                y += height;
            }
        }

        void header(@NonNull Box inner, @NonNull List<Group> groups, boolean canClear) {
            int total = 0;
            for (Group g : groups) total += g.count;
            String word = groups.get(0).person() ? words.headerMessages() : words.headerNotifications();
            String count = words.count(total);
            float right = inner.right();
            if (canClear) {
                String clear = words.clearAll();
                float w = Math.min(width(clear, Role.LABEL, false), inner.width / 2f);
                float top = inner.top + (line(Role.TITLE) - line(Role.LABEL)) / 2f;
                parts.add(Part.text("clear", new Box(right - w, top, w, line(Role.LABEL)), clear,
                    Role.LABEL, false, size(Role.LABEL), Ink.MOON));
                float touchTop = Math.max(0f, inner.top + line(Role.TITLE) / 2f - TOUCH_DP / 2f);
                parts.add(Part.touch("clear.touch", new Box(right - w - GAP_L, touchTop,
                    w + 2f * GAP_L, TOUCH_DP)));
                right -= w + GAP_M;
            }
            float countW = width(count, Role.TITLE, true);
            float titleRoom = Math.max(0f, right - inner.left - countW - GAP_S);
            float titleW = Math.min(width(word, Role.TITLE, false), titleRoom);
            text("header.title", word, Role.TITLE, false, Ink.STRONG, inner.left, inner.top, titleRoom);
            text("header.count", count, Role.TITLE, true, Ink.SOFT, inner.left + titleW + GAP_S,
                inner.top, Math.max(0f, right - (inner.left + titleW + GAP_S)));
        }

        // ----- notice ---------------------------------------------------------------------

        /** One element of the notice's stack; the lowest rank goes first when it is short. */
        private static final class El {
            final int rank; final float gap, height; boolean kept = true; float top;
            El(int rank, float gap, float height) { this.rank = rank; this.gap = gap; this.height = height; }
        }

        void notice(@NonNull Box inner, @NonNull Notice n) {
            boolean compact = WidgetTokens.isCompact(span);
            Role role = Role.BODY;
            if (line(role) > inner.height) role = Role.LABEL_FORCED;
            List<String> lines = WidgetLayoutPlan.wrap(n.message, inner.width, 2, measure, role,
                size(role));
            List<El> els = new ArrayList<>();
            El glyph = new El(1, 0f, NOTICE_GLYPH_DP);
            els.add(glyph);
            List<El> lineEls = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                El el = new El(i == 0 ? Integer.MAX_VALUE : 3, i == 0 ? GAP_M : 0f, line(role));
                lineEls.add(el);
                els.add(el);
            }
            El hint = new El(2, GAP_S, line(Role.LABEL));
            boolean hasHint = !n.hint.isEmpty() && !compact;
            if (hasHint) els.add(hint);
            while (total(els) > inner.height + WidgetLayoutPlan.EPSILON) {
                El weakest = null;
                for (El el : els) {
                    if (el.kept && el.rank != Integer.MAX_VALUE && (weakest == null || el.rank < weakest.rank)) {
                        weakest = el;
                    }
                }
                if (weakest == null) break;
                weakest.kept = false;
            }
            float y = inner.top + (inner.height - total(els)) / 2f;
            boolean first = true;
            for (El el : els) {
                if (!el.kept) continue;
                if (!first) y += el.gap;
                el.top = y;
                y += el.height;
                first = false;
            }
            float cx = inner.centerX();
            if (glyph.kept) {
                parts.add(Part.glyph("glyph", new Box(cx - NOTICE_GLYPH_DP / 2f, glyph.top,
                    NOTICE_GLYPH_DP, NOTICE_GLYPH_DP), GLYPH_BELL, Ink.SOFT));
            }
            int last = 0;
            for (int i = 0; i < lineEls.size(); i++) if (lineEls.get(i).kept) last = i;
            for (int i = 0; i < lineEls.size() && lineEls.get(i).kept; i++) {
                String text = lines.get(i);
                if (i == last) {
                    StringBuilder rest = new StringBuilder(text);
                    for (int j = last + 1; j < lines.size(); j++) rest.append(' ').append(lines.get(j));
                    text = rest.toString();
                }
                float w = Math.min(width(text, role, false), inner.width);
                parts.add(Part.text("message." + i, new Box(cx - w / 2f, lineEls.get(i).top, w,
                    line(role)), text, role, false, size(role), Ink.STRONG));
            }
            if (hasHint && hint.kept) {
                float w = Math.min(width(n.hint, Role.LABEL, false), inner.width);
                parts.add(Part.text("hint", new Box(cx - w / 2f, hint.top, w, line(Role.LABEL)),
                    n.hint, Role.LABEL, false, size(Role.LABEL), Ink.SOFT));
            }
        }

        static float total(@NonNull List<El> els) {
            float sum = 0f;
            boolean first = true;
            for (El el : els) {
                if (!el.kept) continue;
                sum += el.height + (first ? 0f : el.gap);
                first = false;
            }
            return sum;
        }
    }
}
