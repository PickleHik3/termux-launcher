package com.termux.app.statusbar;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** Snapshot of the active {@code MediaSession} the media widget renders. */
public final class TopPaneMediaState {

    public final String packageName;
    public final String title;
    public final String artist;
    public final String appLabel;
    @Nullable public final Bitmap art;
    public final long positionMs;
    public final long durationMs;
    public final boolean playing;
    /**
     * When {@link #positionMs} was true, on the {@code SystemClock.elapsedRealtime()} clock, or
     * 0 when unknown — so a reader that comes along later can count forward from it.
     */
    public final long positionUpdatedAtElapsedMs;

    public TopPaneMediaState(@NonNull String packageName, @Nullable String title,
                             @Nullable String artist, @Nullable String appLabel,
                             @Nullable Bitmap art, long positionMs, long durationMs,
                             boolean playing) {
        this(packageName, title, artist, appLabel, art, positionMs, durationMs, playing, 0L);
    }

    public TopPaneMediaState(@NonNull String packageName, @Nullable String title,
                             @Nullable String artist, @Nullable String appLabel,
                             @Nullable Bitmap art, long positionMs, long durationMs,
                             boolean playing, long positionUpdatedAtElapsedMs) {
        this.positionUpdatedAtElapsedMs = Math.max(0L, positionUpdatedAtElapsedMs);
        this.packageName = packageName;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.appLabel = appLabel == null ? "" : appLabel;
        this.art = art;
        this.positionMs = Math.max(0L, positionMs);
        this.durationMs = Math.max(0L, durationMs);
        this.playing = playing;
    }

    /** {@code artist · app name}, collapsing to whichever half is known. */
    @NonNull
    public String subtitle() {
        if (artist.isEmpty()) return appLabel;
        if (appLabel.isEmpty()) return artist;
        return artist + " · " + appLabel;
    }

    /** Single-line label for the contention strip. */
    @NonNull
    public String stripLabel() {
        if (title.isEmpty()) return artist.isEmpty() ? appLabel : artist;
        if (artist.isEmpty()) return title;
        return title + " — " + artist;
    }

    public float progress() {
        if (durationMs <= 0L) return 0f;
        return Math.max(0f, Math.min(1f, positionMs / (float) durationMs));
    }

    @NonNull
    public TopPaneMediaState withPlaying(boolean nowPlaying) {
        return new TopPaneMediaState(packageName, title, artist, appLabel, art, positionMs,
            durationMs, nowPlaying, positionUpdatedAtElapsedMs);
    }

    /**
     * Same session, same text, same playback position and the very same artwork bitmap. The
     * artwork is compared by identity: the listener keeps the metadata a session last sent, so an
     * unchanged track hands over the same bitmap, and comparing pixels is not worth a frame.
     */
    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof TopPaneMediaState)) return false;
        TopPaneMediaState that = (TopPaneMediaState) other;
        return positionMs == that.positionMs
            && durationMs == that.durationMs
            && playing == that.playing
            && positionUpdatedAtElapsedMs == that.positionUpdatedAtElapsedMs
            && art == that.art
            && Objects.equals(packageName, that.packageName)
            && title.equals(that.title)
            && artist.equals(that.artist)
            && appLabel.equals(that.appLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, title, artist, appLabel, System.identityHashCode(art),
            positionMs, durationMs, playing, positionUpdatedAtElapsedMs);
    }
}
