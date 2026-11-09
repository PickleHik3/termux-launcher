package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * What a stored shader looked like when its program was built: the source's time stamp, the
 * texture's name and time stamp, the quality and the rate. Immutable and pure. Comparing the running
 * stamp with the stored one says whether an edit of the active shader needs a new program
 * ({@link Change#REBUILD}: source or texture), only new knobs ({@link Change#IN_PLACE}: quality or
 * rate, which the layer takes as it runs), or nothing.
 */
public final class ShaderStamp {

    public enum Change { NONE, IN_PLACE, REBUILD }

    public final long sourceMtime;
    @Nullable public final String texture;
    public final long textureMtime;
    @NonNull public final ShaderSafety.Quality quality;
    public final int rateHz;

    public ShaderStamp(long sourceMtime, @Nullable String texture, long textureMtime,
                       @NonNull ShaderSafety.Quality quality, int rateHz) {
        this.sourceMtime = sourceMtime;
        this.texture = texture;
        this.textureMtime = texture == null ? 0L : textureMtime;
        this.quality = quality;
        this.rateHz = rateHz;
    }

    /** What going from {@code running} to {@code stored} takes; a missing running stamp is a rebuild. */
    @NonNull
    public static Change decide(@Nullable ShaderStamp running, @NonNull ShaderStamp stored) {
        if (running == null) return Change.REBUILD;
        if (running.sourceMtime != stored.sourceMtime || !Objects.equals(running.texture, stored.texture)
            || running.textureMtime != stored.textureMtime) {
            return Change.REBUILD;
        }
        if (running.quality != stored.quality || running.rateHz != stored.rateHz) return Change.IN_PLACE;
        return Change.NONE;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ShaderStamp)) return false;
        ShaderStamp s = (ShaderStamp) o;
        return s.sourceMtime == sourceMtime && Objects.equals(s.texture, texture)
            && s.textureMtime == textureMtime && s.quality == quality && s.rateHz == rateHz;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceMtime, texture, textureMtime, quality, rateHz);
    }
}
