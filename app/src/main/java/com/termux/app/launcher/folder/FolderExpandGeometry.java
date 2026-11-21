package com.termux.app.launcher.folder;

/**
 * How a folder grows out of its icon, as Launcher3's folder does: the panel is laid out where it
 * rests (against its anchor, inside the screen) and only its scale and alpha are played. At the
 * start it is scaled down to the icon's size about a pivot: the icon's centre, clamped into the
 * panel's own bounds, which is the panel point nearest the icon since the panel rests beside it.
 * A panel scaled about a point inside itself never leaves its bounds, so a popup window sized to
 * the panel never clips the grow. At the end it is the identity. Because the panel never moves in
 * layout, nothing is measured or laid out while it grows, and it settles exactly on the rest
 * position the anchored placement chose.
 *
 * <p>Build one with {@link #of} once the icon and the panel are on screen, both rects in the same
 * coordinate space (screen pixels), then ask {@link #frameAt} per frame. No Android types, so the
 * numbers are pinned by a JVM test.</p>
 */
public final class FolderExpandGeometry {
    /** How long a folder takes to open from its icon or to close back into it. */
    public static final long DURATION_MS = 200L;

    /** The part of the motion the alpha takes: opaque from here on, so the grow reads as solid. */
    public static final float FADE_FRACTION = 0.4f;

    /** The smallest start scale, so a tiny icon does not start the panel as a speck. */
    public static final float MIN_SCALE = 0.1f;

    /** The start scale with no icon to grow from: a small settle, not a grow. */
    public static final float FALLBACK_SCALE = 0.94f;

    /** One frame of the motion; a caller keeps one and has {@link #frameAt} fill it. */
    public static final class Frame {
        public float scale = 1f;
        public float alpha = 1f;
    }

    private final float scale0;
    private final float pivotX;
    private final float pivotY;

    private FolderExpandGeometry(float scale0, float pivotX, float pivotY) {
        this.scale0 = scale0;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
    }

    /** The motion with no icon to grow from: a 0.94 scale and a fade about the panel's centre. */
    public static FolderExpandGeometry fallback(int panelWidth, int panelHeight) {
        return new FolderExpandGeometry(FALLBACK_SCALE, panelWidth / 2f, panelHeight / 2f);
    }

    /**
     * The motion from the icon's rect to the panel's rest rect. An empty or zero-size icon rect,
     * or an empty panel, gives {@link #fallback}.
     */
    public static FolderExpandGeometry of(int iconLeft, int iconTop, int iconRight, int iconBottom,
                                          int panelLeft, int panelTop, int panelRight,
                                          int panelBottom) {
        float iconW = iconRight - iconLeft;
        float iconH = iconBottom - iconTop;
        float panelW = panelRight - panelLeft;
        float panelH = panelBottom - panelTop;
        if (iconW <= 0f || iconH <= 0f || panelW <= 0f || panelH <= 0f) return fallback(Math.max(0, (int) panelW), Math.max(0, (int) panelH));
        float scale = Math.max(iconW / panelW, iconH / panelH);
        scale = Math.max(MIN_SCALE, Math.min(1f, scale));
        float px = Math.max(0f, Math.min(panelW, (iconLeft + iconRight) / 2f - panelLeft));
        float py = Math.max(0f, Math.min(panelH, (iconTop + iconBottom) / 2f - panelTop));
        return new FolderExpandGeometry(scale, px, py);
    }

    public float startScale() { return scale0; }

    /** The scale's pivot, in the panel's own pixels from its top-left. */
    public float pivotX() { return pivotX; }
    public float pivotY() { return pivotY; }

    /** Fills {@code out} with the scale and alpha at progress {@code t} (0 at the icon, 1 at rest). */
    public void frameAt(float t, Frame out) {
        float p = Math.max(0f, Math.min(1f, t));
        out.scale = scale0 + (1f - scale0) * p;
        out.alpha = Math.min(1f, p / FADE_FRACTION);
    }
}
