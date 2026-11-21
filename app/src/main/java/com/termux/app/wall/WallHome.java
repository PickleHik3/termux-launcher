package com.termux.app.wall;

/**
 * What the wall asks of Home: how many Home pages there are, which is in front, turning to one,
 * the run of Home pages the one Home frame pages inside, the page a Home press lands on and how
 * far Home's own swipe has moved its page. The wall asks it for every Home question instead of
 * its Host; the Terminal row's windows stay with the Host. Main thread only.
 */
public interface WallHome {

    /** How many Home pages there are; at least 1. */
    int count();

    /** Which Home page is in front, counting from 0. */
    int index();

    /** Turn Home to its {@code index}-th page at once. */
    void show(int index);

    /**
     * Bind the one Home frame to the Home run of global Home-page indexes {@code first} to
     * {@code last}, both included; {@code last} below 0 for every Home page.
     */
    void bindRun(int first, int last);

    /** The Home page the Home press lands on, counting from 0. */
    int pressPage();

    /**
     * How far Home's page in front stands off its rest while Home's own swipe turns it, as a
     * fraction of a page width, positive to the right. Read on every frame the wallpaper pans; it
     * must not allocate.
     */
    float shiftFraction();

    /** A Home with one page and no run, for a wall whose host has none. */
    WallHome NONE = new WallHome() {
        @Override public int count() { return 1; }
        @Override public int index() { return 0; }
        @Override public void show(int index) { }
        @Override public void bindRun(int first, int last) { }
        @Override public int pressPage() { return 0; }
        @Override public float shiftFraction() { return 0f; }
    };
}
