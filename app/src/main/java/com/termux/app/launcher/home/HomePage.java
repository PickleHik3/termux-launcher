package com.termux.app.launcher.home;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;

import com.termux.app.page.Page;
import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

import java.util.List;

/**
 * One Home page behind the page contract. Every Home page shares the host's one view; the page
 * runs Home's shown work when it becomes the front and its leave work when it stops being the
 * front for a page that is not Home.
 */
public final class HomePage implements Page {

    private final int mPageId;
    @NonNull private final HomePageHost mHost;

    @Nullable private final HomeGlass mGlass;

    public HomePage(int pageId, @NonNull HomePageHost host) {
        this(pageId, host, null);
    }

    public HomePage(int pageId, @NonNull HomePageHost host, @Nullable HomeGlass glass) {
        mPageId = pageId;
        mHost = host;
        mGlass = glass;
    }

    /** Home's widgets and floats declare their glass here; every Home page adds the same ones. */
    @Override
    public void declareGlass(@NonNull List<GlassEntry> out) {
        if (mGlass != null) mGlass.declare(out);
    }

    public int pageId() {
        return mPageId;
    }

    @NonNull @Override public PageKind kind() { return PageKind.HOME; }

    @NonNull @Override public View view() { return mHost.view(); }

    /** What Home asks of the chrome while it is in front and still: {@link HomeRest}. */
    @NonNull
    @Override
    public Rest rest(@NonNull Rest.Inputs in) {
        return HomeRest.of(in);
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
