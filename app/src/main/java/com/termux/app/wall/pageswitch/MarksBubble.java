package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

/**
 * Where the Marks slide's bubble stands, pure. The line sits at the top of the screen, so the
 * bubble drops below it; with the status bar at the bottom it rises above; for the column on a
 * side edge it stands beside it, toward the screen centre. All numbers are in the frame the bubble
 * is drawn in (the host's), and the bubble is kept {@code margin} inside that frame.
 */
public final class MarksBubble {

    private MarksBubble() {}

    public enum Side { BELOW, ABOVE, RIGHT, LEFT }

    /** The bubble's top-left corner; held by the caller and reused. */
    public static final class Box {
        public float left;
        public float top;
    }

    /**
     * The side the bubble opens to, from the line's centre in the host's frame: a row in the top
     * half drops it below and one in the bottom half lifts it above; a column in the left half puts
     * it to the right and one in the right half to the left.
     */
    @NonNull
    public static Side sideFor(boolean vertical, float lineCentreX, float lineCentreY,
                               float hostWidth, float hostHeight) {
        if (vertical) return lineCentreX < hostWidth / 2f ? Side.RIGHT : Side.LEFT;
        return lineCentreY < hostHeight / 2f ? Side.BELOW : Side.ABOVE;
    }

    /**
     * Fills {@code out} for a {@code w} by {@code h} bubble beside the bar rectangle
     * ({@code barLeft..barBottom}), {@code gap} off it on {@code side}, centred on {@code along}
     * (x for a row, y for a column) and held {@code margin} inside the host.
     */
    public static void place(@NonNull Side side, float barLeft, float barTop, float barRight,
                             float barBottom, float along, float w, float h, float gap,
                             float margin, float hostWidth, float hostHeight, @NonNull Box out) {
        switch (side) {
            case BELOW:
                out.top = barBottom + gap;
                out.left = hold(along - w / 2f, margin, hostWidth - margin - w);
                break;
            case ABOVE:
                out.top = barTop - gap - h;
                out.left = hold(along - w / 2f, margin, hostWidth - margin - w);
                break;
            case RIGHT:
                out.left = barRight + gap;
                out.top = hold(along - h / 2f, margin, hostHeight - margin - h);
                break;
            default:
                out.left = barLeft - gap - w;
                out.top = hold(along - h / 2f, margin, hostHeight - margin - h);
                break;
        }
    }

    /** The bubble's width: its round head ({@code height}) and the name after it, no wider than {@code maxWidth}. */
    public static float widthFor(float height, float textWidth, float padding, float maxWidth) {
        return Math.max(height, Math.min(maxWidth, height + textWidth + padding));
    }

    private static float hold(float v, float low, float high) {
        return Math.max(low, Math.min(high, v));
    }
}
