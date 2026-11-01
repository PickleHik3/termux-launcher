package com.termux.app.place.layout;

import androidx.annotation.NonNull;

import com.termux.app.wall.WallPages.PageKind;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every layout at one moment (ADR 0020 §5): the four kind documents and the shared sizes. Read
 * once from the {@code layouts} file and never changed in place, so no reader holds a
 * half-written layout; a reader takes {@link LayoutStore#current()} per pass.
 */
public final class Layouts {

    @NonNull private final EnumMap<LayoutKind, KindLayout> mKinds;
    @NonNull private final Sizes mSizes;

    public Layouts(@NonNull Map<LayoutKind, KindLayout> kinds, @NonNull Sizes sizes) {
        mKinds = new EnumMap<>(LayoutKind.class);
        for (LayoutKind kind : LayoutKind.values()) {
            KindLayout layout = kinds.get(kind);
            mKinds.put(kind, layout != null ? layout : LayoutsJson.shippedDefault(kind));
        }
        mSizes = sizes;
    }

    /** The shipped defaults for every document: a fresh install. */
    @NonNull
    public static Layouts shipped() {
        return new Layouts(new EnumMap<>(LayoutKind.class), Sizes.EMPTY);
    }

    @NonNull
    public KindLayout kind(@NonNull LayoutKind kind) {
        return mKinds.get(kind);
    }

    /** What a page of {@code kind} wears, minimal mode being {@code minimal}. */
    @NonNull
    public KindLayout forPage(@NonNull PageKind kind, boolean minimal) {
        return kind(LayoutKind.of(kind, minimal));
    }

    @NonNull
    public Sizes sizes() {
        return mSizes;
    }

    @NonNull
    public Layouts withKind(@NonNull LayoutKind kind, @NonNull KindLayout layout) {
        if (layout.equals(mKinds.get(kind))) return this;
        EnumMap<LayoutKind, KindLayout> copy = new EnumMap<>(mKinds);
        copy.put(kind, layout);
        return new Layouts(copy, mSizes);
    }

    @NonNull
    public Layouts withSizes(@NonNull Sizes sizes) {
        return sizes.equals(mSizes) ? this : new Layouts(mKinds, sizes);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Layouts)) return false;
        Layouts l = (Layouts) o;
        return mKinds.equals(l.mKinds) && mSizes.equals(l.mSizes);
    }

    @Override
    public int hashCode() {
        return mKinds.hashCode() * 31 + mSizes.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "Layouts" + mKinds + mSizes;
    }
}
