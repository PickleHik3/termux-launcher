package com.termux.app.terminal;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.page.Page;
import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

/** One Terminal window behind the page contract; every window shares the one pane host. */
public final class TerminalPage implements Page {

    @NonNull private final TerminalPageHost mHost;

    TerminalPage(@NonNull TerminalPageHost host) {
        mHost = host;
    }

    @NonNull @Override public PageKind kind() { return PageKind.TERMINAL; }

    @NonNull @Override public View view() { return mHost.paneHost(); }

    @NonNull
    @Override
    public Rest rest(@NonNull Rest.Inputs in) {
        return TerminalRest.of(in);
    }

    /** The active pane takes the keys, once, unless a terminal already holds them. */
    @Override
    public void onFront() {
        if (!mHost.keysWithTerminal()) mHost.handKeysToTerminal();
    }

    /**
     * Nothing yet: the panes' repaint stop stays on the wall's off-screen callback until the wall
     * host goes (#273 slice 4).
     */
    @Override
    public void offFront() { }

    @Override
    public boolean onBack() {
        return false;
    }
}
