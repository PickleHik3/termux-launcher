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

    @Nullable private final TerminalGlass mGlass;

    public TerminalPages(@NonNull TerminalPageHost host) {
        this(host, null);
    }

    public TerminalPages(@NonNull TerminalPageHost host, @Nullable TerminalGlass glass) {
        mHost = host;
        mGlass = glass;
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

    /** The page in front, else any window's: every window's page declares the same floats' glass. */
    @Nullable
    @Override
    public Page glassPage() {
        Page front = current();
        if (front != null) return front;
        for (TerminalPage page : mPages.values()) return page;
        return null;
    }

    /** The Terminal page came back on screen: the window in front takes the keys it is owed. */
    public void onPanesBackOnScreen() {
        TerminalPaneController.Window window = mHost.activeWindow();
        if (window != null) pageOf(window).onPanesBackOnScreen();
    }

    /** The one page of {@code window}. */
    @NonNull
    public TerminalPage pageOf(@NonNull TerminalPaneController.Window window) {
        TerminalPage page = mPages.get(window);
        if (page == null) {
            page = new TerminalPage(mHost, mGlass);
            mPages.put(window, page);
        }
        return page;
    }
}
