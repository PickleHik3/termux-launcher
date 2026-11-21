package com.termux.app.x11;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.page.Page;
import com.termux.app.page.PageKind;
import com.termux.app.page.Rest;

/**
 * One Display page behind the page contract. Every Display page shares the one display view
 * ({@link DisplayPages}); the page attaches and focuses it when it becomes the front, and lets it
 * go when it stops being the front for a page that is not a Display page.
 */
public final class DisplayPage implements Page {

    private final int mPageId;
    @NonNull private final DisplayPages mPages;

    DisplayPage(int pageId, @NonNull DisplayPages pages) {
        mPageId = pageId;
        mPages = pages;
    }

    public int pageId() {
        return mPageId;
    }

    @NonNull @Override public PageKind kind() { return PageKind.DISPLAY; }

    /** The display's view; asked only once its frame is built. */
    @NonNull
    @Override
    public View view() {
        View view = mPages.host().displayView();
        if (view == null) throw new IllegalStateException("The display's frame is not built");
        return view;
    }

    /** What the Display asks of the chrome while it is in front and still: {@link DisplayRest}. */
    @NonNull
    @Override
    public Rest rest(@NonNull Rest.Inputs in) {
        return DisplayRest.of(in);
    }

    @Override
    public void onFront() {
        mPages.attach();
    }

    @Override
    public void offFront() {
        // Another Display page keeps the one view: it is bound there, not let go.
        if (mPages.host().committedKind() == PageKind.DISPLAY) return;
        mPages.leave();
    }

    /** Back after everything raised over the page: the keyboard first, then the X app's own. */
    @Override
    public boolean onBack() {
        return mPages.onBack();
    }
}
