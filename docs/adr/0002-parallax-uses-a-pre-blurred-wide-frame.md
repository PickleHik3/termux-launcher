---
status: accepted
date: 2026-09-26
---

# Wallpaper parallax reads from a pre-blurred wide frame, not a real-time blur

A managed wallpaper now pans by a quarter of a screen per place slide (portrait only), and every
glass surface must stay aligned with the wallpaper behind it while it moves. We decided to keep
blurring the wallpaper once per radius, into a frame 1.5× the screen width, and to have each
surface shift where it samples that frame on every animation frame. Nothing is re-blurred while
the places slide.

The alternative was a real-time blur: `RenderEffect.createBlurEffect` (API 31+) or an AGSL shader
(API 33+) on each glass surface, blurring whatever is behind it every frame. For a still picture
that only moves, that produces the same image at the cost of one blur pass per surface per frame.
It leaves phones below API 31 without the effect, and it splits the frost, grain and tint tuning
into two paths. It stays an experiment for a future live wallpaper, where the content behind the
glass really does change per frame.

Consequences: a cached radius costs 1.5× the bytes, so the 72 MB budget holds about four radii at
1080×2412 instead of six. Surfaces that hold a cropped bitmap today (dock, keyboard, status bar,
window bar) must move to shader-offset sampling of the shared frame, as pane glass already does,
so that a slide never needs a new crop. A wallpaper cropped before this change is one screen wide
and simply does not pan until it is picked again.
