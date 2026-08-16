package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/** Pure real-layout geometry for the transient FULL status pane. */
public final class FullStatusBarGeometry {
    public static final class Frame {
        public final int height;
        public final int fullHeight;
        public final float progress;

        private Frame(int height, int fullHeight, float progress) {
            this.height = height;
            this.fullHeight = fullHeight;
            this.progress = progress;
        }
    }

    private FullStatusBarGeometry() {}

    public static int resolveFullHeight(int parentMeasuredHeight, int parentPaddingTop,
                                        int parentPaddingBottom, int hostTopMargin) {
        return Math.max(0, parentMeasuredHeight - Math.max(0, parentPaddingTop)
            - Math.max(0, parentPaddingBottom) - Math.max(0, hostTopMargin));
    }

    @NonNull
    public static Frame calculate(int priorHeight, int parentMeasuredHeight,
                                  int parentPaddingTop, int parentPaddingBottom,
                                  int hostTopMargin, float progress) {
        int full = Math.max(priorHeight, resolveFullHeight(parentMeasuredHeight, parentPaddingTop,
            parentPaddingBottom, hostTopMargin));
        float p = finiteClamp(progress);
        int height = Math.round(priorHeight + (full - priorHeight) * p);
        return new Frame(Math.max(0, Math.min(full, height)), full, p);
    }

    public static float progressForHeight(int height, int priorHeight, int fullHeight) {
        int range = fullHeight - priorHeight;
        if (range <= 0) return height >= fullHeight ? 1f : 0f;
        return finiteClamp((height - priorHeight) / (float) range);
    }

    private static float finiteClamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
