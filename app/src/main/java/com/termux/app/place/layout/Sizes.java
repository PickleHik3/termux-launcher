package com.termux.app.place.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceOrientation;

/**
 * The {@code sizes} document (ADR 0020 §3): keyboard height and chin, dock height and strip
 * height, per orientation, shared by every kind, so a page change never resizes them. The same
 * shape as a {@link KindLayout}; its wide section holds only fields this build does not know.
 */
public final class Sizes {

    public static final Sizes EMPTY = new Sizes(KindLayout.EMPTY);

    @NonNull private final KindLayout mDocument;

    public Sizes(@NonNull KindLayout document) {
        mDocument = document;
    }

    @NonNull public KindLayout document() { return mDocument; }

    @NonNull
    public LayoutFields orientation(@NonNull PlaceOrientation orientation) {
        return mDocument.orientation(orientation);
    }

    @NonNull
    public Sizes with(@NonNull PlaceOrientation orientation, @NonNull String key,
                      @Nullable Object value) {
        KindLayout changed = mDocument.with(orientation, key, value);
        return changed == mDocument ? this : new Sizes(changed);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Sizes && ((Sizes) o).mDocument.equals(mDocument);
    }

    @Override
    public int hashCode() {
        return mDocument.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "Sizes" + mDocument;
    }
}
