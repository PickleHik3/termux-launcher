# Glass during page swipes: frozen backdrops, live look (2026-11-07)

Decided with the developer on 2026-11-07. Builds on ADR 0021 (glass kinds) and the page layer
(§2a, `PageLayerCapture`, `WallSwipeGlassPolicy`).

## Problem

On pong, with the developer's Custom look (Opacity 12, Blur 20, Edge 40, Depth 90), Home ↔
Workspace swipes miss frames: p90 40–57 ms against 24 ms with the Solid look. Blur and refraction
each cost almost nothing (turning either off leaves the per-frame render cost unchanged). The cost is
translucency: the RenderThread re-renders the layers beneath each see-through glass surface on every
swipe frame ("flush layers" 4.1 ms/frame vs 1.4 on Solid; full-screen draw 9 ms vs 6 ms).

## What the developer keeps (non-negotiable)

1. While swiping across any kind of page, under any page-transition setting, glass surfaces look
   like live glass refracting what is beneath them. Glass on glass stays.
2. Floating terminals are glass on glass. Two stacked floats show the float behind and the Home
   screen behind, through both.
3. AGSL live scenes keep animating during a swipe. Glass over bare wallpaper keeps its existing
   live refresh rate over a scene (`SceneFramePolicy` glass rate, 15 Hz by default).

## Decisions

- **Frozen backdrops for the swipe.** At swipe start, what lies beneath each glass surface is
  recorded once as a picture per page (the page layer), and the glass refracts that picture as it
  slides. Nothing beneath glass is re-rendered per swipe frame. Content that changes during the
  swipe (terminal output, a clock tick, a widget) shows as it was at swipe start and refreshes on
  landing.
- **Scenes are the exception.** The wallpaper layer under the frozen pages is not frozen: over a live
  scene, glass keeps re-reading the wallpaper at the scene glass rate. Only the pages' own content is
  pictured.
- **Scope, first round:** page swipes for every page kind (Home, terminal, Assistant, Display) and
  every transition setting. Dragging a floating terminal keeps today's live glass.
- **Look-through depth setting:** Appearance › Global gets "See through: All layers | One layer",
  default All layers. One layer means the topmost glass shows only what is directly beneath it (not
  glass seen through glass). It is a manual setting; nothing switches by itself.

## Measuring

The same scripted swipe run on pong, within one session (the phone's temperature moves every
section by about 1.5× between sessions): Custom look before and after, then Solid as the floor.
Targets: RenderThread "flush layers" near Solid's 1.4 ms per swipe frame, and swipe p90 within a
few ms of Solid's.
