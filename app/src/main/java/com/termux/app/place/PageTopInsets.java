package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.app.page.PageKind;


/**
 * Where each page of the wall starts at the top, so a slide across the seam between Home and
 * the Terminal never moves a page's content when it settles.
 *
 * <p>The wall's pages share one canvas. The Terminal's status bar takes a band at the top that
 * Home may not have (a Home layout can put the bar away or stand it elsewhere), so a canvas that
 * stood below the bar on the Terminal and reached the top on Home moved every page by the band
 * at settle: the terminal down as the bar took its band, Home up as it gave it back.
 *
 * <p>Instead, while a Home page can be on screen — the wall resting on Home, or moving at all —
 * the canvas reaches up under the bar ({@link #canvasReachPx}) and each page keeps its own top:
 * a Terminal page (the terminal's or the display window's) one bar band below it, and a Home
 * page one band of its own bar below it (the wall's {@code PageTopInset.pageTopInsetPx}): the same band as a window's
 * where Home wears the bar as the Terminal does (#90), so the two line up across the seam, and
 * the canvas's top itself where Home's layout stands no bar along the top. Resting on the Terminal, the
 * canvas stands below the bar as it always has and no page is inset, so everything that reads
 * the canvas at rest there reads what it always did. Switching between the two moves no page on
 * screen: the canvas grows up by exactly the inset its Terminal pages take, so the terminal
 * keeps its rect, its size and its grid.
 *
 * <p>Pure, so the arithmetic is read and tested on its own.
 */
public final class PageTopInsets {

    private PageTopInsets() {}

    /**
     * Whether the canvas reaches up under the status bar: while the chrome is committed to a
     * Home page, and whenever the wall is off its rest, so a slide from the Terminal toward Home
     * has Home's page at its own top from its first frame. Only a wall resting on a Terminal page
     * keeps the canvas below the bar.
     */
    public static boolean canvasReachesUnderBar(@NonNull PageKind committed,
                                                boolean wallAtRest) {
        return committed == PageKind.HOME || !wallAtRest;
    }

    /**
     * Whether the wall is at rest for {@link #canvasReachesUnderBar}: still, at offset 0, and on
     * the page the chrome is committed to. The wall's last offset callback of a landing comes
     * before the page is committed, so a wall standing still on Home while the chrome is still
     * committed to the Terminal is not at rest there: reading it so dropped Home's top inset to
     * 0 for one layout pass as Home landed.
     *
     * @param wallPage the kind of the page whose cell the wall is on
     */
    public static boolean wallAtRestOn(@NonNull PageKind committed, boolean wallMoving,
                                       float wallOffsetPx, @Nullable PageKind wallPage) {
        return !wallMoving && wallOffsetPx == 0f && wallPage == committed;
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
     * How far below the canvas's top a Terminal page starts: the bar's band, wherever the canvas
     * reaches up under it. The band is the one the bar on screen stands in when the layout on
     * screen has it on the top edge (the Terminal's own, or the bar a slide from Home has brought
     * over Home), and otherwise the band the Terminal's layout will give it, so the terminal
     * waiting beside Home has the size it has on the Terminal. 0 while the canvas stands below
     * the bar, and for a Terminal whose bar is not along the top or is put away.
     */
    public static int terminalTopInsetPx(boolean reaches, boolean barShown,
                                          @NonNull PlaceLayout onScreen,
                                          @NonNull PlaceLayout terminal,
                                          @NonNull EdgeStackPolicy.Metrics metrics) {
        if (!reaches || !barShown) return 0;
        int standing = EdgeStackPolicy.topOverlapPx(onScreen, metrics, true);
        if (standing > 0) return standing;
        return EdgeStackPolicy.topOverlapPx(terminal, metrics, true);
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
     * Where the flat body surface behind the terminal starts below the canvas's top: where the
     * canvas started before it reached under the bar, so the body never shows under the bar on
     * the Terminal — the canvas's reach while the chrome is committed to a window, 0 on Home.
     */
    public static int bodySurfaceTopPx(@NonNull PageKind committed, int canvasReachPx) {
        return committed == PageKind.HOME ? 0 : Math.max(0, canvasReachPx);
    }
}
