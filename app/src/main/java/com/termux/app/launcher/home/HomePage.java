package com.termux.app.launcher.home;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.page.Page;
import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

/**
 * One Home page behind the page contract. Every Home page shares the host's one view; the page
 * runs Home's shown work when it becomes the front and its leave work when it stops being the
 * front for a page that is not Home.
 */
public final class HomePage implements Page {

    private final int mPageId;
    @NonNull private final HomePageHost mHost;

    public HomePage(int pageId, @NonNull HomePageHost host) {
        mPageId = pageId;
        mHost = host;
    }

    public int pageId() {
        return mPageId;
    }

    @NonNull @Override public PageKind kind() { return PageKind.HOME; }

    @NonNull @Override public View view() { return mHost.view(); }

    /** Nothing calls this yet. */
    @NonNull
    @Override
    public Rest rest(@NonNull Rest.Inputs in) {
        throw new UnsupportedOperationException("Home's rest arrives with #271 slice 1");
    }

    @Override
    public void onFront() {
        mHost.shown();
    }

    @Override
    public void offFront() {
        mHost.dismissControls();
        if (mHost.committedKind() != PageKind.HOME) mHost.leaving();
    }

    @Override
    public boolean onBack() {
        return mHost.widgetBack() || mHost.lowerFloatKeyboard();
    }
}
