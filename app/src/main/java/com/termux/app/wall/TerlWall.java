package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * v1's wall under Terl (the Terl switch on, with the tiling desktop built): Home's pages, and the
 * AI page left of Home 1 while it is on (ADR 0043). Terminals and the Display are windows of the
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
     * The edit that stands the AI page left of Home 1 (ADR 0043): the wall's Assistant page, added
     * when it has none (its chats are kept by the Assistant either way, ADR 0022), moved before
     * every other page. Nothing changes when it already stands first, or on a wall with no Home
     * page to stand beside.
     */
    @NonNull
    public static WallPages.Result placeAiPage(@NonNull WallPages pages) {
        if (pages.homePages().isEmpty()) {
            return new WallPages.Result(pages, WallPages.ChangeType.NONE, null, null, -1, -1);
        }
        WallPages.Page page = pages.assistant();
        if (page == null) {
            WallPages.Result added = pages.add(PageKind.ASSISTANT, WallPages.END, null);
            if (!added.ok() || added.page == null) return added;
            WallPages.Result moved = added.pages.move(added.page.id, 0);
            if (!moved.ok()) return added;
            return new WallPages.Result(moved.pages, WallPages.ChangeType.ADDED, added.page, null,
                -1, 0);
        }
        return pages.move(page.id, 0);
    }
}
