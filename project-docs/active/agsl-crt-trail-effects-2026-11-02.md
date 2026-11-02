# CRT realism, bezel depth, trail glow and event effects (2026-11-02)

Status: spec, settled in a grilling round on 2026-11-02 (19 questions). Wave 1 in progress.

Builds on [`agsl-effects-and-touch-latency-2026-10-22.md`](agsl-effects-and-touch-latency-2026-10-22.md)
and amends two of its decisions (see "Amendments"). Everything here is built-in AGSL picked in
settings (its decision 4); nothing reads user shader files.

Terms: **Event effect**, **Ambient effect** as in [`docs/GLOSSARY.md`](../../docs/GLOSSARY.md).
**Screen**: the rect inside a pane's bezel the CRT draws on (`RetroUniforms.screen*`). **Chrome**:
the Workspace bars the effect also covers (`chrome/ChromeRetroEffect.java`), drawn flat.

## What exists today

- `terminal/PaneRetroEffect.java`: one AGSL program per family. `CRT_AGSL` (:110–147) does barrel
  bend `CRT_BEND = 0.04` (:29), vignette `PANE_VIGNETTE = 0.08` (:32), a 4-tap glow at
  `1.5 * uDensity` mixed at 0.35 (:130–135), phosphor tint, scanlines `mix(0.70, 1.0, cos)` at
  `RetroUniforms.PERIOD_DP = 3` dp (:141). `TFT_AGSL` (:149–159) is a grid. Uniforms are declared
  once in `HEAD` (:77–100) and written by `RetroEffectBinder` from `RetroUniforms`.
- The chrome runs the same program with `uBend = 0` and no vignette (`ChromeRetroEffect` javadoc).
- `terminal/PaneBezel.java`: `SIDE_DP = 12`, `TOP_BOTTOM_DP = 16`, `SCREEN_RADIUS_DP = 10` (:23–27);
  `fill()` (:111–113) is flat: transparent while the pane's glass shows through, else the opaque
  surface colour. `terminal/PaneContentFrame.java` draws the fill and a 1 dp line
  (`PaneRim.STOCK_STROKE_DP`) around the screen (about :555–570).
- Cursor trail: `terminal-view/.../KittyCursorTrail.java` is a faithful port of kitty's
  `cursor_trail.c` (per-corner decay :278–341, opacity ramp :343–349). Drawn by
  `terminal/PaneMotionOverlayView.java` as a flat quad in the cursor colour (`drawQuad` :486–500).
  Defaults 10 ms, decay 0.10/0.40 s, threshold 2 cells (`TerminalFontConfig.java:823–892`,
  `TerminalPaneController.java:675–678, 726–745`). Six styles in `CursorTrailStyle.java`.
- Setting: `terminal_retro_effect` list (None, CRT, CRT green, CRT amber, CRT Material colours,
  TFT) in `performance_preferences.xml:55–61` and `terminal_text_preferences.xml:24`, shown only
  when `PaneRetroEffect.available()` (API 33+).

## Decisions

1. **Deliverable.** This spec, then prototype builds shot on the emulator onto a before/after page in
   `~/reports`; pong only after the developer has looked at the page.
2. **Scope.** New static parts go on panes and on the chrome (keeping the one-display rule); every
   animated part (glitch, flicker, events) is pane-only. Home chrome stays plain, as today.
3. **Headache guard.** Scanlines and the triad mask are static; nothing rolls. No flicker unless
   Idle animations is on, and then within the 10-22 caps (a few percent, under 3 flash-like events a
   second). Glitches are events, under 400 ms, and never fire while the user is typing or scrolling
   (a key commit or a scroll within the last 1.5 s suppresses them, as scenes already hold still).
   Every animated part is off while Android's "Remove animations" is on
   (`ValueAnimator.areAnimatorsEnabled()` false).
4. **Battery.** Static passes ride the pane's own invalidates; a pane at rest costs nothing. Only
   events and opted-in ambient parts drive frames, and the trail only while a trail is live.
5. **Richer static CRT**, replacing the current one in place (no "classic" preset):
   - *Chromatic aberration*: red and blue sampled at offsets growing from 0 at the screen centre to
     about 0.6 px at the edges (in the bent space, so it follows the glass). On the chrome a constant
     0.3 px split.
   - *Phosphor triad mask*: vertical RGB stripes at the 3 dp period, about 10% depth, on panes and
     chrome alike, phase-locked to `uOrigin` like the scanlines so stripes run on across surfaces.
   - *Glass recess and sheen*, pane-only: an inner shadow inside the screen's edge (about 6 dp wide,
     darkest at the lip, about 25% at the edge, zero at 6 dp) and one faint diagonal specular band
     (under 6% white, top-left to bottom-right, fixed, no motion).
   - Existing bend, vignette, glow, tint and scanline depth are unchanged.
