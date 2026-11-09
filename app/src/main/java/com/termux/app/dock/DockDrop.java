package com.termux.app.dock;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;

/**
 * The dock's pinned row as a place to put down an app icon carried in from Home. Every point is on
 * screen; the row works out the slot under it ({@link DockDropPolicy}). Only the row lying down
 * on a pinned page takes one: a rail, the A–Z row or a typed line's suggestions answer false.
 */
public interface DockDrop {

    /**
     * An icon is carried at ({@code rawX}, {@code rawY}). True while the row is under it and takes
     * it: a gap opens at the finger, and a rest at either end of the row turns its page. False
     * elsewhere, with any gap closed again.
     */
    boolean hoverApp(float rawX, float rawY);

    /** The icon went elsewhere or the drag ended: the gap closes and the ends stop turning pages. */
    void leave();

    /**
     * The icon was let go there: {@code app} goes into the row at the slot under the finger,
     * makes a folder with the icon under it, or joins the folder under it. True when the row took
     * it; false when it did not (it was already there, or the folder is full), and the row has
     * said why.
     */
    boolean dropApp(@NonNull AppRef app, float rawX, float rawY);
}
