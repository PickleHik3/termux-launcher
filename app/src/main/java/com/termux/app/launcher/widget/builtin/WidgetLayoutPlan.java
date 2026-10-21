package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a built-in widget shows and where: the parts of one layout, each with its box in dp from
 * the card's top left. A planner builds it from the kind, the span bucket, the room, the font
 * scale and the content; the widget's view only draws it ({@link WidgetPlanView}). Android-free,
 * so every span, font scale and content is tested on the JVM.
 */
final class WidgetLayoutPlan {
    /** What a part is drawn as. */
    enum Type {
        /** One line of text, cut short with an ellipsis where its box is narrower. */
        TEXT,
        /** A Nerd glyph fit to its square box. */
        GLYPH,
        /** A range bar: a track with the span {@link Part#start}..{@link Part#end} filled. */
        TRACK,
        /** A hairline. */
        RULE,
        /** Where a tap lands; drawn as nothing. */
        TOUCH,
        /** A box the widget draws into itself (the Clock's face); the plan view leaves it empty. */
        FACE
    }

    /** The colour role a part is drawn in; the view maps it to the widget's style. */
    enum Ink { STRONG, SOFT, SUN, MOON, CLOUD, RULE, CAUTION }

    /** A box in dp. */
    static final class Box {
        final float left, top, width, height;

        Box(float left, float top, float width, float height) {
            this.left = left; this.top = top;
            this.width = Math.max(0f, width); this.height = Math.max(0f, height);
        }

        float right() { return left + width; }
        float bottom() { return top + height; }
        float centerX() { return left + width / 2f; }
        float centerY() { return top + height / 2f; }

        /** Whether the two boxes share any area; touching edges do not. */
        boolean overlaps(@NonNull Box other) {
            return left < other.right() - EPSILON && other.left < right() - EPSILON
                && top < other.bottom() - EPSILON && other.top < bottom() - EPSILON;
        }

        /** Whether {@code other} lies wholly inside this box. */
        boolean contains(@NonNull Box other) {
            return other.left >= left - EPSILON && other.top >= top - EPSILON
                && other.right() <= right() + EPSILON && other.bottom() <= bottom() + EPSILON;
        }

        @NonNull @Override public String toString() {
            return String.format(java.util.Locale.ROOT, "[%.1f,%.1f %.1fx%.1f]", left, top, width,
                height);
        }
    }

    /** Rounding slack for the box tests, in dp. */
    static final float EPSILON = 0.01f;

    /** One thing the widget draws. */
    static final class Part {
        /** What the part is, stable across sizes: "temp", "hour.2.label", "day.0.bar". */
        @NonNull final String id;
        @NonNull final Type type;
        @NonNull final Box box;
        /** The text of a {@link Type#TEXT} or {@link Type#GLYPH} part, empty for the others. */
        @NonNull final String text;
        /** The type role of a text part, null for the others. */
        @Nullable final WidgetTokens.Role role;
        /** Whether a text part is numerals, drawn with tabular figures. */
        final boolean numeric;
        /** A text part's size in dp, a glyph's side; zero for the others. */
        final float sizeDp;
        @NonNull final Ink ink;
        /** A track's filled span, 0..1. */
        final float start, end;

        private Part(@NonNull String id, @NonNull Type type, @NonNull Box box, @NonNull String text,
                     @Nullable WidgetTokens.Role role, boolean numeric, float sizeDp,
                     @NonNull Ink ink, float start, float end) {
            this.id = id; this.type = type; this.box = box; this.text = text; this.role = role;
            this.numeric = numeric; this.sizeDp = sizeDp; this.ink = ink;
            this.start = start; this.end = end;
        }

        @NonNull static Part text(@NonNull String id, @NonNull Box box, @NonNull String text,
                                  @NonNull WidgetTokens.Role role, boolean numeric, float sizeDp,
                                  @NonNull Ink ink) {
            return new Part(id, Type.TEXT, box, text, role, numeric, sizeDp, ink, 0f, 0f);
        }

        @NonNull static Part glyph(@NonNull String id, @NonNull Box box, @NonNull String glyph,
                                   @NonNull Ink ink) {
            return new Part(id, Type.GLYPH, box, glyph, null, false,
                Math.min(box.width, box.height), ink, 0f, 0f);
        }

