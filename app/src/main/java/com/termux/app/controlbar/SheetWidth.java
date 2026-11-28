package com.termux.app.controlbar;

/**
 * How wide a sheet of text rows stands (the Workspace list, a window's menu): its own width at the
 * default text size, wider in step with Android's font scale so a name that fit at 1.0 still fits
 * (round 4: "Workspac…" and "Move to Works…" at 1.3), never wider than the room it has. Pure.
 */
public final class SheetWidth {

    private SheetWidth() {}

    /** The width in px for a sheet {@code baseDp} wide at font scale 1, in {@code roomPx}. */
    public static int px(float baseDp, float density, float fontScale, int roomPx) {
        int wanted = Math.round(baseDp * density * Math.max(1f, fontScale));
        return roomPx > 0 ? Math.min(wanted, roomPx) : wanted;
    }
}
