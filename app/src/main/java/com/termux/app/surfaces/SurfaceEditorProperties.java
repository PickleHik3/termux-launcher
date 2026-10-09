package com.termux.app.surfaces;

/**
 * The Appearance editor's live-preview scopes.
 *
 * <p>A control's ticks fire far faster than a full re-apply fits in a frame, so each write
 * declares only what it actually touches and the editor coalesces the requests to one apply per
 * animation frame. GLASS (the accessory re-render) runs on every apply; BLUR additionally re-blurs
 * the wallpaper frames, the single most expensive thing a slider can cause, so only the Blur
 * control asks for it — and mid-drag it waits for the release.
 *
 * <p>The per-surface row tables that used to live here went with the per-surface cards
 * (appearance-layout-editor SPEC §6): Custom's row 2 writes through {@link AppearanceLooks}'s rules,
 * and a Look through {@link SurfacePresets}.
 */
public final class SurfaceEditorProperties {

    private SurfaceEditorProperties() {}

    public static final int PREVIEW_GLASS = 1;
    public static final int PREVIEW_BLUR = 1 << 1;
    public static final int PREVIEW_GEOMETRY = 1 << 2;
    public static final int PREVIEW_SURFACES = 1 << 3;
    public static final int PREVIEW_KEYBOARD = 1 << 4;
    /**
     * Lets the geometry pass tell the shell: the terminal resize (a SIGWINCH per reflow) is worth
     * one settle, not one per tick.
     */
    public static final int PREVIEW_GEOMETRY_COMMIT = 1 << 5;
    public static final int PREVIEW_ALL = PREVIEW_GLASS | PREVIEW_BLUR | PREVIEW_GEOMETRY
        | PREVIEW_SURFACES | PREVIEW_KEYBOARD;
}
