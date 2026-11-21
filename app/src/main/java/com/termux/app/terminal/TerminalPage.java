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

    /** Between this window's on-front and its off-front. */
    private boolean mFront;

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

    /**
     * The active pane takes the keys, once, unless it already holds them. A window step lands
     * here as well as a slide onto the Terminal: the pane of the window just shown is the one
     * typed into, whatever had the focus before (the window that left, a chip).
     */
    @Override
    public void onFront() {
        mFront = true;
        handKeysToActivePane();
    }

    /**
     * Nothing else yet: the panes' repaint stop stays on the wall's off-screen callback until the
     * wall host goes.
     */
    @Override
    public void offFront() {
        mFront = false;
    }

    /**
     * The panes came back on screen. Off screen they dropped the focus and refused it (a window
     * step pans the page in from the side), so the front hand-back may not have landed. The keys
     * go to the active pane now, if this window is still in front.
     */
    void onPanesBackOnScreen() {
        if (mFront) handKeysToActivePane();
    }

    private void handKeysToActivePane() {
        if (!mHost.activePaneHoldsKeys()) mHost.handKeysToTerminal();
    }

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
