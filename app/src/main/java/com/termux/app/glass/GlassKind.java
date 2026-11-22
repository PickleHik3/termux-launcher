package com.termux.app.glass;

/**
 * What a glass surface draws (ADR 0029 section 2). A surface asks for one; the service grants it,
 * or Wallpaper when it cannot, and changes it only on a policy event.
 */
public enum GlassKind {
    /** The stored pre-blurred wallpaper frame, aimed where the surface stands. */
    WALLPAPER,
    /** One picture of what is beneath, taken as the surface opens. */
    SNAPSHOT,
    /** The live backdrop layers of what is beneath: ground, pages and local. */
    LIVE,
    /** The system's blur of what is behind the window (the keyboard over the X11 screen). */
    COMPOSITOR
}