        @NonNull static Part track(@NonNull String id, @NonNull Box box, float start, float end) {
            return new Part(id, Type.TRACK, box, "", null, false, 0f, Ink.SUN, start, end);
        }

        @NonNull static Part rule(@NonNull String id, @NonNull Box box) {
            return new Part(id, Type.RULE, box, "", null, false, 0f, Ink.RULE, 0f, 0f);
        }

        @NonNull static Part touch(@NonNull String id, @NonNull Box box) {
            return new Part(id, Type.TOUCH, box, "", null, false, 0f, Ink.RULE, 0f, 0f);
        }

        @NonNull static Part face(@NonNull String id, @NonNull Box box) {
            return new Part(id, Type.FACE, box, "", null, false, 0f, Ink.STRONG, 0f, 0f);
        }

        @NonNull @Override public String toString() {
            return id + box + (text.isEmpty() ? "" : " \"" + text + "\"");
        }
    }

    /**
     * How wide a line of text draws, in dp. The view measures with the paints it draws with; a
     * test passes a deterministic estimate.
     */
    interface Measure {
        float widthDp(@NonNull String text, @NonNull WidgetTokens.Role role, boolean numeric,
                      float sizeDp);
    }

    @NonNull final BuiltinWidgetKind kind;
    @NonNull final BuiltinWidgetSpan span;
    /** The largest span that still adds content; past it a widget only gains room. */
    @NonNull final BuiltinWidgetSpan maxSpan;
    final float fontScale;
    /** The card, at the origin. */
    @NonNull final Box card;
    /** The card less the content inset: every drawn part sits inside it. */
    @NonNull final Box inset;
    @NonNull final List<Part> parts;

    WidgetLayoutPlan(@NonNull BuiltinWidgetKind kind, @NonNull BuiltinWidgetSpan span,
                     @NonNull BuiltinWidgetSpan maxSpan, float fontScale, @NonNull Box card,
                     @NonNull Box inset, @NonNull List<Part> parts) {
        this.kind = kind; this.span = span; this.maxSpan = maxSpan; this.fontScale = fontScale;
        this.card = card; this.inset = inset;
        this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
    }

    /** The part named {@code id}, or null when this layout leaves it out. */
    @Nullable Part part(@NonNull String id) {
        for (Part part : parts) if (part.id.equals(id)) return part;
        return null;
    }

    boolean has(@NonNull String id) { return part(id) != null; }

    /** The parts whose id starts with {@code prefix}, in drawing order. */
    @NonNull List<Part> partsStartingWith(@NonNull String prefix) {
        List<Part> found = new ArrayList<>();
        for (Part part : parts) if (part.id.startsWith(prefix)) found.add(part);
        return found;
    }

    /** The card inset by {@code insetDp} on every side. */
    @NonNull static Box insetBox(float widthDp, float heightDp, float insetDp) {
        return new Box(insetDp, insetDp, widthDp - 2f * insetDp, heightDp - 2f * insetDp);
    }

    /**
     * {@code text} broken at spaces into lines no wider than {@code widthDp}, at most
     * {@code maxLines}; the last line keeps whatever is left, for the view to cut short.
     */
    @NonNull static List<String> wrap(@NonNull String text, float widthDp, int maxLines,
                                      @NonNull Measure measure, @NonNull WidgetTokens.Role role,
                                      float sizeDp) {
        List<String> lines = new ArrayList<>();
        String rest = text.trim();
        while (!rest.isEmpty() && lines.size() < maxLines - 1) {
            if (measure.widthDp(rest, role, false, sizeDp) <= widthDp) break;
            int cut = -1;
            for (int i = rest.indexOf(' '); i > 0; i = rest.indexOf(' ', i + 1)) {
                if (measure.widthDp(rest.substring(0, i), role, false, sizeDp) > widthDp) break;
                cut = i;
            }
            if (cut <= 0) break;
            lines.add(rest.substring(0, cut));
            rest = rest.substring(cut + 1).trim();
        }
        if (!rest.isEmpty()) lines.add(rest);
        return lines;
    }
}
