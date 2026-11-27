package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where a Linux window stands and what it shows (spec #384, "The window contract"): its place in
 * the Terminal's row, its chip's name, whether it holds a surface, where its app's pixels start
 * and where a dialog draws over its parent. Pure; the controller and the page view apply the answers.
 */
public final class LinuxWindowRow {

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

    /** Whether the window is a layer surface (an overlay or top layer launcher or bubble), not a window of the row. */
    public static boolean isLayer(int flags) {
        return (flags & ILinuxWindows.WINDOW_LAYER) != 0;
    }

    /** Whether the service re-sent this window to a newly registered launcher (it was open before). */
    public static boolean replayed(int flags) {
        return (flags & ILinuxWindows.WINDOW_REPLAYED) != 0;
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
     * Where the app's pixels start below the window's top: the status row's room and nothing more.
     * The window controls live on the window's mark, so no band stands above the app for a tab.
     */
    public static int slotTopPx(int topInsetPx) {
        return Math.max(0, topInsetPx);
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
     * Where a layer surface of {@code w} x {@code h} (its drawn size, else the size the host
     * configured it with) stands in a slot of {@code slotW} x {@code slotH}: on an axis it is
     * anchored to one edge it sits that far in by its margin, anchored to both it is centred
     * between the margins, anchored to neither it is centred. Never larger than the slot. {@code
     * margins} is top, right, bottom, left in px; {@code out} gets left, top, width, height.
     * Exclusive zones are ignored.
     */
    public static void layerFrame(int slotW, int slotH, int w, int h, int anchor, @NonNull int[] margins,
                                  @NonNull int[] out) {
        int fw = Math.max(1, Math.min(w > 0 ? w : slotW, Math.max(1, slotW)));
        int fh = Math.max(1, Math.min(h > 0 ? h : slotH, Math.max(1, slotH)));
        out[0] = along(slotW, fw, margins[3], margins[1], (anchor & ILinuxWindows.LAYER_ANCHOR_LEFT) != 0,
            (anchor & ILinuxWindows.LAYER_ANCHOR_RIGHT) != 0);
        out[1] = along(slotH, fh, margins[0], margins[2], (anchor & ILinuxWindows.LAYER_ANCHOR_TOP) != 0,
            (anchor & ILinuxWindows.LAYER_ANCHOR_BOTTOM) != 0);
        out[2] = fw;
        out[3] = fh;
    }

    /** One axis of {@link #layerFrame}: the start of a {@code size} long span in {@code room}. */
    private static int along(int room, int size, int startMargin, int endMargin, boolean start, boolean end) {
        int at;
        if (start && end) at = startMargin + (room - startMargin - endMargin - size) / 2;
        else if (start) at = startMargin;
        else if (end) at = room - endMargin - size;
        else at = (room - size) / 2;
        return Math.max(0, Math.min(at, Math.max(0, room - size)));
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

    /**
     * Whether a window of {@code drawnW} x {@code drawnH} is bigger than its slot of {@code slotW} x
     * {@code slotH} on either axis, so it is shown whole ({@link #fixedFrame}) instead of filling
     * the slot. A window that has not drawn yet (0) or a slot not laid out fits.
     */
    public static boolean overflowsSlot(int slotW, int slotH, int drawnW, int drawnH) {
        if (slotW <= 0 || slotH <= 0 || drawnW <= 0 || drawnH <= 0) return false;
        return drawnW > slotW || drawnH > slotH;
    }

    /**
     * A touch at {@code v} px along an axis of a view {@code viewPx} long, in the units of the size
     * the host was told ({@code sentPx}), which it maps onto the app. They differ for a Main window
     * shown whole at its drawn size while it is told its slot's ({@link #fixedFrame}); otherwise
     * they are equal and the touch passes unchanged, as it does before either size is known.
     */
    public static float toConfigured(float v, int viewPx, int sentPx) {
        if (viewPx <= 0 || sentPx <= 0 || viewPx == sentPx) return v;
        return v * sentPx / viewPx;
    }
}
