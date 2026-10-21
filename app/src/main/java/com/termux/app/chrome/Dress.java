package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.WallHalfShape;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Everything a page wears (the glossary's <b>Dress</b>): the layout it follows and the elements it
 * leaves out, the Dock design with its Search field and Drawer tile, and the Style, Corners and
 * Margin. Whether the keyboard is open is not dress; each half remembers that.
 *
 * <p>A value, so two pages dress alike exactly when their dresses are equal. Made by {@link
 * DressResolver}, never by a caller.
 */
public final class Dress {

    @NonNull private final LayoutVariant mLayout;
    @NonNull private final Set<Element> mLeftOut;
    @NonNull private final DockStyle mDockDesign;
    private final boolean mSearchField;
    private final boolean mDrawerTile;
    @NonNull private final WallHalfShape mShape;

    Dress(@NonNull LayoutVariant layout, @NonNull Set<Element> leftOut,
          @NonNull DockStyle dockDesign, boolean searchField, boolean drawerTile,
          @NonNull WallHalfShape shape) {
        mLayout = layout;
        mLeftOut = leftOut.isEmpty() ? Collections.emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(leftOut));
        mDockDesign = dockDesign;
        mSearchField = searchField;
        mDrawerTile = drawerTile;
        mShape = shape;
    }

    /**
     * The layout the page follows: where its arrangement is read and written, and with it the
     * keyboard's shape (height, chin, rows and form), which the layout holds per orientation.
     */
    @NonNull
    public LayoutVariant layout() {
        return mLayout;
    }

    /**
     * Whether the page wears this element at all, and so whether the Layout editor offers it
     * there. An element left out is never shown on the page, whatever the layout stores for it.
     */
    public boolean wears(@NonNull Element element) {
        return !mLeftOut.contains(element);
    }

    /** The Dock design the page's dock draws: the one its layout holds, on either half. */
    @NonNull
    public DockStyle dockDesign() {
        return mDockDesign;
    }

    /**
     * Whether the dock carries the Search field, which opens the command palette: the toggle the
     * page's layout holds beside its design, with any design (#96).
     */
    public boolean searchField() {
        return mSearchField;
    }

    /**
     * Whether the dock carries the Drawer tile, which opens the app drawer: the toggle the page's
     * layout holds beside its design, with any design (#97; once the Taskbar's tile).
     */
    public boolean drawerTile() {
        return mDrawerTile;
    }

    /** The Style, Corners and Margin the page's chrome is shaped by. */
    @NonNull
    public WallHalfShape shape() {
        return mShape;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof Dress)) return false;
        Dress that = (Dress) other;
        return mLayout == that.mLayout && mLeftOut.equals(that.mLeftOut)
            && mDockDesign == that.mDockDesign && mSearchField == that.mSearchField
            && mDrawerTile == that.mDrawerTile && mShape.equals(that.mShape);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLayout, mLeftOut, mDockDesign, mSearchField, mDrawerTile, mShape);
    }

    @NonNull
    @Override
    public String toString() {
        return "Dress{" + mLayout + ", leftOut=" + mLeftOut + ", " + mDockDesign
            + (mSearchField ? "+search_field" : "") + (mDrawerTile ? "+drawer_tile" : "") + ", "
            + mShape + "}";
    }
}
