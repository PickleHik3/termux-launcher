package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * The section switch's numbers, pure: the capsule's size from its two label widths, where the
 * selected capsule stands for a pager position, which label a tap hit, and how far each label
 * stands out. The view measures the text and draws; it keeps no geometry of its own.
 *
 * <p>Sizes match the Settings segment ({@code Widget.Termux.Button.Segment}): a 40dp capsule, 14sp
 * text, 12dp either side of a label. The selected capsule is one segment: the body's inset inside
 * it on every side, so it reads as sitting in the body.
 *
 * <p>Rects are in the view's own coordinates and by section index (Apps 0, Categories 1); in RTL
 * the segments are mirrored, as the pages are.
 */
public final class AppDrawerSectionSwitchGeometry {

    /** The body's height: the Settings segment's. */
    public static final float HEIGHT_DP = 40f;
    /** The body's inset around the two segments; the selected capsule is the segment inside it. */
    public static final float BODY_INSET_DP = 4f;
    /** The label size: the Settings segment's. */
    public static final float TEXT_SP = 14f;
    /** Either side of a label, inside its segment. */
    public static final float SEGMENT_PADDING_DP = 12f;
    /** What the padding gives way to, before a label is shortened, when the width is capped. */
    public static final float COMPACT_PADDING_DP = 4f;

    /** One solved layout; reused, written by {@link #compute}. */
    public static final class Layout {
        public float width;
        public float height;
        /** Each segment's left and right edge, by section index. */
        public final float[] segmentLeft = new float[2];
        public final float[] segmentRight = new float[2];
        /** The width each label draws in: its measured width, less when the cap shortened it. */
        public final float[] labelWidth = new float[2];
        /** The space kept either side of a label inside its segment. */
        public float padding;
        public float capsuleTop;
        public float capsuleBottom;
    }

    private AppDrawerSectionSwitchGeometry() {
    }

    /**
     * Solves {@code out} for two measured labels. Over {@code maxWidthPx} (zero or less: no cap) the
     * padding gives way first, down to {@link #COMPACT_PADDING_DP}; only then are the labels
     * shortened, both by the same factor.
     */
    public static void compute(@NonNull Layout out, float appsLabelPx, float categoriesLabelPx,
                               float density, float maxWidthPx, boolean rtl) {
        float inset = BODY_INSET_DP * density;
        float labelA = Math.max(0f, appsLabelPx);
        float labelB = Math.max(0f, categoriesLabelPx);
        float padding = SEGMENT_PADDING_DP * density;
        if (maxWidthPx > 0f) {
            float room = maxWidthPx - 2f * inset - labelA - labelB;
            float compact = COMPACT_PADDING_DP * density;
            padding = Math.max(compact, Math.min(padding, room / 4f));
            float labelRoom = maxWidthPx - 2f * inset - 4f * padding;
            float labels = labelA + labelB;
            if (labels > labelRoom) {
                float factor = labels > 0f ? Math.max(0f, labelRoom) / labels : 0f;
                labelA *= factor;
                labelB *= factor;
            }
        }
        float segmentA = labelA + 2f * padding;
        float segmentB = labelB + 2f * padding;
        out.padding = padding;
        out.height = HEIGHT_DP * density;
        out.width = 2f * inset + segmentA + segmentB;
        out.capsuleTop = inset;
        out.capsuleBottom = out.height - inset;
        out.labelWidth[0] = labelA;
        out.labelWidth[1] = labelB;
        out.segmentLeft[0] = inset;
        out.segmentRight[0] = inset + segmentA;
        out.segmentLeft[1] = inset + segmentA;
        out.segmentRight[1] = inset + segmentA + segmentB;
        if (rtl) {
            for (int i = 0; i < 2; i++) {
                float left = out.width - out.segmentRight[i];
                float right = out.width - out.segmentLeft[i];
                out.segmentLeft[i] = left;
                out.segmentRight[i] = right;
            }
        }
    }

    /** The selected capsule's left edge at pager {@code position}: between the two segments'. */
    public static float capsuleLeft(@NonNull Layout layout, float position) {
        return lerp(layout.segmentLeft[0], layout.segmentLeft[1], clamp(position));
    }

    /** The selected capsule's right edge at pager {@code position}. */
    public static float capsuleRight(@NonNull Layout layout, float position) {
        return lerp(layout.segmentRight[0], layout.segmentRight[1], clamp(position));
    }

    /**
     * How much of the selected ink label {@code index} wears at {@code position}: 1 on its own
     * page, 0 on the other, between as the pages move.
     */
    public static float labelWeight(int index, float position) {
        float p = clamp(position);
        return index == 0 ? 1f - p : p;
    }

    /**
     * The section index a point on the control hit, or -1 outside the body. The body's inset and the
     * seam between the segments go to the nearer segment.
     */
    public static int hit(@NonNull Layout layout, float x, float y) {
        if (x < 0f || x > layout.width || y < 0f || y > layout.height) return -1;
        int first = layout.segmentLeft[0] <= layout.segmentLeft[1] ? 0 : 1;
        return x < layout.segmentRight[first] ? first : 1 - first;
    }

    private static float clamp(float position) {
        return AppDrawerSectionPager.clamp(position);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
