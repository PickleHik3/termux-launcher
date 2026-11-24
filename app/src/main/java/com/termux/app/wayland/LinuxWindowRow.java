package com.termux.app.wayland;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * Where a Linux window stands and what it shows (spec #384, "The window contract"): its place in
 * the Terminal's row, its chip's name, whether it holds a surface, where its rim tab sits and
 * where a dialog draws over its parent. Pure; the controller and the page view apply the answers.
 */
public final class LinuxWindowRow {

    /** No page: a Linux window that has none (a dialog, a minimised window). */
    public static final int NO_PAGE = -1;

    /** The rim tab's size and its gap to the rim, in dp. */
    public static final float TAB_WIDTH_DP = 40f;
    public static final float TAB_HEIGHT_DP = 18f;
    /** The band at the window's top the tab sits in: the app's pixels start below it. */
    public static final float TAB_BAND_DP = 22f;

    private LinuxWindowRow() {}

    /**
     * The page a new Linux window is added after: the window in front when it is a Linux window,
     * else the Terminal page (whose windows the wall pages through inside one cell), else the page
     * in front, else the end of the row ({@link #NO_PAGE}).
     */
    public static int insertAfter(@Nullable PageKind current, int currentPageId, int terminalPageId) {
        if (current == PageKind.LINUX && currentPageId >= 0) return currentPageId;
        if (terminalPageId >= 0) return terminalPageId;
        return currentPageId >= 0 ? currentPageId : NO_PAGE;
    }

    /**
     * A restored window's place: after the page that stood before it when it was minimised, while
     * that page is still on the wall; else where a new window goes.
     */
    public static int restoreAfter(int leftWhenMinimised, boolean leftStillThere,
                                   @Nullable PageKind current, int currentPageId, int terminalPageId) {
        if (leftWhenMinimised >= 0 && leftStillThere) return leftWhenMinimised;
        return insertAfter(current, currentPageId, terminalPageId);
    }

    /**
     * A Linux window's chip name: its app's name from the desktop entry, else its app id, else
     * {@code fallback}. Never the window's title, which the app changes as it likes.
     */
    @NonNull
    public static String chipLabel(@Nullable String appName, @Nullable String appId,
                                   @NonNull String fallback) {
        if (appName != null && !appName.trim().isEmpty()) return appName.trim();
        if (appId != null && !appId.trim().isEmpty()) return appId.trim();
        return fallback;
    }

    /**
     * Whether a window gets a page of its own: a dialog whose parent is open draws over the
     * parent's window instead.
     */
    public static boolean ownsPage(long parentId, boolean parentOpen) {
        return parentId == 0L || !parentOpen;
    }

    /**
     * Whether the window bound to the Linux view holds its surface: only while its cell is the
     * one in front or next to it ({@code LiveNeighbourPolicy}'s reach). Any other window, and a
     * bound one the wall left further away, gives its buffers back; the compositor keeps its last
     * frame and repaints it when the surface returns.
     */
    public static boolean holdsSurface(boolean bound, int windowCell, int currentCell) {
        if (!bound || windowCell < 0 || currentCell < 0) return false;
        return Math.abs(windowCell - currentCell) <= 1;
    }

    /**
     * The rim tab in the window's own coordinates: at the top-right, inside the outline (clear of
     * the corner's curve by {@code cornerRadiusPx}) and in the band above the app's pixels.
     */
    public static void tabRect(int widthPx, float cornerRadiusPx, float density, @NonNull Rect out) {
        int w = Math.round(TAB_WIDTH_DP * density);
        int h = Math.round(TAB_HEIGHT_DP * density);
        int band = bandPx(density);
        int right = widthPx - Math.max(Math.round(cornerRadiusPx), Math.round(4f * density));
        int top = Math.max(0, (band - h) / 2);
        out.set(Math.max(0, right - w), top, Math.max(0, right), top + h);
    }

    /** The band above the app's pixels the tab sits in, in px. */
    public static int bandPx(float density) {
        return Math.round(TAB_BAND_DP * Math.max(0f, density));
    }

    /**
     * Where a dialog draws over its parent's area of {@code parentW} x {@code parentH}: centred,
     * at its own drawn size but never larger than the area. {@code out} gets left, top, width,
     * height; a dialog that has not drawn yet (0) takes the area's size until it has.
     */
    public static void dialogFrame(int parentW, int parentH, int drawnW, int drawnH, @NonNull int[] out) {
        int w = drawnW > 0 ? Math.min(drawnW, parentW) : parentW;
        int h = drawnH > 0 ? Math.min(drawnH, parentH) : parentH;
        out[0] = Math.max(0, (parentW - w) / 2);
        out[1] = Math.max(0, (parentH - h) / 2);
        out[2] = Math.max(0, w);
        out[3] = Math.max(0, h);
    }
}
