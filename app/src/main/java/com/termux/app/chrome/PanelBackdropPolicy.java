package com.termux.app.chrome;

import android.os.Build;

/**
 * Which picture a panel (the app drawer, the command palette, the terminal sheet, a popup menu)
 * shows behind its glass, and when a Snapshot is taken. Pure, so every rule is testable without a
 * window; {@link PanelBackdrop} applies the answers.
 *
 * <ul>
 *   <li><b>Live</b>: Live blur is on and the panel is in the launcher's own window: the recorded
 *   picture refreshes on frames where something beneath it changed.</li>
 *   <li><b>Snapshot</b>: the same recording, blurred once when the panel opens and again when it
 *   settles, and frozen while it moves: on the GPU from Android 12, and below it (no
 *   {@code RenderEffect}, so no Live either) drawn in software and blurred by RenderScript.</li>
 *   <li><b>Stored</b>: with no recorder, or below Android 12 once the software blur has failed: the
 *   panel shows the stored pre-blurred wallpaper frame instead.</li>
 * </ul>
 */
public final class PanelBackdropPolicy {

    public enum Kind { LIVE, SNAPSHOT, STORED }

    /** The first Android that can picture what is beneath a panel: a software Snapshot. */
    public static final int MIN_SDK = Build.VERSION_CODES.O;

    /** The first Android that records and blurs on the GPU ({@code RenderEffect}): Live, and GPU Snapshots. */
    public static final int MIN_GPU_SDK = Build.VERSION_CODES.S;

    private PanelBackdropPolicy() {}

    /** Whether a panel can show a recorded picture at all here. */
    public static boolean canRecord(int sdkInt) {
        return sdkInt >= MIN_SDK;
    }

    /** Whether the picture is recorded and blurred on the GPU; below this a Snapshot is drawn in software. */
    public static boolean onGpu(int sdkInt) {
        return sdkInt >= MIN_GPU_SDK;
    }

    /**
     * Whether a panel shows a recorded picture now: it can record here, and, where that is the
     * software Snapshot, the software blur has not failed in this process ({@code softwareFailed}).
     * When false the panel shows the stored wallpaper frame ({@code WallpaperFrostPainter}).
     */
    public static boolean pictured(int sdkInt, boolean softwareFailed) {
        return canRecord(sdkInt) && (onGpu(sdkInt) || !softwareFailed);
    }

    /**
     * @param recorderAvailable the window has a recorder ({@link LiveBackdrop}) to record with
     * @param liveBlur          {@link LiveBlurPolicy#live} as it stands now
     * @param sameWindow        the panel is inside the recorder's window (a popup menu is not, and
     *                          the recorder only refreshes surfaces in its own window)
     * @param liveSlotFree      the recorder can take one more live surface
     */
    public static Kind kind(int sdkInt, boolean recorderAvailable, boolean liveBlur,
                            boolean sameWindow, boolean liveSlotFree) {
        if (!canRecord(sdkInt) || !recorderAvailable) return Kind.STORED;
        if (!onGpu(sdkInt)) return Kind.SNAPSHOT;
        return liveBlur && sameWindow && liveSlotFree ? Kind.LIVE : Kind.SNAPSHOT;
    }

    /**
     * Whether this motion change takes a Snapshot: only a Snapshot panel, only while open, and
     * only when it comes to rest. Nothing is taken while it moves, so the picture stays frozen.
     */
    public static boolean snapshotOnMotion(Kind kind, boolean open, boolean wasMoving,
                                           boolean moving) {
        return kind == Kind.SNAPSHOT && open && wasMoving && !moving;
    }

    /** Whether opening takes a Snapshot: every Snapshot panel does, so it never shows a stale one. */
    public static boolean snapshotOnOpen(Kind kind) {
        return kind == Kind.SNAPSHOT;
    }
}
