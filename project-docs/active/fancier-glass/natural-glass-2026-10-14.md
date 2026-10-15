# Natural glass — working note (2026-10-14, overnight)

Status: in progress on branch `feat/glass-natural` (worktree `.claude/worktrees/glass-natural`, based on
`pre` at a35f6ef93). The developer asked, before going to sleep: natural, beautiful glass that uses the
AGSL program; new edge and refraction effects wired into the shipped Looks; a few high-value extra
effects and touch feedback on glass with little movement; production quality (what Apple or Samsung
would ship); verify on the emulator (never pong); keep these docs current; file a PR for the work and a
PR for every bug or design inconsistency met on the way.

## What changed and why

Read with SPEC.md §4 (the five Edge knobs). The program is still `chrome/GlassRefraction.AGSL`, one
compile per surface, uniforms written only when something moved; idle cost stays zero.

| Effect | What it does | Knob | Where |
|---|---|---|---|
| Lit rim | The hairline rim is lit from above and a little to the left (`kLightUp` = (-0.33, -0.944)) with a weaker light answering from below. Top edge bright, bottom softer (0.6×), sides faint (floor 0.40 of Edge light), never gone, since the hairline is what reads as the edge. Replaces the one-brightness hairline, which read as a stroke. | Edge light (same knob, new distribution) | AGSL `rimK` |
| Thickness | Across the bend band the picture brightens toward the lit rim (up to +12%) and dims toward the shaded one (up to −6%), multiplicative, fading to nothing at the band's inner edge. The bend alone looked like a smear; this is the bevel's shading. | None: scales with Bend, full from 8 dp (`Look.thickness`) | AGSL `thick`, uniform `uThick` |
| Edge clarity | A second input, `sharp`: a clearer frame of the same wallpaper rect. Inside the band the bent edge shows it in place of the frost, on x² (near the frost over the inner half, the knob's value at the rim). One extra sample, only in the band, only when the Look asks. Bound to the content itself until a surface hands one in; under a RenderEffect (the dock) the term stays 0, so an unbound shader is never read. | Clarity, `fancier_glass_clarity`, 0–100, default 0 (sixth Edge slider) | `Program.setClarityInput`, `SharedFrameDrawable.setClearFrame`, clear frame = cache radius `CLARITY_RADIUS_DP` (2 dp) |
| Touch light | A finger on the glass: the rim nearest it catches 1.5× more light, a faint glow (0.045) scatters under the fingertip, the picture is drawn a tenth of the way toward it (`TOUCH_LENS`). Rises in 110 ms, falls in 240 ms, follows the finger. Light, not motion. | None (off under reduced motion, lazy mode, battery saver) | `Program.setTouch`, `SharedFrameDrawable.setTouch`, `chrome/GlassTouchLight` (driver), `GlassPress` |

Kept as they were: Shine (the diagonal bevel light) and Dispersion stay 0 in every Look; the key lens;
every seam rule; the plain draw below API 33.

New Look recipes (bend, edge width, edge light, shine, dispersion, clarity):
Clear 6/14/40/0/0/70 · Mist 9/20/30/0/0/60 · Tint 6/14/36/0/0/55 · Solid unchanged, clarity 0.
Preset format version 4; a look stored earlier reads clarity 0.

## Progress

- [x] Shader, `Look.clarityPercent`, pref key, `Program.setClarityInput/setTouch`, unit + instrumentation tests (fc28efa6f)
- [x] `SharedFrameDrawable.setClearFrame/setTouch` (48c80eef7)
- [x] Clear-frame plumbing (frost painter, pane slab view), presets v4, Clarity slider (90c2c3a70)
- [x] Touch light driver, GlassPress overload, terminal sheet card rows (6a69653d0)
- [x] Clear frame on the keyboard host, the under-keyboard card and the pane slabs; dock touch light with a stepped fade (b7df86c02)
- [x] Emulator (tl16, x86_64 debug): Clear, Mist and Tint apply without a crash; the Edge group shows six sliders with the Look's values; the corner bend and hairline are clean; clarity shows at the keyboard's top rim under Mist on a dark wallpaper (faint at 45%); the dock touch light is a soft glow behind the pressed icon that fades after release, the far side of the dock unchanged (frames in the session scratchpad, `fr-dock3-sheet.png`)
- [ ] Not seen on the emulator: the touch light on a sheet card row (no centred card was reached in the time; the terminal action sheet's Kill process confirm is one), clarity on the panes under Floating, the Widgets and Display pages, RTL and large font
- [x] Instrumentation test on the emulator: `GlassRefractionAgslInstrumentationTest`, 8 tests, 0 failures (tl16)
- [x] A fresh reviewer (no code) on the crops: the rim still read as the surfaces' own stroke and clarity as not visible at Clear 24 / Mist 18 / Mist clarity 45; retuned to Clear 40, Tint 36, Mist 30 and Mist clarity 60, and the clarity profile to a smoothstep across the band. The dock "flash" it reported was Chrome launching at the end of the recorded tap, not the light.
- [x] SPEC.md §4.1
- [ ] ADR amending 0005; pong look by the developer

## Found on the way

- Applying a second wallpaper reset the Look's blur and opacity to the install's first values
  (Mist read as blur 4 / opacity 10 with Mist's tint). Cause: `ChromePolicy.applyWallpaperModePreferences(prefs, true)`
  restored the "wallpaper enabled" snapshot although the mode was already on. Fixed on
  `fix/wallpaper-reapply-keeps-look`, PR #74.

## Open questions for the developer

0. The emulator renders on the CPU and the test wallpaper is synthetic: the rim light, the
   thickness and the clarity are tuned to read there, which may be too much or too little on pong.
   The three knobs to move are the Looks' Edge light and Clarity (`SurfacePresets`) and the band
   profile in the shader (`x * x * (3.0 - 2.0 * x)`).

1. The lit rim changes the resting look of every Look (and the dock in the default mode): sides are
   fainter than before at the same Edge light. If that reads as too faint on pong, raise the floor
   (`rimK` 0.40) or the Looks' Edge light.
2. The thickness shading is tied to Bend rather than a seventh knob. A knob would need a seventh Edge
   slider slot.
3. A clear frame at 2 dp is held at full size (about 15.5 MB for a 1.5×-wide frame on pong). If that
   is too much next to the radii in use, store it at half size (the renderer's downsample factor).

## How to pick this up

```
cd .claude/worktrees/glass-natural && git log --oneline pre..HEAD
./gradlew -q :app:testDebugUnitTest --tests 'com.termux.app.chrome.*' --tests 'com.termux.app.surfaces.*'
```
Emulator: `ad -s emu lease acquire`, `ad -s emu emu start`, install
`app/build/outputs/apk/debug/termux-app_apt-android-7-debug_x86_64.apk`, set a wallpaper from the
launcher (device-map.md), Appearance editor → Look → Edge.
