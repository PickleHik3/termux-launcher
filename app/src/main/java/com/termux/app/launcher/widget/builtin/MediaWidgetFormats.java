package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * The media widget's arithmetic: where playback is now, given where the session last said it was,
 * and how that reads as {@code 1:42}. The session reports a position only when its state changes,
 * so between reports the widget carries it forward itself while the track plays.
 */
final class MediaWidgetFormats {
    private MediaWidgetFormats() { }

    /**
     * The position {@code nowMs} after a report of {@code anchorPositionMs} at {@code anchorAtMs}:
     * advanced by the time since while {@code playing}, held while paused, and never past the end
     * of a track whose {@code durationMs} is known.
     */
    static long positionAt(long anchorPositionMs, long anchorAtMs, long nowMs, boolean playing,
                           long durationMs) {
        long position = Math.max(0L, anchorPositionMs);
        if (playing) position += Math.max(0L, nowMs - anchorAtMs);
        if (durationMs > 0L) position = Math.min(position, durationMs);
        return position;
    }

    /**
     * Whether a new report should be read as a fresh position or as the old one carried on. A
     * report that repeats the previous position for the same track is a republish (artwork
     * arrived, or the play glyph flipped ahead of the player) rather than a seek, so the position
     * already advanced on screen must not jump back to it.
     */
    static boolean carriesPosition(@NonNull String previousPackage, @NonNull String previousTitle,
                                   long previousPositionMs, @NonNull String nextPackage,
                                   @NonNull String nextTitle, long nextPositionMs) {
        return previousPackage.equals(nextPackage) && previousTitle.equals(nextTitle)
            && previousPositionMs == nextPositionMs;
    }

    /** How far through the track, 0..1; 0 when the length is unknown. */
    static float fraction(long positionMs, long durationMs) {
        if (durationMs <= 0L) return 0f;
        return Math.max(0f, Math.min(1f, positionMs / (float) durationMs));
    }

    /** {@code 1:42}, or {@code 1:02:05} past the hour. */
    @NonNull static String clock(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long hours = total / 3600L, minutes = (total / 60L) % 60L, seconds = total % 60L;
        return hours > 0
            ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
            : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    /** {@code 1:42 / 3:58}; the position alone when the length is unknown. */
    @NonNull static String elapsedOfTotal(long positionMs, long durationMs) {
        if (durationMs <= 0L) return clock(positionMs);
        return clock(positionMs) + " / " + clock(durationMs);
    }
}