6. **Bezel**: `SIDE_DP` 12 → 8, `TOP_BOTTOM_DP` 16 → 10. Depth from a multiplicative shade overlay
   drawn after the fill (so it works over glass and over the solid surface colour alike): the bezel's
   top edge lit (about +8% white), bottom edge darker (about −18% black), linear top to bottom; the
   inner lip's shadow onto the screen is decision 5's recess. The 1 dp line stays. Bend clearance
   (`PaneBezel.screen`, `bendClearancePx`) is recomputed from the new sizes, nothing else changes.
7. **Glitch, events only** (each bounded; all pane-only; all honour decision 3):
   - *Bell*: one horizontal tear band (a 3–6 row slice shifted sideways a few px) plus a brief red/blue
     split, under 150 ms.
   - *Pane opens*: power-on sweep, about 400 ms (a bright horizontal line widening to the screen, with
     the picture fading in).
   - *Pane closes*: collapse to a bright line, about 250 ms.
   - *Pane takes focus*: faint degauss wobble (a damped sideways shear of a few px), under 300 ms.
   - *Pane's process dies*: a burst of snow for about 300 ms before the dead-pane card.
   - *Ambient glitch*: a random tear every 2–5 minutes, under 200 ms, only under Idle animations.
8. **Unfocused panes dim**: with more than one pane, unfocused ones lose about 10% brightness and 20%
   saturation inside the CRT pass. Static (one uniform per pane), no extra frames.
9. **Cursor trail**: an AGSL glow halo under kitty's quad, about one cell wide, additive in the trail
   colour, brightest at the cursor end and fading along the body; the quad above it stays as
   drawn. Applies to `DEFAULT` and `COMET` only. App defaults for the decays become 0.15 / 0.60 s
   (`cursor_trail_decay` in kitty.conf still wins); the 2-cell threshold and 10 ms delay stay.
   Measure first: the same kitty.conf recorded on the emulator and on the developer's desktop kitty,
   side by side on the reports page, so the halo targets the real gap.
10. **Settings.** The Terminal effect list is unchanged. One new switch under it, **Screen events**
    (bell tear, power on and off, focus wobble, death static), default on. One new switch on the
    Battery and performance page, **Idle animations**, default off, described as a battery cost;
    it gates the ambient glitch, flicker and any future ambient effect. Plain copy per the UI
    copy rule: the row says what it does in one fact.
11. **Verification.** Emulator shots and short clips per wave on a `~/reports` page (CRT pane,
    bezel, chrome; bell tear; power on/off; trail next to desktop kitty). The 10-22 missed-echo-frame
    measurement with Terminal effect on and off runs once on pong during wave 1, as a check, not a
    gate.

## Amendments to the 10-22 spec

- *Decision 10 (flicker)*: flicker is ambient-only, under Idle animations, never tied to keystrokes
  or redraws. The amplitude and event caps stand.
- *Decision 11 (surfaces)*: the cursor and chrome take static shader parts; animated parts are
  pane-only. Keyboard and bars get aberration (constant) and the triad mask, nothing that moves.

## Waves

Each wave is one worker, merged and shot before the next starts.

1. **Static CRT + bezel** (pane and chrome): decisions 5 and 6. Files: `PaneRetroEffect.java`
   (`HEAD`, `CRT_AGSL`), `RetroUniforms.java` (new uniforms: recess/sheen on, aberration scale,
   dim), `RetroEffectBinder`, `PaneBezel.java`, `PaneContentFrame.java` (shade overlay). JVM tests
   for the new geometry (`PaneBezel.screen` sizes and clearance, shade stops), instrumentation
   compile of the shader as `GlassRefractionAgslInstrumentationTest` does.
2. **Trail**: decision 9 (halo + decays), plus the side-by-side measurement.
3. **Events**: decision 7's five events and the Screen events switch.
4. **Idle animations**: the switch, ambient glitch, flicker, and decision 8's dim.

## Owed device checks

Wave 1: a look at a CRT pane and the chrome on pong; the missed-frame check typing with Terminal
effect on and off. Each later wave: a look at its effect on pong. Record in
`project-docs/device-checks.md` when built.
