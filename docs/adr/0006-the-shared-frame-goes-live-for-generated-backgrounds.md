---
status: accepted
date: 2026-09-30
amends: 0002 (the frame is no longer always a still; it can be re-blurred as the wallpaper advances), 0004 (the surfaces read a live frame, picked at draw time)
---

# The shared frame goes live for generated backgrounds, and the system keeps the rest pose

ADR 0002 blurred the wallpaper once per radius into a frame 1.5× the screen width and left
real-time blur as "an experiment for a future live wallpaper". ADR 0004 had every glass surface
sample that one frame at its live position. Both assumed the frame is a still. Generated backgrounds
(`project-docs/active/animated-wallpaper/SPEC.md`, issue #41) are AGSL wallpapers drawn by the
launcher itself, behind everything, and the glass has to show them moving, blurred and in register.

We decided that the shared frame goes live. While a generated background plays, the wallpaper's
shader is drawn into a small source node once per radius in use, blurred by a `RenderEffect`, and
rendered into a ring of hardware buffers (`LiveWallpaperRenderer`, `LiveWallpaperFrames`). The
readers keep sampling one frame at the surface's live position; only the pick changes. At draw time
each reader takes the live frame for its radius when there is one and the still otherwise, so a
software canvas or a paused launcher draws today's picture. Nothing is re-blurred by a slide, a pan
or a keyboard travel, since those still change only the parallax and the aim. The radius-0 frame is
the shader itself, drawn by the self-drawn backdrop. A paused background keeps its last frame, and
costs nothing.

**The system is given the rest pose.** Every built-in has an `energy` uniform. At energy 0 its
picture is fixed and independent of time; the motion is scaled by energy and never cross-faded. The
still that goes to `ManagedWallpaper.apply` is rendered at energy 0, not at t=0. So the lock screen
and other apps show exactly what the launcher settles on, and the lock moment eases energy to 0
before the screen locks so that the keyguard picks up the same picture. The launcher does not run a
`WallpaperService`: under one, the glass would lose its blur (`WallpaperPicture.NO_STILL`), and the
keyguard would still show a still. The alternative, a t=0 still, would jump against the settled
launcher whenever the animation had moved on.

**The gate is Fancier Glass, with an API 34 floor.** A generated background plays only where
`FancierGlassPolicy` is active, the SDK is 34 or later, a background id is stored, the managed
picture is on screen and the backdrop is self-drawn. Fancier Glass's own floor is API 33, and the
renderer needs `HardwareBufferRenderer` (API 34), so the two floors stay separate conditions: on
API 33, Fancier Glass works and the Animated row stays hidden. The developer decided on 2026-09-30
that the toggle is the one switch, which replaces the earlier draft's "plays with Fancier Glass on
or off" (SPEC.md §13 Q2). Turning it off leaves the background on its still; turning it on again
resumes it. When to play is one pure class, `WallpaperDirector`, which also owns the moments, the
energy and the lock delay.

**No kitty-style post-process, and no user shaders.** kitty runs Slang shaders over the finished
desktop window. A post-process here would be a full-window pass over every place, glass and text
included, on top of the blur the frame already costs, and it would colour the chrome ink that
`ChromeInk` samples from the still. Generators draw behind everything and reach the glass through
the frame for free, which is the design's point. A shader is also code, so shader packs and
`custom_shaders` from kitty.conf are not read: a kitty.conf synced from a desktop must not change
the phone's wallpaper. Several of kitty's shaders cannot be copied under their licences (`fireworks`
and `crt` contain CC BY-NC-SA code), so every built-in is written from scratch and
`THIRD_PARTY_NOTICES.md` gains nothing.

Consequences: `SharedFrameDrawable`, `PaneGlassBackdropView` and `PaneControlsView` gain the draw-time
pick, and `WallpaperBackdropView` a live mode. The system's colours are re-derived from the still
we set, so the Material palette is captured at set time and on a scheme change and never from
`OnColorsChangedListener`, which would loop. Phase 0 (SPEC.md §10) measures the render cost and the
full-window redraw on pong before any of this is built.
