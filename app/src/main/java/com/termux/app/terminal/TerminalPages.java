package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Page;
import com.termux.app.page.Pages;

import java.util.WeakHashMap;

/** The Terminal cell's pages, one per window, made on first ask; the window in front is current. */
public final class TerminalPages implements Pages.Stops {

    @NonNull private final TerminalPageHost mHost;
    @NonNull private final WeakHashMap<TerminalPaneController.Window, TerminalPage> mPages =
        new WeakHashMap<>();

    public TerminalPages(@NonNull TerminalPageHost host) {
        mHost = host;
    }

    @NonNull
    public TerminalPageHost host() {
        return mHost;
    }

    /** The page of the window in front, null when there is none. */
    @Nullable
    @Override
    public Page current() {
        TerminalPaneController.Window window = mHost.activeWindow();
        return window == null ? null : pageOf(window);
    }

    /** The one page of {@code window}. */
    @NonNull
    public TerminalPage pageOf(@NonNull TerminalPaneController.Window window) {
        TerminalPage page = mPages.get(window);
        if (page == null) {
            page = new TerminalPage(mHost);
            mPages.put(window, page);
        }
        return page;
    }
}
