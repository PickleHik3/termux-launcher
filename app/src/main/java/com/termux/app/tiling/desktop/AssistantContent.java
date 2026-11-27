package com.termux.app.tiling.desktop;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Page;

/**
 * The Assistant's window content (#419): an Assistant page built for the window while it is
 * live, front while the window is focused. Parked or closed, the page and its view go; the chats
 * are kept by the Assistant (ADR 0022), so a page built again shows them.
 */
public final class AssistantContent implements TilingWindowContent {

    /** Builds the Assistant page. */
    public interface Host {
        @NonNull Page createPage();

        /** The keyboard comes up typing into the page's field, or goes; true when taken. */
        default boolean toggleKeyboard() {
            return false;
        }
    }

    @NonNull private final Host mHost;
    @Nullable private Page mPage;
    private long mWindowId = -1L;
    private boolean mFront;

    public AssistantContent(@NonNull Host host) {
        mHost = host;
    }

    @Override
    public boolean attach(long windowId, long contentId, @NonNull ViewGroup frame) {
        Page page = mPage;
        if (page == null) {
            page = mHost.createPage();
            mPage = page;
        }
        mWindowId = windowId;
        TilingWindowContent.place(page.view(), frame);
        return true;
    }

    @Override
    public void park(long windowId, long contentId) {
        Page page = mPage;
        if (page == null || mWindowId != windowId) return;
        if (mFront) {
            mFront = false;
            page.offFront();
        }
        TilingWindowContent.unplace(page.view());
        mPage = null;
        mWindowId = -1L;
    }

    @Override
    public void focus(long windowId, long contentId, boolean focused) {
        Page page = mPage;
        if (page == null || mWindowId != windowId || mFront == focused) return;
        mFront = focused;
        if (focused) page.onFront();
        else page.offFront();
    }

    /** Only the window holding the page has its keyboard. */
    @Override
    public boolean toggleKeyboard(long windowId, long contentId) {
        return mPage != null && mWindowId == windowId && mHost.toggleKeyboard();
    }

    /** The window closes at once; the chats stay. */
    @Override
    public boolean close(long windowId, long contentId) {
        return true;
    }
}
