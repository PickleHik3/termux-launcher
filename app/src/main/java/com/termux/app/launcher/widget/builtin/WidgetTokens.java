package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

/**
 * The shared tokens of every built-in widget (spec #101, "Widget tokens"; research note §4.1–4.2):
 * the content inset, the inner radius, the gap rhythm, the touch target and the five type roles
 * with their 11 sp floor. Sizes are in dp unless named sp. Android-free, so a widget layout plan
 * built from them is tested on the JVM.
 */
final class WidgetTokens {
    private WidgetTokens() { }

    /** The content inset, in dp, on spans of two rows. */
    static final int INSET_DP = 16;
    /** The content inset, in dp, on one-row spans and at 1×1: a 92 dp row keeps 68 dp. */
    static final int INSET_COMPACT_DP = 12;

    /** The three element gaps, in dp; no other gap is used inside a widget. */
    static final int GAP_TIGHT_DP = 4;
    static final int GAP_DP = 8;
    static final int GAP_LOOSE_DP = 12;

    /** The smallest touch target, in dp, on each axis. */
    static final int TOUCH_TARGET_DP = 48;

    /** No text on a widget is smaller than this. */
    static final float TEXT_FLOOR_SP = 11f;

    /** The font feature that gives numerals tabular figures, so a ticking value keeps its width. */
    static final String TABULAR_FIGURES = "tnum";

    /** A line of text's box, per pixel of text size. */
    static final float LINE_HEIGHT = 1.2f;
    /**
     * The hero's box, per pixel of text size: numerals and a degree sign have no descenders, so the
     * hero is boxed to its figures rather than to a full line.
     */
    static final float HERO_LINE_HEIGHT = 0.8f;

    /**
     * The type roles. Every role but the hero is a fixed size in sp that follows the font scale;
     * the hero is fit to its span between {@link #HERO_MIN_SP} and {@link #HERO_MAX_SP}.
     */
    enum Role {
        /** Time, temperature, battery %, a count: fit to the span, emphasised. */
        HERO(0f, true),
        /** The hero on 1×1 and one-row spans when nothing larger fits. */
        HEADLINE(24f, true),
        /** Event name, track title, place. */
        TITLE(16f, true),
        /** List rows, supporting lines. */
        BODY(14f, false),
        /** Captions, times, metadata. */
        LABEL(12f, true),
        /** A label where space forces it: never smaller. */
        LABEL_FORCED(11f, true);

        /** The role's size in sp; zero for the hero, which is fit. */
        final float sp;
        /** Whether the role is drawn at a heavier weight than the body. */
        final boolean emphasised;

        Role(float sp, boolean emphasised) { this.sp = sp; this.emphasised = emphasised; }
    }

    /** The hero's smallest on 1×1 and one-row spans (the Headline role), and on taller ones. */
    static final float HERO_MIN_SP = 24f;
    static final float HERO_TALL_MIN_SP = 36f;
    /** The hero's largest. */
    static final float HERO_MAX_SP = 57f;

    /** Whether {@code span} is one of the compact ones: 1×1 or a single row. */
    static boolean isCompact(@NonNull BuiltinWidgetSpan span) {
        return span == BuiltinWidgetSpan.ONE_BY_ONE || span.rows == 1;
    }

    /** The content inset, in dp, at {@code span}. */
    static int insetDp(@NonNull BuiltinWidgetSpan span) {
        return isCompact(span) ? INSET_COMPACT_DP : INSET_DP;
    }

    /** The radius of chips, art and tracks inside a card cut at {@code outer}: concentric with it. */
    static float innerRadius(float outer, float inset) {
        return Math.max(0f, outer - inset);
    }

    /** A fixed role's text size in dp at {@code fontScale}. Not for the hero, which is fit. */
    static float sizeDp(@NonNull Role role, float fontScale) {
        return role.sp * fontScale;
    }

    /** The box of one line of text at {@code sizeDp}. */
    static float lineDp(float sizeDp) {
        return sizeDp * LINE_HEIGHT;
    }

    /** The hero's box at {@code sizeDp}. */
    static float heroLineDp(float sizeDp) {
        return sizeDp * HERO_LINE_HEIGHT;
    }

    /**
     * The hero's smallest size in dp at {@code span}. The hero does not follow the font scale,
     * since it is fit to the span, but it never falls under the text floor at that scale.
     */
    static float heroMinDp(@NonNull BuiltinWidgetSpan span, float fontScale) {
        return Math.max(isCompact(span) ? HERO_MIN_SP : HERO_TALL_MIN_SP, TEXT_FLOOR_SP * fontScale);
    }

    /** The hero's largest size in dp. */
    static float heroMaxDp() {
        return HERO_MAX_SP;
    }
}
