package com.termux.app.terminal;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The arithmetic of a <b>Home float</b> (docs/GLOSSARY.md): a window of the current session drawn
 * floating over one Home page instead of in the Workspace's row. Pure, so the room it is born into,
 * which page shows it, its memory of each orientation and the chip it is tucked into read as
 * arithmetic without a window to float.
 */
public final class HomeFloatPolicy {

    private HomeFloatPolicy() {}

    /** A window's Home page while it is not a Home float: it is in the Workspace's row. */
    public static final int NOT_HOME_FLOAT = -1;

    /** The shape a Home float is born with: the whole room, between the status bar and the dock. */
    @NonNull
    public static RectF birthFrac() {
        return FloatTitlebarPolicy.fullRoom();
    }

    /**
     * The room Home floats are laid out in, in the float layer's own pixels: the layer's width, and
     * its height between {@code topPx} (what the status bar covers) and {@code bottomPx} from its
     * bottom (what the dock covers). Never inverted: a room squeezed to nothing keeps one pixel.
     */
    @NonNull
    public static Rect room(int layerWidthPx, int layerHeightPx, int topPx, int bottomPx) {
        int width = Math.max(0, layerWidthPx);
        int height = Math.max(0, layerHeightPx);
        int top = Math.max(0, Math.min(height, topPx));
        int bottom = Math.max(top + 1, height - Math.max(0, bottomPx));
        return new Rect(0, top, width, bottom);
    }

    /** Whether Home page {@code page} shows a float pinned to {@code floatPage}. */
    public static boolean shownOn(int floatPage, boolean minimised, int page) {
        return !minimised && floatPage >= 0 && floatPage == page;
    }

    /** Whether a room this size is a landscape one, which is what a float's memory is keyed by. */
    public static boolean isLandscape(float widthPx, float heightPx) {
        return widthPx > heightPx;
    }

    /**
     * The words a float's chip carries after its label: the float's page, as the page dots count
     * it ("· 2"), so floats from every page can share one row of chips.
     */
    @NonNull
    public static String chipPageMark(int floatPage) {
        return " · " + (Math.max(0, floatPage) + 1);
    }

    /**
     * A float's size and spot in each orientation, while the process lives. Today's floats keep
     * fractions of their room, which carry proportionally across a turn; this adds the turn as a
     * key, so a float shaped in portrait and again in landscape comes back to each shape.
     */
    public static final class ShapeMemory {
        @Nullable private Boolean mLandscape;
        @Nullable private RectF mPortraitFrac, mPortraitRestore;
        @Nullable private RectF mLandscapeFrac, mLandscapeRestore;
        private boolean mPortraitKnown, mLandscapeKnown;

        /**
         * The room is {@code landscape} now. On a turn, what the float has ({@code frac} and the
         * shape Maximise restores, {@code restoreFrac}) is kept under the orientation it leaves,
         * and the shape it last had in the new one is handed back. Null keeps the float as it is:
         * no turn, or a first visit to this orientation.
         */
        @Nullable
        public Shape turn(boolean landscape, @Nullable RectF frac, @Nullable RectF restoreFrac) {
            Boolean previous = mLandscape;
            mLandscape = landscape;
            if (previous == null || previous == landscape) return null;
            if (previous) {
                mLandscapeFrac = copy(frac);
                mLandscapeRestore = copy(restoreFrac);
                mLandscapeKnown = true;
            } else {
                mPortraitFrac = copy(frac);
                mPortraitRestore = copy(restoreFrac);
                mPortraitKnown = true;
            }
            if (landscape ? !mLandscapeKnown : !mPortraitKnown) return null;
            return landscape ? new Shape(copy(mLandscapeFrac), copy(mLandscapeRestore))
                : new Shape(copy(mPortraitFrac), copy(mPortraitRestore));
        }

        @Nullable
        private static RectF copy(@Nullable RectF rect) {
            return rect == null ? null : new RectF(rect);
        }
    }

    /** A remembered shape: the float's fractions, and what Maximise restores (null unless maximised). */
    public static final class Shape {
        @Nullable public final RectF frac;
        @Nullable public final RectF restoreFrac;

        Shape(@Nullable RectF frac, @Nullable RectF restoreFrac) {
            this.frac = frac;
            this.restoreFrac = restoreFrac;
        }
    }
}
