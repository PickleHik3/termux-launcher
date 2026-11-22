package com.termux.terminal;

/**
 * A one-slot hook so a module that sees the touch (the in-app keyboard, the terminal view) can tell
 * the app a finger just went down without depending on it. {@link #fire} is a volatile read and,
 * when a listener is installed, one call; it allocates nothing.
 */
public final class TouchDownListener {

    public interface Listener {
        void onTouchDown();
    }

    private static volatile Listener sListener;

    private TouchDownListener() {}

    public static void set(Listener listener) {
        sListener = listener;
    }

    /** Clears the slot only if it still holds {@code listener}. */
    public static void clear(Listener listener) {
        if (sListener == listener) sListener = null;
    }

    public static void fire() {
        Listener l = sListener;
        if (l != null) l.onTouchDown();
    }
}
