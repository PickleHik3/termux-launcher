package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * v1's wall under Terl (the Terl switch on, with the tiling desktop built): Home's pages, and the
 * AI page left of Home 1 while it is on (ADR 0043), ordered when the wall is laid out and never
 * written into the wall's pages. Terminals and the Display are windows of the
 * **Workspace** stack below Home, so the wall lays out no Terminal or Display page; the wall's
 * Assistant page is the AI page, laid out only while it is on. Home's page glyph row is Terl's own
 * {@code home.page_dots} (off by default; the control bar shows a dot per Home page), naming Home's
 * pages only when on. With the switch off every answer is v1's.
 *
 * <p>Pure: the switch goes in, the answers come out.
 */
public final class TerlWall {

    private TerlWall() {}

    /**
     * Settings › Home › Page indicator as the Home grid wears it: under Terl Terl's own
     * {@code home.page_dots} ({@code terlDots}, off by default), never v1's choice.
     */
    @androidx.annotation.NonNull
    public static com.termux.app.launcher.home.HomePageIndicator indicator(
            @androidx.annotation.NonNull com.termux.app.launcher.home.HomePageIndicator chosen,
            boolean terl, boolean terlDots) {
        if (!terl) return chosen;
        return terlDots ? com.termux.app.launcher.home.HomePageIndicator.GLYPHS
            : com.termux.app.launcher.home.HomePageIndicator.OFF;
    }

    /** Whether the wall lays out a page of {@code kind}: under Terl only Home's (the AI page off). */
    public static boolean laysOut(@Nullable PageKind kind, boolean terl) {
        return laysOut(kind, terl, false);
    }

    /**
     * Whether the wall lays out a page of {@code kind}: under Terl Home's, and the Assistant page
     * while the AI page ({@code aiPage}) is on.
     */
    public static boolean laysOut(@Nullable PageKind kind, boolean terl, boolean aiPage) {
        return !terl || kind == PageKind.HOME || (aiPage && kind == PageKind.ASSISTANT);
    }

    /**
     * The id the AI page's cell goes by: the wall's own Assistant page when it has one (its chats
     * are the Assistant's either way, ADR 0022), else a kind id of the wall's. Nothing is written
     * into the wall's pages for it: v1's order stays the user's (ADR 0043 §6).
     */
    public static int aiPageId(@Nullable WallPages pages) {
        WallPages.Page assistant = pages == null ? null : pages.assistant();
        return assistant != null ? assistant.id : PaneWallLayout.kindPageId(PageKind.ASSISTANT);
    }

    /**
     * The ids the wall lays out, in order: with the switch off every page as v1 keeps them; under
     * Terl Home's pages, with the AI page first while {@code aiPage} is on. The order is the
     * layout's alone; the pages are never changed.
     */
    @NonNull
    public static java.util.List<Integer> laidOutIds(@NonNull WallPages pages, boolean terl,
                                                      boolean aiPage) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (terl && aiPage) ids.add(aiPageId(pages));
        for (WallPages.Page page : pages.pages()) {
            if (terl && page.kind != PageKind.HOME) continue;
            ids.add(page.id);
        }
        return ids;
    }
}
