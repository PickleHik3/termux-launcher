package com.termux.app.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One MPRIS player as its last report described it. Immutable. */
final class PlayerState {

    enum Status { PLAYING, PAUSED, STOPPED }

    /** Its bus name, {@code org.mpris.MediaPlayer2.kew}. */
    @NonNull final String name;
    /** The process that owns the name, for finding which distro it runs in; 0 when unknown. */
    final int pid;
    @NonNull final Status status;
    @Nullable final String title;
    @Nullable final String artist;
    @Nullable final String album;
    /** {@code mpris:artUrl} as the player gave it. */
    @Nullable final String artUrl;
    /** {@code mpris:trackid}, which {@code SetPosition} needs. */
    @Nullable final String trackId;
    /** The track's length; -1 when the player does not say. */
    final long lengthMs;
    /** Where the track was when the report was made; -1 when the player does not say. */
    final long positionMs;
    final float rate;
    final boolean canPlay;
    final boolean canPause;
    final boolean canNext;
    final boolean canPrevious;
    final boolean canSeek;

    PlayerState(@NonNull String name, int pid, @NonNull Status status, @Nullable String title,
                @Nullable String artist, @Nullable String album, @Nullable String artUrl,
                @Nullable String trackId, long lengthMs, long positionMs, float rate,
                boolean canPlay, boolean canPause, boolean canNext, boolean canPrevious,
                boolean canSeek) {
        this.name = name;
        this.pid = pid;
        this.status = status;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.artUrl = artUrl;
        this.trackId = trackId;
        this.lengthMs = lengthMs;
        this.positionMs = positionMs;
        this.rate = rate;
        this.canPlay = canPlay;
        this.canPause = canPause;
        this.canNext = canNext;
        this.canPrevious = canPrevious;
        this.canSeek = canSeek;
    }

    /** This state with the position a {@code Seeked} signal reported. */
    @NonNull
    PlayerState seekedTo(long positionMs) {
        return new PlayerState(name, pid, status, title, artist, album, artUrl, trackId, lengthMs,
            positionMs, rate, canPlay, canPause, canNext, canPrevious, canSeek);
    }

    /** What a person calls the player: the last part of its bus name, {@code kew}. */
    @NonNull
    String shortName() {
        String rest = name.startsWith(MprisReply.MPRIS_PREFIX)
            ? name.substring(MprisReply.MPRIS_PREFIX.length()) : name;
        int dot = rest.indexOf('.');
        return dot > 0 ? rest.substring(0, dot) : rest;
    }
}
