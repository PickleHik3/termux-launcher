# Glass as a physical material: spec

Status: agreed with the developer 2026-11-22 (grilling session). Decision record:
[ADR 0033](../../docs/adr/0033-glass-is-a-physical-material.md). Research and code citations:
[`docs/research/glass-realism-2026-11-22.md`](../../docs/research/glass-realism-2026-11-22.md)
(§2 today's glass, §4 proposals, §5 what to remove, §6 target architecture). Branch work
starts from `v2-redesign` (a change in behaviour; reaches users through a pre-release drop).

## Goal

Every glass surface in the app looks like physically realistic glass by default, and a few
simple controls let a user push it toward dramatic. On pong the developer judged today's glass
"a graphics effect"; the only change they could see clearly in the 2026-11-22 experiments was a
thinner, sharper edge. The visible gain therefore comes from the edge and the lens zone.

## Scope

All glass: terminal panes (`PaneGlassBackdropView`), built-in and third-party widget frames,
dock and capsule (`SharedFrameDrawable`), keyboard, sheets, the page bar, chips and pills.
Live blur and the stored still-frame routes, and glass over shader (scene) wallpapers.

Not in scope: the capture pipeline's structure (quarter-scale layers, page captures, pool,
GPU-resident frames), the software-canvas and Android-version guards, the corner mask, the X11
keyboard frost and the SurfaceView recording rule (research §5.3). They stay.

## The material

- **Outline:** one rounded-box signed distance per surface, with the same radius as the view's
  clip outline (`PaneShape.radiusForBounds` and the corner-radius rules). Everything below
  derives from it; no per-side or per-corner branches, and no light normal on a wider arc than
  the drawn corner.
- **Rim:** drawn once, by the shader. No rim drawable or stroke over a shader rim (built-in
  widgets restore one today: `BuiltinWidgetView.java:850`). A thin crisp line: a light line
  facing the room light, a dark line, and a baseline of equal strength all round, so no side
  vanishes in a horizontal motion. White light, not a multiple of the backdrop colour; in linear
  light (`toLinearSrgb`/`fromLinearSrgb`); premultiplied output. Precision: `float` for
  coordinates, distances and normals.
- **Lens zone:** the slab curves down to the edge over a lens zone (about 16 dp on large
  surfaces; on small pieces a share of the short side, capped at the Depth value). A smooth
  convex profile, C1 where it meets the flat face; its normal is the SDF gradient times the
  profile's slope. Refraction: Snell's law through the slab's thickness (`refract`, an index
  of refraction around 1.5 at the defaults), sampling the **sharp** frame, compressing and
  bending the background strongly toward the edge, with no colour gain. Fresnel (Schlick) from
  the slab normal and the view. Dispersion only at high Shine, following the bend, never a
  corner-only split.
- **Face:** the flat middle carries frost (roughness), opacity, tint (absorption) and the veil,
  looking as today unless the controls change.
