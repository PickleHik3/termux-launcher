package com.termux.view;

/**
 * The rules that turn a finger's touch stream into a terminal click, kept out of the view so they
 * can be reasoned about and tested on their own. The cell mapping itself stays in
 * {@link TerminalView}, which is the only thing that knows the renderer's metrics.
 */
final class TapPrecision {

    private TapPrecision() {
    }

    /**
     * Where a finger click should land. A thumb rolls a few pixels between press and lift, and at
     * a small font size that is already most of a cell; while the whole gesture stayed inside one
     * row's worth of travel the press point is what the user aimed at, so both the press and the
     * release go there. Once the finger travelled further than that, the lift is the honest
     * answer.
     *
     * @param rowHeight one row in pixels — how far a finger may travel and still be a click.
     * @return {@code {x, y}} to take the cell from.
     */
    static float[] clickPointFor(float downX, float downY, float upX, float upY, float rowHeight) {
        float dx = upX - downX;
        float dy = upY - downY;
        boolean stationary = rowHeight > 0f && dx * dx + dy * dy < rowHeight * rowHeight;
        return stationary ? new float[] { downX, downY } : new float[] { upX, upY };
    }

    /**
     * Whether a gesture actually moved anything. {@link android.view.GestureDetector} reports a
     * scroll as soon as a finger crosses its 8dp slop, which at a small font size is ordinary
     * tremor rather than an intent to scroll; a click is only lost to a scroll once a row, a
     * column or a pixel of the transcript really moved under it.
     */
    static final class ScrollDelivery {

        private boolean mDelivered;

        /** A fresh gesture has delivered nothing yet. */
        void reset() {
            mDelivered = false;
        }

        void rowsScrolled(int deltaRows) {
            if (deltaRows != 0)
                mDelivered = true;
        }

        void columnsScrolled(int deltaColumns) {
            if (deltaColumns != 0)
                mDelivered = true;
        }

        /** A smooth scroll delivers whatever it moved the buffer by, fractions included. */
        void pixelsScrolled(float before, float after) {
            if (before != after)
                mDelivered = true;
        }

        boolean delivered() {
            return mDelivered;
        }
    }
}
