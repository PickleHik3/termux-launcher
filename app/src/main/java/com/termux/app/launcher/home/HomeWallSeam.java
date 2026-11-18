package com.termux.app.launcher.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.WidgetPaneController;
import com.termux.app.wall.WallHome;
import com.termux.app.wall.WallPages;

import java.util.function.Supplier;

/**
 * The wall's way into Home: the widget controller's Home pages, read through suppliers because
 * the controller is built after the wall. The run the wall binds is kept here too, so the host
 * can hand it to a widget controller built later and read it for crossings. Main thread only.
 */
public final class HomeWallSeam implements WallHome {

    @NonNull private final Supplier<WidgetPaneController> mWidgets;
    @NonNull private final Supplier<WallPages> mPages;
    private int mRunFirst = 0;
    private int mRunLast = -1;

    public HomeWallSeam(@NonNull Supplier<WidgetPaneController> widgets,
                        @NonNull Supplier<WallPages> pages) {
        mWidgets = widgets;
        mPages = pages;
    }

    @Override
    public int count() {
        WidgetPaneController widgets = mWidgets.get();
        return widgets == null ? 1 : widgets.homePageCount();
    }

    @Override
    public int index() {
        WidgetPaneController widgets = mWidgets.get();
        return widgets == null ? 0 : widgets.homePageIndex();
    }

    @Override
    public void show(int index) {
        WidgetPaneController widgets = mWidgets.get();
        if (widgets != null) widgets.showHomePage(index);
    }

    @Override
    public void bindRun(int first, int last) {
        mRunFirst = first;
        mRunLast = last;
        WidgetPaneController widgets = mWidgets.get();
        if (widgets != null) widgets.setHomeRun(first, last);
    }

    /** The Home page the press lands on, held to the pages there are now. */
    @Override
    public int pressPage() {
        WallPages pages = mPages.get();
        int home = pages == null ? 0 : pages.homeIndex();
        return Math.max(0, Math.min(home, count() - 1));
    }

    @Override
    public float shiftFraction() {
        WidgetPaneController widgets = mWidgets.get();
        return widgets == null ? 0f : widgets.homePageShiftFraction();
    }

    /** The first global Home-page index of the run last bound. */
    public int runFirst() {
        return mRunFirst;
    }

    /** The last of the run last bound; below 0 for every Home page. */
    public int runLast() {
        return mRunLast;
    }
}
