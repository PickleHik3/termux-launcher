package com.termux.app.tiling.desktop;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A Linux app's window content (#419): the app's frame from the Linux windows controller, drawn
 * while its window is live. Parked, the frame goes back to the controller and the app keeps
 * running in the Linux windows host, undrawn; the app takes the keys while its window is focused.
 */
public final class LinuxAppContent implements TilingWindowContent {

    /** What it asks of the Linux windows controller, by the host's window id. */
    public interface Host {
        /** Window {@code linuxId}'s frame, drawn while it is on screen; null when the window is gone. */
        @Nullable View take(long linuxId);

        /** Window {@code linuxId}'s frame is not shown any more: the app keeps running, undrawn. */
        void release(long linuxId);

        /** The app of window {@code linuxId} takes the keys ({@code focused}) or gives them up. */
        void focus(long linuxId, boolean focused);

        /** Asks the app to close window {@code linuxId}; the window goes when the app's does. */
        void askClose(long linuxId);

        /** The keyboard comes up typing into window {@code linuxId}, or goes; true when taken. */
        boolean toggleKeyboard(long linuxId);
    }

    @NonNull private final Host mHost;

    public LinuxAppContent(@NonNull Host host) {
        mHost = host;
    }

    @Override
    public boolean attach(long windowId, long contentId, @NonNull ViewGroup frame) {
        View view = mHost.take(contentId);
        if (view == null) return false;
        TilingWindowContent.place(view, frame);
        return true;
    }

    @Override
    public void park(long windowId, long contentId) {
        mHost.release(contentId);
    }

    @Override
    public void focus(long windowId, long contentId, boolean focused) {
        mHost.focus(contentId, focused);
    }

    /**
     * A Linux app's window takes direct touch only: there is no touchpad for it yet, so Mouse mode
     * changes nothing here (the default's false).
     */
    @Override
    public boolean toggleKeyboard(long windowId, long contentId) {
        return mHost.toggleKeyboard(contentId);
    }

    @Override
    public boolean close(long windowId, long contentId) {
        mHost.askClose(contentId);
        return false;
    }
}
