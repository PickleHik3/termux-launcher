# Glass plan: the next round on `pre` (2026-10-16)

Status: spec. Rounds 0 and 1 are built on `pre` (e928252ab), and Round 1 is now always on (phase
A of the glass controls work, 2026-10-16): the Adaptive glass switch is gone and the gathered
light is the only edge the program draws on Android 13 and up. Rounds 2 to 5 are not built. It is
for the developer, who decides, and for the agent who builds it.

**Superseded by phase A** (read the sections below with this in mind):

- "One switch: Adaptive glass" no longer holds. The key `fancier_glass_adaptive`, its accessors,
  the settings row and its two strings are deleted; a stored value is ignored, with no migration.
  Every surface that runs the program wears the gathered light, the dock and the under-pill strip
  with no Look (`Look.DEFAULT`) among them. Later rounds that were to sit behind the switch need a
  new decision.
- Round 1's hairline branch, `uGathered`, `kRim`, `kShade`, `kLight`, `kLobeK`, `lobe()`,
  `edgeLit`, `Program.setGathered` and the flag on `setRefraction`, `rimReachPx` and `seamReachPx`
  are deleted. `kRimDp`/`kMinRimPx` stay for the pull's ease. Seams are always pushed past the
  glow's 10 dp reach.
- New in phase A: the light's strength is held under the headroom the sample's brightest channel
  has left (`heldK`: exact below half of it, then `1 - 1/(4r)`), so a pale backdrop's outline
  never clips and keeps its hue. A 0.5 grey now peaks near 0.87 at the brightest corner, not 0.98.
- Rims: decided per surface from what is on screen, never from the look. `GlassStack` drops its
  rim only when its own backdrop refracts (`SharedFrameDrawable.refracts`). The window bar's stroke
  follows its frost as each top-pane frost pass leaves it (`Surfaces.onWindowBarFrostEdge`, which
  re-dresses the bar when the answer moves). The dock drops `rimUnderGlass` while a refraction
  effect is installed on its visible backdrop (`setDockProgramEdge`, which re-cuts the host's
  shape; the capsule keeps an outline-only background to clip to). A glass pane's resting border
  asks its own slab on every draw (`PaneRim.SlabEdge`, answered by `PaneGlassBackdropView`) and is
  redrawn when the slab's answer moves. Focus and attention borders stay. The corner tab gets the
  gathered edge with everything else.
- Not done, by the developer's decision: no top layer. The light stays under tint, veil and grain,
  so opaque surfaces (Mist, Solid, the sheet and chip floors) show it only through their tint;
  phase B scales its strength by opacity.

**Superseded by phase B** (the glass controls, 2026-10-17, branch `feat/glass-controls-unify`):

- Round 2's K-2 film is superseded by **Shade**, a flat layer under the tint (black to 40% on dark
  glass, white to 25% on light glass), a Look › Custom slider at 0 in every Look and measured by
  `ChromeInk` like the tint. No `uFilm`, no per-pixel ink limit: if a per-pixel film is still
  wanted after pong, it is a new decision.
- Dispersion is now the user's: Look › Custom's Edge pill writes Off 0, Glint 40 or Prism
  80 (provisional, under the 0.05 share cap). Round 4's `uForm` would scale it with the pull.
- The gathered light is raised by the cover over it (`uEdgeGain`, `Look.withCover`): 1 up to
  Clear's 10%, `0.9 / (1 - cover)` above, at most 2 (`Look.MAX_EDGE_GAIN`, a pass-2 tuning
  knob: past it a mid backdrop's ring runs past `heldK`'s knee and flattens), still held under the
  headroom. The dock and the under-pill strip follow the same rule with or without a Look. That is the
  "scales its strength by opacity" phase A left open.
- Vibrancy (0.7x to 1.5x) is baked into the shared frame when it is blurred; the frost the program
  samples is brighter or duller with it, and `heldK` still holds the light under its headroom.

It covers the ten items the developer picked: C-01, K-2, C-02, E-1, C-03, C-05, C-07, C-09,
C-10, C-11. The developer ruled these out: accessibility settings (C-06), the scroll-edge fade
(C-08), removing the Docked grey frame (C-04), a tilt-driven light, merging droplets, and removing
sliders.

Read first: ADR 0004 and 0005, [`fancier-glass/SPEC.md`](fancier-glass/SPEC.md), and the two
notes `reference/launcher/glass-refraction-references-2026-10-15.md` and
`glass-refraction-controls-2026-10-15.md`. Apple's side is in `docs/research/liquid-glass-design.md`
(untracked in the main checkout). The mockups are in `.lavish/glass-plan/img/` in the main
checkout (gitignored):

- `*-contrast-now / -one / -local.png`: today, one darkening per surface, and darkening per spot (K-2).
- `*-edge-now / -none / -tinted / -two.png`: today's hairline, no edge, gathered light (E-1), two lights.

## Words used here

