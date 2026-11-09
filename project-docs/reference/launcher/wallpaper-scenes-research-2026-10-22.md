# Wallpaper scenes: eleven Wallpaper Engine examples judged for first-party AGSL scenes (2026-10-22)

Date: 2026-10-22. Status: research only. Nothing is built and no decision is recorded here.
Brief: "Open questions › Wallpaper scenes" in
[../../active/agsl-effects-and-touch-latency-2026-10-22.md](../../active/agsl-effects-and-touch-latency-2026-10-22.md)
(in-launcher layer behind the surfaces, not a system live wallpaper; scenes react to panes, touches,
typing, bell and focus; glass samples a low-res second pass at about 1/8 per axis; recreate the look,
never the workshop assets; the user's own photo is not animated). Cost rules from
[../terminal/agsl-performance-and-touch-latency-research.md](../terminal/agsl-performance-and-touch-latency-research.md) §1;
frame budget from [../../active/input-latency-study.md](../../active/input-latency-study.md)
("Baseline on pong", "Buffer Stuffing analysis").

Visual review page with every preview beside its verdict:
https://amal-build.taild9c5dc.ts.net/reports/wallpaper-scenes/ (files in `~/reports/wallpaper-scenes/`).

Conventions: **(inference)** is my reading with no measurement behind it. Every cost class below is
(inference): nothing was run on pong. Item data came from the Steam
`ISteamRemoteStorage/GetPublishedFileDetails/v1` API and the workshop pages, fetched 2026-10-22.
Preview GIFs are 192-256 px, so fine detail is judged from frame sheets and, where the page listed
them, the extra screenshots (examples 3, 9, 10). Workshop content was read as data only. No
wallpaper package was downloaded.

## Answer in brief

1. **Taste in one line.** Dark, low-key fields lit by one coloured light (violet, teal, amber), with
   slow motion that never stops: contours that flow, rain on a pane, haze, glowing filaments,
   petals. Half the examples are technical/geometric (bar field, contours, slats, orbits, Saturn), a
   third are nature with small motion (rain valley, magnolia, dahlia), and one is retro hardware
   (nixie tubes). Every one also carries a clock or dashboard overlay; we drop all of those.
2. **Verdicts.** Recreate the look: 8 (slat backdrop), 10 (dahlia), 11 (topography, brighter).
   Reinterpret: 1 (bar field), 3 (rain on glass), 4 (Saturn), 6 (orrery, no clock), 7 (magnolia),
   9 (nixie/CRT), 5 (atrium, later, needs a commissioned plate). Skip as a scene: 2 (a licensed
   anime character in a video texture), but take its idea, windows that recolour what is behind
   them, into the glass.
3. **Cost.** On pong the GPU has about 7 ms after `queueBuffer` against a 16.2 ms deadline. Treat
   "cheap" as a full-screen pass of tens of ALU ops per pixel and at most one sample (well under
   1 ms), "moderate" as 50-150 ops or 2-4 samples or a half-resolution pass (1-2 ms), "heavy" as
   ray marching or full-res fbm with 5+ octaves (over 2 ms, out of budget while typing). The
   1/8-per-axis glass pass is about 41k pixels, 1/64 of the screen, so it is cheap for every scene.
   Most scenes here are cheap or moderate when the soft layers (haze, fog, glow) run at half
   resolution and the ambient motion runs at 30 Hz into a cached layer, with reactions at full rate.
4. **Reactions should be events, not lifts.** The 10-06 stills failed at +15%. Every proposal below
   makes a keystroke, the bell or a touch change something you can point at: a petal falls, a tube
   lights the typed letter, a hill rises under the finger, a row of slats lifts, a lightning flash.
5. **Prototype first: "Contour" and "Filament".** Contour (example 11, brighter, touch raises hills,
   typing runs sparks) is the cheapest full-screen scene, needs no art, and proves the event wiring
   and the glass low-res pass. Filament (examples 9 + 5: nixie tubes and CRT glow in haze that light
   up with typing) is the developer's stated taste, the strongest typing reaction, and the test case
   for warm scene colour flowing into tinted glass.
6. **Coloured glass.** Make the pane tint a *filter* on the scene's low-res pass (a gel multiply, a
   duotone or a hue shift) instead of a flat colour laid over it, with the colour taken from a small
   palette each scene publishes as uniforms (no GPU readback). Opt-in knob; the legibility veil stays.

