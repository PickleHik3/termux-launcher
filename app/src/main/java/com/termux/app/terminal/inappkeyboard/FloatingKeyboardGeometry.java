package com.termux.app.terminal.inappkeyboard;

/**
 * Where a floating keyboard is and how wide it is, as arithmetic.
 *
 * <p>Two numbers describe the frame. Its width is a share of the room it is floating in, so the
 * same setting reads the same on a phone and on a tablet. Its place is a pair of fractions of the
 * <em>travel</em> — the room left over once the frame is taken out of the content, {@code 0} against
 * the left or top edge and {@code 1} against the right or bottom one. A fraction rather than a pixel
 * is what lets the memory survive a rotation, a font-scale change and a keyboard the user has since
 * made taller: the frame comes back the same distance along a different amount of room.
 *
 * <p>Travel can be zero — a full-width frame has nowhere to go sideways — and every conversion here
 * has to answer for that rather than divide by it. Pure, so the answers can be read and tested
 * without a window; {@link FloatingKeyboardFrame} only applies them.
 */
public final class FloatingKeyboardGeometry {

    /** An unmoved keyboard starts where a docked one sits: along the bottom, centred. */
    public static final float DEFAULT_X_FRACTION = 0.5f;
    public static final float DEFAULT_Y_FRACTION = 1f;

    private FloatingKeyboardGeometry() {}

    /** Whether a stored fraction is a place the frame can be put back in. */
    public static boolean isPositionSet(float fraction) {
        return !Float.isNaN(fraction) && fraction >= 0f && fraction <= 1f;
    }

    /** A stored fraction, or the edge an unmoved frame starts against. */
    public static float xFractionOr(float stored) {
        return isPositionSet(stored) ? stored : DEFAULT_X_FRACTION;
    }

    public static float yFractionOr(float stored) {
        return isPositionSet(stored) ? stored : DEFAULT_Y_FRACTION;
    }

    /**
     * The frame's width: the user's share of the content width, never below a width there is still
     * a keyboard on and never wider than the room itself.
     */
    public static int frameWidthPx(int contentWidthPx, float widthScale, int minWidthPx) {
        int content = Math.max(0, contentWidthPx);
        if (content == 0) return 0;
        float scale = Float.isNaN(widthScale) ? 1f : Math.max(0f, Math.min(1f, widthScale));
        int wanted = Math.round(content * scale);
        int floor = Math.min(Math.max(0, minWidthPx), content);
        return Math.max(floor, Math.min(content, wanted));
    }

    /** The room the frame has to move in along one axis. Zero when it fills that axis. */
    public static int travelPx(int contentPx, int framePx) {
        return Math.max(0, Math.max(0, contentPx) - Math.max(0, framePx));
    }

    /** Where a remembered fraction puts the frame's leading edge. */
    public static int positionPx(float fraction, int travelPx) {
        int travel = Math.max(0, travelPx);
        if (travel == 0) return 0;
        float clamped = Float.isNaN(fraction) ? 0f : Math.max(0f, Math.min(1f, fraction));
        return Math.round(clamped * travel);
    }

    /** A dragged pixel offset, held inside the content. */
    public static int clampPx(int px, int travelPx) {
        return Math.max(0, Math.min(Math.max(0, travelPx), px));
    }

    /**
     * The width share a drag on the card's bottom-left grip lands on. The card's right edge is
     * fixed, so the width the finger asks for is the width it started at less however far left the
     * finger went — pulling outward widens, pushing inward narrows. Held between the width there is
     * still a keyboard on and the room the card floats in, then between the two ends the stored
     * share itself allows, so neither floor can be dragged through.
     */
    public static float widthScaleForResize(float startScale, int deltaXPx, int contentWidthPx,
                                            int minWidthPx, float minScale, float maxScale) {
        int content = Math.max(0, contentWidthPx);
        if (content == 0) return clampScale(startScale, minScale, maxScale);
        int startWidthPx = frameWidthPx(content, startScale, minWidthPx);
        int floor = Math.min(Math.max(0, minWidthPx), content);
        int wanted = Math.max(floor, Math.min(content, startWidthPx - deltaXPx));
        return clampScale(wanted / (float) content, minScale, maxScale);
    }

    /**
     * Where the card's leading edge goes once a resize gave it a new width. The right edge stays
     * where it was, so every pixel the card gained is a pixel its left edge moved out by — and the
     * result is held inside the content, which is what stops a card grown against the left edge
     * from walking off it.
     */
    public static int resizeXPx(int startXPx, int startWidthPx, int newWidthPx,
                                int contentWidthPx) {
        return clampPx(startXPx + (startWidthPx - newWidthPx),
            travelPx(contentWidthPx, newWidthPx));
    }

    /**
     * The row-height multiplier the same drag lands on. Vertical movement is read as a share of the
     * keyboard's own height at the moment the drag began — a finger that drags down by half the
     * keyboard makes it half again as tall, whatever height the user had already chosen — and every
     * frame of the drag is measured from that same start, so the answer never accumulates drift.
     */
    public static float heightScaleForResize(float startScale, int deltaYPx,
                                             int startKeyboardHeightPx, float minScale,
                                             float maxScale) {
        if (startKeyboardHeightPx <= 0 || Float.isNaN(startScale))
            return clampScale(startScale, minScale, maxScale);
        float grown = (startKeyboardHeightPx + deltaYPx) / (float) startKeyboardHeightPx;
        return clampScale(startScale * Math.max(0f, grown), minScale, maxScale);
    }

    /**
     * A scale held between the two ends its preference allows. A non-finite scale has no place on
     * that range at all and falls to its bottom; the preference's own clamp, which the caller
     * writes through, is what turns nonsense back into the default.
     */
    public static float clampScale(float scale, float minScale, float maxScale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) return minScale;
        return Math.max(minScale, Math.min(maxScale, scale));
    }

    /**
     * What a pixel offset is worth as memory. A frame with no travel is against both edges at once,
     * so it remembers the edge it starts from rather than a meaningless fraction.
     */
    public static float fractionFor(int px, int travelPx) {
        int travel = Math.max(0, travelPx);
        if (travel == 0) return 0f;
        return clampPx(px, travel) / (float) travel;
    }
}
