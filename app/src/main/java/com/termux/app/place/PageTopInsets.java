package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;

/**
 * Where each page of the wall starts at the top, so a slide across the seam between Home and
 * the Workspace never moves a page's content when it settles.
 *
 * <p>The wall's pages share one canvas. The Workspace's status bar takes a band at the top that
 * Home may not have (a Home layout can put the bar away or stand it elsewhere), so a canvas that
 * stood below the bar on the Workspace and reached the top on Home moved every page by the band
 * at settle: the terminal down as the bar took its band, Home up as it gave it back.
 *
 * <p>Instead, while a Home page can be on screen — the wall resting on Home, or moving at all —
 * the canvas reaches up under the bar ({@link #canvasReachPx}) and each page keeps its own top:
 * a Workspace page (the terminal's or the display window's) one bar band below it, and a Home
 * page one band of its own bar below it ({@link #pageTopInsetPx}): the same band as a window's
 * where Home wears the bar as the Workspace does (#90), so the two line up across the seam, and
 * the canvas's top itself where Home's layout stands no bar along the top. Resting on the Workspace, the
 * canvas stands below the bar as it always has and no page is inset, so everything that reads
 * the canvas at rest there reads what it always did. Switching between the two moves no page on
 * screen: the canvas grows up by exactly the inset its Workspace pages take, so the terminal
 * keeps its rect, its size and its grid.
 *
 * <p>Pure, so the arithmetic is read and tested on its own.
 */
public final class PageTopInsets {

    private PageTopInsets() {}

    /**
     * Whether the canvas reaches up under the status bar: while the chrome is committed to a
     * Home page, and whenever the wall is off its rest, so a slide from the Workspace toward Home
     * has Home's page at its own top from its first frame. Only a wall resting on a Workspace page
     * keeps the canvas below the bar.
     */
    public static boolean canvasReachesUnderBar(@NonNull PaneWallPage committed,
                                                boolean wallAtRest) {
        return committed == PaneWallPage.WIDGETS || !wallAtRest;
    }

    /**
     * How far the canvas reaches up under the top edge's stack: the status bar's band while it
     * reaches ({@link #canvasReachesUnderBar}) and the bar stands on the top edge of the layout on
     * screen, and 0 otherwise. Every other band on the top edge keeps its room.
     *
     * @param barShown whether the bar is shown at all (it is not in compatibility mode); a bar
     *                 put away claims no band to reach under
     */
    public static int canvasReachPx(boolean reaches, boolean barShown,
                                    @NonNull PlaceLayout onScreen,
                                    @NonNull EdgeStackPolicy.Metrics metrics) {
        if (!reaches || !barShown) return 0;
        return EdgeStackPolicy.topOverlapPx(onScreen, metrics, true);
    }

    /**
     * How far below the canvas's top a Workspace page starts: the bar's band, wherever the canvas
     * reaches up under it. The band is the one the bar on screen stands in when the layout on
     * screen has it on the top edge (the Workspace's own, or the bar a slide from Home has brought
     * over Home), and otherwise the band the Workspace's layout will give it, so the terminal
     * waiting beside Home has the size it has on the Workspace. 0 while the canvas stands below
     * the bar, and for a Workspace whose bar is not along the top or is put away.
     */
    public static int workspaceTopInsetPx(boolean reaches, boolean barShown,
                                          @NonNull PlaceLayout onScreen,
                                          @NonNull PlaceLayout workspace,
                                          @NonNull EdgeStackPolicy.Metrics metrics) {
        if (!reaches || !barShown) return 0;
        int standing = EdgeStackPolicy.topOverlapPx(onScreen, metrics, true);
        if (standing > 0) return standing;
        return EdgeStackPolicy.topOverlapPx(workspace, metrics, true);
    }

    /**
     * How far below the canvas's top a Home page starts: the band of the bar Home's own layout
     * stands along the top ({@code home}, as Home resolves it, never a slide's borrowed bar),
     * wherever the canvas reaches up under the bar, so Home's grid starts below its bar as a
     * window's page does (#90). 0 while the canvas stands below the bar, and for a Home whose
     * bar is put away or on another edge: that Home starts at the canvas's top.
     */
    public static int homeTopInsetPx(boolean reaches, boolean barShown,
                                     @NonNull PlaceLayout home,
                                     @NonNull EdgeStackPolicy.Metrics metrics) {
        if (!reaches || !barShown) return 0;
        return EdgeStackPolicy.topOverlapPx(home, metrics, true);
    }

    /**
     * Where {@code page} starts below the canvas's top: a Home page at Home's inset ({@link
     * #homeTopInsetPx}); every Workspace page —
     * {@code null} is one the wall does not know, the display window's — at the Workspace's inset.
     */
    public static int pageTopInsetPx(@Nullable PaneWallPage page, int workspaceTopInsetPx,
                                     int homeTopInsetPx) {
        if (page == PaneWallPage.WIDGETS) return Math.max(0, homeTopInsetPx);
        return Math.max(0, workspaceTopInsetPx);
    }

    /**
     * Where the flat body surface behind the terminal starts below the canvas's top: where the
     * canvas started before it reached under the bar, so the body never shows under the bar on
     * the Workspace — the canvas's reach while the chrome is committed to a window, 0 on Home.
     */
    public static int bodySurfaceTopPx(@NonNull PaneWallPage committed, int canvasReachPx) {
        return committed == PaneWallPage.WIDGETS ? 0 : Math.max(0, canvasReachPx);
    }
}
