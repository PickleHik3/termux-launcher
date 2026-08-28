package com.termux.app.statusbar;

import android.graphics.Rect;

import androidx.annotation.NonNull;

/** Single owner of normal-to-FULL bounds for clock, notifications and media. */
public final class TopPaneFullRowPolicy {
    public static final class Result {
        @NonNull public final Rect clock;
        @NonNull public final Rect notifications;
        @NonNull public final Rect media;

        private Result(Rect clock, Rect notifications, Rect media) {
            this.clock = clock;
            this.notifications = notifications;
            this.media = media;
        }
    }

    private TopPaneFullRowPolicy() {}

    @NonNull
    public static Result calculate(@NonNull TopPaneSlotMode mode, int pinnedCount,
                                   int width, int height, int gutter, int desiredGap,
                                   int clockDesiredWidth, int notificationDesiredWidth,
                                   int mediaDesiredWidth, @NonNull Rect normalClock,
                                   @NonNull Rect normalNotifications, @NonNull Rect normalMedia,
                                   float progress, boolean rtl) {
        int available = Math.max(0, width - gutter * 2);
        int clockWidth = clampPositive(clockDesiredWidth, available);
        int notificationWidth = mode.showsNotifications()
            ? clampPositive(notificationDesiredWidth, available) : 0;
        int mediaWidth = mode.showsMedia() ? clampPositive(mediaDesiredWidth, available) : 0;
        int count = 1 + (notificationWidth > 0 ? 1 : 0) + (mediaWidth > 0 ? 1 : 0);
        int gap = count <= 1 ? 0 : Math.max(0, Math.min(desiredGap,
            (available - count) / (count - 1)));
        int total = clockWidth + notificationWidth + mediaWidth + gap * (count - 1);
        if (total > available) {
            int excess = total - available;
            int shrinkable = Math.max(1, clockWidth + notificationWidth + mediaWidth - count);
            clockWidth = shrink(clockWidth, excess, shrinkable);
            notificationWidth = shrink(notificationWidth, excess, shrinkable);
            mediaWidth = shrink(mediaWidth, excess, shrinkable);
            total = clockWidth + notificationWidth + mediaWidth + gap * (count - 1);
            if (total > available && count > 1) gap = Math.max(0,
                (available - clockWidth - notificationWidth - mediaWidth) / (count - 1));
            total = clockWidth + notificationWidth + mediaWidth + gap * (count - 1);
        }
        int cursor = gutter + Math.max(0, (available - total) / 2);
        Rect fullClock = rowRect(cursor, height, clockWidth, normalClock.height());
        cursor += clockWidth;
        Rect fullNotifications = new Rect();
        Rect fullMedia = new Rect();
        if (notificationWidth > 0) {
            cursor += gap;
            fullNotifications = rowRect(cursor, height, notificationWidth,
                Math.max(1, normalNotifications.height()));
            cursor += notificationWidth;
        }
        if (mediaWidth > 0) {
            cursor += gap;
            fullMedia = rowRect(cursor, height, mediaWidth, Math.max(1, normalMedia.height()));
        }
        if (rtl) {
            fullClock = mirror(fullClock, width);
            fullNotifications = mirror(fullNotifications, width);
            fullMedia = mirror(fullMedia, width);
        }
        float p = FullStatusBarGeometry.finiteUnit(progress);
        Rect outClock = interpolate(normalClock, fullClock, p);
        Rect outNotifications = interpolate(normalNotifications, fullNotifications, p);
        Rect outMedia = interpolate(normalMedia, fullMedia, p);
        // The contention mode begins as a vertical notification/media column and ends as a
        // horizontal group. Do not interpolate both axes through the diagonal overlap region:
        // retain the already non-overlapping normal rows until their horizontal bounds separate.
        if (!normalNotifications.isEmpty() && !normalMedia.isEmpty()
            && !intersects(normalNotifications, normalMedia)) {
            float separation = horizontalSeparationProgress(normalNotifications,
                normalMedia, fullNotifications, fullMedia);
            if (separation > 0f && separation < 1f) {
                float verticalProgress = p <= separation ? 0f
                    : (p - separation) / (1f - separation);
                outNotifications.top = lerp(normalNotifications.top,
                    fullNotifications.top, verticalProgress);
                outNotifications.bottom = lerp(normalNotifications.bottom,
                    fullNotifications.bottom, verticalProgress);
                outMedia.top = lerp(normalMedia.top, fullMedia.top, verticalProgress);
                outMedia.bottom = lerp(normalMedia.bottom, fullMedia.bottom,
                    verticalProgress);
            }
        }
        return new Result(outClock, outNotifications, outMedia);
    }

    private static int shrink(int value, int excess, int shrinkable) {
        if (value <= 0) return 0;
        return Math.max(1, value - Math.round(excess * (value - 1f) / shrinkable));
    }

    private static int clampPositive(int value, int max) {
        return Math.max(1, Math.min(max, value));
    }

    private static Rect rowRect(int left, int height, int width, int desiredHeight) {
        int childHeight = Math.max(1, Math.min(height, desiredHeight));
        int top = Math.max(0, (height - childHeight) / 2);
        return new Rect(left, top, left + width, top + childHeight);
    }

    private static Rect mirror(Rect value, int width) {
        if (value.isEmpty()) return new Rect();
        return new Rect(width - value.right, value.top, width - value.left, value.bottom);
    }

    private static Rect interpolate(Rect from, Rect to, float p) {
        if (from.isEmpty() && to.isEmpty()) return new Rect();
        Rect start = from.isEmpty() ? to : from;
        Rect end = to.isEmpty() ? from : to;
        return new Rect(lerp(start.left, end.left, p), lerp(start.top, end.top, p),
            lerp(start.right, end.right, p), lerp(start.bottom, end.bottom, p));
    }

    private static int lerp(int a, int b, float p) { return Math.round(a + (b - a) * p); }

    private static boolean intersects(Rect a, Rect b) {
        return !a.isEmpty() && !b.isEmpty() && a.left < b.right && b.left < a.right
            && a.top < b.bottom && b.top < a.bottom;
    }

    /** Progress at which the two horizontally stacked destinations first stop overlapping. */
    static float horizontalSeparationProgress(Rect normalA, Rect normalB, Rect fullA, Rect fullB) {
        if (fullA.right <= fullB.left) {
            return crossingProgress(normalA.right - normalB.left,
                fullA.right - fullB.left);
        }
        if (fullB.right <= fullA.left) {
            return crossingProgress(normalB.right - normalA.left,
                fullB.right - fullA.left);
        }
        return 1f;
    }

    private static float crossingProgress(float startOverlap, float endOverlap) {
        if (startOverlap <= 0f) return 0f;
        if (endOverlap >= 0f) return 1f;
        return Math.max(0f, Math.min(1f, startOverlap / (startOverlap - endOverlap)));
    }
}