- **Light:** one fixed room light, upper left, the same for every surface. No gyroscope.
- **Motion:** from the page's pose (translation, rotation, scale) that each draw already has.
  Slide: optically still (only the background drifts under the glass). Turns (Carousel, Stack's
  turn, the plank's tip): the image shifts through the slab with the angle, the far bevel takes
  Fresnel light, a soft glint crosses the face. Depth and Stack's scale: a slight magnification
  change. The speed spring (`GlassMotionLight` lag) goes. Reduced motion and Animation speed
  Off keep the glass flat. No frames requested at rest; no per-frame allocation.
- **Gap between windows:** during a turning space-bar switch the gap between the departure card
  and the arriving window shows raw wallpaper today (it predates 2026-11-22; with glass on the
  Terminal's ground is only its wallpaper dim, `TerminalSurfacePolicy.pageGround`). It gets the
  wall's shade, as at rest.

## Controls

- **Glass sliders: Frost, Depth, Shine.** Frost = roughness of the face (today's Blur).
  Depth = thickness and lens zone (today's Depth). Shine = light intensity on the rim and lens,
  adding a little dispersion at the high end (folds today's Rim and Edge). Realistic values sit
  at the defaults; the ends go past realism toward a dramatic look.
- **Per surface:** Opacity and Frost keep their per-surface rows (Dock, Status, Keyboard,
  Terminal). Depth and Shine are app-wide.
- **Unchanged:** Opacity, Tint, Corner radius, Accent.
- **Removed from the editor:** Grain, Rim strength, Edge, Vibrancy; set by the presets.
  The seven hidden keys (`fancier_glass_edge_light`, `_specular`, `_clarity`, `_face_clarity`,
  `_dim`, `_size_lens`, and the edge width) become derived or go (research §4(f)).
- **Presets:** Clear (clear glass, visible lens edge), Frosted (blurred face, same edge), Tinted
  (coloured glass), Solid (near-opaque, for readability), Liquid (dramatic: thick lens, strong
  shine, a touch of dispersion), and Custom. New installs start on **Frosted**.
- **Settings:** Glass refraction stays as the off switch (flat frosted glass, no lens or light,
  for weak phones). Living glass is removed (motion follows Animation speed and reduced motion).
  Live blur stays.

## Migration

No reset. Each stored value maps to the nearest value in the new model: today's Blur → Frost,
Depth → Depth, Rim/Edge → Shine; Grain and Vibrancy fold into the preset the user's look is
closest to (or stay as Custom's values). Old presets map Clear → Clear, Mist → Frosted,
Tint → Tinted, Solid → Solid; a Custom look stays Custom with mapped values. The mapping is a
pure, tested function, run once on upgrade. `AppearanceReset.NUMBER` is not raised.

## Delivery

Two halves, each shippable in a pre-release drop on its own.

**First half: the visible fixes, no settings change.**
1. Remove the widgets' second rim; one rim per surface (research §6 step 1).
2. Light normal on the drawn radius; dispersion no longer corner-only (step 2).
3. The thin crisp rim in linear light, removing the gain-based edge code with it (step 3).
4. The rim samples the sharp frame (step 4); measure layers and memory on the live route
   (research §7 q7).
5. The gap between windows takes the wall's shade.

**Second half: the material and the controls.**
6. The physical slab with the lens zone (step 5).
7. Pose-driven motion; remove the speed spring and Living glass (step 6).
8. Frost / Depth / Shine, the new presets, the migration, the Settings changes.
9. Colour bakes into the shader; shrink `LiveStoredParity` (step 7).
10. One source interface for stored, live and scene frames (step 8); remove the remaining
    compensations listed in research §5.

**In parallel:** bundle three Mirage shaders (Apache-2.0, GLSL ES 3.00) as shader wallpapers in
the scene system: mesh gradient, water, fluted glass, with their licence notice. Not Mirage
itself (it redraws inside the window every frame).

## Tuning

Default numbers (lens width, thickness, index, rim line strengths, baseline, Shine curve, turn
response, Frost range) live in one pure parameter class with unit tests. Each half exposes them
on a debug property; they are tuned by eye on pong with the developer, then frozen in code.
The experiments on `exp/glass-motion` are dropped from pong's build once the first half lands;
their tested maths (`GlassRealOptics`, `GlassEdgeModel`) is reused where it fits.

## Ship bar (each half)

- A perfetto trace round on pong against the 2026-11-22 sweep (`scratchpad perf/report.md`
  numbers): no glass frame over 11 ms (90 Hz) at rest or in swipes; drawer, palette, keyboard,
  page swipe, Home ⇄ Terminal and space-bar switch scenarios.
- The full unit suite green (`scripts/full-suite.sh`), including pinned look tests updated
  deliberately for the new look.
- Pong checks on every glass surface in light, dark, black and Material You, Docked and
  Floating, keyboard up and down; the emulator sweeps the other layouts.
- Owed checks recorded in `project-docs/device-checks.md` in the same commits.

## Docs

This spec moves to `project-docs/reference/` when the second half ships. The user guide in
`docs/en/` (Appearance and glass) is updated with the second half: the three sliders, the
presets, what Glass refraction off does.

## Open questions for the tuning sessions (research §7)

Whether widget corners still read as stitched after steps 1-2 alone; the rim's width in pixels
before and after; whether Slide reads as glass with the lag off; whether the turn glint and far
Fresnel read as glass at 60°; the cost of linear light on the drawer and palette scenarios;
whether the window is ever in a wide-gamut mode; whether the live route can bind the sharp frame
with no extra layer; whether third-party widget frames restore a rim too.
