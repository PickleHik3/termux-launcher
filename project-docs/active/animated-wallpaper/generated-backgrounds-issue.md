# Generated animated backgrounds, with moments and Material colour

Published as [#41](https://github.com/PickleHik3/termux-launcher/issues/41) on 2026-09-30 with the label
`ready-for-agent`. The issue is authoritative; this copy is the in-repo record. It builds on
`project-docs/active/animated-wallpaper/SPEC.md` (the pipeline: a live shared frame, the backdrop,
the phase 0 measurement) and on `kitty-custom-shaders-research.md` in the same folder. Where this
issue and SPEC.md disagree, this issue wins, and SPEC.md is amended to match.

## Problem Statement

A still photo is the only wallpaper the launcher can show behind its glass. A system live wallpaper
is no answer: under a `WallpaperService` the glass loses its blur (`WallpaperPicture.NO_STILL`).
kitty 0.49 now ships animated backgrounds (aurora, matrix rain, fireworks, water) and effects that
react to what the user does. People who use kitty on the desktop see those and want the same on
the phone. But kitty's shaders are Slang post-processes over a desktop terminal window. They don't
run on Android, several can't be copied under their licences, and they know nothing about a phone
screen.

The launcher also has moments that land flat today:
- Unlocking the phone just shows the Home place.
- Locking from the A–Z index (the double tap) cuts straight to black.
- Opening or splitting a pane animates the panes, but nothing behind them responds.

And a generated background has to look like it belongs. It must match the Material palette the
status bar, dock, keyboard and A–Z index already use, and it must agree with the still that the
system shows on the lock screen and in other apps.

## Solution

The launcher ships its own **generated backgrounds**: original AGSL wallpapers written for a
portrait, OLED phone screen. They draw behind everything and feed the live shared frame (SPEC.md
§3), so every glass surface (status bar, dock, keyboard, A–Z index, pane slabs, corner tabs) shows
them blurred and in register.

Each generated background has:
- **Ambient motion**: slow, low-contrast, looping, and dark-dominant, so the OLED panel stays
  mostly off.
- **A rest pose**: its picture with the motion at zero. The rest pose is the still given to the
  system, so the lock screen and other apps show exactly what the launcher shows when it settles.
- **Moments**: short effects inside the wallpaper, set off by launcher events:
  - **unlock**: the rest pose blooms into motion;
  - **lock from the A–Z index**: the motion settles into the rest pose and dims, then the screen
    locks, so the keyguard picks up the same picture;
  - **pane open or split**: a wave from the new pane's seam;
  - **pane close**: the wave folds inward;
  - **page change on the pane wall**: the flow is nudged in the slide's direction;
  - **touch on bare wallpaper on the Home place**: a ripple (the idea of kitty's `pond-ripple`);
  - **terminal bell**: a glow behind the ringing pane (kitty's bell region).
- **A palette**: either **Material** (four colours from the launcher's Material palette, taken when
  the background is chosen) or the background's own fixed palette.

A generated background is picked from the Animated row in the wallpaper picker. The row is shown only
where Fancier Glass is on and the device runs API 34 or later. Everywhere else, and whenever the
launcher is paused, the still shows and nothing changes from today.

Here is how much of kitty's custom-shader set this covers. Everything carried over is rewritten, and
nothing is copied (research §4.3):

| kitty shader | Here | Why |
|---|---|---|
| `northern-lights` | Rewritten as **Aurora** | A single-pass generator; kitty's ray-march and `persist` pass are dropped |
| `inside-the-matrix` | Rewritten as **Rain** (glyph-cell rain, procedural cells, no font) | Shadertoy provenance with no licence, so the idea only |
| `water` | Rewritten as **Tide**, plus the touch ripple moment | kitty's version only distorts the finished frame; here it becomes a generator |
| `fireworks` | Not carried over | Contains CC BY-NC-SA code; its look fights the ink colour |
| `pond-ripple` | Rewritten as the touch ripple moment | |
| `tab-change` | Rewritten as the page change moment | |
| bell region (`bell_window_geometry`) | Rewritten as the bell moment | |
| `spotlight` | Not carried over | A phone has no hover |
| `dim-inactive-windows`, `focus-highlight` | Not carried over | Post-processes over the window; the active pane already shows through focus growth and the idle ring |
| `crt`, `crt-blue`, `tft` | Not carried over | Full-window post-process, and `crt` contains CC BY-NC-SA code |
| `cursor-trail-blaze` / `lightning` / `motion-blur` | Not here | Belongs to the backlog row "AGSL glass experiment: cursor trail in its test matrix" |

A fourth built-in, **Mesh**, is our own: a four-colour Material gradient that drifts. It is SPEC.md's
Gradient flow under a shorter name.

## User Stories

1. As a launcher user, I want to pick an animated background from the wallpaper picker, so that my
   home screen feels alive without installing a system live wallpaper.
2. As a launcher user, I want the Animated row to show a still thumbnail for each background, so that
   I can choose without the picker draining my battery.
3. As a launcher user, I want the glass on the status bar, dock, keyboard, A–Z index and pane slabs
   to show the moving background, blurred, so that the glass stays true to what is behind it.
4. As a launcher user, I want the background and the glass to stay in register while I slide
   pages, drag a border or raise the keyboard, so that the glass never shows a different picture
   from the backdrop.
5. As a launcher user, I want a background that follows my Material palette, so that it matches the
   status bar, dock and keyboard colours.
6. As a launcher user who set my own colour scheme in `colors.properties`, I want the Material
   palette choice to follow my scheme, so that my custom colours reach the background too.
7. As a launcher user, I want to choose a background's own fixed palette instead, so that I can have
   an aurora in its natural greens even when my palette is blue.
8. As a launcher user, I want the lock screen and other apps to show the same picture the launcher
   settles on, so that locking and switching apps don't make the wallpaper jump.
9. As a launcher user, I want the background to bloom from its rest pose into motion when I unlock
   the phone onto the launcher, so that unlocking feels like arriving.
10. As a launcher user, I want a double tap on the A–Z index to settle the background into its rest
    pose and dim it before the screen locks, so that locking feels deliberate and the keyguard shows
    the same picture.
11. As a launcher user, I want that lock animation to be short (under about 400 ms), so that locking
    is never slower in any way that matters.
12. As a launcher user, I want a second double tap during the lock animation to lock at once, so that
    I am never kept waiting.
13. As a launcher user with reduced motion on, I want the lock to happen at once with no animation,
    so that the setting is respected.
14. As a launcher user, I want opening or splitting a pane to send a soft wave out from the new pane's
    seam, so that the background answers the layout change.
15. As a launcher user, I want closing a pane to fold that wave inward, so that open and close read as
    opposites.
16. As a launcher user, I want a page change on the pane wall to nudge the background's flow in the
    slide's direction, so that the slide has weight.
17. As a launcher user, I want a tap on bare wallpaper on the Home place to leave a ripple, so that the
    background feels touchable.
18. As a launcher user, I want a tap on a surface (dock, tile, keyboard) not to ripple the background,
    so that ordinary use stays calm.
19. As a terminal user, I want a bell in a pane to glow faintly behind that pane, so that I notice
    which pane rang even when I'm looking elsewhere.
20. As a launcher user, I want a moment to show through the glass too, so that a wave passing under
    the dock is seen in the dock's blur.
21. As a launcher user, I want at most two moments to play at once, the newest replacing the oldest,
    so that a burst of events never turns into noise.
22. As a launcher user, I want the moments to stay dim enough that the text and icons on the glass keep
    their contrast, so that nothing becomes hard to read mid-moment.
23. As a launcher user on an OLED phone, I want the backgrounds to be dark-dominant, so that the panel
    stays mostly off and the battery cost stays small.
24. As a launcher user, I want the animation to pause when battery saver is on, so that saving battery
    means saving battery.
25. As a launcher user, I want the animation to pause when the phone is warm (thermal moderate or
    worse) and drop to 15 fps when it is only slightly warm or the battery is low, so that the
    background never makes the phone hot.
26. As a launcher user in Lazy mode, I want the background paused, so that Lazy mode stays calm.
27. As a launcher user, I want the animation to stop when the launcher is not visible or the screen is
    off, so that it costs nothing then.
28. As a launcher user, I want a paused background to keep showing its current frame, not jump, so
    that a pause is invisible.
29. As a launcher user, I want moments not to play while the background is paused, so that battery
    saver and Lazy mode also cover moments.
30. As a launcher user on Android 13 or earlier, or with Fancier Glass off, I want the Animated row
    hidden and my wallpaper unchanged, so that nothing half-works on my device.
31. As a launcher user who turns Fancier Glass off while a generated background is chosen, I want the
    background to stop on its still, and to start again when I turn Fancier Glass back on, so that
    the toggle is the one switch.
32. As a launcher user who restores a backup onto an older phone, I want the stored background to show
    as its still, so that the restore never fails.
33. As a launcher user, I want choosing a photo to replace the generated background cleanly, so that
    going back to a photo is one step.
34. As a launcher user, I want a wallpaper set from another app to stop the animation on its own, so
    that the launcher never draws over a wallpaper I chose elsewhere.
35. As a launcher user, I want rotation to keep the background running at the new size, so that
    landscape works.
36. As a launcher user, I want the background to resume on the next frame when I come back to the
    launcher, so that there is no warm-up flash.
37. As a power user, I want `launcherctl wallpaper set --builtin aurora`, so that I
    can script my setup.
38. As a power user, I want `launcherctl wallpaper list-builtins` to list the backgrounds and their
    palettes, so that scripts can discover them.
39. As a power user, I want `launcherctl wallpaper` to report whether a background is animated and
    playing, and why not when it isn't (api, fancier_glass_off, paused), so that I can debug my
    setup.
40. As a power user who uses kitty on the desktop, I want the docs to say which kitty shaders have a
    counterpart here and which don't, so that I know what to expect.
41. As a power user, I want a kitty.conf synced from my desktop not to change the phone's wallpaper,
    so that desktop settings never leak into the phone (`custom_shaders` is ignored).
42. As a developer, I want every rule about when to play, how fast, which moment and which colours in
    one pure class, so that the rules are unit-tested without a device.
43. As a developer, I want a hidden kill switch and a self-check that ignores the post-wake warm-up,
    steps a slow renderer down (30 to 15 fps, a cheaper resolution, then 10 fps) and kills it only
    when the lowest tier still fails, with a new chance at the next screen-on, unlock or start
    (at most 3 kills per process), so that a bad GPU driver shows a still instead of stutter and a
    busy unlock does not cost the session.
44. As a developer, I want the renderer released 30 s after the launcher stops and on low memory, so
    that a background costs no memory in the background.
45. As a maintainer, I want every built-in written from scratch, so that `THIRD_PARTY_NOTICES.md` needs
    no new licence and nothing CC BY-NC-SA ships.

## Implementation Decisions

**Gate.**
- A generated background plays only when all of these hold:
  - Fancier Glass is **active** (`FancierGlassPolicy.active`);
  - the SDK is 34 or later;
  - a generated background id is stored;
  - the managed picture is on screen;
  - the backdrop is self-drawn.
- This **amends SPEC.md §13 Q2** (settled earlier the same day as "plays with Fancier Glass on or
  off"). The developer's newer decision: gate on the Fancier Glass setting. Fancier Glass's own floor
  is API 33, so the renderer's API 34 floor stays a separate condition. On an API 33 device Fancier
  Glass works and the Animated row stays hidden.
- The Animated row in the picker shows exactly when the gate's device conditions hold (Fancier
  Glass active, SDK 34+).

**The pipeline stays SPEC.md's.** It keeps the live shared frame per radius (§3), the backdrop
drawing the shader directly (§3.5), the `HardwareBufferRenderer` rings, `LiveWallpaperFrames`, the
draw-time pick in the three readers, and the phase 0 go/no-go measurement (§10). None of that is
re-decided here.

**WallpaperDirector (the one seam; replaces SPEC.md's `AnimatedWallpaperPolicy`).** It is a pure
Java class with no Android types, built like `FancierGlassPolicy` and `WallSlideClock`.
- **Inputs**, set whenever they change:
  - the device and gate conditions: the gate above, launcher visible, screen on, battery saver,
    thermal band (none / light / moderate+), battery low and discharging, Lazy mode, reduced
    motion, the kill switch, and whether the renderer is healthy;
  - the chosen background's rules (loop period, moment durations);
  - the palette: the four ARGB colours captured at set time.
- **Events**:
  - `unlock`;
  - `lockRequested` (returns the delay before the lock runs);
  - `lockNow` (a second double tap);
  - `paneOpened(rect)` and `paneClosed(rect)`, in frame pixels;
  - `pageChanged(direction)`;
  - `touch(point)`;
  - `bell(rect)`.
- **Output per frame** (`frame(frameTimeNanos)`):
  - fps (0 means paused);
  - shader time in seconds, which advances only while playing and is kept across pauses;
  - energy, 0..1: how far the picture is from its rest pose;
  - dim, 0..1;
  - the palette;
  - up to two moment slots, each a kind, a rect or point, and progress 0..1;
  - whether a pending lock is now due.
- The host (`TermuxActivity` and `AnimatedWallpaperClock`) only feeds inputs and writes outputs to
  uniforms. It holds no rules.

**Pause rules.** SPEC.md §5 carries over unchanged: 30 fps; 15 under light thermal or low battery;
paused for battery saver, moderate thermal, Lazy mode, reduced motion, not visible, screen off and
the kill switch. Also:
- While paused, events are dropped, but a lock request still returns a delay of 0.
- While not visible, `unlock` is queued and plays on the first visible frame.

**Rest pose and energy.**
- Every built-in has a uniform `energy`. At energy 0 its output is the rest pose, independent of
  time. The motion is scaled by energy, and the picture is not cross-faded.
- The still given to the system (`AnimatedWallpaperStill`, SPEC.md §7.1) is rendered at energy 0. It
  replaces SPEC.md's "frame t=0". This is what makes the lock screen, the other apps and the
  launcher agree.
- Energy is 1 during normal play.
- **Lock moment:** energy eases to 0 and dim to the lock dim over the settle time (≤ 400 ms), then
  `lockDue` fires and the existing `lockScreenFromAzDoubleTap` method (Shizuku or accessibility)
  runs. Reduced motion or a pause gives a delay of 0.
- **Unlock moment:** energy eases 0 → 1 over about 900 ms, starting from the rest pose.

**Unlock signal (new).**
- Today the app has no screen-on or unlock handling.
- Add a receiver for `ACTION_USER_PRESENT` and `ACTION_SCREEN_OFF`. Register it at runtime while the
  activity lives, not in the manifest.
- A `USER_PRESENT` that follows a `SCREEN_OFF` sends `unlock` to the Director. It plays only if the
  launcher is the visible activity. Screen off also counts as not visible for the Director.
- `onStart` and `onResume` after a screen-off also send `unlock` (once per screen-off, whichever
  signal comes first), because some ROMs and face or smart unlock never send `USER_PRESENT`.

**Moment hooks.** These are existing callbacks, with no new event bus:

| Moment | Hook |
|---|---|
| Pane open or split | `TerminalPaneController`, where the split reveal starts |
| Pane close | Where the pane is removed |
| Page change | `PaneWallController.Host.onWallPageSettled`/`Changed` |
| Touch on bare wallpaper | Taps on the Home place that no surface consumed |
| Bell | The session's bell callback, with the pane's rect |

All rects are converted to shared-frame pixels with the parallax applied.

**Moment rendering.**
- A moment is drawn inside the wallpaper shader through the two moment slots' uniforms. It adds no
  view, no layer and no extra pass, so it reaches the glass through the shared frame for free.
- A moment's luminance change is capped (about +15 % L), so `ChromeInk` can keep sampling the still.

**Built-ins** (each an original AGSL generator with the uniforms time, energy, dim, palette[4] and
the two moment slots):
- **Aurora**: slow ribbons.
- **Mesh**: a drifting four-colour gradient.
- **Tide**: soft waves; the ripple is native to it.
- **Rain**: sparse glyph-cell rain, low contrast.
- Every built-in must meet SPEC.md §6.1's target of about 60 ALU ops per pixel with no texture reads,
  and keep its loop period at 60 s or more.
- Every built-in is dark-dominant. Its brightest pixel stays under a mid tone, so that it suits OLED
  and keeps the ink valid.

**Material palette.**
- Material mode takes four roles from the launcher's resolved Material palette. That palette is
  already the output of `LauncherSchemeTheme` and so includes any `colors.properties` override. The
  roles are primary container, tertiary container, surface-dim and secondary.
- They are captured when the background is chosen, and again when the user changes the launcher
  colour scheme. They are then stored with the id (`managed_wallpaper_animated_palette`), and the
  still is re-rendered and re-applied.
- The palette is **never** re-captured from `OnColorsChangedListener`. Setting our still makes the
  system re-derive its colours from the still, and following that would loop.
- Fixed mode uses the palette the background ships with.
- This settles SPEC.md §13 Q7.

**Surfaces.**
- The status bar, dock, keyboard, A–Z index, pane slabs and corner tabs already sample the shared
  frame, so they need no change beyond SPEC.md §3.3's draw-time pick.
- The keyboard key colours (`KeyboardMaterialPolicy`), the system bar colours and the glass tint and
  rim stay driven by the Material palette as today. Material-mode backgrounds match them by
  construction.

**Choosing.**
- The picker sheet and the `launcherctl` routes are SPEC.md §7, extended:
  - (superseded: the Material / Own toggle is gone; the wallpaper always uses its own palette);
  - `POST /v1/wallpaper` accepts `"palette"` for compatibility and ignores it;
  - `GET /v1/wallpaper/builtins` lists `palettes`;
  - `GET /v1/wallpaper` adds `"reason"` when a background is not playing, one of `api`,
    `fancier_glass_off`, `paused` or `killed`.
  - `GET /v1/wallpaper` also reports `tier` (0 is full rate and resolution) and `kills` (self-check
    kills this process).
- `custom_shaders` in kitty.conf is not read.

**Records.**
- ADR 0006 (SPEC.md §10 1c) also records the rest-pose rule and the Fancier Glass gate.
- SPEC.md §13 Q2 and Q7 are amended.
- The user docs gain a table mapping kitty shaders to what exists here (the table above).

## Testing Decisions

- **A good test here** drives `WallpaperDirector` through inputs, events and frame times, and asserts
  only on what it outputs per frame (fps, time, energy, dim, the moment slots, `lockDue`, the lock
  delay). It never asserts on internal fields or animator state.
- **What is tested (unit, pure JVM, no Robolectric runner):** `WallpaperDirector`.
  - **Gate:** every gate condition off in turn gives fps 0 and no moments.
  - **Fancier Glass:** turned off mid-play gives a pause at the current frame; turned back on
    resumes with time continuous.
  - **Pause table:** every row of SPEC.md §5, including 15 fps under light thermal and under low
    battery while discharging.
  - **Time:** it advances only while playing and is continuous across pauses.
  - **Energy:** 1 during play; the lock eases it to 0 by the settle time, then `lockDue` fires once.
  - **Lock:** the delay is 0 under reduced motion or a pause; `lockNow` during the settle makes it due
    on the next frame.
  - **Unlock:** it eases energy up from 0; an unlock while not visible is queued and plays on the
    first visible frame.
  - **Slots:** at most two moments; a third replaces the oldest; progress runs 0 → 1 over the
    moment's duration and then the slot frees.
  - **While paused:** events are dropped.
  - **Palette:** passes through unchanged. Re-capturing is a host decision, and the Director never
    changes the palette on its own.
- **Prior art:** `chrome/FancierGlassPolicyTest` (pure gate tables), `wall/WallSlideClockTest` and
  `wall/SettleSpringTest` (pure frame-time stepping), `terminal/PaneMotionMathTest`.
- **Not unit-tested:** the AGSL built-ins, the renderer, the unlock receiver or the moment hooks.
  Robolectric runs at sdk P, which has no `RuntimeShader`. They are verified on pong per SPEC.md §9
  and §11, plus:
  - each moment on each place;
  - the lock animation with both lock methods (Shizuku, accessibility);
  - that the keyguard picture matches the settled launcher picture;
  - unlock onto the launcher and onto another app (no moment);
  - a Material palette change;
  - the HTC (API 28): the Animated row is hidden.

## Out of Scope

- User-supplied shaders, shader packs, and honouring `custom_shaders` from kitty.conf.
- Copying any kitty shader code, and the CC BY-NC-SA material (`fireworks`, `crt`).
- kitty's post-process effects over the window: `crt`/`tft`, `dim-inactive-windows`,
  `focus-highlight`, `spotlight`.
- A shader-drawn cursor trail. It belongs to the backlog row for the AGSL glass experiment.
- Animating the system lock screen or keyguard itself, and a system `WallpaperService`. The keyguard
  shows the still, the rest pose. (Superseded for the lock screen on 2026-10-03 by
  [`lock-live-wallpaper.md`](lock-live-wallpaper.md): a lock-only live wallpaper, API 34+.)
- Devices below API 34, and any OpenGL ES path to reach them.
- Video and GIF backgrounds (SPEC.md phase 2).
- Pausing on user idle, as kitty does. SPEC.md §13 Q4 settled on "play whenever visible".
- New settings pages. Everything is chosen in the picker, and only the kill switch is hidden.

## Further Notes

- Phase 0 (SPEC.md §10) still decides whether any of this is built. The moments add no render
  passes, so they don't change the measurement, but the lock and unlock moments need the renderer
  warm at the moment they start.
- The Director returns the lock delay so that the lock stays owned by the existing lock methods. If
  the renderer fails mid-settle, the host locks when the delay runs out regardless.
- Rain is the riskiest built-in for ink contrast. If it can't stay under the luminance cap it is
  dropped, and the rest ship.
- Mesh in Material mode is the closest to "the wallpaper is the palette". It is likely the default
  tile.
- Research: `project-docs/active/animated-wallpaper/kitty-custom-shaders-research.md`.
