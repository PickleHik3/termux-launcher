package com.termux.app.wayland;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where a Linux window stands and what it shows (spec #384, "The window contract"): its place in
 * the Terminal's row, its chip's name, whether it holds a surface, where its rim tab sits and
 * where a dialog draws over its parent. Pure; the controller and the page view apply the answers.
 */
public final class LinuxWindowRow {

    /** The rim tab's size and its gap to the rim, in dp. */
    public static final float TAB_WIDTH_DP = 40f;
    public static final float TAB_HEIGHT_DP = 18f;
    /** The band at the window's top the tab sits in: the app's pixels start below it. */
    public static final float TAB_BAND_DP = 22f;

    private LinuxWindowRow() {}

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

    /** Where a window stands (see {@link #placement}). */
    public static final int PLACE_ROW = 0;
    public static final int PLACE_OVER_PARENT = 1;
    public static final int PLACE_OVER_FRONT = 2;

    /** The class in a window's flags: 0 Main, 1 Dialog, 2 Fixed, 3 Transient; anything unknown reads Main. */
    public static int windowClass(int flags) {
        int cls = (flags & ILinuxWindows.WINDOW_CLASS_MASK) >>> ILinuxWindows.WINDOW_CLASS_SHIFT;
        return cls >= ILinuxWindows.WINDOW_CLASS_MAIN && cls <= ILinuxWindows.WINDOW_CLASS_TRANSIENT
            ? cls : ILinuxWindows.WINDOW_CLASS_MAIN;
    }

    /**
     * Where a window of {@code windowClass} stands: a place of its own in the row (Main, Fixed), over its open parent (a dialog with a parent, and today's
     * rule for Main), or over the page in front (a Dialog without an open parent, and Transient).
     */
    public static int placement(int windowClass, long parentId, boolean parentOpen) {
        switch (windowClass) {
            case ILinuxWindows.WINDOW_CLASS_TRANSIENT:
                return PLACE_OVER_FRONT;
            case ILinuxWindows.WINDOW_CLASS_FIXED:
                return PLACE_ROW;
            case ILinuxWindows.WINDOW_CLASS_DIALOG:
                return parentId != 0L && parentOpen ? PLACE_OVER_PARENT : PLACE_OVER_FRONT;
            default:
                return ownsPage(parentId, parentOpen) ? PLACE_ROW : PLACE_OVER_PARENT;
        }
    }

    /** Whether a window of {@code windowClass} gets a page of its own in the row. */
    public static boolean ownsPage(int windowClass, long parentId, boolean parentOpen) {
        return placement(windowClass, parentId, parentOpen) == PLACE_ROW;
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
     * The row place of the terminal window nearest {@code front}, for a "Go to Terminal" from a
     * Linux window's page ({@code linux[place]} is true for a Linux window); the earlier place
     * wins a tie. -1 when the row holds no terminal window.
     */
    public static int nearestTerminalPlace(int front, @NonNull boolean[] linux) {
        for (int d = 0; d < linux.length; d++) {
            int before = front - d;
            int after = front + d;
            if (before >= 0 && before < linux.length && !linux[before]) return before;
            if (after >= 0 && after < linux.length && !linux[after]) return after;
        }
        return -1;
    }

    /**
     * The rim tab in the window's own coordinates: at the top-right, inside the outline (clear of
     * the corner's curve by {@code cornerRadiusPx}) and in the band above the app's pixels.
     */
    public static void tabRect(int widthPx, float cornerRadiusPx, float density, @NonNull Rect out) {
        tabRect(widthPx, cornerRadiusPx, density, 0, out);
    }

    /**
     * As {@link #tabRect(int, float, float, Rect)}, the band standing {@code topInsetPx} below the
     * window's top: the status row's room, which the Terminal's top pane holds too.
     */
    public static void tabRect(int widthPx, float cornerRadiusPx, float density, int topInsetPx,
                               @NonNull Rect out) {
        int w = Math.round(TAB_WIDTH_DP * density);
        int h = Math.round(TAB_HEIGHT_DP * density);
        int band = bandPx(density);
        int right = widthPx - Math.max(Math.round(cornerRadiusPx), Math.round(4f * density));
        int top = Math.max(0, topInsetPx) + Math.max(0, (band - h) / 2);
        out.set(Math.max(0, right - w), top, Math.max(0, right), top + h);
    }

    /** Where the app's pixels start below the window's top: the status row's room, then the band. */
    public static int slotTopPx(float density, int topInsetPx) {
        return Math.max(0, topInsetPx) + bandPx(density);
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

    /**
     * Where a Fixed window of {@code drawnW} x {@code drawnH} stands in its slot of {@code slotW} x
     * {@code slotH}: centred, at 1:1 when it fits, else scaled down uniformly until it does (never
     * stretched, never larger than 1:1). {@code out} gets the visible frame's left, top, width and
     * height; the result is the scale. A window that has not drawn yet (0) takes the slot, at 1.
     * The view is laid out at the drawn size and scaled about its centre, so touches map through the
     * view's own transform and a touch outside the frame never reaches it.
     */
    public static float fixedFrame(int slotW, int slotH, int drawnW, int drawnH, @NonNull int[] out) {
        float scale = 1f;
        int w = slotW;
        int h = slotH;
        if (drawnW > 0 && drawnH > 0) {
            w = drawnW;
            h = drawnH;
            if (slotW > 0 && slotH > 0 && (drawnW > slotW || drawnH > slotH)) {
                scale = Math.min((float) slotW / drawnW, (float) slotH / drawnH);
                w = Math.max(1, Math.round(drawnW * scale));
                h = Math.max(1, Math.round(drawnH * scale));
            }
        }
        out[0] = Math.max(0, (slotW - w) / 2);
        out[1] = Math.max(0, (slotH - h) / 2);
        out[2] = Math.max(0, w);
        out[3] = Math.max(0, h);
        return scale;
    }
}
