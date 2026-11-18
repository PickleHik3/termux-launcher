package com.termux.app.wall;

import androidx.annotation.NonNull;
import com.termux.app.page.PageKind;

/**
 * What the wall tells the activity about where it is going and where it came to rest. Main thread
 * only.
 */
public interface WallLanding {

    /** The wall committed to {@code page}, before the slide finishes. */
    default void committed(@NonNull PageKind page) { }

    /** {@code page} came to rest on screen. */
    default void landed(@NonNull PageKind page) { }

    /** A landing that nobody hears, for a wall whose host has none. */
    WallLanding NONE = new WallLanding() { };
}
