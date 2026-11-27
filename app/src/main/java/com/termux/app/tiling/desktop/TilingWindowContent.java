package com.termux.app.tiling.desktop;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a window of a kind other than a shell shows (#419): a Linux app, the X11 Display or the
 * Assistant. The desktop asks each kind's content through this one interface, so it learns nothing
 * of a kind's own views; each kind has its own small adapter behind it.
 *
 * <p>{@code contentId} names what the window shows within its kind: the Linux windows host's id
 * for a Linux app's window, 0 for the Display and the Assistant (there is one of each).
 *
 * <p>A window's frame is a live window's ({@code TilingLivePolicy}): a parked window's view goes
 * and what it shows keeps running, and it comes back when the window is live again. The frame
 * gives every touch on the window's body to the content.
 */
public interface TilingWindowContent {

    /**
     * Draws window {@code windowId}'s content in {@code frame}, filling it; content already there
     * stays. Asked on every pass that lays the window out, so it compares before it moves
     * anything. False when there is nothing to draw now.
     */
    boolean attach(long windowId, long contentId, @NonNull ViewGroup frame);

    /** The window parks or closes: its view leaves its frame; what it shows keeps running. */
    void park(long windowId, long contentId);

    /** The window takes the keyboard ({@code focused}) or gives it up. Only an attached window is focused. */
    void focus(long windowId, long contentId, boolean focused);

    /**
     * The user closes the window. True when it closes now; false when it closes once its content
     * is gone (a Linux app is asked to close, and its window goes when the app's does).
     */
    boolean close(long windowId, long contentId);

    /**
     * The control bar's Mouse mode for this window (#424): touchpad or direct touch. True when the
     * window's kind has a mouse mode and it switched; false, with nothing changed, when it has none.
     */
    default boolean toggleMouseMode(long windowId, long contentId) {
        return false;
    }

    /**
     * The control bar's Keyboard for this window (#424): the keyboard comes up typing into it, or
     * goes. True when the window took it.
     */
    default boolean toggleKeyboard(long windowId, long contentId) {
        return false;
    }

    /**
     * Puts {@code view} in {@code frame}, filling it, out of any parent it had; nothing when it is
     * already there. For the adapters.
     */
    static void place(@NonNull View view, @NonNull ViewGroup frame) {
        if (view.getParent() == frame) return;
        unplace(view);
        frame.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Takes {@code view} out of its parent, if it has one. For the adapters. */
    static void unplace(@Nullable View view) {
        if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }
}
