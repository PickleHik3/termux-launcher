package com.termux.app.terminal;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.page.Page;
import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

import java.util.List;

/** One Terminal window behind the page contract; every window shares the one pane host. */
public final class TerminalPage implements Page {

    @NonNull private final TerminalPageHost mHost;

    @Nullable private final TerminalGlass mGlass;

    TerminalPage(@NonNull TerminalPageHost host) {
        this(host, null);
    }

    TerminalPage(@NonNull TerminalPageHost host, @Nullable TerminalGlass glass) {
        mHost = host;
        mGlass = glass;
    }

    /** The floats of every window declare their glass here; every window's page adds the same ones. */
    @Override
    public void declareGlass(@NonNull List<GlassEntry> out) {
        if (mGlass != null) mGlass.declare(out);
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
     * host goes.
     */
    @Override
    public void offFront() { }

    /**
     * Back with nothing raised over the terminal: the keyboard goes down if it is up, else the
     * chain's default takes the wall to Home page 1. Back mapped to Escape is the user's Escape;
     * a Back that reached the chain while no pane held the keys is spent here, as today, never a
     * slide Home.
     */
    @Override
    public boolean onBack() {
        if (mHost.backIsEscape()) return true;
        return mHost.lowerKeyboard();
    }
}
