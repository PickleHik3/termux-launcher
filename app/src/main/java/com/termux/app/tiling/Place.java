package com.termux.app.tiling;

import androidx.annotation.NonNull;

/**
 * One stop in the stack: a Home page (by index from 0) or a Workspace (by number from 1). A
 * Workspace's number is its position, so a Place names whichever Workspace stands there now.
 */
public final class Place {

    private final boolean home;
    private final int index;

    private Place(boolean home, int index) {
        this.home = home;
        this.index = index;
    }

    @NonNull
    public static Place home(int page) {
        if (page < 0) throw new IllegalArgumentException("Home page " + page);
        return new Place(true, page);
    }

    @NonNull
    public static Place workspace(int number) {
        if (number < 1) throw new IllegalArgumentException("Workspace " + number);
        return new Place(false, number);
    }

    public boolean isHome() {
        return home;
    }

    /** The Home page's index, or -1 for a Workspace. */
    public int homePage() {
        return home ? index : -1;
    }

    /** The Workspace's number, or -1 for a Home page. */
    public int workspaceNumber() {
        return home ? -1 : index;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Place)) return false;
        Place place = (Place) other;
        return home == place.home && index == place.index;
    }

    @Override
    public int hashCode() {
        return home ? -1 - index : index;
    }

    @NonNull
    @Override
    public String toString() {
        return home ? "Home page " + (index + 1) : "Workspace " + index;
    }
}
