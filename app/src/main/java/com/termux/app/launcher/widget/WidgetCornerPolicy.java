package com.termux.app.launcher.widget;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pure decision for how a {@link WidgetCellView} should clip a provider's content. Mirrors
 * Launcher3's {@code RoundedCornerEnforcement}: a widget that already draws its own rounded
 * background (an {@code android:id/background} child with {@code clipToOutline == true}) opted
 * out and gets no clip at all; otherwise the background child's own rectangle is clipped, not the
 * whole cell, so a widget that only partly fills the cell (a narrower background, its own margin)
 * is not cut along an edge nothing draws.
 */
public final class WidgetCornerPolicy {
    /** What to clip, and to what radius. {@link #clip} is null when nothing should be clipped. */
    public static final class Decision {
        @Nullable public final Rect clip;
        public final float radius;

        private Decision(@Nullable Rect clip, float radius) {
            this.clip = clip;
            this.radius = radius;
        }

        static Decision none() { return new Decision(null, 0f); }
        static Decision clip(@NonNull Rect rect, float radius) { return new Decision(rect, radius); }
    }

    /** One provider background child, as much as the policy needs to know about it. */
    public static final class BackgroundChild {
        final boolean clipsToOutline;
        @NonNull final Rect bounds;

        public BackgroundChild(boolean clipsToOutline, @NonNull Rect bounds) {
            this.clipsToOutline = clipsToOutline;
            this.bounds = bounds;
        }
    }

    private WidgetCornerPolicy() {}

    /**
     * @param background the cell's {@code android:id/background} descendant, or null when the
     *                    provider's content has none.
     * @param cellBounds  the cell's own rectangle, used when there is no background child to clip
     *                    instead.
     * @param ourRadius       this launcher's own cell-corner radius.
     * @param systemRadius the radius the platform hands widget backgrounds.
     */
    @NonNull public static Decision decide(@Nullable BackgroundChild background,
                                    @NonNull Rect cellBounds, float ourRadius, float systemRadius) {
        if (background != null && background.clipsToOutline) return Decision.none();
        float radius = Math.min(ourRadius, systemRadius);
        Rect target = background != null ? background.bounds : cellBounds;
        return Decision.clip(target, radius);
    }
}
