package com.termux.app.tiling.desktop;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The X11 Display's window content (#419): the display's one frame, lent to its window while the
 * window is live. It takes the keys while its window is focused. Parked or closed, the frame
 * leaves the window and its surface goes; the display keeps running, and its frame comes back
 * when the window is live again or opened again.
 */
public final class DisplayContent implements TilingWindowContent {

    /** What it asks of the activity's display. */
    public interface Host {
        /** The display's frame, built if there is none yet; null when this launcher runs no display. */
        @Nullable View frame();

        /** The display's frame takes the keys ({@code focused}) or gives them up. */
        void focus(boolean focused);

        /** The display's frame left its window: the view lets go of the display. */
        void parked();
    }

    @NonNull private final Host mHost;
    /** The window the frame is lent to, or none. */
    private long mWindowId = -1L;
    /** The frame as lent, for the park to take back without building one. */
    @Nullable private View mView;

    public DisplayContent(@NonNull Host host) {
        mHost = host;
    }

    @Override
    public boolean attach(long windowId, long contentId, @NonNull ViewGroup frame) {
        View view = mHost.frame();
        if (view == null) return false;
        TilingWindowContent.place(view, frame);
        mWindowId = windowId;
        mView = view;
        return true;
    }

    @Override
    public void park(long windowId, long contentId) {
        // The frame is one: only the window holding it lets it go.
        if (mWindowId != windowId) return;
        mWindowId = -1L;
        TilingWindowContent.unplace(mView);
        mView = null;
        mHost.parked();
    }

    @Override
    public void focus(long windowId, long contentId, boolean focused) {
        if (mWindowId == windowId) mHost.focus(focused);
    }

    /** The window closes at once; the display keeps running until it is stopped. */
    @Override
    public boolean close(long windowId, long contentId) {
        return true;
    }
}
