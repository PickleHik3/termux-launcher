# Live wallpapers: first-party AGSL scenes (2026-11-05)

Status (2026-11-06): built on `v2-redesign` and checked on pong (`d5c40d253`). Five first-party
scenes (Aurora, Contour Waves, Nebula Drift, Fractal Bloom, Particles; Bokeh Orbits declined) replace
the three of decision 2. The picker follows the OpenDesign design (`live-wallpapers-df6e/picker.html`)
without its "Show home" switch. Not yet built: the shared reaction inputs (decisions 9-10). Power on
pong, unplugged, whole phone: no scene about 1.27 W; Aurora (half-res) about 1.2 W; Particles about 2.0 W.

Research behind it:
[AGSL capabilities](../reference/launcher/agsl-capabilities-research-2026-11-05.md),
[AGSL wallpapers](../reference/launcher/agsl-wallpapers-research-2026-11-05.md).
Earlier context: the 10-06 removal of living stills and the 10-22 decision to bring back
procedural scenes ([agsl-effects-and-touch-latency-2026-10-22.md](agsl-effects-and-touch-latency-2026-10-22.md),
Open questions). The debug prototype behind `debug.tl.scene` (`chrome/scene/`: `SceneLayerView`,
`SceneController`, `SceneLowRes`, `SceneFramePolicy`) is the starting point.

## Problem

Termux-launcher's wallpaper is a photo. The developer wants moving wallpapers that are pure AGSL
shaders, drawn by the launcher itself. Glass keeps working over them, and people who use
termux-launcher only as a terminal app get them too. Animating the user's own photo stays parked.

## Decisions (developer, 2026-11-05)

### The feature
1. **Name and place.** A new kind, **Live**, next to **Photo** in the existing Wallpaper picker
   (Appearance). Each scene's knobs go on its own detail page. Hidden below Android 13 (AGSL is
   API 33; minSdk is 26).
2. **Catalogue.** v1 ships **3 first-party scenes**, the prototype's Contour among them. Each has
   knobs for colour, speed and density. A scene is offered only after a pong check of heat and
   frame time. Loading users' own `.agsl` files comes later; the input contract below is shaped so
   it can take them.
3. **Pure procedural.** Scenes read no photo and no texture. The only inputs are the shared ones
   below.

### Drawing
4. **Own surface, at the very back.** A full-window surface behind every termux-launcher view,
   drawn on its own thread (the prototype's `SceneLayerView` design). No `RenderEffect` or Paint
   shader inside the window for the scene.
5. **Pacing.** 30 fps by default, with a 15 / 30 / 60 knob. The scene skips frames itself;
   `Surface.setFrameRate` is only a hint.
6. **Pause rules.** The scene pauses onto the still when it is fully covered, when the screen is
   off, when battery saver is on, or when the phone reports thermal throttling.
7. **Resume.** It always restarts from the still's frame (scene time = the still's time). The still
   and the live scene therefore always match with no jump. Scenes are designed so a restart doesn't
   feel repetitive (slow drift from the start frame).
8. **Glass.** Glass reads the 1/8-size blurred second pass (`SceneLowRes`), refreshed at **half the
   scene rate** (15 fps at 30). The slight lag is accepted; blur hides it.

### Reactions (shared input contract)
9. Every scene gets the same uniforms, declared by a shared prelude prepended to each scene. It has
   to be a prelude because `RuntimeShader` throws when the app sets an undeclared uniform. The
   uniforms:
   - time
   - resolution
   - page offset (for parallax; `setWallpaperOffsets` only moves the system layer)
   - last touch point and its age
   - up to **8 open-window rects**; past 8, the smallest are dropped
   - keyboard height
10. A per-scene **"uses windows"** flag. When it is off, the launcher does not track window rects
    for that scene.

### The system wallpaper
11. **One still for Home and Lock.** When a scene is picked or a knob changes, the launcher renders
    one frame and sets it through `WallpaperManager` (the route `ManagedWallpaper` already uses for
    photos). The still shows in recents, app launch and on the lock screen. It is not re-rendered
    in between.
12. **Lock screen = still only.** No `WallpaperService`; no live lock screen.
13. **Photos are restored.** The previous photo choice (Home and Lock slots) is remembered and comes
    back when the user switches to Photo or turns Live off.
14. **Terminal-app mode.** When termux-launcher is not the default home app, the scene plays inside
    the app only. The phone's wallpaper is untouched unless the user taps **"Also set as phone
    wallpaper"**.

## Not in scope
- Live lock screen / system `WallpaperService`.
- Animating the user's photo (depth, segmentation, moments): parked since 10-06.
- User `.agsl` files.
- Reactions to terminal output or other inputs beyond the contract above.
- Material You colours: the system derives them from the still. Nothing extra is planned.

## Facts to settle (tests, not decisions)
Ranked by how much they block the build; methods are in the wallpapers research note.
1. **Layer order:** does the scene surface stay above the ROM's wallpaper layer in every state
   (recents, app launch, rotation, split screen, flag set and cleared)? Pong checklist plus
   `dumpsys SurfaceFlinger`.
2. **Heat and battery:** 20 minutes on pong at 30, 15 and 0 fps, with glass at half rate. Record
   thermal status, batterystats and GPU time.
3. **Glass stall:** why does a hardware bitmap stall the UI thread when glass reads it? This blocks
   dropping `SceneSoftPool`. Perfetto trace.
4. **Refresh rate:** does a 30 fps `setFrameRate` on the scene surface lower the display rate beside
   a 90 Hz window? Pong plus `dumpsys display`.
5. **Uniform limit:** does a 16-vec4 rect array stay under the uniform limit, and what is the failure
   mode? Test shader.
6. **Still export:** render the still with `HardwareRenderer` into a bitmap, or on a software
   `Canvas` if `RuntimeShader` works there. Ten-line test.

## Rough waves (to be split into briefs when the build starts)
- **A.** Promote the prototype: the input contract and prelude, pause and resume rules, pacing knob,
  glass at half rate. Settle facts 1, 3, 4 and 5 first.
- **B.** Still export and the system wallpaper (Home and Lock), photo restore, the terminal-app-mode
  opt-in.
- **C.** Picker: the Live kind, scene detail pages, hidden below API 33. Settings pages are
  fragile: keep the footprint small.
- **D.** Two more scenes; pong heat and frame-time check per scene (fact 2).
