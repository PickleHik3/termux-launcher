package com.termux.app.place.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceOrientation;

import java.util.Objects;

/**
 * One kind's layout document (ADR 0020 §5): the values it holds once for both orientations (its
 * dock design, search field, drawer tile, keyboard rest), and a portrait and a landscape section.
 * Immutable; every change is a {@code with…} copy that {@link LayoutStore#update} writes whole.
 */
public final class KindLayout {

    /** The document version this build writes. */
    public static final int VERSION = 1;

    public static final KindLayout EMPTY =
        new KindLayout(VERSION, LayoutFields.EMPTY, LayoutFields.EMPTY, LayoutFields.EMPTY);

    public final int version;
    @NonNull private final LayoutFields mWide;
    @NonNull private final LayoutFields mPortrait;
    @NonNull private final LayoutFields mLandscape;

    public KindLayout(int version, @NonNull LayoutFields wide, @NonNull LayoutFields portrait,
                      @NonNull LayoutFields landscape) {
        this.version = version;
        mWide = wide;
        mPortrait = portrait;
        mLandscape = landscape;
    }

    /** The values held once for both orientations, plus top-level fields this build does not know. */
    @NonNull public LayoutFields wide() { return mWide; }

    @NonNull public LayoutFields portrait() { return mPortrait; }

    @NonNull public LayoutFields landscape() { return mLandscape; }

    @NonNull
    public LayoutFields orientation(@NonNull PlaceOrientation orientation) {
        return orientation == PlaceOrientation.LANDSCAPE ? mLandscape : mPortrait;
    }

    @NonNull
    public KindLayout withWide(@NonNull LayoutFields wide) {
        return wide.equals(mWide) ? this : new KindLayout(version, wide, mPortrait, mLandscape);
    }

    @NonNull
    public KindLayout withWide(@NonNull String key, @Nullable Object value) {
        return withWide(mWide.with(key, value));
    }

    @NonNull
    public KindLayout withOrientation(@NonNull PlaceOrientation orientation,
                                      @NonNull LayoutFields fields) {
        if (fields.equals(orientation(orientation))) return this;
        return orientation == PlaceOrientation.LANDSCAPE
            ? new KindLayout(version, mWide, mPortrait, fields)
            : new KindLayout(version, mWide, fields, mLandscape);
    }

    @NonNull
    public KindLayout with(@NonNull PlaceOrientation orientation, @NonNull String key,
                           @Nullable Object value) {
        return withOrientation(orientation, orientation(orientation).with(key, value));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof KindLayout)) return false;
        KindLayout k = (KindLayout) o;
        return version == k.version && mWide.equals(k.mWide) && mPortrait.equals(k.mPortrait)
            && mLandscape.equals(k.mLandscape);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, mWide, mPortrait, mLandscape);
    }

    @NonNull
    @Override
    public String toString() {
        return "{wide=" + mWide + ", portrait=" + mPortrait + ", landscape=" + mLandscape + "}";
    }
}
