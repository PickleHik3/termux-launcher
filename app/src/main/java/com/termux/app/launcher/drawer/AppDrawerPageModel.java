package com.termux.app.launcher.drawer;

/** Pure page partitioning for the horizontal drawer. */
public final class AppDrawerPageModel {

    private AppDrawerPageModel() {}

    public static int pageCount(int itemCount, int itemsPerPage) {
        int count = Math.max(0, itemCount);
        int capacity = Math.max(1, itemsPerPage);
        return count == 0 ? 0 : ((count - 1) / capacity) + 1;
    }

    public static int startForPage(int page, int itemCount, int itemsPerPage) {
        int count = Math.max(0, itemCount);
        if (count == 0) return 0;
        int capacity = Math.max(1, itemsPerPage);
        return clampPage(page, pageCount(count, capacity)) * capacity;
    }

    public static int endForPage(int page, int itemCount, int itemsPerPage) {
        int count = Math.max(0, itemCount);
        return Math.min(count, startForPage(page, count, itemsPerPage)
            + Math.max(1, itemsPerPage));
    }

    public static int clampPage(int page, int pageCount) {
        int count = Math.max(0, pageCount);
        if (count == 0) return 0;
        return Math.max(0, Math.min(count - 1, page));
    }
}
