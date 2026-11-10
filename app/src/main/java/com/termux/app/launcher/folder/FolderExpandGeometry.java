package com.termux.app.launcher.folder;

/**
 * How a folder grows out of its icon, as Launcher3's folder does: the panel is laid out where it
 * rests (against its anchor, inside the screen) and only its transform is played. At the start it
 * is scaled about its own centre down to the icon's size and translated so its centre sits on the
 * icon's centre, with nothing showing; at the end it is the identity. Because the panel never
 * moves in layout, nothing is measured or laid out while it grows, and it settles exactly on the
 * rest position the anchored placement chose.
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
        public float translationX;
        public float translationY;
        public float alpha = 1f;
    }

    private final float scale0;
    private final float translationX0;
    private final float translationY0;

    private FolderExpandGeometry(float scale0, float translationX0, float translationY0) {
        this.scale0 = scale0;
        this.translationX0 = translationX0;
        this.translationY0 = translationY0;
    }

    /** The motion with no icon to grow from: a 0.94 scale and a fade, no translation. */
    public static FolderExpandGeometry fallback() {
        return new FolderExpandGeometry(FALLBACK_SCALE, 0f, 0f);
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
        if (iconW <= 0f || iconH <= 0f || panelW <= 0f || panelH <= 0f) return fallback();
        float scale = Math.max(iconW / panelW, iconH / panelH);
        scale = Math.max(MIN_SCALE, Math.min(1f, scale));
        float tx = (iconLeft + iconRight) / 2f - (panelLeft + panelRight) / 2f;
        float ty = (iconTop + iconBottom) / 2f - (panelTop + panelBottom) / 2f;
        return new FolderExpandGeometry(scale, tx, ty);
    }

    public float startScale() { return scale0; }
    public float startTranslationX() { return translationX0; }
    public float startTranslationY() { return translationY0; }

    /** Fills {@code out} with the transform at progress {@code t} (0 at the icon, 1 at rest). */
    public void frameAt(float t, Frame out) {
        float p = Math.max(0f, Math.min(1f, t));
        out.scale = scale0 + (1f - scale0) * p;
        out.translationX = translationX0 * (1f - p);
        out.translationY = translationY0 * (1f - p);
        out.alpha = Math.min(1f, p / FADE_FRACTION);
    }
}