| Word | Meaning |
|---|---|
| Program | The one AGSL shader every refracting glass surface draws through, `chrome/GlassRefraction.AGSL`. It runs on Android 13 and up, with a wallpaper set from inside the launcher (the Fancier Glass path). |
| Uniform | A number the app hands the program. The app writes it only when it changes. |
| Frame | The pre-blurred still of the wallpaper that every glass surface samples (ADR 0004). It is never live. |
| Sample | The colour the program reads from the frame at a pixel, after the bend. |
| Edge band | The strip along a surface's outline where the program bends the picture. Its width is `uBand`, the Look's edge width in dp. |
| Chrome band | A different thing, from `ChromeInk`: a piece of chrome whose ink is decided together (status bar, window bar, A–Z strip, dock, session chip, terminal pane, keyboard). `GlassBackdropCache.Band`. |
| Ink | The colour of text and glyphs on glass: a pale ink (light text) or a dark ink. |
| Polarity | Which of the two inks the chrome uses. Today all chrome shares one (ChromeInk's coherence rule). |
| Veil | OnGlass's extra layer of the surface's own base colour, at the smallest alpha that makes the ink readable. One alpha per chrome band, drawn over the glass. |
| Film | New here (K-2): a darkening of the frame, inside the program, decided pixel by pixel. |
| Lobe | How the edge light's strength changes round the outline: brightest where the outline faces the light, weaker away from it. |
| Short side | The smaller of a surface's width and height, in dp. |
| Joined frame | Docked style, where the bars join into one sheet of glass behind the whole screen (`FrameGlassPolicy.sheetsStandDown`). |
| Seam | An edge two surfaces share as one sheet. It takes no edge light and no bend (`GlassRefraction.rimRect`). |

## The rounds

| Round | Items | What you will see | Where the work is |
|---|---|---|---|
| 0 | C-05 | Nothing. A check. | No code. |
| 1 | the switch, C-02 with E-1 | The edge reads as light, not a drawn line. | The program, three rim call sites, one settings row. |
| 2 | C-01 with K-2 | Clear glass darkens behind text over bright spots, and nowhere else. | The program, `ChromeInk`, the same routes to every surface. |
| 3 | C-07, C-03 | Small pieces pick their own light or dark text. Small glass is clearer. | `ChromeInk`, `GlassSurfaceFactory`. |
| 4 | C-10, C-09 | Sheet cards grow their edge as they fade in. A dock press glows a little into the next bar. | The program, `GlassMotionPlayer`, the dock's touch light. |
| 5 | C-11, only if Round 1 fails a measurement | A soft shadow under floating panels. | A static drawable. |

Rounds 1 and 2 are the smallest visible wins, so they come first. Each round is one or two
commits, compiles on its own and adds its line to `project-docs/prerelease-notes.md` when it lands
on `pre`.

## One switch: Adaptive glass

*Superseded (phase A, 2026-10-16): the switch is deleted and Round 1 is always on. Kept for the
record.*

Every item here changes a look someone already has. The developer's stance is that new glass terms
arrive as opt-in knobs. So all of them sit behind one switch. One row, not nine, keeps the settings
change small.

- Key `fancier_glass_adaptive`, a boolean, default **off**. Accessors on
  `TermuxAppSharedPreferences` beside `isFancierGlassEnabled`.
- The row: Settings › Style › Theme, right under Wallpaper parallax, in
  `res/xml/termux_style_preferences.xml`. A plain `SwitchPreferenceCompat`, handled in
  `TermuxStylePreferencesFragment.putBoolean` / `getBoolean` the way `wallpaper_parallax` is: the
  setter, then `scheduleTermuxActivityStylingSync(false)`.
- Hidden below Android 13 (`GlassRefraction.available()`). Shown disabled, with the existing
  `settings_fancier_glass_hint`, when the wallpaper was not set inside the launcher.
- Title "Adaptive glass". Summary: "Edges catch the wallpaper's light. Clear glass darkens behind
  text over bright spots. Small pieces pick light or dark text."
- **The Appearance editor is not touched.** No slider, no Look key, no preset format change. The
  shipped Looks stay exactly as they are. Settings pages break easily; this is the smallest change
  that gives the developer a knob.
- `TermuxActivity.syncFancierGlassLook` reads it beside the Fancier Glass rule. A moved answer marks
  every backdrop dirty and drops the pane style key, the path a Look change already takes.
  `PaneSurfaceStyle` and `ChromeRenderer`'s surfaces interface carry it as `adaptiveGlass()`.
- Off means every surface draws exactly as today. Every new uniform's default is the old look, and
  a test pins that.
- Later, if the developer likes it on pong: turn it on for fresh installs in a separate change.

## Round 0 · C-05: is the default edge off Depth's curve?

Verify first. No constants change.

**The arithmetic, from the code.**

- The stored defaults are bend 9 dp and edge width 20 dp (`TermuxPreferenceConstants.java:1270`,
  `:1276`). The same pair is `Look.DEFAULT`, the dock's refraction when Fancier Glass is off (ADR 0005).
- Depth (`surfaces/GlassEdgeControls`) writes bend `floor(0.3 t)` and width `round(2 bend / 3)`.
  Bend 9 comes from positions 30 to 33, and they all write width **6**, not 20.
- Depth reads a stored pair back as the nearest point on its curve. `depthFor(9, 20)` is **34**,
  which writes bend 10, width 7.

**What a fresh install shows** (Android 13 and up, wallpaper set in the launcher):

- No Look is applied on first run. Nothing calls `SurfacePresets.apply` except the editor and the
  low-memory preset. Base is blur 8, opacity 34, grain 18, so the editor's Look page shows Custom.
- The program gets an edge band of 20 dp and a pull of 9 dp (`min(9, 1.5 × 20)`).
- Depth's thumb sits at 34. One step to 35 writes 10/7, and the edge band drops from 20 dp to 7.
- What the eye sees is the bend ring. On Haze's profile the pull 4 dp in from the outline is 3.4 dp
  at 9/20 and 0.4 dp at 10/7. The ring where the pull is over half a dp is about 10 dp wide at
  9/20 and about 3.7 dp at 10/7. The first nudge shrinks the ring to about a third.
- No slider position gives 9/20 back.

**Verdict: a real mismatch, but cosmetic.** Nothing breaks and legibility is not touched. The
default is a point the slider cannot reach, and the first touch makes a jump. An install that ever
picked a Look is not affected: every shipped Look sits on the curve
(`GlassEdgeControlsTest.everyGlassLookSitsOnTheCurveAndRoundTrips`).

Options for later, for the developer to choose: (a) apply a shipped Look on first run; (b) move the
stored default to a pair on the curve, and give `Look.DEFAULT` its own constants so the default-mode
dock keeps 9/20; (c) leave it.

Device check owed:

- (on merge) · Round 0, no commit · Fresh install, wallpaper set in the launcher: the Look page says
  Custom and Depth reads 34; screenshot the dock's top edge, move Depth one step, screenshot again.
  Passes if the second shot shows a narrower bend ring, confirming the jump (a confirmation, not a
  fix) · emulator, `ad clear` then set a wallpaper through the picker.

## Round 1 · C-02 with E-1: the edge reads as light

**What you will see.** The rim stops looking like a white line drawn on top. It looks like the
wallpaper's own colour, brighter, caught at the outline. It is brightest at the top-left, with a faint
share all the way round, and it fades softly inward over about a dp and a half. Over a pale wallpaper
the glint gets smaller instead of turning white. Mockups `dark-edge-tinted.png`, `light-edge-tinted.png`.

**Today.** Two white lines:

1. The program's hairline: 1 dp wide (`kRimDp`, never under 2 px, `kMinRimPx`), 12% darker all
   round (`kShade`), and mixed toward white by up to 45% (`kRim`). That happens where it faces a
   light straight above (`kLight = (0, -1)`), on a lobe with exponent 8 (`kLobeK`).
2. On the surface's `LayerDrawable`, a white gradient stroke: `GradientRimDrawable` through
   `GlassSurfaceFactory.rimDrawable`, white 10% at the top-left down to 1%.

Two white lines on one edge is the "tacked on" look.

**New: gathered light.** This is the mockup's formula in the program's terms. `inside` is px in from
the outline, `n` the outward normal (`rectNormal`), `s` the sample.

```
glow = exp(-inside / (kGlowDp * uDensity))          // 1 at the outline, 1/e at 1.4 dp
key  = pow(max(dot(n, kKeyLight), 0.0), 2.0)        // the lobe: one light, top-left
k    = glow * (kAmbient + (1 - kAmbient) * key + 1.5 * touchG) * (1 - kPaleCut * luma(s))
col *= 1 - glow * kEdgeShade                        // a faint dark under the light
col  = min(col.a, col * (1 + kGain * k) + kLift * k)
```

| Constant | Value | Note |
|---|---|---|
| `kKeyLight` | (-0.552, -0.834) | The mockup's (-0.55, -0.83), normalised. Up and to the left. |
| `kGlowDp` | 1.4 | How fast the light fades inward. |
| `kAmbient` | 0.25 | The share every side gets, so the light follows the whole shape. |
| key exponent | 2 | Today's lobe is 8. This one is much wider. |
| `kPaleCut` | 0.75 | Over a white sample the glint keeps a quarter of its strength. |
| `kGain` | 1.6 | Brightens the sample's own colour. |
| `kLift` | 0.07 | A little grey light, so a black wallpaper still shows an edge. |
| `kEdgeShade` | 0.05 | Replaces today's 12% shade. |
| `kGlowReachDp` | 10 | `exp(-10 / 1.4)` is 0.0008, under one 8-bit step even at full gain. Pixels further in skip the term. |

The lobe's share round a surface: top-left corner 0.97, top edge 0.77, left edge 0.48, right and
bottom edges 0.25 (the ambient share alone).

Why it is not white: the light multiplies the sample's own colour, so a blue wallpaper gives a
brighter blue edge. Only `kLift`, 7% of `k`, is grey. `luma(s)` uses Rec. 709 weights on the sample
before any film (Round 2), so it measures how pale the wallpaper really is.

**What happens to today's pieces when the switch is on:**

| Piece | With Adaptive glass on |
|---|---|
| `kRim` 0.45, `kLight` (0, -1), `kLobeK` 8 | Not used. Replaced by the terms above. |
| `kShade` 0.12 | Replaced by `kEdgeShade` 0.05, on the same glow. |
| `kRimDp` 1 and `kMinRimPx` 2, the `edge` smoothstep | Kept. They still ease the pull to nothing across the outermost dp (Haze's edge softness). Only the light leaves them. |
| Touch light on the rim (`1.5 × touchG`) | Kept, moved inside `k`. |
| Key glow and touch scatter (small additive light) | Unchanged. |
| `GradientRimDrawable` over the glass (white 10% to 1%) | Dropped where the program draws the surface: `GlassStack.Spec.rim` false, `paneGlassRimWanted()` false, and `GlassSurfaceFactory.surface` skips `rimDrawable`. Kept where the frame draws plain (below Android 13, Fancier Glass rule off). |
| `rimUnderGlass` (the dock capsule's gradient, under its backdrop, also its outline) | Kept. A device check looks for a double edge on the dock. |
| `GlassSurfaceFactory.rim()`, the grey stroke at 18/255 | No caller in main code on `pre`, only the method itself. Nothing to do here; deleting it is a separate chore. The grey line the developer sees in Docked is the divider (`PaneDividerEdges`, `PaneRim`), which is C-04 and out of scope. |

**Code.**

- AGSL: `uniform float uGathered;`. 0 draws today's hairline, 1 draws the gathered light. It is a
  uniform branch, so there is one program and no second compile. The constants above go in as
  `const`, each with a Java twin on `Look` (`GLOW_DP`, `AMBIENT`, `KEY_EXPONENT`, `PALE_CUT`, `GAIN`,
  `LIFT`, `EDGE_SHADE`, `GLOW_REACH_DP`) and `KEY_LIGHT_X` / `KEY_LIGHT_Y` beside `LIGHT_X` / `LIGHT_Y`.
- `Program.setGathered(boolean)`, compared before it is written. The constructor writes 0.
- A Java twin of the light, `GlassRefraction.gatheredLit(...)`, beside `edgeLit`, for the tests.
- **Seams.** A seam is pushed past the edge light so no line shows through one sheet. Today it is
  pushed past 2 px of hairline. The glow reaches 10 dp, so `Look.rimReachPx` must return
  `max(rimWidthPx, GLOW_REACH_DP × density)` when gathered, and `seamReachPx` must take the flag.
  Without this, the keyboard over the under-pill strip and the status bar over the window bar show a
  soft bright line.
- Callers set the flag where they set the Look: `SharedFrameDrawable.setRefraction`,
  `PaneGlassBackdropView.setRefraction`, and the dock and strip `RenderEffect` programs in
  `TermuxActivity` (where `program.setLook(GlassStack.lookFor(...))` runs).
- The Look stays what is stored. The flag is passed beside it, not added to it, so `Look.equals` and
  the stored keys do not move.

**Tests** (JVM, in `GlassRefractionTest`'s style):

- `theGatheredEdgeIsOffByDefault`: the AGSL declares `uGathered`; the program writes 0 first; the
  existing hairline tests stay green.
- `theGatherBrightensTheBackdropsOwnColour`: for a sample (0.2, 0.3, 0.8) at the outline facing the
  light, each channel rises in proportion to itself, plus at most `kLift × k`. Blue over red stays
  within 10% of what it was. Nothing goes over 1.
- `theLightIsBrightestTopLeft`: lobe share top-left > top > left > right = bottom = 0.25.
- `theGlintShrinksOverAPaleBackdrop`: `k` over a white sample is a quarter of `k` over black.
- `theGlowFadesAndStops`: 1/e at 1.4 dp, nothing past `GLOW_REACH_DP`.
- `aSeamIsPushedPastTheGlow`: `seamReachPx` when gathered is at least the glow's reach plus the pull,
  the corner radius and 2 dp.
- `noPixelReadsMoreThanSixSamples` keeps its counts, 4 and 4.
- `GlassStackTest`: with the switch on and a refracting spec, no `GradientRimDrawable` layer; off,
  unchanged.
- `FancierGlassPreferencesTest`: the switch defaults to off.
- On the emulator, `GlassRefractionAgslInstrumentationTest` compiles the new AGSL and draws a lit
  corner. Only a device can prove AGSL compiles.

**Device checks owed** (format of `project-docs/device-checks.md`):

- Agent · (on merge) · Round 1 commit · Adaptive glass off: Clear, Mist and Tint look exactly as on
  `pre` (crop a pane corner and the dock's top edge on both builds). On: the edge is the wallpaper's
  colour brightened, brightest top-left and faint all round; no white line and no second stroke; over a
  pale wallpaper the glint shrinks; no bright line on a seam (keyboard over the under-pill strip, status
  over the window bar); the dock shows one edge, not two; in a joined frame, no glow runs along the
  screen's own edges · emulator, `glass-city` and `glass-flowers` wallpapers, Docked and Floating,
  keyboard up and down.
- Agent · (on merge) · Round 1 commit · `GlassRefractionAgslInstrumentationTest` passes · emulator.
- Developer · (on merge) · Round 1 commit · With the switch on, the edge reads as light, not an
  outline. Also record whether floating panels still stand apart from a white wallpaper: that is
  Round 5's gate · pong, release build.

## Round 2 · C-01 with K-2: a film under clear glass

**What you will see.** On Clear, over bright parts of the wallpaper, the glass darkens just enough
for light text to read. Over dark parts nothing changes. A wallpaper with both, say white clouds
in a dark sky, gets film over the clouds only. Mockups `*-contrast-local.png`.

Apple asks for a 35% dark layer under Clear over bright content. The developer's words: "a wallpaper
may not be a coherent thing, it could have both dark and white areas". So the film is decided at
every pixel, and the cap is 40%.

**Which glass takes it.** Glass whose own opacity is 20% or less. That is Clear (10%) and any Custom
as clear. Mist (60%), Tint (46%) and Solid do not take it.

Why Clear only: Apple says Clear "does not have adaptive behaviors" and needs a dimming layer, while
Regular adapts by itself. Our Mist and Tint already carry 46 to 60% tint plus OnGlass's veil. A film
under them would add little, and it would move the veil on the shipped Looks. The gate reads each
surface's own opacity (Base, keyboard, terminal), never a size-scaled one (Round 3).

**And only for light ink.** The film only darkens, and darkening only helps light text. When the ink
is dark (light mode over a light wallpaper), the film is off.

**In the program**, right after the bend and the clarity mix, before the edge light:

```
float L = dot(s.rgb * s.rgb, kLumaW);              // c² stands in for linear light
float keep = L > uFilmLuma ? sqrt(uFilmLuma / L) : 1.0;
col.rgb *= max(keep, 1.0 - uFilm);                 // never more than uFilm darker
```

- `kLumaW` is (0.2126, 0.7152, 0.0722).
- c² for linear light is gamma 2.0 instead of 2.2. It is cheaper than `pow`, and it rates a pixel a
  little brighter than it is. So it errs toward slightly more film, never less.
- `uFilm` is the cap: 0.40 on, 0 off. The program's default is 0.
- `uFilmLuma` is how bright the frame may be at a pixel (in c² terms) for the ink still to reach its
  target once everything above the frame is drawn.

**How the program knows the ink.** It does not need the colour, only that one limit. `ChromeInk`
works it out per surface, because it already knows the ink, the target and what is drawn over the frame:

1. The ink. For a chrome band, its light ink (`Contract.paleInk`). For the terminal pane, the
   palette's foreground. For the keyboard, the key label. Glass with no band of its own (palette, sheet,
   drawer, planks, A–Z host) takes the band it is dressed for, or else the chrome's light role ink at
   body text.
2. The target: the band's own target scaled by the legibility level (`ChromeInk.target`). That is 4.5
   for body text at the Default level.
3. What sits over the frame up to the ink: the frost filter (`GlassFilters.frost`: saturation 1.3,
   then ×1.06), the launcher's dim, the base tint at its alpha, the light model's worst row
   (`DockGlassRendering.worstLightModelStop`), and for the keyboard the key cap. OnGlass already
   composes exactly these (`OnGlass.backdrop`, `DockGlassRendering.glassSurfaceAt`).
4. A search over a grey frame `g` from 0 to 1: the brightest `g` whose composed surface still gives
   `ratio(ink, surface) ≥ target`. Then `uFilmLuma = g²`.

So the frost's 6% gain, the tint and the dim are all folded in on the Java side. The program does one
comparison.

How much film, roughly (white ink, target 4.5, only the frost's gain above the frame, gamma 2.2):

| Wallpaper grey (sRGB) | 0.3 | 0.5 | 0.6 | 0.8 and brighter |
|---|---|---|---|---|
| Film | none | 13% | 27% | 40% (the cap) |

The pale primary ink `#B8C7FF` needs more: its limit is about half of white's. Where the cap stops
short, the veil makes up the rest.

**No double darkening with the veil.**

- OnGlass still decides the veil, but on the filmed backdrop. In `ChromeInk.resolveBand`,
  `terminalPane` and `onFixedInk`, the wallpaper sample under the band goes through
  `GlassFilm.filmed(sample, spec)` (the Java twin of the three lines above) before it is composed.
  Where the film already reaches the target, the veil search finds 0 and no veil is drawn. Where the
  40% cap stops short, the veil buys only what is left.
- The veil works from the band's average sample, the film from each pixel. Filming the average says the
  band is a little brighter than it really ends up, so the veil can come out slightly higher than
  needed, never lower.
- The polarity vote stays on the unfilmed glass. Switching Adaptive glass on cannot flip the chrome's
  polarity, and the film cannot feed back into its own choice of ink.

**The routes to every surface.** The dropped attempt (commit `657153bd0`) found them all. Reuse the
map, not what it sent:

| Surfaces | Route |
|---|---|
| keyboard host, frame gutter, under-keyboard card | `GlassStack.build` → `SharedFrameDrawable.setFilm` |
| status strip, window bar, palette, sheet, drawer, off-dock plank, A–Z host | `WallpaperFrostPainter` → `SharedFrameDrawable.setFilm` |
| dock, under-pill strip | their `RenderEffect` program → `Program.setFilm`; the effect is rebuilt only when the spec moved |
| terminal pane slabs | `PaneSurfaceStyle` → `PaneGlassBackdropView` → `Program.setFilm`; the pane refreshes its veil before it draws the frame, so both land in one draw |

`SharedFrameDrawable.setFilm` writes both programs (the current one and the crossfade's previous
one), on set and in `onBoundsChange`, and compares before it writes.

**Code.**

- `chrome/GlassFilm.java`, new and pure: `Spec` (cap, frame limit) with `NONE`;
  `takes(opacityPercent, adaptive, ink)`; `frameLumaFor(ink, target, layers above)`;
  `keep(sample, spec)`; `filmed(sample, spec)`.
- `ChromeInk.film(Band band, int opacityPercent)`, and `film(Object key, Rect rootRect, int
  opacityPercent)` for glass with no band. Remembered per band, forgotten with the band's answer and
  keyed on the cache's generation.
- `Program.setFilm(float cap, float frameLuma)`.
- In `ChromeInk`'s class doc, a paragraph on the film beside "Where a veil is drawn".

**Tests.**

- `GlassFilmTest`: `nothingOverADarkBackdrop`; `theFilmStopsAtTheTarget` (white ink over grey 0.6: the
  ratio after film and frost is within 2% of 4.5); `neverMoreThanFortyPercent`; `aDarkInkTakesNone`;
  `onlyClearGlassTakesIt` (opacity 10 and 20 yes, 21 and 60 no); `theLayersAboveAreFolded` (a 10% tint
  changes the limit); `theProgramAndItsTwinAgree` on 50 samples.
- `ChromeInkTest`: `theVeilIsMeasuredOnTheFilmedBackdrop` (the veil with film is at most the veil
  without, and 0 where the film reaches the target); `thePolarityIgnoresTheFilm`;
  `switchedOffIsTodaysAnswer`.
- `GlassRefractionTest`: the AGSL declares `uFilm` and `uFilmLuma`; `uFilm` defaults to 0; the sample
  counts stay 4 and 4.

**Device checks owed.**

- Agent · (on merge) · Round 2 commit · A wallpaper half white (#F2F2F2), half dark (#202020), Clear,
  dark mode, switch on: pane text, status labels and A–Z letters over the white half have a dark film
  under them and read; over the dark half the glass is as before; the film has no hard edge where it
  starts (it follows the blurred frame); Mist and Tint unchanged with the switch on; switch off is
  today · emulator.
- Developer · (on merge) · Round 2 commit · Your own wallpaper on Clear: does the text read, and does
  the film look like dark glass or like a smudge? Home ↔ terminal slide with the switch on and off:
  no dropped frames (`docs/agents/performance.md`) · pong.

## Round 3 · C-07 and C-03: small and big glass behave differently

### C-07: small pieces choose their ink

**What you will see.** The A–Z strip, the chips and the sheet cards pick light or dark text for what
is under them: dark letters over a bright spot, light letters over a dark one. Big panels (pane,
keyboard, dock) and the status and window bars never flip. They rely on the film and the veil.

**This reverses a rule.** ChromeInk's coherence rule today is one polarity for all chrome, and "no
band can flip on its own" (ChromeInk's class doc). It was made to stop "a veiled status bar in dark
ink a few hundred pixels above a bare A–Z strip in pale ink". C-07 asks for that very thing, for small
pieces, as Apple does: small elements flip, big ones don't. The commit that builds this rewrites that
paragraph of the class doc. The rule stays for everything outside the small set.

| Small piece | Today | New |
|---|---|---|
| A–Z strip (rail, letters, pull tab) | chrome band `AZ_STRIP`, shared polarity | its own polarity, one answer for the whole rail, not per letter |
| session chip | chrome band `SESSION_CHIP` | its own polarity |
| window chips | `inkOn(resolved, ground, …)` on the window bar | its own polarity per chip |
| terminal sheet cards (menus, confirms, prompts) | the chrome's polarity | its own polarity per card |

Launcher popups (`AnchoredMenu`) are not in the set. They draw a live `RealtimeBlurView` with M3 ink,
not the frame, so nothing here reaches them.

**How.**

- Each small piece keeps a memo, keyed by its band or by the caller's key: polarity, rect, wallpaper
  generation. It reuses the dropped attempt's `surfaceLuma` memo: it reads through
  `GlassBackdropCache.sampleUncached(rect)` (already on `pre`), never enters the band cache (so it
  casts no vote), and a rect that slid 24 px or less keeps its answer.
- The choice: the ink that reads better on the piece's flat glass (wallpaper, dim, base: the same
  flat glass the polarity vote uses). It flips only when the losing ink beats the current one by
  `POLARITY_FLIP_MARGIN` (1.15), the chrome's own margin. That margin is the hysteresis: the
  head-room a flip needs. A piece starts on the chrome's polarity.
- The frame is a still, so an answer can only move when the piece moves or the wallpaper changes.
  It never changes on its own.
- The small pieces still vote in the chrome's polarity, so switching Adaptive glass on does not move
  the bars.
- A flip changes the ink on the next chrome pass. There is no fade in this round.
- Code: a `Contract.ownPolarity` flag with its own `Polarity`, and
  `ChromeInk.elementInk(Object key, Rect rootRect, int darkInk, int paleInk, int ground, double target)`
  for the chips and cards.

**Tests** (`ChromeInkTest`): `aSmallPieceTakesTheInkThatReads`; `itHoldsUntilTheMargin` (better by
1.10 does not flip, 1.20 does); `aShortSlideKeepsTheAnswer`; `bigPanelsKeepTheChromesPolarity`;
`theBarsDoNotMoveWhenTheSwitchFlips`; `switchedOffIsTheCoherenceRule`.

### C-03: size changes the glass

**What you will see.** Small glass (chips, the A–Z strip, the bars) is clearer: more wallpaper, less
tint. Big glass (panes, keyboard) stays as it is today. Nothing gets whiter.

**Why big glass does not get more opaque.** The ask says larger surfaces get "slightly more
frosted/opaque" and never mix in white. In light mode those two clash. The glass base there is
`termux_surface_panel_high` (`resolveAccessoryGlassBaseColor`), a near-white panel tone. More opacity
is more of that near-white, the milk the dropped attempt made. So big glass keeps today's opacity, and
the size term only takes tint away from small glass. Big glass still reads more opaque than small
glass; it gets there because the small glass gets clearer.

**The term.** `share = 0.7` at a short side of 48 dp or less, `1.0` at 160 dp or more, a straight line
between. The anchors are constants, tuned on pong. It multiplies the tint's alpha (`barAlpha`) before
`ChromePolicy.dockGlassBaseAlpha`, so the base wash, the sheen and the foot thin together. The share is
never above 1.

**What it does not touch.**

- **Depth.** Depth owns the bend and the edge band, and size never touches them. The program already
  caps the bend at half the short side, so small glass bends less by itself. Depth's curve and the
  editor stay as they are.
- **Blur.** A blur that changed with size would need one more resident frame per radius and one more
  blur pass on each wallpaper change. The opacity term gets the same ordering without that cost.
- **The film gate and the frost filter.** They read the surface's own opacity, not the scaled one.
  Round 3 cannot move a surface in or out of the film, or change its frost offset
  (`GlassFilters.frost(opacityPercent)`).
- **The veil.** OnGlass measures what is drawn (`noteBandGlass` gets the scaled alpha), so if thinner
  tint needs a veil, the veil follows.

**Where.** `GlassSurfaceFactory.surface(...)` gets the surface's short side in dp. 0 means unknown
and gives share 1, today's look, until the next pass after layout. The callers that build small glass
pass it: status strip, window bar, A–Z host, dock plank, off-dock planks, session chip, the window
chips' wash. The `GlassStack.build` specs (keyboard, gutter, under-keyboard card) and the pane slabs
are big and need no change. The policy is pure: `chrome/GlassSize.share(float shortSideDp)`.

The bars count as small. The status strip and window bar are roughly 24 to 32 dp tall (not measured),
so they take about 0.7. Apple counts navigation and tab bars as small too. If the developer wants the
bars to keep today's tint, they can be left out by role in one line (open question 2).

**Tests** (`GlassSizeTest`): `aChipIsClearerAPanelIsNot` (share at 48 is 0.7; at 160 and 400 it is
1); `neverMoreOpaque` (share ≤ 1 at every size); `unknownSizeIsToday` (share at 0 is 1);
`depthIsUntouched` (`bandPx` and `strengthPx` do not change with size);
`theFilmGateReadsTheLooksOpacity`.

**Device checks owed for Round 3.**

- Agent · (on merge) · Round 3 commit · Mist, where it shows most, switch on against off: the A–Z
  strip, status bar and chips are visibly clearer; pane and keyboard unchanged; in light mode nothing
  is whiter anywhere. On the half white, half dark wallpaper: the A–Z letters are dark over the white
  half and light over the dark half; the status bar keeps one ink; dragging a window chip across the
  boundary flips its title once, not back and forth · emulator.
- Developer · (on merge) · Round 3 commit · Does the A–Z strip changing ink help or distract? Are the
  bars too clear? · pong.

## Round 4 · C-10 and C-09: motion and touch

### C-10: the edge grows in with the fade

**What you will see.** A sheet card fades in as it does today, and its edge grows in with it: the
bend and the edge light start at nothing and are full when the fade ends. On the way out they shrink
as it fades. Apple says glass appears by "gradually modulating the light bending and lensing".

**Which surfaces.** Those that come in through `GlassMotionPlayer.enter` / `exit` and whose glass is a
`SharedFrameDrawable` that refracts. Today that is the terminal sheet card (`TerminalSheetController`,
about lines 1219 and 1269). "The editor card" is taken to be the sheet card used for text prompts,
which is the same path (open question 1). The foot panel slides up with no fade, so it keeps its full
edge. The command palette fades by another path; adding it is one call if wanted.

**How.**

- AGSL `uniform float uForm;`, 0 to 1, default 1. It scales the pull (and so the colour split) and the
  edge light `k`, gathered or hairline. It does not scale the film, so text reads from the first frame.
- `Program.setForm(f)`, and `SharedFrameDrawable.setForm(f)` for both programs, which invalidates.
- `GlassMotionPlayer.enter` / `exit(card, motion, onEnd, @Nullable FormSink form)`. The form follows
  the alpha's own fraction and curve: classic 170 ms in and 110 out, Mist 280 and 200. The sink is the
  card's frost drawable. At the end the form is exactly 1 and nothing more is written.
- Only during the fade. At rest nothing animates.
- Off when the Fancier Glass motions are stopped (lazy mode, battery saver, reduced motion,
  animations off): the form stays 1, as today. Only with the switch on and the program drawing.

**Tests.** `GlassRefractionTest`: `formScalesThePullAndTheLight` (at 0: no pull and no light, the film
unchanged); `formDefaultsToOne`. A `GlassMotionPlayer` test with a fake ticker, like
`GlassTouchLightTest`'s: the form tracks the alpha's fraction; it ends at exactly 1; a re-enter in the
middle of an exit starts from where it was; stopped motion writes nothing.

### C-09: a dock press glows into the next bar

**What you will see.** Docked, with a joined frame: pressing a dock icon lights the glass under the
finger, and the light spills a little into the bar beside it (the keyboard or the status bar), weaker
there.

**Verify first.** Under a joined frame the dock has no glass of its own: `frameStandsDown()` makes
`shouldUseAccessoryRenderEffectBlur` false. But the dock's touch light writes into the dock's
`RenderEffect` program (`dockTouchFade()`, `mDockRefraction`). So a dock press there probably lights
nothing today. Check this on the emulator before building.

**How.**

- **Route.** When the frame stands down, the dock's touch goes to the frame's own glass: the
  `SharedFrameDrawable` that `applyFrameGutterGlass` builds (keep it in a field; today only the whole
  `LayerDrawable` is kept, as `mFrameGutterDrawable`). It goes through a `GlassTouchLight` (rise 110 ms,
  fall 240 ms) whose sink is that drawable's `setTouch`, in the frame view's coordinates. Outside a
  joined frame nothing changes: `DockTouchFade` as today.
- **Spill.** AGSL `uniform float4 uTouchHome;`: the rect of the bar the finger is on, in the surface's
  coordinates. The touch counts in full inside it and at `kSpill` (0.35) outside, across a soft 6 dp
  edge. The default home is the whole plane (±1e6), so every other surface is unchanged. The light still
  reaches only `RADIUS_DP` (56 dp) from the finger, so the spill is a short glow across the boundary,
  not the whole next bar.
- `Program.setTouchHome(l, t, r, b)` and `SharedFrameDrawable.setTouchHome`.
- Gated like every glass touch light (`isGlassTouchLightAllowed`), and by the switch.
- Not included: spill from the frame into the pane slabs, which are separate programs. The ask is the
  next bar.

**Tests.** `GlassRefractionTest`: `theTouchCountsInFullAtHome` (1 inside, 0.35 beyond the 6 dp edge,
smooth between; the default home counts 1 everywhere). A pure routing rule beside `FrameGlassPolicy`,
`touchGoesToFrame(standsDown, adaptive)`, with its own test.

**Device checks owed for Round 4.**

- Agent · (on merge) · Round 4 commit · First, before building C-09: in a joined frame, does a dock
  press light anything on `pre`? Record it. After: Docked, keyboard up, joined frame; hold a dock icon:
  a glow under the finger and a weaker glow on the keyboard's edge next to the dock; it fades within a
  quarter of a second after release; Floating unchanged; nothing lights under reduced motion, lazy mode
  or battery saver · emulator.
- Agent · (on merge) · Round 4 commit · Open a terminal sheet (the session menu) and record the
  arrival: the edge grows with the fade; at rest it matches Round 3; the frame count stops when the
  card settles (`dumpsys gfxinfo`) · emulator.

## Round 5 · C-11: a soft shadow, only if needed

**The gate.** Build this only if, after Round 1 on pong, the gathered edge does not set big panels
apart from the wallpaper.

**The measurement that decides it.** Floating, switch on, Clear and Mist. Three wallpapers: plain
white (#F5F5F5), a busy mid-grey photo, and the developer's own. For the pane, the keyboard and the
dock: take a screenshot, and along the top and bottom edges compare the average luminance of the 2 dp
just inside the outline with the 2 dp just outside. The edge separates the panel when:

1. the developer can see where the panel ends at arm's length, and
2. the ratio is at least 1.10 along at least the top edge.

If either fails, build C-11. Record the numbers in this file.

**If built.**

- Floating only. Docked panels sit flush, and a shadow there would read as a line.
- The pane, the keyboard and the dock only.
- Start from Kyant's numbers: blur radius 24 dp, offset 4 dp down, black at 10%.
- Static: a ring drawable behind the panel, clipped out of the panel's own shape so the shadow never
  darkens the glass. Its bitmap is cached per size and corner radius and redrawn only when the panel's
  size changes. No repaint at rest.
- Not adaptive in this round (Apple's "stronger over text" is left for later).
- Behind the switch.
- Tests: a `ShadowRingTest`: nothing drawn inside the shape; the offset and alpha; a cache hit at the
  same size.
- Device check owed: Developer · (on merge) · Round 5 commit · Does the shadow help or muddy, on the
  white and busy wallpapers? · pong.

## What to keep from the dropped attempt

Branch `worktree-agent-a53eeccfec00e894f`, commits `408477477` (shader, `GlassBody`) and `657153bd0`
(luminance threading). The developer dropped it because its body made everything milky on pong.

| Piece | Verdict | Why |
|---|---|---|
| The body: `uBody`, mixing the face toward white over dark bands (most 18.75%) | Drop | It made everything milky. |
| The hairline that followed luminance (`kShadeDark`, `kShadeBright`, `kRimDark`) | Drop | E-1 replaces the hairline. |
| `uDim`, one darkening per surface | Drop | K-2 replaces it pixel by pixel. One number missed the bright spots: on the emulator the pane's average was dark, so light text over a bright ribbon got nothing. |
| `Program.setBackdrop`'s pattern: compare before writing, both programs, again in `onBoundsChange` | Reuse | For `setFilm`. |
| The call-site map: `GlassStack.build`, `WallpaperFrostPainter`, the dock and strip `RenderEffect`s, `PaneSurfaceStyle` → `PaneGlassBackdropView`, the pane's veil refreshed before its frame draws | Reuse | The film travels the same routes. |
| `ChromeInk.surfaceLuma`'s memo: per key, per generation, 24 px slop, through `sampleUncached`, no vote | Reuse | C-07's per-piece sample. |
| `ChromeInk.bandLuma` and `terminalPaneLuma` | Not needed | The program reads the wallpaper itself. Java needs the ink's limit, not the wallpaper's luminance. |
| `GlassBody.strength`'s anchors (48 and 200 dp) | Reuse the idea | C-03 uses 48 and 160 dp, for opacity, not for a body. |
| Its worker's warning: after a wallpaper change, a keyed rebuild can read before `ChromeInk.invalidate()` bumps the generation | Carry over | It applies to C-07's memo and to the film's spec. Key both on the cache's generation. |

## Costs and limits

- **Samples.** K-2 and E-1 use the sample the program already read. C-03, C-07, C-09 and C-10 add
  none. `noPixelReadsMoreThanSixSamples` keeps its counts.
- **Arithmetic per pixel.** K-2 adds a dot and a square root on Clear surfaces. E-1 adds one `exp` in
  the outer 10 dp only. The cost lands on frames that redraw anyway (slides, keyboard travel). At rest
  nothing redraws.
- **Memory.** Nothing new.
- **Uniform writes.** Only on change, as today. C-10 writes one per fade frame, C-09 one per touch frame.
- **No live sampling.** Everything reads the still frame (ADR 0004).
- **Measured, not claimed.** Frame timing on pong for a home ↔ terminal slide, with the switch on and
  off, is a Round 2 device check.

## The developer's answers (2026-10-16)

These override the rounds above where they differ.

1. **C-10: both.** The terminal sheet cards and the Appearance editor panel grow their edge as they
   fade in. The editor panel does not come in through `GlassMotionPlayer`; Round 4 finds its entry
   path and routes `uForm` there too, or reports why it cannot.
2. **C-03: the bars are not small.** The status strip and the window bar keep today's tint. Only
   chips, the A–Z host, the dock plank, the off-dock planks, the session chip and the window chips'
   wash pass a short side.
3. **C-07: yes.** Small pieces pick their own ink; rewrite ChromeInk's coherence paragraph for them.
4. **Default: off** until it is seen on pong.
5. **C-05: option (a).** Fresh installs apply Clear on first run. No constants move, and the
   default-mode dock keeps `Look.DEFAULT`. Round 0 now builds this after the check, with a test that
   a first run stores Clear's values and that Depth reads Clear's position.

## Item receipt

| ID | Where |
|---|---|
| C-01 | Round 2, built through K-2. |
| K-2 | Round 2: the program's three lines, the ink limit from `ChromeInk`, the veil on the filmed backdrop, Clear only. |
| C-02 | Round 1. |
| E-1 | Round 1: the formula in the program's terms, its constants, what happens to today's hairline and strokes. |
| C-03 | Round 3: share by short side, opacity only, big glass unchanged, Depth untouched. |
| C-05 | Round 0: the arithmetic, the verdict, what a fresh install shows, the device check. No constants changed. |
| C-07 | Round 3: a polarity per small piece, with hysteresis; big panels and bars keep the shared one. |
| C-09 | Round 4: route the dock's touch to the joined frame's glass, spill outside the touched bar at 0.35. |
| C-10 | Round 4: `uForm` ramped with the existing fade, sheet cards only, Fancier Glass path only. |
| C-11 | Round 5: gated on a measurement after Round 1. |
