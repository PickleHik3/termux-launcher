package com.termux.app.page;

import androidx.annotation.NonNull;

/**
 * The one page kind (ADR 0030 section 1). It is added beside the older kind enums
 * ({@code PaneWallPage}, {@code WallPages.PageKind}, {@code PageSwitchModel.Kind},
 * {@code PageOverviewPolicy.Kind}, {@code LookGrid.Kind}, ...), which stay until their pages move
 * behind the contract.
 */
public enum PageKind {
    HOME,
    TERMINAL,
    DISPLAY,
    ASSISTANT;

    /** Home's side for a Home page, the Workspace's for every window. */
    @NonNull
    public Side side() {
        return this == HOME ? Side.HOME : Side.WORKSPACE;
    }
}
