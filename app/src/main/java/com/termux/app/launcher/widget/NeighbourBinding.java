package com.termux.app.launcher.widget;

/**
 * Whether the second page layer's cells, built for one page of the wall at one revision, can be
 * shown again for {@code page} as they are. The answer is the whole key: a page's widget set and
 * their content only change through a new wall revision.
 */
final class NeighbourBinding {
    private NeighbourBinding() { }

    static boolean reusable(boolean layerExists, int boundPage, long boundRevision,
                            int page, long revision) {
        return layerExists && boundPage >= 0 && boundPage == page && boundRevision == revision;
    }
}