## Cost model used here

- Screen 1080 × 2412 = 2.6 Mpx. Half per axis = 0.65 Mpx. Glass pass at 1/8 per axis = 135 × 302 =
  41k px.
- A continuously animated scene redraws every frame it runs, and so does every glass pane that samples
  it. The community finding in the AGSL note (a redrawing shader holds a GPU power state) means a
  lower rate saves less power than expected, but a 30 Hz ambient layer still frees GPU time on two of
  every three frames at 90 Hz while the user types **(inference)**.
- Budget rule proposed for all scenes: ambient layer at 30 Hz (45 Hz for rain), soft layers at half
  resolution, crisp layers (lines, sparks, drop edges) at full resolution but cheap, reaction overlays
  drawn only in their bounding quad, and the whole scene paused under the existing gates (Battery
  Saver, animator scale 0, thermal status, screen off, launcher not visible). When a terminal pane
  covers the screen, only the glass's low-res pass needs updating **(inference)**.
- AGSL is ES2-level SkSL. Loops need constant bounds. Screen-space derivatives (`fwidth`) should be
  treated as unavailable (**unverified**), so line widths come from an analytic gradient or a
  neighbour sample.

## The examples

### 1. sonic-topography

https://steamcommunity.com/sharedfiles/filedetails/?id=3747222633 · Type: Web, audio responsive,
16 colour themes. Preview: `img/ex1.gif`.

- **What it shows.** A perspective field of square columns (the description says 160 × 160 bars)
  under a dark navy sky, lit violet. Low audio sends concentric ripple rings out across the field;
  high audio drops meteors that burst on impact; with no audio the bars breathe gently.
- **Layers and motion.** Heightfield of extruded bars, a ring wave travelling outward, sparse bright
  "hot" bars, horizon falloff into fog.
- **AGSL build.** A true 3D bar field needs a grid ray-march (DDA through cells): heavy. Reinterpret
  as 2.5D: a top-down or steep oblique grid where each cell's height drives its brightness and a
  fake extrusion (one offset sample for the side face), heights from a sum of ring waves and 2-octave
  value noise. No art. Ring waves are analytic (`sin(k·r − ωt)·exp(−r)`), up to 8 from a uniform array.
- **Cost on pong.** True 3D: heavy. 2.5D: cheap-moderate (≈40-60 ops, no samples), fine at 30 Hz.
- **Reactions.** Each keystroke drops a ring from the cursor's screen position; Enter sends a larger
  ring; the bell drops a "meteor" (bright streak, then a ring and a spray of lit cells) on the
  bell's pane; touch lifts the cells under the finger; Terminal effect on swaps the palette to the
  effect's phosphor colour.
- **Glass low-res pass.** A soft violet wash with brighter bands where rings pass, so panes pulse as
  a ring crosses them.
- **Fit with notes.** No note from the developer. It is the bar-field cousin of example 11.
- **Verdict: reinterpret.** The ring-from-keystroke idea is strong, the 3D field is not affordable.

### 2. Miku Monitoring

https://steamcommunity.com/sharedfiles/filedetails/?id=3585875739 · Type: Scene with **Video
Texture**, anime, audio responsive. Preview: `img/ex2.jpg` (a still).

- **What it shows.** A close illustrated face of an anime character (a licensed character) with
  retro "Serious error occurred" dialogs floating over it. The central dialog shows the eye
  magnified and recoloured pink-magenta, against the cooler blue-grey of the rest.
- **Layers and motion.** Painted character art in a video texture, floating dialog windows,
  presumably flicker and glitch (no description on the page).
