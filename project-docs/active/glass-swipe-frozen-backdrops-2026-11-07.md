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

- **Backdrop layers (chosen 2026-11-07, after the first build attempt found the cause).** Today a
  Live surface records everything beneath it as one picture, so on a swipe its stale test (the
  surface's matrix against the window) fails every frame and it re-records and re-blurs all of it.
  Instead each Live surface holds its **backdrop layers**, a short stack of pictures that each move
  on their own:
  - **ground**: the stored pre-blurred wallpaper frame sampled at the parallax offset (as the
    Wallpaper kind already does), or over a live scene the scene frame at its glass rate;
  - **pages**: the page picture (§2a), moved by the pages' travel;
  - **local**: what lies beneath the surface on its own page or float, recorded and blurred once.
  A swipe frame only moves the layers; a layer is recorded again only when its own content changes
  or the swipe ends. The glass program composites the layers in one draw. Content that changes
  during the swipe shows as it was at swipe start and refreshes on landing.
- **Scope, first build:** while pages move only. At rest glass works as today, but the layers are
  shaped so rest can use them later (a float over a printing terminal re-recording only its local
  layer) without rework.
- **Soft edges:** blurring layers separately differs from blurring their composite at soft edges of
  translucent content. Accepted where it is not visible side by side on pong; a visible case records
  that pair as one layer.
- **No pop (ship gate):** the first and last swipe frames look identical to the resting glass,
  checked on pong with screenshots before, during and after. Fails ship.
- **Not ready:** a layer that has not landed (the page picture is still rendering) falls back to
  today's live recording for those frames, which must still pass the no-pop gate. Never blank glass.
- **Scope, first round:** page swipes for every page kind (Home, terminal, Assistant, Display) and
  every transition setting. Dragging a floating terminal keeps today's live glass.
- **Look-through depth setting:** Battery & performance gets "See through: All layers | One layer",
  default All layers, in the same build. One layer means the topmost glass shows only what is
  directly beneath it (the glass layers below it are dropped from its backdrop layers). It is a
  manual setting; nothing switches by itself.
- **Records:** an amendment to ADR 0021 (backdrop layers replace the single recording and fold the
  §2a page layer into the general rule) and a "Backdrop layers" entry in docs/GLOSSARY.md, with the
  first build.
- **Rejected:** freezing glass with its page (seams, not live); never layering pages that hold glass
  (keeps the per-frame re-record, the larger cost); the wall drawing all glass beneath page content
  (still re-blurs every frame, large rework, glass no longer owned by its surface).

## Measuring

The same scripted swipe run on pong, within one session (the phone's temperature moves every
section by about 1.5× between sessions): Custom look before and after, then Solid as the floor.
Targets: RenderThread "flush layers" near Solid's 1.4 ms per swipe frame, and swipe p90 within a
few ms of Solid's.
