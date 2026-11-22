# Vitro against our glass: what is worth taking (2026-10-25)

Question: does github.com/itshedimisawi/vitro, a Compose "Liquid Glass" library, hold anything we
should adopt in the launcher's glass? Read-only research. Nothing here is device-verified, nothing
was built or run, and no code was changed. Items marked (reasoned) come from reading code, not from
a measurement.

## Sources

- **Vitro@3384d04** = `github.com/itshedimisawi/vitro` at `3384d0432dc2a7fe69804873f607af941c2d1088`
  (2026-10-24). Paths are under `vitro/src/main/kotlin/io/github/itshedimisawi/vitro/` unless they
  start `README.md` or `ROADMAP.md`. Read from a shallow clone; never built.
- **License: Apache-2.0** (`LICENSE`; README "License" section). Adoptable with the usual notice and
  attribution if code is copied. Ideas and constants we re-derive need nothing. Haze and Kyant,
  which our earlier notes studied, are Apache-2.0 too.
- Android docs fetched 2026-10-25:
  - `developer.android.com/guide/topics/renderscript/migration-guide`: "RenderScript APIs are
    deprecated starting in Android 12 ... support is expected to be removed entirely in a future
    release"; recommends `RenderEffect` for views and a `HardwareRenderer` into a `HardwareBuffer`
    for blurring into a Bitmap.
  - `developer.android.com/develop/ui/views/graphics/agsl/using-agsl`: describes
    `RenderEffect.createRuntimeShaderEffect`; states no cost guidance and no API level (the page
    says nothing on either, so API gates below come from our own code and Vitro's, not from it).
- Ours: `GlassRefraction.java` (`GR`), `SharedFrameDrawable.java`, `WallpaperBlurRenderer.java`,
  `FancierGlassPolicy.java`, `GlassBackdropCache.java`, `GlassMotionLight.java`;
  `docs/adr/0004-every-glass-surface-samples-the-shared-frame.md`,
  `docs/adr/0006-the-shared-frame-goes-live-for-generated-backgrounds.md` (superseded),
  `project-docs/active/glass-plan-2026-10-16.md`,
  `project-docs/reference/launcher/glass-refraction-references-2026-10-15.md` and
  `glass-refraction-controls-2026-10-15.md`,
  `glass-controls-unification-2026-10-16.md`, `docs/agents/performance.md`.

## Summary

Vitro is a smaller shader than ours. Its rim (refraction, dispersion, light) is the same family as
what we already took from Haze and Kyant, and ours goes further (tilt, lag, beads, clarity, glint).
Its genuinely different ideas are (1) live backdrop sampling, which our rules forbid, (2) a
Gaussian blur written as an AGSL tap list, (3) smooth-union merging of nearby shapes, (4) a
three-tier fallback down to API 23, (5) an analytic shadow, and (6) a luminance model that applies the
glass's own colour grading before reading brightness. Only (2), (4) and small parts of (5) and (6)
are worth anything to us, and none of them is urgent. **There is no quick visual win here.** The
honest top item is a hedge against RenderScript going away.

Our hard constraints, from `AGENTS.md` and `docs/agents/performance.md`: at rest nothing draws
(rule 1); pixels sit behind a budgeted store (rule 2); glass samples the shared frame blurred once
per radius (rule 3); a milky body was tried and dropped (`glass-plan-2026-10-16.md:610-617`); live
wallpapers were removed (ADR 0006 header note). AGSL needs API 33: `FancierGlassPolicy.MIN_SDK`
(`FancierGlassPolicy.java:17`), minSdk is 26 (`gradle.properties:29`).

## How Vitro is built, in one paragraph

Each glass piece is a Compose node. The backdrop content is recorded into a `GraphicsLayer`
(`internal/node/BackdropNode.kt:53-55`); each piece draws that layer through a `RuntimeShader`
render effect, clipped to its own view rectangle (`internal/render/ShaderRenderer.kt:176-186`).
The renderer is chosen by API: AGSL shader on 33+, platform `BlurRenderer` on 31-32, CPU
`SnapshotRenderer` below (`internal/render/GlassRenderer.kt:26-28`; README "Android versions").
Up to eight shapes share one outline pass (`internal/render/GlassShaders.kt:25`).

## Worth adopting

### 1. A non-RenderScript pre-blur for the shared frame. Priority: medium (hedge, no visible change)

- **Vitro.** The Gaussian is two AGSL passes (`GlassShaders.kt:49-62` across, `:166-173` down), fed
  by tap pairs where each tap reads between two pixels so bilinear filtering supplies both weights
  (`internal/render/BlurTaps.kt:25-45`; 16 taps per side, sigma up to 10.67,
  `GlassShaders.kt:31-32`). Stronger blurs fall back to `RenderEffect.createBlurEffect`
  (`ShaderRenderer.kt:180-183`). For readback it uses a long-lived `HardwareRenderer` +
  `ImageReader`, drawn without stalling and read on a background thread
  (`internal/tone/ToneCapture.kt:165-242`, `@RequiresApi(29)`).
- **Ours.** The frame is blurred once per radius with `AndroidStockBlurImpl`, a RenderScript
  `ScriptIntrinsicBlur` wrapper (`com/github/mmin18/widget/AndroidStockBlurImpl.java:15-31`), called
  from `WallpaperBlurRenderer.java:41-75`. RenderScript caps the radius at 25 px, so
  `doPreBlur` downsamples by `ceil(radius / 25)` (`WallpaperBlurRenderer.java:50-62`).
- **Gap.** Every glass surface depends on a deprecated API Google says it expects to remove
  (migration guide above). If it goes, there is no blurred frame and ADR 0004's whole design stops.
  The 25 px cap also ties blur radius to stored resolution.
- **Change.** On API 29+ draw the source bitmap through a `HardwareRenderer` with a blur
  `RenderEffect` (the migration guide's own recipe), or Vitro's tap list as AGSL on 33+, into an
  `ImageReader`/`HardwareBuffer`, and copy to the same-size Bitmap the cache already stores. Keep
  RenderScript below 29 and as the fallback if the renderer returns null (Vitro's pattern:
  `ToneCapture.read` returns null on `SYNC_FRAME_DROPPED`, `ToneCapture.kt:197-200`).
- **Cost.** Work stays once per radius, off the main thread (rule 4). GPU time is a one-off, not
  per frame. Memory: same stored frame (`WallpaperBlurCache` budget unchanged); a transient
  hardware buffer the size of one frame while blurring (reasoned). API 29+.
- **Risk.** Hits every surface at once, so output must match today's within a unit or two or the
  look shifts everywhere (hit-every-surface rule). `HardwareRenderer` blur edge handling differs
  (tile mode) from the script's; compare crops against the current frame before shipping.
  `RenderEffect.createBlurEffect` has its own radius semantics (Vitro encodes them in
  `internal/material/Optics.kt:285-288`: sigma = radius x 0.57735 + 0.5). The parallax frame is
  1.5x wide, so size matters. Needs a pong (or other phone) check; the emulator is banned.
- **Why not more urgent.** RenderScript works on every current build; Google's wording is "expected".

## Maybe

### 2. A lens approximation for API 26-32. Priority: low

- **Vitro.** Below API 33 it does not drop the lens, it approximates it: `FallbackPainter` redraws
  the backdrop in five clipped, progressively stretched rings that step the same cubic curve
  (`internal/render/FallbackPainter.kt:50-58`, `Rings = 5` at `:175`), with a path-drawn rim and
  shadow (`FallbackPainter.kt:139`).
- **Ours.** Under API 33 Fancier Glass is hidden, not disabled (`FancierGlassPolicy.java:21-23`);
  those phones get the plain frame plus rim drawables.
- **Gap.** Phones on 12 and below see no bend at all.
- **Change.** `SharedFrameDrawable` already draws a still bitmap through a shader; five extra
  clipped draws of that bitmap scaled outward from the centre would stack rings.
- **Cost.** Five clipped bitmap draws per redraw of a surface, no offscreen layer, nothing at rest
  (reasoned). API 26+.
- **Risk.** A scale from the centre is not a push along the rim normal, so corners and non-square
  panes read wrong; the developer reads "janky" as "looks bad", and this is crude. We have no phone
  on API 32 or lower to judge it on. Do not build without one.

### 3. Shadow falloff and dark-scheme reduction, if C-11 is ever built. Priority: low

- **Vitro.** The shadow is analytic: `1 - smoothstep(-spread, spread, signedDistance(coord - offset))`
  (`GlassShaders.kt:120-149`, falloff at `:144`), dropped to 15% on dark glass (`DarkShadow = 0.15f`,
  `Optics.kt:272`; applied in `internal/render/GlassScene.kt:480`).
- **Ours.** No shadow yet. C-11 is gated on a measurement after Round 1 and, if built, is a static
  cached ring bitmap (`glass-plan-2026-10-16.md:580-608`).
- **Change.** If C-11 happens, generate the ring bitmap with this falloff instead of a box blur, and
  scale its alpha down on dark ground so it does not read as a dirty halo. The per-frame shader
  itself is not for us (item "Not for us" 3).
- **Cost.** Nothing beyond C-11's own cached bitmap. **Risk.** None new; it stays gated.

### 4. Read ink brightness through the glass's own colour grading. Priority: low (reasoned)

- **Vitro.** `GlassShade.through` applies saturation, veil, dark curve, tint and dimming to a
  sampled colour (`internal/material/GlassShade.kt:19-38`), and `BackdropTone.luminance(area,
  beneath)` uses it so legibility is judged on what the viewer sees
  (`internal/tone/BackdropTone.kt:38-66`). It also works in linear light (`pow(v, 2.2)`,
  `BackdropTone.kt:129-134`).
- **Ours.** The ink choice judges the "flat glass" (wallpaper, dim, base) for small pieces
  (`glass-plan-2026-10-16.md:419-421`), and averages in plain sRGB on purpose
  (`GlassBackdropCache.java:413-417`, a blurred wash, within about a unit of linear).
- **Gap.** If the grading changes brightness materially (Clear's K-2 darkening behind bright
  spots, a strong tint), the ink decision may be taken on a colour the user never sees. I did not
  trace whether `ChromeInk` already folds those in; it may.
- **Change.** Check first. If it does not, apply the surface's tint and dim to the sampled colour
  before the polarity vote. Leave sRGB averaging alone: our samples are of the blurred frame, and
  the choice was documented.
- **Cost.** A few multiplies per sampled rect, no new reads. **Risk.** Flips ink on existing
  surfaces, so it needs the `ChromeInkTest` polarity tests and a pong look.

### 5. Smooth-union of neighbouring shapes. Priority: low

- **Vitro.** `share`/`join` blend signed distances so shapes closer than `blend` flow into one
  outline, normals, tint and shadow included (`GlassShaders.kt:111-118`, used at `:131-134` and
  `:194-205`), with a bounding-box early-out per shape (`isAway`, `:76-83`).
- **Ours.** Surfaces are separate large panels; we have beads and a "joined frame" for the dock's
  touch light (`glass-plan-2026-10-16.md` C-09), but no metaball merge.
- **Where it could show.** Window chips, Peek dock cards, extra-keys groups.
- **Cost.** Up to eight shape loops per pixel in Vitro; ours would add a loop to a program whose
  sample count is pinned by `GlassRefractionTest.noPixelReadsMoreThanSixSamples`
  (`GlassRefractionTest.java:243`). **Risk.** High complexity for a look nobody asked for; it would
  also need our corner-radius rules (Docked square, Floating 26 dp) to survive the blend. Park it.

## Not for us, with reasons

1. **Live backdrop sampling.** Vitro records the content behind the glass and re-reads its tone
   about every 120 ms (`BackdropNode.kt:53-55`, `internal/tone/ToneReader.kt:111`, 6 dp cells at
   `:110`). Our glass samples a still frame (ADR 0004); the live variant was ADR 0006 and was removed
   with live wallpapers on 2026-10-06. It also breaks "at rest, nothing draws" and adds a readback
   buffer per backdrop. Do not reintroduce.
2. **Velocity stretch.** `MotionStretch` squashes moving glass up to 14% on a spring
   (`internal/node/MotionStretch.kt:67-70`). We deliberately settled on a rigid slab: no dent, only
   a lag in the bend (`GlassMotionLight.java` class doc, the user, 2026-10-20). It also moves the
   outline, which our View surfaces do not do.
3. **The per-frame shadow shader.** It paints a rect larger than the glass every frame
   (`ShaderRenderer.kt:58-71`). A static ring (C-11) costs nothing at rest.
4. **Vitro's light and dark materials.** `LightVeil 1.32`, `LightFade 1.34`, `LightWash 0.58` and the
   dark lift (`Optics.kt:264-280`; shader `GlassShaders.kt:253-261`) brighten the face toward
   white. That is the milky body the developer dropped (`glass-plan-2026-10-16.md:610-617`;
   `glass-controls-unification-2026-10-16.md:263`).
5. **Its rim, bevel and dispersion.** Cubic bevel `slope^3` (`GlassShaders.kt:212-213`), 3-tap
   fringe skipped when flat (`:217-219`) and a rim brightened in proportion to headroom
   (`:262-271`) are simpler versions of what we run from Haze and Kyant. Ours has a circle profile,
   soft clamp, tilt, lag, glint and clarity
   (`glass-refraction-controls-2026-10-15.md` section 5, table). Nothing to learn that the earlier
   notes did not already record.
6. **Per-surface layers and their growth room.** Vitro grows each glass layer in 16 dp steps to
   avoid buffer reallocation (`ShaderRenderer.kt:98-106`, `:238`) and clips the shader to the view
   (`:185-186`). ADR 0004 already removed per-surface bitmaps: our surfaces draw the shared frame
   through a shader in the same pass, so there is no layer to resize.
7. **Scroll edge effect, `glassEffectContainer` API, interactive press glow.** Compose-specific or
   already covered by `GlassTouchLight` and C-09. Vitro's own roadmap lists progressive blur and
   reduced-transparency support as unfinished (`ROADMAP.md`, "Legibility" and "Further ideas"), so
   there is nothing to borrow there.

## What I did not check

- Whether our AGSL returns before any sample for interior pixels beyond the beads case
  (`GlassRefraction.java:911-912`); I did not read the whole body.
- Whether `ChromeInk` already folds Clear's darkening and tint into the polarity vote (item 4).
- Real cost of anything above on pong. No numbers were measured; `docs/agents/performance.md` says
  what a report must show if one of these is built (before and after on the same build type, and
  `scripts/perf-baseline.sh`).
- Vitro's output against an iPhone; the README's calibration claim is the author's.
