package com.termux.app.statusbar;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    public TopPaneMediaState(@NonNull String packageName, @Nullable String title,
                             @Nullable String artist, @Nullable String appLabel,
                             @Nullable Bitmap art, long positionMs, long durationMs,
                             boolean playing) {
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
            durationMs, nowPlaying);
    }
}