- **AGSL build.** Not recreatable: the subject is a character and the motion is video.
- **Cost on pong.** n/a.
- **What to take.** The windows act as *colour filters* on what is behind them: the same image,
  regraded inside a frame. That maps directly onto the launcher's glass panes (see "Coloured glass").
  A glitch flash on the bell, scoped to the bell's pane, is a second idea worth keeping.
- **Glass low-res pass.** n/a.
- **Fit with notes.** The developer's note is about the colour-filter windows, not the scene.
- **Verdict: skip the scene, take the filter idea.** Character art and video cannot be ours.

### 3. Lost Valley 2 | northway.

https://steamcommunity.com/sharedfiles/filedetails/?id=3774904326 · Type: Scene, landscape, audio
responsive; photographic art credited to another artist. Previews: `img/ex3.gif`, `img/ex3-b.gif`.

- **What it shows.** A misty green valley between two ridges under a heavy grey sky, seen through a
  window with raindrops on it. A small day/date widget ("FRI") sits in the middle.
- **Layers and motion.** Static photo; slow cloud drift; raindrops on the pane (static beads and a
  few running drops); thin rain streaks; low fog.
- **AGSL build.** Rain on glass is a well-known procedural technique (grid cells, one drop per cell
  with a jittered position, a running drop with a trail, refraction by offsetting the backdrop lookup
  by the drop's normal). Write our own; do not port Shadertoy code under NC licences. The backdrop
  would be procedural: 3-4 layered ridge silhouettes from 1D noise, fog bands between them, a sky
  gradient with 2-octave cloud noise, baked once into a bitmap at half resolution and only the
  clouds/fog animated. No photo needed; a painted plate is optional if the procedural valley looks
  too plain.
- **Cost on pong.** Moderate: two drop layers (≈80-120 ops) plus one backdrop sample per pixel at
  full resolution, so drop edges stay crisp; 45 Hz is enough for running drops.
- **Reactions.** Touch wipes a clear streak through the drops that slowly mists back (the most
  physical reaction in this set). Typing shakes loose a running drop per keystroke near the cursor;
  the bell is a lightning flash that lights the ridges for 200 ms; opening a pane clears the fog in
  its region; Terminal effect on fogs the glass.
- **Glass low-res pass.** Drops vanish at 1/8 (good: no noise behind text); panes show the grey-green
  valley and brighten on lightning.
- **Fit with notes.** No note. Matches the calm, dark taste.
- **Verdict: reinterpret.** Rain on a pane plus a procedural valley is ours to build; drop the widget.

### 4. 土星 | Saturn - Sykm

https://steamcommunity.com/sharedfiles/filedetails/?id=3589454154 · Type: Scene, 3D, audio
responsive, HDR. The description lists a full ephemeris (VSOP87, Kepler solvers, 17 moons).
Preview: `img/ex4.gif`.

- **What it shows.** Close views of Saturn in muted sepia: the ring plane in grazing light with a
  moon crossing, then the planet with its rings; overlays show a clock, volume, date and a launcher
  dock with icons.
- **Layers and motion.** Analytic planet with banding, ring plane with particle scatter, a slowly
  moving camera, a moon transiting.
- **AGSL build.** Ray-sphere for the planet (banding from 1D noise on latitude, terminator from a
  light direction), ray-plane for the rings (radius → band density from a 1D profile, plus hashed
  particles near the camera), planet shadow on the rings and ring shadow on the planet are each one
  extra intersection. No art and no ephemeris needed.
- **Cost on pong.** Cheap-moderate (≈60-90 ops, no samples). The camera moves slowly, so 30 Hz is fine.
- **Reactions.** Touch tilts the camera (parallax a few degrees, springs back); typing makes the
  near ring particles glint; the bell sends a moon across the disc with a sharp shadow; page change
  rotates the view a step around the planet.
- **Glass low-res pass.** A warm beige band of ring light through dark: panes show a soft gold stripe.
- **Fit with notes.** No note; the overlays are clocks and go.
- **Verdict: reinterpret.** Analytic Saturn is cheap and calm; drop the ephemeris and every widget.

### 5. Office Lobby Waterfall

https://steamcommunity.com/sharedfiles/filedetails/?id=3144017277 · Type: Scene with **Video
Texture**. No description. Preview: `img/ex5.gif`.

- **What it shows.** A dark glass-roofed atrium in teal: a waterfall down a planted wall, a pool
  floor with ripples, and in front a wooden desk with a beige computer whose CRT shows a blue screen,
  lit by a desk lamp. Liminal, 1990s office.
- **Layers and motion.** Video of falling water and pool ripples; static furniture; the lamp pool and
  the monitor's blue glow.
- **AGSL build.** Water would be procedural (streaked noise scrolling down a mask; ripple caustics in
  the pool) over an **original plate**: one painted or rendered atrium still with three masks
  (waterfall, pool, screen). That plate must be commissioned or rendered (a simple Blender scene),
  never traced from this one.
- **Cost on pong.** Moderate: one plate sample, masked noise in about a third of the pixels, pool
  reflection one offset sample.
- **Reactions.** The CRT on the desk mirrors the launcher: its glow brightens per keystroke and shows
  a few scrolling lines when a command runs; the bell turns the screen red for a beat; touch on the
  pool spreads ripples; Terminal effect on puts scanlines on the little CRT.
- **Glass low-res pass.** Deep teal with a blue point near the desk.
- **Fit with notes.** Ties to the developer's CRT taste (note on 9).
- **Verdict: reinterpret, later.** Good mood, but it needs a commissioned plate before anything shows.

### 6. Solar system 太阳系 [3D/4k/自定义]

https://steamcommunity.com/sharedfiles/filedetails/?id=3455121165 · Type: Scene, 3D, audio
responsive. Preview: `img/ex6.gif`.

- **What it shows.** A bright sun in the centre of faint elliptical orbit lines, small planets on
  their orbits, black space; a large clock and date at the top.
- **Layers and motion.** Sun glow, orbit rings, planets moving at artful rates.
- **AGSL build.** Orbits as ellipses in screen space (distance to ellipse, approximated), planets as
  shaded discs on a constant-bound loop of 8, a radial glow for the sun. No art.
- **Cost on pong.** Cheap (≈40 ops, no samples).
- **Reactions (an orrery of the session).** Each open pane is a planet; opening a pane spawns a planet
  on a new orbit, closing one lets it drift out and fade; the focused pane's planet brightens and
  its orbit line lights; the bell is a flare from the sun; typing makes the sun's corona breathe.
- **Glass low-res pass.** Mostly black with a warm centre: panes near the middle warm up.
- **Fit with notes.** "No clocks or dates in our wallpapers": drop the clock.
- **Verdict: reinterpret.** Cheap, and panes-as-planets is a reaction only a launcher can have.

### 7. 万语难尽涩于口，祈尔繁芜胜常春

https://steamcommunity.com/sharedfiles/filedetails/?id=3712980239 · Type: Scene, landscape. No
description. Preview: `img/ex7.jpg` (a still).

- **What it shows.** Looking straight up through a blossoming magnolia (pink-white) against a clear
  blue sky, framed by dark tiled eaves top and bottom; a pink petal falling.
- **Layers and motion (inferred from the still).** Falling petals, branch sway, perhaps light flicker
  through the blossoms.
- **AGSL build.** Needs a tree. Generate it ourselves once (a space-colonisation or L-system tree
  drawn with `Canvas` at first run, blossoms as stamped procedural petals) and bake it to a bitmap;
  the shader adds sway as a low-frequency displacement of the bitmap lookup (more at the tips),
  procedural falling petals in grid cells (2-3 layers), and a sky gradient. Alternatively commission
  one original painted plate with an alpha mask.
- **Cost on pong.** Moderate: one bitmap sample with displacement plus petal cells (≈70-100 ops).
- **Reactions.** Each keystroke releases a petal near the top of the screen; Enter a small shower;
  the bell is a gust (sway jumps, a burst of petals across the screen); touch scatters petals away
  from the finger; when no pane is open the tree is in full bloom, and it thins as panes stack up.
- **Glass low-res pass.** Sky blue with pink patches: the brightest scene of the set, so the veil
  matters most here.
- **Fit with notes.** Fits "flowers with little animation" (note on 10).
- **Verdict: reinterpret.** Needs an original generated tree, then it is cheap and very readable.

### 8. Rhine Lab · 莱茵生命交互桌面 | Interactive Desktop

https://steamcommunity.com/sharedfiles/filedetails/?id=3799142774 · Type: Web, audio responsive.
Fan desktop themed on a game faction. Preview: `img/ex8.gif`.

- **What it shows.** A desktop of panels (clock, to-do, focus timer) over a backdrop: an oblique
  field of long rounded slats in diagonal rows, bone-white in light mode and graphite in dark,
  with soft shading and gaps. The panels themselves are flat translucent cards.
- **Layers and motion.** The slat field (rows drift and some slats sit higher than others), the
  panels on top.
- **AGSL build.** Pure geometry, no art: shear the plane, repeat a rounded-box SDF per cell with a
  per-row offset and per-slat height from a hash, shade from the SDF gradient with one key light,
  darken the gaps for ambient occlusion. Light and dark variants are two palettes.
- **Cost on pong.** Cheap (≈40-50 ops, no samples), even at full resolution and 60 Hz.
- **Reactions.** A keystroke lifts the slats in the cursor's row and the lift runs outward as a
  wave; the bell pushes every slat down and back up in a ripple from the bell's pane; touch presses
  slats down under the finger like keys; pane focus swings the key light toward the focused pane;
  page change slides the rows.
- **Glass low-res pass.** Near-flat bone or graphite with faint diagonal banding: the most neutral
  backdrop for text.
- **Fit with notes.** "Backdrops like these": the slat field is the thing; the panels and the clock
  go, and the faction branding is not ours.
- **Verdict: recreate the look.** Generic geometry, cheap, neutral behind text, light and dark.

### 9. Ember Hours - Portrait

https://steamcommunity.com/sharedfiles/filedetails/?id=3669800784 · Type: Scene, portrait, audio
responsive. Previews: `img/ex9.gif`, `img/ex9-1.jpg` to `img/ex9-5.jpg`.

- **What it shows.** A heavy industrial bezel holding four nixie tubes with orange filaments, in
  deep shadow and drifting haze, with optional day/date, year progress and music panels. The author
  lists "subtle per tube voltage flicker", "drifting haze", optional film grain and CRT modes.
- **Layers and motion.** Tube glow (digit glyph + halo + glass reflections), per-tube flicker,
  slow haze, the metal housing, grain.
- **AGSL build.** Housing as an SDF (rounded, chamfered rectangle with a bevel, a brushed-metal
  noise, a couple of bolts), tubes as glass cylinders with a highlight, the glowing cathode as a
  glyph sampled from a small atlas we render once from our own font with a wide halo from 2-3
  extra blurred samples (or a pre-blurred atlas mip), haze as 2-octave fbm at half resolution, grain
  last. No commissioned art is strictly needed; a commissioned housing plate would look richer.
- **Cost on pong.** Moderate: the haze at half resolution plus tubes in a bounded quad. The tubes need
  frames only on change and flicker; the haze can run at 30 Hz.
- **Reactions (no clock).** The tubes show the last characters typed, cycling through cathodes like
  a real nixie as each key lands; the filament surges on each keystroke and decays; the last exit
  status shows as a lit or dark indicator tube; the bell flares every tube with a stutter; Terminal
  effect on switches the scene into its CRT mode (scanlines and curvature over the whole scene).
- **Glass low-res pass.** Black with an amber band: panes in front take a warm glow.
- **Fit with notes.** "I like the ambience of cathode ray tubes, retro / steampunk (a little)". The
  "no clocks" rule means the tubes must show something else: typed glyphs fit the terminal.
- **Verdict: reinterpret.** The developer's clearest stated taste, and typing has an obvious target.

### 10. winOS (Limited Edition)

https://steamcommunity.com/sharedfiles/filedetails/?id=3213435819 · Type: Scene, audio responsive.
Previews: `img/ex10.gif`, `img/ex10-b.jpg`.

- **What it shows.** A large dahlia-like flower on black, lit purple in the preview and gold in the
  screenshot, under a desktop of widgets (clock, calendar, year progress, music).
- **Layers and motion.** The flower breathes and its colour drifts; the widgets are static panels.
- **AGSL build.** A procedural bloom: polar coordinates, 5-7 rings of petals, each petal a
  teardrop SDF repeated by angle with a per-ring twist, shaded by a fake normal (centre bright,
  edges dark) and ring depth. Slow rotation and opening via uniforms. No art.
- **Cost on pong.** Cheap-moderate (≈60-100 ops for 6 rings, constant loop), half resolution is
  acceptable because the bloom is soft.
- **Reactions.** The bloom opens a ring when the user comes home and closes slowly when idle; each
  keystroke trembles the outer ring; the bell makes it pulse open; pane focus rotates the bloom a
  step; page change shifts its hue between the palettes.
- **Glass low-res pass.** A purple or gold glow centred on the bloom.
- **Fit with notes.** "Flowers with little animation." Drop every widget.
- **Verdict: recreate the look.** A procedural bloom is cheap and needs no art.

### 11. Topographic DARK MODE (slowed/clean)

https://steamcommunity.com/sharedfiles/filedetails/?id=3035743755 · Type: Scene, audio responsive.
Preview: `img/ex11.gif` (author: preview sped up about 3×).

- **What it shows.** Thin grey contour lines of a noise heightfield on black, flowing slowly like
  water, with short white sparks running along the lines.
- **Layers and motion.** One heightfield, contour extraction, a moving highlight.
- **AGSL build.** 3-octave value noise for height, contours from `fract(h·n)`, a fixed pixel width
  from the analytic gradient (no `fwidth`), sparks as a travelling phase along the line (a function
  of position and time masked to a few short segments). Single pass, no art.
- **Cost on pong.** Cheap (≈50-70 ops, no samples). Lines must be full resolution; the motion is slow
  enough for 30 Hz.
- **Reactions.** Touch raises a hill under the finger so contours ring around it and relax; each
  keystroke sends a spark along the nearest line from the cursor; the bell sends a pulse outward from
  the bell's pane that lights each contour as it passes; an open pane presses a basin into the
  terrain under its rect; page change pans the map.
- **Glass low-res pass.** Lines blur into a faint even grey: panes stay neutral.
- **Fit with notes.** "Too dark but an example." Raise the line contrast, add a faint base gradient
  and two palettes (graphite on bone, light on deep blue).
- **Verdict: recreate (brighter).** Cheapest scene in the set and the best test bed for reactions.

## Synthesis

### Recurring themes in the developer's taste

- **One coloured light in the dark.** Violet (1, 10), teal (3, 5), amber (9, 4), with deep
  shadows. Calm overall level, so a terminal on top stays legible.
- **Technical geometry that moves slowly:** contour maps, bar fields, slats, orbits, rings.
- **Nature with small motion:** rain on glass, blossom and petals, a breathing flower, fog.
- **Retro hardware ambience:** nixie tubes, CRT, a beige 90s computer, steampunk bezels.
- **Not liked, by note:** clocks and dates in the wallpaper; too dark.
- Every example is also a dashboard (clock, calendar, music). The launcher *is* that layer, so the
  scene's reactions replace the widgets as the "alive" part.

### Original scene proposals

| Scene | Built from | Look | Reactions | Cost | Art |
|---|---|---|---|---|---|
| **Contour** | 11 + 1 | Flowing topographic lines, graphite-on-bone or light-on-deep-blue, sparks | Touch raises hills; key sends a spark from the cursor; Enter a ring; bell a pulse out of its pane; panes press basins; page pans | Cheap | None |
| **Filament** | 9 + 5 | A nixie bench in industrial haze; warm amber; optional CRT mode | Tubes show typed glyphs, cathodes cycle; filament surges per key; exit status tube; bell flares all tubes; Terminal effect on = CRT scanlines over the scene | Moderate | None needed (font atlas); optional housing plate |
| **Slats** | 8 | Oblique rounded slat field, bone or graphite | Key lifts the cursor row as a wave; touch presses slats like keys; focus swings the light; bell ripples the field | Cheap | None |
| **Window rain** | 3 | Procedural ridges and fog behind a wet pane | Finger wipes a clear streak that mists back; keys loosen running drops; bell = lightning; pane open clears fog | Moderate | None; optional painted valley |
| **Bloom** | 10 + 7 | A procedural dahlia that opens and closes; or a generated magnolia with petals | Opens on coming home, closes when idle; a petal falls per key; bell = gust; touch scatters petals | Cheap (dahlia) / moderate (magnolia) | Dahlia none; magnolia needs a generated tree |
| **Orrery** | 6 + 4 | Sun and orbits, or analytic Saturn | Panes are planets: spawn, drift out, focus lights an orbit; bell = flare or eclipse; touch tilts the camera | Cheap | None |

### Coloured glass: windows as colour filters (from example 2)

Today a glass pane samples a shared pre-blurred frame and lays a `tintColor` over it
(`PaneGlassBackdropView.setGlass(frame, rect, tintColor, …)`). Example 2's windows do something
different: the same picture, regraded inside the frame. Proposal **(inference, not built)**:

- **Filter, not overlay.** In the glass shader, map the sampled low-res scene through a per-pane
  filter: a *gel* (multiply by a colour, keeps the scene's light and dark), a *duotone* (scene
  luminance → mix of two pane colours, the strongest look), or a *hue shift* (rotate the scene's hue
  by a fixed angle). Each is a few ALU on the 41k-pixel glass pass or the pane's own pixels; no new
  samples.
- **Colour from the scene, without readback.** Each scene publishes a 3-5 colour palette as uniforms
  (key light, fill, accent, shadow) and updates it on reactions (Filament's amber surges on typing;
  Contour's spark colour). The glass reads those, so no GPU-to-CPU readback is needed. The focused
  pane takes the key light, other panes the fill; the bell's pane flashes the accent for a beat.
- **Legibility first.** The existing veil (legibility band) stays above the filter, saturation is
  capped, and the terminal palette's contrast target is checked against the filtered colour, as it is
  against the tint today.
- **Opt-in knob**, per the glass stance: "Window colour: Off / Tint (today) / Gel / Duotone", with the
  scene choosing the colours.

### What to prototype first, and why

1. **Contour.** Cheapest full-screen scene (one pass, no samples, no art), the developer liked the
   look and only asked for it brighter, and every launcher event has a visible, local answer (touch
   hill, key spark, bell pulse, pane basin). It is the right harness for the event plumbing, the 30 Hz
   ambient layer, the pause gates and the glass low-res pass, measured on pong against the typing
   baseline.
2. **Filament.** The developer's clearest stated taste (CRT, retro, a little steampunk), and the
   boldest typing reaction (the tubes spell what you type). It exercises the harder parts Contour does
   not: a half-resolution haze, a glyph atlas, a bounded-quad reaction, the Terminal-effect tie-in,
   and warm scene colour flowing into gel/duotone glass. It needs no commissioned art to start.

Slats is the fallback second if Filament's haze costs too much on pong: it is as cheap as Contour and
was asked for by name ("backdrops like these").

Owed before any decision: a measured GPU time per frame on pong for Contour at full and half rate,
with glass on and Terminal effect on, against the 7 ms margin.

## Sources not reached

- Workshop pages render previews only as small GIFs (192-256 px) or one still; examples 2 and 7
  have still previews only, so their motion is inferred (2 is a video texture; 7 has no
  description). No videos were watched.
- Examples 2, 5 and 7 have empty descriptions in the Steam API.
- Nothing was measured on a device; every cost figure is (inference) from the AGSL rules of thumb.
- Whether AGSL exposes `fwidth`/`dFdx` at its ES2 level was not checked (**unverified**).
