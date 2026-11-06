---
status: accepted
date: 2026-11-03
amends: performance.md rule 3 (glass samples the shared frame), ADR 0002 and 0004 (the stored frame stays the default, no longer the only picture)
---

# Glass records what is beneath it

Spec: [`project-docs/active/live-glass-2026-11-03.md`](../../project-docs/active/live-glass-2026-11-03.md).

Glass was a picture of the wallpaper alone, blurred once per radius and stored. A floating window
over Home could not show the widgets under it, and a card sliding in showed nothing but wallpaper.
The panels that did show what was behind them (drawer, palette, sheet, menus) used
`RealtimeBlurView`, a software blur that redrew the whole decor into a bitmap in a pre-draw
listener: about 13 ms a frame while live and 30 to 36 ms for a one-off capture. It was the cause
of most of the jank on the palette, sheet and drawer.

We decided the following.

## 1. One backdrop service, three kinds

Every glass surface declares one kind:

- **Wallpaper**: the stored pre-blurred frame. The status bar, window bar, plank, A-Z host and the
  other bars. Costs nothing while moving.
- **Snapshot**: the drawer, command palette, terminal sheet and menus while Live blur is off. The
  picture is recorded once into a GPU bitmap on open and on settle, and frozen while moving.
- **Live**: the dock, floating windows and widgets, and the panels too while Live blur is on. The
  picture is re-recorded and re-blurred on frames where something beneath it changed.

Panels follow the switch: Snapshot and Live are the same recorded picture, and the switch only
decides whether it refreshes while the panel is open.

## 2. One recorder: only what is beneath

`LiveBackdrop.recordBelow(surface)` walks from the surface up through the
`LiveBackdrop.Container` parents and draws, in z-order, only what is drawn before it: siblings,
the page, the wallpaper. A surface never records itself or anything above it, so glass on glass
falls out (a float shows the widgets' glass beneath it). The recording reuses each view's cached
display list and is blurred on the GPU at a quarter of the scale (`RenderEffect`). Every recording
draws the window's ground first (`LiveBackdrop.drawGround`), so a flat or self-drawn ground never
leaves the picture see-through.

## 3. One effect builder, one picture seam

`GlassEffects` builds the refraction effect for every dock style, the nav strip and Live widgets.
What a surface draws behind its glass comes through `GlassPicture`: `StoredPicture` wraps the
stored frame, `LivePicture` draws the recorded node at the surface's aim. There is no copy per
dock style or per surface; a dock style changes only `DockSurfaceSpec` numbers.

### 2a. The page layer (amendment, 2026-11-07)

On pong a Home ↔ Workspace swipe under Look Clear cost about 4 ms more RenderThread time a frame
than under Look Solid: every frame re-blurred both pages' whole content under each live surface.
While the wall moves (`LiveBackdrop.Wall`, `PaneWallLayout.isMoving`), a surface whose beneath
holds the wall draws two layers (`WallSwipeGlassPolicy`): the ground and the chrome stay in the
recording and are drawn live, so the parallax, the scene, the bars, the dock and the keyboard move
as they do; in the wall's place is a quarter-scale picture of the pages, rendered once offscreen
as the swipe starts (`PageLayerCapture`) and only moved by the pages' travel. What animates in
place on a page holds still under the glass for the swipe. When the pages stop moving together, or
the picture has not landed yet, the surface records as in §2. At rest under a live scene the page
layer is the pages themselves on a cached layer, so a scene frame no longer re-blurs them. The
page layer counts in the picture's own share of the budget (§4).

## 4. Live is opt-in and budgeted

One **Live blur** row in Battery & performance, off by default, covers the dock, floats and
widgets. `LiveBlurPolicy` also requires Android 13 or later, Glass refraction, an in-app wallpaper
no battery saver, no low-memory preset and not lazy; when any term fails the surface draws the stored frame at once.

The recorder holds at most 8 live surfaces, about 8.8 MiB, asserted by `LiveBackdropBudgetTest`.
When more want a slot the priority is dock, then floats, then widgets. Layers are released when a
surface leaves the screen, under memory pressure and when the switch goes off.

## 5. Android 8 to 11

A `RenderNode` cannot be blurred before Android 12. Panels there take a software Snapshot:
`LiveBackdrop.drawBelow` on a software canvas, blurred with RenderScript, about 2.1 MB while a
panel is open and released on close. A failure falls back to the frost or the tint. The bars use
the stored frame. `RealtimeBlurView` and its blur implementations are deleted.

## Rejected

- **Hide the glass while recording the whole wall.** Recording reuses each view's cached display
  list, so a hide flag never reaches it.
- **`PixelCopy` for panels.** It reads the composited screen, so it copies the panel itself unless
  it runs before the panel appears, and it cannot blur on the GPU.
- **A copy of the live path per dock style.** The styles already share one path.
- **Moving the dock onto the paint route.** Its blur and frost run over drawn pixels; a
  `BitmapShader` would change both for no gain, since Live needs the effect route anyway.

## Consequences

- Rule 3 of `performance.md` is narrowed: blur through `RenderEffect` on the RenderThread, and a
  re-record only when content beneath changes, are allowed; a software capture in a pre-draw
  listener is not.
- The default is unchanged. With Live on nothing draws at rest, but a frame where something
  beneath the glass changes (a slide, a printing terminal, the clock's seconds hand) re-blurs the
  surfaces above it. On pong a float over moving content cost about 3 to 5 ms at p50 and p90 on a
  swipe and 9 ms on each seconds-hand frame.
- Panels cost less than before: palette 10.3% to 5.9% janky, sheet 8.3% to 5.4%, drawer 12.4%
  to 6.4%.
- Built-in widgets take the live picture in wave 3b, landing alongside this record. Third-party
  AppWidgets wear no glass and stay out.
- The thick-glass shape (bevel, dispersion, Fresnel reflection, key light) is parked.
